// app/src/main/kotlin/com/watermelon/player/viewmodel/LibraryViewModel.kt
// ViewModel for the Library screen. Exposes video list and scan state.

package com.watermelon.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.repository.MediaRepository
import com.watermelon.player.scan.VideoScannerWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MediaDatabase.getInstance(application)
    private val repository = MediaRepository(db)
    private val scanner = VideoScannerWorker(application)

    val videos = repository.getVisibleVideos().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(Pair(0, 0))
    val scanProgress: StateFlow<Pair<Int, Int>> = _scanProgress.asStateFlow()

    init {
        scanner.setOnProgress { scanned, total ->
            _scanProgress.value = Pair(scanned, total)
        }
        scanner.setOnComplete { total ->
            _isScanning.value = false
            _scanProgress.value = Pair(total, total)
        }
        startScan()
    }

    fun startScan() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = Pair(0, 0)
            scanner.scanAll()
        }
    }

    fun refresh() {
        startScan()
    }

    override fun onCleared() {
        scanner.release()
        super.onCleared()
    }
}