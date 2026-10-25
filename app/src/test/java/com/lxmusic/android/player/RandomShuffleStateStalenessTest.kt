package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression tests for the two shuffle defects that made playback appear to
 * freeze.
 *
 * 1. Size staleness. [RandomShuffleState] memoises its answers by source
 *    position only. [PlayerController.installShuffleOrder] hands the *same*
 *    state object to a brand new [RandomShuffleOrder] every time the queue
 *    length changes, so an answer memoised while the queue was long used to be
 *    replayed after the queue shrank. ExoPlayer treats a [androidx.media3.exoplayer.source.ShuffleOrder]
 *    result as a child index and never range-checks it, so an out-of-range
 *    answer corrupts the timeline instead of merely picking a wrong track.
 *
 * 2. Missing synchronisation. The state is written from ExoPlayer's playback
 *    thread (`MediaSourceList.setShuffleOrder` -> `cloneAndInsert` ->
 *    `invalidate`) and from the app's main thread (`redrawNext` on a button
 *    press). The memos are plain HashMaps; a `put` racing a resize can leave a
 *    bucket list cyclic and the next traversal on the main thread never
 *    terminates. That is an ANR with no crash and no log.
 */
class RandomShuffleStateStalenessTest {

    private fun state() = RandomShuffleState()

    @Test
    fun aShrunkQueueNeverReplaysAnOutOfRangeNextIndex() {
        val state = state()
        val long = RandomShuffleOrder(64, state, Random(5))
        long.getNextIndex(3)

        val short = RandomShuffleOrder(8, state, Random(5))
        val replayed = short.getNextIndex(3)

        assertTrue(
            "a shrunk queue replayed a stale index " + replayed + " (valid 0 until 8)",
            replayed in 0 until 8,
        )
    }

    @Test
    fun aShrunkQueueNeverReplaysAnOutOfRangePreviousIndex() {
        val state = state()
        val long = RandomShuffleOrder(64, state, Random(9))
        long.getPreviousIndex(1)

        val short = RandomShuffleOrder(4, state, Random(9))
        val replayed = short.getPreviousIndex(1)

        // V6.1: -1 is a legal answer at the first slot of a cycle (ExoPlayer
        // restarts the current track). The defect this test guards is an index
        // that belongs to the old, longer queue.
        assertTrue("stale previous " + replayed, replayed == -1 || replayed in 0 until 4)
    }

    @Test
    fun aShrunkQueueNeverReplaysAnOutOfRangeLastIndex() {
        val state = state()
        val long = RandomShuffleOrder(64, state, Random(3))
        long.getLastIndex()

        val short = RandomShuffleOrder(4, state, Random(3))
        val replayed = short.getLastIndex()

        assertTrue("stale last " + replayed, replayed in 0 until 4)
    }

    @Test
    fun everyDrawStaysInRangeAcrossManySizes() {
        val state = state()
        var size = 1
        while (size <= 32) {
            val order = RandomShuffleOrder(size, state, Random(size))
            for (i in 0 until size) {
                val next = order.getNextIndex(i)
                val previous = order.getPreviousIndex(i)
                // A one-track queue has nowhere to go; -1 is the contract there.
                val expected = if (size == 1) -1 else null
                if (expected != null) {
                    assertEquals("size 1 must report no neighbour", expected, next)
                    assertEquals("size 1 must report no neighbour", expected, previous)
                } else {
                    assertTrue("next out of range at size " + size, next in 0 until size)
                    // V6.1: -1 is a legal answer for "previous" at the first
                    // slot of a cycle - ExoPlayer turns it into "restart this
                    // track". What must never happen is an *out-of-range* index.
                    assertTrue(
                        "previous out of range at size $size: $previous",
                        previous == -1 || previous in 0 until size,
                    )
                }
            }
            size += 7
        }
    }

    @Test
    fun aStillValidMemoIsReusedRatherThanRedrawn() {
        val state = state()
        val order = RandomShuffleOrder(8, state, Random(11))
        val first = order.getNextIndex(2)
        val second = order.getNextIndex(2)

        assertEquals("a valid memo must stay stable", first, second)
    }

    @Test
    fun theMemoNeverGrowsWithoutBoundWhenTheQueueKeepsResizing() {
        val state = state()
        for (round in 0 until 50) {
            val order = RandomShuffleOrder(4 + round % 5, state, Random(round))
            order.getNextIndex(0)
            order.getPreviousIndex(0)
        }
        assertTrue("memo leaked " + state.memoised + " entries", state.memoised <= 10)
    }
}
