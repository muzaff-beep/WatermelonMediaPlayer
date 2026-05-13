// app/src/main/kotlin/com/watermelon/player/repository/MediaRepository.kt
// Repository for video media queries. Manifesto §3.3 frozen interface.

package com.watermelon.player.repository

import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.database.VideoEntity
import kotlinx.coroutines.flow.Flow

class MediaRepository(database: MediaDatabase) {

    private val videoDao = database.videoDao()

    /** Get all visible videos, ordered by date added descending. */
    fun getVisibleVideos(): Flow<List<VideoEntity>> = videoDao.getAllVisible()

    /** Get all videos regardless of visibility. */
    fun getAllVideos(): Flow<List<VideoEntity>> = videoDao.getAll()

    /** Look up a video by its URI. */
    suspend fun getByUri(uri: String): VideoEntity? = videoDao.getByUri(uri)

    /** Set the visibility of a single video. */
    suspend fun setVisibility(uri: String, visible: Boolean) {
        videoDao.setVisibility(uri, visible)
    }

    /** Remove a video from the database. */
    suspend fun deleteByUri(uri: String) {
        videoDao.deleteByUri(uri)
    }
}