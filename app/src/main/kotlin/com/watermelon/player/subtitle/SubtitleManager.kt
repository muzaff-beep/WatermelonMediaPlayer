package com.watermelon.player.subtitle

import java.io.File
import java.nio.charset.Charset

class SubtitleManager {
    private var cues: List<SubtitleCue> = emptyList()
    var offsetMs: Long = 0
        private set

    fun loadFromFile(file: File, encoding: Charset = Charsets.UTF_8): Boolean {
        return try {
            cues = SubtitleParser.parseSrt(file, encoding)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setOffset(ms: Long) {
        offsetMs = ms
    }

    fun getCurrentCues(positionMs: Long): List<SubtitleCue> {
        val adjusted = positionMs + offsetMs
        return cues.filter { adjusted in it.startMs..it.endMs }
    }

    fun hasSubtitles(): Boolean = cues.isNotEmpty()

    fun clear() {
        cues = emptyList()
        offsetMs = 0
    }
}