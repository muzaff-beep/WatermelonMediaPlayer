// app/src/main/kotlin/com/watermelon/player/database/VideoEntity.kt
package com.watermelon.player.database

data class VideoEntity(
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val isVisible: Boolean = true
)