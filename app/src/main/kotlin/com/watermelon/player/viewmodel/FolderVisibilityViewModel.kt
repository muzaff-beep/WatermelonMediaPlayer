// app/src/main/kotlin/com/watermelon/player/viewmodel/FolderVisibilityViewModel.kt
package com.watermelon.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.player.database.FolderVisibility
import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.repository.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FolderItem(
    val folderUri: String,
    val displayName: String,
    val isVisible: Boolean
)

class FolderVisibilityViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MediaDatabase(application)
    private val folderRepository = FolderRepository(db.folderVisibilityDao)

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    init {
        loadFolders()
    }

    private fun loadFolders() {
        val items = folderRepository.getAll().map { fv ->
            FolderItem(
                folderUri = fv.folderUri,
                displayName = fv.folderUri.substringAfterLast('/').ifEmpty { fv.folderUri },
                isVisible = fv.isVisible
            )
        }
        _folders.value = items
    }

    fun toggleVisibility(folderUri: String) {
        viewModelScope.launch {
            folderRepository.toggleVisibility(folderUri)
            loadFolders()
        }
    }

    fun removeOverride(folderUri: String) {
        viewModelScope.launch {
            folderRepository.removeOverride(folderUri)
            loadFolders()
        }
    }

    fun addFolder(folderUri: String, visible: Boolean = true) {
        viewModelScope.launch {
            folderRepository.setVisibility(folderUri, visible)
            loadFolders()
        }
    }
}