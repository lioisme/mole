package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 偏差模型持久化对账门禁(V50,实锤缺陷 S1 的防回归面)。
 *
 * 背景:[AppListenOverride.toJson] 走 Gson 反射(**自动**写出全部属性),
 * 而 [AppListenOverride.fromJson] 是**手写逐键**解析。两者不对称 ⇒
 * 新增属性时忘了补解析臂,编译与全部既有单测都照常绿(测试经内存对象直读,
 * 从不走「落盘 JSON → 解析」这一圈),但用户侧表现为
 * **「重启后自己改的东西全部消失」** —— V49 R-2 的每应用画像微调正是这样静默失效数轮。
 *
 * 属性清单取自主构造器源文本(不用 `kotlin.reflect.full`:测试类路径无 kotlin-reflect,
 * 且本门禁属源码扫描族,同源输入更自洽)。两条断言方向互补:
 *  A1 每个属性都必须有对应的解析臂(缺 = 写得出读不回 = 静默丢数据);
 *  A2 每个 `FIELD_*` 常量都必须对应一个真实属性(多 = 解析/写入了不存在的键)。
 */
class AppListenOverridePersistenceTest {

    private val source: File =
        File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev/core/model/AppListenOverride.kt")

    /** 主构造器里的 `val xxx:` 清单 = 模型属性唯一事实源。 */
    private fun constructorProperties(text: String): Set<String> {
        val params = text.substringAfter("data class AppListenOverride(").substringBefore("\n) {")
        val names = Regex("""(?m)^\s*val\s+(\w+)\s*:""").findAll(params).map { it.groupValues[1] }.toList()
        assertTrue("主构造器属性解析为空或过少(源形态变更 = 门禁失效信号): $names", names.size > 20)
        return names.toSet()
    }

    /** JSON 键名与属性名的等价判定(允许 `processControl` ↔ `processControlListen` 这类前后缀形态)。 */
    private fun matches(key: String, prop: String): Boolean =
        key == prop || prop.startsWith(key) || key.startsWith(prop)

    @Test
    fun `A1 every override property has a fromJson parse arm`() {
        val text = source.readText()
        val properties = constructorProperties(text)
        // 扫描区 = 从 `fun fromJson(` 到文件尾,**含其后的私有解析器**(`kindsOf` 等),
        // 否则 `kinds` 这类「经 helper 解析」的属性会被误判成孤儿。
        val parseRegion = text.substringAfter("fun fromJson(")
        assertTrue("fromJson 解析区未定位到(源形态变更 = 门禁失效信号)", parseRegion.length > 100)

        val constants = Regex("""const val (FIELD_\w+)\s*=\s*"([^"]+)"""")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val parsedKeys = Regex("""\bFIELD_\w+\b""")
            .findAll(parseRegion)
            .mapNotNull { constants[it.value] }
            .toSet()

        val missing = properties.filter { prop -> parsedKeys.none { matches(it, prop) } }.sorted()
        assertEquals(
            "属性可被 toJson 写出却无 fromJson 解析臂 ⇒ 落盘后读回即丢(重启失效):" +
                "\n${missing.joinToString("\n")}\n" +
                "补法:在 fromJson 的构造调用里加一行 `<prop> = <解析器>(obj, FIELD_<XXX>)` 并登记常量;" +
                "确实不该持久化的属性,须在本测试里显式登记理由。",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `A2 every FIELD constant maps to a constructor property`() {
        val text = source.readText()
        val properties = constructorProperties(text)
        val parsedKeys = Regex("""const val FIELD_\w+\s*=\s*"([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()
        val dangling = parsedKeys.filter { key -> properties.none { matches(key, it) } }.sorted()
        assertEquals(
            "FIELD_ 常量不对应任何主构造器属性(键名漂移,或属性已删而常量留着 = 解析死键):" +
                "\n${dangling.joinToString("\n")}",
            emptyList<String>(),
            dangling,
        )
    }

    /**
     * A3 **行为级**往返对账(比 A1/A2 的源码扫描更接近用户可见结果):
     * 逐属性构造一个「非默认值」的偏差对象,经真实 toJson → fromJson 后必须逐项相等。
     * 本条直接钉住 S1 的用户可见形态。
     */
    @Test
    fun `A3 full deviation object survives a real json roundtrip`() {
        val gson = com.google.gson.GsonBuilder().serializeNulls().create()
        val seeded = AppListenOverride(
            cryptoListen = false,
            dbListen = true,
            behaviorListen = false,
            prefsListen = true,
            webviewListen = false,
            networkListen = true,
            deviceEnvListen = false,
            uiListen = true,
            systemListen = false,
            processControlListen = true,
            kinds = mapOf("crypto:CIPHER" to false, "behavior:SHELL_EXEC" to true),
            logStack = true,
            maxBytesPerField = 4096,
            ignoreStackPrefixes = listOf("com.example"),
            maxResultRows = 7,
            maxResultChars = 77,
            fileContentCapture = true,
            ignorePathPrefixes = listOf("/sdcard/x"),
            dialogKeywords = listOf("升级"),
            spoofProfileId = "builtin:pixel7pro",
            spoofOverrides = mapOf("build.MODEL" to "Legacy"),
            profileOverrides = mapOf("model" to "Pixel 8", "simOperator" to ""),
            extraRootPackages = listOf("com.evil.su"),
            extraXposedPrefixes = listOf("org.lsposed"),
            extraFridaPorts = listOf(27042),
            extraMaskKeywords = listOf("frida"),
            keywordIds = listOf(1L, 2L),
            keywordFollowGlobal = false,
        )
        val restored = AppListenOverride.fromJson(gson, seeded.toJson(gson))
        assertEquals(
            "逐属性偏差落盘后读回不一致(每一项都必须有解析臂,含 V49 R-2 的 profileOverrides)",
            seeded,
            restored,
        )
    }
}
