package id.alquranhadits.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Emerald = Color(0xFF0F5132)
val Gold = Color(0xFFC9A227)

private val Light = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBD9),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF8A6D1D),
    secondaryContainer = Color(0xFFF7E7B4),
    onSecondaryContainer = Color(0xFF2A1F00),
    tertiary = Color(0xFF3B6470),
    tertiaryContainer = Color(0xFFD3EEF7),
    background = Color(0xFFFBFAF5),
    surface = Color(0xFFFBFAF5),
    surfaceVariant = Color(0xFFE6EDE7),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DD5AE),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF14522F),
    onPrimaryContainer = Color(0xFFCDEBD9),
    secondary = Color(0xFFE5C46B),
    secondaryContainer = Color(0xFF574500),
    onSecondaryContainer = Color(0xFFF7E7B4),
    tertiary = Color(0xFFA6CEDA),
    tertiaryContainer = Color(0xFF234C57),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
