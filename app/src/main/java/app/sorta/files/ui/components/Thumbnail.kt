package app.sorta.files.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.MimeUtil
import java.io.File

private fun thumbnailable(item: FileItem): Boolean =
    !item.isDir && (item.category == FileTypeCategory.IMAGE ||
        item.category == FileTypeCategory.VIDEO ||
        item.category == FileTypeCategory.APK ||
        MimeUtil.extension(item.name) == "pdf")

@Composable
fun Thumbnail(
    item: FileItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (!thumbnailable(item)) {
        FileIcon(item, modifier, contentScale)
        return
    }
    val fallback: @Composable () -> Unit = {
        Icon(item.category.icon, contentDescription = null,
            tint = item.category.tint.copy(alpha = 0.9f),
            modifier = Modifier.size(24.dp))
    }
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(item.category.tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(item.path))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}
