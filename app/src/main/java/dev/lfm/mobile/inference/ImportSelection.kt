package dev.lfm.mobile.inference

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Provider metadata is only an early usability check; streamed size and SHA remain authoritative. */
object ImportSelection {
    fun problem(name: String?, size: Long?, expectedName: String, expectedSize: Long): String? {
        val known = listOf("LFM3-1.7B-Q5_K_M.gguf") + VoicePart.entries.map { it.filename }
        if (name != null && known.any { it.equals(name, true) } && !name.equals(expectedName, true))
            return "Selected $name. This slot needs $expectedName. Choose its matching model card."
        if (size != null && size >= 0 && size != expectedSize)
            return "Selected ${name ?: "file"}: $size bytes. Expected $expectedName ($expectedSize bytes). Choose the matching file; an incomplete download also has the wrong size."
        return null
    }
    fun check(context: Context, uri: Uri, expectedName: String, expectedSize: Long) {
        var name: String? = null
        var size: Long? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n)
                if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s)
            }
        }
        check(problem(name, size, expectedName, expectedSize) == null) { problem(name, size, expectedName, expectedSize)!! }
    }
}
