package com.lxmusic.android.lyric

/**
 * Best-effort reader for lyrics stored inside audio tags.
 *
 * [android.media.MediaMetadataRetriever] exposes no lyrics key, so the
 * containers are parsed directly:
 *  - ID3v2 `USLT` / `ULT` / `TXXX` frames (mp3),
 *  - Vorbis comments `LYRICS` / `UNSYNCEDLYRICS` (flac, ogg, opus),
 *  - the iTunes `©lyr` atom (m4a, mp4).
 */
object EmbeddedLyrics {

    /** Returns the embedded lyric text, or null when the tag is absent. */
    fun read(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        return runCatching {
            when {
                bytes.startsWithAscii("ID3") -> readId3(bytes)
                bytes.startsWithAscii("fLaC") -> readFlac(bytes)
                bytes.startsWithAscii("OggS") -> readOgg(bytes)
                asciiOf(bytes, 4, 4) == "ftyp" -> readMp4(bytes)
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // ---------------- ID3v2 ----------------

    private fun readId3(raw: ByteArray): String? {
        var b = raw
        val major = b[3].toInt() and 0xFF
        val flags = b[5].toInt() and 0xFF
        if (flags and 0x80 != 0) b = deUnsynchronise(b)
        val end = minOf(b.size, 10 + syncSafe(b, 6))
        var pos = 10
        if (flags and 0x40 != 0) {
            pos += if (major >= 4) syncSafe(b, pos) else beInt(b, pos) + 4
        }
        val idLen = if (major == 2) 3 else 4
        val headerLen = if (major == 2) 6 else 10
        while (pos + headerLen <= end) {
            val id = asciiOf(b, pos, idLen)
            if (id.isBlank()) break
            val frameSize = when {
                major == 2 -> be24(b, pos + 3)
                major >= 4 -> syncSafe(b, pos + 4)
                else -> beInt(b, pos + 4)
            }
            if (frameSize <= 0 || pos + headerLen + frameSize > b.size) break
            val body = b.copyOfRange(pos + headerLen, pos + headerLen + frameSize)
            when (id) {
                "USLT", "ULT" -> decodeUslt(body)?.let { if (it.isNotBlank()) return it }
                "TXXX" -> decodeTxxx(body)?.let { if (it.isNotBlank()) return it }
            }
            pos += headerLen + frameSize
        }
        return null
    }

    private fun deUnsynchronise(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            val v = b[i].toInt() and 0xFF
            out.write(v)
            if (v == 0xFF && i + 1 < b.size && (b[i + 1].toInt() and 0xFF) == 0x00) i++
            i++
        }
        return out.toByteArray()
    }

    private fun decodeUslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val enc = body[0].toInt() and 0xFF
        val wide = enc == 1 || enc == 2
        val start = terminatorAt(body, 4, wide) + if (wide) 2 else 1
        if (start >= body.size) return null
        return decodeId3(body, start, body.size - start, enc)
    }

    private fun decodeTxxx(body: ByteArray): String? {
        if (body.size < 3) return null
        val enc = body[0].toInt() and 0xFF
        val wide = enc == 1 || enc == 2
        val descEnd = terminatorAt(body, 1, wide)
        val desc = decodeId3(body, 1, (descEnd - 1).coerceAtLeast(0), enc)
        if (!desc.uppercase().contains("LYRIC")) return null
        val start = descEnd + if (wide) 2 else 1
        return decodeId3(body, start, body.size - start, enc)
    }

    private fun decodeId3(b: ByteArray, off: Int, len: Int, enc: Int): String {
        if (off < 0 || len <= 0 || off >= b.size) return ""
        val slice = b.copyOfRange(off, minOf(b.size, off + len))
        val text = when (enc) {
            1 -> String(slice, Charsets.UTF_16)
            2 -> String(slice, Charsets.UTF_16BE)
            3 -> String(slice, Charsets.UTF_8)
            else -> LyricEncoding.decode(slice)
        }
        return text.trim { it == '\u0000' || it == '\uFEFF' }
    }

    // ---------------- FLAC / Vorbis ----------------

    private fun readFlac(b: ByteArray): String? {
        var pos = 4
        while (pos + 4 <= b.size) {
            val header = b[pos].toInt() and 0xFF
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val len = be24(b, pos + 1)
            if (type == 4) return parseVorbisComment(b, pos + 4, len)
            if (last) break
            pos += 4 + len
        }
        return null
    }

    private fun readOgg(b: ByteArray): String? {
        val vorbis = indexOf(
            b,
            byteArrayOf(3, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(),
                'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte()),
        )
        if (vorbis >= 0) return parseVorbisComment(b, vorbis + 7, b.size - vorbis - 7)
        val opus = indexOf(b, asciiBytes("OpusTags"))
        if (opus >= 0) return parseVorbisComment(b, opus + 8, b.size - opus - 8)
        return null
    }

    private fun parseVorbisComment(b: ByteArray, start: Int, len: Int): String? {
        val end = minOf(b.size, start + len)
        if (start + 8 > end) return null
        var pos = start
        val vendorLen = leInt(b, pos)
        if (vendorLen < 0 || vendorLen > end) return null
        pos += 4 + vendorLen
        if (pos + 4 > end) return null
        val count = leInt(b, pos)
        pos += 4
        if (count < 0 || count > 4096) return null
        repeat(count) {
            if (pos + 4 > end) return null
            val itemLen = leInt(b, pos)
            pos += 4
            if (itemLen < 0 || pos + itemLen > end) return null
            val item = String(b, pos, itemLen, Charsets.UTF_8)
            pos += itemLen
            val eq = item.indexOf('=')
            if (eq > 0) {
                val key = item.substring(0, eq).uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "LYRIC") {
                    return item.substring(eq + 1).trim { it == '\u0000' || it == '\uFEFF' }
                }
            }
        }
        return null
    }

    // ---------------- MP4 ----------------

    private fun readMp4(b: ByteArray): String? {
        val tag = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var idx = indexOf(b, tag)
        while (idx >= 0) {
            val sizePos = idx + 4
            if (sizePos + 16 <= b.size) {
                val size = beInt(b, sizePos)
                if (size > 12 && asciiOf(b, sizePos + 4, 4) == "data") {
                    // Inner `data` box: 4 size + 4 type + 4 version/flags + payload.
                    val textLen = minOf(size - 12, b.size - sizePos - 12)
                    val text = String(b, sizePos + 12, textLen, Charsets.UTF_8)
                        .trim { it == '\u0000' || it == '\uFEFF' }
                    if (text.isNotBlank()) return text
                }
            }
            idx = indexOf(b, tag, idx + 1)
        }
        return null
    }

    // ---------------- byte helpers ----------------

    private fun ByteArray.startsWithAscii(s: String): Boolean {
        if (size < s.length) return false
        for (i in s.indices) if ((this[i].toInt() and 0xFF) != s[i].code) return false
        return true
    }

    private fun asciiBytes(s: String): ByteArray = ByteArray(s.length) { s[it].code.toByte() }

    private fun asciiOf(b: ByteArray, off: Int, len: Int): String {
        if (off < 0 || off + len > b.size) return ""
        val sb = StringBuilder(len)
        for (i in 0 until len) {
            val c = b[off + i].toInt() and 0xFF
            if (c == 0) break
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun be24(b: ByteArray, off: Int): Int {
        if (off + 3 > b.size) return 0
        return ((b[off].toInt() and 0xFF) shl 16) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            (b[off + 2].toInt() and 0xFF)
    }

    private fun beInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
    }

    private fun leInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun syncSafe(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || hay.size < needle.size) return -1
        outer@ for (i in from..(hay.size - needle.size)) {
            for (j in needle.indices) {
                if (hay[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /** Index of the first null terminator at/after [start]; steps by 2 for wide encodings. */
    private fun terminatorAt(b: ByteArray, start: Int, wide: Boolean): Int {
        var i = start
        while (i < b.size) {
            if (wide) {
                if (i + 1 < b.size && b[i].toInt() == 0 && b[i + 1].toInt() == 0) return i
                i += 2
            } else {
                if (b[i].toInt() == 0) return i
                i++
            }
        }
        return b.size
    }
}
