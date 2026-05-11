package com.watermelon.player.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderVisibilityDao {
    @Query("SELECT * FROM folder_visibility WHERE isVisible = 0")
    fun getHiddenFolders(): Flow<List<FolderVisibility>>

    @Query("SELECT isVisible FROM folder_visibility WHERE folderPath = :path")
    suspend fun isFolderVisible(path: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVisibility(folder: FolderVisibility)

    @Query("DELETE FROM folder_visibility WHERE folderPath = :path")
    suspend fun deleteVisibility(path: String)
}