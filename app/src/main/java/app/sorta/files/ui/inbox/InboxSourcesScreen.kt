package app.sorta.files.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import app.sorta.files.data.db.InboxSource
import app.sorta.files.ui.components.FolderPickerDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxSourcesScreen(nav: NavController, container: AppContainer) {
    val sources by container.db.inboxSourceDao().observeAll()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(false) }
    val dao = container.db.inboxSourceDao()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.inbox_manage)) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                }
            },
            actions = {
                IconButton(onClick = { picker = true }) { Icon(Icons.Outlined.Add, null) }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(sources, key = { it.path }) { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.label)
                        Text(s.path, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = s.recursive,
                                onCheckedChange = { scope.launch { dao.upsert(s.copy(recursive = it)) } },
                            )
                            Text(stringResource(R.string.inbox_subfolders),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Switch(
                            checked = s.enabled,
                            onCheckedChange = { scope.launch { dao.upsert(s.copy(enabled = it)) } },
                        )
                        IconButton(onClick = { scope.launch { dao.delete(s.path) } }) {
                            Icon(Icons.Outlined.Delete, null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { picker = true }, modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.inbox_add_folder))
                }
            }
        }
    }

    if (picker) {
        FolderPickerDialog(container,
            onSelect = { path ->
                picker = false
                scope.launch {
                    dao.upsert(InboxSource(path, java.io.File(path).name.ifEmpty { path }))
                }
            },
            onDismiss = { picker = false })
    }
}
