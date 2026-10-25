package com.lxmusic.android.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * V5.3.6: ExoPlayer's [ShuffleOrder] backed by [RandomShuffleState].
 *
 * Gives the media notification, lockscreen, bluetooth and fluid-cloud next and
 * previous buttons exactly the same playlist as the in-app buttons - one order,
 * not two.
 *
 * What matters here is what is *not* in this class: any decision. The shuffle
 * cycle is one order over the queue and is kept by [RandomShuffleState], so four
 * questions ("where does it start", "where does it end", "what is after this
 * track", "what is before this track") are guaranteed to describe the same
 * playlist. ExoPlayer asks all four, sometimes more than once per decision,
 * and an implementation that answers any of them independently can name a
 * track and then seek to another, report "no next item" while one exists, or
 * hand back a source index that no longer exists after a queue edit.
 *
 * ExoPlayer asserts `getLength() == mediaSourceHolderSnapshots.size()` inside
 * `setShuffleOrder`, and it clones the order on every playlist mutation - both
 * to publish a new playlist and to *repair* a length mismatch. A clone is
 * therefore a candidate, not a commitment: the order it came from may keep
 * serving the session. Each clone takes a snapshot of the order
 * ([RandomShuffleState.copy]) instead of sharing it, so building a candidate
 * never disturbs the live one.
 */
@UnstableApi
class RandomShuffleOrder(
    private val size: Int,
    private val state: RandomShuffleState,
    private val random: Random = Random.Default,
    /**
     * V6.2: 「上一首」的答案由谁给。
     *
     * 随机模式下全序的前一位是本轮还没播过的歌，用户按上一首想回的是刚听过的
     * 那首。所以通知栏/锁屏/蓝牙那条路径必须也能读到播放历史 —— 它们只认识
     * 这个 [ShuffleOrder]，不认识 app 的 [PlayHistory]。传入方负责把历史目标
     * 换成播放器下标；返回 -1 表示「没有可回退的」，media3 会理解为重播当前曲。
     *
     * 为 null 时退化成全序前驱（只有测试与「没有历史概念」的场景会这样）。
     */
    private val previousOf: ((Int) -> Int)? = null,
) : ShuffleOrder {

    override fun getLength(): Int = size

    override fun getNextIndex(index: Int): Int = state.nextIndex(size, index, random)

    override fun getPreviousIndex(index: Int): Int {
        val fromHistory = previousOf?.invoke(index) ?: -1
        if (fromHistory >= 0) return fromHistory
        if (previousOf != null) return -1
        return state.previousIndex(size, index, random)
    }

    override fun getLastIndex(): Int = state.lastIndex(size, random)

    override fun getFirstIndex(): Int = state.firstIndex(size, random)

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder =
        RandomShuffleOrder((size + insertionCount).coerceAtLeast(0), state.copy(), random, previousOf)

    override fun cloneAndRemove(removalIndex: Int, removalCount: Int): ShuffleOrder =
        RandomShuffleOrder((size - removalCount).coerceAtLeast(0), state.copy(), random, previousOf)

    override fun cloneAndClear(): ShuffleOrder = RandomShuffleOrder(0, state.copy(), random, previousOf)
}
