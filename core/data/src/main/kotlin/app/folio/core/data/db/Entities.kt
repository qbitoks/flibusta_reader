package app.folio.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books", indices = [Index("lastOpenedAt"), Index("sourceUri")])
data class BookEntity(
    /** SHA-1 of the file content: the same book imported twice is recognised. */
    @PrimaryKey val id: String,
    val title: String,
    /** Authors joined by newline. */
    val authors: String,
    val series: String?,
    val seriesIndex: Int?,
    val language: String?,
    val format: String,
    val fileSize: Long,
    /** Content URI of the original in a watched folder, if it was found there. */
    val sourceUri: String?,
    val hasCover: Boolean,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    /** Encoded [app.folio.core.model.Position]. */
    val position: String? = null,
    val progress: Float = 0f,
    val finishedAt: Long? = null,
    val wantToRead: Boolean = false,
    /** Title set with «Переименовать…»; the parsed [title] is kept for reset and search. */
    @ColumnInfo(defaultValue = "NULL") val customTitle: String? = null,
    /** Position in the library under manual sort; smaller first. */
    @ColumnInfo(defaultValue = "0") val manualOrder: Int = 0,
    /** Two ARGB colours "top,bottom" for the «Продолжить» card gradient, taken from the cover. */
    @ColumnInfo(defaultValue = "NULL") val coverColors: String? = null,
    /** «Удалить из списка „Продолжить“»: the book moves to «Предыдущие». */
    @ColumnInfo(defaultValue = "0") val hiddenFromContinue: Boolean = false,
) {
    val displayTitle: String get() = customTitle ?: title
    val authorList: List<String> get() = authors.split('\n').filter { it.isNotBlank() }
    val isFinished: Boolean get() = finishedAt != null
    val isStarted: Boolean get() = lastOpenedAt != null
}

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)

@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    foreignKeys = [
        ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class CollectionBookEntity(
    val collectionId: Long,
    val bookId: String,
    val addedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val position: String,
    val chapterTitle: String?,
    val snippet: String,
    val progress: Float,
    val createdAt: Long,
)

/**
 * A highlighted passage («Выделить»), optionally with a note. [start]/[end] are encoded
 * [app.folio.core.model.Position]s, end exclusive; [text] is the passage as selected.
 */
@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val start: String,
    val end: String,
    val text: String,
    val color: String = "yellow",
    val note: String? = null,
    val progress: Float,
    val createdAt: Long,
)

/** Silently recorded now so Reading Goals (v2) start with real history. */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("startedAt")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val startedAt: Long,
    val durationMs: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val usedAt: Long,
)

/** Lifecycle of a [DownloadEntity]; stored by name. */
enum class DownloadState { Queued, Running, Paused, Failed, Done }

/**
 * A catalog download kept across restarts (see `DownloadManager`). The partial file is
 * `files/downloads/<id>.part` until the book is imported.
 */
@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadEntity(
    /** SHA-1 of [link]. */
    @PrimaryKey val id: String,
    /** Acquisition link from the feed; requested from the download host with the same path. */
    val link: String,
    val format: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    /** [DownloadState] name. */
    val state: String,
    val bytes: Long = 0,
    /** Full size, -1 while unknown. */
    val total: Long = -1,
    /** `NetworkErrors` code of the last failed attempt. */
    val error: String? = null,
    val attempts: Int = 0,
    /** Strong ETag or Last-Modified of the partial file, sent as If-Range when resuming. */
    val validator: String? = null,
    val bookId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val downloadState: DownloadState get() = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.Failed)
}
