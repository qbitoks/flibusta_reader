package net.flibustareader.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import net.flibustareader.core.layout.ReaderStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Apple Books' six themes; each has a light and a dark palette. */
enum class ReaderTheme { Original, Quiet, Paper, Bold, Calm, Focus }

/** «Как на устройстве / Светлое / Тёмное». */
enum class Appearance { Auto, Light, Dark }

/** «Скольжение / Загиб / Быстрое выцветание / Прокрутка». */
enum class PageTurn { Slide, Curl, Fade, Scroll }

/** «Затемнение фона» of the guide line: how far the text off the line fades into the page. */
enum class GuideDim(val fade: Float) { High(0.85f), Medium(0.75f), Low(0.65f), None(0f) }

enum class LibraryLayout { Grid, List }

enum class LibrarySort { Recent, Title, Author, Manual }

/** Built-in collections of the «Коллекции» sheet; the user can hide them in edit mode. */
enum class SystemCollection { WantToRead, Finished, Books }

data class ReaderSettings(
    /** Shared by all themes, changed with the A │ A buttons. */
    val fontSize: Float = ReaderStyle().fontSize,
    val theme: ReaderTheme = ReaderTheme.Original,
    val themeStyles: Map<ReaderTheme, ThemeStyle> = ReaderTheme.entries.associateWith { ThemeStyle.defaultFor(it) },
    val pageTurn: PageTurn = PageTurn.Slide,
    /** «Направляющая линия» (guide line) is on. */
    val guideLine: Boolean = false,
    val guideDim: GuideDim = GuideDim.Low,
    /** Colour of the last highlight; new highlights use it, as in Apple Books. */
    val highlightColor: String = "yellow",
) {
    val themeStyle: ThemeStyle get() = themeStyles.getValue(theme)
    val style: ReaderStyle get() = themeStyle.toReaderStyle(fontSize)
}

data class LibrarySettings(
    val layout: LibraryLayout = LibraryLayout.Grid,
    val sort: LibrarySort = LibrarySort.Recent,
    val watchedFolder: String? = null,
    val hiddenSystemCollections: Set<SystemCollection> = emptySet(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.dataStore

    private object Keys {
        val fontSize = floatPreferencesKey("font_size")
        val theme = stringPreferencesKey("theme")
        // "appearance" held the reader-wide appearance before the app-wide one; left unread (no migration, user decision).
        val appAppearance = stringPreferencesKey("app_appearance")
        val pageTurn = stringPreferencesKey("page_turn")
        val guideLine = booleanPreferencesKey("guide_line")
        val guideDim = stringPreferencesKey("guide_dim")
        val highlightColor = stringPreferencesKey("highlight_color")
        val libraryLayout = stringPreferencesKey("library_layout")
        val librarySort = stringPreferencesKey("library_sort")
        val watchedFolder = stringPreferencesKey("watched_folder")
        val hiddenSystem = stringSetPreferencesKey("hidden_system_collections")
        fun themeStyle(theme: ReaderTheme) = stringPreferencesKey("theme_style_${theme.name}")
        fun bookAppearance(bookId: String) = stringPreferencesKey("book_appearance_$bookId")
    }

    val reader: Flow<ReaderSettings> = store.data.map { p ->
        ReaderSettings(
            fontSize = p[Keys.fontSize] ?: ReaderStyle().fontSize,
            theme = enumOr(p[Keys.theme], ReaderTheme.Original),
            themeStyles = ReaderTheme.entries.associateWith { ThemeStyle.decode(p[Keys.themeStyle(it)], it) },
            // "None" was the old name of «Быстрое выцветание».
            pageTurn = if (p[Keys.pageTurn] == "None") PageTurn.Fade else enumOr(p[Keys.pageTurn], PageTurn.Slide),
            guideLine = p[Keys.guideLine] ?: false,
            guideDim = enumOr(p[Keys.guideDim], GuideDim.Low),
            highlightColor = p[Keys.highlightColor] ?: "yellow",
        )
    }.distinctUntilChanged()

    val library: Flow<LibrarySettings> = store.data.map { p ->
        LibrarySettings(
            layout = enumOr(p[Keys.libraryLayout], LibraryLayout.Grid),
            sort = enumOr(p[Keys.librarySort], LibrarySort.Recent),
            watchedFolder = p[Keys.watchedFolder],
            hiddenSystemCollections = p[Keys.hiddenSystem].orEmpty()
                .mapNotNull { runCatching { SystemCollection.valueOf(it) }.getOrNull() }.toSet(),
        )
    }.distinctUntilChanged()

    /** Appearance of the whole app; Auto follows the system. */
    val appAppearance: Flow<Appearance> = store.data.map { enumOr(it[Keys.appAppearance], Appearance.Auto) }.distinctUntilChanged()

    /** A book's own Light/Dark; null means it follows [appAppearance]. */
    fun bookAppearance(bookId: String): Flow<Appearance?> = store.data
        .map { p -> p[Keys.bookAppearance(bookId)]?.let { runCatching { Appearance.valueOf(it) }.getOrNull() }?.takeIf { it != Appearance.Auto } }
        .distinctUntilChanged()

    suspend fun currentReader(): ReaderSettings = reader.first()

    suspend fun setFontSize(size: Float) {
        store.edit { it[Keys.fontSize] = size }
    }

    suspend fun setThemeStyle(theme: ReaderTheme, style: ThemeStyle) {
        store.edit { it[Keys.themeStyle(theme)] = style.encode() }
    }

    suspend fun resetThemeStyle(theme: ReaderTheme) {
        store.edit { it.remove(Keys.themeStyle(theme)) }
    }

    suspend fun setTheme(theme: ReaderTheme) {
        store.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setAppAppearance(value: Appearance) {
        store.edit { it[Keys.appAppearance] = value.name }
    }

    /** null (or Auto) makes the book follow the app again. */
    suspend fun setBookAppearance(bookId: String, value: Appearance?) {
        store.edit {
            if (value == null || value == Appearance.Auto) it.remove(Keys.bookAppearance(bookId))
            else it[Keys.bookAppearance(bookId)] = value.name
        }
    }

    suspend fun setPageTurn(value: PageTurn) {
        store.edit { it[Keys.pageTurn] = value.name }
    }

    suspend fun setGuideLine(on: Boolean) {
        store.edit { it[Keys.guideLine] = on }
    }

    suspend fun setGuideDim(value: GuideDim) {
        store.edit { it[Keys.guideDim] = value.name }
    }

    suspend fun setHighlightColor(value: String) {
        store.edit { it[Keys.highlightColor] = value }
    }

    suspend fun setLibraryLayout(value: LibraryLayout) {
        store.edit { it[Keys.libraryLayout] = value.name }
    }

    suspend fun setLibrarySort(value: LibrarySort) {
        store.edit { it[Keys.librarySort] = value.name }
    }

    suspend fun setWatchedFolder(uri: String?) {
        store.edit { if (uri == null) it.remove(Keys.watchedFolder) else it[Keys.watchedFolder] = uri }
    }

    suspend fun setSystemCollectionHidden(collection: SystemCollection, hidden: Boolean) {
        store.edit {
            val current = it[Keys.hiddenSystem].orEmpty()
            it[Keys.hiddenSystem] = if (hidden) current + collection.name else current - collection.name
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
