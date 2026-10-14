package app.folio.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType

/** A button in the top row of a menu: icon above a short label («Поделиться», «Хочу прочесть»). */
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Apple-style pull-down menu anchored to its parent Box. Popups live in their own window,
 * so the background is the translucent glass fallback rather than a live blur.
 *
 * @param checkColumn reserves a leading column for ✓ marks (view and sort menus).
 */
@Composable
fun PullDownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 250.dp,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    checkColumn: Boolean = false,
    content: @Composable PullDownMenuScope.() -> Unit,
) {
    val c = Folio.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = RoundedCornerShape(22.dp),
        containerColor = c.glassFallback.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(0.5.dp, if (c.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f)),
        modifier = modifier.width(width),
    ) {
        PullDownMenuScope(checkColumn, onDismiss).content()
    }
}

class PullDownMenuScope internal constructor(
    private val checkColumn: Boolean,
    private val dismiss: () -> Unit,
) {
    /** Row of 1–3 icon-over-label buttons at the top of the menu. */
    @Composable
    fun Actions(vararg actions: MenuAction) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            actions.forEach { a ->
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { dismiss(); a.onClick() }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(a.icon, null, tint = Folio.colors.label, modifier = Modifier.size(24.dp))
                    Text(a.label, style = FolioType.subheadline, color = Folio.colors.label, maxLines = 1)
                }
            }
        }
        Divider()
    }

    /**
     * @param checked ✓ in the leading column; only shown when the menu has a check column.
     * @param chevron shows › for items that open a submenu.
     */
    @Composable
    fun Item(
        text: String,
        onClick: () -> Unit,
        icon: ImageVector? = null,
        checked: Boolean = false,
        destructive: Boolean = false,
        chevron: Boolean = false,
    ) {
        val color = if (destructive) Folio.colors.destructive else Folio.colors.label
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clickable { dismiss(); onClick() }
                .padding(start = if (checkColumn) 10.dp else 20.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (checkColumn) {
                Box(Modifier.width(26.dp)) {
                    if (checked) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                Box(Modifier.width(14.dp))
            }
            Text(text, style = FolioType.body, color = color, modifier = Modifier.weight(1f))
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = color, modifier = Modifier.size(20.dp))
        }
    }

    /** Grey caption introducing a group: «Сортировка по…», «Затемнение фона». */
    @Composable
    fun Header(text: String) {
        Text(
            text,
            style = FolioType.subheadline,
            color = Folio.colors.secondaryLabel,
            modifier = Modifier.padding(start = if (checkColumn) 36.dp else 20.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
        )
    }

    @Composable
    fun Divider() {
        HorizontalDivider(
            color = Folio.colors.separator,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}
