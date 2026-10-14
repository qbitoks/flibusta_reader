package app.folio.feature.reader

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.folio.core.data.db.HighlightEntity
import app.folio.core.data.settings.PageTurn
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.layout.Page
import app.folio.core.layout.PageRenderer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.folio.core.data.settings.ReaderSettings

@Composable
internal fun ReaderSelectionOverlays(
    vm: ReaderViewModel,
    settings: ReaderSettings,
    palette: ReaderPalette,
    controls: ReaderControls,
    layout: ReaderLayout,
    highlights: List<HighlightEntity>,
    locator: ScrollLocator,
) {
    val state = vm.state
    val scroll = settings.pageTurn == PageTurn.Scroll
    val selection = vm.selection
    val contentOrigin = layout.origin
    val contentTop = contentOrigin.y
    val heightPx = layout.height
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    // Selection and the highlight palette sit on the page they were made on: the current page,
    // or in the scroll mode the page under the finger, wherever the list has it now.
    // Reading the list's layout recomposes on every scroll frame, so only while something is open.
    val focus = if (scroll) controls.focusPage else state.page
    val focusOrigin = when {
        !scroll -> contentOrigin
        selection != null || controls.activeHighlight != null -> focus?.let(locator::originOf)
        else -> null
    }
    if (selection != null && focus != null && focusOrigin != null) {
        val touched = vm.highlightsOverlapping(selection)
        fun done() = vm.select(null)
        SelectionLayer(
            page = focus,
            selection = selection,
            origin = focusOrigin,
            limit = heightPx - with(density) { 90.dp.toPx() },
            palette = palette,
            actions = SelectionActions(
                canRemove = touched.isNotEmpty(),
                onHighlight = { vm.highlight(selection) { controls.activeHighlight = it }; done() },
                onRemove = { vm.removeHighlights(selection); done() },
                onNote = {
                    // On a single highlight the controls.note belongs to it; otherwise a new highlight is made with the controls.note.
                    val own = touched.singleOrNull()
                    controls.noteFor = if (own != null) {
                        NoteTarget(own.id, vm.spanOf(own) ?: selection, own.note.orEmpty(), HighlightColor.of(own.color), own.createdAt)
                    } else {
                        NoteTarget(null, selection, "", HighlightColor.of(settings.highlightColor))
                    }
                    done()
                },
                onFind = { vm.search(vm.textOf(selection)); done(); controls.sheet = ReaderSheet.Search },
                onCopy = {
                    clipboard.setText(AnnotatedString(vm.textOf(selection)))
                    done()
                },
                onShare = {
                    val text = passageForSharing(vm.textOf(selection), state.title, vm.book?.metadata?.authors?.firstOrNull())
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    done()
                },
            ),
            onChange = {
                if (it != vm.selection) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.select(it)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Note markers at the start of highlights with notes (#78); the scroll mode draws them on its pages.
    val shownPage = state.page
    if (shownPage != null && !scroll) {
        NoteMarkers(shownPage, contentOrigin, highlights, vm, palette) { controls.noteFor = it }
    }
    // The palette of the tapped highlight (#72).
    val active = highlights.firstOrNull { it.id == controls.activeHighlight }
    val activeBounds = active?.let(vm::spanOf)?.let { span -> focus?.let { PageRenderer.bounds(it, span) } }
    if (active != null && activeBounds != null && focusOrigin != null) {
        val gap = with(density) { 8.dp.toPx() }
        val paletteHeight = with(density) { 44.dp.toPx() }
        val above = focusOrigin.y + activeBounds.first.top - gap - paletteHeight
        val y = if (above > contentTop - with(density) { 30.dp.toPx() }) above else focusOrigin.y + activeBounds.second.bottom + gap
        Box(Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) }, contentAlignment = Alignment.TopCenter) {
            HighlightPalette(palette, onPick = { vm.setHighlightColor(active.id, it.key) })
        }
    }
}

/** Round markers at the start of highlights with notes on [page]; [origin] is its content box's top-left (#78). */
@Composable
internal fun NoteMarkers(
    page: Page,
    origin: Offset,
    highlights: List<HighlightEntity>,
    vm: ReaderViewModel,
    palette: ReaderPalette,
    onOpen: (NoteTarget) -> Unit,
) {
    val markerHalf = with(LocalDensity.current) { 11.dp.toPx() }
    highlights.forEach { h ->
        val note = h.note ?: return@forEach
        val span = vm.spanOf(h) ?: return@forEach
        val (first, _) = PageRenderer.bounds(page, span) ?: return@forEach
        val color = HighlightColor.of(h.color)
        NoteMarker(
            color = highlightPaint(color, palette, active = true),
            palette = palette,
            modifier = Modifier.offset {
                IntOffset((origin.x + first.left - markerHalf).roundToInt(), (origin.y + first.top - markerHalf).roundToInt())
            },
        ) { onOpen(NoteTarget(h.id, span, note, color, h.createdAt)) }
    }
}
