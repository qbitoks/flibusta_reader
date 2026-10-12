package net.flibustareader.core

import org.junit.Assert.*
import org.junit.Test

class ReaderOptionsTest {
    @Test fun indicatorTogglesAreIndependentAndReversible() {
        val original = ReaderOptions(scroll = true, fontSize = 1.4)
        for (indicator in ReaderIndicator.entries) {
            val hidden = original.toggle(indicator)
            assertEquals(indicator != ReaderIndicator.CLOCK, hidden.clockVisible)
            assertEquals(indicator != ReaderIndicator.PROGRESS, hidden.progressVisible)
            assertEquals(indicator != ReaderIndicator.BATTERY, hidden.batteryVisible)
            assertEquals(original, hidden.toggle(indicator))
        }
    }
    @Test fun allIndicatorsCanBeHiddenWithoutResettingReadingPreferences() {
        val options = ReaderOptions(color = ReaderColor.GRAY, margins = 1.8)
            .toggle(ReaderIndicator.CLOCK).toggle(ReaderIndicator.PROGRESS).toggle(ReaderIndicator.BATTERY)
        assertFalse(options.clockVisible || options.progressVisible || options.batteryVisible)
        assertEquals(ReaderColor.GRAY, options.color)
        assertEquals(1.8, options.margins, .001)
    }
    @Test fun invalidStoredValuesAreClampedBeforeReachingNavigator() {
        val options = ReaderOptions(fontSize = -9.0, lineHeight = Double.NaN, margins = 999.0, brightness = Double.POSITIVE_INFINITY).normalized()
        assertEquals(.7, options.fontSize, .001)
        assertEquals(1.5, options.lineHeight, .001)
        assertEquals(2.5, options.margins, .001)
        assertEquals(.65, options.brightness, .001)
    }
}
