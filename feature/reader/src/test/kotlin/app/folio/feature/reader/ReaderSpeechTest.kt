package app.folio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSpeechTest {
    @Test
    fun `rate chip cycles through the speeds`() {
        assertEquals(1f, nextSpeechRate(0.8f))
        assertEquals(1.25f, nextSpeechRate(1f))
        assertEquals(0.8f, nextSpeechRate(2f))
        // A saved rate between steps moves to the next step up.
        assertEquals(1.5f, nextSpeechRate(1.3f))
    }
}
