package app.folio.core.data.settings

import kotlin.math.abs
import kotlin.math.pow

/** Which side of the guided line the extra lines light: both, the lines to read next, or the read ones. */
enum class GuideSpread { Around, Down, Up }

/** How each extra line dims with distance: line d away keeps [step]^d of the light. */
enum class GuideFalloff(val step: Float) { None(1f), Soft(0.6f), Strong(0.35f) }

/**
 * The lit window of the guide: the guided line plus [extra] lines on the [spread] side(s).
 * `extra = 0` is the classic single line.
 */
data class GuideWindow(
    val spread: GuideSpread = GuideSpread.Down,
    val extra: Int = 0,
    val falloff: GuideFalloff = GuideFalloff.None,
) {
    /** Lines lit above the guided one. */
    val above: Int get() = if (spread == GuideSpread.Down) 0 else extra
    /** Lines lit below the guided one. */
    val below: Int get() = if (spread == GuideSpread.Up) 0 else extra

    /**
     * Light (0..1) of a line [offset] lines below the guided one (negative = above). Fractional offsets
     * come from the gliding plate: past the window's edge the light fades out over one line.
     */
    fun light(offset: Float): Float {
        val inside = offset.coerceIn(-above.toFloat(), below.toFloat())
        val outside = abs(offset - inside)
        return falloff.step.pow(abs(inside)) * (1f - outside).coerceAtLeast(0f)
    }

    /** Lit lines of a page with [count] lines, guided line [anchor]: index to light, ascending. */
    fun weights(anchor: Int, count: Int): List<Pair<Int, Float>> {
        if (count <= 0) return emptyList()
        val at = anchor.coerceIn(0, count - 1)
        return (maxOf(0, at - above)..minOf(count - 1, at + below)).map { it to light((it - at).toFloat()) }
    }

    fun encode(): String = "${spread.name};$extra;${falloff.name}"

    companion object {
        val EXTRA = 0..4

        /** Ready windows of the settings cards, in their order. */
        val LINE = GuideWindow()
        val NEXT = GuideWindow(GuideSpread.Down, 1, GuideFalloff.Strong)
        val BEAM = GuideWindow(GuideSpread.Around, 4, GuideFalloff.Strong)
        val PARAGRAPH = GuideWindow(GuideSpread.Around, 2, GuideFalloff.Soft)
        val BAND = GuideWindow(GuideSpread.Down, 2, GuideFalloff.None)
        val PRESETS = listOf(LINE, BEAM, NEXT, PARAGRAPH, BAND)

        fun decode(value: String?): GuideWindow {
            val parts = value?.split(';') ?: return LINE
            if (parts.size != 3) return LINE
            val spread = runCatching { enumValueOf<GuideSpread>(parts[0]) }.getOrNull() ?: return LINE
            val extra = parts[1].toIntOrNull()?.takeIf { it in EXTRA } ?: return LINE
            val falloff = runCatching { enumValueOf<GuideFalloff>(parts[2]) }.getOrNull() ?: return LINE
            return GuideWindow(spread, extra, falloff)
        }
    }
}
