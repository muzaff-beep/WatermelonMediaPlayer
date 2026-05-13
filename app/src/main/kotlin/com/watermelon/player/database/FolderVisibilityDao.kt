// app/src/main/kotlin/com/watermelon/player/database/FolderVisibilityDao.kt
package com.watermelon.player.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderVisibilityDao {
    @Query("SELECT * FROM folder_visibility")
    fun getAll(): Flow<List<FolderVisibility>>

    @Query("SELECT isVisible FROM folder_visibility WHERE folderUri = :folderUri")
    suspend fun isVisible(folderUri: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVisibility(folderVisibility: FolderVisibility)

    @Query("DELETE FROM folder_visibility WHERE folderUri = :folderUri")
    suspend fun delete(folderUri: String)
}