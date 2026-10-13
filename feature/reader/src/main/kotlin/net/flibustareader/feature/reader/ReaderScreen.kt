package net.flibustareader.feature.reader

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.ReaderSettings
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.ReaderPalettes
import net.flibustareader.core.designsystem.FlibustaTheme

@Composable
fun ReaderRoute(onClose: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    ReaderScreen(viewModel, onClose)
}


/**
 * The reader's own controls (menus, sheets, sliders) follow the book's appearance rather than
 * the system's, so a light page never gets dark sheets and vice versa.
 */
@Composable
fun ReaderScreen(vm: ReaderViewModel, onClose: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = readerPalette(settings)
    FlibustaTheme(dark = palette.isDark) { ReaderContent(vm, onClose) }
}

@Composable
private fun ReaderContent(vm: ReaderViewModel, onClose: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val palette = readerPalette(settings)
    val controls = rememberReaderControls()
    vm.ink.apply {
        text = palette.text.toArgb()
        secondary = palette.secondary.toArgb()
        link = palette.link.toArgb()
        highlight = palette.highlight.toArgb()
    }
    ImmersiveMode(showBars = controls.chromeVisible || controls.sheet != null, lightBars = !palette.isDark)
    ReadingSessionEffect(vm)
    WindowEffects(rotationLocked = controls.rotationLocked, brightness = controls.brightness)
    BackHandler(enabled = controls.menuVisible) { controls.menuVisible = false }
    ReaderSurface(vm, settings, palette, controls, bookmarks.size + highlights.size, highlights, onClose)
    ReaderSheets(vm, settings, palette, controls)
}

/** Page palette of [settings]; isSystemInDarkTheme() already reflects the app's own appearance (per-app night mode). */
@Composable
internal fun readerPalette(settings: ReaderSettings): ReaderPalette =
    ReaderPalettes.of(settings.theme, ReaderPalettes.isDark(settings.appearance, isSystemInDarkTheme()))
