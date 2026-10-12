package net.flibustareader.core.data.db

import androidx.room.AutoMigration
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
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class FlibustaDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun collections(): CollectionDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun highlights(): HighlightDao
    abstract fun sessions(): SessionDao
    abstract fun searchHistory(): SearchHistoryDao
    abstract fun downloads(): DownloadDao
}
