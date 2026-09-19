package app.sorta.files.core.fs

import android.content.pm.PackageManager
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File

/** Coil fetcher returning the APK's own icon via PackageManager. */
class ApkIconFetcher(
    private val file: File,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val pm = options.context.packageManager
        val info = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                pm.getPackageArchiveInfo(file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0))
            else
                @Suppress("DEPRECATION") pm.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull() ?: return null
        info.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }
        val icon = info.applicationInfo?.loadIcon(pm) ?: return null
        return DrawableResult(icon, isSampled = false, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (MimeUtil.extension(data.name) == "apk") ApkIconFetcher(data, options) else null
    }
}
