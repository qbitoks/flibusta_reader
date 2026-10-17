package app.folio.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType

/** Group title above a card: «Текст», «Универсальный доступ и макет». */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = FolioType.title3,
        color = Folio.colors.label,
        modifier = modifier.padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** Rounded card holding rows, inset 16 dp from the screen edges. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Folio.colors.card),
        content = content,
    )
}

/**
 * One row of a grouped list. The separator starts where the title starts, as in iOS.
 *
 * @param editing shows a red ⊖ in front (calls [onRemove]) and, if [reorderable], a drag handle.
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    /** A second, secondary line under [title] (e.g. which engine a voice belongs to). */
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    valueStyle: TextStyle = FolioType.body,
    chevron: Boolean = false,
    enabled: Boolean = true,
    titleColor: Color = Folio.colors.label,
    showDivider: Boolean = true,
    editing: Boolean = false,
    onRemove: (() -> Unit)? = null,
    reorderable: Boolean = false,
    handleModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Folio.colors
    val contentAlpha = if (enabled) 1f else 0.35f
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .then(if (onClick != null && enabled && !editing) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 18.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing && onRemove != null) {
                Icon(
                    Icons.Rounded.RemoveCircle,
                    contentDescription = null,
                    tint = c.destructive,
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).clickable(onClick = onRemove),
                )
                Spacer(Modifier.width(14.dp))
            }
            when {
                leading != null -> Box(Modifier.width(36.dp)) { leading() }
                icon != null -> Icon(icon, null, tint = c.label.copy(alpha = contentAlpha), modifier = Modifier.width(36.dp).size(24.dp))
            }
            if (leading != null || icon != null) Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = if (subtitle != null) 9.dp else 13.dp)) {
                Text(
                    title,
                    style = FolioType.body,
                    color = titleColor.copy(alpha = titleColor.alpha * contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = FolioType.footnote, color = c.secondaryLabel.copy(alpha = contentAlpha),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null && !editing) {
                Text(value, style = valueStyle, color = c.secondaryLabel.copy(alpha = contentAlpha), maxLines = 1)
            }
            if (!editing) trailing?.invoke()
            if (chevron && !editing) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 4.dp).size(22.dp))
            }
            if (editing && reorderable) {
                Icon(Icons.Rounded.DragHandle, null, tint = c.tertiaryLabel, modifier = handleModifier.size(24.dp))
            }
        }
        if (showDivider) {
            val inset = 18.dp + (if (editing && onRemove != null) 40.dp else 0.dp) + (if (leading != null || icon != null) 50.dp else 0.dp)
            HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = inset, end = 16.dp))
        }
    }
}
