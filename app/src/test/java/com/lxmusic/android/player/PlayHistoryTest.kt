package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V6.1.1: [PlayHistory] 是「上一首」唯一的数据来源，之前没有直接的单测——
 * 只在 [ShuffleStallProbe] 里被间接走了一遍。这里的每个用例对应一条真实行为，
 * 失败即意味着随机模式下按上一首会跳到没听过的歌、或原地打转。
 */
class PlayHistoryTest {

    @Test
    fun anEmptyHistoryHasNothingToGoBackTo() {
        val h = PlayHistory()
        assertNull(h.confirmBack())
        assertEquals(0, h.size)
    }

    @Test
    fun leavingATrackPushesIt() {
        val h = PlayHistory()
        h.onTrackChanged("b", leftId = "a")
        assertEquals(listOf("a"), h.entries)
    }

    @Test
    fun goingBackPopsTheLastTrackAndIsNotRecordedAgain() {
        val h = PlayHistory()
        h.onTrackChanged("b", leftId = "a")
        h.onTrackChanged("c", leftId = "b")
        assertEquals(listOf("a", "b"), h.entries)

        // User presses previous: target is the track we just left.
        assertEquals("b", h.confirmBack())
        // The transition that lands on "b" reports it; it was already popped,
        // so it must not be pushed back (otherwise previous ping-pongs).
        assertTrue(h.onTrackChanged("b", leftId = "c"))
        assertEquals(listOf("a"), h.entries)

        assertEquals("a", h.confirmBack())
        assertTrue(h.onTrackChanged("a", leftId = "b"))
        assertEquals(emptyList<String>(), h.entries)
    }

    @Test
    fun aForwardTransitionAfterABackIsRecordedNormally() {
        val h = PlayHistory()
        h.onTrackChanged("b", leftId = "a")
        assertEquals("a", h.confirmBack())
        h.onTrackChanged("a", leftId = "b")

        // Now the user goes forward to a brand new track: that is real history.
        assertFalse(h.onTrackChanged("c", leftId = "a"))
        assertEquals(listOf("a"), h.entries)
    }

    @Test
    fun aTransitionThatMissesTheExpectedTargetIsStillRecorded() {
        val h = PlayHistory()
        h.onTrackChanged("b", leftId = "a")
        h.onTrackChanged("c", leftId = "b")
        assertEquals(listOf("a", "b"), h.entries)
        assertEquals("b", h.confirmBack())

        // The player did not land where we asked (e.g. the track failed to
        // resolve) and played "c" instead. The popped "b" is gone, but the
        // new transition must still be recorded - otherwise history silently
        // stops growing after one failure.
        assertFalse(h.onTrackChanged("c", leftId = "a"))
        assertEquals(listOf("a"), h.entries)
    }

    @Test
    fun pushingTheSameTrackTwiceInARowIsIgnored() {
        val h = PlayHistory()
        h.onTrackChanged("b", leftId = "a")
        h.onTrackChanged("c", leftId = "a") // tapping the same track again
        assertEquals(listOf("a"), h.entries)
    }

    @Test
    fun theStackIsBoundedByCapacity() {
        val h = PlayHistory(capacity = 3)
        h.onTrackChanged("1", leftId = "0")
        h.onTrackChanged("2", leftId = "1")
        h.onTrackChanged("3", leftId = "2")
        h.onTrackChanged("4", leftId = "3")
        assertEquals(listOf("1", "2", "3"), h.entries)
    }

    @Test
    fun clearDropsEverythingIncludingAPendingBack() {
        val h = PlayHistory()
        h.onTrackChanged("b", leftId = "a")
        assertEquals("a", h.confirmBack())
        h.clear()
        assertEquals(0, h.size)
        assertNull(h.confirmBack())
        // A late transition from the old queue must not be mistaken for the
        // cancelled back-press.
        assertFalse(h.onTrackChanged("a", leftId = "z"))
    }

    @Test
    fun aCopyIsASnapshotNotASharedView() {
        val h = PlayHistory()
        h.onTrackChanged("b", leftId = "a")
        val snap = h.copy()
        h.clear()
        assertEquals(listOf("a"), snap.entries)
        assertEquals("a", snap.confirmBack())
    }
}
