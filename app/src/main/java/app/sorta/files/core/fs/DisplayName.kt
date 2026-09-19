package app.sorta.files.core.fs

import android.content.Context
import app.sorta.files.R
import java.io.File

/**
 * Human-friendly path display: never shows raw "/storage/emulated/0".
 * Volume root -> volume label; nested -> "Internal storage › Download".
 */
object DisplayName {
    fun volumeRoots(): List<Pair<String, String>> {
        // (rootPath, label)
        val roots = mutableListOf(
            "/storage/emulated/0" to "Internal storage",
            "/sdcard" to "Internal storage",
        )
        File("/storage").listFiles()?.forEach { f ->
            if (f.isDirectory && f.absolutePath !in roots.map { it.first } &&
                !f.absolutePath.endsWith("/emulated") && f.name != "self") {
                roots += f.absolutePath to f.name
            }
        }
        return roots
    }

    fun rootOf(path: String): Pair<String, String>? =
        volumeRoots().filter { path == it.first || path.startsWith(it.first + "/") }
            .maxByOrNull { it.first.length }

    /** "Internal storage" or "Internal storage › Download/Foo". */
    fun pretty(path: String): String {
        val r = rootOf(path) ?: return path
        val rel = path.removePrefix(r.first).trim('/')
        return if (rel.isEmpty()) r.second else "${r.second} › $rel"
    }

    fun localized(context: Context, path: String): String {
        val r = rootOf(path) ?: return path
        val label = if (r.second == "Internal storage")
            context.getString(R.string.internal_storage) else r.second
        val rel = path.removePrefix(r.first).trim('/')
        return if (rel.isEmpty()) label else "$label › $rel"
    }
}
