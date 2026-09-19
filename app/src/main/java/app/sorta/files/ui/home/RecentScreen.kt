package app.sorta.files.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileTypeCategory
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.scan.RecentFilesScanner
import app.sorta.files.ui.FileActions
import app.sorta.files.ui.components.BottomActionBar
import app.sorta.files.ui.components.ConfirmDialog
import app.sorta.files.ui.components.EmptyState
import app.sorta.files.ui.components.FileRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecentViewModel(app: android.app.Application) : ViewModel() {
    private val ctx = app.applicationContext
    private val _items = MutableStateFlow<List<FileItem>>(emptyList())
    val items: StateFlow<List<FileItem>> = _items
    val filter = MutableStateFlow<FileTypeCategory?>(null)
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection
    private var anchor: Int? = null

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = RecentFilesScanner.recent(ctx, 7, 500)
        }
    }

    fun filtered(): List<FileItem> =
        filter.value?.let { f -> _items.value.filter { it.category == f } } ?: _items.value

    fun toggle(p: String) {
        val s = _selection.value.toMutableSet()
        if (!s.add(p)) s.remove(p)
        _selection.value = s
    }

    fun longPress(item: FileItem) {
        val list = filtered()
        val idx = list.indexOfFirst { it.path == item.path }
        val a = anchor
        if (_selection.value.isNotEmpty() && a != null && idx >= 0) {
            val r = minOf(a, idx)..maxOf(a, idx)
            _selection.value = _selection.value + r.map { list[it].path }
        } else toggle(item.path)
        anchor = idx
    }

    fun clear() { _selection.value = emptySet(); anchor = null }
}

private fun dayLabel(context: android.content.Context, ts: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
    cal.timeInMillis = ts
    val day = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
    return when {
        day == today -> context.getString(R.string.today)
        today.first - day.first == 1 && today.second == day.second -> context.getString(R.string.yesterday)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(nav: NavController, container: AppContainer) {
    val vm: RecentViewModel = viewModel()
    val context = LocalContext.current
    val items by vm.items.collectAsState()
    val filter by vm.filter.collectAsState()
    val selection by vm.selection.collectAsState()
    val lastTrashed by container.operations.lastTrashed.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var deleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }
    val movedToTrash = stringResource(R.string.deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(lastTrashed) {
        val entries = lastTrashed ?: return@LaunchedEffect
        if (entries.isEmpty()) return@LaunchedEffect
        if (snackbar.showSnackbar(movedToTrash, undoLabel) == SnackbarResult.ActionPerformed) {
            entries.forEach { container.trashManager.restore(it) }
        }
        container.operations.clearLastTrashed()
        vm.load()
    }

    val shown = vm.filtered()
    val cats = listOf(
        null to R.string.filter_type,
        FileTypeCategory.IMAGE to R.string.cat_images,
        FileTypeCategory.VIDEO to R.string.cat_videos,
        FileTypeCategory.AUDIO to R.string.cat_audio,
        FileTypeCategory.DOCUMENT to R.string.cat_documents,
        FileTypeCategory.APK to R.string.cat_apks,
    )
    val inSelection = selection.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recent_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (inSelection) {
                val sel = shown.filter { it.path in selection }
                BottomActionBar(
                    single = sel.size == 1, canFavorite = false,
                    onCopy = {}, onMove = {}, onRename = {},
                    onDelete = { deleteConfirm = true },
                    onShare = { FileActions.share(context, sel) },
                    onBasket = {},
                    moreItems = emptyList(),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                cats.forEach { (c, label) ->
                    FilterChip(
                        selected = filter == c,
                        onClick = { vm.filter.value = c },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            if (shown.isEmpty()) EmptyState(stringResource(R.string.empty_results))
            else {
                // group by day
                val grouped = shown.groupBy { dayLabel(context, it.lastModified) }
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (day, list) ->
                        item(key = "h_$day") {
                            Text(day, style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        items(list, key = { it.path }) { item ->
                            FileRow(item, item.path in selection, inSelection,
                                onClick = {
                                    if (inSelection) vm.toggle(item.path)
                                    else FileActions.open(context, nav, item)
                                },
                                onLongClick = { vm.longPress(item) })
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        val sel = shown.filter { it.path in selection }
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, sel.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteConfirm = false; vm.clear()
                container.operations.run(OpType.DELETE, sel.map { File(it.path) }, null)
            },
            onDismiss = { deleteConfirm = false },
        )
    }
}
