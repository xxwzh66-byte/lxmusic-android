package com.lxmusic.android.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies LRC parsing: timestamps, translations, and binary-search lookup. */
class LrcParserTest {

    @Test
    fun parsesBasicLrc() {
        val raw = """
            [ti:Test]
            [00:00.00]第一行
            [00:12.50]第二行
            [00:25.00]第三行
        """.trimIndent()
        val lyric = LrcParser.parse(raw)
        assertEquals(3, lyric.lines.size)
        assertEquals(0L, lyric.lines[0].timeMs)
        assertEquals(12_500L, lyric.lines[1].timeMs)
        assertEquals(25_000L, lyric.lines[2].timeMs)
        assertEquals("第二行", lyric.lines[1].text)
    }

    @Test
    fun handlesMultipleTimestampsPerLine() {
        val raw = "[00:01.00][00:31.00]副歌"
        val lyric = LrcParser.parse(raw)
        assertEquals(2, lyric.lines.size)
        assertEquals(1_000L, lyric.lines[0].timeMs)
        assertEquals(31_000L, lyric.lines[1].timeMs)
    }

    @Test
    fun detectsTranslationByTimestamp() {
        val raw = """
            [00:10.00]Hello world
            [00:10.00]你好世界
            [00:20.00]Goodbye
            [00:20.00]再见
        """.trimIndent()
        val lyric = LrcParser.parse(raw)
        assertEquals(2, lyric.lines.size)
        assertEquals(2, lyric.translation.size)
        assertEquals("你好世界", lyric.translationFor(0))
        assertEquals("再见", lyric.translationFor(1))
    }

    @Test
    fun indexAtFindsActiveLine() {
        val raw = """
            [00:00.00]A
            [00:10.00]B
            [00:20.00]C
        """.trimIndent()
        val lyric = LrcParser.parse(raw)
        assertEquals(0, lyric.indexAt(0))
        assertEquals(0, lyric.indexAt(9_999))
        assertEquals(1, lyric.indexAt(10_000))
        assertEquals(2, lyric.indexAt(99_000))
    }

    @Test
    fun emptyInputYieldsEmptyLyric() {
        assertTrue(LrcParser.parse(null).isEmpty)
        assertTrue(LrcParser.parse("").isEmpty)
    }

    @Test
    fun plainTextWithoutTagsStillParses() {
        // Embedded USLT lyrics are often plain text: keep the lines, evenly paced.
        val lyric = LrcParser.parse("no timestamps here")
        assertFalse(lyric.isEmpty)
        assertEquals("no timestamps here", lyric.lines[0].text)
        assertEquals(0L, lyric.lines[0].timeMs)
    }

    @Test
    fun plainTextMultiLineAreSequential() {
        val lyric = LrcParser.parse("first\n\nsecond\n[garbage tag]\nthird")
        assertEquals(3, lyric.lines.size)
        assertEquals("first", lyric.lines[0].text)
        assertEquals("second", lyric.lines[1].text)
        assertEquals("third", lyric.lines[2].text)
        assertEquals(0L, lyric.lines[0].timeMs)
        assertEquals(8000L, lyric.lines[2].timeMs)
    }

    @Test
    fun parsesVariousFractionLengths() {
        val raw = "[00:01.5]a\n[00:02.25]b\n[00:03.125]c"
        val lyric = LrcParser.parse(raw)
        assertEquals(1_500L, lyric.lines[0].timeMs)
        assertEquals(2_250L, lyric.lines[1].timeMs)
        assertEquals(3_125L, lyric.lines[2].timeMs)
    }
}