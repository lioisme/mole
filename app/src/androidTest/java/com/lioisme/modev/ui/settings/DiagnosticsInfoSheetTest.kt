package com.lioisme.modev.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V26 R4 诊断信息单行省略 + 底部弹层验收(组件级,环境无关):
 * - SettingsInfoItem(valueMaxLines=1 + onClick + testTag):长值行可点击;
 * - DiagnosticsDetailSheet:标题 + 完整值(可选中文本)可见,复制动作写剪贴板;
 * - 复制反馈 Snackbar(diagnostics_copied 复用);关闭后弹层语义消失。
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsInfoSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private val longValue =
        "LSPosed Framework 1.9.2 (6925) - very long framework description " +
            "that must be ellipsized to a single line inside the row"

    private var lastCopied: String? = null

    /** 测试宿主:信息行(点击开弹层)+ 弹层(复制走宿主回调,断言可观测)。 */
    @Composable
    private fun Host(initiallyOpen: Boolean = false) {
        val clipboard = LocalClipboardManager.current
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var open by remember { mutableStateOf(initiallyOpen) }
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                SettingsSection(str(R.string.settings_framework_section)) {
                    SettingsInfoItem(
                        icon = Icons.Default.Android,
                        title = str(R.string.settings_framework),
                        value = longValue,
                        valueMaxLines = 1,
                        modifier = Modifier.testTag("info-row"),
                        onClick = { open = true },
                    )
                }
            }
        }
        if (open) {
            DiagnosticsDetailSheet(
                detail = DiagnosticsDetail(title = str(R.string.settings_framework), value = longValue),
                onDismiss = { open = false },
                onCopy = { value ->
                    lastCopied = value
                    clipboard.setText(AnnotatedString(value))
                    scope.launch { snackbarHostState.showSnackbar(str(R.string.diagnostics_copied)) }
                },
            )
        }
    }

    @Test
    fun longValueRow_click_opensSheet_fullValueSelectable() {
        rule.setContent { MaterialTheme { Host() } }

        // 单击信息行 → 底部弹层打开,完整值可见(单行省略仅视觉,弹层承载全文)。
        rule.onNodeWithTag("info-row").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        var opened = false
        while (System.currentTimeMillis() < deadline && !opened) {
            rule.waitForIdle()
            opened = rule.onAllNodesWithText(longValue).fetchSemanticsNodes().isNotEmpty()
            if (!opened) Thread.sleep(100)
        }
        assertTrue("点击行后弹层应打开并含完整值", opened)
        // 标题在行与弹层双处承载(onAllNodes 多节点安全形态)。
        assertTrue(
            "弹层标题应在树",
            rule.onAllNodesWithText(str(R.string.settings_framework)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun sheetCopyAction_copiesFullValue_andShowsSnackbar() {
        lastCopied = null
        rule.setContent { MaterialTheme { Host(initiallyOpen = true) } }

        rule.onNodeWithText(str(R.string.diagnostics_detail_copy)).performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && lastCopied == null) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertEquals(longValue, lastCopied)

        // Snackbar 反馈。
        var shown = false
        while (System.currentTimeMillis() < deadline && !shown) {
            rule.waitForIdle()
            shown = rule.onAllNodesWithText(str(R.string.diagnostics_copied)).fetchSemanticsNodes().isNotEmpty()
            if (!shown) Thread.sleep(100)
        }
        assertTrue("复制后应显示 Snackbar 反馈", shown)

        // 关闭弹层(BACK = ModalBottomSheet onDismissRequest;hide 完成后置空契约)。
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        val goneDeadline = System.currentTimeMillis() + 10_000
        var gone = false
        while (System.currentTimeMillis() < goneDeadline && !gone) {
            rule.waitForIdle()
            gone = rule.onAllNodesWithTag("diagnostics-info-sheet").fetchSemanticsNodes().isEmpty()
            if (!gone) Thread.sleep(100)
        }
        assertTrue("关闭后弹层应从语义树移除", gone)
    }

    /** 读系统剪贴板(备用;主断言经宿主回调捕获)。 */
    @Suppress("unused")
    private fun readClipboard(): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
    }
}
