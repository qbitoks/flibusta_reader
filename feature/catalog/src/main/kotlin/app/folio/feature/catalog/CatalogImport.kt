package app.folio.feature.catalog

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.data.catalog.CatalogPreset
import app.folio.core.data.catalog.NetworkErrors
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.CircleButtonStyle
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import app.folio.core.designsystem.components.SheetHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A list file is small: anything bigger is not a catalog list. */
private const val MAX_LIST_BYTES = 512 * 1024

/** Reads a list file the user picked; throws [IllegalArgumentException] with a user message. */
internal suspend fun readListFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_LIST_BYTES + 1) }
        ?: throw IllegalArgumentException("Файл не открывается")
    require(bytes.size <= MAX_LIST_BYTES) { "Файл слишком большой для списка каталогов" }
    String(bytes, Charsets.UTF_8)
}

/** Writes the export to a file the user created. */
internal suspend fun writeListFile(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("No output")
}

/**
 * «Импорт»: a list from a link ([preloaded] null) or from a file; the user ticks what to add. A catalog already
 * saved (same id or address) is marked «Обновится»: importing it again updates its addresses.
 */
@Composable
internal fun ImportCatalogs(vm: CatalogViewModel, preloaded: List<CatalogPreset>?, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    val saved by vm.catalogs.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf("") }
    var list by remember { mutableStateOf(preloaded) }
    var picked by remember { mutableStateOf(preloaded.orEmpty().map { it.catalog.id }.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    /** A network failure as a [app.folio.core.data.catalog.NetworkErrors] code; [error] is a ready message. */
    var errorCode by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    fun load() {
        if (loading) return
        loading = true
        error = null
        errorCode = null
        scope.launch {
            try {
                val loaded = vm.fetchList(address.trim().replaceFirst(Regex("^http://"), "https://"))
                list = loaded
                picked = loaded.map { it.catalog.id }.toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is IllegalArgumentException) error = e.message else errorCode = NetworkErrors.code(e)
            } finally {
                loading = false
            }
        }
    }
    val shown = list
    SheetHeader(
        stringResource(R.string.catalog_import_title),
        leading = { CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.catalog_back), onBack, style = CircleButtonStyle.Light) },
        trailing = {
            if (shown != null) {
                CircleButton(
                    Icons.Rounded.Check, stringResource(R.string.catalog_import_add),
                    {
                        vm.importCatalogs(shown.map { it.catalog }.filter { it.id in picked })
                        onDone()
                    },
                    style = CircleButtonStyle.Confirm,
                    enabled = picked.isNotEmpty(),
                )
            }
        },
    )
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(bottom = 24.dp)) {
        if (preloaded == null) {
            GroupedCard(Modifier.padding(top = 8.dp)) {
                CatalogField(stringResource(R.string.catalog_import_link), address, KeyboardType.Uri, placeholder = "https://") { address = it; error = null; errorCode = null }
                HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 18.dp))
                GroupedRow(
                    stringResource(if (loading) R.string.catalog_import_loading else R.string.catalog_import_load),
                    titleColor = c.accent, showDivider = false, enabled = !loading && address.isNotBlank(), onClick = ::load,
                )
            }
            (error ?: errorCode?.let { errorText(it) })?.let { CatalogFootnote(it, destructive = true) }
            CatalogFootnote(stringResource(R.string.catalog_import_text))
        }
        if (shown != null) {
            GroupedCard(Modifier.padding(top = 16.dp)) {
                shown.forEachIndexed { i, item ->
                    val on = item.catalog.id in picked
                    val known = saved.any { it.id == item.catalog.id || it.address == item.catalog.address }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { picked = if (on) picked - item.catalog.id else picked + item.catalog.id }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (on) c.accent else c.tertiaryLabel, modifier = Modifier.padding(end = 14.dp).size(22.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.catalog.title, style = FolioType.body, color = c.label)
                            Text(item.description.ifEmpty { item.catalog.host }, style = FolioType.footnote, color = c.secondaryLabel)
                            val note = listOfNotNull(stringResource(R.string.catalog_import_update).takeIf { known }, item.note).joinToString(" · ")
                            if (note.isNotEmpty()) Text(note, style = FolioType.footnote, color = c.tertiaryLabel)
                        }
                    }
                    if (i < shown.lastIndex) HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                }
            }
            CatalogFootnote(stringResource(R.string.catalog_import_rights))
        }
    }
}
