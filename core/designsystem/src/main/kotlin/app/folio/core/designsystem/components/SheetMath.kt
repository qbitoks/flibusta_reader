package app.folio.core.designsystem.components

import kotlin.math.max

/**
 * Pure sheet motion. Offsets are the sheet top in px from the window top: [top] where it rests,
 * [hidden] below the window. Velocity is px/s, positive downwards.
 */
internal object SheetMath {
    /** Whether a released sheet closes: only after a long pull or a deliberate fast swipe. */
    fun closes(offset: Float, velocity: Float, top: Float, hidden: Float, flingVelocity: Float, minTravel: Float, dismissFraction: Float): Boolean {
        if (velocity <= -flingVelocity) return false
        if (velocity >= flingVelocity && offset - top >= minTravel) return true
        return offset - top >= dismissFraction * (hidden - top)
    }

    /** Moves the sheet by a finger [delta]; above [top] it follows ever more stiffly, at most [limit] px. */
    fun drag(offset: Float, delta: Float, top: Float, limit: Float): Float {
        if (delta >= 0f || offset + delta >= top) return offset + delta
        var from = offset
        var rest = delta
        if (from > top) { rest += from - top; from = top }
        val factor = 0.35f * (1f - (top - from) / limit).coerceIn(0f, 1f)
        return max(from + rest * factor, top - limit)
    }
}
