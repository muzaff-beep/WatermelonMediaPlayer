// app/src/main/kotlin/com/watermelon/player/platform/SurfaceProvider.kt
// Manages the Android Surface lifecycle for video rendering.
// Provides Surface to Rust core via WatermelonCore.setSurface().

package com.watermelon.player.platform

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.util.Log

/**
 * Wraps a TextureView and provides Surface lifecycle management.
 * The Surface is handed to the Rust engine for ANativeWindow rendering.
 */
class SurfaceProvider(
    private val textureView: TextureView
) : TextureView.SurfaceTextureListener {

    private val TAG = "SurfaceProvider"
    private var currentSurface: Surface? = null
    private var onSurfaceReady: ((Surface) -> Unit)? = null
    private var onSurfaceDestroyed: (() -> Unit)? = null

    init {
        textureView.surfaceTextureListener = this
        // If the TextureView already has a surface available, deliver it immediately
        if (textureView.isAvailable) {
            currentSurface = Surface(textureView.surfaceTexture)
            Log.d(TAG, "Surface available on init")
        }
    }

    /** Register callback invoked when a new Surface is ready. */
    fun setOnSurfaceReady(callback: (Surface) -> Unit) {
        onSurfaceReady = callback
        // Deliver existing surface if already available
        currentSurface?.let { callback(it) }
    }

    /** Register callback invoked when the Surface is about to be destroyed. */
    fun setOnSurfaceDestroyed(callback: () -> Unit) {
        onSurfaceDestroyed = callback
    }

    /** Get the current Surface, or null if not available. */
    fun getSurface(): Surface? = currentSurface

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        currentSurface = Surface(surfaceTexture)
        Log.d(TAG, "Surface available: ${width}x${height}")
        onSurfaceReady?.invoke(currentSurface!!)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "Surface size changed: ${width}x${height}")
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.d(TAG, "Surface destroyed")
        onSurfaceDestroyed?.invoke()
        currentSurface?.release()
        currentSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // No-op: frame updates are handled by native renderer
    }

    /** Release resources. Call when the view is detached. */
    fun release() {
        currentSurface?.release()
        currentSurface = null
        textureView.surfaceTextureListener = null
    }
}