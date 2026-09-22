package com.lioisme.modev.architecture

import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.core.model.UiInteractionRecord
import com.lioisme.modev.core.model.WebViewRecord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 写入模型对账门禁(V45 T2.5,ISO-1 的防回归面):
 *
 *  背景:`domain.record.RecordDraft` 曾是存储 110 列的**第三份平铺镜像**
 *  (实体 / 类型化记录 / Draft),Draft→实体映射是恒等逐列拷贝。
 *  现写入面直接用类型化模型族 [MonitoredRecord],落库经
 *  `data.mapping.MonitoredRecord.writeEntity()` 分派到各 `*RecordMappers.toEntity()`。
 *
 * 本门禁锁定该结构的三条不变量:
 *  P1 **无孤儿列**:实体每个 `@ColumnInfo` 列至少被一个映射器写入
 *     (新增列而无人写 = 静默丢数据,这是平铺镜像时代最易漂移的一处);
 *  P2 **必填键齐备**:每种记录类型的模型都携带 `packageName`/`kind`/`operation`
 *     (MCP 写入面与 taxonomy 校验的共同前提);
 *  P3 **平铺镜像不得复活**:主源集零 `RecordDraft` 引用;
 *  P4 **domain 零 Room 依赖**:方法层不得点名存储注解。
 */
class RecordWriteModelParityTest {

    private val mainRoot: File = File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev")

    private fun readFile(rel: String): String = File(mainRoot, rel).readText()

    private fun mainSources(): List<Pair<String, String>> = mainRoot.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".kt") }
        .map { it.readText() to it.relativeTo(mainRoot).path.replace('\\', '/') }
        .toList()

    /** 类型化模型 → 记录类型(与 `writeEntity()` 分派表同集合,独立复写以成对账)。 */
    private val modelClasses: Map<RecordType, Class<out MonitoredRecord>> = mapOf(
        RecordType.CRYPTO to CryptoRecord::class.java,
        RecordType.DATABASE to SqlRecord::class.java,
        RecordType.BEHAVIOR to BehaviorRecord::class.java,
        RecordType.PREFS to PrefsRecord::class.java,
        RecordType.WEBVIEW to WebViewRecord::class.java,
        RecordType.NETWORK to NetworkRecord::class.java,
        RecordType.DEVICE_ENV to DeviceEnvRecord::class.java,
        RecordType.UI_INTERACTION to UiInteractionRecord::class.java,
        RecordType.SYSTEM to SystemRecord::class.java,
        RecordType.PROCESS_CONTROL to ProcessRecord::class.java,
    )

    @Test
    fun `every entity column has at least one writer`() {
        val entity = readFile("data/db/CryptoRecordEntity.kt")
        val columns = Regex("""@ColumnInfo\(name\s*=\s*"(\w+)"""")
            .findAll(entity)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("实体列解析为空(源形态变更,门禁失效信号)", columns.isNotEmpty())

        // 存储自管列:由数据库/摄入链路赋值,模型映射器不写(登记而非漏写)。
        //  id = Room 自增主键(create 置 0、update 由仓库 .copy(id = …) 覆盖);
        //  seq = 注入侧 WAL 幂等游标(仅 wire 链路需要,宿主直投记录无游标)。
        val storageManaged = setOf("id", "seq")
        assertTrue("存储自管列必须仍在实体上(例外清单已失效): $columns", columns.containsAll(storageManaged))

        val mappers = mainSources().filter { it.second.startsWith("data/mapping/") }
        val written = mutableSetOf<String>()
        for ((text, _) in mappers) {
            for (col in columns) {
                // 命名片段参数赋值即视为写入者:`col = …`(排除 `val col`/比较)
                if (Regex("""(?m)^\s*\Q$col\E\s*=[^=]""").containsMatchIn(text)) written += col
            }
        }
        val orphans = (columns - written - storageManaged).sorted()
        assertEquals(
            "存储列无人写入(新增列必须在某个 *RecordMappers.toEntity() 落地):\n" + orphans.joinToString("\n"),
            emptyList<String>(),
            orphans,
        )
    }

    @Test
    fun `each record type model carries the mandatory write keys`() {
        assertEquals("模型表必须覆盖全部记录类型", RecordType.entries.size, modelClasses.size)
        val mandatory = setOf("packageName", "kind", "operation", "timestamp", "threadName")
        val missing = mutableListOf<String>()
        for ((type, clazz) in modelClasses) {
            val fields = clazz.declaredFields.map { it.name }.toSet()
            (mandatory - fields).forEach { missing += "${type.wire} -> $it" }
            assertTrue("${type.wire} 必须是 MonitoredRecord 实现", MonitoredRecord::class.java.isAssignableFrom(clazz))
        }
        assertEquals(
            "类型化写入模型缺必填键(MCP 写入面/taxonomy 校验前提):\n" + missing.joinToString("\n"),
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `flat column-mirror write model never returns`() {
        // 按「文件存在 + import 行」判定(纯名字文本会命中历史说明性注释,非引用)
        assertTrue(
            "RecordDraft 源文件已随 V45 T2.5 删除,禁止复活",
            !File(mainRoot, "domain/record/RecordDraft.kt").exists(),
        )
        assertTrue(
            "RecordDraft→实体恒等映射文件已删除,禁止复活",
            !File(mainRoot, "data/mapping/RecordDraftMappers.kt").exists(),
        )
        val offenders = mainSources()
            .filter { (text, _) -> text.lines().any { it.startsWith("import com.lioisme.modev.domain.record.RecordDraft") } }
            .map { it.second }
        assertEquals(
            "逐列镜像实体的第三份平铺副本(RecordDraft)禁止再被引用:\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `domain layer never names room annotations`() {
        val offenders = mainSources()
            .filter { (text, path) -> path.startsWith("domain/") && text.contains("androidx.room") }
            .map { it.second }
        assertEquals(
            "domain 子树依赖 Room(方法层必须与存储细节解耦):\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }
}
