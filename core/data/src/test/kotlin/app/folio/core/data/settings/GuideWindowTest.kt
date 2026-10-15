package app.folio.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideWindowTest {
    @Test fun singleLineLightsOnlyTheGuidedOne() {
        assertEquals(listOf(3 to 1f), GuideWindow.LINE.weights(3, 10))
    }

    @Test fun paragraphFadesBothSides() {
        val w = GuideWindow.PARAGRAPH.weights(3, 10)
        assertEquals(listOf(1, 2, 3, 4, 5), w.map { it.first })
        assertEquals(1f, w[2].second, 0f)
        assertEquals(0.6f, w[1].second, 1e-6f)
        assertEquals(0.36f, w[4].second, 1e-6f)
    }

    @Test fun bandStaysEvenAndStopsAtThePageEnd() {
        assertEquals(listOf(8 to 1f, 9 to 1f), GuideWindow.BAND.weights(8, 10))
    }

    @Test fun upLightsReadLines() {
        val w = GuideWindow(GuideSpread.Up, 2, GuideFalloff.Strong).weights(1, 10)
        assertEquals(listOf(0, 1), w.map { it.first })
    }

    @Test fun lightFadesOutPastTheEdgeWhileGliding() {
        assertEquals(0.5f, GuideWindow.LINE.light(0.5f), 1e-6f)
        assertEquals(0f, GuideWindow.LINE.light(1f), 0f)
        assertEquals(0.35f * 0.5f, GuideWindow.NEXT.light(1.5f), 1e-6f)
    }

    @Test fun codecRoundTripsAndRejectsJunk() {
        val w = GuideWindow(GuideSpread.Around, 3, GuideFalloff.Soft)
        assertEquals(w, GuideWindow.decode(w.encode()))
        assertEquals(GuideWindow.LINE, GuideWindow.decode(null))
        assertEquals(GuideWindow.LINE, GuideWindow.decode("Around;9;Soft"))
        assertEquals(GuideWindow.LINE, GuideWindow.decode("x"))
    }

    @Test fun bookKeepsItsOwnWindow() {
        val shared = ReaderSettings()
        val own = BookOverrides.between(shared, shared.copy(guideLine = true, guideWindow = GuideWindow.PARAGRAPH))
        assertEquals(GuideWindow.PARAGRAPH, own.applyTo(shared).guideWindow)
        assertEquals(true, own.applyTo(shared).guideLine)
    }
}
