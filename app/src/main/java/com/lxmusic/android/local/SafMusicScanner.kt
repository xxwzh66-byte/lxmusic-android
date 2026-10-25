package com.lxmusic.android.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.lxmusic.android.data.model.MusicInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Scans user-authorised folders (SAF) for audio files.
 *
 * Folders are granted with [android.content.Intent.ACTION_OPEN_DOCUMENT_TREE] and
 * must be persisted with
 * [android.content.ContentResolver.takePersistableUriPermission], otherwise the
 * grant disappears after a reboot.
 */
class SafMusicScanner(private val context: Context) {

    companion object {
        private const val TAG = "SafMusicScanner"

        /** Extensions we treat as playable audio. */
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "ape", "mp4", "mka",
        )

        /** Directory names never descended into. */
        private val SKIP_DIRS = setOf("Android", ".thumbnails")

        fun extensionOf(name: String): String =
            name.substringAfterLast('.', "").lowercase()

        fun isAudioFile(name: String, mime: String?): Boolean {
            if (mime != null && mime.startsWith("audio/")) return true
            return extensionOf(name) in AUDIO_EXTENSIONS
        }
    }

    /** Cached cover directory (cacheDir/covers). */
    private val coverDir: File by lazy {
        File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
    }

    /**
     * Scans every tree in [treeUris].
     *
     * @param onProgress called with the running track count so the UI can show progress.
     */
    suspend fun scanFolders(
        treeUris: List<String>,
        readEmbeddedCover: Boolean = true,
        onProgress: (Int) -> Unit = {},
    ): List<MusicInfo> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, MusicInfo>()
        for (tree in treeUris) {
            coroutineContext.ensureActive()
            val uri = runCatching { Uri.parse(tree) }.getOrNull() ?: continue
            val root = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull() ?: continue
            walk(root, "folder", readEmbeddedCover, out, onProgress)
        }
        out.values.toList()
    }

    /** Reads metadata for explicitly added single files (T3). */
    suspend fun readSingle(uri: Uri, readEmbeddedCover: Boolean = true): MusicInfo? =
        withContext(Dispatchers.IO) {
            runCatching { readFile(DocumentFile.fromSingleUri(context, uri) ?: return@withContext null, uri.toString(), "manual", readEmbeddedCover) }
                .getOrNull()
        }

    private suspend fun walk(
        dir: DocumentFile,
        origin: String,
        readEmbeddedCover: Boolean,
        out: MutableMap<String, MusicInfo>,
        onProgress: (Int) -> Unit,
    ) {
        coroutineContext.ensureActive()
        val children = runCatching { dir.listFiles() }.getOrElse { return }
        for (child in children) {
            coroutineContext.ensureActive()
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                if (name in SKIP_DIRS) continue
                if (File(name).name == ".nomedia") continue
                walk(child, origin, readEmbeddedCover, out, onProgress)
            } else if (isAudioFile(name, child.type)) {
                val info = runCatching { readFile(child, child.uri.toString(), origin, readEmbeddedCover) }.getOrNull()
                if (info != null) {
                    out[info.id] = info
                    onProgress(out.size)
                }
            }
        }
    }

    private fun readFile(
        file: DocumentFile,
        uriString: String,
        origin: String,
        readEmbeddedCover: Boolean,
    ): MusicInfo? {
        val name = file.name ?: "Unknown"
        val docId = runCatching { DocumentsContract.getDocumentId(Uri.parse(uriString)) }.getOrNull()
            ?: uriString
        val id = "saf_" + md5(docId).take(16)

        var title = name.substringBeforeLast('.')
        var artist = "未知歌手"
        var album = ""
        var durationMs = 0L
        var coverPath: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (readEmbeddedCover) {
                coverPath = retriever.embeddedPicture?.let { saveCover(id, it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "metadata failed for $uriString: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }

        val seconds = (durationMs / 1000).toInt()
        return MusicInfo(
            id = id,
            source = MusicInfo.SOURCE_LOCAL,
            songmid = docId,
            name = title,
            singer = artist,
            albumName = album,
            interval = formatTime(seconds.toLong()),
            intervalSeconds = seconds,
            img = coverPath,
            localPath = uriString,
            localDurationMs = durationMs,
            localAlbumId = album,
            dateAdded = System.currentTimeMillis(),
            extra = mapOf(
                "uri" to uriString,
                "documentId" to docId,
                "origin" to origin,
                "lastModified" to file.lastModified().toString(),
                "size" to file.length().toString(),
            ),
        )
    }

    /** Persists embedded artwork into cacheDir/covers; returns the file path. */
    private fun saveCover(id: String, bytes: ByteArray): String? = runCatching {
        val f = File(coverDir, "$id.jpg")
        if (!f.exists()) {
            FileOutputStream(f).use { it.write(bytes) }
        }
        f.absolutePath
    }.getOrNull()

    private fun formatTime(seconds: Long): String =
        if (seconds <= 0) "00:00" else "%02d:%02d".format(seconds / 60, seconds % 60)

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
