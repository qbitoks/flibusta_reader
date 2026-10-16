package app.folio.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetMathTest {
    private val top = 100f
    private val hidden = 2000f

    private fun closes(offset: Float, velocity: Float = 0f) =
        SheetMath.closes(offset, velocity, top, hidden, flingVelocity = 5000f, minTravel = 200f, dismissFraction = 0.35f)

    @Test fun aShortPullReturnsToTheTop() {
        assertFalse(closes(500f))
    }

    @Test fun aLongPullCloses() {
        assertTrue(closes(top + 0.35f * (hidden - top)))
    }

    @Test fun aFastFlickWithoutTravelStays() {
        assertFalse(closes(top + 150f, velocity = 9000f))
    }

    @Test fun aFastLongSwipeCloses() {
        assertTrue(closes(top + 250f, velocity = 9000f))
    }

    @Test fun aFastUpwardSwipeKeepsALongPullOpen() {
        assertFalse(closes(top + 1500f, velocity = -9000f))
    }

    @Test fun belowTheTopTheSheetFollowsTheFinger() {
        assertEquals(350f, SheetMath.drag(300f, 50f, top, limit = 100f))
        assertEquals(250f, SheetMath.drag(300f, -50f, top, limit = 100f))
    }

    @Test fun aboveTheTopItResistsAndStopsAtTheLimit() {
        val pulled = SheetMath.drag(top, -50f, top, limit = 100f)
        assertTrue(pulled < top && pulled > top - 50f)
        assertEquals(top - 100f, SheetMath.drag(top - 99.9f, -10_000f, top, limit = 100f), 0.5f)
        assertEquals(top - 100f, SheetMath.drag(top - 100f, -500f, top, limit = 100f), 0.01f)
    }
}
