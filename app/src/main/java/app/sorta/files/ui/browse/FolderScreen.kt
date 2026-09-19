package app.sorta.files.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.RestrictedPaths
import app.sorta.files.core.fs.SortField
import app.sorta.files.core.fs.SortSpec
import app.sorta.files.core.ops.OpType
import app.sorta.files.ui.FileActions
import app.sorta.files.ui.basket.SelectionBasket
import app.sorta.files.ui.components.BottomActionBar
import app.sorta.files.ui.components.ConfirmDialog
import app.sorta.files.ui.components.DetailsDialog
import app.sorta.files.ui.components.EmptyState
import app.sorta.files.ui.components.FileGridCell
import app.sorta.files.ui.components.FileRow
import app.sorta.files.ui.components.NameDialog
import app.sorta.files.ui.components.PasteBar
import app.sorta.files.ui.components.SelectionTopBar
import app.sorta.files.ui.navigation.Dest
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(nav: NavController, container: AppContainer, path: String) {
    val vm: BrowseViewModel = viewModel()
    val context = LocalContext.current
    val items by vm.items.collectAsState()
    val selection by vm.selection.collectAsState()
    val sort by vm.sort.collectAsState()
    val grid by vm.grid.collectAsState()
    val showHidden by vm.showHidden.collectAsState()
    val restricted by vm.restricted.collectAsState()
    val basket by SelectionBasket.items.collectAsState()
    val lastTrashed by container.operations.lastTrashed.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    var showSortMenu by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var detailsItem by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(path) { vm.loadFolder(path) }

    // restore scroll
    LaunchedEffect(items.isNotEmpty()) {
        vm.restoredScroll?.let { (i, o) ->
            if (items.isNotEmpty()) listState.scrollToItem(i, o)
        }
        vm.restoredScroll = null
    }
    // persist scroll
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (i, o) -> vm.saveScroll(i, o) }
    }

    // undo snackbar after trash
    val movedToTrash = stringResource(R.string.deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(lastTrashed) {
        val entries = lastTrashed ?: return@LaunchedEffect
        if (entries.isEmpty()) return@LaunchedEffect
        val res = snackbar.showSnackbar(movedToTrash, undoLabel)
        if (res == SnackbarResult.ActionPerformed) {
            entries.forEach { container.trashManager.restore(it) }
        }
        container.operations.clearLastTrashed()
        vm.reload()
    }
    LaunchedEffect(container.operations.lastResults.collectAsState().value) { vm.reload() }

    val inSelection = selection.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (inSelection) SelectionTopBarInline(selection.size, vm::clearSelection, vm::selectAll)
                    else Breadcrumbs(path) { nav.navigate(Dest.folder(it)) { popUpTo(Dest.BROWSE) } }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Outlined.Sort, null) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf(
                            SortField.NAME to R.string.sort_name,
                            SortField.DATE to R.string.sort_date,
                            SortField.SIZE to R.string.sort_size,
                            SortField.TYPE to R.string.sort_type,
                        ).forEach { (f, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label) +
                                    if (sort.field == f) " ✓" else "") },
                                onClick = {
                                    showSortMenu = false
                                    vm.setSort(
                                        if (sort.field == f) sort.copy(ascending = !sort.ascending)
                                        else SortSpec(f, true)
                                    )
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.show_hidden) +
                                if (showHidden) " ✓" else "") },
                            onClick = { showSortMenu = false; vm.setShowHidden(!showHidden) },
                        )
                    }
                    IconButton(onClick = { vm.setGrid(!grid) }) {
                        Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, null)
                    }
                },
            )
        },
        floatingActionButton = {
            if (!inSelection) FloatingActionButton(onClick = { newFolderDialog = true }) {
                Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.action_new_folder))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (basket.isNotEmpty()) {
                    PasteBar(basket.size,
                        onPaste = {
                            val op = if (basket.first().op == OpType.MOVE) OpType.MOVE else OpType.COPY
                            container.operations.run(
                                op, basket.map { File(it.item.path) }, File(path))
                            SelectionBasket.clear()
                        },
                        onClear = { SelectionBasket.clear() })
                }
                if (inSelection) {
                    val sel = items.filter { it.path in selection }
                    BottomActionBar(
                        single = sel.size == 1,
                        canFavorite = sel.size == 1 && sel.first().isDir,
                        onCopy = { SelectionBasket.set(sel, OpType.COPY); vm.clearSelection() },
                        onMove = { SelectionBasket.set(sel, OpType.MOVE); vm.clearSelection() },
                        onRename = { renameTarget = sel.first() },
                        onDelete = { deleteConfirm = true },
                        onShare = { FileActions.share(context, sel) },
                        onBasket = { SelectionBasket.add(sel, OpType.COPY); vm.clearSelection() },
                        moreItems = buildList {
                            add(stringResource(R.string.action_open_with) to {
                                if (sel.size == 1) FileActions.openWith(context, sel.first())
                            })
                            add(stringResource(R.string.action_details) to {
                                if (sel.size == 1) detailsItem = sel.first()
                            })
                            if (sel.size == 1 && sel.first().isDir) {
                                add(stringResource(R.string.action_add_favorite) to {
                                    vm.addFavorite(sel.first()); vm.clearSelection()
                                })
                            }
                            add(stringResource(R.string.action_compress) + " (soon)" to {})
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                restricted -> RestrictedInfo()
                items.isEmpty() -> EmptyState(stringResource(R.string.empty_folder))
                grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = rememberLazyGridStateWrapper(listState),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    gridItems(items, key = { it.path }) { item ->
                        FileGridCell(item, item.path in selection, inSelection,
                            onClick = {
                                if (inSelection) vm.toggleSelect(item)
                                else FileActions.open(context, nav, item)
                            },
                            onLongClick = { vm.longPress(item) })
                    }
                }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.path }) { item ->
                        FileRow(item, item.path in selection, inSelection,
                            onClick = {
                                if (inSelection) vm.toggleSelect(item)
                                else FileActions.open(context, nav, item)
                            },
                            onLongClick = { vm.longPress(item) })
                    }
                }
            }
        }
    }

    if (newFolderDialog) {
        NameDialog(
            title = stringResource(R.string.dialog_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.dialog_create),
            onConfirm = { name ->
                newFolderDialog = false
                container.operations.run(OpType.NEW_FOLDER,
                    listOf(File(path, name)), File(path))
            },
            onDismiss = { newFolderDialog = false },
        )
    }
    renameTarget?.let { t ->
        NameDialog(
            title = stringResource(R.string.dialog_rename),
            initial = t.name,
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { name ->
                renameTarget = null
                vm.clearSelection()
                container.operations.run(OpType.RENAME, listOf(File(t.path)), null,
                    renameTarget = name)
            },
            onDismiss = { renameTarget = null },
        )
    }
    if (deleteConfirm) {
        val sel = items.filter { it.path in selection }
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, sel.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteConfirm = false
                vm.clearSelection()
                container.operations.run(OpType.DELETE, sel.map { File(it.path) }, null)
            },
            onDismiss = { deleteConfirm = false },
        )
    }
    detailsItem?.let { DetailsDialog(it, onDismiss = { detailsItem = null }) }
}

@Composable
private fun SelectionTopBarInline(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClose) { Text("✕") }
        Text("$count", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.select_all)) }
    }
}

@Composable
private fun Breadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val segments = remember(path) {
        val parts = mutableListOf<Pair<String, String>>() // label to full path
        var cur = File(path)
        while (true) {
            parts.add(0, (cur.name.ifEmpty { "/" }) to cur.absolutePath)
            val p = cur.parentFile ?: break
            if (p.absolutePath == cur.absolutePath) break
            cur = p
            if (parts.size > 12) break
        }
        parts
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { i, (label, p) ->
            if (i > 0) Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onNavigate(p) }) {
                Text(if (label == "0" && p.contains("emulated")) "Internal" else label,
                    maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RestrictedInfo() {
    Column(Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.restricted_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.restricted_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun rememberLazyGridStateWrapper(listState: androidx.compose.foundation.lazy.LazyListState) =
    androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = listState.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
    )
