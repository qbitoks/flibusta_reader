package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.data.settings.CustomColors
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalettes
import app.folio.core.designsystem.components.GroupedCard

/**
 * Ready tone/contrast pairs as a choice row, like fonts. Slider values outside the presets show a
 * selected «Своя» tile; it stays in the row while the tab lives, so a preset tap can be undone.
 */
@Composable
internal fun ToneSettingsCard(colors: CustomColors, dark: Boolean, own: Boolean, paint: (CustomColors) -> Unit,
    details: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var lastWarmth by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastContrast by rememberSaveable { mutableStateOf(0) }
    val current = TonePreset.of(colors.warmth, colors.contrast)
    if (current == null) { lastWarmth = colors.warmth; lastContrast = colors.contrast }
    val names = mapOf(TonePreset.Normal to R.string.tone_normal, TonePreset.Warm to R.string.tone_warm,
        TonePreset.Soft to R.string.tone_soft, TonePreset.Crisp to R.string.tone_crisp,
        TonePreset.Night to R.string.tone_night, TonePreset.Cool to R.string.tone_cool)
    val scroll = rememberScrollState()
    val custom = current == null
    // Slider edits make «Свой» appear at the far end; bring it into view.
    LaunchedEffect(custom) { if (custom) scroll.animateScrollTo(scroll.maxValue) }
    Column {
        SettingsGroupHeader(stringResource(R.string.settings_tone_contrast), own = own)
        GroupedCard {
            Row(Modifier.horizontalScroll(scroll).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonePreset.entries.forEach { preset ->
                    ToneTile(colors.copy(warmth = preset.warmth, contrast = preset.contrast), dark,
                        stringResource(names.getValue(preset)), null, current == preset) {
                        paint(colors.copy(warmth = preset.warmth, contrast = preset.contrast))
                    }
                }
                lastWarmth?.let { w ->
                    val custom = colors.copy(warmth = w, contrast = lastContrast)
                    ToneTile(custom, dark, stringResource(R.string.tone_custom),
                        "${signed(w)} / ${signed(lastContrast)}", current == null) { paint(custom) }
                }
            }
            SettingsDivider()
            SettingsDetailsRow(expanded) { expanded = !expanded }
            SettingsDetails(expanded) { details() }
        }
    }
}

/** A swatch of the page with this adjustment over the current theme, as the shared choice tile. */
@Composable
private fun ToneTile(colors: CustomColors, dark: Boolean, name: String, values: String?, selected: Boolean, onClick: () -> Unit) {
    val page = ReaderPalettes.custom(colors, dark)
    ChoiceTile(name, selected, page.background, Modifier.width(84.dp), values, onClick) {
        Text("Аа", color = page.text, maxLines = 1, style = FolioType.body.copy(fontSize = 20.sp))
    }
}
