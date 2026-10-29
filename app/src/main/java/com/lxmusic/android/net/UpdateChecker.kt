package com.lxmusic.android.net

import com.google.gson.JsonParser

/**
 * V1.0.2: 设置页「检查更新」。
 *
 * 一次 GitHub Release，压成更新对话框真正要用的几个字段。[notes] 就是
 * 「更新内容公告」的正文（release 的 `body`），原样交给对话框渲染。
 */
data class UpdateInfo(
    /** release 的 tag，例如 `v1.0.1`。 */
    val tagName: String,
    /** release 标题，缺省时回落到 [tagName]。 */
    val title: String,
    /** 更新内容公告正文（release `body`），可能为空串。 */
    val notes: String,
    /** release 页面地址，「前往下载」按钮跳这里。 */
    val url: String,
    /** 资产里第一个 `.apk` 的文件名；没有 APK 资产时为 null。 */
    val apkName: String?,
    /** 同上资产的字节数，没有则 0。 */
    val apkSize: Long,
)

/** 更新检查失败的可展示原因（网络不通、404、限流……）。[message] 直接进对话框。 */
class UpdateUnavailableException(message: String) : Exception(message)

/**
 * 拉 GitHub Releases 的 `releases/latest`，交给调用方与本机 `versionName` 比较。
 *
 * 仓库 [DEFAULT_REPO] 是私有的，匿名请求会拿到 404。这里不把 404 显示成
 * `Not Found`，而是翻译成一句人话，并支持可选访问令牌（`Authorization: Bearer …`），
 * 由设置页的「访问令牌」字段提供。
 */
class UpdateChecker(
    private val http: HttpBridge = HttpBridge(),
    private val repo: String = DEFAULT_REPO,
) {

    /**
     * 同步拉取最新 release。调用方负责放到 IO 线程。
     *
     * 失败一律以 [Result.failure] 返回且异常是 [UpdateUnavailableException]，
     * 这样界面拿到的 `message` 就是可以直接给用户看的一句话，而不是堆栈。
     */
    fun fetchLatest(token: String? = null): Result<UpdateInfo> = runCatching {
        val headers = LinkedHashMap<String, Any?>()
        headers["Accept"] = "application/vnd.github+json"
        // GitHub API 要求有 User-Agent，缺省会被拒。
        headers["User-Agent"] = "lxmusic-android"
        val t = token?.trim().orEmpty()
        if (t.isNotEmpty()) headers["Authorization"] = "Bearer $t"

        val res = http.execute(
            "https://api.github.com/repos/$repo/releases/latest",
            mapOf("method" to "GET", "headers" to headers),
        )
        when (res.statusCode) {
            200 -> parseRelease(res.bodyAsString())
            404 -> throw UpdateUnavailableException("更新源不可达，或仓库为私有（可在「访问令牌」里填 GitHub 令牌）")
            401 -> throw UpdateUnavailableException("访问令牌无效或已过期")
            403, 429 -> throw UpdateUnavailableException("GitHub 限流，请稍后再试（HTTP ${res.statusCode}）")
            else -> throw UpdateUnavailableException("更新源返回 HTTP ${res.statusCode}")
        }
    }.recoverCatching { e ->
        if (e is UpdateUnavailableException) throw e
        throw UpdateUnavailableException("检查更新失败：${e.message ?: e.javaClass.simpleName}")
    }

    companion object {
        const val DEFAULT_REPO = "xxwzh66-byte/lxmusic-android"

        /**
         * 解析 `releases/latest` 的响应体。字段缺失一律降级，不抛异常：
         * 拿不到 title 就用 tag，拿不到 notes 就当空串，资产里没有 `.apk` 就是 null。
         */
        internal fun parseRelease(json: String): UpdateInfo {
            val root = JsonParser.parseString(json).asJsonObject
            fun str(key: String): String =
                root.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: ""

            val tag = str("tag_name")
            val assets = root.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray
            var apkName: String? = null
            var apkSize = 0L
            assets?.forEach { el ->
                if (apkName != null) return@forEach
                val o = el.asJsonObject
                val name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkName = name
                    apkSize = o.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                }
            }

            return UpdateInfo(
                tagName = tag,
                title = str("name").ifBlank { tag },
                notes = str("body"),
                url = str("html_url"),
                apkName = apkName,
                apkSize = apkSize,
            )
        }

        /**
         * 版本号比较：`v1.0.2` 与 `1.0.2` 视为同一个；缺位补 0；
         * 数字位按数值比（所以 `1.0.10 > 1.0.2`），预发布后缀按字典比，
         * 且「无后缀 > 有后缀」（`1.0.2 > 1.0.2-beta`）。
         */
        fun compareVersions(a: String, b: String): Int {
            val sa = segments(a)
            val sb = segments(b)
            for (i in 0 until maxOf(sa.size, sb.size)) {
                val x = sa.getOrNull(i) ?: ""
                val y = sb.getOrNull(i) ?: ""
                val nx = x.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                val ny = y.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                if (nx != ny) return nx.compareTo(ny)
                val tx = x.dropWhile { it.isDigit() }
                val ty = y.dropWhile { it.isDigit() }
                if (tx != ty) return when {
                    tx.isEmpty() -> 1
                    ty.isEmpty() -> -1
                    else -> tx.compareTo(ty)
                }
            }
            return 0
        }

        /** `[remote]` 是否比 `[current]` 更新。 */
        fun isNewer(remote: String, current: String): Boolean = compareVersions(remote, current) > 0

        private fun segments(v: String): List<String> {
            val s = v.trim().removePrefix("v").removePrefix("V")
            val main = s.substringBefore('-')
            val pre = s.substringAfter('-', "")
            val parts = main.split('.').map { it.trim() }.toMutableList()
            if (pre.isNotEmpty()) parts.add(pre)
            return parts
        }
    }
}
