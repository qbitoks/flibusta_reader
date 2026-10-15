package app.folio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.SpacingPresets
import app.folio.core.data.settings.TextSet
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderSpacingIcons
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.SegmentedControl

/** The exact stacked control used for page turning, with two independent cyclic actions. */
@Composable
internal fun SpacingSettingsCard(text: TextSet, update: (TextSet) -> Unit, details: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val c = Folio.colors
    val lineIndex = SpacingPresets.index(text.lineSpacing, SpacingPresets.lines)
    val marginIndex = SpacingPresets.index(text.marginsPct.toFloat(), SpacingPresets.margins)
    val lineNames = listOf(R.string.spacing_dense, R.string.spacing_normal, R.string.spacing_loose)
    val marginNames = listOf(R.string.spacing_narrow, R.string.spacing_medium, R.string.spacing_wide)
    val lineState = stringResource(if (lineIndex < 0) R.string.spacing_custom else lineNames[lineIndex])
    val marginState = stringResource(if (marginIndex < 0) R.string.spacing_custom else marginNames[marginIndex])
    val lineTitle = stringResource(R.string.spacing_interval)
    val marginTitle = stringResource(R.string.settings_margins)
    val lineIcon = listOf(ReaderSpacingIcons.Dense, ReaderSpacingIcons.Normal, ReaderSpacingIcons.Loose)
        .getOrNull(lineIndex) ?: ReaderSpacingIcons.CustomLines
    val marginIcon = listOf(ReaderSpacingIcons.Narrow, ReaderSpacingIcons.Medium, ReaderSpacingIcons.Wide)
        .getOrNull(marginIndex) ?: ReaderSpacingIcons.CustomMargins
    Spacer(Modifier.height(12.dp))
    GroupedCard {
        Text(stringResource(R.string.settings_spacing_group), style = FolioType.body, color = c.label,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp))
        SegmentedControl(
            options = listOf(lineTitle, marginTitle), selected = -1,
            onSelect = { index ->
                if (index == 0) update(text.copy(lineSpacing = SpacingPresets.next(text.lineSpacing, SpacingPresets.lines)))
                else update(text.copy(marginsPct = SpacingPresets.next(text.marginsPct.toFloat(), SpacingPresets.margins).toInt()))
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            icons = listOf(lineIcon, marginIcon), descriptions = listOf("$lineTitle: $lineState", "$marginTitle: $marginState"),
            role = Role.Button, stacked = true,
            activeIndices = buildSet { if (lineIndex >= 0) add(0); if (marginIndex >= 0) add(1) },
        )
        SettingsDivider()
        val description = stringResource(if (expanded) R.string.line_details_expanded else R.string.line_details_collapsed)
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = description }.heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.line_details), style = FolioType.body, color = c.label, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null,
                tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
        }
        AnimatedVisibility(expanded) { Column { SettingsDivider(); details() } }
    }
}
