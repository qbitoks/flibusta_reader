package app.folio.feature.reader

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Hardware keys for the open book. The activity hands every key here first ([dispatch]); Compose key events
 * need focus, which sheets and selection keep moving.
 */
object ReaderKeys {
    @Volatile private var handler: ((KeyEvent) -> Boolean)? = null

    /** True when the open reader used [event]. */
    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) ?: false

    internal fun register(h: (KeyEvent) -> Boolean) { handler = h }
    internal fun unregister(h: (KeyEvent) -> Boolean) { if (handler === h) handler = null }
}

/** +1 / −1 for keys that turn a page, null for the rest. Volume keys count only when [volume] is on. */
internal fun keyTurn(keyCode: Int, volume: Boolean): Int? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> 1.takeIf { volume }
    KeyEvent.KEYCODE_VOLUME_UP -> (-1).takeIf { volume }
    KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SPACE -> 1
    KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> -1
    else -> null
}

/**
 * Turns pages with keys while the reader is shown: [onTurn] gets the direction on key down; the matching key up
 * is swallowed too, so the system shows no volume panel. [volume] off, or reading aloud, leaves volume keys alone.
 */
@Composable
internal fun ReaderKeysEffect(volume: Boolean, onTurn: (Int) -> Unit) {
    val latestVolume by rememberUpdatedState(volume)
    val latestOnTurn by rememberUpdatedState(onTurn)
    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = handler@{ event ->
            val direction = keyTurn(event.keyCode, latestVolume) ?: return@handler false
            if (event.action == KeyEvent.ACTION_DOWN) latestOnTurn(direction)
            true
        }
        ReaderKeys.register(handler)
        onDispose { ReaderKeys.unregister(handler) }
    }
}
