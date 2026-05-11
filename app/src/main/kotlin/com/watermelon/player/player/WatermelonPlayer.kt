package com.watermelon.player.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.watermelon.player.player.loadcontrol.SimpleLoadControl

@OptIn(UnstableApi::class)
class WatermelonPlayer(private val context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context))
        .setLoadControl(SimpleLoadControl())
        .build()

    fun setSource(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun getCurrentPosition(): Long = player.currentPosition
    fun getDuration(): Long = player.duration
    fun release() = player.release()
    fun isPlaying(): Boolean = player.isPlaying

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        player.removeListener(listener)
    }
}