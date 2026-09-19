package app.sorta.files.core.fs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class SortField { NAME, DATE, SIZE, TYPE }

data class SortSpec(val field: SortField = SortField.NAME, val ascending: Boolean = true)

/** Path segments under <volume>/Android that are restricted to other apps. */
object RestrictedPaths {
    fun isRestricted(path: String): Boolean {
        val norm = path.trimEnd('/')
        return norm.endsWith("/Android/data") || norm.endsWith("/Android/obb") ||
            norm.contains("/Android/data/") || norm.contains("/Android/obb/")
    }

    fun restrictedParentOf(path: String): Boolean {
        // listing Android/ itself shows data + obb rows as restricted
        return normEndsWithAndroid(path)
    }

    private fun normEndsWithAndroid(path: String) = path.trimEnd('/').endsWith("/Android")
}

object FileSystem {

    fun stat(path: String): FileItem? {
        val f = File(path)
        if (!f.exists()) return null
        return toItem(f)
    }

    fun toItem(f: File): FileItem {
        val mime = if (f.isDirectory) null else MimeUtil.mimeOf(f.name)
        return FileItem(
            path = f.absolutePath,
            name = f.name.ifEmpty { f.absolutePath },
            isDir = f.isDirectory,
            size = if (f.isDirectory) 0L else f.length(),
            lastModified = f.lastModified(),
            mime = mime,
            category = MimeUtil.categoryOf(f.name, f.isDirectory),
            childCount = if (f.isDirectory) f.list()?.size else null,
        )
    }

    suspend fun list(
        dir: String,
        showHidden: Boolean,
        sort: SortSpec,
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val files = File(dir).listFiles() ?: return@withContext emptyList()
        val filtered = files.asSequence()
            .filter { showHidden || !it.isHidden && !it.name.startsWith(".") }
            .map { toItem(it) }
        sortList(filtered.toList(), sort)
    }

    fun sortList(items: List<FileItem>, sort: SortSpec): List<FileItem> {
        // Dirs always first; the field comparator is applied within each group,
        // reversed when descending (never reversing a CASE_INSENSITIVE name tiebreak oddly).
        fun sortGroup(l: List<FileItem>): List<FileItem> {
            val fieldCmp: Comparator<FileItem> = when (sort.field) {
                SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortField.DATE -> compareBy { it.lastModified }
                SortField.SIZE -> compareBy { it.size }
                SortField.TYPE -> compareBy<FileItem> { it.category.ordinal }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
            return if (sort.ascending) l.sortedWith(fieldCmp) else l.sortedWith(fieldCmp.reversed())
        }
        return sortGroup(items.filter { it.isDir }) + sortGroup(items.filter { !it.isDir })
    }

    /** True when the dir exists but children cannot be listed (usually restricted). */
    fun isUnreadable(dir: String): Boolean {
        val f = File(dir)
        return f.isDirectory && f.listFiles() == null
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        do { v /= 1024; i++ } while (v >= 1024 && i < units.lastIndex)
        return String.format("%.1f %s", v, units[i])
    }
}
