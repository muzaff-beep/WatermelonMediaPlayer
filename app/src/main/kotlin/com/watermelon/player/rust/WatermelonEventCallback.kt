// app/src/main/kotlin/com/watermelon/player/rust/WatermelonEventCallback.kt
// Callback interface implemented by Kotlin, called by Rust via JNI.
// Manifesto §3.1 frozen contract.

package com.watermelon.player.rust

/**
 * Event callback from Rust media engine to Kotlin UI layer.
 * All methods receive JSON strings per Manifesto §3.6.
 */
interface WatermelonEventCallback {
    /** Called when media preparation is complete. */
    fun onPrepared(durationUs: Long)

    /** Called when playback state changes. 0=idle,1=preparing,2=playing,3=paused,4=ended,5=error */
    fun onPlaybackStateChanged(state: Int)

    /** Called when an error occurs. */
    fun onError(code: Int, message: String)

    /** Called when subtitle cues are active at the current position. JSON array of {startUs, endUs, text}. */
    fun onSubtitleCues(cuesJson: String)
}