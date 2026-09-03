package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `device_env_categories_match_defaults`() {
        // V33:所有 DeviceEnv 干预类 defaultEnabled 改为 false(安全优先,与 HookSwitch.DEFAULT 对齐)
        val off = CategoryTaxonomy.forType(RecordType.DEVICE_ENV).filter { !it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(21, off.size)
        val on = CategoryTaxonomy.forType(RecordType.DEVICE_ENV).filter { it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(0, on.size)
    }

    @Test
    fun `network_intervention_categories_match_hookswitch_defaults`() {
        // V34 审计:干预类(SSL/代理/VPN) defaultEnabled 应为 false,与 HookSwitch 一致
        val off = CategoryTaxonomy.forType(RecordType.NETWORK).filter { !it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(
            setOf(
                NetworkKind.SSL_CONTEXT.name, NetworkKind.SSL_TRUST_MANAGER.name,
                NetworkKind.SSL_OKHTTP_PINNER.name, NetworkKind.SSL_OKHTTP_VERIFIER.name,
                NetworkKind.SSL_WEBVIEW.name, NetworkKind.SSL_NETWORK_CONFIG.name,
                NetworkKind.PROXY_SYSTEM_PROP.name, NetworkKind.PROXY_CONNECTIVITY.name,
                NetworkKind.PROXY_SETTINGS.name, NetworkKind.PROXY_OKHTTP.name,
                NetworkKind.VPN_CAPABILITIES.name, NetworkKind.VPN_INTERFACE.name,
                NetworkKind.VPN_NETWORK_INFO.name, NetworkKind.VPN_LINK_PROPERTIES.name,
            ),
            off,
        )
        // 观测类(REQUEST) defaultEnabled 应为 true
        val on = CategoryTaxonomy.forType(RecordType.NETWORK).filter { it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(setOf(NetworkKind.REQUEST.name), on)
    }

    @Test
    fun `validateOperations includes DeviceEnvOperation entries`() {
        val valid = CategoryTaxonomy.validateOperations(RecordTypes.DEVICE_ENV, setOf("FILE_EXISTS", "CLASS_FOR_NAME"))
        assertNotNull(valid)
        assertEquals(2, valid!!.size)
    }

    @Test
    fun defaultEnabledIds_aligns_with_entries() {
        assertEquals(
            CategoryTaxonomy.forType(RecordType.CRYPTO).filter { it.defaultEnabled }.map { it.id }.toSet(),
            CategoryTaxonomy.defaultEnabledIds(RecordType.CRYPTO),
        )
    }

    @Test
    fun `all CategoryTaxonomy entries have valid enum kind ids`() {
        for (recordType in RecordType.entries) {
            val categories = CategoryTaxonomy.forType(recordType)
            assert(categories.isNotEmpty()) { "CategoryTaxonomy has no entries for $recordType" }
            for (category in categories) {
                val validId = when (recordType) {
                    RecordType.CRYPTO -> CryptoKind.entries.any { it.name == category.id }
                    RecordType.DATABASE -> SqlKind.entries.any { it.name == category.id }
                    RecordType.BEHAVIOR -> BehaviorKind.entries.any { it.name == category.id }
                    RecordType.PREFS -> PrefsKind.entries.any { it.name == category.id }
                    RecordType.WEBVIEW -> WebViewKind.entries.any { it.name == category.id }
                    RecordType.NETWORK -> NetworkKind.entries.any { it.name == category.id }
                    RecordType.DEVICE_ENV -> DeviceEnvKind.entries.any { it.name == category.id }
                    RecordType.UI_INTERACTION -> UiInteractionKind.entries.any { it.name == category.id }
                    RecordType.SYSTEM -> SystemKind.entries.any { it.name == category.id }
                    RecordType.PROCESS_CONTROL -> ProcessKind.entries.any { it.name == category.id }
                }
                assert(validId) {
                    "CategoryTaxonomy entry '${category.id}' for $recordType has no matching enum value"
                }
            }
        }
    }

    @Test
    fun `DeviceEnv taxonomy has 21 entries`() {
        assertEquals(21, CategoryTaxonomy.forType(RecordType.DEVICE_ENV).size)
    }
}
