package dev.dwm.liftlog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent palette shared across screens (Strong/MacroFactor-inspired)
object Palette {
    val Success = Color(0xFF4CAF50) // completed sets, calendar dots, finish button
    val Protein = Color(0xFFFF8A50)
    val Fat = Color(0xFFFFC94D)
    val Carbs = Color(0xFFB5E655)
    val Calories = Color(0xFF4D9FFF)
    val Pr = Color(0xFFFFD54F)
}

private val Scheme = darkColorScheme(
    primary = Color(0xFF4D9FFF),
    onPrimary = Color(0xFF00284D),
    secondary = Color(0xFF4CAF50),
    onSecondary = Color(0xFF07290A),
    background = Color(0xFF101214),
    surface = Color(0xFF16181C),
    surfaceVariant = Color(0xFF23262B),
    surfaceContainer = Color(0xFF1B1E23),
    surfaceContainerHigh = Color(0xFF23262B),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF3C4045),
)

@Composable
fun LiftLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
