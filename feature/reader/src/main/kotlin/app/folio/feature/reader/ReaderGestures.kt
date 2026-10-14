package app.folio.feature.reader

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Share of the width on each side where a tap turns the page; the middle toggles the controls. */
internal const val EDGE_TAP = 1f / 3f

/** -1 for the left edge zone (back), +1 for the right one (forward), 0 for the middle. */
internal fun tapZone(x: Float, width: Float): Int = when {
    x < width * EDGE_TAP -> -1
    x > width * (1 - EDGE_TAP) -> 1
    else -> 0
}

internal enum class Release { Tap, SwipeDown, None }

/**
 * What a finger lifted without a horizontal drag meant, from its total movement. A tap may smear a
 * little; closing the book takes a long, clearly vertical swipe so a sloppy tap never does it.
 */
internal fun classifyRelease(dx: Float, dy: Float, touchSlop: Float, swipeDown: Float): Release = when {
    dy > swipeDown && dy > abs(dx) * 2f -> Release.SwipeDown
    hypot(dx, dy) < touchSlop * 2.5f -> Release.Tap
    else -> Release.None
}

/**
 * Where a released drag goes: +1 forward, -1 back, 0 back into place. A fling decides even when
 * the page moved little; a fling against the drag cancels it rather than turning the other way.
 */
internal fun settleDirection(offset: Float, velocity: Float, width: Float, fling: Float, canNext: Boolean, canPrev: Boolean): Int = when {
    offset < 0f && (offset < -width * 0.2f || velocity < -fling) && velocity < fling * 0.45f && canNext -> 1
    offset > 0f && (offset > width * 0.2f || velocity > fling) && velocity > -fling * 0.45f && canPrev -> -1
    else -> 0
}

/**
 * Duration of the rest of a turn: a full turn takes [full] ms, a page already dragged most of the
 * way takes proportionally less, and a fling finishes at least at the finger's speed.
 */
internal fun turnDuration(remaining: Float, width: Float, velocity: Float, full: Int): Int {
    val byDistance = full * (remaining / width)
    val byVelocity = if (abs(velocity) > 1f) remaining / abs(velocity) * 1000f * 1.5f else Float.MAX_VALUE
    return min(byDistance, byVelocity).roundToInt().coerceIn(MIN_TURN_MS, full)
}

private const val MIN_TURN_MS = 90
