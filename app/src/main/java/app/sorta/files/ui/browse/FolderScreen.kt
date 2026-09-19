package app.sorta.files.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.activity.compose.BackHandler
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
    var compressDialog by remember { mutableStateOf(false) }
    var zipSheet by remember { mutableStateOf<FileItem?>(null) }
    var destPicker by remember { mutableStateOf(false) }
    val permLost by container.operations.permissionLost.collectAsState()
    val extractPicker = remember { mutableStateOf<FileItem?>(null) }

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
    BackHandler(enabled = inSelection) { vm.clearSelection() }

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
                    if (permLost) {
                        TextButton(onClick = {
                            container.operations.clearPermissionLost()
                            (context as? android.app.Activity)?.let {
                                (it as? app.sorta.files.MainActivity)?.openAllFilesAccess()
                            }
                        }) {
                            Text(stringResource(R.string.perm_lost),
                                color = MaterialTheme.colorScheme.error, maxLines = 1)
                        }
                    }
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
                        onRename = {
                            if (sel.size > 1) {
                                nav.navigate(Dest.batchRename(sel.map { it.path }))
                                vm.clearSelection()
                            } else renameTarget = sel.first()
                        },
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
                            add(stringResource(R.string.action_compress) to { compressDialog = true })
                            add(stringResource(R.string.move_to_favorite) to { destPicker = true })
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
                    columns = GridCells.Fixed(
                        if (context.resources.configuration.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE) 5 else 3),
                    state = rememberLazyGridStateWrapper(listState),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    gridItems(items, key = { it.path }) { item ->
                        FileGridCell(item, item.path in selection, inSelection,
                            onClick = {
                                if (inSelection) vm.toggleSelect(item)
                                else openItem(context, nav, item) { zipSheet = it }
                            },
                            onLongClick = { vm.longPress(item) })
                    }
                }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.path }) { item ->
                        FileRow(item, item.path in selection, inSelection,
                            onClick = {
                                if (inSelection) vm.toggleSelect(item)
                                else openItem(context, nav, item) { zipSheet = it }
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

    if (compressDialog) {
        val sel = items.filter { it.path in selection }
        NameDialog(
            title = stringResource(R.string.zip_compress_title),
            initial = (sel.firstOrNull()?.name?.substringBeforeLast('.')
                ?: stringResource(R.string.zip_default_name)) + ".zip",
            confirmLabel = stringResource(R.string.action_compress),
            onConfirm = { name ->
                compressDialog = false
                vm.clearSelection()
                container.operations.runCompress(sel.map { File(it.path) },
                    File(path, if (name.endsWith(".zip")) name else "$name.zip"))
            },
            onDismiss = { compressDialog = false },
        )
    }

    zipSheet?.let { z ->
        ZipActionSheet(item = z, currentDir = path,
            onExtractHere = {
                container.operations.run(OpType.EXTRACT, listOf(File(z.path)), File(path),
                    app.sorta.files.core.ops.ConflictPolicy.KEEP_BOTH)
                zipSheet = null
            },
            onExtractNew = {
                val dir = File(path, z.name.removeSuffix(".zip"))
                container.operations.run(OpType.EXTRACT, listOf(File(z.path)), dir,
                    app.sorta.files.core.ops.ConflictPolicy.KEEP_BOTH)
                zipSheet = null
            },
            onExtractTo = { extractPicker.value = z; zipSheet = null },
            onOpenWith = { FileActions.openWith(context, z); zipSheet = null },
            onDismiss = { zipSheet = null })
    }
    extractPicker.value?.let { z ->
        app.sorta.files.ui.components.FolderPickerDialog(container,
            onSelect = { d ->
                extractPicker.value = null
                container.operations.run(OpType.EXTRACT, listOf(File(z.path)), File(d),
                    app.sorta.files.core.ops.ConflictPolicy.KEEP_BOTH)
            },
            onDismiss = { extractPicker.value = null })
    }

    if (destPicker) {
        val sel = items.filter { it.path in selection }
        app.sorta.files.ui.inbox.FavoriteDestPicker(container,
            onPick = { dest ->
                destPicker = false; vm.clearSelection()
                container.operations.run(OpType.MOVE, sel.map { File(it.path) }, File(dest))
            },
            onDismiss = { destPicker = false })
    }
}

/** Taps: zips get an action sheet; everything else goes through FileActions.open. */
private fun openItem(
    context: android.content.Context,
    nav: NavController,
    item: FileItem,
    onZip: (FileItem) -> Unit,
) {
    if (!item.isDir && app.sorta.files.core.zip.ZipExtractor.isZip(File(item.path))) onZip(item)
    else FileActions.open(context, nav, item)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZipActionSheet(
    item: FileItem,
    currentDir: String,
    onExtractHere: () -> Unit,
    onExtractNew: () -> Unit,
    onExtractTo: () -> Unit,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(6.dp))
            TextButton(onClick = onExtractHere, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.zip_extract_here))
            }
            TextButton(onClick = onExtractNew, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.zip_extract_to_new, item.name.removeSuffix(".zip")))
            }
            TextButton(onClick = onExtractTo, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.zip_extract_to))
            }
            TextButton(onClick = onOpenWith, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.zip_open_with))
            }
            Spacer(Modifier.padding(10.dp))
        }
    }
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
    // Strip the storage-root prefix; first crumb = volume label.
    val segments = remember(path) {
        val volPrefixes = listOf("/storage/emulated/0", "/sdcard") +
            (java.io.File("/storage").listFiles()?.map { it.absolutePath } ?: emptyList())
        val volRoot = volPrefixes.filter { path.startsWith(it) }
            .maxByOrNull { it.length }
        val label = when (volRoot) {
            null -> "/"
            "/sdcard", "/storage/emulated/0" -> null // resolve below
            else -> volRoot.substringAfterLast('/')
        }
        val parts = mutableListOf<Pair<String, String>>()
        val rel = if (volRoot != null) path.removePrefix(volRoot) else path
        var acc = volRoot ?: ""
        if (volRoot != null) {
            val volLabel = if (volRoot == "/storage/emulated/0" || volRoot == "/sdcard")
                "Internal storage" else volRoot.substringAfterLast('/')
            parts += volLabel to volRoot
        }
        rel.split('/').filter { it.isNotEmpty() }.forEach { seg ->
            acc = "$acc/$seg"
            parts += seg to acc
        }
        if (parts.isEmpty()) parts += "Internal storage" to path
        parts.takeLast(12)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { i, (label, p) ->
            if (i > 0) Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val isLast = i == segments.lastIndex
            TextButton(onClick = { if (!isLast) onNavigate(p) }, enabled = !isLast) {
                Text(label, maxLines = 1,
                    style = if (isLast) MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium)
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
