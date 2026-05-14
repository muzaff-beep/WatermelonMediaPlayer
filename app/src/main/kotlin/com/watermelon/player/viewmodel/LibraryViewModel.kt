// app/src/main/kotlin/com/watermelon/player/viewmodel/LibraryViewModel.kt
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

    init {
        scanner.setOnProgress { scanned, total ->
            _scanProgress.value = Pair(scanned, total)
        }
        scanner.setOnComplete { total ->
            _isScanning.value = false
            _scanProgress.value = Pair(total, total)
            _videos.value = repository.getVisibleVideos()
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