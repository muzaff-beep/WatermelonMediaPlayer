// app/src/main/kotlin/com/watermelon/player/ui/screens/PlayerScreen.kt
package com.watermelon.player.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.watermelon.player.ui.theme.AppIcons
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.FileFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var volume by remember { mutableStateOf(1f) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var availableSubtitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSubtitle by remember { mutableStateOf<String?>(null) }
    var playerViewRef by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(videoUri) {
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
        player.playWhenReady = true
        isPlaying = true
        scanSubtitles()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                    isPlaying = false
                }
                Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                    isPlaying = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = player.currentPosition
            durationMs = if (player.duration > 0) player.duration else 0
            delay(250)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    fun scanSubtitles() {
        try {
            val videoFile = File(videoUri.replace("file://", ""))
            val parentDir = videoFile.parentFile ?: return
            val subtitleFiles = parentDir.listFiles(FileFilter {
                it.extension.equals("srt", ignoreCase = true) ||
                it.extension.equals("ass", ignoreCase = true)
            })
            availableSubtitles = subtitleFiles?.map { it.name } ?: emptyList()
        } catch (_: Exception) {
            availableSubtitles = emptyList()
        }
    }

    fun captureScreenshot() {
        try {
            val view = (playerViewRef as? PlayerView)
            val bitmap = view?.bitmap
            if (bitmap != null) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "watermelon_screenshot_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } catch (_: Exception) {}
    }

    fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val activity = context as? Activity ?: return
            if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                try {
                    activity.enterPictureInPictureMode(
                        android.app.PictureInPictureParams.Builder().build()
                    )
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).also {
                    it.player = player
                    playerViewRef = it
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(AppIcons.back, contentDescription = "Back", tint = Color.White)
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Slider(
                    value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { fraction ->
                        val newPos = (fraction * durationMs).toLong()
                        player.seekTo(newPos)
                        currentPositionMs = newPos
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        player.seekTo(maxOf(0, player.currentPosition - 10_000))
                    }) {
                        Icon(AppIcons.rewind, contentDescription = "Rewind", tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.play()
                            isPlaying = true
                        }
                    }) {
                        Icon(
                            if (isPlaying) AppIcons.pause else AppIcons.play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    IconButton(onClick = {
                        player.seekTo(minOf(player.duration, player.currentPosition + 10_000))
                    }) {
                        Icon(AppIcons.fastForward, contentDescription = "Fast forward", tint = Color.White)
                    }
                    IconButton(onClick = { captureScreenshot() }) {
                        Icon(AppIcons.download, contentDescription = "Screenshot", tint = Color.White)
                    }
                    IconButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                        Icon(AppIcons.volumeUp, contentDescription = "Volume", tint = Color.White)
                    }
                    IconButton(onClick = { showSubtitleMenu = true }) {
                        Icon(
                            if (selectedSubtitle != null) AppIcons.subtitlesOn else AppIcons.subtitlesOff,
                            contentDescription = "Subtitles",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { enterPiP() }) {
                        Icon(AppIcons.fullscreenExit, contentDescription = "PiP", tint = Color.White)
                    }
                }
                AnimatedVisibility(visible = showVolumeSlider) {
                    Slider(
                        value = volume,
                        onValueChange = { v ->
                            volume = v
                            player.volume = v
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White
                        )
                    )
                }
            }
        }
    }

    if (showSubtitleMenu) {
        AlertDialog(
            onDismissRequest = { showSubtitleMenu = false },
            title = { Text("Select Subtitle") },
            text = {
                if (availableSubtitles.isEmpty()) {
                    Text("No subtitle files found in video directory.")
                } else {
                    Column {
                        availableSubtitles.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSubtitle = sub
                                        showSubtitleMenu = false
                                    }
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = sub,
                                    color = if (sub == selectedSubtitle) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleMenu = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}