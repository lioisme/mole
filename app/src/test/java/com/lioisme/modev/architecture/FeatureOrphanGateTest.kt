package com.lioisme.modev.architecture

import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.RecordType
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 孤儿功能门禁(V50 需求 2:「禁止有任何一个孤儿功能」)。
 *
 * 孤儿 = **在分类法里登记、在 UI/MCP 上可选、在筛选菜单里可点,但注入侧永不发射**的条目。
 * 它是本仓最贵的一类缺陷:用户勾了开关、筛了操作,列表恒空,而全部单测照绿
 * (开关位存在 ≠ 有人读它;枚举值存在 ≠ 有人发射它)。
 *
 * 三条不变量,方向互补:
 *  O1 每个登记类别(kind)必须在注入侧源集被引用 —— 即真的有钩子挂它;
 *  O2 每个操作枚举值必须在注入侧源集被引用 —— 否则筛选菜单上是一个恒空选项;
 *  O3 注入侧引用的 kind 枚举名必须全部在分类法登记 —— 反向旁路(发射了但没人能筛/没有开关)。
 *
 * 判据是「被引用」而非「被执行」:它能挡住 WEB_CRYPTO 型死行与 BUILD_FIELDS 型死钩的**声明面**,
 * 执行面的真实性仍归设备注入矩阵(V49 结论:生效判据 = 靶程序读回)。
 */
class FeatureOrphanGateTest {

    private val mainRoot: File = File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev")

    /** 注入侧源集:hook 全子树(含 probes/hooks/webview/adapters)。 */
    private fun injectionSources(): List<Pair<String, String>> =
        File(mainRoot, "hook").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.readText() to it.relativeTo(mainRoot).path.replace('\\', '/') }
            .toList()

    @Test
    fun `O1 every registered kind is referenced by the injection side`() {
        val sources = injectionSources()
        assertTrue("注入侧源集为空(源目录形态变更 = 门禁失效信号)", sources.isNotEmpty())

        val orphans = CategoryTaxonomy.all()
            .filter { category ->
                // 引用形态:`SomeKind.<ID>`(枚举成员访问)。发射点恒为此形态
                // (`kind = CryptoKind.CIPHER` / `when (CryptoKind.X)` / `featureOn(DeviceEnvKind.Y)`)。
                val referenced = sources.any { (text, _) -> text.contains(".${category.id}") }
                !referenced
            }
            .map { "${it.type.wire}:${it.id}(domain=${it.domain})" }

        assertTrue(
            "孤儿类别:已登记、有开关行、可被筛选,但注入侧零引用 ⇒ 勾选后永不产出记录或永不干预:\n" +
                orphans.joinToString("\n"),
            orphans.isEmpty(),
        )
    }

    @Test
    fun `O2 every operation enum value is referenced by the injection side`() {
        val sources = injectionSources()
        val orphans = RecordType.entries.flatMap { type ->
            CategoryTaxonomy.operationsOf(type)
                .filter { operation -> !sources.any { (text, _) -> text.contains(".$operation") } }
                .map { "${type.wire}:$it" }
        }

        assertTrue(
            "孤儿操作:枚举已登记、四语有标签、筛选菜单可点,但注入侧零发射 ⇒ 恒空选项:\n" +
                orphans.joinToString("\n"),
            orphans.isEmpty(),
        )
    }

    @Test
    fun `O3 every kind id used in the injection side is registered in the taxonomy`() {
        val sources = injectionSources()
        val registered = CategoryTaxonomy.all().map { it.id }.toSet()
        // 注入侧所有 `<Kind>.<NAME>` 形态引用中,名字须能在分类法里找到(大小写敏感)。
        val pattern = Regex("""\b\w*(?:Kind)\.([A-Z][A-Z0-9_]+)""")
        val unregistered = sources
            .flatMap { (text, path) ->
                pattern.findAll(text).map { it.groupValues[1] to path }.toList()
            }
            .filter { (name, _) -> name !in registered }
            .map { (name, path) -> "$name <- $path" }
            .distinct()
            .sorted()

        assertTrue(
            "未登记类别:注入侧发射/门控了分类法里没有的 kind ⇒ 无开关、无标签、无法筛选:\n" +
                unregistered.joinToString("\n"),
            unregistered.isEmpty(),
        )
    }

    /**
     * O4(V50 需求 2 的登记侧):两域类别数与能力表口径必须由本文件**唯一**硬编码处登记,
     * 且每个域至少有一行 —— 防「新类别忘了标域」之外的另一半:标了域但整域为空。
     */
    @Test
    fun `O4 both domains stay populated and the split is explicit`() {
        val listen = CategoryTaxonomy.forDomain(CapabilityDomain.LISTEN).size
        val feature = CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE).size
        val total = CategoryTaxonomy.all().size
        assertTrue("两域均须非空(LISTEN=$listen FEATURE=$feature)", listen > 0 && feature > 0)
        assertTrue("两域之和必须等于全类别数(有类别未标域即不等):$listen + $feature != $total",
            listen + feature == total)
    }
}
