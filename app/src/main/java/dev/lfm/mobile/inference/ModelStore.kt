package dev.lfm.mobile.inference

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelStore(private val context: Context) {
    companion object {
        const val SHA256 = "41e51fb0be5f472d323055f2734d986d5a2429570973508fa6322f3f07f6d7f3"
        const val SIZE = 1258663200L
        const val REVISION = "4b423c332fd97961416f2e8b69467758fd27370d"
        const val SOURCE = "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/blob/$REVISION/LFM3-1.7B-Q5_K_M.gguf"
    }
    private val directory = File(context.filesDir, "models").apply { mkdirs() }
    val model = File(directory, "text.gguf")
    suspend fun importModel(uri: Uri, progress: (Long) -> Unit): String = withContext(Dispatchers.IO) {
        check(StatFs(directory.path).availableBytes > SIZE + 256L * 1024 * 1024) { "Need at least 1.5 GB free app storage" }
        val temp = File(directory, "text.gguf.partial")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Cannot open selected file" }
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        check(count <= SIZE) { "Incorrect model size: use official LFM3-1.7B Q5_K_M" }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        progress(count)
                    }
                    output.fd.sync()
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(count == SIZE && hash == SHA256) { "Checksum mismatch: expected official LFM3-1.7B Q5_K_M ($SHA256)" }
            check(temp.renameTo(model)) { "Cannot commit verified model file" }
            hash
        } finally { temp.delete() }
    }
}
