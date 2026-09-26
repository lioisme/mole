package com.lioisme.modev.core

import com.lioisme.modev.R
import com.lioisme.modev.core.model.CapabilityMap
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.FeatureGroups
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.ui.navigation.ModevRoutes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定向测试(V48 R-2):能力表 = 把后端 81 个类别投影到「用户在哪一屏改它、在哪一屏看它、
 * 由哪个钩子发射」。前后端分类必须逐行对得上,这张表就是它们唯一的对照物。
 *
 * 三条不变式各自拦一种真实漂移:
 * 1. **表不许变成第二份 kind 清单**(全部行由 [CategoryTaxonomy] 派生);
 * 2. **表不许指向空气**(分区键 / 路由常量 / 探针类必须真实存在 —— 分类写在纸上而 UI 没渲染 = 假分类);
 * 3. **跨域闸必须可见**(LISTEN 类别若由功能页 master 管辖,必须在表里点名,供 UI 提示消费)。
 */
class CapabilityMapTest {

    @Test
    fun mapIsBijectionWithTaxonomyAndNeverRestatesIt() {
        val fromTaxonomy = CategoryTaxonomy.all().map { it.type to it.id }.toSet()
        val fromMap = CapabilityMap.entries.map { it.type to it.kind }

        assertEquals("能力表与分类法必须同集", fromTaxonomy, fromMap.toSet())
        assertEquals("每 kind 恰一行(出现重复即表在另抄清单)", fromTaxonomy.size, fromMap.size)

        CapabilityMap.entries.forEach { row ->
            val category = CategoryTaxonomy.byId(row.type, row.kind)
            assertNotNull("孤儿类别 ${row.type}/${row.kind}", category)
            assertEquals("域归属漂移 ${row.kind}", category?.domain, row.domain)
        }
    }

    /** 分类快照:域数量与跨域类型集合是本次重整的根据,改动必须是有意的。 */
    @Test
    fun frozenClassificationSnapshot() {
        // V50 孤儿整改:89/44 是含死行 CryptoKind.WEB_CRYPTO 的口径;该行注入侧零发射
        // (勾选后开关亮、永不出记录),已随 HookSwitch.webView 位一并退役 ⇒ 88/43。
        assertEquals(88, CapabilityMap.entries.size)
        assertEquals(43, CapabilityMap.entries.count { it.domain == CapabilityDomain.LISTEN })
        assertEquals(45, CapabilityMap.entries.count { it.domain == CapabilityDomain.FEATURE })
        assertEquals(
            "跨域类型 = 同一记录类型里两域混居,这正是 V48 要治理的形态",
            setOf(RecordType.NETWORK, RecordType.DEVICE_ENV, RecordType.UI_INTERACTION),
            CapabilityMap.crossDomainTypes,
        )
    }

    /** 每行的分区键必须是真资源名(分类落到屏幕上,不是落到文档里)。 */
    @Test
    fun everySectionKeyIsARealStringResource() {
        CapabilityMap.entries.map { it.sectionKey }.distinct().forEach { key ->
            val field = runCatching { R.string::class.java.getDeclaredField(key) }.getOrNull()
            assertNotNull("分区键 ${key} 在 R.string 里不存在", field)
        }
    }

    /** 每行的路由常量必须真存在(点击「查看记录」不能通向空气)。 */
    @Test
    fun everyRouteKeyIsARealNavigationRoute() {
        val declared = ModevRoutes::class.java.fields.map { it.name }.toSet()
        CapabilityMap.entries.map { it.routeKey }.distinct().forEach { key ->
            assertTrue("路由常量 $key 未声明于 ModevRoutes", key in declared)
        }
    }

    /** 每行的探针类必须有源文件(分类不许宣称一个不存在的实现方)。 */
    @Test
    fun everyProbeHasASourceFile() {
        val probeDir = File("src/main/java/com/lioisme/modev/hook/probes")
        val present = probeDir.listFiles()?.map { it.nameWithoutExtension }?.toSet().orEmpty()
        assertTrue("探针目录读不到(测试工作目录异常)", present.isNotEmpty())
        CapabilityMap.entries.map { it.probe }.distinct().forEach { probe ->
            assertTrue("探针 $probe 无源文件", probe in present)
        }
    }

    /**
     * 功能域行的分区必须等于 `FeatureGroups` 给出的组 —— 即能力表**不能**自成一套分类,
     * 它必须与功能页真正渲染的分组同源(两处各写一份就是下一次「勾了不生效」的温床)。
     */
    @Test
    fun featureRowsFollowFeatureGroupsExactly() {
        val groupOfKind = FeatureGroups.ALL.flatMap { group ->
            FeatureGroups.kindIds(group).map { id -> id to "feature_group_${group.name.lowercase()}" }
        }.toMap()

        CapabilityMap.entries.filter { it.domain == CapabilityDomain.FEATURE }.forEach { row ->
            assertEquals(
                "${row.kind} 的分区与功能页分组不一致",
                groupOfKind[row.kind],
                row.sectionKey,
            )
        }
    }

    /**
     * 跨域闸点名(V48-DFC-03):监听域类别若其 master 由功能页渲染,必须在表里可枚举 ——
     * 监听页据此给用户一条「它的总开关在功能设置页」的提示,而不是让勾选静默无效。
     */
    @Test
    fun crossDomainListenRowsAreNamedWithTheirForeignMaster() {
        val listenUnderFeatureMaster = CapabilityMap.crossDomainListen
        assertEquals(
            "实测 7 项:网络请求 + Root/模拟器检测监控 + 弹窗展示/控件文本/Activity 起止",
            7,
            listenUnderFeatureMaster.size,
        )
        listenUnderFeatureMaster.forEach { row ->
            assertEquals("必须是监听域才谈得上跨域闸", CapabilityDomain.LISTEN, row.domain)
            assertTrue(
                "${row.kind} 的 master ${row.master} 应归功能页",
                row.master in setOf("uiListen", "networkListen", "deviceEnvListen"),
            )
        }
    }
}
