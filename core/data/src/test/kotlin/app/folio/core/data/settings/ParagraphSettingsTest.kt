package app.folio.core.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphSettingsTest {
    @Test fun extendedTextSetSurvivesSharedAndBookPersistence() {
        val encoded = "lora|false|1.35|0|0|5|true|0.5|1.2"
        val text = TextSet.decode(encoded, TextSet.DEFAULT)
        assertEquals(text, TextSet.decode(text.encode(), TextSet.DEFAULT))
        val shared = ReaderSettings()
        val edited = shared.withText(text)
        val preferences = mutablePreferencesOf()
        ReaderPreferences.write(preferences, edited)
        assertEquals(edited, ReaderPreferences.read(preferences))
        val overrides = BookOverrides.between(shared, edited)
        assertEquals(edited.textSet, BookOverrides.decode(overrides.encode()).applyTo(shared).textSet)
    }

    @Test fun obsoleteTextFormatUsesDefaults() {
        assertEquals(TextSet.DEFAULT, TextSet.decode("pt_serif|true|true|1.4|2|3|6|false", TextSet.DEFAULT))
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
        val fallback = TextSet.DEFAULT
        assertEquals(fallback, TextSet.decode("lora|false|1.35|0|0|5|true|NaN|1.2", fallback))
        val clamped = TextSet.decode("lora|false|1.35|0|0|5|true|-1.0|8.0", fallback)
        assertEquals(0f, clamped.paragraphSpacing, 0f)
        assertEquals(3f, clamped.firstLineIndent, 0f)
    }
}
