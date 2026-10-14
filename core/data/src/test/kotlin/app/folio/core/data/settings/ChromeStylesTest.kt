package app.folio.core.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChromeStylesTest {
    @Test fun bookHeaderAndFooterInheritIndependently() {
        val defaults = ChromeStyles(header = ChromeStyle.Classic, footer = ChromeStyle.Book)

        assertEquals(defaults, resolveChromeStyles(defaults, BookChromeStyles()))
        assertEquals(
            ChromeStyles(header = ChromeStyle.Clear, footer = ChromeStyle.Book),
            resolveChromeStyles(defaults, BookChromeStyles(header = ChromeStyle.Clear)),
        )
        assertEquals(
            ChromeStyles(header = ChromeStyle.Classic, footer = ChromeStyle.Quiet),
            resolveChromeStyles(defaults, BookChromeStyles(footer = ChromeStyle.Quiet)),
        )
    }

    @Test fun missingOrUnknownDefaultStyleFallsBackToQuietIndependently() {
        val preferences = mutablePreferencesOf()
        assertEquals(ChromeStyles(), ChromeStylePreferences.readDefaults(preferences))

        preferences[stringPreferencesKey("header_chrome_style")] = "future-style"
        preferences[stringPreferencesKey("footer_chrome_style")] = "Classic"
        assertEquals(
            ChromeStyles(header = ChromeStyle.Quiet, footer = ChromeStyle.Classic),
            ChromeStylePreferences.readDefaults(preferences),
        )
    }

    @Test fun missingOrUnknownBookStyleInheritsItsCurrentDefault() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("book_header_chrome_style_a") to "future-style",
            stringPreferencesKey("book_footer_chrome_style_a") to "Clear",
        )
        val overrides = ChromeStylePreferences.readBook(preferences, "a")

        assertEquals(BookChromeStyles(footer = ChromeStyle.Clear), overrides)
        assertEquals(BookChromeStyles(), ChromeStylePreferences.readBook(preferences, "other"))
        assertEquals(
            ChromeStyles(header = ChromeStyle.Book, footer = ChromeStyle.Clear),
            resolveChromeStyles(ChromeStyles(header = ChromeStyle.Book), overrides),
        )
    }

    @Test fun defaultsRoundTripWithoutReplacingContentOrBookPreferences() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("footer_style") to "Strip",
            stringPreferencesKey("page_header") to "Chapter",
            stringPreferencesKey("book_appearance_a") to "Dark",
            stringPreferencesKey("book_footer_chrome_style_a") to "Book",
        )
        val styles = ChromeStyles(header = ChromeStyle.Classic, footer = ChromeStyle.Clear)

        ChromeStylePreferences.writeDefaults(preferences, styles)

        assertEquals(styles, ChromeStylePreferences.readDefaults(preferences))
        assertEquals("Strip", preferences[stringPreferencesKey("footer_style")])
        assertEquals("Chapter", preferences[stringPreferencesKey("page_header")])
        assertEquals("Dark", preferences[stringPreferencesKey("book_appearance_a")])
        assertEquals(BookChromeStyles(footer = ChromeStyle.Book), ChromeStylePreferences.readBook(preferences, "a"))
    }

    @Test fun resettingOneBookStyleRemovesOnlyThatOverride() {
        val preferences = mutablePreferencesOf()
        val styles = BookChromeStyles(header = ChromeStyle.Quiet, footer = ChromeStyle.Classic)
        ChromeStylePreferences.writeBook(preferences, "a", styles)
        ChromeStylePreferences.writeBook(preferences, "b", BookChromeStyles(header = ChromeStyle.Clear))
        assertEquals(styles, ChromeStylePreferences.readBook(preferences, "a"))

        ChromeStylePreferences.writeBook(preferences, "a", styles.copy(header = null))

        assertNull(preferences[stringPreferencesKey("book_header_chrome_style_a")])
        assertEquals(BookChromeStyles(footer = ChromeStyle.Classic), ChromeStylePreferences.readBook(preferences, "a"))
        assertEquals(BookChromeStyles(header = ChromeStyle.Clear), ChromeStylePreferences.readBook(preferences, "b"))

        ChromeStylePreferences.writeBook(preferences, "a", BookChromeStyles())

        assertNull(preferences[stringPreferencesKey("book_footer_chrome_style_a")])
        assertEquals(BookChromeStyles(), ChromeStylePreferences.readBook(preferences, "a"))
    }
}
