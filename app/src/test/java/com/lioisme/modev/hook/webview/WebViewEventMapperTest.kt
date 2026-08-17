package com.lioisme.modev.hook.webview

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探针事件 → 记录映射契约(JVM,V12):
 * - 合法事件全字段映射(类别/操作/方向/算法/IV/密钥/输入输出/provider);
 * - 畸形载荷静默丢弃(解析 null / 未知 op);
 * - maxBytesPerField 保险丝截断(truncated 语义与 JCA 捕获侧一致);
 * - 开关关闭(webView=false)时桥侧零 emit 由 WebViewJsBridge 保证,映射层不关心。
 */
class WebViewEventMapperTest {

    private val switch = HookSwitch.DEFAULT

    private fun event(
        op: String = "encrypt",
        alg: String = "WebCrypto/AES-GCM",
        direction: Int = 1,
        iv: String? = "000102030405060708090a0b0c0d0e0f",
        key: String? = null,
        inputHex: String? = "deadbeef",
        inputText: String? = null,
        outputHex: String? = "cafebabe",
        outputText: String? = null,
        verify: Boolean? = null,
        params: Map<String, String>? = mapOf("tagLength" to "128"),
    ) = JsEvent(
        op = op, alg = alg, direction = direction, iv = iv, key = key,
        inputHex = inputHex, inputText = inputText,
        outputHex = outputHex, outputText = outputText, verify = verify, params = params,
    )

    @Test
    fun maps_full_encrypt_event() {
        val record = WebViewEventMapper.toRecord(event(), "com.example.app", "com.example.app:web", switch)
        assertNotNull(record)
        assertEquals(CryptoKind.WEB_CRYPTO, record!!.kind)
        assertEquals(CryptoOperation.ENCRYPT, record.operation)
        assertEquals("WebCrypto/AES-GCM", record.algorithm)
        assertEquals("WebView", record.provider)
        assertEquals(1, record.direction)
        assertEquals("com.example.app", record.packageName)
        assertEquals("com.example.app:web", record.processName)
        assertEquals("000102030405060708090a0b0c0d0e0f", record.iv?.hex)
        assertEquals("deadbeef", record.input?.hex)
        assertEquals("cafebabe", record.output?.hex)
        assertEquals(mapOf("tagLength" to "128"), record.params)
        assertTrue("DEFAULT.logStack=true 时须有堆栈帧", record.stack.isNotEmpty())
        assertTrue("模块帧须被过滤", record.stack.none { it.contains("WebViewEventMapper") })
        assertTrue(record.stack[0].contains("org.junit"))
    }

    @Test
    fun maps_op_variants() {
        assertEquals(CryptoOperation.DECRYPT, WebViewEventMapper.toRecord(event(op = "decrypt", direction = 2), "p", "p", switch)!!.operation)
        assertEquals(CryptoOperation.DIGEST, WebViewEventMapper.toRecord(event(op = "digest"), "p", "p", switch)!!.operation)
        assertEquals(CryptoOperation.SIGN, WebViewEventMapper.toRecord(event(op = "sign"), "p", "p", switch)!!.operation)
        assertEquals(CryptoOperation.VERIFY, WebViewEventMapper.toRecord(event(op = "verify", verify = false), "p", "p", switch)!!.operation)
        assertEquals(false, WebViewEventMapper.toRecord(event(op = "verify", verify = false), "p", "p", switch)!!.verifyResult)
        assertEquals(CryptoOperation.GET_KEY, WebViewEventMapper.toRecord(event(op = "importKey", key = "aabb"), "p", "p", switch)!!.operation)
        assertEquals("aabb", WebViewEventMapper.toRecord(event(op = "importKey", key = "aabb"), "p", "p", switch)!!.key?.hex)
        assertEquals(CryptoOperation.GENERATE, WebViewEventMapper.toRecord(event(op = "generateKey"), "p", "p", switch)!!.operation)
    }

    @Test
    fun text_fallback_when_hex_missing() {
        val record = WebViewEventMapper.toRecord(
            event(inputHex = null, outputHex = null, inputText = "hello", outputText = "base64=="),
            "p", "p", switch,
        )
        assertEquals("hello", record!!.input?.text)
        assertEquals("base64==", record.output?.text)
    }

    @Test
    fun malformed_json_parses_to_null() {
        assertNull(WebViewEventParser.parse(null))
        assertNull(WebViewEventParser.parse(""))
        assertNull(WebViewEventParser.parse("not-json"))
        assertNull(WebViewEventParser.parse("{}"))
        assertNull(WebViewEventParser.parse("""{"alg":"x"}"""))
    }

    @Test
    fun unknown_op_maps_to_null() {
        assertNull(WebViewEventMapper.toRecord(event(op = "skylight"), "p", "p", switch))
    }

    @Test
    fun invalid_hex_ignored_not_crash() {
        val record = WebViewEventMapper.toRecord(event(iv = "zzz", inputHex = "xyz"), "p", "p", switch)
        assertNotNull(record)
        assertNull(record!!.iv)
        assertNull(record.input)
    }

    @Test
    fun oversized_hex_truncated_by_fuse() {
        val small = HookSwitch.DEFAULT.copy(maxBytesPerField = 4)
        val bigHex = "ab".repeat(100)
        val record = WebViewEventMapper.toRecord(event(inputHex = bigHex), "p", "p", small)
        assertNotNull(record)
        assertTrue(record!!.input!!.truncated)
        assertEquals(100, record.input!!.totalLength)
        assertTrue("hex 展示须被截断", record.input!!.hex.length < bigHex.length)
        assertTrue(record.input!!.hex.startsWith("abab"))
    }

    @Test
    fun js_pre_truncated_hex_stays_exact() {
        val small = HookSwitch.DEFAULT.copy(maxBytesPerField = 4)
        val cappedHex = "ab".repeat(4)
        val record = WebViewEventMapper.toRecord(event(inputHex = cappedHex), "p", "p", small)
        assertEquals(4, record!!.input!!.totalLength)
        assertEquals(false, record.input!!.truncated)
        assertEquals(cappedHex, record.input!!.hex)
    }

    @Test
    fun zero_max_means_unlimited() {
        val unlimited = HookSwitch.DEFAULT.copy(maxBytesPerField = 0)
        val bigHex = "ab".repeat(100)
        val record = WebViewEventMapper.toRecord(event(inputHex = bigHex), "p", "p", unlimited)
        assertEquals(100, record!!.input!!.totalLength)
        assertEquals(false, record.input!!.truncated)
    }
}
