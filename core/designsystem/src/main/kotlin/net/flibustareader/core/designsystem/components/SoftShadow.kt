package net.flibustareader.core.designsystem.components

import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft, dark drop shadow as under Apple Books covers. The platform elevation shadow is
 * capped by the theme's shadow alpha and looks too faint, so this draws a blurred
 * shadow layer directly (hardware-accelerated since API 28).
 */
fun Modifier.softShadow(
    radius: Dp = 10.dp,
    offsetY: Dp = 4.dp,
    color: Color = Color.Black.copy(alpha = 0.55f),
    cornerRadius: Dp = 3.dp,
): Modifier = if (radius <= 0.dp) this else drawBehind {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.TRANSPARENT
        setShadowLayer(radius.toPx(), 0f, offsetY.toPx(), color.toArgb())
    }
    val r = cornerRadius.toPx()
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint) }
}
