package app.sorta.files.ui.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.MimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(nav: NavController, path: String) {
    var siblings by remember { mutableStateOf<List<File>>(listOf(File(path))) }
    LaunchedEffect(path) {
        siblings = withContext(Dispatchers.IO) {
            val f = File(path)
            f.parentFile?.listFiles()
                ?.filter { it.isFile && MimeUtil.categoryOf(it.name, false) == FileTypeCategory.IMAGE }
                ?.sortedBy { it.name } ?: listOf(f)
        }
    }
    val start = siblings.indexOfFirst { it.absolutePath == path }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start, pageCount = { siblings.size })

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(siblings.getOrNull(pager.currentPage)?.name ?: "") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                }
            },
        )
    }) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.padding(padding).fillMaxSize()) { page ->
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x; offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = siblings[page],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offsetX; translationY = offsetY
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextPreviewScreen(nav: NavController, path: String) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        text = withContext(Dispatchers.IO) {
            runCatching { File(path).readText(Charsets.UTF_8) }.getOrElse { it.message }
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(File(path).name) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                }
            },
        )
    }) { padding ->
        Text(
            text ?: "",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(nav: NavController, path: String) {
    var pageCount by remember { mutableStateOf(0) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                renderer = r
                pageCount = r.pageCount
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(File(path).name) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(pageCount) { i ->
                PdfPage(renderer, i)
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer?, index: Int) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(renderer, index) {
        withContext(Dispatchers.IO) {
            val r = renderer ?: return@withContext
            synchronized(r) {
                runCatching {
                    val page = r.openPage(index)
                    val w = page.width * 2
                    val h = page.height * 2
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(android.graphics.Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp = b
                }
            }
        }
    }
    bmp?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxWidth().padding(4.dp))
    }
}
