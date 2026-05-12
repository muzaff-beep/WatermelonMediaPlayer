package com.watermelon.player.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VideoEntity::class, FolderVisibility::class],
    version = 1,
    exportSchema = false
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun folderVisibilityDao(): FolderVisibilityDao

    companion object {
        @Volatile
        private var INSTANCE: MediaDatabase? = null

        fun getDatabase(context: Context): MediaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MediaDatabase::class.java,
                    "watermelon_media.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}