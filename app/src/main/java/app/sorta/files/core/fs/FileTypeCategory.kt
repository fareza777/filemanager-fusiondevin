package app.sorta.files.core.fs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class FileTypeCategory(val icon: ImageVector, val tint: Color) {
    IMAGE(Icons.Outlined.Image, Color(0xFF4F8FDD)),
    VIDEO(Icons.Outlined.VideoFile, Color(0xFF9C5BD5)),
    AUDIO(Icons.Outlined.AudioFile, Color(0xFFE06AA0)),
    DOCUMENT(Icons.Outlined.Description, Color(0xFF3FA46A)),
    APK(Icons.Outlined.Widgets, Color(0xFF2BB3A3)),
    ARCHIVE(Icons.Outlined.Inventory2, Color(0xFFF5A524)),
    OTHER(Icons.Outlined.InsertDriveFile, Color(0xFF8A90A6)),
    FOLDER(Icons.Outlined.Folder, Color(0xFFF5B93C)),
}
