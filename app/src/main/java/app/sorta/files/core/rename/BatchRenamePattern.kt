package app.sorta.files.core.rename

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RenamePreview(
    val oldName: String,
    val newName: String,
    val conflict: Boolean,
)

/**
 * Batch rename by pattern tokens or simple find/replace.
 * Tokens: {name} {ext} {n} {date}
 */
class BatchRenamePattern(
    val pattern: String = "{name}{ext}",
    val start: Int = 1,
    val step: Int = 1,
    val padding: Int = 1,
    val find: String? = null,
    val replace: String = "",
) {
    private val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun newName(file: File, index: Int): String {
        val dot = file.name.lastIndexOf('.')
        val stem = if (dot > 0) file.name.substring(0, dot) else file.name
        val ext = if (dot > 0) file.name.substring(dot) else ""
        if (find != null) {
            val renamedStem = stem.replace(find, replace, ignoreCase = true)
            return renamedStem + ext
        }
        val n = (start + index * step).toString().padStart(padding, '0')
        return pattern
            .replace("{name}", stem)
            .replace("{ext}", ext)
            .replace("{n}", n)
            .replace("{date}", dateFmt.format(Date(file.lastModified())))
    }

    /** Preview new names; conflicts = duplicate targets or existing files on disk. */
    fun preview(files: List<File>): List<RenamePreview> {
        val names = files.mapIndexed { i, f -> newName(f, i) }
        val counts = names.groupingBy { it.lowercase(Locale.US) }.eachCount()
        return files.mapIndexed { i, f ->
            val newName = names[i]
            val sameDir = File(f.parentFile, newName)
            val conflict = counts[newName.lowercase(Locale.US)]!! > 1 ||
                (sameDir.exists() && newName != f.name)
            RenamePreview(f.name, newName, conflict)
        }
    }

    companion object {
        /** Apply renames; returns list of (old,new) pairs. Caller runs via engine per item ideally. */
        fun applyRenames(files: List<File>, previews: List<RenamePreview>): List<Pair<File, File>> =
            files.mapIndexed { i, f -> f to File(f.parentFile, previews[i].newName) }
    }
}
