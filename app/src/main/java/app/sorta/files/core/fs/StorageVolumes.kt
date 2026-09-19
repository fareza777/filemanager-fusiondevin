package app.sorta.files.core.fs

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class VolumeInfo(
    val path: String,
    val name: String,
    val isPrimary: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

object StorageVolumes {

    fun list(context: Context): List<VolumeInfo> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = sm.storageVolumes
            .filter { it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { toInfo(context, it) }
            .distinctBy { it.path }
        if (volumes.isEmpty()) {
            Environment.getExternalStorageDirectory()?.let { f ->
                return listOf(
                    VolumeInfo(f.absolutePath, "Internal storage", true, f.totalSpace, f.usableSpace)
                )
            }
        }
        return volumes.sortedByDescending { it.isPrimary }
    }

    private fun toInfo(context: Context, v: StorageVolume): VolumeInfo? {
        val dir: File = if (Build.VERSION.SDK_INT >= 30) {
            v.directory ?: return null
        } else {
            @Suppress("DEPRECATION")
            try {
                StorageVolume::class.java.getMethod("getPathFile").invoke(v) as? File
            } catch (_: Exception) { null } ?: return null
        }
        val name = v.getDescription(context) ?: dir.name
        return VolumeInfo(
            path = dir.absolutePath,
            name = name,
            isPrimary = v.isPrimary,
            totalBytes = dir.totalSpace,
            freeBytes = dir.usableSpace,
        )
    }
}
