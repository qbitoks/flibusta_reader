package app.folio.feature.reader

import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.*
import org.junit.Assert.*
import org.junit.Test

class LineBlockGeometryTest {
    @Test fun compactDefaultsKeepExplicitlySavedHeights() {
        assertEquals(36, LineLayout.HEADER.requestedBlockHeight)
        assertEquals(36, LineLayout.FOOTER.requestedBlockHeight)
        val saved = LineLayout(customize = true, blockHeight = 48)
        assertEquals(48, LineLayout.decode(saved.encode(), LineLayout.HEADER).requestedBlockHeight)
    }

    @Test fun zeroGapPlacesTextAtBlockEdgeRegardlessOfRuleAndLabelPosition() {
        for (position in LineVertical.entries) for (rule in listOf(false, true)) {
            val layout = LineLayout(customize = true, blockHeight = 64, textGap = 0,
                vertical = position, rule = rule)
            val geometry = lineBlockGeometry(layout, 20.dp)
            assertEquals(64.dp, geometry.textInset)
            assertEquals(64.dp, geometry.height)
            val settings = ReaderSettings(pageTurn = PageTurn.Scroll, headerLayout = layout, footerLayout = layout)
            assertEquals(64.dp to 64.dp, textInsets(settings, 20.dp, 20.dp))
        }
    }

    @Test fun verticalPositionsUseTheSameBlockAndKeepLabelsInside() {
        val layout = LineLayout(blockHeight = 64, textGap = 10)
        assertEquals(8.dp, lineBlockGeometry(layout.copy(vertical = LineVertical.Edge), 20.dp).labelOffset)
        assertEquals(22.dp, lineBlockGeometry(layout.copy(vertical = LineVertical.Center), 20.dp).labelOffset)
        assertEquals(36.dp, lineBlockGeometry(layout.copy(vertical = LineVertical.Text), 20.dp).labelOffset)
        assertEquals(74.dp, lineBlockGeometry(layout, 20.dp).textInset)
    }

    @Test fun largeLabelsExpandTheBlockEvenWhenRequestedHeightIsSmall() {
        val geometry = lineBlockGeometry(LineLayout(blockHeight = 32, textGap = 0), 60.dp)
        assertEquals(76.dp, geometry.height)
        assertEquals(8.dp, geometry.labelOffset)
    }

    @Test fun currentLayoutsRoundTripAndObsoleteFormatsUseDefaults() {
        assertEquals(LineLayout.HEADER, LineLayout.decode("true|120|80|End|8|-12|true|true|false", LineLayout.HEADER))
        val layout = LineLayout(customize = true, sizePct = 120, gap = -12,
            blockHeight = 72, vertical = LineVertical.Text, textGap = 0)
        assertEquals(layout, LineLayout.decode(layout.encode(), LineLayout.HEADER))
        val shared = ReaderSettings()
        val edited = shared.copy(headerLayout = layout)
        val restored = BookOverrides.decode(BookOverrides.between(shared, edited).encode()).applyTo(shared)
        assertEquals(layout, restored.headerLayout)
    }

    @Test fun boundariesAreIndependentAndDefaultToClip() {
        val settings = ReaderSettings(pageTurn = PageTurn.Scroll).editLineBoundary(true, FooterStyle.Fade)
        assertEquals(FooterStyle.Fade, settings.scrollHeaderBoundary)
        assertEquals(FooterStyle.Clip, settings.scrollFooterBoundary)
        val edited = settings.editLineBoundary(false, FooterStyle.Fade)
        assertEquals(FooterStyle.Fade, edited.scrollHeaderBoundary)
        assertEquals(FooterStyle.Fade, edited.scrollFooterBoundary)
    }

    @Test fun hiddenLinesReleaseTheirBlocksAndClockOnlyFooterKeepsItsBlock() {
        val layout = LineLayout(customize = true, blockHeight = 64, textGap = 0)
        val settings = ReaderSettings(pageTurn = PageTurn.Scroll, header = PageHeader.None,
            footer = PageFooter(page = FooterPage.None), headerLayout = layout, footerLayout = layout)
        assertEquals(12.dp to 20.dp, textInsets(settings, 20.dp, 20.dp))
        assertEquals(12.dp to 64.dp, textInsets(settings.copy(footer = settings.footer.copy(clock = true)), 20.dp, 20.dp))
    }
}
