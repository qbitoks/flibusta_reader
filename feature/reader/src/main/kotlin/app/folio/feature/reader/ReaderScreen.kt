package app.folio.feature.reader

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.settings.ReaderSettings
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.ReaderPalettes
import app.folio.core.designsystem.FolioTheme

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
    FolioTheme(dark = palette.isDark) { ReaderContent(vm, onClose) }
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
    BackHandler(enabled = controls.chromeVisible && controls.sheet == null) { controls.chromeVisible = false }
    ReaderSurface(vm, settings, palette, controls, bookmarks.size + highlights.size, highlights, onClose)
    ReaderSheets(vm, settings, palette, controls)
}

/** Page palette of [settings]; «Авто» follows Android, not the app's own appearance. */
@Composable
internal fun readerPalette(settings: ReaderSettings): ReaderPalette {
    val dark = ReaderPalettes.isDark(settings.appearance, androidInDarkTheme())
    val colors = settings.customColors
    return if (settings.custom && colors != null) ReaderPalettes.custom(colors, dark) else ReaderPalettes.of(settings.theme, dark)
}

/**
 * Android's own dark theme. isSystemInDarkTheme() reflects the app's appearance (per-app night mode);
 * the system resources keep the device's. Reading the local configuration recomposes when either changes.
 */
@Composable
internal fun androidInDarkTheme(): Boolean {
    LocalConfiguration.current
    return Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
