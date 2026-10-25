package com.lxmusic.android.player

import com.lxmusic.android.data.model.MusicInfo

/**
 * V6.2: 把播放器报告的位置翻译成 app 队列下标。
 *
 * 为什么不能直接用 `currentMediaItemIndex`：单曲模式（[PlayerController.playIndex]）
 * 只往播放器里放**一首**，所以换歌回调报告的 index 永远是 0，而 app 正在播的
 * 可能是 queue[3]。用 index 直接查队列会把 queue[0] 当成「当前曲」发布出去，
 * 并把 `currentIndex` 归零——表现就是在线队列点第 4 首却显示第 1 首，而且
 * 「下一首」永远算 0+1，走不出第二首。
 *
 * 每个 [MediaItem][androidx.media3.common.MediaItem] 都带着队列项的 id，所以
 * 以 id 为准；只有 id 不在队列里（例如播放器里还留着上一队列的项）才回退到
 * 位置。回退也失败时返回 -1，调用方应当忽略这次回调而不是猜一个下标。
 */
internal fun queueIndexOf(
    mediaId: String?,
    queue: List<MusicInfo>,
    playerIndex: Int,
): Int {
    if (mediaId != null) {
        val byId = queue.indexOfFirst { it.id == mediaId }
        if (byId >= 0) return byId
    }
    return if (playerIndex in queue.indices) playerIndex else -1
}
