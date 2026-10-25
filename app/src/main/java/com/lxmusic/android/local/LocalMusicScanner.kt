package com.lxmusic.android.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lxmusic.android.data.model.MusicInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the device for local audio using MediaStore.
 *
 * Each track is returned as a [MusicInfo] with source = MusicInfo.SOURCE_LOCAL, a content://
 * [Uri] in [MusicInfo.extra] under key "uri", and imageUri for artwork.
 */
class LocalMusicScanner(private val context: Context) {

    suspend fun scan(): List<MusicInfo> = withContext(Dispatchers.IO) {
        val out = ArrayList<MusicInfo>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val title = c.getString(titleCol) ?: c.getString(nameCol) ?: "Unknown"
                val artist = c.getString(artistCol) ?: "Unknown artist"
                val album = c.getString(albumCol) ?: ""
                val durationMs = c.getLong(durCol)
                val data = c.getString(dataCol) ?: ""
                val dateAdded = c.getLong(addedCol) * 1000L

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )

                out.add(
                    MusicInfo(
                        id = "local_$id",
                        source = MusicInfo.SOURCE_LOCAL,
                        songmid = id.toString(),
                        name = title,
                        singer = artist,
                        albumName = album,
                        albumId = albumId.toString(),
                        interval = formatTime(durationMs / 1000),
                        intervalSeconds = (durationMs / 1000).toInt(),
                        img = artUri.toString(),
                        localPath = data.ifBlank { contentUri.toString() },
                        localDurationMs = durationMs,
                        localAlbumId = albumId.toString(),
                        dateAdded = dateAdded,
                        extra = mapOf(
                            "uri" to contentUri.toString(),
                            "artUri" to artUri.toString(),
                        ),
                    )
                )
            }
        }
        out
    }

    private fun formatTime(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}