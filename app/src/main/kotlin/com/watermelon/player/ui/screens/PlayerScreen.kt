package com.watermelon.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.watermelon.player.player.MediaSessionManager
import com.watermelon.player.player.PiPManager
import com.watermelon.player.player.WatermelonPlayer
import com.watermelon.player.subtitle.SubtitleManager
import com.watermelon.player.subtitle.SubtitleOverlay
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PlayerScreen(videoUri: Uri, subtitleFile: File? = null) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val player = remember { WatermelonPlayer(context) }
    val subtitleManager = remember { SubtitleManager() }
    val mediaSession = remember { MediaSessionManager(context, player) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            mediaSession.release()
        }
    }

    LaunchedEffect(subtitleFile) {
        subtitleFile?.let { subtitleManager.loadFromFile(it) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                mediaSession.updatePlaybackState(playing, player.getCurrentPosition(), player.getDuration())
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = player.getDuration()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.getCurrentPosition()
            delay(250)
        }
    }

    LaunchedEffect(videoUri) {
        player.setSource(videoUri)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        if (subtitleManager.hasSubtitles()) {
            SubtitleOverlay(
                cues = subtitleManager.getCurrentCues(currentPosition),
                fontSize = 20f,
                fontFamily = FontFamily.Default,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showControls) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { player.seekTo((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    if (subtitleManager.hasSubtitles()) {
                        TextButton(onClick = { showOffsetDialog = true }) {
                            Text("Offset", color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // PiP button (API 26+)
                    TextButton(onClick = { PiPManager.enterPiP(activity) }) {
                        Text("PiP", color = Color.White)
                    }
                }
            }
        }

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

    if (showOffsetDialog) {
        SubtitleOffsetDialog(
            initialOffsetMs = offset,
            onApply = { newOffset ->
                offset = newOffset
                subtitleManager.setOffset(newOffset)
                showOffsetDialog = false
            },
            onDismiss = { showOffsetDialog = false }
        )
    }
}

// Helper to get Activity from Context
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}