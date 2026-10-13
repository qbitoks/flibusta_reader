package net.flibustareader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import net.flibustareader.core.designsystem.components.IosSlider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import net.flibustareader.core.designsystem.components.SegmentedControl
import androidx.compose.ui.unit.dp
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.components.GroupedCard
import kotlin.math.roundToInt

/** A labelled disclosure, with choices in the same card instead of another window. */
@Composable
internal fun <T> SettingsChoice(
    title: String, current: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit,
    optionStyle: @Composable (T) -> TextStyle = { FlibustaType.body },
) {
    var expanded by remember { mutableStateOf(false) }
    val c = Flibusta.colors
    Column {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = FlibustaType.body, color = c.label)
                Text(options.firstOrNull { it.first == current }?.second.orEmpty(), style = FlibustaType.subheadline, color = c.secondaryLabel)
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
    sample: (@Composable (T) -> Unit)? = null,
) {
    val c = Flibusta.colors
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, (value, label) ->
            if (index > 0) SettingsDivider()
            Row(Modifier.fillMaxWidth().selectable(value == current, role = Role.RadioButton) { onSelect(value) }
                .heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { if (value == current) SettingsCheck() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = FlibustaType.body, color = c.label)
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
internal fun <T> SettingsSegments(current: T, options: List<SegmentOption<T>>, onSelect: (T) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = FlibustaType.subheadline.copy(fontWeight = FlibustaType.headline.fontWeight)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val segment = (maxWidth - 24.dp - 6.dp - 2.dp * (options.size - 1)) / options.size
        val fits = density.fontScale <= 1.3f && options.all { o ->
            val text = if (o.label.isEmpty()) 0.dp else with(density) { measurer.measure(o.label, style).size.width.toDp() }
            text + (if (o.icon != null) 22.dp else 0.dp) + 12.dp <= segment
        }
        if (fits) {
            SegmentedControl(options.map { it.label }, options.indexOfFirst { it.value == current }, { onSelect(options[it].value) },
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                icons = options.map { it.icon }, descriptions = options.map { it.description }, role = Role.RadioButton)
        } else {
            SettingsOptions(current, options.map { it.value to it.description }, onSelect)
        }
    }
}

/** Group title with the current choice at the trailing edge, as iOS settings show values. */
@Composable
internal fun ValueGroupHeader(title: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom) {
        Text(title, style = FlibustaType.title3, color = Flibusta.colors.label, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = FlibustaType.subheadline, color = Flibusta.colors.secondaryLabel,
            maxLines = 1, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
internal fun SettingsCheck() = Icon(Icons.Rounded.Check, null, tint = Flibusta.colors.label, modifier = Modifier.size(20.dp))

@Composable
internal fun SettingsNote(text: String) = Text(text, style = FlibustaType.footnote,
    color = Flibusta.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))

@Composable
internal fun SettingsDivider() = HorizontalDivider(color = Flibusta.colors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))

/** Shared labelled slider with native adjustable accessibility semantics. */
@Composable
internal fun SettingsSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, shown: String, onChange: (Float) -> Unit) {
    val c = Flibusta.colors
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FlibustaType.body, color = c.label, modifier = Modifier.weight(1f))
            Text(shown, style = FlibustaType.subheadline, color = c.secondaryLabel, modifier = Modifier.padding(start = 12.dp))
        }
        val change by rememberUpdatedState(onChange)
        val steps = ((range.endInclusive - range.start) / step).roundToInt().minus(1).coerceAtLeast(0)
        IosSlider(value = value, onValueChange = { change(it) }, valueRange = range, steps = step,
            contentDescription = title,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps)
                setProgress { target ->
                    val snapped = range.start + ((target.coerceIn(range) - range.start) / step).roundToInt() * step
                    change(snapped.coerceIn(range))
                    true
                }
            })
    }
}

@Composable
internal fun FontSizeSetting(size: Float, onChange: (Float) -> Unit) {
    GroupedCard {
        SettingsSlider(stringResource(R.string.settings_font_size), size, 13f..42f, 1f, "${size.roundToInt()}", onChange)
    }
}


/** The whole labelled row is one accessible switch with a generous tap target. */
@Composable
internal fun SettingsToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = 54.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = FlibustaType.body, color = Flibusta.colors.label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        net.flibustareader.core.designsystem.components.IosSwitch(checked, onChange, Modifier.clearAndSetSemantics { })
    }
}
