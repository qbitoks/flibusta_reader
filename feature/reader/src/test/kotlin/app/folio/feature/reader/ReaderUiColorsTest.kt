package app.folio.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.folio.core.data.settings.CustomColors
import app.folio.core.data.settings.ReaderTheme
import app.folio.core.designsystem.ReaderPalettes
import app.folio.core.designsystem.uiColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderUiColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private val palettes = ReaderTheme.entries.flatMap { theme ->
        listOf(false, true).flatMap { dark ->
            listOf(ReaderPalettes.of(theme, dark), ReaderPalettes.custom(CustomColors(base = theme, warmth = 10, contrast = -10), dark))
                .map { "$theme dark=$dark" to it }
        }
    }

    @Test
    fun `chrome labels stay readable on every reader surface`() {
        for ((name, palette) in palettes) {
            val c = palette.uiColors()
            for ((role, surface) in listOf("surface" to c.surface, "card" to c.card, "capsule" to c.capsule, "background" to c.background)) {
                val ratio = contrast(c.label, surface)
                assertTrue("$name label on $role: $ratio", ratio >= 4.5f)
            }
        }
    }

    @Test
    fun `chrome takes the page tone`() {
        val calm = ReaderPalettes.of(ReaderTheme.Calm, false)
        val c = calm.uiColors()
        // A sepia page gets warm sheets, not the app's neutral white or grey.
        assertTrue(c.surface.red > c.surface.blue + 0.05f)
        assertTrue(c.card.red > c.card.blue + 0.03f)
    }
}
