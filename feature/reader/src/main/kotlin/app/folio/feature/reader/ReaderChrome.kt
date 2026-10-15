package app.folio.feature.reader

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.data.library.BookSharing
import app.folio.core.data.settings.LineAlign
import app.folio.core.data.settings.PageTurn
import app.folio.core.data.settings.ReaderSettings
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.readerChromeSeparator
import app.folio.core.designsystem.capsuleColor
import app.folio.core.designsystem.controlColor

internal fun ReaderPalette.control(): Color = controlColor()
internal fun ReaderPalette.menuCapsule(): Color = capsuleColor()
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
    compact: Boolean,
    screenHeight: Dp,
) {
    val state = vm.state
    val context = LocalContext.current
    val density = LocalDensity.current
    var topPanelHeight by remember { mutableStateOf(layout.topInset + 80.dp) }
    val scroll = settings.pageTurn == PageTurn.Scroll
    val guideOn = settings.guideLine && !scroll
    val footer = settings.shownFooter
    val margin = layout.padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
    val headerLook = headerLook(settings, palette)
    val footerLook = footerLook(settings, palette)
    val footerAlpha by animateFloatAsState(if (controls.chromeVisible) 0f else 1f, tween(150), label = "scrollFooter")
    val headerAlpha by animateFloatAsState(if (marks) 0f else 1f, tween(150), label = "scrollTitle")

    if (scroll) headerText(settings.header, state.title, state.chapterTitle)?.let { title ->
        ReaderLineBlock(true, headerLook, margin,
            Modifier.align(Alignment.TopCenter).padding(top = layout.topInset).alpha(headerAlpha)) {
            BlockTitle(title, headerLook, headerLook.titleMargin(24.dp, margin))
        }
    }
    if (scroll && !footer.isEmpty) {
        ReaderLineBlock(false, footerLook, margin,
            Modifier.align(Alignment.BottomCenter).padding(bottom = layout.bottomInset).alpha(footerAlpha)) {
            PageFooterBar(
                center = footerLook.text(footerCenter(footer, state.globalPage, state.totalPages, state.progress,
                    state.pagesLeftInChapter.takeIf { state.page != null }, footerWords, readerChromeSeparator(settings.chromeStyles.footer))),
                clock = clock, battery = battery, palette = palette,
                modifier = Modifier.padding(horizontal = margin), textStyle = footerLook.style,
                spread = footerLook.layout.align == LineAlign.Spread, onCenterTap = onFooterTap,
            )
        }
    }
    if (guideOn && !controls.chromeVisible) {
        GuideLineButton(settings.guideDim, palette, vm::setGuideDim, { vm.setGuideLine(false) },
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = layout.footerY))
    }
    if (state.bookmarked && !controls.chromeVisible) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = layout.topInset, end = 16.dp)
            .size(48.dp).clickable(role = Role.Button) { vm.toggleBookmark() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bookmark, stringResource(R.string.reader_remove_bookmark), tint = BookmarkRed, modifier = Modifier.size(26.dp))
        }
    }
    AnimatedVisibility(controls.chromeVisible, Modifier.align(Alignment.TopCenter)
        .onSizeChanged { topPanelHeight = with(density) { it.height.toDp() } }, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        ReaderTopBar(state.title, vm.book?.metadata?.authors.orEmpty().joinToString(", "), palette, layout.topInset,
            onClose = onClose, onShare = { shareBook(context, vm) })
    }
    AnimatedVisibility(controls.chromeVisible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        ReaderNavigationPanel(vm, settings, palette, controls, fragments, layout.bottomInset, compact,
            maxHeight = (screenHeight - topPanelHeight - 8.dp).coerceAtLeast(48.dp))
    }
}

private fun shareBook(context: Context, vm: ReaderViewModel) {
    val file = vm.bookFile(context) ?: return
    BookSharing.share(context, listOf(file to vm.state.title))
}

/** Title belongs to the page, so it moves with the page turn. */
@Composable
internal fun PageTitle(title: String, palette: ReaderPalette, modifier: Modifier, margin: Dp, look: LineLook) {
    val height = with(LocalDensity.current) { look.style.lineHeight.toDp() } + 16.dp
    Box(modifier.fillMaxWidth().height(height.coerceAtLeast(32.dp)), contentAlignment = look.alignment) {
        Text(title, style = look.style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = look.textAlign, modifier = Modifier.padding(horizontal = margin))
        if (look.layout.rule) LineRule(look.ruleColor, Modifier.align(Alignment.BottomCenter).padding(horizontal = margin))
    }
}

@Composable
internal fun RoundButton(
    icon: ImageVector, description: String, container: Color, tint: Color,
    modifier: Modifier = Modifier, size: Dp = 46.dp, onClick: () -> Unit,
) {
    Box(modifier.size(size)
        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .clip(CircleShape).background(container).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
