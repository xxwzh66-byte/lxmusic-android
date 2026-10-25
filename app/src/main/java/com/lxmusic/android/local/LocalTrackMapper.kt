package com.lxmusic.android.local

import com.lxmusic.android.data.db.LocalTrackEntity
import com.lxmusic.android.data.model.MusicInfo

/** Conversions between the persisted cache row and the in-memory model (C2). */
object LocalTrackMapper {

    fun toEntity(m: MusicInfo, origin: String): LocalTrackEntity = LocalTrackEntity(
        id = m.id,
        uri = m.localPath ?: m.extra["uri"].orEmpty(),
        documentId = m.extra["documentId"] ?: m.songmid,
        title = m.name,
        artist = m.singer,
        album = m.albumName,
        durationMs = m.localDurationMs,
        size = m.extra["size"]?.toLongOrNull() ?: 0L,
        lastModified = m.extra["lastModified"]?.toLongOrNull() ?: 0L,
        coverPath = m.img,
        origin = origin,
        dateAdded = m.dateAdded,
    )

    fun toMusic(e: LocalTrackEntity): MusicInfo = MusicInfo(
        id = e.id,
        source = MusicInfo.SOURCE_LOCAL,
        songmid = e.documentId,
        name = e.title,
        singer = e.artist,
        albumName = e.album,
        albumId = e.album,
        interval = formatTime((e.durationMs / 1000).toInt()),
        intervalSeconds = (e.durationMs / 1000).toInt(),
        img = e.coverPath,
        localPath = e.uri,
        localDurationMs = e.durationMs,
        localAlbumId = e.album,
        dateAdded = e.dateAdded,
        extra = mapOf(
            "uri" to e.uri,
            "documentId" to e.documentId,
            "origin" to e.origin,
            "lastModified" to e.lastModified.toString(),
            "size" to e.size.toString(),
        ),
    )

    private fun formatTime(seconds: Int): String =
        if (seconds <= 0) "00:00" else "%02d:%02d".format(seconds / 60, seconds % 60)
}
