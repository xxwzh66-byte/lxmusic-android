package com.lxmusic.android.player

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V5.3.6 regression test: the contract ExoPlayer expects from a [ShuffleOrder].
 *
 * ExoPlayer treats a ShuffleOrder as a pure function *and* as one consistent
 * playlist: it asks for the same index several times per decision (once to
 * decide whether a next item exists, once while seeking, once to render the
 * notification buttons), and it combines getFirstIndex / getLastIndex /
 * getNextIndex / getPreviousIndex into a walk over the queue.
 *
 * A ShuffleOrder that draws independently per position breaks that walk: the
 * notification says one track is next, the seek lands on another, and the
 * playlist reads as "finished" while tracks remain - the user-visible "随机
 * 播放卡住 / 上下首切换卡住" report.
 */
@UnstableApi
class RandomShuffleOrderTest {

    private fun order(size: Int, state: RandomShuffleState = RandomShuffleState()) =
        RandomShuffleOrder(size, state, Random(7))

    @Test
    fun getNextIndexIsStableForTheSamePosition() {
        val o = order(8)
        val first = o.getNextIndex(3)
        repeat(6) { assertEquals("asking twice for the same position must answer the same", first, o.getNextIndex(3)) }
    }

    @Test
    fun getPreviousIndexIsStableForTheSamePosition() {
        val o = order(8)
        val first = o.getPreviousIndex(3)
        repeat(6) { assertEquals("asking twice for the same position must answer the same", first, o.getPreviousIndex(3)) }
    }

    @Test
    fun previousIsRandomAcrossPositionsNotAHistoryWalk() {
        // Randomness lives in *which order the playlist is walked in*, not in a
        // separate draw per question - see RandomShuffleTest.
        //
        // V6.1 details that matter here: the playlist has to be drawn *before*
        // asking (an undrawn state anchors the cycle on the index being asked
        // about, which makes that index the cycle's start and therefore the -1
        // answer), and -1 is legal at a cycle's start.
        val seen = HashSet<Int>()
        for (seed in 0 until 200) {
            val o = RandomShuffleOrder(5, RandomShuffleState(), Random(seed))
            o.getFirstIndex()
            val previous = o.getPreviousIndex(2)
            assertEquals(5, o.getLength())
            assertTrue("previous out of range: $previous", previous == -1 || previous in 0 until 5)
            assertNotEquals(2, previous)
            if (previous >= 0) seen.add(previous)
        }
        assertEquals("previous must be able to land on every other track", setOf(0, 1, 3, 4), seen)
    }

    @Test
    fun nextIndexNeverEndsPlaybackWhileATrackRemains() {
        // The v5.3.1 bug: getNextIndex returned -1 once a "pass" was used up,
        // and ExoPlayer reads -1 as "playlist finished" and stops for good.
        val o = order(3)
        for (index in 0 until 3) {
            assertTrue("index $index must still have a successor", o.getNextIndex(index) >= 0)
        }
    }

    @Test
    fun aSingleTrackQueueStillReportsNoNext() {
        assertEquals(-1, order(1).getNextIndex(0))
    }

    @Test
    fun walkingNextFromAnyIndexCoversTheWholeQueueExactlyOnce() {
        // The end-to-end contract: following the answers from any start must
        // reach every source index exactly once. V6.1: at the end of a cycle the
        // order reshuffles anchored on the current track instead of wrapping to
        // the first one - that is what stops "the same track twice in a row"
        // while still guaranteeing a successor forever.
        val o = order(9)
        val start = o.getFirstIndex()
        val seen = HashSet<Int>()
        var cursor = start
        repeat(9) {
            assertTrue("walk revisited $cursor before covering the queue", seen.add(cursor))
            if (it < 8) cursor = o.getNextIndex(cursor)
        }
        assertEquals("the walk must cover the queue exactly once", 9, seen.size)
        val wrapped = o.getNextIndex(cursor)
        assertTrue("a finished cycle must still have a successor", wrapped in 0 until 9)
        assertNotEquals("the new cycle must not start with the track that just played", cursor, wrapped)
    }

    @Test
    fun previousUndoesNextAlongTheWholeWalk() {
        // Walks a drawn cycle up to (but not through) its last slot: stepping
        // past it deliberately rolls onto a new cycle, and comparing across that
        // boundary would be comparing two different permutations.
        val o = order(11)
        var cursor = o.getFirstIndex()
        repeat(10) {
            val ahead = o.getNextIndex(cursor)
            assertEquals("previous of next must return to $cursor", cursor, o.getPreviousIndex(ahead))
            cursor = ahead
        }
    }

    @Test
    fun theEndsOfThePlaylistAreConsistentWithTheWalk() {
        // V6.1: the first slot of a cycle has no predecessor (-1 tells ExoPlayer
        // to restart the current track), and the last slot has a successor that
        // opens the next cycle rather than the old "wrap to first".
        val o = order(7)
        val first = o.getFirstIndex()
        val last = o.getLastIndex()
        assertEquals("the first slot of a cycle has nothing behind it", -1, o.getPreviousIndex(first))
        val successor = o.getNextIndex(last)
        assertTrue("the last slot must still have a successor", successor in 0 until 7)
        assertNotEquals("the end of a cycle must not replay the same track", last, successor)
        assertNotEquals(first, last)
    }

    @Test
    fun clonesKeepTheSamePolicyAndResize() {
        val state = RandomShuffleState()
        val base = RandomShuffleOrder(4, state, Random(7))
        assertEquals(5, base.cloneAndInsert(2, 1).getLength())
        assertEquals(3, base.cloneAndRemove(1, 1).getLength())
        assertEquals(0, base.cloneAndClear().getLength())
    }

    @Test
    fun aCloneAtTheSameLengthAnswersExactlyAsItsParent() {
        // The snapshot contract: a clone inherits the parent's playlist, so as
        // long as the length agrees the two must walk it identically. This is
        // the property that kept MediaSourceList's length-repair path and the
        // app's install path from disagreeing before v5.3.1.
        //
        // V6.1 caveat: asking for the successor of the *last* slot of a cycle
        // rolls the parent onto a fresh cycle (that is what keeps playback
        // going). The clone keeps the snapshot it was handed, so the walk below
        // stops one step short of that rollover.
        val state = RandomShuffleState()
        val parent = RandomShuffleOrder(6, state, Random(7))
        // Ask the parent first so the playlist is drawn and snapshotted, then
        // clone: the snapshot must carry that same playlist over.
        val parentAnswer = parent.getNextIndex(parent.getFirstIndex())
        val clone = parent.cloneAndInsert(0, 0)
        assertEquals(6, clone.getLength())
        assertEquals(parentAnswer, clone.getNextIndex(1))
        assertEquals(parent.getFirstIndex(), clone.getFirstIndex())
        assertEquals(parent.getLastIndex(), clone.getLastIndex())

        var cursor = clone.getFirstIndex()
        repeat(5) {
            val fromParent = parent.getNextIndex(cursor)
            val fromClone = clone.getNextIndex(cursor)
            assertEquals("clone disagreed with its parent at $cursor", fromParent, fromClone)
            assertEquals(parent.getPreviousIndex(fromClone), clone.getPreviousIndex(fromClone))
            cursor = fromClone
        }
    }

    @Test
    fun everyIndexStaysInRange() {
        // V6.1: "previous" answers -1 at the first slot of a cycle, which
        // ExoPlayer turns into "restart the current track". Everything else must
        // be a real index - never a stale one from a longer queue.
        val o = order(6)
        for (index in 0 until 6) {
            assertTrue(o.getNextIndex(index) in 0 until 6)
            val previous = o.getPreviousIndex(index)
            assertTrue("previous out of range: $previous", previous == -1 || previous in 0 until 6)
            assertNotEquals(index, o.getNextIndex(index))
        }
        assertTrue(o.getLastIndex() in 0 until 6)
        assertTrue(o.getFirstIndex() in 0 until 6)
    }

    // --- V6.2: "previous" must come from the play history, not the cycle ------

    @Test
    fun previousPrefersTheHistoryHookWhenItHasATarget() {
        // The notification / lockscreen / bluetooth "previous" only sees this
        // ShuffleOrder. Handing it the history target is the only way those
        // surfaces stop jumping forward to a track the cycle has not reached.
        val o = RandomShuffleOrder(6, RandomShuffleState(), Random(7), previousOf = { 4 })
        assertEquals(4, o.getPreviousIndex(2))
        assertEquals(4, o.getPreviousIndex(0))
    }

    @Test
    fun anEmptyHistoryMeansRestartNotACycleJump() {
        // -1 is media3's "restart the current track". Falling back to the
        // cycle's predecessor here would be the old bug under a new name.
        val o = RandomShuffleOrder(6, RandomShuffleState(), Random(7), previousOf = { -1 })
        assertEquals(-1, o.getPreviousIndex(2))
        assertEquals(-1, o.getPreviousIndex(o.getLastIndex()))
    }

    @Test
    fun clonesCarryTheHistoryHook() {
        // ExoPlayer clones the order on every playlist mutation. If the clone
        // dropped the hook, "previous" would silently revert to the cycle for
        // the rest of the session.
        val o = RandomShuffleOrder(6, RandomShuffleState(), Random(7), previousOf = { 3 })
        assertEquals(3, o.cloneAndInsert(0, 1).getPreviousIndex(2))
        assertEquals(3, o.cloneAndRemove(0, 1).getPreviousIndex(2))
    }
}
