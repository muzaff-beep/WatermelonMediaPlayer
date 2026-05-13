// app/src/main/kotlin/com/watermelon/player/viewmodel/PlayerViewModel.kt
// ViewModel for the Player screen. Bridges UI to WatermelonCore engine.

package com.watermelon.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.player.platform.AudioFocusManager
import com.watermelon.player.platform.NetworkSocketProvider
import com.watermelon.player.platform.SurfaceProvider
import com.watermelon.player.platform.ThermalMonitor
import com.watermelon.player.rust.WatermelonCore
import com.watermelon.player.rust.WatermelonEventCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val durationUs: Long = 0,
    val currentPositionUs: Long = 0,
    val subtitleCuesJson: String = "[]",
    val errorMessage: String? = null,
    val playbackState: Int = 0 // 0=idle,1=preparing,2=playing,3=paused,4=ended,5=error
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val audioFocus = AudioFocusManager(application)
    private val networkProvider = NetworkSocketProvider()
    private val thermalMonitor = ThermalMonitor(application)

    private var surfaceProvider: SurfaceProvider? = null

    private val eventCallback = object : WatermelonEventCallback {
        override fun onPrepared(durationUs: Long) {
            _playerState.value = _playerState.value.copy(
                isPreparing = false,
                durationUs = durationUs,
                isPlaying = true,
                playbackState = 2
            )
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playerState.value = _playerState.value.copy(
                playbackState = state,
                isPlaying = (state == 2),
                isPreparing = (state == 1),
                errorMessage = if (state == 5) "Playback error" else null
            )
        }

        override fun onError(code: Int, message: String) {
            _playerState.value = _playerState.value.copy(
                playbackState = 5,
                errorMessage = message,
                isPlaying = false
            )
        }

        override fun onSubtitleCues(cuesJson: String) {
            _playerState.value = _playerState.value.copy(subtitleCuesJson = cuesJson)
        }
    }

    init {
        WatermelonCore.setEventCallback(eventCallback)
        thermalMonitor.startMonitoring()
        thermalMonitor.setOnThermalStatusChanged { status ->
            if (status.level >= com.watermelon.player.platform.ThermalMonitor.ThermalStatus.MODERATE.level) {
                WatermelonCore.pause()
            }
        }
    }

    fun loadVideo(uri: String) {
        _playerState.value = PlayerState(isPreparing = true, playbackState = 1)
        if (!audioFocus.requestFocus()) return
        WatermelonCore.setDataSource(uri)
        WatermelonCore.prepare()
    }

    fun setSurfaceProvider(provider: SurfaceProvider) {
        surfaceProvider = provider
        provider.getSurface()?.let { WatermelonCore.setSurface(it) }
        provider.setOnSurfaceReady { WatermelonCore.setSurface(it) }
        provider.setOnSurfaceDestroyed { WatermelonCore.setSurface(null) }
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            WatermelonCore.pause()
        } else {
            WatermelonCore.play()
        }
    }

    fun seekTo(positionUs: Long) {
        WatermelonCore.seekTo(positionUs)
    }

    fun loadSubtitle(path: String) {
        WatermelonCore.loadSubtitle(path)
    }

    fun setSubtitleOffset(offsetMs: Long) {
        WatermelonCore.setSubtitleOffset(offsetMs)
    }

    fun setSubtitleFont(fontPath: String) {
        WatermelonCore.setSubtitleFont(fontPath)
    }

    fun updatePosition() {
        viewModelScope.launch {
            val position = WatermelonCore.getCurrentPosition()
            _playerState.value = _playerState.value.copy(currentPositionUs = position)
        }
    }

    override fun onCleared() {
        audioFocus.abandonFocus()
        networkProvider.shutdown()
        surfaceProvider?.release()
        super.onCleared()
    }
}