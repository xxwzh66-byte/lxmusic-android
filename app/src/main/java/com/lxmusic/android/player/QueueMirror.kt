package com.lxmusic.android.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lxmusic.android.data.model.MusicInfo

/**
 * The slice of [Player] a playlist mirror actually needs.
 *
 * Ten members instead of a hundred, so the ordering rules in [QueueMirror] can
 * be tested without an instrumented `ExoPlayer`. The production adapter is
 * [PlayerTarget]; the tests use a recorder.
 */
internal interface MirrorTarget {
    val mediaItemCount: Int
    var repeatMode: Int
    var shuffleModeEnabled: Boolean

    fun setMediaItems(items: List<MediaItem>, startIndex: Int)
    fun addMediaItems(index: Int, items: List<MediaItem>)
    fun moveMediaItem(from: Int, to: Int)
    fun removeMediaItem(index: Int)
    fun clearMediaItems()
    fun prepare()
    fun play()
}

/** Production [MirrorTarget]: everything forwards to media3. */
internal class PlayerTarget(private val playerOf: () -> Player?) : MirrorTarget {
    private val p: Player? get() = playerOf()

    override val mediaItemCount: Int get() = p?.mediaItemCount ?: 0
    override var repeatMode: Int
        get() = p?.repeatMode ?: Player.REPEAT_MODE_OFF
        set(value) { p?.repeatMode = value }
    override var shuffleModeEnabled: Boolean
        get() = p?.shuffleModeEnabled ?: false
        set(value) { p?.shuffleModeEnabled = value }

    override fun setMediaItems(items: List<MediaItem>, startIndex: Int) {
        p?.setMediaItems(items, startIndex, 0L)
    }

    override fun addMediaItems(index: Int, items: List<MediaItem>) = p?.addMediaItems(index, items) ?: Unit
    override fun moveMediaItem(from: Int, to: Int) = p?.moveMediaItem(from, to) ?: Unit
    override fun removeMediaItem(index: Int) = p?.removeMediaItem(index) ?: Unit
    override fun clearMediaItems() = p?.clearMediaItems() ?: Unit
    override fun prepare() = p?.prepare() ?: Unit
    override fun play() = p?.play() ?: Unit
}

/**
 * Keeps the player's playlist a 1:1 mirror of the app's queue.
 *
 * ## Why this is a class and not eight code blocks
 *
 * Mirroring is five things that must happen in one order:
 *
 * 1. drop the installed shuffle order (its indices describe the *old* list),
 * 2. apply the playlist edit,
 * 3. put the repeat mode back (a wholesale rebuild resets it),
 * 4. put the shuffle mode back (same reason),
 * 5. ask for an order again.
 *
 * Before this class those steps were copy-pasted into `setQueue`, `enqueue`,
 * `playNext`, `moveInQueue`, `removeFromQueue`, `clearQueue` and two rebuild
 * paths. Step 1 was once missed in one place (`resetShuffle()` ran *before*
 * `moveMediaItem`, installing an order whose indices described the list before
 * the move) and the two views silently desynchronised. The order is now a
 * property of this class instead of a convention eight call sites keep agreeing
 * on.
 *
 * ## What it hides
 *
 * `mirrored` - the flag `PlayerController.playAt` consults to choose between
 * "seek by index" and "replace the item". It used to be a `private var` on the
 * controller that eight methods had to remember to clear, and one of them
 * forgot.
 *
 * The queue list itself stays in `PlayerController`: the UI publishes it and
 * reorders it, so this class reads it through [queue] and never holds a copy.
 */
internal class QueueMirror(
    private val sink: ShuffleSink,
    /** The queue list, live. */
    private val queue: () -> List<MusicInfo>,
    /**
     * Builds a MediaItem for one queue entry, or null when the track resolves
     * only asynchronously (an online source).
     *
     * One function instead of a `uriOf` + `itemOf` pair: "can I put this in the
     * playlist?" and "here it is" are the same question, and two signatures let
     * them disagree.
     */
    private val itemOf: (MusicInfo) -> MediaItem?,
    /** The queue slot the app considers current. */
    private val currentSlot: () -> Int,
    /** Tells the app which slot is current now. */
    private val selectSlot: (Int) -> Unit,
    private val repeatFlag: () -> Int,
    private val shuffleFlag: () -> Boolean,
    private val isPlaying: () -> Boolean,
    /**
     * The queue changed: drop the memoised draws and reinstall the order. Same
     * contract as the old `resetShuffle()` - an answer drawn for the old
     * length means nothing against the new one.
     */
    private val onQueueMutated: () -> Unit,
) {
    /** Injected by `PlayerController`; replaced in tests. */
    internal var target: MirrorTarget? = null

    /**
     * True while the player holds exactly the queue, so `PlayerController.playAt`
     * may seek by index and the system notification gets real next/previous.
     */
    var isMirrored: Boolean = false
        private set

    /** All items, or null if any entry cannot go into the playlist now. */
    private fun items(list: List<MusicInfo>): List<MediaItem>? {
        val built = ArrayList<MediaItem>(list.size)
        for (track in list) built += itemOf(track) ?: return null
        return built
    }

    /** Steps 3-5: modes back in place, then a fresh order. */
    private fun resync(t: MirrorTarget) {
        t.repeatMode = repeatFlag()
        t.shuffleModeEnabled = shuffleFlag()
        onQueueMutated()
    }

    /**
     * Replace the playlist with [list], selecting [startIndex].
     *
     * Returns false when some track resolves only asynchronously (an online
     * source). The caller then plays single items instead - which is also why
     * shuffle keeps working for an online queue even though the notification
     * buttons cannot walk it.
     */
    fun replace(list: List<MusicInfo>, startIndex: Int): Boolean {
        val t = target ?: return false
        val built = items(list) ?: return false
        if (built.isEmpty()) return false
        val at = startIndex.coerceIn(0, built.lastIndex)
        sink.dropInstalled()
        isMirrored = true
        selectSlot(at)
        t.setMediaItems(built, at)
        resync(t)
        t.prepare()
        t.play()
        return true
    }

    /**
     * Guarantee the player holds the whole queue before shuffle picks a target.
     *
     * The shared random order can only be installed on a playlist of the queue's
     * length, and only such a player surfaces next/previous to the notification,
     * lockscreen, bluetooth and fluid-cloud. Playback is preserved: the current
     * track stays selected and keeps playing.
     */
    fun ensure(): Boolean {
        val t = target ?: return false
        val list = queue()
        if (isMirrored && t.mediaItemCount == list.size) return true
        if (list.size < 2) return false
        val built = items(list) ?: return false
        val keep = currentSlot().coerceIn(0, built.lastIndex)
        sink.dropInstalled()
        isMirrored = true
        selectSlot(keep)
        t.setMediaItems(built, keep)
        resync(t)
        if (isPlaying()) t.play()
        return true
    }

    /**
     * Insert [list] at playlist position [at].
     *
     * A wholesale rebuild happens only when one of the new tracks cannot be
     * resolved synchronously: patching a playlist entry-by-entry with a track
     * the player cannot open leaves a mirror that looks complete and is not.
     */
    fun insert(list: List<MusicInfo>, at: Int): Boolean {
        val t = target ?: return false
        if (!isMirrored) return false
        val added = items(list) ?: return rebuild(t)
        if (added.isEmpty()) return false
        sink.dropInstalled()
        t.addMediaItems(at.coerceIn(0, queue().size), added)
        resync(t)
        return true
    }

    /**
     * Move one playlist entry.
     *
     * The player is moved **first**; only then is the order rebuilt. The other
     * way round installs an order whose indices describe the pre-move list, and
     * ExoPlayer then walks the wrong list.
     */
    fun move(from: Int, to: Int): Boolean {
        val t = target ?: return false
        if (!isMirrored) return false
        t.moveMediaItem(from, to)
        sink.dropInstalled()
        resync(t)
        return true
    }

    /** Remove one playlist entry. */
    fun remove(at: Int): Boolean {
        val t = target ?: return false
        if (!isMirrored) return false
        t.removeMediaItem(at)
        sink.dropInstalled()
        resync(t)
        return true
    }

    /** The queue is gone: drop the playlist and the flag with it. */
    fun clear() {
        target?.clearMediaItems()
        sink.dropInstalled()
        isMirrored = false
    }

    /**
     * The playlist was replaced by a single item outside the mirror's back.
     *
     * Three paths do that: `playIndex`, `prepareRestored`, and
     * `rebuildFrozenSource`. Leaving `isMirrored` true made every later `playAt`
     * seek to index 0 of a one-item playlist while the app queue still held N
     * entries - the button looked stuck, with no error and no log.
     */
    fun dropToSingleItem() {
        sink.dropInstalled()
        isMirrored = false
    }

    /** Replace the whole playlist with the queue, keeping the current slot. */
    private fun rebuild(t: MirrorTarget): Boolean {
        val built = items(queue()) ?: return false
        if (built.isEmpty()) return false
        val keep = currentSlot().coerceIn(0, built.lastIndex)
        sink.dropInstalled()
        isMirrored = true
        t.setMediaItems(built, keep)
        resync(t)
        t.prepare()
        if (isPlaying()) t.play()
        return true
    }
}
