package app.sorta.files.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sorta.files.R

/** Localized message for an engine error key. */
@Composable
fun errorMessage(key: String?): String = when (key) {
    "disk_full" -> stringResource(R.string.err_disk_full)
    "into_itself" -> stringResource(R.string.err_into_itself)
    "permission_denied" -> stringResource(R.string.err_permission_denied)
    "source_delete_failed" -> stringResource(R.string.err_source_delete)
    "size_mismatch" -> stringResource(R.string.err_size_mismatch)
    "checksum_mismatch" -> stringResource(R.string.err_checksum_mismatch)
    "dest_exists_dir" -> stringResource(R.string.err_dest_exists_dir)
    null -> ""
    else -> key
}
