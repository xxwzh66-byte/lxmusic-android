package com.lxmusic.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * V5.2-1: OLED dark design-system tokens (from ui-ux-pro-max "Dark Mode (OLED)").
 *
 * The now-playing screen is intentionally always dark (its background is a
 * colour-scrim gradient over the artwork), so it uses these semantic tokens
 * instead of `colorScheme.onSurface`, which turns dark in light mode and would
 * become unreadable. Brand emphasis still comes from `colorScheme.primary`.
 */
object OledTokens {
    val background = Color(0xFF0F0F23)
    val foreground = Color(0xFFF8FAFC)
    val card = Color(0xFF1B1B30)
    val muted = Color(0xFF27273B)
    val mutedForeground = Color(0xFF94A3B8)
    val border = Color(0xFF312E81)
    val accent = Color(0xFF22C55E)
    val destructive = Color(0xFFEF4444)

    /** Secondary text on the dark player stage. */
    val onStageMuted: Color get() = mutedForeground

    /** Primary text on the dark player stage. */
    val onStage: Color get() = foreground

    // ---- spacing (4dp grid, no magic numbers) ----
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 20.dp
    val screenMargin = 16.dp
    val radiusSm = 6.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp

}

