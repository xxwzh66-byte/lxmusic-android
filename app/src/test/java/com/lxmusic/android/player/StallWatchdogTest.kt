package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V6.1: the freeze detector behind "some songs stop at 1:50".
 *
 * Uses a fake clock so the timing budget is asserted exactly rather than slept
 * through. The four behaviours that matter:
 * - a healthy track is never touched,
 * - a paused track is not a stall,
 * - a frozen track is retried once before the source is rebuilt,
 * - a stuck buffer is detected even though the player is not "playing".
 */
class StallWatchdogTest {

    private fun watchdog() = StallWatchdog(stallMs = 8_000, bufferingMs = 20_000, escalateAfter = 2)

    @Test
    fun aHealthyTrackIsNeverTouched() {
        val w = watchdog()
        var now = 1_000L
        repeat(100) { i ->
            assertEquals(StallAction.NONE, w.onTick(i * 1_000L, playing = true, buffering = false, nowMs = now))
            now += 1_000L
        }
    }

    @Test
    fun aPausedTrackIsNotAStall() {
        val w = watchdog()
        var now = 0L
        repeat(50) {
            assertEquals(StallAction.NONE, w.onTick(5_000L, playing = false, buffering = false, nowMs = now))
            now += 1_000L
        }
    }

    @Test
    fun aFreezeIsRetriedBeforeTheSourceIsRebuilt() {
        val w = watchdog()
        // Prime with a moving position.
        w.onTick(0L, playing = true, buffering = false, nowMs = 0L)
        w.onTick(1_000L, playing = true, buffering = false, nowMs = 1_000L)

        // Freeze: the first tick only marks the start.
        assertEquals(StallAction.NONE, w.onTick(1_000L, playing = true, buffering = false, nowMs = 2_000L))
        // Still inside the budget.
        assertEquals(StallAction.NONE, w.onTick(1_000L, playing = true, buffering = false, nowMs = 9_000L))
        // Budget exhausted -> retry, not rebuild.
        assertEquals(StallAction.RETRY, w.onTick(1_000L, playing = true, buffering = false, nowMs = 11_000L))
        // A second whole budget with no progress -> rebuild.
        assertEquals(StallAction.NONE, w.onTick(1_000L, playing = true, buffering = false, nowMs = 12_000L))
        assertEquals(StallAction.REBUFFER, w.onTick(1_000L, playing = true, buffering = false, nowMs = 20_000L))
    }

    @Test
    fun progressClearsTheAttemptCount() {
        val w = watchdog()
        w.onTick(0L, playing = true, buffering = false, nowMs = 0L)
        w.onTick(1_000L, playing = true, buffering = false, nowMs = 1_000L)
        w.onTick(1_000L, playing = true, buffering = false, nowMs = 2_000L)
        assertEquals(StallAction.RETRY, w.onTick(1_000L, playing = true, buffering = false, nowMs = 11_000L))

        // The retry worked.
        assertEquals(StallAction.NONE, w.onTick(2_000L, playing = true, buffering = false, nowMs = 12_000L))
        // A later freeze gets a fresh retry, not an immediate rebuild.
        assertEquals(StallAction.NONE, w.onTick(2_000L, playing = true, buffering = false, nowMs = 13_000L))
        assertEquals(StallAction.RETRY, w.onTick(2_000L, playing = true, buffering = false, nowMs = 30_000L))
    }

    @Test
    fun aStuckBufferIsDetectedWhileNotPlaying() {
        val w = watchdog()
        assertEquals(StallAction.NONE, w.onTick(0L, playing = false, buffering = true, nowMs = 0L))
        assertEquals(StallAction.NONE, w.onTick(0L, playing = false, buffering = true, nowMs = 19_000L))
        assertEquals(StallAction.RETRY, w.onTick(0L, playing = false, buffering = true, nowMs = 20_000L))
    }

    @Test
    fun aShortBufferIsNotAStall() {
        val w = watchdog()
        assertEquals(StallAction.NONE, w.onTick(0L, playing = false, buffering = true, nowMs = 0L))
        assertEquals(StallAction.NONE, w.onTick(0L, playing = true, buffering = false, nowMs = 1_500L))
        assertEquals(StallAction.NONE, w.onTick(500L, playing = true, buffering = false, nowMs = 2_000L))
    }

    @Test
    fun resetForgetsAFreezeInProgress() {        val w = watchdog()
        w.onTick(1_000L, playing = true, buffering = false, nowMs = 0L)
        w.onTick(1_000L, playing = true, buffering = false, nowMs = 1_000L)
        w.onTick(1_000L, playing = true, buffering = false, nowMs = 10_000L)
        w.reset()
        // A rebuilt source starts from a clean slate: the same frozen position is
        // just a new observation, not a third attempt.
        assertEquals(StallAction.NONE, w.onTick(1_000L, playing = true, buffering = false, nowMs = 20_000L))
        assertEquals(StallAction.NONE, w.onTick(1_000L, playing = true, buffering = false, nowMs = 21_000L))
        assertTrue(true)
    }

    /**
     * V6.1.2: a buffer and a freeze are different fights. The buffer used to add
     * to the same [StallWatchdog] attempt counter, which only a moving position
     * cleared - so a track that buffered and then froze escalated to REBUFFER on
     * its first detection, skipping the cheap retry ADR 0002 promises.
     */
    @Test
    fun aBufferDoesNotSpendTheFreezeAttemptBudget() {
        val w = watchdog()
        // One full buffer budget burns an attempt.
        assertEquals(StallAction.NONE, w.onTick(0L, playing = false, buffering = true, nowMs = 0L))
        assertEquals(StallAction.RETRY, w.onTick(0L, playing = false, buffering = true, nowMs = 20_000L))

        // Buffering clears at the same position. The position never moved, so
        // without the reset below this freeze would count as attempt #2.
        assertEquals(StallAction.NONE, w.onTick(0L, playing = true, buffering = false, nowMs = 20_500L))
        assertEquals(StallAction.NONE, w.onTick(0L, playing = true, buffering = false, nowMs = 21_000L))
        // First freeze detection: a retry, not a rebuild.
        assertEquals(StallAction.RETRY, w.onTick(0L, playing = true, buffering = false, nowMs = 29_000L))
    }
}
