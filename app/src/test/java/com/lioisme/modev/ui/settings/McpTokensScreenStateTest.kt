package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.lioisme.modev.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 认证状态行三态渲染回归(V41 T1,Robolectric 全装配驱动,秒级反馈):
 * 回归防线 —— `tokens_status_active` 曾被以 (String, Int) 双参调用而资源仅含
 * 单个 `%1$,d` 占位符,`String.format` 抛 `IllegalFormatConversionException`
 * → 组合期整个 App 闪退(V41 V1.1:启用认证即崩——空集自动派生令牌使
 * tokenCount≥1,且偏好先落盘,此后每次进入令牌页首帧必崩)。
 * 三态覆盖:停用 / 启用+≥1 令牌(崩溃路径)/ 启用+0 令牌(空集全拒)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpTokensScreenStateTest {

    @get:Rule
    val compose = createComposeRule()

    private fun expectedText(res: Int, vararg args: Any?): String =
        RuntimeEnvironment.getApplication().getString(res, *args)

    private fun assertTextPresent(text: String) {
        assertTrue(
            "状态行应渲染 \"$text\"",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun setStatusLine(authEnabled: Boolean, tokenCount: Int) {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AuthStatusLine(authEnabled = authEnabled, tokenCount = tokenCount)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun statusOff_rendersSelfContainedText() {
        setStatusLine(authEnabled = false, tokenCount = 0)
        assertTrue(
            "状态行 testTag 应存在",
            compose.onAllNodesWithTag("tokens-auth-status").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTextPresent(expectedText(R.string.tokens_status_off))
    }

    @Test
    fun statusActive_withTokens_rendersTokenCount_noFormatCrash() {
        // V1.1 崩溃路径:旧实现传 (String, Int) → IllegalFormatConversionException。
        setStatusLine(authEnabled = true, tokenCount = 2)
        assertTextPresent(expectedText(R.string.tokens_status_active, 2))
    }

    @Test
    fun statusActive_zeroTokens_rendersDenyAllWarning() {
        setStatusLine(authEnabled = true, tokenCount = 0)
        assertTextPresent(expectedText(R.string.tokens_status_deny_all))
    }
}
