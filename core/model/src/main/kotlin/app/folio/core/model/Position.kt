package app.folio.core.model

/**
 * A layout-independent location in a book. Survives font, size and screen changes,
 * so the reader always reopens at the same sentence.
 */
data class Position(
    val chapter: Int,
    val block: Int = 0,
    val offset: Int = 0,
) : Comparable<Position> {

    override fun compareTo(other: Position): Int = compareValuesBy(this, other, { it.chapter }, { it.block }, { it.offset })

    fun encode(): String = "$chapter:$block:$offset"

    companion object {
        val Start = Position(0, 0, 0)

        fun decode(value: String?): Position? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 3) return null
            return Position(
                parts[0].toIntOrNull() ?: return null,
                parts[1].toIntOrNull() ?: return null,
                parts[2].toIntOrNull() ?: return null,
            )
        }
    }
}

private const val INTERNAL_PREFIX = "@"

/** Encodes an in-book link destination for [Span.target]. */
fun internalTarget(position: Position): String = INTERNAL_PREFIX + position.encode()

/** Decodes a [Span.target] produced by [internalTarget]; null for external URLs and footnote keys. */
fun parseInternalTarget(target: String): Position? =
    if (target.startsWith(INTERNAL_PREFIX)) Position.decode(target.substring(1)) else null

/**
 * Text shown in the footnote popup. Explicit footnotes (FB2 notes, EPUB asides) are used
 * directly; otherwise the target paragraph and the ones following it up to the next
 * heading or anchored block are shown, which matches how converted books lay out notes.
 */
fun Book.footnoteBlocks(target: String): List<Block> {
    footnotes[target]?.let { return it }
    val position = parseInternalTarget(target) ?: return emptyList()
    val chapter = chapters.getOrNull(position.chapter) ?: return emptyList()
    val anchored = chapter.anchors.values.toHashSet()
    val result = ArrayList<Block>()
    var chars = 0
    var i = position.block
    // Continuation paragraphs are included, but a bounded amount: a "note" that is really
    // a link into running text must not turn into a page-long popup.
    while (i < chapter.blocks.size && result.size < 6 && chars < 1200) {
        val block = chapter.blocks[i]
        if (i > position.block && (i in anchored || (block is Block.Text && block.kind.isHeading))) break
        result += block
        if (block is Block.Text) chars += block.text.length
        i++
    }
    return result
}

/** Fraction of the book (0..1) read at [position], weighted by characters. */
fun Book.progressAt(position: Position): Float {
    if (totalChars == 0L) return 0f
    var chars = 0L
    for (i in 0 until position.chapter.coerceAtMost(chapters.size)) chars += chapters[i].charCount
    val chapter = chapters.getOrNull(position.chapter) ?: return 1f
    for (i in 0 until position.block.coerceAtMost(chapter.blocks.size)) {
        val b = chapter.blocks[i]
        chars += if (b is Block.Text) b.text.length else 1
    }
    chars += position.offset
    return (chars.toDouble() / totalChars).toFloat().coerceIn(0f, 1f)
}
