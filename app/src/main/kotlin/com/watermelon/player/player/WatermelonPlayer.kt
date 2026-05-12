package com.watermelon.player.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector

@OptIn(UnstableApi::class)
class WatermelonPlayer(private val context: Context) {

    enum class DecoderMode { AUTO, HARDWARE, SOFTWARE }

    var decoderMode: DecoderMode = DecoderMode.AUTO
        set(value) {
            field = value
            applyDecoderMode()
        }

    private val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context))
        .setTrackSelector(DefaultTrackSelector(context))
        .build()

    private fun applyDecoderMode() {
        when (decoderMode) {
            DecoderMode.SOFTWARE -> {
                // Prefer software for all codecs
                renderersFactory.setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                )
            }
            else -> {
                renderersFactory.setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                )
            }
        }
        // Reinitialize player if already created? For simplicity, restart needed.
    }

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
    fun isPlaying(): Boolean = player.isPlaying

    fun getAudioTracks(): List<String> {
        val mappedInfo = (player.trackSelector as? MappingTrackSelector)?.currentMappedTrackInfo
            ?: return emptyList()
        val trackGroups = mutableListOf<String>()
        for (i in 0 until mappedInfo.rendererCount) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                val group = mappedInfo.getTrackGroups(i)
                for (g in 0 until group.length) {
                    val format = group.getFormat(g)
                    trackGroups.add(format.label ?: "Audio ${g+1}")
                }
            }
        }
        return trackGroups
    }

    fun setAudioTrack(index: Int) {
        val trackSelector = player.trackSelector as? DefaultTrackSelector ?: return
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage(null) // reset
                .setPreferredAudioRoleFlags(0)
        )
        val mappedInfo = trackSelector.currentMappedTrackInfo ?: return
        for (i in 0 until mappedInfo.rendererCount) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                val trackGroup = mappedInfo.getTrackGroups(i)
                if (index < trackGroup.length) {
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                            .setSelectionOverride(i, trackGroup, index)
                    )
                }
                break
            }
        }
    }

    fun getSubtitleTracks(): List<String> {
        val mappedInfo = (player.trackSelector as? MappingTrackSelector)?.currentMappedTrackInfo
            ?: return emptyList()
        val tracks = mutableListOf<String>()
        for (i in 0 until mappedInfo.rendererCount) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_TEXT) {
                val group = mappedInfo.getTrackGroups(i)
                for (g in 0 until group.length) {
                    val format = group.getFormat(g)
                    tracks.add(format.label ?: "Subtitle ${g+1}")
                }
            }
        }
        return tracks
    }

    fun setSubtitleTrack(index: Int) {
        val trackSelector = player.trackSelector as? DefaultTrackSelector ?: return
        val mappedInfo = trackSelector.currentMappedTrackInfo ?: return
        for (i in 0 until mappedInfo.rendererCount) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_TEXT) {
                val trackGroup = mappedInfo.getTrackGroups(i)
                if (index < trackGroup.length) {
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                            .setSelectionOverride(i, trackGroup, index)
                    )
                }
                break
            }
        }
    }

    fun release() {
        player.release()
    }

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        player.removeListener(listener)
    }
}