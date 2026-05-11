package com.watermelon.player.subtitle

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val style: CueStyle = CueStyle()
)

data class CueStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val color: Int? = null,
    val fontSize: Float? = null
)