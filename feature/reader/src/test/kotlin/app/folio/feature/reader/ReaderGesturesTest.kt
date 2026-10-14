package app.folio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGesturesTest {
    private val width = 1080f

    @Test fun outerThirdsTurnPagesAndTheMiddleToggles() {
        assertEquals(-1, tapZone(100f, width))
        assertEquals(-1, tapZone(350f, width))
        assertEquals(0, tapZone(540f, width))
        assertEquals(1, tapZone(730f, width))
        assertEquals(1, tapZone(1000f, width))
    }

    @Test fun aSlightlySmearedTapIsStillATap() {
        assertEquals(Release.Tap, classifyRelease(dx = 0f, dy = 3f, touchSlop = 20f, swipeDown = 360f))
        assertEquals(Release.Tap, classifyRelease(dx = 25f, dy = 20f, touchSlop = 20f, swipeDown = 360f))
    }

    @Test fun onlyALongVerticalSwipeClosesTheBook() {
        assertEquals(Release.SwipeDown, classifyRelease(dx = 30f, dy = 400f, touchSlop = 20f, swipeDown = 360f))
        // Too short, or too diagonal.
        assertEquals(Release.None, classifyRelease(dx = 0f, dy = 250f, touchSlop = 20f, swipeDown = 360f))
        assertEquals(Release.None, classifyRelease(dx = 250f, dy = 400f, touchSlop = 20f, swipeDown = 360f))
    }

    @Test fun releaseSettlesByDistanceOrFling() {
        val fling = 900f
        assertEquals(1, settleDirection(-300f, 0f, width, fling, canNext = true, canPrev = true))
        assertEquals(-1, settleDirection(300f, 0f, width, fling, canNext = true, canPrev = true))
        assertEquals(0, settleDirection(-100f, 0f, width, fling, canNext = true, canPrev = true))
        // A quick flick turns even a little-moved page.
        assertEquals(1, settleDirection(-60f, -2000f, width, fling, canNext = true, canPrev = true))
        // Flinging back against the drag cancels it.
        assertEquals(0, settleDirection(-400f, 1500f, width, fling, canNext = true, canPrev = true))
        // Nothing to turn to.
        assertEquals(0, settleDirection(-600f, -3000f, width, fling, canNext = false, canPrev = true))
    }

    @Test fun turnDurationShrinksWithDistanceLeftAndFingerSpeed() {
        assertEquals(300, turnDuration(remaining = width, width = width, velocity = 0f, full = 300))
        assertEquals(150, turnDuration(remaining = width * 0.5f, width = width, velocity = 0f, full = 300))
        val flung = turnDuration(remaining = width * 0.8f, width = width, velocity = -6000f, full = 300)
        assertTrue("fast fling finishes quicker than a tap turn: $flung", flung < 240)
        assertEquals(90, turnDuration(remaining = 1f, width = width, velocity = 0f, full = 300))
    }
}
