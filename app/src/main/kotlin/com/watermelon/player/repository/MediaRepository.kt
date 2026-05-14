// app/src/main/kotlin/com/watermelon/player/repository/MediaRepository.kt
package com.watermelon.player.repository

import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.database.VideoEntity

class MediaRepository(private val db: MediaDatabase) {

    fun getVisibleVideos(): List<VideoEntity> = db.videoDao.getAllVisible()

    fun getAllVideos(): List<VideoEntity> = db.videoDao.getAll()

    fun getByUri(uri: String): VideoEntity? = db.videoDao.getByUri(uri)

    fun setVisibility(uri: String, visible: Boolean) {
        db.videoDao.setVisibility(uri, visible)
    }

    fun deleteByUri(uri: String) {
        db.videoDao.deleteByUri(uri)
    }
}