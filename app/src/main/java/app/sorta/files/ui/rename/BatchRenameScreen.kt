package app.sorta.files.ui.rename

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.rename.BatchRenamePattern
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenameScreen(nav: NavController, container: AppContainer, paths: List<String>) {
    val files = remember(paths) { paths.map { File(it) } }
    var mode by remember { mutableStateOf(0) } // 0 pattern, 1 find/replace
    var pattern by remember { mutableStateOf("{name}{ext}") }
    var start by remember { mutableIntStateOf(1) }
    var step by remember { mutableIntStateOf(1) }
    var padding by remember { mutableIntStateOf(1) }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }

    val renamer = remember(pattern, start, step, padding, mode, find, replace) {
        if (mode == 1) BatchRenamePattern(find = find, replace = replace)
        else BatchRenamePattern(pattern, start, step, padding)
    }
    val previews = remember(renamer) { renamer.preview(files) }
    val conflicts = previews.count { it.conflict }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rename_batch_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                if (conflicts > 0) {
                    Text(stringResource(R.string.rename_conflicts, conflicts),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
                Button(
                    onClick = {
                        previews.forEachIndexed { i, p ->
                            if (p.newName != p.oldName) {
                                container.operations.run(OpType.RENAME, listOf(files[i]), null,
                                    renameTarget = p.newName)
                            }
                        }
                        nav.popBackStack()
                    },
                    enabled = conflicts == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.rename_apply)) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = mode == 0, onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.rename_mode_pattern))
                }
                SegmentedButton(selected = mode == 1, onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.rename_find_replace))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (mode == 0) {
                OutlinedTextField(value = pattern, onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.rename_pattern_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = "$start",
                        onValueChange = { start = it.toIntOrNull() ?: start },
                        label = { Text(stringResource(R.string.rename_start)) },
                        singleLine = true, modifier = Modifier.width(90.dp))
                    OutlinedTextField(value = "$step",
                        onValueChange = { step = it.toIntOrNull() ?: step },
                        label = { Text(stringResource(R.string.rename_step)) },
                        singleLine = true, modifier = Modifier.width(90.dp))
                    OutlinedTextField(value = "$padding",
                        onValueChange = { padding = (it.toIntOrNull() ?: padding).coerceIn(1, 8) },
                        label = { Text(stringResource(R.string.rename_padding)) },
                        singleLine = true, modifier = Modifier.width(90.dp))
                }
            } else {
                OutlinedTextField(value = find, onValueChange = { find = it },
                    label = { Text(stringResource(R.string.rename_find)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = replace, onValueChange = { replace = it },
                    label = { Text(stringResource(R.string.rename_replace)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(previews) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(p.oldName, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text("→", Modifier.padding(horizontal = 8.dp))
                        Text(p.newName, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            color = if (p.conflict) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
