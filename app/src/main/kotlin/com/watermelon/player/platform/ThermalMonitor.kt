// app/src/main/kotlin/com/watermelon/player/platform/ThermalMonitor.kt
// Monitors device thermal status to adjust playback performance.
// On throttling, notifies Rust engine to reduce decode complexity.

package com.watermelon.player.platform

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Listens for thermal status changes and reports throttling level.
 * Thermal levels: 0 = none, 1 = light, 2 = moderate, 3 = severe, 4 = critical, 5 = emergency, 6 = shutdown.
 */
class ThermalMonitor(context: Context) {

    private val TAG = "ThermalMonitor"
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var onThermalStatusChanged: ((status: ThermalStatus) -> Unit)? = null

    /** Thermal severity levels exposed to UI and Rust engine. */
    enum class ThermalStatus(val level: Int) {
        NONE(0),
        LIGHT(1),
        MODERATE(2),
        SEVERE(3),
        CRITICAL(4),
        EMERGENCY(5),
        SHUTDOWN(6);

        companion object {
            fun fromPowerManagerStatus(status: Int): ThermalStatus = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> NONE
                PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> SHUTDOWN
                else -> NONE
            }
        }
    }

    /**
     * Register a listener for thermal status changes.
     * Called whenever the device thermal state changes.
     */
    fun setOnThermalStatusChanged(callback: (ThermalStatus) -> Unit) {
        onThermalStatusChanged = callback
    }

    /**
     * Get the current thermal status.
     */
    fun getCurrentStatus(): ThermalStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = powerManager.currentThermalStatus
            Log.d(TAG, "Current thermal status: $status")
            ThermalStatus.fromPowerManagerStatus(status)
        } else {
            ThermalStatus.NONE
        }
    }

    /**
     * Add a thermal status listener on Android 10+. On older versions, no-op.
     */
    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener { status ->
                val thermalStatus = ThermalStatus.fromPowerManagerStatus(status)
                Log.d(TAG, "Thermal status changed: $thermalStatus")
                onThermalStatusChanged?.invoke(thermalStatus)
            }
            Log.d(TAG, "Thermal monitoring started")
        } else {
            Log.d(TAG, "Thermal monitoring not available (API < 29)")
        }
    }

    /**
     * Determine if playback should reduce quality based on thermal status.
     */
    fun shouldThrottle(): Boolean {
        val status = getCurrentStatus()
        return status.level >= ThermalStatus.MODERATE.level
    }
}