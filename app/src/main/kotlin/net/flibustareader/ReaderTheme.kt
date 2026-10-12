package net.flibustareader

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ReaderTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFD0BCFF), background = Color.Black, surface = Color(0xFF17141C)) else lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEAE1FA), onPrimaryContainer = Color(0xFF342356),
        background = Color(0xFFF7F5F0), surface = Color(0xFFF7F5F0),
        surfaceVariant = Color(0xFFECE8E0), onSurface = Color(0xFF24212B),
        secondaryContainer = Color(0xFFEAE1FA)
    ), content = content)
}
