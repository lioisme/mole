package com.lioisme.modev.ui.settings.profiles

import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileCodec
import com.lioisme.modev.core.model.ProfileImportOutcome
import com.lioisme.modev.core.model.ProfileResolution
import com.lioisme.modev.domain.config.SpoofProfileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 画像库契约的内存替身(V47 WP-5 测试夹具):校验口径与真仓储一致
 * —— 空名 / 零字段正文 / 非法正文一律拒绝,故「UI 能不能建出一行垃圾」
 * 在测试里与在库里是同一个答案。
 */
class FakeSpoofProfileStore(
    seed: List<DeviceProfileEntry> = emptyList(),
) : SpoofProfileService {

    private val list = MutableList(seed.size) { seed[it] }
    private var nextId = 1

    private val flow = MutableStateFlow<List<DeviceProfileEntry>>(list.toList())

    override val profiles: StateFlow<List<DeviceProfileEntry>> = flow

    /** 当前库存快照(断言用)。 */
    fun snapshot(): List<DeviceProfileEntry> = list.toList()

    private fun publish() {
        flow.value = list.toList()
    }

    override fun find(id: String?): DeviceProfileEntry? =
        id?.let { key -> list.firstOrNull { it.id == key } }

    override fun resolve(id: String?): ProfileResolution = when {
        id.isNullOrEmpty() -> ProfileResolution.Unbound
        else -> find(id)?.let { ProfileResolution.Bound(it) } ?: ProfileResolution.Missing(id)
    }

    override suspend fun create(
        name: String,
        summary: String,
        profile: DeviceProfile,
    ): DeviceProfileEntry? {
        if (name.isBlank() || profile.isEmpty || !ProfileCodec.isValid(profile)) return null
        return DeviceProfileEntry(
            id = "id-${nextId++}",
            name = name,
            summary = summary,
            profile = profile,
        ).also { list += it; publish() }
    }

    override suspend fun update(
        id: String,
        name: String,
        summary: String,
        profile: DeviceProfile,
    ): Boolean {
        val index = list.indexOfFirst { it.id == id }
        if (index < 0 || profile.isEmpty || !ProfileCodec.isValid(profile)) return false
        list[index] = list[index].copy(name = name, summary = summary, profile = profile)
        return true
    }

    override suspend fun duplicate(id: String, name: String): DeviceProfileEntry? =
        find(id)?.let { source ->
            if (name.isBlank()) return null
            DeviceProfileEntry(
                id = "id-${nextId++}",
                name = name,
                summary = source.summary,
                profile = source.profile,
            ).also { list += it; publish() }
        }

    override suspend fun delete(id: String): Boolean = list.removeAll { it.id == id }

    override suspend fun synchronizeBuiltins() = Unit

    override fun exportJson(ids: List<String>): String = "[]"

    override suspend fun importJson(
        text: String,
        dryRun: Boolean,
        defaultName: String,
    ): ProfileImportOutcome = throw AssertionError("unused by these tests")
}
