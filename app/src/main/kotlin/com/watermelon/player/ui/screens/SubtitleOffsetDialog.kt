// app/src/main/kotlin/com/watermelon/player/ui/screens/SubtitleOffsetDialog.kt
// Dialog for adjusting subtitle timing offset.

package com.watermelon.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SubtitleOffsetDialog(
    onDismiss: () -> Unit,
    onOffsetSet: (Long) -> Unit
) {
    var offsetMs by remember { mutableStateOf(0L) }
    var textValue by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle offset") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Adjust subtitle timing",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        textValue = newValue
                        offsetMs = newValue.toLongOrNull() ?: 0L
                    },
                    label = { Text("Offset (ms)") },
                    placeholder = { Text("Positive = delay, negative = advance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (offsetMs > 0) "Delay by ${offsetMs}ms"
                    else if (offsetMs < 0) "Advance by ${-offsetMs}ms"
                    else "No offset",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onOffsetSet(offsetMs) }) {
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