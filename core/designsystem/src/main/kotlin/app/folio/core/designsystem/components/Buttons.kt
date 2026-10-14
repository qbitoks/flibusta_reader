package app.folio.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType

enum class CircleButtonStyle {
    /** Translucent glass: ×, ≡, •••, back. */
    Glass,
    /** Dark filled with a white glyph: the confirming ✓ in sheet headers. */
    Confirm,
    /** Light filled with a dark glyph: the dismissing × in «Настройка темы». */
    Light,
}

/** Round header/toolbar button, Ø 44 dp by default. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CircleButtonStyle = CircleButtonStyle.Glass,
    size: Dp = 44.dp,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val c = Folio.colors
    val base = modifier.size(size)
    val surface = when (style) {
        CircleButtonStyle.Glass -> base.glass(CircleShape, elevation = 2.dp)
        CircleButtonStyle.Confirm -> base
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (c.isDark) Color(0xFFF2F2F2) else Color(0xFF3B3B3B))
        CircleButtonStyle.Light -> base
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (c.isDark) Color(0xFF3A3A3C) else Color(0xFFFAFAFA))
    }
    val glyph = tint ?: when (style) {
        CircleButtonStyle.Confirm -> if (c.isDark) Color.Black else Color.White
        else -> c.label
    }
    Box(
        surface.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) glyph else glyph.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.5f))
    }
}

/** Glass capsule with text (and optional leading icon): «Править», «Выбрать все», «Добавить в...». */
@Composable
fun GlassCapsule(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
) {
    Row(
        modifier
            .heightIn(min = height)
            .glass(CircleShape, elevation = 2.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Folio.colors.label, modifier = Modifier.size(20.dp))
        Text(text, style = FolioType.body, color = Folio.colors.label)
    }
}

/**
 * Filled full-width or content-width capsule: «Настроить», «Продолжить чтение», «Сбросить тему».
 * [subtitle] adds a second, secondary line as in «Продолжить чтение / <книга>».
 */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    container: Color = Folio.colors.capsule,
    contentColor: Color = Folio.colors.label,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .heightIn(min = 45.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = FolioType.headline,
                color = if (enabled) contentColor else Folio.colors.tertiaryLabel,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(subtitle, style = FolioType.footnote, color = Folio.colors.secondaryLabel, maxLines = 1)
            }
        }
    }
}

/** Small uppercase action capsule on book rows: «ЧИТАТЬ». Inverted colours: white on dark, black on light. */
@Composable
fun ActionPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Folio.colors
    Box(
        modifier
            .clip(CircleShape)
            .background(c.label)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = FolioType.badge, color = c.background)
    }
}

/** «НОВАЯ» badge under a cover. */
@Composable
fun NewBadge(text: String, modifier: Modifier = Modifier) {
    val c = Folio.colors
    Text(
        text.uppercase(),
        style = FolioType.badge,
        color = c.badgeText,
        modifier = modifier
            .clip(CircleShape)
            .background(c.badgeBackground)
            .padding(horizontal = 9.dp, vertical = 2.dp),
    )
}
