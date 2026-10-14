package app.folio.feature.reader

import app.folio.core.data.settings.FooterPage
import org.junit.Assert.assertEquals
import org.junit.Test

class FooterContentOptionsTest {
    @Test fun progressCyclesThroughEveryFormatAndReturnsToTheStart() {
        var page = FooterPage.NumberOfTotal
        val visited = List(4) { nextFooterPage(page).also { page = it } }
        assertEquals(listOf(FooterPage.Percent, FooterPage.Number, FooterPage.None, FooterPage.NumberOfTotal), visited)
    }

    @Test fun normalPhoneWidthFitsFourControls() {
        assertEquals(4, footerContentColumns(296f, 1f))
        assertEquals(4, footerContentColumns(400f, 1f))
    }

    @Test fun narrowPanelsAndLargeTextWrapButWidePanelsStillFit() {
        assertEquals(2, footerContentColumns(220f, 1f))
        assertEquals(2, footerContentColumns(296f, 1.5f))
        assertEquals(4, footerContentColumns(600f, 1.5f))
    }
}
