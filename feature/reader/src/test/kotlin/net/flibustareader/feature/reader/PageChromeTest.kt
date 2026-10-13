package net.flibustareader.feature.reader

import net.flibustareader.core.data.settings.FooterPage
import net.flibustareader.core.data.settings.PageFooter
import net.flibustareader.core.data.settings.PageHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageChromeTest {
    private val words = FooterWords(
        pageOf = { n, total -> "$n из $total" },
        percent = { "$it %" },
        pagesLeft = { "ещё $it стр." },
        chapterEnd = "конец главы",
    )

    private fun center(footer: PageFooter, number: Int? = 42, total: Int? = 586, progress: Float = 0.07f, left: Int? = 3) =
        footerCenter(footer, number, total, progress, left, words)

    @Test fun bareNumberDoesNotExpandWithKnownTotal() {
        assertEquals("42", center(PageFooter(page = FooterPage.Number)))
    }

    @Test fun pageOptions() {
        assertEquals("42 из 586", center(PageFooter(page = FooterPage.NumberOfTotal)))
        assertEquals("7 %", center(PageFooter(page = FooterPage.Percent)))
        assertEquals("", center(PageFooter(page = FooterPage.None)))
    }

    @Test fun percentStandsInUntilTheBookIsCounted() {
        assertEquals("7 %", center(PageFooter(page = FooterPage.Number), number = null, total = null))
        assertEquals("7 %", center(PageFooter(page = FooterPage.NumberOfTotal), total = null))
        // The number is known but not the total: no «из N» even with the controls up.
        assertEquals("42", center(PageFooter(page = FooterPage.Number), total = null))
    }

    @Test fun pagesLeftInChapterFollowTheNumber() {
        assertEquals("42 · ещё 3 стр.", center(PageFooter(page = FooterPage.Number, pagesLeft = true)))
        assertEquals("42 · конец главы", center(PageFooter(page = FooterPage.Number, pagesLeft = true), left = 0))
        assertEquals("ещё 3 стр.", center(PageFooter(page = FooterPage.None, pagesLeft = true)))
        // Chapter not laid out yet: nothing rather than a guess.
        assertEquals("42", center(PageFooter(page = FooterPage.Number, pagesLeft = true), left = null))
    }

    @Test fun footerIsEmptyOnlyWithEverythingOff() {
        assertTrue(PageFooter(page = FooterPage.None, pagesLeft = false, clock = false, battery = false).isEmpty)
        assertEquals(false, PageFooter(page = FooterPage.None, clock = true, battery = false).isEmpty)
    }

    @Test fun headerFallsBackToTheBookForUntitledChapters() {
        assertNull(headerText(PageHeader.None, "Книга", "Глава"))
        assertEquals("Книга", headerText(PageHeader.Book, "Книга", "Глава"))
        assertEquals("Глава", headerText(PageHeader.Chapter, "Книга", "Глава"))
        assertEquals("Книга", headerText(PageHeader.Chapter, "Книга", null))
        assertEquals("Книга", headerText(PageHeader.Chapter, "Книга", " "))
    }
}
