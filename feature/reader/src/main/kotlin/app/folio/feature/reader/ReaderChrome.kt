package app.folio.feature.reader

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.data.library.BookSharing
import app.folio.core.data.settings.FooterStyle
import app.folio.core.data.settings.LineAlign
import app.folio.core.data.settings.PageTurn
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.readerChromeSeparator
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.UiSans
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.BoxScope
import app.folio.core.data.settings.ReaderSettings

/** Colours of floating reader controls (round buttons, menu capsules) for a palette. */
internal fun ReaderPalette.control(): Color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF7F7F7)
/** Glass of menus over the page: on light pages a white glass tinted by the page (251/242/227 on «Покой», #72). */
internal fun ReaderPalette.menuCapsule(): Color = if (isDark) Color(0xFF3A3A3C) else androidx.compose.ui.graphics.lerp(background, Color.White, 0.6f)
internal val BookmarkRed = Color(0xFFFF3B30)

@Composable
internal fun BoxScope.ReaderChrome(
    vm: ReaderViewModel,
    settings: ReaderSettings,
    palette: ReaderPalette,
    controls: ReaderControls,
    layout: ReaderLayout,
    fragments: Int,
    marks: Boolean,
    footerWords: FooterWords,
    clock: String?,
    battery: BatteryInfo?,
    onClose: () -> Unit,
    onFooterTap: () -> Unit,
) {
    val state = vm.state
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scroll = settings.pageTurn == PageTurn.Scroll
    val guideOn = settings.guideLine && !scroll
    val header = settings.header
    val footer = settings.shownFooter
    val margin = settings.style.margin.dp
    val topInset = layout.topInset
    val bottomInset = layout.bottomInset
    val headerY = layout.headerY
    val footerY = layout.footerY
    // Time/battery repeat the status bar; the guide button and bookmark need space at the corners.
    val headerLook = headerLook(settings, palette)
    val footerLook = footerLook(settings, palette)
    val footerStyle = footerLook.style
    val footerEnd = if (state.bookmarked) (74.dp - margin).coerceAtLeast(0.dp) else 0.dp
    val footerLineHeight = with(LocalDensity.current) { footerStyle.lineHeight.toDp() }
    val stripHeight = (footerLineHeight + 10.dp).coerceAtLeast(FooterStripHeight)
    val footerAlpha by animateFloatAsState(if (controls.chromeVisible) 0f else 1f, tween(150), label = "scrollFooter")
    // With nothing above the text, the controls get a band of page colour to stand on.
    AnimatedVisibility(
        visible = controls.chromeVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        EdgeFade(palette, topInset + 64.dp, top = true)
    }

    // Header: the chosen title; ↶ / ↷ appear with the controls, and ↶ for a moment after a jump (white spot 7).
    ReaderHeader(
        title = if (scroll) headerText(header, state.title, state.chapterTitle)?.let(headerLook::text) else null,
        history = vm.history,
        pageNumberOf = vm::pageNumberOf,
        palette = palette,
        look = headerLook,
        textMargin = margin,
        chromeVisible = controls.chromeVisible,
        showBack = marks,
        onBack = vm::historyBack,
        onForward = vm::historyForward,
        onClose = onClose,
        // Over scrolling text the title sits a little higher, clear of the fade above the text.
        modifier = Modifier.align(Alignment.TopCenter).padding(top = if (scroll) headerY - 10.dp else headerY),
    )

    // Footer: «6 из 1269» with the controls, just «6» without them (white spot 5), plus time and battery.
    if (scroll && !footer.isEmpty) {
        val strip = footer.style == FooterStyle.Strip
        PageFooterBar(
            center = footerLook.text(footerCenter(footer, state.globalPage, state.totalPages, state.progress, state.pagesLeftInChapter.takeIf { state.page != null }, footerWords, readerChromeSeparator(settings.chromeStyles.footer))),
            clock = clock,
            battery = battery,
            palette = palette,
            modifier = Modifier.align(Alignment.BottomCenter).padding(
                start = margin,
                end = margin,
                bottom = if (strip) bottomInset + layout.footerEdge + (stripHeight - footerLineHeight) / 2 else footerY + 12.dp,
            ).alpha(footerAlpha),
            textStyle = footerStyle,
            spread = footerLook.layout.align == LineAlign.Spread,
            rule = footerLook.ruleColor.takeIf { footerLook.layout.rule && !strip },
            endInset = footerEnd,
            onCenterTap = onFooterTap,
        )
    }

    if (guideOn) {
        GuideLineButton(
            dim = settings.guideDim,
            palette = palette,
            onDim = vm::setGuideDim,
            onTurnOff = { vm.setGuideLine(false) },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = footerY),
        )
    }
    val cornerButton = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = footerY)
    if (controls.chromeVisible) {
        MenuButton(palette, bookmarked = state.bookmarked, modifier = cornerButton) { controls.menuVisible = !controls.menuVisible }
    } else if (state.bookmarked) {
        // A bookmarked page shows a red bookmark where the menu button sits (white spot 4).
        RoundButton(Icons.Rounded.Bookmark, stringResource(R.string.reader_bookmark), BookmarkRed, Color.White,
            cornerButton) { vm.toggleBookmark() }
    }

    AnimatedVisibility(
        visible = controls.menuVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
        exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = footerY + 56.dp),
    ) {
        ReaderMenu(
            palette = palette,
            progress = state.progress,
            fragments = fragments,
            bookmarked = state.bookmarked,
            rotationLocked = controls.rotationLocked,
            guideLine = settings.guideLine,
            onContents = { controls.menuVisible = false; controls.sheet = ReaderSheet.Contents },
            onFragments = { controls.menuVisible = false; controls.sheet = ReaderSheet.Bookmarks },
            onSearch = { controls.menuVisible = false; controls.sheet = ReaderSheet.Search },
            onThemes = { controls.menuVisible = false; controls.sheet = ReaderSheet.Themes },
            onShare = { controls.menuVisible = false; shareBook(context, vm) },
            onRotationLock = { controls.rotationLocked = !controls.rotationLocked },
            onGuideLine = { controls.menuVisible = false; vm.setGuideLine(!settings.guideLine) },
            onBookmark = {
                haptic.performHapticFeedback(if (state.bookmarked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                vm.toggleBookmark()
            },
        )
    }
}

private fun shareBook(context: Context, vm: ReaderViewModel) {
    val file = vm.bookFile(context) ?: return
    BookSharing.share(context, listOf(file to vm.state.title))
}

@Composable
internal fun ReaderHeader(
    title: String?,
    history: NavHistory,
    pageNumberOf: (app.folio.core.model.Position) -> Int?,
    palette: ReaderPalette,
    look: LineLook,
    textMargin: Dp,
    chromeVisible: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val headerHeight = (with(LocalDensity.current) { look.style.lineHeight.toDp() } + 16.dp).coerceAtLeast(32.dp)
    Box(modifier.fillMaxWidth().height(headerHeight)) {
        val alpha by animateFloatAsState(if (chromeVisible || showBack) 0f else 1f, tween(150), label = "scrollHeader")
        if (title != null) PageTitle(title, palette, Modifier.align(Alignment.Center).alpha(alpha), look.titleMargin(24.dp, textMargin), look)
        history.back.lastOrNull()?.let { target ->
            AnimatedVisibility(showBack, Modifier.align(Alignment.CenterStart).padding(start = 22.dp), enter = fadeIn(), exit = fadeOut()) {
                HistoryMark(pageNumberOf(target), back = true, palette, Modifier, onBack)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            history.forward.lastOrNull()?.let { target ->
                AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                    HistoryMark(pageNumberOf(target), back = false, palette, Modifier.padding(end = 10.dp), onForward)
                }
            }
            if (chromeVisible) {
                RoundButton(Icons.Rounded.Close, stringResource(R.string.reader_close), palette.control(), Folio.colors.label, Modifier, onClick = onClose)
            }
        }
    }
}

/** «↶ 6» / «111 ↷»: a filled circle with the arrow and the page number of the jump target. */
@Composable
private fun HistoryMark(page: Int?, back: Boolean, palette: ReaderPalette, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val arrow = @Composable {
            Box(Modifier.size(19.dp).clip(CircleShape).background(palette.text), contentAlignment = Alignment.Center) {
                Icon(
                    if (back) Icons.AutoMirrored.Rounded.Undo else Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = stringResource(if (back) R.string.reader_history_back else R.string.reader_history_forward),
                    tint = palette.background,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        val number = @Composable { Text(page?.toString() ?: "•", color = palette.text, fontFamily = UiSans, fontSize = 15.sp) }
        if (back) { arrow(); number() } else { number(); arrow() }
    }
}

/**
 * Room the title leaves at the sides for what sits next to it: «1201 ↷» and × with the controls,
 * «↶ 6» while [marks] show; otherwise almost the full width. The title stays centred.
 */
internal fun titleMargin(marks: Boolean, chromeVisible: Boolean, history: NavHistory): Dp = when {
    chromeVisible && history.forward.isNotEmpty() -> 150.dp
    chromeVisible || (marks && history.back.isNotEmpty()) -> 96.dp
    else -> 24.dp
}

/** Title at the top of a page in the line's look; a rule, when chosen, runs under it at the text side. */
@Composable
internal fun PageTitle(title: String, palette: ReaderPalette, modifier: Modifier, margin: Dp, look: LineLook) {
    val height = with(LocalDensity.current) { look.style.lineHeight.toDp() } + 16.dp
    Box(modifier.fillMaxWidth().height(height.coerceAtLeast(32.dp)), contentAlignment = look.alignment) {
        Text(
            title,
            style = look.style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = look.textAlign,
            modifier = Modifier.padding(horizontal = margin),
        )
        if (look.layout.rule) LineRule(look.ruleColor, Modifier.align(Alignment.BottomCenter).padding(horizontal = margin))
    }
}

@Composable
internal fun RoundButton(
    icon: ImageVector,
    description: String,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Round menu button; a red bookmark badge sits on it while the page is bookmarked. */
@Composable
private fun MenuButton(palette: ReaderPalette, bookmarked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        RoundButton(Icons.AutoMirrored.Rounded.List, stringResource(R.string.reader_menu), palette.control(), palette.text, onClick = onClick)
        if (bookmarked) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp).clip(CircleShape).background(BookmarkRed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Bookmark, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}
