package com.lxmusic.android.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * V5.2-1: the OLED stage tokens must stay readable. WCAG AA body text needs
 * 4.5:1 against the background; large text still needs 3:1.
 */
class OledTokensTest {

    private fun luminance(c: androidx.compose.ui.graphics.Color): Double {
        fun channel(x: Float): Double {
            val v = x.coerceIn(0f, 1f).toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun primaryText_meetsAA_onBackground() {
        val r = contrast(OledTokens.onStage, OledTokens.background)
        assertTrue("onStage/background was $r", r >= 4.5)
    }

    @Test
    fun mutedText_meetsAA_onBackground() {
        val r = contrast(OledTokens.onStageMuted, OledTokens.background)
        assertTrue("onStageMuted/background was $r", r >= 4.5)
    }

    @Test
    fun foreground_meetsAA_onCard() {
        val r = contrast(OledTokens.foreground, OledTokens.card)
        assertTrue("foreground/card was $r", r >= 4.5)
    }

    @Test
    fun mutedForeground_meetsAA_onCard() {
        val r = contrast(OledTokens.mutedForeground, OledTokens.card)
        assertTrue("mutedForeground/card was $r", r >= 4.5)
    }

    @Test
    fun surfacesAreDistinct() {
        assertTrue(OledTokens.card != OledTokens.background)
        assertTrue(OledTokens.muted != OledTokens.card)
        assertTrue(OledTokens.border != OledTokens.background)
    }

    @Test
    fun spacingIsOnTheFourDpGrid() {
        listOf(
            OledTokens.spaceXs, OledTokens.spaceSm, OledTokens.spaceMd,
            OledTokens.spaceLg, OledTokens.spaceXl, OledTokens.screenMargin,
        ).forEach { d ->
            assertTrue("spacing $d off grid", d.value % 4f == 0f)
        }
    }
}