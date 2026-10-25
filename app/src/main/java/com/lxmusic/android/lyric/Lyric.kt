package com.lxmusic.android.lyric

/**
 * One timed line of a lyric (or translation).
 * @param timeMs start time in milliseconds
 * @param text   the line content
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/** A parsed lyric, optionally with a parallel translation track. */
data class Lyric(
    val lines: List<LyricLine>,
    val translation: List<LyricLine> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Returns the index of the line active at [positionMs], or -1. */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        return result
    }

    /** Translation text for a given original-line index, matched by timestamp. */
    fun translationFor(index: Int): String? {
        if (index !in lines.indices || translation.isEmpty()) return null
        val t = lines[index].timeMs
        return translation.firstOrNull { kotlin.math.abs(it.timeMs - t) < 40 }?.text
    }
}

/** Parses LRC text into [Lyric]. Handles multiple timestamps per line and
 * "translation" detection (duplicate timestamps) when [separateTranslation]. */
object LrcParser {

    private val TIME_TAG = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(raw: String?, separateTranslation: Boolean = true): Lyric {
        if (raw.isNullOrBlank()) return Lyric(emptyList())
        val primary = ArrayList<LyricLine>()
        val secondary = ArrayList<LyricLine>()

        for (line in raw.split('\n', '\r')) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val tags = TIME_TAG.findAll(trimmed).toList()
            if (tags.isEmpty()) continue

            val text = trimmed.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) continue

            for (tag in tags) {
                val min = tag.groupValues[1].toLongOrNull() ?: continue
                val sec = tag.groupValues[2].toLongOrNull() ?: continue
                val frac = tag.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.substring(0, 3).toLong()
                }
                val time = min * 60_000 + sec * 1000 + ms

                if (separateTranslation && primary.any { it.timeMs == time }) {
                    // Same timestamp repeated -> treat as translation.
                    secondary.add(LyricLine(time, text))
                } else {
                    primary.add(LyricLine(time, text))
                }
            }
        }

        primary.sortBy { it.timeMs }
        secondary.sortBy { it.timeMs }
        if (primary.isEmpty() && secondary.isEmpty()) {
            // Embedded lyrics are often plain text without [mm:ss] tags (USLT etc).
            // Do not drop them: show the lines in order, evenly paced.
            val textLines = raw.replace("\r", "")
                .split("\n")
                .map { it.trim().trimStart('-') }
                .filter { it.isNotEmpty() && !it.startsWith("[") }
            if (textLines.isNotEmpty()) {
                val step = 4000L
                return Lyric(textLines.mapIndexed { i, t -> LyricLine(i * step, t) })
            }
        }
        return Lyric(primary, secondary)
    }
}