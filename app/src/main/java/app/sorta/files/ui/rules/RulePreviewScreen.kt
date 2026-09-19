package app.sorta.files.ui.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.sorta.files.core.ops.OpType
import app.sorta.files.core.rules.RuleEngine
import app.sorta.files.core.scan.InboxScanner
import app.sorta.files.data.db.InboxState
import app.sorta.files.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulePreviewScreen(nav: NavController, container: AppContainer) {
    val rules by container.db.sortRuleCrudDao().observeAll().collectAsState(initial = emptyList())
    var matches by remember { mutableStateOf<List<RuleEngine.RuleMatch>>(emptyList()) }
    var checked by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rules) {
        if (rules.isEmpty()) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val sources = container.db.inboxSourceDao().enabled()
            val all = mutableListOf<app.sorta.files.core.scan.InboxFile>()
            InboxScanner.scan(sources, container.db.inboxStateDao()).collect { batch ->
                all += batch.filter { !it.tidied }
            }
            val m = RuleEngine.preview(rules.filter { it.enabled }, all.map { it.item })
            matches = m
            checked = m.map { it.item.path }.toSet()
        }
    }

    val sel = matches.filter { it.item.path in checked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
            )
        },
        bottomBar = {
            if (sel.isNotEmpty()) {
                Button(
                    onClick = {
                        // group by target folder, run a MOVE per group
                        sel.groupBy { it.rule.targetFolder }.forEach { (target, group) ->
                            container.operations.run(
                                OpType.MOVE, group.map { File(it.item.path) }, File(target))
                        }
                        // mark moved tidied (source paths; destination unknown until op done)
                        scope.launch(Dispatchers.IO) {
                            sel.forEach {
                                container.db.inboxStateDao().upsert(
                                    InboxState(it.item.path, "TIDIED"))
                            }
                        }
                        nav.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.rules_move, sel.size)) }
            }
        },
    ) { padding ->
        if (matches.isEmpty()) EmptyState(stringResource(R.string.rules_no_match))
        else LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(matches, key = { it.item.path }) { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = m.item.path in checked,
                        onCheckedChange = {
                            checked = if (it) checked + m.item.path else checked - m.item.path
                        })
                    Column(Modifier.weight(1f)) {
                        Text(m.item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text("${m.rule.name} → ${m.rule.targetFolder}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}
