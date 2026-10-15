package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.SpacingPresets
import app.folio.core.data.settings.TextSet
import app.folio.core.designsystem.ReaderSpacingIcons
import app.folio.core.designsystem.components.GroupedCard

/** Group like the header/footer lines: two cyclic tiles, each showing its current step, above shared details. */
@Composable
internal fun SpacingSettingsCard(text: TextSet, own: Boolean, update: (TextSet) -> Unit, details: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
    Column {
    SettingsGroupHeader(stringResource(R.string.settings_spacing_group), own = own)
    GroupedCard {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpacingTile(lineIcon, lineTitle, lineState, Modifier.weight(1f)) {
                update(text.copy(lineSpacing = SpacingPresets.next(text.lineSpacing, SpacingPresets.lines)))
            }
            SpacingTile(marginIcon, marginTitle, marginState, Modifier.weight(1f)) {
                update(text.copy(marginsPct = SpacingPresets.next(text.marginsPct.toFloat(), SpacingPresets.margins).toInt()))
            }
        }
        SettingsDivider()
        SettingsDetailsRow(expanded) { expanded = !expanded }
        SettingsDetails(expanded) { details() }
    }
    }
}

/** A button, not a choice: equal tiles, no raised selection; the caption shows the current step; the title is only spoken. */
@Composable
private fun SpacingTile(icon: ImageVector, title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Folio.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.fill)
        .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = title; stateDescription = value }
        .heightIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.label, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = FolioType.footnote, color = c.label, maxLines = 1, textAlign = TextAlign.Center)
    }
}
