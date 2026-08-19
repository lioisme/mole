package com.lioisme.modev.ui.components

import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.mapping.CryptoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 行版式契约(V12 T5;V13 去徽章 + 空白治理;V14 R1 层级精修):
 * - 三级信息架构(M3 ListItem)+ 尾部时间:算法(SemiBold)/ 类别·操作组合行 /
 *   方向胶囊 / 数据段,时间尾随右对齐(秒级);
 * - 时间尊重系统 12/24h 时制,恒含秒;
 * - 无类别字母徽章(V13 移除,与类别色去重);
 * - 单击进详情、长按复制摘要回调([copySummary] 拼接契约)。
 */
@RunWith(AndroidJUnit4::class)
class RecordRowLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val record = CryptoRecord(
        packageName = "com.example.app",
        processName = "com.example.app:web",
        threadName = "test-thread",
        kind = CryptoKind.WEB_CRYPTO,
        operation = CryptoOperation.ENCRYPT,
        algorithm = "WebCrypto/AES-GCM",
        provider = "WebView",
        direction = 1,
        iv = BytesView(hex = "000102030405060708090a0b0c0d0e0f", text = null, totalLength = 16, truncated = false),
        key = null,
        input = BytesView(hex = "deadbeef", text = null, totalLength = 4, truncated = false),
        output = BytesView(hex = "cafebabe", text = null, totalLength = 4, truncated = false),
        timestamp = 1_700_000_000_000,
    )

    private fun row(rec: CryptoRecord) = CryptoRow(rec)

    @Test
    fun rendersOwnLines_withSecondsPrecisionTime() {
        rule.setContent {
            MaterialTheme {
                RecordRow(record = row(record), onClick = {}, onLongClick = {})
            }
        }
        // V14 R1 层级:算法(SemiBold headline)/ 类别·操作组合行(supporting)/
        // 方向胶囊 / 数据段;时间尾随右对齐(秒级)。
        rule.onNodeWithText("WebCrypto/AES-GCM").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.kind_webview) + " · " + str(R.string.op_encrypt)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.dir_encrypt)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.record_data_out) + " " + "cafebabe").assertIsDisplayed()
        // 尾部时间:秒级精度(格式与生产同源)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        val expected = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(record.timestamp))
        rule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun noBadgeLetterRendered() {
        rule.setContent {
            MaterialTheme {
                RecordRow(record = row(record), onClick = {}, onLongClick = {})
            }
        }
        // V13:徽章移除,类别字母(与筛选菜单同源的首字母)不得再以独立节点渲染
        // (精确匹配防算法名/组合行首字母误命中)
        rule.onAllNodesWithText("W", substring = false).assertCountEquals(0)
    }

    @Test
    fun copySummary_concatenatesAllOwnLines() {
        rule.setContent {
            MaterialTheme {
                RecordRow(record = row(record), onClick = {}, onLongClick = {})
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        val expectedTime = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(record.timestamp))
        val summary = record.copySummary(context)
        // 摘要 = 算法 / 操作 / 方向 / 数据段 / 时间 / 包名,逐行拼接
        assertEquals("WebCrypto/AES-GCM", summary.lineSequence().elementAt(0))
        assertEquals(str(R.string.op_encrypt), summary.lineSequence().elementAt(1))
        assertEquals(str(R.string.dir_encrypt), summary.lineSequence().elementAt(2))
        assertEquals(str(R.string.record_data_out) + " " + "cafebabe", summary.lineSequence().elementAt(3))
        assertEquals(expectedTime, summary.lineSequence().elementAt(4))
        assertEquals("com.example.app", summary.lineSequence().elementAt(5))
    }

    @Test
    fun clickNavigates_longPressCopies() {
        val clicked = AtomicBoolean(false)
        val longPressed = AtomicBoolean(false)
        rule.setContent {
            MaterialTheme {
                RecordRow(record = row(record), onClick = { clicked.set(true) }, onLongClick = { longPressed.set(true) })
            }
        }
        rule.onNodeWithText("WebCrypto/AES-GCM").performTouchInput { longClick() }
        assertTrue("长按须触发复制回调", longPressed.get())
        rule.onNodeWithText("WebCrypto/AES-GCM").performTouchInput { click() }
        assertTrue("单击须触发导航回调", clicked.get())
    }

    @Test
    fun blankAlgorithm_headlineAndSummaryFallBackToKindLabel() {
        val blank = record.copy(algorithm = "   ")
        rule.setContent {
            MaterialTheme {
                RecordRow(record = row(blank), onClick = {}, onLongClick = {})
            }
        }
        // V16 R1:空算法不得渲染空白行,回退类别名(与详情页标题/摘要同源)
        rule.onNodeWithText(str(R.string.kind_webview)).assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(str(R.string.kind_webview), blank.copySummary(context).lineSequence().elementAt(0))
    }

    @Test
    fun dataLine_prefersKeyOverOutput_whenBothPresent() {
        val withKey = record.copy(
            key = BytesView(hex = "aabbcc", text = null, totalLength = 3, truncated = false),
            output = BytesView(hex = "cafebabe", text = null, totalLength = 4, truncated = false),
        )
        rule.setContent {
            MaterialTheme {
                RecordRow(record = row(withKey), onClick = {}, onLongClick = {})
            }
        }
        rule.onNodeWithText(str(R.string.record_data_key) + " " + "aabbcc").assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.record_data_out) + " " + "cafebabe").assertCountEquals(0)
    }
}
