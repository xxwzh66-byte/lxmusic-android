package com.lxmusic.android.player

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V6.2: [queueIndexOf] 是「播放器报的位置 → app 队列下标」的唯一翻译点。
 *
 * 失败即意味着在线队列里点第 N 首会显示成第 1 首、`currentIndex` 被归零，
 * 以及「下一首」永远算 0+1 走不出第二首。
 */
class QueueIndexTest {

    private fun track(id: String) = MusicInfo(
        id = id,
        source = "kw",
        songmid = id,
        name = id,
        singer = "s",
    )

    @Test
    fun aSingleItemPlaylistStillResolvesToTheQueueSlotOfThePlayingTrack() {
        val queue = listOf(track("a"), track("b"), track("c"), track("d"), track("e"))
        // playIndex(3) puts ONE item in the player, so it reports index 0.
        assertEquals(3, queueIndexOf("d", queue, playerIndex = 0))
    }

    @Test
    fun aMirroredPlaylistResolvesThroughTheListPosition() {
        val queue = listOf(track("a"), track("b"), track("c"))
        for (i in queue.indices) {
            assertEquals(i, queueIndexOf(queue[i].id, queue, playerIndex = i))
        }
    }

    @Test
    fun anUnknownIdFallsBackToTheReportedPosition() {
        val queue = listOf(track("a"), track("b"), track("c"))
        // The player still holds an item from a queue that has just been replaced.
        assertEquals(1, queueIndexOf("stale", queue, playerIndex = 1))
    }

    @Test
    fun anUnknownIdAndAnOutOfRangePositionAskTheCallerToIgnoreTheCallback() {
        val queue = listOf(track("a"), track("b"))
        assertEquals(-1, queueIndexOf("stale", queue, playerIndex = 7))
        assertEquals(-1, queueIndexOf(null, queue, playerIndex = -1))
    }

    @Test
    fun aBlankIdIsTreatedAsUnknown() {
        val queue = listOf(track("a"), track("b"))
        assertEquals(1, queueIndexOf("", queue, playerIndex = 1))
    }
}
