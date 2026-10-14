package app.folio.feature.reader

import app.folio.core.data.settings.*
import org.junit.Assert.*
import org.junit.Test

class PageLineEditsTest {
    @Test fun clockOnlyStartsFromVisibleFooterWithoutRestoringDisabledValues() {
        val settings = ReaderSettings(
            footer = PageFooter(page = FooterPage.None, battery = true),
            footerLayout = LineLayout.FOOTER.copy(sizePct = 140),
        ).editFooterContent { it.copy(clock = true) }
        assertTrue(settings.shownFooter.clock)
        assertFalse(settings.shownFooter.battery)
        assertEquals(FooterPage.None, settings.shownFooter.page)
        assertEquals(100, settings.shownFooterLayout.sizePct)
        assertFalse(settings.shownFooter.isEmpty)
    }

    @Test fun editingAnActiveLinePreservesItsOtherSettings() {
        val settings = ReaderSettings(
            footer = PageFooter(clock = true),
            footerLayout = LineLayout.FOOTER.copy(customize = true, sizePct = 120, gap = 8),
        ).editFooterLine { it.copy(opacityPct = 80) }
        assertTrue(settings.shownFooter.clock)
        assertEquals(120, settings.footerLayout.sizePct)
        assertEquals(8, settings.footerLayout.gap)
        assertEquals(80, settings.footerLayout.opacityPct)
    }

    @Test fun editingHeaderStartsFromVisibleLayout() {
        val settings = ReaderSettings(
            headerLayout = LineLayout.HEADER.copy(sizePct = 140, uppercase = true),
            headerUnder = FooterStyle.Fade,
        ).editHeaderLine { it.copy(edge = 4) }
        assertEquals(100, settings.shownHeaderLayout.sizePct)
        assertFalse(settings.shownHeaderLayout.uppercase)
        assertEquals(4, settings.shownHeaderLayout.edge)
        assertEquals(FooterStyle.None, settings.shownHeaderUnder)
    }
}
