package com.lxmusic.android.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for the SAF scanner's file filters (T2). */
class SafMusicScannerTest {

    @Test
    fun extensionOfIsLowercased() {
        assertEquals("mp3", SafMusicScanner.extensionOf("Song.MP3"))
        assertEquals("flac", SafMusicScanner.extensionOf("a.flac"))
        assertEquals("", SafMusicScanner.extensionOf("noextension"))
    }

    @Test
    fun recognisesCommonAudioExtensions() {
        val names = listOf(
            "a.mp3", "a.flac", "a.m4a", "a.aac", "a.wav",
            "a.ogg", "a.opus", "a.wma", "a.ape", "a.mka",
        )
        for (n in names) assertTrue("$n should be audio", SafMusicScanner.isAudioFile(n, null))
    }

    @Test
    fun rejectsNonAudioExtensions() {
        val names = listOf("a.txt", "a.jpg", "a.pdf", "cover.png", "video.mkv")
        for (n in names) assertFalse("$n should not be audio", SafMusicScanner.isAudioFile(n, null))
    }

    @Test
    fun mimeTypeWinsOverExtension() {
        // An audio mime accepts a file even with an unknown extension...
        assertTrue(SafMusicScanner.isAudioFile("weird.bin", "audio/mpeg"))
        // ...and a known audio extension is accepted even with a non-audio mime,
        // because SAF providers frequently report "application/octet-stream".
        assertTrue(SafMusicScanner.isAudioFile("a.mp3", "application/octet-stream"))
        // Neither mime nor extension says audio -> rejected.
        assertFalse(SafMusicScanner.isAudioFile("clip.mkv", "video/x-matroska"))
    }

    @Test
    fun extensionMatchingIsCaseInsensitive() {
        assertTrue(SafMusicScanner.isAudioFile("SONG.MP3", null))
        assertTrue(SafMusicScanner.isAudioFile("Song.FlAc", null))
    }
}
