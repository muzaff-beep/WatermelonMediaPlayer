// app/src/main/kotlin/com/watermelon/player/scan/ScanScheduler.kt
package com.watermelon.player.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

class ScanScheduler(
    private val context: Context,
    private val scannerWorker: VideoScannerWorker
) {
    private val TAG = "ScanScheduler"
    private val receiver = ScanTriggerReceiver()

    fun register() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addDataScheme("file")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        Log.d(TAG, "Scan triggers registered")
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver not registered")
        }
    }

    inner class ScanTriggerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Scan triggered by: ${intent.action}")
            scannerWorker.scanAll()
        }
    }
}