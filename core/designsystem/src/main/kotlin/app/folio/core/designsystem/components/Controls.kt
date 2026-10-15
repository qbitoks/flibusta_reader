package app.folio.core.designsystem.components

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import kotlin.math.roundToInt

/** iOS switch: 51 × 31 track, 27 dp thumb, green when on. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Folio.colors
    val track by animateColorAsState(if (checked) c.switchOn else c.fill, label = "track")
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, spring(stiffness = 900f), label = "thumb")
    Box(
        modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) { onCheckedChange(!checked) }
            .semantics { stateDescription = if (checked) "on" else "off" },
    ) {
        Box(
            Modifier
                .offset(x = offset, y = 2.dp)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * iOS-style slider: thin track filled with the label colour up to a white capsule thumb.
 * [onValueChangeFinished] fires on release, so expensive consumers (re-pagination) run once.
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Float = 0f,
    onValueChangeFinished: () -> Unit = {},
    contentDescription: String? = null,
    enabled: Boolean = true,
    onValueChangeCancelled: () -> Unit = {},
) {
    val c = Folio.colors
    val density = LocalDensity.current
    val latestValue by rememberUpdatedState(value)
    val changeValue by rememberUpdatedState(onValueChange)
    val finishChange by rememberUpdatedState(onValueChangeFinished)
    val cancelChange by rememberUpdatedState(onValueChangeCancelled)
    val span = valueRange.endInclusive - valueRange.start
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(valueRange.start, valueRange.endInclusive)
        return if (steps > 0f) (valueRange.start + ((clamped - valueRange.start) / steps).roundToInt() * steps) else clamped
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbW = 36.dp
        val width = maxWidth
        val trackPx = with(density) { (width - thumbW).toPx() }.coerceAtLeast(1f)
        val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
        val gestures = Modifier
            .pointerInput(valueRange, trackPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val halfThumb = thumbW.toPx() / 2
                    val thumbCenter = halfThumb + ((latestValue - valueRange.start) / span) * trackPx
                    // Keep the grab offset on the thumb; a track press chooses its own position.
                    val grabOffset = if (kotlin.math.abs(down.position.x - thumbCenter) <= halfThumb)
                        down.position.x - thumbCenter else 0f
                    var last = latestValue
                    fun update(x: Float) {
                        val next = snap(valueRange.start + ((x - grabOffset - halfThumb) / trackPx) * span)
                        if (next != last) {
                            last = next
                            changeValue(next)
                        }
                    }
                    var released = false
                    try {
                        update(down.position.x)
                        // Own both axes until release, including movement outside the track.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            update(change.position.x)
                            event.changes.forEach { it.consume() }
                            if (!change.pressed) {
                                released = true
                                finishChange()
                                break
                            }
                        }
                    } finally {
                        if (!released) cancelChange()
                    }
                }
            }
        Box(Modifier.fillMaxWidth().fillMaxHeight().then(if (enabled) gestures else Modifier), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .padding(horizontal = thumbW / 2)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.fill),
            )
            Box(
                Modifier
                    .padding(start = thumbW / 2)
                    .width((width - thumbW) * fraction)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.label),
            )
            Box(
                Modifier
                    .offset(x = (width - thumbW) * fraction)
                    .size(thumbW, 24.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/**
 * iOS segmented control: grey capsule with the selected segment raised on a lighter capsule.
 * [icons] lead the labels (an empty label leaves an icon-only segment), or sit above small
 * captions when [stacked]; [descriptions] replace the spoken labels; [role] is [Role.RadioButton]
 * when the control picks a value.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
    descriptions: List<String>? = null,
    role: Role = Role.Tab,
    stacked: Boolean = false,
    activeIndices: Set<Int>? = null,
) {
    val c = Folio.colors
    val outer = if (stacked) RoundedCornerShape(16.dp) else CircleShape
    val inner = if (stacked) RoundedCornerShape(13.dp) else CircleShape
    Row(
        modifier
            .fillMaxWidth()
            .height(if (stacked) 58.dp else 36.dp)
            .clip(outer)
            .background(c.fill)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = activeIndices?.contains(i) ?: (i == selected)
            val segment = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (isSelected) Modifier.shadow(2.dp, inner) else Modifier)
                .clip(inner)
                .background(if (isSelected) (if (c.isDark) Color(0xFF636366) else Color.White) else Color.Transparent)
                .selectable(isSelected, role = role) { onSelect(i) }
                .then(descriptions?.getOrNull(i)?.let { d -> Modifier.semantics { contentDescription = d } } ?: Modifier)
                .padding(horizontal = 4.dp)
            val icon = icons?.getOrNull(i)
            if (stacked) {
                Column(segment, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    icon?.let { Icon(it, null, tint = c.label, modifier = Modifier.size(22.dp)) }
                    val caption = if (isSelected) FolioType.caption.copy(fontWeight = FolioType.headline.fontWeight) else FolioType.caption
                    // Captions shrink rather than push the icons out: a long name must not drop the row.
                    BasicText(label, Modifier.padding(horizontal = 2.dp), style = caption.copy(color = c.label, textAlign = TextAlign.Center),
                        maxLines = 1, softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = caption.fontSize, stepSize = 0.5.sp))
                }
            } else {
                Row(segment, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    icon?.let { Icon(it, null, tint = c.label, modifier = Modifier.size(18.dp)) }
                    val text = if (isSelected) FolioType.subheadline.copy(fontWeight = FolioType.headline.fontWeight) else FolioType.subheadline
                    if (label.isNotEmpty()) BasicText(label, style = text.copy(color = c.label), maxLines = 1, softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = text.fontSize, stepSize = 0.5.sp))
                }
            }
        }
    }
}

/** Row of dots marking discrete steps (font size), filled up to [current]. */
@Composable
fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val c = Folio.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= current) c.label else c.tertiaryLabel),
            )
        }
    }
}
