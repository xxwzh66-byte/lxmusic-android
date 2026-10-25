package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo

/** How the local library is grouped. */
enum class LocalGroup { SONGS, ALBUMS, ARTISTS }

/** Sort orders for the local song list. */
enum class LocalSort(val label: String) {
    TITLE_ASC("标题 ↑"),
    TITLE_DESC("标题 ↓"),
    ARTIST_ASC("歌手 ↑"),
    ARTIST_DESC("歌手 ↓"),
    ALBUM_ASC("专辑 ↑"),
    DURATION_ASC("时长 ↑"),
    DURATION_DESC("时长 ↓"),
    DATE_ADDED_DESC("最近添加"),
}

/** A grouping bucket (an album or an artist). */
data class MusicGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val tracks: List<MusicInfo>,
) {
    val count: Int get() = tracks.size
    val totalSeconds: Int get() = tracks.sumOf { it.intervalSeconds }
    val totalDuration: String get() = formatTotal(totalSeconds)

    private fun formatTotal(seconds: Int): String {
        if (seconds <= 0) return "0 分钟"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h} 小时 ${m} 分" else "${m} 分钟"
    }
}

/** Helpers to group and filter local tracks. */
object LocalLibrary {

    /** Applies a [LocalSort] to a list of tracks. */
    fun sort(tracks: List<MusicInfo>, order: LocalSort): List<MusicInfo> = when (order) {
        LocalSort.TITLE_ASC -> tracks.sortedBy { it.name.lowercase() }
        LocalSort.TITLE_DESC -> tracks.sortedByDescending { it.name.lowercase() }
        LocalSort.ARTIST_ASC -> tracks.sortedWith(compareBy({ it.singer.lowercase() }, { it.name.lowercase() }))
        LocalSort.ARTIST_DESC -> tracks.sortedWith(compareByDescending<MusicInfo> { it.singer.lowercase() }.thenBy { it.name.lowercase() })
        LocalSort.ALBUM_ASC -> tracks.sortedWith(compareBy({ it.albumName.lowercase() }, { it.name.lowercase() }))
        LocalSort.DURATION_ASC -> tracks.sortedBy { it.intervalSeconds }
        LocalSort.DURATION_DESC -> tracks.sortedByDescending { it.intervalSeconds }
        LocalSort.DATE_ADDED_DESC -> tracks.sortedByDescending { it.dateAdded }
    }

    /** Case-insensitive filter over name / singer / album. */
    fun search(tracks: List<MusicInfo>, query: String): List<MusicInfo> {
        if (query.isBlank()) return tracks
        val q = query.trim().lowercase()
        return tracks.filter {
            it.name.lowercase().contains(q) ||
                it.singer.lowercase().contains(q) ||
                it.albumName.lowercase().contains(q)
        }
    }

    /** Groups tracks by album (falling back to "未知专辑"). */
    fun byAlbum(tracks: List<MusicInfo>): List<MusicGroup> =
        tracks.groupBy { it.albumName.ifBlank { "未知专辑" } }
            .map { (album, list) ->
                val sorted = list.sortedBy { it.name }
                MusicGroup(
                    key = "album:$album",
                    title = album,
                    subtitle = sorted.firstOrNull()?.singer ?: "未知歌手",
                    coverUrl = sorted.firstNotNullOfOrNull { it.img },
                    tracks = sorted,
                )
            }
            .sortedBy { it.title }

    /** Splits multi-singer tags and groups by each artist. */
    fun byArtist(tracks: List<MusicInfo>): List<MusicGroup> {
        val map = LinkedHashMap<String, MutableList<MusicInfo>>()
        for (t in tracks) {
            val artists = t.singer.split('、', '/', '&', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("未知歌手") }
            for (a in artists) map.getOrPut(a) { mutableListOf() }.add(t)
        }
        return map.map { (artist, list) ->
            MusicGroup(
                key = "artist:$artist",
                title = artist,
                subtitle = "${list.size} 首歌曲",
                coverUrl = list.firstNotNullOfOrNull { it.img },
                tracks = list.sortedBy { it.name },
            )
        }.sortedBy { it.title }
    }

    /**
     * Merges scanner results into a single de-duplicated list.
     *
     * Tracks are keyed by [MusicInfo.id]; later lists win (so a fresh scan
     * replaces a stale cache entry with the same id).
     */
    fun merge(vararg lists: List<MusicInfo>): List<MusicInfo> {
        val map = LinkedHashMap<String, MusicInfo>()
        for (list in lists) for (t in list) map[t.id] = t
        return map.values.toList()
    }

    /**
     * Drops cached entries whose underlying file disappeared.
     *
     * A cached row is kept when it is either still present in [fresh] or was
     * added manually (those are never re-scanned).
     */
    fun prune(cached: List<MusicInfo>, fresh: List<MusicInfo>): List<MusicInfo> {
        val freshIds = fresh.mapTo(HashSet()) { it.id }
        return cached.filter { it.id in freshIds || it.extra["origin"] == "manual" }
    }
}
