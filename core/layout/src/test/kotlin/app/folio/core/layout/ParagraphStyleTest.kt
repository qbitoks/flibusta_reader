package app.folio.core.layout

import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.TextKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphStyleTest {
    @Test fun zeroParagraphGapKeepsTheNormalLinePitch() {
        // A 32 px final line has no 8 px leading, unlike lines inside a 1.25-spaced paragraph.
        val lastBaselineToBottom = 7f
        val nextTopToBaseline = 25f
        val seamPitch = lastBaselineToBottom + paragraphGap(32, 1.25f, 0f) + nextTopToBaseline
        assertEquals(40f, seamPitch, 0f)
    }

    @Test fun themeGapIsAddedAfterRestoringTheLinePitch() {
        assertEquals(50f, 32f + paragraphGap(32, 1.25f, 10f), 0f)
        assertEquals(36f, 24f + paragraphGap(24, 1.5f, 0f), 0f)
    }

    @Test fun compressedLineSpacingDoesNotExpandAtParagraphSeams() {
        assertEquals(26f, 32f + paragraphGap(32, 0.8f, 0f), 0f)
        assertEquals(32f, 32f + paragraphGap(32, 1f, 0f), 0f)
    }

    @Test fun bodyIndentCanBeChangedOrRemoved() {
        val body = Block.Text("Абзац")
        assertEquals(2.4f, BlockStyle.of(body, true, 2.4f).indent, 0f)
        assertEquals(0f, BlockStyle.of(body, false, 0f).indent, 0f)
    }

    @Test fun specialBlocksKeepTheirTypography() {
        for (kind in TextKind.entries - TextKind.Body) {
            val block = Block.Text("Текст", kind = kind)
            assertEquals(BlockStyle.of(block, true), BlockStyle.of(block, true, 3f))
        }
        for (align in listOf(Align.Center, Align.End)) {
            assertEquals(0f, BlockStyle.of(Block.Text("Текст", align = align), true, 3f).indent, 0f)
        }
    }
}
