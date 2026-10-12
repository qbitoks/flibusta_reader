package net.flibustareader.core

enum class ReaderIndicator { CLOCK, PROGRESS, BATTERY }
enum class ReaderColor { WHITE, SEPIA, GRAY, BLACK }
enum class ReaderFont { BOOK, SANS, MONO }

data class ReaderOptions(
    val color: ReaderColor = ReaderColor.SEPIA,
    val font: ReaderFont = ReaderFont.BOOK,
    val fontSize: Double = 1.1,
    val lineHeight: Double = 1.5,
    val margins: Double = 1.2,
    val scroll: Boolean = false,
    val systemBrightness: Boolean = true,
    val brightness: Double = .65,
    val clockVisible: Boolean = true,
    val progressVisible: Boolean = true,
    val batteryVisible: Boolean = true
) {
    fun toggle(indicator: ReaderIndicator): ReaderOptions = when (indicator) {
        ReaderIndicator.CLOCK -> copy(clockVisible = !clockVisible)
        ReaderIndicator.PROGRESS -> copy(progressVisible = !progressVisible)
        ReaderIndicator.BATTERY -> copy(batteryVisible = !batteryVisible)
    }
    fun normalized(): ReaderOptions = copy(
        fontSize = fontSize.safe(.7, 2.5, 1.1),
        lineHeight = lineHeight.safe(1.1, 2.2, 1.5),
        margins = margins.safe(.3, 2.5, 1.2),
        brightness = brightness.safe(.05, 1.0, .65)
    )
    private fun Double.safe(min: Double, max: Double, fallback: Double) = if (isFinite()) coerceIn(min, max) else fallback
}
