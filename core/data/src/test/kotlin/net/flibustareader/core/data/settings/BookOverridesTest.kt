package net.flibustareader.core.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookOverridesTest {
    private val shared = ReaderSettings()

    @Test fun keepsOnlyFieldsThatDifferFromShared() {
        val edited = shared.copy(theme = ReaderTheme.Paper, fontSize = shared.fontSize + 2, pageTurn = PageTurn.Slide)
        val own = BookOverrides.between(shared, edited)

        assertEquals(setOf(BookOverrides.THEME, BookOverrides.FONT_SIZE), own.values.keys)
        assertEquals(edited, own.applyTo(shared))
        assertTrue(BookOverrides.between(shared, shared).isEmpty)
    }

    @Test fun laterSharedChangesReachUntouchedFields() {
        val own = BookOverrides.between(shared, shared.copy(theme = ReaderTheme.Calm))
        val newShared = shared.copy(fontSize = 30f, pageTurn = PageTurn.Scroll)

        val book = own.applyTo(newShared)
        assertEquals(ReaderTheme.Calm, book.theme)
        assertEquals(30f, book.fontSize)
        assertEquals(PageTurn.Scroll, book.pageTurn)
    }

    @Test fun bookTypographyAppliesToTheThemeInEffect() {
        val edited = shared.copy(theme = ReaderTheme.Focus).let { it.withThemeStyle(it.themeStyle.copy(font = "lora", boldText = true)) }
        val own = BookOverrides.between(shared, edited)

        assertEquals(setOf(BookOverrides.THEME, BookOverrides.FONT, BookOverrides.BOLD), own.values.keys)
        val book = own.applyTo(shared)
        assertEquals("lora", book.themeStyle.font)
        assertEquals(ThemeStyle.defaultFor(ReaderTheme.Original), book.themeStyles.getValue(ReaderTheme.Original))
    }

    @Test fun encodingRoundTripsAndIgnoresUnknownFields() {
        val own = BookOverrides.between(shared, shared.copy(
            appearance = Appearance.Dark,
            headerLayout = LineLayout.HEADER.copy(customize = true, sizePct = 120, align = LineAlign.Start),
            footer = shared.footer.copy(page = FooterPage.Percent),
        ))
        assertEquals(own, BookOverrides.decode(own.encode()))

        val withFuture = BookOverrides.decode(own.encode() + "\nfuture_field=1")
        assertEquals(own.applyTo(shared), withFuture.applyTo(shared))
    }

    @Test fun oldBookKeysAreReadAndRemovedOnWrite() {
        val p = mutablePreferencesOf(
            stringPreferencesKey("book_appearance_a") to "Dark",
            stringPreferencesKey("book_header_chrome_style_a") to "Classic",
        )
        val own = ReaderPreferences.readBook(p, "a")
        assertEquals(Appearance.Dark, own.applyTo(shared).appearance)
        assertEquals(ChromeStyle.Classic, own.applyTo(shared).chromeStyles.header)

        ReaderPreferences.writeBook(p, "a", own)
        assertNull(p[stringPreferencesKey("book_appearance_a")])
        assertNull(p[stringPreferencesKey("book_header_chrome_style_a")])
        assertEquals(own, ReaderPreferences.readBook(p, "a"))

        ReaderPreferences.writeBook(p, "a", BookOverrides())
        assertNull(p[stringPreferencesKey("book_reader_a")])
    }

    @Test fun changedFooterContentStartsCustomisedSoNothingMoves() {
        val p = mutablePreferencesOf(booleanPreferencesKey("footer_battery") to true)
        val settings = ReaderPreferences.read(p)
        assertTrue(settings.footerLayout.customize)
        assertTrue(settings.shownFooter.battery)

        assertFalse(ReaderPreferences.read(mutablePreferencesOf()).footerLayout.customize)
    }

    @Test fun sharedSettingsRoundTrip() {
        val p = mutablePreferencesOf()
        val settings = shared.copy(
            theme = ReaderTheme.Bold, appearance = Appearance.Light, header = PageHeader.Chapter,
            footerLayout = LineLayout.FOOTER.copy(customize = true, gap = -6, rule = true),
        ).let { it.withThemeStyle(it.themeStyle.copy(customize = true, marginsPct = 5)) }
        ReaderPreferences.write(p, settings)
        assertEquals(settings, ReaderPreferences.read(p))
    }

    @Test fun defaultsAndNoneDropsOnlyThePageNumber() {
        assertEquals(PageHeader.Chapter, shared.header)
        assertEquals(PageFooter(page = FooterPage.Number), shared.shownFooter)
        assertFalse(shared.shownFooter.clock || shared.shownFooter.battery)
        val noNumber = shared.copy(footer = PageFooter(page = FooterPage.None))
        assertTrue(noNumber.shownFooter.isEmpty)
        val clockOnly = noNumber.copy(footer = PageFooter(page = FooterPage.None, clock = true),
            footerLayout = noNumber.footerLayout.copy(customize = true))
        assertTrue(clockOnly.shownFooter.clock && !clockOnly.shownFooter.isEmpty)
    }
}
