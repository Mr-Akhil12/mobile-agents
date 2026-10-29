package dev.lfm.mobile.updates

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Grants read access to one verified public APK, never to history/model files. */
class UpdateApkProvider : ContentProvider() {
    override fun onCreate() = true
    private fun file(uri: Uri): File { require(uri.path == "/candidate.apk"); return File(checkNotNull(context).cacheDir, "updates/candidate.apk") }
    override fun getType(uri: Uri): String { file(uri); return "application/vnd.android.package-archive" }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor { require(mode == "r"); return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY) }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = file(uri); val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) "Local_Agents_update.apk" else if (it == OpenableColumns.SIZE) f.length() else null }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
}
