package com.lxmusic.android.lyric

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resolves lyrics for a local track, in priority order:
 *  1. a sibling `.lrc` file next to the audio file (SAF aware),
 *  2. lyrics embedded in the audio tags (ID3 USLT, Vorbis/Opus comments, MP4 copyright-lyr),
 *  3. the platform metadata retriever as a last resort.
 *
 * SAF `content://` documents never resolve to a real path, so sibling lookups go
 * through [DocumentFile.parentFile] instead of the file system.
 */
class LyricLoader(private val context: Context) {

    suspend fun loadLocal(
        pathOrUri: String,
        preferLrcFile: Boolean = true,
        readEmbedded: Boolean = true,
    ): Lyric = withContext(Dispatchers.IO) {
        if (pathOrUri.isBlank()) return@withContext Lyric(emptyList())

        if (preferLrcFile) {
            readSiblingLrc(pathOrUri)?.let { if (!it.isEmpty) return@withContext it }
        }

        if (readEmbedded) {
            readEmbeddedLyric(pathOrUri)?.let { if (!it.isEmpty) return@withContext it }
        }

        if (!preferLrcFile) {
            readSiblingLrc(pathOrUri)?.let { if (!it.isEmpty) return@withContext it }
        }

        readRetrieverLyric(pathOrUri) ?: Lyric(emptyList())
    }

    // ------------------------------------------------------------ sibling .lrc

    private fun readSiblingLrc(pathOrUri: String): Lyric? = try {
        val bytes = if (pathOrUri.startsWith("content://")) siblingLrcFromSaf(pathOrUri)
        else siblingLrcFromFile(pathOrUri)
        bytes?.let { LrcParser.parse(LyricEncoding.decode(it)) }
    } catch (_: Exception) {
        null
    }

    private fun siblingLrcFromFile(pathOrUri: String): ByteArray? {
        val path = if (pathOrUri.startsWith("file://")) Uri.parse(pathOrUri).path ?: return null else pathOrUri
        val audio = File(path)
        val base = audio.name.substringBeforeLast('.')
        val dir = audio.parentFile ?: return null
        val lrc = listOf("$base.lrc", "$base.LRC", "$base.Lrc").map { File(dir, it) }.firstOrNull { it.isFile }
        return lrc?.readBytes()
    }

    private fun siblingLrcFromSaf(pathOrUri: String): ByteArray? {
        val uri = Uri.parse(pathOrUri)
        val parent = DocumentFile.fromSingleUri(context, uri)?.parentFile ?: return null
        val base = (displayName(uri) ?: return null).substringBeforeLast('.')
        val wanted = "$base.lrc".lowercase()
        val lrc = parent.listFiles().firstOrNull { it.isFile && it.name?.lowercase() == wanted } ?: return null
        return context.contentResolver.openInputStream(lrc.uri)?.use { it.readBytes() }
    }

    private fun displayName(uri: Uri): String? {
        // SAF documents expose their name as the last path segment.
        uri.lastPathSegment?.let { seg ->
            val idx = seg.indexOf(':')
            val name = if (idx >= 0) seg.substring(idx + 1) else seg
            if (name.isNotBlank()) return name.substringAfterLast('/')
        }
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ embedded tags

    private fun readEmbeddedLyric(pathOrUri: String): Lyric? = try {
        val bytes = readHead(pathOrUri) ?: return null
        EmbeddedLyrics.read(bytes)?.let { LrcParser.parse(it) }
    } catch (_: Exception) {
        null
    }

    /** Reads the whole file when it is small enough, otherwise just its head. */
    private fun readHead(pathOrUri: String): ByteArray? {
        val maxHold = 64L * 1024 * 1024
        val headLen = 8 * 1024 * 1024
        if (pathOrUri.startsWith("content://")) {
            val uri = Uri.parse(pathOrUri)
            val size = sizeOf(uri)
            return context.contentResolver.openInputStream(uri)?.use { input ->
                if (size in 1..maxHold) input.readBytes() else input.readAtMost(headLen)
            }
        }
        val path = if (pathOrUri.startsWith("file://")) Uri.parse(pathOrUri).path ?: return null else pathOrUri
        val file = File(path)
        if (!file.isFile) return null
        return if (file.length() <= maxHold) file.readBytes() else file.inputStream().use { it.readAtMost(headLen) }
    }

    private fun sizeOf(uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var filled = 0
        while (filled < limit) {
            val read = read(buffer, filled, limit - filled)
            if (read <= 0) break
            filled += read
        }
        return if (filled == limit) buffer else buffer.copyOf(filled)
    }

    // ------------------------------------------------------------ platform fallback

    private fun readRetrieverLyric(pathOrUri: String): Lyric? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (pathOrUri.startsWith("content://")) retriever.setDataSource(context, Uri.parse(pathOrUri))
            else retriever.setDataSource(pathOrUri)
            // MediaMetadataRetriever has no public lyrics key; key 1000 is the
            // conventional METADATA_KEY_LYRICS value on Android.
            val embedded = retriever.extractMetadata(1000)
            if (!embedded.isNullOrBlank()) LrcParser.parse(embedded) else null
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}