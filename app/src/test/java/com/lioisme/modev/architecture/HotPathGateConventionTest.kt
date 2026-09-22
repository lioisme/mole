package com.lioisme.modev.architecture

import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V43 T2 热路径门控门禁(源码扫描,镜像 [InterventionGateConventionTest]):
 * 热路径 hooker 清单内的框架回调,**首条有效语句必须是门控判定**(或紧随其后的
 * 廉价空守卫提取 `as? … ?: return` / 直接委托已门控的 emit/ensure 系列助手)。
 *
 * 背景(V2.1–V2.4/V2.7/V2.8):getAll 全量 JSON 序列化、startActivity 的 Binder IPC +
 * Bundle 反序列化、SQL bind 全局锁写、File.exists/readLine 的 lowercase 分配、
 * getPackageInfo 四连反射——这些重成本曾全部先于 kind 门控执行,「开关全关」只对
 * 记录数生效、对宿主线程白付费。本门禁把「门控先行」从约定升级为红线:
 * 新增热路径回调或把既有回调的 gate 后移,CI 即红。
 *
 * 清单即契约:未列入的文件不受本门禁约束;向清单新增文件须保证首行门控形态。
 */
class HotPathGateConventionTest {

    /**
     * 热路径 hooker 文件(回调每调用执行,成本敏感)——清单 = V43 审计实证范围
     * (V2.1–V2.9)+ watcher 订阅者;V44 T2.2 扩围:SSL lazy-scan(SSLContext.init)、
     * WidgetText(setText)、Logcat、NioFiles(readAllBytes/readAllLines)、SqlDatabase
     * 全回调 / SqlOpenHelper / Dialog×2 / Asset / FileStream、proc 单一广播协调器
     * ——审计实证这些回调的「取参/反射/整文件拼接」曾全部先于门控执行。
     */
    private val hotPathFiles = listOf(
        "PrefsGetHooker.kt",
        "PrefsEditorHooker.kt",
        "ActivityTransitionHooker.kt",
        "SqlStatementHooker.kt",
        "SqlDatabaseHooker.kt",
        "SqlOpenHelperHooker.kt",
        "RootFileHooker.kt",
        "DetectionMonitorHooker.kt",
        "AppInfoHooker.kt",
        "SignatureMonitorHooker.kt",
        "WebViewHooker.kt",
        "NetworkRequestHooker.kt",
        "SSLBypassHooker.kt",
        "SystemPropertiesHooker.kt",
        "SettingsHooker.kt",
        "EmulatorSensorHooker.kt",
        "DeviceSysPropHooker.kt",
        "LogcatHooker.kt",
        "WidgetTextHooker.kt",
        "XposedClassHooker.kt",
        "ProcScanBroadcaster.kt",
        "AssetHooker.kt",
        "FileStreamHooker.kt",
        "NioFilesHooker.kt",
        "DialogKeywordInterceptHooker.kt",
        "DialogLocationHooker.kt",
    )

    /** 门控谓词/守卫标记(出现于回调首条有效语句即合规)。 */
    private val gateTokens = listOf(
        "featureOn(", "anySslFeatureOn(", "config().", "prefsEnabled(", "dbEnabled(", "dbListen",
        "uiInteractionEnabled(", "systemEnabled(", "processControlEnabled(",
        "networkEnabled(", "behaviorEnabled(", "webviewEnabled(", "deviceEnvEnabled(",
        "enabled(", "val switch = config()", "emit(",
    )

    /** 廉价前置守卫:`… as? T ?: return[@label]` / `inv.? ?: return`(仅取参/判空,零成本)。 */
    private val cheapGuard = Pattern.compile(
        "\\bas\\?\\s+\\S+.*\\?\\:\\s+return(?:@\\w+)?|inv\\.(?:thisObject|result|getArg)[^;]*\\?\\:\\s+return(?:@\\w+)?",
    )

    /** 重入守卫(`if (x.get() == true) return`)。 */
    private val reentryGuard = Pattern.compile("\\.get\\(\\)\\s*==\\s*true\\).*return")

    /**
     * 委托合规:首行直接调用内部自带门控的助手(emit/ensure/before/handle/process/record
     * 系列,含适配器转发 `before(invocation)` 与函数引用 `::ensureBridge`)。
     */
    private val delegatingCall = Pattern.compile(
        "\\b(?:emit|ensure|after|before|handle|process|record|collect|guard|dispatch|onLoadAttempt)[A-Z]\\w*\\s*\\(|" +
            "\\b(?:before|after)\\(invocation\\)|::ensure\\w+|\\bbeforeBegin\\(",
    )

    private val hookHelperOpen = Pattern.compile(
        "(?<!fun )\\b(?:hook(?:Before|After|CtorAfter|CtorBefore)|interceptBefore)\\s*[({]",
    )
    private val overrideOpen = Pattern.compile("override fun (?:before|after)\\(invocation: Invocation\\)")

    @Test
    fun hotPathCallbacksGateBeforeAnyWork() {
        val root = locateProjectRoot()
        val hooksDir = File(root, "app/src/main/java/com/lioisme/modev/hook/hooks")
        assertTrue("hooks source dir not found: $hooksDir", hooksDir.isDirectory)

        val violations = mutableListOf<String>()
        for (name in hotPathFiles) {
            val file = File(hooksDir, name)
            assertTrue("hot-path hooker missing: $name", file.isFile)
            violations += scanFile(name, file.readText())
        }
        assertTrue(
            "hot-path callbacks must gate (featureOn/*Enabled/config()./delegate/reentry-guard) " +
                "as the first effective statement — host threads must not pay per-call cost with " +
                "switches off (V43 T2):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun scanFile(fileName: String, source: String): List<String> {
        val lineComment = Pattern.compile("//.*$")
        val raw = source.lines()
        val lines = ArrayList<String>(raw.size)
        for (l in raw) {
            val stripped = lineComment.matcher(l).replaceAll("")
            // 块注释行跳过(保留缩进语义用空串)
            lines.add(if (stripped.isBlank()) stripped else stripped)
        }
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val s = lines[i]
            val isOverride = overrideOpen.matcher(s).find()
            val isHookHelper = !isOverride && hookHelperOpen.matcher(s).find()
            if (!isOverride && !isHookHelper) {
                i++
                continue
            }
            // 定位回调体 `{`
            var k = i
            var remainder = ""
            while (k < lines.size) {
                val openIdx = lines[k].indexOf('{')
                if (openIdx >= 0) {
                    remainder = lines[k].substring(openIdx + 1)
                    break
                }
                k++
            }
            if (k >= lines.size) {
                i++
                continue
            }
            val bodyStartLine: Int
            if (isHookHelper) {
                // lambda 参数头:同行之内的 `->` 结束;否则跨行找 `->`
                val arrowIdx = remainder.indexOf("->")
                if (arrowIdx >= 0) {
                    remainder = remainder.substring(arrowIdx + 2)
                    bodyStartLine = k + 1
                } else {
                    var m = k + 1
                    remainder = ""
                    while (m < lines.size && !lines[m].contains("->")) m++
                    if (m < lines.size) {
                        remainder = lines[m].substringAfterLast("->")
                        m++
                    }
                    bodyStartLine = m
                }
            } else {
                bodyStartLine = k + 1
            }
            // 首条有效语句:同行余文或后续非空代码行
            var first = remainder.trim().takeIf { it.isNotEmpty() }
            var firstLine = k
            var probe = bodyStartLine
            while (first == null && probe < lines.size) {
                val t = lines[probe].trim()
                if (t.isNotEmpty() && !t.startsWith("*") && !t.startsWith("/*")) {
                    first = t
                    firstLine = probe
                }
                probe++
            }
            if (first != null && !isCompliant(first)) {
                out += "$fileName:L${firstLine + 1} ${first.take(100)}"
            }
            i = (if (isHookHelper) bodyStartLine else firstLine + 1).coerceAtLeast(i + 1)
        }
        return out
    }

    private fun isCompliant(statement: String): Boolean =
        gateTokens.any { statement.contains(it) } ||
            cheapGuard.matcher(statement).find() ||
            reentryGuard.matcher(statement).find() ||
            delegatingCall.matcher(statement).find() ||
            // 同行 if-return 守卫(如 `if (result !is Boolean) return@hookAfter`)
            Pattern.matches("^if\\s*\\(.*\\)\\s+return(?:@\\w+)?\\s*$", statement.trim())

    private fun locateProjectRoot(): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        val candidates = listOf(
            File(userDir),
            File(userDir, ".."),
        )
        return candidates.firstOrNull {
            File(it, "app/src/main/java/com/lioisme/modev/hook/hooks").isDirectory
        } ?: error("project root not located from $userDir")
    }
}
