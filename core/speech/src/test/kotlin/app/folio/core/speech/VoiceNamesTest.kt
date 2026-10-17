package app.folio.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class VoiceNamesTest {
    private val ru = Locale.forLanguageTag("ru-RU")

    @Test fun aSpeakersLocalAndNetworkVoicesBecomeOneRow() {
        val voices = describeVoices(listOf(
            Triple("ru-ru-x-ruf-network", ru, true),
            Triple("ru-ru-x-dfc-network", ru, true),
            Triple("ru-ru-x-dfc-local", ru, false),
            Triple("ru-ru-x-ruf-local", ru, false),
        ), ru)
        assertEquals(listOf("ru-ru-x-dfc-local", "ru-ru-x-ruf-local"), voices.map { it.local })
        assertEquals(listOf("ru-ru-x-dfc-network", "ru-ru-x-ruf-network"), voices.map { it.network })
        assertEquals(listOf(1, 2), voices.map { it.speaker })
        assertEquals(listOf(null, null), voices.map { it.title })
    }

    @Test fun variantPrefersTheAskedOneAndFallsBackToTheOther() {
        val both = TtsVoice("a-local", "a-network", ru, null, 1)
        assertEquals("a-network", both.variant(online = true))
        assertEquals("a-local", both.variant(online = false))
        val onlineOnly = TtsVoice(null, "b-network", ru, null, 2)
        assertEquals("b-network", onlineOnly.variant(online = false))
    }

    @Test fun anOnlineOnlySpeakerHasNoLocalVoice() {
        val voices = describeVoices(listOf(Triple("ru-ru-x-abc-network", ru, true)), ru)
        assertNull(voices.single().local)
    }

    @Test fun folioVoicesKeepSpeakerNames() {
        val voices = describeVoices(listOf(Triple("silero-ru_dmitriy", ru, false), Triple("piper-ru_RU-irina-medium", ru, false)), ru)
        assertEquals(listOf("Irina", "Dmitriy"), voices.map { it.title })
    }

    @Test fun theBookRegionComesFirst() {
        val ua = Locale.forLanguageTag("ru-UA")
        val voices = describeVoices(listOf(Triple("a-local", ua, false), Triple("b-local", ru, false)), ru)
        assertEquals(listOf("b-local", "a-local"), voices.map { it.local })
    }
}
