package com.lioisme.modev.ui.apps

import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.ui.components.behaviorKindLabelRes
import com.lioisme.modev.ui.components.deviceEnvKindLabelRes
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.networkKindLabelRes
import com.lioisme.modev.ui.components.prefsKindLabelRes
import com.lioisme.modev.ui.components.processKindLabelRes
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.systemKindLabelRes
import com.lioisme.modev.ui.components.uiInteractionKindLabelRes
import com.lioisme.modev.ui.components.webviewKindLabelRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 标签映射唯一表门禁(V44 D3):
 * - [KindLabelRes.resolve] 对全部 taxonomy (type,id) 恒命中真标签,不落 unknown_error 兜底;
 * - resolve 与各枚举穷尽表(列表行/详情共用)逐类别等值——双端同文案的结构保证;
 * - 跨类型碰撞 id(OPEN/SIGNATURE)按类型解析到各自资源键(裸 id 表形态下 SIGNATURE 的
 *   SystemKind 分支曾被 CryptoKind 分支遮蔽恒不可达,本测试钉死修复)。
 */
class KindLabelResExhaustiveTest {

    @Test
    fun `every taxonomy category resolves to a real label`() {
        for (category in CategoryTaxonomy.all()) {
            val resolved = KindLabelRes.resolve(category.type, category.id)
            assertNotEquals(
                "unknown_error 兜底命中 = (type,id) 表与 taxonomy 漂移: ${category.type}/${category.id}",
                R.string.unknown_error,
                resolved,
            )
        }
    }

    @Test
    fun `resolve agrees with enum tables for every category`() {
        for (category in CategoryTaxonomy.all()) {
            val enumRes: Int? = when (category.type) {
                RecordType.CRYPTO -> CryptoKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::kindLabelRes)

                RecordType.DATABASE -> SqlKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::sqlKindLabelRes)

                RecordType.BEHAVIOR -> BehaviorKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::behaviorKindLabelRes)

                RecordType.PREFS -> PrefsKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::prefsKindLabelRes)

                RecordType.WEBVIEW -> WebViewKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::webviewKindLabelRes)

                RecordType.NETWORK -> NetworkKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::networkKindLabelRes)

                RecordType.DEVICE_ENV -> DeviceEnvKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::deviceEnvKindLabelRes)

                RecordType.UI_INTERACTION -> UiInteractionKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::uiInteractionKindLabelRes)

                RecordType.SYSTEM -> SystemKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::systemKindLabelRes)

                RecordType.PROCESS_CONTROL -> ProcessKind.entries.firstOrNull { it.name == category.id }
                    ?.let(::processKindLabelRes)
            }
            assertEquals(
                "表间漂移: ${category.type}/${category.id}",
                enumRes,
                KindLabelRes.resolve(category.type, category.id),
            )
        }
    }

    @Test
    fun `colliding ids resolve per type (no shadowing)`() {
        // OPEN:数据库 ≠ 偏好;SIGNATURE:算法 ≠ 系统信息。
        assertNotEquals(
            KindLabelRes.resolve(RecordType.DATABASE, "OPEN"),
            KindLabelRes.resolve(RecordType.PREFS, "OPEN"),
        )
        assertEquals(sqlKindLabelRes(SqlKind.OPEN), KindLabelRes.resolve(RecordType.DATABASE, "OPEN"))
        assertEquals(prefsKindLabelRes(PrefsKind.OPEN), KindLabelRes.resolve(RecordType.PREFS, "OPEN"))
        assertNotEquals(
            KindLabelRes.resolve(RecordType.CRYPTO, "SIGNATURE"),
            KindLabelRes.resolve(RecordType.SYSTEM, "SIGNATURE"),
        )
        assertEquals(
            systemKindLabelRes(SystemKind.SIGNATURE),
            KindLabelRes.resolve(RecordType.SYSTEM, "SIGNATURE"),
        )
    }
}
