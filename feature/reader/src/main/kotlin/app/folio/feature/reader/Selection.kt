package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.UiSans
import app.folio.core.layout.Page
import app.folio.core.layout.PageRenderer
import app.folio.core.layout.TextSpan
import kotlin.math.roundToInt

/** iOS tint of the selection handles (measured on #19). */
internal val SelectionBlue = Color(0xFF007AFF)

/** Selection fill: the tint at ≈20 % over the page (190/215/237 on a 237 grey page in #19). */
internal fun selectionFill(palette: ReaderPalette) = SelectionBlue.copy(alpha = if (palette.isDark) 0.35f else 0.2f)

/** What the selection menu can do; [canRemove] when the selection touches saved highlights («Убрать», #76). */
internal class SelectionActions(
    val canRemove: Boolean,
    val onHighlight: () -> Unit,
    val onRemove: () -> Unit,
    val onNote: () -> Unit,
    val onFind: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
)

/**
 * Handles and the edit menu over a selection (white spot 2, #19–#20). The blue fill itself is
 * drawn with the page. Handles drag the boundaries within the page; the capsule
 * «Выделить │ Добавить заметку │ ›» sits under the selection and › opens the full menu.
 *
 * @param origin top-left of the page's content box in this layer's coordinates.
 * @param limit lowest y the menu may reach before it moves above the selection.
 */
@Composable
internal fun SelectionLayer(
    page: Page,
    selection: TextSpan,
    origin: Offset,
    limit: Float,
    palette: ReaderPalette,
    actions: SelectionActions,
    onChange: (TextSpan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val start = PageRenderer.caret(page, selection.start, atEnd = false) ?: return
    val end = PageRenderer.caret(page, selection.end, atEnd = true) ?: return
    val latest by rememberUpdatedState(selection)
    var expanded by remember(selection.start, selection.end) { mutableStateOf(false) }

    Box(modifier) {
        Handle(
            x = origin.x + start.left, top = origin.y + start.top, bottom = origin.y + start.bottom, knobOnTop = true,
        ) { point ->
            val p = PageRenderer.positionNear(page, point.x - origin.x, point.y - origin.y) ?: return@Handle
            if (p < latest.end) onChange(TextSpan(p, latest.end))
        }
        Handle(
            x = origin.x + end.left, top = origin.y + end.top, bottom = origin.y + end.bottom, knobOnTop = false,
        ) { point ->
            val p = PageRenderer.positionNear(page, point.x - origin.x, point.y - origin.y) ?: return@Handle
            if (p > latest.start) onChange(TextSpan(latest.start, p))
        }

        // Under the selection when there is room, otherwise above it.
        var menuSize by remember { mutableStateOf(IntSize.Zero) }
        val gap = with(density) { 14.dp.toPx() }
        val below = origin.y + end.bottom + gap
        Box(
            Modifier
                .fillMaxWidth()
                .offset {
                    val y = if (below + menuSize.height <= limit) below else origin.y + start.top - gap - menuSize.height
                    IntOffset(0, y.roundToInt())
                }
                .onSizeChanged { menuSize = it },
            contentAlignment = Alignment.TopCenter,
        ) {
            if (expanded) SelectionMenu(palette, actions, Modifier.align(Alignment.TopEnd).padding(end = 22.dp))
            else SelectionCapsule(palette, actions) { expanded = true }
        }
    }
}

@Composable
private fun Handle(x: Float, top: Float, bottom: Float, knobOnTop: Boolean, onDrag: (Offset) -> Unit) {
    val density = LocalDensity.current
    val knob = with(density) { 14.dp.toPx() }
    val stem = with(density) { 2.dp.toPx() }
    val touch = with(density) { 44.dp.toPx() }
    val latestDrag by rememberUpdatedState(onDrag)
    val anchor = Offset(x, if (knobOnTop) top else bottom)
    val latestAnchor by rememberUpdatedState(anchor)
    // Stem across the line, knob on top for the start and at the bottom for the end.
    Box(
        Modifier
            .offset { IntOffset((x - stem / 2).roundToInt(), top.roundToInt()) }
            .size(with(density) { stem.toDp() }, with(density) { (bottom - top).toDp() })
            .background(SelectionBlue),
    )
    val knobY = if (knobOnTop) top - knob + 1 else bottom - 1
    Box(
        Modifier
            .offset { IntOffset((x - knob / 2).roundToInt(), knobY.roundToInt()) }
            .size(with(density) { knob.toDp() })
            .background(SelectionBlue, CircleShape),
    )
    // Generous invisible touch target around the knob.
    Box(
        Modifier
            .offset { IntOffset((x - touch / 2).roundToInt(), (knobY + knob / 2 - touch / 2).roundToInt()) }
            .size(with(density) { touch.toDp() })
            .pointerInput(Unit) {
                var point = Offset.Zero
                detectDragGestures(
                    onDragStart = { point = latestAnchor + Offset(0f, if (knobOnTop) knob else -knob) },
                    onDrag = { change, amount ->
                        change.consume()
                        point += amount
                        latestDrag(point)
                    },
                )
            },
    )
}

@Composable
private fun SelectionCapsule(palette: ReaderPalette, actions: SelectionActions, onMore: () -> Unit) {
    val container = palette.menuCapsule()
    Row(
        Modifier
            .height(40.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(container, CircleShape)
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleItem(stringResource(R.string.selection_highlight), palette, actions.onHighlight)
        CapsuleDivider(palette)
        CapsuleItem(stringResource(R.string.selection_add_note), palette, actions.onNote)
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(32.dp)
                .background(palette.text.copy(alpha = 0.08f), CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.selection_more), tint = palette.text, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CapsuleItem(text: String, palette: ReaderPalette, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = palette.text, fontFamily = UiSans, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun CapsuleDivider(palette: ReaderPalette) {
    Box(Modifier.width(0.5.dp).height(22.dp).background(palette.text.copy(alpha = 0.2f)))
}

/** The full menu behind › (#20). «Перевести» and «Искать» are left out (decision A). */
@Composable
private fun SelectionMenu(palette: ReaderPalette, actions: SelectionActions, modifier: Modifier) {
    Column(
        modifier
            .width(250.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .background(palette.menuCapsule(), RoundedCornerShape(22.dp))
            .padding(vertical = 6.dp),
    ) {
        MenuRow(Icons.Outlined.Edit, stringResource(R.string.selection_highlight), palette, actions.onHighlight)
        MenuRow(Icons.Outlined.StickyNote2, stringResource(R.string.selection_add_note), palette, actions.onNote)
        if (actions.canRemove) {
            MenuRow(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.selection_remove), palette, actions.onRemove, tint = Color(0xFFFF3B30))
        }
        MenuDivider(palette)
        MenuRow(Icons.Outlined.FindInPage, stringResource(R.string.selection_find), palette, actions.onFind)
        MenuRow(Icons.Outlined.ContentCopy, stringResource(R.string.selection_copy), palette, actions.onCopy)
        MenuDivider(palette)
        MenuRow(Icons.Outlined.IosShare, stringResource(R.string.selection_share), palette, actions.onShare)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, palette: ReaderPalette, onClick: () -> Unit, tint: Color = palette.text) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Text(text, color = tint, fontFamily = UiSans, fontSize = 16.sp)
    }
}

@Composable
private fun MenuDivider(palette: ReaderPalette) {
    HorizontalDivider(color = palette.text.copy(alpha = 0.12f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
}

/** Shares a passage as Apple does: the quote, then where it comes from. */
internal fun passageForSharing(text: String, title: String, author: String?): String =
    buildString {
        append('«').append(text).append('»')
        append("\n\n").append(title)
        if (!author.isNullOrBlank()) append(", ").append(author)
    }
