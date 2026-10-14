package app.folio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.GuideDim
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.FolioTheme
import app.folio.core.designsystem.components.PullDownMenu

/**
 * «Направляющая линия» (white spot 3, screenshot #21): the current line sits on a plate in the
 * page colour with a hairline border and a soft shadow; everything else fades towards the page
 * colour by [fade] (Apple's «Низкая» leaves black text at about 35 % contrast).
 *
 * @param plate the plate in this composable's coordinates.
 * @param text the area that dims: the text column, not the title or page number (as in #21).
 */
@Composable
internal fun GuideLineOverlay(plate: Rect, text: Rect, fade: Float, palette: ReaderPalette, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        clipRect(text.left, text.top, text.right, text.bottom) {
            val radius = CornerRadius(9.dp.toPx())
            val round = RoundRect(plate, radius)
            if (fade > 0f) {
                val outside = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(text)
                    addRoundRect(round)
                }
                drawPath(outside, palette.background.copy(alpha = fade))
            }
            // Soft shadow just outside the plate, then the hairline border.
            val shadow = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(round)
            }
            clipPath(shadow) {
                val shade = if (palette.isDark) Color.Black else Color.Black.copy(alpha = 0.55f)
                for (i in 1..6) {
                    val grow = i * 2.dp.toPx()
                    drawRoundRect(
                        shade.copy(alpha = shade.alpha * 0.035f),
                        topLeft = plate.topLeft - Offset(grow / 2, grow / 2 - 1.dp.toPx()),
                        size = Size(plate.width + grow, plate.height + grow),
                        cornerRadius = CornerRadius(radius.x + grow / 2),
                    )
                }
            }
            drawRoundRect(
                (if (palette.isDark) Color.White else Color.Black).copy(alpha = 0.09f),
                topLeft = plate.topLeft,
                size = plate.size,
                cornerRadius = radius,
                style = Stroke(1.dp.toPx()),
            )
        }
    }
}

/** Round button bottom-left while the guide line is on; opens «Затемнение фона» (#22). */
@Composable
internal fun GuideLineButton(
    dim: GuideDim,
    palette: ReaderPalette,
    onDim: (GuideDim) -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        RoundButton(
            Icons.Rounded.CalendarViewDay,
            stringResource(R.string.reader_guide_line),
            palette.control(),
            palette.text,
            Modifier,
        ) { open = true }
        // Apple shows this menu dark even over a light page.
        FolioTheme(dark = palette.isDark) {
            PullDownMenu(
                expanded = open,
                onDismiss = { open = false },
                width = 230.dp,
                offset = DpOffset(0.dp, (-8).dp),
                checkColumn = true,
            ) {
                Header(stringResource(R.string.guide_dim))
                Item(stringResource(R.string.guide_dim_high), { onDim(GuideDim.High) }, checked = dim == GuideDim.High)
                Item(stringResource(R.string.guide_dim_medium), { onDim(GuideDim.Medium) }, checked = dim == GuideDim.Medium)
                Item(stringResource(R.string.guide_dim_low), { onDim(GuideDim.Low) }, checked = dim == GuideDim.Low)
                Item(stringResource(R.string.guide_dim_none), { onDim(GuideDim.None) }, checked = dim == GuideDim.None)
                Divider()
                Item(stringResource(R.string.guide_off), onTurnOff)
            }
        }
    }
}
