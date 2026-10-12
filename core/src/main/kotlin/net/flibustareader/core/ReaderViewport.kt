package net.flibustareader.core

data class HorizontalViewport(val left: Int, val width: Int)
object ReaderViewport {
    const val HUD_HEIGHT_DP = 24
    const val HUD_TOUCH_HEIGHT_DP = 48
    fun horizontal(parentWidth: Int, leftInset: Int, rightInset: Int, maxWidth: Int): HorizontalViewport {
        require(parentWidth > 0 && maxWidth > 0)
        val left = leftInset.coerceIn(0, parentWidth - 1)
        val right = rightInset.coerceIn(0, parentWidth - left - 1)
        val available = parentWidth - left - right
        val width = available.coerceAtMost(maxWidth)
        return HorizontalViewport(left + (available - width) / 2, width)
    }
}
