package net.flibustareader.core

import org.junit.Assert.*
import org.junit.Test

class ReaderInteractionTest {
    @Test fun centerTogglesControlsAndPageEdgesNavigate() {
        assertEquals(ReaderTap.PREVIOUS, ReaderInteraction.tap(.1, false))
        assertEquals(ReaderTap.TOGGLE_CONTROLS, ReaderInteraction.tap(.25, false))
        assertEquals(ReaderTap.TOGGLE_CONTROLS, ReaderInteraction.tap(.5, false))
        assertEquals(ReaderTap.TOGGLE_CONTROLS, ReaderInteraction.tap(.75, false))
        assertEquals(ReaderTap.NEXT, ReaderInteraction.tap(.9, false))
    }
    @Test fun tapsNeverPageInScrollingMode() {
        assertEquals(ReaderTap.TOGGLE_CONTROLS, ReaderInteraction.tap(.05, true))
        assertEquals(ReaderTap.TOGGLE_CONTROLS, ReaderInteraction.tap(.95, true))
    }
    @Test fun seekingSingleResourceFb2PreservesWholeBookFraction() {
        assertEquals(SeekCoordinate(0, .65), ReaderInteraction.seek(.65, 1))
    }
    @Test fun seekingAcrossResourcesHasSafeEndBoundary() {
        assertEquals(SeekCoordinate(2, .5), ReaderInteraction.seek(.5, 5))
        assertEquals(SeekCoordinate(4, 1.0), ReaderInteraction.seek(1.0, 5))
        assertEquals(SeekCoordinate(0, 0.0), ReaderInteraction.seek(-5.0, 5))
    }
    @Test fun bookProgressAndSeekAgreeForUnequalChapterLengths() {
        val weights = listOf(2, 8)
        assertEquals(.6, ReaderInteraction.bookProgress(1, .5, weights), .0001)
        assertEquals(SeekCoordinate(1, .5), ReaderInteraction.seek(.6, weights))
    }
}
