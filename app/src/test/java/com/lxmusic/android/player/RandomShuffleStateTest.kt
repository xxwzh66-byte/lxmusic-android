package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V1.0.1: the shared random source.
 *
 * The contract is short and every part of it has a bug behind it:
 *
 * - one decision draws once (v5.3.2 drew per question → notification and player
 *   disagreed → "随机播放卡住");
 * - every new decision draws again (V6.1 drew one fixed order per pass → the
 *   order was decided in advance → "播放顺序是固定的，不是随机的");
 * - `next` and `previous` both answer with a real index while the queue has two
 *   or more tracks, because media3 reads -1 as "no such item" and restarts the
 *   current track (the "first headset previous replays this song" report).
 */
class RandomShuffleStateTest {

    private fun state(seed: Int = 7) = RandomShuffleState() to Random(seed)

    // --- one decision, one draw ------------------------------------------------

    @Test
    fun nextIndexIsStableForTheSamePosition() {
        val (s, random) = state()
        val first = s.nextIndex(8, 3, random)
        repeat(6) { assertEquals("a decision must not change mid-flight", first, s.nextIndex(8, 3, random)) }
        assertEquals(1, s.memoised)
    }

    @Test
    fun previousIndexIsStableForTheSamePosition() {
        val (s, random) = state()
        val first = s.previousIndex(8, 3, random)
        repeat(6) { assertEquals("a decision must not change mid-flight", first, s.previousIndex(8, 3, random)) }
    }

    @Test
    fun nextAndPreviousAreDrawnIndependently() {
        // Pressing "next" must not consume the "previous" draw and vice versa -
        // they are separate questions with separate memos.
        val (s, random) = state()
        val ahead = s.nextIndex(10, 4, random)
        val behind = s.previousIndex(10, 4, random)
        assertEquals("the next memo must not be overwritten", ahead, s.nextIndex(10, 4, random))
        assertEquals("the previous memo must not be overwritten", behind, s.previousIndex(10, 4, random))
    }

    @Test
    fun neitherDirectionEverReturnsTheCurrentTrack() {
        val (s, random) = state()
        for (index in 0 until 10) {
            val next = s.nextIndex(10, index, random)
            assertNotEquals("next replayed $index", index, next)
            assertTrue("next out of range: $next", next in 0 until 10)
            s.invalidate()
            val previous = s.previousIndex(10, index, random)
            assertNotEquals("previous replayed $index", index, previous)
            assertTrue("previous out of range: $previous", previous in 0 until 10)
            s.invalidate()
        }
    }

    // --- a new decision draws again -------------------------------------------

    @Test
    fun onTrackStartedReleasesTheMemo() {
        val (s, random) = state()
        val first = s.nextIndex(6, 2, random)
        assertEquals(1, s.memoised)
        s.onTrackStarted(6, first)
        assertEquals("the memo must be released once the track really plays", 0, s.memoised)
        // A fresh draw may or may not repeat by chance, but it must not be
        // pinned by the old memo any more.
        val redraws = (0 until 200).map { s.nextIndex(6, first, random).also { s.invalidate() } }.toSet()
        assertTrue("the new decision must be able to reach other tracks: $redraws", redraws.size > 1)
    }

    @Test
    fun everyDecisionCanReachEveryOtherTrack() {
        // The core of the "the order is fixed" complaint: across decisions from
        // the same track, all N-1 alternatives must be reachable.
        val (s, random) = state()
        val reached = HashSet<Int>()
        repeat(1_000) {
            val target = s.nextIndex(7, 3, random)
            reached.add(target)
            s.onTrackStarted(7, target)
        }
        assertEquals("every alternative must be drawable from track 3", 6, reached.size)
        assertTrue("track 3 must never be drawn", 3 !in reached)
    }

    @Test
    fun theOrderIsNotFixedBeforeTheUserPressesTheButton() {
        // Reproduces the report directly: walk the queue by always asking for
        // "next" after starting the track. If the answers were one fixed order,
        // the walk would repeat after N steps. It must not.
        val (s, random) = state()
        var cursor = 0
        s.onTrackStarted(8, cursor)
        val walk = mutableListOf(cursor)
        repeat(40) {
            cursor = s.nextIndex(8, cursor, random)
            assertTrue("the walk left the queue: $cursor", cursor in 0 until 8)
            walk.add(cursor)
            s.onTrackStarted(8, cursor)
        }
        assertEquals("the walk must start where we started", 0, walk[0])
        assertTrue("a fixed order would repeat every 8 steps: $walk", walk.take(8) != walk.drop(8).take(8))
    }

    @Test
    fun previousAvoidsTheTrackThatJustFinished() {
        // Going back to the track the user just heard reads as "the button did
        // nothing", so the draw skips it while the queue is longer than two.
        // Each iteration gets a fresh state: invalidate() also forgets lastTrack,
        // which is exactly what we are testing here.
        for (index in 0 until 9) {
            val s = RandomShuffleState()
            s.onTrackStarted(9, 5)
            val previous = s.previousIndex(9, index, Random(7))
            assertNotEquals("previous sent the user back to the track that just finished", 5, previous)
        }
    }

    @Test
    fun aTwoTrackQueueDoesNotExcludeBothAlternatives() {
        // With two tracks and lastTrack excluded, there would be nothing left to
        // draw. The exclusion has to relax instead of returning the current one.
        val (s, random) = state()
        s.onTrackStarted(2, 1)
        for (index in 0 until 2) {
            val previous = s.previousIndex(2, index, random)
            assertEquals("a two-track queue has exactly one answer", 1 - index, previous)
            s.invalidate()
        }
    }

    // --- invalidation ---------------------------------------------------------

    @Test
    fun invalidateDropsTheWholeOrder() {
        val (s, random) = state()
        s.nextIndex(6, 1, random)
        s.previousIndex(6, 1, random)
        s.onTrackStarted(6, 2)
        s.invalidate()
        assertEquals(0, s.memoised)
    }

    @Test
    fun invalidateAdvancesTheVersionSoTheOrderIsReinstalled() {
        val (s, _) = state()
        val before = s.version
        s.invalidate()
        assertNotEquals("invalidate must let the install path see a new order", before, s.version)
    }

    @Test
    fun releasingTheMemoIsNotAVersionChange() {
        // The memo is a note-to-self inside one decision. The version question is
        // "is the order installed in ExoPlayer still this one?" - and it is, so
        // bumping it here would reinstall the order after every track, which is
        // the timeline-change feedback loop that caused the mode-switch jank.
        val (s, random) = state()
        s.nextIndex(6, 1, random)
        val before = s.version
        s.onTrackStarted(6, 2)
        assertEquals("a track change must not reinstall the order", before, s.version)
    }

    // --- degenerate inputs ----------------------------------------------------

    @Test
    fun degenerateSizesAreHandled() {
        val (s, random) = state()
        assertEquals(-1, s.nextIndex(0, 0, random))
        assertEquals(-1, s.previousIndex(0, 0, random))
        assertEquals(-1, s.nextIndex(1, 0, random))
        assertEquals(-1, s.previousIndex(1, 0, random))
        assertEquals(-1, s.firstIndex(0))
        assertEquals(-1, s.lastIndex(0))
    }

    @Test
    fun outOfRangeCurrentIndexDoesNotThrow() {
        val (s, random) = state()
        assertEquals(-1, s.nextIndex(5, -1, random))
        assertEquals(-1, s.nextIndex(5, 5, random))
        assertEquals(-1, s.previousIndex(5, -1, random))
        assertEquals(-1, s.previousIndex(5, 5, random))
    }

    @Test
    fun aSingleTrackQueueCanNeverAdvance() {
        val (s, random) = state()
        repeat(10) { assertEquals(-1, s.nextIndex(1, 0, random)) }
        s.onTrackStarted(1, 0)
        repeat(10) { assertEquals(-1, s.previousIndex(1, 0, random)) }
    }

    @Test
    fun firstAndLastAreTheQueueEnds() {
        val (s, _) = state()
        for (size in 1..12) {
            assertEquals(0, s.firstIndex(size))
            assertEquals(size - 1, s.lastIndex(size))
        }
    }

    // --- copies ---------------------------------------------------------------

    @Test
    fun aCopyIsASnapshotNotASharedView() {
        // ExoPlayer clones the state on every playlist edit and may hold the
        // clone for a while. Writing the clone's memo must not rewrite the
        // parent's answer mid-decision.
        val (s, random) = state()
        val parentAnswer = s.nextIndex(6, 1, random)
        val clone = s.copy()
        s.onTrackStarted(6, parentAnswer)
        assertEquals("the clone must keep its own snapshot", parentAnswer, clone.nextIndex(6, 1, random))
        assertEquals(0, s.memoised)
    }

    @Test
    fun aCopySharesTheVersionSoEitherSideCanInvalidate() {
        val (s, _) = state()
        val clone = s.copy()
        val before = clone.version
        s.invalidate()
        assertNotEquals("the clone must see the parent's invalidation", before, clone.version)
    }
}
