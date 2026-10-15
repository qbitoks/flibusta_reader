package app.folio.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Semantic colours measured from Apple Books screenshots (docs/design/apple-books-guidelines.md § 2.3).
 * The interface is almost monochrome: selection is shown with the label colour, not an accent.
 */
@Immutable
data class FolioColors(
    val background: Color,
    val groupedBackground: Color,
    val surface: Color,
    /** Card inside a sheet or grouped list. */
    val card: Color,
    /** Selected row inside a sheet (current TOC chapter). */
    val selectedRow: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    /** Filled capsule button on plain background ("Продолжить чтение"). */
    val capsule: Color,
    /** Tint over blurred content for glass surfaces, and the solid colour used where blur is unavailable. */
    val glass: Color,
    val glassFallback: Color,
    /** Top and bottom of a Home section band gradient. */
    val bandTop: Color,
    val bandBottom: Color,
    val accent: Color,
    val destructive: Color,
    val bookmark: Color,
    val switchOn: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val isDark: Boolean,
)

private val Light = FolioColors(
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F1F7),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    selectedRow = Color(0xFFE5E5EA),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF8A8A8E),
    tertiaryLabel = Color(0xFFB6B6B8),
    separator = Color(0xFFDCDCDE),
    fill = Color(0xFFEDEDEF),
    capsule = Color(0xFFE5E5E7),
    glass = Color(0xB8F7F7F7),
    glassFallback = Color(0xF2F7F7F7),
    bandTop = Color(0xFFFFFFFF),
    bandBottom = Color(0xFFF1F1F1),
    accent = Color(0xFF2E6A4F),
    destructive = Color(0xFFFF3B30),
    bookmark = Color(0xFFFF3B30),
    switchOn = Color(0xFF34C759),
    badgeBackground = Color(0xFFDDEBFB),
    badgeText = Color(0xFF1C416E),
    isDark = false,
)

private val Dark = FolioColors(
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF1C1C1E),
    surface = Color(0xFF1C1C1E),
    card = Color(0xFF2C2C2E),
    selectedRow = Color(0xFF414143),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFFA5A5A7),
    tertiaryLabel = Color(0xFF5A5A5E),
    separator = Color(0xFF38383A),
    fill = Color(0xFF2C2C2E),
    capsule = Color(0xFF343434),
    glass = Color(0xB31C1C1E),
    glassFallback = Color(0xF21C1C1E),
    bandTop = Color(0xFF1E1E1E),
    bandBottom = Color(0xFF010101),
    accent = Color(0xFF5FB38A),
    destructive = Color(0xFFFF453A),
    bookmark = Color(0xFFFF3B30),
    switchOn = Color(0xFF30D158),
    badgeBackground = Color(0xFF1C416E),
    badgeText = Color(0xFFD8F5FF),
    isDark = true,
)

/** Serif for screen and section titles (the role New York plays in Apple Books). */
val SerifDisplay = FontFamily(
    Font(R.font.source_serif_regular_400, FontWeight.Normal),
    Font(R.font.source_serif_regular_700, FontWeight.SemiBold),
    Font(R.font.source_serif_regular_700, FontWeight.Bold),
    Font(R.font.source_serif_italic_400, FontWeight.Normal, FontStyle.Italic),
)

/** Interface sans (the role SF Pro plays). */
val UiSans = FontFamily(
    Font(R.font.inter_regular_400, FontWeight.Normal),
    Font(R.font.inter_regular_400, FontWeight.Medium),
    Font(R.font.inter_regular_700, FontWeight.SemiBold),
    Font(R.font.inter_regular_700, FontWeight.Bold),
    Font(R.font.inter_italic_400, FontWeight.Normal, FontStyle.Italic),
)

/** Type scale from guideline § 2.2. */
object FolioType {
    private fun serif(size: Int, line: Int, weight: FontWeight = FontWeight.Bold) =
        TextStyle(fontFamily = SerifDisplay, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = UiSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    /** «Главная», «Библиотека». */
    val largeTitle = serif(34, 41)
    /** «Цели по чтению». */
    val title1 = serif(26, 32)
    /** Section headers: «Продолжить», «Хочу прочесть ›». */
    val section = serif(24, 29)
    /** Large sheet title: «Коллекции»; inline screen title «Предыдущие». */
    val sheetTitle = serif(22, 28)
    /** Book title in a collection list. */
    val bookTitle = serif(20, 25, FontWeight.SemiBold)
    /** Panel title: «Темы и настройки». */
    val title2 = sans(22, 28, FontWeight.Bold)
    /** Settings group header: «Текст». */
    val title3 = sans(20, 25, FontWeight.Bold)
    val headline = sans(17, 22, FontWeight.SemiBold)
    val body = sans(17, 22)
    val callout = sans(16, 21)
    val subheadline = sans(15, 20)
    val footnote = sans(13, 18)
    val caption = sans(12, 16)
    val tab = sans(11, 13, FontWeight.Medium)
    /** Uppercase captions above sliders. */
    val captionCaps = sans(15, 20).copy(letterSpacing = 0.2.sp)
    val badge = sans(13, 16, FontWeight.SemiBold).copy(letterSpacing = 0.6.sp)
}

val LocalFolioColors = staticCompositionLocalOf { Light }

/** The app's own light or dark colours. */
fun folioColors(dark: Boolean): FolioColors = if (dark) Dark else Light

object Folio {
    val colors: FolioColors @Composable get() = LocalFolioColors.current
}

@Composable
fun FolioTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    FolioTheme(folioColors(dark), content)

/** Theme with explicit [c], e.g. the reader's colours derived from the page ([uiColors]). */
@Composable
fun FolioTheme(c: FolioColors, content: @Composable () -> Unit) {
    val dark = c.isDark
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.label, onPrimary = c.background,
            background = c.background, onBackground = c.label,
            surface = c.surface, onSurface = c.label,
            surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = c.surface, surfaceContainerHigh = c.card, surfaceContainerLow = c.surface,
            outline = c.separator, outlineVariant = c.separator, error = c.destructive,
        )
    } else {
        lightColorScheme(
            primary = c.label, onPrimary = c.background,
            background = c.background, onBackground = c.label,
            surface = c.surface, onSurface = c.label,
            surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = c.groupedBackground, surfaceContainerHigh = c.card, surfaceContainerLow = c.surface,
            outline = c.separator, outlineVariant = c.separator, error = c.destructive,
        )
    }
    val typography = Typography(
        displaySmall = FolioType.largeTitle,
        headlineMedium = FolioType.title1,
        headlineSmall = FolioType.title2,
        titleLarge = FolioType.title3,
        titleMedium = FolioType.headline,
        bodyLarge = FolioType.body,
        bodyMedium = FolioType.subheadline,
        bodySmall = FolioType.footnote,
        labelLarge = FolioType.callout,
        labelMedium = FolioType.footnote,
        labelSmall = FolioType.caption,
    )
    CompositionLocalProvider(LocalFolioColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
