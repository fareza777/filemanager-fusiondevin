package app.sorta.files.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.ui.navigation.Dest

private data class CatEntry(val cat: FileTypeCategory, val labelRes: Int)

private val cats = listOf(
    CatEntry(FileTypeCategory.IMAGE, R.string.cat_images),
    CatEntry(FileTypeCategory.VIDEO, R.string.cat_videos),
    CatEntry(FileTypeCategory.AUDIO, R.string.cat_audio),
    CatEntry(FileTypeCategory.DOCUMENT, R.string.cat_documents),
    CatEntry(FileTypeCategory.APK, R.string.cat_apks),
    CatEntry(FileTypeCategory.ARCHIVE, R.string.cat_archives),
)

@Composable
fun BrowseRootScreen(nav: NavController, container: AppContainer) {
    val vm: BrowseViewModel = viewModel()
    val volumes by vm.volumes.collectAsState()
    val downloads = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.tab_browse), style = MaterialTheme.typography.headlineSmall)
        }
        items(volumes.size) { i ->
            val v = volumes[i]
            Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.folder(v.path)) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium)
                    Text(v.path, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { v.usedFraction },
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${FileSystem.formatSize(v.usedBytes)} / ${FileSystem.formatSize(v.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.browse_categories), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(180.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cats) { c ->
                    Card(Modifier.clickable { nav.navigate(Dest.category(c.cat.name)) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(c.cat.icon, null, tint = c.cat.tint)
                            Text(stringResource(c.labelRes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.folder(downloads)) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(FileTypeCategory.OTHER.icon, null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(6.dp))
                    Text(stringResource(R.string.cat_downloads), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
