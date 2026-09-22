package com.lioisme.modev.domain.record

import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.RecordTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 筛选契约收敛表(V45 T1.8 / QA-6):`kinds`/`operations` 必须收敛到
 * **所选记录类型的合法名集** —— 单类型取该类型集(原语义),
 * 多类型取并集(旧形态在此完全不收敛,跨类同名 id 会命中未选类型的行);
 * 未选类型 / 全未知类型名 = 不收敛(交由协议层拒绝)。
 *
 * 表驱动:每行 = (所选类型, 入参 kinds/ops, 期望收敛集),
 * 空集期望即「筛无可筛」——仓库层据此短路为零结果(禁退化为「忽略该维度」)。
 */
class RecordFilterConvergenceTest {

    private fun filter(types: Set<String>?, kinds: Set<String>? = null, ops: Set<String>? = null) =
        RecordFilter(recordTypes = types, kinds = kinds, operations = ops).normalize()

    @Test
    fun `single type converges to its own kind set`() {
        val f = filter(
            types = setOf(RecordTypes.CRYPTO),
            kinds = setOf("CIPHER", SqlKindLikeOpen, "GET"),
        )
        assertEquals(setOf("CIPHER"), f.kinds)
    }

    @Test
    fun `multi type converges to the union of the selected types`() {
        // OPEN 同名于 SQL 与 Prefs 两域;PUT 仅 Prefs;CIPHER 仅算法(未选 → 必须剔除)
        val f = filter(
            types = setOf(RecordTypes.DATABASE, RecordTypes.PREFS),
            kinds = setOf(SqlKindLikeOpen, "PUT", "CIPHER"),
        )
        assertEquals(setOf(SqlKindLikeOpen, "PUT"), f.kinds)
    }

    @Test
    fun `operations converge the same way`() {
        val f = filter(
            types = setOf(RecordTypes.CRYPTO, RecordTypes.DATABASE),
            ops = setOf("ENCRYPT", "BEGIN_TRANSACTION", "MEOW"),
        )
        assertEquals(setOf("ENCRYPT", "BEGIN_TRANSACTION"), f.operations)
    }

    @Test
    fun `no overlap yields empty set, not a silently ignored dimension`() {
        val f = filter(types = setOf(RecordTypes.DATABASE), kinds = setOf("CIPHER"))
        assertEquals("清空后必须是空集(零结果短路),不得回退 null(不过滤)", emptySet<String>(), f.kinds)
        // 空集在谓词生成侧短路为「永不匹配」,而非丢弃该维度
        assertTrue(f.kinds!!.isEmpty())
    }

    @Test
    fun `absent or unknown type selection never converges`() {
        // 无类型约束 = 无从收敛:入参原样保留(全库 kind 名可能合法属于任一类型)
        assertEquals(setOf("CIPHER"), filter(types = null, kinds = setOf("CIPHER")).kinds)
        assertEquals(setOf("CIPHER"), filter(types = emptySet(), kinds = setOf("CIPHER")).kinds)
        // 全未知类型名(协议漂移):维持入参,由类型校验在入口拒绝
        assertEquals(setOf("CIPHER"), filter(types = setOf("not_a_type"), kinds = setOf("CIPHER")).kinds)
    }

    @Test
    fun `null and empty inputs stay null`() {
        assertNull(filter(types = setOf(RecordTypes.PREFS), kinds = null).kinds)
        assertNull(filter(types = setOf(RecordTypes.PREFS), kinds = emptySet()).kinds)
    }

    @Test
    fun `convergence agrees with the taxonomy source`() {
        val allTypes = com.lioisme.modev.core.model.RecordType.entries.map { it.wire }.toSet()
        val probe = setOf("OPEN", "GET", "QUERY", "DIALOG_KEYWORD", "CIPHER")
        val expected = com.lioisme.modev.core.model.RecordType.entries
            .flatMap { CategoryTaxonomy.forType(it) }
            .map { it.id }
            .toSet()
        assertEquals(probe.intersect(expected), filter(types = allTypes, kinds = probe).kinds)
    }

    /** 跨类同名 kind(SQL 与 Prefs 皆有 OPEN);常量化以免字面量漂移。 */
    private val SqlKindLikeOpen = "OPEN"
}
