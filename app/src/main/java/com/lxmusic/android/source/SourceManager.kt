package com.lxmusic.android.source

import android.content.Context
import com.google.gson.Gson
import com.lxmusic.android.FeatureFlags
import com.lxmusic.android.LxApp
import com.lxmusic.android.data.db.UserApiEntity
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.jsengine.LxScriptRuntime
import com.lxmusic.android.jsengine.LxSearchRuntime
import com.lxmusic.android.net.HttpBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the two WebView engines and exposes a single facade used by the UI:
 *  - search(): queries the bundled LX search providers.
 *  - resolveMusicUrl()/resolveLyric()/resolvePic(): delegate to the loaded
 *    custom source script (the "自定义源" loaded by the user).
 *
 * Mirrors the role of `store/apiSource` + `core/music` in lx-music-desktop.
 */
class SourceManager(private val context: Context) {

    private val gson = Gson()
    private val http = HttpBridge()

    /** Created only on first real use so no WebView process exists while online is off. */
    private var searchRuntime: LxSearchRuntime? = null
    private val searchLock = Mutex()

    private var scriptRuntime: LxScriptRuntime? = null
    private var loadedApiId: String? = null
    private val scriptLock = Mutex()

    val capabilities: List<SourceCapability>
        get() = scriptRuntime?.capabilities ?: emptyList()

    // ---------------- search ----------------

    suspend fun search(source: LxSource, keyword: String, page: Int = 1, limit: Int = 30): List<MusicInfo> {
        if (!FeatureFlags.ONLINE_ENABLED) {
            throw IllegalStateException("在线搜索已关闭")
        }
        val runtime = searchLock.withLock {
            searchRuntime ?: LxSearchRuntime(context).also {
                it.start()
                searchRuntime = it
            }
        }
        return runtime.search(source.id, keyword, page, limit)
    }

    /** Releases the search WebView (used when online is disabled or on low memory). */
    fun releaseSearchEngine() {
        searchRuntime?.release()
        searchRuntime = null
    }

    // ---------------- custom source ----------------

    /** Load a user-api script (raw JS). Returns true if it declared capabilities. */
    suspend fun loadScript(apiId: String, script: String): Boolean {
        if (!FeatureFlags.CUSTOM_SOURCE_ENABLED) return false
        scriptLock.withLock {
            unloadLocked()
            val rt = LxScriptRuntime(context)
            rt.start()
            // Wait briefly for the page to be ready, then evaluate the script.
            kotlinx.coroutines.delay(400)
            val deferred: CompletableDeferred<Boolean> = rt.loadScript(script)
            val ok = try { deferred.await() } catch (_: Exception) { false }
            scriptRuntime = rt
            loadedApiId = apiId
            return ok && rt.capabilities.isNotEmpty()
        }
    }

    fun unload() {
        scriptRuntime?.release()
        scriptRuntime = null
        loadedApiId = null
    }

    private fun unloadLocked() {
        scriptRuntime?.release()
        scriptRuntime = null
        loadedApiId = null
    }

    fun isSourceSupported(source: LxSource): Boolean =
        capabilities.any { it.source == source && LxAction.MUSIC_URL in it.actions }

    suspend fun resolveMusicUrl(music: MusicInfo, quality: LxQuality): ActionResult.MusicUrl {
        val rt = scriptRuntime ?: throw IllegalStateException("No custom source loaded")
        val cap = capabilities.firstOrNull { it.source == LxSource.fromId(music.source) }
            ?: throw IllegalStateException("Source ${music.source} not supported by current script")
        val q = if (quality in cap.qualities) quality else cap.qualities.firstOrNull() ?: LxQuality.K128
        val info = buildMusicInfoPayload(music, q)
        return rt.invoke(music.source, LxAction.MUSIC_URL.id, info) as ActionResult.MusicUrl
    }

    suspend fun resolveLyric(music: MusicInfo): ActionResult.LyricInfo {
        val rt = scriptRuntime ?: throw IllegalStateException("No custom source loaded")
        val info = buildMusicInfoPayload(music, LxQuality.K128)
        return rt.invoke(music.source, LxAction.LYRIC.id, info) as ActionResult.LyricInfo
    }

    suspend fun resolvePic(music: MusicInfo): ActionResult.PicUrl {
        val rt = scriptRuntime ?: throw IllegalStateException("No custom source loaded")
        val info = buildMusicInfoPayload(music, LxQuality.K128)
        return rt.invoke(music.source, LxAction.PIC.id, info) as ActionResult.PicUrl
    }

    /**
     * Build the `info` object handed to the script, matching the shape LX uses
     * in core/music (toOldMusicInfo).
     */
    private fun buildMusicInfoPayload(music: MusicInfo, quality: LxQuality): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>(
            "id" to music.id,
            "source" to music.source,
            "songmid" to music.songmid,
            "name" to music.name,
            "singer" to music.singer,
            "albumName" to music.albumName,
            "albumId" to music.albumId,
            "interval" to music.interval,
            "img" to music.img,
            "type" to quality.id,
            "types" to music.qualitys.map { (k, v) ->
                linkedMapOf<String, Any?>("type" to k, "size" to v.size, "hash" to v.hash)
            },
        )
        base.putAll(music.extra)
        return base
    }

    // ---------------- user api persistence ----------------

    suspend fun saveUserApi(script: String): UserApiEntity {
        val info = parseScriptInfo(script)
        val id = "user_api_" + System.currentTimeMillis()
        val entity = UserApiEntity(
            id = id,
            name = info.name,
            description = info.description,
            version = info.version,
            author = info.author,
            homepage = info.homepage,
            script = script,
            createdAt = System.currentTimeMillis(),
        )
        LxApp.instance.db.userApiDao().upsert(entity)
        return entity
    }

    suspend fun listUserApis(): List<UserApiEntity> = LxApp.instance.db.userApiDao().all()
    suspend fun deleteUserApi(id: String) = LxApp.instance.db.userApiDao().delete(id)

    private fun parseScriptInfo(script: String): ScriptInfo {
        val header = Regex("^/\\*[\\s\\S]+?\\*/").find(script)?.value ?: ""
        fun field(key: String): String =
            Regex("@$key\\s+(.+)").find(header)?.groupValues?.get(1)?.trim() ?: ""
        return ScriptInfo(
            name = field("name").ifEmpty { "user_api_${System.currentTimeMillis()}" },
            description = field("description"),
            version = field("version"),
            author = field("author"),
            homepage = field("homepage"),
        )
    }
}