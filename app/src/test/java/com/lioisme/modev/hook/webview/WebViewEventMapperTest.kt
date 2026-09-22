package com.lioisme.modev.hook.webview

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探针事件 → WebView 记录映射契约(JVM,V29):
 * - 合法事件全字段映射(类别=JS_CRYPTO/操作/输入输出);
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
        // V44 T2.3/D-V44-2:logStack 默认翻转 false(默认配置零栈捕获预算);
        // 本测试验证「开启栈捕获」路径 —— 显式开启以断言模块帧过滤契约。
        val stackOn = switch.copy(logStack = true)
        val record = WebViewEventMapper.toRecord(event(), "com.example.app", "com.example.app:web", stackOn)
        assertNotNull(record)
        assertEquals(WebViewKind.JS_CRYPTO, record!!.kind)
        assertEquals(WebViewOperation.JS_ENCRYPT, record.operation)
        assertEquals("com.example.app", record.packageName)
        assertEquals("com.example.app:web", record.processName)
        assertEquals("deadbeef", record.input?.hex)
        assertEquals("cafebabe", record.output?.hex)
        assertEquals(mapOf("tagLength" to "128"), record.params)
        assertTrue("logStack=true 时须有堆栈帧", record.stack.isNotEmpty())
        assertTrue("模块帧须被过滤", record.stack.none { it.contains("WebViewEventMapper") })
        assertTrue(record.stack[0].contains("org.junit"))
    }

    @Test
    fun maps_op_variants() {
        assertEquals(WebViewOperation.JS_DECRYPT, WebViewEventMapper.toRecord(event(op = "decrypt"), "p", "p", switch)!!.operation)
        assertEquals(WebViewOperation.JS_DIGEST, WebViewEventMapper.toRecord(event(op = "digest"), "p", "p", switch)!!.operation)
        assertEquals(WebViewOperation.JS_SIGN, WebViewEventMapper.toRecord(event(op = "sign"), "p", "p", switch)!!.operation)
        assertEquals(WebViewOperation.JS_VERIFY, WebViewEventMapper.toRecord(event(op = "verify", verify = false), "p", "p", switch)!!.operation)
        assertEquals(WebViewOperation.JS_IMPORT_KEY, WebViewEventMapper.toRecord(event(op = "importKey", key = "aabb"), "p", "p", switch)!!.operation)
        assertEquals(WebViewOperation.JS_GENERATE_KEY, WebViewEventMapper.toRecord(event(op = "generateKey"), "p", "p", switch)!!.operation)
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
        assertNull(record!!.input)
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
