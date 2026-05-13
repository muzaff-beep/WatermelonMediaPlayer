// app/src/main/kotlin/com/watermelon/player/callback/CoreEventAdapter.kt
// Adapter converting Rust JSON callbacks to WatermelonEventCallback interface calls.

package com.watermelon.player.callback

import com.watermelon.player.rust.WatermelonEventCallback
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses JSON event strings from Rust and dispatches to the typed callback interface.
 */
class CoreEventAdapter(private val target: WatermelonEventCallback) {

    /**
     * Dispatch a JSON event string from the Rust engine.
     * Called by the native callback bridge.
     */
    fun dispatch(jsonEvent: String) {
        try {
            val root = JSONObject(jsonEvent)
            val eventType = root.getString("event")
            when (eventType) {
                "prepared" -> {
                    val durationUs = root.getLong("durationUs")
                    target.onPrepared(durationUs)
                }
                "state" -> {
                    val state = root.getInt("state")
                    target.onPlaybackStateChanged(state)
                }
                "error" -> {
                    val code = root.getInt("code")
                    val message = root.getString("message")
                    target.onError(code, message)
                }
                "cues" -> {
                    val cuesArray = root.getJSONArray("cues")
                    val cuesJson = cuesArray.toString()
                    target.onSubtitleCues(cuesJson)
                }
                else -> {
                    android.util.Log.w("CoreEventAdapter", "Unknown event type: $eventType")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CoreEventAdapter", "Failed to parse event: $jsonEvent", e)
        }
    }
}