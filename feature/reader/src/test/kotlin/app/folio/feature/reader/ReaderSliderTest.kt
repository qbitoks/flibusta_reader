package app.folio.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.folio.core.data.settings.ReaderTheme
import app.folio.core.designsystem.ReaderPalettes
import org.junit.Assert.*
import org.junit.Test

class ReaderSliderTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun everyStandardThemeHasVisibleSliderInkInBothAppearances() {
        for (dark in listOf(false, true)) for (theme in ReaderTheme.entries) {
            val palette = ReaderPalettes.of(theme, dark)
            assertTrue("$theme dark=$dark", contrast(palette.background, readerSliderInk(palette.background, palette.text)) >= 3f)
        }
    }

    @Test fun softCustomInkGainsContrastWithoutChangingAlreadyReadableInk() {
        val paper = Color(0xFFF0E2C8)
        val brown = Color(0xFF2F2019)
        assertEquals(brown, readerSliderInk(paper, brown))
        assertTrue(contrast(paper, readerSliderInk(paper, Color(0xFFE4D5BC))) >= 3f)
        assertTrue(contrast(Color(0xFF19150C), readerSliderInk(Color(0xFF19150C), Color(0xFF353025))) >= 3f)
    }
}
