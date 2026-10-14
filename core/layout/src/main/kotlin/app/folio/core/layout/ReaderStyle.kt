package app.folio.core.layout

import android.graphics.Typeface

/** User-controlled typography. Every field affects layout, so it is part of the page cache key. */
data class ReaderStyle(
    val font: String = "literata",
    /** Base font size in sp. */
    val fontSize: Float = 19f,
    /** Multiplier over the font's natural line height. */
    val lineSpacing: Float = 1.25f,
    /** Extra tracking in em. */
    val letterSpacing: Float = 0f,
    /** Extra word spacing in em (applied on API 34+, where Paint supports it). */
    val wordSpacing: Float = 0f,
    /** Horizontal page margin in dp. */
    val margin: Float = 28f,
    val justify: Boolean = true,
    val hyphenate: Boolean = true,
    val boldText: Boolean = false,
    /** Extra gap between body paragraphs, in body line heights. */
    val paragraphSpacing: Float = 0f,
    /** First-line indent of body paragraphs, in em. */
    val firstLineIndent: Float = 1.6f,
)

/** Resolves a [ReaderStyle.font] key to a typeface family that has real bold/italic faces. */
fun interface TypefaceProvider {
    fun typeface(font: String): Typeface
}

/** Resolves image intrinsic size in pixels; null when the resource is missing or undecodable. */
fun interface ImageSizer {
    fun size(resource: String): IntSizePx?
}

data class IntSizePx(val width: Int, val height: Int)

/**
 * Theme colours read at draw time. Spans reference this holder instead of baking colours
 * into the text, so switching themes only repaints and never re-lays out a page.
 */
class Ink {
    @Volatile var text: Int = 0xFF1C1C1E.toInt()
    @Volatile var secondary: Int = 0xFF6E6E73.toInt()
    @Volatile var link: Int = 0xFF0A84FF.toInt()
    @Volatile var highlight: Int = 0x55FFD60A
}
