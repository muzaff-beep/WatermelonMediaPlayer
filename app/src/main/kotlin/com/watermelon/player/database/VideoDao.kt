package com.watermelon.player.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("""
        SELECT * FROM videos 
        WHERE folder_path NOT IN (
            SELECT folderPath FROM folder_visibility WHERE is_visible = 0
        )
        ORDER BY date_added DESC
    """)
    fun getVisibleVideos(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("DELETE FROM videos")
    suspend fun deleteAll()

    @Query("UPDATE videos SET last_position = :position WHERE id = :videoId")
    suspend fun updateLastPosition(videoId: Long, position: Long)

    @Query("SELECT DISTINCT folder_path FROM videos ORDER BY folder_path ASC")
    fun getAllFolders(): Flow<List<String>>

    @Query("SELECT * FROM videos WHERE folder_path = :folderPath")
    fun getVideosInFolder(folderPath: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoEntity?
}