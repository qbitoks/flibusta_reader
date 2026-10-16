package app.folio.core.designsystem.components

import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp)
private val Overshoot = 48.dp

/**
 * Full-height sheet as in Apple Books: starts below the status bar, 23 dp top corners,
 * no drag handle unless [handle]. Header content is supplied by [SheetHeader]. Shorter content makes
 * a shorter sheet. It is hard to close by accident: only a pull past about a third of the way down or
 * a fast, long swipe closes it, and a swipe that started by scrolling the content never moves it.
 * Back and the caller's own buttons close it too.
 */
@Composable
fun FolioSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Folio.colors.surface,
    contentColor: Color = Folio.colors.label,
    scrimColor: Color = Color.Black.copy(alpha = 0.32f),
    handle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val motion = remember { SheetMotion(scope, density) }
    SideEffect { motion.onHidden = onDismiss }
    Dialog(onDismissRequest = { motion.hide() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false,
    )) {
        // The sheet draws its own scrim, over the status bar and cutout too.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.let {
                it.setDimAmount(0f)
                it.attributes = it.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val total = constraints.maxHeight.toFloat()
            val full = WindowInsets.statusBars.getTop(density).toFloat()
            SideEffect { motion.layout(full, total) }
            Box(Modifier.fillMaxSize().drawBehind { drawRect(scrimColor, alpha = motion.scrimFraction()) })
            Column(
                modifier
                    .offset { IntOffset(0, motion.visualOffset().roundToInt()) }
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .clip(SheetShape)
                    .background(containerColor)
                    .nestedScroll(motion.connection)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val gesture = motion.pressed()
                            do { val event = awaitPointerEvent(PointerEventPass.Initial) } while (event.changes.any { it.pressed })
                            motion.released(gesture)
                        }
                    }
                    .draggable(rememberDraggableState { motion.drag(it) }, Orientation.Vertical,
                        onDragStopped = { motion.settle(it) }),
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(max = with(density) { (total - full).toDp() })
                        .onSizeChanged { motion.contentHeight(it.height) },
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        Column(Modifier.fillMaxWidth(), content = content)
                    }
                    if (handle) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(36.dp, 5.dp)
                            .background(contentColor.copy(alpha = 0.22f), CircleShape))
                    }
                }
                // Shows below the content while the sheet is pulled above its top.
                Spacer(Modifier.height(Overshoot))
            }
        }
    }
}

/** Sheet position and gestures; offsets are the sheet top in window px. */
private class SheetMotion(private val scope: CoroutineScope, density: Density) {
    private val flingVelocity = with(density) { 1800.dp.toPx() }
    private val minTravel = with(density) { 80.dp.toPx() }
    private val overshoot = with(density) { Overshoot.toPx() }
    private val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    private var offset by mutableFloatStateOf(Float.NaN)
    var onHidden: () -> Unit = {}

    private var full = 0f
    private var hidden = 0f
    private var measured = 0
    private var dragging = false
    private var hiding = false
    private var contentScrolled = false
    private var gestures = 0
    private var job: Job? = null
    private var jobTarget = Float.NaN

    private val top get() = max(full, hidden - measured)

    fun visualOffset() = if (offset.isNaN()) hidden else offset

    fun scrimFraction(): Float =
        if (offset.isNaN() || hidden <= top) 0f else ((hidden - offset) / (hidden - top)).coerceIn(0f, 1f)

    fun layout(full: Float, total: Float) {
        this.full = full
        hidden = total
        refresh()
    }

    fun contentHeight(height: Int) {
        if (height != measured) { measured = height; refresh() }
    }

    /** Opens on the first measure and follows top changes (rotation, content loading). */
    private fun refresh() {
        if (measured == 0 || dragging || hiding) return
        if (offset.isNaN()) offset = hidden
        val moving = job?.isActive == true && abs(jobTarget - top) < 0.5f
        if (abs(offset - top) > 0.5f && !moving) animateTo(top, 0f)
    }

    fun pressed(): Int { contentScrolled = false; return ++gestures }

    /** A gesture that moved the sheet but ended without a fling still has to settle. */
    fun released(gesture: Int) {
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            if (gesture == gestures) settle(0f)
        }
    }

    fun drag(delta: Float) {
        if (hiding || offset.isNaN()) return
        if (!dragging) { job?.cancel(); dragging = true }
        offset = SheetMath.drag(offset, delta, top, overshoot)
    }

    fun settle(velocity: Float) {
        if (!dragging) return
        dragging = false
        if (SheetMath.closes(offset, velocity, top, hidden, flingVelocity, minTravel, dismissFraction = 0.35f)) hide(velocity)
        else animateTo(top, velocity)
    }

    fun hide(velocity: Float = 0f) {
        if (hiding) return
        hiding = true
        dragging = false
        job?.cancel()
        if (offset.isNaN()) { onHidden(); return }
        job = scope.launch {
            animate(offset, hidden, velocity, spec) { value, _ -> offset = value }
            onHidden()
        }
    }

    private fun animateTo(target: Float, velocity: Float) {
        job?.cancel()
        jobTarget = target
        job = scope.launch { animate(offset, target, velocity, spec) { value, _ -> offset = value } }
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // A sheet already pulled down takes the move back before the content scrolls.
            if (source != NestedScrollSource.UserInput || hiding || !dragging) return Offset.Zero
            drag(available.y)
            return Offset(0f, available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || hiding) return Offset.Zero
            if (consumed.y != 0f) contentScrolled = true
            if (available.y <= 0f || contentScrolled) return Offset.Zero
            drag(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragging) return Velocity.Zero
            settle(available.y)
            return available
        }
    }
}

/**
 * Sheet header row, 44 dp buttons at the edges.
 * [centered] puts the title in the middle («Поиск в книге», «Настройка темы»); otherwise it
 * leads as a large serif title («Коллекции»).
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
    titleStyle: TextStyle = if (centered) FolioType.headline else FolioType.sheetTitle,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (leading != null) Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Text(
            title,
            style = titleStyle,
            color = Folio.colors.label,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (centered) {
                Modifier.align(Alignment.Center).padding(horizontal = 56.dp)
            } else {
                Modifier.align(Alignment.CenterStart).padding(start = if (leading != null) 56.dp else 20.dp, end = 120.dp)
            },
        )
        if (trailing != null) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}
