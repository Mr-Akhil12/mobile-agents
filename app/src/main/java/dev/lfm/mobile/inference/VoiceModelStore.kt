package dev.lfm.mobile.inference

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

enum class VoicePart(val filename: String, val bytes: Long, val sha256: String, val local: String) {
    MODEL("LFM3-Omni-2B-Q4_K_M.gguf", 1099630080L, "9f8d30e9d4fd7a9349d586e077988229b3ca0a0e4ad730897563d1eccc9893e2", "omni.gguf"),
    PROJECTOR("mmproj-LFM3-Omni-2B-Q8_0.gguf", 540892768L, "720ff4fc09df0e026c4ec641d0c698bf27b5bcd50561944c88eda5e3dffe1839", "omni-projector.gguf");
    val source get() = "https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF/blob/2918dd47cfceb4f260cd1734cd1aac1b22bbe4b6/$filename"
}
class VoiceModelStore(private val context: Context) {
    private val directory = File(context.filesDir, "models").apply { mkdirs() }
    fun file(part: VoicePart) = File(directory, part.local)
    val ready get() = VoicePart.entries.all { file(it).length() == it.bytes }
    suspend fun import(part: VoicePart, uri: Uri, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        check(StatFs(directory.path).availableBytes > part.bytes + 256L * 1024 * 1024) { "Not enough free app storage for ${part.filename}" }
        val temp = File(directory, part.local + ".partial")
        try {
            val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Cannot open selected voice file" }
                FileOutputStream(temp).use { output ->
                    val bytes = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(bytes); if (n < 0) break
                        count += n; check(count <= part.bytes) { "Wrong voice file size; choose ${part.filename}" }
                        digest.update(bytes, 0, n); output.write(bytes, 0, n); progress(count)
                    }
                    output.fd.sync()
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(count == part.bytes && hash == part.sha256) { "Voice checksum mismatch; expected the pinned ${part.filename}" }
            check(temp.renameTo(file(part))) { "Cannot commit verified voice file" }
        } finally { temp.delete() }
    }
}
