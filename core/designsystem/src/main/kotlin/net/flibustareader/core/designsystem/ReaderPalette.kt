package net.flibustareader.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import net.flibustareader.core.data.settings.Appearance
import net.flibustareader.core.data.settings.ReaderTheme

/** Colours of the reading surface for one theme in one appearance. */
@Immutable
data class ReaderPalette(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val link: Color,
    val highlight: Color,
    val chrome: Color,
    val isDark: Boolean,
)

object ReaderPalettes {
    /** Measured from Apple Books theme tiles (guideline § 6.7); chrome/secondary follow the reader screenshots. */
    private fun p(bg: Long, text: Long, dark: Boolean) = ReaderPalette(
        background = Color(bg),
        text = Color(text),
        secondary = if (dark) Color(0xFFA3A3A5) else Color(0xFF8E8E93),
        link = if (dark) Color(0xFF5AA7FF) else Color(0xFF1A73E8),
        // Search hits and the active selection are blue in Apple Books.
        highlight = if (dark) Color(0x993A7BDF) else Color(0xCCA9C6F4),
        chrome = if (dark) Color(0xFF1C1C1E) else Color(0xFFFAFAFA),
        isDark = dark,
    )

    private val light = mapOf(
        ReaderTheme.Original to p(0xFFFFFFFF, 0xFF000000, false),
        ReaderTheme.Quiet to p(0xFF4A4A4C, 0xFFB8B8BA, true),
        ReaderTheme.Paper to p(0xFFEDEDED, 0xFF000000, false),
        ReaderTheme.Bold to p(0xFFFFFFFF, 0xFF000000, false),
        ReaderTheme.Calm to p(0xFFF0E2C8, 0xFF2F2019, false),
        ReaderTheme.Focus to p(0xFFFFFCF3, 0xFF010100, false),
    )

    private val dark = mapOf(
        ReaderTheme.Original to p(0xFF000000, 0xFFFFFFFF, true),
        ReaderTheme.Quiet to p(0xFF000000, 0xFF9C9C9E, true),
        ReaderTheme.Paper to p(0xFF1C1C1E, 0xFFFFFFFF, true),
        ReaderTheme.Bold to p(0xFF000000, 0xFFFFFFFF, true),
        ReaderTheme.Calm to p(0xFF433D31, 0xFFFFFEF3, true),
        ReaderTheme.Focus to p(0xFF19150C, 0xFFFFFFF6, true),
    )

    fun of(theme: ReaderTheme, dark: Boolean): ReaderPalette = (if (dark) this.dark else light).getValue(theme)

    fun isDark(appearance: Appearance, systemDark: Boolean) = when (appearance) {
        Appearance.Auto -> systemDark
        Appearance.Light -> false
        Appearance.Dark -> true
    }
}
