package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V37 T3.1/T3.4 功能组完备性门禁:FEATURE 域类别 ↔ 功能组并集双向锁定。 */
class FeatureGroupsTest {

    @Test
    fun `every feature-domain kind belongs to exactly one group`() {
        val grouped = FeatureGroups.ALL.flatMap { FeatureGroups.kindIds(it) }
        val featureIds = CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE).map { it.id }
        assertEquals("功能组并集必须恰等于 FEATURE 域(未归组/混入监听域即红)", featureIds.sorted(), grouped.sorted())
        assertEquals("组间不得重复归组", grouped.size, grouped.distinct().size)
    }

    @Test
    fun `no listen-domain kind leaks into groups`() {
        val listenIds = CategoryTaxonomy.forDomain(CapabilityDomain.LISTEN).map { it.id }.toSet()
        val grouped = FeatureGroups.ALL.flatMap { FeatureGroups.kindIds(it) }.toSet()
        assertTrue(listenIds.intersect(grouped).isEmpty())
    }

    @Test
    fun `deviationCount counts masters kinds and list deviations`() {
        val override = AppListenOverride(
            cryptoListen = false,
            kinds = mapOf("CIPHER" to true, "QUERY" to false),
            ignorePathPrefixes = listOf("/a"),
            spoofProfileId = SpoofProfiles.XIAOMI_13,
            keywordFollowGlobal = false,
        )
        // 1 master + 2 kinds + 1 清单 + 1 画像 + 1 关键词跟随 = 6
        assertEquals(6, override.deviationCount)
        assertEquals(0, AppListenOverride.DEFAULT.deviationCount)
    }
}
