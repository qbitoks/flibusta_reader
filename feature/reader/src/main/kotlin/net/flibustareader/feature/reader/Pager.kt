package net.flibustareader.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import net.flibustareader.core.layout.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class TapAction { Next, Prev, ToggleChrome, Handled }

/**
 * Horizontal page turner. Only three pages exist at any time (previous, current, next),
 * so turning never depends on knowing the total page count.
 *
 * @param mode «Скольжение» slides the top page over the lower one (Apple's overlay),
 *   «Быстрое выцветание» cross-fades in 100 ms (both as in Apple Books).
 * @param onTap decides what a tap at a point means (edge zones, links, chrome toggle).
 * @param onSwipeDown a downward swipe anywhere closes the book.
 * @param onLongPress a finger held still; returns true if it started a selection, which then
 *   follows the finger through [onLongPressDrag] until release.
 */
@Composable
fun HorizontalPageTurner(
    current: Page?,
    prev: Page?,
    next: Page?,
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
    content: @Composable (Page?) -> Unit,
) {
    val offset = remember { Animatable(0f) }
    /** Cross-fade progress towards the page in [fadeDirection] (Fade mode only). */
    val fade = remember { Animatable(0f) }
    var fadeDirection by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableStateOf(1f) }
    val density = LocalDensity.current
    val swipeDownDistance = with(density) { 90.dp.toPx() }
    val hasNext by rememberUpdatedState(next != null)
    val hasPrev by rememberUpdatedState(prev != null)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrev by rememberUpdatedState(onPrev)
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestOnLongPressDrag by rememberUpdatedState(onLongPressDrag)
    val latestMode by rememberUpdatedState(mode)
    var turnJob by remember { mutableStateOf<Job?>(null) }
    /** Where the finger went down; tilts the «Загиб» fold. Taps on the edge curl from low on the page. */
    var curlAnchorY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    val curl = remember { if (PageCurl.supported) PageCurl() else null }
    val step = width

    fun turn(scope: CoroutineScope, direction: Int, velocity: Float = 0f) {
        val previous = turnJob
        turnJob = scope.launch {
            // A tap during an animation finishes the running turn immediately, then starts the next.
            previous?.let {
                it.cancel()
                it.join()
            }
            val possible = if (direction > 0) hasNext else hasPrev
            if (!possible) {
                offset.animateTo(0f, spring(stiffness = 600f))
                return@launch
            }
            try {
                when (latestMode) {
                    TurnMode.Slide -> offset.animateTo(
                        -direction * step,
                        tween(durationMillis = if (velocity != 0f) 220 else 300, easing = FastOutSlowInEasing),
                    )
                    TurnMode.Curl -> offset.animateTo(
                        -direction * step,
                        tween(durationMillis = if (velocity != 0f) 300 else 450, easing = FastOutSlowInEasing),
                    )
                    TurnMode.Fade -> {
                        offset.snapTo(0f)
                        fadeDirection = direction
                        fade.snapTo(0f)
                        fade.animateTo(1f, tween(durationMillis = 100))
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    if (direction > 0) latestOnNext() else latestOnPrev()
                    offset.snapTo(0f)
                    fade.snapTo(0f)
                    fadeDirection = 0
                }
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
                    var held = false
                    val drag = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                overSlop = over
                            }
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
                        }
                        return@awaitEachGesture
                    }
                    if (drag == null) {
                        // No horizontal movement: a tap, a downward swipe, or a cancelled gesture.
                        val up = currentEvent.changes.firstOrNull()
                        // A control above the page (note marker, palette) that consumed the tap keeps it.
                        if (up != null && !up.pressed && !up.isConsumed) {
                            val d = up.position - down.position
                            when {
                                d.getDistance() < viewConfiguration.touchSlop -> when (latestOnTap(down.position, width).also { curlAnchorY = height * 0.85f }) {
                                    TapAction.Next -> turn(scope, +1)
                                    TapAction.Prev -> turn(scope, -1)
                                    else -> Unit
                                }
                                d.y > swipeDownDistance && d.y > abs(d.x) * 1.5f -> latestOnSwipeDown()
                            }
                        }
                        return@awaitEachGesture
                    }
                    onDragStart()
                    turnJob?.cancel()
                    if (latestMode == TurnMode.Fade) {
                        // Fade mode does not follow the finger: the swipe only picks the direction.
                        var dx = overSlop
                        horizontalDrag(drag.id) { change ->
                            dx += change.positionChange().x
                            change.consume()
                        }
                        if (dx < 0f) turn(scope, +1) else turn(scope, -1)
                        return@awaitEachGesture
                    }
                    // Past the first/last page the page follows the finger with resistance.
                    fun resist(raw: Float) = if ((raw < 0f && !hasNext) || (raw > 0f && !hasPrev)) raw * 0.3f else raw
                    var raw = offset.value + overSlop
                    scope.launch { offset.snapTo(resist(raw)) }
                    horizontalDrag(drag.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        raw += change.positionChange().x
                        change.consume()
                        scope.launch { offset.snapTo(resist(raw)) }
                    }
                    val velocity = tracker.calculateVelocity().x
                    val o = offset.value
                    when {
                        (o < -width * 0.2f || velocity < -900f) && velocity < 400f && hasNext -> turn(scope, +1, velocity)
                        (o > width * 0.2f || velocity > 900f) && velocity > -400f && hasPrev -> turn(scope, -1, velocity)
                        else -> scope.launch { offset.animateTo(0f, spring(stiffness = 700f)) }
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
        fun Modifier.layer(block: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit) = graphicsLayer(block)

        if (latestMode == TurnMode.Curl && curl != null) {
            // «Загиб»: the top page curls away over the next one, or the previous page uncurls over this one.
            fun Modifier.curled(turned: () -> Float?) = layer {
                val t = turned()
                renderEffect = t?.let { curl.effect(it, size.width, size.height, curlAnchorY, pageColor, pageColor.luminance() < 0.4f) }
            }
            if (next != null) {
                Box(Modifier.fillMaxSize().layer { alpha = if (offset.value < 0f) 1f else 0f }.background(pageColor)) { content(next) }
            }
            Box(Modifier.fillMaxSize().curled { offset.value.takeIf { it < 0f }?.let { -it / step } }.background(pageColor)) { content(current) }
            if (prev != null) {
                Box(
                    Modifier.fillMaxSize()
                        .layer { alpha = if (offset.value > 0f) 1f else 0f }
                        .curled { offset.value.takeIf { it > 0f }?.let { 1f - it / step } }
                        .background(pageColor),
                ) { content(prev) }
            }
            return@Box
        }

        // Lower page when going forward.
        if (next != null) {
            Box(
                Modifier.fillMaxSize().layer {
                    val o = offset.value
                    alpha = when {
                        fadeDirection > 0 -> 0f
                        o < 0f -> 1f
                        else -> 0f
                    }
                }.background(pageColor),
            ) { content(next) }
            Box(Modifier.fillMaxSize().layer {
                val o = offset.value
                alpha = if (o < 0f) 0.25f * (1f + o / step).coerceIn(0f, 1f) else 0f
            }.background(Color.Black))
        }
        // Current page: on top going forward, underneath going back.
        Box(
            Modifier.fillMaxSize().layer {
                val o = offset.value
                translationX = if (o < 0f) o else 0f
                alpha = if (fadeDirection != 0) 1f - fade.value else 1f
                val moving = o < 0f
                shape = movingShape
                clip = moving
                shadowElevation = if (moving) shadow else 0f
            }.background(pageColor),
        ) { content(current) }
        if (prev != null) {
            Box(Modifier.fillMaxSize().layer {
                val o = offset.value
                alpha = if (o > 0f) 0.25f * (o / step).coerceIn(0f, 1f) else 0f
            }.background(Color.Black))
            // Previous page arrives from the left over the current one.
            Box(
                Modifier.fillMaxSize().layer {
                    val o = offset.value
                    translationX = if (o > 0f) o - step else -step
                    alpha = if (o > 0f) 1f else 0f
                    shape = movingShape
                    clip = o > 0f
                    shadowElevation = if (o > 0f) shadow else 0f
                }.background(pageColor),
            ) { content(prev) }
        }
        // During a fade the target page is drawn in place, over the current one.
        if (fadeDirection > 0 && next != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(pageColor)) { content(next) }
        } else if (fadeDirection < 0 && prev != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(pageColor)) { content(prev) }
        }
    }
}

enum class TurnMode { Slide, Fade, Curl }
