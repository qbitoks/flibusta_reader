package app.folio.feature.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.CircleButtonStyle
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.SheetHeader

/** «Источники»: catalog and download hosts, × to cancel and ✓ to save, as in «Настройка темы». */
@Composable
internal fun SourcesSheet(vm: CatalogViewModel, onDismiss: () -> Unit) {
    val c = Folio.colors
    var catalog by remember { mutableStateOf(vm.catalogBase) }
    var downloads by remember { mutableStateOf(vm.downloadBase) }
    var error by remember { mutableStateOf<String?>(null) }
    FolioSheet(onDismiss = onDismiss, containerColor = c.groupedBackground) {
        SheetHeader(
            stringResource(R.string.catalog_sources),
            leading = { CircleButton(Icons.Rounded.Close, stringResource(R.string.catalog_cancel), onDismiss, style = CircleButtonStyle.Light) },
            trailing = {
                CircleButton(
                    Icons.Rounded.Check, stringResource(R.string.catalog_sources),
                    { error = vm.saveSources(catalog, downloads); if (error == null) onDismiss() },
                    style = CircleButtonStyle.Confirm,
                )
            },
        )
        Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 24.dp)) {
            GroupedCard(Modifier.padding(top = 8.dp)) {
                SourceField(stringResource(R.string.catalog_source_catalog), catalog) { catalog = it; error = null }
                HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
                SourceField(stringResource(R.string.catalog_source_downloads), downloads) { downloads = it; error = null }
            }
            error?.let { Text(it, style = FolioType.footnote, color = c.destructive, modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 10.dp)) }
            Text(
                stringResource(R.string.catalog_sources_text), style = FolioType.footnote, color = c.secondaryLabel,
                modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 10.dp),
            )
        }
    }
}

@Composable
private fun SourceField(label: String, value: String, onChange: (String) -> Unit) {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FolioType.body, color = c.label, modifier = Modifier.width(118.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = FolioType.body.copy(color = c.secondaryLabel),
            cursorBrush = SolidColor(c.label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f).padding(vertical = 13.dp),
        )
    }
}
