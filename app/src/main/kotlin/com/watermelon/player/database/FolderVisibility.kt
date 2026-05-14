// app/src/main/kotlin/com/watermelon/player/database/FolderVisibility.kt
package com.watermelon.player.database

data class FolderVisibility(
    val folderUri: String,
    val isVisible: Boolean = true
)