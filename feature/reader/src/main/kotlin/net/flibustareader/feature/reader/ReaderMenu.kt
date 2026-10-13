package net.flibustareader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.flibustareader.core.designsystem.ReaderPalette
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.UiSans

/** Reader menu (guideline § 6.3, white spots 3–4): capsules, then a row of square buttons. */
@Composable
internal fun ReaderMenu(
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
        Text(title, color = Flibusta.colors.label, fontFamily = UiSans, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (trailingText != null) Text(trailingText, color = Flibusta.colors.secondaryLabel, fontFamily = UiSans, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
        Icon(icon, contentDescription = null, tint = Flibusta.colors.label, modifier = Modifier.size(22.dp))
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
    val container = activeColor ?: if (active) Flibusta.colors.label else palette.menuCapsule()
    val tint = if (activeColor != null) Color.White else if (active) Flibusta.colors.background else Flibusta.colors.label
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
