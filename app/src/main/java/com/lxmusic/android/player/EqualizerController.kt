package com.lxmusic.android.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.lxmusic.android.data.settings.EqProfile

/** One adjustable EQ band. */
data class EqBand(val index: Int, val centerFreqHz: Int, val levelDb: Short, val minDb: Short, val maxDb: Short)

/** A named equalizer preset. */
data class EqPreset(val name: String, val levelsMb: IntArray) {
    override fun equals(other: Any?): Boolean =
        other is EqPreset && other.name == name && other.levelsMb.contentEquals(levelsMb)
    override fun hashCode(): Int = name.hashCode() * 31 + levelsMb.contentHashCode()
}

/** Observable equalizer state for the UI. */
data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val presets: List<EqPreset> = emptyList(),
    val currentPreset: String? = null,
    val bassStrength: Int = 0,      // 0..1000
    val virtualizerStrength: Int = 0, // 0..1000
    val loudnessGainMb: Int = 0,    // 0..1500
    val error: String? = null,
)

/**
 * Wraps Android's system audio effects (Equalizer / BassBoost / Virtualizer /
 * LoudnessEnhancer) and binds them to ExoPlayer's audio session.
 *
 * The effects are attached lazily once the player has a valid session id.
 */
class EqualizerController {

    private val TAG = "EqualizerController"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private var sessionId: Int = 0

    /**
     * V6.1: 用户上一次调好的曲线，attach 之后用来重新灌回效果器。
     *
     * 只在 attach 期间生效：效果器对象本身不跨会话，新会话的效果器一定是全平的，
     * 所以「记住的设置」必须是 app 侧的一份快照（见 [EqProfile]）。
     */
    private var restoreProfile: EqProfile? = null

    /** The most recent state published; the UI's source of truth. */
    var state: EqualizerState = EqualizerState()
        private set

    var onStateChanged: ((EqualizerState) -> Unit)? = null

    private fun publish(s: EqualizerState) {
        state = s
        onStateChanged?.invoke(s)
    }

    /** Attach effects to a player's audio session. Safe to call repeatedly. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            eq.enabled = false

            val bands = ArrayList<EqBand>()
            for (i in 0 until eq.numberOfBands) {
                val range = eq.bandLevelRange
                bands.add(
                    EqBand(
                        index = i,
                        centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000,
                        levelDb = eq.getBandLevel(i.toShort()),
                        minDb = range[0],
                        maxDb = range[1],
                    )
                )
            }

            val presets = ArrayList<EqPreset>()
            for (i in 0 until eq.numberOfPresets) {
                val name = runCatching { eq.getPresetName(i.toShort()) }.getOrDefault("Preset $i")
                // GetPresetBandLevel does not exist; select the preset and read back
                // the band levels, then restore the previous selection.
                val levels = IntArray(bands.size)
                runCatching {
                    val prev = eq.currentPreset
                    eq.usePreset(i.toShort())
                    for (b in bands.indices) levels[b] = eq.getBandLevel(b.toShort()).toInt()
                    eq.usePreset(prev)
                }
                presets.add(EqPreset(name, levels))
            }
            // Reset to flat after probing presets.
            for (b in bands.indices) eq.setBandLevel(b.toShort(), 0)

            bassBoost = runCatching { BassBoost(0, audioSessionId).apply { enabled = false } }.getOrNull()
            virtualizer = runCatching { Virtualizer(0, audioSessionId).apply { enabled = false } }.getOrNull()
            loudness = runCatching { LoudnessEnhancer(audioSessionId).apply { setTargetGain(0); enabled = false } }.getOrNull()

            publish(
                EqualizerState(
                    available = true,
                    enabled = false,
                    bands = bands,
                    presets = presets,
                    currentPreset = null,
                )
            )
            Log.d(TAG, "attached to session $audioSessionId with ${bands.size} bands, ${presets.size} presets")
            // V6.1: 新建的效果器一定是全平的，用户上次调好的曲线要靠这里灌回去。
            restoreProfile?.let { applyProfile(it) }
        } catch (e: Exception) {
            Log.e(TAG, "attach failed", e)
            publish(EqualizerState(available = false, error = e.message))
        }
    }

    /**
     * V6.1: 记住 [profile]，将来每次 attach 都用它初始化效果器。
     *
     * 已经 attach 过的话当场生效，这样「设置页改完 → 回到播放页」不需要等下一次
     * 会话重建。
     */
    fun setProfile(profile: EqProfile) {
        restoreProfile = profile
        if (equalizer != null) applyProfile(profile)
    }

    /**
     * 当前设置快照，用于持久化。
     *
     * V6.2: 未 attach 时**必须**拿 [restoreProfile] 当底。以前这里无条件从
     * [state] 取频段，而未 attach 的 [state] 里 `bands` 是空的（没有效果器就
     * 没有频段信息），于是「播放器还没就绪时动一下均衡器开关」会落盘一条
     * `bands=""` 的曲线，把用户上次调好的频段整条抹掉。频段是设备无关的用户
     * 选择，只有存在效果器时才该由效果器回报。
     */
    fun snapshot(): EqProfile {
        val base = restoreProfile ?: EqProfile.FLAT
        if (equalizer == null) {
            return base.copy(
                enabled = state.enabled,
                preset = state.currentPreset ?: base.preset,
                bass = state.bassStrength,
                virtualizer = state.virtualizerStrength,
                loudnessMb = state.loudnessGainMb,
            )
        }
        return EqProfile(
            enabled = state.enabled,
            preset = state.currentPreset,
            bands = state.bands.map { it.levelDb },
            bass = state.bassStrength,
            virtualizer = state.virtualizerStrength,
            loudnessMb = state.loudnessGainMb,
        )
    }

    /**
     * V6.1.1: 把「现在的样子」记为下次 attach 要恢复的样子。
     *
     * 每次用户改动之后都必须调一次。以前只有 [setProfile] 会写 [restoreProfile]，
     * 而 [setProfile] 只在启动时被调用一次 —— 于是用户调完曲线、播放器换了音频
     * 会话，[attach] 恢复的还是**开机时**那份旧曲线，用户刚调的全部丢失。落盘
     * 是另一条路径（MainViewModel.persistEq），修不了这个：数据在盘上是对的，
     * 只是没人读回来。
     */
    private fun rememberCurrent() {
        restoreProfile = snapshot()
    }

    /**
     * 把 [profile] 灌进当前效果器。
     *
     * 顺序有讲究：先写频段/强度，最后才 `enabled`——`setStrength` 在音效关闭时
     * 不会真正生效（见 [setBassStrength] 里的 `if (strength > 0 && state.enabled)`），
     * 先开再写会让写进去的值被系统的 enabled 切换吃掉。
     */
    private fun applyProfile(profile: EqProfile) {
        try {
            val eq = equalizer ?: return
            for (i in 0 until eq.numberOfBands) {
                eq.setBandLevel(i.toShort(), profile.bands.getOrElse(i) { 0 })
            }
            bassBoost?.setStrength(profile.bass.toShort())
            virtualizer?.setStrength(profile.virtualizer.toShort())
            loudness?.setTargetGain(profile.loudnessMb)
            eq.enabled = profile.enabled
            bassBoost?.enabled = profile.enabled
            virtualizer?.enabled = profile.enabled
            loudness?.enabled = profile.enabled
            publish(
                state.copy(
                    enabled = profile.enabled,
                    currentPreset = profile.preset,
                    bands = state.bands.mapIndexed { i, band ->
                        band.copy(levelDb = profile.bands.getOrElse(i) { 0 })
                    },
                    bassStrength = profile.bass,
                    virtualizerStrength = profile.virtualizer,
                    loudnessGainMb = profile.loudnessMb,
                    error = null,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "applyProfile failed", e)
            publish(state.copy(error = e.message))
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            loudness?.enabled = enabled
            publish(state.copy(enabled = enabled))
            rememberCurrent()
        } catch (e: Exception) {
            publish(state.copy(error = e.message))
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Short) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), levelMb)
            publish(state.copy(
                bands = state.bands.map { if (it.index == bandIndex) it.copy(levelDb = levelMb) else it },
                currentPreset = null,
            ))
            rememberCurrent()
        } catch (e: Exception) {
            publish(state.copy(error = e.message))
        }
    }

    /** Applies a preset by name. */
    fun applyPreset(preset: EqPreset, enable: Boolean = true) {
        try {
            val eq = equalizer ?: return
            for (b in state.bands.indices) {
                eq.setBandLevel(b.toShort(), preset.levelsMb.getOrElse(b) { 0 }.toShort())
            }
            if (enable) {
                eq.enabled = true
                bassBoost?.enabled = true
                virtualizer?.enabled = true
                loudness?.enabled = true
            }
            publish(state.copy(
                enabled = enable,
                currentPreset = preset.name,
                bands = state.bands.mapIndexed { i, band ->
                    band.copy(levelDb = preset.levelsMb.getOrElse(i) { 0 }.toShort())
                },
            ))
            rememberCurrent()
        } catch (e: Exception) {
            publish(state.copy(error = e.message))
        }
    }

    fun setBassStrength(strength: Int) {
        try {
            bassBoost?.setStrength(strength.toShort())
            if (strength > 0 && state.enabled) bassBoost?.enabled = true
            publish(state.copy(bassStrength = strength))
            rememberCurrent()
        } catch (e: Exception) {
            publish(state.copy(error = e.message))
        }
    }

    fun setVirtualizerStrength(strength: Int) {
        try {
            virtualizer?.setStrength(strength.toShort())
            if (strength > 0 && state.enabled) virtualizer?.enabled = true
            publish(state.copy(virtualizerStrength = strength))
            rememberCurrent()
        } catch (e: Exception) {
            publish(state.copy(error = e.message))
        }
    }

    fun setLoudnessGainMb(gainMb: Int) {
        try {
            loudness?.setTargetGain(gainMb)
            if (gainMb > 0 && state.enabled) loudness?.enabled = true
            publish(state.copy(loudnessGainMb = gainMb))
            rememberCurrent()
        } catch (e: Exception) {
            publish(state.copy(error = e.message))
        }
    }

    fun reset() {
        try {
            equalizer?.let { eq ->
                for (i in 0 until eq.numberOfBands) eq.setBandLevel(i.toShort(), 0)
            }
            bassBoost?.setStrength(0)
            virtualizer?.setStrength(0)
            loudness?.setTargetGain(0)
            publish(state.copy(
                currentPreset = null,
                bassStrength = 0,
                virtualizerStrength = 0,
                loudnessGainMb = 0,
                bands = state.bands.map { it.copy(levelDb = 0) },
            ))
            // V6.2: 重置的语义是「回到全平」，所以要明确写 FLAT —— 不能交给
            // rememberCurrent()，因为未 attach 时 snapshot() 会拿 restoreProfile
            // 当底（见 [snapshot]），那样重置就成了空操作。
            restoreProfile = EqProfile.FLAT.copy(enabled = state.enabled)
        } catch (e: Exception) {
            publish(state.copy(error = e.message))
        }
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}
        equalizer = null; bassBoost = null; virtualizer = null; loudness = null
        sessionId = 0
    }
}
