package com.lxmusic.android.player

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V1.0.1 regression test: the contract ExoPlayer expects from a [ShuffleOrder].
 *
 * ExoPlayer treats a ShuffleOrder as a pure function *and* asks it several times
 * per decision (once to decide whether a neighbour exists, once while seeking,
 * once to render the notification buttons). Two rules follow:
 *
 * - the same question must answer the same thing until the track really starts;
 * - `-1` is not "unknown", it is "there is nothing there", and media3 turns it
 *   into *restart the current track*. Answering -1 to a "previous" question on a
 *   queue with two or more tracks is the "the first headset press replays this
 *   song" report, so it must never happen here.
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
    fun previousAlwaysHasAnAnswerWhenTwoTracksExist() {
        // The v1.0.0 bug: an empty history made this return -1 on the very first
        // press, and media3 read -1 as "no previous item" and restarted the
        // current track. There is no such thing as "no previous" in shuffle mode
        // any more - every press is a fresh draw.
        for (size in 2..12) {
            for (index in 0 until size) {
                val previous = order(size).getPreviousIndex(index)
                assertTrue("size=$size index=$index answered $previous", previous in 0 until size)
                assertNotEquals("previous must change the track (size=$size)", index, previous)
            }
        }
    }

    @Test
    fun nextAlwaysHasAnAnswerWhenTwoTracksExist() {
        // The v5.3.1 stall: getNextIndex returned -1 once a "pass" was used up,
        // and ExoPlayer reads -1 as "playlist finished" and stops for good.
        for (size in 2..12) {
            for (index in 0 until size) {
                val next = order(size).getNextIndex(index)
                assertTrue("size=$size index=$index answered $next", next in 0 until size)
                assertNotEquals("next must change the track (size=$size)", index, next)
            }
        }
    }

    @Test
    fun previousAvoidsTheTrackThatJustFinished() {
        // "Previous" after a track ends should not immediately replay the track
        // the user just heard - that reads as "the button did nothing".
        val state = RandomShuffleState()
        state.onTrackStarted(size = 6, current = 4)
        for (index in 0 until 6) {
            val o = RandomShuffleOrder(6, state, Random(7))
            assertNotEquals("previous sent the user straight back to 4", 4, o.getPreviousIndex(index))
        }
    }

    @Test
    fun aSingleTrackQueueReportsNoNeighbour() {
        // -1 is correct *here*: there is genuinely nothing to go to, and media3
        // restarts the single track, which is what every player does.
        assertEquals(-1, order(1).getNextIndex(0))
        assertEquals(-1, order(1).getPreviousIndex(0))
    }

    @Test
    fun drawingAgainAfterATrackStartsGivesANewAnswer() {
        // The whole point of V1.0.1: a new decision is a new draw. If the memo
        // survived the track change, the walk would be a fixed order again.
        val state = RandomShuffleState()
        val moves = HashMap<Int, MutableSet<Int>>()
        for (seed in 0 until 300) {
            state.invalidate()
            val o = RandomShuffleOrder(9, state, Random(seed))
            val from = 3
            moves.getOrPut(from) { HashSet() }.add(o.getNextIndex(from))
            state.onTrackStarted(9, o.getNextIndex(from))
        }
        assertEquals("every other track must be reachable as 'next'", 8, moves[3]?.size)
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
        // The snapshot contract: MediaSourceList clones the order on every queue
        // edit and repairs a length mismatch the same way. The snapshot carries
        // the memo drawn so far, so anything the parent had already decided must
        // come back unchanged from the clone.
        for (index in 0 until 6) {
            val state = RandomShuffleState()
            val parent = RandomShuffleOrder(6, state, Random(7))
            val wantedNext = parent.getNextIndex(index)
            val wantedPrevious = parent.getPreviousIndex(index)
            val clone = parent.cloneAndInsert(0, 0)
            assertEquals(6, clone.getLength())
            assertEquals("clone disagreed on next at $index", wantedNext, clone.getNextIndex(index))
            assertEquals("clone disagreed on previous at $index", wantedPrevious, clone.getPreviousIndex(index))
            assertEquals(parent.getFirstIndex(), clone.getFirstIndex())
            assertEquals(parent.getLastIndex(), clone.getLastIndex())
        }
    }

    @Test
    fun everyIndexStaysInRange() {
        val o = order(6)
        for (index in 0 until 6) {
            assertTrue(o.getNextIndex(index) in 0 until 6)
            assertTrue(o.getPreviousIndex(index) in 0 until 6)
            assertNotEquals(index, o.getNextIndex(index))
            assertNotEquals(index, o.getPreviousIndex(index))
        }
        assertTrue(o.getLastIndex() in 0 until 6)
        assertTrue(o.getFirstIndex() in 0 until 6)
        assertEquals(0, o.getFirstIndex())
        assertEquals(5, o.getLastIndex())
    }

    @Test
    fun theEndsAreTheQueueEndsNotADraw() {
        // first/last are only used for repeat-ALL wrap-around and for building
        // the MediaPeriodQueue; they must be stable, because answering twice
        // differently makes ExoPlayer re-sort its buffers.
        val o = order(5)
        repeat(4) {
            assertEquals(0, o.getFirstIndex())
            assertEquals(4, o.getLastIndex())
        }
    }
}
