package com.lxmusic.android.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ordering rules [QueueMirror] exists to own.
 *
 * These are the invariants eight copy-pasted call sites used to keep by
 * agreement: drop the order before the edit, not after; move the player before
 * rebuilding; never leave `isMirrored` true over a one-item playlist.
 */
class QueueMirrorTest {

    /** Records what was asked of the player, in order. */
    private class FakeTarget(var count: Int = 0) : MirrorTarget {
        val calls = mutableListOf<String>()
        override var repeatMode: Int = Player.REPEAT_MODE_OFF
            set(value) { field = value; calls += "repeat=$value" }
        override var shuffleModeEnabled: Boolean = false
            set(value) { field = value; calls += "shuffle=$value" }

        override val mediaItemCount: Int get() = count

        override fun setMediaItems(items: List<MediaItem>, startIndex: Int) {
            calls += "set(start=$startIndex,n=${items.size})"
            count = items.size
        }

        override fun addMediaItems(index: Int, items: List<MediaItem>) {
            calls += "add(at=$index,n=${items.size})"
            count += items.size
        }

        override fun moveMediaItem(from: Int, to: Int) { calls += "move($from,$to)" }
        override fun removeMediaItem(index: Int) { calls += "remove($index)"; count -= 1 }
        override fun clearMediaItems() { calls += "clear"; count = 0 }
        override fun prepare() { calls += "prepare" }
        override fun play() { calls += "play" }
    }

    /** Records the order lifecycle. */
    private class FakeSink : ShuffleSink {
        val log = mutableListOf<String>()
        override fun install(): Boolean { log += "install"; return true }
        override fun dropInstalled() { log += "drop" }
    }

    private fun track(id: String, local: Boolean = true) = MusicInfo(
        id = id, source = if (local) MusicInfo.SOURCE_LOCAL else "kw",
        songmid = id, name = id, singer = "s",
        localPath = if (local) "/music/$id.mp3" else null,
    )

    private fun mirror(
        queue: List<MusicInfo>,
        slot: Int = 0,
        playing: Boolean = true,
        onMutated: () -> Unit = {},
    ): Triple<QueueMirror, FakeTarget, FakeSink> {
        val target = FakeTarget()
        val sink = FakeSink()
        var current = slot
        val m = QueueMirror(
            sink = sink,
            queue = { queue },
            // An online track has no synchronous item - the same reason
            // resolvableUri() returns null for it in PlayerController.
            itemOf = { t -> if (t.isLocal) MediaItem.Builder().setMediaId(t.id).build() else null },
            currentSlot = { current },
            selectSlot = { current = it },
            repeatFlag = { Player.REPEAT_MODE_ALL },
            shuffleFlag = { true },
            isPlaying = { playing },
            onQueueMutated = onMutated,
        )
        m.target = target
        return Triple(m, target, sink)
    }

    // ---- replace ----

    @Test
    fun replaceBuildsTheWholePlaylistAndStartsIt() {
        val list = listOf(track("a"), track("b"), track("c"))
        val (m, t, sink) = mirror(list)
        assertTrue(m.replace(list, 1))

        assertEquals("set(start=1,n=3)", t.calls.first())
        assertTrue("a replaced playlist must be prepared", t.calls.contains("prepare"))
        assertTrue("a replaced playlist must start", t.calls.contains("play"))
        assertEquals("the stale order must go before the edit", "drop", sink.log.first())
        assertTrue(m.isMirrored)
    }

    @Test
    fun replaceRefusesAQueueWithAnUnresolvableTrack() {
        val list = listOf(track("a"), track("online", local = false))
        val (m, t, _) = mirror(list)
        assertFalse("an online track cannot be mirrored synchronously", m.replace(list, 0))
        assertFalse(m.isMirrored)
        assertTrue("nothing may be sent to the player", t.calls.isEmpty())
    }

    @Test
    fun replaceClampsAStartIndexPastTheEnd() {
        val list = listOf(track("a"), track("b"))
        val (m, t, _) = mirror(list)
        assertTrue(m.replace(list, 9))
        assertEquals("set(start=1,n=2)", t.calls.first())
    }

    @Test
    fun replaceRefusesAnEmptyQueue() {
        val (m, _, _) = mirror(emptyList())
        assertFalse(m.replace(emptyList(), 0))
        assertFalse(m.isMirrored)
    }

    // ---- ensure ----

    @Test
    fun ensureIsANoOpWhenTheMirrorAlreadyMatchesTheQueue() {
        val list = listOf(track("a"), track("b"))
        val (m, t, sink) = mirror(list)
        m.replace(list, 0)
        val before = t.calls.size
        sink.log.clear()

        assertTrue(m.ensure())
        assertEquals("an already-correct mirror must not be rebuilt", before, t.calls.size)
        assertTrue(sink.log.isEmpty())
    }

    @Test
    fun ensureRebuildsWhenThePlayerCountDriftedFromTheQueue() {
        val list = listOf(track("a"), track("b"), track("c"))
        val (m, t, sink) = mirror(list, slot = 2)
        m.replace(list, 2)
        // The state a setMediaItem leaves behind: mirror says yes, one item.
        t.count = 1
        t.calls.clear()

        assertTrue(m.ensure())
        assertTrue(
            "the drift must be rebuilt, not patched",
            t.calls.contains("set(start=2,n=3)"),
        )
        assertEquals(3, t.mediaItemCount)
    }

    @Test
    fun ensureRefusesASingleTrackQueue() {
        val (m, _, _) = mirror(listOf(track("a")))
        assertFalse("shuffle has nowhere to go with one track", m.ensure())
    }

    @Test
    fun ensureRefusesAQueueItCannotResolve() {
        val list = listOf(track("a"), track("b", local = false))
        val (m, _, _) = mirror(list)
        assertFalse(m.ensure())
        assertFalse(m.isMirrored)
    }

    // ---- insert ----

    @Test
    fun insertPatchesThePlaylistInPlace() {
        val list = listOf(track("a"), track("b"), track("c"))
        val (m, t, sink) = mirror(list)
        m.replace(list, 0)
        sink.log.clear()
        t.calls.clear()

        assertTrue(m.insert(listOf(track("x")), 2))
        assertTrue("a resolvable insert patches in place", t.calls.contains("add(at=2,n=1)"))
        assertFalse(
            "patching must not throw the whole playlist away",
            t.calls.any { it.startsWith("set(") },
        )
        assertEquals("drop", sink.log.first())
    }

    @Test
    fun insertRebuildsEverythingForAnUnresolvableTrack() {
        val list = listOf(track("a"), track("b"))
        val (m, t, _) = mirror(list)
        m.replace(list, 0)
        t.calls.clear()

        assertTrue(m.insert(listOf(track("x", local = false)), 1))
        assertTrue(
            "an online insert must rebuild, not patch",
            t.calls.any { it.startsWith("set(") },
        )
        assertFalse("nothing may be patched in", t.calls.any { it.startsWith("add(") })
    }

    @Test
    fun insertDoesNothingWhenTheQueueWasNeverMirrored() {
        val (m, t, _) = mirror(listOf(track("a"), track("b")))
        assertFalse(m.insert(listOf(track("x")), 0))
        assertTrue(t.calls.isEmpty())
    }

    // ---- move ----

    @Test
    fun moveReordersThePlayerBeforeTheOrderIsRebuilt() {
        val list = listOf(track("a"), track("b"), track("c"))
        val (m, t, sink) = mirror(list)
        m.replace(list, 0)
        sink.log.clear()
        t.calls.clear()

        assertTrue(m.move(0, 2))
        assertEquals("move(0,2)", t.calls.first())
        assertEquals(
            "the order must be dropped only after the list moved",
            "drop",
            sink.log.first(),
        )
        assertTrue(
            "and only after the player reported the move",
            t.calls.indexOfFirst { it.startsWith("move(") } <
                t.calls.indexOfFirst { it.startsWith("repeat=") },
        )
    }

    // ---- remove / clear / drop ----

    @Test
    fun removeDropsTheOrderAfterTheEdit() {
        val list = listOf(track("a"), track("b"))
        val (m, t, sink) = mirror(list)
        m.replace(list, 0)
        sink.log.clear()
        t.calls.clear()

        assertTrue(m.remove(1))
        assertEquals("remove(1)", t.calls.first())
        assertEquals("drop", sink.log.first())
    }

    @Test
    fun clearForgetsTheMirror() {
        val list = listOf(track("a"), track("b"))
        val (m, _, sink) = mirror(list)
        m.replace(list, 0)

        m.clear()
        assertFalse("a cleared playlist is not a mirror", m.isMirrored)
        assertEquals("drop", sink.log.last())
    }

    @Test
    fun dropToSingleItemForgetsTheMirrorToo() {
        val list = listOf(track("a"), track("b"))
        val (m, _, sink) = mirror(list)
        m.replace(list, 0)
        sink.log.clear()

        m.dropToSingleItem()
        assertFalse(m.isMirrored)
        assertEquals(listOf("drop"), sink.log)
    }

    @Test
    fun everyEditResetsBothModes() {
        val list = listOf(track("a"), track("b"), track("c"))
        val (m, t, _) = mirror(list)
        m.replace(list, 0)
        t.calls.clear()

        m.move(0, 1)
        assertTrue("a rebuild clears repeat mode", t.calls.contains("repeat=${Player.REPEAT_MODE_ALL}"))
        assertTrue("a rebuild clears shuffle mode", t.calls.contains("shuffle=true"))
    }

    @Test
    fun everyMutationAsksForAFreshOrder() {
        val list = listOf(track("a"), track("b"), track("c"))
        var mutations = 0
        val (m, _, _) = mirror(list, onMutated = { mutations++ })
        m.replace(list, 0)
        val afterReplace = mutations

        m.move(0, 2)
        m.remove(0)
        m.insert(listOf(track("z")), 0)
        assertEquals("every list change needs a new order", afterReplace + 3, mutations)
    }
}
