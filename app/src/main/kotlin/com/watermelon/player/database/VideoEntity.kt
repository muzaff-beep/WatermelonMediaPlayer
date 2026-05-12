package com.watermelon.player.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "hash") val hash: String = "",
    @ColumnInfo(name = "last_position") val lastPosition: Long = 0,
    @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis()
)