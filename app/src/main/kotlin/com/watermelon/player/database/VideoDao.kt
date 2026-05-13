// app/src/main/kotlin/com/watermelon/player/database/VideoDao.kt
package com.watermelon.player.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos WHERE isVisible = 1 ORDER BY dateAdded DESC")
    fun getAllVisible(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY dateAdded DESC")
    fun getAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Update
    suspend fun update(video: VideoEntity)

    @Query("UPDATE videos SET isVisible = :visible WHERE uri = :uri")
    suspend fun setVisibility(uri: String, visible: Boolean)

    @Query("DELETE FROM videos WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM videos WHERE uri NOT IN (:uris)")
    suspend fun deleteAllExcept(uris: List<String>)
}