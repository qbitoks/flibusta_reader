package app.folio.feature.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio

/** The Advanced tab owns typography, colour adjustments and page layout, including their reset. */
@Composable
internal fun ReaderAdvancedSettings(editor: SettingsEditor, preview: PreviewSource) {
    var confirmReset by rememberSaveable(editor.bookScope) { mutableStateOf(false) }
    ReaderPageSettings(editor, preview)
    ReadingTextSettings(editor)
    ReadingColorSettings(editor)
    Spacer(Modifier.height(16.dp))
    ResetButton(stringResource(R.string.settings_reset_reading),
        editor.settings != editor.settings.withAdvancedDefaults()) { confirmReset = true }
    if (confirmReset) {
        AlertDialog(onDismissRequest = { confirmReset = false }, containerColor = Folio.colors.surface,
            title = { Text(stringResource(R.string.settings_reset_reading)) },
            text = { Text(stringResource(if (editor.bookScope) R.string.settings_reset_reading_book_text else R.string.settings_reset_reading_all_text)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; editor.edit { it.withAdvancedDefaults() } }) {
                    Text(stringResource(R.string.settings_reset_own_confirm), color = Folio.colors.destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.settings_cancel)) }
            })
    }
}
