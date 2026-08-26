package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 二态偏差解析契约(V24):
 * - null/零偏差零拷贝回传默认配置;
 * - 总开关与类别显式位强制生效,未定制位取默认值;
 * - 未知 kind 忽略(存储漂移兜底);master OFF 抑制一切算法捕获;
 * - [EffectiveSwitch.readKind] 与写入映射互逆(设置页行值回显契约)。
 */
class EffectiveSwitchTest {

    private val d = HookSwitch.DEFAULT

    @Test
    fun `null override returns same instance`() {
        assertSame(d, EffectiveSwitch.resolve(d, null))
    }

    @Test
    fun `zero-deviation override returns same instance`() {
        assertSame(d, EffectiveSwitch.resolve(d, AppListenOverride.DEFAULT))
    }

    @Test
    fun `master deviation forces value over default`() {
        val off = EffectiveSwitch.resolve(
            d.copy(cryptoListen = true),
            AppListenOverride(cryptoListen = false),
        )
        assertFalse(off.cryptoListen)

        val on = EffectiveSwitch.resolve(
            d.copy(cryptoListen = false),
            AppListenOverride(cryptoListen = true),
        )
        assertTrue(on.cryptoListen)
        assertTrue(on.anyCryptoEnabled)
    }

    @Test
    fun `db master deviation forces value over default`() {
        val effective = EffectiveSwitch.resolve(
            d.copy(dbListen = true),
            AppListenOverride(dbListen = false),
        )
        assertFalse(effective.dbListen)
        assertFalse(effective.anyDbEnabled)
    }

    @Test
    fun `kind deviation overrides default bit`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(kinds = mapOf(CryptoKind.CIPHER.name to false)),
        )
        assertFalse(effective.cipher)
        // 未定制位取默认
        assertTrue(effective.messageDigest)
        // master 未动 → 投影仍可达
        assertTrue(effective.anyCryptoEnabled)
    }

    @Test
    fun `sql kind deviation overrides default bit`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(kinds = mapOf(SqlKind.DML.name to true, SqlKind.OPEN.name to true)),
        )
        assertTrue(effective.dbDml)
        assertTrue(effective.dbOpen)
        assertEqualsDefaultDbRest(effective)
    }

    private fun assertEqualsDefaultDbRest(effective: HookSwitch) {
        assertFalse(effective.dbTransaction)
        assertTrue(effective.dbQuery)
        assertTrue(effective.dbExec)
    }

    @Test
    fun `unknown kind name ignored without garbage propagation`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(kinds = mapOf("NOT_A_KIND" to false, CryptoKind.MAC.name to false)),
        )
        assertFalse(effective.mac)
        // 其余位不受垃圾键影响
        assertTrue(effective.cipher)
        assertTrue(effective.messageDigest)
    }

    @Test
    fun `multiple deviations apply together over modified default`() {
        val modifiedDefault = d.copy(cipher = false, mac = false, dbDml = false)
        val effective = EffectiveSwitch.resolve(
            modifiedDefault,
            AppListenOverride(
                kinds = mapOf(
                    CryptoKind.CIPHER.name to true,
                    SqlKind.DML.name to true,
                ),
            ),
        )
        assertTrue(effective.cipher)
        assertTrue(effective.dbDml)
        // 未定制偏差位保持默认配置当前值(false)
        assertFalse(effective.mac)
    }

    @Test
    fun `master off suppresses forced-on kind projection`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(cryptoListen = false, kinds = mapOf(CryptoKind.CIPHER.name to true)),
        )
        assertFalse(effective.cryptoListen)
        assertFalse(effective.anyCryptoEnabled)
        assertFalse(effective.enabled(CryptoKind.CIPHER))
    }

    @Test
    fun `derived flags follow resolved result`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(dbListen = true, kinds = mapOf(SqlKind.OPEN.name to true)),
        )
        assertTrue(effective.dbEnabled(SqlKind.OPEN))
        assertTrue(effective.anyDbEnabled)
    }

    /** 全类别不变量:全部类别按各自默认值取反定制后,解析回读 == 显式偏差(读写映射互逆)。 */
    @Test
    fun `readKind invariant holds for every taxonomy kind`() {
        val flipped = CategoryTaxonomy.all().associate {
            it.id to !(EffectiveSwitch.readKind(d, it.id) ?: true)
        }
        val effective = EffectiveSwitch.resolve(d, AppListenOverride(kinds = flipped))
        for (category in CategoryTaxonomy.all()) {
            org.junit.Assert.assertEquals(
                "kind=" + category.id,
                flipped[category.id],
                EffectiveSwitch.readKind(effective, category.id),
            )
        }
    }

    @Test
    fun `readKind returns field values and null for unknown id`() {
        assertTrue(EffectiveSwitch.readKind(d, CryptoKind.CIPHER.name) == d.cipher)
        assertTrue(EffectiveSwitch.readKind(d, SqlKind.OPEN.name) == d.dbOpen)
        assertNull(EffectiveSwitch.readKind(d, "NOT_A_KIND"))
    }

    /** V25 采集微调项:非 null 即生效,null 位取默认。 */
    @Test
    fun `capture option deviations overlay onto default`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(
                logStack = false,
                maxBytesPerField = 2048,
                maxResultRows = 50,
            ),
        )
        assertFalse(effective.logStack)
        assertEquals(2048, effective.maxBytesPerField)
        assertEquals(50, effective.maxResultRows)
        // 未定制微调位取默认
        assertEquals(d.maxResultChars, effective.maxResultChars)
        assertEquals(d.ignoreStackPrefixes, effective.ignoreStackPrefixes)
    }

    @Test
    fun `ignore stack prefixes replace as whole list`() {
        val prefixes = listOf("com.example.crypto", "kotlin.")
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(ignoreStackPrefixes = prefixes),
        )
        assertEquals(prefixes, effective.ignoreStackPrefixes)
    }

    @Test
    fun `option-only deviation is not default`() {
        assertFalse(AppListenOverride(logStack = false).isDefault)
        assertTrue(AppListenOverride(ignoreStackPrefixes = emptyList()).isDefault)
    }

    /** V27 行为域:master 偏差 + 类别偏差 + 微调项(fileContentCapture/ignorePathPrefixes)。 */
    @Test
    fun `behavior master and kind deviations overlay onto default`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(
                behaviorListen = false,
                kinds = mapOf(BehaviorKind.FILE_READ.name to true),
                fileContentCapture = true,
                ignorePathPrefixes = listOf("/proc/", "/sys/"),
            ),
        )
        assertFalse(effective.behaviorListen)
        // master 关 → 投影与类别判定全停,分开关原态保留
        assertFalse(effective.anyBehaviorEnabled)
        assertFalse(effective.behaviorEnabled(BehaviorKind.FILE_READ))
        assertTrue(effective.fileRead)
        assertFalse(effective.behaviorEnabled(BehaviorKind.FILE_WRITE))
        assertTrue(effective.fileContentCapture)
        assertEquals(listOf("/proc/", "/sys/"), effective.ignorePathPrefixes)
        // 算法/数据库不受行为偏差影响
        assertTrue(effective.anyCryptoEnabled)
        assertTrue(effective.anyDbEnabled)

        val on = EffectiveSwitch.resolve(
            d.copy(behaviorListen = false, fileWrite = false),
            AppListenOverride(behaviorListen = true),
        )
        assertTrue(on.anyBehaviorEnabled)
    }

    /** V27 行为微调项:null 位取默认;空路径表不构成偏差。 */
    @Test
    fun `behavior option nulls take default and empty path list is zero deviation`() {
        val effective = EffectiveSwitch.resolve(
            d,
            AppListenOverride(ignorePathPrefixes = listOf("/data/local/tmp/")),
        )
        assertEquals(d.fileContentCapture, effective.fileContentCapture)
        assertEquals(listOf("/data/local/tmp/"), effective.ignorePathPrefixes)
        assertFalse(AppListenOverride(fileContentCapture = true).isDefault)
        assertFalse(AppListenOverride(behaviorListen = true).isDefault)
    }
}
