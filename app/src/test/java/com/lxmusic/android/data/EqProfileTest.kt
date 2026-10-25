package com.lxmusic.android.data

import com.lxmusic.android.data.settings.EqProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V6.1: the equalizer profile is the only thing that makes a tuned curve survive
 * a restart — the audio effect itself is rebuilt flat on every new audio session.
 * These tests pin the encode/decode contract that the DataStore round-trip relies
 * on, including the "corrupt data must not crash the app" rule.
 */
class EqProfileTest {

    @Test
    fun flatIsTheIdentity() {
        val flat = EqProfile.FLAT
        assertTrue(!flat.enabled)
        assertNull(flat.preset)
        assertTrue(flat.bands.isEmpty())
        assertEquals(0, flat.bass)
        assertEquals(0, flat.virtualizer)
        assertEquals(0, flat.loudnessMb)
    }

    @Test
    fun bandsRoundTripThroughText() {
        val bands = listOf<Short>(300, -150, 0, 600, -900)
        assertEquals(bands, EqProfile.decodeBands(EqProfile.encodeBands(bands)))
    }

    @Test
    fun encodedBandsAreCommaSeparated() {
        assertEquals("1,2,3", EqProfile.encodeBands(listOf<Short>(1, 2, 3)))
    }

    @Test
    fun emptyBandsEncodeToEmptyText() {
        assertEquals("", EqProfile.encodeBands(emptyList()))
        assertTrue(EqProfile.decodeBands("").isEmpty())
        assertTrue(EqProfile.decodeBands(null).isEmpty())
    }

    @Test
    fun corruptEntriesAreDroppedNotThrown() {
        // "abc" and the overflowing number are dropped; the rest survives.
        assertEquals(
            listOf<Short>(100, -200),
            EqProfile.decodeBands("100, abc, -200, 999999999"),
        )
    }

    @Test
    fun whitespaceAroundEntriesIsTolerated() {
        assertEquals(listOf<Short>(5, 6), EqProfile.decodeBands(" 5 , 6 "))
    }

    @Test
    fun copyKeepsUnrelatedFields() {
        val p = EqProfile(enabled = true, preset = "Rock", bass = 400)
        val louder = p.copy(bass = 800)
        assertTrue(louder.enabled)
        assertEquals("Rock", louder.preset)
        assertEquals(800, louder.bass)
    }
}
