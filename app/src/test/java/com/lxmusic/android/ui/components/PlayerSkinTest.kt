package com.lxmusic.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V4-1: skin convergence — legacy skin values must normalize to VINYL and
 * [PlayerSkin.next] must toggle between exactly the two surviving skins.
 */
class PlayerSkinTest {

    @Test
    fun `legacy skin values normalize to vinyl`() {
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from("TAPE"))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from("WAVE"))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from("MINIMAL"))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from("CARD"))
    }

    @Test
    fun `unknown and null values fall back to vinyl`() {
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from(null))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from(""))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from("nope"))
    }

    @Test
    fun `surviving skins still parse`() {
        assertEquals(PlayerSkin.VINYL, PlayerSkin.from("VINYL"))
        assertEquals(PlayerSkin.LYRICS, PlayerSkin.from("LYRICS"))
    }

    @Test
    fun `next toggles between the two skins`() {
        assertTrue(PlayerSkin.entries.size == 2)
        assertEquals(PlayerSkin.LYRICS, PlayerSkin.next(PlayerSkin.VINYL))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.next(PlayerSkin.LYRICS))
    }
}
