package net.flibustareader.core

import org.junit.Assert.*
import org.junit.Test

class ReadingProgressTest {
    @Test fun usesFineGrainedProgressForSingleResourceFb2() {
        assertEquals(27, ReadingProgress.percent(.27, 0.0, 1))
    }
    @Test fun usesWholeBookProgressForMultipleResources() {
        assertEquals(60, ReadingProgress.percent(.2, .6, 5))
    }
    @Test fun doesNotInventProgressWhenUnavailable() {
        assertNull(ReadingProgress.percent(null, null, 1))
    }
}
