package net.flibustareader.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideNavigationTest {
    private val first = PageRef(0, 0)
    private val second = PageRef(0, 1)

    @Test fun aFailedBackwardBoundaryTapDoesNotAffectTheNextForwardTurn() {
        val start = GuideNavigation().landed(first, 1, 8)
        val failed = start.step(-1, 8)
        assertEquals(TapAction.Prev, failed.action)
        val stillFirst = failed.navigation.landed(first, 1, 8)
        assertEquals(start.index, stillFirst.index)
        val forward = stillFirst.landed(second, 1, 5, direction = 1)
        assertEquals(0, forward.index)
    }

    @Test fun successfulBackwardSwipesAndBoundaryTapsSelectTheSameLastRow() {
        val start = GuideNavigation().landed(second, 1, 5)
        val swiped = start.landed(first, 1, 8, direction = -1)
        val tapped = start.step(-1, 5).navigation.landed(first, 1, 8, direction = -1)
        assertEquals(7, swiped.index)
        assertEquals(swiped, tapped)
    }

    @Test fun anUncommittedTurnKeepsTheCurrentRow() {
        val start = GuideNavigation().landed(second, 1, 5).step(1, 5).navigation
        val canceled = start.landed(second, 1, 5, direction = -1)
        assertEquals(start, canceled)
    }

    @Test fun successfulForwardTurnsSelectTheFirstRow() {
        val start = GuideNavigation().landed(first, 1, 8).step(1, 8).navigation
        assertEquals(0, start.landed(second, 1, 5, direction = 1).index)
    }

    @Test fun jumpsAndRepaginationResetToTheFirstRow() {
        val start = GuideNavigation().landed(second, 1, 5).step(1, 5).navigation
        assertEquals(0, start.landed(first, 1, 8).index)
        assertEquals(0, start.landed(second, 2, 5).index)
        assertEquals(0, start.landed(second, 1, 5, reset = true).index)
        assertEquals(0, start.landed(second, 1, 5, jumpId = 2).index)
        assertEquals(0, start.landed(first, 2, 8, direction = -1).index)
    }

    @Test fun anEmptyPageHasNoNegativeGuideIndex() {
        val start = GuideNavigation().landed(second, 1, 5)
        assertEquals(0, start.landed(first, 1, 0, direction = -1).index)
    }

    @Test fun ordinaryGuideTapsMoveOnlyOneRowWithoutRequestingATurn() {
        val start = GuideNavigation().landed(first, 1, 8)
        val next = start.step(1, 8)
        assertEquals(TapAction.Handled, next.action)
        assertEquals(1, next.navigation.index)
        val previous = next.navigation.step(-1, 8)
        assertEquals(TapAction.Handled, previous.action)
        assertEquals(start, previous.navigation)
    }
}
