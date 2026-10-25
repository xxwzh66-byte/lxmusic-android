package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Round-trip tests for the Room cache mapping (C2). */
class LocalTrackMapperTest {

    private val music = MusicInfo(
        id = "saf_abc",
        source = "local",
        songmid = "primary:Music/a.mp3",
        name = "\u6674\u5929",
        singer = "\u5468\u6770\u4f26",
        albumName = "\u53f6\u60e0\u7f8e",
        interval = "04:29",
        intervalSeconds = 269,
        img = "/cache/covers/saf_abc.jpg",
        localPath = "content://com.android.externalstorage.documents/tree/x",
        localDurationMs = 269_000L,
        localAlbumId = "\u53f6\u60e0\u7f8e",
        dateAdded = 1_700_000_000_000L,
        extra = mapOf(
            "uri" to "content://com.android.externalstorage.documents/tree/x",
            "documentId" to "primary:Music/a.mp3",
            "origin" to "folder",
            "lastModified" to "123456",
            "size" to "7890",
        ),
    )

    @Test
    fun entityKeepsAllFields() {
        val e = LocalTrackMapper.toEntity(music, "folder")
        assertEquals("saf_abc", e.id)
        assertEquals("primary:Music/a.mp3", e.documentId)
        assertEquals("\u6674\u5929", e.title)
        assertEquals(269_000L, e.durationMs)
        assertEquals(123456L, e.lastModified)
        assertEquals(7890L, e.size)
        assertEquals("folder", e.origin)
        assertEquals("/cache/covers/saf_abc.jpg", e.coverPath)
    }

    @Test
    fun musicSurvivesRoundTrip() {
        val back = LocalTrackMapper.toMusic(LocalTrackMapper.toEntity(music, "folder"))
        assertEquals(music.id, back.id)
        assertEquals(music.name, back.name)
        assertEquals(music.singer, back.singer)
        assertEquals(music.albumName, back.albumName)
        assertEquals(music.localDurationMs, back.localDurationMs)
        assertEquals(music.localPath, back.localPath)
        assertEquals("local", back.source)
    }

    @Test
    fun intervalIsFormattedFromDuration() {
        val back = LocalTrackMapper.toMusic(LocalTrackMapper.toEntity(music, "folder"))
        assertEquals("04:29", back.interval)
        assertEquals(269, back.intervalSeconds)
    }

    @Test
    fun zeroDurationFormatsAsZero() {
        val t = music.copy(localDurationMs = 0L)
        val back = LocalTrackMapper.toMusic(LocalTrackMapper.toEntity(t, "manual"))
        assertEquals("00:00", back.interval)
        assertEquals("manual", back.extra["origin"])
    }
}
