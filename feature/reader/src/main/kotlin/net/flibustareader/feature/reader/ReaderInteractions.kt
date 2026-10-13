package net.flibustareader.feature.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import net.flibustareader.core.layout.Page
import net.flibustareader.core.layout.PageRenderer
import net.flibustareader.core.layout.TextSpan
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.graphics.RectF

internal class ReaderInteractions(
    val onTap: (Offset, Float) -> TapAction,
    val tapOnPage: (Page?, Offset) -> Boolean,
    val startSelection: (Page, Offset) -> Boolean,
    val extendSelection: (Page, Offset) -> Unit,
)

@Composable
internal fun readerInteractions(
    vm: ReaderViewModel,
    controls: ReaderControls,
    guidePlate: Rect?,
    guideLines: List<RectF>,
    contentOrigin: Offset,
): ReaderInteractions {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var anchor by remember { mutableStateOf<TextSpan?>(null) }
    // The pager keeps its first callback, so guide geometry must be read at call time.
    val currentGuidePlate by rememberUpdatedState(guidePlate)
    val currentGuideLines by rememberUpdatedState(guideLines)
    /** Long press at [local] (content-box coordinates of [page]) selects the word there. */
    fun startSelection(page: Page, local: Offset): Boolean {
        if (controls.menuVisible) return false
        val word = PageRenderer.wordAt(page, local.x, local.y) ?: return false
        anchor = word
        controls.focusPage = page
        vm.select(word)
        controls.chromeVisible = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    fun extendSelection(page: Page, local: Offset) {
        val from = anchor ?: return
        val p = PageRenderer.positionNear(page, local.x, local.y) ?: return
        vm.select(if (p < from.start) TextSpan(p, from.end) else TextSpan(from.start, maxOf(p, from.end)))
    }

    /** Opens whatever is under a tap on [page] (content-box coordinates); false if nothing is. */
    fun openTarget(page: Page, x: Float, y: Float): Boolean {
        when (val target = vm.tapTarget(page, x, y)) {
            is TapTarget.Note -> controls.note = target.blocks
            is TapTarget.Link -> vm.goTo(target.position)
            is TapTarget.External -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url))) }
            is TapTarget.Image -> controls.image = target.file
            null -> return false
        }
        return true
    }

    /** A tap that closes what is open or opens what is under it ([local]: content box of [page]); false if it did neither. */
    fun tapOnPage(page: Page?, local: Offset): Boolean {
        if (controls.menuVisible) {
            controls.menuVisible = false
            return true
        }
        if (vm.selection != null || controls.activeHighlight != null) {
            vm.select(null)
            controls.activeHighlight = null
            return true
        }
        if (page != null && openTarget(page, local.x, local.y)) return true
        vm.clearHighlight()
        // A tap on a saved highlight opens its colour palette (#72).
        val saved = page?.let { PageRenderer.hitTest(it, local.x, local.y) }?.let(vm::highlightAt) ?: return false
        controls.activeHighlight = saved.id
        controls.focusPage = page
        return true
    }

    fun onTap(offset: Offset, width: Float): TapAction {
        if (tapOnPage(vm.state.page, offset - contentOrigin)) return TapAction.Handled
        val plate = currentGuidePlate
        if (plate != null) {
            // With the guide line on, a tap on the right half steps it down a line and on the left half
            // up a line, crossing pages at the ends; a tap on the line itself toggles the controls.
            return when {
                plate.contains(offset) -> { controls.chromeVisible = !controls.chromeVisible; TapAction.ToggleChrome }
                offset.x >= width / 2 -> if (controls.guideIndex < currentGuideLines.lastIndex) { controls.guideIndex++; TapAction.Handled } else TapAction.Next
                else -> if (controls.guideIndex > 0) { controls.guideIndex--; TapAction.Handled } else { controls.guideToLast = true; TapAction.Prev }
            }
        }
        // Outer thirds turn the page (and put the controls away); the middle toggles them.
        return when (tapZone(offset.x, width)) {
            -1 -> { controls.chromeVisible = false; TapAction.Prev }
            1 -> { controls.chromeVisible = false; TapAction.Next }
            else -> {
                controls.chromeVisible = !controls.chromeVisible
                TapAction.ToggleChrome
            }
        }
    }
    return ReaderInteractions(::onTap, ::tapOnPage, ::startSelection, ::extendSelection)
}
