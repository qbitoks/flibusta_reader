package app.folio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.DpOffset
import app.folio.core.data.settings.GuideDim
import app.folio.core.designsystem.FolioTheme
import app.folio.core.designsystem.components.PullDownMenu
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.data.settings.PageTurn
import app.folio.core.data.settings.ReaderSettings
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.components.glass

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderNavigationPanel(
    vm: ReaderViewModel, settings: ReaderSettings, palette: ReaderPalette, controls: ReaderControls,
    fragments: Int, bottomInset: Dp, compact: Boolean, maxHeight: Dp,
) {
    val state = vm.state
    // This draft is local to the visible panel; only release records one navigation jump.
    var draft by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.layoutGeneration, state.progress, controls.chromeVisible) { draft = null }
    val progress = draft ?: state.progress
    val preview = draft?.let(vm::previewProgress)
    val percent = stringResource(R.string.reader_book_percent, (progress * 100).toInt())
    val haptic = LocalHapticFeedback.current
    val c = Folio.colors
    val guideAvailable = settings.pageTurn != PageTurn.Scroll

    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).glass(RectangleShape, tint = palette.background.copy(alpha = 0.94f))
        .readerPanelInput().padding(bottom = bottomInset)) {
        HorizontalDivider(color = palette.text.copy(alpha = 0.12f), thickness = 0.5.dp)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), overscrollEffect = null).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { controls.sheet = ReaderSheet.Contents }
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.AutoMirrored.Rounded.List, stringResource(R.string.reader_contents), tint = c.label, modifier = Modifier.size(24.dp))
                Text(if (draft != null) preview?.chapter ?: stringResource(R.string.reader_untitled)
                    else state.chapterTitle ?: stringResource(R.string.reader_contents),
                    style = FolioType.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (draft != null) {
                    Text(preview?.page?.let { stringResource(R.string.reader_scrub_page, it) } ?: stringResource(R.string.reader_scrub_loading),
                        style = FolioType.footnote, color = c.secondaryLabel)
                } else state.pagesLeftInChapter?.takeIf { state.page != null }?.let { remaining ->
                    Text(if (remaining == 0) stringResource(R.string.reader_chapter_end)
                        else androidx.compose.ui.res.pluralStringResource(R.plurals.footer_left_in_chapter, remaining, remaining),
                        style = FolioType.footnote, color = c.secondaryLabel, maxLines = 2,
                        modifier = Modifier.widthIn(max = 110.dp), textAlign = TextAlign.End)
                }
            }
            val progressDescription = stringResource(R.string.reader_progress)
            ReaderProgressSlider(progress, palette = palette, onValueChange = { draft = it },
                onValueChangeFinished = {
                    val target = draft
                    draft = null
                    if (target != null) vm.goToProgress(target)
                }, onValueChangeCancelled = { draft = null }, enabled = state.page != null && vm.book?.totalChars != 0L,
                contentDescription = progressDescription)
            ReaderHistoryProgress(vm, percent, onPosition = { controls.sheet = ReaderSheet.Position })
        }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = if (compact) 0.dp else 4.dp, bottom = if (compact) 0.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                NavigationAction(Icons.Rounded.EditNote, stringResource(R.string.reader_fragments),
                    badge = fragments, modifier = Modifier.weight(1f)) { controls.sheet = ReaderSheet.Bookmarks }
                NavigationAction(if (state.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    stringResource(if (state.bookmarked) R.string.reader_remove_bookmark else R.string.reader_bookmark),
                    active = state.bookmarked, activeTint = BookmarkRed, modifier = Modifier.weight(1f)) {
                    haptic.performHapticFeedback(if (state.bookmarked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    vm.toggleBookmark()
                }
                NavigationAction(Icons.Rounded.TextFields, stringResource(R.string.reader_themes), modifier = Modifier.weight(1f)) {
                    controls.sheet = ReaderSheet.Themes
                }
                // Tap toggles; long press opens dimming (the full block lives in «Аа»).
                var guideMenu by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    NavigationAction(Icons.Rounded.CalendarViewDay, stringResource(R.string.reader_guide_line),
                        active = settings.guideLine && guideAvailable, enabled = guideAvailable,
                        modifier = Modifier.fillMaxWidth(), onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            guideMenu = true
                        }) {
                        val enable = !settings.guideLine
                        vm.setGuideLine(enable)
                        if (enable) controls.chromeVisible = false
                    }
                    GuideDimMenu(guideMenu, settings, palette, onDismiss = { guideMenu = false }, onDim = { dim ->
                        vm.setGuideDim(dim)
                        if (!settings.guideLine) vm.setGuideLine(true)
                    }, onTurnOff = { vm.setGuideLine(false) })
                }
                NavigationAction(Icons.Rounded.Search, stringResource(R.string.reader_search), modifier = Modifier.weight(1f)) {
                    controls.sheet = ReaderSheet.Search
                }
            }
    }
}

@Composable
private fun NavigationAction(
    icon: ImageVector, description: String, modifier: Modifier,
    active: Boolean = false, enabled: Boolean = true, activeTint: Color? = null, badge: Int = 0,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val c = Folio.colors
    val label = if (badge > 0) "$description, $badge" else description
    Box(modifier.heightIn(min = 52.dp).clip(CircleShape)
        .background(if (active) c.label.copy(alpha = 0.08f) else Color.Transparent)
        .combinedClickable(enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
        .semantics {
            if (activeTint != null || icon == Icons.Rounded.CalendarViewDay) selected = active
        }, contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (!enabled) c.secondaryLabel.copy(alpha = 0.45f) else if (active) activeTint ?: c.label else c.label,
            modifier = Modifier.size(26.dp))
    }
}

/** Quick dimming choice from the panel; picking a level also turns the line on. */
@Composable
private fun GuideDimMenu(
    expanded: Boolean, settings: ReaderSettings, palette: ReaderPalette,
    onDismiss: () -> Unit, onDim: (GuideDim) -> Unit, onTurnOff: () -> Unit,
) {
    FolioTheme(dark = palette.isDark) {
        PullDownMenu(expanded = expanded, onDismiss = onDismiss, width = 230.dp, offset = DpOffset(0.dp, (-8).dp), checkColumn = true) {
            Header(stringResource(R.string.guide_dim))
            guideDimOptions().asReversed().forEach { option ->
                Item(option.label, { onDim(option.value) }, checked = settings.guideLine && settings.guideDim == option.value)
            }
            if (settings.guideLine) {
                Divider()
                Item(stringResource(R.string.guide_off), onTurnOff)
            }
        }
    }
}
