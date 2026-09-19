package app.sorta.files.core.ops

import android.content.Context
import app.sorta.files.data.db.HistoryEntry
import app.sorta.files.data.db.HistoryDao
import app.sorta.files.data.db.TrashEntry
import app.sorta.files.data.db.TrashDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Singleton glue between UI and [OperationEngine] + [FileOperationService].
 * UI calls [run]; dialogs are surfaced via [pendingConflict].
 */
class OperationController(
    private val appContext: Context,
    private val trashDao: TrashDao,
    private val historyDao: HistoryDao,
    private val trashRoot: (File) -> File, // volume root → trash dir
) : OperationHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var engine: OperationEngine? = null
        private set

    private var job: Job? = null

    private val _pendingConflict = MutableStateFlow<ConflictRequest?>(null)
    val pendingConflict: StateFlow<ConflictRequest?> = _pendingConflict

    private val _lastResults = MutableStateFlow<List<ItemResult>?>(null)
    val lastResults: StateFlow<List<ItemResult>?> = _lastResults

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** Set when any item fails with permission_denied → UI shows grant-access banner. */
    private val _permissionLost = MutableStateFlow(false)
    val permissionLost: StateFlow<Boolean> = _permissionLost
    fun clearPermissionLost() { _permissionLost.value = false }

    /** Snapshot of last delete-to-trash for Undo. */
    private val _lastTrashed = MutableStateFlow<List<TrashEntry>?>(null)
    val lastTrashed: StateFlow<List<TrashEntry>?> = _lastTrashed

    override suspend fun askConflict(sourceName: String, destPath: String): Pair<ConflictPolicy, Boolean> {
        val req = ConflictRequest(sourceName, destPath, CompletableDeferred())
        _pendingConflict.value = req
        // Never hang: if the UI never answers (host gone, timeout) default to SKIP.
        val r = kotlinx.coroutines.withTimeoutOrNull(CONFLICT_TIMEOUT_MS) {
            req.response.await()
        } ?: (ConflictPolicy.SKIP to false)
        _pendingConflict.value = null
        return r
    }

    override suspend fun trashMove(src: File): String {
        val trashDir = trashRoot(src)
        trashDir.mkdirs()
        val target = File(trashDir, UUID.randomUUID().toString() + "_" + src.name)
        if (!src.renameTo(target)) {
            // cross-FS or rename failure: copy then delete
            src.copyRecursively(target, overwrite = true)
            if (!src.deleteRecursively()) throw java.io.IOException("trash move failed")
        }
        val size = if (target.isFile) target.length()
        else target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val entry = TrashEntry(
            trashPath = target.absolutePath,
            originalPath = src.absolutePath,
            name = src.name,
            isDir = target.isDirectory,
            size = size,
        )
        trashDao.upsert(entry)
        synchronized(trashBuffer) { trashBuffer.add(entry) }
        return target.absolutePath
    }

    private val trashBuffer = mutableListOf<TrashEntry>()

    fun run(
        opType: OpType,
        sources: List<File>,
        destDir: File?,
        conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
        renameTarget: String? = null,
    ) {
        if (_running.value) return
        val eng = OperationEngine(this)
        eng.onMediaScan = { paths ->
            try {
                android.media.MediaScannerConnection.scanFile(
                    appContext, paths.toTypedArray(), null, null)
            } catch (_: Exception) {}
        }
        engine = eng
        _running.value = true
        _lastResults.value = null
        if (opType == OpType.DELETE) trashBuffer.clear()
        FileOperationService.start(appContext)
        job = scope.launch {
            try {
                val results = eng.run(opType, sources, destDir, conflictPolicy, renameTarget)
                _lastResults.value = results
                if (opType == OpType.DELETE) {
                    val entries = synchronized(trashBuffer) { trashBuffer.toList().also { trashBuffer.clear() } }
                    _lastTrashed.value = entries
                }
                if (results.any { it.error == "permission_denied" }) _permissionLost.value = true
                writeHistory(opType, results)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _lastResults.value = emptyList()
            } finally {
                _running.value = false
            }
        }
    }

    /** Compress [sources] to [outFile] through the engine (progress + history). */
    fun runCompress(sources: List<File>, outFile: File) {
        if (_running.value) return
        val eng = OperationEngine(this)
        eng.onMediaScan = { paths ->
            try {
                android.media.MediaScannerConnection.scanFile(
                    appContext, paths.toTypedArray(), null, null)
            } catch (_: Exception) {}
        }
        engine = eng
        _running.value = true
        _lastResults.value = null
        FileOperationService.start(appContext)
        job = scope.launch {
            try {
                val results = eng.runCompress(sources, outFile)
                _lastResults.value = results
                writeHistory(OpType.COMPRESS, results)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _lastResults.value = emptyList()
            } finally {
                _running.value = false
            }
        }
    }

    private suspend fun writeHistory(opType: OpType, results: List<ItemResult>) {
        val detail = results.joinToString("\n") {
            "${it.source} -> ${it.dest ?: "-"} : ${it.status}${it.error?.let { e -> " ($e)" } ?: ""}"
        }.take(4000)
        historyDao.insert(
            HistoryEntry(
                opType = opType.name,
                itemCount = results.size,
                succeeded = results.count { it.status == ItemStatus.SUCCESS },
                failed = results.count { it.status == ItemStatus.FAILED },
                skipped = results.count { it.status == ItemStatus.SKIPPED },
                detail = detail,
            )
        )
    }

    fun resolveConflict(policy: ConflictPolicy, applyToAll: Boolean) {
        _pendingConflict.value?.response?.complete(policy to applyToAll)
    }

    fun cancel() = job?.cancel()

    fun clearLastTrashed() { _lastTrashed.value = null }

    fun clearLastResults() { _lastResults.value = null }

    companion object {
        const val CONFLICT_TIMEOUT_MS = 120_000L
        @Volatile var instance: OperationController? = null
    }
}
