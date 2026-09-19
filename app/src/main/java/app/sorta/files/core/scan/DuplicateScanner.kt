package app.sorta.files.core.scan

import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DuplicateScan(
    val groups: List<List<FileItem>>,
    val filesScanned: Int,
    val done: Boolean,
)

object DuplicateScanner {
    const val MIN_SIZE = 1L // include everything; size-grouping is cheap

    private fun allFiles(root: File, out: MutableList<File>) {
        root.listFiles()?.forEach { f ->
            if (f.isDirectory) { if (!f.name.startsWith(".")) allFiles(f, out) }
            else if (f.length() > MIN_SIZE) out += f
        }
    }

    private fun head64k(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(f).use { s ->
            val buf = ByteArray(8192)
            var left = 65536
            while (left > 0) {
                val n = s.read(buf, 0, minOf(buf.size, left))
                if (n < 0) break
                md.update(buf, 0, n); left -= n
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { s ->
            val buf = ByteArray(65536)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Emits progressively: size groups -> 64KB-head candidates -> verified SHA-256 groups.
     */
    fun scan(root: File): Flow<DuplicateScan> = flow {
        val files = mutableListOf<File>()
        try { allFiles(root, files) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        emit(DuplicateScan(emptyList(), files.size, false))

        val bySize = files.groupBy { it.length() }.filter { it.value.size > 1 }
        val byHead = bySize.values.flatten().groupBy { head64k(it) }
            .filter { it.value.size > 1 }
        val groups = mutableListOf<List<FileItem>>()
        byHead.forEach { (_, cands) ->
            cands.groupBy { sha256(it) }.values.filter { it.size > 1 }
                .forEach { g ->
                    groups += g.map { FileSystem.toItem(it) }
                        .sortedByDescending { it.lastModified }
                    emit(DuplicateScan(groups.toList(), files.size, false))
                }
        }
        emit(DuplicateScan(groups.toList(), files.size, true))
    }.flowOn(Dispatchers.IO)
}
