package com.lioisme.modev.hook

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.hooks.PrefsGetHooker
import com.lioisme.modev.hook.hooks.PrefsOpenHooker
import com.lioisme.modev.hook.probes.PrefsProbe
import java.io.File
import java.lang.reflect.Executable
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 偏好监听真机端到端(V28):宿主进程内以 PrefsProbe 装配三 Hooker
 * (PrefsFakePlatform 捕获注册,手动按调用时序驱动真实 SharedPreferencesImpl),
 * 验证 OPEN→GET→PUT→REMOVE→CLEAR 全链产出、实例索引路径回填、
 * fileContentCapture 闸门(D6)与开关关闭零产出。
 */
@RunWith(AndroidJUnit4::class)
class PrefsProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var platform: PrefsFakePlatform
    private lateinit var sink: RecordSink
    private lateinit var captured: MutableList<MonitoredRecord>
    private lateinit var configVar: HookSwitch
    private lateinit var prefsFile: File

    @Before
    fun setUp() {
        configVar = HookSwitch(
            prefsOpen = true,
            prefsGet = true,
            prefsPut = true,
            prefsRemove = true,
            prefsClear = true,
        )
        platform = PrefsFakePlatform()
        captured = Collections.synchronizedList(ArrayList())
        sink = RecordSink(
            jsonOf = { record ->
                captured.add(record)
                "{}"
            },
            sendJson = { true },
            isConnected = { true },
        )
        sink.start()
        val probeContext = ProbeContext(
            engine = HookEngine(platform),
            config = { configVar },
            tracker = CryptoContextTracker,
            sink = sink,
            packageName = "com.lioisme.modev.test",
        )
        PrefsProbe().install(probeContext)
        prefsFile = File(context.filesDir, "probe_prefs.xml")
        prefsFile.delete()
    }

    @After
    fun tearDown() {
        prefsFile.delete()
    }

    @Test
    fun open_get_put_remove_clear_full_chain_with_path_backfill() {
        // OPEN:构造器 after(首参 File)→ 路径登记 + OPEN 记录(mode 入 params)
        val impl = openPrefs()
        val open = awaitPrefs(1)[0] as PrefsRecord
        assertEquals("dump=$open", prefsFile.absolutePath, open.path)

        // GET_STRING:返回值捕获(fileContentCapture 默认关 → value=null,type 入 params)
        val editor = fireEdit(impl)
        val got = impl.getString("server_url", null)
        fireAfter(
            method("android.app.SharedPreferencesImpl", "getString", String::class.java, String::class.java),
            impl, arrayOf("server_url", null), got,
        )
        val getRecord = awaitPrefs(2)[1] as PrefsRecord
        assertEquals(PrefsOperation.GET_STRING, getRecord.operation)
        assertEquals(PrefsKind.GET, getRecord.kind)
        assertEquals("server_url", getRecord.key)
        assertNull(getRecord.value)
        assertEquals("string", getRecord.params?.get(PrefsGetHooker.PARAM_TYPE))
        assertEquals("getDump=$getRecord capture=${configVar.fileContentCapture}", prefsFile.absolutePath, getRecord.path)

        // PUT_STRING:实参暂存值捕获(editor 实例索引回填路径)
        editor.putString("server_url", "https://example.com")
        fireAfter(
            method(PrefsOpenHooker.IMPL_CLASS + "\$EditorImpl", "putString", String::class.java, String::class.java),
            editor, arrayOf("server_url", "https://example.com"), editor,
        )
        val put = awaitPrefs(3)[2] as PrefsRecord
        assertEquals(PrefsOperation.PUT_STRING, put.operation)
        assertEquals(PrefsKind.PUT, put.kind)
        assertEquals("server_url", put.key)
        assertNull(put.value)
        assertEquals(prefsFile.absolutePath, put.path)

        // REMOVE / CLEAR
        editor.remove("server_url")
        fireAfter(
            method(PrefsOpenHooker.IMPL_CLASS + "\$EditorImpl", "remove", String::class.java),
            editor, arrayOf("server_url"), editor,
        )
        val remove = awaitPrefs(4)[3] as PrefsRecord
        assertEquals(PrefsOperation.REMOVE, remove.operation)
        assertEquals("server_url", remove.key)

        editor.clear()
        fireAfter(
            method(PrefsOpenHooker.IMPL_CLASS + "\$EditorImpl", "clear"),
            editor, emptyArray(), editor,
        )
        val clear = awaitPrefs(5)[4] as PrefsRecord
        assertEquals(PrefsOperation.CLEAR, clear.operation)
        assertNull(clear.key)
    }

    @Test
    fun content_capture_on_carries_value_text() {
        configVar = configVar.copy(fileContentCapture = true)
        val impl = openPrefs()
        // OPEN 记录经 sink 工作线程异步到达:先等待落定再清空,防竞态污染后续计数
        awaitPrefs(1)
        captured.clear()
        val editor = fireEdit(impl)
        editor.putString("k", "hello")
        fireAfter(
            method(PrefsOpenHooker.IMPL_CLASS + "\$EditorImpl", "putString", String::class.java, String::class.java),
            editor, arrayOf("k", "hello"), editor,
        )
        val put = awaitPrefs(1)[0] as PrefsRecord
        assertEquals("putDump=$put capture=${configVar.fileContentCapture}", "hello", put.value?.text)
    }

    @Test
    fun prefs_switches_off_emits_nothing() {
        configVar = HookSwitch(
            prefsOpen = false, prefsGet = false, prefsPut = false,
            prefsRemove = false, prefsClear = false,
        )
        val impl = openPrefs()
        val editor = fireEdit(impl)
        editor.putString("x", "y")
        fireAfter(
            method(PrefsOpenHooker.IMPL_CLASS + "\$EditorImpl", "putString", String::class.java, String::class.java),
            editor, arrayOf("x", "y"), editor,
        )
        Thread.sleep(500)
        assertEquals(0, captured.size)
    }

    // region 工具

    /** 反射构造真实 SharedPreferencesImpl(hide 类;公有构造器即磁盘载入)并补发 after 回调。 */
    private fun openPrefs(): android.content.SharedPreferences {
        val implClass = Class.forName(PrefsOpenHooker.IMPL_CLASS)
        val implCtor = implClass.declaredConstructors
            .firstOrNull { it.parameterTypes.firstOrNull() == File::class.java }!!
        implCtor.isAccessible = true
        val sp = implCtor.newInstance(prefsFile, Context.MODE_PRIVATE) as android.content.SharedPreferences
        // 构造器 after 的 thisObject = 新建实例(与真实 Invocation 契约一致;实例索引登记依赖它)
        platform.fireAfter(implCtor, sp, arrayOf(prefsFile, Context.MODE_PRIVATE), sp)
        return sp
    }

    private fun fireEdit(impl: android.content.SharedPreferences): android.content.SharedPreferences.Editor {
        val editor = impl.edit()
        platform.fireAfter(
            method("android.app.SharedPreferencesImpl", "edit"),
            impl, emptyArray(), editor,
        )
        return editor
    }

    private fun method(className: String, name: String, vararg params: Class<*>): java.lang.reflect.Method =
        Class.forName(className).getMethod(name, *params)

    private fun fireAfter(method: Executable, thisObject: Any?, args: Array<Any?>, result: Any?) {
        platform.fireAfter(method, thisObject, args, result)
    }

    private fun awaitPrefs(count: Int): List<MonitoredRecord> {
        val deadline = System.currentTimeMillis() + 5000
        while (captured.count { it is PrefsRecord } < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        val records = captured.filterIsInstance<PrefsRecord>().take(count)
        assertEquals(count, records.size)
        return records
    }

    // endregion
}

private class PrefsFakePlatform : Platform {

    private val registrations = LinkedHashMap<Executable, MutableList<MethodHook>>()

    override fun register(executable: Executable?, hook: MethodHook): Boolean {
        if (executable == null) return false
        registrations.getOrPut(executable) { mutableListOf() }.add(hook)
        return true
    }

    override fun log(message: String, throwable: Throwable?) {}

    fun fireAfter(method: Executable, thisObject: Any?, args: Array<Any?>, result: Any?) {
        registrations[method]?.forEach { hook ->
            hook.after(PrefsSimpleInvocation(method, thisObject, args, result))
        }
    }
}

private class PrefsSimpleInvocation(
    override val executable: Executable?,
    override val thisObject: Any?,
    private val args: Array<Any?>,
    override val result: Any?,
) : Invocation() {

    override val argCount: Int get() = args.size

    override fun getArgUnsafe(index: Int): Any? = args[index]
}
