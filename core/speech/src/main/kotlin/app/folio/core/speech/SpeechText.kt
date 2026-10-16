package app.folio.core.speech

import app.folio.core.model.Block
import app.folio.core.model.Book
import app.folio.core.model.Position
import app.folio.core.model.TextKind
import java.text.BreakIterator
import java.util.Locale

/** One spoken phrase: text of one block between [start] and [end] (end exclusive, same block). */
data class Utterance(val text: String, val start: Position, val end: Position)

/** Engines refuse longer inputs (Android caps at ~4000 chars); long sentences are split at spaces. */
internal const val MAX_UTTERANCE = 1000

/**
 * Phrases of [book] from the sentence containing [from] to the end, in reading order.
 * Images, separators and preformatted text are skipped; headings are spoken as their own phrase.
 */
fun Book.utterancesFrom(from: Position, locale: Locale = speechLocale()): Sequence<Utterance> = sequence {
    val iterator = BreakIterator.getSentenceInstance(locale)
    for (c in from.chapter.coerceAtLeast(0) until chapters.size) {
        val blocks = chapters[c].blocks
        val firstBlock = if (c == from.chapter) from.block.coerceAtLeast(0) else 0
        for (b in firstBlock until blocks.size) {
            val block = blocks[b] as? Block.Text ?: continue
            if (block.kind == TextKind.Preformatted) continue
            val skipBefore = if (c == from.chapter && b == from.block) from.offset else 0
            for (range in sentenceRanges(block.text, iterator)) {
                if (range.last < skipBefore) continue
                yield(Utterance(block.text.substring(range.first, range.last + 1), Position(c, b, range.first), Position(c, b, range.last + 1)))
            }
        }
    }
}

/** Book language as a locale; the device locale when the book has none. */
fun Book.speechLocale(): Locale =
    metadata.language?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() } ?: Locale.getDefault()

/** Trimmed sentence ranges of [text] (inclusive), each at most [MAX_UTTERANCE] characters. */
internal fun sentenceRanges(text: String, iterator: BreakIterator = BreakIterator.getSentenceInstance()): List<IntRange> {
    val result = ArrayList<IntRange>()
    iterator.setText(text)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (e > s && (s until e).any { text[it].isLetterOrDigit() }) splitLong(text, s, e, result)
        start = end
        end = iterator.next()
    }
    return result
}

private fun splitLong(text: String, start: Int, end: Int, out: MutableList<IntRange>) {
    var s = start
    while (end - s > MAX_UTTERANCE) {
        val limit = s + MAX_UTTERANCE
        var cut = text.lastIndexOf(' ', limit)
        if (cut <= s) cut = limit
        out += s until cut
        s = cut
        while (s < end && text[s].isWhitespace()) s++
    }
    if (end > s) out += s until end
}
