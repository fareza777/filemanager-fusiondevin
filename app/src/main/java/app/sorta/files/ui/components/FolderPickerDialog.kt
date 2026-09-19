package app.sorta.files.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.sorta.files.core.fs.FileSystem
import app.sorta.files.core.fs.SortSpec
import java.io.File

/** In-dialog folder navigator with "Select this folder" and "New folder". */
@Composable
fun FolderPickerDialog(
    container: AppContainer,
    initialPath: String? = null,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember {
        mutableStateOf(initialPath
            ?: android.os.Environment.getExternalStorageDirectory().absolutePath)
    }
    var children by remember { mutableStateOf<List<File>>(emptyList()) }
    var newFolder by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(current) {
        children = File(current).listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (File(current).parentFile != null) {
                    IconButton(onClick = { current = File(current).parent }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                }
                Text(File(current).name.ifEmpty { current }, maxLines = 1)
            }
        },
        text = {
            Column {
                Text(current, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.height(280.dp)) {
                    items(children, key = { it.absolutePath }) { d ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { current = d.absolutePath }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Folder, null,
                                tint = MaterialTheme.colorScheme.tertiary)
                            Text(d.name, Modifier.padding(start = 12.dp))
                        }
                    }
                }
                TextButton(onClick = { newFolder = true }) {
                    Text(stringResource(R.string.action_new_folder))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(current) }) {
                Text(stringResource(R.string.tidy_select_folder))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (newFolder) {
        NameDialog(
            title = stringResource(R.string.dialog_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.dialog_create),
            onConfirm = { name ->
                newFolder = false
                val f = File(current, name)
                if (f.mkdirs() || f.isDirectory) current = f.absolutePath
            },
            onDismiss = { newFolder = false },
        )
    }
}
