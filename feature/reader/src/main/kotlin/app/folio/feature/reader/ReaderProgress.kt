package app.folio.feature.reader

import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.Position

/** Same character weights as saved book progress; never return an empty or past-end block. */
internal fun Book.readerPositionAtProgress(fraction: Float): Position? {
    if (!fraction.isFinite() || totalChars == 0L) return null
    var remaining = (totalChars * fraction.coerceIn(0f, 1f)).toLong().coerceAtMost(totalChars - 1)
    chapters.forEachIndexed { c, chapter ->
        if (remaining >= chapter.charCount) {
            remaining -= chapter.charCount
        } else {
            chapter.blocks.forEachIndexed { b, block ->
                val length = if (block is Block.Text) block.text.length else 1
                if (remaining < length) return Position(c, b, remaining.toInt())
                remaining -= length
            }
        }
    }
    return null
}

internal data class ReaderProgressPreview(val chapter: String?, val page: Int?)
