package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V6.1: the shared shuffle cycle.
 *
 * The contract changed shape in this version, and the change is the point:
 *
 * - a cycle is one real order - following `nextIndex` visits every queue
 *   index exactly once before any of them comes back;
 * - the cycle **survives** a track change (the old code invalidated the whole
 *   order on every transition, which is what made shuffle behave like
 *   "sample one track uniformly at random, forever");
 * - the end of a cycle reshuffles *anchored on the current track*, so the next
 *   cycle's first track is not the one that just played;
 * - `previousIndex` returns -1 at a cycle's start - ExoPlayer turns that into
 *   "restart the current track", which is the right answer there.
 */
class RandomShuffleStateTest {

    private fun state(seed: Int = 7) = RandomShuffleState() to Random(seed)

    @Test
    fun nextIndexIsStableForTheSamePosition() {
        val (s, r) = state()
        val first = s.nextIndex(8, 3, r)
        repeat(6) { assertEquals(first, s.nextIndex(8, 3, r)) }
    }

    @Test
    fun previousIndexIsStableForTheSamePosition() {
        val (s, r) = state()
        s.nextIndex(8, 3, r)
        val first = s.previousIndex(8, 3, r)
        repeat(6) { assertEquals(first, s.previousIndex(8, 3, r)) }
    }

    @Test
    fun nextIndexNeverReturnsTheCurrentTrack() {
        val (s, r) = state()
        for (size in 2..10) {
            for (current in 0 until size) {
                val pick = s.nextIndex(size, current, r)
                assertTrue("pick $pick out of range for size $size", pick in 0 until size)
                assertNotEquals("pick repeated the current track $current", current, pick)
            }
        }
    }

    @Test
    fun previousIsTheOtherEndOfNext() {
        // One order means previous must undo next, except at the cycle's
        // very first slot - there is nothing behind it.
        val (s, r) = state(3)
        val size = 9
        val here = 4
        val ahead = s.nextIndex(size, here, r)
        assertEquals(
            "previous of next must return to where we started",
            here,
            s.previousIndex(size, ahead, r),
        )
    }

    @Test
    fun oneCycleCoversEveryTrackExactlyOnce() {
        // The whole reason for a cycle: no repeats until the queue is exhausted.
        val (s, r) = state(11)
        val size = 12
        val seen = HashSet<Int>()
        var cursor = s.firstIndex(size, r)
        repeat(size) {
            assertTrue("cycle revisited a track before covering the queue", seen.add(cursor))
            if (it < size - 1) cursor = s.nextIndex(size, cursor, r)
        }
        assertEquals("a cycle must cover the whole queue", size, seen.size)
    }

    @Test
    fun theCycleSurvivesATrackChange() {
        // The regression this version fixes: transitioning to a track must not
        // throw the order away. Walking a full cycle through the *same*
        // state must still visit every index once.
        val (s, r) = state(29)
        val size = 10
        val seen = HashSet<Int>()
        var cursor = s.firstIndex(size, r)
        repeat(size) {
            assertTrue("a transition discarded the cycle", seen.add(cursor))
            if (it < size - 1) cursor = s.nextIndex(size, cursor, r)
        }
        assertEquals(size, seen.size)
        assertEquals("the cycle must not be redrawn mid-walk", 1, s.cycles)
    }

    @Test
    fun aFinishedCycleStartsANewOneAnchoredOnTheCurrentTrack() {
        // V1.0: the cycle advances on the *transition*, not inside the query.
        // nextIndex() stays pure so media3's repeated questions in one decision
        // all see the same order.
        val (s, r) = state(31)
        val size = 8
        val firstOrder = s.firstIndex(size, r)
        var cursor = s.firstIndex(size, r)
        repeat(size - 1) { cursor = s.nextIndex(size, cursor, r) }
        val last = cursor
        assertEquals("walking a cycle must not redraw it", 1, s.cycles)

        val cycled = s.advanceCycle(size, last, r)
        assertTrue("reaching the last slot must roll the cycle", cycled)
        assertEquals("a finished cycle draws a new order", 2, s.cycles)

        val next = s.nextIndex(size, last, r)
        assertTrue("the wrapped pick is out of range", next in 0 until size)
        assertNotEquals("a finished cycle must not replay the same track", last, next)

        // The slot that just played is the new cycle's anchor, so asking again
        // for the track after it gives the same answer.
        assertEquals(next, s.nextIndex(size, last, r))
        assertTrue(firstOrder in 0 until size)
    }

    @Test
    fun queryingAtTheCycleEndDoesNotRedrawIt() {
        // The regression behind "shuffle needs three presses to actually move":
        // media3 asks getNextIndex() several times per decision. If the query
        // redrew the order, each of those questions would answer against a
        // different order and the cycle would be cut short.
        val (s, r) = state(41)
        val size = 6
        var cursor = s.firstIndex(size, r)
        repeat(size - 1) { cursor = s.nextIndex(size, cursor, r) }
        val last = cursor
        repeat(20) { s.nextIndex(size, last, r) }
        assertEquals("querying must never draw a new cycle", 1, s.cycles)
        repeat(20) { s.firstIndex(size, r); s.lastIndex(size, r) }
        assertEquals("neither must first/last", 1, s.cycles)
    }

    @Test
    fun queryingAtTheCycleEndNeverAnswersWithTheTrackThatJustPlayed() {
        // The defensive wrap at the last slot must not answer "this same
        // track": the cycle that would follow is anchored on it, so it sits at
        // slot 0 and a naive wrap would replay it.
        val (s, r) = state(47)
        for (size in 2..9) {
            s.invalidate()
            var cursor = s.firstIndex(size, r)
            repeat(size - 1) { cursor = s.nextIndex(size, cursor, r) }
            val last = cursor
            // No advanceCycle here - this is the defensive path, where the
            // order still describes the cycle that just finished.
            repeat(10) {
                val pick = s.nextIndex(size, last, r)
                assertTrue("wrap pick $pick out of range for size $size", pick in 0 until size)
                assertNotEquals("the wrap replayed the track that just played", last, pick)
            }
        }
    }

    @Test
    fun rollingTheCycleTwiceForTheSameTrackIsHarmless() {
        // advanceCycle is called from both the transition callback and
        // playIndex(); the second call must not roll again.
        val (s, r) = state(43)
        val size = 5
        var cursor = s.firstIndex(size, r)
        repeat(size - 1) { cursor = s.nextIndex(size, cursor, r) }
        assertTrue(s.advanceCycle(size, cursor, r))
        assertEquals(2, s.cycles)
        repeat(5) { s.advanceCycle(size, cursor, r) }
        assertEquals("rolling must be idempotent for one track", 2, s.cycles)
    }

    @Test
    fun previousAtTheCycleStartAsksForARestart() {
        // -1 is meaningful: ExoPlayer turns it into "replay this track".
        val (s, r) = state(37)
        val size = 9
        val first = s.firstIndex(size, r)
        assertEquals(-1, s.previousIndex(size, first, r))
    }

    @Test
    fun invalidateDropsTheWholeOrder() {
        val (s, r) = state(19)
        s.nextIndex(6, 0, r)
        s.previousIndex(6, 0, r)
        s.lastIndex(6, r)
        s.firstIndex(6, r)
        assertTrue(s.memoised > 0)
        s.invalidate()
        assertEquals(0, s.memoised)
    }

    @Test
    fun anInvalidatedStateDrawsANewOrder() {
        val (s, r) = state(23)
        val firstOrder = s.firstIndex(6, r)
        val orders = HashSet<Int>()
        repeat(60) {
            s.invalidate()
            orders.add(s.firstIndex(6, r))
        }
        assertTrue("a fresh draw must be able to start elsewhere", orders.size > 1)
        assertEquals(6, orders.size)
        assertTrue(firstOrder in 0 until 6)
    }

    @Test
    fun generationAdvancesWhenTheOrderChanges() {
        // installShuffleOrder uses this to skip reinstalling an identical order:
        // every reinstall forces ExoPlayer to rebuild its masking timeline.
        val (s, r) = state(41)
        val before = s.version
        s.firstIndex(6, r)
        assertEquals("drawing an order is a change", before + 1, s.version)
        val drawn = s.version
        s.firstIndex(6, r)
        assertEquals("reading the same order is not a change", drawn, s.version)
        s.invalidate()
        assertTrue("invalidate must change the generation", s.version > drawn)
    }

    @Test
    fun aDifferentQueueLengthRedrawsRatherThanReusingIndices() {
        // Reusing an order drawn for another length would hand ExoPlayer child
        // indices that do not exist in the new playlist.
        val (s, r) = state(43)
        s.firstIndex(9, r)
        val afterNine = s.version
        assertTrue(s.firstIndex(5, r) in 0 until 5)
        assertEquals("a length change must redraw", afterNine + 1, s.version)
    }

    @Test
    fun degenerateSizesAreHandled() {
        val (s, r) = state()
        assertEquals(-1, s.nextIndex(0, 0, r))
        assertEquals(-1, s.previousIndex(0, 0, r))
        assertEquals(-1, s.nextIndex(1, 0, r))
        assertEquals(-1, s.previousIndex(1, 0, r))
        assertEquals(-1, s.lastIndex(0, r))
        assertEquals(-1, s.firstIndex(0, r))
    }

    @Test
    fun outOfRangeCurrentIndexDoesNotThrow() {
        val (s, r) = state()
        assertEquals(-1, s.nextIndex(5, 9, r))
        assertEquals(-1, s.previousIndex(5, -1, r))
    }

    @Test
    fun lastAndFirstIndexAreInRangeAndStable() {
        val (s, r) = state(23)
        val last = s.lastIndex(7, r)
        val first = s.firstIndex(7, r)
        assertTrue(last in 0 until 7)
        assertTrue(first in 0 until 7)
        repeat(5) {
            assertEquals(last, s.lastIndex(7, r))
            assertEquals(first, s.firstIndex(7, r))
        }
    }

    @Test
    fun aCopyIsASnapshotNotASharedView() {
        // ExoPlayer keeps clones alive across playlist edits; a shared reference
        // would let one of them repair the order the other is still serving.
        val (s, r) = state(47)
        val first = s.firstIndex(6, r)
        val clone = s.copy()
        s.invalidate()
        assertEquals("the snapshot must survive the original", first, clone.firstIndex(6, r))
    }

    @Test
    fun aSingleTrackQueueCanNeverAdvance() {
        // Restoring the last-played track leaves a one-track queue; there is
        // genuinely nothing to go to, and ExoPlayer must be told so.
        val (s, r) = state()
        assertEquals(-1, s.nextIndex(1, 0, r))
        assertEquals(-1, s.previousIndex(1, 0, r))
    }
}
