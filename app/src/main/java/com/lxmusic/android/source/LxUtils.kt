package com.lxmusic.android.source

import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.model.QualityInfo

/** Helpers ported from lx-music-desktop src/renderer/utils and musicSdk/utils. */
object LxUtils {

    /** Decode HTML entities commonly embedded in platform responses. */
    fun decodeName(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        return str
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    /** Format a duration in seconds to "mm:ss". */
    fun formatPlayTime(seconds: Int): String {
        if (seconds <= 0) return "00:00"
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    /** Human readable file size, matching LX's sizeFormate. */
    fun sizeFormate(size: Long): String {
        if (size <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = size.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) { value /= 1024; i++ }
        return if (i == 0) "${size}B" else "%.1f%s".format(value, units[i])
    }

    /** Join multiple singer names with the LX default separator "、". */
    fun formatSingerName(names: List<String>): String =
        decodeName(names.filter { it.isNotBlank() }.joinToString("、"))

    /** Parse "mm:ss" into seconds. */
    fun intervalToSeconds(interval: String?): Int {
        if (interval.isNullOrBlank()) return 0
        val parts = interval.split(":")
        var total = 0
        var unit = 1
        for (i in parts.indices.reversed()) {
            total += (parts[i].toIntOrNull() ?: 0) * unit
            unit *= 60
        }
        return total
    }
}

/** Common contract every native search provider implements. */
interface SearchProvider {
    val source: LxSource

    /**
     * Search for music.
     * @return a list of matches (may be empty) and the total page count.
     */
    suspend fun search(keyword: String, page: Int = 1, limit: Int = 30): SearchPage
}

data class SearchPage(
    val list: List<MusicInfo>,
    val total: Int,
    val page: Int,
    val allPage: Int,
    val source: LxSource,
)

/** Build a stable app-level id from source + platform song id. */
fun musicId(source: String, songmid: String): String = "${source}_$songmid"

/** Quality map convenience. */
fun qualities(vararg pairs: Pair<String, QualityInfo>): Map<String, QualityInfo> = pairs.toMap()