// app/src/main/kotlin/com/watermelon/player/viewmodel/FolderVisibilityViewModel.kt
// ViewModel for the Folder Visibility screen.

package com.watermelon.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.player.database.FolderVisibility
import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.repository.FolderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderItem(
    val folderUri: String,
    val displayName: String,
    val isVisible: Boolean
)

class FolderVisibilityViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MediaDatabase.getInstance(application)
    private val folderRepository = FolderRepository(db.folderVisibilityDao())

    val folders: StateFlow<List<FolderItem>> = folderRepository.getAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    ).let { flow ->
        // Map FolderVisibility entities to FolderItem for display
        kotlinx.coroutines.flow.combine(
            flow,
            kotlinx.coroutines.flow.flowOf(emptyList<FolderVisibility>())
        ) { items, _ ->
            items.map { fv ->
                FolderItem(
                    folderUri = fv.folderUri,
                    displayName = fv.folderUri.substringAfterLast('/').ifEmpty { fv.folderUri },
                    isVisible = fv.isVisible
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun toggleVisibility(folderUri: String) {
        viewModelScope.launch {
            folderRepository.toggleVisibility(folderUri)
        }
    }

    fun removeOverride(folderUri: String) {
        viewModelScope.launch {
            folderRepository.removeOverride(folderUri)
        }
    }

    fun addFolder(folderUri: String, visible: Boolean = true) {
        viewModelScope.launch {
            folderRepository.setVisibility(folderUri, visible)
        }
    }
}