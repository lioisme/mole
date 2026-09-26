package com.lioisme.modev.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lioisme.modev.core.model.CarrierEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 运营商目录仓库测试:口径与 `DeviceCatalogRepositoryTest` 同族 —— 测**资产通路 + 缓存闸**,
 * 解析细节归 `CarrierCatalogTest`。两个仓库共用 [CachedAssetReader],所以这四条只需在这一处
 * 再跑一遍真实资产,就能证明「抽公共闸」没有把机型目录的语义改坏(那一份的回归由它自己的测试盯)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarrierCatalogRepositoryTest {

    private val assets get() = ApplicationProvider.getApplicationContext<Context>().assets

    @Test
    fun shippedAssetIsReadThroughTheApplicationAssetManager() = runBlocking {
        val repo = CarrierCatalogRepository(assets)
        val carriers = repo.carriers()
        assertNotNull("随包 carriers.json 应可读可解", carriers)
        assertTrue("零条目 = 资产没真读出来", carriers!!.isNotEmpty())
        assertTrue("应存在带 PLMN 的条目", carriers.any { it.plmn.matches(Regex("\\d{5,6}")) })
        assertEquals(1, repo.readAttempts)
    }

    @Test
    fun secondCallIsServedFromCacheWithoutTouchingDisk() = runBlocking {
        val repo = CarrierCatalogRepository(assets)
        val first = checkNotNull(repo.carriers())
        val second = checkNotNull(repo.carriers())
        assertSame("缓存未命中(每次调用都重读 134 KB 资产)", first, second)
        assertEquals(1, repo.readAttempts)
    }

    @Test
    fun missingAssetYieldsNullInsteadOfThrowingOrAnEmptyTable() = runBlocking {
        val repo = CarrierCatalogRepository(assets, assetName = "no-such-carriers.json")
        assertNull(repo.carriers())
        assertEquals(1, repo.readAttempts)
    }

    @Test
    fun failureIsRetriedOnlyAfterTheCooldownWindow() = runBlocking {
        var now = 0L
        val repo = CarrierCatalogRepository(
            assets,
            clock = { now },
            assetName = "no-such-carriers.json",
        )
        assertNull(repo.carriers())
        now += CachedAssetReader.RETRY_COOLDOWN_MS - 1
        assertNull("冷却窗口内不得重试开文件", repo.carriers())
        assertEquals(1, repo.readAttempts)
        now += 2
        assertNull(repo.carriers())
        assertEquals("窗口过后仍会重试(null 不被当成功缓存)", 2, repo.readAttempts)
    }

    /** 条目解码保真:四键都要落到位(Gson 字段名与资产键名不能悄悄错配)。 */
    @Test
    fun allFourAssetColumnsSurviveDecoding() = runBlocking {
        val carriers = checkNotNull(CarrierCatalogRepository(assets).carriers())
        val sample: CarrierEntry = carriers.first { it.countryCode.length == 2 && it.countryName.isNotBlank() }
        assertTrue(sample.name.isNotBlank() && sample.plmn.isNotBlank())
        assertEquals(2, sample.countryCode.length)
    }
}
