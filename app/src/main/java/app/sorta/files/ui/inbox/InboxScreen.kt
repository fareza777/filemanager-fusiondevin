package app.sorta.files.ui.inbox

import android.app.Application
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.SortaApp
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.scan.InboxFile
import app.sorta.files.core.scan.InboxScanner
import app.sorta.files.data.db.InboxState
import app.sorta.files.ui.FileActions
import app.sorta.files.ui.components.BottomActionBar
import app.sorta.files.ui.components.ConfirmDialog
import app.sorta.files.ui.components.EmptyState
import app.sorta.files.ui.components.FileRow
import app.sorta.files.ui.navigation.Dest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class InboxViewModel(app: Application) : AndroidViewModel(app) {
    val container get() = getApplication<SortaApp>().container
    private val _files = MutableStateFlow<List<InboxFile>>(emptyList())
    val files: StateFlow<List<InboxFile>> = _files
    val tab = MutableStateFlow(0) // 0=New 1=Tidied
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection
    private var anchor: Int? = null

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = container.db.inboxSourceDao().enabled()
            val acc = mutableListOf<InboxFile>()
            InboxScanner.scan(sources, container.db.inboxStateDao()).collect { batch ->
                acc += batch
                _files.value = acc.sortedByDescending { it.item.lastModified }
                container.inboxBadgeCount.value = acc.count { !it.tidied }
            }
        }
    }

    fun shown(): List<InboxFile> = _files.value.filter { it.tidied == (tab.value == 1) }

    fun toggle(p: String) {
        val s = _selection.value.toMutableSet()
        if (!s.add(p)) s.remove(p)
        _selection.value = s
    }

    fun longPress(item: InboxFile) {
        val list = shown()
        val idx = list.indexOfFirst { it.item.path == item.item.path }
        val a = anchor
        if (_selection.value.isNotEmpty() && a != null && idx >= 0) {
            val r = minOf(a, idx)..maxOf(a, idx)
            _selection.value = _selection.value + r.map { list[it].item.path }
        } else toggle(item.item.path)
        anchor = idx
    }

    fun selectAll() { _selection.value = shown().map { it.item.path }.toSet() }
    fun clearSel() { _selection.value = emptySet(); anchor = null }

    fun markTidied(paths: List<String>, tidied: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach {
                if (tidied) container.db.inboxStateDao().upsert(InboxState(it, "TIDIED"))
                else container.db.inboxStateDao().delete(it)
            }
            scan()
        }
    }

    fun markNewPaths(newPaths: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            newPaths.forEach { container.db.inboxStateDao().upsert(InboxState(it, "TIDIED")) }
        }
    }
}

private fun dayLabel(ctx: android.content.Context, ts: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
    cal.timeInMillis = ts
    val day = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
    return when {
        day == today -> ctx.getString(R.string.today)
        today.first - day.first == 1 && today.second == day.second -> ctx.getString(R.string.yesterday)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(nav: NavController, container: AppContainer) {
    val vm: InboxViewModel = viewModel()
    val context = LocalContext.current
    val files by vm.files.collectAsState()
    val tab by vm.tab.collectAsState()
    val selection by vm.selection.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var deleteConfirm by remember { mutableStateOf(false) }
    var tidySheet by remember { mutableStateOf<List<InboxFile>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var destPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.scan() }
    val movedToTrash = stringResource(R.string.deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    val lastTrashed by container.operations.lastTrashed.collectAsState()
    LaunchedEffect(lastTrashed) {
        val entries = lastTrashed ?: return@LaunchedEffect
        if (entries.isEmpty()) return@LaunchedEffect
        if (snackbar.showSnackbar(movedToTrash, undoLabel) == SnackbarResult.ActionPerformed)
            entries.forEach { container.trashManager.restore(it) }
        container.operations.clearLastTrashed()
        vm.scan()
    }
    LaunchedEffect(container.operations.lastResults.collectAsState().value) { vm.scan() }

    val shown = vm.shown()
    val inSelection = selection.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_inbox)) },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.inbox_manage)) },
                            onClick = { menu = false; nav.navigate(Dest.INBOX_SOURCES) })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rules_run)) },
                            onClick = { menu = false; nav.navigate(Dest.RULE_PREVIEW) })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rules_title)) },
                            onClick = { menu = false; nav.navigate(Dest.RULES) })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (inSelection) {
                val sel = shown.filter { it.item.path in selection }
                BottomActionBar(
                    single = sel.size == 1, canFavorite = false,
                    onCopy = { app.sorta.files.ui.basket.SelectionBasket.set(
                        sel.map { it.item }, OpType.COPY); vm.clearSel() },
                    onMove = { app.sorta.files.ui.basket.SelectionBasket.set(
                        sel.map { it.item }, OpType.MOVE); vm.clearSel() },
                    onRename = {},
                    onDelete = { deleteConfirm = true },
                    onShare = { FileActions.share(context, sel.map { it.item }) },
                    onBasket = {},
                    moreItems = buildList {
                        add(stringResource(R.string.inbox_tidy) to { tidySheet = sel })
                        if (tab == 0) add(stringResource(R.string.inbox_mark_tidied) to {
                            vm.markTidied(sel.map { it.item.path }); vm.clearSel()
                        })
                        else add(stringResource(R.string.inbox_unmark) to {
                            vm.markTidied(sel.map { it.item.path }, tidied = false); vm.clearSel()
                        })
                        add(stringResource(R.string.move_to_favorite) to { destPicker = true })
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedButton(selected = tab == 0, onClick = { vm.tab.value = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) {
                    Text(stringResource(R.string.inbox_new))
                }
                SegmentedButton(selected = tab == 1, onClick = { vm.tab.value = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) {
                    Text(stringResource(R.string.inbox_tidied))
                }
            }
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Inbox, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.inbox_empty_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.inbox_empty_tip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val grouped = shown.groupBy { dayLabel(context, it.item.lastModified) }
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (day, list) ->
                        item(key = "h$day") {
                            Text(day, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        items(list, key = { it.item.path }) { inf ->
                            FileRow(inf.item, inf.item.path in selection, inSelection,
                                onClick = {
                                    if (inSelection) vm.toggle(inf.item.path)
                                    else FileActions.open(context, nav, inf.item)
                                },
                                onLongClick = { vm.longPress(inf) },
                                subtitleOverride = stringResource(R.string.inbox_row_sub,
                                    FileSystem.formatSize(inf.item.size), inf.sourceLabel))
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        val sel = shown.filter { it.item.path in selection }
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, sel.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteConfirm = false; vm.clearSel()
                container.operations.run(OpType.DELETE, sel.map { File(it.item.path) }, null)
            },
            onDismiss = { deleteConfirm = false },
        )
    }

    var tidyDest by remember { mutableStateOf<String?>(null) }
    val lastResults by container.operations.lastResults.collectAsState()
    LaunchedEffect(lastResults, tidyDest) {
        val d = tidyDest ?: return@LaunchedEffect
        val res = lastResults ?: return@LaunchedEffect
        if (res.isEmpty()) { tidyDest = null; return@LaunchedEffect }
        val newPaths = res.mapNotNull { r ->
            if (r.status == app.sorta.files.core.ops.ItemStatus.SUCCESS) r.dest else null
        }
        container.db.recentLocationDao().upsert(
            app.sorta.files.data.db.RecentLocation(d, File(d).name))
        vm.markNewPaths(newPaths)
        vm.clearSel()
        tidyDest = null
        vm.scan()
    }

    tidySheet?.let { sel ->
        TidyFlowSheet(nav, container, sel,
            onDone = { tidied ->
                vm.markNewPaths(tidied)
                vm.clearSel()
                tidySheet = null
                vm.scan()
            },
            onDismiss = { tidySheet = null },
            onConfirmOp = { d ->
                // sheet dismissed first; op runs so the progress sheet is alone
                tidySheet = null
                tidyDest = d
                container.operations.run(OpType.MOVE,
                    sel.map { File(it.item.path) }, File(d))
            })
    }

    if (destPicker) {
        val sel = shown.filter { it.item.path in selection }
        FavoriteDestPicker(container,
            onPick = { dest ->
                destPicker = false
                vm.clearSel()
                container.operations.run(OpType.MOVE, sel.map { File(it.item.path) }, File(dest))
            },
            onDismiss = { destPicker = false })
    }
}
