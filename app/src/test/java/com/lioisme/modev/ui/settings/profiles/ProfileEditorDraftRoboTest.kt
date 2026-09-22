package com.lioisme.modev.ui.settings.profiles

import com.lioisme.modev.R
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 画像编辑器的**新建 / 落库**通路回归(V47 WP-5)。
 *
 * 新建必须走草稿而不是「先落一行空画像」:`SpoofProfileService.create` 按契约拒绝零字段
 * 正文(库里的一行至少要装一个钩子,否则绑定它等于绑空气),所以旧形态的「填名字 → 建空档」
 * 是一条**必然静默失败**的路径 —— 名称校验通过、创建返回 null、界面既不报错也不进编辑器。
 * 本测试逐点钉住新通路:草稿保存 = 建一行、就地升格为主键、再保存 = 原地更新,
 * 以及「编辑途中被删」必须报拒绝而不是悄悄新建一行覆盖。
 *
 * Robolectric 而非纯 JVM:[viewModelScope] 需要 Main 调度器。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileEditorDraftRoboTest {

    private fun draft(store: FakeSpoofProfileStore, name: String = "我的档") = ProfileEditorViewModel(
        profileId = "draft:$name",
        draftName = name,
        profiles = store,
        deviceCatalog = FakeDeviceCatalog(FakeDeviceCatalog.READY),
    )

    @Test
    fun draft_firstSave_createsExactlyOneRowAndPromotesTheDraft() {
        val store = FakeSpoofProfileStore()
        val vm = draft(store)

        assertTrue("草稿开局无主键", vm.isDraft)
        assertNull("草稿在库里没有对应行", vm.entry)

        vm.update(ProfileField.MODEL, "Pixel 7 Pro")
        vm.save()

        assertFalse("保存后升格为已落行", vm.isDraft)
        val rows = store.snapshot()
        assertEquals(1, rows.size)
        assertEquals("我的档", rows.first().name)
        assertEquals("Pixel 7 Pro", rows.first().profile.model)
        assertEquals(rows.first().id, vm.entry?.id)
        assertEquals(ProfileSaveResult.Saved, vm.lastSaveResult.value)
    }

    @Test
    fun draft_secondSave_updatesInPlaceInsteadOfAddingARow() {
        val store = FakeSpoofProfileStore()
        val vm = draft(store)
        vm.update(ProfileField.MODEL, "Pixel 7 Pro")
        vm.save()
        val createdId = requireNotNull(vm.entry).id

        vm.update(ProfileField.BRAND, "google")
        vm.save()

        val rows = store.snapshot()
        assertEquals("再保存不得多出一行", 1, rows.size)
        assertEquals(createdId, rows.first().id)
        assertEquals("google", rows.first().profile.brand)
    }

    @Test
    fun draft_withoutASingleParameter_cannotBeSavedAtAll() {
        val store = FakeSpoofProfileStore()
        val vm = draft(store)

        assertFalse("零生效项不构成可保存的画像", vm.view.value.canSave)
        vm.save()

        assertTrue("保存闸失守:库里被写入了空档", store.snapshot().isEmpty())
    }

    @Test
    fun existingProfile_saveUpdatesBodyAndKeepsStoredName() {
        val seeded = DeviceProfileEntry(
            id = "id-1",
            name = "旧名",
            summary = "旧摘要",
            profile = DeviceProfile(brand = "google", model = "Pixel 7 Pro"),
        )
        val store = FakeSpoofProfileStore(listOf(seeded))
        val vm = ProfileEditorViewModel(profileId = seeded.id, draftName = null, profiles = store, deviceCatalog = FakeDeviceCatalog(FakeDeviceCatalog.READY))

        assertFalse("已落行不是草稿", vm.isDraft)
        assertEquals("Pixel 7 Pro", vm.view.value.let { it.texts[ProfileField.MODEL] })

        vm.update(ProfileField.MODEL, "Pixel 8")
        vm.save()

        val row = store.snapshot().single()
        assertEquals("Pixel 8", row.profile.model)
        assertEquals("名称与摘要归画像库行编辑,本屏不得改写", "旧名", row.name)
        assertEquals("旧摘要", row.summary)
    }

    @Test
    fun rowDeletedMidEdit_saveIsRejectedAndNeverRecreatesARow() {
        val seeded = DeviceProfileEntry(
            id = "id-1",
            name = "会被删",
            summary = "",
            profile = DeviceProfile(model = "Pixel 7 Pro"),
        )
        val store = FakeSpoofProfileStore(listOf(seeded))
        val vm = ProfileEditorViewModel(profileId = seeded.id, draftName = null, profiles = store, deviceCatalog = FakeDeviceCatalog(FakeDeviceCatalog.READY))
        kotlinx.coroutines.runBlocking { store.delete(seeded.id) }

        vm.update(ProfileField.MODEL, "Pixel 9")
        vm.save()

        assertEquals(ProfileSaveResult.Rejected, vm.lastSaveResult.value)
        assertTrue("条目已删 ⇒ 不得静默新建一行(那是把删除当成没发生)", store.snapshot().isEmpty())
        // 拒绝态必须有**自己**的文案:此前它借用了「请填写名称」,在屏上说的是另一件事。
        assertNotEquals(
            profileSaveMessage(ProfileSaveResult.Saved),
            profileSaveMessage(ProfileSaveResult.Rejected),
        )
        assertNotEquals(R.string.profile_name_required, profileSaveMessage(ProfileSaveResult.Rejected))
    }
}
