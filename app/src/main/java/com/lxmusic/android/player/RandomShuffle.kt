package com.lxmusic.android.player

import kotlin.random.Random

/**
 * V6.1: 随机播放的「一条全序」。
 *
 * 历史教训（v5.3.2 ~ v6.0）：这里的实现曾经是「每次按键独立均匀抽一首」，
 * 于是 20 首的队列连播 10 首以内听到重复的概率约 89%。用户点随机图的是
 * 「这一轮把没听过的都过一遍」，不是「掷骰子」。
 *
 * 现在的语义是**洗牌周期**：一条洗牌全序走到底才重洗，周期内
 * 每首歌恰好出现一次。周期结束时的重洗**锚定在当前这首歌上**——新序的第
 * 0 位是刚播完的歌，第 1 位才是下一轮的开头，所以既不会「上一轮最后一首
 * 和下一轮第一首是同一首」，也不会漏掉任何歌。
 *
 * 为什么必须是「一条全序」而不是「每首歌各抽一次」：ExoPlayer 的
 * [androidx.media3.exoplayer.source.ShuffleOrder] 问的是四个互相约束的
 * 问题——`getFirstIndex()` / `getNextIndex(i)` / `getPreviousIndex(i)` /
 * `getLastIndex()`。它会拿这四个答案去判断「还有没有下一首」、去建
 * MediaPeriodQueue、去决定通知栏按钮作用在哪一项。如果每个问题各抽一次，
 * 就会出现「报了 A 却跳到 B」「明明有下一首却说没有」——在 app 里读起来
 * 就是随机播放卡住。
 */
object RandomShuffle {

    /**
     * `0 until size` 的一个均匀打乱，Fisher-Yates。
     *
     * 元素 `k` 是「第 k 个播的队列下标」。每个下标恰好出现一次。
     */
    fun shuffledOrder(size: Int, random: Random = Random.Default): IntArray {
        if (size <= 0) return IntArray(0)
        val order = IntArray(size) { it }
        for (i in size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }
        return order
    }

    /**
     * 以 [anchor] 开头的周期：第 0 位固定是 [anchor]，其余下标均匀洗牌。
     *
     * 用于「一轮播完从当前这首重新起一轮」——保证新周期的第一首不是刚听
     * 过的那首（否则一首歌会连着出现两次），同时周期本身仍然覆盖全队列。
     */
    fun cycleFrom(size: Int, anchor: Int, random: Random = Random.Default): IntArray {
        if (size <= 0) return IntArray(0)
        val head = anchor.coerceIn(0, size - 1)
        val order = IntArray(size)
        order[0] = head
        var at = 1
        for (i in 0 until size) {
            if (i == head) continue
            order[at++] = i
        }
        // 只洗尾部，[head] 留在第 0 位。
        for (i in size - 1 downTo 2) {
            val j = 1 + random.nextInt(i)
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }
        return order
    }
}

/**
 * V6.1: app 按钮与 ExoPlayer 共享的同一条洗牌全序。
 *
 * [PlayerController] 把这个实例交给每一个 [RandomShuffleOrder]，所以通知栏、
 * 锁屏、蓝牙、车机问到的下一首和 app 按钮点出来的下一首是同一首——两条决策
 * 路径共用一个状态，不是各抽各的。
 *
 * 线程：ExoPlayer 的播放线程会通过已安装的 [RandomShuffleOrder] 进来提问，
 * app 主线程会因为按键、队列编辑进来回答。内部只有一个 [IntArray] 和几个
 * Int，但并发改写数组会让某个线程读到写了一半的序（重复/漏项），所以每个
 * 入口都在本实例的监视器上串行化。
 */
class RandomShuffleState {

    /** 当前周期：元素 `k` 是第 k 个播的队列下标；第 0 位是本周期的起点。 */
    private var order: IntArray? = null

    /** [order] 是为多长的队列画的；长度不符即视为过期。 */
    private var orderSize = 0

    /**
     * 换过多少次序（含 [invalidate]）。
     *
     * 存在理由是给 [PlayerController] 判断「装进 ExoPlayer 的那个 order 还是
     * 不是这一份」——重装一份一模一样的 order 会让 ExoPlayer 重建 masking
     * timeline，而 timeline 变化又会回到安装点，形成反馈环。
     *
     * V6.2: 计数器**与克隆共享**。ExoPlayer 每次队列改动都会拿
     * `cloneAndInsert/Remove` 的克隆体去服务会话；克隆体有自己的数组，一旦它
     * 走到周期交界自己重洗，原实例的 version 不会动，安装点就永远发现不了
     * 「通知栏那条序和 app 这条序已经分叉」。共享计数器让任何一侧的重洗都能
     * 被看见，从而在同一次 timeline 事件里被修回。
     */
    private var generation = ShuffleGeneration()

    /** 画过多少个周期（测试用）。 */
    private var cyclesDrawn = 0

    /**
     * 最近一次被问到的「当前曲」下标，-1 表示还不知道。
     *
     * V6.2: [firstIndex] / [lastIndex] 在还没有序的时候要画一条新序。用无锚
     * 洗牌画出来的那条，起点是随机的一首——而 ExoPlayer 经常在 `seekToFirst`
     * 或建 MediaPeriodQueue 时问这两个问题，于是用户走到一半的周期会被悄悄
     * 换成「从某首没听过的歌开始」。记住最近一次查询里的当前位置，就能用
     * [RandomShuffle.cycleFrom] 锚着它起新周期，语义与周期交界处一致。
     */
    private var lastKnown = -1

    // ---------------- 查询 ----------------

    /**
     * [current] 之后播什么。
     *
     * **纯查询：不画序、不推进周期、不改版本号。** media3 在一次决策里会反复
     * 问这个问题（判断「还有没有下一首」、给通知栏取按钮目标、建
     * MediaPeriodQueue），只要它有副作用，同一个决策就会拿到两套不同的洗牌
     * 全序。那正是「随机播放要按几次才真跳」的根因：前几次问到的答案是按旧序
     * 算的，只有等到某次查询顺手重洗完，答案才变成新序的。
     *
     * 周期的推进只发生在 [advanceCycle]（真的换了歌），因此每个周期恰好被
     * 消费一次，与 ExoPlayer 问了几遍无关。
     */
    @Synchronized
    fun nextIndex(size: Int, current: Int, random: Random = Random.Default): Int {
        val o = cycleOrder(size, current, random) ?: return -1
        val pos = o.indexOf(current)
        // 走到本周期末位时回到这条序的开头。正常情况下不会走到这里：
        // [advanceCycle] 在末位那首歌真正开始播时就已经画好了下一轮，
        // 此时 current 落在新序的第 0 位。这里是防御性回退。
        //
        // 回退不能直接返回 o[0]：新序由 [RandomShuffle.cycleFrom] 锚定在当前
        // 这首上，o[0] 就是 current 自己，返回它等于「下一首 = 这首」，用户
        // 按下一首会听到原地重播。所以跳过 current 取下一个槽位。
        if (pos < 0) return o[0]
        if (pos < size - 1) return o[pos + 1]
        // 末位：回绕到序首。但序首可能就是 [current] —— [RandomShuffle.cycleFrom]
        // 正是把它锚在第 0 位。返回自己等于「下一首 = 这首」，用户按下会听到
        // 原地重播，所以这种情况再往后挪一格（size > 1 由 cycleOrder 保证，
        // o[1] 一定存在且不等于 current）。
        return if (o[0] != current) o[0] else o[1]
    }

    /**
     * [current] 之前是什么。
     *
     * 周期内是全序的前一位；[current] 已经在周期起点时返回 `-1`。这个 `-1`
     * 是有意义的：ExoPlayer 拿到 -1 会把「上一首」实现成**把当前这首从头
     * 再放一次**，正是用户在列表开头按上一首时期待的行为。返回「绕到周期
     * 末位」则会跳到本轮还没播过的歌上，听起来像随机跳。
     *
     * app 内的「上一首」不走这里——它走播放历史（见 [PlayHistory]），因为
     * 用户按上一首时想回去的是「刚才听的那首」。
     */
    @Synchronized
    fun previousIndex(size: Int, current: Int, random: Random = Random.Default): Int {
        val o = cycleOrder(size, current, random) ?: return -1
        val pos = o.indexOf(current)
        if (pos <= 0) return -1
        return o[pos - 1]
    }

    /** 本周期从哪首开始。 */
    @Synchronized
    fun firstIndex(size: Int, random: Random = Random.Default): Int {
        if (size <= 0) return -1
        return orderFor(size, lastKnown.takeIf { it in 0 until size }, random)[0]
    }

    /** 本周期最后一首。 */
    @Synchronized
    fun lastIndex(size: Int, random: Random = Random.Default): Int {
        if (size <= 0) return -1
        return orderFor(size, lastKnown.takeIf { it in 0 until size }, random)[size - 1]
    }

    /**
     * 三个查询共用的入口：校验、记住当前位置、取（必要时画出）本周期。
     *
     * @return 本周期的序；`size`/`current` 不可用时返回 null。
     */
    private fun cycleOrder(size: Int, current: Int, random: Random): IntArray? {
        if (size <= 1) return null
        if (current !in 0 until size) return null
        lastKnown = current
        return orderFor(size, current, random)
    }

    // ---------------- 生命周期 ----------------

    /**
     * 一首歌真的开始播了——**周期推进的唯一入口**。
     *
     * 当这首歌正好是本周期末位时，立刻以它为锚画下一轮：新序第 0 位是刚播
     * 完的这首，第 1 位是下一轮的开头。于是下一首歌既不是刚听过的这首，也
     * 不会漏掉任何歌。
     *
     * 幂等：重画之后 current 落在新序第 0 位，再调一次不会继续重画。所以
     * 「transition 回调」和「playIndex」两条路径都可以放心调，重复无害。
     *
     * @return true 表示这次调用换了序，调用方需要把新序重新装给 ExoPlayer。
     */
    @Synchronized
    fun advanceCycle(size: Int, current: Int, random: Random = Random.Default): Boolean {
        val o = cycleOrder(size, current, random) ?: return false
        if (o.indexOf(current) < size - 1) return false
        drawn(RandomShuffle.cycleFrom(size, current, random))
        return true
    }

    /**
     * 丢掉当前周期，下一首歌开始新的一轮。
     *
     * 只在「队列本身变了」或「开关随机」时调用。**不在每次换歌时调用**——
     * 那正是旧实现退化成均匀随机抽样的原因：每首歌都重洗一次，全序永远走
     * 不到第二位。
     */
    @Synchronized
    fun invalidate() {
        order = null
        orderSize = 0
        generation.bump()
    }

    /**
     * 当前这份序的版本号；[invalidate] 和每次重洗都会让它递增。
     */
    @get:Synchronized
    val version: Int get() = generation.value

    /**
     * 一个独立快照，给 ExoPlayer 克隆用。
     *
     * `MediaSourceList.setShuffleOrder` 会用 `cloneAndClear().cloneAndInsert(...)`
     * 修长度不匹配，`ExoPlayerImpl` 还可能用长度断言拒掉克隆体。被克隆的那
     * 一份仍然在服务会话，所以克隆必须是快照而不是共享引用，否则修复动作
     * 会顺手改掉正在用的那条序。
     *
     * V6.2: 数组是快照，[generation] 是共享的（见字段注释）。
     */
    @Synchronized
    fun copy(): RandomShuffleState = RandomShuffleState().also { clone ->
        clone.order = order?.copyOf()
        clone.orderSize = orderSize
        clone.generation = generation
        clone.cyclesDrawn = cyclesDrawn
        clone.lastKnown = lastKnown
    }

    /** 测试用：当前是否已经画过序。 */
    @get:Synchronized
    val memoised: Int get() = if (order == null) 0 else 1

    /** 测试用：累计画过多少个周期。 */
    @get:Synchronized
    val cycles: Int get() = cyclesDrawn

    // ---------------- 内部 ----------------

    /**
     * [size] 对应的那份序。
     *
     * 长度不符时**不能**沿用旧数组——它装的是另一个队列的下标，ExoPlayer 会
     * 当成 child index 直接用而不做越界检查。此时以 [anchor]（有的话）为起点
     * 画一轮新的。
     */
    private fun orderFor(size: Int, anchor: Int?, random: Random): IntArray {
        val cached = order
        if (cached != null && orderSize == size && cached.size == size) return cached
        val fresh = if (anchor != null) RandomShuffle.cycleFrom(size, anchor, random)
        else RandomShuffle.shuffledOrder(size, random)
        return drawn(fresh)
    }

    /** 记下一条新序，并让版本号前进（安装方据此判断要不要重装）。 */
    private fun drawn(fresh: IntArray): IntArray {
        order = fresh
        orderSize = fresh.size
        cyclesDrawn++
        generation.bump()
        return fresh
    }
}

/**
 * V6.2: 一次「序被改写」的共享计数器。
 *
 * 为什么要单独一个对象：ExoPlayer 会拿着 [RandomShuffleState.copy] 的克隆体
 * 去服务真实会话，克隆体的序数组是独立快照，但「这份序还是不是当前版本」
 * 这个问题必须两边答案一致。用同一个计数器实例，任何一侧重洗都会让另一侧
 * 看见，安装点才能把它修回来。`@Synchronized` 保证自增对两个线程可见。
 */
internal class ShuffleGeneration {
    private var count = 0

    val value: Int
        @Synchronized get() = count

    @Synchronized
    fun bump() {
        count++
    }
}
