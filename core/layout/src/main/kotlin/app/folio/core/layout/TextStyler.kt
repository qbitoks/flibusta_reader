package app.folio.core.layout

import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import app.folio.core.model.Align
import app.folio.core.model.Block
import app.folio.core.model.Span
import app.folio.core.model.TextKind
import java.util.Locale

/** Typographic treatment of each [TextKind]; sizes and spacing are in em of the base size. */
internal data class BlockStyle(
    val sizeScale: Float = 1f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val mono: Boolean = false,
    val align: Align = Align.Justify,
    val insetStart: Float = 0f,
    val insetEnd: Float = 0f,
    val spaceBefore: Float = 0f,
    val spaceAfter: Float = 0f,
    val indent: Float = 1.6f,
    val keepWithNext: Boolean = false,
) {
    companion object {
        private val styles = mapOf(
            TextKind.Body to BlockStyle(),
            TextKind.Heading1 to BlockStyle(1.45f, bold = true, align = Align.Center, spaceBefore = 1.6f, spaceAfter = 1.1f, indent = 0f, keepWithNext = true),
            TextKind.Heading2 to BlockStyle(1.25f, bold = true, align = Align.Center, spaceBefore = 1.3f, spaceAfter = 0.9f, indent = 0f, keepWithNext = true),
            TextKind.Heading3 to BlockStyle(1.1f, bold = true, align = Align.Center, spaceBefore = 1.1f, spaceAfter = 0.7f, indent = 0f, keepWithNext = true),
            TextKind.Subtitle to BlockStyle(1.0f, bold = true, align = Align.Center, spaceBefore = 0.8f, spaceAfter = 0.6f, indent = 0f, keepWithNext = true),
            TextKind.Epigraph to BlockStyle(0.92f, italic = true, align = Align.Start, insetStart = 0.3f, spaceAfter = 0.2f, indent = 0f),
            TextKind.TextAuthor to BlockStyle(0.92f, italic = true, align = Align.End, insetStart = 0.3f, spaceAfter = 1.0f, indent = 0f),
            TextKind.Poem to BlockStyle(1.0f, align = Align.Start, insetStart = 0.1f, spaceBefore = 0.4f, spaceAfter = 0.8f, indent = 0f),
            TextKind.Cite to BlockStyle(0.95f, insetStart = 0.06f, insetEnd = 0.06f, spaceAfter = 0.3f),
            TextKind.Preformatted to BlockStyle(0.85f, mono = true, align = Align.Start, spaceAfter = 0.5f, indent = 0f),
        )

        fun of(block: Block.Text, justify: Boolean): BlockStyle {
            val base = styles.getValue(block.kind)
            val align = when (block.align) {
                Align.Default -> base.align
                Align.Justify -> Align.Justify
                else -> block.align
            }
            val effective = if (align == Align.Justify && !justify) Align.Start else align
            return base.copy(
                align = effective,
                indent = if (align == Align.Center || align == Align.End) 0f else base.indent,
            )
        }
    }
}

/**
 * Turns blocks into [StaticLayout]s. Holds one [TextPaint] per distinct style so paints
 * can be recoloured in place when the theme changes.
 */
internal class TextStyler(
    private val spec: LayoutSpec,
    typefaces: TypefaceProvider,
    private val ink: Ink,
) {
    val basePx: Float = spec.style.fontSize * spec.density * spec.fontScale

    private val family: Typeface = typefaces.typeface(spec.style.font).let {
        if (spec.style.boldText) Typeface.create(it, 600, false) else it
    }
    private val paints = HashMap<BlockStyle, TextPaint>()
    private val locale: Locale? = spec.language?.let { Locale.forLanguageTag(it) }

    val bodyLineHeight: Float by lazy {
        val p = paintFor(BlockStyle())
        (p.fontMetrics.descent - p.fontMetrics.ascent) * spec.style.lineSpacing
    }

    val allPaints: Collection<TextPaint> get() = paints.values

    fun paintFor(style: BlockStyle): TextPaint = paints.getOrPut(style.copy(align = Align.Default, insetStart = 0f, insetEnd = 0f, spaceBefore = 0f, spaceAfter = 0f, indent = 0f, keepWithNext = false)) {
        TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            val styleBits = (if (style.bold) Typeface.BOLD else 0) or (if (style.italic) Typeface.ITALIC else 0)
            typeface = when {
                style.mono -> Typeface.create(Typeface.MONOSPACE, styleBits)
                styleBits != 0 -> Typeface.create(family, styleBits)
                else -> family
            }
            textSize = basePx * style.sizeScale
            letterSpacing = spec.style.letterSpacing
            if (Build.VERSION.SDK_INT >= 34) wordSpacing = spec.style.wordSpacing * textSize
            locale?.let { textLocale = it }
            color = ink.text
        }
    }

    fun layout(block: Block.Text, style: BlockStyle, width: Int): StaticLayout {
        val paint = paintFor(style)
        val text = spannable(block, style, paint.textSize)
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(
                when (style.align) {
                    Align.Center -> Layout.Alignment.ALIGN_CENTER
                    Align.End -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                },
            )
            .setLineSpacing(0f, spec.style.lineSpacing)
            .setIncludePad(false)
            .setUseLineSpacingFromFallbacks(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(hyphenation())
        // First-line indent via setIndents, not LeadingMarginSpan: with a margin span, justified lines
        // are measured (getPrimaryHorizontal, getSelectionPath) narrower than they are drawn, which
        // shifted selections and highlights left by a growing amount along the line.
        if (style.indent > 0f) builder.setIndents(intArrayOf((style.indent * paint.textSize).toInt(), 0), null)
        if (style.align == Align.Justify) builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
        return builder.build()
    }

    private fun hyphenation(): Int = when {
        !spec.style.hyphenate -> Layout.HYPHENATION_FREQUENCY_NONE
        Build.VERSION.SDK_INT >= 33 -> Layout.HYPHENATION_FREQUENCY_NORMAL_FAST
        else -> Layout.HYPHENATION_FREQUENCY_NORMAL
    }

    private fun spannable(block: Block.Text, style: BlockStyle, sizePx: Float): CharSequence {
        if (block.spans.isEmpty()) return block.text
        val s = SpannableString(block.text)
        val shift = ByteArray(s.length)
        for (span in block.spans) {
            val start = span.start.coerceIn(0, s.length)
            val end = span.end.coerceIn(start, s.length)
            if (start == end) continue
            fun set(what: Any) = s.setSpan(what, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (span.has(Span.BOLD)) set(StyleSpan(Typeface.BOLD))
            if (span.has(Span.ITALIC)) set(StyleSpan(Typeface.ITALIC))
            if (span.has(Span.STRIKE)) set(StrikethroughSpan())
            if (span.has(Span.MONOSPACE)) set(TypefaceSpan("monospace"))
            if (span.has(Span.SMALL)) set(RelativeSizeSpan(0.85f))
            val superscript = span.has(Span.SUPERSCRIPT) || (span.has(Span.NOTE_REF) && !span.has(Span.SUBSCRIPT))
            if (superscript) shift.fill(SHIFT_UP, start, end) else if (span.has(Span.SUBSCRIPT)) shift.fill(SHIFT_DOWN, start, end)
            if (span.has(Span.LINK) || span.has(Span.NOTE_REF)) set(InkLinkSpan(ink))
        }
        // A note link usually wraps a <sup> (or sits in one): two spans over the same text must
        // raise and shrink it once, not twice.
        var i = 0
        while (i < shift.size) {
            val kind = shift[i]
            var j = i + 1
            while (j < shift.size && shift[j] == kind) j++
            if (kind != SHIFT_NONE) {
                s.setSpan(if (kind == SHIFT_UP) SuperscriptSpan() else SubscriptSpan(), i, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.68f), i, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            i = j
        }
        return s
    }
}

/** Link colour looked up at draw time from [Ink]; does not affect measurement. */
internal class InkLinkSpan(private val ink: Ink) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) {
        tp.color = ink.link
    }
}

private const val SHIFT_NONE: Byte = 0
private const val SHIFT_UP: Byte = 1
private const val SHIFT_DOWN: Byte = 2
