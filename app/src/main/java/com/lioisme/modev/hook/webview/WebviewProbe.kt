package com.lioisme.modev.hook.webview

/**
 * WebView JS 探针:内嵌字符串常量而非 asset——注入进程无模块资源上下文,
 * 零 IO 零失败面。由 [WebViewHooker] 经 evaluateJavascript 注入,幂等守卫
 * [IDEMPOTENCE_GUARD](window.__modevProbe),重复注入零副作用。
 *
 * 包装范围:
 * - WebCrypto(crypto.subtle):encrypt / decrypt / digest / sign / verify / importKey / generateKey;
 * - CryptoJS:AES / DES / TripleDES / Rabbit / RC4 分组加密 + MD5/SHA 系列摘要 + HMAC 系列;
 * - JSEncrypt:prototype.encrypt / decrypt。
 *
 * 数据面:ArrayBuffer / TypedArray → hex;CryptoJS WordArray → hex(经 C.enc.Hex);
 * 字符串明文 → text;全部经 JS 侧 MAX_HEX 预截断(桥接接口 maxHexChars 读取
 * HookSwitch.maxBytesPerField),Java 侧 BytesView.of 保险丝兜底。
 * 仅只读包装,异常全捕获,绝不影响宿主页面。
 */
object WebviewProbe {

    /** JS 桥全局名(与 addJavascriptInterface 的 name 参数同源,单一事实源)。 */
    const val BRIDGE_NAME = "modevJsBridge"

    /** 幂等守卫:注入过的页面不再重复包装。 */
    const val IDEMPOTENCE_GUARD = "__modevProbe"

    /**
     * 探针脚本(ES5,兼容旧 WebView;无外联、无注入逃逸面)。
     * 注意:内容禁止出现 `${` 与 `"""`(Kotlin raw string 约束)。
     */
    const val PROBE_JS: String = """
(function () {
  if (window.__modevProbe) { return; }
  window.__modevProbe = true;
  var BRIDGE = 'modevJsBridge';
  var MAX_HEX = 0;
  try {
    var b0 = window[BRIDGE];
    if (b0 && b0.maxHexChars) {
      var m = b0.maxHexChars();
      if (typeof m === 'number' && m > 0) { MAX_HEX = m; }
    }
  } catch (e) {}
  var cap = function (s) {
    if (!s || MAX_HEX <= 0 || s.length <= MAX_HEX) { return s; }
    return s.substring(0, MAX_HEX);
  };
  var hexOfBuffer = function (buf) {
    var u8 = new Uint8Array(buf);
    var out = '';
    for (var i = 0; i < u8.length; i++) {
      var h = u8[i].toString(16);
      out += (h.length < 2 ? '0' : '') + h;
    }
    return cap(out);
  };
  var hexOf = function (v) {
    if (!v) { return null; }
    if (typeof v === 'string') { return null; }
    if (v instanceof ArrayBuffer) { return hexOfBuffer(v); }
    if (v.buffer && v.buffer instanceof ArrayBuffer && v.byteLength !== undefined) {
      return hexOfBuffer(v.buffer.slice(v.byteOffset, v.byteOffset + v.byteLength));
    }
    return null;
  };
  var report = function (obj) {
    try {
      var b = window[BRIDGE];
      if (b && b.report) { b.report(JSON.stringify(obj)); }
    } catch (e) {}
  };
  var wrapSubtle = function () {
    try {
      var subtle = window.crypto && window.crypto.subtle;
      if (!subtle) { return; }
      var wrap = function (name, fn, inputIndex, resultHex) {
        return function () {
          var args = Array.prototype.slice.call(arguments);
          var ev = {
            op: name,
            alg: 'WebCrypto/' + (args[0] && args[0].name ? args[0].name : ''),
            direction: name === 'encrypt' ? 1 : (name === 'decrypt' ? 2 : 0) || null
          };
          try {
            var alg = args[0] || {};
            if (alg.iv) { ev.iv = hexOf(alg.iv); }
            if (inputIndex >= 0 && args[inputIndex] !== undefined) { ev.inputHex = hexOf(args[inputIndex]); }
            if (name === 'importKey') { ev.key = hexOf(args[1]); }
            if (alg.tagLength !== undefined) { ev.params = { tagLength: String(alg.tagLength) }; }
            if (alg.counter !== undefined) { ev.params = ev.params || {}; ev.params.counter = String(alg.counter); }
            if (alg.length !== undefined) { ev.params = ev.params || {}; ev.params.length = String(alg.length); }
          } catch (e2) {}
          var p;
          try { p = fn.apply(subtle, args); } catch (e3) { return; }
          if (p && p.then) {
            return p.then(function (res) {
              try {
                if (resultHex) { ev.outputHex = hexOf(res); }
                if (name === 'verify') { ev.verify = !!res; }
                report(ev);
              } catch (e4) {}
              return res;
            });
          }
          return p;
        };
      };
      subtle.encrypt = wrap('encrypt', subtle.encrypt, 2, true);
      subtle.decrypt = wrap('decrypt', subtle.decrypt, 2, true);
      subtle.digest = wrap('digest', subtle.digest, 1, true);
      subtle.sign = wrap('sign', subtle.sign, 2, true);
      subtle.verify = wrap('verify', subtle.verify, 3, false);
      subtle.importKey = wrap('importKey', subtle.importKey, -1, false);
      subtle.generateKey = wrap('generateKey', subtle.generateKey, -1, false);
    } catch (e5) {}
  };
  var wrapCryptoJS = function () {
    try {
      var C = window.CryptoJS;
      if (!C) { return; }
      var blockCiphers = ['AES', 'DES', 'TripleDES', 'Rabbit', 'RC4'];
      var i;
      for (i = 0; i < blockCiphers.length; i++) {
        (function (name) {
          var lib = C[name];
          if (!lib || !lib.encrypt || !lib.decrypt) { return; }
          var enc = lib.encrypt;
          var dec = lib.decrypt;
          lib.encrypt = function () {
            var args = Array.prototype.slice.call(arguments);
            var ev = { op: 'encrypt', alg: 'CryptoJS/' + name, direction: 1 };
            var res;
            try { res = enc.apply(lib, args); } catch (e) { return; }
            try {
              if (res) {
                if (res.ciphertext) { ev.outputHex = cap(res.ciphertext.toString(C.enc.Hex)); }
                if (res.iv) { ev.iv = cap(res.iv.toString(C.enc.Hex)); }
                if (res.salt) { ev.params = { salt: cap(res.salt.toString(C.enc.Hex)) }; }
              }
            } catch (e2) {}
            report(ev);
            return res;
          };
          lib.decrypt = function () {
            var args = Array.prototype.slice.call(arguments);
            var ev = { op: 'decrypt', alg: 'CryptoJS/' + name, direction: 2 };
            var res;
            try { res = dec.apply(lib, args); } catch (e) { return; }
            try {
              if (res && typeof res.toString === 'function') { ev.outputText = cap(res.toString(C.enc.Utf8)); }
            } catch (e2) {}
            report(ev);
            return res;
          };
        })(blockCiphers[i]);
      }
      var digests = ['MD5', 'SHA1', 'SHA256', 'SHA224', 'SHA512', 'SHA384', 'SHA3'];
      for (i = 0; i < digests.length; i++) {
        (function (name) {
          var fn = C[name];
          if (typeof fn !== 'function') { return; }
          C[name] = function () {
            var args = Array.prototype.slice.call(arguments);
            var res;
            try { res = fn.apply(null, args); } catch (e) { return; }
            try { report({ op: 'digest', alg: 'CryptoJS/' + name, outputHex: cap(res.toString(C.enc.Hex)) }); } catch (e2) {}
            return res;
          };
        })(digests[i]);
      }
      var hmacs = ['HmacMD5', 'HmacSHA1', 'HmacSHA256', 'HmacSHA224', 'HmacSHA512', 'HmacSHA384'];
      for (i = 0; i < hmacs.length; i++) {
        (function (name) {
          var fn = C[name];
          if (typeof fn !== 'function') { return; }
          C[name] = function () {
            var args = Array.prototype.slice.call(arguments);
            var res;
            try { res = fn.apply(null, args); } catch (e) { return; }
            try { report({ op: 'digest', alg: 'CryptoJS/' + name, outputHex: cap(res.toString(C.enc.Hex)) }); } catch (e2) {}
            return res;
          };
        })(hmacs[i]);
      }
    } catch (e6) {}
  };
  var wrapJSEncrypt = function () {
    try {
      var J = window.JSEncrypt;
      if (!J || !J.prototype) { return; }
      var enc = J.prototype.encrypt;
      var dec = J.prototype.decrypt;
      if (typeof enc === 'function') {
        J.prototype.encrypt = function () {
          var args = Array.prototype.slice.call(arguments);
          var ev = { op: 'encrypt', alg: 'JSEncrypt', direction: 1 };
          try { ev.inputText = cap(String(args[0])); } catch (e) {}
          var res;
          try { res = enc.apply(this, args); } catch (e2) { return; }
          try { ev.outputText = cap(String(res)); } catch (e3) {}
          report(ev);
          return res;
        };
      }
      if (typeof dec === 'function') {
        J.prototype.decrypt = function () {
          var args = Array.prototype.slice.call(arguments);
          var ev = { op: 'decrypt', alg: 'JSEncrypt', direction: 2 };
          try { ev.inputText = cap(String(args[0])); } catch (e) {}
          var res;
          try { res = dec.apply(this, args); } catch (e2) { return; }
          try { ev.outputText = cap(String(res)); } catch (e3) {}
          report(ev);
          return res;
        };
      }
    } catch (e7) {}
  };
  wrapSubtle();
  wrapCryptoJS();
  wrapJSEncrypt();
})();
"""
}
