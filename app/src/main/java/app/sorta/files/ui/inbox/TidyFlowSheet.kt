package app.sorta.files.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.core.fs.DisplayName
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.rename.BatchRenamePattern
import app.sorta.files.core.scan.InboxFile
import app.sorta.files.ui.components.FolderPickerDialog
import app.sorta.files.ui.components.Thumbnail
import java.io.File

/** Steps: 0=preview/rename, 1=destination, 2=confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidyFlowSheet(
    nav: NavController,
    container: AppContainer,
    files: List<InboxFile>,
    onDone: (List<String>) -> Unit, // new paths marked tidied
    onDismiss: () -> Unit,
    onConfirmOp: (String) -> Unit = {}, // dest path chosen; parent starts the op
) {
    var step by remember { mutableStateOf(0) }
    var dest by remember { mutableStateOf<String?>(null) }
    var singleName by remember(files) {
        mutableStateOf(files.singleOrNull()?.item?.name ?: "")
    }
    var pattern by remember { mutableStateOf("{name}{ext}") }
    var browsePicker by remember { mutableStateOf(false) }

    val favorites by container.db.favoriteFolderDao().observeAll()
        .collectAsState(initial = emptyList())
    val recentDests by container.db.recentLocationDao().observeRecent(5)
        .collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            // stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(R.string.tidy_step_preview, R.string.tidy_step_dest, R.string.tidy_step_confirm)
                    .forEachIndexed { i, label ->
                        Text(stringResource(label),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (i < 2) Text("  ›  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (step + 1) / 3f },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            when (step) {
                0 -> {
                    // preview strip
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        files.forEach { f ->
                            Column(Modifier.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Thumbnail(f.item, Modifier.size(56.dp))
                                Text(f.item.name, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (files.size == 1) {
                        OutlinedTextField(
                            value = singleName, onValueChange = { singleName = it },
                            label = { Text(stringResource(R.string.dialog_name)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        OutlinedTextField(
                            value = pattern, onValueChange = { pattern = it },
                            label = { Text(stringResource(R.string.tidy_rename_pattern)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        val preview = remember(pattern) {
                            BatchRenamePattern(pattern).preview(files.map { File(it.item.path) })
                        }
                        preview.take(3).forEach { p ->
                            Text("${p.oldName} → ${p.newName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.conflict) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tidy_next))
                    }
                }
                1 -> {
                    Text(stringResource(R.string.tidy_favorites), style = MaterialTheme.typography.titleSmall)
                    if (favorites.isEmpty()) {
                        Text(stringResource(R.string.tidy_no_favorites),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { browsePicker = true }) {
                            Text(stringResource(R.string.tidy_add_favorite))
                        }
                    }
                    LazyColumn(Modifier.height(200.dp)) {
                        items(favorites, key = { it.path }) { f ->
                            Row(Modifier.fillMaxWidth()
                                .clickable { dest = f.path; step = 2 }
                                .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, null,
                                    tint = MaterialTheme.colorScheme.tertiary)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(f.label)
                                    Text(DisplayName.localized(LocalContext.current, f.path),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            Text(stringResource(R.string.tidy_recent_dest),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                        items(recentDests.filter { it.path != dest }.take(5), key = { it.path }) { l ->
                            Row(Modifier.fillMaxWidth()
                                .clickable { dest = l.path; step = 2 }
                                .padding(vertical = 8.dp)) {
                                Icon(Icons.Outlined.Folder, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(l.label)
                                    Text(DisplayName.localized(LocalContext.current, l.path),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { browsePicker = true }) {
                        Text(stringResource(R.string.tidy_browse))
                    }
                }
                2 -> {
                    Text(stringResource(R.string.tidy_summary, files.size,
                        DisplayName.localized(LocalContext.current, dest ?: "")),
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { step = 1 }) { Text(stringResource(R.string.tidy_back)) }
                        Button(onClick = {
                            val d = dest ?: return@Button
                            // dismiss this sheet BEFORE starting the op — the
                            // progress sheet must be the only sheet on screen
                            onConfirmOp(d)
                        }) { Text(stringResource(R.string.tidy_confirm)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (browsePicker) {
        FolderPickerDialog(container,
            onSelect = { browsePicker = false; dest = it; step = 2 },
            onDismiss = { browsePicker = false })
    }
}

/** Simple picker listing favorites + recent destinations (used by "Move to favorite"). */
@Composable
fun FavoriteDestPicker(
    container: AppContainer,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val favorites by container.db.favoriteFolderDao().observeAll()
        .collectAsState(initial = emptyList())
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_favorite)) },
        text = {
            LazyColumn {
                items(favorites, key = { it.path }) { f ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(f.path) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(f.label)
                            Text(DisplayName.localized(LocalContext.current, f.path),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
