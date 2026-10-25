package com.lxmusic.android.ui.neon

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lxmusic.android.ui.theme.AppPalette
import com.lxmusic.android.ui.theme.OledTokens
import com.lxmusic.android.ui.theme.paletteById

/**
 * V6.0 "Neon Vinyl" 霓虹唱片视觉系统的色板。
 *
 * 四条约束决定了这组数值（每一条都对应项目里已经存在的事实）：
 *
 * 1. **不引入 RenderEffect 模糊。** V5.5.2 的崩溃排查已经把 API31+ 全屏
 *    RenderEffect 从播放页删掉了（见 NowPlayingScreen.kt:140-145 的注释）。
 *    所以"玻璃"必须是画出来的（半透明白 + 1dp 描边 + 顶部高光），不能用真的
 *    背景模糊。这也是 ui-ux-pro-max 里 Glassmorphism 的 cost:low 版本。
 *
 * 2. **文字色沿用 [OledTokens]。** `OledTokensTest` 已经在锁 4.5:1 对比度
 *    （OledTokensTest.kt:28-49），播放页正文继续读那两个 token，换背景也不会
 *    掉对比度。霓虹三原色只用于渐变、进度和"活动态"，不承担正文。
 *
 * 3. **霓虹只给"正在发生的事"上色**：进度、正在播放的封面、随机/循环的开启态、
 *    当前歌词行。静态装饰一律走中性玻璃——整屏都在发光等于没有重点。
 *
 * 4. **极光颜色来自用户选的调色板 + 封面取色**，不是写死的三原色。所以
 *    `brandGradient` 是函数不是常量，`auroraFrom` 负责把封面色和品牌色拼成
 *    一组可叠加的极光。
 *
 * 风格来源（ui-ux-pro-max）：Aurora UI（music platform 首选；流动渐变 8-12s
 * 循环，cost:low, drivers:none）+ Dark Mode (OLED)（cost:low）+ Glassmorphism
 * （rgba 0.06-0.13 填充 + 0.14 描边）。
 */
object NeonTokens {

    /** 播放页底色。比 OledTokens.background(#0F0F23) 更深，给极光留出层次。 */
    val ink = Color(0xFF07050F)

    /** 极光最暗处的兜底色，只在极光完全透明的位置露出来。 */
    val inkDeep = Color(0xFF030109)

    /** 列表/卡片底色（比 ink 略亮一档）。 */
    val inkSoft = Color(0xFF110D22)

    /** 玻璃面板的实心兜底色，用于无法叠内容的位置。 */
    val surface = Color(0xFF171226)

    // ---- 文字：直接转调 OLED 语义 token，对比度已被 OledTokensTest 锁定 ----
    /** 主文字。 */
    val text: Color get() = OledTokens.foreground

    /** 次文字。 */
    val textMuted: Color get() = OledTokens.mutedForeground

    // ---- 霓虹三原色：只用于渐变与"活动态" ----
    /** 品红，进度条起点 / 主强调。 */
    val neonA = Color(0xFFFF2E88)

    /** 紫罗兰，渐变中段。 */
    val neonB = Color(0xFF7C5CFF)

    /** 青，进度条终点 / 频谱。 */
    val neonC = Color(0xFF2EE6D6)

    /** 开启态（随机 / 循环 / 收藏）。与 OledTokens.accent 同族，更亮一档。 */
    val accent = Color(0xFF37F2A0)

    /** 危险操作（保持与 OledTokens.destructive 一致，避免两套红色）。 */
    val destructive: Color get() = OledTokens.destructive

    // ---- 玻璃：全部是"画"出来的，没有真模糊 ----
    /** 面板底色（低）。 */
    val glass = Color.White.copy(alpha = 0.06f)

    /** 面板中段（按钮容器）。 */
    val glassStrong = Color.White.copy(alpha = 0.10f)

    /** 面板顶部高光（高）。 */
    val glassHi = Color.White.copy(alpha = 0.13f)

    /** 1dp 描边。 */
    val stroke = Color.White.copy(alpha = 0.14f)

    /** 描边提亮版（按下 / 选中）。 */
    val strokeHot = Color.White.copy(alpha = 0.24f)

    /** 轨道未播部分。 */
    val rail = Color.White.copy(alpha = 0.12f)

    /**
     * 极光单团的最大不透明度。**这是可读性的红线**：超过 0.34 之后，
     * #F8FAFC 正文压在最亮的极光上会跌破 4.5:1（`NeonTokensTest` 锁这个值）。
     */
    const val AURORA_PEAK = 0.34f

    /** 玻璃面板统一圆角（比 OledTokens.radiusLg 更圆，接近 iOS 卡片）。 */
    val radiusGlass = 24.dp

    // ---------------- 播放页的几处固定比例 ----------------
    // 这些值原来是散在 NowPlayingScreen 里的字面量（1.34f / 0.16f / 0.10f / 420.dp）。
    // 提出来是因为它们互相约束：光晕要包住唱片、封面要淡到不抢字、compact 阈值要
    // 同时适配「有歌词」和「没歌词」两种高度，改一个不同步另外几个就会错位。

    /** 唱片外光晕相对唱片的直径倍数。 */
    const val DISC_HALO_SCALE = 1.34f

    /** 唱片外光晕中心的不透明度（向外渐变到全透明）。 */
    const val DISC_HALO_ALPHA = 0.16f

    /** 封面铺满全屏时的 alpha：给「这首歌」一点实体感，又不抢正文。 */
    const val STAGE_COVER_ALPHA = 0.10f

    /** 歌词页顶部压暗强度，保证高亮行压在最亮的极光上也够读。 */
    const val LYRIC_SCRIM_TOP = 0.30f

    /** 歌词页底部压暗强度（比顶部更重：底部是当前行滚动区）。 */
    const val LYRIC_SCRIM_BOTTOM = 0.42f

    /** 舞台高度低于此值时唱片缩小，避免和歌词/控制条抢地方。 */
    val compactStageHeight = 420.dp

    /**
     * 品牌三色渐变。**取用户当前选的调色板**（ColorPalettes.kt 里 8 套），
     * 这样霓虹系统不会和设置页的主题色打架——用户选"橙色"，进度条就是橙系，
     * 只是被拉到了霓虹饱和度。
     */
    fun brandGradient(themeColor: String = "blue"): List<Color> {
        val palette: AppPalette = paletteById(themeColor)
        // seed 提亮一档做起点，原始 seed 做中段，压暗做终点，保证三色可辨。
        val seed = palette.seed
        return listOf(
            lighten(seed, 0.42f),
            seed,
            darken(seed, 0.35f),
        )
    }

    /** 横向品牌渐变 Brush，供进度条与高亮条复用。 */
    fun brandBrush(themeColor: String = "blue"): Brush =
        Brush.horizontalGradient(brandGradient(themeColor))

    /**
     * 由封面取色得到的三团极光。没有封面时退回品牌三色，
     * 保证任何一首歌都不会出现灰屏或黑屏。
     *
     * @param primary 封面主色（ArtworkColors.schemeFor 的 primary）
     * @param secondary 封面暗色（同上的 secondary）
     * @param themeColor 用户选的主题色，用于补足第三团
     */
    fun auroraFrom(
        primary: Color,
        secondary: Color,
        themeColor: String = "blue",
    ): List<Color> {
        val brand = brandGradient(themeColor)
        return listOf(primary, secondary, brand.first())
    }

    /**
     * 发光用色：把色值压到很低的 alpha，靠"多圈叠加"伪造辉光，
     * 代替无法使用的 RenderEffect 模糊。
     *
     * 为什么不用 shadow/glow 库：Compose 的 `Modifier.shadow` 不发光只投影，
     * 真正的发光要么走 RenderEffect（已禁用），要么就是多画几层半透明圆。
     * 后者零依赖、零崩溃风险，代价是每帧多几次 drawCircle。
     */
    fun glow(color: Color, alpha: Float): Color =
        color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f))

    /** 按比例提亮（往白里混）。 */
    fun lighten(color: Color, amount: Float): Color {
        val a = amount.coerceIn(0f, 1f)
        return Color(
            red = color.red + (1f - color.red) * a,
            green = color.green + (1f - color.green) * a,
            blue = color.blue + (1f - color.blue) * a,
            alpha = color.alpha,
        )
    }

    /** 按比例压暗（往黑里混）。 */
    fun darken(color: Color, amount: Float): Color {
        val a = (1f - amount).coerceIn(0f, 1f)
        return Color(
            red = color.red * a,
            green = color.green * a,
            blue = color.blue * a,
            alpha = color.alpha,
        )
    }
}

/**
 * V6.0 动效 token。
 *
 * 所有时长集中在这里，是为了让 `NeonTokensTest` 能锁住两条约束：
 * 极光必须够慢（≥8s，ui-ux-pro-max 的 Aurora UI 明确要求 8-12s，
 * 更快会让人晕），开关类反馈必须够快（≤300ms，否则手感黏）。
 *
 * 实际播放时还要再过一遍系统的"移除动画"开关（见 `rememberMotionEnabled`）。
 */
object NeonTiming {
    /** 主极光漂移一圈的时长。落在 8-12s 窗口的中间偏慢。 */
    const val auroraMs = 11_000

    /** 次极光的时长，取与 11s 互质的 17s，避免两团同步（同步会看出周期性）。 */
    const val auroraSecondaryMs = 17_000

    /** 第三团的时长，与上两个都互质。 */
    const val auroraTertiaryMs = 23_000

    /** 播放键呼吸光晕的周期。 */
    const val pulseMs = 2_600

    /** 状态切换（颜色 / 图标）的默认时长。 */
    const val stateMs = 220

    /** 装饰性电平条的单个循环。 */
    const val barMs = 820

    /** 唱针落下/抬起的时长，与 RotatingArtwork 现有 300ms 对齐。 */
    const val tonearmMs = 300
}
