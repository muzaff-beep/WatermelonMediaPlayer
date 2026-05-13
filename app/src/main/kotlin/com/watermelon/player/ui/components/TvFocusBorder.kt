// app/src/main/kotlin/com/watermelon/player/ui/components/TvFocusBorder.kt
// D-pad focus indicator for Android TV navigation.

package com.watermelon.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Modifier that adds a visible border when the composable has D-pad focus.
 * Used on TV targets for accessibility compliance.
 */
@Composable
fun Modifier.tvFocusBorder(isFocused: Boolean): Modifier = this.then(
    if (isFocused) {
        Modifier.border(3.dp, Color(0xFF4CAF50))
    } else {
        Modifier
    }
)