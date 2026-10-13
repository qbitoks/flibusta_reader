package net.flibustareader.feature.reader

import net.flibustareader.core.model.Block
import net.flibustareader.core.model.Book
import net.flibustareader.core.model.Position

/** A bounded excerpt at the opening position; never changes the reader's position or source blocks. */
internal fun settingsPreviewBlocks(book: Book?, position: Position): List<Block> {
    if (book == null) return emptyList()
    val result = mutableListOf<Block>()
    var remaining = 2000
    chapters@ for (chapter in position.chapter.coerceAtLeast(0)..book.chapters.lastIndex) {
        val blocks = book.chapters[chapter].blocks
        val first = if (chapter == position.chapter) position.block.coerceAtLeast(0) else 0
        for (index in first..blocks.lastIndex) {
            val block = blocks[index] as? Block.Text ?: continue
            val start = if (chapter == position.chapter && index == position.block) position.offset.coerceIn(0, block.text.length) else 0
            val end = minOf(block.text.length, start + remaining)
            if (start == end) continue
            result += block.copy(text = block.text.substring(start, end), spans = block.spans.mapNotNull {
                val left = maxOf(start, it.start)
                val right = minOf(end, it.end)
                if (left < right) it.copy(start = left - start, end = right - start) else null
            })
            remaining -= end - start
            if (remaining == 0 || result.size == 4) break@chapters
        }
    }
    // A large chapter heading can consume the entire compact viewport; prefer the following text.
    return result.dropWhile { (it as Block.Text).kind.isHeading }.ifEmpty { result }
}
