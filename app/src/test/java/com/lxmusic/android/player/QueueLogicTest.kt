package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {

    private val q = listOf("A", "B", "C", "D")

    @Test fun moveKeepsSameTrackSelected() {
        val r = QueueLogic.move(q, index = 1, from = 0, to = 3)
        assertEquals(listOf("B", "C", "D", "A"), r.list)
        assertEquals(0, r.index)
    }

    @Test fun moveTrackItself() {
        val r = QueueLogic.move(q, index = 1, from = 1, to = 3)
        assertEquals(listOf("A", "C", "D", "B"), r.list)
        assertEquals(3, r.index)
    }

    @Test fun moveBeforeSelectedShiftsIndex() {
        val r = QueueLogic.move(q, index = 2, from = 3, to = 0)
        assertEquals(listOf("D", "A", "B", "C"), r.list)
        assertEquals(3, r.index)
    }

    @Test fun moveInvalidIsNoop() {
        val r = QueueLogic.move(q, index = 1, from = 9, to = 0)
        assertEquals(q, r.list)
        assertEquals(1, r.index)
    }

    @Test fun removeBeforeSelectedShiftsIndex() {
        val r = QueueLogic.remove(q, index = 2, removeAt = 0)
        assertEquals(listOf("B", "C", "D"), r.list)
        assertEquals(1, r.index)
        assertFalse(r.shouldPlay)
    }

    @Test fun removeSelectedAsksToPlayNext() {
        val r = QueueLogic.remove(q, index = 1, removeAt = 1)
        assertEquals(listOf("A", "C", "D"), r.list)
        assertEquals(1, r.index)
        assertTrue(r.shouldPlay)
    }

    @Test fun removeSelectedLastClampsIndex() {
        val r = QueueLogic.remove(q, index = 3, removeAt = 3)
        assertEquals(listOf("A", "B", "C"), r.list)
        assertEquals(2, r.index)
        assertTrue(r.shouldPlay)
    }

    @Test fun removeLastItemClearsQueue() {
        val r = QueueLogic.remove(listOf("A"), index = 0, removeAt = 0)
        assertTrue(r.list.isEmpty())
        assertEquals(-1, r.index)
        assertFalse(r.shouldPlay)
    }

    @Test fun nextIndexSequential() {
        assertEquals(2, QueueLogic.nextIndex(4, 1, shuffle = false, repeatAll = false))
    }

    @Test fun nextIndexAtEndStopsUnlessRepeatAll() {
        assertEquals(0, QueueLogic.nextIndex(4, 3, shuffle = false, repeatAll = true))
        assertEquals(-1, QueueLogic.nextIndex(4, 3, shuffle = false, repeatAll = false))
    }

    @Test fun nextIndexEmptyQueue() {
        assertEquals(-1, QueueLogic.nextIndex(0, -1, shuffle = false, repeatAll = true))
    }

    @Test fun prevIndexAtStartRestarts() {
        assertEquals(0, QueueLogic.prevIndex(4, 0, shuffle = false))
        assertEquals(1, QueueLogic.prevIndex(4, 2, shuffle = false))
    }
}