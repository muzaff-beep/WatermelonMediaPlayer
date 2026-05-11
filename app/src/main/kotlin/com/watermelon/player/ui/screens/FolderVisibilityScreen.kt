package com.watermelon.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watermelon.player.ui.viewmodel.FolderVisibilityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVisibilityScreen(
    onBack: () -> Unit,
    viewModel: FolderVisibilityViewModel = viewModel()
) {
    val folders by viewModel.folders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Folders") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No folders found. Scan your device first.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(folders, key = { it.path }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = folder.path,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = folder.isVisible,
                            onCheckedChange = { checked ->
                                viewModel.toggleFolder(folder.path, checked)
                            }
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}