package app.folio.core.designsystem.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
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
 * Apple-style pull-down menu anchored to its parent Box: below it, or above when there is no room.
 * Popups live in their own window, so the background is the translucent glass fallback rather than a
 * live blur, and the window keeps [ShadowRoom] around the card: a shadow cut by the window edge shows
 * as a grey box.
 *
 * @param offset x shifts the menu; |y| is the gap to the anchor.
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
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return
    val density = LocalDensity.current
    val provider = remember(offset, density) { MenuPosition(offset, density) }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val transition = rememberTransition(visible, label = "menu")
        val alpha by transition.animateFloat({ tween(if (targetState) 120 else 90) }, label = "menuAlpha") { if (it) 1f else 0f }
        val scale by transition.animateFloat({ tween(if (targetState) 160 else 90) }, label = "menuScale") { if (it) 1f else 0.92f }
        val shape = RoundedCornerShape(22.dp)
        Box(Modifier.padding(ShadowRoom)) {
            Column(
                modifier
                    .width(width)
                    .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale; transformOrigin = provider.origin }
                    .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                    .background(c.glassFallback.copy(alpha = 0.98f), shape)
                    .border(0.5.dp, if (c.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f), shape)
                    .clip(shape)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                PullDownMenuScope(checkColumn, onDismiss).content()
            }
        }
    }
}

/** Transparent room the popup window keeps around the card for its shadow. */
private val ShadowRoom = 28.dp

/** Places the padded card under the anchor (or over it), clamped to the window; the room may overhang. */
private class MenuPosition(private val offset: DpOffset, private val density: Density) : PopupPositionProvider {
    var origin by mutableStateOf(TransformOrigin(0.5f, 0f))

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val room = with(density) { ShadowRoom.roundToPx() }
        val gap = with(density) { abs(offset.y.roundToPx()) }
        val shift = with(density) { offset.x.roundToPx() }
        val card = IntSize(popupContentSize.width - 2 * room, popupContentSize.height - 2 * room)
        val start = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left + shift else anchorBounds.right - card.width - shift
        val x = start.coerceIn(0, (windowSize.width - card.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val fitsBelow = below + card.height <= windowSize.height
        val y = (if (fitsBelow) below else anchorBounds.top - gap - card.height).coerceIn(0, (windowSize.height - card.height).coerceAtLeast(0))
        val pivotX = ((anchorBounds.center.x - x).toFloat() / card.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        origin = TransformOrigin(pivotX, if (fitsBelow) 0f else 1f)
        return IntOffset(x - room, y - room)
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
