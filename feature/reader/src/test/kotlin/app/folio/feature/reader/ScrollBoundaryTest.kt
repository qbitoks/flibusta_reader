package app.folio.feature.reader

import app.folio.core.data.settings.*
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ScrollBoundaryTest {
    @Test fun legacyNoneUsesProtectedSmoothBoundary() {
        val settings = ReaderSettings(pageTurn = PageTurn.Scroll)
        assertEquals(FooterStyle.Fade, settings.scrollHeaderBoundary)
        assertEquals(FooterStyle.Fade, settings.scrollFooterBoundary)
    }

    @Test fun negativeLegacyGapsCannotShrinkProtectedZones() {
        val settings = ReaderSettings(pageTurn = PageTurn.Scroll,
            headerLayout = LineLayout.HEADER.copy(customize = true, gap = -12, rule = true),
            footerLayout = LineLayout.FOOTER.copy(customize = true, gap = -12, rule = true))
        assertEquals(0, settings.shownHeaderLayout.distanceToText)
        assertEquals(0, settings.shownFooterLayout.distanceToText)
        assertTrue(settings.shownHeaderLayout.rule)
        assertTrue(settings.shownFooterLayout.rule)
    }

    @Test fun footerBoundaryEditPreservesClockOnlyFooterAndLineGeometry() {
        val settings = ReaderSettings(pageTurn = PageTurn.Scroll,
            footer = PageFooter(page = FooterPage.None, clock = true),
            footerLayout = LineLayout.FOOTER.copy(customize = true, sizePct = 140, edge = 24))
            .editLineBoundary(false, FooterStyle.Clip)
        assertEquals(FooterStyle.Clip, settings.scrollFooterBoundary)
        assertEquals(FooterStyle.None, settings.headerUnder)
        assertEquals(FooterStyle.Clip, settings.footer.style)
        assertTrue(settings.shownFooter.clock)
        assertEquals(FooterPage.None, settings.shownFooter.page)
        assertEquals(140, settings.shownFooterLayout.sizePct)
        assertEquals(24, settings.shownFooterLayout.edge)
    }

    @Test fun pagedModeKeepsNegativeGap() {
        val settings = ReaderSettings(headerLayout = LineLayout.HEADER.copy(customize = true, gap = -12))
        assertEquals(-12, settings.shownHeaderLayout.gap)
    }
}
