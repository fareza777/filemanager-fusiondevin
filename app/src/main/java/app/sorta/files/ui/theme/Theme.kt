package app.sorta.files.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Indigo = Color(0xFF4F5BD5)
val Teal = Color(0xFF2BB3A3)
val Amber = Color(0xFFF5A524)

private val DarkColors = darkColorScheme(
    primary = Indigo,
    secondary = Teal,
    tertiary = Amber,
    background = Color(0xFF0F1117),
    surface = Color(0xFF161922),
    surfaceVariant = Color(0xFF1E2230),
    onPrimary = Color.White,
    onBackground = Color(0xFFE8EAF2),
    onSurface = Color(0xFFE8EAF2),
)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Teal,
    tertiary = Amber,
    background = Color(0xFFF7F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0F7),
    onPrimary = Color.White,
    onBackground = Color(0xFF1B1E2A),
    onSurface = Color(0xFF1B1E2A),
)

val SortaShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(12.dp),
)

@Composable
fun SortaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        shapes = SortaShapes,
        content = content,
    )
}
