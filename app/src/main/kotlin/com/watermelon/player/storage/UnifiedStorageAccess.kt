package com.watermelon.player.storage

import android.content.Context
import android.provider.MediaStore
import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.database.VideoEntity
import java.io.File

class UnifiedStorageAccess(private val context: Context) {

    private val db = MediaDatabase.getDatabase(context)
    private val videoDao = db.videoDao()
    private val folderVisibilityDao = db.folderVisibilityDao()

    suspend fun scanAndInsertVideos() {
        val videos = mutableListOf<VideoEntity>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,          // deprecated but still works on older APIs
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val data = cursor.getString(dataCol) ?: continue
                val file = File(data)
                val folderPath = file.parent ?: relativePathCol.let { 
                    cursor.getString(it) ?: "unknown"
                }

                // Basic filter: skip files < 1 second (likely corrupt)
                val duration = cursor.getLong(durationCol)
                if (duration < 1000) continue

                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idCol)
                ).toString()

                videos.add(
                    VideoEntity(
                        title = cursor.getString(nameCol) ?: "Unknown",
                        uri = uri,
                        duration = duration,
                        folderPath = folderPath,
                        hash = ""   // compute later
                    )
                )
            }
        }

        videoDao.deleteAll()
        videoDao.insertAll(videos)
    }

    suspend fun getVisibleVideos() = videoDao.getVisibleVideos()

    suspend fun setFolderVisibility(folderPath: String, visible: Boolean) {
        folderVisibilityDao.setVisibility(
            com.watermelon.player.database.FolderVisibility(folderPath, visible)
        )
    }

    suspend fun getHiddenFolders() = folderVisibilityDao.getHiddenFolders()
}