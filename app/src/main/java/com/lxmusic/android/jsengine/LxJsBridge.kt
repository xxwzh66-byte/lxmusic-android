package com.lxmusic.android.jsengine

import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.lxmusic.android.source.ActionRequest
import kotlinx.coroutines.CoroutineScope

/**
 * The source-script side of the `window.lx` contract. Extends [SharedNativeBridge]
 * (crypto/HTTP) with the source-specific event handling: lx.send(), the action
 * response callback, and injection of the request handler into the page.
 */
class LxJsBridge(
    scope: CoroutineScope,
    private val onEvent: (event: String, json: String) -> Unit,
    private val onActionResult: (requestKey: String, ok: Boolean, payloadJson: String, message: String?) -> Unit,
    private val dispatchJs: (String) -> Unit,
) : SharedNativeBridge(scope) {

    private val gson = Gson()

    override fun dispatch(js: String) { dispatchJs(js) }

    @JavascriptInterface
    override fun send(eventName: String, dataJson: String) { onEvent(eventName, dataJson) }

    @JavascriptInterface
    override fun onResponse(requestKey: String, ok: Boolean, payloadJson: String, message: String) {
        onActionResult(requestKey, ok, payloadJson, message)
    }

    /** Push an action request into the script's registered handler. */
    fun postActionToScript(requestKey: String, request: ActionRequest) {
        val payload = gson.toJson(
            mapOf(
                "requestKey" to requestKey,
                "data" to mapOf(
                    "source" to request.source,
                    "action" to request.action,
                    "info" to request.info,
                ),
            )
        )
        dispatch("window.__lx_handle_request__(${gson.toJson(payload)})")
    }
}