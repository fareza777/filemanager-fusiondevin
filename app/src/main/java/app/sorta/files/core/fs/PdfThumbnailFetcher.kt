package app.sorta.files.core.fs

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse
import androidx.core.graphics.drawable.toDrawable
import java.io.File

/** Coil fetcher rendering the first page of a PDF at the requested size. */
class PdfThumbnailFetcher(
    private val file: File,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (!file.isFile) return null
        val bmp = renderFirstPage(file,
            options.size.width.pxOrElse { 256 },
            options.size.height.pxOrElse { 256 }) ?: return null
        return DrawableResult(
            drawable = bmp.toDrawable(options.context.resources),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    companion object {
        fun renderFirstPage(file: File, reqW: Int, reqH: Int): Bitmap? = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val scale = minOf(reqW.toFloat() / page.width, reqH.toFloat() / page.height)
                .coerceAtLeast(0.1f)
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bmp
        }.getOrNull()
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (MimeUtil.extension(data.name) == "pdf") PdfThumbnailFetcher(data, options) else null
    }
}
