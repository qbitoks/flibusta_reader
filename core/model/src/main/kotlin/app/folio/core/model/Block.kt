package app.folio.core.model

sealed interface Block {

    /** Any run of text: body paragraph, heading, epigraph line, poem verse and so on. */
    data class Text(
        val text: String,
        val spans: List<Span> = emptyList(),
        val kind: TextKind = TextKind.Body,
        val align: Align = Align.Default,
    ) : Block

    data class Image(
        val resource: String,
        val caption: String? = null,
    ) : Block

    /** Explicit scene break: FB2 `<empty-line/>`, `<hr/>` and similar. */
    data object Separator : Block
}

enum class TextKind {
    Body,
    Heading1,
    Heading2,
    Heading3,
    Subtitle,
    Epigraph,
    TextAuthor,
    Poem,
    Cite,
    Preformatted;

    val isHeading: Boolean get() = this == Heading1 || this == Heading2 || this == Heading3

    companion object {
        fun heading(level: Int): TextKind = when {
            level <= 1 -> Heading1
            level == 2 -> Heading2
            else -> Heading3
        }
    }
}

enum class Align { Default, Start, Center, End, Justify }

/**
 * Inline style over `[start, end)` of the parent [Block.Text]. [flags] is a bitset of
 * [Span] constants; [target] is set for links and footnote references.
 */
data class Span(
    val start: Int,
    val end: Int,
    val flags: Int,
    val target: String? = null,
) {
    fun has(flag: Int): Boolean = flags and flag != 0

    companion object {
        const val BOLD = 1
        const val ITALIC = 1 shl 1
        const val SUPERSCRIPT = 1 shl 2
        const val SUBSCRIPT = 1 shl 3
        const val STRIKE = 1 shl 4
        const val MONOSPACE = 1 shl 5
        const val LINK = 1 shl 6
        const val NOTE_REF = 1 shl 7
        const val SMALL = 1 shl 8
    }
}
