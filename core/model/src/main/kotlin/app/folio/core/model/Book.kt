package app.folio.core.model

/**
 * Fully parsed, format-independent book. Produced once by a format parser and then
 * cached in binary form (see [BookCodec]) so subsequent opens skip XML parsing.
 */
class Book(
    val metadata: BookMetadata,
    val chapters: List<Chapter>,
    val toc: List<TocEntry>,
    /** Footnote bodies keyed by note id (FB2 notes, EPUB asides). */
    val footnotes: Map<String, List<Block>>,
) {
    val totalChars: Long by lazy { chapters.sumOf { it.charCount.toLong() } }
}

data class BookMetadata(
    val title: String,
    val authors: List<String>,
    val language: String? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val description: String? = null,
    /** Resource id of the cover image, if the book declares one. */
    val coverResource: String? = null,
)

class Chapter(
    val title: String?,
    val blocks: List<Block>,
    /** Anchor id → block index inside this chapter, used for links and TOC. */
    val anchors: Map<String, Int> = emptyMap(),
) {
    val charCount: Int by lazy {
        blocks.sumOf { if (it is Block.Text) it.text.length else 1 }
    }
}

data class TocEntry(
    val title: String,
    val position: Position,
    val children: List<TocEntry> = emptyList(),
)
