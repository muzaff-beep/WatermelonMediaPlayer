// app/src/main/kotlin/com/watermelon/player/ui/screens/FolderVisibilityScreen.kt
// Folder visibility management screen. Toggle which folders are scanned.

package com.watermelon.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watermelon.player.viewmodel.FolderItem
import com.watermelon.player.viewmodel.FolderVisibilityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVisibilityScreen(
    onBack: () -> Unit,
    viewModel: FolderVisibilityViewModel = viewModel()
) {
    val folders by viewModel.folders.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folder visibility") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add folder")
                    }
                }
            )
        }
    ) { padding ->
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No folder visibility rules")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("Add folder to manage visibility")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(padding.calculateTopPadding() + 8.dp, 8.dp, 8.dp, 8.dp)
            ) {
                items(folders, key = { it.folderUri }) { folder ->
                    FolderVisibilityItem(
                        folder = folder,
                        onToggle = { viewModel.toggleVisibility(folder.folderUri) },
                        onRemove = { viewModel.removeOverride(folder.folderUri) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var folderUri by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add folder path") },
            text = {
                OutlinedTextField(
                    value = folderUri,
                    onValueChange = { folderUri = it },
                    label = { Text("Folder URI") },
                    placeholder = { Text("/storage/emulated/0/Movies") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderUri.isNotBlank()) {
                            viewModel.addFolder(folderUri.trim())
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FolderVisibilityItem(
    folder: FolderItem,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (folder.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = if (folder.isVisible)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = folder.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = folder.folderUri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}