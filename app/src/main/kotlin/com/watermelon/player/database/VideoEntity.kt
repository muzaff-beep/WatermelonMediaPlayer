package com.watermelon.player.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val path: String,
    val duration: Long
)

// Already has id, title, path, duration – add:
@ColumnInfo(name = "folder_path")
val folderPath: String