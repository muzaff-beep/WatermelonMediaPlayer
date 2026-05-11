package com.watermelon.player.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "uri") val uri: String,          // content:// or file://
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "hash") val hash: String = ""    // MD5 future use
)