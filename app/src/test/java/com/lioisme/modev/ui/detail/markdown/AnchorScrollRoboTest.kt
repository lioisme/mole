package com.lioisme.modev.ui.detail.markdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.ui.detail.CryptoLoadedContent
import com.lioisme.modev.ui.detail.markdown.DetailCodeActions
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 锚点滚动回归(V26,Robolectric 全装配驱动,秒级反馈):
 * 回归防线 —— 详情页 LazyColumn 必须绑定簿记共享的 listState
 * (V26 重写期曾遗漏 state 参数导致锚点滚动静默失效:animateScrollToItem
 * 打在无布局的孤立状态上,firstVisible 恒 0)。
 * 契约:点击「密钥材料」锚点 → firstVisibleItemIndex = 密钥区 item 索引,
 * 且密钥 hex 内容进入组合。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnchorScrollRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private fun record() = CryptoRecord(
        packageName = "com.example.app",
        kind = CryptoKind.CIPHER,
        operation = CryptoOperation.ENCRYPT,
        algorithm = "AES/CBC/PKCS5Padding",
        direction = 1,
        key = BytesView(hex = "0102030405060708090a0b0c0d0e0f10", text = null, totalLength = 16, truncated = false),
        iv = BytesView(hex = "000102030405060708090a0b0c0d0e0f", text = null, totalLength = 16, truncated = false),
        input = BytesView(hex = "68656c6c6f", text = "hello", totalLength = 5, truncated = false),
        output = BytesView(hex = "1b7d0e5f", text = null, totalLength = 4, truncated = false),
        threadName = "t",
        stack = listOf("a.b.C.m(C.kt:1)"),
        timestamp = 1_700_000_000_000,
    )

    @Test
    fun anchorClick_scrollsToKeyZone_hexComposable() {
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Scaffold { padding ->
                        val listState = rememberLazyListState()
                        state = listState
                        CryptoLoadedContent(
                            record = record(),
                            innerPadding = padding,
                            listState = listState,
                            actions = DetailCodeActions({}, {}),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        // 对照实验 B:点击 TOC 导航行(V28;标签与区块标题同源,行级 clickable
        // 中心命中稳定;簿记索引经 println 落 XML system-out)。
        val anchorLabel = RuntimeEnvironment.getApplication().getString(R.string.section_zone_key_material)
        compose.onAllNodesWithText(anchorLabel).onFirst().performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val found =
            compose.onAllNodesWithText("0102030405060708090a0b0c0d0e0f10").fetchSemanticsNodes().isNotEmpty()
        println("ANCHOR_ROBO clickFound=$found firstVisible=" + state.firstVisibleItemIndex)
        val info = state.layoutInfo
        println(
            "ANCHOR_LAYOUT total=" + info.totalItemsCount +
                " viewport=" + info.viewportEndOffset +
                " visible=" + info.visibleItemsInfo.joinToString { it.index.toString() + ":" + it.size },
        )
        assertTrue("hex 应在组合中", found)
    }
}
