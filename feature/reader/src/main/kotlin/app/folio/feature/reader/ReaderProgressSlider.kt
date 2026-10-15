package app.folio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.ReaderPalette
import kotlin.math.abs

/** Keep the page's hue but raise very soft custom text to usable control contrast. */
internal fun readerSliderInk(background: Color, text: Color): Color {
    fun contrast(color: Color): Float {
        val a = background.luminance()
        val b = color.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    if (contrast(text) >= 3f) return text
    val target = if (contrast(Color.Black) > contrast(Color.White)) Color.Black else Color.White
    for (step in 1..10) {
        val ink = lerp(text, target, step / 10f)
        if (contrast(ink) >= 3f) return ink
    }
    return target
}

/** A small round thumb over a thin themed track, with a full 48 dp touch target. */
@Composable
internal fun ReaderProgressSlider(
    value: Float, palette: ReaderPalette, onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit, onValueChangeCancelled: () -> Unit,
    enabled: Boolean, contentDescription: String, modifier: Modifier = Modifier,
) {
    val latestValue by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val cancel by rememberUpdatedState(onValueChangeCancelled)
    val ink = readerSliderInk(palette.background, palette.text)
    Canvas(modifier.fillMaxWidth().height(48.dp).semantics {
        this.contentDescription = contentDescription
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
        if (!enabled) disabled() else setProgress { target ->
            if (!target.isFinite()) false else {
                change(target.coerceIn(0f, 1f))
                finish()
                true
            }
        }
    }.then(if (!enabled) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val radius = 7.dp.toPx()
            val travel = (size.width - radius * 2).coerceAtLeast(1f)
            val centre = radius + latestValue * travel
            val grab = if (abs(down.position.x - centre) < radius + 8.dp.toPx()) down.position.x - centre else 0f
            var last = latestValue
            var released = false
            fun update(x: Float) {
                val next = ((x - radius - grab) / travel).coerceIn(0f, 1f)
                if (next != last) { last = next; change(next) }
            }
            try {
                update(down.position.x)
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (pointer.isConsumed) break
                    update(pointer.position.x)
                    event.changes.forEach { it.consume() }
                    if (!pointer.pressed) { released = true; finish(); break }
                }
            } finally {
                if (!released) cancel()
            }
        }
    })) {
        val radius = 7.dp.toPx()
        val start = Offset(radius, size.height / 2)
        val end = Offset((size.width - radius).coerceAtLeast(radius), start.y)
        val thumb = Offset(start.x + (end.x - start.x) * value.coerceIn(0f, 1f), start.y)
        val track = lerp(palette.background, ink, if (enabled) 0.3f else 0.15f)
        val active = if (enabled) ink else lerp(palette.background, ink, 0.4f)
        drawLine(track, start, end, 3.dp.toPx(), StrokeCap.Round)
        drawLine(active, start, thumb, 3.dp.toPx(), StrokeCap.Round)
        drawCircle(active, radius, thumb)
    }
}
