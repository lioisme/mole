package com.lioisme.modev.core.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookSwitchTest {

    @Test
    fun default_any_enabled() {
        assertTrue(HookSwitch.DEFAULT.anyEnabled)
        assertTrue(HookSwitch.DEFAULT.anyCryptoEnabled)
        assertTrue(HookSwitch.DEFAULT.anyDbEnabled)
    }

    @Test
    fun all_off_any_disabled() {
        val s = HookSwitch(
            messageDigest = false, cipher = false, mac = false, signature = false,
            keyParam = false, keyGeneration = false, keyFactory = false,
            keyStore = false, keyAgreement = false, random = false, base64 = false,
            webView = false,
            dbOpen = false, dbQuery = false, dbDml = false, dbExec = false,
            dbTransaction = false,
            fileRead = false, fileWrite = false, fileDelete = false,
            fileRename = false, fileList = false, assetRead = false, shellExec = false,
            prefsOpen = false, prefsGet = false, prefsPut = false,
            prefsRemove = false, prefsClear = false,
        )
        assertFalse(s.anyEnabled)
        assertFalse(s.anyCryptoEnabled)
        assertFalse(s.anyDbEnabled)
        assertFalse(s.anyBehaviorEnabled)
        assertFalse(s.anyPrefsEnabled)
    }

    @Test
    fun db_only_enables_only_db_projection() {
        val s = HookSwitch(
            messageDigest = false, cipher = false, mac = false, signature = false,
            keyParam = false, keyGeneration = false, keyFactory = false,
            keyStore = false, keyAgreement = false, random = false, base64 = false,
            webView = false,
            dbOpen = false, dbQuery = true, dbDml = false, dbExec = false,
            dbTransaction = false,
        )
        assertTrue(s.anyDbEnabled)
        assertFalse(s.anyCryptoEnabled)
        assertTrue(s.anyEnabled)
    }

    @Test
    fun crypto_only_enables_only_crypto_projection() {
        val s = HookSwitch(
            cipher = true,
            dbOpen = false, dbQuery = false, dbDml = false, dbExec = false,
            dbTransaction = false,
        )
        assertTrue(s.anyCryptoEnabled)
        assertFalse(s.anyDbEnabled)
        assertTrue(s.anyEnabled)
    }

    @Test
    fun noise_categories_off_by_default() {
        // 噪点算法类别默认关(与 CategoryTaxonomy.defaultEnabledIds(CRYPTO) 一致)
        for (kind in listOf(
            CryptoKind.KEY_PARAM, CryptoKind.KEY_GENERATION, CryptoKind.KEY_FACTORY,
            CryptoKind.KEY_STORE, CryptoKind.KEY_AGREEMENT, CryptoKind.RANDOM,
            CryptoKind.BASE64, CryptoKind.WEB_CRYPTO,
        )) {
            assertFalse("noise crypto category $kind should default off", HookSwitch.DEFAULT.enabled(kind))
        }
        // 高信号算法类别默认开
        for (kind in listOf(CryptoKind.CIPHER, CryptoKind.MESSAGE_DIGEST, CryptoKind.MAC, CryptoKind.SIGNATURE)) {
            assertTrue("signal crypto category $kind should default on", HookSwitch.DEFAULT.enabled(kind))
        }
        // 噪点数据库类别默认关
        assertFalse(HookSwitch.DEFAULT.dbEnabled(SqlKind.OPEN))
        assertFalse(HookSwitch.DEFAULT.dbEnabled(SqlKind.TRANSACTION))
        // 高信号数据库类别默认开
        for (kind in listOf(SqlKind.QUERY, SqlKind.DML, SqlKind.EXEC)) {
            assertTrue("signal db category $kind should default on", HookSwitch.DEFAULT.dbEnabled(kind))
        }
    }

    @Test
    fun defaults_align_with_category_taxonomy() {
        val crypto = CategoryTaxonomy.defaultEnabledIds(RecordType.CRYPTO)
        val db = CategoryTaxonomy.defaultEnabledIds(RecordType.DATABASE)
        val behavior = CategoryTaxonomy.defaultEnabledIds(RecordType.BEHAVIOR)
        val prefs = CategoryTaxonomy.defaultEnabledIds(RecordType.PREFS)
        for (kind in CryptoKind.entries) {
            assertEquals(
                "crypto $kind default mismatch with taxonomy",
                crypto.contains(kind.name),
                HookSwitch.DEFAULT.enabled(kind),
            )
        }
        for (kind in SqlKind.entries) {
            assertEquals(
                "db $kind default mismatch with taxonomy",
                db.contains(kind.name),
                HookSwitch.DEFAULT.dbEnabled(kind),
            )
        }
        for (kind in BehaviorKind.entries) {
            assertEquals(
                "behavior $kind default mismatch with taxonomy",
                behavior.contains(kind.name),
                HookSwitch.DEFAULT.behaviorEnabled(kind),
            )
        }
        for (kind in PrefsKind.entries) {
            assertEquals(
                "prefs $kind default mismatch with taxonomy",
                prefs.contains(kind.name),
                HookSwitch.DEFAULT.prefsEnabled(kind),
            )
        }
    }

    @Test
    fun base64_off_by_default() {
        assertFalse(HookSwitch.DEFAULT.enabled(CryptoKind.BASE64))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.CIPHER))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.MESSAGE_DIGEST))
    }

    @Test
    fun enabled_maps_each_kind() {
        val s = HookSwitch(
            messageDigest = true, cipher = false, mac = true, signature = false,
            keyParam = true, keyGeneration = false, keyFactory = true, base64 = true,
        )
        assertTrue(s.enabled(CryptoKind.MESSAGE_DIGEST))
        assertFalse(s.enabled(CryptoKind.CIPHER))
        assertTrue(s.enabled(CryptoKind.MAC))
        assertFalse(s.enabled(CryptoKind.SIGNATURE))
        assertTrue(s.enabled(CryptoKind.KEY_PARAM))
        assertFalse(s.enabled(CryptoKind.KEY_GENERATION))
        assertTrue(s.enabled(CryptoKind.KEY_FACTORY))
        assertTrue(s.enabled(CryptoKind.BASE64))
    }

    @Test
    fun db_enabled_maps_each_kind() {
        val s = HookSwitch(
            dbOpen = true, dbQuery = false, dbDml = true, dbExec = false,
            dbTransaction = true,
        )
        assertTrue(s.dbEnabled(SqlKind.OPEN))
        assertFalse(s.dbEnabled(SqlKind.QUERY))
        assertTrue(s.dbEnabled(SqlKind.DML))
        assertFalse(s.dbEnabled(SqlKind.EXEC))
        assertTrue(s.dbEnabled(SqlKind.TRANSACTION))
    }

    @Test
    fun webview_off_by_default() {
        assertFalse(HookSwitch.DEFAULT.enabled(CryptoKind.WEB_CRYPTO))
        assertFalse(HookSwitch.DEFAULT.webView)
    }

    @Test
    fun webview_enabled_maps_kind_and_any() {
        val s = HookSwitch(webView = true)
        assertTrue(s.enabled(CryptoKind.WEB_CRYPTO))
        assertTrue(s.anyEnabled)
        val allOff = HookSwitch(
            messageDigest = false, cipher = false, mac = false, signature = false,
            keyParam = false, keyGeneration = false, keyFactory = false,
            keyStore = false, keyAgreement = false, random = false, base64 = false,
            webView = false,
            dbOpen = false, dbQuery = false, dbDml = false, dbExec = false,
            dbTransaction = false,
            fileRead = false, fileWrite = false, fileDelete = false,
            fileRename = false, fileList = false, assetRead = false, shellExec = false,
            prefsOpen = false, prefsGet = false, prefsPut = false,
            prefsRemove = false, prefsClear = false,
        )
        assertFalse(allOff.anyEnabled)
    }

    @Test
    fun result_caps_have_sane_defaults() {
        assertEquals(RecordLimits.DEFAULT_MAX_RESULT_ROWS, HookSwitch.DEFAULT.maxResultRows)
        assertEquals(RecordLimits.DEFAULT_MAX_RESULT_CHARS, HookSwitch.DEFAULT.maxResultChars)
    }

    @Test
    fun copy_preserves_unknown() {
        val s = HookSwitch(maxStackDepth = 5).copy(cipher = false)
        assertEquals(5, s.maxStackDepth)
        assertTrue(s.dbQuery)
    }

    @Test
    fun master_switches_gate_projections() {
        // 算法总开关关:全部算法类别停用,分开关原态保留,数据库不受影响。
        val cryptoOff = HookSwitch(cryptoListen = false)
        assertFalse(cryptoOff.anyCryptoEnabled)
        assertFalse(cryptoOff.enabled(CryptoKind.CIPHER))
        assertFalse(cryptoOff.enabled(CryptoKind.MESSAGE_DIGEST))
        assertTrue(cryptoOff.copy(dbOpen = true).dbEnabled(SqlKind.OPEN))
        assertTrue(cryptoOff.anyDbEnabled)
        // 恢复总开关:分开关原态仍生效。
        assertTrue(cryptoOff.copy(cryptoListen = true).enabled(CryptoKind.CIPHER))

        // 数据库总开关关:全部数据库类别停用,算法不受影响。
        val dbOff = HookSwitch(dbListen = false)
        assertFalse(dbOff.anyDbEnabled)
        assertFalse(dbOff.dbEnabled(SqlKind.OPEN))
        assertFalse(dbOff.dbEnabled(SqlKind.QUERY))
        assertTrue(dbOff.enabled(CryptoKind.CIPHER))
        assertTrue(dbOff.anyCryptoEnabled)
        // 行为总开关关:全部行为类别停用,算法/数据库不受影响(V27)。
        val behaviorOff = HookSwitch(behaviorListen = false)
        assertFalse(behaviorOff.anyBehaviorEnabled)
        assertFalse(behaviorOff.behaviorEnabled(BehaviorKind.SHELL_EXEC))
        assertTrue(behaviorOff.enabled(CryptoKind.CIPHER))
        assertTrue(behaviorOff.anyCryptoEnabled)
        assertTrue(behaviorOff.anyDbEnabled)
        // 偏好总开关关:全部偏好类别停用,其余域不受影响(V28)。
        val prefsOff = HookSwitch(prefsListen = false)
        assertFalse(prefsOff.anyPrefsEnabled)
        assertFalse(prefsOff.prefsEnabled(PrefsKind.OPEN))
        assertTrue(prefsOff.enabled(CryptoKind.CIPHER))
        assertTrue(prefsOff.anyCryptoEnabled)
        assertTrue(prefsOff.anyDbEnabled)
        assertTrue(prefsOff.anyBehaviorEnabled)
        // 四总开关关:整体停用。
        assertFalse(cryptoOff.copy(dbListen = false, behaviorListen = false, prefsListen = false).anyEnabled)
    }

    @Test
    fun master_switches_default_on() {
        assertTrue(HookSwitch.DEFAULT.cryptoListen)
        assertTrue(HookSwitch.DEFAULT.dbListen)
        assertTrue(HookSwitch.DEFAULT.behaviorListen)
        assertTrue(HookSwitch.DEFAULT.prefsListen)
    }

    @Test
    fun prefs_enabled_maps_each_kind() {
        val s = HookSwitch(
            prefsOpen = true, prefsGet = true, prefsPut = false,
            prefsRemove = true, prefsClear = false,
        )
        assertTrue(s.prefsEnabled(PrefsKind.OPEN))
        assertTrue(s.prefsEnabled(PrefsKind.GET))
        assertFalse(s.prefsEnabled(PrefsKind.PUT))
        assertTrue(s.prefsEnabled(PrefsKind.REMOVE))
        assertFalse(s.prefsEnabled(PrefsKind.CLEAR))
    }

    @Test
    fun prefs_noise_categories_off_by_default() {
        // 高噪声偏好类别默认关:GET(App 每次配置读取均触发)
        assertFalse("noise prefs category GET should default off", HookSwitch.DEFAULT.prefsEnabled(PrefsKind.GET))
        // 高信号偏好类别默认开:OPEN/PUT/REMOVE/CLEAR
        for (kind in listOf(PrefsKind.OPEN, PrefsKind.PUT, PrefsKind.REMOVE, PrefsKind.CLEAR)) {
            assertTrue("signal prefs category $kind should default on", HookSwitch.DEFAULT.prefsEnabled(kind))
        }
    }

    @Test
    fun behavior_enabled_maps_each_kind() {
        val s = HookSwitch(
            fileRead = true, fileWrite = false, fileDelete = true,
            fileRename = false, fileList = true, assetRead = false, shellExec = true,
        )
        assertTrue(s.behaviorEnabled(BehaviorKind.FILE_READ))
        assertFalse(s.behaviorEnabled(BehaviorKind.FILE_WRITE))
        assertTrue(s.behaviorEnabled(BehaviorKind.FILE_DELETE))
        assertFalse(s.behaviorEnabled(BehaviorKind.FILE_RENAME))
        assertTrue(s.behaviorEnabled(BehaviorKind.FILE_LIST))
        assertFalse(s.behaviorEnabled(BehaviorKind.ASSET_READ))
        assertTrue(s.behaviorEnabled(BehaviorKind.SHELL_EXEC))
    }

    @Test
    fun behavior_noise_categories_off_by_default() {
        // 高噪声行为类别默认关:读/列目录/Assets(App 启动即高频触发)
        for (kind in listOf(BehaviorKind.FILE_READ, BehaviorKind.FILE_LIST, BehaviorKind.ASSET_READ)) {
            assertFalse("noise behavior category $kind should default off", HookSwitch.DEFAULT.behaviorEnabled(kind))
        }
        // 高信号行为类别默认开:写/删/重命名/Shell
        for (kind in listOf(
            BehaviorKind.FILE_WRITE, BehaviorKind.FILE_DELETE,
            BehaviorKind.FILE_RENAME, BehaviorKind.SHELL_EXEC,
        )) {
            assertTrue("signal behavior category $kind should default on", HookSwitch.DEFAULT.behaviorEnabled(kind))
        }
    }

    @Test
    fun from_json_missing_master_fields_defaults_true() {
        val gson = Gson()
        // 旧配置(无总开关字段):补齐默认开启,其余字段按 JSON 解析。
        val legacy = HookSwitch.fromJson(gson, """{"cipher":false,"dbOpen":false}""")!!
        assertTrue(legacy.cryptoListen)
        assertTrue(legacy.dbListen)
        assertFalse(legacy.cipher)
        assertFalse(legacy.dbOpen)
        assertTrue(legacy.messageDigest)
        // 显式关闭字段被保留;分开关原态不受总开关影响。
        val explicit = HookSwitch.fromJson(gson, """{"cryptoListen":false,"dbListen":false}""")!!
        assertFalse(explicit.cryptoListen)
        assertFalse(explicit.dbListen)
        assertTrue(explicit.cipher)
        assertTrue(explicit.messageDigest)
        assertFalse(explicit.enabled(CryptoKind.CIPHER))
        // 非法 JSON → null。
        assertNull(HookSwitch.fromJson(gson, "not-json"))
    }

    @Test
    fun json_roundtrip_preserves_master_switches() {
        val gson = Gson()
        val json = gson.toJson(HookSwitch.DEFAULT.copy(cryptoListen = false))
        val parsed = HookSwitch.fromJson(gson, json)!!
        assertFalse(parsed.cryptoListen)
        assertTrue(parsed.dbListen)
        assertFalse(parsed.anyCryptoEnabled)
    }
}