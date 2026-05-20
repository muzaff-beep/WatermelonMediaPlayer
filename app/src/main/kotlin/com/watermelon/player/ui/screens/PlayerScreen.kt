// app/src/main/kotlin/com/watermelon/player/ui/screens/SettingsScreen.kt
package com.watermelon.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watermelon.player.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.back, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsItem(
                title = "About Watermelon",
                subtitle = "Version 0.1.0",
                onClick = { showAbout = true }
            )
            SettingsItem(
                title = "Codec plugins",
                subtitle = "Manage external codec plugins",
                onClick = { /* Navigate to plugin management */ }
            )
            SettingsItem(
                title = "Storage",
                subtitle = "Media scan preferences",
                onClick = { /* Navigate to storage settings */ }
            )
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About Watermelon") },
            text = {
                Column {
                    Text("Watermelon MediaPlayer")
                    Text("Version 0.1.0", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A hybrid Kotlin/Rust media player for Android.")
                    Text("Built with ExoPlayer, Jetpack Compose, and custom icon set.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            AppIcons.next,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}