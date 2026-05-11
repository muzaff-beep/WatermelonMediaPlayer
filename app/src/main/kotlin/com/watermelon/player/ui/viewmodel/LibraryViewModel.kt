package com.watermelon.player.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.player.database.FolderVisibility
import com.watermelon.player.database.MediaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FolderItem(
    val path: String,
    val isVisible: Boolean
)

class FolderVisibilityViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MediaDatabase.getDatabase(application)
    private val videoDao = db.videoDao()
    private val folderVisibilityDao = db.folderVisibilityDao()

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders

    init {
        viewModelScope.launch {
            combine(
                videoDao.getAllFolders(),
                folderVisibilityDao.getHiddenFolders()
            ) { allFolders, hiddenFolders ->
                val hiddenSet = hiddenFolders.map { it.folderPath }.toSet()
                allFolders.map { path ->
                    FolderItem(
                        path = path,
                        isVisible = path !in hiddenSet
                    )
                }
            }.collect { items ->
                _folders.value = items
            }
        }
    }

    fun toggleFolder(path: String, visible: Boolean) {
        viewModelScope.launch {
            folderVisibilityDao.setVisibility(FolderVisibility(path, visible))
        }
    }
}