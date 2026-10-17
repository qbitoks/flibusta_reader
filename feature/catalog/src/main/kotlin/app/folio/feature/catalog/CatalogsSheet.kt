package app.folio.feature.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.catalog.Catalog
import app.folio.core.data.catalog.CatalogDirectory
import app.folio.core.data.catalog.CatalogPreset
import app.folio.core.data.catalog.CatalogPresets
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.CircleButtonStyle
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import app.folio.core.designsystem.components.SegmentedControl
import app.folio.core.designsystem.components.SheetHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface CatalogsPage {
    data object List : CatalogsPage
    /** [catalog] is null for a catalog added by address. */
    data class Edit(val catalog: Catalog?) : CatalogsPage
    /** [list] is a file's catalogs; null asks for a link first. */
    data class Import(val list: kotlin.collections.List<CatalogPreset>?) : CatalogsPage
}

/**
 * «Каталоги»: favorites first, then the rest (the built-in iKnigi and the user's own). Tap opens, ★ keeps it at
 * the top, touch and hold edits; the last card adds by address, imports a list (link or file) and exports one.
 */
@Composable
internal fun CatalogsSheet(vm: CatalogViewModel, onDismiss: () -> Unit, edit: Catalog? = null) {
    val c = Folio.colors
    var page by remember { mutableStateOf<CatalogsPage>(edit?.let { CatalogsPage.Edit(it) } ?: CatalogsPage.List) }
    FolioSheet(onDismiss = onDismiss, containerColor = c.groupedBackground) {
        when (val p = page) {
            CatalogsPage.List -> CatalogList(vm, onDismiss, onEdit = { page = CatalogsPage.Edit(it) }, onImport = { page = CatalogsPage.Import(it) })
            is CatalogsPage.Import -> ImportCatalogs(vm, p.list, onBack = { page = CatalogsPage.List }, onDone = { page = CatalogsPage.List })
            is CatalogsPage.Edit -> EditCatalog(
                vm, p.catalog,
                onBack = { if (edit != null) onDismiss() else page = CatalogsPage.List },
                onSaved = { if (p.catalog == null || edit != null) onDismiss() else page = CatalogsPage.List },
            )
        }
    }
}

@Composable
private fun CatalogList(vm: CatalogViewModel, onDismiss: () -> Unit, onEdit: (Catalog?) -> Unit, onImport: (List<CatalogPreset>?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.catalogs.collectAsStateWithLifecycle()
    val issues by vm.downloadIssues.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val exported = stringResource(R.string.catalog_export_done)
    val all = CatalogDirectory.all(saved).filter { it.matches(query) }
    val open = { catalog: Catalog -> vm.select(catalog); onDismiss() }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = null
            try {
                onImport(vm.parseList(readListFile(context, uri)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = (e as? IllegalArgumentException)?.message ?: e.toString()
            }
        }
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                writeListFile(context, uri, vm.exportList())
                exported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.toString()
            }
        }
    }
    @Composable
    fun Group(title: String, catalogs: List<Catalog>, footer: @Composable () -> Unit = {}) {
        if (catalogs.isEmpty()) return
        GroupHeader(title)
        GroupedCard {
            catalogs.forEachIndexed { i, catalog ->
                CatalogRow(
                    catalog,
                    issue = issues[catalog.host] != null,
                    current = catalog.id == vm.catalog?.id,
                    divider = i < catalogs.lastIndex,
                    onOpen = { open(catalog) },
                    onStar = { vm.setFavorite(catalog, !catalog.favorite) },
                    onEdit = { onEdit(catalog) },
                )
            }
        }
        footer()
    }
    SheetHeader(
        stringResource(R.string.catalog_catalogs),
        leading = { CircleButton(Icons.Rounded.Close, stringResource(R.string.catalog_close), onDismiss, style = CircleButtonStyle.Light) },
    )
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(bottom = 24.dp)) {
        FindField(query) { query = it }
        val favorites = all.filter { it.favorite }
        Group(stringResource(R.string.catalog_favorites), favorites) {
            CatalogFootnote(stringResource(R.string.catalog_catalogs_text))
        }
        Group(stringResource(R.string.catalog_group_mine), all.filterNot { it.favorite })
        GroupHeader(stringResource(R.string.catalog_group_add))
        GroupedCard {
            GroupedRow(stringResource(R.string.catalog_add_address), icon = Icons.Rounded.Add, chevron = true, onClick = { onEdit(null) })
            GroupedRow(stringResource(R.string.catalog_import_by_link), icon = Icons.Rounded.Link, chevron = true, onClick = { onImport(null) })
            GroupedRow(stringResource(R.string.catalog_import_file), icon = Icons.Rounded.FileOpen, onClick = { pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
            GroupedRow(stringResource(R.string.catalog_export), icon = Icons.Rounded.IosShare, showDivider = false, onClick = { createFile.launch("folio-catalogs.json") })
        }
        message?.let { CatalogFootnote(it, destructive = it != exported) }
        CatalogFootnote(stringResource(R.string.catalog_lists_text))
        if (favorites.isEmpty() && query.isBlank()) CatalogFootnote(stringResource(R.string.catalog_catalogs_text))
    }
}

private fun Catalog.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return title.contains(q, ignoreCase = true) || host.contains(q, ignoreCase = true) ||
        CatalogPresets.preset(id)?.description?.contains(q, ignoreCase = true) == true
}

@Composable
private fun FindField(value: String, onChange: (String) -> Unit) {
    val c = Folio.colors
    Row(
        Modifier.padding(horizontal = 16.dp).padding(top = 4.dp).fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(12.dp)).background(c.fill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = FolioType.body.copy(color = c.label),
            cursorBrush = SolidColor(c.label),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(stringResource(R.string.catalog_find), style = FolioType.body, color = c.secondaryLabel)
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel, stringResource(R.string.catalog_search_clear), tint = c.tertiaryLabel,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
            )
        }
    }
}

/** Title, what it is (a preset's description or the host) and a warning; ✓ marks the open one. */
@Composable
private fun CatalogRow(
    catalog: Catalog,
    issue: Boolean,
    current: Boolean,
    divider: Boolean,
    onOpen: () -> Unit,
    onStar: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = Folio.colors
    val haptic = LocalHapticFeedback.current
    val preset = CatalogPresets.preset(catalog.id)
    val note = if (issue) stringResource(R.string.catalog_issue_short) else preset?.note
    Column {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() })
                .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(catalog.title, style = FolioType.body, color = c.label)
                Text(
                    if (preset != null && !CatalogDirectory.isEdited(catalog)) preset.description else catalog.host,
                    style = FolioType.footnote, color = c.secondaryLabel,
                )
                if (note != null) Text(note, style = FolioType.footnote, color = c.tertiaryLabel)
            }
            if (current) Icon(Icons.Rounded.Check, null, tint = c.label, modifier = Modifier.padding(start = 10.dp).size(20.dp))
            Icon(
                if (catalog.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                stringResource(if (catalog.favorite) R.string.catalog_favorite_remove else R.string.catalog_favorite_add),
                tint = if (catalog.favorite) c.accent else c.tertiaryLabel,
                modifier = Modifier.padding(start = 4.dp).clip(CircleShape).clickable(onClick = onStar).padding(8.dp).size(22.dp),
            )
        }
        if (divider) HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp, end = 16.dp))
    }
}

/**
 * Name and feed address, the format «Скачать» takes, an optional sign-in, a download mirror under
 * «Дополнительно»; × goes back, ✓ saves. A saved password is never shown: an empty field keeps it.
 */
@Composable
private fun EditCatalog(vm: CatalogViewModel, catalog: Catalog?, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = Folio.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(catalog?.title.orEmpty()) }
    var address by remember { mutableStateOf(catalog?.address.orEmpty()) }
    if (catalog == null) {
        // An address copied on OPDS Home or a website fills the empty field when the user comes back.
        LifecycleResumeEffect(Unit) {
            scope.launch {
                val copied = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                if (address.isEmpty() && (copied.startsWith("https://") || copied.startsWith("http://"))) address = copied
            }
            onPauseOrDispose { }
        }
    }
    var downloads by remember { mutableStateOf(catalog?.downloads.orEmpty()) }
    var format by remember { mutableStateOf(catalog?.format ?: Catalog.DEFAULT_FORMAT) }
    val logins by vm.logins.collectAsStateWithLifecycle()
    val savedLogin = catalog?.let { logins[it.id] }
    var login by remember { mutableStateOf(savedLogin.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    SheetHeader(
        stringResource(if (catalog == null) R.string.catalog_add_address else R.string.catalog_edit_title),
        leading = {
            CircleButton(
                if (catalog == null) Icons.AutoMirrored.Rounded.ArrowBackIos else Icons.Rounded.Close,
                stringResource(if (catalog == null) R.string.catalog_back else R.string.catalog_cancel),
                onBack, style = CircleButtonStyle.Light,
            )
        },
        trailing = {
            CircleButton(
                Icons.Rounded.Check, stringResource(R.string.catalog_save),
                {
                    val base = catalog ?: Catalog(vm.newCatalogId(), "", "")
                    val edited = base.copy(title = title, address = address.trim().replaceFirst(Regex("^http://"), "https://"), downloads = downloads, format = format)
                    error = vm.saveCatalog(edited, login, password.ifEmpty { null })
                    if (error == null) onSaved()
                },
                style = CircleButtonStyle.Confirm,
            )
        },
    )
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(bottom = 24.dp)) {
        GroupedCard(Modifier.padding(top = 8.dp)) {
            CatalogField(stringResource(R.string.catalog_field_title), title, KeyboardType.Text) { title = it; error = null }
            HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
            CatalogField(stringResource(R.string.catalog_field_address), address, KeyboardType.Uri) { address = it; error = null }
        }
        error?.let { CatalogFootnote(it, destructive = true) }
        CatalogFootnote(stringResource(R.string.catalog_sources_text))
        GroupedCard(Modifier.padding(top = 16.dp)) {
            GroupedRow(stringResource(R.string.catalog_field_format), showDivider = false, trailing = {
                SegmentedControl(Catalog.FORMATS, Catalog.FORMATS.indexOf(format), { format = Catalog.FORMATS[it] }, Modifier.width(150.dp))
            })
        }
        CatalogFootnote(stringResource(R.string.catalog_format_text))
        GroupHeader(stringResource(R.string.catalog_sign_in_group))
        GroupedCard {
            CatalogField(stringResource(R.string.catalog_field_login), login, KeyboardType.Email, placeholder = stringResource(R.string.catalog_field_optional)) { login = it; error = null }
            HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
            CatalogField(
                stringResource(R.string.catalog_field_password), password, KeyboardType.Password,
                placeholder = stringResource(if (savedLogin != null && login.trim() == savedLogin) R.string.catalog_password_saved else R.string.catalog_field_optional),
                secret = true,
            ) { password = it; error = null }
        }
        CatalogFootnote(stringResource(R.string.catalog_sign_in_text))
        GroupHeader(stringResource(R.string.catalog_advanced))
        GroupedCard {
            CatalogField(stringResource(R.string.catalog_source_downloads), downloads, KeyboardType.Uri, placeholder = stringResource(R.string.catalog_field_optional)) { downloads = it; error = null }
        }
        CatalogFootnote(stringResource(R.string.catalog_mirror_text))
        when {
            catalog == null -> Unit
            !CatalogDirectory.isPreset(catalog) -> GroupedCard(Modifier.padding(top = 16.dp)) {
                GroupedRow(stringResource(R.string.catalog_remove_catalog), titleColor = c.destructive, showDivider = false, onClick = { vm.removeCatalog(catalog); onBack() })
            }
            CatalogDirectory.isEdited(catalog) -> GroupedCard(Modifier.padding(top = 16.dp)) {
                GroupedRow(stringResource(R.string.catalog_reset), titleColor = c.destructive, showDivider = false, onClick = { vm.resetCatalog(catalog); onBack() })
            }
        }
    }
}

@Composable
internal fun CatalogField(label: String, value: String, type: KeyboardType, placeholder: String? = null, secret: Boolean = false, onChange: (String) -> Unit) {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FolioType.body, color = c.label, modifier = Modifier.width(118.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = FolioType.body.copy(color = c.secondaryLabel),
            cursorBrush = SolidColor(c.label),
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.weight(1f).padding(vertical = 13.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = FolioType.body, color = c.tertiaryLabel)
                inner()
            },
        )
    }
}

@Composable
internal fun CatalogFootnote(text: String, destructive: Boolean = false) {
    val c = Folio.colors
    Text(
        text, style = FolioType.footnote, color = if (destructive) c.destructive else c.secondaryLabel,
        modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 10.dp),
    )
}
