package app.folio.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.catalog.Catalog
import app.folio.core.data.catalog.CatalogPresets
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.CircleButtonStyle
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import app.folio.core.designsystem.components.SheetHeader

private sealed interface CatalogsPage {
    data object List : CatalogsPage
    data object Add : CatalogsPage
    /** [catalog] is null for a catalog added by address. */
    data class Edit(val catalog: Catalog?) : CatalogsPage
}

/**
 * «Каталоги»: the user's catalogs (tap to switch, «Изменить» to edit or remove), and «Добавить»
 * with the known catalogs, an address field and OPDS Home, a directory of catalogs.
 */
@Composable
internal fun CatalogsSheet(vm: CatalogViewModel, onDismiss: () -> Unit) {
    val c = Folio.colors
    var page by remember { mutableStateOf<CatalogsPage>(CatalogsPage.List) }
    FolioSheet(onDismiss = onDismiss, containerColor = c.groupedBackground) {
        when (val p = page) {
            CatalogsPage.List -> CatalogList(vm, onDismiss, onAdd = { page = CatalogsPage.Add }, onEdit = { page = CatalogsPage.Edit(it) })
            CatalogsPage.Add -> AddCatalog(vm, onBack = { page = CatalogsPage.List }, onAdded = onDismiss, onAddress = { page = CatalogsPage.Edit(null) })
            is CatalogsPage.Edit -> EditCatalog(vm, p.catalog, onBack = { page = CatalogsPage.List }, onSaved = { page = CatalogsPage.List })
        }
    }
}

@Composable
private fun CatalogList(vm: CatalogViewModel, onDismiss: () -> Unit, onAdd: () -> Unit, onEdit: (Catalog) -> Unit) {
    val c = Folio.colors
    val catalogs by vm.catalogs.collectAsStateWithLifecycle()
    val issues by vm.downloadIssues.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    SheetHeader(
        stringResource(R.string.catalog_catalogs),
        leading = { CircleButton(Icons.Rounded.Close, stringResource(R.string.catalog_close), onDismiss, style = CircleButtonStyle.Light) },
        trailing = {
            if (catalogs.isNotEmpty()) {
                Text(
                    stringResource(if (editing) R.string.catalog_done else R.string.catalog_edit),
                    style = FolioType.body, color = c.label,
                    modifier = Modifier.padding(end = 8.dp).clickableText { editing = !editing },
                )
            }
        },
    )
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
        if (catalogs.isNotEmpty()) {
            GroupedCard(Modifier.padding(top = 8.dp)) {
                catalogs.forEachIndexed { i, catalog ->
                    GroupedRow(
                        catalog.title,
                        value = (if (issues[catalog.host] != null) stringResource(R.string.catalog_issue_short) else catalog.host).takeIf { !editing },
                        valueStyle = FolioType.footnote,
                        showDivider = i < catalogs.lastIndex,
                        editing = editing,
                        onRemove = { vm.removeCatalog(catalog) },
                        trailing = {
                            if (catalog.id == vm.catalog?.id) Icon(Icons.Rounded.Check, null, tint = c.label, modifier = Modifier.padding(start = 10.dp).size(20.dp))
                            Icon(
                                Icons.Rounded.Info, stringResource(R.string.catalog_edit), tint = c.tertiaryLabel,
                                modifier = Modifier.padding(start = 10.dp).size(22.dp).clickableText { onEdit(catalog) },
                            )
                        },
                        onClick = { vm.select(catalog); onDismiss() },
                    )
                }
            }
        }
        GroupedCard(Modifier.padding(top = 16.dp)) {
            GroupedRow(stringResource(R.string.catalog_add), icon = Icons.Rounded.Add, showDivider = false, chevron = true, onClick = onAdd)
        }
        Footnote(stringResource(R.string.catalog_catalogs_text))
    }
}

@Composable
private fun AddCatalog(vm: CatalogViewModel, onBack: () -> Unit, onAdded: () -> Unit, onAddress: () -> Unit) {
    val catalogs by vm.catalogs.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var error by remember { mutableStateOf<String?>(null) }
    val known = catalogs.map { it.address }.toSet()
    val presets = CatalogPresets.all.filter { it.catalog.address !in known }
    SheetHeader(
        stringResource(R.string.catalog_add),
        leading = { CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.catalog_back), onBack, style = CircleButtonStyle.Light) },
    )
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
        GroupedCard(Modifier.padding(top = 8.dp)) {
            GroupedRow(stringResource(R.string.catalog_add_address), icon = Icons.Rounded.ContentPaste, chevron = true, onClick = onAddress)
            GroupedRow(
                stringResource(R.string.catalog_directory), icon = Icons.AutoMirrored.Rounded.OpenInNew, showDivider = false,
                onClick = { runCatching { uri.openUri(CatalogPresets.DIRECTORY) } },
            )
        }
        Footnote(stringResource(R.string.catalog_directory_text))
        if (presets.isNotEmpty()) {
            GroupHeader(stringResource(R.string.catalog_presets))
            GroupedCard {
                presets.forEachIndexed { i, preset ->
                    PresetRow(preset.catalog.title, preset.description, preset.note, divider = i < presets.lastIndex) {
                        error = vm.saveCatalog(preset.catalog.copy(id = if (catalogs.any { it.id == preset.catalog.id }) vm.newCatalogId() else preset.catalog.id))
                        if (error == null) onAdded()
                    }
                }
            }
            error?.let { Footnote(it, destructive = true) }
            Footnote(stringResource(R.string.catalog_presets_text))
        }
    }
}

@Composable
private fun PresetRow(title: String, description: String, note: String?, divider: Boolean, onAdd: () -> Unit) {
    val c = Folio.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickableText(onAdd).padding(start = 18.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = FolioType.body, color = c.label)
                Text(description, style = FolioType.footnote, color = c.secondaryLabel)
                if (note != null) Text(note, style = FolioType.footnote, color = c.tertiaryLabel)
            }
            Icon(Icons.Rounded.Add, stringResource(R.string.catalog_add), tint = c.label, modifier = Modifier.padding(start = 10.dp).size(22.dp))
        }
        if (divider) HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp, end = 16.dp))
    }
}

/** Name, feed address and an optional download mirror; × goes back, ✓ saves. */
@Composable
private fun EditCatalog(vm: CatalogViewModel, catalog: Catalog?, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = Folio.colors
    val clipboard = LocalClipboard.current
    var title by remember { mutableStateOf(catalog?.title.orEmpty()) }
    var address by remember { mutableStateOf(catalog?.address.orEmpty()) }
    if (catalog == null) {
        // An address copied from OPDS Home or a website fills the field.
        LaunchedEffect(Unit) {
            val copied = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (address.isEmpty() && (copied.startsWith("https://") || copied.startsWith("http://"))) address = copied
        }
    }
    var downloads by remember { mutableStateOf(catalog?.downloads.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    SheetHeader(
        stringResource(if (catalog == null) R.string.catalog_add_address else R.string.catalog_edit_title),
        leading = { CircleButton(Icons.Rounded.Close, stringResource(R.string.catalog_cancel), onBack, style = CircleButtonStyle.Light) },
        trailing = {
            CircleButton(
                Icons.Rounded.Check, stringResource(R.string.catalog_save),
                {
                    val edited = (catalog ?: Catalog(vm.newCatalogId(), "", "")).copy(title = title, address = address.trim().replaceFirst(Regex("^http://"), "https://"), downloads = downloads)
                    error = vm.saveCatalog(edited)
                    if (error == null) onSaved()
                },
                style = CircleButtonStyle.Confirm,
            )
        },
    )
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(bottom = 24.dp)) {
        GroupedCard(Modifier.padding(top = 8.dp)) {
            Field(stringResource(R.string.catalog_field_title), title, KeyboardType.Text) { title = it; error = null }
            HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
            Field(stringResource(R.string.catalog_field_address), address, KeyboardType.Uri) { address = it; error = null }
            HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
            Field(stringResource(R.string.catalog_source_downloads), downloads, KeyboardType.Uri, placeholder = stringResource(R.string.catalog_field_optional)) { downloads = it; error = null }
        }
        error?.let { Footnote(it, destructive = true) }
        Footnote(stringResource(R.string.catalog_sources_text))
        if (catalog != null) {
            GroupedCard(Modifier.padding(top = 16.dp)) {
                GroupedRow(stringResource(R.string.catalog_remove_catalog), titleColor = c.destructive, showDivider = false, onClick = { vm.removeCatalog(catalog); onBack() })
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, placeholder: String? = null, onChange: (String) -> Unit) {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FolioType.body, color = c.label, modifier = Modifier.width(118.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = FolioType.body.copy(color = c.secondaryLabel),
            cursorBrush = SolidColor(c.label),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.weight(1f).padding(vertical = 13.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = FolioType.body, color = c.tertiaryLabel)
                inner()
            },
        )
    }
}

@Composable
private fun Footnote(text: String, destructive: Boolean = false) {
    val c = Folio.colors
    Text(
        text, style = FolioType.footnote, color = if (destructive) c.destructive else c.secondaryLabel,
        modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 10.dp),
    )
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier = clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
