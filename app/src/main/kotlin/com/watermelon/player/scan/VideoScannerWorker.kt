// app/src/main/kotlin/com/watermelon/player/scan/VideoScannerWorker.kt
package com.watermelon.player.scan

import android.content.Context
import android.util.Log
import com.watermelon.player.database.MediaDatabase
import com.watermelon.player.storage.UnifiedStorageAccess
import kotlinx.coroutines.*

class VideoScannerWorker(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val TAG = "VideoScannerWorker"
    private val storage = UnifiedStorageAccess(context)
    private val db = MediaDatabase(context)
    private var scanJob: Job? = null
    private var onProgress: ((scanned: Int, total: Int) -> Unit)? = null
    private var onComplete: ((totalFound: Int) -> Unit)? = null

    fun setOnProgress(callback: (scanned: Int, total: Int) -> Unit) {
        onProgress = callback
    }

    fun setOnComplete(callback: (totalFound: Int) -> Unit) {
        onComplete = callback
    }

    fun scanAll() {
        scanJob?.cancel()
        scanJob = scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting full media scan")
                    val videos = storage.queryMediaStore()
                    onProgress?.invoke(videos.size, videos.size)
                    val uris = videos.map { it.uri }
                    db.videoDao.deleteAllExcept(uris)
                    db.videoDao.insertAll(videos)
                    Log.d(TAG, "Scan complete: ${videos.size} videos found")
                    onComplete?.invoke(videos.size)
                } catch (e: Exception) {
                    Log.e(TAG, "Scan failed", e)
                    onComplete?.invoke(0)
                }
            }
        }
    }

    fun cancel() {
        scanJob?.cancel()
        scanJob = null
    }

    fun release() {
        cancel()
        scope.cancel()
    }
}