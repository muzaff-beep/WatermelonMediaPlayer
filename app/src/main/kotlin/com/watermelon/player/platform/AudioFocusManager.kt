// app/src/main/kotlin/com/watermelon/player/platform/AudioFocusManager.kt
// Manages Android audio focus to cooperate with other media apps.
// Manifesto §1.2: AAudio output path needs proper audio focus handling.

package com.watermelon.player.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Requests and releases audio focus for media playback.
 * Respects audio focus changes (e.g., incoming call, notification).
 */
class AudioFocusManager(context: Context) {

    private val TAG = "AudioFocusManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var onFocusGained: (() -> Unit)? = null
    private var onFocusLost: (() -> Unit)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasAudioFocus = true
                onFocusGained?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost: $focusChange")
                hasAudioFocus = false
                onFocusLost?.invoke()
            }
        }
    }

    /** Register callback when focus is gained after a transient loss. */
    fun setOnFocusGained(callback: () -> Unit) {
        onFocusGained = callback
    }

    /** Register callback when focus is lost. */
    fun setOnFocusLost(callback: () -> Unit) {
        onFocusLost = callback
    }

    /**
     * Request audio focus for media playback.
     * @return true if focus was granted.
     */
    fun requestFocus(): Boolean {
        if (hasAudioFocus) return true

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            Log.d(TAG, "Focus request result: $hasAudioFocus")
            hasAudioFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasAudioFocus
        }
    }

    /**
     * Abandon audio focus. Call when playback stops.
     */
    fun abandonFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        hasAudioFocus = false
        focusRequest = null
        Log.d(TAG, "Audio focus abandoned")
    }

    /** Check if audio focus is currently held. */
    fun hasFocus(): Boolean = hasAudioFocus
}