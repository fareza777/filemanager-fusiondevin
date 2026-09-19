package app.sorta.files.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import app.sorta.files.R
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.MimeUtil
import java.io.File

object FileActions {

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "app.sorta.files.fileprovider", file)

    fun open(context: Context, nav: NavController, item: FileItem) {
        val f = File(item.path)
        if (item.isDir) {
            nav.navigate(app.sorta.files.ui.navigation.Dest.folder(item.path))
            return
        }
        val cat = item.category
        when {
            cat == FileTypeCategory.IMAGE ->
                nav.navigate(app.sorta.files.ui.navigation.Dest.imagePreview(item.path))
            MimeUtil.extension(item.name) == "pdf" ->
                nav.navigate(app.sorta.files.ui.navigation.Dest.pdfPreview(item.path))
            MimeUtil.isTextLike(item.name, item.mime) && f.length() <= 2L * 1024 * 1024 ->
                nav.navigate(app.sorta.files.ui.navigation.Dest.textPreview(item.path))
            else -> openWith(context, item)
        }
    }

    fun openWith(context: Context, item: FileItem) {
        val uri = contentUri(context, File(item.path))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.open_with_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, items: List<FileItem>) {
        val uris = ArrayList(items.filter { !it.isDir }.map {
            contentUri(context, File(it.path))
        })
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items.first().mime ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.share_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { }
    }
}
