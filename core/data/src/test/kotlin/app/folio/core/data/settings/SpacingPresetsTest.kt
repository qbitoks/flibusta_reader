package app.folio.core.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class SpacingPresetsTest {
    @Test fun defaultsAreTheMiddleOfBothCycles() {
        val text = ReaderSettings().textSet
        assertEquals(1, SpacingPresets.index(text.lineSpacing, SpacingPresets.lines))
        assertEquals(1, SpacingPresets.index(text.marginsPct.toFloat(), SpacingPresets.margins))
    }

    @Test fun cyclesWrapAndCustomValuesRestartAtMinimum() {
        for (values in listOf(SpacingPresets.lines, SpacingPresets.margins)) {
            assertEquals(values[1], SpacingPresets.next(values[0], values))
            assertEquals(values[2], SpacingPresets.next(values[1], values))
            assertEquals(values[0], SpacingPresets.next(values[2], values))
            assertEquals(-1, SpacingPresets.index(1.75f, values))
            assertEquals(values[0], SpacingPresets.next(1.75f, values))
        }
    }

    @Test fun manualMarginsDoNotChangeLineSpacingAndSurviveReopen() {
        val shared = ReaderSettings()
        val edited = shared.withText(shared.textSet.copy(marginsPct = 4))
        assertEquals(shared.textSet.lineSpacing, edited.textSet.lineSpacing, 0f)
        assertEquals(-1, SpacingPresets.index(edited.textSet.marginsPct.toFloat(), SpacingPresets.margins))
        val p = mutablePreferencesOf()
        ReaderPreferences.write(p, edited)
        assertEquals(edited, ReaderPreferences.read(p))
        assertEquals(edited, BookOverrides.decode(BookOverrides.between(shared, edited).encode()).applyTo(shared))
        assertEquals(16f, edited.style.horizontalMargin(400f), 0.001f)
        assertEquals(32f, edited.style.horizontalMargin(800f), 0.001f)
    }
}
