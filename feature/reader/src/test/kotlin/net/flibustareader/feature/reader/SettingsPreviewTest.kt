package net.flibustareader.feature.reader

import net.flibustareader.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SettingsPreviewTest {
    private fun book(vararg blocks: Block) = Book(BookMetadata("Test", emptyList()),
        listOf(Chapter(null, blocks.toList())), emptyList(), emptyMap())

    @Test fun startsAtReadingOffsetAndClipsInlineSpans() {
        val source = book(Block.Text("abcdefghij", listOf(Span(1, 7, Span.BOLD), Span(8, 10, Span.ITALIC))))
        val text = settingsPreviewBlocks(source, Position(0, 0, 4)).single() as Block.Text
        assertEquals("efghij", text.text)
        assertEquals(listOf(Span(0, 3, Span.BOLD), Span(4, 6, Span.ITALIC)), text.spans)
        assertEquals("abcdefghij", (source.chapters[0].blocks[0] as Block.Text).text)
    }

    @Test fun skipsImagesAndContinuesIntoNextChapter() {
        val source = Book(BookMetadata("Test", emptyList()), listOf(
            Chapter(null, listOf(Block.Image("cover"))),
            Chapter(null, listOf(Block.Text("Next chapter"))),
        ), emptyList(), emptyMap())
        assertEquals(listOf(Block.Text("Next chapter")), settingsPreviewBlocks(source, Position.Start))
    }

    @Test fun boundsWorkForVeryLongParagraphs() {
        val result = settingsPreviewBlocks(book(Block.Text("x".repeat(100_000))), Position.Start)
        assertEquals(2000, (result.single() as Block.Text).text.length)
    }

    @Test fun emptyBookHasNoInventedBookText() {
        assertTrue(settingsPreviewBlocks(null, Position.Start).isEmpty())
        assertTrue(settingsPreviewBlocks(book(), Position.Start).isEmpty())
    }

    @Test fun leadingHeadingDoesNotConsumeTheWholePreviewAtLargeFontSizes() {
        val source = book(Block.Text("Chapter", kind = TextKind.Heading1), Block.Text("Body sample"))
        assertEquals(listOf(Block.Text("Body sample")), settingsPreviewBlocks(source, Position.Start))
    }

    @Test fun headingOnlyExcerptIsStillShown() {
        val heading = Block.Text("Chapter", kind = TextKind.Heading1)
        assertEquals(listOf(heading), settingsPreviewBlocks(book(heading), Position.Start))
    }
}
