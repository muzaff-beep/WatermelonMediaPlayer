// app/src/main/kotlin/com/watermelon/player/rust/WatermelonCore.kt
// JNI wrapper for libwatermelon_core.so. Manifesto §3.1 frozen signatures.

package com.watermelon.player.rust

import android.util.Log
import java.io.File

/**
 * Singleton bridge to the Rust media engine.
 * All native methods map 1:1 to the frozen JNI contract.
 */
object WatermelonCore {

    private const val TAG = "WatermelonCore"
    private var nativeHandle: Long = 0
    private var isLoaded = false

    /**
     * Load the native library. Must be called before any other method.
     * @param pluginsDir absolute path to the codec plugins directory.
     */
    fun init(pluginsDir: File) {
        if (isLoaded) return
        try {
            System.loadLibrary("watermelon_core")
            isLoaded = true
            nativeHandle = nativeInit()
            Log.i(TAG, "Native engine initialized: handle=$nativeHandle")
            // Pre-load plugins from directory
            pluginsDir.listFiles()?.filter { it.extension == "so" }?.forEach { plugin ->
                if (nativeLoadPlugin(nativeHandle, plugin.absolutePath)) {
                    Log.i(TAG, "Plugin loaded: ${plugin.name}")
                } else {
                    Log.w(TAG, "Failed to load plugin: ${plugin.name}")
                }
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
            throw e
        }
    }

    /** Release the native engine. Safe to call multiple times. */
    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
            isLoaded = false
            Log.i(TAG, "Native engine destroyed")
        }
    }

    // --- Source and control ---

    fun setDataSource(uri: String): Boolean {
        checkInitialized()
        return nativeSetDataSource(nativeHandle, uri)
    }

    fun prepare() {
        checkInitialized()
        nativePrepare(nativeHandle)
    }

    fun play() {
        checkInitialized()
        nativePlay(nativeHandle)
    }

    fun pause() {
        checkInitialized()
        nativePause(nativeHandle)
    }

    fun seekTo(positionUs: Long) {
        checkInitialized()
        nativeSeekTo(nativeHandle, positionUs)
    }

    fun getCurrentPosition(): Long {
        checkInitialized()
        return nativeGetCurrentPosition(nativeHandle)
    }

    fun getDuration(): Long {
        checkInitialized()
        return nativeGetDuration(nativeHandle)
    }

    // --- Surface ---

    fun setSurface(surface: android.view.Surface?) {
        checkInitialized()
        nativeSetSurface(nativeHandle, surface)
    }

    // --- Subtitles ---

    fun loadSubtitle(path: String): Boolean {
        checkInitialized()
        return nativeLoadSubtitle(nativeHandle, path)
    }

    fun setSubtitleOffset(offsetMs: Long) {
        checkInitialized()
        nativeSetSubtitleOffset(nativeHandle, offsetMs)
    }

    fun setSubtitleFont(fontPath: String) {
        checkInitialized()
        nativeSetSubtitleFont(nativeHandle, fontPath)
    }

    // --- Plugins ---

    fun loadPlugin(soPath: String): Boolean {
        checkInitialized()
        return nativeLoadPlugin(nativeHandle, soPath)
    }

    // --- Callbacks ---

    fun setEventCallback(callback: WatermelonEventCallback?) {
        checkInitialized()
        nativeSetEventCallback(nativeHandle, callback)
    }

    // --- Private ---

    private fun checkInitialized() {
        require(isLoaded && nativeHandle != 0L) { "WatermelonCore not initialized. Call init() first." }
    }

    // --- Native declarations ---
    private external fun nativeInit(): Long
    private external fun nativeDestroy(enginePtr: Long)
    private external fun nativeSetDataSource(enginePtr: Long, uri: String): Boolean
    private external fun nativePrepare(enginePtr: Long)
    private external fun nativePlay(enginePtr: Long)
    private external fun nativePause(enginePtr: Long)
    private external fun nativeSeekTo(enginePtr: Long, positionUs: Long)
    private external fun nativeGetCurrentPosition(enginePtr: Long): Long
    private external fun nativeGetDuration(enginePtr: Long): Long
    private external fun nativeSetSurface(enginePtr: Long, surface: Any?)
    private external fun nativeLoadSubtitle(enginePtr: Long, path: String): Boolean
    private external fun nativeSetSubtitleOffset(enginePtr: Long, offsetMs: Long)
    private external fun nativeSetSubtitleFont(enginePtr: Long, fontPath: String)
    private external fun nativeLoadPlugin(enginePtr: Long, soPath: String): Boolean
    private external fun nativeSetEventCallback(enginePtr: Long, callback: WatermelonEventCallback?)
}