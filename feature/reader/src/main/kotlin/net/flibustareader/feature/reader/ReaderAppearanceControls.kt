package net.flibustareader.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.settings.Appearance
import net.flibustareader.core.designsystem.*
import net.flibustareader.core.designsystem.components.IosSlider
import kotlin.math.roundToInt

/** Book-only appearance override; absent follows the app appearance. */
@Composable
internal fun BookAppearanceChoices(current: Appearance?, onChange: (Appearance?) -> Unit) {
    SettingsSegments(current, listOf(
        SegmentOption(null, stringResource(R.string.appearance_app_short), stringResource(R.string.appearance_app), Icons.Rounded.BrightnessAuto),
        SegmentOption(Appearance.Light, stringResource(R.string.appearance_light), icon = Icons.Rounded.LightMode),
        SegmentOption(Appearance.Dark, stringResource(R.string.appearance_dark), icon = Icons.Rounded.DarkMode),
    ), onChange)
}

@Composable
internal fun BrightnessSlider(brightness: Float, manual: Float, onChange: (Float) -> Unit) {
    val enabled = brightness >= 0
    val c = Flibusta.colors
    val title = stringResource(R.string.settings_brightness)
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FlibustaType.body, color = c.label, modifier = Modifier.weight(1f))
            Text(if (enabled) "${(brightness * 100).roundToInt()} %" else "—", style = FlibustaType.subheadline, color = c.secondaryLabel)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.BrightnessLow, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            IosSlider(value = if (enabled) brightness else manual, onValueChange = onChange,
                valueRange = 0.02f..1f, enabled = enabled,
                contentDescription = title,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    if (enabled) {
                        progressBarRangeInfo = ProgressBarRangeInfo(brightness, 0.02f..1f)
                        setProgress { onChange(it.coerceIn(0.02f, 1f)); true }
                    }
                })
            Icon(Icons.Rounded.BrightnessHigh, null, tint = c.secondaryLabel, modifier = Modifier.size(24.dp))
        }
    }
}
