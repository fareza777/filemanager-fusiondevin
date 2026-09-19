package app.sorta.files.core.scan

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.MimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecentFilesScanner {

    /** Files modified within [days], newest first, via MediaStore.Files. */
    suspend fun recent(context: Context, days: Int, limit: Int = 500): List<FileItem> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<FileItem>()
            val cutoff = System.currentTimeMillis() / 1000 - days * 24L * 3600
            val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val proj = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
            )
            try {
                context.contentResolver.query(
                    uri, proj,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?",
                    arrayOf(cutoff.toString()),
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { c ->
                    val dCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    val nCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val mCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val tCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    while (c.moveToNext() && out.size < limit) {
                        val path = c.getString(dCol) ?: continue
                        val f = java.io.File(path)
                        if (!f.isFile) continue
                        val name = c.getString(nCol) ?: f.name
                        val mime = c.getString(tCol) ?: MimeUtil.mimeOf(name)
                        out += FileItem(
                            path = path, name = name, isDir = false,
                            size = c.getLong(sCol),
                            lastModified = c.getLong(mCol) * 1000,
                            mime = mime,
                            category = MimeUtil.categoryOf(name, false),
                        )
                    }
                }
            } catch (_: Exception) { }
            // fallback: also scan Downloads dir directly (MediaStore may lag)
            if (out.isEmpty()) {
                val dl = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS)
                dl.listFiles()?.filter { it.isFile && it.lastModified() >= cutoff * 1000 }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(limit)
                    ?.forEach { out += FileSystem.toItem(it) }
            }
            out
        }

    /** MediaStore query for a category across the device. */
    suspend fun byCategory(context: Context, category: app.sorta.files.core.fs.FileTypeCategory): List<FileItem> =
        withContext(Dispatchers.IO) {
            val uri = when (category) {
                app.sorta.files.core.fs.FileTypeCategory.IMAGE ->
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                app.sorta.files.core.fs.FileTypeCategory.VIDEO ->
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                app.sorta.files.core.fs.FileTypeCategory.AUDIO ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
            val selection: String? = when (category) {
                app.sorta.files.core.fs.FileTypeCategory.APK ->
                    "${MediaStore.Files.FileColumns.DATA} LIKE '%.apk'"
                app.sorta.files.core.fs.FileTypeCategory.ARCHIVE ->
                    "(" + listOf("zip","rar","7z","tar","gz").joinToString(" OR ") {
                        "${MediaStore.Files.FileColumns.DATA} LIKE '%.$it'"
                    } + ")"
                app.sorta.files.core.fs.FileTypeCategory.DOCUMENT ->
                    "(" + listOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","md","csv","json").joinToString(" OR ") {
                        "${MediaStore.Files.FileColumns.DATA} LIKE '%.$it'"
                    } + ")"
                else -> null
            }
            val out = mutableListOf<FileItem>()
            val proj = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
            )
            try {
                context.contentResolver.query(
                    uri, proj, selection, null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { c ->
                    val dCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    val nCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val mCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val tCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    while (c.moveToNext()) {
                        val path = c.getString(dCol) ?: continue
                        if (!java.io.File(path).isFile) continue
                        val name = c.getString(nCol) ?: path.substringAfterLast('/')
                        val mime = c.getString(tCol) ?: MimeUtil.mimeOf(name)
                        out += FileItem(
                            path, name, false, c.getLong(sCol),
                            c.getLong(mCol) * 1000, mime,
                            MimeUtil.categoryOf(name, false),
                        )
                    }
                }
            } catch (_: Exception) { }
            // MediaStore can miss files on emulators — fall back to a bounded fs walk
            if (out.isEmpty()) {
                val pred: (String) -> Boolean = { n ->
                    MimeUtil.categoryOf(n, false) == category
                }
                val roots = listOf(android.os.Environment.getExternalStorageDirectory())
                roots.forEach { root ->
                    root.walkTopDown().onEnter { it.name != ".sorta_trash" && !it.isHidden }
                        .filter { it.isFile && pred(it.name) }
                        .take(2000)
                        .forEach { out += FileSystem.toItem(it) }
                }
                out.sortByDescending { it.lastModified }
            }
            out
        }
}
