package com.lxmusic.android.player

/**
 * V6.1: 「上一首」到底回哪儿——播放历史栈。
 *
 * 随机模式下没有「上一首」这个概念：全序里当前曲的前一位是这一轮还没播过的
 * 歌。用户在随机模式里按上一首，想回去的是**刚才听过的那首**。所以 app 的
 * 上一首不走洗牌序，走这个历史栈。
 *
 * 语义（与主流播放器一致）：
 * - 每次真正换到一首新歌，把**离开的那首**压栈；
 * - 按上一首时弹栈，目标是最后压进去的那首；
 * - 栈空时不换歌，由调用方把当前曲从头再放一次——这正是用户在第一首按
 *   上一首时期待的行为。
 *
 * 为什么不用「历史 + 一个正在回退的布尔标记」：那个标记必须在每一个可能的
 * 换歌路径上复位（手动点歌、通知栏、播放键、出错、队列清空），漏一个就会
 * 让回退之后的第一次前进被误判成回退，把栈悄悄吃掉。这里改成**在弹栈后把
 * 目标记成 [expected]**，换歌回调只要看到「新下标 == expected」就认定这次
 * 是回退、不入栈，然后清掉 expected——不需要在别处复位，因为任何别的换歌
 * 都和 expected 对不上，走的是正常入栈分支，而 expected 也就在那里被清掉。
 *
 * 线程：换歌回调与按键都在主线程，但这里仍然加锁，理由和
 * [RandomShuffleState] 一样——一旦有一处从 IO 线程碰它，栈的 head 指针就
 * 可能读到写了一半的状态。
 */
class PlayHistory(private val capacity: Int = 200) {

    private val stack = ArrayDeque<String>()

    /**
     * 回退目标 id，可以有多笔同时在途。
     *
     * V6.2: 原来这里是单个 `String?`。`prev()` 的落地是异步的（`playAt` 会走
     * `scope.launch { playIndex(i) }`），用户连按两次上一首时第二笔会把第一笔
     * 覆盖掉，那一次回退就被当成「正常换歌」把 id 压回栈里，之后的上一首会
     * 往前跳。改成待认领集合：每一笔回退各占一项，先到的那次换歌认领自己那
     * 一项即可。
     */
    private val expected = LinkedHashSet<String>()

    /** Track ids, most recent last. 供测试与调试观察。 */
    val entries: List<String>
        @Synchronized get() = stack.toList()

    @get:Synchronized
    val size: Int get() = stack.size

    /**
     * 记下「离开 [id] 去了别处」。
     *
     * 同 id 连压没有意义（重复点同一首），栈顶相同就跳过。
     */
    @Synchronized
    fun push(id: String) {
        if (stack.lastOrNull() == id) return
        stack.addLast(id)
        while (stack.size > capacity) stack.removeFirst()
    }

    /**
     * 确定要回到栈顶那首了：弹栈并把它记成待认领的回退目标。
     *
     * 返回 null 表示栈空，调用方应把当前曲从头播放。
     */
    @Synchronized
    fun confirmBack(): String? {
        val target = stack.removeLastOrNull() ?: return null
        expected.add(target)
        return target
    }

    /**
     * 回退目标（栈顶），**只看不动**。
     *
     * 给通知栏/锁屏/蓝牙那条路径用：media3 会就同一个位置反复问
     * `getPreviousIndex`，真正的前进可能根本不发生（用户松手、被后续 seek
     * 覆盖），所以那里不能弹栈。等换歌回调真的落到这首时，由
     * [onTrackChanged] 的「落回栈顶」分支把这一项弹掉。
     */
    val backTargetId: String?
        @Synchronized get() = stack.lastOrNull()

    /**
     * 换歌回调：报告「现在到了 [id]」。
     *
     * @param leftId 刚刚离开的那首（第一首时为 null）。
     * @return true 表示这次换歌是回退的结果，调用方**不要**把它当新历史。
     */
    @Synchronized
    fun onTrackChanged(id: String, leftId: String?): Boolean {
        if (expected.remove(id)) {
            // 回退落地：这一首已经在弹栈时从历史里取走了，不能再压回去，
            // 否则上一首会变成原地打转。
            return true
        }
        if (stack.lastOrNull() == id) {
            // V6.2: 落到栈顶这一首 = 回退，即使这次回退不是 app 按钮发起的。
            // 通知栏/蓝牙的「上一首」走的是 media3 的 ShuffleOrder 查询，那条
            // 路径只能看不能弹栈（查询可能被丢弃），所以认领动作放在这里：
            // 目标曲真的开始播了，才把它从历史里取走，否则下一次上一首会
            // 在「当前曲 ↔ 上一首」之间来回跳。
            stack.removeLast()
            return true
        }
        leftId?.let { push(it) }
        return false
    }

    /**
     * 队列被整体替换（新专辑、清空队列、恢复上次播放）时清空——旧下标指向
     * 的队列已经不存在了，留着只会把上一首导到无关的歌上。
     */
    @Synchronized
    fun clear() {
        stack.clear()
        expected.clear()
    }

    @Synchronized
    fun copy(): PlayHistory = PlayHistory(capacity).also {
        it.stack.addAll(stack)
        it.expected.addAll(expected)
    }
}
