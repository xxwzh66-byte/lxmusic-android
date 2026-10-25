package com.lxmusic.android.data.repo

import android.content.Context
import com.google.gson.Gson
import com.lxmusic.android.data.db.AppDatabase
import com.lxmusic.android.data.db.FavoriteEntity
import com.lxmusic.android.data.db.PlayEventEntity
import com.lxmusic.android.data.db.PlayHistoryEntity
import com.lxmusic.android.data.db.PlaylistEntity
import com.lxmusic.android.data.db.LocalTrackEntity
import com.lxmusic.android.data.db.PlaylistTrackEntity
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.stats.PlayRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A playlist with its resolved tracks. */
data class PlaylistWithTracks(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val tracks: List<MusicInfo>,
    val createdAt: Long,
)

/**
 * Single access point for all persisted user data: playlists, favourites and
 * play history. Tracks are stored as JSON so any source works uniformly.
 */
class MusicRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val gson = Gson()

    private companion object {
        /** Play events older than this are pruned on every write. */
        const val RETENTION_MS = 365L * 24L * 60L * 60L * 1000L
    }

    // ---------------- favourites ----------------

    suspend fun favorites(): List<MusicInfo> = withContext(Dispatchers.IO) {
        db.favoriteDao().all().mapNotNull { runCatching { gson.fromJson(it.musicJson, MusicInfo::class.java) }.getOrNull() }
    }

    suspend fun isFavorite(musicId: String): Boolean = withContext(Dispatchers.IO) {
        db.favoriteDao().isFavorite(musicId)
    }

    /** Toggles favourite state; returns the new state. */
    suspend fun toggleFavorite(music: MusicInfo): Boolean = withContext(Dispatchers.IO) {
        val dao = db.favoriteDao()
        if (dao.isFavorite(music.id)) {
            dao.remove(music.id); false
        } else {
            dao.add(FavoriteEntity(music.id, gson.toJson(music))); true
        }
    }

    // ---------------- history ----------------

    suspend fun recent(limit: Int = 100): List<MusicInfo> = withContext(Dispatchers.IO) {
        db.historyDao().recent(limit).mapNotNull { runCatching { gson.fromJson(it.musicJson, MusicInfo::class.java) }.getOrNull() }
    }

    suspend fun markPlayed(music: MusicInfo) = withContext(Dispatchers.IO) {
        db.historyDao().touch(PlayHistoryEntity(music.id, gson.toJson(music)))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { db.historyDao().clear() }

    // ---------------- play statistics (V5.3.3) ----------------

    /**
     * Appends one playback event.
     *
     * Called alongside [markPlayed] rather than instead of it: the history table
     * answers "what did I listen to recently", this one answers "how often".
     */
    suspend fun logPlay(music: MusicInfo, at: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            db.playEventDao().log(PlayEventEntity(musicId = music.id, musicJson = gson.toJson(music), playedAt = at))
            // Trim anything older than a year so the table cannot grow forever.
            db.playEventDao().prune(at - RETENTION_MS)
        }

    /** Every event inside the window [from, now], oldest first. */
    suspend fun playRecords(from: Long, now: Long = System.currentTimeMillis()): List<PlayRecord> =
        withContext(Dispatchers.IO) {
            db.playEventDao().since(from, now).mapNotNull { row ->
                runCatching { gson.fromJson(row.musicJson, MusicInfo::class.java) }
                    .getOrNull()?.let { PlayRecord(it, row.playedAt) }
            }
        }

    suspend fun playEventCount(): Int = withContext(Dispatchers.IO) { db.playEventDao().count() }

    suspend fun clearPlayEvents() = withContext(Dispatchers.IO) { db.playEventDao().clear() }

    // ---------------- playlists ----------------

    suspend fun playlists(): List<PlaylistWithTracks> = withContext(Dispatchers.IO) {
        db.playlistDao().all().map { p ->
            val tracks = db.playlistDao().tracks(p.id).mapNotNull {
                runCatching { gson.fromJson(it.musicJson, MusicInfo::class.java) }.getOrNull()
            }
            PlaylistWithTracks(p.id, p.name, p.coverUrl, tracks, p.createdAt)
        }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        db.playlistDao().insert(PlaylistEntity(name = name))
    }

    suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        db.playlistDao().rename(id, name)
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.playlistDao().clearTracks(id)
        db.playlistDao().delete(id)
    }

    suspend fun addToPlaylist(playlistId: Long, music: MusicInfo) = withContext(Dispatchers.IO) {
        val dao = db.playlistDao()
        val pos = (dao.maxPosition(playlistId) ?: -1) + 1
        dao.insertTrack(PlaylistTrackEntity(playlistId, music.id, gson.toJson(music), pos))
        // Use the first track's artwork as the playlist cover.
        val count = dao.trackCount(playlistId)
        if (count == 1) dao.setCover(playlistId, music.img)
    }

    suspend fun removeFromPlaylist(playlistId: Long, musicId: String) = withContext(Dispatchers.IO) {
        db.playlistDao().removeTrack(playlistId, musicId)
    }

    // ---------------- maintenance ----------------

    /** Wipes favourites, play history and every playlist ("clear data"). */
    suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        db.historyDao().clear()
        db.playEventDao().clear()
        db.playlistDao().all().forEach { db.playlistDao().delete(it.id) }
        db.favoriteDao().all().forEach { db.favoriteDao().remove(it.musicId) }
    }

    // ---------------- local track cache (C2) ----------------

    /** Every cached local track (SAF folders + manually added files). */
    suspend fun cachedLocalTracks(): List<LocalTrackEntity> = withContext(Dispatchers.IO) {
        db.localTrackDao().all()
    }

    /** Inserts/updates cached local tracks in one transaction. */
    suspend fun upsertLocalTracks(tracks: List<LocalTrackEntity>) = withContext(Dispatchers.IO) {
        if (tracks.isNotEmpty()) db.localTrackDao().upsertAll(tracks)
    }

    /** Removes a single cached track (used by T4 "remove from library"). */
    suspend fun removeLocalTrack(id: String) = withContext(Dispatchers.IO) {
        db.localTrackDao().delete(id)
    }

    /** Removes several cached tracks at once. */
    suspend fun removeLocalTracks(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) db.localTrackDao().deleteByIds(ids)
    }

    /** Drops every folder-sourced cache row (manual rows survive). */
    suspend fun clearFolderCache() = withContext(Dispatchers.IO) {
        db.localTrackDao().clearFolders()
    }

    suspend fun clearLocalTracks() = withContext(Dispatchers.IO) { db.localTrackDao().clear() }
}
