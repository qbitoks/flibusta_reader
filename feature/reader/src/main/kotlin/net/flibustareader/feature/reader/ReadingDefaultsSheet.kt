package net.flibustareader.feature.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.flibustareader.core.data.settings.BookOverrides
import net.flibustareader.core.data.settings.ReaderSettings
import net.flibustareader.core.data.settings.SettingsRepository
import net.flibustareader.core.data.settings.ThemeStyle
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaTheme
import net.flibustareader.core.designsystem.FlibustaType
import javax.inject.Inject

@HiltViewModel
class ReadingDefaultsViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<ReaderSettings> = settingsRepository.reader.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    /** Closing the panel never cancels a save. */
    fun save(change: (ReaderSettings) -> ReaderSettings) = viewModelScope.launch { settingsRepository.updateReader(change) }
}

/**
 * Library → «Настройки чтения»: the reader's settings panel for all books, over a sample page.
 * Books keep what they set themselves.
 */
@Composable
fun ReadingDefaultsSheet(onDismiss: () -> Unit, vm: ReadingDefaultsViewModel = hiltViewModel()) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    val editor = rememberSettingsEditor(saved, BookOverrides(), bookScope = false,
        save = { change -> vm.save(change).join() }, textReset = { it.withThemeStyle(ThemeStyle.defaultFor(it.theme)) })
    FlibustaTheme(dark = readerPalette(editor.settings).isDark) {
        ReadingSettingsSheet(
            editor = editor,
            title = stringResource(R.string.reading_defaults_title),
            subtitle = { Text(stringResource(R.string.settings_scope_all), style = FlibustaType.subheadline, color = Flibusta.colors.secondaryLabel) },
            preview = samplePreviewSource(),
            brightness = null,
            onDismiss = onDismiss,
        )
    }
}
