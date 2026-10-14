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
    // "footer_style" held Fade/Strip when fading was the default; only its Strip carries over to "footer_under",
    // where the removed «Полоска» (Strip) reads as «Обрезка».
    private val oldFooterStyle = stringPreferencesKey("footer_style")
    private val footerUnder = stringPreferencesKey("footer_under")
    private val headerLayout = stringPreferencesKey("header_layout")
    private val footerLayout = stringPreferencesKey("footer_layout")
    private val headerUnder = stringPreferencesKey("header_under")
    private fun themeStyle(theme: ReaderTheme) = stringPreferencesKey("theme_style_${theme.name}")
    private fun book(bookId: String) = stringPreferencesKey("book_reader_$bookId")
    // Before per-book settings a book kept only its Light/Dark; read once, then folded into book_reader_<id>.
    private fun oldBookAppearance(bookId: String) = stringPreferencesKey("book_appearance_$bookId")

    fun read(p: Preferences): ReaderSettings {
        val footer = PageFooter().let { d ->
            PageFooter(
                page = enumOr(p[footerPage], d.page),
                pagesLeft = p[footerPagesLeft] ?: d.pagesLeft,
                clock = p[footerClock] ?: d.clock,
                battery = p[footerBattery] ?: d.battery,
                style = enumOr(withoutStrip(p[footerUnder] ?: p[oldFooterStyle]?.takeIf { it == OLD_STRIP }), d.style),
            )
        }
        return ReaderSettings(
            fontSize = p[fontSize] ?: ReaderStyle().fontSize,
            theme = enumOr(p[theme], ReaderTheme.Original),
            appearance = enumOr(p[appearance], Appearance.Auto),
            themeStyles = ReaderTheme.entries.associateWith { ThemeStyle.decode(p[themeStyle(it)], it) },
            // "None" was the old name of «Быстрое выцветание».
            pageTurn = if (p[pageTurn] == "None") PageTurn.Fade else enumOr(p[pageTurn], PageTurn.Slide),
            guideLine = p[guideLine] ?: false,
            guideDim = enumOr(p[guideDim], GuideDim.Low),
            highlightColor = p[highlightColor] ?: "yellow",
            header = enumOr(p[pageHeader], PageHeader.Chapter),
            footer = footer,
            chromeStyles = ChromeStylePreferences.readDefaults(p),
            headerLayout = LineLayout.decode(p[headerLayout], LineLayout.HEADER),
            // Footer content used to be plain settings: keep a changed footer as it was by starting customised.
            footerLayout = LineLayout.decode(p[footerLayout], LineLayout.FOOTER.copy(customize = !footer.hasDefaultContent)),
            headerUnder = enumOr(p[headerUnder], FooterStyle.None),
        )
    }

    fun write(p: MutablePreferences, s: ReaderSettings) {
        p[fontSize] = s.fontSize
        p[theme] = s.theme.name
        p[appearance] = s.appearance.name
        s.themeStyles.forEach { (t, style) ->
            if (style == ThemeStyle.defaultFor(t)) p.remove(themeStyle(t)) else p[themeStyle(t)] = style.encode()
        }
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
        p.remove(oldFooterStyle)
        ChromeStylePreferences.writeDefaults(p, s.chromeStyles)
        p[headerLayout] = s.headerLayout.encode()
        p[footerLayout] = s.footerLayout.encode()
        p[headerUnder] = s.headerUnder.name
    }

    fun readBook(p: Preferences, bookId: String): BookOverrides {
        val own = BookOverrides.decode(p[book(bookId)]).values.toMutableMap()
        val oldStyles = ChromeStylePreferences.readBook(p, bookId)
        p[oldBookAppearance(bookId)]?.takeIf { it == Appearance.Light.name || it == Appearance.Dark.name }
            ?.let { own.putIfAbsent(BookOverrides.APPEARANCE, it) }
        oldStyles.header?.let { own.putIfAbsent(BookOverrides.HEADER_STYLE, it.name) }
        oldStyles.footer?.let { own.putIfAbsent(BookOverrides.FOOTER_STYLE, it.name) }
        // "footer_scroll" held Fade/Strip when fading was the default; only Strip stays the book's own.
        own.remove(OLD_FOOTER_SCROLL)?.takeIf { it == OLD_STRIP }?.let { own.putIfAbsent(BookOverrides.FOOTER_UNDER, it) }
        if (own[BookOverrides.FOOTER_UNDER] == OLD_STRIP) own[BookOverrides.FOOTER_UNDER] = FooterStyle.Clip.name
        return BookOverrides(own)
    }

    /** Writes the book's own values; an empty set removes the key, as do the keys it replaced. */
    fun writeBook(p: MutablePreferences, bookId: String, value: BookOverrides) {
        if (value.isEmpty) p.remove(book(bookId)) else p[book(bookId)] = value.encode()
        p.remove(oldBookAppearance(bookId))
        ChromeStylePreferences.writeBook(p, bookId, BookChromeStyles())
    }

    private const val OLD_FOOTER_SCROLL = "footer_scroll"
    private const val OLD_STRIP = "Strip"

    /** «Полоска» was removed; it was closest to «Обрезка» at the line's rule. */
    private fun withoutStrip(name: String?): String? = if (name == OLD_STRIP) FooterStyle.Clip.name else name

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
