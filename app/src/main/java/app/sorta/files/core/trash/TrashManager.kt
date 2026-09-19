package app.sorta.files.core.trash

import app.sorta.files.data.db.TrashDao
import app.sorta.files.data.db.TrashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class TrashManager(private val trashDao: TrashDao) {

    /** Trash dir for a file: <volumeRoot>/.sorta_trash/<uuid-of-parent>/ */
    fun trashRootFor(file: File): File {
        // find volume root: use external storage root when under it, else walk up
        val root = volumeRootOf(file)
        val uuid = java.util.UUID.nameUUIDFromBytes(
            (file.parent ?: "/").toByteArray()
        ).toString()
        return File(root, ".sorta_trash/$uuid")
    }

    fun volumeRootOf(file: File): File {
        var f = file.canonicalFile
        var last = f
        while (true) {
            val p = f.parentFile ?: break
            last = f
            f = p
        }
        // `last` is the top-level child of "/", e.g. /storage or /sdcard.
        // Prefer known storage roots instead.
        val knownRoots = listOf(
            android.os.Environment.getExternalStorageDirectory(),
            File("/storage/emulated/0"),
        ) + File("/storage").listFiles().orEmpty().toList()
        val path = file.absolutePath
        return knownRoots.filter { path.startsWith(it.absolutePath) }
            .maxByOrNull { it.absolutePath.length } ?: last
    }

    suspend fun restore(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        val src = File(entry.trashPath)
        val dest = File(entry.originalPath)
        if (!src.exists()) return@withContext false
        dest.parentFile?.mkdirs()
        if (dest.exists()) return@withContext false
        val ok = src.renameTo(dest) || run {
            src.copyRecursively(dest, true) && src.deleteRecursively()
        }
        if (ok) {
            // remove empty uuid dir
            src.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
            trashDao.delete(entry.trashPath)
        }
        ok
    }

    suspend fun purge(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        val ok = File(entry.trashPath).deleteRecursively()
        if (ok || !File(entry.trashPath).exists()) trashDao.delete(entry.trashPath)
        ok
    }

    /** Delete entries older than 30 days. Called on app start. */
    suspend fun purgeExpired() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        trashDao.olderThan(cutoff).forEach { e ->
            File(e.trashPath).deleteRecursively()
            trashDao.delete(e.trashPath)
        }
        // also drop DB rows whose files vanished
    }

    fun isInsideTrash(path: String): Boolean = path.contains("/.sorta_trash/")
}
