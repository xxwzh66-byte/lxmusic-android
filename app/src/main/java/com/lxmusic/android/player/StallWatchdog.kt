package com.lxmusic.android.player

/** What [StallWatchdog] wants the caller to do about a frozen playback position. */
enum class StallAction {
    /** Playback is healthy. */
    NONE,

    /** The position froze but the player believes it is playing: ask it to retry. */
    RETRY,

    /** A retry already failed: rebuild the media source (an expired stream URL, a dead codec). */
    REBUFFER,
}

/**
 * V6.1: detects playback that has silently frozen.
 *
 * The report this answers was "some songs stop at 1:50" - the position stops
 * moving while the player still says it is playing, so nothing anywhere in the
 * app decides to do anything: the progress loop keeps reading the same number
 * forever and the user has to skip the track by hand. A freeze never raises
 * `onPlayerError`, so there is nothing to react to; the only way out is to
 * notice the *absence* of progress.
 *
 * Kept free of Android types on purpose: the decision is a pure function of
 * (position, playing, buffering, clock), so it is unit-tested with a fake clock
 * instead of a device.
 *
 * Two different freezes are handled, because they look nothing alike:
 * - **stalled**: `isPlaying` is true but the position does not move. After
 *   [stallMs] the player is asked to retry, and only after a retry failed is the
 *   source rebuilt. Escalating on the *first* detection would restart the media
 *   source for a hiccup that a `prepare()`/`seekTo` fixes; escalating on the
 *   *second* fight of the same freeze is what makes an expired URL recoverable.
 * - **stuck buffering**: `isPlaying` is false while `STATE_BUFFERING` never
 *   clears. The stalled branch can never see this (it requires playing), so it
 *   is timed separately with a longer budget - a slow network legitimately
 *   buffers for a while.
 *
 * The attempt counter is cleared as soon as the position moves again, so every
 * freeze gets a fresh retry before anything heavy happens. V6.1.2: it is also
 * cleared when a buffering episode ends, because a buffer and a freeze are
 * different fights - the counter used to leak across them, so a track that
 * buffered for 20s and then froze escalated straight to [StallAction.REBUFFER]
 * after 8s and never got the cheap retry this class promises.
 */
class StallWatchdog(
    // V1.1.0: var + 默认值不变——「高级」设置页可以按设备性能调宽/调紧；
    // 改值即时生效，下一次 onTick 就用新阈值。
    var stallMs: Long = 8_000L,
    var bufferingMs: Long = 20_000L,
    private val escalateAfter: Int = 2,
) {

    private var lastPositionMs = Long.MIN_VALUE
    // Nullable rather than 0-sentineled: the clock is caller-supplied and 0 is a
    // perfectly legal first sample (elapsedRealtime() right after boot).
    private var frozenSince: Long? = null
    private var bufferingSince: Long? = null
    private var attempts = 0

    /** Forgets everything, e.g. after the caller recovered by rebuilding the source. */
    @Synchronized
    fun reset() {
        lastPositionMs = Long.MIN_VALUE
        frozenSince = null
        bufferingSince = null
        attempts = 0
    }

    /**
     * Feeds one observation. [nowMs] and [positionMs] are milliseconds on any
     * monotonic clock (`SystemClock.elapsedRealtime()` in production).
     */
    @Synchronized
    fun onTick(positionMs: Long, playing: Boolean, buffering: Boolean, nowMs: Long): StallAction {
        if (buffering) {
            // A freeze while buffering is a different failure: the player is not
            // playing, so the stalled branch below would never fire.
            frozenSince = null
            val since = bufferingSince
            if (since == null) {
                bufferingSince = nowMs
                return StallAction.NONE
            }
            if (nowMs - since < bufferingMs) return StallAction.NONE
            bufferingSince = nowMs
            attempts++
            return escalate()
        }
        if (bufferingSince != null) {
            // V6.1.2: buffering just ended. Whatever escalation count it built up
            // belonged to *that* fight; carrying it into the next freeze would let
            // a stall escalate to REBUFFER on its first detection, skipping the
            // cheap retry ADR 0002 promises. Also drop the freeze baseline: the
            // position may have jumped while buffering, and comparing against a
            // pre-buffer sample would report "still frozen" on the very first tick.
            attempts = 0
            frozenSince = null
            lastPositionMs = Long.MIN_VALUE
        }
        bufferingSince = null

        if (!playing) {
            // Paused on purpose is not a stall.
            lastPositionMs = positionMs
            frozenSince = null
            attempts = 0
            return StallAction.NONE
        }

        if (positionMs != lastPositionMs) {
            lastPositionMs = positionMs
            frozenSince = null
            attempts = 0
            return StallAction.NONE
        }

        val since = frozenSince
        if (since == null) {
            frozenSince = nowMs
            return StallAction.NONE
        }
        if (nowMs - since < stallMs) return StallAction.NONE

        frozenSince = nowMs
        attempts++
        return escalate()
    }

    private fun escalate(): StallAction =
        if (attempts >= escalateAfter) StallAction.REBUFFER else StallAction.RETRY
}
