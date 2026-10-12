package net.flibustareader.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.flibustareader.core.data.library.BookSharing
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.flibustareader.core.data.settings.PageTurn
import net.flibustareader.core.designsystem.ReaderFont
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.ReaderPalettes
import net.flibustareader.core.designsystem.FlibustaTheme
import net.flibustareader.core.designsystem.UiSans
import net.flibustareader.core.layout.Page
import net.flibustareader.core.layout.PageRenderer
import net.flibustareader.core.model.Block
import java.io.File
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import net.flibustareader.core.layout.Mark
import net.flibustareader.core.layout.TextSpan
import net.flibustareader.core.layout.MarkStyle
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

internal enum class ReaderSheet { Contents, Bookmarks, Search, Themes, Customize }

/** Width of the edge strips that turn pages on tap (white spot 1: about 10 % each side). */
private const val EDGE_TAP = 0.10f

@Composable
fun ReaderRoute(onClose: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    ReaderScreen(viewModel, onClose)
}

/** Colours of floating reader controls (round buttons, menu capsules) for a palette. */
internal fun ReaderPalette.control(): Color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF7F7F7)
/** Glass of menus over the page: on light pages a white glass tinted by the page (251/242/227 on «Покой», #72). */
internal fun ReaderPalette.menuCapsule(): Color = if (isDark) Color(0xFF3A3A3C) else androidx.compose.ui.graphics.lerp(background, Color.White, 0.6f)
internal val BookmarkRed = Color(0xFFFF3B30)

/**
 * The reader's own controls (menus, sheets, sliders) follow the book's appearance rather than
 * the system's, so a light page never gets dark sheets and vice versa.
 */
@Composable
fun ReaderScreen(vm: ReaderViewModel, onClose: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark = ReaderPalettes.isDark(settings.appearance, isSystemInDarkTheme())
    val palette = ReaderPalettes.of(settings.theme, dark)
    FlibustaTheme(dark = palette.isDark) { ReaderContent(vm, onClose) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderContent(vm: ReaderViewModel, onClose: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val state = vm.state
    val dark = ReaderPalettes.isDark(settings.appearance, isSystemInDarkTheme())
    val palette = ReaderPalettes.of(settings.theme, dark)
    vm.ink.apply {
        text = palette.text.toArgb()
        secondary = palette.secondary.toArgb()
        link = palette.link.toArgb()
        highlight = palette.highlight.toArgb()
    }

    // Apple Books opens a book with its controls showing (guideline § 6.2).
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var menuVisible by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var note by remember { mutableStateOf<List<Block>?>(null) }
    var image by remember { mutableStateOf<File?>(null) }
    var rotationLocked by rememberSaveable { mutableStateOf(false) }
    var brightness by rememberSaveable { mutableFloatStateOf(-1f) }
    // Guide line: index into the current page's lines; stepping back across a page lands on its last line.
    var guideIndex by remember { mutableIntStateOf(0) }
    var guideToLast by remember { mutableStateOf(false) }
    /** Note being edited: an existing highlight's id, or a new selection to save with the note. */
    var noteFor by remember { mutableStateOf<NoteTarget?>(null) }
    /** Highlight whose colour palette is open (#72). */
    var activeHighlight by remember { mutableStateOf<Long?>(null) }
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    ImmersiveMode(showBars = chromeVisible || sheet != null, lightBars = !palette.isDark)
    ReadingSessionEffect(vm)
    WindowEffects(rotationLocked = rotationLocked, brightness = brightness)
    BackHandler(enabled = menuVisible) { menuVisible = false }

    val density = LocalDensity.current
    // Insets ignoring visibility: showing or hiding the bars must never re-paginate the book.
    val topInset = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(density).toDp() }
    val bottomInset = with(density) { WindowInsets.navigationBarsIgnoringVisibility.getBottom(density).toDp() }
    val padding = PaddingValues(
        start = settings.style.margin.dp,
        end = settings.style.margin.dp,
        top = topInset + 56.dp,
        bottom = bottomInset + 60.dp,
    )
    val headerY = topInset + 14.dp
    val footerY = bottomInset + 16.dp

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val contentLeft = with(density) { padding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
        val contentTop = with(density) { padding.calculateTopPadding().toPx() }
        LaunchedEffect(widthPx, heightPx, padding, density) {
            val w = widthPx - with(density) { (padding.calculateLeftPadding(LayoutDirection.Ltr) + padding.calculateRightPadding(LayoutDirection.Ltr)).toPx() }
            val h = heightPx - with(density) { (padding.calculateTopPadding() + padding.calculateBottomPadding()).toPx() }
            vm.onViewport(Viewport(w.toInt(), h.toInt(), density.density, density.fontScale))
        }

        val guideOn = settings.guideLine && settings.pageTurn != PageTurn.Scroll
        val guideLines = remember(state.page) { state.page?.let(PageRenderer::lines).orEmpty() }
        LaunchedEffect(state.page) {
            guideIndex = if (guideToLast) guideLines.lastIndex.coerceAtLeast(0) else 0
            guideToLast = false
        }
        val guidePlate: Rect? = if (!guideOn) null else guideLines.getOrNull(guideIndex.coerceAtMost(guideLines.lastIndex))?.let { line ->
            val padX = with(density) { 14.dp.toPx() }
            val padY = with(density) { 3.dp.toPx() }
            Rect(contentLeft + line.left - padX, contentTop + line.top - padY, contentLeft + line.right + padX, contentTop + line.bottom + padY)
        }
        // onTap outlives compositions (the pager keeps the first reference), so it reads these at call time.
        val currentGuidePlate by rememberUpdatedState(guidePlate)
        val currentGuideLines by rememberUpdatedState(guideLines)

        // Saved highlights (yellow) and the live selection (blue) drawn under the text.
        val cornerPx = with(density) { 4.dp.toPx() }
        val underlinePx = with(density) { 2.dp.toPx() }
        val savedMarks = remember(highlights, palette, activeHighlight, cornerPx) {
            highlights.mapNotNull { h ->
                val span = vm.spanOf(h) ?: return@mapNotNull null
                val color = HighlightColor.of(h.color)
                val paint = highlightPaint(color, palette, active = h.id == activeHighlight).toArgb()
                if (color == HighlightColor.Underline) Mark(span, paint, MarkStyle.Underline, underlinePx) else Mark(span, paint, MarkStyle.Highlight, cornerPx)
            }
        }
        val selection = vm.selection
        val selectionColor = selectionFill(palette).toArgb()
        LaunchedEffect(state.page) {
            vm.select(null)
            activeHighlight = null
        }
        /** The word a long press started on; dragging extends the selection from it. */
        var anchor by remember { mutableStateOf<TextSpan?>(null) }

        fun onLongPress(offset: Offset): Boolean {
            if (menuVisible) return false
            val page = vm.state.page ?: return false
            val word = PageRenderer.wordAt(page, offset.x - contentLeft, offset.y - contentTop) ?: return false
            anchor = word
            vm.select(word)
            chromeVisible = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            return true
        }

        fun onLongPressDrag(offset: Offset) {
            val page = vm.state.page ?: return
            val from = anchor ?: return
            val p = PageRenderer.positionNear(page, offset.x - contentLeft, offset.y - contentTop) ?: return
            vm.select(if (p < from.start) TextSpan(p, from.end) else TextSpan(from.start, maxOf(p, from.end)))
        }

        /** Opens whatever is under a tap on [page] (content-box coordinates); false if nothing is. */
        fun openTarget(page: Page, x: Float, y: Float): Boolean {
            when (val target = vm.tapTarget(page, x, y)) {
                is TapTarget.Note -> note = target.blocks
                is TapTarget.Link -> vm.goTo(target.position)
                is TapTarget.External -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url))) }
                is TapTarget.Image -> image = target.file
                null -> return false
            }
            return true
        }

        fun onTap(offset: Offset, width: Float): TapAction {
            if (menuVisible) {
                menuVisible = false
                return TapAction.Handled
            }
            if (vm.selection != null || activeHighlight != null) {
                vm.select(null)
                activeHighlight = null
                return TapAction.Handled
            }
            val page = vm.state.page
            if (page != null && openTarget(page, offset.x - contentLeft, offset.y - contentTop)) return TapAction.Handled
            vm.clearHighlight()
            // A tap on a saved highlight opens its colour palette (#72).
            val hit = page?.let { PageRenderer.hitTest(it, offset.x - contentLeft, offset.y - contentTop) }
            val saved = hit?.let(vm::highlightAt)
            if (saved != null) {
                activeHighlight = saved.id
                return TapAction.Handled
            }
            val plate = currentGuidePlate
            if (plate != null) {
                // With the guide line on, a tap on the right half steps it down a line and on the left half
                // up a line, crossing pages at the ends; a tap on the line itself toggles the controls.
                return when {
                    plate.contains(offset) -> { chromeVisible = !chromeVisible; TapAction.ToggleChrome }
                    offset.x >= width / 2 -> if (guideIndex < currentGuideLines.lastIndex) { guideIndex++; TapAction.Handled } else TapAction.Next
                    else -> if (guideIndex > 0) { guideIndex--; TapAction.Handled } else { guideToLast = true; TapAction.Prev }
                }
            }
            return when {
                offset.x < width * EDGE_TAP -> TapAction.Prev
                offset.x > width * (1 - EDGE_TAP) -> TapAction.Next
                else -> {
                    chromeVisible = !chromeVisible
                    TapAction.ToggleChrome
                }
            }
        }

        when {
            state.error -> Text(
                stringResource(R.string.reader_error),
                color = palette.secondary,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                textAlign = TextAlign.Center,
            )
            state.page == null -> CircularProgressIndicator(
                color = palette.secondary,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
            settings.pageTurn == PageTurn.Scroll -> ScrollReader(
                vm = vm,
                palette = palette,
                padding = padding,
                onTapPage = { page, local ->
                    if (menuVisible) {
                        menuVisible = false
                    } else if (!openTarget(page, local.x, local.y)) {
                        vm.clearHighlight()
                        chromeVisible = !chromeVisible
                    }
                },
                onScrollStart = {
                    chromeVisible = false
                    menuVisible = false
                },
                marks = savedMarks,
            )
            else -> HorizontalPageTurner(
                current = state.page,
                prev = state.prev,
                next = state.next,
                mode = when {
                    settings.pageTurn == PageTurn.Fade -> TurnMode.Fade
                    settings.pageTurn == PageTurn.Curl && PageCurl.supported -> TurnMode.Curl
                    else -> TurnMode.Slide
                },
                onNext = vm::next,
                onPrev = vm::prev,
                onTap = ::onTap,
                onDragStart = {
                    chromeVisible = false
                    menuVisible = false
                    vm.clearHighlight()
                    vm.select(null)
                    activeHighlight = null
                },
                onSwipeDown = onClose,
                pageColor = palette.background,
                onLongPress = ::onLongPress,
                onLongPressDrag = ::onLongPressDrag,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                key(state.layoutGeneration) {
                    // Title and page number belong to the page and slide with it (guideline § 12).
                    Box(Modifier.fillMaxSize()) {
                        PageView(
                            page = page,
                            ink = vm.ink,
                            palette = palette,
                            highlight = state.highlight,
                            imageFile = vm::imageFile,
                            modifier = Modifier.fillMaxSize().padding(padding),
                            marks = if (page != null && page === state.page && selection != null) savedMarks + Mark(selection, selectionColor) else savedMarks,
                        )
                        PageTitle(
                            state.title, palette, Modifier.align(Alignment.TopCenter).padding(top = headerY),
                            titleMargin(chromeVisible, vm.history),
                        )
                        PageLabel(
                            page = page?.let { vm.pageNumberOf(it.start) },
                            total = state.totalPages,
                            full = chromeVisible,
                            palette = palette,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = footerY + 12.dp),
                        )
                    }
                }
            }
        }

        guidePlate?.let {
            val textArea = Rect(0f, contentTop - with(density) { 6.dp.toPx() }, widthPx.toFloat(), heightPx - with(density) { padding.calculateBottomPadding().toPx() - 6.dp.toPx() })
            GuideLineOverlay(it, textArea, settings.guideDim.fade, palette)
        }

        val selectedPage = state.page
        if (selection != null && selectedPage != null) {
            val touched = vm.highlightsOverlapping(selection)
            fun done() = vm.select(null)
            SelectionLayer(
                page = selectedPage,
                selection = selection,
                origin = Offset(contentLeft, contentTop),
                limit = heightPx - with(density) { 90.dp.toPx() },
                palette = palette,
                actions = SelectionActions(
                    canRemove = touched.isNotEmpty(),
                    onHighlight = { vm.highlight(selection) { activeHighlight = it }; done() },
                    onRemove = { vm.removeHighlights(selection); done() },
                    onNote = {
                        // On a single highlight the note belongs to it; otherwise a new highlight is made with the note.
                        val own = touched.singleOrNull()
                        noteFor = if (own != null) {
                            NoteTarget(own.id, vm.spanOf(own) ?: selection, own.note.orEmpty(), HighlightColor.of(own.color), own.createdAt)
                        } else {
                            NoteTarget(null, selection, "", HighlightColor.of(settings.highlightColor))
                        }
                        done()
                    },
                    onFind = { vm.search(vm.textOf(selection)); done(); sheet = ReaderSheet.Search },
                    onCopy = {
                        clipboard.setText(AnnotatedString(vm.textOf(selection)))
                        done()
                    },
                    onShare = {
                        val text = passageForSharing(vm.textOf(selection), state.title, vm.book?.metadata?.authors?.firstOrNull())
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        done()
                    },
                ),
                onChange = {
                    if (it != vm.selection) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.select(it)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Note markers at the start of highlights with notes (#78), and the palette of the tapped highlight (#72).
        val shownPage = state.page
        if (shownPage != null && settings.pageTurn != PageTurn.Scroll) {
            val markerHalf = with(density) { 11.dp.toPx() }
            highlights.forEach { h ->
                val note = h.note ?: return@forEach
                val span = vm.spanOf(h) ?: return@forEach
                val (first, _) = PageRenderer.bounds(shownPage, span) ?: return@forEach
                val color = HighlightColor.of(h.color)
                NoteMarker(
                    color = highlightPaint(color, palette, active = true),
                    palette = palette,
                    modifier = Modifier.offset {
                        IntOffset((contentLeft + first.left - markerHalf).roundToInt(), (contentTop + first.top - markerHalf).roundToInt())
                    },
                ) { noteFor = NoteTarget(h.id, span, note, color, h.createdAt) }
            }
            val active = highlights.firstOrNull { it.id == activeHighlight }
            val activeSpan = active?.let(vm::spanOf)
            val activeBounds = activeSpan?.let { PageRenderer.bounds(shownPage, it) }
            if (active != null && activeBounds != null) {
                val gap = with(density) { 8.dp.toPx() }
                val paletteHeight = with(density) { 44.dp.toPx() }
                val above = contentTop + activeBounds.first.top - gap - paletteHeight
                val y = if (above > contentTop - with(density) { 30.dp.toPx() }) above else contentTop + activeBounds.second.bottom + gap
                Box(Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) }, contentAlignment = Alignment.TopCenter) {
                    HighlightPalette(palette, onPick = { vm.setHighlightColor(active.id, it.key) })
                }
            }
        }

        // Header: the book title is always there; ↶ / ↷ appear after jumps (white spot 7).
        val scroll = settings.pageTurn == PageTurn.Scroll
        ReaderHeader(
            title = if (scroll) state.title else null,
            history = vm.history,
            pageNumberOf = vm::pageNumberOf,
            palette = palette,
            chromeVisible = chromeVisible,
            onBack = vm::historyBack,
            onForward = vm::historyForward,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = headerY),
        )

        // Footer: «6 из 1269» with the controls, just «6» without them (white spot 5).
        if (scroll) {
            PageLabel(
                page = state.globalPage,
                total = state.totalPages,
                full = chromeVisible,
                palette = palette,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = footerY + 12.dp),
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
        if (chromeVisible) {
            MenuButton(palette, bookmarked = state.bookmarked, modifier = cornerButton) { menuVisible = !menuVisible }
        } else if (state.bookmarked) {
            // A bookmarked page shows a red bookmark where the menu button sits (white spot 4).
            RoundButton(Icons.Rounded.Bookmark, stringResource(R.string.reader_bookmark), BookmarkRed, Color.White, cornerButton) { vm.toggleBookmark() }
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = footerY + 56.dp),
        ) {
            ReaderMenu(
                palette = palette,
                progress = state.progress,
                fragments = bookmarks.size + highlights.size,
                bookmarked = state.bookmarked,
                rotationLocked = rotationLocked,
                guideLine = settings.guideLine,
                onContents = { menuVisible = false; sheet = ReaderSheet.Contents },
                onFragments = { menuVisible = false; sheet = ReaderSheet.Bookmarks },
                onSearch = { menuVisible = false; sheet = ReaderSheet.Search },
                onThemes = { menuVisible = false; sheet = ReaderSheet.Themes },
                onShare = { menuVisible = false; shareBook(context, vm) },
                onRotationLock = { rotationLocked = !rotationLocked },
                onGuideLine = { menuVisible = false; vm.setGuideLine(!settings.guideLine) },
                onBookmark = {
                    haptic.performHapticFeedback(if (state.bookmarked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    vm.toggleBookmark()
                },
            )
        }
    }

    when (sheet) {
        ReaderSheet.Contents -> ContentsSheet(vm, palette, onDismiss = { sheet = null })
        ReaderSheet.Bookmarks -> FragmentsSheet(vm, palette, ReaderFont.of(settings.themeStyle.font).composeFamily, onDismiss = { sheet = null })
        ReaderSheet.Search -> SearchSheet(vm, palette, ReaderFont.of(settings.themeStyle.font).composeFamily, onDismiss = { sheet = null })
        ReaderSheet.Themes, ReaderSheet.Customize -> ThemesSheet(
            vm, settings, palette,
            brightness = brightness,
            onBrightness = { brightness = it },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
    noteFor?.let { target ->
        NoteSheet(
            target = target,
            quote = vm.textOf(target.span),
            bookFont = ReaderFont.of(settings.themeStyle.font).composeFamily,
            palette = palette,
            onDone = { text ->
                if (target.highlightId != null) vm.setHighlightNote(target.highlightId, text)
                else vm.highlightWithNote(target.span, text)
                noteFor = null
            },
        )
    }
    note?.let { FootnoteSheet(it, palette, onDismiss = { note = null }) }
    image?.let { ImageViewer(it, onDismiss = { image = null }) }
}

private fun shareBook(context: Context, vm: ReaderViewModel) {
    val file = vm.bookFile(context) ?: return
    BookSharing.share(context, listOf(file to vm.state.title))
}

@Composable
private fun ReaderHeader(
    title: String?,
    history: NavHistory,
    pageNumberOf: (net.flibustareader.core.model.Position) -> Int?,
    palette: ReaderPalette,
    chromeVisible: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth().height(48.dp)) {
        if (title != null) PageTitle(title, palette, Modifier.align(Alignment.Center), titleMargin(chromeVisible, history))
        history.back.lastOrNull()?.let { target ->
            HistoryMark(pageNumberOf(target), back = true, palette, Modifier.align(Alignment.CenterStart).padding(start = 22.dp), onBack)
        }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            history.forward.lastOrNull()?.let { target ->
                HistoryMark(pageNumberOf(target), back = false, palette, Modifier.padding(end = if (chromeVisible) 10.dp else 4.dp), onForward)
            }
            if (chromeVisible) {
                RoundButton(Icons.Rounded.Close, stringResource(R.string.reader_close), palette.control(), palette.secondary, Modifier, onClick = onClose)
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

/** «1201 ↷» next to × takes more room than the usual side margin; the title stays centred. */
private fun titleMargin(chromeVisible: Boolean, history: NavHistory): Dp =
    if (chromeVisible && history.forward.isNotEmpty()) 150.dp else 96.dp

/** Book title at the top of a page, grey and centred. */
@Composable
private fun PageTitle(title: String, palette: ReaderPalette, modifier: Modifier, margin: Dp) {
    Box(modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Text(
            title,
            color = palette.secondary,
            fontFamily = UiSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = margin),
        )
    }
}

/** «6 из 1269» with the controls, just «6» without them (white spot 5). */
@Composable
private fun PageLabel(page: Int?, total: Int?, full: Boolean, palette: ReaderPalette, modifier: Modifier) {
    val label = when {
        page != null && total != null && full -> stringResource(R.string.reader_page_of, page, total)
        page != null -> page.toString()
        else -> ""
    }
    Text(label, color = palette.secondary, fontFamily = UiSans, fontSize = 15.sp, modifier = modifier)
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

/** Reader menu (guideline § 6.3, white spots 3–4): capsules, then a row of square buttons. */
@Composable
private fun ReaderMenu(
    palette: ReaderPalette,
    progress: Float,
    fragments: Int,
    bookmarked: Boolean,
    rotationLocked: Boolean,
    guideLine: Boolean,
    onContents: () -> Unit,
    onFragments: () -> Unit,
    onSearch: () -> Unit,
    onThemes: () -> Unit,
    onShare: () -> Unit,
    onRotationLock: () -> Unit,
    onGuideLine: () -> Unit,
    onBookmark: () -> Unit,
) {
    Column(Modifier.width(272.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MenuCapsule(stringResource(R.string.reader_contents) + " • " + stringResource(R.string.reader_percent, (progress * 100).toInt()), Icons.AutoMirrored.Rounded.List, palette, onContents)
        if (fragments > 0) {
            MenuCapsule(stringResource(R.string.reader_fragments), Icons.Rounded.Bookmarks, palette, onFragments, trailingText = fragments.toString())
        }
        MenuCapsule(stringResource(R.string.reader_search), Icons.Rounded.Search, palette, onSearch)
        MenuCapsule(stringResource(R.string.reader_themes), Icons.Rounded.TextFields, palette, onThemes)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MenuSquare(Icons.Rounded.IosShare, stringResource(R.string.reader_share), palette, Modifier.weight(1f), onClick = onShare)
            MenuSquare(
                if (rotationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation,
                stringResource(R.string.reader_rotation_lock),
                palette,
                Modifier.weight(1f),
                active = rotationLocked,
                onClick = onRotationLock,
            )
            MenuSquare(
                Icons.Rounded.CalendarViewDay,
                stringResource(R.string.reader_guide_line),
                palette,
                Modifier.weight(1f),
                active = guideLine,
                onClick = onGuideLine,
            )
            MenuSquare(
                if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                stringResource(R.string.reader_bookmark),
                palette,
                Modifier.weight(1f),
                activeColor = if (bookmarked) BookmarkRed else null,
                onClick = onBookmark,
            )
        }
    }
}

@Composable
private fun MenuCapsule(title: String, icon: ImageVector, palette: ReaderPalette, onClick: () -> Unit, trailingText: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(CircleShape)
            .background(palette.menuCapsule())
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = palette.text, fontFamily = UiSans, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (trailingText != null) Text(trailingText, color = palette.secondary, fontFamily = UiSans, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
        Icon(icon, contentDescription = null, tint = palette.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MenuSquare(
    icon: ImageVector,
    description: String,
    palette: ReaderPalette,
    modifier: Modifier,
    active: Boolean = false,
    activeColor: Color? = null,
    onClick: () -> Unit,
) {
    val container = activeColor ?: if (active) palette.text else palette.menuCapsule()
    val tint = if (activeColor != null) Color.White else if (active) palette.background else palette.text
    Box(
        modifier
            .height(46.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Rotation lock and the window-only brightness from «Темы и настройки» (decision C). */
@Composable
private fun WindowEffects(rotationLocked: Boolean, brightness: Float) {
    val activity = LocalView.current.context.findActivity() ?: return
    DisposableEffect(rotationLocked) {
        activity.requestedOrientation = if (rotationLocked) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(brightness) {
        val attrs = activity.window.attributes
        attrs.screenBrightness = if (brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brightness.coerceIn(0.02f, 1f)
        activity.window.attributes = attrs
        onDispose {
            val a = activity.window.attributes
            a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = a
        }
    }
}

/** Hides the system bars while reading; they come back with the reader chrome. */
@Composable
private fun ImmersiveMode(showBars: Boolean, lightBars: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    DisposableEffect(showBars, lightBars) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
        if (showBars) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    val appDark = isSystemInDarkTheme()
    DisposableEffect(Unit) {
        onDispose {
            // Hand the bars back in the app's own appearance, not the reading theme's.
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = !appDark
            controller.isAppearanceLightNavigationBars = !appDark
        }
    }
}

@Composable
private fun ReadingSessionEffect(vm: ReaderViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.onPause()
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
