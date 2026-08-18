package com.lioisme.modev

import android.text.format.DateFormat
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.MainActivity
import com.lioisme.modev.ui.common.groupLabelOrNull
import com.lioisme.modev.ui.common.timelineLabel
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 秒级时间线分组验收(V17 R3):
 * 种子数据 = 同秒 3 行 + 跨秒 1 行 + 跨日 1 行,进入被测应用记录页断言:
 * - 首项(r4)恒有组头 = 日期 + 秒级时间;
 * - 跨秒新头 = 同日仅秒级时间;
 * - 同秒 3 行仅 1 个头(单头不重复);
 * - 跨日头带日期前缀。
 * 前置:被测应用自身是 Xposed 模块(默认隐藏),@Before 开启「显示 Xposed 模块」
 * 使行可见,@After 复位(与单元测试同源,避免污染其他用例)。
 * 期望文案经 TimelineGroup 纯函数以设备 locale / 12-24h 时制复算,与生产同源。
 */
@RunWith(AndroidJUnit4::class)
class TimelineListTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val packageName = "com.lioisme.modev"

    /** 种子时间戳(@Before 固定,测试体内复算会因导航耗时跨秒漂移,禁止)。 */
    private var s1 = 0L
    private var s2 = 0L
    private var d1 = 0L

    @Before
    fun seed() {
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        // 清空被测包既有记录,断言确定性(与运行库共享单例,直接操作同一 DB)。
        runBlocking { dao.clearPackage(packageName) }
        val now = System.currentTimeMillis()
        s1 = now - 120_000
        s2 = s1 + 1_000
        d1 = now - 2 * 24 * 3600 * 1_000L
        runBlocking {
            dao.insertAll(
                listOf(
                    record(s1, "MESSAGE_DIGEST", "SHA-256", "DIGEST"),
                    record(s1, "MESSAGE_DIGEST", "MD5", "DIGEST"),
                    record(s1, "CIPHER", "AES", "ENCRYPT"),
                    record(s2, "CIPHER", "DES", "ENCRYPT"),
                    record(d1, "MAC", "HmacSHA256", "SIGN"),
                ),
            )
            // 写回读验:与 UI 同一 DB 单例,必须即刻可见(哨兵计数语义同 DAO 契约)。
            val seeded = dao
                .observeCountByFilter(
                    recordTypes = emptyList(),
                    packages = listOf(packageName),
                    kinds = emptyList(),
                    typeCount = 0,
                    packageCount = 1,
                    kindCount = 0,
                    query = null,
                )
                .first()
            assertEquals("种子写入须在读回中可见", 5L, seeded)
        }
        // 被测应用默认隐藏(自身是 Xposed 模块),开启显隐使行可见。
        // 仅作用域筛选会按进程隐藏行(rootHasOnly 勾选后持久化),一并复位保证确定性。
        AppListSettings.setOnlyScoped(false, ctx)
        AppListSettings.setShowModules(true, ctx)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false, ctx)
    }

    private fun record(ts: Long, kind: String, algorithm: String, operation: String) =
        CryptoRecordEntity(
            packageName = packageName,
            kind = kind,
            operation = operation,
            algorithm = algorithm,
            threadName = "timeline-test",
            timestamp = ts,
        )

    @Test
    fun timelineGroups_sameSecondSingleHeader_crossSecondAndCrossDayHeaders() {
        rule.waitForIdle()
        // 目录异步加载 + 模块显隐传播,轮询等待被测包行出现(滚动惰性列表查找),
        // 找不到超时即失败(种子数据恒在,仅目录加载有延迟)。
        val deadline = System.currentTimeMillis() + 10_000
        var rowFound = false
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                try {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(packageName))
                    rowFound = true
                    break
                } catch (_: AssertionError) {
                    // 目标行未加载/被模块显隐过滤,下一轮重试。
                }
            }
            Thread.sleep(100)
        }
        assertTrue("等待「显示 Xposed 模块」生效后被测包行应出现", rowFound)
        rule.onNodeWithText(packageName).performClick()
        rule.waitForIdle()

        val locale: Locale =
            LocaleManager.wrap(ctx).resources.configuration.locales[0] ?: Locale.getDefault()
        val is24h = DateFormat.is24HourFormat(ctx)

        // 分页异步加载,有界轮询等待首个组头出现(与既有 androidTest 轮询风格一致)。
        val headersDeadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < headersDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithTag("timeline-header").fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(100)
        }
        rule.onAllNodesWithTag("timeline-header").onFirst().assertIsDisplayed()
        // 首项(r4)恒为组起始:日期 + 秒级时间。
        rule.onNodeWithText(timelineLabel(s2, locale, is24h)).assertIsDisplayed()
        // 跨秒新头(同日仅秒级时间),同秒组仅此一个头(单头不重复)。
        // 计数须限定在组头节点内:行尾时间与组头同日格式同串,防误伤。
        val sameSecondLabel = groupLabelOrNull(s1, s2, locale, is24h)
        rule.onAllNodes(hasTestTag("timeline-header") and hasAnyDescendant(hasText(sameSecondLabel!!)))
            .assertCountEquals(1)
        // 跨日头带日期前缀。
        rule.onNodeWithText(groupLabelOrNull(d1, s1, locale, is24h)!!).assertIsDisplayed()
    }
}