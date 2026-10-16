package app.folio.core.format

import app.folio.core.model.Block
import app.folio.core.model.Chapter
import app.folio.core.model.Position
import app.folio.core.model.TextKind
import app.folio.core.model.TocEntry
import java.io.InputStream

/** Project limit for one unpacked book file (FB2, zipped TXT/HTML/RTF, DOCX part, MOBI text). */
internal const val MAX_UNPACKED_BYTES = 50L * 1024 * 1024

/** Characters after which a long run without headings starts a new chapter, so pagination stays fast. */
internal const val CHAPTER_CHARS = 64 * 1024

internal fun readBounded(input: InputStream): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        if (out.size() + n > MAX_UNPACKED_BYTES) throw UnsupportedBookException("Book is larger than 50 MiB")
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

internal class SplitBook(val chapters: List<Chapter>, val toc: List<TocEntry>, private val starts: IntArray) {
    /** Position of a block given by its index in the flat, unsplit list. */
    fun position(flatIndex: Int): Position {
        var chapter = starts.binarySearch(flatIndex)
        if (chapter < 0) chapter = -chapter - 2
        chapter = chapter.coerceIn(0, chapters.lastIndex)
        return Position(chapter, flatIndex - starts[chapter])
    }
}

/**
 * Splits a single flat run of blocks (one HTML page, an RTF or DOCX body) into chapters:
 * at every first- and second-level heading, and after [CHAPTER_CHARS] without one.
 * Headings become the table of contents.
 */
internal fun splitChapters(blocks: List<Block>, anchors: Map<String, Int> = emptyMap()): SplitBook {
    val starts = ArrayList<Int>()
    var chars = 0
    blocks.forEachIndexed { i, block ->
        val text = block as? Block.Text
        val major = text != null && (text.kind == TextKind.Heading1 || text.kind == TextKind.Heading2)
        val previousHeading = i > 0 && (blocks[i - 1] as? Block.Text)?.kind?.isHeading == true
        if (starts.isEmpty() || (major && !previousHeading && chars > 0) || (chars >= CHAPTER_CHARS && text?.kind?.isHeading != true)) {
            starts += i
            chars = 0
        }
        chars += text?.text?.length ?: 1
    }
    if (starts.isEmpty()) starts += 0
    val startArray = starts.toIntArray()
    val chapters = ArrayList<Chapter>()
    val toc = ArrayList<TocEntry>()
    for ((c, start) in startArray.withIndex()) {
        val end = if (c + 1 < startArray.size) startArray[c + 1] else blocks.size
        val part = if (blocks.isEmpty()) listOf<Block>(Block.Text(" ")) else blocks.subList(start, end)
        val localAnchors = anchors.filterValues { it in start until end }.mapValues { it.value - start }
        val heading = part.firstOrNull { it is Block.Text && it.kind.isHeading } as Block.Text?
        chapters += Chapter(heading?.text?.replace('\n', ' '), part.toList(), localAnchors)
        part.forEachIndexed { b, block ->
            if (block is Block.Text && (block.kind == TextKind.Heading1 || block.kind == TextKind.Heading2)) {
                toc += TocEntry(block.text.replace('\n', ' ').trim(), Position(c, b))
            }
        }
    }
    return SplitBook(chapters, toc, startArray)
}
