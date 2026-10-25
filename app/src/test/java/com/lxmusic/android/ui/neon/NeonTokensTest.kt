package com.lxmusic.android.ui.neon

import androidx.compose.ui.graphics.Color
import com.lxmusic.android.ui.theme.AppPalettes
import com.lxmusic.android.ui.theme.OledTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * V6.0: NeonTokens 的三条自证。
 *
 * 为什么这些必须被测：霓虹系统最容易被后续改动破坏的地方不是"好不好看"，
 * 而是**可读性红线**（极光太亮压垮正文）和**动效节律**（太快的渐变会让人晕）。
 * 这两件事在截图上都看不出来，只能靠常量级的断言守住。
 */
class NeonTokensTest {

    private fun luminance(c: Color): Double {
        fun channel(x: Float): Double {
            val v = x.coerceIn(0f, 1f).toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** 把一团极光按峰值叠到兜底底色上，模拟最坏情况：正文正好压在光心。 */
    private fun overAurora(peak: Float, aurora: Color): Color {
        val base = NeonTokens.ink
        return Color(
            red = base.red + (aurora.red - base.red) * aurora.alpha * peak,
            green = base.green + (aurora.green - base.green) * aurora.alpha * peak,
            blue = base.blue + (aurora.blue - base.blue) * aurora.alpha * peak,
        )
    }

    @Test
    fun auroraPeakNeverPushesBodyTextBelowAA() {
        // 最坏情况不是"平均极光"，而是正文正好落在某团光的圆心上。
        // 8 套调色板全部跑一遍，任何一套都不许破 4.5:1。
        AppPalettes.forEach { palette ->
            NeonTokens.brandGradient(palette.id).forEach { aurora ->
                val bg = overAurora(NeonTokens.AURORA_PEAK, aurora)
                val r = contrast(OledTokens.foreground, bg)
                assertTrue(
                    "palette=${palette.id} aurora=$aurora contrast=$r",
                    r >= 4.5,
                )
            }
        }
    }

    @Test
    fun peakIsPinnedToTheDocumentedValue() {
        // 注释说 0.34 是红线；改大任何一个数都要同步改注释与这条断言。
        assertEquals(0.34f, NeonTokens.AURORA_PEAK, 0.0001f)
    }

    @Test
    fun textTokensDelegateToOled() {
        // 霓虹层不允许自建文字色：一旦分叉，OledTokensTest 锁的 4.5:1 就管不到它了。
        assertEquals(OledTokens.foreground, NeonTokens.text)
        assertEquals(OledTokens.mutedForeground, NeonTokens.textMuted)
        assertEquals(OledTokens.destructive, NeonTokens.destructive)
    }

    @Test
    fun brandGradientHasThreeDistinctStops() {
        AppPalettes.forEach { palette ->
            val g = NeonTokens.brandGradient(palette.id)
            assertEquals("palette=${palette.id}", 3, g.size)
            // 起点必须比终点亮：进度条从左到右是"生长"的方向。
            val l0 = luminance(g.first())
            val l2 = luminance(g.last())
            assertTrue("palette=${palette.id} start=$l0 end=$l2", l0 > l2)
            // 三段之间要有可分辨的差，否则画出来是一条纯色带。
            assertTrue("palette=${palette.id}", abs(l0 - luminance(g[1])) > 0.01)
            assertTrue("palette=${palette.id}", abs(luminance(g[1]) - l2) > 0.01)
        }
    }

    @Test
    fun unknownThemeColorFallsBackToBlue() {
        // 与 ThemePaletteTest 的 fallback 约定保持一致。
        assertEquals(NeonTokens.brandGradient("blue"), NeonTokens.brandGradient("not-a-palette"))
    }

    @Test
    fun lightenAndDarkenMoveTowardsTheEnds() {
        val base = Color(0xFF3366CC)
        assertTrue(luminance(NeonTokens.lighten(base, 0.5f)) > luminance(base))
        assertTrue(luminance(NeonTokens.darken(base, 0.5f)) < luminance(base))
        // 全部压到 0 或提满 1 时不能溢出。
        assertEquals(1f, NeonTokens.lighten(base, 1f).red, 0.0001f)
        assertEquals(0f, NeonTokens.darken(base, 1f).red, 0.0001f)
    }

    @Test
    fun auroraIsSlowAndFeedbackIsFast() {
        // ui-ux-pro-max 的 Aurora UI：流动渐变 8-12s，更快会晕。
        assertTrue("aurora=${NeonTiming.auroraMs}", NeonTiming.auroraMs >= 8_000)
        assertTrue("aurora=${NeonTiming.auroraMs}", NeonTiming.auroraMs <= 12_000)
        // 开关类反馈必须够快，否则手感黏。
        assertTrue("state=${NeonTiming.stateMs}", NeonTiming.stateMs <= 300)
        assertTrue("tonearm=${NeonTiming.tonearmMs}", NeonTiming.tonearmMs <= 300)
    }

    @Test
    fun auroraPeriodsDoNotSync() {
        // 三团同周期会看出明显的整体呼吸（一眼假）。要求两两之间有足够差。
        val periods = listOf(
            NeonTiming.auroraMs,
            NeonTiming.auroraSecondaryMs,
            NeonTiming.auroraTertiaryMs,
        )
        periods.forEachIndexed { i, a ->
            periods.drop(i + 1).forEach { b ->
                assertTrue("$a vs $b too close", abs(a - b) >= 3_000)
            }
        }
        assertEquals("periods must be distinct", 3, periods.toSet().size)
    }
}
