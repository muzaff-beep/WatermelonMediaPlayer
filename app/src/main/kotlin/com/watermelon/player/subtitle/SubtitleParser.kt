package com.watermelon.player.subtitle

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

object SubtitleParser {

    fun parseSrt(file: File, encoding: Charset = Charsets.UTF_8): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = readLines(file, encoding)
        var index = 0

        while (index < lines.size) {
            // skip blank lines
            if (lines[index].isBlank()) { index++; continue }

            // read index (ignore)
            val indexLine = lines[index].trim()
            index++

            // read timestamp
            if (index >= lines.size) break
            val timestamp = lines[index].trim()
            index++
            val (start, end) = parseTimestamp(timestamp) ?: continue

            // read text until blank line
            val textBuilder = StringBuilder()
            while (index < lines.size && lines[index].isNotBlank()) {
                if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                textBuilder.append(lines[index].trim())
                index++
            }

            val text = cleanTags(textBuilder.toString())
            cues.add(SubtitleCue(start, end, text))
        }

        return cues
    }

    private fun readLines(file: File, encoding: Charset): List<String> {
        val lines = mutableListOf<String>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file), encoding)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lines
    }

    private fun parseTimestamp(line: String): Pair<Long, Long>? {
        val regex = Regex("""(\d{2}:\d{2}:\d{2}[.,]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[.,]\d{3})""")
        val match = regex.find(line) ?: return null
        val start = timeToMs(match.groupValues[1])
        val end = timeToMs(match.groupValues[2])
        return Pair(start, end)
    }

    private fun timeToMs(time: String): Long {
        val parts = time.split(":", ".", ",")
        val h = parts[0].toLong()
        val m = parts[1].toLong()
        val s = parts[2].toLong()
        val ms = parts[3].toLong()
        return h * 3600000 + m * 60000 + s * 1000 + ms
    }

    private fun cleanTags(text: String): String {
        return text.replace(Regex("<[^>]*>"), "")    // strip HTML-like tags
    }
}