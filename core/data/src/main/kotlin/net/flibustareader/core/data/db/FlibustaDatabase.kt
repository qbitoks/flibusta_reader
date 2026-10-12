package net.flibustareader.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        CollectionBookEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ReadingSessionEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FlibustaDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun collections(): CollectionDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun highlights(): HighlightDao
    abstract fun sessions(): SessionDao
    abstract fun searchHistory(): SearchHistoryDao
}
