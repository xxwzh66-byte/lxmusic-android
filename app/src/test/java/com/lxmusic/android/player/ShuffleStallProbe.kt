package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V1.0.1 探针：按 media3 1.4.1 的真实语义还原「随机播放」的端到端行为。
 *
 * 依据（反汇编 media3-exoplayer-1.4.1.aar 得到，见 `_dex/`）：
 *  - `AbstractConcatenatedTimeline.getNextChildIndex(index, shuffle)`
 *      if (shuffle) return shuffleOrder.getNextIndex(index);
 *  - `AbstractConcatenatedTimeline.getNextWindowIndex(window, repeat, shuffle)`
 *      每首 MediaItem 是独立 child timeline、单窗口，所以 child == window；
 *      子 timeline 返回 -1 → 走 getNextChildIndex；仍 -1 且 repeat==ALL 才回 first
 *  - `BasePlayer.seekToPrevious()`：`hasPreviousMediaItem()` 为 false →
 *      `seekToCurrentItem(0L)`，也就是**从头重播当前曲**
 *  - `ExoPlayerImpl.setShuffleOrder` 断言 `getLength() == mediaSourceHolderSnapshots.size()`
 *  - `MediaSourceList.setShuffleOrder` 长度不符时换成 clone
 *
 * 因此决定「下一首 / 上一首」的来源是 installed ShuffleOrder，app 按钮走的是
 * PlayerController.shuffleState —— 两条路径必须共享同一个 state，否则通知栏和
 * 按钮各抽各的。
 *
 * V1.0.1 的语义（本探针锁的就是这些）：
 *  - 每次决策抽一次，同一次决策内 media3 问几遍都拿同一个答案；
 *  - 换歌（[RandomShuffleState.onTrackStarted]）之后下一按是新的随机，
 *    所以播放顺序**不是**事先固定的；
 *  - 两个方向都永不返回 -1（队列 ≥ 2 首），所以按键一定有反应，也不会
 *    让 media3 走「重播当前曲」那条分支。
 */
class ShuffleStallProbe {

    /** 忠实复刻 AbstractConcatenatedTimeline（每个 child 单窗口）的导航结果。 */
    private class Timeline(private val childCount: Int, private val order: ShuffleOrderLike) {
        fun windowCount() = childCount

        fun firstWindow(shuffle: Boolean): Int {
            if (childCount == 0) return -1
            if (shuffle) return order.getFirstIndex()
            return 0
        }

        fun lastWindow(shuffle: Boolean): Int {
            if (childCount == 0) return -1
            if (shuffle) return order.getLastIndex()
            return childCount - 1
        }

        /** repeat: 0=OFF 1=ONE 2=ALL */
        fun nextWindow(window: Int, repeat: Int, shuffle: Boolean): Int {
            var candidate = -1
            if (window in 0 until childCount) {
                candidate = if (shuffle) order.getNextIndex(window) else -1
            }
            if (candidate != -1) return candidate
            if (repeat == 2) {
                val f = firstWindow(shuffle)
                if (f != -1) return f
            }
            return -1
        }

        /**
         * `BasePlayer.seekToPrevious()` 的真实语义：
         * `hasPreviousMediaItem()` 为 false 就回头播当前曲，不是失败。
         */
        fun previousWindow(window: Int, shuffle: Boolean): Int {
            if (window !in 0 until childCount) return -1
            val candidate = if (shuffle) order.getPreviousIndex(window) else -1
            // media3 在这里会读 -1 并改成 seekToCurrentItem(0) —— 从头重播。
            // 探针保留 -1 让「按钮无反应」可被断言发现。
            return candidate
        }
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
     * 模型：app 侧逻辑（PlayerController V1.0.1 实现）+ media3 时间线。
     * 队列 = child 列表，app 的 index 与 child index 一一对应。
     */
    private inner class Model(private val size: Int) {
        var index = 0
        val state = RandomShuffleState()
        private val random = Random(3)
        private val timeline: Timeline
            get() = Timeline(size, makeOrder(size, state, random))
        var shuffle = false

        /** PlayerController.toggleShuffle：清缓存，不动当前曲。 */
        fun toggleShuffle() {
            shuffle = !shuffle
            state.invalidate()
        }

        /** app 按钮 next（PlayerController.next 的随机分支）。 */
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

        /** app 按钮 prev（PlayerController.prev 的随机分支，V1.0.1 同样是抽）。 */
        fun appPrev(): Boolean {
            if (shuffle) {
                val t = state.previousIndex(size, index, random)
                if (t < 0) return false
                move(t)
                return true
            }
            if (index - 1 >= 0) { move(index - 1); return true }
            return false
        }

        /** media3 原生导航：通知栏 / 锁屏 / 蓝牙 / 系统媒体键。 */
        fun mediaNext(repeat: Int = 0): Boolean {
            val t = timeline.nextWindow(index, repeat, shuffle)
            if (t < 0) return false
            move(t)
            return true
        }

        fun mediaPrev(): Boolean {
            val t = timeline.previousWindow(index, shuffle)
            if (t < 0) return false
            move(t)
            return true
        }

        /**
         * 一次换歌落地：更新 index 并释放抽样缓存。
         *
         * 等同于 PlayerController 的两条换歌路径（onMediaItemTransition 与
         * playIndex）都在调 [RandomShuffleState.onTrackStarted]。**不**
         * invalidate：缓存释放不等于「队列变了」，后者才需要重装 order。
         */
        private fun move(target: Int) {
            index = target
            if (shuffle) state.onTrackStarted(size, target)
        }

        /** 测试用：直接把播放头放回某一首，模拟「又播到了这一首」。 */
        fun seekTo(target: Int) {
            index = target
            state.onTrackStarted(size, target)
        }
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
    fun appPrevMustNeverStallInShuffleMode() {
        // The user's report: "上一首不随机 / 按键没反应". Both directions must
        // answer with a real, different index from the very first press.
        val m = Model(8)
        m.toggleShuffle()
        for (press in 0 until 500) {
            val before = m.index
            assertTrue("app previous stalled at press $press (index $before)", m.appPrev())
            assertNotEquals("app previous must change the track", before, m.index)
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
    fun mediaPrevMustNeverStallInShuffleMode() {
        // This is the headset bug: an empty history used to answer -1 here, and
        // media3 read -1 as "restart this track".
        val m = Model(8)
        m.toggleShuffle()
        for (press in 0 until 500) {
            val before = m.index
            assertTrue("system previous stalled at press $press (index $before)", m.mediaPrev())
            assertNotEquals("system previous must change the track", before, m.index)
        }
    }

    @Test
    fun thePlaybackOrderIsNotFixedUpFront() {
        // The user's report: "这一次的播放顺序是固定的，不是随机的".
        // A fixed order would make the walk repeat with period `size`.
        val m = Model(8)
        m.toggleShuffle()
        val walk = mutableListOf(m.index)
        repeat(40) {
            assertTrue("app next stalled", m.appNext())
            walk.add(m.index)
        }
        assertTrue("a fixed order repeats every 8 steps: $walk", walk.take(8) != walk.drop(8).take(8))
        assertTrue("the walk never left the first track: $walk", walk.toSet().size > 1)
    }

    @Test
    fun appAndSystemPickTheSameNextTrack() {
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
    fun appAndSystemPickTheSamePreviousTrack() {
        val app = Model(8)
        val media = Model(8)
        app.toggleShuffle(); media.toggleShuffle()
        repeat(20) {
            assertTrue("app previous stalled", app.appPrev())
            assertTrue("system previous stalled", media.mediaPrev())
            assertEquals(
                "the button and the notification must pick the same previous track",
                media.index,
                app.index,
            )
        }
    }

    @Test
    fun twoTrackQueueStillAdvancesBothDirections() {
        val m = Model(2)
        m.toggleShuffle()
        for (press in 0 until 100) {
            val before = m.index
            assertTrue("2-track queue must keep advancing forward", m.appNext())
            assertNotEquals(before, m.index)
        }
        for (press in 0 until 100) {
            val before = m.index
            assertTrue("2-track queue must keep advancing backward", m.appPrev())
            assertNotEquals(before, m.index)
        }
    }

    @Test
    fun everyDrawFromTheSameTrackCanReachEveryOtherTrack() {
        // End-to-end version of "the order is not fixed": pressing next from the
        // same track across sessions must eventually reach all N-1 alternatives.
        val m = Model(6)
        m.toggleShuffle()
        val reached = HashSet<Int>()
        repeat(300) {
            m.seekTo(0)
            assertTrue("app next stalled", m.appNext())
            reached.add(m.index)
        }
        assertEquals("every other track must be reachable", 5, reached.size)
    }
}
