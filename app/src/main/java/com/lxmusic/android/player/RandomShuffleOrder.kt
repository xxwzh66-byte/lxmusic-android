package com.lxmusic.android.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * V1.0.1: ExoPlayer's [ShuffleOrder] backed by [RandomShuffleState].
 *
 * 存在的唯一理由：让通知栏、锁屏、蓝牙、车机的上一首/下一首和 app 里的按钮
 * 走**同一个随机源**。media3 只认识这个接口，不认识 app 的按钮，所以 app 的
 * 每一次决策都必须以「装进 ExoPlayer 的那个 order 会给出同样的答案」为准。
 * 两条路径各抽各的，就是 v5.3.2「随机播放卡住」的全部原因。
 *
 * 这个类里没有任何决策，四个问题全部转发给 [RandomShuffleState]。它保留的原因
 * 只是 media3 的接口形状：
 *
 * - `getLength()` 必须等于 `MediaSourceHolderSnapshot` 的数量，否则
 *   `setShuffleOrder` 会断言失败；
 * - media3 在每次 playlist 变更时克隆 order，既用于发布新 playlist，也用于
 *   **修复**长度不匹配。所以克隆体是候选者而不是承诺，被克隆的那一份可能仍在
 *   服务会话。每个克隆体拿 [RandomShuffleState.copy] 的快照，构造候选不会扰动
 *   在用的那一份。
 *
 * ## V1.0.1 删掉了 previousOf 钩子
 *
 * 一直到 V1.0.0，这里有一个 `previousOf: ((Int) -> Int)?` 参数，由
 * [PlayerController] 传入「播放历史栈顶那条」。它带来两个问题：
 *
 * 1. 历史为空时返回 -1，而 media3 把 -1 读成「没有上一首」，于是
 *    `BasePlayer.seekToPrevious()` 落到 `seekToCurrentItem(0L)`——**把当前这首
 *    从头重播**。用户报的「耳机切歌第一次会重新播放这首歌」就是这个。
 * 2. 它把「上一首」绑到历史栈上，而历史栈只在 app 内按键和 media item 切换时
 *    维护，通知栏按出来的回退是另一条记账路径，于是两个方向的答案会分叉。
 *
 * 用户选定的语义是「上一首也重新随机」，所以上一首的答案现在由
 * [RandomShuffleState.previousIndex] 直接抽出来，永远是一个合法下标，-1 只出现
 * 在单曲队列这种抽不出来的时候。钩子失去意义，删掉。
 */
@UnstableApi
class RandomShuffleOrder(
    private val size: Int,
    private val state: RandomShuffleState,
    private val random: Random = Random.Default,
) : ShuffleOrder {

    override fun getLength(): Int = size

    override fun getNextIndex(index: Int): Int = state.nextIndex(size, index, random)

    override fun getPreviousIndex(index: Int): Int = state.previousIndex(size, index, random)

    override fun getLastIndex(): Int = state.lastIndex(size)

    override fun getFirstIndex(): Int = state.firstIndex(size)

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder =
        RandomShuffleOrder((size + insertionCount).coerceAtLeast(0), state.copy(), random)

    override fun cloneAndRemove(removalIndex: Int, removalCount: Int): ShuffleOrder =
        RandomShuffleOrder((size - removalCount).coerceAtLeast(0), state.copy(), random)

    override fun cloneAndClear(): ShuffleOrder = RandomShuffleOrder(0, state.copy(), random)
}
