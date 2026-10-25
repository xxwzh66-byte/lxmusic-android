package com.lxmusic.android.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Round-trips real tag containers so the embedded-lyric reader is exercised on
 * bytes rather than on a live file system.
 */
class EmbeddedLyricsTest {

    private val utf8 = Charsets.UTF_8
    private val gbk: Charset = Charset.forName("GBK")

    // ---------------- helpers ----------------

    private fun id3v23Frame(id: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        val size = payload.size
        out.write((size shr 24) and 0xFF)
        out.write((size shr 16) and 0xFF)
        out.write((size shr 8) and 0xFF)
        out.write(size and 0xFF)
        out.write(byteArrayOf(0, 0)) // flags
        out.write(payload)
        return out.toByteArray()
    }

    private fun id3v23Tag(frames: List<ByteArray>): ByteArray {
        val body = frames.fold(ByteArrayOutputStream()) { acc, f -> acc.apply { write(f) } }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(byteArrayOf(3, 0, 0)) // v2.3.0
        val size = body.size
        out.write((size shr 21) and 0x7F)
        out.write((size shr 14) and 0x7F)
        out.write((size shr 7) and 0x7F)
        out.write(size and 0x7F)
        out.write(body)
        return out.toByteArray()
    }

    private fun usltPayload(text: String, charset: Charset, encodingByte: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodingByte)
        out.write("eng".toByteArray(Charsets.ISO_8859_1))
        out.write(0) // empty content descriptor
        out.write(text.toByteArray(charset))
        return out.toByteArray()
    }

    private fun vorbisComment(key: String, value: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        val vendor = "test".toByteArray(utf8)
        le32(vendor.size); out.write(vendor)
        le32(1)
        val entry = "$key=$value".toByteArray(utf8)
        le32(entry.size); out.write(entry)
        return out.toByteArray()
    }

    private fun flacWithComment(comment: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        out.write(0x84) // last block, type 4 (vorbis comment)
        out.write((comment.size shr 16) and 0xFF)
        out.write((comment.size shr 8) and 0xFF)
        out.write(comment.size and 0xFF)
        out.write(comment)
        return out.toByteArray()
    }

    private fun oggWithComment(comment: ByteArray, opus: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OggS".toByteArray(Charsets.ISO_8859_1))
        out.write(ByteArray(20))
        if (opus) {
            out.write("OpusTags".toByteArray(Charsets.ISO_8859_1))
        } else {
            out.write(byteArrayOf(3, 0x76, 0x6F, 0x72, 0x62, 0x69, 0x73)) // \x03vorbis
        }
        out.write(comment)
        return out.toByteArray()
    }

    /** `moov` > `©lyr` > `data` > UTF-8 payload, as written by real muxers. */
    private fun mp4WithLyrics(text: String): ByteArray {
        val payload = text.toByteArray(utf8)
        val data = ByteArrayOutputStream()
        data.write((12 + payload.size).toByteArray4()) // size + type + version/flags
        data.write("data".toByteArray(Charsets.ISO_8859_1))
        data.write(ByteArray(4)) // version + flags
        data.write(payload)

        val lyr = ByteArrayOutputStream()
        lyr.write((8 + data.size()).toByteArray4())
        lyr.write("\u00a9lyr".toByteArray(Charsets.ISO_8859_1))
        lyr.write(data.toByteArray())

        val moov = ByteArrayOutputStream()
        moov.write((8 + lyr.size()).toByteArray4())
        moov.write("moov".toByteArray(Charsets.ISO_8859_1))
        moov.write(lyr.toByteArray())

        // Real files start with an `ftyp` box; that is what the reader sniffs.
        val ftyp = ByteArrayOutputStream()
        ftyp.write(16.toByteArray4())
        ftyp.write("ftyp".toByteArray(Charsets.ISO_8859_1))
        ftyp.write("M4A ".toByteArray(Charsets.ISO_8859_1))
        ftyp.write(0.toByteArray4())

        return ftyp.toByteArray() + moov.toByteArray()
    }

    private fun Int.toByteArray4(): ByteArray = byteArrayOf(
        (this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte(),
    )

    private val LRC = "[00:01.00]first line\n[00:05.50]second line"

    // ---------------- ID3 ----------------

    @Test
    fun readsUtf8UsltFromId3v23() {
        val tag = id3v23Tag(listOf(id3v23Frame("USLT", usltPayload(LRC, utf8, 3))))
        assertEquals(LRC, EmbeddedLyrics.read(tag))
    }

    @Test
    fun readsLatin1UsltFromId3v23() {
        val text = "[00:01.00]caf\u00e9 line"
        val tag = id3v23Tag(listOf(id3v23Frame("USLT", usltPayload(text, Charsets.ISO_8859_1, 0))))
        assertEquals(text, EmbeddedLyrics.read(tag))
    }

    @Test
    fun readsGbkUsltThroughEncodingFallback() {
        val text = "[00:01.00]\u4e2d\u6587\u6b4c\u8bcd"
        val tag = id3v23Tag(listOf(id3v23Frame("USLT", usltPayload(text, gbk, 0))))
        assertEquals(text, EmbeddedLyrics.read(tag))
    }

    @Test
    fun readsTxxxLyricsFrame() {
        val payload = ByteArrayOutputStream().apply {
            write(3)
            write("LYRICS".toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(LRC.toByteArray(utf8))
        }.toByteArray()
        val tag = id3v23Tag(listOf(id3v23Frame("TXXX", payload)))
        assertEquals(LRC, EmbeddedLyrics.read(tag))
    }

    @Test
    fun skipsUnrelatedFrames() {
        val title = id3v23Frame("TIT2", byteArrayOf(3) + "Song".toByteArray(utf8))
        val tag = id3v23Tag(listOf(title))
        assertNull(EmbeddedLyrics.read(tag))
    }

    // ---------------- FLAC / Ogg / MP4 ----------------

    @Test
    fun readsVorbisCommentFromFlac() {
        val flac = flacWithComment(vorbisComment("LYRICS", LRC))
        assertEquals(LRC, EmbeddedLyrics.read(flac))
    }

    @Test
    fun readsVorbisCommentFromOgg() {
        val ogg = oggWithComment(vorbisComment("UNSYNCEDLYRICS", LRC), opus = false)
        assertEquals(LRC, EmbeddedLyrics.read(ogg))
    }

    @Test
    fun readsOpusTags() {
        val opus = oggWithComment(vorbisComment("LYRICS", LRC), opus = true)
        assertEquals(LRC, EmbeddedLyrics.read(opus))
    }

    @Test
    fun readsMp4LyricsAtom() {
        assertEquals(LRC, EmbeddedLyrics.read(mp4WithLyrics(LRC)))
    }

    // ---------------- negative cases ----------------

    @Test
    fun returnsNullForUnknownContainer() {
        assertNull(EmbeddedLyrics.read("not a song at all".toByteArray(utf8)))
    }

    @Test
    fun returnsNullForEmptyInput() {
        assertNull(EmbeddedLyrics.read(ByteArray(0)))
    }

    @Test
    fun returnsNullWhenNoLyricTagPresent() {
        val flac = flacWithComment(vorbisComment("ARTIST", "someone"))
        assertNull(EmbeddedLyrics.read(flac))
    }

    // ---------------- encoding helper ----------------

    @Test
    fun decodePrefersUtf8() {
        val text = "[00:01.00]\u6d4b\u8bd5"
        assertEquals(text, LyricEncoding.decode(text.toByteArray(utf8)))
    }

    @Test
    fun decodeFallsBackToGbk() {
        val text = "[00:01.00]\u4e2d\u6587\u6b4c\u8bcd\u6d4b\u8bd5"
        assertEquals(text, LyricEncoding.decode(text.toByteArray(gbk)))
    }

    @Test
    fun decodeStripsBom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "[00:01.00]hello".toByteArray(utf8)
        assertEquals("[00:01.00]hello", LyricEncoding.decode(bytes))
    }

    @Test
    fun stripBomLeavesCleanInputAlone() {
        val bytes = "abc".toByteArray(utf8)
        assertEquals(3, LyricEncoding.stripBom(bytes).size)
    }

    // ---------------- integration with the LRC parser ----------------

    @Test
    fun embeddedLyricParsesIntoTimedLines() {
        val tag = id3v23Tag(listOf(id3v23Frame("USLT", usltPayload(LRC, utf8, 3))))
        val lyric = LrcParser.parse(EmbeddedLyrics.read(tag))
        assertEquals(2, lyric.lines.size)
        assertEquals("first line", lyric.lines[0].text)
        assertEquals(1000L, lyric.lines[0].timeMs)
        assertEquals(5500L, lyric.lines[1].timeMs)
    }

    @Test
    fun gbkLyricSurvivesParser() {
        val text = "[00:02.00]\u6d4b\u8bd5\u6b4c\u8bcd"
        val tag = id3v23Tag(listOf(id3v23Frame("USLT", usltPayload(text, gbk, 0))))
        val lyric = LrcParser.parse(EmbeddedLyrics.read(tag))
        assertEquals(1, lyric.lines.size)
        assertTrue(lyric.lines[0].text.contains("\u6d4b\u8bd5"))
    }
}