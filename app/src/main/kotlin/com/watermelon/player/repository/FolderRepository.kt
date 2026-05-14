// app/src/main/kotlin/com/watermelon/player/repository/FolderRepository.kt
package com.watermelon.player.repository

import com.watermelon.player.database.FolderVisibility
import com.watermelon.player.database.FolderVisibilityDao

class FolderRepository(private val folderVisibilityDao: FolderVisibilityDao) {

    fun getAll(): List<FolderVisibility> = folderVisibilityDao.getAll()

    fun isVisible(folderUri: String): Boolean {
        return folderVisibilityDao.isVisible(folderUri) ?: true
    }

    fun toggleVisibility(folderUri: String) {
        val current = folderVisibilityDao.isVisible(folderUri)
        val newVisibility = !(current ?: true)
        folderVisibilityDao.setVisibility(FolderVisibility(folderUri, newVisibility))
    }

    fun setVisibility(folderUri: String, visible: Boolean) {
        folderVisibilityDao.setVisibility(FolderVisibility(folderUri, visible))
    }

    fun removeOverride(folderUri: String) {
        folderVisibilityDao.delete(folderUri)
    }
}