package app.sorta.files.ui.home

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.SortaApp
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.StorageVolumes
import app.sorta.files.core.fs.VolumeInfo
import app.sorta.files.core.scan.RecentFilesScanner
import app.sorta.files.data.db.FavoriteFolder
import app.sorta.files.data.db.RecentLocation
import app.sorta.files.ui.FileActions
import app.sorta.files.ui.components.AdBanner
import app.sorta.files.ui.components.FileRow
import app.sorta.files.ui.navigation.Dest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val container get() = getApplication<SortaApp>().container

    private val _volumes = MutableStateFlow<List<VolumeInfo>>(emptyList())
    val volumes: StateFlow<List<VolumeInfo>> = _volumes

    private val _recent = MutableStateFlow<List<FileItem>>(emptyList())
    val recent: StateFlow<List<FileItem>> = _recent

    val favorites: StateFlow<List<FavoriteFolder>> =
        container.db.favoriteFolderDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val locations: StateFlow<List<RecentLocation>> =
        container.db.recentLocationDao().observeRecent(5)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _volumes.value = StorageVolumes.list(getApplication())
            _recent.value = RecentFilesScanner.recent(getApplication(), 7, 40)
        }
    }

    fun removeFavorite(path: String) {
        viewModelScope.launch { container.db.favoriteFolderDao().delete(path) }
    }
}

@Composable
fun HomeScreen(nav: NavController, container: AppContainer) {
    val vm: HomeViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    val volumes by vm.volumes.collectAsState()
    val recent by vm.recent.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val locations by vm.locations.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.SEARCH) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null)
                    Text(stringResource(R.string.home_search_hint),
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        volumes.firstOrNull()?.let { v ->
            item {
                Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.STORAGE) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.home_storage),
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { v.usedFraction },
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${FileSystem.formatSize(v.usedBytes)} / ${FileSystem.formatSize(v.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { AdBanner() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_recent),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.navigate(Dest.RECENT) }) {
                    Text(stringResource(R.string.home_see_all))
                }
            }
        }
        val shown = recent.take(10)
        if (shown.isEmpty()) {
            item { Text(stringResource(R.string.empty_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(shown.size) { i ->
                val item = shown[i]
                FileRow(item, false, false,
                    onClick = { FileActions.open(context, nav, item) },
                    onLongClick = { })
            }
        }
        item {
            Text(stringResource(R.string.home_favorites), style = MaterialTheme.typography.titleMedium)
            if (favorites.isEmpty()) {
                Text(stringResource(R.string.home_empty_favorites),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(favorites.size) { i ->
            val f = favorites[i]
            Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.folder(f.path)) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.label, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.removeFavorite(f.path) }) {
                        Text(stringResource(R.string.action_remove))
                    }
                }
            }
        }
        if (locations.isNotEmpty()) {
            item {
                Text(stringResource(R.string.home_locations), style = MaterialTheme.typography.titleMedium)
            }
            items(locations.size) { i ->
                val l = locations[i]
                Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.folder(l.path)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(l.label)
                        Text(l.path, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
