package net.flibustareader.core.data.di

import android.content.Context
import androidx.room.Room
import net.flibustareader.core.data.db.FlibustaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FlibustaDatabase =
        Room.databaseBuilder(context, FlibustaDatabase::class.java, "flibusta.db").build()
}
