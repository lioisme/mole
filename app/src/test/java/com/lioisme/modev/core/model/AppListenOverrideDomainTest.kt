package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 偏差的双域分区(V45 T3.5 / DUAL-12):
 * 「恢复默认配置」在监听页与功能页各有一行,清除与计数都必须**域内**成立 ——
 * 旧形态两面共用全量删键,一页恢复会抹掉另一页的定制。
 * 本用例是 [AppListenOverride.clearDomain] 分区的唯一机器证明
 * (master 归属、kinds 归属、微调项与功能数据段的归属都在此钉死)。
 */
class AppListenOverrideDomainTest {

    private val full = AppListenOverride(
        cryptoListen = true,
        dbListen = false,
        behaviorListen = true,
        prefsListen = false,
        webviewListen = true,
        systemListen = false,
        networkListen = true,
        deviceEnvListen = false,
        uiListen = true,
        processControlListen = false,
        kinds = mapOf(
            AppListenOverride.kindKey(RecordType.CRYPTO, "CIPHER") to true,
            AppListenOverride.kindKey(RecordType.BEHAVIOR, "FILE_READ") to false,
            AppListenOverride.kindKey(RecordType.UI_INTERACTION, "DIALOG_KEYWORD") to true,
            AppListenOverride.kindKey(RecordType.PROCESS_CONTROL, "EXIT_INTERCEPT") to false,
        ),
        logStack = false,
        maxBytesPerField = 512,
        ignoreStackPrefixes = listOf("com.demo."),
        maxResultRows = 50,
        maxResultChars = 1_000,
        fileContentCapture = true,
        ignorePathPrefixes = listOf("/proc/"),
        dialogKeywords = listOf("Upd"),
        spoofProfileId = "pixel7pro",
        spoofOverrides = mapOf("build.BRAND" to "Google"),
        extraRootPackages = listOf("com.demo.root"),
        extraXposedPrefixes = listOf("de.robv"),
        extraFridaPorts = listOf(27042),
        extraMaskKeywords = listOf("magisk"),
        keywordIds = listOf(3L),
        keywordFollowGlobal = false,
    )

    @Test
    fun clearDomain_listen_keeps_every_feature_deviation() {
        val cleared = full.clearDomain(CapabilityDomain.LISTEN)
        // 监听侧全清
        listOf(
            cleared.cryptoListen, cleared.dbListen, cleared.behaviorListen, cleared.prefsListen,
            cleared.webviewListen, cleared.systemListen, cleared.logStack, cleared.maxBytesPerField,
            cleared.ignoreStackPrefixes, cleared.maxResultRows, cleared.maxResultChars,
            cleared.fileContentCapture, cleared.ignorePathPrefixes,
        ).forEach { assertNull("listen deviation must be cleared: $it", it) }
        assertEquals(emptyMap<String, Boolean>(), cleared.kinds.filterKeys { !CategoryTaxonomy.isFeatureKindKey(it) })
        // 只剩功能域两类(弹窗关键词拦截 + 退出拦截),监听域两类被清
        assertEquals(2, cleared.kinds.size)
        assertTrue(cleared.kinds.keys.all { CategoryTaxonomy.isFeatureKindKey(it) })
        assertEquals(true, cleared.networkListen)
        assertEquals(false, cleared.deviceEnvListen)
        assertEquals(true, cleared.uiListen)
        assertEquals(false, cleared.processControlListen)
        assertEquals("pixel7pro", cleared.spoofProfileId)
        assertEquals(listOf("com.demo.root"), cleared.extraRootPackages)
        assertEquals(listOf(27042), cleared.extraFridaPorts)
        assertEquals(listOf(3L), cleared.keywordIds)
        assertEquals(false, cleared.keywordFollowGlobal)
        assertEquals(listOf("Upd"), cleared.dialogKeywords)
    }

    @Test
    fun clearDomain_feature_keeps_every_listen_deviation() {
        val cleared = full.clearDomain(CapabilityDomain.FEATURE)
        assertEquals(true, cleared.cryptoListen)
        assertEquals(false, cleared.dbListen)
        assertEquals(false, cleared.logStack)
        assertEquals(512, cleared.maxBytesPerField)
        assertEquals(true, cleared.fileContentCapture)
        assertEquals(listOf("/proc/"), cleared.ignorePathPrefixes)
        assertNull(cleared.networkListen)
        assertNull(cleared.uiListen)
        assertNull(cleared.spoofProfileId)
        assertNull(cleared.extraRootPackages)
        assertNull(cleared.keywordIds)
        // 4 条 kinds 里恰好 2 条属功能域,清除后只剩监听域 2 条
        assertEquals(2, cleared.kinds.size)
        assertTrue(cleared.kinds.keys.all { !CategoryTaxonomy.isFeatureKindKey(it) })
    }

    /** 恒等式:两域计数之和 = 跨域总数,且两侧无重叠归属。 */
    @Test
    fun domain_counts_partition_the_total() {
        val listen = full.deviationCountFor(CapabilityDomain.LISTEN)
        val feature = full.deviationCountFor(CapabilityDomain.FEATURE)
        assertEquals(full.deviationCount, listen + feature)
        assertTrue("both sides must carry deviations: $listen/$feature", listen > 0 && feature > 0)
        assertEquals(0, AppListenOverride.DEFAULT.deviationCountFor(CapabilityDomain.LISTEN))
        assertEquals(0, AppListenOverride.DEFAULT.deviationCountFor(CapabilityDomain.FEATURE))
    }

    /** 清完两域 = 完全默认(删键条件),两域各自清完再合起来不丢语义。 */
    @Test
    fun clearing_both_domains_is_the_same_as_the_old_full_reset() {
        val both = full.clearDomain(CapabilityDomain.LISTEN).clearDomain(CapabilityDomain.FEATURE)
        assertTrue("residual deviation after both-domain reset: ${both.deviationCount}", both.isDefault)
    }

    /** master 归属与两页实际渲染的总开关集合同源(功能页 4 个 / 监听页 6 个)。 */
    @Test
    fun master_domain_partition_matches_the_two_screens() {
        val featureMasters = RecordType.entries.filter {
            AppListenOverride.masterDomainOf(it) == CapabilityDomain.FEATURE
        }.map { it.wire }.sorted()
        // wire 名口径:ui/process(非 ui_interaction/process_control —— 路由与 kindKey 同此一名)
        assertEquals(listOf("device_env", "network", "process", "ui"), featureMasters)
        assertEquals(6, RecordType.entries.size - featureMasters.size)
    }
}
