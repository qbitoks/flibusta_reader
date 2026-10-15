package app.folio.core.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.folio.core.layout.ReaderStyle

/** Codecs of the shared reader settings and of a book's own ones, inside one DataStore edit. */
internal object ReaderPreferences {
    private val fontSize = floatPreferencesKey("font_size")
    private val theme = stringPreferencesKey("theme")
    private val appearance = stringPreferencesKey("reader_appearance")
    private val pageTurn = stringPreferencesKey("page_turn")
    private val guideLine = booleanPreferencesKey("guide_line")
    private val guideDim = stringPreferencesKey("guide_dim")
    private val highlightColor = stringPreferencesKey("highlight_color")
    private val pageHeader = stringPreferencesKey("page_header")
    private val footerPage = stringPreferencesKey("footer_page")
    private val footerPagesLeft = booleanPreferencesKey("footer_pages_left")
    private val footerClock = booleanPreferencesKey("footer_clock")
    private val footerBattery = booleanPreferencesKey("footer_battery")
    private val footerUnder = stringPreferencesKey("footer_under")
    private val headerLayout = stringPreferencesKey("header_layout")
    private val footerLayout = stringPreferencesKey("footer_layout")
    private val headerUnder = stringPreferencesKey("header_under")
    private val customColors = stringPreferencesKey("custom_colors")
    private val readingText = stringPreferencesKey("reading_text")
    private fun book(bookId: String) = stringPreferencesKey("book_reader_$bookId")

    fun read(p: Preferences): ReaderSettings {
        val palette = enumOr(p[theme], ReaderTheme.Original)
        val footer = PageFooter().let { d ->
            PageFooter(
                page = enumOr(p[footerPage], d.page),
                pagesLeft = p[footerPagesLeft] ?: d.pagesLeft,
                clock = p[footerClock] ?: d.clock,
                battery = p[footerBattery] ?: d.battery,
                style = enumOr(p[footerUnder], d.style),
            )
        }
        return ReaderSettings(
            fontSize = p[fontSize] ?: ReaderStyle().fontSize,
            theme = palette,
            appearance = enumOr(p[appearance], Appearance.Auto),
            customColors = CustomColors.decode(p[customColors])?.copy(base = CustomColors.of(palette).base),
            readingText = TextSet.decode(p[readingText], TextSet.DEFAULT),
            pageTurn = enumOr(p[pageTurn], PageTurn.Slide),
            guideLine = p[guideLine] ?: false,
            guideDim = enumOr(p[guideDim], GuideDim.Low),
            highlightColor = p[highlightColor] ?: "yellow",
            header = enumOr(p[pageHeader], PageHeader.Chapter),
            footer = footer,
            chromeStyles = ChromeStylePreferences.readDefaults(p),
            headerLayout = LineLayout.decode(p[headerLayout], LineLayout.HEADER),
            footerLayout = LineLayout.decode(p[footerLayout], LineLayout.FOOTER),
            headerUnder = enumOr(p[headerUnder], FooterStyle.None),
        )
    }

    fun write(p: MutablePreferences, s: ReaderSettings) {
        p[fontSize] = s.fontSize
        p[theme] = s.theme.name
        p[appearance] = s.appearance.name
        p[readingText] = s.textSet.encode()
        s.customColors?.let { p[customColors] = it.encode() } ?: p.remove(customColors)
        p[pageTurn] = s.pageTurn.name
        p[guideLine] = s.guideLine
        p[guideDim] = s.guideDim.name
        p[highlightColor] = s.highlightColor
        p[pageHeader] = s.header.name
        p[footerPage] = s.footer.page.name
        p[footerPagesLeft] = s.footer.pagesLeft
        p[footerClock] = s.footer.clock
        p[footerBattery] = s.footer.battery
        p[footerUnder] = s.footer.style.name
        ChromeStylePreferences.writeDefaults(p, s.chromeStyles)
        p[headerLayout] = s.headerLayout.encode()
        p[footerLayout] = s.footerLayout.encode()
        p[headerUnder] = s.headerUnder.name
    }

    fun readBook(p: Preferences, bookId: String): BookOverrides = BookOverrides.decode(p[book(bookId)])

    fun writeBook(p: MutablePreferences, bookId: String, value: BookOverrides) {
        if (value.isEmpty) p.remove(book(bookId)) else p[book(bookId)] = value.encode()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
