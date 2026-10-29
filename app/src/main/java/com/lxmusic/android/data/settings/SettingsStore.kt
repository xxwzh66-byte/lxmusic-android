package com.lxmusic.android.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How the app resolves light/dark colours. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Density of list rows. */
enum class ListStyle(val label: String) { COMPACT("紧凑"), STANDARD("标准"), LARGE("大封面") }

/** Preferred playback quality for online sources. */
enum class AudioQuality(val label: String) { Q128("128k"), Q320("320k"), FLAC("flac") }

/**
 * Which stores a track that just started should be appended to.
 *
 * V5.3.6: these are independent. 最近播放 (`play_history`) is the opt-outable
 * privacy list; the play-count statistics (`play_event`) are a separate
 * preference and must not be silenced by the first one.
 */
data class RecordPolicy(val history: Boolean, val stats: Boolean)

/** The recording policy this snapshot describes. */
fun AppSettings.recordPolicy(): RecordPolicy =
    RecordPolicy(history = recordHistory, stats = recordStats)

/** Immutable snapshot of every user preference. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: String = "blue",
    val listStyle: ListStyle = ListStyle.STANDARD,
    /** Persisted SAF tree URIs the user granted access to. */
    val scanFolders: Set<String> = emptySet(),
    /** Individually added audio document URIs. */
    val manualUris: Set<String> = emptySet(),
    val autoScanOnStart: Boolean = true,
    val readEmbeddedCover: Boolean = true,
    val quality: AudioQuality = AudioQuality.Q320,
    /** V5.3.3: append to the 最近播放 list (`play_history` table). */
    val recordHistory: Boolean = true,
    /** V5.3.6: append a row to the play-count statistics (`play_event` table). */
    val recordStats: Boolean = true,
    val pauseOnHeadsetUnplug: Boolean = true,
    val lyricFontScale: Float = 1.0f,
    val lyricOffsetMs: Int = 0,
    val preferLrcFile: Boolean = true,
    val playerSkin: String = "VINYL",
    /** V4-3: show the big song-name/artist rows under the vinyl stage. */
    val showPlayerMeta: Boolean = true,
    /** V5.1-3: last played track id + resume position (null = nothing to restore). */
    val lastPlayedId: String? = null,
    val lastPlayedPosMs: Long = 0L,
    /** V5.1-4: persisted playback modes ("OFF"/"ALL"/"ONE" + shuffle). */
    val repeatMode: String = "OFF",
    val shuffle: Boolean = false,
    /**
     * V6.1: 用户调好的均衡器曲线。
     *
     * 效果器本身绑在音频会话上、每次播放器重建都会归零，所以「记住设置」这件事
     * 只能由这里承担：启动时 [AppSettings.eq] 交给 `EqualizerController.setProfile`，
     * 每次用户改动再回写。
     */
    val eq: EqProfile = EqProfile.FLAT,

    /** V1.0.2: GitHub 访问令牌。仓库为私有，匿名拉不到 releases；留空则匿名请求。 */
    val updateToken: String = "",
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Application-scoped preference store backed by DataStore.
 *
 * A single instance lives on [com.lxmusic.android.LxApp]; consumers observe
 * [state] and call the setter helpers, which persist asynchronously.
 */
class SettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /**
     * True once the first DataStore read has been applied to [state].
     *
     * [state] starts at the defaults, so anything that must not act on a
     * not-yet-loaded value (for example the changelog check) should wait for
     * this first.
     */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            appContext.settingsDataStore.data.collect { prefs ->
                _state.value = prefs.toSettings()
                _loaded.value = true
            }
        }
    }

    // ---------------- setters ----------------

    fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME_MODE] = mode.name }
    fun setThemeColor(id: String) = edit { it[KEY_THEME_COLOR] = id }
    fun setListStyle(style: ListStyle) = edit { it[KEY_LIST_STYLE] = style.name }
    fun setAutoScan(on: Boolean) = edit { it[KEY_AUTO_SCAN] = on }
    fun setReadEmbeddedCover(on: Boolean) = edit { it[KEY_READ_COVER] = on }
    fun setQuality(q: AudioQuality) = edit { it[KEY_QUALITY] = q.name }
    fun setRecordHistory(on: Boolean) = edit { it[KEY_RECORD_HISTORY] = on }
    fun setRecordStats(on: Boolean) = edit { it[KEY_RECORD_STATS] = on }
    fun setPauseOnHeadsetUnplug(on: Boolean) = edit { it[KEY_PAUSE_UNPLUG] = on }
    fun setLyricFontScale(v: Float) = edit { it[KEY_LYRIC_SCALE] = v }
    fun setLyricOffset(ms: Int) = edit { it[KEY_LYRIC_OFFSET] = ms }
    fun setPreferLrcFile(on: Boolean) = edit { it[KEY_PREFER_LRC] = on }

    fun setPlayerSkin(skin: String) = edit { it[KEY_PLAYER_SKIN] = skin }

    fun setShowPlayerMeta(on: Boolean) = edit { it[KEY_SHOW_PLAYER_META] = on }

    /** V5.1-3: persist the currently playing track and its resume position. */
    fun setLastPlayed(id: String?, posMs: Long) = edit {
        if (id == null) {
            it.remove(KEY_LAST_PLAYED_ID)
            it.remove(KEY_LAST_PLAYED_POS)
        } else {
            it[KEY_LAST_PLAYED_ID] = id
            it[KEY_LAST_PLAYED_POS] = posMs
        }
    }

    /** V5.1-4: persist repeat/shuffle the moment the user flips them. */
    fun setRepeatMode(mode: String) = edit { it[KEY_REPEAT_MODE] = mode }
    fun setShuffle(on: Boolean) = edit { it[KEY_SHUFFLE] = on }

    /** V6.1: persist the equalizer curve so it survives an app restart. */
    fun setEq(profile: EqProfile) = edit { writeEq(it, profile) }

    /** V1.0.2: GitHub 访问令牌（私有仓的 releases 要鉴权才能拉到）。 */
    fun setUpdateToken(token: String) = edit { writeUpdateToken(it, token) }

    fun addScanFolder(uri: String) = edit { prefs ->
        prefs[KEY_SCAN_FOLDERS] = (prefs[KEY_SCAN_FOLDERS] ?: emptySet()) + uri
    }

    fun removeScanFolder(uri: String) = edit { prefs ->
        prefs[KEY_SCAN_FOLDERS] = (prefs[KEY_SCAN_FOLDERS] ?: emptySet()) - uri
    }

    fun addManualUri(uri: String) = edit { prefs ->
        prefs[KEY_MANUAL_URIS] = (prefs[KEY_MANUAL_URIS] ?: emptySet()) + uri
    }

    fun removeManualUri(uri: String) = edit { prefs ->
        prefs[KEY_MANUAL_URIS] = (prefs[KEY_MANUAL_URIS] ?: emptySet()) - uri
    }

    /** Wipes every preference (used by "clear data"). */
    fun clearAll() = edit { it.clear() }

    private inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { appContext.settingsDataStore.edit { block(it) } }
    }

    companion object {
        @Volatile private var INSTANCE: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsStore(context).also { INSTANCE = it }
            }

        /**
         * Preferences -> 不可变快照。
         *
         * 写入侧（`setEq` 等）和读取侧用的是同一批 `KEY_*`，但写错一个键名不会有
         * 任何编译错误、运行时也不报错——那个字段只是永远读回默认值。用户报的
         * 「均衡器不保存」正是这一类缺陷，所以读取路径本身必须有测试走过。
         *
         * V6.2: 放在 companion 里（而不是做成实例方法），因为纯 JVM 单测构造不出
         * [SettingsStore]（要 Context），但能构造 `Preferences`。
         */
        internal fun readSettings(prefs: Preferences): AppSettings = prefs.toSettings()

        /**
         * 均衡器曲线的写入侧，抽出来是为了让测试能把「写进去的键」与
         * [toSettings] 读的键对上。两侧共用同一批 `KEY_EQ_*`，写错一个名字
         * 既不会编译失败也不抛异常——那个字段只是永远读回默认值，而用户看到
         * 的就是「均衡器不保存」。
         */
        internal fun writeEq(prefs: MutablePreferences, profile: EqProfile) {
            prefs[KEY_EQ_ENABLED] = profile.enabled
            prefs[KEY_EQ_PRESET] = profile.preset ?: ""
            prefs[KEY_EQ_BANDS] = EqProfile.encodeBands(profile.bands)
            prefs[KEY_EQ_BASS] = profile.bass
            prefs[KEY_EQ_VIRTUALIZER] = profile.virtualizer
            prefs[KEY_EQ_LOUDNESS] = profile.loudnessMb
        }

        /**
         * V1.0.2: 访问令牌的写入侧，与 [writeEq] 同一个理由抽出来——
         * 读写两侧键名拼错不报错，只是永远读回默认值。
         */
        internal fun writeUpdateToken(prefs: MutablePreferences, token: String) {
            prefs[KEY_UPDATE_TOKEN] = token.trim()
        }

        private fun Preferences.toSettings(): AppSettings = AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(this[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            themeColor = this[KEY_THEME_COLOR] ?: "blue",
            listStyle = runCatching { ListStyle.valueOf(this[KEY_LIST_STYLE] ?: ListStyle.STANDARD.name) }
                .getOrDefault(ListStyle.STANDARD),
            scanFolders = this[KEY_SCAN_FOLDERS] ?: emptySet(),
            manualUris = this[KEY_MANUAL_URIS] ?: emptySet(),
            autoScanOnStart = this[KEY_AUTO_SCAN] ?: true,
            readEmbeddedCover = this[KEY_READ_COVER] ?: true,
            quality = runCatching { AudioQuality.valueOf(this[KEY_QUALITY] ?: AudioQuality.Q320.name) }
                .getOrDefault(AudioQuality.Q320),
            recordHistory = this[KEY_RECORD_HISTORY] ?: true,
            recordStats = this[KEY_RECORD_STATS] ?: true,
            pauseOnHeadsetUnplug = this[KEY_PAUSE_UNPLUG] ?: true,
            lyricFontScale = this[KEY_LYRIC_SCALE] ?: 1.0f,
            lyricOffsetMs = this[KEY_LYRIC_OFFSET] ?: 0,
            preferLrcFile = this[KEY_PREFER_LRC] ?: true,
            // V4-1 data migration: legacy skins (TAPE/WAVE/MINIMAL/CARD) normalize to
            // VINYL on read — that normalization lives in PlayerSkin.from, not here.
            playerSkin = this[KEY_PLAYER_SKIN] ?: "VINYL",
            showPlayerMeta = this[KEY_SHOW_PLAYER_META] ?: true,
            lastPlayedId = this[KEY_LAST_PLAYED_ID],
            lastPlayedPosMs = this[KEY_LAST_PLAYED_POS] ?: 0L,
            repeatMode = runCatching {
                val v = this[KEY_REPEAT_MODE] ?: "OFF"
                if (v in setOf("OFF", "ALL", "ONE")) v else "OFF"
            }.getOrDefault("OFF"),
            shuffle = this[KEY_SHUFFLE] ?: false,
            eq = EqProfile(
                enabled = this[KEY_EQ_ENABLED] ?: false,
                preset = this[KEY_EQ_PRESET]?.takeIf { it.isNotBlank() },
                bands = EqProfile.decodeBands(this[KEY_EQ_BANDS]),
                bass = this[KEY_EQ_BASS] ?: 0,
                virtualizer = this[KEY_EQ_VIRTUALIZER] ?: 0,
                loudnessMb = this[KEY_EQ_LOUDNESS] ?: 0,
            ),
            updateToken = this[KEY_UPDATE_TOKEN] ?: "",
        )

        internal val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        internal val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        internal val KEY_LIST_STYLE = stringPreferencesKey("list_style")
        internal val KEY_SCAN_FOLDERS = stringSetPreferencesKey("scan_folders")
        internal val KEY_MANUAL_URIS = stringSetPreferencesKey("manual_uris")
        internal val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan")
        internal val KEY_READ_COVER = booleanPreferencesKey("read_embedded_cover")
        internal val KEY_QUALITY = stringPreferencesKey("quality")
        internal val KEY_RECORD_HISTORY = booleanPreferencesKey("record_history")
        internal val KEY_RECORD_STATS = booleanPreferencesKey("record_stats")
        internal val KEY_PAUSE_UNPLUG = booleanPreferencesKey("pause_on_headset_unplug")
        internal val KEY_LYRIC_SCALE = floatPreferencesKey("lyric_font_scale")
        internal val KEY_LYRIC_OFFSET = intPreferencesKey("lyric_offset_ms")
        internal val KEY_PREFER_LRC = booleanPreferencesKey("prefer_lrc_file")
        internal val KEY_PLAYER_SKIN = stringPreferencesKey("player_skin")
        internal val KEY_SHOW_PLAYER_META = booleanPreferencesKey("show_player_meta")
        internal val KEY_LAST_PLAYED_ID = stringPreferencesKey("last_played_id")
        internal val KEY_LAST_PLAYED_POS = longPreferencesKey("last_played_pos_ms")
        internal val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        internal val KEY_SHUFFLE = booleanPreferencesKey("shuffle")
        internal val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        internal val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        internal val KEY_EQ_BANDS = stringPreferencesKey("eq_bands")
        internal val KEY_EQ_BASS = intPreferencesKey("eq_bass")
        internal val KEY_EQ_VIRTUALIZER = intPreferencesKey("eq_virtualizer")
        internal val KEY_EQ_LOUDNESS = intPreferencesKey("eq_loudness")
        internal val KEY_UPDATE_TOKEN = stringPreferencesKey("update_token")
        }
}
