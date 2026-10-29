package dev.lfm.mobile.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.atomic.AtomicReference

data class AppUpdate(val code: Long, val version: String, val url: String, val sha256: String,
    val bytes: Long, val signer: String, val notes: String)
data class UpdateState(val busy: Boolean = false, val message: String = "Check for signed app updates", val available: AppUpdate? = null,
    val downloaded: Boolean = false, val progress: Int = 0)

/** Only public release metadata/APKs leave or enter this path. No chat/model data. */
class UpdateManager(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("updates", 0)
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private val connection = AtomicReference<HttpsURLConnection?>()
    private val folder = File(context.cacheDir, "updates").apply { mkdirs() }
    private val candidate = File(folder, "candidate.apk")
    val currentCode get() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    val currentVersion get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: currentCode.toString()
    val autoCheck get() = prefs.getBoolean("automatic", true)
    fun setAutoCheck(enabled: Boolean) { prefs.edit().putBoolean("automatic", enabled).apply() }
    fun shouldPrompt(update: AppUpdate) = update.code > prefs.getLong("dismissed", 0)
    fun dismiss() { state.value.available?.let { prefs.edit().putLong("dismissed", it.code).apply() } }
    fun checkIfDue() { if (autoCheck && System.currentTimeMillis() - prefs.getLong("checked", 0) > 24L * 60 * 60 * 1000) check() }
    fun check() {
        if (state.value.busy) return
        job = scope.launch(Dispatchers.IO) {
            mutable.update { it.copy(busy = true, message = "Checking GitHub for signed updates") }
            prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
            try {
                val releases = JSONArray(getBytes("https://api.github.com/repos/Mr-Akhil12/mobile-agents/releases?per_page=5", 1024 * 1024).decodeToString())
                val ownSigner = currentSigner(context)
                val compatible = mutableListOf<AppUpdate>()
                var metadataCount = 0
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i); if (release.optBoolean("draft")) continue
                    val assets = release.optJSONArray("assets") ?: continue
                    val all = (0 until assets.length()).map { assets.getJSONObject(it) }
                    val manifest = all.firstOrNull { it.optString("name") == "local-agents-update.json" } ?: continue
                    metadataCount++
                    val json = JSONObject(getBytes(manifest.getString("browser_download_url"), 65536).decodeToString())
                    check(json.getString("applicationId") == context.packageName) { "Update package mismatch" }
                    val name = json.getString("apk")
                    val apk = all.firstOrNull { it.optString("name") == name } ?: error("Release APK missing")
                    val update = UpdatePolicy.parse(json, apk.getString("browser_download_url"), release.optString("body").take(6000))
                    check(update.signer == ownSigner) { "Update signing identity differs; keep this installation and its data" }
                    if (json.optInt("minSdk", 28) <= android.os.Build.VERSION.SDK_INT) compatible += update
                }
                val newest = compatible.maxByOrNull { it.code }?.takeIf { it.code > currentCode }
                mutable.value = UpdateState(message = if (newest != null) "Local Agents ${newest.version} is available" else if (metadataCount == 0) "No signed app updates published yet" else "You're up to date", available = newest)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = e.message?.take(180) ?: "Update check unavailable; local chat still works") } }
            finally { connection.getAndSet(null)?.disconnect(); mutable.update { it.copy(busy = false) } }
        }
    }
    fun download() {
        val update = state.value.available ?: return
        if (state.value.busy) return
        job = scope.launch(Dispatchers.IO) {
            val partial = File(folder, "candidate.apk.partial")
            mutable.update { it.copy(busy = true, downloaded = false, progress = 0, message = "Downloading signed update") }
            try {
                val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
                open(update.url).inputStream.use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        count += n; check(count <= update.bytes && count <= UpdatePolicy.MAX_BYTES) { "Update exceeds expected size" }
                        digest.update(buffer, 0, n); output.write(buffer, 0, n)
                        mutable.update { it.copy(progress = (count * 100 / update.bytes).toInt()) }
                    }
                } }
                check(count == update.bytes && hex(digest.digest()) == update.sha256) { "Update checksum mismatch" }
                verifyArchive(context, partial, update)
                check(partial.renameTo(candidate)) { "Cannot stage verified update" }
                mutable.update { it.copy(downloaded = true, message = "Update verified · Android will ask before installing") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = e.message?.take(180) ?: "Update download failed") } }
            finally { partial.delete(); connection.getAndSet(null)?.disconnect(); mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancel() { job?.cancel(); connection.getAndSet(null)?.disconnect(); mutable.update { it.copy(busy = false, message = "Update operation cancelled") } }
    suspend fun installerIntent(): Intent = withContext(Dispatchers.IO) {
        val update = checkNotNull(state.value.available)
        check(state.value.downloaded && candidate.isFile) { "Download and verify the update first" }
        val hash = candidate.inputStream().use { stream -> val d = MessageDigest.getInstance("SHA-256"); val b = ByteArray(65536); while (true) { val n = stream.read(b); if (n < 0) break; d.update(b, 0, n) }; hex(d.digest()) }
        check(hash == update.sha256) { "Cached update changed; download again" }
        verifyArchive(context, candidate, update)
        if (!context.packageManager.canRequestPackageInstalls()) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        else Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://${context.packageName}.updates/candidate.apk"), "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    private fun getBytes(url: String, limit: Int): ByteArray {
        open(url).inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream(); val b = ByteArray(8192)
            while (true) { val n = input.read(b); if (n < 0) break; check(out.size() + n <= limit) { "Update metadata too large" }; out.write(b, 0, n) }
            return out.toByteArray()
        }
    }
    private fun open(url: String): HttpsURLConnection {
        var next = url
        for (redirect in 0..5) {
            val uri = URI(next)
            check(uri.scheme == "https" && uri.userInfo == null && uri.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) { "Untrusted update host" }
            val c = URL(next).openConnection() as HttpsURLConnection; connection.getAndSet(c)?.disconnect()
            c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "Local-Agents-Android"); c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode in 300..399) { next = URI(next).resolve(checkNotNull(c.getHeaderField("Location"))).toString(); c.disconnect(); continue }
            check(c.responseCode in 200..299) { "Update service unavailable (${c.responseCode})" }; return c
        }
        error("Too many update redirects")
    }
    companion object {
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        fun currentSigner(context: Context): String {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val certificates = checkNotNull(info.signingInfo).apkContentsSigners
            check(certificates.size == 1) { "Unsupported signing configuration" }
            return hex(MessageDigest.getInstance("SHA-256").digest(certificates.single().toByteArray()))
        }
        fun verifyArchive(context: Context, file: File, update: AppUpdate) {
            val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("Invalid APK")
            check(info.packageName == context.packageName) { "Wrong update application" }
            val certificates = checkNotNull(info.signingInfo).apkContentsSigners
            check(certificates.size == 1 && hex(MessageDigest.getInstance("SHA-256").digest(certificates.single().toByteArray())) == currentSigner(context) && update.signer == currentSigner(context)) { "Update signer mismatch; existing data is safe" }
            check(info.longVersionCode == update.code && update.code > context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode) { "Update version is not newer or does not match metadata" }
        }
    }
}

object UpdatePolicy {
    const val MAX_BYTES = 256L * 1024 * 1024
    fun parse(json: JSONObject, url: String, notes: String): AppUpdate {
        val uri = URI(url).normalize()
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.path.startsWith("/Mr-Akhil12/mobile-agents/releases/download/")) { "Untrusted release URL" }
        val hash = json.getString("sha256").lowercase(); val signer = json.getString("signerSha256").lowercase()
        require(Regex("[a-f0-9]{64}").matches(hash) && Regex("[a-f0-9]{64}").matches(signer)) { "Invalid release digests" }
        val bytes = json.getLong("bytes"); require(bytes in 1..MAX_BYTES)
        val code = json.getLong("versionCode"); require(code in 1..Int.MAX_VALUE.toLong())
        return AppUpdate(code, json.getString("versionName").take(60), uri.toString(), hash, bytes, signer, notes)
    }
}
