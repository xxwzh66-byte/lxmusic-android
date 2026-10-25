package com.lxmusic.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the static colour palettes (T6). */
class ThemePaletteTest {

    @Test
    fun thereAreEightPalettes() {
        assertEquals(8, AppPalettes.size)
    }

    @Test
    fun everyPaletteHasUniqueIdAndLabel() {
        assertEquals(AppPalettes.size, AppPalettes.map { it.id }.toSet().size)
        assertEquals(AppPalettes.size, AppPalettes.map { it.label }.toSet().size)
        for (p in AppPalettes) {
            assertTrue(p.id.isNotBlank())
            assertTrue(p.label.isNotBlank())
        }
    }

    @Test
    fun paletteByIdFindsEveryPalette() {
        for (p in AppPalettes) assertNotNull(paletteById(p.id))
    }

    @Test
    fun unknownIdFallsBackToBlue() {
        assertEquals(paletteById("blue"), paletteById("does-not-exist"))
    }

    @Test
    fun lightAndDarkSchemesDiffer() {
        for (p in AppPalettes) {
            val light = lightSchemeFor(p.id)
            val dark = darkSchemeFor(p.id)
            // Light and dark tonal variants must be visibly different.
            assertNotEquals(light.background, dark.background)
            assertNotEquals(light.surface, dark.surface)
            assertNotEquals(light.primary, dark.primary)
            // The seed still identifies the palette family.
            assertTrue(p.seed.alpha == 1f)
        }
    }

    @Test
    fun everySchemeHasUsableContrastingText() {
        for (p in AppPalettes) {
            for (scheme in listOf(lightSchemeFor(p.id), darkSchemeFor(p.id))) {
                assertNotEquals(scheme.onPrimary, scheme.primary)
                assertNotEquals(scheme.onSurface, scheme.surface)
                assertNotEquals(scheme.onBackground, scheme.background)
            }
        }
    }

    @Test
    fun everyPaletteHasMultiStopGradient() {
        for (p in AppPalettes) {
            assertTrue(p.gradient.size >= 2)
            assertEquals(p.seed, p.gradient.first())
        }
    }

    @Test
    fun lightSchemesAreTintedNotNeutralGray() {
        // V5-4: surface must carry the brand hue (blue channel differs from red on blue family etc.);
        // simplest robust check: light surface is no longer pure white / neutral #FFFFFF.
        for (p in AppPalettes) {
            val surface = lightSchemeFor(p.id).surface
            val v: ULong = surface.value
            val red = ((v shr 32) and 0xFFuL).toInt()
            val green = ((v shr 16) and 0xFFuL).toInt()
            val blue = (v and 0xFFuL).toInt()
            assertFalse((red == green) && (green == blue)) // not neutral gray
        }
    }
}
