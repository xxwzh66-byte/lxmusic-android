package com.lxmusic.android.ui.neon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lxmusic.android.ui.theme.paletteById

/**
 * 主界面外壳的材质规格。
 *
 * 为什么需要这个东西：项目设置里 `themeMode` 有三档（SYSTEM / LIGHT / DARK），
 * 默认 SYSTEM，而且 `ThemePaletteTest` 已经锁死了浅色与深色两套 scheme 必须不同。
 * 列表页的正文颜色读的是 `MaterialTheme.colorScheme.onSurface`——浅色主题下它是
 * 近黑的。如果主界面无条件铺一层深色极光，浅色主题下就是"深字压深底"，
 * 正文直接不可读。
 *
 * 所以**霓虹外壳必须是双主题的**：
 * - 深色：极光 + 亮色玻璃（白色低透明度），这是设计稿的形态。
 * - 浅色：极光压到只剩一层色晕，玻璃换成高透明度白色（在浅底上就是磨砂白），
 *   描边换成正黑低透明度。同一套组件、两套数值，不为浅色另写一套组件。
 *
 * 播放页不适用这套：它是沉浸舞台，永远深色（正文用 [NeonTokens.text]，
 * 也就是 OledTokens 的亮色 token），与主题模式无关。
 */
data class NeonSurfaceSpec(
    /** 极光之下的兜底底色。 */
    val baseColor: Color,
    /** 三团极光的颜色。 */
    val auroraColors: List<Color>,
    /** 极光压暗量（0 不压）。 */
    val dim: Float,
    /** 玻璃面板顶部高光。 */
    val glassTop: Color,
    /** 玻璃面板底部填充。 */
    val glassBottom: Color,
    /** 1dp 描边。 */
    val stroke: Color,
    /** 未播轨道。 */
    val rail: Color,
    /** 迷你播放条这种"几乎不透明"的面板底色。 */
    val panelSolid: Color,
)

/**
 * 按当前深浅色算出主界面材质。
 *
 * 浅色侧的数值不是拍脑袋：极光峰值压到 `AURORA_PEAK * 0.22`，只剩一点色偏，
 * 保证 `onSurface`（近黑）在它上面仍然远超 4.5:1；玻璃用 `White 0.55` 是因为
 * 底色已经是浅灰紫，白色半透明才看得出"面板"这一层，用深色玻璃反而会脏。
 */
@Composable
fun rememberNeonSurface(
    dark: Boolean,
    themeColor: String,
    /** V1.1.0: 当前曲目封面主色；null = 沿用调色板（第一次启动/纯色占位封面）。 */
    artworkTint: androidx.compose.ui.graphics.Color? = null,
): NeonSurfaceSpec {
    return remember(dark, themeColor, artworkTint) {
        val brand = artworkTint?.let { tint ->
            // 封面主色替换品牌渐变的中段，首尾照旧做提亮/压暗，
            // 保证三档可辨——与 brandGradient 同一套数值语言。
            listOf(
                NeonTokens.lighten(tint, 0.42f),
                tint,
                NeonTokens.darken(tint, 0.35f),
            )
        } ?: NeonTokens.brandGradient(themeColor)
        if (dark) {
            NeonSurfaceSpec(
                baseColor = NeonTokens.inkSoft,
                auroraColors = brand,
                dim = 0.55f,
                glassTop = NeonTokens.glassStrong,
                glassBottom = NeonTokens.inkSoft.copy(alpha = 0.94f),
                stroke = NeonTokens.stroke,
                rail = NeonTokens.rail,
                panelSolid = NeonTokens.surface,
            )
        } else {
            // 浅色极光：把品牌色往白里提很多，再靠 dim 把峰值压到 ~7%，
            // 于是它读起来是"一层很淡的彩色雾"，而不是一块彩色玻璃。
            val seed = paletteById(themeColor).seed
            NeonSurfaceSpec(
                baseColor = Color(0xFFF6F5FB),
                auroraColors = listOf(
                    NeonTokens.lighten(seed, 0.72f),
                    NeonTokens.lighten(seed, 0.55f),
                    NeonTokens.lighten(seed, 0.88f),
                ),
                dim = 0.78f,
                glassTop = Color.White.copy(alpha = 0.62f),
                glassBottom = Color.White.copy(alpha = 0.34f),
                stroke = Color.Black.copy(alpha = 0.07f),
                rail = Color.Black.copy(alpha = 0.10f),
                panelSolid = Color.White.copy(alpha = 0.86f),
            )
        }
    }
}
