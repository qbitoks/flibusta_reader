package app.folio.feature.reader

import android.graphics.BlurMaskFilter
import android.graphics.RectF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.GuideFalloff
import app.folio.core.data.settings.GuideWindow
import app.folio.core.designsystem.ReaderPalette
import kotlin.math.ceil
import kotlin.math.floor

/**
 * «Направляющая линия» drawn as part of one page, so dimming and plate slide, curl and fade with it and
 * never flash off during a turn (white spot 3, screenshot #21).
 *
 * The plate frames the fully lit lines of [window] across the whole text column (stable width on short
 * lines) and reaches at most half the margin past it. Every other line of the text area dims by [fade]
 * times what [GuideWindow.light] leaves unlit; the shadow may leave the text area.
 *
 * @param lines the page's lines in content coordinates ([PageRenderer.lines]).
 * @param index the guided line; [animate] glides the window when it changes.
 * @param padding the text column's insets in this composable, as given to `PageView`.
 * @param fade dimming of unlit text, 0 = none.
 */
@Composable
internal fun GuidePage(
    lines: List<RectF>,
    index: Int,
    padding: PaddingValues,
    fade: Float,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    window: GuideWindow = GuideWindow(),
    animate: Boolean = true,
) {
    if (lines.isEmpty()) return
    val last = lines.size - 1
    val pos by animateFloatAsState(index.coerceIn(0, last).toFloat(), tween(if (animate) 180 else 0), label = "guideLine")
    val shown by animateFloatAsState(fade, tween(220), label = "guideFade")
    Canvas(modifier.fillMaxSize()) {
        val column = Rect(padding.calculateLeftPadding(LayoutDirection.Ltr).toPx(), padding.calculateTopPadding().toPx(),
            size.width - padding.calculateRightPadding(LayoutDirection.Ltr).toPx(), size.height - padding.calculateBottomPadding().toPx())
        val padX = minOf(12.dp.toPx(), column.left / 2)
        val padY = 3.dp.toPx()
        // Edges of a fractional line, so the window glides between rows.
        fun edge(at: Float, top: Boolean): Float {
            val f = at.coerceIn(0f, last.toFloat())
            val lo = lines[floor(f).toInt()]
            val hi = lines[ceil(f).toInt()]
            val t = f - floor(f)
            return column.top + if (top) lo.top + (hi.top - lo.top) * t else lo.bottom + (hi.bottom - lo.bottom) * t
        }
        val full = window.falloff == GuideFalloff.None
        val plate = Rect(column.left - padX, edge(pos - if (full) window.above else 0, top = true) - padY,
            column.right + padX, edge(pos + if (full) window.below else 0, top = false) + padY)
        val radius = CornerRadius(9.dp.toPx())
        val round = RoundRect(plate, radius)
        if (shown > 0f) {
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(round)
            }
            // Each line owns the band up to the middle of its gaps; the first and last reach the text area's edges.
            clipPath(outside) {
                lines.forEachIndexed { i, line ->
                    val dim = shown * (1f - window.light(i - pos))
                    if (dim <= 0f) return@forEachIndexed
                    val top = if (i == 0) -padY else (lines[i - 1].bottom + line.top) / 2
                    val bottom = if (i == last) column.height + padY else (line.bottom + lines[i + 1].top) / 2
                    drawRect(palette.background.copy(alpha = dim), Offset(0f, column.top + top), Size(size.width, bottom - top))
                }
            }
        }
        // One soft blurred shadow outside the plate; on dark pages a shadow is invisible, so none.
        if (!palette.isDark) {
            val outsidePlate = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(round)
            }
            clipPath(outsidePlate) {
                drawIntoCanvas { canvas ->
                    val blur = 5.dp.toPx()
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.Black.copy(alpha = 0.16f).toArgb()
                        maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                    }
                    val dy = 1.dp.toPx()
                    canvas.nativeCanvas.drawRoundRect(plate.left, plate.top + dy, plate.right, plate.bottom + dy,
                        radius.x, radius.y, paint)
                }
            }
        }
        drawRoundRect(
            (if (palette.isDark) Color.White else Color.Black).copy(alpha = if (palette.isDark) 0.14f else 0.08f),
            topLeft = plate.topLeft,
            size = plate.size,
            cornerRadius = radius,
            style = Stroke(1.dp.toPx()),
        )
    }
}
