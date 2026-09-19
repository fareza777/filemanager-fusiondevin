package app.sorta.files.core.fs

/** A single file-system entry presented to the UI. */
data class FileItem(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val mime: String?,
    val category: FileTypeCategory,
    val childCount: Int? = null,
)
