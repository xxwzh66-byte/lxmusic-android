package com.lxmusic.android.lyric

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes lyric text found on disk or inside audio tags.
 *
 * UTF-8 is tried first (strict), then GBK, so both modern and legacy
 * Chinese lyric files load correctly. A leading BOM is always stripped.
 */
object LyricEncoding {

    private val gbk: Charset? = runCatching { Charset.forName("GBK") }.getOrNull()

    /** Strips a UTF-8 / UTF-16 byte-order mark. */
    fun stripBom(bytes: ByteArray): ByteArray {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return bytes.copyOfRange(3, bytes.size)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return bytes.copyOfRange(2, bytes.size)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return bytes.copyOfRange(2, bytes.size)
        return bytes
    }

    /** UTF-8 first, GBK as fallback. */
    fun decode(bytes: ByteArray): String {
        val b = stripBom(bytes)
        if (b.isEmpty()) return ""
        try {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b))
                .toString()
        } catch (_: Exception) {
            // fall through to GBK
        }
        val cs = gbk
        if (cs != null) {
            // Strict decode: many old tags claim ISO-8859-1 while actually holding
            // GBK, but a genuine Latin-1 byte such as 0xE9 followed by a space is
            // not valid GBK and must fall through to the Latin-1 branch below.
            val decoded = runCatching {
                cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b))
                    .toString()
            }.getOrNull()
            if (decoded != null) return decoded
        }
        return String(b, Charsets.ISO_8859_1)
    }
}
