package net.flibustareader.core.format

import net.flibustareader.core.model.Block
import net.flibustareader.core.model.Book
import net.flibustareader.core.model.Chapter
import net.flibustareader.core.model.Span
import net.flibustareader.core.model.internalTarget

/**
 * Rewrites raw link targets into their final form: footnote keys stay as they are when a
 * matching footnote exists, other internal links become encoded positions ("@c:b:o"),
 * and links that point nowhere lose their link styling.
 */
internal fun resolveInternalTargets(book: Book, locate: (String) -> net.flibustareader.core.model.Position?): Book {
    fun fix(blocks: List<Block>): List<Block> = blocks.map { block ->
        if (block !is Block.Text || block.spans.none { it.target != null }) return@map block
        block.copy(
            spans = block.spans.map { span ->
                val target = span.target
                when {
                    target == null || target.contains("://") || target.startsWith("mailto:") -> span
                    book.footnotes.containsKey(target) ->
                        span.copy(flags = (span.flags and Span.LINK.inv()) or Span.NOTE_REF)
                    else -> {
                        val position = locate(target)
                        if (position != null) {
                            span.copy(target = internalTarget(position))
                        } else {
                            span.copy(flags = span.flags and (Span.LINK or Span.NOTE_REF).inv(), target = null)
                        }
                    }
                }
            },
        )
    }
    return Book(
        metadata = book.metadata,
        chapters = book.chapters.map { Chapter(it.title, fix(it.blocks), it.anchors) },
        toc = book.toc,
        footnotes = book.footnotes.mapValues { fix(it.value) },
    )
}
