package com.lxmusic.android.data.settings

/**
 * V6.1: 均衡器的可持久化快照。
 *
 * 为什么需要这么一个东西：Android 的 `Equalizer` 效果是绑在**音频会话**上的，
 * 每次 ExoPlayer 重建 audio session 都必须 `release()` 再重新 `Equalizer(0, newSessionId)`。
 * 新建的效果器永远是全平的——所以「用户调好的曲线」不能只活在那几个 effect 对象里，
 * 必须在 app 侧留一份快照，attach 之后重新灌回去。用户说的「均衡器下次打开要生效」
 * 就是这条链。
 *
 * 只存「用户的选择」，不存系统反馈：`available` / `error` 每次 attach 都会重算，
 * `bands` 的中心频率与 dB 范围来自设备，不同设备不一样，存下来反而会串味。
 *
 * 放在 data.settings 而不是 player：这是**设置**，由 [SettingsStore] 持有并把
 * 快照交给 `EqualizerController`，依赖方向是 player → data，不是反过来。
 */
data class EqProfile(
    val enabled: Boolean = false,
    /** 选中的预设名；手调过任意频段后为 null。 */
    val preset: String? = null,
    /** 各频段电平（毫贝），按下标排列。长度不足时缺的按 0 处理。 */
    val bands: List<Short> = emptyList(),
    /** 低音增强 0..1000。 */
    val bass: Int = 0,
    /** 环绕扩展 0..1000。 */
    val virtualizer: Int = 0,
    /** 响度提升，单位毫贝 0..1500。 */
    val loudnessMb: Int = 0,
) {
    companion object {
        val FLAT = EqProfile()

        /**
         * 频段电平编成 `"0,-150,300"`。用字符串而不是 IntArray：DataStore 的
         * Preferences 没有数组类型，而包一层 JSON 只为了几个 short 不划算。
         */
        fun encodeBands(levels: List<Short>): String = levels.joinToString(",")

        /**
         * 解析失败的位置直接丢弃，不抛异常——设置是「坏了也不能让 app 起不来」
         * 的那类数据，和 [SettingsStore] 里其它字段的 `runCatching` 取值策略一致。
         */
        fun decodeBands(text: String?): List<Short> {
            if (text.isNullOrBlank()) return emptyList()
            return text.split(',').mapNotNull { it.trim().toShortOrNull() }
        }
    }
}
