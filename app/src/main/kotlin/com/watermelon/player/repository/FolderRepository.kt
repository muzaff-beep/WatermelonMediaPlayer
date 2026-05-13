// app/src/main/kotlin/com/watermelon/player/repository/FolderRepository.kt
// Folder visibility management. Manifesto §3.3 frozen interface.

package com.watermelon.player.repository

import com.watermelon.player.database.FolderVisibility
import com.watermelon.player.database.FolderVisibilityDao
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val folderVisibilityDao: FolderVisibilityDao) {

    /** Get all folder visibility entries. */
    fun getAll(): Flow<List<FolderVisibility>> = folderVisibilityDao.getAll()

    /** Check if a folder is visible. Defaults to true if not explicitly hidden. */
    suspend fun isVisible(folderUri: String): Boolean {
        return folderVisibilityDao.isVisible(folderUri) ?: true
    }

    /** Toggle folder visibility. If the folder was visible, hide it and vice versa. */
    suspend fun toggleVisibility(folderUri: String) {
        val current = folderVisibilityDao.isVisible(folderUri)
        val newVisibility = !(current ?: true)
        folderVisibilityDao.setVisibility(FolderVisibility(folderUri, newVisibility))
    }

    /** Set explicit visibility for a folder. */
    suspend fun setVisibility(folderUri: String, visible: Boolean) {
        folderVisibilityDao.setVisibility(FolderVisibility(folderUri, visible))
    }

    /** Remove visibility override for a folder (reverts to default visible). */
    suspend fun removeOverride(folderUri: String) {
        folderVisibilityDao.delete(folderUri)
    }
}