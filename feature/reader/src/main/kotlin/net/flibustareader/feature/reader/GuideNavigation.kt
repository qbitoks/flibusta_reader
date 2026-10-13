package net.flibustareader.feature.reader

internal data class GuideNavigation(
    val page: PageRef? = null,
    val layoutGeneration: Int = -1,
    val index: Int = 0,
    val jumpId: Int = 0,
) {
    fun step(direction: Int, lineCount: Int): GuideStep = when {
        direction > 0 && index < lineCount - 1 -> GuideStep(copy(index = index + 1), TapAction.Handled)
        direction > 0 -> GuideStep(this, TapAction.Next)
        index > 0 -> GuideStep(copy(index = index - 1), TapAction.Handled)
        else -> GuideStep(this, TapAction.Prev)
    }

    fun landed(
        page: PageRef?,
        layoutGeneration: Int,
        lineCount: Int,
        direction: Int? = null,
        reset: Boolean = false,
        jumpId: Int = this.jumpId,
    ): GuideNavigation {
        val last = (lineCount - 1).coerceAtLeast(0)
        val rebuilt = reset || layoutGeneration != this.layoutGeneration || jumpId != this.jumpId
        val row = when {
            rebuilt -> 0
            page == this.page -> index.coerceIn(0, last)
            direction != null && direction < 0 -> last
            else -> 0
        }
        return GuideNavigation(page, layoutGeneration, row, jumpId)
    }
}

internal data class GuideStep(val navigation: GuideNavigation, val action: TapAction)
