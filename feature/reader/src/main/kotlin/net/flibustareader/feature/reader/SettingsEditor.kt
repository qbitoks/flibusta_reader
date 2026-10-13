package net.flibustareader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.flibustareader.core.data.settings.BookOverrides
import net.flibustareader.core.data.settings.ReaderSettings

/**
 * What the settings panel edits: one book's own settings or the shared ones. Edits show at once;
 * saved values replace the shown ones only when no save is pending, so sliders never jump back.
 */
@Stable
internal class SettingsEditor(
    initial: ReaderSettings,
    val bookScope: Boolean,
    private val scope: CoroutineScope,
    private val save: suspend ((ReaderSettings) -> ReaderSettings) -> Unit,
    private val textReset: (ReaderSettings) -> ReaderSettings,
) {
    var settings by mutableStateOf(initial)
        private set
    /** Fields this book sets itself; empty while the shared settings are edited. */
    var own by mutableStateOf(BookOverrides())
        private set
    private var pending by mutableIntStateOf(0)

    fun edit(change: (ReaderSettings) -> ReaderSettings) {
        settings = change(settings)
        pending++
        scope.launch {
            try { save(change) } finally { pending-- }
        }
    }

    fun resetText() = edit(textReset)

    fun isOwn(keys: Collection<String>): Boolean = bookScope && own.has(keys)

    fun isOwn(key: String): Boolean = isOwn(listOf(key))

    internal fun sync(saved: ReaderSettings, own: BookOverrides) {
        this.own = if (bookScope) own else BookOverrides()
        if (pending == 0) settings = saved
    }
}

@Composable
internal fun rememberSettingsEditor(
    saved: ReaderSettings,
    own: BookOverrides,
    bookScope: Boolean,
    save: suspend ((ReaderSettings) -> ReaderSettings) -> Unit,
    textReset: (ReaderSettings) -> ReaderSettings,
): SettingsEditor {
    val scope = rememberCoroutineScope()
    val editor = remember(bookScope) { SettingsEditor(saved, bookScope, scope, save, textReset) }
    LaunchedEffect(editor, saved, own) { editor.sync(saved, own) }
    return editor
}
