package com.watermelon.player.viewmodel

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.database.VideoEntity
import com.watermelon.player.repository.MediaRepository
import com.watermelon.player.scan.VideoScannerWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortMode { NAME, DATE, SIZE }

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MediaDatabase(application)
    private val repository = MediaRepository(db)
    private val scanner = VideoScannerWorker(application)

    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("LibraryViewModel", "Coroutine crash", throwable)
        saveCrashLog(throwable)
    }

    private val _videos = MutableStateFlow<List<VideoEntity>>(emptyList())
    val videos: StateFlow<List<VideoEntity>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(Pair(0, 0))
    val scanProgress: StateFlow<Pair<Int, Int>> = _scanProgress.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _gridColumns = MutableStateFlow(2)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    init {
        scanner.setOnProgress { scanned, total ->
            _scanProgress.value = Pair(scanned, total)
        }
        scanner.setOnComplete { total ->
            _isScanning.value = false
            _scanProgress.value = Pair(total, total)
            _videos.value = getSortedVideos(repository.getVisibleVideos())
        }
        startScan()
    }

    fun startScan() {
        viewModelScope.launch(crashHandler) {
            try {
                _isScanning.value = true
                _scanProgress.value = Pair(0, 0)
                scanner.scanAll()
            } catch (e: SecurityException) {
                Log.e("LibraryViewModel", "Permission denied during scan", e)
                _isScanning.value = false
                saveCrashLog(e)
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Scan failed", e)
                _isScanning.value = false
                saveCrashLog(e)
            }
        }
    }

    fun refresh() {
        startScan()
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        _videos.value = getSortedVideos(repository.getVisibleVideos())
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGridList() {
        _isGridView.value = !_isGridView.value
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
    }

    private fun getSortedVideos(list: List<VideoEntity>): List<VideoEntity> {
        return when (_sortMode.value) {
            SortMode.NAME -> list.sortedBy { it.displayName.lowercase() }
            SortMode.DATE -> list.sortedByDescending { it.dateAdded }
            SortMode.SIZE -> list.sortedByDescending { it.sizeBytes }
        }
    }

    override fun onCleared() {
        scanner.release()
        super.onCleared()
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val logFile = File(downloadDir, "watermelon_crash.txt")
            val writer = PrintWriter(FileWriter(logFile, true))
            writer.println("=== Coroutine Crash ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===")
            throwable.printStackTrace(writer)
            writer.flush()
            writer.close()
        } catch (_: Exception) {}
    }
}