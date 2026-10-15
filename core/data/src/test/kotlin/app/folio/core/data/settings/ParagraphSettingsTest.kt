package app.folio.core.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphSettingsTest {
    @Test fun extendedTextSetSurvivesSharedAndBookPersistence() {
        val encoded = "lora|false|true|1.35|0|0|0|true|0.5|1.2"
        val text = TextSet.decode(encoded, TextPreset.Classic.text!!)
        assertEquals(text, TextSet.decode(text.encode(), TextSet.DEFAULT))
        val shared = ReaderSettings()
        val edited = shared.withText(text)
        val preferences = mutablePreferencesOf()
        ReaderPreferences.write(preferences, edited)
        assertEquals(edited, ReaderPreferences.read(preferences))
        val overrides = BookOverrides.between(shared, edited)
        assertEquals(edited.textSet, BookOverrides.decode(overrides.encode()).applyTo(shared).textSet)
    }

    @Test fun legacyTextKeepsItsFontAndIntervals() {
        val text = TextSet.decode("pt_serif|true|true|1.4|2|3|6|false", TextPreset.Classic.text!!)
        assertEquals("pt_serif", text.font)
        assertEquals(1.4f, text.toReaderStyle(19f).lineSpacing, 0f)
        assertEquals(0f, text.paragraphSpacing, 0f)
        assertEquals(1.6f, text.firstLineIndent, 0f)
    }

    @Test fun themesPreserveIndependentParagraphSettings() {
        for (theme in ReaderTheme.entries) {
            val settings = ReaderSettings().withTheme(theme)
            assertEquals(settings.textSet.paragraphSpacing, settings.style.paragraphSpacing, 0f)
            assertEquals(settings.textSet.firstLineIndent, settings.style.firstLineIndent, 0f)
            assertEquals(ReaderSettings().textSet, settings.textSet)
        }
        assertEquals(0f, ReaderSettings().style.paragraphSpacing, 0f)
        assertEquals(1.5f, ReaderSettings().withTheme(ReaderTheme.Focus).style.firstLineIndent, 0f)
    }

    @Test fun invalidParagraphSettingsAreRejectedAndRangesAreClamped() {
        val fallback = TextPreset.Classic.text!!
        assertEquals(fallback, TextSet.decode("lora|false|true|1.35|0|0|0|true|NaN|1.2", fallback))
        val clamped = TextSet.decode("lora|false|true|1.35|0|0|0|true|-1.0|8.0", fallback)
        assertEquals(0f, clamped.paragraphSpacing, 0f)
        assertEquals(3f, clamped.firstLineIndent, 0f)
    }
}
