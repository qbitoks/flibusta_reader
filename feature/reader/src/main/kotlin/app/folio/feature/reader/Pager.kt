package app.folio.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.folio.core.layout.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

enum class TapAction { Next, Prev, ToggleChrome, Handled }

data class PageTurnEvent(val active: Boolean, val direction: Int = 0, val from: Page? = null, val to: Page? = null)

/**
 * Horizontal page turner. Only three pages exist at any time (previous, current, next),
 * so turning never depends on knowing the total page count.
 *
 * @param mode «Скольжение» slides the top page over the lower one (Apple's overlay),
 *   «Быстрое выцветание» cross-fades in 100 ms (both as in Apple Books).
 * @param canPrev / [canNext] whether the book goes on; the neighbouring page itself may still be
 *   laying out (a new chapter), and a turn then waits for it instead of bouncing.
 * @param onTap decides what a tap at a point means (edge zones, links, chrome toggle).
 * @param onSwipeDown a long downward swipe closes the book.
 * @param onLongPress a finger held still; returns true if it started a selection, which then
 *   follows the finger through [onLongPressDrag] until release. Otherwise the finger can still
 *   drag the page.
 */
@Composable
fun HorizontalPageTurner(
    current: Page?,
    prev: Page?,
    next: Page?,
    canPrev: Boolean,
    canNext: Boolean,
    mode: TurnMode,
    onNext: () -> Boolean,
    onPrev: () -> Boolean,
    onTap: (Offset, width: Float) -> TapAction,
    onDragStart: () -> Unit,
    onSwipeDown: () -> Unit,
    pageColor: Color,
    onLongPress: (Offset) -> Boolean = { false },
    onLongPressDrag: (Offset) -> Unit = {},
    modifier: Modifier = Modifier,
    onTurnEvent: (PageTurnEvent) -> Unit = {},
    content: @Composable (Page?) -> Unit,
) {
    /** Offset of an animation; while a finger drags, [dragOffset] is shown instead. */
    val offset = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var gesturing by remember { mutableStateOf(false) }
    /** Cross-fade progress towards the page in [fadeDirection] (Fade mode only). */
    val fade = remember { Animatable(0f) }
    var fadeDirection by remember { mutableIntStateOf(0) }
    /** A turn is waiting for the next chapter to lay out. */
    var waiting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val swipeDownDistance = with(density) { 140.dp.toPx() }
    val flingVelocity = with(density) { 350.dp.toPx() }
    val latestNext by rememberUpdatedState(next)
    val latestPrev by rememberUpdatedState(prev)
    val latestCurrent by rememberUpdatedState(current)
    val latestCanNext by rememberUpdatedState(canNext)
    val latestCanPrev by rememberUpdatedState(canPrev)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrev by rememberUpdatedState(onPrev)
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestOnLongPressDrag by rememberUpdatedState(onLongPressDrag)
    val latestOnTurnEvent by rememberUpdatedState(onTurnEvent)
    val latestMode by rememberUpdatedState(mode)
    var turnJob by remember { mutableStateOf<Job?>(null) }
    var committedFrom by remember { mutableStateOf<Page?>(null) }
    var committedTo by remember { mutableStateOf<Page?>(null) }
    /** Where the finger went down; tilts the «Загиб» fold. Taps on the edge curl from low on the page. */
    var curlAnchorY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    val curl = remember { if (PageCurl.supported) PageCurl() else null }
    val step = width

    /** Current horizontal shift of the turning page; read only inside layer blocks, so frames skip recomposition. */
    fun shift(): Float = if (dragging) dragOffset else offset.value

    /** Takes over a finished drag at [from] so the page never jumps back for a frame. */
    suspend fun takeOver(from: Float?) {
        if (from == null) return
        offset.snapTo(from)
        dragging = false
    }

    fun turn(scope: CoroutineScope, direction: Int, velocity: Float = 0f, from: Float? = null) {
        val previous = turnJob
        turnJob = scope.launch {
            // A tap during an animation finishes the running turn immediately, then starts the next.
            previous?.let {
                it.cancel()
                it.join()
            }
            val departed = committedFrom
            val landed = committedTo
            if (landed != null && latestCurrent === departed) {
                // Navigation is synchronous, but new page/neighbor parameters arrive at recomposition.
                withTimeoutOrNull(WAIT_FOR_PAGE_MS) {
                    snapshotFlow { latestCurrent }.first { it === landed || it !== departed }
                }
            }
            committedFrom = null
            committedTo = null
            takeOver(from)
            val origin = latestCurrent
            var destination: Page? = null
            var commit = false
            latestOnTurnEvent(PageTurnEvent(active = true))
            fun target() = if (direction > 0) latestNext else latestPrev
            val possible = if (direction > 0) latestCanNext else latestCanPrev
            try {
                if (possible && target() == null) {
                    // The next chapter is still laying out: hold the page and wait rather than bounce.
                    waiting = true
                    try {
                        withTimeoutOrNull(WAIT_FOR_PAGE_MS) { snapshotFlow { target() }.first { it != null } }
                    } finally {
                        waiting = false
                    }
                }
                destination = target()
                if (!possible || destination == null || latestCurrent !== origin) {
                    offset.animateTo(0f, spring(stiffness = 600f))
                    return@launch
                }
                commit = true
                when (latestMode) {
                    TurnMode.Slide, TurnMode.Curl -> {
                        val goal = -direction * step
                        val full = if (latestMode == TurnMode.Curl) 450 else 300
                        offset.animateTo(
                            goal,
                            tween(
                                durationMillis = turnDuration(abs(goal - offset.value), step, velocity, full),
                                // A fling leaves at the finger's speed; a tap eases in.
                                easing = if (velocity != 0f) LinearOutSlowInEasing else FastOutSlowInEasing,
                            ),
                        )
                    }
                    TurnMode.Fade -> {
                        offset.snapTo(0f)
                        fadeDirection = direction
                        fade.snapTo(0f)
                        fade.animateTo(1f, tween(durationMillis = 100))
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    val moved = commit && latestCurrent === origin &&
                        if (direction > 0) latestOnNext() else latestOnPrev()
                    if (moved) {
                        committedFrom = origin
                        committedTo = destination
                    }
                    offset.snapTo(0f)
                    fade.snapTo(0f)
                    fadeDirection = 0
                    latestOnTurnEvent(PageTurnEvent(active = gesturing || dragging, direction = direction, from = origin, to = destination.takeIf { moved }))
                }
            }
        }
    }

    fun settleBack(scope: CoroutineScope, from: Float) {
        turnJob = scope.launch {
            takeOver(from)
            latestOnTurnEvent(PageTurnEvent(active = true))
            try {
                offset.animateTo(0f, spring(stiffness = 700f))
            } finally {
                latestOnTurnEvent(PageTurnEvent(active = gesturing || dragging || offset.value != 0f))
            }
        }
    }

    Box(
        modifier
            .onSizeChanged {
                width = it.width.toFloat().coerceAtLeast(1f)
                height = it.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A drag lifts the sheet where the finger is; a tap curls it from the lower corner.
                    curlAnchorY = down.position.y
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var overSlop = 0f
                    val onSlop = { change: PointerInputChange, over: Float ->
                        change.consume()
                        overSlop = over
                    }
                    var held = false
                    var drag = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            awaitHorizontalTouchSlopOrCancellation(down.id, onSlop)
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        held = true
                        null
                    }
                    if (held) {
                        if (latestOnLongPress(down.position)) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                if (change.positionChanged()) latestOnLongPressDrag(change.position)
                            }
                            return@awaitEachGesture
                        }
                        // Held over blank space: a slow drag still turns the page.
                        drag = awaitHorizontalTouchSlopOrCancellation(down.id, onSlop) ?: return@awaitEachGesture
                    }
                    if (drag == null) {
                        // No horizontal movement: a tap, a downward swipe, or nothing.
                        val up = currentEvent.changes.firstOrNull()
                        // A control above the page (note marker, palette) that consumed the tap keeps it.
                        if (up != null && !up.pressed && !up.isConsumed) {
                            val d = up.position - down.position
                            when (classifyRelease(d.x, d.y, viewConfiguration.touchSlop, swipeDownDistance)) {
                                Release.Tap -> when (latestOnTap(down.position, width)) {
                                    TapAction.Next -> { curlAnchorY = height * 0.85f; turn(scope, +1) }
                                    TapAction.Prev -> { curlAnchorY = height * 0.85f; turn(scope, -1) }
                                    else -> Unit
                                }
                                Release.SwipeDown -> latestOnSwipeDown()
                                Release.None -> Unit
                            }
                        }
                        return@awaitEachGesture
                    }
                    latestOnDragStart()
                    gesturing = true
                    turnJob?.cancel()
                    latestOnTurnEvent(PageTurnEvent(active = true))
                    if (latestMode == TurnMode.Fade) {
                        // Fade mode does not follow the finger: the swipe only picks the direction.
                        var dx = overSlop
                        val released = horizontalDrag(drag.id) { change ->
                            dx += change.positionChange().x
                            change.consume()
                        }
                        gesturing = false
                        if (released) {
                            if (dx < 0f) turn(scope, +1) else turn(scope, -1)
                        } else {
                            latestOnTurnEvent(PageTurnEvent(active = false))
                        }
                        return@awaitEachGesture
                    }
                    // Past the first/last page the page follows the finger with resistance.
                    fun resist(raw: Float) = if ((raw < 0f && !latestCanNext) || (raw > 0f && !latestCanPrev)) raw * 0.3f else raw
                    var raw = overSlop
                    dragOffset = resist(raw)
                    dragging = true
                    val released = horizontalDrag(drag.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        raw += change.positionChange().x
                        change.consume()
                        dragOffset = resist(raw)
                    }
                    gesturing = false
                    if (!released) {
                        settleBack(scope, dragOffset)
                        return@awaitEachGesture
                    }
                    val velocity = tracker.calculateVelocity().x
                    val o = dragOffset
                    when (settleDirection(o, velocity, width, flingVelocity, latestCanNext, latestCanPrev)) {
                        1 -> turn(scope, +1, velocity, from = o)
                        -1 -> turn(scope, -1, velocity, from = o)
                        else -> settleBack(scope, o)
                    }
                }
            },
    ) {
        // Apple Books' «Скольжение» is an overlay (guideline § 12): the top page slides over a
        // static lower page that is dimmed by ≈0.25 × the part still covered. Forward, the current
        // page leaves to the left over the next one; back, the previous page arrives from the left
        // over the current one. The moving page has rounded corners and a soft shadow.
        // All layers stay composed; only layer properties change per frame (no recomposition).
        val movingShape = RoundedCornerShape(with(density) { 27.dp })
        val shadow = with(density) { 14.dp.toPx() }
        fun Modifier.layer(block: GraphicsLayerScope.() -> Unit) = graphicsLayer(block)

        if (latestMode == TurnMode.Curl && curl != null) {
            // «Загиб»: the top page curls away over the next one, or the previous page uncurls over this one.
            fun Modifier.curled(turned: () -> Float?) = layer {
                val t = turned()
                renderEffect = t?.let { curl.effect(it, size.width, size.height, curlAnchorY, pageColor, pageColor.luminance() < 0.4f) }
            }
            if (next != null) {
                Box(Modifier.fillMaxSize().layer { alpha = if (shift() < 0f) 1f else 0f }.background(pageColor)) { content(next) }
            }
            Box(Modifier.fillMaxSize().curled { shift().takeIf { it < 0f }?.let { -it / step } }.background(pageColor)) { content(current) }
            if (prev != null) {
                Box(
                    Modifier.fillMaxSize()
                        .layer { alpha = if (shift() > 0f) 1f else 0f }
                        .curled { shift().takeIf { it > 0f }?.let { 1f - it / step } }
                        .background(pageColor),
                ) { content(prev) }
            }
            return@Box
        }

        // Each page keeps its composition and drawn layer while its role changes with a turn
        // (next → current → previous), so turning does not draw the text of all three pages again.
        val roles = listOfNotNull(next?.let { it to ROLE_NEXT }, current?.let { it to ROLE_CURRENT }, prev?.let { it to ROLE_PREV })
        for ((page, role) in roles) key(page) {
            Box(
                Modifier.fillMaxSize().layer {
                    val o = shift()
                    when (role) {
                        // Lower page when going forward.
                        ROLE_NEXT -> {
                            translationX = 0f
                            alpha = if (fadeDirection == 0 && o < 0f) 1f else 0f
                            clip = false
                            shadowElevation = 0f
                        }
                        // On top going forward, underneath going back.
                        ROLE_CURRENT -> {
                            translationX = if (o < 0f) o else 0f
                            alpha = if (fadeDirection != 0) 1f - fade.value else 1f
                            shape = movingShape
                            clip = o < 0f
                            shadowElevation = if (o < 0f) shadow else 0f
                        }
                        // Previous page arrives from the left over the current one.
                        else -> {
                            translationX = if (o > 0f) o - step else -step
                            alpha = if (o > 0f) 1f else 0f
                            shape = movingShape
                            clip = o > 0f
                            shadowElevation = if (o > 0f) shadow else 0f
                        }
                    }
                }.background(pageColor),
            ) { content(page) }
            // The dim over whichever page is being uncovered or covered.
            if (role != ROLE_PREV) {
                Box(
                    Modifier.fillMaxSize().layer {
                        val o = shift()
                        alpha = when {
                            role == ROLE_NEXT && o < 0f -> 0.25f * (1f + o / step).coerceIn(0f, 1f)
                            role == ROLE_CURRENT && o > 0f && prev != null -> 0.25f * (o / step).coerceIn(0f, 1f)
                            else -> 0f
                        }
                    }.background(Color.Black),
                )
            }
        }
        // During a fade the target page is drawn in place, over the current one.
        if (fadeDirection > 0 && next != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(pageColor)) { content(next) }
        } else if (fadeDirection < 0 && prev != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(pageColor)) { content(prev) }
        }

        // A spinner only if the next chapter takes noticeably long to lay out.
        var spinner by remember { mutableStateOf(false) }
        LaunchedEffect(waiting) {
            if (waiting) delay(200)
            spinner = waiting
        }
        if (spinner) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
    }
}

enum class TurnMode { Slide, Fade, Curl }

private const val ROLE_NEXT = 1
private const val ROLE_CURRENT = 0
private const val ROLE_PREV = -1
private const val WAIT_FOR_PAGE_MS = 5000L
