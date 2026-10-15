package app.folio.feature.reader

import kotlin.math.abs

/** Counts reading landings only; jumps reset this window without clearing navigation history. */
internal data class HistoryVisibility(val remaining: Int = 0) {
    val visible: Boolean get() = remaining > 0
    fun jumped(): HistoryVisibility = HistoryVisibility(5)

    fun read(from: PageRef?, to: PageRef?, offsets: IntArray?): HistoryVisibility {
        if (!visible || from == null || to == null || from == to) return this
        val distance = when {
            from.chapter == to.chapter -> abs(to.index - from.index)
            offsets?.getOrNull(from.chapter) != null && offsets.getOrNull(to.chapter) != null ->
                abs(offsets[to.chapter] + to.index - offsets[from.chapter] - from.index)
            else -> 1
        }
        return copy(remaining = (remaining - distance).coerceAtLeast(0))
    }
}

/** Global page numbers are one-based; repeated chapter offsets denote empty chapters. */
internal fun readerPageRef(page: Int, offsets: IntArray?): PageRef? {
    if (offsets == null || offsets.size < 2 || page !in 1..offsets.last()) return null
    val target = page - 1
    var low = 0
    var high = offsets.lastIndex
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (offsets[mid] <= target) low = mid else high = mid - 1
    }
    return PageRef(low, target - offsets[low])
}

internal fun readerPercentInput(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()
    ?.takeIf { it.isFinite() && it in 0f..100f }?.div(100f)
