package app.folio.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class VoiceNamesTest {
    private val ru = Locale.forLanguageTag("ru-RU")

    @Test fun codeNamedVoicesAreNumberedBySpeaker() {
        val voices = describeVoices(listOf(
            Triple("ru-ru-x-ruf-network", ru, true),
            Triple("ru-ru-x-dfc-network", ru, true),
            Triple("ru-ru-x-dfc-local", ru, false),
            Triple("ru-ru-x-ruf-local", ru, false),
        ), ru)
        assertEquals(listOf("ru-ru-x-dfc-local", "ru-ru-x-dfc-network", "ru-ru-x-ruf-local", "ru-ru-x-ruf-network"), voices.map { it.name })
        assertEquals(listOf(1, 1, 2, 2), voices.map { it.speaker })
        assertEquals(listOf(null, null, null, null), voices.map { it.title })
    }

    @Test fun folioVoicesKeepSpeakerNames() {
        val voices = describeVoices(listOf(Triple("silero-ru_dmitriy", ru, false), Triple("piper-ru_RU-irina-medium", ru, false)), ru)
        assertEquals(listOf("Irina", "Dmitriy"), voices.map { it.title })
    }

    @Test fun theBookRegionComesFirst() {
        val ua = Locale.forLanguageTag("ru-UA")
        val voices = describeVoices(listOf(Triple("a-local", ua, false), Triple("b-local", ru, false)), ru)
        assertEquals(listOf("b-local", "a-local"), voices.map { it.name })
    }
}
