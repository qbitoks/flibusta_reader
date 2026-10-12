package net.flibustareader.core

enum class ReaderTap { PREVIOUS, NEXT, TOGGLE_CONTROLS }
data class SeekCoordinate(val resourceIndex: Int, val progression: Double)
object ReaderInteraction {
    fun bookProgress(index: Int, local: Double, weights: List<Int>): Double {
        require(weights.isNotEmpty())
        val safe = weights.map { it.coerceAtLeast(1).toDouble() }
        val resource = index.coerceIn(safe.indices)
        val fraction = if (local.isFinite()) local.coerceIn(0.0, 1.0) else 0.0
        return (safe.take(resource).sum() + fraction * safe[resource]) / safe.sum()
    }
    fun seek(progress: Double, weights: List<Int>): SeekCoordinate {
        require(weights.isNotEmpty())
        val safe = weights.map { it.coerceAtLeast(1).toDouble() }
        val fraction = if (progress.isFinite()) progress.coerceIn(0.0, 1.0) else 0.0
        var remaining = fraction * safe.sum()
        safe.forEachIndexed { index, weight ->
            if (remaining < weight || index == safe.lastIndex) return SeekCoordinate(index, (remaining / weight).coerceIn(0.0, 1.0))
            remaining -= weight
        }
        error("Empty progress map")
    }
    fun tap(fraction: Double, scroll: Boolean): ReaderTap = when {
        scroll || !fraction.isFinite() -> ReaderTap.TOGGLE_CONTROLS
        fraction < .25 -> ReaderTap.PREVIOUS
        fraction > .75 -> ReaderTap.NEXT
        else -> ReaderTap.TOGGLE_CONTROLS
    }
    fun seek(progress: Double, resourceCount: Int): SeekCoordinate {
        require(resourceCount > 0)
        val clamped = if (progress.isFinite()) progress.coerceIn(0.0, 1.0) else 0.0
        val scaled = clamped * resourceCount
        val index = scaled.toInt().coerceAtMost(resourceCount - 1)
        return SeekCoordinate(index, (scaled - index).coerceIn(0.0, 1.0))
    }
}
