package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTaxonomyTest {

    @Test
    fun crypto_categories_match_recommended_noise_set() {
        val off = CategoryTaxonomy.forType(RecordType.CRYPTO).filter { !it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(
            setOf(
                CryptoKind.KEY_PARAM.name, CryptoKind.KEY_GENERATION.name, CryptoKind.KEY_FACTORY.name,
                CryptoKind.KEY_STORE.name, CryptoKind.KEY_AGREEMENT.name, CryptoKind.RANDOM.name,
                CryptoKind.BASE64.name, CryptoKind.WEB_CRYPTO.name,
            ),
            off,
        )
        val on = CategoryTaxonomy.forType(RecordType.CRYPTO).filter { it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(
            setOf(
                CryptoKind.CIPHER.name, CryptoKind.MESSAGE_DIGEST.name, CryptoKind.MAC.name,
                CryptoKind.SIGNATURE.name,
            ),
            on,
        )
    }

    @Test
    fun db_categories_match_recommended_noise_set() {
        val off = CategoryTaxonomy.forType(RecordType.DATABASE).filter { !it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(setOf(SqlKind.OPEN.name, SqlKind.TRANSACTION.name), off)
        val on = CategoryTaxonomy.forType(RecordType.DATABASE).filter { it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(setOf(SqlKind.QUERY.name, SqlKind.DML.name, SqlKind.EXEC.name), on)
    }

    @Test
    fun forType_returns_ordered_categories() {
        val crypto = CategoryTaxonomy.forType(RecordType.CRYPTO)
        assertEquals(12, crypto.size)
        assertEquals((0..11).toList(), crypto.map { it.order })
        assertEquals(CryptoKind.CIPHER.name, crypto.first().id)
        val db = CategoryTaxonomy.forType(RecordType.DATABASE)
        assertEquals(5, db.size)
        assertEquals(SqlKind.OPEN.name, db.first().id)
    }

    @Test
    fun validateKinds_null_or_empty_returns_no_filter() {
        assertNull(CategoryTaxonomy.validateKinds(RecordTypes.CRYPTO, null))
        assertNull(CategoryTaxonomy.validateKinds(RecordTypes.CRYPTO, emptySet()))
    }

    @Test
    fun validateKinds_no_type_keeps_original() {
        val kinds = setOf(CryptoKind.CIPHER.name, SqlKind.OPEN.name)
        assertEquals(kinds, CategoryTaxonomy.validateKinds(null, kinds))
        assertEquals(kinds, CategoryTaxonomy.validateKinds("unknown", kinds))
    }

    @Test
    fun validateKinds_scopes_to_type_and_may_be_empty() {
        // 跨类型 kind 被收敛:db 的 OPEN 不属于 crypto → 空交集
        assertEquals(emptySet<String>(), CategoryTaxonomy.validateKinds(RecordTypes.CRYPTO, setOf(SqlKind.OPEN.name)))
        // 合法 crypto kind 保留
        assertEquals(
            setOf(CryptoKind.CIPHER.name),
            CategoryTaxonomy.validateKinds(RecordTypes.CRYPTO, setOf(CryptoKind.CIPHER.name, SqlKind.OPEN.name)),
        )
        // db 视图:OPEN 合法
        assertEquals(
            setOf(SqlKind.OPEN.name),
            CategoryTaxonomy.validateKinds(RecordTypes.DATABASE, setOf(SqlKind.OPEN.name)),
        )
    }

    @Test
    fun validateOperations_scopes_to_type() {
        assertEquals(emptySet<String>(), CategoryTaxonomy.validateOperations(RecordTypes.CRYPTO, setOf(SqlOperation.INSERT.name)))
        assertTrue(
            CategoryTaxonomy.validateOperations(RecordTypes.DATABASE, setOf(SqlOperation.INSERT.name))!!
                .contains(SqlOperation.INSERT.name),
        )
    }

    @Test
    fun defaultEnabledIds_aligns_with_entries() {
        assertEquals(
            CategoryTaxonomy.forType(RecordType.CRYPTO).filter { it.defaultEnabled }.map { it.id }.toSet(),
            CategoryTaxonomy.defaultEnabledIds(RecordType.CRYPTO),
        )
    }
}
