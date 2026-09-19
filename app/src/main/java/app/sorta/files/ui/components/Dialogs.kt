package app.sorta.files.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.core.fs.FileItem
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.ops.ConflictPolicy
import java.text.DateFormat
import java.util.Date

/** Observes OperationController.pendingConflict and shows the conflict dialog. */
@Composable
fun ConflictDialogHost(container: AppContainer) {
    val req by container.operations.pendingConflict.collectAsState()
    val r = req ?: return
    var applyAll by remember(r) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { container.operations.resolveConflict(ConflictPolicy.SKIP, false) },
        title = { Text(stringResource(R.string.conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.conflict_body, r.sourceName))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                    Text(stringResource(R.string.conflict_apply_all))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                container.operations.resolveConflict(ConflictPolicy.KEEP_BOTH, applyAll)
            }) { Text(stringResource(R.string.conflict_keep_both)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    container.operations.resolveConflict(ConflictPolicy.SKIP, applyAll)
                }) { Text(stringResource(R.string.conflict_skip)) }
                TextButton(onClick = {
                    container.operations.resolveConflict(ConflictPolicy.OVERWRITE, applyAll)
                }) { Text(stringResource(R.string.conflict_overwrite)) }
            }
        },
    )
}

/** Progress bottom sheet driven by the engine's progress flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationProgressHost(container: AppContainer) {
    val running by container.operations.running.collectAsState()
    val progress = container.operations.engine?.progress?.collectAsState()?.value
    if (!running && progress?.finished != false) return
    ModalBottomSheet(onDismissRequest = { /* block dismiss while running */ }) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            val title = when (progress?.opType) {
                app.sorta.files.core.ops.OpType.COPY -> stringResource(R.string.progress_copy)
                app.sorta.files.core.ops.OpType.MOVE -> stringResource(R.string.progress_move)
                app.sorta.files.core.ops.OpType.DELETE,
                app.sorta.files.core.ops.OpType.DELETE_PERMANENT ->
                    stringResource(R.string.progress_delete)
                else -> stringResource(R.string.progress_title)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val frac = progress?.let {
                if (it.totalBytes > 0) it.doneBytes.toFloat() / it.totalBytes
                else if (it.totalItems > 0) it.doneItems.toFloat() / it.totalItems else 0f
            } ?: 0f
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(progress?.currentItem ?: "", style = MaterialTheme.typography.bodySmall,
                maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { container.operations.cancel() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.dialog_name)) }, singleLine = true)
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DetailsDialog(item: FileItem, onDismiss: () -> Unit) {
    val fmt = remember { DateFormat.getDateTimeInstance() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${stringResource(R.string.details_path)}: ${item.path}",
                    style = MaterialTheme.typography.bodySmall)
                Text("${stringResource(R.string.details_type)}: ${item.mime ?: item.category.name}",
                    style = MaterialTheme.typography.bodySmall)
                if (!item.isDir)
                    Text("${stringResource(R.string.details_size)}: ${FileSystem.formatSize(item.size)}",
                        style = MaterialTheme.typography.bodySmall)
                if (item.isDir)
                    Text("${stringResource(R.string.details_contains)}: ${stringResource(R.string.items_count, item.childCount ?: 0)}",
                        style = MaterialTheme.typography.bodySmall)
                Text("${stringResource(R.string.details_modified)}: ${fmt.format(Date(item.lastModified))}",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun AdBannerSlot() {
    AdBanner()
}
