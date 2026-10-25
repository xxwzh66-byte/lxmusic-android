package com.lxmusic.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lxmusic.android.data.settings.ThemeMode

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * App theme. Dynamic (Material You) colour is intentionally not supported: the
 * palette is always the one picked in Settings so the look stays consistent
 * across devices.
 */
@Composable
fun JiJianTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeColor: String = "blue",
    content: @Composable () -> Unit,
) {
    val dark = resolveDark(themeMode)
    val scheme = remember(dark, themeColor) {
        if (dark) darkSchemeFor(themeColor) else lightSchemeFor(themeColor)
    }

    // T-nav: keep status/navigation-bar icon contrast in sync with the theme
    // even when the in-app mode differs from the system mode (dark icons on a
    // dark background are invisible otherwise).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}