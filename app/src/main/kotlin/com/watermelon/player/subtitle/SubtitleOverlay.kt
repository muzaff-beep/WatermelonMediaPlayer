package com.watermelon.player.subtitle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.player.config.RegionalConfig

@Composable
fun SubtitleOverlay(
    cues: List<SubtitleCue>,
    modifier: Modifier = Modifier,
    fontSize: Float = 20f,
    languageHint: String? = null
) {
    val displayText = cues.joinToString("\n") { it.text }
    if (displayText.isBlank()) return

    val fontFamily = when {
        languageHint == null -> FontFamily.Default
        else -> try {
            FontFamily(FontFamily.SansSerif) // fallback; real font loading handled in theme
        } catch (_: Exception) { FontFamily.Default }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = displayText,
            color = Color.White,
            fontSize = fontSize.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = (fontSize * 1.4).sp,
            modifier = Modifier.padding(bottom = 48.dp)
        )
    }
}