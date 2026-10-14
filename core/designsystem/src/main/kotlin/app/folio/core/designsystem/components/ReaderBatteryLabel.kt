package app.folio.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.ReaderPalette

@Composable
fun ReaderBatteryLabel(level: Int, charging: Boolean, palette: ReaderPalette, textStyle: TextStyle) {
    val iconHeight = with(LocalDensity.current) { textStyle.fontSize.toDp() } * 0.8f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${level}%", style = textStyle, maxLines = 1)
        Box(contentAlignment = Alignment.Center) {
            val tint = textStyle.color
            Canvas(Modifier.size(width = iconHeight * 2, height = iconHeight)) {
                val stroke = 1.dp.toPx()
                val cap = 2.dp.toPx()
                val body = Size(size.width - cap - stroke, size.height - stroke)
                val radius = CornerRadius(3.dp.toPx())
                drawRoundRect(tint, Offset(stroke / 2, stroke / 2), body, radius, style = Stroke(stroke))
                val inner = 2.dp.toPx()
                val fill = (body.width - inner * 2) * level.coerceIn(0, 100) / 100f
                drawRoundRect(tint, Offset(stroke / 2 + inner, stroke / 2 + inner), Size(fill, body.height - inner * 2), CornerRadius(1.5.dp.toPx()))
                drawRoundRect(tint, Offset(size.width - cap, size.height / 3), Size(cap, size.height / 3), CornerRadius(1.dp.toPx()))
            }
            if (charging) {
                Icon(Icons.Rounded.Bolt, null, tint = palette.background, modifier = Modifier.size(iconHeight))
            }
        }
    }
}
