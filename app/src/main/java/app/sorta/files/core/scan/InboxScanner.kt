package app.sorta.files.core.scan

import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.data.db.InboxSource
import app.sorta.files.data.db.InboxState
import app.sorta.files.data.db.InboxStateDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class InboxFile(val item: FileItem, val sourceLabel: String, val tidied: Boolean)

object InboxScanner {

    /** Emit per-source batches of inbox files (newest first within source). */
    fun scan(
        sources: List<InboxSource>,
        stateDao: InboxStateDao,
    ): Flow<List<InboxFile>> = flow {
        sources.filter { it.enabled }.forEach { src ->
            val root = File(src.path)
            if (!root.isDirectory) return@forEach
            val files = mutableListOf<File>()
            root.listFiles()?.filter { it.isFile }?.let { files += it }
            if (src.recursive) {
                // depth ≤ 2 subfolders
                root.listFiles()?.filter { it.isDirectory && !it.isHidden }?.forEach { d1 ->
                    d1.listFiles()?.filter { it.isFile }?.let { files += it }
                    d1.listFiles()?.filter { it.isDirectory && !it.isHidden }?.forEach { d2 ->
                        d2.listFiles()?.filter { it.isFile }?.let { files += it }
                    }
                }
            }
            val batch = files.sortedByDescending { it.lastModified() }
                .map {
                    val st = stateDao.get(it.absolutePath)
                    InboxFile(FileSystem.toItem(it), src.label, st?.state == "TIDIED")
                }
            emit(batch)
        }
    }.flowOn(Dispatchers.IO)
}
