package com.lxmusic.android.jsengine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.lxmusic.android.source.ActionRequest
import com.lxmusic.android.source.ActionResult
import com.lxmusic.android.source.LxAction
import com.lxmusic.android.source.LxQuality
import com.lxmusic.android.source.LxSource
import com.lxmusic.android.source.SourceCapability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Hosts a single LX Music custom source script inside an offscreen WebView and
 * implements the desktop "user api" protocol (see lx-music-desktop
 * src/main/modules/userApi/renderer/preload.js).
 *
 * Lifecycle:
 *  1. loadScript() evaluates the user's script after injecting window.lx.
 *  2. The script calls lx.send('inited', {sources:{...}}) -> capabilities.
 *  3. invoke() pushes {source, action, info} to the script's handler, awaits
 *     the resolved url/lyric/pic via lx.onResponse.
 */
class LxScriptRuntime(private val context: Context) {

    private val TAG = "LxScriptRuntime"
    private val main = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var webView: WebView
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ActionResult>>()

    @Volatile var capabilities: List<SourceCapability> = emptyList(); private set
    @Volatile var initError: String? = null; private set

    private val initDeferred = CompletableDeferred<Boolean>()
    private var shimInjected = false

    private val bridge: LxJsBridge by lazy {
        LxJsBridge(
            scope = scope,
            onEvent = { name, json -> handleEvent(name, json) },
            onActionResult = { key, ok, payload, msg -> onResponse(key, ok, payload, msg) },
            dispatchJs = { js -> main.post { webView.evaluateJavascript(js, null) } },
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.addJavascriptInterface(bridge, "lxNative")
                webView.evaluateJavascript(SHIM_JS) { shimInjected = true }
            }
        }
        webView.loadDataWithBaseURL("https://lx.local/", BLANK_HTML, "text/html", "utf-8", null)
    }

    /** Evaluate the source script; resolves true when the script reports inited. */
    fun loadScript(script: String): CompletableDeferred<Boolean> {
        main.post {
            try {
                webView.evaluateJavascript(script) { result -> Log.d(TAG, "script eval: $result") }
            } catch (e: Exception) {
                initError = e.message
                initDeferred.complete(false)
            }
        }
        return initDeferred
    }

    suspend fun invoke(source: String, action: String, info: Map<String, Any?>, timeoutMs: Long = 20_000): ActionResult {
        val key = "req_" + UUID.randomUUID().toString().replace("-", "")
        val d = CompletableDeferred<ActionResult>()
        pending[key] = d
        main.post { bridge.postActionToScript(key, ActionRequest(source, action, info)) }
        return try { withTimeout(timeoutMs) { d.await() } } finally { pending.remove(key) }
    }

    private fun handleEvent(eventName: String, json: String) {
        if (eventName.endsWith("inited") && !eventName.contains("init")) return
        when {
            eventName == "inited" || eventName.endsWith("inited") -> handleInited(json)
            eventName.endsWith("updateAlert") -> Log.i(TAG, "update alert: $json")
        }
    }

    private fun handleInited(json: String) {
        try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(json, Map::class.java) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val sources = map["sources"] as? Map<String, Any?> ?: emptyMap()
            val caps = mutableListOf<SourceCapability>()
            for (src in LxSource.online) {
                @Suppress("UNCHECKED_CAST")
                val s = sources[src.id] as? Map<String, Any?> ?: continue
                val type = s["type"] as? String ?: continue
                @Suppress("UNCHECKED_CAST")
                val actions = ((s["actions"] as? List<Any?>) ?: emptyList()).mapNotNull { LxAction.fromId(it.toString()) }
                @Suppress("UNCHECKED_CAST")
                val qualitys = ((s["qualitys"] as? List<Any?>) ?: emptyList()).mapNotNull { LxQuality.fromId(it.toString()) }
                caps.add(SourceCapability(src, type, actions, qualitys))
            }
            capabilities = caps
            if (caps.isEmpty()) initError = "Script declared no supported sources"
            initDeferred.complete(caps.isNotEmpty())
        } catch (e: Exception) {
            initError = "Invalid init payload: ${e.message}"
            initDeferred.complete(false)
        }
    }

    private fun onResponse(requestKey: String, ok: Boolean, payloadJson: String, message: String?) {
        val d = pending.remove(requestKey) ?: return
        if (!ok) { d.completeExceptionally(RuntimeException(message ?: "action failed")); return }
        try {
            @Suppress("UNCHECKED_CAST")
            val m = gson.fromJson(payloadJson, Map::class.java) as Map<String, Any?>
            when (m["action"]) {
                "musicUrl" -> {
                    @Suppress("UNCHECKED_CAST")
                    val data = m["data"] as? Map<String, Any?>
                    val url = data?.get("url") as? String
                    if (url.isNullOrEmpty()) d.completeExceptionally(RuntimeException("empty url"))
                    else d.complete(ActionResult.MusicUrl(data["type"] as? String ?: "128k", url))
                }
                "lyric" -> {
                    @Suppress("UNCHECKED_CAST")
                    val dt = m["data"] as? Map<String, Any?> ?: emptyMap()
                    d.complete(
                        ActionResult.LyricInfo(
                            lyric = dt["lyric"] as? String ?: "",
                            tlyric = dt["tlyric"] as? String,
                            rlyric = dt["rlyric"] as? String,
                            lxlyric = dt["lxlyric"] as? String,
                        )
                    )
                }
                "pic" -> {
                    val url = m["data"] as? String
                    if (url.isNullOrEmpty()) d.completeExceptionally(RuntimeException("empty pic"))
                    else d.complete(ActionResult.PicUrl(url))
                }
                else -> d.completeExceptionally(RuntimeException("unknown action"))
            }
        } catch (e: Exception) { d.completeExceptionally(e) }
    }

    fun release() { main.post { try { webView.destroy() } catch (_: Exception) {} } }

    companion object {
        private const val BLANK_HTML = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body></body></html>"

        /** window.lx shim matching lx-music-desktop preload.js, routed to lxNative. */
        private val SHIM_JS = """
        (function() {
          var EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' };
          var callbacks = {};
          var cbSeq = 0;
          var requestHandler = null;
          var initedCalled = false;
          var updateAlertCalled = false;
          function b64ToBytes(b64){var bin=atob(b64);var a=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)a[i]=bin.charCodeAt(i);return a;}
          function bytesToB64(b){var s='';for(var i=0;i<b.length;i++)s+=String.fromCharCode(b[i]);return btoa(s);}
          function bufLike(bytes){bytes=bytes||new Uint8Array(0);return {__isBuf:true,bytes:bytes,length:bytes.length,
            toString:function(enc){if(enc==='hex')return Array.prototype.map.call(bytes,function(x){return('0'+x.toString(16)).slice(-2);}).join('');
              if(enc==='base64')return bytesToB64(bytes);if(enc==='latin1'||enc==='binary'){var s='';for(var i=0;i<bytes.length;i++)s+=String.fromCharCode(bytes[i]);return s;}
              return new TextDecoder('utf-8').decode(bytes);},
            slice:function(a,b){return bufLike(bytes.slice(a,b));}};}
          window.__lx_cb__ = function(cbId,err,statusCode,statusMessage,headersJson,bodyStr,rawB64){
            var cb=callbacks[cbId]; if(!cb)return; delete callbacks[cbId];
            if(err){cb(new Error(err),null,null);return;}
            var headers={};try{headers=JSON.parse(headersJson||'{}');}catch(e){}
            var raw=bufLike(b64ToBytes(rawB64||'')); var body=bodyStr; try{body=JSON.parse(bodyStr);}catch(e){}
            cb(null,{statusCode:statusCode,statusMessage:statusMessage,headers:headers,bytes:raw.length,raw:raw,body:body},body);
          };
          window.__lx_handle_request__ = function(payloadJson){
            var p=JSON.parse(payloadJson); var requestKey=p.requestKey; var data=p.data;
            if(!requestHandler){ window.lxNative.onResponse(requestKey,false,'{}','no request handler'); return; }
            Promise.resolve(requestHandler.call(window,{source:data.source,action:data.action,info:data.info}))
              .then(function(response){
                var result={source:data.source,action:data.action};
                if(data.action==='musicUrl'){
                  if(typeof response!=='string'||response.length>8192||!/^https?:/.test(response))throw new Error('invalid music url');
                  result.data={type:(data.info&&data.info.type)||'128k',url:response};
                }else if(data.action==='lyric'){
                  if(typeof response!=='object')throw new Error('invalid lyric');
                  function clip(v,n){return(typeof v==='string'&&v.length<=n)?v:null;}
                  result.data={lyric:typeof response.lyric==='string'?response.lyric:'',tlyric:clip(response.tlyric,5120),rlyric:clip(response.rlyric,5120),lxlyric:clip(response.lxlyric,8192)};
                }else if(data.action==='pic'){
                  if(typeof response!=='string'||!/^https?:/.test(response))throw new Error('invalid pic url');
                  result.data=response;
                }else{throw new Error('unknown action');}
                window.lxNative.onResponse(requestKey,true,JSON.stringify(result),'');
              }).catch(function(e){window.lxNative.onResponse(requestKey,false,'{}',String(e&&e.message?e.message:e));});
          };
          var lx={
            EVENT_NAMES:EVENT_NAMES,
            request:function(url,options,callback){
              options=options||{}; var cbId='cb_'+(++cbSeq); callbacks[cbId]=callback;
              var safe={method:options.method||'get',headers:options.headers||{},timeout:options.timeout,body:options.body,form:options.form,formData:options.formData};
              window.lxNative.request(url,JSON.stringify(safe),cbId);
              return function(){delete callbacks[cbId];};
            },
            send:function(eventName,data){
              return new Promise(function(resolve,reject){
                if(eventName===EVENT_NAMES.inited){if(initedCalled)return reject(new Error('Script is inited'));initedCalled=true;window.lxNative.send(eventName,JSON.stringify(data||{}));resolve();return;}
                if(eventName===EVENT_NAMES.updateAlert){if(updateAlertCalled)return reject(new Error('The update alert can only be called once.'));updateAlertCalled=true;window.lxNative.send(eventName,JSON.stringify(data||{}));resolve();return;}
                reject(new Error('Unknown event name: '+eventName));
              });
            },
            on:function(eventName,handler){if(eventName===EVENT_NAMES.request){requestHandler=handler;return Promise.resolve();}return Promise.reject(new Error('The event is not supported: '+eventName));},
            utils:{
              crypto:{
                aesEncrypt:function(buffer,mode,key,iv){return bufLike(b64ToBytes(window.lxNative.aesEncrypt(bytesToB64(buffer.bytes||buffer),mode,bytesToB64(key.bytes||key),bytesToB64(iv.bytes||iv))));},
                rsaEncrypt:function(){throw new Error('rsaEncrypt not supported');},
                randomBytes:function(size){return bufLike(b64ToBytes(window.lxNative.randomBytes(size)));},
                md5:function(str){return window.lxNative.md5(String(str));}
              },
              buffer:{
                from:function(data,enc){
                  if(typeof data==='string'){
                    if(enc==='base64')return bufLike(b64ToBytes(data));
                    if(enc==='hex'){var b=new Uint8Array(data.length/2);for(var i=0;i<b.length;i++)b[i]=parseInt(data.substr(i*2,2),16);return bufLike(b);}
                    return bufLike(new TextEncoder().encode(data));
                  }
                  if(data&&data.bytes)return data; return bufLike(new Uint8Array(data||[]));
                },
                bufToString:function(buf,format){var b=buf&&buf.bytes?buf.bytes:buf;
                  if(format==='hex')return Array.prototype.map.call(b,function(x){return('0'+x.toString(16)).slice(-2);}).join('');
                  if(format==='base64')return bytesToB64(b);
                  if(format==='latin1'||format==='binary'){var s='';for(var i=0;i<b.length;i++)s+=String.fromCharCode(b[i]);return s;}
                  return new TextDecoder('utf-8').decode(b);}
              },
              zlib:{
                inflate:function(buf){return Promise.resolve(bufLike(b64ToBytes(window.lxNative.inflate(bytesToB64(buf.bytes||buf)))));},
                deflate:function(data){return Promise.resolve(bufLike(b64ToBytes(window.lxNative.deflate(bytesToB64(data.bytes||data)))));}
              }
            },
            currentScriptInfo:{name:'',description:'',version:'',author:'',homepage:'',rawScript:''},
            version:'2.0.0',
            env:'desktop'
          };
          window.lx=lx;
        })();
        """.trimIndent()
    }
}