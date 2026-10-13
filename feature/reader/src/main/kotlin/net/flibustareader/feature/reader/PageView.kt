package net.flibustareader.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.layout.Ink
import net.flibustareader.core.layout.Mark
import net.flibustareader.core.layout.Page
import net.flibustareader.core.layout.PageItem
import net.flibustareader.core.layout.PageRenderer
import net.flibustareader.core.layout.TextRange
import coil3.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

/**
 * Draws one pre-laid-out page. Text goes straight to the native canvas from cached
 * [android.text.StaticLayout]s, so a page costs about a millisecond to draw; images are
 * overlaid with Coil, which decodes them off the main thread at display size.
 */
@Composable
fun PageView(
    page: Page?,
    ink: Ink,
    palette: ReaderPalette,
    highlight: TextRange?,
    imageFile: (String) -> File,
    modifier: Modifier = Modifier,
    marks: List<Mark> = emptyList(),
) {
    Box(modifier) {
        // Own layer: redrawing the title, time or page number next to the page leaves its text recorded.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val p = page ?: return@Canvas
            // Reading the palette here re-runs the draw (not the layout) when the theme changes.
            palette.text
            drawIntoCanvas { PageRenderer.draw(it.nativeCanvas, p, ink, highlight, marks) }
        }
        page?.items?.forEach { item ->
            if (item is PageItem.Image) {
                val density = LocalDensity.current
                AsyncImage(
                    model = imageFile(item.resource),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .offset { IntOffset(item.left.roundToInt(), item.top.roundToInt()) }
                        .size(with(density) { item.width.toDp() }, with(density) { item.height.toDp() }),
                )
            }
        }
    }
}
