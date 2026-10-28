package com.lxmusic.android.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import com.google.common.util.concurrent.MoreExecutors
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.lyric.Lyric
import com.lxmusic.android.lyric.LyricLoader
import com.lxmusic.android.source.LxQuality
import com.lxmusic.android.source.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Repeat modes for the player. */
enum class RepeatMode { OFF, ALL, ONE }

/** Playback state observed by the UI. */
data class PlayerState(
    val current: MusicInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lyric: Lyric = Lyric(emptyList()),
    val lyricIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    /** Current play queue (snapshot for the UI, always in queue order). */
    val queue: List<MusicInfo> = emptyList(),
    /** Index of [current] within [queue], or -1. */
    val queueIndex: Int = -1,
    val error: String? = null,
)

/**
 * Drives ExoPlayer. Local tracks play straight from their file / content Uri;
 * online tracks resolve a stream URL through the custom-source [SourceManager].
 *
 * V1.0.1: shuffle is a **fresh draw per decision, in both directions**.
 *
 * Every press of next picks a uniform random slot other than the current one;
 * every press of previous does the same. There is no shuffle cycle any more -
 * a cycle meant the order of a pass was fixed the moment it was drawn, which is
 * exactly the "the play order is not random, it is fixed" complaint. "Previous"
 * is not a walk back through a remembered stack either: the user asked for it to
 * be random too.
 *
 * What keeps the two decision paths honest is that app buttons and ExoPlayer's
 * installed [RandomShuffleOrder] share one [RandomShuffleState], and that state
 * memoises one draw per (direction, track) so media3's several questions inside
 * a single decision all get the same answer. The memo is dropped by
 * [RandomShuffleState.onTrackStarted] - i.e. when a track really starts.
 */
class PlayerController(
    private val context: Context,
    private val sourceManager: SourceManager,
    private val lyricLoader: LyricLoader = LyricLoader(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /**
     * V6.1.1: written from the MediaController future's completion thread and
     * read from Main. Without @Volatile a reader can keep seeing null (or a
     * half-published reference) after connect() "finished", which shows up as
     * buttons that do nothing rather than as a crash.
     */
    @Volatile
    private var controller: MediaController? = null

    /** Invoked when a track begins playing (used by the UI for scrobbling). */
    var onTrackStarted: ((MusicInfo) -> Unit)? = null

    /**
     * V5.3.3: supplies the surrounding tracks when the queue has collapsed to a
     * single item (which happens after restoring the last-played track).
     *
     * Asking at press time rather than at restore time means the queue is
     * rebuilt with whatever library state exists right now, and a press on
     * "next" is never a no-op just because the library was still loading when
     * the app started.
     */
    var onNeedBackfill: (suspend (currentId: String) -> List<MusicInfo>)? = null

    /** System audio effects bound to this player. */
    val equalizer = EqualizerController()

    val queue = mutableListOf<MusicInfo>()

    /**
     * Position of the current track inside [queue]; -1 before anything plays.
     *
     * V6.1.2: this used to be called `index`, which the GLOSSARY lists as a word
     * to avoid ("playing index" reads equally well as the player's own index, a
     * position in the shuffle cycle, or this queue slot). It is the queue
     * slot, and it is what [playAt] takes.
     */
    private var currentIndex = -1
    var repeatMode: RepeatMode = RepeatMode.OFF

    /**
     * V1.1: the shuffle order's two operations, as the seam [QueueMirror] drives.
     *
     * Naming the pair was the point: `installShuffleOrder()` and
     * `forgetInstalledOrder()` were always called together with a playlist edit
     * in between, but nothing said so, and one call site got the order wrong.
     */
    private val shuffleSink = object : ShuffleSink {
        override fun install(): Boolean = installShuffleOrder()

        override fun dropInstalled() = forgetInstalledOrder()
    }

    /**
     * V1.1: keeps the player's playlist a 1:1 mirror of [queue].
     *
     * Owns what used to be `playlistMode` plus the five-step edit sequence that
     * eight methods had copy-pasted. See [QueueMirror] for the reasoning.
     */
    private val mirror = QueueMirror(
        sink = shuffleSink,
        queue = { queue },
        itemOf = { m -> resolvableUri(m)?.let { buildMediaItem(m, it) } },
        currentSlot = { currentIndex },
        selectSlot = { currentIndex = it },
        repeatFlag = { repeatMode.toPlayerRepeatMode() },
        shuffleFlag = { shuffle },
        isPlaying = { _state.value.isPlaying },
        onQueueMutated = { resetShuffle() },
    ).also { it.target = PlayerTarget { controller } }

    /** Lyric timing calibration in milliseconds (positive = lyrics appear earlier). */
    var lyricOffsetMs: Int = 0

    /** Whether a sibling `.lrc` file wins over embedded tags. */
    var preferLrcFile: Boolean = true

    /** Whether embedded lyrics are read from the audio tags. */
    var readEmbeddedLyric: Boolean = true
    var shuffle = false

    /**
     * V5.3.2: the random-playback policy state, shared by the app-side buttons
     * and by the [RandomShuffleOrder] handed to ExoPlayer, so both draw from the
     * same source.
     *
     * V1.0.1: one uniform draw per decision, in both directions. See the class
     * KDoc - the cycle is gone, because a cycle fixes the play order.
     */
    val shuffleState = RandomShuffleState()

    /** V5.1-4: notified whenever the user flips repeat/shuffle. */
    var onModeChanged: ((RepeatMode, Boolean) -> Unit)? = null

    /** V5.1-3: notified with (trackId, positionMs) for resume persistence. */
    var onProgress: ((String, Long) -> Unit)? = null
    private var lastSavedId: String? = null
    private var lastSavedPosMs: Long = -1L

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _state.value = _state.value.copy(isBuffering = true)
                        Player.STATE_READY -> {
                            _state.value = _state.value.copy(isBuffering = false)
                            attachEqualizer()
                        }
                        Player.STATE_ENDED -> onTrackEnded()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _state.value = _state.value.copy(error = error.message)
                }

                override fun onTimelineChanged(
                    timeline: androidx.media3.common.Timeline,
                    reason: Int,
                ) {
                    // V5.3.1: MediaController dispatches playlist edits to the
                    // session asynchronously, so right after setMediaItems() the
                    // ExoPlayer still holds the *old* item count and
                    // ExoPlayerImpl.setShuffleOrder would fail its length
                    // assertion. This is the first moment the new playlist is
                    // actually in place, so the shared random policy is
                    // (re)installed from here.
                    installShuffleOrder()
                    // V6.1.2: a seek requested before this event could land (see
                    // playAt). The playlist is only now the right length, so this
                    // is the first moment seeking by index is meaningful.
                    resolvePendingSeek()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // V6.2: resolve the queue slot from the item's media id, not
                    // from the player's index. playIndex() plays a ONE-item
                    // playlist, so the index this callback reports is always 0
                    // while the app is playing queue[i]; mapping by index alone
                    // republished queue[0] over the track the user picked and
                    // reset currentIndex to 0, which is why "next" could never
                    // walk past the second track of an online queue.
                    val i = queueIndexOf(
                        mediaId = mediaItem?.mediaId,
                        queue = queue,
                        playerIndex = controller?.currentMediaItemIndex ?: -1,
                    )
                    if (i < 0) return
                    val music = queue.getOrNull(i) ?: return
                    if (currentIndex == i && _state.value.current?.id == music.id) return
                    val changed = currentIndex != i
                    currentIndex = i
                    if (changed) {
                        // V6.1: a new track starts from zero, so a freeze count
                        // inherited from the previous one would escalate on the
                        // very first tick.
                        watchdog.reset()
                    }
                    // V1.0.1: the ONE place the draw memo is dropped for every
                    // path media3 itself drives - notification, lockscreen,
                    // bluetooth, car head unit, and the native auto-advance.
                    //
                    // 这里不能像 v5.3.2 那样调 `invalidate()`：那会把 lastTrack
                    // 也清掉，于是「上一首避开刚听完的那首」失去依据，而且会让
                    // generation 无谓递增、逼安装点重装 order。`onTrackStarted`
                    // 只丢两个抽样缓存。
                    //
                    // app 按键那条路径不经过这里时由 playIndex 自己调一次，两条
                    // 路径各自负责自己的那一半（见 RandomShuffleState.onTrackStarted
                    // 的 KDoc 上写的「任一路径漏掉」的后果）。
                    if (changed) shuffleState.onTrackStarted(queue.size, i)
                    _state.value = _state.value.copy(
                        current = music, error = null, positionMs = 0, durationMs = 0,
                        lyric = Lyric(emptyList()), lyricIndex = -1,
                        queue = queue.toList(), queueIndex = i,
                    )
                    onTrackStarted?.invoke(music)
                    loadLyric(music)
                }
            })
            startProgressLoop()
        }, MoreExecutors.directExecutor())
    }

    private fun onTrackEnded() {
        if (mirror.isMirrored) return  // queue advance/repeat is driven natively by ExoPlayer
        when (repeatMode) {
            RepeatMode.ONE -> playAt(currentIndex)
            else -> scope.launch { advanceAfterTrackEnd() }
        }
    }

    /** Auto advance when a track finishes: a fresh shuffle draw, or the next queue slot. */
    private suspend fun advanceAfterTrackEnd() {
        if (queue.isEmpty()) return
        if (shuffle) {
            // V1.0.1: the auto-advance path is a decision like any other, so it
            // draws. (V6.1 made it walk the cycle so it could not disagree with
            // the buttons; with the cycle gone the same guarantee comes from the
            // shared memo instead - one draw per decision, whoever asked.)
            if (queue.size <= 1) return
            val target = shuffleState.nextIndex(queue.size, currentIndex)
            if (target < 0) return
            playIndex(target)
            return
        }
        if (currentIndex + 1 < queue.size) playIndex(currentIndex + 1)
        else if (repeatMode == RepeatMode.ALL && queue.isNotEmpty()) playIndex(0)
    }

    fun setQueue(list: List<MusicInfo>, startIndex: Int) {
        queue.clear(); queue.addAll(list); currentIndex = startIndex
        resetShuffle()
        publishQueue()
        if (mirror.replace(queue, startIndex)) {
            attachEqualizer()
            return
        }
        scope.launch { playIndex(currentIndex) }
    }


    /**
     * URI for a local track's file, or null when it carries no path.
     *
     * Both `content://` (SAF-scanned) and plain filesystem paths (legacy scan,
     * manual pick) reach here, which is why the scheme has to be sniffed rather
     * than assumed. Shared by [resolvableUri], [playIndex] and
     * [rebuildFrozenSource] - all three used to spell this out separately.
     */
    private fun localFileUri(m: MusicInfo): Uri? {
        val raw = m.localPath ?: m.extra["uri"] ?: return null
        return if (raw.startsWith("content://")) Uri.parse(raw) else Uri.fromFile(File(raw))
    }

    /** Synchronous URI for local tracks, null when async resolution is needed. */
    private fun resolvableUri(m: MusicInfo): Uri? = if (m.isLocal) localFileUri(m) else null

    /** Builds a MediaItem with full metadata (title/artist/album/artwork) for session + notification. */
    private fun buildMediaItem(m: MusicInfo, uri: Uri?): MediaItem {
        val builder = MediaMetadata.Builder()
            .setTitle(m.name)
            .setArtist(m.singer)
            .setAlbumTitle(m.albumName)
        // Notification / fluid-cloud artwork: absolute file paths must become file:// URIs.
        m.img?.let { raw ->
            runCatching {
                if (raw.startsWith("content://") || raw.startsWith("file://") || raw.startsWith("http")) {
                    Uri.parse(raw)
                } else {
                    Uri.fromFile(File(raw))
                }
            }.getOrNull()?.let { builder.setArtworkUri(it) }
        }
        val item = MediaItem.Builder().setMediaId(m.id).setMediaMetadata(builder.build())
        uri?.let { item.setUri(it) }
        return item.build()
    }

    /**
     * V5.3.6: start queue entry [i].
     *
     * The mirrored path seeks by index, which is what the system next/previous
     * buttons need in order to agree with the app. It is only taken when the
     * player really holds [queue]: `ExoPlayerImpl.seekTo` **silently returns**
     * when the index is outside the timeline it currently holds, so seeking a
     * half-applied playlist edit used to do nothing at all - the button read as
     * stuck with no error and no log. Falling back to [playIndex] always
     * produces a real transition.
     */
    fun playAt(i: Int) {
        if (i !in queue.indices) return
        if (mirror.isMirrored) {
            if (PlayerHolder.player?.mediaItemCount == queue.size) {
                controller?.seekTo(i, 0L)
                controller?.play()
                return
            }
            // V6.1.2: the mirror was *just* requested. MediaController dispatches
            // playlist edits to the session asynchronously, so mediaItemCount is
            // still the old one right after mirror.ensure() returned true.
            // Falling through to playIndex() here cleared the mirror and
            // silently undid it - the first shuffle press after a
            // single-track restore walked one track and lost the playlist.
            // Defer the seek to the timeline event that lands the edit.
            pendingSeekIndex = i
            controller?.play()
            return
        }
        scope.launch { playIndex(i) }
    }

    /**
     * V6.1.2: queue index [playAt] could not seek to yet because the mirrored
     * playlist edit was still in flight, or -1. Resolved in [onTimelineChanged].
     */
    private var pendingSeekIndex = -1

    /**
     * V6.1.2: replay a [playAt] that arrived while the mirrored playlist was
     * still being applied.
     *
     * A seek against the wrong-length timeline is dropped silently by
     * `ExoPlayerImpl.seekTo`, so this must happen after the edit lands. The
     * pending index is cleared first: a stale value surviving one event would
     * jump the user later, and re-entrancy here is plausible (seekTo can itself
     * produce a timeline event).
     */
    private fun resolvePendingSeek() {
        val target = pendingSeekIndex
        if (target < 0) return
        pendingSeekIndex = -1
        val count = PlayerHolder.player?.mediaItemCount ?: return
        if (target >= count || count != queue.size) return
        controller?.seekTo(target, 0L)
        controller?.play()
    }

    /**
     * V5.3.6: guarantee the player holds [queue] when shuffle needs it.
     *
     * The shared random order can only be installed on a playlist whose length
     * equals the queue's, and only such a player surfaces next/previous to the
     * notification, lockscreen, bluetooth and fluid-cloud. Single-item mode (a
     * restored track, an online source) can never reach that length, so the
     * system next button used to walk ExoPlayer's own order instead of ours and
     * stopped when it ran out of one shuffle order.
     *
     * Returns true when the queue is now mirrored and [playAt] may seek by
     * index. Playback is preserved: the current track stays selected and keeps
     * playing.
     *
     * V1.1: the work moved to [QueueMirror.ensure]; this stays so the shuffle
     * paths read the same and so the queue gets published on the one branch that
     * changed the current slot.
     */
    private fun ensureMirrorPlaylist(): Boolean {
        val ok = mirror.ensure()
        if (ok) publishQueue()
        return ok
    }

    // ---------------- queue editing ----------------

    /** Append tracks to the end of the queue. */
    fun enqueue(list: List<MusicInfo>) {
        if (list.isEmpty()) return
        queue.addAll(list)
        resetShuffle()
        publishQueue()
        mirror.insert(list, queue.size - list.size)
    }

    /** Insert tracks to play next (right after the current track). */
    fun playNext(list: List<MusicInfo>) {
        if (list.isEmpty()) return
        val at = (currentIndex + 1).coerceIn(0, queue.size)
        queue.addAll(at, list)
        resetShuffle()
        publishQueue()
        mirror.insert(list, at)
    }

    /** Move a queue entry, keeping the currently-playing track selected. */
    fun moveInQueue(from: Int, to: Int) {
        val r = QueueLogic.move(queue, currentIndex, from, to)
        queue.clear(); queue.addAll(r.list); currentIndex = r.index
        publishQueue()
        if (!mirror.move(from, to)) resetShuffle()
    }

    /** Remove a queue entry. If it is the current track, advance appropriately. */
    fun removeFromQueue(position: Int) {
        if (position !in queue.indices) return
        val r = QueueLogic.remove(queue, currentIndex, position)
        queue.clear(); queue.addAll(r.list); currentIndex = r.index
        if (queue.isEmpty()) {
            mirror.clear()
            _state.value = _state.value.copy(current = null, isPlaying = false)
        } else if (r.shouldPlay) {
            scope.launch { playIndex(currentIndex) }
        } else {
            mirror.remove(position)
        }
        resetShuffle()
        publishQueue()
    }

    fun clearQueue() {
        queue.clear()
        currentIndex = -1
        shuffleState.invalidate()
        mirror.clear()
        _state.value = _state.value.copy(current = null, isPlaying = false)
        publishQueue()
    }

    /**
     * Expands a one-track queue so shuffle has somewhere to go.
     *
     * Returns true when the queue now holds more than one track. [onNeedBackfill]
     * is consulted only in the degenerate case, so a normal queue never pays for
     * this.
     */
    private suspend fun ensureShuffleableQueue(): Boolean {
        if (queue.size > 1) return true
        val current = queue.getOrNull(currentIndex) ?: return false
        val extra = onNeedBackfill?.invoke(current.id).orEmpty()
        if (extra.size > 1) backfillQueue(extra, current.id)
        return queue.size > 1
    }

    private fun publishQueue() {
        _state.value = _state.value.copy(queue = queue.toList(), queueIndex = currentIndex)
    }

    /**
     * V6.1.1: the RepeatMode -> Player.REPEAT_MODE_* mapping was spelled out at
     * four call sites. One of them drifting would make the toggle and the
     * mirror disagree, which is exactly the class of bug this file keeps
     * producing.
     */
    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

    /**
     * V6.1: "next".
     *
     * Two paths, one shared order:
     * - shuffle: the target comes from [RandomShuffleState.nextIndex] - the
     *   *same* answer ExoPlayer's installed [RandomShuffleOrder] gives the
     *   notification, lockscreen, bluetooth and auto-advance. Deliberately not
     *   a separate draw of its own: the memo in [RandomShuffleState] is what
     *   makes one decision answer consistently, and it is dropped the moment
     *   the track starts.
     * - sequential: the next queue slot, or nothing at the end (repeat ALL
     *   wraps, repeat OFF stops - ExoPlayer honours it because the mirrored
     *   playlist has a real end).
     *
     * When the queue cannot be mirrored (an online track, or a one-track
     * restore) shuffle still works: [playAt] falls back to a single-item play
     * for the target, so only the *notification* buttons need the mirror.
     * Before V6.2 this returned early and the button looked dead.
     */
    suspend fun next() {
        if (queue.isEmpty()) return
        if (shuffle) {
            if (!ensureShuffleableQueue()) return
            // V6.2: the mirror is best-effort. An online queue holds tracks
            // whose URL is only known at play time, so ensureMirrorPlaylist()
            // can never succeed for it. Returning here (the old behaviour) made
            // the button a silent no-op; playAt() knows how to play a single
            // item, so the button keeps working either way.
            ensureMirrorPlaylist()
            val target = shuffleState.nextIndex(queue.size, currentIndex)
            if (target < 0) return
            playAt(target)
            return
        }
        if (currentIndex + 1 < queue.size) {
            playAt(currentIndex + 1)
        } else if (repeatMode == RepeatMode.ALL) {
            playAt(0)
        } else if (mirror.isMirrored) {
            controller?.seekToNextMediaItem()
        }
    }

    /**
     * V1.0.1: "previous".
     *
     * In shuffle mode this is a **fresh uniform draw**, exactly like [next] and
     * exactly what ExoPlayer's installed [RandomShuffleOrder] answers to the
     * notification / lockscreen / bluetooth previous button. The user asked for
     * both directions to be random, so neither this nor the system button walks
     * a remembered stack.
     *
     * Two policies came before and both are gone:
     * - the shuffle cycle's predecessor, which is a track this pass has not
     *   reached yet - it sounded like a random jump *forward*;
     * - the play-history stack, which answered -1 when empty, and media3 reads
     *   -1 as "no previous item" and restarts the current track from zero. That
     *   is the "first headset previous replays this song" report; the fix is not
     *   to special-case the empty stack but to stop handing media3 a -1 at all.
     *
     * [RandomShuffleState.previousIndex] also avoids the track that just played,
     * so pressing previous twice in a row cannot bounce between the same two.
     */
    suspend fun prev() {
        if (queue.isEmpty()) return
        if (shuffle) {
            if (!ensureShuffleableQueue()) return
            ensureMirrorPlaylist()
            val target = shuffleState.previousIndex(queue.size, currentIndex)
            if (target < 0) {
                // Single-track queue: nothing to draw. Restart this one, which is
                // what every mainstream player does.
                if (currentIndex in queue.indices) playAt(currentIndex)
                return
            }
            playAt(target)
            return
        }
        if (currentIndex - 1 >= 0) playAt(currentIndex - 1)
        else if (mirror.isMirrored) controller?.seekToPreviousMediaItem()
        else playAt(0)
    }

    private suspend fun playIndex(i: Int) {
        val music = queue.getOrNull(i) ?: return
        currentIndex = i
        // V1.1: this path drops the mirrored playlist, so the flag has to go
        // with it. See [QueueMirror.dropToSingleItem] for the bug that caused
        // when the two disagreed.
        mirror.dropToSingleItem()
        // V1.0.1: the draw memo is deliberately *not* released here. This path
        // sets `current` before setMediaItem, so onMediaItemTransition sees
        // "unchanged" and returns early - which is why the release happens
        // further down, after the URI resolves and the player really starts.
        // Releasing it here would let a track whose source fails still count as
        // "started". Callers that change the queue itself call [resetShuffle].
        _state.value = _state.value.copy(
            current = music, error = null, positionMs = 0, durationMs = 0,
            lyric = Lyric(emptyList()), lyricIndex = -1,
            queue = queue.toList(), queueIndex = currentIndex,
        )

        val uri: Uri = try {
            if (music.isLocal) {
                localFileUri(music) ?: throw IllegalStateException("local track has no path")
            } else {
                val quality = pickQuality(music)
                val url = withContext(Dispatchers.IO) { sourceManager.resolveMusicUrl(music, quality).url }
                Uri.parse(url)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message); return
        }

        controller?.run {
            setMediaItem(buildMediaItem(music, uri)); prepare(); play()
        }
        // V1.0.1: release the draw memo only now that the track really plays.
        // Running it before the URI is resolved would let a track whose source
        // fails still count as "started", so the next press would re-serve a
        // draw for a track nobody heard.
        //
        // onTrackStarted is idempotent, so the onMediaItemTransition callback -
        // which also fires for this track - calling it again is harmless.
        shuffleState.onTrackStarted(queue.size, i)
        attachEqualizer()
        onTrackStarted?.invoke(music)

        loadLyric(music)
    }

    /** Lyrics: local files from disk/tags; online tracks have none. */
    private fun loadLyric(music: MusicInfo) {
        scope.launch {
            val lyric = try {
                if (music.isLocal) {
                    lyricLoader.loadLocal(
                        music.localPath ?: music.extra["uri"].orEmpty(),
                        preferLrcFile = preferLrcFile,
                        readEmbedded = readEmbeddedLyric,
                    )
                } else {
                    Lyric(emptyList())
                }
            } catch (_: Exception) { Lyric(emptyList()) }
            _state.value = _state.value.copy(lyric = lyric)
        }
    }

    /** Binds system audio effects to the real ExoPlayer audio session. */
    private fun attachEqualizer() {
        val session = com.lxmusic.android.player.PlayerHolder.player?.audioSessionId ?: 0
        if (session != 0) equalizer.attach(session)
    }

    /** Preferred quality id from settings ("128k"/"320k"/"flac"/"flac24bit"); null = no preference. */
    var preferredQualityId: String? = null

    private fun pickQuality(music: MusicInfo): LxQuality {
        // A5: user preference first, then best-available fallback.
        preferredQualityId?.let { pref ->
            if (music.qualitys.containsKey(pref)) return LxQuality.fromId(pref) ?: LxQuality.K128
        }
        val order = listOf(LxQuality.FLAC24BIT, LxQuality.FLAC, LxQuality.K320, LxQuality.K128)
        return order.firstOrNull { music.qualitys.containsKey(it.id) } ?: LxQuality.K128
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun cycleRepeat(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        controller?.repeatMode = repeatMode.toPlayerRepeatMode()
        _state.value = _state.value.copy(repeatMode = repeatMode)
        onModeChanged?.invoke(repeatMode, shuffle)
        return repeatMode
    }

    fun toggleShuffle(): Boolean {
        shuffle = !shuffle
        // V5.3.2: entering shuffle starts fresh draws; leaving it drops them but
        // leaves the queue order untouched.
        shuffleState.invalidate()
        if (shuffle) installShuffleOrder()
        controller?.shuffleModeEnabled = shuffle
        _state.value = _state.value.copy(shuffle = shuffle)
        onModeChanged?.invoke(repeatMode, shuffle)
        return shuffle
    }

    /**
     * V5.3.1: (re)start the random pass and publish the policy to ExoPlayer.
     *
     * Called whenever the queue itself changes (new queue, reorder, insert,
     * remove), because a pass counter measured against the old queue length
     * means nothing once the queue has a different size.
     */
    private fun resetShuffle() {
        shuffleState.invalidate()
        installShuffleOrder()
    }

    /**
     * V5.3.1: hand ExoPlayer the same random policy the in-app buttons use, so
     * the notification / lockscreen / bluetooth / fluid-cloud next action draws
     * from the identical source instead of ExoPlayer's own shuffle RNG.
     *
     * MediaController exposes no shuffle-order setter, but the underlying
     * ExoPlayer does, so we reach it through [PlayerHolder].
     *
     * ExoPlayer asserts the order length matches the playlist it currently holds
     * (`ExoPlayerImpl.setShuffleOrder` -> `Assertions.checkArgument`). A mismatch
     * used to be swallowed silently by a `runCatching`, which left ExoPlayer
     * walking its own order while the app believed ours was installed.
     *
     * V6.1.1: the mismatch was logged here and handed to an
     * `onShuffleOrderRejected` hook. The hook had no subscribers at all, which
     * made the previous KDoc claim ("surfaced instead of disappearing") untrue -
     * the failure went nowhere.
     *
     * V6.2.0: the hook is gone. A diagnostic with no listener is worse than a
     * log line: it reads like someone is watching. Both outcomes now go to
     * logcat under the same tag as the rest of the player.
     */
    private fun installShuffleOrder(): Boolean {
        if (installingShuffleOrder) return false
        val exo = PlayerHolder.player ?: return false
        if (!shuffle || queue.isEmpty()) return false
        val length = exo.mediaItemCount
        if (length != queue.size) {
            // Not a failure yet: the playlist edit may still be in flight. The
            // timeline listener retries once it has actually landed.
            Log.w(TAG, "shuffle order deferred: playlist=$length queue=${queue.size}")
            return false
        }
        // V6.1: 这是「切模式卡顿」的根因所在。
        //
        // ExoPlayerImpl.setShuffleOrder 会 createMaskingTimeline() 造一个新的
        // PlaylistTimeline 实例，而 PlaylistTimeline 没有重写 equals ——
        // updatePlaybackInfo 里的 Timeline.equals 因此必然为 false，于是它
        // 派发 onTimelineChanged 回到本函数，本函数又装一次、又造一个新
        // timeline……一次开关随机变成一串 rebuild，跨进程的 MediaController
        // 再把每一轮放大成真正的主线程停顿。
        //
        // 装进去的那份 order 背后是同一个 RandomShuffleState，只有它的
        // generation 变了才需要重装；同一个 generation 重装一份一模一样的东西
        // 语义上没有任何变化，只是白付一次 masking 的代价。
        //
        // V6.1.2（审查澄清）: 这个 memo 不是「永不重装」的保证——第一次
        // `orderFor` 画出新序时 generation 会 +1，于是下一个 timeline 事件仍会
        // 重装一次。那一次是**有界**的（重装本身不画序，所以不会再次失效），
        // 也正是它把原来的无限重建环截断成「一次开关最多多付一轮」。
        // 想彻底消掉这一轮，得让 ExoPlayer 侧的 order 不再是快照（见
        // [RandomShuffleState.copy] 与 cloneAndInsert），改动面比收益大。
        val version = shuffleState.version
        if (version == installedShuffleVersion && installedShuffleLength == length) {
            controller?.shuffleModeEnabled = shuffle
            return true
        }
        var installed = false
        installingShuffleOrder = true
        try {
            runCatching {
                exo.setShuffleOrder(
                    RandomShuffleOrder(
                        size = queue.size,
                        state = shuffleState,
                    )
                )
            }
                .onSuccess {
                    installedShuffleVersion = version
                    installedShuffleLength = length
                    installed = true
                }
                .onFailure {
                    Log.e(TAG, "shuffle order refused: playlist=$length queue=${queue.size}", it)
                }
        } finally {
            installingShuffleOrder = false
        }
        // V5.3.6: expose the shared order to the player's own navigation too.
        // ExoPlayer only consults the installed ShuffleOrder while
        // shuffleModeEnabled is on, so a queue installed before the mode flip
        // used to be walked by its internal order instead of ours.
        controller?.shuffleModeEnabled = shuffle
        return installed
    }

    /** Guards [installShuffleOrder] against re-entry from its own timeline event. */
    private var installingShuffleOrder = false

    /**
     * V6.1: generation of the order currently installed in ExoPlayer, and the
     * playlist length it was installed against.
     *
     * Together they say "the installed order is still this one" - see the guard
     * in [installShuffleOrder]. Reset to -1 whenever the playlist is rebuilt
     * from scratch, because the new playlist has no installed order at all.
     */
    private var installedShuffleVersion = -1
    private var installedShuffleLength = -1

    /**
     * V6.1: the playlist underneath the installed order is gone.
     *
     * Called before every whole-playlist `setMediaItems`/`clearMediaItems`. The
     * old order's indices refer to the previous list, so nothing about it may
     * be reused - not even "the generation did not change" (the queue may keep
     * its length while every slot holds a different track).
     */
    private fun forgetInstalledOrder() {
        installedShuffleVersion = -1
        installedShuffleLength = -1
    }

    /**
     * V5.1-3: throttled persistence of the current track + position.
     *
     * V6.2.0 note: the old `onShuffleOrderRejected` diagnostic hook used to be
     * declared here. It had no subscribers in the app or the tests, so it was
     * removed; [installShuffleOrder] logs both rejection paths instead.
     */
    private fun maybeSaveProgress() {
        val m = queue.getOrNull(currentIndex) ?: return
        val pos = controller?.currentPosition?.coerceAtLeast(0L) ?: return
        if (m.id != lastSavedId) {
            lastSavedId = m.id
            lastSavedPosMs = pos
            onProgress?.invoke(m.id, pos)
        } else if (kotlin.math.abs(pos - lastSavedPosMs) >= 2_000L) {
            lastSavedPosMs = pos
            onProgress?.invoke(m.id, pos)
        }
    }

    private fun startProgressLoop() {
        scope.launch {
            while (true) {
                controller?.let { c ->
                    val pos = c.currentPosition.coerceAtLeast(0)
                    val lyricIdx = _state.value.lyric.indexAt(pos + lyricOffsetMs)
                    _state.value = _state.value.copy(
                        positionMs = pos,
                        durationMs = c.duration.coerceAtLeast(0),
                        isPlaying = c.isPlaying,
                        lyricIndex = lyricIdx,
                    )
                    // V6.1: 这首歌是不是已经悄悄冻住了。冻结不会抛 onPlayerError，
                    // 只能靠「位置不动了」这件事本身发现。
                    //
                    // V6.2: 只在用户「想让它在播」时才观察。以前的守卫只看
                    // isPlaying，于是一首被暂停、但缓冲还没吐完的歌会在 20s 后被
                    // 判成 stuck buffer 并升级 REBUFFER，两个处理器最后都调
                    // `c.play()` —— 把用户刚按下的暂停又推回播放。暂停期间没有
                    // 任何东西需要被救，直接清零让下一次播放从干净状态开始。
                    val action = if (!c.playWhenReady) {
                        watchdog.reset()
                        StallAction.NONE
                    } else {
                        watchdog.onTick(
                            positionMs = pos,
                            playing = c.isPlaying,
                            buffering = c.playbackState == Player.STATE_BUFFERING,
                            nowMs = android.os.SystemClock.elapsedRealtime(),
                        )
                    }
                    when (action) {
                        StallAction.NONE -> Unit
                        StallAction.RETRY -> retryFrozenPlayback()
                        StallAction.REBUFFER -> rebuildFrozenSource()
                    }
                }
                maybeSaveProgress()
                delay(300)
            }
        }
    }

    private val watchdog = StallWatchdog()

    /**
     * V6.1: first response to a frozen position - ask ExoPlayer to re-prepare
     * from where it is. Cheap, keeps the media source, and recovers the common
     * case (a decoder hiccup, a seek that never landed).
     */
    private fun retryFrozenPlayback() {
        val c = controller ?: return
        val pos = c.currentPosition.coerceAtLeast(0)
        Log.w(TAG, "playback frozen at ${pos}ms, retrying")
        runCatching {
            c.prepare()
            c.seekTo(pos)
            c.play()
        }
    }

    /**
     * V6.1: second response - the source itself is dead.
     *
     * For an online track the stream URL has usually expired, so the URL is
     * resolved again and the item is replaced; for a local track the same file
     * is re-opened. Playback resumes at the frozen position so a 30-minute
     * track does not start over.
     */
    private fun rebuildFrozenSource() {
        val c = controller ?: return
        val music = queue.getOrNull(currentIndex) ?: return
        val pos = c.currentPosition.coerceAtLeast(0)
        Log.w(TAG, "playback still frozen at ${pos}ms, rebuilding source for ${music.id}")
        scope.launch {
            val uri: Uri = try {
                if (music.isLocal) {
                    localFileUri(music) ?: return@launch
                } else {
                    val quality = pickQuality(music)
                    Uri.parse(withContext(Dispatchers.IO) { sourceManager.resolveMusicUrl(music, quality).url })
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
                return@launch
            }
            watchdog.reset()
            // V6.1.1: setMediaItem replaces the *whole* playlist with this one
            // item, so the mirror is gone. Leaving the flag set made playAt()
            // keep seeking into a one-item playlist while the app queue still
            // had N entries - every later "next" silently seeked to index 0 of a
            // single-item list. restore() drops it; ensureMirrorPlaylist()
            // rebuilds it the next time shuffle needs the full list.
            mirror.dropToSingleItem()
            c.setMediaItem(buildMediaItem(music, uri))
            c.repeatMode = repeatMode.toPlayerRepeatMode()
            c.prepare()
            c.seekTo(pos)
            c.play()
        }
    }

    /** V5.1-4: reused by the app when replaying persisted playback modes. */
    fun applyPersistedModes(mode: RepeatMode, shuffleOn: Boolean) {
        repeatMode = mode
        shuffle = shuffleOn
        controller?.repeatMode = mode.toPlayerRepeatMode()
        // V5.3.6: install the shared order *before* enabling shuffle mode. A
        // queue restored into playlist mode used to serve ExoPlayer's own
        // order until something else happened to reinstall ours.
        shuffleState.invalidate()
        if (shuffleOn) installShuffleOrder()
        controller?.shuffleModeEnabled = shuffleOn
        _state.value = _state.value.copy(repeatMode = mode, shuffle = shuffleOn)
    }

    /**
     * V5.1-3: silently reload the last-played track at its saved position.
     * Prepares the player but does NOT start playback; no network needed for
     * local tracks (online tracks are resolved lazily on first user play).
     */
    fun prepareRestored(music: com.lxmusic.android.data.model.MusicInfo, posMs: Long) {
        val c = controller ?: return
        queue.clear()
        queue.add(music)
        currentIndex = 0
        mirror.dropToSingleItem()
        shuffleState.invalidate()
        _state.value = _state.value.copy(
            current = music, queue = queue.toList(), queueIndex = 0,
            positionMs = posMs, isBuffering = false,
        )
        val uri = if (music.isLocal) resolvableUri(music) else null
        if (uri == null) {
            lastSavedId = music.id
            lastSavedPosMs = posMs
            onProgress?.invoke(music.id, posMs)
            loadLyric(music)
            onTrackStarted?.invoke(music)
            return
        }
        c.setMediaItem(buildMediaItem(music, uri))
        c.prepare()
        c.seekTo(posMs)
        lastSavedId = music.id
        lastSavedPosMs = posMs
        attachEqualizer()
        onTrackStarted?.invoke(music)
        loadLyric(music)
    }

    /**
     * V5.5-3: restore leaves a single-track queue, which breaks shuffle (there
     * is nothing else to draw). The app supplies a context list to rebuild the
     * full queue around the restored track without disturbing playback.
     */
    fun backfillQueue(list: List<MusicInfo>, currentId: String) {
        if (queue.size > 1 || list.none { it.id == currentId }) return
        queue.clear(); queue.addAll(list)
        currentIndex = list.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        // V5.3.6: a restored single-track queue played as one item, so the
        // player only ever held that item. The mirror is dropped so the next
        // shuffle press re-mirrors the whole context (see
        // [ensureMirrorPlaylist]); playback itself is left untouched, because
        // re-issuing it here would restart the track from zero.
        mirror.dropToSingleItem()
        resetShuffle()
        publishQueue()
    }

    /** V5.1-3: clear the resume bookkeeping (e.g. when restore fails). */
    fun clearSavedProgress() {
        lastSavedId = null
        lastSavedPosMs = -1L
    }

    fun release() {
        // V6.1.1: the progress loop is `while (true)` on a scope this class owns,
        // so releasing the player alone left it ticking forever, reading a null
        // controller and calling maybeSaveProgress() every 300ms for the rest of
        // the process's life. Cancel the scope first, then drop the player.
        scope.cancel()
        equalizer.release()
        controller?.release()
        controller = null
    }

    private companion object {
        const val TAG = "PlayerController"
    }
}
