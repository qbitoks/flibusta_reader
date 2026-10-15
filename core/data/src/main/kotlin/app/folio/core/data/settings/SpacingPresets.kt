package app.folio.core.data.settings

import kotlin.math.abs

/** The cards and sliders edit the same values; an unmatched value is custom. */
object SpacingPresets {
    val lines = listOf(1f, 1.2f, 1.4f)
    val margins = listOf(3f, 5f, 8f)

    fun index(value: Float, values: List<Float>): Int = values.indexOfFirst { abs(it - value) < 0.0001f }

    fun next(value: Float, values: List<Float>): Float = values[(index(value, values) + 1) % values.size]
}
