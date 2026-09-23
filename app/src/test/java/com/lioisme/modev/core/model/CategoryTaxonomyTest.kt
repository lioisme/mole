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
        // V33:所有 DeviceEnv **条件档** defaultEnabled = false(安全优先,与 HookSwitch.DEFAULT 对齐)
        val off = CategoryTaxonomy.forType(RecordType.DEVICE_ENV).filter { !it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(21, off.size)
        // V49 WP-D1/D2:画像强制档默认开 —— 它真正的闸是「画像里这一项是否被填」
        // (空 ⇒ 一族钩子都不装),所以默认开不会造成「没想伪装却被改写」。
        val on = CategoryTaxonomy.forType(RecordType.DEVICE_ENV).filter { it.defaultEnabled }.map { it.id }.toSet()
        assertEquals(
            setOf(
                DeviceEnvKind.PROFILE_BUILD_IDENTITY.name,
                DeviceEnvKind.PROFILE_BUILD_VERSION.name,
                DeviceEnvKind.PROFILE_DENSITY.name,
                DeviceEnvKind.PROFILE_CPU.name,
                DeviceEnvKind.PROFILE_BATTERY.name,
                DeviceEnvKind.PROFILE_ANDROID_ID.name,
                DeviceEnvKind.PROFILE_TELEPHONY_ID.name,
                DeviceEnvKind.PROFILE_SIM.name,
            ),
            on,
        )
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
    fun `DeviceEnv taxonomy has 29 entries`() {
        assertEquals(29, CategoryTaxonomy.forType(RecordType.DEVICE_ENV).size)
    }

    /**
     * V44 D2 门禁:枚举 ⊆ taxonomy(与上方 taxonomy ⊆ 枚举合成双向锁)。
     * 旧门禁只锁 taxonomy 登记的 id 都存在于枚举,但**新枚举值漏登记 taxonomy** 不被捕获——
     * 菜单由枚举遍历驱动时幽灵项经 validateKinds 交集空即「静默零结果陷阱」。
     * 菜单收敛到 [CategoryTaxonomy.forType] 驱动后,本测试转为唯一防线:
     * 任何 kind 枚举新增值必须同步登记 taxonomy,否则红。
     */
    @Test
    fun `every kind enum value is registered in taxonomy`() {
        for (kind in CryptoKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.CRYPTO, kind.name) != null) {
                "CryptoKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in SqlKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.DATABASE, kind.name) != null) {
                "SqlKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in BehaviorKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.BEHAVIOR, kind.name) != null) {
                "BehaviorKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in PrefsKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.PREFS, kind.name) != null) {
                "PrefsKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in WebViewKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.WEBVIEW, kind.name) != null) {
                "WebViewKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in NetworkKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.NETWORK, kind.name) != null) {
                "NetworkKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in DeviceEnvKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name) != null) {
                "DeviceEnvKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in UiInteractionKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.UI_INTERACTION, kind.name) != null) {
                "UiInteractionKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in SystemKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.SYSTEM, kind.name) != null) {
                "SystemKind.$kind not registered in CategoryTaxonomy"
            }
        }
        for (kind in ProcessKind.entries) {
            assert(CategoryTaxonomy.byId(RecordType.PROCESS_CONTROL, kind.name) != null) {
                "ProcessKind.$kind not registered in CategoryTaxonomy"
            }
        }
    }

    /** V44 D2:枚举总数 == taxonomy 条目数(双向锁计数侧,防多类型漏登记抵消)。 */
    @Test
    fun `taxonomy size equals total kind enum values`() {
        val enumTotal =
            CryptoKind.entries.size + SqlKind.entries.size + BehaviorKind.entries.size +
                PrefsKind.entries.size + WebViewKind.entries.size + NetworkKind.entries.size +
                DeviceEnvKind.entries.size + UiInteractionKind.entries.size +
                SystemKind.entries.size + ProcessKind.entries.size
        assertEquals("枚举与 taxonomy 条目数漂移", enumTotal, CategoryTaxonomy.all().size)
    }

    /**
     * V44 跨类型 kind id 碰撞集回归:唯一合法碰撞 = (db/prefs OPEN) ∪ (crypto/system SIGNATURE)。
     * 新增碰撞即红(限定键 [AppListenOverride.kindKey] 已彻底消歧,但裸 id 遗留解析
     * first-wins 序依赖碰撞集不变)。
     */
    @Test
    fun `cross-type colliding kind ids are exactly the documented set`() {
        val colliding = CategoryTaxonomy.all().groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertEquals(setOf(SqlKind.OPEN.name, CryptoKind.SIGNATURE.name), colliding)
        // 序首归一:Databases 前于 Prefs;Crypto 前于 System
        assertEquals(RecordType.DATABASE, CategoryTaxonomy.typesWithId(SqlKind.OPEN.name).first())
        assertEquals(RecordType.CRYPTO, CategoryTaxonomy.typesWithId(CryptoKind.SIGNATURE.name).first())
    }

    // ── V37 T1.1 双域拆分(D-0 基线)快照锁定 ───────────────────────────

    @Test
    fun `feature domain snapshot matches D-0 baseline`() {
        val featureCats = CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE)
        assertEquals(45, featureCats.size)
        val feature = featureCats.map { it.id }.toSet()
        val expected = setOf(
            // 进程控制 3
            ProcessKind.EXIT_INTERCEPT.name, ProcessKind.CRASH_PREVENT.name, ProcessKind.SCREEN_RECORD.name,
            // UI 拦截 1
            UiInteractionKind.DIALOG_KEYWORD.name,
            // 网络干预 14(SSL×6 + 代理×4 + VPN×4)
            NetworkKind.SSL_CONTEXT.name, NetworkKind.SSL_TRUST_MANAGER.name,
            NetworkKind.SSL_OKHTTP_PINNER.name, NetworkKind.SSL_OKHTTP_VERIFIER.name,
            NetworkKind.SSL_WEBVIEW.name, NetworkKind.SSL_NETWORK_CONFIG.name,
            NetworkKind.PROXY_SYSTEM_PROP.name, NetworkKind.PROXY_CONNECTIVITY.name,
            NetworkKind.PROXY_SETTINGS.name, NetworkKind.PROXY_OKHTTP.name,
            NetworkKind.VPN_CAPABILITIES.name, NetworkKind.VPN_INTERFACE.name,
            NetworkKind.VPN_NETWORK_INFO.name, NetworkKind.VPN_LINK_PROPERTIES.name,
            // 设备环境干预 19
            DeviceEnvKind.ROOT_FILE.name, DeviceEnvKind.ROOT_EXEC.name,
            DeviceEnvKind.ROOT_PACKAGE_MANAGER.name, DeviceEnvKind.ROOT_PROPERTY.name,
            DeviceEnvKind.XPOSED_CLASS.name, DeviceEnvKind.XPOSED_STACK.name, DeviceEnvKind.XPOSED_MAPS.name,
            DeviceEnvKind.EMULATOR_BUILD.name, DeviceEnvKind.EMULATOR_FILE.name,
            DeviceEnvKind.EMULATOR_SENSOR.name, DeviceEnvKind.EMULATOR_NETWORK.name,
            DeviceEnvKind.EMULATOR_TELEPHONY.name, DeviceEnvKind.EMULATOR_CPU.name,
            DeviceEnvKind.EMULATOR_BATTERY.name, DeviceEnvKind.SYSTEM_PROPERTIES.name,
            DeviceEnvKind.PROC_STATUS.name, DeviceEnvKind.PROC_MOUNT_INFO.name,
            DeviceEnvKind.PROC_CGROUP.name, DeviceEnvKind.PROC_NET.name,
            // V49 WP-D1/D2 画像强制档(Build 身份与版本 / 密度 / 核数 / 电池)
            DeviceEnvKind.PROFILE_BUILD_IDENTITY.name, DeviceEnvKind.PROFILE_BUILD_VERSION.name,
            DeviceEnvKind.PROFILE_DENSITY.name, DeviceEnvKind.PROFILE_CPU.name, DeviceEnvKind.PROFILE_BATTERY.name,
            DeviceEnvKind.PROFILE_ANDROID_ID.name, DeviceEnvKind.PROFILE_TELEPHONY_ID.name, DeviceEnvKind.PROFILE_SIM.name,
        )
        assertEquals(expected, feature)
    }

    @Test
    fun `listen domain snapshot covers remaining categories`() {
        // 注意:kind id 跨类型存在同名碰撞(OPEN: SQL/PREFS;SIGNATURE: CRYPTO/SYSTEM),
        // 域完备性断言必须用 (type,id) 对而非裸 id 集合。
        val listen = CategoryTaxonomy.forDomain(CapabilityDomain.LISTEN)
        assertEquals(44, listen.size)
        val listenIds = listen.map { it.id }.toSet()
        // 观测子类不得混入功能域
        assertTrue(listenIds.contains(NetworkKind.REQUEST.name))
        assertTrue(listenIds.contains(DeviceEnvKind.DETECT_ROOT.name))
        assertTrue(listenIds.contains(DeviceEnvKind.DETECT_EMULATOR.name))
        assertTrue(listenIds.contains(UiInteractionKind.DIALOG_SHOW.name))
        // 两域互斥且完备((type,id) 对级)
        val pairs = { cats: List<RecordCategory> -> cats.map { it.type to it.id }.toSet() }
        val feature = CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE)
        assertEquals(pairs(CategoryTaxonomy.all()), pairs(listen) + pairs(feature))
        assertTrue(pairs(listen).intersect(pairs(feature)).isEmpty())
    }

    @Test
    fun `feature domain defaults off except data-gated dialog keyword`() {
        val enabledFeature = CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE).filter { it.defaultEnabled }
        assertEquals(
            "功能域默认全关(数据门控类别除外:弹窗词表与画像强制档都是「填了才生效」)",
            setOf(
                UiInteractionKind.DIALOG_KEYWORD.name,
                DeviceEnvKind.PROFILE_BUILD_IDENTITY.name,
                DeviceEnvKind.PROFILE_BUILD_VERSION.name,
                DeviceEnvKind.PROFILE_DENSITY.name,
                DeviceEnvKind.PROFILE_CPU.name,
                DeviceEnvKind.PROFILE_BATTERY.name,
                DeviceEnvKind.PROFILE_ANDROID_ID.name,
                DeviceEnvKind.PROFILE_TELEPHONY_ID.name,
                DeviceEnvKind.PROFILE_SIM.name,
            ),
            enabledFeature.map { it.id }.toSet(),
        )
    }

    @Test
    fun `isFeature resolves domain by type and id`() {
        assertTrue(CategoryTaxonomy.isFeature(RecordType.PROCESS_CONTROL, ProcessKind.EXIT_INTERCEPT.name))
        assertFalse(CategoryTaxonomy.isFeature(RecordType.NETWORK, NetworkKind.REQUEST.name))
        assertFalse(CategoryTaxonomy.isFeature(RecordType.NETWORK, "UNKNOWN_KIND"))
    }
}
