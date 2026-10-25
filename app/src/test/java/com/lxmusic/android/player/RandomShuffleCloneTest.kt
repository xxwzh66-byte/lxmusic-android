package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression tests for the ExoPlayer clone protocol.
 *
 * `MediaSourceList.setShuffleOrder` builds *candidate* orders:
 *
 *   if (shuffleOrder.getLength() != holderCount) {
 *       shuffleOrder = shuffleOrder.cloneAndClear().cloneAndInsert(0, holderCount);
 *   }
 *   this.shuffleOrder = shuffleOrder;
 *
 * and the result can still be rejected by the length assertion in
 * `ExoPlayerImpl.setShuffleOrder`. The order a clone came from therefore stays
 * installed and keeps answering the notification, lockscreen and bluetooth
 * buttons. A clone that invalidated the shared state used to wipe the live
 * order's memo as a side effect of building a candidate: the very next
 * `getNextIndex` on the installed order returned a different track, and the
 * speculative path could also shift the answer the app had already shown the
 * user. Each clone now takes a snapshot instead.
 */
class RandomShuffleCloneTest {

    private fun state() = RandomShuffleState()

    @Test
    fun aCloneMustNotBlindTheOrderItWasClonedFrom() {
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(4))
        val before = installed.getNextIndex(3)

        installed.cloneAndInsert(0, 1)

        assertEquals(
            "building a candidate order must not rewrite the live order",
            before,
            installed.getNextIndex(3),
        )
    }

    @Test
    fun cloneAndRemoveMustNotBlindTheOrderItWasClonedFrom() {
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(6))
        val before = installed.getPreviousIndex(2)

        installed.cloneAndRemove(0, 1)

        assertEquals(
            "a speculative remove must not rewrite the live order",
            before,
            installed.getPreviousIndex(2),
        )
    }

    @Test
    fun cloneAndClearMustNotBlindTheOrderItWasClonedFrom() {
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(5))
        val before = installed.getNextIndex(1)

        installed.cloneAndClear()

        assertEquals(
            "clearing a candidate must not rewrite the live order",
            before,
            installed.getNextIndex(1),
        )
    }

    @Test
    fun aCloneAtTheSameLengthInheritsItsParentsPlaylist() {
        // A clone is a snapshot: at equal length it walks the same order as its
        // parent, at a different length it draws its own (the old playlist no
        // longer describes the queue) - but either way it stays usable.
        val state = state()
        val parent = RandomShuffleOrder(6, state, Random(8))
        val parentAnswer = parent.getNextIndex(1)

        val clone = parent.cloneAndInsert(0, 0)

        assertEquals("the playlist must survive a clone at equal length", parentAnswer, clone.getNextIndex(1))
        assertEquals(6, clone.getLength())
        assertNotEquals(parent.getLastIndex(), clone.getFirstIndex())
    }

    @Test
    fun theLengthRepairPathYieldsAUsableOrder() {
        // The speculative path MediaSourceList uses when the installed order
        // disagrees with the holder count: the repaired order must be a valid
        // playlist for the *new* size, never -1 while a track remains.
        val state = state()
        val wrong = RandomShuffleOrder(3, state, Random(2))

        val repaired = wrong.cloneAndClear().cloneAndInsert(0, 8)

        assertEquals("repair must reach the holder count", 8, repaired.getLength())
        assertTrue(repaired.getFirstIndex() in 0 until 8)
        assertTrue(repaired.getLastIndex() in 0 until 8)
        for (i in 0 until 8) {
            assertTrue("repaired order out of range at " + i, repaired.getNextIndex(i) in 0 until 8)
            assertNotEquals(i, repaired.getNextIndex(i))
        }
    }

    @Test
    fun aRepairedOrderWalksItsWholeQueue() {
        // V6.1: a finished cycle rolls onto a fresh one anchored on the track
        // that just played instead of wrapping back to the first slot, so the
        // root property is coverage - every track once before any repeat.
        val state = state()
        val repaired = RandomShuffleOrder(3, state, Random(5)).cloneAndClear().cloneAndInsert(0, 8)
        val seen = HashSet<Int>()
        var cursor = repaired.getFirstIndex()
        repeat(8) { step ->
            assertTrue("walk revisited $cursor before covering the queue", seen.add(cursor))
            val next = repaired.getNextIndex(cursor)
            assertTrue("a finished cycle must still have a successor", next in 0 until 8)
            if (step == 7) assertNotEquals("the next cycle must not repeat the track", cursor, next)
            cursor = next
        }
        assertEquals("the walk must cover the queue", 8, seen.size)
    }

    @Test
    fun aSnapshotDoesNotLeakBackIntoTheParent() {
        val state = state()
        val parent = RandomShuffleOrder(8, state, Random(3))
        val parentAnswer = parent.getNextIndex(0)

        val child = parent.cloneAndInsert(0, 1)
        child.getNextIndex(0)
        child.getNextIndex(1)
        child.getNextIndex(2)

        assertEquals("parent memo must be untouched", 1, state.memoised())
        assertEquals("parent must still answer the same way", parentAnswer, parent.getNextIndex(0))
    }

    /** The memo of the live state, which the clones must never touch. */
    private fun RandomShuffleState.memoised(): Int = memoised
}
