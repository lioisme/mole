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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 行版式契约(V12 T5):
 * - 五段各占一行:算法 / 操作 / 方向胶囊 / 数据段 / 时间(秒级);
 * - 时间尊重系统 12/24h 时制,恒含秒;
 * - 类别徽章字母与筛选菜单同源(WEB_CRYPTO→'W');
 * - 单击进详情、长按复制回调。
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

    @Test
    fun rendersFiveOwnLines_withSecondsPrecisionTime() {
        rule.setContent {
            MaterialTheme {
                RecordRow(record = record, onClick = {}, onLongClick = {})
            }
        }
        // 行1 算法 / 行2 操作 / 行3 方向胶囊 / 行4 数据段
        rule.onNodeWithText("WebCrypto/AES-GCM").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.op_encrypt)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.dir_encrypt)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.record_data_out) + " " + "cafebabe").assertIsDisplayed()
        // 行5 时间:秒级精度(格式与生产同源)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        val expected = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(record.timestamp))
        rule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun badge_showsKindLetterW_forWebCrypto() {
        rule.setContent {
            MaterialTheme {
                RecordRow(record = record, onClick = {}, onLongClick = {})
            }
        }
        rule.onNodeWithText("W").assertIsDisplayed()
    }

    @Test
    fun clickNavigates_longPressCopies() {
        val clicked = AtomicBoolean(false)
        val longPressed = AtomicBoolean(false)
        rule.setContent {
            MaterialTheme {
                RecordRow(
                    record = record,
                    onClick = { clicked.set(true) },
                    onLongClick = { longPressed.set(true) },
                )
            }
        }
        rule.onNodeWithText("WebCrypto/AES-GCM").performTouchInput { longClick() }
        assertTrue("长按须触发复制回调", longPressed.get())
        rule.onNodeWithText("WebCrypto/AES-GCM").performTouchInput { click() }
        assertTrue("单击须触发导航回调", clicked.get())
    }

    @Test
    fun dataLine_prefersKeyOverOutput_whenBothPresent() {
        val withKey = record.copy(
            key = BytesView(hex = "aabbcc", text = null, totalLength = 3, truncated = false),
            output = BytesView(hex = "cafebabe", text = null, totalLength = 4, truncated = false),
        )
        rule.setContent {
            MaterialTheme {
                RecordRow(record = withKey, onClick = {}, onLongClick = {})
            }
        }
        rule.onNodeWithText(str(R.string.record_data_key) + " " + "aabbcc").assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.record_data_out) + " " + "cafebabe").assertCountEquals(0)
    }
}
