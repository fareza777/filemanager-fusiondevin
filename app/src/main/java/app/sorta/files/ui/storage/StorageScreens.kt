package app.sorta.files.ui.storage

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.data.db.TrashEntry
import app.sorta.files.ui.components.ConfirmDialog
import app.sorta.files.ui.components.EmptyState
import app.sorta.files.ui.errorMessage
import app.sorta.files.ui.navigation.Dest
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(nav: NavController, container: AppContainer) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.tab_storage)) })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.TRASH) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.storage_trash), Modifier.padding(start = 12.dp))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
            }
            Card(Modifier.fillMaxWidth().clickable { nav.navigate(Dest.HISTORY) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.storage_history), Modifier.padding(start = 12.dp))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.placeholder_screen),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(nav: NavController, container: AppContainer) {
    val entries by container.db.trashDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var emptyConfirm by remember { mutableStateOf(false) }
    var permConfirm by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { emptyConfirm = true }) {
                            Text(stringResource(R.string.trash_empty_cta),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selection.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        scope.launch {
                            entries.filter { it.trashPath in selection }
                                .forEach { container.trashManager.restore(it) }
                            selection = emptySet()
                        }
                    }) { Text(stringResource(R.string.action_restore)) }
                    TextButton(onClick = { permConfirm = true }) {
                        Text(stringResource(R.string.trash_delete_perm),
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    ) { padding ->
        if (entries.isEmpty()) EmptyState(stringResource(R.string.trash_empty))
        else LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(entries, key = { it.trashPath }) { e ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            selection = if (e.trashPath in selection) selection - e.trashPath
                            else selection + e.trashPath
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = e.trashPath in selection,
                        onCheckedChange = {
                            selection = if (it) selection + e.trashPath else selection - e.trashPath
                        })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge)
                        Text(e.originalPath, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text("${fmt.format(Date(e.deletedAt))} · ${FileSystem.formatSize(e.size)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (emptyConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_cta),
            body = stringResource(R.string.trash_empty_confirm, entries.size),
            confirmLabel = stringResource(R.string.trash_delete_perm),
            onConfirm = {
                emptyConfirm = false
                scope.launch { entries.forEach { container.trashManager.purge(it) } }
            },
            onDismiss = { emptyConfirm = false },
        )
    }
    if (permConfirm) {
        val sel = entries.filter { it.trashPath in selection }
        ConfirmDialog(
            title = stringResource(R.string.trash_delete_perm),
            body = stringResource(R.string.trash_delete_perm_body, sel.size),
            confirmLabel = stringResource(R.string.trash_delete_perm),
            onConfirm = {
                permConfirm = false
                scope.launch { sel.forEach { container.trashManager.purge(it) } }
                selection = emptySet()
            },
            onDismiss = { permConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavController, container: AppContainer) {
    val entries by container.db.historyDao().observeRecent().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<Long?>(null) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.history_title)) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                }
            },
            actions = {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { container.db.historyDao().clear() } }) {
                        Text(stringResource(R.string.history_clear))
                    }
                }
            },
        )
    }) { padding ->
        if (entries.isEmpty()) EmptyState(stringResource(R.string.history_empty))
        else LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(entries, key = { it.id }) { e ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { expanded = if (expanded == e.id) null else e.id },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.opType, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(fmt.format(Date(e.at)), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            stringResource(R.string.items_count, e.itemCount) + " · " +
                                when {
                                    e.failed > 0 -> stringResource(R.string.history_failed, e.failed)
                                    e.skipped > 0 -> stringResource(R.string.history_skipped, e.skipped)
                                    else -> stringResource(R.string.history_all_ok)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.failed > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (expanded == e.id) {
                            Spacer(Modifier.height(6.dp))
                            e.detail.split("\n").forEach { line ->
                                val status = line.substringAfterLast(" : ").substringBefore(" ")
                                val err = line.substringAfter("(").substringBefore(")", "")
                                Text(
                                    line.substringBefore(" : "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (line.contains("FAILED"))
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                                if (line.contains("FAILED") && err.isNotEmpty()) {
                                    Text(errorMessage(err),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
