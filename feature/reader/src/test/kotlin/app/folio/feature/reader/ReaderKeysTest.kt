package app.folio.feature.reader

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderKeysTest {
    @Test fun volumeKeysTurnOnlyWhenOn() {
        assertEquals(1, keyTurn(KeyEvent.KEYCODE_VOLUME_DOWN, volume = true))
        assertEquals(-1, keyTurn(KeyEvent.KEYCODE_VOLUME_UP, volume = true))
        assertNull(keyTurn(KeyEvent.KEYCODE_VOLUME_DOWN, volume = false))
        assertNull(keyTurn(KeyEvent.KEYCODE_VOLUME_UP, volume = false))
    }

    @Test fun keyboardKeysAlwaysTurn() {
        assertEquals(1, keyTurn(KeyEvent.KEYCODE_PAGE_DOWN, volume = false))
        assertEquals(-1, keyTurn(KeyEvent.KEYCODE_PAGE_UP, volume = false))
        assertEquals(1, keyTurn(KeyEvent.KEYCODE_SPACE, volume = false))
        assertNull(keyTurn(KeyEvent.KEYCODE_BACK, volume = true))
    }
}
