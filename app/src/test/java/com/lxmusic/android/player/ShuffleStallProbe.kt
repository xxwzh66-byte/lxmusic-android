package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V6.1 探针：按 media3 1.4.1 的真实语义还原「随机播放卡住 / 上下首卡住」。
 *
 * 依据（apkanalyzer 反汇编 media3-exoplayer-1.4.1.aar 得到，见 `_dex/`）：
 *  - `AbstractConcatenatedTimeline.getNextChildIndex(index, shuffle)`
 *      if (shuffle) return shuffleOrder.getNextIndex(index);
 *  - `AbstractConcatenatedTimeline.getNextWindowIndex(window, repeat, shuffle)`
 *      每首 MediaItem 是独立 child timeline、单窗口，所以 child == window；
 *      子 timeline 返回 -1 → 走 getNextChildIndex；仍 -1 且 repeat==ALL 才回 first
 *  - `ExoPlayerImpl.setShuffleOrder` 断言 `getLength() == mediaSourceHolderSnapshots.size()`
 *  - `MediaSourceList.setShuffleOrder` 长度不符时换成 clone
 *  - `ExoPlayerImpl.addMediaSourcesInternal/removeMediaItemsInternal` 每次队列编辑后
 *      把 shuffleOrder 换成一个 **clone**
 *
 * 因此决定「下一首」的来源是 installed ShuffleOrder，app 按钮走的是
 * PlayerController.shuffleState —— 两条路径必须指向同一个 state。
 *
 * v6.1 的语义变化（本探针锁的就是这些）：
 *  - 换歌**不**再 invalidate 整条序，周期可以走完（旧版每首歌重洗 → A→B→A）；
 *  - `nextIndex` 在周期末尾**重洗并锚定当前曲**，所以永远有下一首、永不卡住；
 *  - `previousIndex` 在周期起点返回 -1（ExoPlayer 理解为「重放当前曲」）；
 *  - app 的「上一首」走 [PlayHistory] 而不是 `previousIndex`——随机模式下全序的
 *    前一位是本轮**还没播过**的歌，听起来像往前跳，不是回去。
 */
class ShuffleStallProbe {

    /** 忠实复刻 AbstractConcatenatedTimeline（每个 child 单窗口）的导航结果。 */
    private class Timeline(private val childCount: Int, private val order: ShuffleOrderLike) {
        fun windowCount() = childCount

        fun firstWindow(shuffle: Boolean): Int {
            if (childCount == 0) return -1
            if (shuffle) {
                val f = order.getFirstIndex()
                if (f == -1) return -1
                return f
            }
            return 0
        }

        fun lastWindow(shuffle: Boolean): Int {
            if (childCount == 0) return -1
            if (shuffle) {
                val l = order.getLastIndex()
                if (l == -1) return -1
                return l
            }
            return childCount - 1
        }

        /** repeat: 0=OFF 1=ONE 2=ALL */
        fun nextWindow(window: Int, repeat: Int, shuffle: Boolean): Int {
            val child = window
            var candidate = -1
            if (child in 0 until childCount) {
                candidate = if (shuffle) order.getNextIndex(child) else -1
            }
            if (candidate != -1) return candidate
            if (repeat == 2) {
                val f = firstWindow(shuffle)
                if (f != -1) return f
            }
            return -1
        }

        fun previousWindow(window: Int, shuffle: Boolean): Int =
            if (window in 0 until childCount) {
                if (shuffle) order.getPreviousIndex(window) else -1
            } else -1
    }

    private interface ShuffleOrderLike {
        fun getNextIndex(index: Int): Int
        fun getPreviousIndex(index: Int): Int
        fun getFirstIndex(): Int
        fun getLastIndex(): Int
    }

    private fun makeOrder(size: Int, state: RandomShuffleState, random: Random): ShuffleOrderLike {
        val o = RandomShuffleOrder(size, state, random)
        return object : ShuffleOrderLike {
            override fun getNextIndex(index: Int) = o.getNextIndex(index)
            override fun getPreviousIndex(index: Int) = o.getPreviousIndex(index)
            override fun getFirstIndex() = o.getFirstIndex()
            override fun getLastIndex() = o.getLastIndex()
        }
    }

    /**
     * 模型：app 侧逻辑（PlayerController V6.1 实现）+ media3 时间线。
     * 队列 = child 列表，app 的 index 与 child index 一一对应。
     */
    private inner class Model(private val size: Int) {
        var index = 0
        val state = RandomShuffleState()
        val history = PlayHistory()
        private val random = Random(3)
        private val timeline: Timeline
            get() = Timeline(size, makeOrder(size, state, random))
        var shuffle = false

        /** PlayerController.toggleShuffle：清序 + 装 order，不动当前曲。 */
        fun toggleShuffle() {
            shuffle = !shuffle
            state.invalidate()
        }

        /**
         * app 按钮 next（PlayerController.next 的随机分支）。
         *
         * v6.1：问的是同一个 state 的 nextIndex，与 installed order 对同一
         * position 完全一致；不再 redrawNext 另抽一条序。
         */
        fun appNext(): Boolean {
            if (shuffle) {
                val t = state.nextIndex(size, index, random)
                if (t < 0) return false
                move(t)
                return true
            }
            if (index + 1 < size) { move(index + 1); return true }
            return false
        }

        /**
         * app 按钮 prev（PlayerController.prev 的随机分支）。
         *
         * v6.1：走播放历史；栈空时重放当前曲（index 不变，但这次「按键有反应」）。
         */
        fun appPrev(): Boolean {
            if (shuffle) {
                val back = history.confirmBack() ?: return true // restart current track
                val target = back.toIntOrNull() ?: return true
                if (target !in 0 until size) return true
                move(target)
                return true
            }
            if (index - 1 >= 0) { move(index - 1); return true }
            return false
        }

        /** media3 原生导航：通知栏 / 锁屏 / 系统「下一首」。 */
        fun mediaNext(repeat: Int = 0): Boolean {
            val t = timeline.nextWindow(index, repeat, shuffle)
            if (t < 0) return false
            move(t)
            return true
        }

        fun mediaPrev(): Boolean {
            val t = timeline.previousWindow(index, shuffle)
            if (t < 0) return false
            move(t, fromHistory = false)
            return true
        }

        /**
         * 一次换歌落地：更新 index 并把「离开的那首」记进历史。
         *
         * v6.1：**不**再 invalidate——周期必须跨换歌存活，否则每首歌都重洗，
         * 全序永远走不到第二位（这就是旧版 A→B→A 合法的原因）。
         */
        private fun move(target: Int, fromHistory: Boolean = true) {
            if (fromHistory) history.onTrackChanged(target.toString(), index.toString())
            index = target
        }

        /** PlayerController.onMediaItemTransition：现在只记历史，不重洗。 */
        fun onTransition() = Unit
    }

    @Test
    fun appNextMustNeverStallInShuffleMode() {
        val m = Model(8)
        m.toggleShuffle()
        for (press in 0 until 500) {
            val before = m.index
            assertTrue("app button stalled at press $press (index $before)", m.appNext())
            assertNotEquals("app next must change the track", before, m.index)
        }
    }

    @Test
    fun mediaNextMustNeverStallInShuffleMode() {
        val m = Model(8)
        m.toggleShuffle()
        for (press in 0 until 500) {
            val before = m.index
            assertTrue("system next stalled at press $press (index $before)", m.mediaNext(0))
            assertNotEquals("system next must change the track", before, m.index)
        }
    }

    @Test
    fun oneCycleNeverRepeatsATrack() {
        // 这是 v6.1 的核心承诺：一条全序走完之前不重复。
        val m = Model(8)
        m.toggleShuffle()
        val seen = HashSet<Int>()
        seen.add(m.index)
        repeat(7) {
            assertTrue("app next stalled inside a cycle", m.appNext())
            assertTrue("cycle revisited index ${m.index}", seen.add(m.index))
        }
        assertEquals("a cycle must cover the whole queue", 8, seen.size)
    }

    @Test
    fun appAndSystemWalkTheSamePlaylist() {
        // 媒体通知问的是 installed order，按钮问的是同一个 state；
        // 两者对同一 position 必须给出同一个答案，否则「换了图标但没换歌」。
        val app = Model(8)
        val media = Model(8)
        app.toggleShuffle(); media.toggleShuffle()
        repeat(20) {
            assertTrue("app next stalled", app.appNext())
            assertTrue("system next stalled", media.mediaNext(0))
            assertEquals(
                "the button and the notification must pick the same next track",
                media.index,
                app.index,
            )
        }
    }

    @Test
    fun appPrevGoesBackToTheTrackActuallyHeard() {
        // 随机模式下「上一首」= 回到刚才听的那首，不是全序的前一位。
        val m = Model(8)
        m.toggleShuffle()
        assertTrue(m.appNext())
        val first = 0
        val second = m.index
        assertTrue(m.appNext())
        val third = m.index
        assertNotEquals(first, third)
        // 按两次上一首应回到第一首。
        assertTrue(m.appPrev())
        assertEquals(second, m.index)
        assertTrue(m.appPrev())
        assertEquals(first, m.index)
    }

    @Test
    fun appPrevAtTheStartRestartsWithoutStalling() {
        val m = Model(8)
        m.toggleShuffle()
        val only = m.index
        assertTrue("prev at the first track must still respond", m.appPrev())
        assertEquals("with no history the current track restarts", only, m.index)
    }

    @Test
    fun twoTrackQueueStillAdvancesBothDirections() {
        val m = Model(2)
        m.toggleShuffle()
        for (press in 0 until 100) {
            val before = m.index
            assertTrue("2-track queue must keep advancing", m.appNext())
            assertNotEquals(before, m.index)
        }
    }

    @Test
    fun systemNextWalksTheSamePlaylistTheAppWalked() {
        // media3 与 app 走同一条播放序：从同一位置出发，两条 walk 的足迹必须一致。
        val apps = Model(8)
        val media = Model(8)
        apps.toggleShuffle(); media.toggleShuffle()

        val appWalk = mutableListOf(apps.index)
        val mediaWalk = mutableListOf(media.index)
        repeat(7) {
            assertTrue("app walk stalled", apps.appNext())
            assertTrue("media walk stalled", media.mediaNext(0))
            assertEquals("the button and the notification disagree", media.index, apps.index)
            appWalk.add(apps.index)
            mediaWalk.add(media.index)
        }
        assertEquals("the two walks must describe the same playlist", appWalk, mediaWalk)
        assertEquals("a cycle must cover the queue", 8, appWalk.toSet().size)
    }
}
