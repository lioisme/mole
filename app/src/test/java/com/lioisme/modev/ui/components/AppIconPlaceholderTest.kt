package com.lioisme.modev.ui.components

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AppIcon 占位底图状态驱动回归(V42 T2,Robolectric 全装配驱动):
 * 占位首字母块必须为「加载中/失败态限定」——加载成功(Success)后立即移除,
 * 不规则/透明图标不再透出底层色块;加载失败仍保持首字母兜底。
 * 两用例经 fake [ImageLoader] 注入(成功/失败双路),与生产 loader 零耦合;
 * 两用例使用不同包名,规避 Coil MemoryCache 跨用例命中(键 = "icon:{pkg}")。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppIconPlaceholderTest {

    @get:Rule
    val compose = createComposeRule()

    /** fake Fetcher 工厂:成功路径返回纯色 Drawable,失败路径抛异常(Coil 置 Error 态)。 */
    private class FakeFetcherFactory(private val fail: Boolean) : Fetcher.Factory<PackageIcon> {
        override fun create(data: PackageIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            object : Fetcher {
                override suspend fun fetch(): DrawableResult {
                    if (fail) throw IllegalStateException("fake fetch failure")
                    return DrawableResult(
                        ColorDrawable(0xFF00FF00.toInt()),
                        isSampled = false,
                        dataSource = DataSource.DISK,
                    )
                }
            }
    }

    private fun fakeLoader(fail: Boolean): ImageLoader =
        ImageLoader.Builder(RuntimeEnvironment.getApplication())
            .components {
                add(object : Keyer<PackageIcon> {
                    override fun key(data: PackageIcon, options: Options): String =
                        "fake:${data.packageName}"
                })
                add(FakeFetcherFactory(fail))
            }
            .build()

    private fun setContent(packageName: String, loader: ImageLoader) {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppIcon(packageName = packageName, size = 40.dp, loader = loader)
                }
            }
        }
    }

    /** Coil 加载为后台协程,Robolectric 无 waitUntil——轮询至条件成立或超时。 */
    private fun pollUntil(desc: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(50)
        }
        compose.waitForIdle()
        assertTrue(desc, condition())
    }

    private fun placeholderNodes() =
        compose.onAllNodesWithTag("app-icon-placeholder").fetchSemanticsNodes()

    @Test
    fun success_state_removesPlaceholder() {
        // 非缓存包名:强制走 fetch 成功路径。
        setContent("fake.success.pkg", fakeLoader(fail = false))
        pollUntil("加载成功后占位底图必须移除") { placeholderNodes().isEmpty() }
    }

    @Test
    fun failure_state_keepsPlaceholder() {
        // 非缓存包名:强制走 fetch 失败路径(占位即终态)。
        setContent("fake.failure.pkg", fakeLoader(fail = true))
        pollUntil("加载失败后占位底图必须保持") { placeholderNodes().isNotEmpty() }
    }
}
