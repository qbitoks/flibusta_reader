package net.flibustareader.feature.reader

/** Next step for the chapters held by the scroll list: one chapter to add, and which ones to keep. */
internal data class ScrollWindowPlan(val load: Int?, val keep: IntRange)

/**
 * Keeps a laid-out chapter ready past each visible edge, so scrolling never runs into the end of
 * the list, and lets go of chapters more than [SCROLL_KEEP] away from what is on screen.
 * [loaded] is contiguous, so the chapter to load is always next to it (forward first).
 */
internal fun planScrollWindow(firstVisible: Int, lastVisible: Int, loaded: IntRange, chapterCount: Int): ScrollWindowPlan {
    val last = chapterCount - 1
    val keep = (firstVisible - SCROLL_KEEP).coerceAtLeast(0)..(lastVisible + SCROLL_KEEP).coerceAtMost(last)
    val load = when {
        loaded.last < (lastVisible + 1).coerceAtMost(last) -> loaded.last + 1
        loaded.first > (firstVisible - 1).coerceAtLeast(0) -> loaded.first - 1
        else -> null
    }
    return ScrollWindowPlan(load, keep)
}

internal const val SCROLL_KEEP = 2
