// app/src/main/kotlin/com/watermelon/player/viewmodel/PlayerViewModel.kt
package com.watermelon.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val currentPositionMs: Long = 0,
    val errorMessage: String? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun updateDuration(durationMs: Long) {
        _playerState.value = _playerState.value.copy(durationMs = durationMs)
    }

    fun updatePosition(positionMs: Long) {
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
    }

    fun setPlaying(playing: Boolean) {
        _playerState.value = _playerState.value.copy(isPlaying = playing)
    }

    fun setError(message: String?) {
        _playerState.value = _playerState.value.copy(errorMessage = message)
    }

    fun startPositionUpdates(getPosition: () -> Long) {
        viewModelScope.launch {
            while (true) {
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = getPosition()
                )
                kotlinx.coroutines.delay(250)
            }
        }
    }
}