package com.lxmusic.android.player

/**
 * The seam between "which track comes next" and "the thing that actually plays
 * it".
 *
 * Before this existed, every queue edit in [PlayerController] had to remember a
 * three-step protocol by hand - drop the installed order, mutate the playlist,
 * put an order back - and it was spelled out at more than twenty call sites.
 * Getting the order wrong once (the old `resetShuffle()` ran *before*
 * `moveMediaItem`) installed an order whose indices described the list before
 * the edit, and ExoPlayer then walked the wrong list.
 *
 * Two adapters keep this a real seam rather than a hypothetical one:
 * - production: [PlayerController] bridging to ExoPlayer through
 *   [PlayerHolder] (the [androidx.media3.session.MediaController] has no
 *   shuffle-order setter, the underlying player does);
 * - tests: the in-memory sink in `ShuffleStallProbe`, which records what was
 *   installed and can be made to refuse.
 */
interface ShuffleSink {

    /**
     * Hand the current random order to the player.
     *
     * @return true when an order is now installed. False is not necessarily a
     *   failure: the playlist edit may still be in flight, in which case the
     *   next timeline event retries.
     */
    fun install(): Boolean

    /**
     * The playlist underneath the installed order is gone.
     *
     * Must be called before every wholesale playlist replacement. Not even "the
     * order's generation did not change" may be reused across it: the queue can
     * keep its length while every slot holds a different track.
     */
    fun dropInstalled()
}
