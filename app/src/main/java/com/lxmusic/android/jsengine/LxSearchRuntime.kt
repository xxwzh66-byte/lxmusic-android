package com.lxmusic.android.jsengine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.model.QualityInfo
import com.lxmusic.android.net.HttpBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the bundled LX search modules inside a WebView.
 *
 * The page loads `search_host.js` (Node shims + httpFetch) followed by
 * `lx-search-bundle.js` (the lx-music-desktop search providers).
 */
class LxSearchRuntime(private val context: Context) {

    private val TAG = "LxSearchRuntime"
    private val main = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var webView: WebView
    private val pending = ConcurrentHashMap<String, CompletableDeferred<List<MusicInfo>>>()
    private val ready = CompletableDeferred<Boolean>()

    private val bridge = SearchBridge()

    /** Shared crypto/HTTP bridge whose dispatch targets this runtime's WebView. */
    private inner class NativeBridge : SharedNativeBridge(scope) {
        override fun dispatch(js: String) { main.post { webView.evaluateJavascript(js, null) } }
    }
    private val nativeBridge by lazy { NativeBridge() }

    inner class SearchBridge {
        @JavascriptInterface
        fun searchResult(requestId: String, ok: Boolean, json: String, message: String?) {
            val d = pending.remove(requestId) ?: return
            if (!ok) { d.completeExceptionally(RuntimeException(message ?: "search failed")); return }
            try {
                @Suppress("UNCHECKED_CAST")
                val root = gson.fromJson(json, Map::class.java) as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val list = root["list"] as? List<Map<String, Any?>> ?: emptyList()
                d.complete(list.map { toMusicInfo(it) })
            } catch (e: Exception) {
                d.completeExceptionally(e)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        }
        webView.addJavascriptInterface(bridge, "lxSearchNative")
        webView.addJavascriptInterface(nativeBridge, "lxNative")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectAssets()
            }
        }
        webView.loadDataWithBaseURL("https://lx.local/", HTML, "text/html", "utf-8", null)
    }

    suspend fun search(sourceId: String, keyword: String, page: Int, limit: Int, timeoutMs: Long = 20_000): List<MusicInfo> {
        if (!ready.await()) throw RuntimeException("search engine not ready")
        val reqId = "q_" + UUID.randomUUID().toString().replace("-", "")
        val d = CompletableDeferred<List<MusicInfo>>()
        pending[reqId] = d
        main.post {
            webView.evaluateJavascript(
                "window.__lx_search__(${js(reqId)}, ${js(sourceId)}, ${js(keyword)}, $page, $limit);", null
            )
        }
        return try { withTimeout(timeoutMs) { d.await() } } finally { pending.remove(reqId) }
    }

    private fun js(s: String): String = gson.toJson(s)

    private fun toMusicInfo(m: Map<String, Any?>): MusicInfo {
        val qualitys = LinkedHashMap<String, QualityInfo>()
        @Suppress("UNCHECKED_CAST")
        (m["qualitys"] as? Map<String, Map<String, Any?>>)?.forEach { (k, v) ->
            qualitys[k] = QualityInfo(k, v["size"] as? String, v["hash"] as? String)
        }
        val extra = LinkedHashMap<String, String>()
        @Suppress("UNCHECKED_CAST")
        (m["extra"] as? Map<String, Any?>)?.forEach { (k, v) -> if (v != null) extra[k] = v.toString() }
        return MusicInfo(
            id = m["id"] as? String ?: "",
            source = m["source"] as? String ?: "",
            songmid = m["songmid"] as? String ?: "",
            name = m["name"] as? String ?: "",
            singer = m["singer"] as? String ?: "",
            albumName = m["albumName"] as? String ?: "",
            albumId = m["albumId"] as? String ?: "",
            interval = m["interval"] as? String ?: "00:00",
            img = m["img"] as? String,
            qualitys = qualitys,
            extra = extra,
        )
    }

    private fun injectAssets() {
        try {
            val host = context.assets.open("jsengine/search_host.js").bufferedReader().use { it.readText() }
            val bundle = context.assets.open("jsengine/lx-search-bundle.js").bufferedReader().use { it.readText() }
            webView.evaluateJavascript(host) {
                webView.evaluateJavascript(bundle) {
                    Log.d(TAG, "search engine assets injected")
                    ready.complete(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to inject search assets", e)
            ready.completeExceptionally(e)
        }
    }

    fun release() { main.post { try { webView.destroy() } catch (_: Exception) {} } }

    companion object {
        private const val HTML = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body></body></html>"
    }
}