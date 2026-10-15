package app.folio.core.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.*
import org.junit.Test

class ReaderThemeSettingsTest {
    @Test fun changingPalettePreservesTypographyAndColourAdjustments() {
        val start = ReaderSettings(fontSize = 27f).let {
            it.withText(it.textSet.copy(font = "inter", boldText = true, lineSpacing = 1.8f))
        }.withColors(CustomColors(ReaderTheme.Calm, 3, -2))
        for (theme in listOf(ReaderTheme.Original, ReaderTheme.Focus, ReaderTheme.Calm, ReaderTheme.Paper, ReaderTheme.Quiet)) {
            val changed = start.withTheme(theme)
            assertEquals(start.style, changed.style)
            assertEquals(CustomColors(theme, 3, -2), changed.customColors)
        }
    }

    @Test fun freshInstallationUsesOneNeutralTextStyleForEveryPalette() {
        val settings = ReaderPreferences.read(mutablePreferencesOf())
        assertEquals(19f, settings.fontSize, 0f)
        assertEquals("literata", settings.style.font)
        assertEquals(1.2f, settings.style.lineSpacing, 0f)
        assertEquals(5f, settings.style.marginPercent, 0f)
        assertEquals(1.5f, settings.style.firstLineIndent, 0f)
        assertTrue(settings.style.justify)
        for (theme in ReaderTheme.entries) assertEquals(settings.style, settings.withTheme(theme).style)
    }

    @Test fun obsoleteTypographyIsIgnoredAndSharedEditsDoNotRewriteBooks() {
        val p = mutablePreferencesOf(
            stringPreferencesKey("theme") to "Calm",
            stringPreferencesKey("text_preset") to "Custom",
            stringPreferencesKey("custom_text") to "obsolete",
            stringPreferencesKey("book_reader_a") to "theme=Paper\nfont_size=25.0",
        )
        assertEquals(TextSet.DEFAULT, ReaderPreferences.read(p).textSet)
        val before = p[stringPreferencesKey("book_reader_a")]
        ReaderPreferences.write(p, ReaderPreferences.read(p).copy(fontSize = 29f))
        assertEquals(before, p[stringPreferencesKey("book_reader_a")])
    }

    @Test fun advancedResetPreservesBasicsAndSurvivesBookReopen() {
        val shared = ReaderSettings(fontSize = 32f, theme = ReaderTheme.Calm, pageTurn = PageTurn.Scroll,
            appearance = Appearance.Dark, guideLine = true, guideDim = GuideDim.High, highlightColor = "green",
            header = PageHeader.None, footer = PageFooter(page = FooterPage.Percent, clock = true),
            footerLayout = LineLayout.FOOTER.copy(customize = true, rule = true))
            .let { it.withText(it.textSet.copy(font = "inter", boldText = true)) }
            .withColors(CustomColors(ReaderTheme.Calm, 4, -3))
        val reset = shared.withAdvancedDefaults()
        assertEquals(shared.theme, reset.theme)
        assertEquals(shared.fontSize, reset.fontSize, 0f)
        assertEquals(shared.appearance, reset.appearance)
        assertEquals(shared.guideLine, reset.guideLine)
        assertEquals(shared.guideDim, reset.guideDim)
        assertEquals(shared.highlightColor, reset.highlightColor)
        assertEquals(TextSet.DEFAULT, reset.textSet)
        assertNull(reset.customColors)
        assertEquals(shared.pageTurn, reset.pageTurn)
        assertEquals(PageHeader.Chapter, reset.header)
        assertEquals(PageFooter(), reset.footer)
        assertEquals(LineLayout.FOOTER, reset.footerLayout)
        val p = mutablePreferencesOf()
        ReaderPreferences.write(p, shared)
        ReaderPreferences.writeBook(p, "a", BookOverrides.between(shared, reset))
        assertEquals(reset, ReaderPreferences.readBook(p, "a").applyTo(ReaderPreferences.read(p)))
        assertEquals(shared, ReaderPreferences.read(p))
        assertEquals(shared, ReaderPreferences.readBook(p, "b").applyTo(ReaderPreferences.read(p)))
    }

    @Test fun inheritedColourAdjustmentsCannotReplaceBooksSelectedPaletteOnSave() {
        val shared = ReaderSettings().withColors(CustomColors(ReaderTheme.Calm, 2, -1))
        val own = BookOverrides(mapOf(BookOverrides.THEME to ReaderTheme.Focus.name))
        val effective = own.applyTo(shared)
        val p = mutablePreferencesOf()
        ReaderPreferences.write(p, effective)
        val reopened = ReaderPreferences.read(p)
        assertEquals(ReaderTheme.Focus, reopened.theme)
        assertEquals(CustomColors(ReaderTheme.Focus, 2, -1), reopened.customColors)
    }

    @Test fun paletteOnlyBookEditKeepsInheritingSharedColourAdjustments() {
        val shared = ReaderSettings().withColors(CustomColors(ReaderTheme.Calm, 2, -1))
        val own = BookOverrides.between(shared, shared.withTheme(ReaderTheme.Focus))
        assertEquals(setOf(BookOverrides.THEME), own.values.keys)
        val changed = shared.withColors(CustomColors(ReaderTheme.Calm, 5, 3))
        val reopened = BookOverrides.decode(own.encode()).applyTo(changed)
        assertEquals(ReaderTheme.Focus, reopened.theme)
        val colors = requireNotNull(reopened.customColors)
        assertEquals(5, colors.warmth)
        assertEquals(3, colors.contrast)
    }

    @Test fun bookToneEditKeepsInheritingSharedContrastAndCanResetToNeutral() {
        val shared = ReaderSettings().withColors(CustomColors(ReaderTheme.Paper, 2, -1))
        val edited = shared.withColors(CustomColors(ReaderTheme.Paper, 4, -1))
        val own = BookOverrides.between(shared, edited)
        val changed = shared.withColors(CustomColors(ReaderTheme.Paper, 2, 3))
        val reopened = BookOverrides.decode(own.encode()).applyTo(changed)
        val colors = requireNotNull(reopened.customColors)
        assertEquals(4, colors.warmth)
        assertEquals(3, colors.contrast)
        val reset = reopened.withAdvancedDefaults()
        assertNull(reset.customColors)
        val restored = BookOverrides.decode(BookOverrides.between(changed, reset).encode()).applyTo(changed)
        assertEquals(reset, restored)
    }
}
