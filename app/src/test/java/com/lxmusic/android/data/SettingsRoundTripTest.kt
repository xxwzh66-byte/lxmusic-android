package com.lxmusic.android.data

import androidx.datastore.preferences.core.emptyPreferences
import com.lxmusic.android.data.settings.AppSettings
import com.lxmusic.android.data.settings.EqProfile
import com.lxmusic.android.data.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings keys, exercised as a write/read round trip.
 *
 * V6.2: `SettingsTest.defaultsAreSane` only ever proves that a *blank*
 * Preferences store maps onto the defaults. Every key could be spelled
 * differently on the write side and on the read side and no test would notice -
 * the field would simply always come back as its default. That is exactly the
 * shape of the "the equalizer does not survive a restart" report, so the round
 * trip gets its own test class.
 */
class SettingsRoundTripTest {

    @Test
    fun anEmptyStoreReadsBackEveryDefault() {
        val s = SettingsStore.readSettings(emptyPreferences())
        assertEquals(AppSettings(), s)
    }

    @Test
    fun theEqualizerProfileSurvivesAWriteAndRead() {
        val saved = EqProfile(
            enabled = true,
            preset = "Rock",
            bands = listOf<Short>(300, -150, 0, 600, -900),
            bass = 400,
            virtualizer = 250,
            loudnessMb = 900,
        )
        val prefs = emptyPreferences().toMutablePreferences().also {
            SettingsStore.writeEq(it, saved)
        }
        assertEquals(saved, SettingsStore.readSettings(prefs).eq)
    }

    @Test
    fun aFlatEqualizerRoundTripsToTheSameValue() {
        val prefs = emptyPreferences().toMutablePreferences().also {
            SettingsStore.writeEq(it, EqProfile.FLAT)
        }
        assertEquals(EqProfile.FLAT, SettingsStore.readSettings(prefs).eq)
    }

    @Test
    fun anEmptyPresetNameIsNotResurrectedAsBlank() {
        // setEq stores null as "", so the reader must not hand back a blank
        // string: the UI shows the preset name and "" would render an empty chip.
        val prefs = emptyPreferences().toMutablePreferences().also {
            SettingsStore.writeEq(it, EqProfile(enabled = false, preset = null))
        }
        assertNull(SettingsStore.readSettings(prefs).eq.preset)
    }

    @Test
    fun corruptBandTextFallsBackToNoBandsInsteadOfThrowing() {
        val prefs = emptyPreferences().toMutablePreferences()
        prefs[SettingsStore.KEY_EQ_BANDS] = "not,a,number"
        val eq = SettingsStore.readSettings(prefs).eq
        assertTrue(eq.bands.isEmpty())
        // ... and the rest of the profile still reads back.
        assertFalse(eq.enabled)
    }

    @Test
    fun anUnknownEnumNameFallsBackToTheDefaultRatherThanCrashing() {
        val prefs = emptyPreferences().toMutablePreferences()
        prefs[SettingsStore.KEY_THEME_MODE] = "SEPIA"
        prefs[SettingsStore.KEY_LIST_STYLE] = "HUGE"
        prefs[SettingsStore.KEY_QUALITY] = "lossless"
        val s = SettingsStore.readSettings(prefs)
        assertEquals(AppSettings().themeMode, s.themeMode)
        assertEquals(AppSettings().listStyle, s.listStyle)
        assertEquals(AppSettings().quality, s.quality)
    }

    @Test
    fun anUnknownRepeatModeFallsBackToOff() {
        val prefs = emptyPreferences().toMutablePreferences()
        prefs[SettingsStore.KEY_REPEAT_MODE] = "SHUFFLE_ALL"
        assertEquals("OFF", SettingsStore.readSettings(prefs).repeatMode)
    }

    @Test
    fun theLastPlayedPointerRoundTrips() {
        val prefs = emptyPreferences().toMutablePreferences()
        prefs[SettingsStore.KEY_LAST_PLAYED_ID] = "track-42"
        prefs[SettingsStore.KEY_LAST_PLAYED_POS] = 91_500L
        val s = SettingsStore.readSettings(prefs)
        assertEquals("track-42", s.lastPlayedId)
        assertEquals(91_500L, s.lastPlayedPosMs)
    }

    @Test
    fun playbackModesRoundTrip() {
        val prefs = emptyPreferences().toMutablePreferences()
        prefs[SettingsStore.KEY_REPEAT_MODE] = "ONE"
        prefs[SettingsStore.KEY_SHUFFLE] = true
        val s = SettingsStore.readSettings(prefs)
        assertEquals("ONE", s.repeatMode)
        assertTrue(s.shuffle)
    }
}
