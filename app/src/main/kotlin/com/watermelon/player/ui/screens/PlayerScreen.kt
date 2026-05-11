package com.watermelon.player.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.watermelon.player.player.WatermelonPlayer
import com.watermelon.player.subtitle.SubtitleManager
import com.watermelon.player.subtitle.SubtitleOverlay
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PlayerScreen(videoUri: Uri, subtitleFile: File? = null) {
    val context = LocalContext.current
    val player = remember { WatermelonPlayer(context) }
    val subtitleManager = remember { SubtitleManager() }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var offset by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Subtitle loading
    LaunchedEffect(subtitleFile) {
        subtitleFile?.let { subtitleManager.loadFromFile(it) }
    }

    // Playback state listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerScreen.isPlaying = isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = player.getDuration()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Periodic position update
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.getCurrentPosition()
            delay(250)
        }
    }

    // Load video
    LaunchedEffect(videoUri) {
        player.setSource(videoUri)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = this@PlayerScreen.player.player
                    useController = false
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtitle overlay
        if (subtitleManager.hasSubtitles()) {
            SubtitleOverlay(
                cues = subtitleManager.getCurrentCues(currentPosition),
                fontSize = 20f,
                fontFamily = FontFamily.Default,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls
        if (showControls) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Seek bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { player.seekTo((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play/Pause
                    IconButton(
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        modifier = Modifier.focusable()
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Subtitle Offset
                    if (subtitleManager.hasSubtitles()) {
                        TextButton(
                            onClick = { /* open offset dialog */ }
                        ) {
                            Text("Offset: ${offset}ms", color = Color.White)
                        }
                    }
                }
            }
        }

        // Toggle controls visibility
        IconButton(
            onClick = { showControls = !showControls },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .focusable()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Controls",
                tint = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
}