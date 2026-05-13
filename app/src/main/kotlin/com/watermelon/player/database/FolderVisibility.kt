// app/src/main/kotlin/com/watermelon/player/database/FolderVisibility.kt
package com.watermelon.player.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_visibility")
data class FolderVisibility(
    @PrimaryKey val folderUri: String,
    val isVisible: Boolean = true
)