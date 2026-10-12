package net.flibustareader.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT sourceUri FROM books WHERE sourceUri IS NOT NULL")
    suspend fun sourceUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET position = :position, progress = :progress, lastOpenedAt = :openedAt, hiddenFromContinue = 0 WHERE id = :id")
    suspend fun updatePosition(id: String, position: String, progress: Float, openedAt: Long)

    @Query("UPDATE books SET lastOpenedAt = :openedAt, hiddenFromContinue = 0 WHERE id = :id")
    suspend fun touch(id: String, openedAt: Long)

    @Query("UPDATE books SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun setFinished(id: String, finishedAt: Long?)

    @Query("UPDATE books SET wantToRead = :value WHERE id = :id")
    suspend fun setWantToRead(id: String, value: Boolean)

    @Query("UPDATE books SET sourceUri = :uri WHERE id = :id AND sourceUri IS NULL")
    suspend fun attachSource(id: String, uri: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET customTitle = :title WHERE id = :id")
    suspend fun rename(id: String, title: String?)

    @Query("UPDATE books SET hiddenFromContinue = :hidden WHERE id = :id")
    suspend fun setHiddenFromContinue(id: String, hidden: Boolean)

    @Query("UPDATE books SET coverColors = :colors WHERE id = :id")
    suspend fun setCoverColors(id: String, colors: String)

    @Query("UPDATE books SET manualOrder = :order WHERE id = :id")
    suspend fun setManualOrder(id: String, order: Int)

    @Query("SELECT id FROM books WHERE hasCover = 1 AND coverColors IS NULL")
    suspend fun idsMissingCoverColors(): List<String>
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT bookId FROM collection_books WHERE collectionId = :id ORDER BY addedAt DESC")
    fun observeBookIds(id: Long): Flow<List<String>>

    @Query("SELECT collectionId FROM collection_books WHERE bookId = :bookId")
    fun observeCollectionsOf(bookId: String): Flow<List<Long>>

    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Upsert
    suspend fun add(entry: CollectionBookEntity)

    @Query("DELETE FROM collection_books WHERE collectionId = :collectionId AND bookId = :bookId")
    suspend fun remove(collectionId: Long, bookId: String)

    @Query("UPDATE collections SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** Book count per collection, for the «Коллекции» list. */
    @Query("SELECT collectionId AS id, COUNT(*) AS count FROM collection_books GROUP BY collectionId")
    fun observeCounts(): Flow<List<CollectionCount>>
}

data class CollectionCount(val id: Long, val count: Int)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY usedAt DESC LIMIT 20")
    fun observe(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY progress")
    fun observe(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY progress")
    fun observe(bookId: String): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("UPDATE highlights SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("UPDATE highlights SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: String)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: ReadingSessionEntity)
}
