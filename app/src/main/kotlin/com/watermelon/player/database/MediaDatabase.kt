// app/src/main/kotlin/com/watermelon/player/database/MediaDatabase.kt
package com.watermelon.player.database

import android.content.Context

class MediaDatabase(context: Context) {
    val videoDao = VideoDao(context)
    val folderVisibilityDao = FolderVisibilityDao(context)
}