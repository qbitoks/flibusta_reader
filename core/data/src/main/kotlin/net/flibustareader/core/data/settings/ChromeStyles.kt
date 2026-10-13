package net.flibustareader.core.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/** Visual treatment of a reader line, independent of the information it contains. */
enum class ChromeStyle { Quiet, Classic, Book, Clear }

data class ChromeStyles(
    val header: ChromeStyle = ChromeStyle.Quiet,
    val footer: ChromeStyle = ChromeStyle.Quiet,
)

data class BookChromeStyles(
    /** null follows the current reader default for this line. */
    val header: ChromeStyle? = null,
    val footer: ChromeStyle? = null,
)

fun resolveChromeStyles(defaults: ChromeStyles, overrides: BookChromeStyles): ChromeStyles = ChromeStyles(
    header = overrides.header ?: defaults.header,
    footer = overrides.footer ?: defaults.footer,
)

internal object ChromeStylePreferences {
    private val header = stringPreferencesKey("header_chrome_style")
    private val footer = stringPreferencesKey("footer_chrome_style")
    private fun bookHeader(bookId: String) = stringPreferencesKey("book_header_chrome_style_$bookId")
    private fun bookFooter(bookId: String) = stringPreferencesKey("book_footer_chrome_style_$bookId")

    fun readDefaults(preferences: Preferences): ChromeStyles = ChromeStyles(
        header = decode(preferences[header]) ?: ChromeStyle.Quiet,
        footer = decode(preferences[footer]) ?: ChromeStyle.Quiet,
    )

    fun readBook(preferences: Preferences, bookId: String): BookChromeStyles = BookChromeStyles(
        header = decode(preferences[bookHeader(bookId)]),
        footer = decode(preferences[bookFooter(bookId)]),
    )

    fun writeDefaults(preferences: MutablePreferences, value: ChromeStyles) {
        preferences[header] = value.header.name
        preferences[footer] = value.footer.name
    }

    fun writeBook(preferences: MutablePreferences, bookId: String, value: BookChromeStyles) {
        writeOverride(preferences, bookHeader(bookId), value.header)
        writeOverride(preferences, bookFooter(bookId), value.footer)
    }

    private fun writeOverride(preferences: MutablePreferences, key: Preferences.Key<String>, value: ChromeStyle?) {
        if (value == null) preferences.remove(key) else preferences[key] = value.name
    }

    private fun decode(value: String?): ChromeStyle? =
        value?.let { runCatching { ChromeStyle.valueOf(it) }.getOrNull() }
}
