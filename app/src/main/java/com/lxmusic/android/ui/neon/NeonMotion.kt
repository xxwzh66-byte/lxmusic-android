package com.lxmusic.android.ui.neon

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

/**
 * 系统级的"减少动画"开关在本应用里的唯一入口。
 *
 * Android 把用户意图拆成了两个设置：开发者选项里的 **Animator duration scale**
 * （给 App 读）和辅助功能里的 **移除动画**（`ValueAnimator.areAnimatorsEnabled()`
 * 在 API 26+ 会同时反映两者）。播放页的极光是几团每帧重绘的渐变，属于最该被
 * 这个开关关掉的东西，所以所有无限动画都走这里，而不是各自 `rememberInfiniteTransition`。
 *
 * 为什么只读一次（`remember` 而不是 Lifecycle 观察者）：改动 Animator duration
 * scale 会让系统重建 Activity，播放页会被重新组合，天然就会重读；为此再挂一个
 * 生命周期观察者只会多一条清理路径，不值。
 */
@Composable
fun rememberMotionEnabled(): Boolean = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        true
    }
}

/**
 * 0..1 的循环相位，**返回 State 而不是 Float**。
 *
 * 这不是风格问题：极光在 Canvas 的 draw 块里读 `.value`，状态变化只会让
 * draw 阶段重跑，不会触发重组、也不会重建 Brush；如果这里直接 `by` 解包成
 * Float，60fps 的动画会变成 60fps 的重组，播放页上还挂着歌词列表，很容易掉帧。
 *
 * 关闭动画时返回一个常量相位，让画面**停在一个好看的构图**上（0.42 比 0
 * 好，因为 0 会让三团极光叠在同一个角落）。
 */
@Composable
fun rememberLoopPhase(periodMs: Int, animate: Boolean = true): State<Float> {
    if (!animate) return remember { ConstantFloat(0.42f) }
    return rememberLinearPhase(periodMs, "phase")
}

/**
 * 播放中的"呼吸"，返回 0..1 的正弦值（不是三角波），所以光晕的收放是匀的，
 * 没有 tween 在端点的那一下停顿感。
 *
 * 关闭动画时固定在 0.75——接近最大亮度但不到顶，静态截图里光晕仍然最亮眼。
 */
@Composable
fun rememberBreath(periodMs: Int, animate: Boolean = true): State<Float> {
    if (!animate) return remember { ConstantFloat(0.75f) }
    val raw = rememberLinearPhase(periodMs, "breathRaw")
    return remember(raw) {
        object : State<Float> {
            override val value: Float
                get() = (kotlin.math.sin(raw.value * 2f * Math.PI.toFloat()) + 1f) / 2f
        }
    }
}

/**
 * 0..1 的线性循环相位，[rememberLoopPhase] 与 [rememberBreath] 共用的那一层。
 *
 * 提出来纯粹是因为两者原先各写了一遍同样的 `infiniteRepeatable + tween +
 * LinearEasing + RepeatMode.Restart`；`label` 参与两者共用的参数，好让 Compose
 * 的工具把它俩区分为两条独立的动画。
 */
@Composable
private fun rememberLinearPhase(periodMs: Int, label: String): State<Float> {
    val transition = rememberInfiniteTransition(label = "${label}_$periodMs")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "${label}_value_$periodMs",
    )
}

/** 一个永不变化的 [State]，用于"关闭动画"分支，避免调用方写两套代码。 */
private class ConstantFloat(private val v: Float) : State<Float> {
    override val value: Float get() = v
}
