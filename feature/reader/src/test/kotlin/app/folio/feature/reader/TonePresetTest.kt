package app.folio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonePresetTest {
    @Test fun exactValuesSelectPreset() {
        TonePreset.entries.forEach { assertEquals(it, TonePreset.of(it.warmth, it.contrast)) }
    }

    @Test fun defaultsAreNormal() = assertEquals(TonePreset.Normal, TonePreset.of(0, 0))

    @Test fun otherValuesAreCustom() {
        assertNull(TonePreset.of(5, 1))
        assertNull(TonePreset.of(-10, 10))
    }

    @Test fun presetsAreDistinct() =
        assertEquals(TonePreset.entries.size, TonePreset.entries.map { it.warmth to it.contrast }.toSet().size)
}
