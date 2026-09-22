package com.lioisme.modev.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lioisme.modev.core.model.DeviceCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 真机目录仓库测试(V47):测的是**资产通路 + 缓存口径**,不是解析细节
 * (解析与写入语义由纯 JVM 的 `core.model.DeviceCatalogTest` 逐条钉住,那里零 Robolectric)。
 *
 * 四条口径:
 *  ① 随包 `devices.json` 真能经 `ApplicationProvider ... .assets` 读出并解出品牌;
 *  ② 第二次调用命中缓存 —— 既断言同一实例,也断言**开文件次数恒为一**(1.9 MB 资产
 *     逐键重读是要防的事,光看返回值看不出重读);
 *  ③ 资产名不存在 ⇒ null 而非抛异常(选择器必须能把 null 上屏成错误态);
 *  ④ 失败后只在冷却窗口过后重试(窗口内零次开文件),失败结论不被当成功缓存。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceCatalogRepositoryTest {

    private val assets get() = ApplicationProvider.getApplicationContext<Context>().assets

    @Test
    fun shippedAssetIsReadThroughTheApplicationAssetManager() = runBlocking {
        val repo = DeviceCatalogRepository(assets)
        val catalog = checkNotNull(repo.catalog()) { "随包 devices.json 应可读可解" }
        assertTrue("目录零品牌 = 资产未被真正读出", catalog.brands.isNotEmpty())
        assertTrue("应存在带机型记录的品牌", catalog.brands.any { it.models.isNotEmpty() })
        assertEquals(DeviceCatalog.SCHEMA_VERSION, catalog.schemaVersion)
        assertEquals(1, repo.readAttempts)
    }

    @Test
    fun secondCallIsServedFromCacheAndNeverReReadsTheAsset() = runBlocking {
        val repo = DeviceCatalogRepository(assets)
        val first = checkNotNull(repo.catalog()) { "首次读取应成功" }
        val second = checkNotNull(repo.catalog()) { "第二次调用不应因缓存实现破损而变 null" }
        assertSame("两次调用必须同一实例(重解析会得到新实例)", first, second)
        assertEquals("缓存命中不得再开文件", 1, repo.readAttempts)
    }

    @Test
    fun missingAssetYieldsNullInsteadOfThrowing() = runBlocking {
        val repo = DeviceCatalogRepository(assets, assetName = "no-such-devices.json")
        assertNull("资产缺失应返回 null,不得向协程抛 IOException", repo.catalog())
        assertEquals(1, repo.readAttempts)
    }

    @Test
    fun failureIsRetriedOnlyAfterTheCooldownWindow() = runBlocking {
        var now = 1_000L
        val repo = DeviceCatalogRepository(
            assets = assets,
            clock = { now },
            assetName = "no-such-devices.json",
        )
        assertNull(repo.catalog())
        now += DeviceCatalogRepository.RETRY_COOLDOWN_MS - 1
        assertNull("窗口内仍报不可用", repo.catalog())
        assertEquals("窗口内不得重试开文件", 1, repo.readAttempts)
        now += 2
        assertNull(repo.catalog())
        assertEquals(2, repo.readAttempts)
    }
}
