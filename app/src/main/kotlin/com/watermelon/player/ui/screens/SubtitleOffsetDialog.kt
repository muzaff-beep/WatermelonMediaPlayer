package com.watermelon.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SubtitleOffsetDialog(
    initialOffsetMs: Long,
    onApply: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var offset by remember { mutableStateOf(initialOffsetMs / 1000f) }  // in seconds for ease

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle Offset") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${offset.toInt()} seconds")
                Slider(
                    value = offset,
                    onValueChange = { offset = it },
                    valueRange = -5f..5f,
                    steps = 19,  // each 0.5s
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply((offset * 1000).toLong()) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}