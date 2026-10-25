package com.lxmusic.android.ui.neon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * V6.0 播放页的背景：三团缓慢漂移的极光。
 *
 * 为什么是"画"而不是"真模糊"：
 * - 用 `Modifier.blur` / RenderEffect 做背景虚化，在 API31+ 上是整屏的一层
 *   离屏渲染，V5.5.2 的崩溃排查已经证明这台设备吃不住（见 NowPlayingScreen
 *   的历史注释）。极光用径向渐变画出来，效果接近，但只是一次普通 draw。
 * - 用一张裁剪过的封面图加 alpha 也能做"环境光"，但那样整屏都是同一张图，
 *   换歌时会闪；渐变可以随相位流动，看起来是"活的"。
 *
 * 三团的位置各走一个椭圆轨道（x/y 半径不同、相位不同），所以它们的相对
 * 位置一直在变，不会退化成简单的整体平移。三个周期取 11s / 17s / 23s
 * （两两互质），叠出来的图案要 11×17×23 秒才重复一次，人耳听一首歌的时间
 * 里看不出周期。
 */
@Composable
fun AuroraBackdrop(
    colors: List<Color>,
    animate: Boolean = true,
    modifier: Modifier = Modifier,
    /** 峰值不透明度。默认取 [NeonTokens.AURORA_PEAK]，播放页可再压低。 */
    peak: Float = NeonTokens.AURORA_PEAK,
    /** 极光之下的兜底底色。主界面用同一套极光但配更深的底。 */
    baseColor: Color = NeonTokens.ink,
    /**
     * 极光整体压暗的量（0 不压）。主界面在极光上盖列表，需要把亮度压到
     * 正文仍然可读的水平（~0.55）；播放页的极光是主角，保持 0。
     */
    dim: Float = 0f,
    /**
     * 是否画上下的压黑渐变。播放页需要（状态栏图标/进度条都要落在暗区），
     * 主界面不需要——那两条渐变会把列表的顶部和底部吃掉一大块。
     */
    scrims: Boolean = true,
) {
    val p1 by rememberLoopPhase(NeonTiming.auroraMs, animate)
    val p2 by rememberLoopPhase(NeonTiming.auroraSecondaryMs, animate)
    val p3 by rememberLoopPhase(NeonTiming.auroraTertiaryMs, animate)

    val a = colors.getOrElse(0) { NeonTokens.neonA }
    val b = colors.getOrElse(1) { NeonTokens.neonB }
    val c = colors.getOrElse(2) { NeonTokens.neonC }

    // 压暗直接作用在峰值上，而不是再盖一层黑：多盖一层会让极光失去颜色层次，
    // 变成"灰蒙蒙的深色"，而压峰值只是变淡，色相还在。
    val p = (peak * (1f - dim.coerceIn(0f, 1f))).coerceIn(0f, 1f)

    Canvas(modifier.fillMaxSize()) {
        drawRect(baseColor)
        val w = size.width
        val h = size.height

        // 第一团：直径约屏宽 0.95 倍，压在左上（唱片后方）。
        drawAurora(
            center = Offset(
                x = w * (0.28f + 0.10f * cosTau(p1)),
                y = h * (0.22f + 0.06f * sinTau(p1)),
            ),
            radius = w * 0.62f,
            color = a,
            peak = p,
        )
        // 第二团：右下，屏幕最大的光斑，承担"暖色"。
        drawAurora(
            center = Offset(
                x = w * (0.78f + 0.08f * cosTau(p2 + 0.33f)),
                y = h * (0.58f + 0.07f * sinTau(p2 + 0.33f)),
            ),
            radius = w * 0.78f,
            color = b,
            peak = p * 0.85f,
        )
        // 第三团：底部中间，偏冷色，把上两团压住不要让整屏发飘。
        drawAurora(
            center = Offset(
                x = w * (0.46f + 0.12f * cosTau(p3 + 0.61f)),
                y = h * (0.96f + 0.03f * sinTau(p3 + 0.61f)),
            ),
            radius = w * 0.70f,
            color = c,
            peak = p * 0.62f,
        )

        if (scrims) {
            // 底部压黑：让进度条与播放键落在一个稳定的深色区，
            // 否则最亮的极光会正好压在时间文字后面。
            drawRect(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to NeonTokens.inkDeep.copy(alpha = 0.92f),
                )
            )
            // 顶部压黑：状态栏图标是白色的，需要一块够暗的底。
            drawRect(
                Brush.verticalGradient(
                    0f to NeonTokens.inkDeep.copy(alpha = 0.85f),
                    0.18f to Color.Transparent,
                )
            )
        }
    }
}

/** 一团极光：径向渐变，中心不透明、外圈透明。 */
private fun DrawScope.drawAurora(
    center: Offset,
    radius: Float,
    color: Color,
    peak: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = peak),
                color.copy(alpha = peak * 0.55f),
                color.copy(alpha = peak * 0.18f),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun cosTau(t: Float): Float = kotlin.math.cos(t * 2f * Math.PI.toFloat())
private fun sinTau(t: Float): Float = kotlin.math.sin(t * 2f * Math.PI.toFloat())
