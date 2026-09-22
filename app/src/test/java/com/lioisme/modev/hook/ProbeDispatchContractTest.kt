package com.lioisme.modev.hook

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.probes.AlgorithmProbe
import com.lioisme.modev.hook.probes.BehaviorProbe
import com.lioisme.modev.hook.probes.DatabaseProbe
import com.lioisme.modev.hook.probes.DeviceEnvProbe
import com.lioisme.modev.hook.probes.NetworkProbe
import com.lioisme.modev.hook.probes.PrefsProbe
import com.lioisme.modev.hook.probes.ProcessControlProbe
import com.lioisme.modev.hook.probes.SystemProbe
import com.lioisme.modev.hook.probes.UiInteractionProbe
import com.lioisme.modev.hook.probes.WebViewProbe
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探针派发契约(T4.1):V44 T2.4 的冷启动裁剪把「整组装钩跳过」的决定权交给
 * [Probe.enabledIn] —— 一个探针接错 master 字段,后果是**能力静默失效**(开关开着却零钩子)
 * 或**该跳过的整组钩子照装**(白付宿主启动税)。本类把三件事钉死:
 * ①10 个监听域 master 与 10 个探针一一对应(逐个单关,只允许它自己变红);
 * ②recordType 与 [RecordTypes] 单射且不重复(宿主管道按此分发,重复即串库);
 * ③探针目录里的每个实现都必须出现在 `HookInstaller` 默认清单(新增能力漏注册即红)。
 */
class ProbeDispatchContractTest {

    private class Binding(
        val label: String,
        val probe: Probe,
        val turnMasterOff: (HookSwitch) -> HookSwitch,
    )

    private val bindings = listOf(
        Binding("cryptoListen", AlgorithmProbe()) { it.copy(cryptoListen = false) },
        Binding("dbListen", DatabaseProbe()) { it.copy(dbListen = false) },
        Binding("behaviorListen", BehaviorProbe()) { it.copy(behaviorListen = false) },
        Binding("prefsListen", PrefsProbe()) { it.copy(prefsListen = false) },
        Binding("webviewListen", WebViewProbe()) { it.copy(webviewListen = false) },
        Binding("networkListen", NetworkProbe()) { it.copy(networkListen = false) },
        Binding("deviceEnvListen", DeviceEnvProbe()) { it.copy(deviceEnvListen = false) },
        Binding("uiListen", UiInteractionProbe()) { it.copy(uiListen = false) },
        Binding("systemListen", SystemProbe()) { it.copy(systemListen = false) },
        Binding("processControlListen", ProcessControlProbe()) { it.copy(processControlListen = false) },
    )

    private val probes: List<Probe> get() = bindings.map { it.probe }

    @Test
    fun `内置默认配置必须让全部探针生效`() {
        val off = bindings.filter { !it.probe.enabledIn(HookSwitch.DEFAULT) }.map { it.label }
        assertTrue("默认配置下不得有任何探针被裁剪(裁了就是接错了闸门):$off", off.isEmpty())
    }

    @Test
    fun `每个监听 master 恰好只裁掉自己的探针`() {
        bindings.forEachIndexed { index, binding ->
            val effective = binding.turnMasterOff(HookSwitch.DEFAULT)
            assertFalse(
                "关闭 ${binding.label} 后,${binding.probe.javaClass.simpleName} 必须整组跳过",
                binding.probe.enabledIn(effective),
            )
            val collateral = bindings.withIndex().filter { (i, other) ->
                i != index && !other.probe.enabledIn(effective)
            }.map { (_, other) -> other.javaClass.simpleName }
            assertTrue(
                "关闭 ${binding.label} 不得波及其他探针(误伤即静默失效):$collateral",
                collateral.isEmpty(),
            )
        }
    }

    @Test
    fun `监听域全关时十个探针一律跳过`() {
        val allOff = HookSwitch.DEFAULT.copy(
            cryptoListen = false,
            dbListen = false,
            behaviorListen = false,
            prefsListen = false,
            webviewListen = false,
            networkListen = false,
            deviceEnvListen = false,
            uiListen = false,
            systemListen = false,
            processControlListen = false,
        )
        assertEquals("十个探针应全部裁剪", 0, probes.count { it.enabledIn(allOff) })
    }

    @Test
    fun `探针只关 master 不足以裁剪_kind 级开关不得影响装配闸门`() {
        // enabledIn 的契约是「本探针所有 kind 都被同一 master 覆盖」⇒ 只关 kind(如
        // messageDigest)时 master 仍在,整组必须照装(否则 kind 关闭会连坐整域钩子)。
        val kindsOnly = HookSwitch.DEFAULT.copy(messageDigest = false, cipher = false, mac = false)
        assertTrue("kind 级关闭不得裁剪算法探针", AlgorithmProbe().enabledIn(kindsOnly))
    }

    @Test
    fun `recordType 与监听类型表一一对应且不重复`() {
        val expected = listOf(
            RecordTypes.CRYPTO,
            RecordTypes.DATABASE,
            RecordTypes.BEHAVIOR,
            RecordTypes.PREFS,
            RecordTypes.WEBVIEW,
            RecordTypes.NETWORK,
            RecordTypes.DEVICE_ENV,
            RecordTypes.UI_INTERACTION,
            RecordTypes.SYSTEM,
            RecordTypes.PROCESS_CONTROL,
        )
        assertEquals(
            "探针产出类型必须与 RecordTypes 全量对齐(缺一个 = 该类型的记录无处可归)",
            expected.sorted(),
            probes.map { it.recordType }.sorted(),
        )
        assertEquals("recordType 不得重复", expected.size, probes.map { it.recordType }.toSet().size)
    }

    @Test
    fun `未声明闸门的探针默认恒装(保守档)`() {
        val naive = object : Probe {
            override val recordType: String = RecordTypes.CRYPTO
            override fun install(context: ProbeContext) = Unit
        }
        assertTrue(
            "接口默认值必须是 true —— 遗漏声明只允许多装,不允许静默不装",
            naive.enabledIn(HookSwitch.DEFAULT.copy(cryptoListen = false)),
        )
    }

    @Test
    fun `干预型标记集合与审计面一致`() {
        val marked = probes.filter { it is InterventionProbeMarker }.map { it.javaClass.simpleName }.sorted()
        assertEquals(
            "干预型探针(会改 setResult/拦弹窗/拦进程)必须恰为这四个(装配日志与审计靠此标记)",
            listOf(
                "DeviceEnvProbe",
                "NetworkProbe",
                "ProcessControlProbe",
                "UiInteractionProbe",
            ),
            marked,
        )
    }

    @Test
    fun `每个探针实现都必须注册进 HookInstaller 默认清单`() {
        val root = locateProjectRoot()
        val installer = File(root, "app/src/main/java/com/lioisme/modev/hook/HookInstaller.kt")
        assertTrue("HookInstaller 源码不存在:$installer", installer.isFile)
        val source = installer.readText(Charsets.UTF_8)

        val probeDir = File(root, "app/src/main/java/com/lioisme/modev/hook/probes")
        assertTrue("探针目录不存在:$probeDir", probeDir.isDirectory)
        val declared = probeDir.listFiles { f -> f.isFile && f.extension == "kt" }!!
            .map { f -> f.nameWithoutExtension }
            .sorted()

        val missing = declared.filterNot { Regex("""$it\(\)""").containsMatchIn(source) }
        assertTrue(
            "探针类已实现但未注册进装配清单(能力静默失效):$missing",
            missing.isEmpty(),
        )
        declared.forEach { name ->
            val hits = Regex("""$name\(\)""").findAll(source).count()
            assertEquals("$name 在装配清单中必须恰好出现一次", 1, hits)
        }
        assertEquals("探针实现数与本次锁定的清单不一致(新增/删除探针须同步本测试)", 10, declared.size)
    }

    /** 源码扫描测试共用:从工作目录上溯找工程根(与各 architecture 测试同一手法)。 */
    private fun locateProjectRoot(): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            if (dir != null && File(dir, "app/src/main/java/com/lioisme/modev/hook").isDirectory) return dir
            dir = dir?.parentFile
        }
        error("project root not found from ${File("").absolutePath}")
    }
}
