package app.folio.feature.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.designsystem.*
import app.folio.core.designsystem.components.*

/** Window brightness controls; only the reader has a window to brighten. */
internal class SheetBrightness(val value: Float, val onChange: (Float) -> Unit)
internal class SheetRotation(val locked: Boolean, val onChange: (Boolean) -> Unit)

/** «Темы и настройки» in a book: the book's own settings or the shared ones, chosen under the title. */
@Composable
internal fun ThemesSheet(vm: ReaderViewModel, brightness: SheetBrightness, rotation: SheetRotation, onDismiss: () -> Unit) {
    val shared by vm.sharedSettings.collectAsStateWithLifecycle()
    val effective by vm.settings.collectAsStateWithLifecycle()
    val own by vm.ownSettings.collectAsStateWithLifecycle()
    // A book with its own settings opens on them; otherwise edits go to all books.
    var bookScope by rememberSaveable { mutableStateOf(!vm.ownSettings.value.isEmpty) }
    val editor = rememberSettingsEditor(if (bookScope) effective else shared, own, bookScope,
        save = { change -> vm.saveSettings(bookScope, change).join() })
    ReadingSettingsSheet(
        editor = editor,
        title = stringResource(R.string.themes_title),
        subtitle = {
            SettingsScopeButton(bookScope, own, onScope = { bookScope = it },
                onShare = { vm.shareOwnSettings(); bookScope = false },
                onReset = { vm.resetOwnSettings(); bookScope = false })
        },
        preview = samplePreviewSource(),
        brightness = brightness,
        rotation = rotation,
        onDismiss = onDismiss,
    )
}

/** One sheet owns navigation; changing sections never opens a second modal. */
@Composable
internal fun ReadingSettingsSheet(
    editor: SettingsEditor,
    title: String,
    subtitle: @Composable () -> Unit,
    preview: PreviewSource,
    brightness: SheetBrightness?,
    onDismiss: () -> Unit,
    rotation: SheetRotation? = null,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val tabStates = rememberSaveableStateHolder()
    val scrollStates = List(2) { rememberScrollState() }
    val c = Folio.colors
    val settings = editor.settings
    val palette = animatedPalette(readerPalette(settings))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false,
    )) {
        if (brightness != null) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(window, brightness.value) {
                window?.let { it.attributes = it.attributes.apply { screenBrightness = if (brightness.value < 0) -1f else brightness.value.coerceIn(0.02f, 1f) } }
                onDispose { }
            }
        }
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp), color = c.groupedBackground) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = FolioType.headline, color = c.label, maxLines = 1, textAlign = TextAlign.Center)
                    subtitle()
                }
                CircleButton(Icons.Rounded.Close, stringResource(R.string.reader_close), onDismiss, Modifier.align(Alignment.CenterEnd))
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth > maxHeight
                val previewHeight = (maxHeight * 0.32f).coerceIn(100.dp, 240.dp)
                @Composable fun Editor(modifier: Modifier) {
                    Column(modifier) {
                        SettingsTabs(section) { section = it }
                        Column(Modifier.weight(1f).verticalScroll(scrollStates[section], overscrollEffect = null).padding(bottom = 24.dp)) {
                            tabStates.SaveableStateProvider(section) {
                                when (section) {
                                    0 -> ReaderAppearanceSettings(editor, brightness, rotation)
                                    1 -> ReaderAdvancedSettings(editor, preview)
                                }
                            }
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        PageSettingsPreview(preview, settings, palette, Modifier.weight(0.44f).fillMaxHeight().padding(bottom = 16.dp))
                        Editor(Modifier.weight(0.56f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        PageSettingsPreview(preview, settings, palette, Modifier.fillMaxWidth().height(previewHeight))
                        Editor(Modifier.weight(1f))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SettingsTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = Folio.colors
    val labels = listOf(R.string.settings_tab_appearance, R.string.settings_tab_page).map { stringResource(it) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = FolioType.subheadline.copy(fontWeight = FontWeight.SemiBold)
    val widths = labels.map { with(density) { measurer.measure(it, style).size.width.toDp() } + 12.dp }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        .clip(CircleShape).background(c.fill).padding(3.dp)) {
        val fits = widths.sumOf { it.value.toDouble() } <= maxWidth.value
        LaunchedEffect(selected, fits) {
            if (!fits) scroll.animateScrollTo(with(density) { widths.take(selected).sumOf { it.toPx().toDouble() }.toInt() })
        }
        Row((if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(scroll)).selectableGroup()) {
            labels.forEachIndexed { i, label ->
                Box((if (fits) Modifier.weight(widths[i].value) else Modifier.width(widths[i]))
                    .clip(CircleShape)
                    .background(if (i == selected) (if (c.isDark) c.selectedRow else c.card) else Color.Transparent)
                    .selectable(i == selected, role = Role.Tab) { onSelect(i) }
                    .heightIn(min = 36.dp).padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = FolioType.subheadline,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = c.label, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** Theme switches cross-fade the preview instead of snapping. */
@Composable
private fun animatedPalette(target: ReaderPalette): ReaderPalette {
    @Composable fun Color.animated() = animateColorAsState(this, tween(220), label = "palette").value
    return target.copy(background = target.background.animated(), text = target.text.animated(),
        secondary = target.secondary.animated(), chrome = target.chrome.animated())
}
