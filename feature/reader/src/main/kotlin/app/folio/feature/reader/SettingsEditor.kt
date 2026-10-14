package app.folio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.folio.core.data.settings.BookOverrides
import app.folio.core.data.settings.ReaderSettings

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
    private val pageReset: (ReaderSettings) -> ReaderSettings,
) {
    var settings by mutableStateOf(initial)
        private set
    /** Fields this book sets itself; empty while the shared settings are edited. */
    var own by mutableStateOf(BookOverrides())
        private set
    private var pending by mutableIntStateOf(0)
    private val queued = mutableListOf<(ReaderSettings) -> ReaderSettings>()
    private var flushJob: Job? = null

    /**
     * Shows the edit at once but saves after a short pause: each save re-paginates the open book,
     * which made slider drags lag behind the finger.
     */
    fun edit(change: (ReaderSettings) -> ReaderSettings) {
        val edited = change(settings)
        if (edited == settings) return
        settings = edited
        if (queued.isEmpty()) pending++
        queued += change
        flushJob?.cancel()
        flushJob = scope.launch { delay(SAVE_DELAY_MS); flush() }
    }

    /** Saves queued edits now; also on close, where [scope] is already gone. */
    fun flush() {
        if (queued.isEmpty()) return
        val batch = queued.toList()
        queued.clear()
        flushJob?.cancel()
        // The save itself runs in the view model's scope; this coroutine only waits for it.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try { save { start -> batch.fold(start) { s, f -> f(s) } } } finally { pending-- }
        }
    }

    fun resetText() = edit(textReset)

    fun resetPage() = edit(pageReset)

    /** «Сбросить настройки страницы» has something to do. */
    val canResetPage: Boolean get() = if (bookScope) isOwn(BookOverrides.HEADER_KEYS + BookOverrides.FOOTER_KEYS)
        else settings.withLinesOf(ReaderSettings()) != settings

    fun isOwn(keys: Collection<String>): Boolean = bookScope && own.has(keys)

    fun isOwn(key: String): Boolean = isOwn(listOf(key))

    internal fun sync(saved: ReaderSettings, own: BookOverrides) {
        this.own = if (bookScope) own else BookOverrides()
        if (pending == 0) settings = saved
    }

    private companion object {
        const val SAVE_DELAY_MS = 300L
    }
}

@Composable
internal fun rememberSettingsEditor(
    saved: ReaderSettings,
    own: BookOverrides,
    bookScope: Boolean,
    save: suspend ((ReaderSettings) -> ReaderSettings) -> Unit,
    textReset: (ReaderSettings) -> ReaderSettings,
    pageReset: (ReaderSettings) -> ReaderSettings,
): SettingsEditor {
    val scope = rememberCoroutineScope()
    val editor = remember(bookScope) { SettingsEditor(saved, bookScope, scope, save, textReset, pageReset) }
    LaunchedEffect(editor, saved, own) { editor.sync(saved, own) }
    DisposableEffect(editor) { onDispose { editor.flush() } }
    return editor
}
