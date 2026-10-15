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
import app.folio.core.designsystem.ReaderPalette

/**
 * «Направляющая линия» drawn as part of one page, so dimming and plate slide, curl and fade with it and
 * never flash off during a turn (white spot 3, screenshot #21).
 *
 * The plate spans the whole text column (stable width on short lines) and reaches at most half the
 * margin past it. Dimming covers only the text area between the page labels; the shadow may leave it.
 *
 * @param lines the page's lines in content coordinates ([PageRenderer.lines]).
 * @param index the guided line; [animate] glides the plate when it changes.
 * @param padding the text column's insets in this composable, as given to `PageView`.
 * @param fade dimming of everything outside the plate, 0 = none.
 */
@Composable
internal fun GuidePage(
    lines: List<RectF>,
    index: Int,
    padding: PaddingValues,
    fade: Float,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val line = lines.getOrNull(index.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) ?: return
    val spec = tween<Float>(if (animate) 180 else 0)
    val top by animateFloatAsState(line.top, spec, label = "guideTop")
    val bottom by animateFloatAsState(line.bottom, spec, label = "guideBottom")
    val shown by animateFloatAsState(fade, tween(220), label = "guideFade")
    Canvas(modifier.fillMaxSize()) {
        val column = Rect(padding.calculateLeftPadding(LayoutDirection.Ltr).toPx(), padding.calculateTopPadding().toPx(),
            size.width - padding.calculateRightPadding(LayoutDirection.Ltr).toPx(), size.height - padding.calculateBottomPadding().toPx())
        val padX = minOf(12.dp.toPx(), column.left / 2)
        val padY = 3.dp.toPx()
        val plate = Rect(column.left - padX, column.top + top - padY, column.right + padX, column.top + bottom + padY)
        val radius = CornerRadius(9.dp.toPx())
        val round = RoundRect(plate, radius)
        val text = Rect(0f, column.top - padY, size.width, column.bottom + padY)
        if (shown > 0f) {
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(text)
                addRoundRect(round)
            }
            drawPath(outside, palette.background.copy(alpha = shown))
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
