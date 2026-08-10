package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        GuestRequestEntity::class,
        DjSettingsEntity::class,
        SongBpmEntity::class,
        SongMetadataEntity::class,
        BeatCacheEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun guestRequestDao(): GuestRequestDao
    abstract fun settingsDao(): SettingsDao
    abstract fun songBpmDao(): SongBpmDao
    abstract fun songMetadataDao(): SongMetadataDao
    abstract fun beatCacheDao(): BeatCacheDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "seamless_dj_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
