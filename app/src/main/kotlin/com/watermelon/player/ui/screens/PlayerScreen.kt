// app/src/main/kotlin/com/watermelon/player/ui/screens/PlayerScreen.kt
// Full video player screen with controls overlay and subtitle rendering.

package com.watermelon.player.ui.screens

import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watermelon.player.platform.SurfaceProvider
import com.watermelon.player.ui.components.tvFocusBorder
import com.watermelon.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoUri: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    var subtitleVisible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    // Position update ticker
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            viewModel.updatePosition()
            delay(250)
        }
    }

    // Load video on first composition
    LaunchedEffect(videoUri) {
        viewModel.loadVideo(videoUri)
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls && playerState.isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .tvFocusBorder(isFocused)
            .clickable { showControls = !showControls }
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    val surfaceProvider = SurfaceProvider(this)
                    viewModel.setSurfaceProvider(surfaceProvider)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtitle overlay
        if (subtitleVisible && playerState.subtitleCuesJson != "[]") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = parseSubtitleText(playerState.subtitleCuesJson),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp)
                )
            }
        }

        // Top bar (back button)
        if (showControls) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        // Error message
        playerState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Bottom controls
        if (showControls && playerState.playbackState != 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                // Progress bar
                Slider(
                    value = if (playerState.durationUs > 0)
                        playerState.currentPositionUs.toFloat() / playerState.durationUs.toFloat()
                    else 0f,
                    onValueChange = { fraction ->
                        val newPos = (fraction * playerState.durationUs).toLong()
                        viewModel.seekTo(newPos)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause
                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(
                            if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    // Subtitles
                    IconButton(onClick = { subtitleVisible = !subtitleVisible }) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            tint = if (subtitleVisible) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            }
        }

        // Loading indicator
        if (playerState.isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}

/** Parse the first active cue text from the cues JSON array. */
private fun parseSubtitleText(cuesJson: String): String {
    return try {
        val json = org.json.JSONArray(cuesJson)
        if (json.length() > 0) {
            json.getJSONObject(0).getString("text")
        } else ""
    } catch (e: Exception) {
        ""
    }
}