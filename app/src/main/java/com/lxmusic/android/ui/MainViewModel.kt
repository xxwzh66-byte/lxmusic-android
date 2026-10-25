package com.lxmusic.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.android.BuildConfig
import com.lxmusic.android.LxApp
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.MusicRepository
import com.lxmusic.android.data.repo.PlaylistWithTracks
import com.lxmusic.android.data.settings.AppSettings
import com.lxmusic.android.data.stats.PlayRecord
import com.lxmusic.android.data.stats.PlayStats
import com.lxmusic.android.data.stats.PlayTally
import com.lxmusic.android.data.stats.StatsBucket
import com.lxmusic.android.data.stats.StatsRange
import com.lxmusic.android.data.settings.AudioQuality
import com.lxmusic.android.data.settings.ListStyle
import com.lxmusic.android.data.settings.recordPolicy
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.data.settings.ThemeMode
import com.lxmusic.android.local.LocalGroup
import com.lxmusic.android.local.LocalLibrary
import com.lxmusic.android.local.LocalSort
import com.lxmusic.android.local.LocalMusicScanner
import com.lxmusic.android.local.LocalFileDeleter
import com.lxmusic.android.local.LocalTrackMapper
import com.lxmusic.android.local.SafMusicScanner
import com.lxmusic.android.local.MusicGroup
import com.lxmusic.android.player.EqPreset
import com.lxmusic.android.player.EqualizerState
import com.lxmusic.android.player.PlayerController
import com.lxmusic.android.player.PlayerState
import com.lxmusic.android.player.RepeatMode
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.lxmusic.android.source.LxSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top-level tabs. */
enum class HomeTab(val label: String) {
    LOCAL("本地音乐"),
    LIBRARY("音乐库"),
    ONLINE("在线搜索"),
}

data class SearchUiState(
    val keyword: String = "",
    val source: LxSource = LxSource.KW,
    val loading: Boolean = false,
    val results: List<MusicInfo> = emptyList(),
    val error: String? = null,
)

data class LocalUiState(
    val loading: Boolean = false,
    val allTracks: List<MusicInfo> = emptyList(),
    val group: LocalGroup = LocalGroup.SONGS,
    val sort: LocalSort = LocalSort.TITLE_ASC,
    val query: String = "",
    /** Current visible songs (respecting search + group). */
    val songs: List<MusicInfo> = emptyList(),
    /** Album/artist groups (empty when grouping by songs). */
    val groups: List<MusicGroup> = emptyList(),
    val error: String? = null,
)

data class LibraryUiState(
    val favorites: List<MusicInfo> = emptyList(),
    val recent: List<MusicInfo> = emptyList(),
    val playlists: List<PlaylistWithTracks> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val playingPlaylistId: Long? = null,
)

/**
 * V5.3.3: the play-count statistics screen.
 *
 * [records] is the raw event list for the selected window; the tallies and the
 * histogram are derived from it so switching range only re-queries the database
 * instead of re-deriving everything twice.
 */
data class StatsUiState(
    val range: StatsRange = StatsRange.WEEK,
    val loading: Boolean = false,
    val records: List<PlayRecord> = emptyList(),
    val tallies: List<PlayTally> = emptyList(),
    val buckets: List<StatsBucket> = emptyList(),
    val totalPlays: Int = 0,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val sourceManager = LxApp.instance.sourceManager
    private val localScanner = LocalMusicScanner(app)
    private val safScanner = SafMusicScanner(app)
    private val repo = MusicRepository(app)
    private val settingsStore = LxApp.instance.settingsStore

    private val _tab = MutableStateFlow(HomeTab.LOCAL)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _local = MutableStateFlow(LocalUiState())
    val local: StateFlow<LocalUiState> = _local.asStateFlow()

    private val _library = MutableStateFlow(LibraryUiState())
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _equalizer = MutableStateFlow(EqualizerState())
    val equalizer: StateFlow<EqualizerState> = _equalizer.asStateFlow()

    val playerController = PlayerController(app, sourceManager)

    /** V5.1-1: fired when a user tap should open the full-screen player. */
    val openNowPlaying = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** V5.1-1: start playback AND open the full-screen player. */
    private fun playAndOpen(list: List<MusicInfo>, index: Int) {
        if (list.isEmpty() || index !in list.indices) return
        playerController.setQueue(list, index)
        openNowPlaying.tryEmit(Unit)
    }

    private val _loadedApiId = MutableStateFlow<String?>(null)
    val loadedApiId: StateFlow<String?> = _loadedApiId.asStateFlow()

    /** User preferences (theme, folders, playback, lyrics). */
    val settings: StateFlow<AppSettings> = settingsStore.state

    val versionName: String get() = BuildConfig.VERSION_NAME

    /**
     * V5.3-2: the track the user listened to last, resolved against the local
     * library so the home screen can offer a one-tap "continue listening"
     * card. Recomputed whenever the library or the saved marker changes.
     */
    val lastPlayed: StateFlow<MusicInfo?> = combine(settingsStore.state, _local) { st, lib ->
        st.lastPlayedId?.let { id -> lib.allTracks.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** V5.3-2: resume the card track inside the whole local queue. */
    fun resumeLastPlayed() {
        val track = lastPlayed.value ?: return
        val list = _local.value.allTracks
        val at = list.indexOfFirst { it.id == track.id }
        if (at >= 0) playAndOpen(list, at) else playAndOpen(listOf(track), 0)
    }

    /** True while the settings screen should be shown. */
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    /** True while the scan-folder manager should be shown. */
    private val _showFolderManager = MutableStateFlow(false)
    val showFolderManager: StateFlow<Boolean> = _showFolderManager.asStateFlow()

    // ---------------- play statistics (V5.3.3) ----------------

    private val _stats = MutableStateFlow(StatsUiState())
    val stats: StateFlow<StatsUiState> = _stats.asStateFlow()

    private val _showStats = MutableStateFlow(false)
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

    fun openStats() {
        _showStats.value = true
        refreshStats()
    }

    fun closeStats() { _showStats.value = false }

    /** Switches the window and re-reads only that window's events. */
    fun setStatsRange(range: StatsRange) {
        if (_stats.value.range == range) return
        _stats.value = _stats.value.copy(range = range)
        refreshStats()
    }

    /**
     * Rebuilds the statistics from the stored play events.
     *
     * The aggregation itself lives in [PlayStats], which is pure, so this method
     * only fetches the window and hands it over.
     */
    fun refreshStats() {
        viewModelScope.launch {
            _stats.value = _stats.value.copy(loading = true, error = null)
            val range = _stats.value.range
            val now = System.currentTimeMillis()
            try {
                val records = repo.playRecords(PlayStats.windowStart(range, now), now)
                _stats.value = _stats.value.copy(
                    loading = false,
                    records = records,
                    tallies = PlayStats.tally(records),
                    buckets = PlayStats.buckets(records, range, now),
                    totalPlays = PlayStats.totalPlays(records),
                    error = null,
                )
            } catch (e: Exception) {
                _stats.value = _stats.value.copy(loading = false, error = e.message)
            }
        }
    }

    /** Plays a track straight from the statistics list. */
    fun playStatsTrack(tally: PlayTally) {
        val list = _stats.value.tallies.map { it.music }
        val at = list.indexOfFirst { it.id == tally.music.id }.coerceAtLeast(0)
        if (list.isNotEmpty()) playAndOpen(list, at)
    }

    /** Wipes the play events; the tallies and the chart go empty with them. */
    fun clearPlayStats() {
        viewModelScope.launch {
            repo.clearPlayEvents()
            refreshStats()
        }
    }

    /** True while a local scan is running (drives the progress indicator). */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    init {
        playerController.connect()
        // V5.1-4: persist mode flips the moment the user makes them.
        playerController.onModeChanged = { mode, shuf ->
            settingsStore.setRepeatMode(mode.name)
            settingsStore.setShuffle(shuf)
        }
        // V5.1-3: throttled last-played persistence.
        playerController.onProgress = { id, pos ->
            settingsStore.setLastPlayed(id, pos)
        }
        // V5.1-3/4: once settings are loaded, replay saved modes and restore
        // the last-played track silently (paused, at its saved position).
        viewModelScope.launch {
            settingsStore.loaded.first()
            val st = settingsStore.state.value
            playerController.applyPersistedModes(
                when (st.repeatMode) {
                    "ALL" -> RepeatMode.ALL
                    "ONE" -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                },
                st.shuffle,
            )
            // V6.1: 均衡器曲线是「用户的选择」，不是播放器状态。效果器绑在音频会话上，
            // 每次重建都归零，所以要在播放器第一次 attach 之前把持久化的快照交给
            // EqualizerController，由它在 attach 后灌回去。
            playerController.equalizer.setProfile(st.eq)
            restoreLastPlayed(st)
            // V6.1.1: read *after* `loaded` flips. This used to sit at the end of
            // init reading `state.value` directly, which is the defaults object
            // until the first DataStore emission lands - a user who turned
            // autoscan off still got a full library scan on every boot.
            if (st.autoScanOnStart) scanLocal()
        }
        // V5.3.3 (Q4): a collapsed queue is repaired at press time, not only at
        // restore time, so "next" never silently does nothing.
        playerController.onNeedBackfill = { currentId -> libraryContextFor(currentId) }
        playerController.onTrackStarted = { music ->
            viewModelScope.launch {
                // V5.3.6: the two histories are independent switches. 最近播放 is
                // opt-outable privacy; the play-count statistics are not, and used
                // to be silenced whenever 记录历史 was off (HANDOVER §9-7).
                val policy = settingsStore.state.value.recordPolicy()
                if (policy.history) {
                    repo.markPlayed(music)
                    refreshLibrary()
                }
                if (policy.stats) repo.logPlay(music)
            }
        }
        viewModelScope.launch { playerController.state.collect { _player.value = it } }
        playerController.equalizer.onStateChanged = { _equalizer.value = it }
        _equalizer.value = playerController.equalizer.state
        viewModelScope.launch {
            settingsStore.state.collect { applyPlaybackSettings(it) }
        }
        refreshLibrary()
    }

    /**
     * V5.1-3: silently reload the last played track at its saved position.
     * Only local tracks are restorable (online would require network at boot);
     * unknown ids are cleared so a dead marker never blocks the next save.
     */
    private fun restoreLastPlayed(st: AppSettings) {
        val id = st.lastPlayedId
        if (id.isNullOrBlank()) return
        viewModelScope.launch {
            val track = _local.value.allTracks.firstOrNull { it.id == id }
                ?: repo.recent().firstOrNull { it.id == id && it.isLocal }
            if (track == null) {
                settingsStore.setLastPlayed(null, 0L)
                playerController.clearSavedProgress()
                return@launch
            }
            playerController.prepareRestored(track, st.lastPlayedPosMs)
            // V5.5-3: restore yields a single-track queue which breaks shuffle;
            // backfill the full context once the scan settles.
            viewModelScope.launch {
                val ordered = libraryContextFor(track.id)
                if (ordered.size > 1) playerController.backfillQueue(ordered, track.id)
            }
        }
    }

    /**
     * The ordered queue context that should surround [currentId].
     *
     * Waits for the first finished scan, then prefers the in-memory library and
     * falls back to the persisted recent list (which is how a track is found
     * before any scan has run). Returns an empty list when the track is unknown,
     * which callers read as "nothing to backfill".
     *
     * Shared by the restore path and [PlayerController.onNeedBackfill] so the two
     * triggers cannot drift apart.
     */
    private suspend fun libraryContextFor(currentId: String): List<MusicInfo> {
        _local.first { !it.loading }
        val all = _local.value.allTracks
        val track = all.firstOrNull { it.id == currentId }
            ?: repo.recent().firstOrNull { it.id == currentId && it.isLocal }
            ?: return emptyList()
        return buildRestoreContext(all, track)
    }

    /**
     * V5.5-3: queue context for a restored track - its group order with the
     * track placed at its natural position (simplest correct slice: whole
     * library ordered so the restored track sits mid-list).
     */
    private fun buildRestoreContext(
        all: List<MusicInfo>,
        track: MusicInfo,
    ): List<MusicInfo> {
        val rest = all.filter { it.id != track.id }
        val at = (all.size / 2).coerceIn(0, rest.size)
        return rest.subList(0, at) + track + rest.subList(at, rest.size)
    }

    /** Mirrors lyric-related preferences onto the player. */
    private fun applyPlaybackSettings(s: AppSettings) {
        // A5: AudioQuality's label *is* the LxQuality id ("128k"/"320k"/"flac"),
        // so the mapping is the enum itself - spelling the strings out again here
        // just gave them a second place to drift.
        playerController.preferredQualityId = s.quality.label
        playerController.lyricOffsetMs = s.lyricOffsetMs
        playerController.preferLrcFile = s.preferLrcFile
        playerController.readEmbeddedLyric = true
    }

    fun setTab(t: HomeTab) {
        _tab.value = t
        if (t == HomeTab.LIBRARY) refreshLibrary()
    }

    // ---------------- local music ----------------

    /**
     * Loads the cached library first, then rescans in the background.
     *
     * With no authorised folders the legacy MediaStore scan is used, so existing
     * users keep their library until they pick a folder.
     */
    fun scanLocal() {
        _local.value = _local.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                // 1. instant paint from the Room cache
                val cached = withContext(Dispatchers.IO) {
                    repo.cachedLocalTracks().map { LocalTrackMapper.toMusic(it) }
                }
                if (cached.isNotEmpty()) {
                    _local.value = _local.value.copy(loading = false, allTracks = cached)
                    recomputeLocal()
                }
                // 2. background refresh
                val folders = settingsStore.state.value.scanFolders.toList()
                val manual = settingsStore.state.value.manualUris.toList()
                val fresh = if (folders.isEmpty()) {
                    localScanner.scan()
                } else {
                    safScanner.scanFolders(
                        treeUris = folders,
                        readEmbeddedCover = settingsStore.state.value.readEmbeddedCover,
                        onProgress = { count -> _scanning.value = count > 0 },
                    ) + manual.mapNotNull { u ->
                        runCatching { safScanner.readSingle(Uri.parse(u), settingsStore.state.value.readEmbeddedCover) }.getOrNull()
                    }
                }
                _scanning.value = false
                val kept = LocalLibrary.prune(cached, fresh)
                val merged = LocalLibrary.merge(fresh, kept)
                _local.value = _local.value.copy(loading = false, allTracks = merged)
                recomputeLocal()
                withContext(Dispatchers.IO) {
                    repo.clearFolderCache()
                    repo.upsertLocalTracks(merged.map { LocalTrackMapper.toEntity(it, it.extra["origin"] ?: "folder") })
                }
            } catch (e: Exception) {
                _scanning.value = false
                _local.value = _local.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun setLocalGroup(g: LocalGroup) {
        _local.value = _local.value.copy(group = g)
        recomputeLocal()
    }

    fun setLocalQuery(q: String) {
        _local.value = _local.value.copy(query = q)
        recomputeLocal()
    }

    fun setLocalSort(sort: LocalSort) {
        _local.value = _local.value.copy(sort = sort)
        recomputeLocal()
    }

    private fun recomputeLocal() {
        val s = _local.value
        val filtered = LocalLibrary.sort(LocalLibrary.search(s.allTracks, s.query), s.sort)
        when (s.group) {
            LocalGroup.SONGS ->
                _local.value = s.copy(songs = filtered, groups = emptyList())
            LocalGroup.ALBUMS ->
                _local.value = s.copy(songs = emptyList(), groups = LocalLibrary.byAlbum(filtered))
            LocalGroup.ARTISTS ->
                _local.value = s.copy(songs = emptyList(), groups = LocalLibrary.byArtist(filtered))
        }
    }

    fun playLocal(index: Int) {
        val list = _local.value.songs
        if (index in list.indices) playAndOpen(list, index) // V5.1-1
    }

    fun playGroup(group: MusicGroup, startIndex: Int = 0) {
        if (group.tracks.isNotEmpty()) playerController.setQueue(group.tracks, startIndex)
    }

    // ---------------- online search ----------------

    fun setKeyword(k: String) { _search.value = _search.value.copy(keyword = k) }
    fun setSource(s: LxSource) { _search.value = _search.value.copy(source = s) }

    fun search() {
        val s = _search.value
        if (s.keyword.isBlank()) return
        _search.value = s.copy(loading = true, error = null, results = emptyList())
        viewModelScope.launch {
            try {
                val list = sourceManager.search(s.source, s.keyword)
                _search.value = _search.value.copy(loading = false, results = list)
            } catch (e: Exception) {
                _search.value = _search.value.copy(loading = false, error = e.message ?: "搜索失败")
            }
        }
    }

    fun playFromResults(index: Int) {
        val list = _search.value.results
        if (index in list.indices) playAndOpen(list, index) // V5.1-1
    }

    // ---------------- library ----------------

    fun refreshLibrary() {
        viewModelScope.launch {
            val favs = repo.favorites()
            val recent = repo.recent()
            val lists = repo.playlists()
            _library.value = _library.value.copy(
                favorites = favs,
                recent = recent,
                playlists = lists,
                favoriteIds = favs.map { it.id }.toSet(),
            )
        }
    }

    suspend fun isFavorite(musicId: String): Boolean = repo.isFavorite(musicId)

    fun toggleFavorite(music: MusicInfo) {
        viewModelScope.launch {
            val nowFav = repo.toggleFavorite(music)
            val favs = repo.favorites()
            _library.value = _library.value.copy(
                favorites = favs,
                favoriteIds = favs.map { it.id }.toSet(),
            )
        }
    }

    fun createPlaylist(name: String, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.createPlaylist(name.ifBlank { "新建歌单" })
            refreshLibrary()
            onDone(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { repo.renamePlaylist(id, name); refreshLibrary() }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repo.deletePlaylist(id); refreshLibrary() }
    }

    fun addToPlaylist(playlistId: Long, music: MusicInfo) {
        viewModelScope.launch { repo.addToPlaylist(playlistId, music); refreshLibrary() }
    }

    fun removeFromPlaylist(playlistId: Long, musicId: String) {
        viewModelScope.launch { repo.removeFromPlaylist(playlistId, musicId); refreshLibrary() }
    }

    fun playPlaylist(playlist: PlaylistWithTracks, startIndex: Int = 0) {
        if (playlist.tracks.isNotEmpty()) playAndOpen(playlist.tracks, startIndex) // V5.1-1
    }

    fun playFavorites(startIndex: Int = 0) {
        val favs = _library.value.favorites
        if (favs.isNotEmpty()) playAndOpen(favs, startIndex) // V5.1-1
    }

    fun playRecent(startIndex: Int = 0) {
        val recent = _library.value.recent
        if (recent.isNotEmpty()) playAndOpen(recent, startIndex) // V5.1-1
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearHistory(); refreshLibrary() }
    }

    // ---------------- playback ----------------

    // ---------------- queue ----------------

    fun queueJumpTo(index: Int) = playerController.playAt(index)
    fun queueMove(from: Int, to: Int) = playerController.moveInQueue(from, to)
    fun queueRemove(index: Int) = playerController.removeFromQueue(index)
    fun queueClear() = playerController.clearQueue()

    fun playList(list: List<MusicInfo>, startIndex: Int = 0) {
        if (list.isNotEmpty()) playerController.setQueue(list, startIndex)
    }

    fun addToQueueNext(music: MusicInfo) = playerController.playNext(listOf(music))
    fun addToQueueLast(music: MusicInfo) = playerController.enqueue(listOf(music))

    // ---------------- playback ----------------

    fun togglePlayPause() = playerController.togglePlayPause()
    /** Pauses playback (used by the ACTION_AUDIO_BECOMING_NOISY receiver). */
    fun pause() {
        if (playerController.state.value.isPlaying) playerController.togglePlayPause()
    }
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun next() { viewModelScope.launch { playerController.next() } }
    fun prev() { viewModelScope.launch { playerController.prev() } }
    fun cycleRepeat() = playerController.cycleRepeat()
    fun toggleShuffle() = playerController.toggleShuffle()

    // ---------------- equalizer ----------------

    // V6.1: 每次改动都回写一份快照。均衡器效果器绑在音频会话上，播放器一重建就归零，
    // 光改效果器不落盘等于「每次重启都白调」——这正是用户报的那个问题。
    // 只在用户改动时写、不在 settingsStore.state 的回调里写，避免 DataStore 自激。
    fun eqSetEnabled(enabled: Boolean) = playerController.equalizer.setEnabled(enabled).also { persistEq() }
    fun eqSetBand(bandIndex: Int, levelMb: Short) =
        playerController.equalizer.setBandLevel(bandIndex, levelMb).also { persistEq() }
    fun eqApplyPreset(preset: EqPreset) = playerController.equalizer.applyPreset(preset).also { persistEq() }
    fun eqSetBass(strength: Int) = playerController.equalizer.setBassStrength(strength).also { persistEq() }
    fun eqSetVirtualizer(strength: Int) =
        playerController.equalizer.setVirtualizerStrength(strength).also { persistEq() }
    fun eqSetLoudness(gainMb: Int) = playerController.equalizer.setLoudnessGainMb(gainMb).also { persistEq() }
    fun eqReset() = playerController.equalizer.reset().also { persistEq() }

    private var eqPersistJob: Job? = null

    /** 有改动还没落盘。onCleared 靠它决定要不要补写。 */
    @Volatile private var eqDirty = false

    private fun persistEq() {
        // V6.1.1: 滑杆的 onValueChange 是逐帧回调，直接落盘等于每拖一个像素写一次
        // DataStore。合并成「停手 400ms 后写一次」：中间的每一帧本来也不会被谁读到。
        eqDirty = true
        eqPersistJob?.cancel()
        eqPersistJob = viewModelScope.launch {
            delay(EQ_PERSIST_DEBOUNCE_MS)
            writeEqNow()
        }
    }

    /**
     * V6.1.2: 立刻把当前曲线写盘。
     *
     * 防抖窗口内关掉界面时，`eqPersistJob` 会随 `viewModelScope` 一起被取消——效果已经
     * 听得到了，存档却没写，下次启动回到上一条曲线。`SettingsStore.setEq` 走的是 store
     * 自己的 IO scope，所以这里可以在 `onCleared` 里安全地补写最后一次。
     */
    private fun writeEqNow() {
        eqDirty = false
        settingsStore.setEq(playerController.equalizer.snapshot())
    }

    // ---------------- sources ----------------

    fun importSource(script: String) {
        viewModelScope.launch {
            try {
                val entity = sourceManager.saveUserApi(script)
                _loadedApiId.value = entity.id
                sourceManager.loadScript(entity.id, entity.script)
            } catch (e: Exception) {
                _search.value = _search.value.copy(error = "导入失败: ${e.message}")
            }
        }
    }


    // ---------------- settings ----------------
    fun openSettings() { _showSettings.value = true }
    fun closeSettings() { _showSettings.value = false }

    fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)
    fun setThemeColor(id: String) = settingsStore.setThemeColor(id)
    fun setListStyle(style: ListStyle) = settingsStore.setListStyle(style)
    fun setAutoScan(on: Boolean) = settingsStore.setAutoScan(on)
    fun setReadEmbeddedCover(on: Boolean) = settingsStore.setReadEmbeddedCover(on)
    fun setQuality(q: AudioQuality) = settingsStore.setQuality(q)
    fun setRecordHistory(on: Boolean) = settingsStore.setRecordHistory(on)
    fun setRecordStats(on: Boolean) = settingsStore.setRecordStats(on)
    fun setPauseOnHeadsetUnplug(on: Boolean) = settingsStore.setPauseOnHeadsetUnplug(on)
    fun setLyricFontScale(v: Float) = settingsStore.setLyricFontScale(v)
    fun setLyricOffset(ms: Int) = settingsStore.setLyricOffset(ms)
    fun setPreferLrcFile(on: Boolean) = settingsStore.setPreferLrcFile(on)
    fun setPlayerSkin(skin: String) = settingsStore.setPlayerSkin(skin)
    fun setShowPlayerMeta(on: Boolean) = settingsStore.setShowPlayerMeta(on)

    fun openFolderManager() { _showFolderManager.value = true }
    fun closeFolderManager() { _showFolderManager.value = false }

    /** Registers a persisted SAF tree permission and rescans (T2). */
    fun addScanFolder(treeUri: String) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                Uri.parse(treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settingsStore.addScanFolder(treeUri)
        scanLocal()
    }

    fun removeScanFolder(treeUri: String) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        settingsStore.removeScanFolder(treeUri)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.removeLocalTracks(
                    repo.cachedLocalTracks().filter { it.uri.startsWith(treeUri) }.map { it.id }
                )
            }
            scanLocal()
        }
    }

    /** Registers a persisted permission for a single picked audio file (T3). */
    fun addManualFiles(uris: List<String>) {
        val ctx = getApplication<Application>()
        uris.forEach { u ->
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    Uri.parse(u),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            settingsStore.addManualUri(u)
        }
        scanLocal()
    }

    fun removeManualFile(uri: String) {
        settingsStore.removeManualUri(uri)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.removeLocalTracks(repo.cachedLocalTracks().filter { it.uri == uri }.map { it.id })
            }
            scanLocal()
        }
    }

    /** Removes a track from the library without touching the file (T4). */
    fun removeFromLibrary(music: MusicInfo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.removeLocalTrack(music.id) }
            _local.value = _local.value.copy(
                allTracks = _local.value.allTracks.filterNot { it.id == music.id }
            )
            recomputeLocal()
            refreshLibrary()
        }
    }

    /** B1/B2: a MediaStore row that needs the system confirm dialog. MainActivity collects this. */
    val deleteRequest = MutableSharedFlow<android.content.IntentSender>(extraBufferCapacity = 1)

    /** Pending system-confirm deletes, persisted so a killed process can reconcile on restart. */
    private val pendingDeletes = mutableSetOf<String>()

    /** Deletes the underlying file (T4). Result reported through [onResult]. */
    fun deleteLocalFile(music: MusicInfo, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { LocalFileDeleter.delete(getApplication(), music) }
            when (result) {
                is com.lxmusic.android.local.DeleteResult.Done -> onDeleteConfirmed(music)
                is com.lxmusic.android.local.DeleteResult.NeedsConfirmation -> {
                    pendingDeletes.add(music.id)
                    deleteRequest.emit(result.sender)
                }
                is com.lxmusic.android.local.DeleteResult.Failed -> onResult(result.reason)
            }
        }
    }

    /** Called by MainActivity after the system delete dialog returns OK. */
    fun onDeleteConfirmed(music: MusicInfo) {
        viewModelScope.launch {
            repo.removeLocalTrack(music.id)
            pendingDeletes.remove(music.id)
            _local.value = _local.value.copy(
                allTracks = _local.value.allTracks.filterNot { it.id == music.id }
            )
            recomputeLocal()
        }
    }

    /** Called when the user cancels the system dialog. */
    fun onDeleteCancelled(music: MusicInfo) {
        pendingDeletes.remove(music.id)
    }

    /** B4: drop cache rows whose backing file no longer exists (crashed confirm flow). */
    fun reconcilePendingDeletes(tracks: List<MusicInfo>) {
        viewModelScope.launch {
            val gone = tracks.filter { it.id in pendingDeletes && !trackExists(it) }
            repo.removeLocalTracks(gone.map { it.id })
            _local.value = _local.value.copy(allTracks = _local.value.allTracks.filterNot { it.id in pendingDeletes && !trackExists(it) })
            gone.forEach { pendingDeletes.remove(it.id) }
            if (gone.isNotEmpty()) recomputeLocal()
        }
    }

    private fun trackExists(m: MusicInfo): Boolean {
        val raw = m.localPath ?: m.extra["uri"] ?: return false
        return runCatching {
            if (raw.startsWith("content://")) {
                getApplication<Application>().contentResolver
                    .openInputStream(android.net.Uri.parse(raw))?.use { true } ?: false
            } else java.io.File(raw.replaceFirst("file://", "")).exists()
        }.getOrDefault(false)
    }

    /** Clears favourites, playlists, history and every preference. */
    fun clearAllData() {
        viewModelScope.launch {
            repo.clearAllUserData()
            settingsStore.clearAll()
            refreshLibrary()
        }
    }

    override fun onCleared() {
        // V6.1.2: 防抖窗口里可能还有一次没落盘的曲线（见 writeEqNow）。
        if (eqDirty) {
            eqPersistJob?.cancel()
            writeEqNow()
        }
        playerController.release()
        super.onCleared()
    }

    private companion object {
        /** 滑杆停手多久之后落盘一次。 */
        const val EQ_PERSIST_DEBOUNCE_MS = 400L
    }
}