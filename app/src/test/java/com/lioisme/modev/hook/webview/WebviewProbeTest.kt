package com.lioisme.modev.hook.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探针脚本结构契约(JVM 纯文本断言,V12):
 * - 幂等守卫、三类包装目标、截断闸、桥名齐全;
 * - 无注入逃逸面(`</script>`)、无外联请求、无 `document.write`。
 */
class WebviewProbeTest {

    @Test
    fun probe_contains_idempotence_guard() {
        assertTrue(WebviewProbe.PROBE_JS.contains(WebviewProbe.IDEMPOTENCE_GUARD))
        assertTrue(WebviewProbe.PROBE_JS.contains("if (window.__modevProbe) { return; }"))
    }

    @Test
    fun probe_wraps_webcrypto() {
        val js = WebviewProbe.PROBE_JS
        assertTrue(js.contains("window.crypto && window.crypto.subtle"))
        for (method in listOf("encrypt", "decrypt", "digest", "sign", "verify", "importKey", "generateKey")) {
            assertTrue("missing subtle.$method wrap", js.contains("subtle.$method = wrap("))
        }
    }

    @Test
    fun probe_wraps_crypto_js() {
        val js = WebviewProbe.PROBE_JS
        assertTrue(js.contains("window.CryptoJS"))
        for (cipher in listOf("AES", "DES", "TripleDES", "Rabbit", "RC4")) {
            assertTrue("missing CryptoJS.$cipher", js.contains("'$cipher'"))
        }
        for (digest in listOf("MD5", "SHA1", "SHA256", "SHA512", "SHA3")) {
            assertTrue("missing CryptoJS.$digest", js.contains("'$digest'"))
        }
        for (hmac in listOf("HmacMD5", "HmacSHA1", "HmacSHA256", "HmacSHA512")) {
            assertTrue("missing CryptoJS.$hmac", js.contains("'$hmac'"))
        }
    }

    @Test
    fun probe_wraps_js_encrypt() {
        val js = WebviewProbe.PROBE_JS
        assertTrue(js.contains("window.JSEncrypt"))
        assertTrue(js.contains("J.prototype.encrypt = function ()"))
        assertTrue(js.contains("J.prototype.decrypt = function ()"))
    }

    @Test
    fun probe_uses_bridge_and_cap() {
        val js = WebviewProbe.PROBE_JS
        assertTrue(js.contains("'${WebviewProbe.BRIDGE_NAME}'"))
        assertTrue(js.contains("b0.maxHexChars"))
        assertTrue(js.contains("b.report(JSON.stringify(obj))"))
    }

    @Test
    fun probe_has_no_injection_surface() {
        val js = WebviewProbe.PROBE_JS
        assertFalse("must not contain script closer", js.contains("</script>"))
        assertFalse("must not load external resources", js.contains("document.write"))
        assertFalse("must not contain importScripts", js.contains("importScripts"))
        assertFalse("must not contain eval(", js.contains("eval("))
    }
}
