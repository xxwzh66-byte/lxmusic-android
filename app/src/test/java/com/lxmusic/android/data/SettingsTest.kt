package com.lxmusic.android.data

import com.lxmusic.android.data.settings.AppSettings
import com.lxmusic.android.data.settings.AudioQuality
import com.lxmusic.android.data.settings.EqProfile
import com.lxmusic.android.data.settings.ListStyle
import com.lxmusic.android.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for the settings model (C1). */
class SettingsTest {

    @Test
    fun themeModeHasThreeOptions() {
        assertEquals(3, ThemeMode.entries.size)
        assertTrue(ThemeMode.entries.contains(ThemeMode.SYSTEM))
    }

    @Test
    fun listStyleLabelsAreNonBlank() {
        for (s in ListStyle.entries) assertTrue(s.label.isNotBlank())
        assertEquals(3, ListStyle.entries.size)
    }

    @Test
    fun audioQualityOptionsAndLabels() {
        assertEquals(3, AudioQuality.entries.size)
        assertEquals("320k", AudioQuality.Q320.label)
    }

    @Test
    fun defaultsAreSane() {
        val d = AppSettings()
        assertEquals(ThemeMode.SYSTEM, d.themeMode)
        assertEquals("blue", d.themeColor)
        assertEquals(ListStyle.STANDARD, d.listStyle)
        assertTrue(d.scanFolders.isEmpty())
        assertTrue(d.manualUris.isEmpty())
        assertTrue(d.autoScanOnStart)
        assertTrue(d.readEmbeddedCover)
        assertEquals(AudioQuality.Q320, d.quality)
        assertTrue(d.recordHistory)
        // V5.3.6: independent of recordHistory, defaults on
        assertTrue(d.recordStats)
        assertTrue(d.pauseOnHeadsetUnplug)
        assertEquals(1.0f, d.lyricFontScale, 0.001f)
        assertEquals(0, d.lyricOffsetMs)
        assertTrue(d.preferLrcFile)
        // V6.1: a fresh install ships a flat equalizer, and the field is a plain
        // preference (it is replayed manually after every audio-session attach).
        assertEquals(EqProfile.FLAT, d.eq)
    }

    @Test
    fun copyPreservesUnchangedFields() {
        val d = AppSettings().copy(themeMode = ThemeMode.DARK, lyricOffsetMs = 250)
        assertEquals(ThemeMode.DARK, d.themeMode)
        assertEquals(250, d.lyricOffsetMs)
        assertEquals("blue", d.themeColor)
        assertFalse(d.scanFolders.contains("x"))
    }
}
