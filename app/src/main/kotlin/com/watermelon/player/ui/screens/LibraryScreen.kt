// app/src/main/kotlin/com/watermelon/player/ui/screens/LibraryScreen.kt
// Part 1 of 2 — Main screen logic. Part 2 contains helper composables.

package com.watermelon.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.watermelon.player.util.ThumbnailProvider
import com.watermelon.player.viewmodel.LibraryViewModel
import com.watermelon.player.viewmodel.SortMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoSelected: (String) -> Unit,
    onFolderVisibility: () -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val allVideos by viewModel.videos.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDensityMenu by remember { mutableStateOf(false) }

    val filteredVideos = remember(allVideos, searchQuery) {
        if (searchQuery.isBlank()) allVideos
        else allVideos.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    val groupedVideos = remember(filteredVideos) {
        filteredVideos.groupBy { File(it.uri).parent ?: "Unknown" }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Watermelon") },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showDensityMenu = true }) {
                            Icon(Icons.Default.GridView, contentDescription = "Layout density")
                        }
                        IconButton(onClick = { viewModel.toggleGridList() }) {
                            Icon(
                                if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle layout"
                            )
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = onFolderVisibility) {
                            Icon(Icons.Default.Folder, contentDescription = "Folder visibility")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search videos...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (isScanning && allVideos.isEmpty()) {
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
        } else if (filteredVideos.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (searchQuery.isNotBlank()) "No videos match \"$searchQuery\""
                    else "No videos found",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(
                        padding.calculateTopPadding() + 8.dp,
                        8.dp,
                        8.dp,
                        8.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredVideos, key = { video -> video.id }) { video ->
                        VideoGridItem(
                            video = video,
                            onClick = { onVideoSelected(video.uri) }
                        )
                    }
                }
            } else {
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
                            FolderHeader(
                                folderName = folderName,
                                count = videoList.size
                            )
                        }
                        items(
                            items = videoList,
                            key = { video -> video.id }
                        ) { video ->
                            VideoListItem(
                                video = video,
                                onClick = { onVideoSelected(video.uri) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Sort menu
    DropdownMenu(
        expanded = showSortMenu,
        onDismissRequest = { showSortMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("Name") },
            onClick = {
                viewModel.setSortMode(SortMode.NAME)
                showSortMenu = false
            },
            leadingIcon = {
                if (sortMode == SortMode.NAME) Icon(Icons.Default.Check, null)
            }
        )
        DropdownMenuItem(
            text = { Text("Date") },
            onClick = {
                viewModel.setSortMode(SortMode.DATE)
                showSortMenu = false
            },
            leadingIcon = {
                if (sortMode == SortMode.DATE) Icon(Icons.Default.Check, null)
            }
        )
        DropdownMenuItem(
            text = { Text("Size") },
            onClick = {
                viewModel.setSortMode(SortMode.SIZE)
                showSortMenu = false
            },
            leadingIcon = {
                if (sortMode == SortMode.SIZE) Icon(Icons.Default.Check, null)
            }
        )
    }

    // Density menu
    DropdownMenu(
        expanded = showDensityMenu,
        onDismissRequest = { showDensityMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("Compact (3 columns)") },
            onClick = {
                viewModel.setGridColumns(3)
                showDensityMenu = false
            },
            leadingIcon = {
                if (gridColumns == 3) Icon(Icons.Default.Check, null)
            }
        )
        DropdownMenuItem(
            text = { Text("Normal (2 columns)") },
            onClick = {
                viewModel.setGridColumns(2)
                showDensityMenu = false
            },
            leadingIcon = {
                if (gridColumns == 2) Icon(Icons.Default.Check, null)
            }
        )
        DropdownMenuItem(
            text = { Text("Comfortable (1 column)") },
            onClick = {
                viewModel.setGridColumns(1)
                showDensityMenu = false
            },
            leadingIcon = {
                if (gridColumns == 1) Icon(Icons.Default.Check, null)
            }
        )
    }
}

@Composable
fun VideoGridItem(video: VideoEntity, onClick: () -> Unit) {
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(video.id) {
        thumbnail = ThumbnailProvider.getThumbnail(
            context = context,
            uriString = video.uri,
            width = 360,
            height = 240
        )
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "Video",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

@Composable
fun VideoListItem(video: VideoEntity, onClick: () -> Unit) {
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(video.id) {
        thumbnail = ThumbnailProvider.getThumbnail(
            context = context,
            uriString = video.uri,
            width = 180,
            height = 120
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = video.displayName,
                    modifier = Modifier
                        .width(120.dp)
                        .height(68.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(68.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "Video",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = formatDuration(video.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = formatSize(video.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FolderHeader(folderName: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = folderName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}