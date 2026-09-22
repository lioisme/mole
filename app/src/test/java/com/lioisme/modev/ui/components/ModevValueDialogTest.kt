package com.lioisme.modev.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 只读值对话框契约(V46 R3/D3):完整值原样上屏 + 复制/关闭各回调恰一次,
 * 且组件面零输入控件。
 *
 * 第三例走源码扫描而非语义匹配器:本仓 Compose 测试库缺件多(AGENTS 记过
 * `assertExists`/`SemanticsProperties.OnClick` 等),「有无输入控件」这一负向
 * 契约由源码门禁给更稳的保证,与 [com.lioisme.modev.architecture.DialogConventionTest] 同族;
 * 组合层两例继续做行为验证。标题用字面量 ⇒ 本类不依赖资源键。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModevValueDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        value: String = DEFAULT_VALUE,
        onCopy: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    ModevValueDialog(
                        title = "value-title",
                        value = value,
                        onCopy = onCopy,
                        onDismiss = onDismiss,
                        valueTestTag = "value-text",
                        copyTestTag = "value-copy",
                        closeTestTag = "value-close",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun rendersValueVerbatim_withCopyAndCloseAnchors() {
        show(value = DEFAULT_VALUE)
        assertTrue(
            "值节点锚点必须存在",
            compose.onAllNodesWithTag("value-text").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "对话框内必须原样呈现完整值(不掩码、不截断)",
            compose.onAllNodesWithText(DEFAULT_VALUE).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun copyInvokesOnce_withoutClosing_andCloseIsSeparate() {
        var copies = 0
        var dismisses = 0
        show(onCopy = { copies++ }, onDismiss = { dismisses++ })

        compose.onNodeWithTag("value-copy").performClick()
        compose.waitForIdle()
        assertEquals("复制只回调一次", 1, copies)
        assertEquals("复制不得替调用方关窗(收口由宿主决定)", 0, dismisses)

        compose.onNodeWithTag("value-close").performClick()
        compose.waitForIdle()
        assertEquals("关闭是独立动作", 1, dismisses)
    }

    @Test
    fun componentSurfaceCarriesNoTextInput() {
        val relative = "src/main/java/com/lioisme/modev/ui/components/ModevDialogs.kt"
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        val file = listOf(
            File(userDir, relative),
            File(userDir, "app/$relative"),
            File(userDir, "../app/$relative"),
        ).firstOrNull { it.isFile }
            ?: error("ModevDialogs.kt 未定位(user.dir=$userDir)")
        assertTrue(
            "只读对话框禁含输入控件(带键盘输入的弹层一律底部 sheet,强约束 #4)",
            !Regex("""\b(Outlined)?TextField\(""").containsMatchIn(file.readText()),
        )
    }

    private companion object {
        const val DEFAULT_VALUE = "0123456789abcdef0123456789abcdef"
    }
}
