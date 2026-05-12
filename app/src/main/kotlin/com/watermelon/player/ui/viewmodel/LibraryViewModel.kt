package com.watermelon.player.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.database.VideoEntity
import com.watermelon.player.storage.UnifiedStorageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MediaDatabase.getDatabase(application)
    private val storage = UnifiedStorageAccess(application)

    private val _videos = MutableStateFlow<List<VideoEntity>>(emptyList())
    val videos: StateFlow<List<VideoEntity>> = _videos

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    init {
        viewModelScope.launch {
            db.videoDao().getVisibleVideos().collect { list ->
                _videos.value = list
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                storage.scanAndInsertVideos()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun toggleFolderVisibility(folderPath: String, visible: Boolean) {
        viewModelScope.launch {
            storage.setFolderVisibility(folderPath, visible)
        }
    }
}