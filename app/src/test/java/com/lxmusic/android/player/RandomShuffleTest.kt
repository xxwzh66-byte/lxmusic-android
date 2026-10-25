package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V5.3.6: the random-playback order.
 *
 * The order is one arrangement of the queue (element `k` is the queue index that
 * plays k-th), produced by Fisher-Yates. Two properties are load-bearing:
 *
 * - every source index appears exactly once, so "next of anything" is always a
 *   different, always valid, always existing track - this is what stops the
 *   player from reading a "no next item" answer and stopping for good;
 * - the draw is uniform over the arrangements, so no position in the playlist
 *   is favoured and a track can legitimately come back after a pass.
 */
class RandomShuffleTest {

    @Test
    fun shuffledOrderIsAnArrangementOfEveryIndex() {
        for (size in 1..24) {
            for (seed in 0 until 20) {
                val order = RandomShuffle.shuffledOrder(size, Random(seed))
                assertEquals("size mismatch", size, order.size)
                assertEquals(
                    "order $order is not an arrangement of 0 until $size",
                    (0 until size).toSet(),
                    order.toSet(),
                )
            }
        }
    }

    @Test
    fun shuffledOrderLeavesNothingInPlaceByChance() {
        // A shuffle that happens to be the identity is not a bug, but one that
        // is *always* the identity is. Across many seeds the order must move.
        var identity = 0
        for (seed in 0 until 500) {
            if (RandomShuffle.shuffledOrder(8, Random(seed)).toList() == (0..7).toList()) identity++
        }
        assertTrue("shuffledOrder stayed the identity $identity times out of 500", identity < 500 / 24)
    }

    @Test
    fun everyPositionOfAQueueCanEndUpAnywhere() {
        // Uniformity: the queue index 0 must show up in every slot of the
        // shuffled order across seeds, otherwise the "random" order is not.
        val size = 6
        val slots = IntArray(size)
        for (seed in 0 until 600) slots[RandomShuffle.shuffledOrder(size, Random(seed)).indexOf(0)]++
        val expected = 100
        for (slot in 0 until size) {
            assertTrue("slot $slot off the uniform band: $slots", slots[slot] in (expected - 40)..(expected + 40))
        }
    }

    @Test
    fun shuffledOrderHandlesDegenerateSizes() {
        assertEquals(0, RandomShuffle.shuffledOrder(0, Random(1)).size)
        assertEquals(0, RandomShuffle.shuffledOrder(-3, Random(1)).size)
        assertEquals(listOf(0), RandomShuffle.shuffledOrder(1, Random(1)).toList())
    }

    @Test
    fun aTwoTrackQueueStillHasATwoWayOrder() {
        for (seed in 0 until 50) {
            val order = RandomShuffle.shuffledOrder(2, Random(seed))
            assertEquals(2, order.size)
            assertNotEquals(order[0], order[1])
        }
    }
}
