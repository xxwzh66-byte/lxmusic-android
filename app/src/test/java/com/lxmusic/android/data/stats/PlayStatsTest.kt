package com.lxmusic.android.data.stats

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The statistics are pure functions of (records, range, now, timezone), so every
 * case here pins the clock and the zone instead of depending on the machine.
 */
class PlayStatsTest {

    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long {
        val c = Calendar.getInstance(tz)
        c.clear()
        c.set(y, mo - 1, d, h, mi, 0)
        return c.timeInMillis
    }

    private fun track(id: String) =
        MusicInfo(id = id, source = "local", songmid = id, name = "Track " + id, singer = "Singer")

    private fun record(id: String, whenMs: Long) = PlayRecord(track(id), whenMs)

    // Wednesday, 2026-10-14, 15:30 local.
    private val now = at(2026, 10, 14, 15, 30)

    @Test
    fun dayStartStripsTheTimeOfDay() {
        assertEquals(at(2026, 10, 14, 0, 0), PlayStats.dayStart(now, tz))
    }

    @Test
    fun hourStartStripsMinutesAndSeconds() {
        assertEquals(at(2026, 10, 14, 15, 0), PlayStats.hourStart(now, tz))
    }

    @Test
    fun weekStartsOnMonday() {
        // Wednesday belongs to the Monday two days earlier.
        assertEquals(at(2026, 10, 12, 0, 0), PlayStats.weekStart(now, tz))
    }

    @Test
    fun sundayBelongsToTheWeekThatStartedOnMonday() {
        val sunday = at(2026, 10, 18, 9, 0)
        assertEquals(at(2026, 10, 12, 0, 0), PlayStats.weekStart(sunday, tz))
    }

    @Test
    fun windowStartOfAllIsEpoch() {
        assertEquals(0L, PlayStats.windowStart(StatsRange.ALL, now, tz))
    }

    @Test
    fun windowStartOfTodayIsMidnight() {
        assertEquals(at(2026, 10, 14, 0, 0), PlayStats.windowStart(StatsRange.TODAY, now, tz))
    }

    @Test
    fun inRangeDropsYesterdayWhenLookingAtToday() {
        val records = listOf(
            record("a", at(2026, 10, 13, 23, 59)),
            record("a", at(2026, 10, 14, 0, 1)),
            record("a", at(2026, 10, 14, 15, 30)),
        )
        assertEquals(2, PlayStats.inRange(records, StatsRange.TODAY, now, tz).size)
    }

    @Test
    fun inRangeOfWeekKeepsMondayButNotSundayBefore() {
        val records = listOf(
            record("a", at(2026, 10, 11, 23, 0)),   // Sunday, previous week
            record("a", at(2026, 10, 12, 0, 1)),    // Monday, this week
        )
        assertEquals(1, PlayStats.inRange(records, StatsRange.WEEK, now, tz).size)
    }

    @Test
    fun tallyCountsEachTrackAndSortsByPlayCount() {
        val records = listOf(
            record("a", at(2026, 10, 14, 9, 0)),
            record("b", at(2026, 10, 14, 10, 0)),
            record("b", at(2026, 10, 14, 11, 0)),
            record("c", at(2026, 10, 14, 12, 0)),
        )
        val tallies = PlayStats.tally(records)
        assertEquals(3, tallies.size)
        assertEquals("b", tallies[0].music.id)
        assertEquals(2, tallies[0].count)
        assertEquals(at(2026, 10, 14, 11, 0), tallies[0].lastPlayedAt)
    }

    @Test
    fun tallyBreaksTiesOnTheMostRecentPlay() {
        val records = listOf(
            record("a", at(2026, 10, 14, 9, 0)),
            record("b", at(2026, 10, 14, 13, 0)),
        )
        val tallies = PlayStats.tally(records)
        assertEquals("b", tallies[0].music.id)
        assertEquals("a", tallies[1].music.id)
    }

    @Test
    fun totalPlaysCountsEveryEventNotEveryTrack() {
        val records = listOf(
            record("a", now),
            record("a", now),
            record("b", now),
        )
        assertEquals(3, PlayStats.totalPlays(records))
    }

    @Test
    fun todayHistogramAlwaysHasTwentyFourHours() {
        val buckets = PlayStats.buckets(listOf(record("a", at(2026, 10, 14, 15, 30))), StatsRange.TODAY, now, tz)
        assertEquals(PlayStats.HOUR_BUCKETS, buckets.size)
        assertEquals("00:00", buckets.first().label)
        assertEquals("23:00", buckets.last().label)
        assertEquals(1, buckets[15].count)
    }

    @Test
    fun hourBucketIgnoresRecordsFromOtherDays() {
        val records = listOf(
            record("a", at(2026, 10, 13, 15, 30)),
            record("a", at(2026, 10, 14, 15, 30)),
        )
        val buckets = PlayStats.buckets(records, StatsRange.TODAY, now, tz)
        assertEquals(1, buckets[15].count)
        assertEquals(1, buckets.sumOf { it.count })
    }

    @Test
    fun weekHistogramHasSevenDaysEndingToday() {
        val buckets = PlayStats.buckets(emptyList(), StatsRange.WEEK, now, tz)
        assertEquals(PlayStats.WEEK_BUCKETS, buckets.size)
        assertEquals("10/8", buckets.first().label)
        assertEquals("10/14", buckets.last().label)
    }

    @Test
    fun weekHistogramPlacesEachRecordInItsOwnDay() {
        val records = listOf(
            record("a", at(2026, 10, 12, 8, 0)),
            record("a", at(2026, 10, 14, 8, 0)),
            record("a", at(2026, 10, 14, 20, 0)),
        )
        val buckets = PlayStats.buckets(records, StatsRange.WEEK, now, tz)
        assertEquals(3, buckets.sumOf { it.count })
        assertEquals(1, buckets[buckets.size - 3].count)   // Monday 10/12
        assertEquals(2, buckets.last().count)              // Wednesday 10/14
    }

    @Test
    fun allHistogramKeepsThirtyDaysEvenWhenEmpty() {
        val buckets = PlayStats.buckets(emptyList(), StatsRange.ALL, now, tz)
        assertEquals(PlayStats.ALL_BUCKETS, buckets.size)
        assertEquals(0, buckets.sumOf { it.count })
    }

    @Test
    fun emptyInputProducesEmptyTallies() {
        assertTrue(PlayStats.tally(emptyList()).isEmpty())
        assertEquals(0, PlayStats.totalPlays(emptyList()))
    }
}
