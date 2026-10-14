package app.folio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollWindowTest {
    @Test fun loadsNextChapterAsSoonAsAChapterIsOpened() {
        val plan = planScrollWindow(firstVisible = 5, lastVisible = 5, loaded = 5..5, chapterCount = 20)
        assertEquals(6, plan.load)
    }

    @Test fun loadsPreviousChapterOnceTheNextIsReady() {
        val plan = planScrollWindow(firstVisible = 5, lastVisible = 5, loaded = 5..6, chapterCount = 20)
        assertEquals(4, plan.load)
    }

    @Test fun nothingToLoadWhenBothNeighboursAreReady() {
        assertNull(planScrollWindow(5, 5, loaded = 4..6, chapterCount = 20).load)
    }

    @Test fun shortChaptersOnScreenLoadPastTheLastVisible() {
        // Chapters 5–7 fit on one screen: the one after 7 is needed, not after 5.
        assertEquals(8, planScrollWindow(5, 7, loaded = 4..7, chapterCount = 20).load)
    }

    @Test fun stopsAtBookEdges() {
        assertNull(planScrollWindow(0, 0, loaded = 0..1, chapterCount = 2).load)
        assertEquals(0..1, planScrollWindow(0, 0, loaded = 0..1, chapterCount = 2).keep)
    }

    @Test fun dropsChaptersFarFromTheScreen() {
        // A long scroll forward left chapters 3–5 behind.
        val plan = planScrollWindow(firstVisible = 9, lastVisible = 9, loaded = 3..10, chapterCount = 20)
        assertEquals(7..11, plan.keep)
        assertNull(plan.load)
    }
}
