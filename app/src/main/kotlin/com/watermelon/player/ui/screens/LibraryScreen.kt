// app/src/main/kotlin/com/watermelon/player/ui/screens/LibraryScreen.kt
package com.watermelon.player.ui.screens

import android.media.ThumbnailUtils
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watermelon.player.database.VideoEntity
import com.watermelon.player.viewmodel.LibraryViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoSelected: (String) -> Unit,
    onFolderVisibility: () -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val videos by viewModel.videos.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watermelon") },
                actions = {
                    IconButton(onClick = onFolderVisibility) {
                        Icon(Icons.Default.Folder, contentDescription = "Folder visibility")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (isScanning && videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Scanning media...")
                    if (scanProgress.second > 0) {
                        Text(
                            "${scanProgress.first}/${scanProgress.second}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else if (videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No videos found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val groupedVideos = remember(videos) {
                videos.groupBy { File(it.uri).parent ?: "Unknown" }
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    padding.calculateTopPadding() + 8.dp,
                    8.dp,
                    8.dp,
                    8.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                groupedVideos.forEach { (folderName, videoList) ->
                    item(key = "header_$folderName") {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
                        )
                    }
                    items(
                        items = videoList,
                        key = { video -> video.id }
                    ) { video ->
                        VideoGridItem(
                            video = video,
                            onClick = { onVideoSelected(video.uri) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoGridItem(video: VideoEntity, onClick: () -> Unit) {
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(video.id) {
        try {
            thumbnail = ThumbnailUtils.createVideoThumbnail(
                video.uri.substringAfterLast("/"),
                MediaStore.Video.Thumbnails.MINI_KIND
            )
        } catch (_: Exception) {}
    }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = video.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = "Video",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDuration(video.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
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