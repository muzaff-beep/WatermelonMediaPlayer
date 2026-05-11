package com.watermelon.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.tvFocusBorder() = this
    .focusable()
    .onFocusChanged { /* currently no action, just enable D-pad */ }
    .border(
        width = if (true) 0.dp else 2.dp,  // placeholder for dynamic border
        color = Color.Transparent
    )