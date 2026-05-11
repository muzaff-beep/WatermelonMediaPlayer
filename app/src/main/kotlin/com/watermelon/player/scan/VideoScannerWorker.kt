package com.watermelon.player.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.watermelon.player.storage.UnifiedStorageAccess

class VideoScannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val storage = UnifiedStorageAccess(applicationContext)
            storage.scanAndInsertVideos()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "video_scanner"
    }
}