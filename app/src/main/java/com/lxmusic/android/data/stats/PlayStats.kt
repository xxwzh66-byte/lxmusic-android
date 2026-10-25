package com.lxmusic.android.data.stats

import com.lxmusic.android.data.model.MusicInfo
import java.util.Calendar
import java.util.TimeZone

/** Which window the statistics screen is looking at. */
enum class StatsRange(val label: String) {
    TODAY("今日"),
    WEEK("本周"),
    ALL("全部"),
}

/** One play of one track. The atom the statistics are built from. */
data class PlayRecord(val music: MusicInfo, val playedAt: Long)

/** A track plus how often it was played inside the selected window. */
data class PlayTally(val music: MusicInfo, val count: Int, val lastPlayedAt: Long)

/**
 * One column of the histogram. [label] is pre-formatted for display, because
 * the bucket is an hour for [StatsRange.TODAY] and a calendar day otherwise.
 */
data class StatsBucket(val label: String, val count: Int)

/**
 * Pure aggregation for the play-count statistics. Nothing here touches Android
 * or the database, so the whole thing is unit-testable against a fixed clock.
 *
 * A "day" is a calendar day in the given [TimeZone] and a "week" starts on
 * Monday, matching what a Chinese-language user expects from 「今日」 and
 * 「本周」. Both are computed with [Calendar] rather than java.time,
 * because minSdk is 24 and core-library desugaring is not enabled.
 */
object PlayStats {

    /** Number of columns shown for [StatsRange.TODAY] (one per hour). */
    const val HOUR_BUCKETS = 24

    /** Number of columns shown for [StatsRange.WEEK] (one per day). */
    const val WEEK_BUCKETS = 7

    /** Number of columns shown for [StatsRange.ALL]. */
    const val ALL_BUCKETS = 30

    /** Midnight at the start of the calendar day containing [now]. */
    fun dayStart(now: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        midnight(now, tz)

    /** Midnight at the start of the hour containing [now]. */
    fun hourStart(now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = now
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * Midnight on the Monday of the week containing [now].
     *
     * [Calendar.DAY_OF_WEEK] is resolved inside the week defined by
     * [Calendar.firstDayOfWeek], so a Sunday belongs to the week that started the
     * previous Monday rather than to the day after it.
     */
    fun weekStart(now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = midnight(now, tz)
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return midnight(c.timeInMillis, tz)
    }

    /**
     * Inclusive lower bound of [range], or 0 for [StatsRange.ALL] (everything
     * ever recorded).
     */
    fun windowStart(
        range: StatsRange,
        now: Long,
        tz: TimeZone = TimeZone.getDefault(),
    ): Long = when (range) {
        StatsRange.TODAY -> dayStart(now, tz)
        StatsRange.WEEK -> weekStart(now, tz)
        StatsRange.ALL -> 0L
    }

    /** Keeps only the records inside [range]. */
    fun inRange(
        records: List<PlayRecord>,
        range: StatsRange,
        now: Long,
        tz: TimeZone = TimeZone.getDefault(),
    ): List<PlayRecord> {
        val from = windowStart(range, now, tz)
        return records.filter { it.playedAt >= from && it.playedAt <= now }
    }

    /**
     * Collapses [records] into one row per track, most-played first.
     *
     * Ties break on the most recent play so the order is deterministic instead of
     * depending on the order the rows happened to come back from SQLite.
     */
    fun tally(records: List<PlayRecord>): List<PlayTally> =
        records.groupBy { it.music.id }
            .map { (_, plays) ->
                PlayTally(
                    music = plays.maxByOrNull { it.playedAt }!!.music,
                    count = plays.size,
                    lastPlayedAt = plays.maxOf { it.playedAt },
                )
            }
            .sortedWith(compareByDescending<PlayTally> { it.count }.thenByDescending { it.lastPlayedAt })

    /** Total number of plays in [records]. */
    fun totalPlays(records: List<PlayRecord>): Int = records.size

    /**
     * The histogram behind the bar chart: one column per hour for
     * [StatsRange.TODAY], one per day for the other ranges.
     *
     * Columns are emitted oldest-first and include the empty ones, so the chart
     * keeps a stable width instead of collapsing to only the busy slots.
     */
    fun buckets(
        records: List<PlayRecord>,
        range: StatsRange,
        now: Long,
        tz: TimeZone = TimeZone.getDefault(),
    ): List<StatsBucket> = when (range) {
        StatsRange.TODAY -> hourBuckets(records, now, tz)
        StatsRange.WEEK -> dayBuckets(records, now, tz, WEEK_BUCKETS)
        StatsRange.ALL -> dayBuckets(records, now, tz, ALL_BUCKETS)
    }

    private fun hourBuckets(
        records: List<PlayRecord>,
        now: Long,
        tz: TimeZone,
    ): List<StatsBucket> {
        val first = hourStart(dayStart(now, tz), tz)
        val counts = IntArray(HOUR_BUCKETS)
        for (r in records) {
            val index = ((r.playedAt - first) / HOUR_MS).toInt()
            if (index in counts.indices) counts[index]++
        }
        return counts.mapIndexed { i, n -> StatsBucket(twoDigits(i) + ":00", n) }
    }

    private fun dayBuckets(
        records: List<PlayRecord>,
        now: Long,
        tz: TimeZone,
        days: Int,
    ): List<StatsBucket> {
        val today = dayStart(now, tz)
        // Oldest column first: walk back days-1 days from today.
        val starts = (days - 1 downTo 0).map { back ->
            val c = Calendar.getInstance(tz)
            c.timeInMillis = today
            c.add(Calendar.DAY_OF_MONTH, -back)
            midnight(c.timeInMillis, tz)
        }
        val counts = IntArray(days)
        for (r in records) {
            for (i in starts.indices.reversed()) {
                if (r.playedAt >= starts[i]) { counts[i]++; break }
            }
        }
        return starts.mapIndexed { i, start ->
            val c = Calendar.getInstance(tz)
            c.timeInMillis = start
            StatsBucket(
                (c.get(Calendar.MONTH) + 1).toString() + "/" + c.get(Calendar.DAY_OF_MONTH),
                counts[i],
            )
        }
    }

    private fun midnight(at: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = at
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun twoDigits(v: Int): String = if (v < 10) "0" + v else v.toString()

    private const val HOUR_MS = 60L * 60L * 1000L
}
