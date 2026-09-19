package app.sorta.files.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.sorta.files.ui.basket.SelectionBasket
import app.sorta.files.ui.components.BottomActionBar
import app.sorta.files.ui.components.ConfirmDialog
import app.sorta.files.ui.components.EmptyState
import app.sorta.files.ui.components.FileRow
import app.sorta.files.ui.components.NameDialog
import app.sorta.files.ui.components.PasteBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class CategoryViewModel(app: android.app.Application) : ViewModel() {
    private val ctx = app.applicationContext
    private val _items = MutableStateFlow<List<FileItem>>(emptyList())
    val items: StateFlow<List<FileItem>> = _items
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection
    private var anchor: Int? = null

    fun load(cat: FileTypeCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = RecentFilesScanner.byCategory(ctx, cat)
        }
    }

    fun toggle(p: String) {
        val s = _selection.value.toMutableSet()
        if (!s.add(p)) s.remove(p)
        _selection.value = s
    }

    fun longPress(item: FileItem) {
        val idx = _items.value.indexOfFirst { it.path == item.path }
        val a = anchor
        if (_selection.value.isNotEmpty() && a != null && idx >= 0) {
            val r = minOf(a, idx)..maxOf(a, idx)
            _selection.value = _selection.value + r.map { _items.value[it].path }
        } else toggle(item.path)
        anchor = idx
    }

    fun selectAll() { _selection.value = _items.value.map { it.path }.toSet() }
    fun clear() { _selection.value = emptySet(); anchor = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(nav: NavController, container: AppContainer, catName: String) {
    val cat = runCatching { FileTypeCategory.valueOf(catName) }.getOrNull() ?: return
    val vm: CategoryViewModel = viewModel()
    val context = LocalContext.current
    val items by vm.items.collectAsState()
    val selection by vm.selection.collectAsState()
    val basket by SelectionBasket.items.collectAsState()
    val lastTrashed by container.operations.lastTrashed.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var deleteConfirm by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }

    val labelRes = when (cat) {
        FileTypeCategory.IMAGE -> R.string.cat_images
        FileTypeCategory.VIDEO -> R.string.cat_videos
        FileTypeCategory.AUDIO -> R.string.cat_audio
        FileTypeCategory.DOCUMENT -> R.string.cat_documents
        FileTypeCategory.APK -> R.string.cat_apks
        FileTypeCategory.ARCHIVE -> R.string.cat_archives
        else -> R.string.browse_categories
    }

    LaunchedEffect(cat) { vm.load(cat) }
    val movedToTrash = stringResource(R.string.deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(lastTrashed) {
        val entries = lastTrashed ?: return@LaunchedEffect
        if (entries.isEmpty()) return@LaunchedEffect
        val res = snackbar.showSnackbar(movedToTrash, undoLabel)
        if (res == SnackbarResult.ActionPerformed) entries.forEach { container.trashManager.restore(it) }
        container.operations.clearLastTrashed()
        vm.load(cat)
    }
    LaunchedEffect(container.operations.lastResults.collectAsState().value) { vm.load(cat) }

    val inSelection = selection.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(labelRes)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (basket.isNotEmpty()) {
                    PasteBar(basket.size,
                        onPaste = {
                            val op = if (basket.first().op == OpType.MOVE) OpType.MOVE else OpType.COPY
                            container.operations.run(op,
                                basket.map { File(it.item.path) },
                                File(items.firstOrNull()?.path ?: "/").parentFile ?: File("/"))
                            SelectionBasket.clear()
                        },
                        onClear = { SelectionBasket.clear() })
                }
                if (inSelection) {
                    val sel = items.filter { it.path in selection }
                    BottomActionBar(
                        single = sel.size == 1,
                        canFavorite = false,
                        onCopy = { SelectionBasket.set(sel, OpType.COPY); vm.clear() },
                        onMove = { SelectionBasket.set(sel, OpType.MOVE); vm.clear() },
                        onRename = { renameTarget = sel.first() },
                        onDelete = { deleteConfirm = true },
                        onShare = { FileActions.share(context, sel) },
                        onBasket = { SelectionBasket.add(sel, OpType.COPY); vm.clear() },
                        moreItems = listOf(
                            stringResource(R.string.action_open_with) to {
                                if (sel.size == 1) FileActions.openWith(context, sel.first())
                            },
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (items.isEmpty()) EmptyState(stringResource(R.string.empty_results))
            else LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.path }) { item ->
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

    if (deleteConfirm) {
        val sel = items.filter { it.path in selection }
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, sel.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteConfirm = false
                vm.clear()
                container.operations.run(OpType.DELETE, sel.map { File(it.path) }, null)
            },
            onDismiss = { deleteConfirm = false },
        )
    }
    renameTarget?.let { t ->
        NameDialog(
            title = stringResource(R.string.dialog_rename),
            initial = t.name,
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { name ->
                renameTarget = null; vm.clear()
                container.operations.run(OpType.RENAME, listOf(File(t.path)), null, renameTarget = name)
            },
            onDismiss = { renameTarget = null },
        )
    }
}
