package com.lxmusic.android.net

import com.google.gson.Gson
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Result of an HTTP call, mirroring the desktop `lx.request` callback arguments. */
class HttpResult(
    val statusCode: Int,
    val statusMessage: String,
    val headersJson: String,
    val raw: ByteArray,
) {
    fun bodyAsString(): String = String(raw, Charsets.UTF_8)
}

/**
 * Thin OkHttp wrapper that accepts the loose option map the source scripts pass
 * to `lx.request(url, {method, headers, body, form, formData, timeout}, cb)`.
 */
class HttpBridge(
    private val defaultTimeoutMs: Long = 15_000,
) {
    private val gson = Gson()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun execute(url: String, opts: Map<String, Any?>): HttpResult {
        val method = (opts["method"] as? String ?: "GET").uppercase()
        val timeout = (opts["timeout"] as? Number)?.toLong() ?: defaultTimeoutMs

        val builder = Request.Builder().url(url)

        // headers
        val headers = opts["headers"] as? Map<*, *>
        headers?.forEach { (k, v) -> if (k is String && v != null) builder.header(k, v.toString()) }

        // body handling: body (raw) | form (urlencoded) | formData (multipart)
        val mediaJson = "application/json; charset=utf-8".toMediaType()
        val mediaForm = "application/x-www-form-urlencoded".toMediaType()
        val requestBody: okhttp3.RequestBody? = when {
            opts["formData"] != null -> buildMultipart(opts["formData"] as Map<*, *>)
            opts["form"] != null -> {
                val b = FormBody.Builder()
                (opts["form"] as Map<*, *>).forEach { (k, v) -> if (k is String) b.add(k, v?.toString() ?: "") }
                b.build()
            }
            opts["body"] != null -> {
                when (val body = opts["body"]) {
                    is String -> body.toRequestBody(mediaJson)
                    else -> gson.toJson(body).toRequestBody(mediaJson)
                }
            }
            method in listOf("POST", "PUT", "PATCH") -> "".toRequestBody(mediaJson)
            else -> null
        }

        builder.method(method, requestBody)

        val perCallClient = client.newBuilder()
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .build()

        perCallClient.newCall(builder.build()).execute().use { resp -> return toResult(resp) }
    }

    private fun buildMultipart(map: Map<*, *>): okhttp3.RequestBody {
        val b = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
        map.forEach { (k, v) ->
            if (k is String) b.addFormDataPart(k, v?.toString() ?: "")
        }
        return b.build()
    }

    private fun toResult(resp: Response): HttpResult {
        val headerMap = LinkedHashMap<String, List<String>>()
        for (name in resp.headers.names()) headerMap[name] = resp.headers.values(name)
        val bytes = resp.body?.bytes() ?: ByteArray(0)
        return HttpResult(resp.code, resp.message, gson.toJson(headerMap), bytes)
    }
}
