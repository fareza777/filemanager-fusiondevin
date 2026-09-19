package app.sorta.files.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.sorta.files.R

@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.placeholder_screen),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SelectionTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null) }
            Text("$count", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all)) }
        }
    }
}

@Composable
fun BottomActionBar(
    single: Boolean,
    canFavorite: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onBasket: () -> Unit,
    moreItems: List<Pair<String, () -> Unit>>,
) {
    val more = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionBtn(Icons.Outlined.ContentCopy, R.string.action_copy, onCopy)
            ActionBtn(Icons.Outlined.DriveFileMove, R.string.action_move, onMove)
            ActionBtn(Icons.Outlined.Edit, R.string.action_rename, onRename)
            ActionBtn(Icons.Outlined.Delete, R.string.action_delete, onDelete)
            ActionBtn(Icons.Outlined.Share, R.string.action_share, onShare)
            ActionBtn(Icons.Outlined.ShoppingBasket, R.string.action_add_basket, onBasket)
            Box {
                IconButton(onClick = { more.value = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more))
                }
                androidx.compose.material3.DropdownMenu(expanded = more.value, onDismissRequest = { more.value = false }) {
                    moreItems.forEach { (label, cb) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { more.value = false; cb() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, stringResource(labelRes)) }
}

/** Persistent "Paste here" bar when the SelectionBasket is non-empty. */
@Composable
fun PasteBar(count: Int, onPaste: () -> Unit, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.action_paste_here, count),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onPaste) { Text(stringResource(R.string.action_paste_here, count).substringBefore(" (")) }
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear_basket)) }
        }
    }
}
