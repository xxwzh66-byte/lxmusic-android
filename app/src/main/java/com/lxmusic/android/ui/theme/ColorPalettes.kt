package com.lxmusic.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** A selectable app colour palette (V5-4: tinted scheme + brand gradient). */
data class AppPalette(
    val id: String,
    val label: String,
    val seed: Color,
    /** V5-4: 2-3 stop brand gradient for the swatch and accents. */
    val gradient: List<Color> = listOf(seed),
)

private fun C(rgb: Int) = Color(0xFF000000.toInt() or rgb)

private val blueLight = lightColorScheme(
    primary = C(0x1E88E5),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xE3EDF9),
    onPrimaryContainer = C(0x0E3D67),
    secondary = C(0x1E88E5),
    background = C(0xF3F6FC),
    surface = C(0xF3F6FC),
    surfaceVariant = C(0xE3EDF9),
    onSurfaceVariant = C(0x0E3D67),
)

private val blueDark = darkColorScheme(
    primary = C(0x6DB2EE),
    onPrimary = C(0x0E3D67),
    primaryContainer = C(0x0E3D67),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0x6DB2EE),
    background = C(0x141B26),
    surface = C(0x141B26),
    surfaceVariant = C(0x233043),
    onSurfaceVariant = C(0xB9C2CF),
)

private val purpleLight = lightColorScheme(
    primary = C(0x8E24AA),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xEEE5F3),
    onPrimaryContainer = C(0x4E145E),
    secondary = C(0x8E24AA),
    background = C(0xF7F3FA),
    surface = C(0xF7F3FA),
    surfaceVariant = C(0xEEE5F3),
    onSurfaceVariant = C(0x4E145E),
)

private val purpleDark = darkColorScheme(
    primary = C(0xB671C8),
    onPrimary = C(0x4E145E),
    primaryContainer = C(0x4E145E),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0xB671C8),
    background = C(0x1A1420),
    surface = C(0x1A1420),
    surfaceVariant = C(0x2E2338),
    onSurfaceVariant = C(0xB9C2CF),
)

private val greenLight = lightColorScheme(
    primary = C(0x2E7D32),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xE4F0E5),
    onPrimaryContainer = C(0x19451C),
    secondary = C(0x2E7D32),
    background = C(0xF3F8F4),
    surface = C(0xF3F8F4),
    surfaceVariant = C(0xE4F0E5),
    onSurfaceVariant = C(0x19451C),
)

private val greenDark = darkColorScheme(
    primary = C(0x77AA7A),
    onPrimary = C(0x19451C),
    primaryContainer = C(0x19451C),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0x77AA7A),
    background = C(0x131C14),
    surface = C(0x131C14),
    surfaceVariant = C(0x243326),
    onSurfaceVariant = C(0xB9C2CF),
)

private val orangeLight = lightColorScheme(
    primary = C(0xEF6C00),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xF7EADC),
    onPrimaryContainer = C(0x833B00),
    secondary = C(0xEF6C00),
    background = C(0xFAF5F0),
    surface = C(0xFAF5F0),
    surfaceVariant = C(0xF7EADC),
    onSurfaceVariant = C(0x833B00),
)

private val orangeDark = darkColorScheme(
    primary = C(0xF59F59),
    onPrimary = C(0x833B00),
    primaryContainer = C(0x833B00),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0xF59F59),
    background = C(0x1C1712),
    surface = C(0x1C1712),
    surfaceVariant = C(0x332A1F),
    onSurfaceVariant = C(0xB9C2CF),
)

private val pinkLight = lightColorScheme(
    primary = C(0xD81B60),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xF7E3EA),
    onPrimaryContainer = C(0x770F35),
    secondary = C(0xD81B60),
    background = C(0xFBF3F6),
    surface = C(0xFBF3F6),
    surfaceVariant = C(0xF7E3EA),
    onSurfaceVariant = C(0x770F35),
)

private val pinkDark = darkColorScheme(
    primary = C(0xE66B98),
    onPrimary = C(0x770F35),
    primaryContainer = C(0x770F35),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0xE66B98),
    background = C(0x1D1318),
    surface = C(0x1D1318),
    surfaceVariant = C(0x34242C),
    onSurfaceVariant = C(0xB9C2CF),
)

private val redLight = lightColorScheme(
    primary = C(0xE53935),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xF7E5E5),
    onPrimaryContainer = C(0x7E1F1D),
    secondary = C(0xE53935),
    background = C(0xFAF4F4),
    surface = C(0xFAF4F4),
    surfaceVariant = C(0xF7E5E5),
    onSurfaceVariant = C(0x7E1F1D),
)

private val redDark = darkColorScheme(
    primary = C(0xEE7E7C),
    onPrimary = C(0x7E1F1D),
    primaryContainer = C(0x7E1F1D),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0xEE7E7C),
    background = C(0x1C1313),
    surface = C(0x1C1313),
    surfaceVariant = C(0x332424),
    onSurfaceVariant = C(0xB9C2CF),
)

private val cyanLight = lightColorScheme(
    primary = C(0x00838F),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xE0EFF1),
    onPrimaryContainer = C(0x00484F),
    secondary = C(0x00838F),
    background = C(0xF1F7F8),
    surface = C(0xF1F7F8),
    surfaceVariant = C(0xE0EFF1),
    onSurfaceVariant = C(0x00484F),
)

private val cyanDark = darkColorScheme(
    primary = C(0x59AEB6),
    onPrimary = C(0x00484F),
    primaryContainer = C(0x00484F),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0x59AEB6),
    background = C(0x111A1C),
    surface = C(0x111A1C),
    surfaceVariant = C(0x203033),
    onSurfaceVariant = C(0xB9C2CF),
)

private val indigoLight = lightColorScheme(
    primary = C(0x3949AB),
    onPrimary = C(0xFFFFFF),
    primaryContainer = C(0xE6E9F5),
    onPrimaryContainer = C(0x1F285E),
    secondary = C(0x3949AB),
    background = C(0xF4F5FA),
    surface = C(0xF4F5FA),
    surfaceVariant = C(0xE6E9F5),
    onSurfaceVariant = C(0x1F285E),
)

private val indigoDark = darkColorScheme(
    primary = C(0x7E89C8),
    onPrimary = C(0x1F285E),
    primaryContainer = C(0x1F285E),
    onPrimaryContainer = C(0xEAF2FC),
    secondary = C(0x7E89C8),
    background = C(0x14161F),
    surface = C(0x14161F),
    surfaceVariant = C(0x262A3D),
    onSurfaceVariant = C(0xB9C2CF),
)

/** All built-in palettes, in display order. */
val AppPalettes: List<AppPalette> = listOf(
    AppPalette("blue", "蓝色", C(0x1E88E5), listOf(C(0x1E88E5), C(0x42A5F5), C(0x1565C0))),
    AppPalette("purple", "紫色", C(0x8E24AA), listOf(C(0x8E24AA), C(0xAB47BC), C(0x6A1B9A))),
    AppPalette("green", "绿色", C(0x2E7D32), listOf(C(0x2E7D32), C(0x43A047), C(0x1B5E20))),
    AppPalette("orange", "橙色", C(0xEF6C00), listOf(C(0xEF6C00), C(0xF57C00), C(0xE65100))),
    AppPalette("pink", "粉色", C(0xD81B60), listOf(C(0xD81B60), C(0xEC407A), C(0xAD1457))),
    AppPalette("red", "红色", C(0xE53935), listOf(C(0xE53935), C(0xEF5350), C(0xB71C1C))),
    AppPalette("cyan", "青色", C(0x00838F), listOf(C(0x00838F), C(0x00ACC1), C(0x006064))),
    AppPalette("indigo", "靛青", C(0x3949AB), listOf(C(0x3949AB), C(0x5C6BC0), C(0x283593))),
)

/** Resolves a palette id (falls back to the first palette). */
fun paletteById(id: String): AppPalette = AppPalettes.firstOrNull { it.id == id } ?: AppPalettes.first()

/** Light colour scheme for [id]. */
fun lightSchemeFor(id: String): ColorScheme = when (id) {
    "blue" -> blueLight
    "purple" -> purpleLight
    "green" -> greenLight
    "orange" -> orangeLight
    "pink" -> pinkLight
    "red" -> redLight
    "cyan" -> cyanLight
    "indigo" -> indigoLight
    else -> blueLight
}

/** Dark colour scheme for [id]. */
fun darkSchemeFor(id: String): ColorScheme = when (id) {
    "blue" -> blueDark
    "purple" -> purpleDark
    "green" -> greenDark
    "orange" -> orangeDark
    "pink" -> pinkDark
    "red" -> redDark
    "cyan" -> cyanDark
    "indigo" -> indigoDark
    else -> blueDark
}
