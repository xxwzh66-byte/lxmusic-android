package com.lxmusic.android.jsengine

import android.util.Base64
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.lxmusic.android.net.HttpBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Native crypto/HTTP primitives shared by the search runtime and the
 * custom-source runtime. Only the `lxNative.request` call is async (results are
 * pushed back into the page via `window.__lx_cb__`); crypto helpers are sync.
 */
open class SharedNativeBridge(
    private val scope: CoroutineScope,
    private val http: HttpBridge = HttpBridge(),
) {
    private val gson = Gson()

    @JavascriptInterface
    fun request(url: String, optionsJson: String, callbackId: String) {
        val opts: Map<String, Any?> = try {
            gson.fromJson(optionsJson, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) { emptyMap() }
        scope.launch(Dispatchers.IO) {
            try {
                val r = http.execute(url, opts)
                // statusCode, statusMessage, headersJson, bodyString, rawB64
                val js = "window.__lx_cb__(${gson.toJson(callbackId)},null,${r.statusCode}," +
                    "${gson.toJson(r.statusMessage)},${gson.toJson(r.headersJson)}," +
                    "${gson.toJson(r.bodyAsString())},${gson.toJson(Base64.encodeToString(r.raw, Base64.NO_WRAP))})"
                dispatch(js)
            } catch (e: Exception) {
                val js = "window.__lx_cb__(${gson.toJson(callbackId)},${gson.toJson(e.message ?: "request failed")},0,null,null,null,null)"
                dispatch(js)
            }
        }
    }

    @JavascriptInterface
    fun md5(str: String): String =
        MessageDigest.getInstance("MD5").digest(str.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @JavascriptInterface
    fun randomBytes(size: Int): String {
        val b = ByteArray(size); SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    @JavascriptInterface
    fun aesEncrypt(dataB64: String, mode: String, keyB64: String, ivB64: String): String =
        aes(dataB64, mode, keyB64, ivB64, Cipher.ENCRYPT_MODE)

    @JavascriptInterface
    fun aesDecrypt(dataB64: String, mode: String, keyB64: String, ivB64: String): String =
        aes(dataB64, mode, keyB64, ivB64, Cipher.DECRYPT_MODE)

    private fun aes(dataB64: String, mode: String, keyB64: String, ivB64: String, opMode: Int): String {
        val data = Base64.decode(dataB64, Base64.NO_WRAP)
        val key = Base64.decode(keyB64, Base64.NO_WRAP)
        val iv = if (ivB64.isBlank()) ByteArray(16) else Base64.decode(ivB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(mode)
        cipher.init(opMode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
    }

    @JavascriptInterface
    fun inflate(bufB64: String): String {
        val input = Base64.decode(bufB64, Base64.NO_WRAP)
        val inflater = Inflater()
        inflater.setInput(input)
        val bos = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) break
                bos.write(buf, 0, n)
            }
        } finally { inflater.end() }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    @JavascriptInterface
    fun deflate(dataB64: String): String {
        val input = Base64.decode(dataB64, Base64.NO_WRAP)
        val deflater = Deflater()
        deflater.setInput(input); deflater.finish()
        val bos = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) bos.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    /** Subclass hook: inject generated JS into the owning WebView. */
    open fun dispatch(js: String) {}

    /** Overridden by the source runtime to receive lx.send(...) events. */
    @JavascriptInterface
    open fun send(eventName: String, dataJson: String) { /* no-op for search runtime */ }

    /** Overridden by the source runtime to receive action responses. */
    @JavascriptInterface
    open fun onResponse(requestKey: String, ok: Boolean, payloadJson: String, message: String) { /* no-op */ }
}