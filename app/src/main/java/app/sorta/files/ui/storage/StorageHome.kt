package app.sorta.files.ui.storage

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.SortaApp
import app.sorta.files.core.fs.DisplayName
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.fs.StorageVolumes
import app.sorta.files.core.fs.VolumeInfo
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.scan.DuplicateScan
import app.sorta.files.core.scan.DuplicateScanner
import app.sorta.files.core.scan.StorageUsage
import app.sorta.files.core.scan.StorageUsageSnapshot
import app.sorta.files.data.db.TrashEntry
import app.sorta.files.ui.FileActions
import app.sorta.files.ui.components.AdBanner
import app.sorta.files.ui.components.ConfirmDialog
import app.sorta.files.ui.components.EmptyState
import app.sorta.files.ui.components.FileRow
import app.sorta.files.ui.components.Thumbnail
import app.sorta.files.ui.navigation.Dest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

class StorageViewModel(app: Application) : AndroidViewModel(app) {
    val container get() = getApplication<SortaApp>().container
    private val _volumes = MutableStateFlow<List<VolumeInfo>>(emptyList())
    val volumes: StateFlow<List<VolumeInfo>> = _volumes
    private val _usage = MutableStateFlow<StorageUsageSnapshot?>(null)
    val usage: StateFlow<StorageUsageSnapshot?> = _usage
    private var job: Job? = null

    init {
        viewModelScope.launch {
            _volumes.value = StorageVolumes.list(app)
            // seed from persisted snapshot for instant paint, then rescan
            container.prefs.usageSnapshot.first()?.let { raw ->
                _volumes.value.firstOrNull()?.let { v ->
                    StorageUsage.deserialize(v.path, raw)?.let { _usage.value = it }
                }
            }
            refresh()
        }
    }

    fun refresh() {
        val root = _volumes.value.firstOrNull()?.let { File(it.path) } ?: return
        job?.cancel()
        job = viewModelScope.launch {
            try {
                StorageUsage.scan(root).collect { s ->
                    _usage.value = s
                    if (s.done) container.prefs.setUsageSnapshot(StorageUsage.serialize(s))
                }
            } catch (_: kotlinx.coroutines.CancellationException) { throw kotlinx.coroutines.CancellationException() }
            catch (_: Exception) {}
        }
    }
}

private fun categoryLabel(cat: FileTypeCategory): Int = when (cat) {
    FileTypeCategory.IMAGE -> R.string.cat_images
    FileTypeCategory.VIDEO -> R.string.cat_videos
    FileTypeCategory.AUDIO -> R.string.cat_audio
    FileTypeCategory.DOCUMENT -> R.string.cat_documents
    FileTypeCategory.APK -> R.string.cat_apks
    FileTypeCategory.ARCHIVE -> R.string.cat_archives
    else -> R.string.category_other
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(nav: NavController, container: AppContainer) {
    val vm: StorageViewModel = viewModel()
    val context = LocalContext.current
    val volumes by vm.volumes.collectAsState()
    val usage by vm.usage.collectAsState()
    val trash by container.db.trashDao().observeAll().collectAsState(initial = emptyList())
    val trashSize = trash.sumOf { it.size }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_storage)) },
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.storage_refresh))
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            volumes.forEach { v ->
                item(key = v.path) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(DisplayName.localized(context, v.path),
                                        style = MaterialTheme.typography.titleMedium)
                                    Text("${FileSystem.formatSize(v.usedBytes)} / ${FileSystem.formatSize(v.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (usage?.done == false) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.storage_scanning),
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { v.usedFraction },
                                modifier = Modifier.fillMaxWidth())
                            usage?.let { u ->
                                Spacer(Modifier.height(10.dp))
                                // segmented bar per category
                                if (u.totalBytes > 0) {
                                    Row(Modifier.fillMaxWidth().height(10.dp)) {
                                        u.perCategory.entries.sortedByDescending { it.value }
                                            .forEach { (cat, bytes) ->
                                                val w = bytes.toFloat() / u.totalBytes
                                                if (w > 0.005f) Box(
                                                    Modifier.weight(w).height(10.dp)
                                                        .padding(end = 1.dp)
                                                        .background(cat.tint))
                                            }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                u.perCategory.entries.sortedByDescending { it.value }
                                    .filter { it.value > 0 }
                                    .forEach { (cat, bytes) ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Icon(cat.icon, null, tint = cat.tint,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(categoryLabel(cat)),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f))
                                            Text(FileSystem.formatSize(bytes),
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                            }
                        }
                    }
                }
            }
            item { AdBanner() }
            item {
                LinkCard(Icons.Outlined.Delete, stringResource(R.string.storage_large_files),
                    stringResource(R.string.storage_large_sub)) { nav.navigate(Dest.LARGE_FILES) }
            }
            item {
                LinkCard(Icons.Outlined.Delete, stringResource(R.string.storage_duplicates),
                    stringResource(R.string.storage_duplicates)) { nav.navigate(Dest.DUPLICATES) }
            }
            item {
                LinkCard(Icons.Outlined.Delete, stringResource(R.string.storage_trash),
                    stringResource(R.string.storage_trash_sub, trash.size,
                        FileSystem.formatSize(trashSize))) { nav.navigate(Dest.TRASH) }
            }
            item {
                LinkCard(Icons.Outlined.History, stringResource(R.string.storage_history),
                    "") { nav.navigate(Dest.HISTORY) }
            }
            usage?.emptyFolders?.takeIf { it.isNotEmpty() }?.let { ef ->
                item {
                    Text(stringResource(R.string.storage_empty_folders) + " (${ef.size})",
                        style = MaterialTheme.typography.titleSmall)
                    ef.take(10).forEach { p ->
                        Text(DisplayName.localized(context, p),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCard(icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title)
                if (subtitle.isNotEmpty()) Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

// ---------------------------------------------------------------- Large files

class LargeFilesViewModel(app: Application) : AndroidViewModel(app) {
    val container get() = getApplication<SortaApp>().container
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files
    val scanning = MutableStateFlow(true)
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection

    init {
        viewModelScope.launch {
            val vols = StorageVolumes.list(app)
            vols.firstOrNull()?.let { v ->
                try {
                    StorageUsage.scan(File(v.path)).collect { s ->
                        _files.value = s.largeFiles
                        scanning.value = !s.done
                    }
                } catch (_: Exception) {}
            }
            scanning.value = false
        }
    }

    fun toggle(p: String) {
        val s = _selection.value.toMutableSet()
        if (!s.add(p)) s.remove(p)
        _selection.value = s
    }
    fun clearSel() { _selection.value = emptySet() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFilesScreen(nav: NavController, container: AppContainer) {
    val vm: LargeFilesViewModel = viewModel()
    val context = LocalContext.current
    val files by vm.files.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val selection by vm.selection.collectAsState()
    var deleteConfirm by remember { mutableStateOf(false) }
    var movePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.large_files_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(20.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp)
                    }
                },
            )
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { deleteConfirm = true }) {
                        Text(stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { movePicker = true }) {
                        Text(stringResource(R.string.action_move))
                    }
                    TextButton(onClick = {
                        FileActions.share(context, files.filter { it.path in selection })
                    }) { Text(stringResource(R.string.action_share)) }
                }
            }
        },
    ) { padding ->
        if (files.isEmpty() && !scanning)
            EmptyState(stringResource(R.string.empty_results))
        else LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(files, key = { it.path }) { item ->
                FileRow(item, item.path in selection, selection.isNotEmpty(),
                    onClick = {
                        if (selection.isNotEmpty()) vm.toggle(item.path)
                        else FileActions.open(context, nav, item)
                    },
                    onLongClick = { vm.toggle(item.path) })
            }
        }
    }

    if (deleteConfirm) {
        val sel = files.filter { it.path in selection }
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, sel.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteConfirm = false; vm.clearSel()
                container.operations.run(OpType.DELETE, sel.map { File(it.path) }, null)
            },
            onDismiss = { deleteConfirm = false },
        )
    }
    if (movePicker) {
        val sel = files.filter { it.path in selection }
        app.sorta.files.ui.components.FolderPickerDialog(container,
            onSelect = { d ->
                movePicker = false; vm.clearSel()
                container.operations.run(OpType.MOVE, sel.map { File(it.path) }, File(d))
            },
            onDismiss = { movePicker = false })
    }
}

// ---------------------------------------------------------------- Duplicates

class DuplicatesViewModel(app: Application) : AndroidViewModel(app) {
    val container get() = getApplication<SortaApp>().container
    private val _scan = MutableStateFlow(DuplicateScan(emptyList(), 0, false))
    val scan: StateFlow<DuplicateScan> = _scan
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection
    private var job: Job? = null

    init { rescan() }

    fun rescan() {
        job?.cancel()
        job = viewModelScope.launch {
            val vols = StorageVolumes.list(getApplication())
            vols.firstOrNull()?.let { v ->
                try { DuplicateScanner.scan(File(v.path)).collect { _scan.value = it } }
                catch (_: Exception) {}
            }
        }
    }

    fun toggle(p: String) {
        val s = _selection.value.toMutableSet()
        if (!s.add(p)) s.remove(p)
        _selection.value = s
    }

    /** Select all but the newest (first item, groups are sorted newest-first). */
    fun keepNewest(group: List<FileItem>) {
        _selection.value = _selection.value - group.map { it.path }.toSet() +
            group.drop(1).map { it.path }
    }

    fun clearSel() { _selection.value = emptySet() }

    override fun onCleared() { job?.cancel() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(nav: NavController, container: AppContainer) {
    val vm: DuplicatesViewModel = viewModel()
    val scan by vm.scan.collectAsState()
    val selection by vm.selection.collectAsState()
    var deleteConfirm by remember { mutableStateOf(false) }

    val wasted = scan.groups.sumOf { g -> g.drop(1).sumOf { it.size } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dups_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    if (!scan.done) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.storage_scanning_dups),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text(stringResource(R.string.items_count, selection.size),
                        modifier = Modifier.align(Alignment.CenterVertically))
                    TextButton(onClick = { deleteConfirm = true }) {
                        Text(stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { vm.clearSel() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        },
    ) { padding ->
        if (scan.groups.isEmpty() && scan.done) {
            EmptyState(stringResource(R.string.storage_duplicates_none))
        } else LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Text(stringResource(R.string.storage_duplicates_sub, scan.groups.size,
                    FileSystem.formatSize(wasted)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            scan.groups.forEachIndexed { gi, group ->
                item(key = "g$gi") {
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.dups_group, group.size,
                                    FileSystem.formatSize(group.first().size)),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.keepNewest(group) }) {
                                    Text(stringResource(R.string.dups_keep_newest))
                                }
                            }
                            group.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { vm.toggle(item.path) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = item.path in selection,
                                        onCheckedChange = { vm.toggle(item.path) })
                                    Thumbnail(item, Modifier.size(40.dp))
                                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1)
                                        Text(DisplayName.localized(
                                            LocalContext.current, item.path),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        val sel = scan.groups.flatten().filter { it.path in selection }
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, sel.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteConfirm = false; vm.clearSel()
                container.operations.run(OpType.DELETE, sel.map { File(it.path) }, null)
            },
            onDismiss = { deleteConfirm = false },
        )
    }
}
