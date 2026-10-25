package com.lxmusic.android.source

/**
 * Model classes mirroring the lx-music-desktop "user api" (custom source) protocol.
 * See src/main/modules/userApi/renderer/preload.js in lx-music-desktop.
 */

/** Music sources supported by LX Music sources. 'local' is special (handled natively). */
enum class LxSource(val id: String) {
    KW("kw"),
    KG("kg"),
    TX("tx"),
    WY("wy"),
    MG("mg"),
    XM("xm"),
    LOCAL("local");

    companion object {
        fun fromId(id: String): LxSource? = entries.firstOrNull { it.id == id }
        val online: List<LxSource> = listOf(KW, KG, TX, WY, MG)
    }
}

/** Audio qualities, ordered from lowest to highest. */
enum class LxQuality(val id: String) {
    K128("128k"),
    K320("320k"),
    FLAC("flac"),
    FLAC24BIT("flac24bit");

    companion object {
        fun fromId(id: String): LxQuality? = entries.firstOrNull { it.id == id }
    }
}

/** Actions a source can perform. */
enum class LxAction(val id: String) {
    MUSIC_URL("musicUrl"),
    LYRIC("lyric"),
    PIC("pic");

    companion object {
        fun fromId(id: String): LxAction? = entries.firstOrNull { it.id == id }
    }
}

/** Declared capabilities of one source inside a loaded script. */
data class SourceCapability(
    val source: LxSource,
    val type: String = "music",
    val actions: List<LxAction>,
    val qualities: List<LxQuality>,
)

/** Metadata parsed from the leading block comment of a user api script. */
data class ScriptInfo(
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val homepage: String,
)

/** A loaded/available user api script. */
data class UserApiInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    var allowShowUpdateAlert: Boolean = true,
    /** Only populated for scripts persisted in-app (not for the bundled sample). */
    val script: String? = null,
)

/** The payload received from a script when it calls lx.send(EVENT_NAMES.inited, info). */
data class InitInfo(
    val sources: Map<String, Map<String, Any?>>,
    val message: String? = null,
    val openDevTools: Boolean = false,
)

/** The request a script must answer: {source, action, info}. */
data class ActionRequest(
    val source: String,
    val action: String,
    val info: Map<String, Any?>,
)

/** Result of an action request. */
sealed class ActionResult {
    data class MusicUrl(val type: String, val url: String) : ActionResult()
    data class LyricInfo(
        val lyric: String,
        val tlyric: String?,
        val rlyric: String?,
        val lxlyric: String?,
    ) : ActionResult()
    data class PicUrl(val url: String) : ActionResult()
}

/** Status reported by a loaded source (mirrors LX.UserApi.UserApiStatus). */
data class SourceStatus(
    val status: Boolean,
    val apiInfo: UserApiInfo? = null,
    val message: String? = null,
)
