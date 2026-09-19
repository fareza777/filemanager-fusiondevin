package app.sorta.files.core.scan

import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.MimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class StorageUsageSnapshot(
    val rootPath: String,
    val perCategory: Map<FileTypeCategory, Long> = emptyMap(),
    val largeFiles: List<FileItem> = emptyList(), // >50MB, desc by size, capped
    val emptyFolders: List<String> = emptyList(),
    val filesScanned: Int = 0,
    val done: Boolean = false,
    val scannedAt: Long = System.currentTimeMillis(),
) {
    val totalBytes: Long get() = perCategory.values.sum()
}

object StorageUsage {
    const val LARGE_FILE_MIN = 50L * 1024 * 1024
    const val LARGE_FILE_CAP = 100

    /** Incremental walk of [root]; emits a snapshot every ~200 files, final one done=true. */
    fun scan(root: File): Flow<StorageUsageSnapshot> = flow {
        val acc = java.util.EnumMap<FileTypeCategory, Long>(FileTypeCategory::class.java)
        val large = java.util.PriorityQueue<FileItem>(compareBy { it.size })
        val emptyDirs = mutableListOf<String>()
        var count = 0

        suspend fun walk(d: File) {
            val kids = d.listFiles()
            if (kids == null) { if (d.length() == 0L && d != root) emptyDirs += d.absolutePath; return }
            if (kids.isEmpty() && d != root) { emptyDirs += d.absolutePath; return }
            for (f in kids) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) walk(f)
                } else {
                    count++
                    val cat = MimeUtil.categoryOf(f.name, false)
                    acc[cat] = (acc[cat] ?: 0L) + f.length()
                    if (f.length() > LARGE_FILE_MIN) {
                        large += FileSystem.toItem(f)
                        if (large.size > LARGE_FILE_CAP) large.poll()
                    }
                    if (count % 200 == 0) {
                        emit(StorageUsageSnapshot(root.absolutePath, acc.toMap(),
                            large.sortedByDescending { it.size }, emptyDirs.toList(), count, false))
                    }
                }
            }
        }
        try { walk(root) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
        emit(StorageUsageSnapshot(root.absolutePath, acc.toMap(),
            large.sortedByDescending { it.size }, emptyDirs.toList(), count, true))
    }.flowOn(Dispatchers.IO)

    // Persisted snapshot: "ts|files|cat:bytes,cat:bytes" — one per root, in DataStore.
    fun serialize(s: StorageUsageSnapshot): String =
        "${s.scannedAt}|${s.filesScanned}|" +
            s.perCategory.entries.joinToString(",") { "${it.key.name}:${it.value}" }

    fun deserialize(root: String, v: String): StorageUsageSnapshot? = runCatching {
        val parts = v.split("|")
        val cats = parts[2].split(",").filter { it.contains(":") }.associate {
            val (k, b) = it.split(":"); FileTypeCategory.valueOf(k) to b.toLong()
        }
        StorageUsageSnapshot(root, cats, emptyList(), emptyList(),
            parts[1].toInt(), done = true, scannedAt = parts[0].toLong())
    }.getOrNull()
}
