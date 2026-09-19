package app.sorta.files.ui.rules

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.sorta.files.data.db.SortRule
import app.sorta.files.ui.components.FolderPickerDialog
import app.sorta.files.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(nav: NavController, container: AppContainer) {
    val dao = container.db.sortRuleCrudDao()
    val rules by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SortRule?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                text = { Text(stringResource(R.string.rules_add)) },
                icon = { Icon(Icons.Outlined.Add, null) },
            )
        },
    ) { padding ->
        if (rules.isEmpty()) EmptyState(stringResource(R.string.rules_empty))
        else LazyColumn(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            items(rules, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.enabled,
                            onCheckedChange = { scope.launch { dao.update(r.copy(enabled = it)) } })
                        Column(Modifier.weight(1f).clickable { editing = r }) {
                            Text(r.name)
                            Text("${r.matchType}: ${r.matchValue} → ${r.targetFolder}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { scope.launch { dao.delete(r) } }) {
                            Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        RuleEditorDialog(
            container = container,
            rule = editing,
            onSave = { rule ->
                scope.launch { if (rule.id == 0L) dao.insert(rule) else dao.update(rule) }
                adding = false; editing = null
            },
            onDismiss = { adding = false; editing = null },
        )
    }
}

@Composable
private fun RuleEditorDialog(
    container: AppContainer,
    rule: SortRule?,
    onSave: (SortRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var matchType by remember { mutableStateOf(rule?.matchType ?: "EXT") }
    var matchValue by remember { mutableStateOf(rule?.matchValue ?: "") }
    var target by remember { mutableStateOf(rule?.targetFolder ?: "") }
    var typeMenu by remember { mutableStateOf(false) }
    var folderPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) stringResource(R.string.rules_add)
                     else stringResource(R.string.dialog_rename)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rules_name)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { typeMenu = true }) {
                        Text(when (matchType) {
                            "EXT" -> stringResource(R.string.rules_match_ext)
                            "NAME_CONTAINS" -> stringResource(R.string.rules_match_contains)
                            else -> stringResource(R.string.rules_match_regex)
                        })
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        listOf("EXT" to R.string.rules_match_ext,
                            "NAME_CONTAINS" to R.string.rules_match_contains,
                            "NAME_REGEX" to R.string.rules_match_regex).forEach { (v, l) ->
                            DropdownMenuItem(text = { Text(stringResource(l)) },
                                onClick = { matchType = v; typeMenu = false })
                        }
                    }
                }
                OutlinedTextField(value = matchValue, onValueChange = { matchValue = it },
                    label = { Text(stringResource(R.string.rules_pattern)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { folderPicker = true }) {
                    Text(if (target.isEmpty()) stringResource(R.string.rules_target) else target,
                        maxLines = 1)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && matchValue.isNotBlank() && target.isNotBlank(),
                onClick = {
                    onSave(SortRule(
                        id = rule?.id ?: 0, name = name.trim(), matchType = matchType,
                        matchValue = matchValue.trim(), targetFolder = target))
                },
            ) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (folderPicker) {
        FolderPickerDialog(container,
            onSelect = { target = it; folderPicker = false },
            onDismiss = { folderPicker = false })
    }
}
