package com.lxmusic.android.data.model

/** A music track returned by search or stored in a list. */
data class MusicInfo(
    val id: String,                 // unique per source, e.g. "kw_62355680"
    val source: String,             // kw/kg/tx/wy/mg/local
    val songmid: String,            // platform-specific song id
    val name: String,
    val singer: String,
    val albumName: String = "",
    val albumId: String = "",
    val interval: String = "00:00",
    val intervalSeconds: Int = 0,
    val img: String? = null,
    /** Available qualities with optional size/hash metadata, keyed by quality id. */
    val qualitys: Map<String, QualityInfo> = emptyMap(),
    /** Extra platform fields (hash, albumAudioId, etc.) needed when requesting URLs. */
    val extra: Map<String, String> = emptyMap(),
    /** Local file path (set only when [isLocal]). */
    val localPath: String? = null,
    /** Duration in ms for local files (ExoPlayer metadata). */
    val localDurationMs: Long = 0L,
    /** Album id used to group local tracks. */
    val localAlbumId: String = "",
    /** Date added (ms) for local files, used for sorting. */
    val dateAdded: Long = 0L,
) {
    /**
     * V6.1.2: [source] is a bare string compared against `"local"` in eight
     * places across the player and the UI. Spelling the literal out at each site
     * made a typo silently route a local track through the online resolver, so
     * the comparison lives here.
     */
    val isLocal: Boolean get() = source == SOURCE_LOCAL

    companion object {
        /** [source] value for tracks that live on this device. */
        const val SOURCE_LOCAL = "local"
    }
}

data class QualityInfo(
    val type: String,
    val size: String? = null,
    val hash: String? = null,
)

/** A playable playlist. */
data class SongList(
    val id: Long = 0,
    val name: String,
    val source: String = "local",
    val coverUrl: String? = null,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Junction row: which music is in which list, plus ordering. */
data class ListItem(
    val id: Long = 0,
    val listId: Long,
    val musicId: String,
    val musicJson: String,
    val position: Int,
)