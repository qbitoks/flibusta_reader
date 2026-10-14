package app.folio.feature.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.FooterStyle

/** How long «Затухание» is, ending at the cut. */
internal val EdgeFadeLength = 18.dp

/**
 * «Обрезка» and «Затухание» of scrolling text: a mask over the text itself, so nothing is painted on
 * the page and no text shows beyond [topCut] from the top and [bottomCut] from the bottom. The fade
 * keeps a line almost whole and drops it only near the cut, as iOS does.
 */
internal fun Modifier.scrollEdges(top: FooterStyle, topCut: Dp, bottom: FooterStyle, bottomCut: Dp): Modifier {
    val masksTop = top != FooterStyle.None
    val masksBottom = bottom != FooterStyle.None
    if (!masksTop && !masksBottom) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        /** Stops from the cut inwards, as distances from that edge. */
        fun edge(style: FooterStyle, at: Float): List<Pair<Float, Color>> {
            if (style != FooterStyle.Fade) return listOf(at to Color.Transparent, at + 0.01f to Color.Black)
            val fade = EdgeFadeLength.toPx()
            return (0..EASE_STEPS).map { i ->
                val t = i.toFloat() / EASE_STEPS
                at + fade * t to Color.Black.copy(alpha = 1f - (1f - t) * (1f - t))
            }
        }
        val stops = buildList {
            add(0f to if (masksTop) Color.Transparent else Color.Black)
            if (masksTop) addAll(edge(top, topCut.toPx()))
            if (masksBottom) addAll(edge(bottom, bottomCut.toPx()).map { (y, c) -> h - y to c }.reversed())
            add(h to if (masksBottom) Color.Transparent else Color.Black)
        }
        val colorStops = stops.map { (y, c) -> (y / h).coerceIn(0f, 1f) to c }.toTypedArray()
        drawRect(Brush.verticalGradient(colorStops = colorStops, startY = 0f, endY = h), blendMode = BlendMode.DstIn)
    }
}

private const val EASE_STEPS = 6
