package app.folio.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.LineLayout
import app.folio.core.data.settings.LineVertical

internal data class LineBlockGeometry(val height: Dp, val labelOffset: Dp, val textInset: Dp)

/** Label placement never moves the block's text-facing edge or its rule. */
internal fun lineBlockGeometry(layout: LineLayout, labelHeight: Dp): LineBlockGeometry {
    val height = maxOf(layout.requestedBlockHeight.dp, labelHeight + 16.dp)
    val offset = when (layout.vertical) {
        LineVertical.Edge -> 8.dp
        LineVertical.Center -> (height - labelHeight) / 2
        LineVertical.Text -> height - labelHeight - 8.dp
    }
    return LineBlockGeometry(height, offset, height + layout.distanceToText.dp)
}

/** Use the same measured text height for book viewport, label blocks and preview. */
@Composable
internal fun lineLabelHeight(look: LineLook): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return with(density) { measurer.measure("Аg", look.style, maxLines = 1).size.height.toDp() }
}

@Composable
internal fun ReaderLineBlock(top: Boolean, look: LineLook, margin: Dp, modifier: Modifier = Modifier,
                             content: @Composable () -> Unit) {
    val geometry = lineBlockGeometry(look.layout, lineLabelHeight(look))
    Box(modifier.fillMaxWidth().height(geometry.height)) {
        val labelAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter
        Box(Modifier.align(labelAlignment).padding(
            top = if (top) geometry.labelOffset else 0.dp,
            bottom = if (top) 0.dp else geometry.labelOffset,
        )) { content() }
        if (look.layout.rule) LineRule(look.ruleColor,
            Modifier.align(if (top) Alignment.BottomCenter else Alignment.TopCenter).padding(horizontal = margin))
    }
}

@Composable
internal fun BlockTitle(title: String, look: LineLook, margin: Dp) {
    Text(look.text(title), style = look.style, textAlign = look.textAlign,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = margin))
}
