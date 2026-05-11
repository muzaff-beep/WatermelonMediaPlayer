package com.watermelon.player.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_visibility")
data class FolderVisibility(
    @PrimaryKey val folderPath: String,
    @ColumnInfo(name = "is_visible") val isVisible: Boolean = true
)