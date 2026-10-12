package net.flibustareader.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType

/** Large serif title with round glass buttons on the right (§ 3.2 of the guideline). */
@Composable
fun LargeTitleHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 32.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = FlibustaType.largeTitle, color = Flibusta.colors.label, maxLines = 1, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** Serif section header; with [onClick] it gets a grey chevron and becomes a link («Хочу прочесть ›»). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Column(modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 14.dp)) {
        Row(
            (if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = FlibustaType.section, color = Flibusta.colors.label)
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Flibusta.colors.tertiaryLabel, modifier = Modifier.size(30.dp))
            }
        }
        if (subtitle != null) Text(subtitle, style = FlibustaType.body, color = Flibusta.colors.secondaryLabel)
    }
}

/**
 * A Home section on its gradient band (§ 2.6): the bands' steps separate sections instead of
 * dividers. The first band is plain in dark mode, as in Apple Books.
 */
@Composable
fun SectionBand(first: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Flibusta.colors
    val brush = if (first && c.isDark) {
        Brush.verticalGradient(listOf(c.background, c.background))
    } else if (first) {
        Brush.verticalGradient(listOf(c.background, c.bandBottom))
    } else {
        Brush.verticalGradient(listOf(c.bandTop, c.bandBottom))
    }
    Column(modifier.fillMaxWidth().background(brush), content = content)
}

/** Thin capsule progress bar filled with the label colour; null [fraction] draws an empty track. */
@Composable
fun ProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val c = Flibusta.colors
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.fill)) {
        if (fraction != null) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(c.label))
        }
    }
}
