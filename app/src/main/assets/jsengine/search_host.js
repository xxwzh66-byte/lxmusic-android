/*
 * LX Search Engine host (runs before the bundled providers).
 *
 * Provides the Node-like globals (Buffer, crypto, zlib, httpFetch) that the
 * bundled lx-music-desktop search modules expect, all backed by the Android
 * `lxNative` bridge. Exposes window.__lx_search__() to native.
 *
 * Native -> page : __lx_search__(requestId, sourceId, keyword, page, limit)
 * Page -> native : lxSearchNative.searchResult(requestId, ok, json, message)
 */
(function () {
  'use strict';

  function b64ToBytes(b64) {
    var bin = atob(b64);
    var arr = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
    return arr;
  }
  function bytesToB64(bytes) {
    var bin = '';
    for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    return btoa(bin);
  }
  window.__b64ToBytes = b64ToBytes;
  window.__bytesToB64 = bytesToB64;

  // -------- Buffer polyfill --------
  function BufferCtor(input, enc) {
    if (typeof input === 'string') {
      if (enc === 'base64') this.bytes = b64ToBytes(input);
      else if (enc === 'hex') {
        var b = new Uint8Array(Math.floor(input.length / 2));
        for (var i = 0; i < b.length; i++) b[i] = parseInt(input.substr(i * 2, 2), 16);
        this.bytes = b;
      } else if (enc === 'binary' || enc === 'latin1') {
        var lb = new Uint8Array(input.length);
        for (var j = 0; j < input.length; j++) lb[j] = input.charCodeAt(j) & 0xff;
        this.bytes = lb;
      } else this.bytes = new TextEncoder().encode(input);
    } else if (input instanceof Uint8Array) this.bytes = input;
    else if (input && input.bytes) this.bytes = input.bytes;
    else if (typeof input === 'number') this.bytes = new Uint8Array(input);
    else this.bytes = new Uint8Array(0);
    this.length = this.bytes.length;
  }
  BufferCtor.from = function (x, enc) { return (x instanceof BufferCtor) ? x : new BufferCtor(x, enc); };
  BufferCtor.isBuffer = function (x) { return x instanceof BufferCtor; };
  BufferCtor.alloc = function (n) { return new BufferCtor(new Uint8Array(n)); };
  BufferCtor.concat = function (list) {
    var total = 0;
    list.forEach(function (b) { total += b.length; });
    var out = new Uint8Array(total), off = 0;
    list.forEach(function (b) { out.set(b.bytes, off); off += b.length; });
    return new BufferCtor(out);
  };
  BufferCtor.prototype.toString = function (enc, start, end) {
    var b = this.bytes;
    if (start != null) b = b.subarray(start, end);
    if (enc === 'hex') return Array.prototype.map.call(b, function (x) { return ('0' + x.toString(16)).slice(-2); }).join('');
    if (enc === 'base64') return bytesToB64(b);
    if (enc === 'binary' || enc === 'latin1') { var s = ''; for (var i = 0; i < b.length; i++) s += String.fromCharCode(b[i]); return s; }
    return new TextDecoder('utf-8').decode(b);
  };
  BufferCtor.prototype.subarray = function (a, b) { return new BufferCtor(this.bytes.subarray(a, b)); };
  BufferCtor.prototype.slice = function (a, b) { return new BufferCtor(this.bytes.slice(a, b)); };
  BufferCtor.prototype.indexOf = function (str) { return this.toString('latin1').indexOf(str); };
  BufferCtor.prototype.toJSON = function () { return { type: 'Buffer', data: Array.prototype.slice.call(this.bytes) }; };
  window.Buffer = BufferCtor;

  // -------- crypto / zlib shims (native-backed) --------
  window.nodeCrypto = {
    createHash: function () {
      var chunks = '';
      return {
        update: function (d) { chunks += (typeof d === 'string') ? d : d.toString('utf8'); return this; },
        digest: function (enc) { return window.lxNative.md5(chunks); }
      };
    },
    createCipheriv: function (mode, key, iv) {
      var parts = [];
      return {
        update: function (buf) { parts.push(buf); return new BufferCtor(new Uint8Array(0)); },
        final: function () {
          var all = BufferCtor.concat(parts);
          var enc = window.lxNative.aesEncrypt(bytesToB64(all.bytes), mode, bytesToB64(key.bytes || key), iv && iv.bytes ? bytesToB64(iv.bytes) : '');
          return new BufferCtor(b64ToBytes(enc));
        }
      };
    },
    createDecipheriv: function (mode, key, iv) {
      var parts = [];
      return {
        update: function (buf) { parts.push(buf); return new BufferCtor(new Uint8Array(0)); },
        final: function () {
          var all = BufferCtor.concat(parts);
          var enc = window.lxNative.aesDecrypt(bytesToB64(all.bytes), mode, bytesToB64(key.bytes || key), iv && iv.bytes ? bytesToB64(iv.bytes) : '');
          return new BufferCtor(b64ToBytes(enc));
        }
      };
    },
    randomBytes: function (n) { return new BufferCtor(b64ToBytes(window.lxNative.randomBytes(n))); },
    publicEncrypt: function () { throw new Error('publicEncrypt not supported'); }
  };
  window.nodeZlib = {
    inflate: function (data, cb) {
      try { cb(null, new BufferCtor(b64ToBytes(window.lxNative.inflate(bytesToB64(data.bytes || data))))); }
      catch (e) { cb(e); }
    },
    deflate: function (data, cb) {
      try { cb(null, new BufferCtor(b64ToBytes(window.lxNative.deflate(bytesToB64(data.bytes || data))))); }
      catch (e) { cb(e); }
    }
  };
  window.crypto = window.crypto || {};
  window.crypto.createHash = window.nodeCrypto.createHash;
  window.crypto.createCipheriv = window.nodeCrypto.createCipheriv;
  window.crypto.createDecipheriv = window.nodeCrypto.createDecipheriv;
  window.crypto.randomBytes = window.nodeCrypto.randomBytes;
  window.require = function (name) {
    if (name === 'crypto' || name === 'node:crypto') return window.nodeCrypto;
    if (name === 'zlib' || name === 'node:zlib') return window.nodeZlib;
    if (name === 'buffer' || name === 'node:buffer') return { Buffer: BufferCtor };
    throw new Error('module not available: ' + name);
  };

  // -------- httpFetch shim (LX request.js promise contract) --------
  var searchCallbacks = {};
  var cbSeq = 0;
  window.httpFetch = function (url, options) {
    options = options || {};
    return {
      promise: new Promise(function (resolve, reject) {
        var cbId = 's_' + (++cbSeq);
        searchCallbacks[cbId] = function (err, statusCode, statusMessage, headersJson, bodyStr, rawB64) {
          if (err) { reject(new Error(err)); return; }
          var headers = {}; try { headers = JSON.parse(headersJson || '{}'); } catch (e) {}
          var body = bodyStr; try { body = JSON.parse(bodyStr); } catch (e) {}
          resolve({ statusCode: statusCode, headers: headers, body: body, raw: new BufferCtor(b64ToBytes(rawB64 || '')) });
        };
        window.lxNative.request(url, JSON.stringify({
          method: options.method || 'get', headers: options.headers || {},
          timeout: options.timeout, body: options.body, form: options.form, formData: options.formData
        }), cbId);
      }),
      cancelHttp: function () {}
    };
  };
  window.httpGet = function (url, options, cb) {
    if (typeof options === 'function') { cb = options; options = {}; }
    window.httpFetch(url, Object.assign({}, options, { method: 'get' })).promise
      .then(function (r) { cb(null, r, r.body); }).catch(function (e) { cb(e, null, null); });
  };
  window.httpPost = function (url, data, options, cb) {
    if (typeof options === 'function') { cb = options; options = {}; }
    window.httpFetch(url, Object.assign({}, options, { method: 'post', body: data })).promise
      .then(function (r) { cb(null, r, r.body); }).catch(function (e) { cb(e, null, null); });
  };

  window.__lx_cb__ = function (cbId, err, statusCode, statusMessage, headersJson, bodyStr, rawB64) {
    var cb = searchCallbacks[cbId];
    if (!cb) return;
    delete searchCallbacks[cbId];
    cb(err, statusCode, statusMessage, headersJson, bodyStr, rawB64);
  };

  // -------- provider dispatch --------
  var providers = {};
  window.__lx_register_provider__ = function (id, provider) { providers[id] = provider; };

  window.__lx_ready__ = false;

  window.__lx_search__ = function (requestId, sourceId, keyword, page, limit) {
    if (!window.__lx_search_ready__) {
      window.lxSearchNative.searchResult(requestId, false, '', 'search bundle not ready');
      return;
    }
    var p = providers[sourceId];
    if (!p) { window.lxSearchNative.searchResult(requestId, false, '', 'source not supported: ' + sourceId); return; }
    Promise.resolve(p.search(keyword, page, limit))
      .then(function (res) { window.lxSearchNative.searchResult(requestId, true, JSON.stringify(res), null); })
      .catch(function (e) { window.lxSearchNative.searchResult(requestId, false, '', String(e && e.message ? e.message : e)); });
  };

  window.__lx_ready__ = true;
})();