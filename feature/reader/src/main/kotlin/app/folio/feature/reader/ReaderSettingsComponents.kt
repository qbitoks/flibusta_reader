package app.folio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import app.folio.core.designsystem.components.IosSlider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import app.folio.core.designsystem.components.SegmentedControl
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.GroupedCard
import kotlin.math.roundToInt

/** A labelled disclosure, with choices in the same card instead of another window. */
@Composable
internal fun <T> SettingsChoice(
    title: String, current: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit,
    optionStyle: @Composable (T) -> TextStyle = { FolioType.body },
) {
    var expanded by remember { mutableStateOf(false) }
    val c = Folio.colors
    Column {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = FolioType.body, color = c.label)
                Text(options.firstOrNull { it.first == current }?.second.orEmpty(), style = FolioType.subheadline, color = c.secondaryLabel)
            }
            Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                null, tint = c.secondaryLabel, modifier = Modifier.padding(start = 12.dp).size(22.dp))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp)).background(c.fill).selectableGroup()) {
                options.forEach { (value, label) ->
                    SettingsDivider()
                    Row(Modifier.fillMaxWidth().selectable(value == current, role = Role.RadioButton) {
                        onSelect(value); expanded = false
                    }.heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = optionStyle(value), color = c.label, modifier = Modifier.weight(1f))
                        if (value == current) SettingsCheck()
                    }
                }
            }
        }
    }
}

/** Short option sets stay visible and form a separate radio group, never a nested menu. */
@Composable
internal fun <T> SettingsOptions(
    current: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    optionStyle: @Composable (T) -> TextStyle = { FolioType.body },
    sample: (@Composable (T) -> Unit)? = null,
) {
    val c = Folio.colors
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, (value, label) ->
            if (index > 0) SettingsDivider()
            Row(Modifier.fillMaxWidth().selectable(value == current, role = Role.RadioButton) { onSelect(value) }
                .heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { if (value == current) SettingsCheck() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = optionStyle(value), color = c.label)
                    sample?.invoke(value)
                }
            }
        }
    }
}

internal class SegmentOption<T>(val value: T, val label: String, val description: String = label, val icon: ImageVector? = null)

/**
 * Short choices as one segmented row inside a card. Falls back to the visible radio list when
 * large text or long labels would not fit a segment, so no choice is ever truncated.
 */
@Composable
internal fun <T> SettingsSegments(current: T, options: List<SegmentOption<T>>, stacked: Boolean = false, onSelect: (T) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = FolioType.subheadline.copy(fontWeight = FolioType.headline.fontWeight)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val segment = (maxWidth - 24.dp - 6.dp - 2.dp * (options.size - 1)) / options.size
        // Labels shrink to fit their segment; only text too large even at the minimum falls back.
        val fits = stacked || density.fontScale <= 1.3f && options.all { o ->
            val text = if (o.label.isEmpty()) 0.dp else with(density) { measurer.measure(o.label, style).size.width.toDp() }
            (text + (if (o.icon != null) 22.dp else 0.dp) + 12.dp) * 0.7f <= segment
        }
        if (fits) {
            SegmentedControl(options.map { it.label }, options.indexOfFirst { it.value == current }, { onSelect(options[it].value) },
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                icons = options.map { it.icon }, descriptions = options.map { it.description }, role = Role.RadioButton, stacked = stacked)
        } else {
            SettingsOptions(current, options.map { it.value to it.description }, onSelect)
        }
    }
}

/**
 * Group title with the current choice at the trailing edge, as iOS settings show values. A dot marks
 * a group this book sets itself while its own settings are edited.
 */
@Composable
internal fun SettingsGroupHeader(title: String, value: String? = null, own: Boolean = false,
    trailing: (@Composable () -> Unit)? = null) {
    val c = Folio.colors
    val ownLabel = stringResource(R.string.settings_own_mark)
    Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = FolioType.title3, color = c.label, modifier = Modifier.weight(1f, fill = false))
        if (own) Text(stringResource(R.string.settings_own_badge), style = FolioType.caption, fontWeight = FontWeight.SemiBold,
            color = c.accent, maxLines = 1, modifier = Modifier.padding(start = 8.dp).clip(CircleShape)
                .background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp)
                .clearAndSetSemantics { contentDescription = ownLabel })
        }
        if (value != null) Text(value, style = FolioType.subheadline, color = c.secondaryLabel,
            maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        trailing?.invoke()
    }
}

@Composable
internal fun SettingsCheck() = Icon(Icons.Rounded.Check, null, tint = Folio.colors.label, modifier = Modifier.size(20.dp))

/** «Подробнее» row of a settings card; [expanded] is UI state only, never a saved setting. */
@Composable
internal fun SettingsDetailsRow(expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.line_details_expanded else R.string.line_details_collapsed)
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = state }
        .heightIn(min = 54.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.line_details), style = FolioType.body, color = Folio.colors.label, modifier = Modifier.weight(1f))
        val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "chevron")
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Folio.colors.secondaryLabel,
            modifier = Modifier.size(22.dp).rotate(turn))
    }
}

/** Disclosed details slide open below their «Подробнее» row; the content stays composed only while shown. */
@Composable
internal fun SettingsDetails(expanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(expanded, enter = expandVertically(tween(220)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
        Column { SettingsDivider(); content() }
    }
}

/** Quiet title above a segmented row: the segments are the control, the title only names it. */
@Composable
internal fun SegmentsTitle(text: String) = Text(text, style = FolioType.subheadline, color = Folio.colors.secondaryLabel,
    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp))

/** Small caption that splits a long card into named runs of rows. */
@Composable
internal fun SettingsSubheader(text: String) = Text(text.uppercase(), style = FolioType.caption.copy(letterSpacing = 0.6.sp),
    fontWeight = FontWeight.SemiBold, color = Folio.colors.secondaryLabel,
    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 2.dp).semantics { heading() })

/** Books-style choice outline: an accent ring set off from the tile by a small gap; draw the tile inside. */
@Composable
internal fun Modifier.selectionRing(selected: Boolean, corner: Dp): Modifier {
    val ring by animateColorAsState(if (selected) Folio.colors.accent else Color.Transparent, tween(180), label = "ring")
    return border(2.dp, ring, RoundedCornerShape(corner + 4.dp)).padding(4.dp)
}

/**
 * The one choice tile of the settings panel (palettes, fonts, tones): a hairline swatch with the
 * accent ring when selected and its name under it. [swatch] paints the swatch's inside.
 */
@Composable
internal fun ChoiceTile(name: String, selected: Boolean, background: Color, modifier: Modifier = Modifier,
    detail: String? = null, onClick: () -> Unit, swatch: @Composable BoxScope.() -> Unit) {
    val c = Folio.colors
    val tick = rememberSelectionTick()
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(RoundedCornerShape(16.dp))
        .selectable(selected, role = Role.RadioButton) { if (!selected) tick(); onClick() }
        .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(name, detail).joinToString(", ") },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().selectionRing(selected, 12.dp)) {
            Box(Modifier.fillMaxWidth().height(64.dp).clip(shape).background(background).border(0.5.dp, c.separator, shape),
                contentAlignment = Alignment.Center, content = swatch)
        }
        Text(name, style = FolioType.caption, color = if (selected) c.label else c.secondaryLabel,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { })
        if (detail != null) Text(detail, style = FolioType.caption, color = c.secondaryLabel, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** A light tick when a tile choice changes; reselecting the current one stays silent. */
@Composable
internal fun rememberSelectionTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

@Composable
internal fun SettingsDivider() = HorizontalDivider(color = Folio.colors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))

/** Shared labelled slider with native adjustable accessibility semantics. */
@Composable
internal fun SettingsSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, shown: String,
    low: ImageVector? = null, high: ImageVector? = null, onChange: (Float) -> Unit) {
    val c = Folio.colors
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FolioType.body, color = c.label, modifier = Modifier.weight(1f))
            Text(shown, style = FolioType.subheadline, color = c.secondaryLabel, modifier = Modifier.padding(start = 12.dp))
        }
        val change by rememberUpdatedState(onChange)
        val steps = ((range.endInclusive - range.start) / step).roundToInt().minus(1).coerceAtLeast(0)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (low != null) Icon(low, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        IosSlider(value = value, onValueChange = { change(it) }, valueRange = range, steps = step,
            contentDescription = title,
            modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps)
                setProgress { target ->
                    val snapped = range.start + ((target.coerceIn(range) - range.start) / step).roundToInt() * step
                    change(snapped.coerceIn(range))
                    true
                }
            })
        if (high != null) Icon(high, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        }
    }
}



/** The whole labelled row is one accessible switch with a generous tap target. */
@Composable
internal fun SettingsToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = 54.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = FolioType.body, color = Folio.colors.label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        app.folio.core.designsystem.components.IosSwitch(checked, onChange, Modifier.clearAndSetSemantics { })
    }
}
