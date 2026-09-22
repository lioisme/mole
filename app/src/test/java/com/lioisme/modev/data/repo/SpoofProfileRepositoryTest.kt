package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileCodec
import com.lioisme.modev.core.model.ProfileEnvelope
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileImportVerdict
import com.lioisme.modev.core.model.ProfileResolution
import com.lioisme.modev.core.model.ProfileTransferItem
import com.lioisme.modev.core.model.withText
import com.lioisme.modev.data.db.ModevDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * V47 T2 画像库行为测试(真 Room 库,非 mock):
 * 内置档账本(补档 / 幂等 / 墓碑 / 脱管 / 收养)、三态绑定解析、写入校验、导入导出。
 *
 * 断言全部针对**用户可见结果**(库里到底有几档、内容是什么、绑定解析到哪一态),
 * 不用「某方法有没有被调用」这类代理指标。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpoofProfileRepositoryTest {

    private lateinit var db: ModevDatabase
    private lateinit var repo: SpoofProfileRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var clock = 1_000L

    private fun tick(): Long {
        clock += 100
        return clock
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        repo = SpoofProfileRepository(db.spoofProfileDao(), scope, timeMillis = { tick() })
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private val pixelProfile: DeviceProfile =
        BuiltinDeviceProfiles.specFor(BuiltinDeviceProfiles.PIXEL_7_PRO)!!.profile

    private fun sampleProfile(brand: String = "Acme", model: String = "One") =
        ProfileField.MODEL.withText(
            ProfileField.BRAND.withText(DeviceProfile.EMPTY, brand),
            model,
        )

    // ---------------------------------------------------------------- 内置档账本

    @Test
    fun `synchronize installs three builtins and is idempotent`() = runBlocking {
        repo.synchronizeBuiltins()
        repo.synchronizeBuiltins()
        val live = repo.profiles.value
        assertEquals(3, live.size)
        assertTrue(live.all { it.isBuiltin })
        assertEquals(
            setOf(
                BuiltinDeviceProfiles.PIXEL_7_PRO,
                BuiltinDeviceProfiles.XIAOMI_13,
                BuiltinDeviceProfiles.GALAXY_S23_ULTRA,
            ),
            live.mapNotNull { it.builtinSeedId }.toSet(),
        )
        assertEquals(13, live.first { it.builtinSeedId == BuiltinDeviceProfiles.PIXEL_7_PRO }.filledCount)
    }

    @Test
    fun `deleting a builtin writes a tombstone and never revives`() = runBlocking {
        repo.synchronizeBuiltins()
        val id = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.XIAOMI_13)
        assertTrue(repo.delete(id))
        assertEquals(2, repo.profiles.value.size)

        repo.resynchronizeBuiltins()
        assertEquals("墓碑档被复活(用户删过的内置档必须恒不再补档)", 2, repo.profiles.value.size)
        assertNull(repo.find(id))
    }

    @Test
    fun `editing a builtin detaches it and survives future sync`() = runBlocking {
        repo.synchronizeBuiltins()
        val id = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO)
        val custom = sampleProfile(brand = "MyBrand", model = "MyModel")
        assertTrue(repo.update(id, "我改过的 Pixel", "", custom))

        repo.resynchronizeBuiltins()

        val entry = repo.find(id)
        assertNotNull(entry)
        assertFalse("已脱管档被升级覆盖回来了", entry!!.profile == pixelProfile)
        assertEquals("MyBrand", entry.profile.brand)
        assertFalse(entry.managed)
        assertTrue("seedId 必须保留(呈现名仍按内置资源取)", entry.isBuiltin)
    }

    @Test
    fun `sync adopts an existing identical profile instead of duplicating`() = runBlocking {
        val created = repo.create("我的 Pixel", "同内容", pixelProfile)
        assertNotNull(created)

        repo.synchronizeBuiltins()

        val live = repo.profiles.value
        assertEquals("同内容画像应被收养而非重复建档", 3, live.size)
        val adopted = live.first { it.id == created!!.id }
        assertEquals(BuiltinDeviceProfiles.PIXEL_7_PRO, adopted.builtinSeedId)
        assertTrue(adopted.managed)
        assertEquals("收养须保留用户起的名字", "我的 Pixel", adopted.name)
    }

    // ---------------------------------------------------------------- 三态解析

    @Test
    fun `resolve distinguishes unbound bound and missing`() = runBlocking {
        repo.synchronizeBuiltins()
        val id = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.GALAXY_S23_ULTRA)

        assertTrue(repo.resolve(null) is ProfileResolution.Unbound)
        assertTrue(repo.resolve("   ") is ProfileResolution.Unbound)
        val bound = repo.resolve(id)
        assertTrue(bound is ProfileResolution.Bound)
        assertEquals(BuiltinDeviceProfiles.GALAXY_S23_ULTRA, (bound as ProfileResolution.Bound).entry.builtinSeedId)

        val missing = repo.resolve("ghost-profile-id")
        assertTrue(
            "绑了不存在的 id 必须落 Missing —— 回落 auto 或不伪装就是 GAP-02 的静默失效",
            missing is ProfileResolution.Missing,
        )
        assertEquals("ghost-profile-id", (missing as ProfileResolution.Missing).id)
        assertEquals(DeviceProfile.EMPTY, missing.profileOrEmpty())
    }

    @Test
    fun `bound profile content equals the builtin spec exactly`() = runBlocking {
        repo.synchronizeBuiltins()
        val resolution = repo.resolve(BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO))
        assertEquals(pixelProfile, resolution.profileOrEmpty())
    }

    // ---------------------------------------------------------------- 写入校验

    @Test
    fun `create rejects blank name empty profile and invalid values`() = runBlocking {
        assertNull(repo.create("   ", "", sampleProfile()))
        assertNull(repo.create("无名", "", DeviceProfile.EMPTY))
        val invalid = ProfileField.IMEI.withText(sampleProfile(), "490171581696171")
        assertNull("非法 IMEI 必须拒绝写入,不得钳制或静默丢弃", repo.create("坏档", "", invalid))
        assertTrue(repo.profiles.value.isEmpty())
    }

    @Test
    fun `update rejects unknown id and keeps every other row untouched`() = runBlocking {
        repo.synchronizeBuiltins()
        assertFalse(repo.update("nope", "x", "", sampleProfile()))
        assertEquals(3, repo.profiles.value.size)
    }

    @Test
    fun `duplicate creates an independent row that survives source deletion`() = runBlocking {
        repo.synchronizeBuiltins()
        val source = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO)
        val copy = repo.duplicate(source, "副本档")
        assertNotNull(copy)
        assertTrue(repo.delete(source))
        val copied = repo.find(copy!!.id)
        assertNotNull(copied)
        assertFalse("副本必须是 DIY 行(不受内置账本管辖)", copied!!.isBuiltin)
        assertEquals(pixelProfile, copied.profile)
    }

    // ---------------------------------------------------------------- 导入导出

    @Test
    fun `export then import round trips and skips duplicates`() = runBlocking {
        repo.synchronizeBuiltins()
        val mine = repo.create("我的档", "s", sampleProfile("Acme", "Zed"))
        assertNotNull(mine)
        val text = repo.exportJson()

        val outcome = repo.importJson(text, defaultName = "导入档")
        assertEquals("内容相同的条目应全部判重跳过", 0, outcome.imported)
        assertEquals(4, outcome.skipped)
        assertEquals(0, outcome.rejected)
        assertEquals(4, repo.profiles.value.size)
    }

    @Test
    fun `import of foreign content lands as a new diy row`() = runBlocking {
        val envelope = ProfileEnvelope(
            items = listOf(ProfileTransferItem("外来档", "来自别机", sampleProfile("Beta", "Two"))),
        )
        val outcome = repo.importJson(Json.gson.toJson(envelope), defaultName = "导入档")
        assertEquals(1, outcome.imported)
        val row = repo.profiles.value.single()
        assertFalse(row.isBuiltin)
        assertEquals("外来档", row.name)
        assertEquals("Beta", row.profile.brand)
    }

    @Test
    fun `import falls back to caller-supplied name but never embeds a data-layer literal`() = runBlocking {
        val envelope = ProfileEnvelope(
            items = listOf(ProfileTransferItem("   ", "", sampleProfile("Gamma", "Three"))),
        )
        val outcome = repo.importJson(Json.gson.toJson(envelope), defaultName = "IMPORTED_DEFAULT")
        assertEquals(listOf(ProfileImportVerdict.IMPORTED_NAME_FALLBACK), outcome.verdicts)
        assertEquals("IMPORTED_DEFAULT", repo.profiles.value.single().name)
    }

    @Test
    fun `import rejects malformed text and wrong schema version as schema failure`() = runBlocking {
        assertTrue(repo.importJson("garbage", defaultName = "d").schemaRejected)
        val wrongVersion = Json.gson.toJson(ProfileEnvelope(schemaVersion = 99, items = listOf()))
        assertTrue(repo.importJson(wrongVersion, defaultName = "d").schemaRejected)
        assertTrue(repo.profiles.value.isEmpty())
    }

    @Test
    fun `import rejects empty and invalid items per-row without aborting the batch`() = runBlocking {
        val badImei = ProfileField.IMEI.withText(sampleProfile("Delta", "Four"), "490171581696171")
        val envelope = ProfileEnvelope(
            items = listOf(
                ProfileTransferItem("空档", "", DeviceProfile.EMPTY),
                ProfileTransferItem("坏档", "", badImei),
                ProfileTransferItem("好档", "", sampleProfile("Delta", "Five")),
            ),
        )
        val outcome = repo.importJson(Json.gson.toJson(envelope), defaultName = "d")
        assertEquals(
            listOf(
                ProfileImportVerdict.REJECTED_EMPTY,
                ProfileImportVerdict.REJECTED_INVALID,
                ProfileImportVerdict.IMPORTED,
            ),
            outcome.verdicts,
        )
        assertEquals(1, repo.profiles.value.size)
    }

    @Test
    fun `dry run validates without writing`() = runBlocking {
        val envelope = ProfileEnvelope(
            items = listOf(ProfileTransferItem("预览档", "", sampleProfile("Echo", "Six"))),
        )
        val outcome = repo.importJson(Json.gson.toJson(envelope), dryRun = true, defaultName = "d")
        assertEquals(1, outcome.imported)
        assertEquals(listOf(ProfileImportVerdict.DRY_RUN_OK), outcome.verdicts)
        assertTrue("dryRun 不得落库", repo.profiles.value.isEmpty())
    }

    @Test
    fun `oversized package is rejected as a whole rather than partially imported`() = runBlocking {
        val items = List(ProfileEnvelope.MAX_ITEMS + 5) {
            ProfileTransferItem("档$it", "", sampleProfile("Bulk", it.toString()))
        }
        val text = Json.gson.toJson(ProfileEnvelope(items = items))
        val outcome = repo.importJson(text, defaultName = "d")
        assertEquals(0, outcome.imported)
        assertEquals(items.size, outcome.rejected)
        assertTrue(outcome.verdicts.all { it == ProfileImportVerdict.REJECTED_LIMIT })
        assertTrue(repo.profiles.value.isEmpty())
    }

    @Test
    fun `repository snapshot is readable immediately after each write`() = runBlocking {
        // 读写竞态围栏:创建后同一次调用链里就要能解析到(不等 Flow 发射)。
        val created = repo.create("立刻可见", "", sampleProfile("Fox", "Seven"))
        assertNotNull(created)
        val resolution = repo.resolve(created!!.id)
        assertTrue(resolution is ProfileResolution.Bound)
        assertEquals("Fox", resolution.profileOrEmpty().brand)
    }

    @Test
    fun `fingerprint stored on builtin row matches recomputed content`() = runBlocking {
        repo.synchronizeBuiltins()
        BuiltinDeviceProfiles.BUILTINS.forEach { spec ->
            val row = db.spoofProfileDao().getBySeedId(spec.seedId)
            assertNotNull(row)
            assertEquals(
                "账本指纹与正文不一致 ⇒ 升级判定会失准",
                ProfileCodec.fingerprint(spec.profile),
                row!!.builtinFingerprint,
            )
        }
    }
}
