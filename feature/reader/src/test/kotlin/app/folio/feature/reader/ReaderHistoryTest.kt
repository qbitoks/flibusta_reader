package app.folio.feature.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderHistoryTest {
    @Test fun arrowsDisappearAfterFiveSuccessfulTurnsIncludingBacktracking() {
        var visibility = HistoryVisibility().jumped()
        for (i in 0..3) {
            assertTrue(visibility.visible)
            visibility = visibility.read(PageRef(0, i % 2), PageRef(0, (i + 1) % 2), null)
        }
        assertTrue(visibility.visible)
        visibility = visibility.read(PageRef(0, 0), PageRef(0, 1), null)
        assertFalse(visibility.visible)
    }

    @Test fun noopAndMissingLandingsDoNotConsumeTheWindow() {
        val visibility = HistoryVisibility().jumped()
        assertEquals(visibility, visibility.read(PageRef(0, 2), PageRef(0, 2), null))
        assertEquals(visibility, visibility.read(null, PageRef(0, 3), null))
    }

    @Test fun scrollCountsSkippedPagesAndCrossChapterDistance() {
        val visibility = HistoryVisibility().jumped()
        val offsets = intArrayOf(0, 10, 20)
        assertFalse(visibility.read(PageRef(0, 8), PageRef(1, 3), offsets).visible)
        assertEquals(2, visibility.read(PageRef(0, 2), PageRef(0, 5), null).remaining)
    }

    @Test fun anotherJumpRestoresFivePagesAndUnknownBoundaryCountsOne() {
        val visibility = HistoryVisibility().jumped().read(PageRef(0, 0), PageRef(0, 4), null)
        assertEquals(5, visibility.jumped().remaining)
        assertFalse(visibility.read(PageRef(0, 4), PageRef(1, 0), null).visible)
    }

    @Test fun exactPageResolverSkipsEmptyChaptersAndRejectsOutOfRange() {
        val offsets = intArrayOf(0, 3, 3, 7)
        assertEquals(PageRef(0, 0), readerPageRef(1, offsets))
        assertEquals(PageRef(2, 0), readerPageRef(4, offsets))
        assertEquals(PageRef(2, 3), readerPageRef(7, offsets))
        assertNull(readerPageRef(0, offsets))
        assertNull(readerPageRef(8, offsets))
        assertNull(readerPageRef(1, null))
    }

    @Test fun percentInputSupportsDecimalCommaButRejectsInvalidPositions() {
        assertEquals(0.655f, readerPercentInput("65,5")!!, 0.0001f)
        assertEquals(1f, readerPercentInput("100")!!, 0f)
        for (text in listOf("", "NaN", "Infinity", "-1", "101", "abc")) assertNull(readerPercentInput(text))
    }
}
