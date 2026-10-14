package app.folio.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.designsystem.components.softShadow
import coil3.compose.AsyncImage
import java.io.File

const val COVER_ASPECT = 2f / 3f

/**
 * Book cover with Apple Books' soft shadow and small corner radius. Books without a
 * cover get a generated one: title and author on a colour derived from the title.
 */
@Composable
fun BookCover(
    title: String,
    author: String?,
    coverFile: File?,
    modifier: Modifier = Modifier,
    elevation: Dp = 6.dp,
    titleSize: TextUnit = 15.sp,
    /** Keep the cover's own proportions (library shelves) instead of forcing 2:3. */
    naturalAspect: Boolean = false,
) {
    val shape = RoundedCornerShape(1.5.dp)
    val shadow = Modifier.softShadow(radius = elevation * 1.6f, offsetY = elevation * 0.45f, cornerRadius = 1.5.dp)
    if (coverFile != null && naturalAspect) {
        AsyncImage(
            model = coverFile,
            contentDescription = title,
            contentScale = ContentScale.FillWidth,
            modifier = modifier.then(shadow).clip(shape).hardcover(),
        )
        return
    }
    Box(modifier.aspectRatio(COVER_ASPECT).then(shadow).clip(shape).hardcover()) {
        if (coverFile != null) {
            AsyncImage(
                model = coverFile,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedCover(title, author, titleSize)
        }
    }
}

@Composable
private fun GeneratedCover(title: String, author: String?, titleSize: TextUnit) {
    val (top, bottom) = remember(title) { coverColors(title) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontFamily = ReaderFont.Literata.composeFamily,
            fontWeight = FontWeight.Bold,
            fontSize = titleSize,
            lineHeight = titleSize * 1.2f,
            textAlign = TextAlign.Center,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (!author.isNullOrBlank()) {
            Text(
                text = author,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = titleSize * 0.72f,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val coverHues = floatArrayOf(4f, 24f, 38f, 150f, 175f, 200f, 215f, 250f, 280f, 330f)

/** Colours of the generated cover for [title]; also used to tint its «Продолжить» card. */
fun coverColors(title: String): Pair<Color, Color> {
    val hue = coverHues[Math.floorMod(title.hashCode(), coverHues.size)]
    return Color.hsl(hue, 0.42f, 0.42f) to Color.hsl(hue, 0.48f, 0.28f)
}

/**
 * Hardcover lighting measured from Apple Books on a dark and a light cover. Each line is a
 * pair of white and black layers at the same place: the white shows on dark covers, the
 * black on light ones. Alphas were solved so both measured covers come out right:
 * left edge (white 21 %, black 22 %), spine hinge at 4.4 % of the width (14 % / 10 %),
 * right edge (10 % / 7 %) and a white top edge (17 %).
 */
private fun Modifier.hardcover(): Modifier = drawWithContent {
    drawContent()
    val w = size.width
    val h = size.height
    fun stops(color: Color, alpha: Float, peak: Float) = when {
        peak <= 0f -> arrayOf(0f to color.copy(alpha = alpha), 1f to Color.Transparent)
        peak >= 1f -> arrayOf(0f to Color.Transparent, 1f to color.copy(alpha = alpha))
        else -> arrayOf(0f to Color.Transparent, peak to color.copy(alpha = alpha), 1f to Color.Transparent)
    }
    /** Vertical band from [x] of [width] whose profile peaks at [peak] (0..1 across it). */
    fun band(x: Float, width: Float, color: Color, alpha: Float, peak: Float) = drawRect(
        Brush.horizontalGradient(*stops(color, alpha, peak), startX = x, endX = x + width),
        topLeft = Offset(x, 0f),
        size = Size(width, h),
    )
    /** Fades whatever was drawn since the layer opened from full at the top to [bottom] at the foot. */
    fun fadeDown(bottom: Float, draw: () -> Unit) = drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
        draw()
        drawRect(
            Brush.verticalGradient(0f to Color.Black, 0.45f to Color.Black.copy(alpha = 0.65f), 1f to Color.Black.copy(alpha = bottom)),
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }
    val edge = (w * 0.014f).coerceAtLeast(2f)
    val hingeX = w * 0.034f
    val hingeW = w * 0.02f
    val rim = (edge * 0.75f).coerceAtLeast(2.5f)

    // Shadows run the full height of the board.
    band(0f, edge, Color.Black, 0.22f, peak = 0f)
    band(hingeX, hingeW, Color.Black, 0.10f, peak = 0.5f)
    band(w - edge - rim * 0.3f, edge, Color.Black, 0.07f, peak = 0.6f)

    // Glints catch light at the top and fade towards the bottom, as in Apple Books.
    fadeDown(bottom = 0.17f) { band(0f, edge, Color.White, 0.21f, peak = 0f) }
    fadeDown(bottom = 0.05f) { band(hingeX, hingeW, Color.White, 0.14f, peak = 0.5f) }
    fadeDown(bottom = 0.3f) { band(w - rim, rim, Color.White, 0.18f, peak = 1f) }
    drawRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.17f), 1f to Color.Transparent, startY = 0f, endY = edge),
        size = Size(w, edge),
    )
}
