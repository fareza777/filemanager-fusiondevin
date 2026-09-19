package app.sorta.files.core.zip

import kotlinx.coroutines.CancellationException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipCompressor {

    /** Compress [sources] into [outFile] (written via temp then renamed). */
    fun compress(
        sources: List<File>,
        outFile: File,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ) {
        val tmp = File(outFile.parentFile, ".sorta_tmp_" + outFile.name)
        try {
            ZipOutputStream(FileOutputStream(tmp).buffered()).use { zos ->
                sources.forEach { src -> put(src, baseName(src, sources), zos, onBytes, shouldContinue) }
            }
            if (outFile.exists() && !outFile.delete()) throw IOException("cannot overwrite")
            if (!tmp.renameTo(outFile)) throw IOException("rename failed")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun baseName(f: File, sources: List<File>): String = f.name

    private fun put(
        f: File, entryPath: String, zos: ZipOutputStream,
        onBytes: (Long) -> Unit, shouldContinue: () -> Boolean,
    ) {
        if (!shouldContinue()) throw CancellationException()
        if (f.isDirectory) {
            if (f.listFiles().isNullOrEmpty()) {
                zos.putNextEntry(ZipEntry("$entryPath/"))
                zos.closeEntry()
            }
            f.listFiles()?.forEach { put(it, "$entryPath/${it.name}", zos, onBytes, shouldContinue) }
        } else {
            zos.putNextEntry(ZipEntry(entryPath))
            BufferedInputStream(FileInputStream(f)).use { ins ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (!shouldContinue()) throw CancellationException()
                    val n = ins.read(buf); if (n < 0) break
                    zos.write(buf, 0, n)
                    onBytes(n.toLong())
                }
            }
            zos.closeEntry()
        }
    }
}

object ZipExtractor {

    /** Extract [zip] into [destDir]; zip-slip safe. */
    fun extract(
        zip: File,
        destDir: File,
        conflictPolicy: app.sorta.files.core.ops.ConflictPolicy,
        keepBothName: (File, String) -> String,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ) {
        destDir.mkdirs()
        val destCanon = destDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!shouldContinue()) throw CancellationException()
                val out = File(destDir, entry.name)
                // zip-slip guard
                if (!out.canonicalPath.startsWith(destCanon)) {
                    entry = zis.nextEntry; continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    var target = out
                    if (target.exists()) {
                        target = when (conflictPolicy) {
                            app.sorta.files.core.ops.ConflictPolicy.SKIP,
                            app.sorta.files.core.ops.ConflictPolicy.ASK -> { zis.closeEntry(); entry = zis.nextEntry; continue }
                            app.sorta.files.core.ops.ConflictPolicy.KEEP_BOTH ->
                                File(destDir, keepBothName(destDir, entry.name))
                            app.sorta.files.core.ops.ConflictPolicy.OVERWRITE -> target
                        }
                    }
                    FileOutputStream(target).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (!shouldContinue()) throw CancellationException()
                            val n = zis.read(buf); if (n < 0) break
                            fos.write(buf, 0, n)
                            onBytes(n.toLong())
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun isZip(f: File): Boolean = app.sorta.files.core.fs.MimeUtil.extension(f.name) == "zip"
}
