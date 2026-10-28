package com.lxmusic.android.player

import kotlin.random.Random

/**
 * V1.0.1: 随机播放的「抽一首」。
 *
 * 历史教训，三次都记在这里：
 *
 * 1. v5.3.2 ~ v6.0 是**每次查询独立抽样**：media3 一次决策会问好几遍
 *    `getNextIndex`，每问一遍都是新答案，于是通知栏报 A、播放器跳 B，
 *    读起来就是「随机播放卡住」。
 * 2. V6.1 ~ V1.0 改成**洗牌周期**：一条全序走完才重洗。这修好了卡住，但带来
 *    新问题——一轮之内顺序是**定死的**，用户按下一首之前就已经知道会播哪首，
 *    而且上一首在这个定死的序里是「本轮还没播过的一首」，不是他刚听过的那首。
 * 3. V1.0.1 按用户的要求回到**真随机，但同一次决策只抽一次**。区别在第 1 条
 *    的失败点上：抽样结果被记在一次决策的缓存里（见
 *    [RandomShuffleState.nextIndex]），media3 问几遍都拿同一个答案；缓存只在
 *    **真的换歌**时清（[RandomShuffleState.onTrackStarted]）。于是每按一次是
 *    新的随机，同一次按键内部是一致的。
 *
 * 也就是说：随机性由「什么时候重抽」决定，不由「怎么抽」决定。
 */
object RandomShuffle {

    /**
     * 从 `0 until size` 里均匀抽一个，避开 [exclude] 里的下标。
     *
     * 避不开时（`size` 太小）退回「抽到谁算谁」，调用方拿到的是范围内的数。
     * 先抽一次再看是否落在禁用集合里，是为了让分布保持均匀——用
     * `while (pick in exclude)` 重抽在 `size == exclude.size` 时会死循环，
     * 而「把候选压成数组再抽」在多线程里每次都要分配。
     */
    fun pickOther(size: Int, exclude: IntArray, random: Random = Random.Default): Int {
        if (size <= 0) return -1
        var pick = random.nextInt(size)
        if (exclude.isEmpty()) return pick
        if (pick !in exclude) return pick
        // 被禁了：把候选压成紧凑数组再抽一次，保证不会无限重抽。
        val candidates = IntArray(size - exclude.count { it in 0 until size })
        if (candidates.isEmpty()) return pick
        var at = 0
        for (i in 0 until size) if (i !in exclude) candidates[at++] = i
        return candidates[random.nextInt(candidates.size)]
    }
}

/**
 * V1.0.1: app 按钮与 ExoPlayer 共享的同一个随机源。
 *
 * [PlayerController] 把这个实例交给每一个 [RandomShuffleOrder]，所以通知栏、
 * 锁屏、蓝牙、车机问到的下一首和 app 按钮点出来的下一首是同一首——两条决策
 * 路径共用一个状态，不是各抽各的。这个共享是 v5.3.6 修「卡住」时确立的，
 * V1.0.1 保留。
 *
 * ## 一次决策只抽一次
 *
 * media3 在一次「下一首」里会把 `getNextIndex(i)` 问好几遍：判断还有没有下一
 * 首、给通知栏取按钮目标、建 MediaPeriodQueue。如果每问一遍都重抽，同一个
 * 决策就会拿到几个不同的目标——这正是 v5.3.2 的卡住。所以抽出来的下标**记在
 * 缓存里**，同一个 `current` 再问就返回同一个答案。
 *
 * 缓存什么时候清：[onTrackStarted]，也就是这首歌真的开始播了。这样
 *
 * - 同一首曲在播期间，media3 问几遍答案都一致；
 * - 换了歌，下一次按键就是新的随机；
 * - 自动续播（一首放完）也是一次新的随机抽。
 *
 * ## 线程
 *
 * ExoPlayer 的播放线程会通过已安装的 [RandomShuffleOrder] 进来提问，app 主
 * 线程会因为按键、队列编辑进来回答。内部只有几个 Int，但「读缓存 → 抽 →
 * 写缓存」必须原子，否则两个线程会各抽一个。每个入口都在本实例的监视器上
 * 串行化。
 */
class RandomShuffleState {

    /** 当前这首「下一首」已经抽定的目标，-1 表示还没抽。 */
    private var nextPick = -1

    /** 当前这首「上一首」已经抽定的目标，-1 表示还没抽。 */
    private var previousPick = -1

    /**
     * 最近一次真正开始播的下标，-1 表示还不知道。
     *
     * 只用于「上一首别把我送回刚听完的那首」——不需要整条历史，一个下标就够
     * 了，因为用户按上一首时想避开的就是刚才那一首。真正的历史栈在 V1.0.1
     * 之后没有消费者了（见 HANDOVER 的变更记录）。
     */
    private var lastTrack = -1

    /**
     * 换过多少次序（含 [invalidate]）。
     *
     * 存在理由是给 [PlayerController] 判断「装进 ExoPlayer 的那个 order 还是
     * 不是这一份」——重装一份一模一样的 order 会让 ExoPlayer 重建 masking
     * timeline，而 timeline 变化又会回到安装点，形成反馈环。
     */
    private var generation = ShuffleGeneration()

    // ---------------- 查询 ----------------

    /**
     * [current] 之后播什么：一个均匀抽出来的、不等于 [current] 的下标。
     *
     * 同一个 [current] 重复问返回同一个答案（见类的 KDoc），直到有人调用
     * [onTrackStarted] 说明那首歌真的播了。
     */
    @Synchronized
    fun nextIndex(size: Int, current: Int, random: Random = Random.Default): Int {
        if (size <= 1) return -1
        if (current !in 0 until size) return -1
        val cached = nextPick
        if (cached in 0 until size && cached != current) return cached
        val picked = RandomShuffle.pickOther(size, intArrayOf(current), random)
        nextPick = picked
        return picked
    }

    /**
     * [current] 之前播什么：同样是一个均匀抽出来的下标。
     *
     * V1.0.1：这里不再是「周期里的前一位」。用户要的是上一次也随机，所以
     * 上一首同样是抽的；唯一的修饰是**避开刚播完的那首**
     * （[lastTrack]），否则「上一首」会退回刚刚听完的曲子，看起来像没生效。
     */
    @Synchronized
    fun previousIndex(size: Int, current: Int, random: Random = Random.Default): Int {
        if (size <= 1) return -1
        if (current !in 0 until size) return -1
        val cached = previousPick
        if (cached in 0 until size && cached != current) return cached
        val avoid = if (size > 2 && lastTrack in 0 until size && lastTrack != current) {
            intArrayOf(current, lastTrack)
        } else {
            intArrayOf(current)
        }
        val picked = RandomShuffle.pickOther(size, avoid, random)
        previousPick = picked
        return picked
    }

    /**
     * 本队列的第一个下标。
     *
     * 不是随机结果：media3 只在 `repeatMode == ALL` 回绕、以及建
     * MediaPeriodQueue 时问它，答案要稳定（问两遍不同会让它重排缓冲）。
     * 随机播放的「下一首」不走这里。
     */
    @Synchronized
    fun firstIndex(size: Int): Int = if (size <= 0) -1 else 0

    /** 本队列的最后一个下标；理由同 [firstIndex]。 */
    @Synchronized
    fun lastIndex(size: Int): Int = if (size <= 0) -1 else size - 1

    // ---------------- 生命周期 ----------------

    /**
     * 一首歌真的开始播了——**清空抽样缓存的唯一入口**。
     *
     * 必须由两条换歌路径都调用：[org.androidx.media3.common.Player.Listener]
     * 的 `onMediaItemTransition`（通知栏/蓝牙/自动续播）和
     * [PlayerController.playIndex]（app 内按键、在线曲目单曲播放）。任一路径
     * 漏掉，那条路径上下一次按键就会拿到上一条路径抽的缓存，表现为「按了没
     * 反应」——按一次不动、按第二次才走。
     */
    @Synchronized
    fun onTrackStarted(size: Int, current: Int) {
        if (current in 0 until size) lastTrack = current
        nextPick = -1
        previousPick = -1
    }

    /**
     * 丢掉缓存，下一次查询重新抽。
     *
     * 只在「队列本身变了」或「开关随机」时调用。**不在每次换歌时调用**——
     * 换歌走 [onTrackStarted]，那个还会顺手记住 [lastTrack]。
     */
    @Synchronized
    fun invalidate() {
        nextPick = -1
        previousPick = -1
        lastTrack = -1
        generation.bump()
    }

    /**
     * 当前这份随机源的版本号；[invalidate] 会让它递增。
     *
     * 抽样缓存的变化**不算**版本变化：缓存是「同一次决策的备忘」，而
     * 版本号问的是「装进 ExoPlayer 的那个 order 还是不是这一份」。order 一直
     * 转发到本实例，缓存变了下一次查询自然就看见了，不需要重装。
     */
    @get:Synchronized
    val version: Int get() = generation.value

    /**
     * 一个独立快照，给 ExoPlayer 克隆用。
     *
     * `MediaSourceList.setShuffleOrder` 会用 `cloneAndClear().cloneAndInsert(...)`
     * 修长度不匹配，`ExoPlayerImpl` 还可能用长度断言拒掉克隆体。被克隆的那一
     * 份仍然在服务会话，所以缓存必须是快照而不是共享引用。
     *
     * [generation] 是共享的：任何一侧让版本前进，安装点都要能看见。
     */
    @Synchronized
    fun copy(): RandomShuffleState = RandomShuffleState().also { clone ->
        clone.nextPick = nextPick
        clone.previousPick = previousPick
        clone.lastTrack = lastTrack
        clone.generation = generation
    }

    /** 测试用：当前是否已经有抽定的下一首。 */
    @get:Synchronized
    val memoised: Int get() = if (nextPick < 0) 0 else 1
}

/**
 * 一次「版本被改写」的共享计数器。
 *
 * 为什么要单独一个对象：ExoPlayer 会拿着 [RandomShuffleState.copy] 的克隆体
 * 去服务真实会话，克隆体的缓存是独立快照，但「这份源还是不是当前版本」这个
 * 问题必须两边答案一致。用同一个计数器实例，任何一侧改写都会让另一侧看见。
 * `@Synchronized` 保证自增对两个线程可见。
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
