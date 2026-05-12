package com.watermelon.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.MotionEvent
import android.view.View
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
    var audioTrackIndex by remember { mutableStateOf(-1) }
    var subtitleTrackIndex by remember { mutableStateOf(-1) }
    val audioTracks = remember { player.getAudioTracks() }
    val subtitleTracks = remember { player.getSubtitleTracks() }
    var showTrackSelector by remember { mutableStateOf(false) }

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

    // Gesture detector for volume and brightness
    AndroidView(
        factory = { ctx ->
            object : View(ctx) {
                var initialX = 0f
                var initialY = 0f
                var initialVolume = 0
                var initialBrightness = 0f
                var gestureActive = false

                override fun onTouchEvent(event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = event.x
                            initialY = event.y
                            gestureActive = true
                            initialVolume = (context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
                                .getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            initialBrightness = (context as Activity).window.attributes.screenBrightness
                            if (initialBrightness < 0f) initialBrightness = 0.5f
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!gestureActive) return false
                            val dx = event.x - initialX
                            val dy = event.y - initialY
                            if (Math.abs(dx) > Math.abs(dy)) {
                                // Seek gesture – handled by slider, skip
                            } else {
                                // Volume / Brightness
                                val fraction = dy / height
                                if (initialX < width / 2) {
                                    // Brightness left side
                                    var newBrightness = initialBrightness - fraction
                                    newBrightness = newBrightness.coerceIn(0f, 1f)
                                    (context as Activity).window.attributes.screenBrightness = newBrightness
                                    (context as Activity).window.addFlags(android.view.WindowManager.LayoutParams.FLAGS_CHANGED)
                                } else {
                                    // Volume right side
                                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    var newVolume = initialVolume - (fraction * maxVolume).toInt()
                                    newVolume = newVolume.coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0)
                                }
                            }
                            true
                        }
                        else -> {
                            gestureActive = false
                            false
                        }
                    }
                    return super.onTouchEvent(event)
                }
            }.apply {
                layoutParams = View.LayoutParams(0, 0) // invisible, just touch interceptor
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

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

                    TextButton(onClick = { showTrackSelector = true }) {
                        Text("Tracks", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = { PiPManager.enterPiP(activity) }) {
                        Text("PiP", color = Color.White)
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

    if (showTrackSelector) {
        AlertDialog(
            onDismissRequest = { showTrackSelector = false },
            title = { Text("Select Track") },
            text = {
                Column {
                    Text("Audio", style = MaterialTheme.typography.labelLarge)
                    audioTracks.forEachIndexed { index, name ->
                        Row {
                            RadioButton(
                                selected = audioTrackIndex == index,
                                onClick = {
                                    audioTrackIndex = index
                                    player.setAudioTrack(index)
                                }
                            )
                            Text(name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Subtitles", style = MaterialTheme.typography.labelLarge)
                    subtitleTracks.forEachIndexed { index, name ->
                        Row {
                            RadioButton(
                                selected = subtitleTrackIndex == index,
                                onClick = {
                                    subtitleTrackIndex = index
                                    player.setSubtitleTrack(index)
                                }
                            )
                            Text(name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSelector = false }) {
                    Text("Close")
                }
            }
        )
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}