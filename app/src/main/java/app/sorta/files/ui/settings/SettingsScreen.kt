package app.sorta.files.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.sorta.files.AppContainer
import app.sorta.files.BuildConfig
import app.sorta.files.MainActivity
import app.sorta.files.R
import app.sorta.files.data.prefs.ThemeMode
import app.sorta.files.ui.navigation.Dest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by container.prefs.theme.collectAsState(initial = ThemeMode.SYSTEM)
    val grid by container.prefs.defaultViewGrid.collectAsState(initial = false)
    val hidden by container.prefs.showHidden.collectAsState(initial = false)
    val thumbs by container.prefs.showThumbs.collectAsState(initial = true)
    val purgeDays by container.prefs.purgeDays.collectAsState(initial = 30)
    val adsRemoved by container.prefs.adsRemoved.collectAsState(initial = false)
    val product by container.billing.product.collectAsState()
    var licenses by remember { mutableStateOf(false) }
    var purgePicker by remember { mutableStateOf(false) }

    // billing one-off events -> toast
    val event by container.billing.events.collectAsState()
    LaunchedEffect(event) {
        event?.let {
            Toast.makeText(context, context.getString(R.string.billing_error, it), Toast.LENGTH_SHORT).show()
            container.billing.consumeEvent()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            item { SectionHeader(stringResource(R.string.settings_section_general)) }

            item {
                Text(stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(ThemeMode.SYSTEM to R.string.settings_theme_system,
                        ThemeMode.LIGHT to R.string.settings_theme_light,
                        ThemeMode.DARK to R.string.settings_theme_dark
                    ).forEachIndexed { i, (m, label) ->
                        SegmentedButton(selected = theme == m,
                            onClick = { scope.launch { container.prefs.setTheme(m) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)) {
                            Text(stringResource(label))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Text(stringResource(R.string.settings_default_view),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to R.string.settings_view_list,
                        true to R.string.settings_view_grid
                    ).forEachIndexed { i, (g, label) ->
                        SegmentedButton(selected = grid == g,
                            onClick = { scope.launch { container.prefs.setDefaultViewGrid(g) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)) {
                            Text(stringResource(label))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                SwitchRow(stringResource(R.string.settings_show_hidden), hidden) {
                    scope.launch { container.prefs.setShowHidden(it) }
                }
            }
            item {
                SwitchRow(stringResource(R.string.settings_thumbs), thumbs) {
                    scope.launch { container.prefs.setShowThumbs(it) }
                }
            }
            item {
                SettingsRow(stringResource(R.string.settings_inbox_sources)) {
                    nav.navigate(Dest.INBOX_SOURCES)
                }
            }
            item {
                SettingsRow(stringResource(R.string.settings_rules)) {
                    nav.navigate(Dest.RULES)
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_data)) }
            item {
                SettingsRow(stringResource(R.string.settings_purge_days),
                    subtitle = stringResource(R.string.settings_purge_days_fmt, purgeDays)) {
                    purgePicker = true
                }
            }
            item {
                SettingsRow(stringResource(R.string.settings_storage_access)) {
                    (context as? MainActivity)?.openAllFilesAccess()
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                SettingsRow(
                    stringResource(R.string.settings_remove_ads),
                    subtitle = if (adsRemoved) stringResource(R.string.settings_purchased)
                    else container.billing.price() ?: "—",
                ) {
                    if (!adsRemoved) (context as? Activity)?.let { container.billing.launchPurchase(it) }
                }
            }
            item {
                SettingsRow(stringResource(R.string.settings_restore)) {
                    container.billing.restorePurchases()
                }
            }
            item {
                SettingsRow(stringResource(R.string.settings_licenses)) { licenses = true }
            }
            item {
                // TODO(PROD): point at the real privacy policy URL before release.
                SettingsRow(stringResource(R.string.settings_privacy)) {
                    try {
                        context.startActivity(android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://example.com/sorta/privacy")))
                    } catch (_: Exception) {}
                }
            }
            item {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp))
            }
        }
    }

    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = { Text(stringResource(R.string.licenses_text)) },
            confirmButton = {
                TextButton(onClick = { licenses = false }) {
                    Text(stringResource(R.string.details_ok))
                }
            },
        )
    }
    if (purgePicker) {
        AlertDialog(
            onDismissRequest = { purgePicker = false },
            title = { Text(stringResource(R.string.settings_purge_days)) },
            text = {
                Column {
                    listOf(7, 30, 90).forEach { d ->
                        Row(Modifier.fillMaxWidth().clickable {
                            scope.launch { container.prefs.setPurgeDays(d) }
                            purgePicker = false
                        }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = purgeDays == d, onClick = null)
                            Text(stringResource(R.string.settings_purge_days_fmt, d))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { purgePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
