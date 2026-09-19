package app.sorta.files.core.ops

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class OpType { COPY, MOVE, DELETE, DELETE_PERMANENT, RENAME, NEW_FOLDER, COMPRESS, EXTRACT }

enum class ConflictPolicy { ASK, SKIP, KEEP_BOTH, OVERWRITE }

enum class ItemStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }

data class ItemResult(
    val source: String,
    var dest: String?,
    var status: ItemStatus = ItemStatus.PENDING,
    var error: String? = null,
)

data class OperationProgress(
    val opType: OpType,
    val totalItems: Int,
    val doneItems: Int,
    val totalBytes: Long,
    val doneBytes: Long,
    val currentItem: String?,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
)

/** A pending conflict awaiting a UI decision. */
data class ConflictRequest(
    val sourceName: String,
    val destPath: String,
    val response: CompletableDeferred<Pair<ConflictPolicy, Boolean>>, // policy, applyToAll
)

/**
 * Callbacks the engine needs from its host (UI/controller).
 * [trashMove] performs a delete-to-trash and returns the trash path used (or throws).
 */
interface OperationHost {
    suspend fun askConflict(sourceName: String, destPath: String): Pair<ConflictPolicy, Boolean>
    suspend fun trashMove(src: File): String // returns trash path
}

class OperationEngine(private val host: OperationHost) {

    private val _progress = MutableStateFlow<OperationProgress?>(null)
    val progress: StateFlow<OperationProgress?> = _progress

    @Volatile private var applyAllPolicy: ConflictPolicy? = null

    /** Injectable copier for tests (simulate IO failures). */
    var copyBytes: (File, File, (Long) -> Unit, () -> Boolean) -> Unit = { src, dst, onBytes, shouldContinue ->
        FileInputStream(src).channel.use { inCh ->
            FileOutputStream(dst).channel.use { outCh ->
                val buf = java.nio.ByteBuffer.allocate(64 * 1024)
                while (true) {
                    if (!shouldContinue()) throw CancellationException()
                    buf.clear()
                    val n = inCh.read(buf)
                    if (n < 0) break
                    buf.flip()
                    outCh.write(buf)
                    onBytes(n.toLong())
                }
                outCh.force(true)
            }
        }
    }

    /** Called with affected paths after each op so MediaStore stays in sync. */
    var onMediaScan: (List<String>) -> Unit = {}

    var sha256: (File) -> String = { f ->
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun totalSizeOf(f: File): Long =
        if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun countItems(f: File): Int =
        if (f.isFile) 1 else 1 + f.walkTopDown().count { it != f }

    /** "name.ext" + existing check → keep-both name "name (1).ext". */
    fun keepBothName(destDir: File, name: String): String {
        if (!File(destDir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val cand = "$stem ($i)$ext"
            if (!File(destDir, cand).exists()) return cand
            i++
        }
    }

    suspend fun run(
        opType: OpType,
        sources: List<File>,
        destDir: File?,
        conflictPolicy: ConflictPolicy,
        renameTarget: String? = null,
    ): List<ItemResult> = withContext(Dispatchers.IO) {
        applyAllPolicy = if (conflictPolicy == ConflictPolicy.ASK) null else conflictPolicy
        continueFlag = { coroutineContext.isActive }
        val results = sources.map { ItemResult(it.absolutePath, null) }
        var totalBytes = 0L
        sources.forEach { totalBytes += try { totalSizeOf(it) } catch (_: Exception) { 0L } }
        var doneItems = 0
        var doneBytes = 0L
        val scanPaths = mutableListOf<String>()
        fun emit(current: String?) {
            _progress.value = OperationProgress(
                opType, sources.size, doneItems, totalBytes, doneBytes, current)
        }
        emit(null)

        // disk-full precheck for copy/move
        if ((opType == OpType.COPY || opType == OpType.MOVE) && destDir != null) {
            val free = destDir.usableSpace
            if (free in 1 until totalBytes) {
                sources.forEach { s ->
                    results[sources.indexOf(s)].apply {
                        status = ItemStatus.FAILED; error = "disk_full"
                    }
                }
                _progress.value = _progress.value!!.copy(doneItems = sources.size, finished = true)
                return@withContext results
            }
        }

        for (i in sources.indices) {
            if (!coroutineContext.isActive) {
                _progress.value = _progress.value!!.copy(cancelled = true, finished = true)
                return@withContext results
            }
            val src = sources[i]
            val res = results[i]
            res.status = ItemStatus.RUNNING
            emit(src.name)
            try {
                when (opType) {
                    OpType.COPY -> copyItem(src, destDir!!, res) { doneBytes += it; emit(src.name) }
                    OpType.MOVE -> moveItem(src, destDir!!, res) { doneBytes += it; emit(src.name) }
                    OpType.DELETE -> {
                        res.dest = host.trashMove(src)
                        res.status = ItemStatus.SUCCESS
                    }
                    OpType.DELETE_PERMANENT -> {
                        if (!src.deleteRecursively()) throw IOException("delete failed")
                        res.status = ItemStatus.SUCCESS
                    }
                    OpType.RENAME -> {
                        val target = File(src.parentFile, renameTarget!!)
                        if (!src.renameTo(target)) throw IOException("rename failed")
                        res.dest = target.absolutePath
                        res.status = ItemStatus.SUCCESS
                    }
                    OpType.NEW_FOLDER -> {
                        val target = File(destDir!!, src.name)
                        if (!target.mkdirs() && !target.isDirectory) throw IOException("mkdir failed")
                        res.dest = target.absolutePath
                        res.status = ItemStatus.SUCCESS
                    }
                    OpType.COMPRESS -> {
                        // handled by runCompress — not reached via per-item loop
                        res.status = ItemStatus.SKIPPED
                    }
                    OpType.EXTRACT -> {
                        app.sorta.files.core.zip.ZipExtractor.extract(
                            src, destDir!!, applyAllPolicy ?: conflictPolicy,
                            { d, n -> keepBothName(d, n) },
                            { doneBytes += it; emit(src.name) }, continueFlag)
                        res.dest = destDir.absolutePath
                        res.status = ItemStatus.SUCCESS
                    }
                }
            } catch (ce: CancellationException) {
                res.status = ItemStatus.FAILED
                res.error = "cancelled"
                throw ce
            } catch (e: SecurityException) {
                res.status = ItemStatus.FAILED
                res.error = "permission_denied"
            } catch (e: Exception) {
                if (res.status == ItemStatus.RUNNING) {
                    res.status = ItemStatus.FAILED
                    res.error = when (e) {
                        is OpFailure -> e.key
                        else -> e.message ?: e.javaClass.simpleName
                    }
                }
            }
            res.dest?.let { scanPaths += it }
            scanPaths += src.absolutePath
            doneItems++
            emit(src.name)
        }
        _progress.value = _progress.value!!.copy(finished = true)
        try { onMediaScan(scanPaths.distinct()) } catch (_: Exception) {}
        results
    }

    /** Compress all [sources] into [outFile]; single-item result batch. */
    suspend fun runCompress(
        sources: List<File>,
        outFile: File,
    ): List<ItemResult> = withContext(Dispatchers.IO) {
        continueFlag = { coroutineContext.isActive }
        val results = sources.map { ItemResult(it.absolutePath, null) }
        var totalBytes = 0L
        sources.forEach { totalBytes += try { totalSizeOf(it) } catch (_: Exception) { 0L } }
        var doneBytes = 0L
        fun emit(cur: String?) {
            _progress.value = OperationProgress(
                OpType.COMPRESS, 1, 0, totalBytes, doneBytes, cur)
        }
        emit(outFile.name)
        try {
            app.sorta.files.core.zip.ZipCompressor.compress(
                sources, outFile,
                { doneBytes += it; emit(outFile.name) }, continueFlag)
            results.forEach { it.status = ItemStatus.SUCCESS; it.dest = outFile.absolutePath }
        } catch (ce: CancellationException) {
            _progress.value = _progress.value!!.copy(cancelled = true, finished = true)
            throw ce
        } catch (e: Exception) {
            results.forEach {
                it.status = ItemStatus.FAILED
                it.error = if (e is OpFailure) e.key else (e.message ?: "generic")
            }
        }
        _progress.value = _progress.value!!.copy(doneItems = 1, finished = true)
        try { onMediaScan(listOf(outFile.absolutePath)) } catch (_: Exception) {}
        results
    }

    class OpFailure(val key: String) : IOException(key)

    private suspend fun resolveConflict(src: File, destDir: File, res: ItemResult): File? {
        var dest = File(destDir, src.name)
        if (!dest.exists()) return dest
        var policy = applyAllPolicy
        if (policy == null || policy == ConflictPolicy.ASK) {
            val (p, applyAll) = host.askConflict(src.name, dest.absolutePath)
            if (applyAll) applyAllPolicy = p
            policy = p
        }
        return when (policy) {
            ConflictPolicy.SKIP -> { res.status = ItemStatus.SKIPPED; null }
            ConflictPolicy.KEEP_BOTH -> File(destDir, keepBothName(destDir, src.name))
            ConflictPolicy.OVERWRITE -> {
                if (dest.isDirectory && src.isFile) throw OpFailure("dest_exists_dir")
                dest
            }
            ConflictPolicy.ASK -> { res.status = ItemStatus.SKIPPED; null }
        }
    }

    private fun isIntoItself(src: File, destDir: File): Boolean = try {
        val s = src.canonicalPath.trimEnd('/') + '/'
        val d = destDir.canonicalPath.trimEnd('/') + '/'
        d.startsWith(s)
    } catch (_: Exception) { false }

    @Volatile private var continueFlag: () -> Boolean = { true }

    private fun copyTree(src: File, dest: File, onBytes: (Long) -> Unit) {
        if (src.isDirectory) {
            if (!dest.mkdirs() && !dest.isDirectory) throw IOException("mkdir failed: ${dest.name}")
            src.listFiles()?.forEach { copyTree(it, File(dest, it.name), onBytes) }
        } else {
            val tmp = File(dest.parentFile, ".sorta_tmp_" + dest.name)
            try {
                copyBytes(src, tmp, onBytes, continueFlag)
                if (tmp.length() != src.length()) throw OpFailure("size_mismatch")
                if (src.length() <= 64L * 1024 * 1024 && sha256(tmp) != sha256(src))
                    throw OpFailure("checksum_mismatch")
                if (dest.exists() && !dest.delete()) throw OpFailure("dest_exists_dir")
                if (!tmp.renameTo(dest)) throw IOException("rename failed")
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
    }

    private suspend fun copyItem(src: File, destDir: File, res: ItemResult, onBytes: (Long) -> Unit) {
        if (src.isDirectory && isIntoItself(src, destDir)) throw OpFailure("into_itself")
        val dest = resolveConflict(src, destDir, res) ?: return
        copyTree(src, dest, onBytes)
        res.dest = dest.absolutePath
        res.status = ItemStatus.SUCCESS
    }

    private suspend fun moveItem(src: File, destDir: File, res: ItemResult, onBytes: (Long) -> Unit) {
        // same-volume fast path
        val naive = File(destDir, src.name)
        if (!naive.exists() && src.renameTo(naive)) {
            res.dest = naive.absolutePath
            res.status = ItemStatus.SUCCESS
            onBytes(totalSizeOf(naive))
            return
        }
        if (src.isDirectory && isIntoItself(src, destDir)) throw OpFailure("into_itself")
        val dest = resolveConflict(src, destDir, res) ?: return
        copyTree(src, dest, onBytes)
        // verified — now delete source
        if (!src.deleteRecursively()) {
            res.status = ItemStatus.FAILED
            res.error = "source_delete_failed"
            return
        }
        res.dest = dest.absolutePath
        res.status = ItemStatus.SUCCESS
    }

    private suspend fun totalSizeOfSafe(f: File): Long = try { totalSizeOf(f) } catch (_: Exception) { 0 }
    private suspend fun countItemsSafe(f: File): Int = try { countItems(f) } catch (_: Exception) { 1 }
    private suspend fun ensure() = coroutineContext.ensureActive()
}
