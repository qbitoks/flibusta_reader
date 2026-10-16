package app.folio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import app.folio.core.designsystem.coverColors
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.designsystem.BookCover
import app.folio.core.designsystem.BookTransition
import app.folio.core.designsystem.bookTransition
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.LargeTitleHeader
import app.folio.core.designsystem.components.SectionTitle
import app.folio.core.designsystem.components.SectionBand
import app.folio.core.designsystem.components.CapsuleButton

/**
 * «Главная» (guideline § 4): sections sit on bands with a vertical gradient, which is what
 * separates them instead of dividers.
 */
@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onOpenShelf: (Shelf) -> Unit,
    onAddBooks: () -> Unit,
    onAddToCollection: (BookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val home by vm.home.collectAsStateWithLifecycle()
    if (!home.loaded) return
    if (home.isEmpty) {
        EmptyLibrary(contentPadding, onAddBooks)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)) {
        item {
            SectionBand(first = true) {
                LargeTitleHeader(stringResource(R.string.home_title))
                AppUpdateCard(Modifier.padding(start = 32.dp, end = 32.dp, bottom = 16.dp))
                if (home.continueReading.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.home_continue), Modifier.padding(top = 0.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 28.dp),
                    ) {
                        items(home.continueReading, key = { it.id }) { item ->
                            BookCard(vm, item, MenuContext.Continue, onOpen, onAddToCollection)
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        if (home.wantToRead.isNotEmpty()) {
            item {
                SectionBand {
                    SectionTitle(stringResource(R.string.home_want_to_read), onClick = { onOpenShelf(Shelf.WantToRead) })
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(17.dp),
                        modifier = Modifier.padding(bottom = 28.dp),
                    ) {
                        items(home.wantToRead, key = { it.id }) { item ->
                            Column(Modifier.width(156.dp)) {
                                Box(Modifier.height(244.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                    BookCover(
                                        item.title,
                                        item.author,
                                        item.cover,
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 244.dp)
                                            .bookTransition("want-${item.id}")
                                            .clickable { BookTransition.open("want-${item.id}", item.id, onOpen) },
                                        elevation = 8.dp,
                                        naturalAspect = true,
                                    )
                                }
                                BookCaption(vm, item, MenuContext.Library, Modifier.padding(top = 10.dp), onAddToCollection = onAddToCollection)
                            }
                        }
                    }
                }
            }
        }
        if (home.previous.isNotEmpty()) {
            item {
                SectionBand {
                    SectionTitle(stringResource(R.string.home_previous), onClick = { onOpenShelf(Shelf.Previous) })
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 28.dp),
                    ) {
                        items(home.previous, key = { it.id }) { item ->
                            BookCard(vm, item, MenuContext.Library, onOpen, onAddToCollection)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal card 242 × 78 dp used by «Продолжить» (tinted with the cover's colours) and
 * «Предыдущие» (neutral).
 */
@Composable
private fun BookCard(
    vm: LibraryViewModel,
    item: BookItem,
    context: MenuContext,
    onOpen: (String) -> Unit,
    onAddToCollection: (BookItem) -> Unit,
) {
    val c = Folio.colors
    val tinted = context == MenuContext.Continue
    val ink = if (tinted) Color.White else c.label
    val secondary = if (tinted) Color.White.copy(alpha = 0.85f) else c.secondaryLabel
    Box(
        Modifier
            .width(242.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.card)
            .clickable { BookTransition.open("$context-${item.id}", item.id, onOpen) },
    ) {
        if (tinted) CardBackdrop(item)
        Row(
            Modifier.fillMaxSize().padding(start = 9.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(item.title, item.author, item.cover, Modifier.width(39.dp).bookTransition("$context-${item.id}"), elevation = 3.dp, titleSize = 5.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                Text(item.title, style = FolioType.subheadline.copy(fontWeight = FolioType.headline.fontWeight), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.author ?: stringResource(R.string.book_unknown_author), style = FolioType.subheadline, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val e = item.entity
                    if (e.isFinished) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = secondary, modifier = Modifier.size(14.dp))
                        Text(" " + stringResource(R.string.book_finished), style = FolioType.subheadline, color = secondary, maxLines = 1)
                    } else {
                        Text(
                            stringResource(R.string.book_kind).lowercase() + " • " + stringResource(R.string.book_percent, (e.progress * 100).toInt()),
                            style = FolioType.subheadline,
                            color = secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            MoreButton(vm, item, context, onAddToCollection, tint = ink)
        }
    }
}

/**
 * Apple Books tints «Продолжить» cards with the cover itself, blurred beyond recognition.
 * The cover is decoded at a tiny size (cheap, already soft when stretched), blurred for real
 * on Android 12+, and darkened so white text stays readable. Books without a cover get the
 * colours of their generated cover.
 */
@Composable
private fun CardBackdrop(item: BookItem) {
    val context = LocalContext.current
    if (item.cover == null) {
        val (top, bottom) = remember(item.title) { coverColors(item.title) }
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(top, bottom))))
        return
    }
    val request = remember(item.cover) {
        ImageRequest.Builder(context).data(item.cover).size(16, 24).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        modifier = Modifier.fillMaxSize().blur(24.dp, BlurredEdgeTreatment.Rectangle),
    )
    // Light covers need more darkening for white text; the stored average colour tells how light it is.
    // Stored colours were darkened to 62 % at import (≈ 0.35 in luminance); undo that to judge the cover.
    val lightness = item.gradient?.let { (a, b) -> ((a.luminance() + b.luminance()) / 2f / 0.35f).coerceAtMost(1f) } ?: 0.3f
    // The stored colours underrate light covers (a white cover read ≈ #878585 behind white text), so the curve is steep.
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (0.3f + lightness * 1.0f).coerceAtMost(0.72f))))
}

@Composable
private fun EmptyLibrary(contentPadding: PaddingValues, onAddBooks: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        LargeTitleHeader(stringResource(R.string.home_title))
        AppUpdateCard(Modifier.padding(horizontal = 32.dp))
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.home_empty_title), style = FolioType.sheetTitle, color = Folio.colors.secondaryLabel, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_empty_text), style = FolioType.subheadline, color = Folio.colors.secondaryLabel, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            CapsuleButton(stringResource(R.string.add_books), onAddBooks)
        }
    }
}
