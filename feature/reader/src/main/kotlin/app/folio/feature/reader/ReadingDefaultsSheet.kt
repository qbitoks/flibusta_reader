package app.folio.feature.reader

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
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.ReaderSettings
import app.folio.core.data.settings.SettingsRepository
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioTheme
import app.folio.core.designsystem.uiColors
import app.folio.core.designsystem.FolioType
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
        save = { change -> vm.save(change).join() })
    FolioTheme(readerPalette(editor.settings).uiColors()) {
        ReadingSettingsSheet(
            editor = editor,
            title = stringResource(R.string.reading_defaults_title),
            subtitle = { Text(stringResource(R.string.settings_scope_all), style = FolioType.subheadline, color = Folio.colors.secondaryLabel) },
            preview = samplePreviewSource(),
            brightness = null,
            onDismiss = onDismiss,
        )
    }
}
