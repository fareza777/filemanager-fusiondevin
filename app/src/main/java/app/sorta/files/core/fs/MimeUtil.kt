package app.sorta.files.core.fs

import android.webkit.MimeTypeMap
import java.util.Locale

object MimeUtil {

    private val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar")
    private val docExts = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
        "rtf", "epub", "mobi", "chm", "djvu"
    )
    private val textExts = setOf(
        "txt", "md", "log", "csv", "json", "xml", "yaml", "yml", "ini", "cfg",
        "kt", "java", "py", "js", "ts", "html", "css", "sh", "c", "cpp", "h",
        "gradle", "properties", "sql", "toml", "rs", "go", "rb"
    )

    fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    fun mimeOf(path: String): String? {
        val ext = extension(path)
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when {
                ext in archiveExts -> "application/zip"
                ext in textExts -> "text/plain"
                ext == "apk" -> "application/vnd.android.package-archive"
                else -> null
            }
    }

    fun categoryOf(name: String, isDir: Boolean): FileTypeCategory {
        if (isDir) return FileTypeCategory.FOLDER
        val ext = extension(name)
        val mime = mimeOf(name)
        return when {
            ext == "apk" -> FileTypeCategory.APK
            ext in archiveExts -> FileTypeCategory.ARCHIVE
            ext in docExts -> FileTypeCategory.DOCUMENT
            ext in textExts -> FileTypeCategory.DOCUMENT
            mime?.startsWith("image/") == true -> FileTypeCategory.IMAGE
            mime?.startsWith("video/") == true -> FileTypeCategory.VIDEO
            mime?.startsWith("audio/") == true -> FileTypeCategory.AUDIO
            mime?.startsWith("text/") == true -> FileTypeCategory.DOCUMENT
            else -> FileTypeCategory.OTHER
        }
    }

    fun isTextLike(name: String, mime: String?): Boolean {
        val ext = extension(name)
        return ext in textExts || mime?.startsWith("text/") == true ||
            mime in setOf("application/json", "application/xml")
    }
}
