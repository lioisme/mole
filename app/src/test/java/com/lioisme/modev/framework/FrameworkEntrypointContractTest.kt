package com.lioisme.modev.framework

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 框架入口与模块元数据契约(V50,交付物 3 `docs/xposed-api-compat.md` 的机器面)。
 *
 * 本模块同时面向两套 Xposed 加载协议:
 *  - **现代**(libxposed API 102):`META-INF/xposed/java_init.list` → [ModevModule];
 *  - **legacy**(Xposed API 82):`assets/xposed_init` → [LegacyModuleEntry]。
 *
 * 加载哪个入口由**框架**决定(现代加载器优先 java_init.list,旧加载器只读 xposed_init),
 * 模块侧唯一能保证的是:两个入口各自存在、各自的元数据诚实、且 legacy 通路
 * **不在签名/常量池里触碰 libxposed 类型**(否则旧框架上首次发射即 NoClassDefFoundError)。
 * 这些全是源码/资源级事实,JVM 可测 —— 运行期生效判据仍归设备注入矩阵。
 */
class FrameworkEntrypointContractTest {

    // gradle 单测的 user.dir = <repo>/app(与本仓其余源码扫描门禁同源)
    private val appDir = File(System.getProperty("user.dir"))
    private val moduleRoot = File(appDir, "src/main/java/com/lioisme/modev")

    private fun resource(vararg parts: String): File = File(appDir, "src/" + parts.joinToString("/"))

    private fun props(path: File): Map<String, String> {
        assertTrue("模块元数据缺失:$path", path.isFile)
        return path.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }

    @Test
    fun `both entrypoints are declared and point at real classes`() {
        val legacyEntryFile = File(moduleRoot, "LegacyModuleEntry.kt")
        val modernEntryFile = File(moduleRoot, "ModevModule.kt")
        assertTrue("legacy 入口源缺失", legacyEntryFile.isFile)
        assertTrue("现代入口源缺失", modernEntryFile.isFile)

        val xposedInit = resource("main", "assets", "xposed_init").readText().trim()
        assertEquals("com.lioisme.modev.LegacyModuleEntry", xposedInit)
        assertTrue(legacyEntryFile.readText().contains("class LegacyModuleEntry : IXposedHookLoadPackage"))

        val javaInit = resource("modern", "resources", "META-INF/xposed/java_init.list").readText().trim()
        assertEquals("com.lioisme.modev.ModevModule", javaInit)
        assertTrue(modernEntryFile.readText().contains("class ModevModule : XposedModule()"))
    }

    @Test
    fun `legacy variant declares no modern entrypoint`() {
        // 旧框架 APK 里若混进 java_init.list,现代加载器与旧加载器的装载顺序就不可预测了。
        assertFalse(
            "legacy 变体不得携带 java_init.list",
            resource("legacy", "resources", "META-INF/xposed/java_init.list").exists(),
        )
    }

    @Test
    fun `module metadata declares identity and matching api floor for both flavors`() {
        val applicationId = Regex("""applicationId\s*=\s*"([^"]+)"""")
            .find(File(appDir, "build.gradle.kts").readText())
            ?.groupValues?.get(1)
        assertEquals("build.gradle.kts 未解析出 applicationId(门禁失效信号)", "com.lioisme.modev", applicationId)

        val modern = props(resource("modern", "resources", "META-INF/xposed/module.prop"))
        val legacy = props(resource("legacy", "resources", "META-INF/xposed/module.prop"))

        for ((flavor, meta) in mapOf("modern" to modern, "legacy" to legacy)) {
            assertEquals("$flavor module.prop 的 id 必须等于 applicationId", applicationId, meta["id"])
            // V50 实锤:modern 此前只有 4 行 API 参数,缺 id/name/version ⇒ 框架侧模块身份
            // 与版本呈现无来源。两个变体的身份段必须齐备且同源。
            for (key in listOf("name", "version", "versionCode", "description")) {
                assertTrue("$flavor module.prop 缺 $key", meta[key]?.isNotBlank() == true)
            }
        }
        assertEquals("82", legacy["minApiVersion"])
        assertEquals("102", modern["minApiVersion"])
        // targetApiVersion 缺失 ⇒ 框架按「未声明上界」处理,行为随框架版本漂移。
        assertEquals(legacy["minApiVersion"], legacy["targetApiVersion"])
        assertEquals(modern["minApiVersion"], modern["targetApiVersion"])
    }

    @Test
    fun `legacy path never names libxposed types in a reachable signature`() {
        // FrameworkBridge 把 serviceFlow 暴露成 StateFlow<Any?> 是刻意的(见其 KDoc):
        // 消费方签名里出现 `XposedService?` 会生成 checkcast,legacy 变体运行时缺 service 库
        // ⇒ **首次发射即抛 NoClassDefFoundError,即使值为 null**。本测试钉住这条不可见契约。
        val consumers = listOf(
            "framework/RemotePrefsBridge.kt",
            "framework/XposedScopeService.kt",
            "ModevApp.kt",
        )
        val offenders = consumers.filter { rel ->
            Regex("""\(\s*service\s*:\s*XposedService""").containsMatchIn(File(moduleRoot, rel).readText())
        }
        assertEquals(
            "框架服务消费方的形参不得直接声明 XposedService 类型(legacy 空值路径会 checkcast):" +
                offenders.joinToString(),
            emptyList<String>(),
            offenders,
        )
        val bridge = File(moduleRoot, "framework/FrameworkBridge.kt").readText()
        assertTrue(
            "serviceFlow 必须继续以 StateFlow<Any?> 对外暴露",
            bridge.contains("val serviceFlow: StateFlow<Any?>"),
        )
    }

    @Test
    fun `both adapters implement the neutral platform contract`() {
        val libxposed = File(moduleRoot, "hook/adapters/LibxposedPlatform.kt").readText()
        val legacy = File(moduleRoot, "hook/adapters/LegacyPlatform.kt").readText()
        val contract = File(moduleRoot, "hook/Platform.kt").readText()

        for ((name, src) in mapOf("LibxposedPlatform" to libxposed, "LegacyPlatform" to legacy)) {
            assertTrue("$name 必须实现 Platform", src.contains(": Platform"))
            assertTrue("$name 必须实现 register", Regex("""override fun register\(""").containsMatchIn(src))
            assertTrue("$name 必须实现 log", Regex("""override fun log\(""").containsMatchIn(src))
        }
        // 干预型注册(setThrowable 通路)是 legacy 与 libxposed 语义分叉点:
        // legacy 靠 MethodHookParam 天然支持,libxposed 需 PASSTHROUGH ⇒ 必须显式覆写。
        assertTrue(
            "libxposed 适配器必须覆写 registerInterventional(默认转 register 不足以承载 setThrowable 通路)",
            Regex("""override fun registerInterventional\(""").containsMatchIn(libxposed),
        )
        assertTrue(
            "Platform 契约的 registerInterventional 必须保留默认实现(legacy 无须覆写)",
            Regex("""fun registerInterventional\([^)]*\)\s*:\s*Boolean\s*=\s*""").containsMatchIn(contract),
        )
        // V43 T3 实锤:API 82 的 MethodHookParam 无 hasResult,短路判定只能近似 + 反射兜底。
        assertTrue(
            "legacy 适配器必须保留 returnEarly 反射判定(否则 setResult(null) 型干预失明)",
            legacy.contains("returnEarly"),
        )
    }
}
