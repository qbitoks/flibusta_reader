package app.folio.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Interface roles inside the reader, derived from the page so every button, sheet and menu opened
 * from a book shares its tone (sepia page → sepia sheets). Steps mix the page towards its text,
 * so custom palettes get matching chrome without extra settings.
 */
private fun ReaderPalette.mix(light: Float, dark: Float): Color = lerp(background, text, if (isDark) dark else light)

/** Sheets and panels over the page (search, contents, position, notes). */
fun ReaderPalette.sheetColor(): Color = mix(0.045f, 0.12f)

/** Round buttons and cards: a step above the page, lighter on a light page. */
fun ReaderPalette.controlColor(): Color =
    if (isDark) mix(0f, 0.19f) else lerp(background, Color.White, 0.35f)

/** Capsules and menus raised over the page. */
fun ReaderPalette.capsuleColor(): Color = if (isDark) mix(0f, 0.24f) else lerp(background, Color.White, 0.3f)

fun ReaderPalette.selectedRowColor(): Color = mix(0.11f, 0.27f)

/** Labels on reader chrome: the page text pushed to full contrast, so «Тихая» controls stay legible. */
fun ReaderPalette.labelColor(): Color = lerp(text, if (isDark) Color.White else Color.Black, if (isDark) 0.8f else 0.4f)

/** [FolioColors] for the reader's subtree; accents and status colours stay the app's. */
fun ReaderPalette.uiColors(): FolioColors {
    val base = folioColors(isDark)
    val sheet = sheetColor()
    return base.copy(
        background = background,
        groupedBackground = sheet,
        surface = sheet,
        card = controlColor(),
        selectedRow = selectedRowColor(),
        label = labelColor(),
        secondaryLabel = secondary,
        tertiaryLabel = mix(0.32f, 0.38f),
        separator = mix(0.14f, 0.22f),
        fill = mix(0.07f, 0.17f),
        capsule = capsuleColor(),
        glass = sheet.copy(alpha = if (isDark) 0.70f else 0.72f),
        glassFallback = sheet.copy(alpha = 0.95f),
        bandTop = background,
        bandBottom = sheet,
    )
}
