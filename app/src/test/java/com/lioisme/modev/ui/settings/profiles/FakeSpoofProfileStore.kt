package com.lioisme.modev.ui.settings.profiles

import com.lioisme.modev.core.model.CarrierEntry
import com.lioisme.modev.core.model.DeviceBrandEntry
import com.lioisme.modev.core.model.DeviceCatalog
import com.lioisme.modev.core.model.DeviceModelEntry
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileCodec
import com.lioisme.modev.core.model.ProfileImportOutcome
import com.lioisme.modev.core.model.ProfileResolution
import com.lioisme.modev.domain.config.CarrierCatalogService
import com.lioisme.modev.domain.config.DeviceCatalogService
import com.lioisme.modev.domain.config.SpoofProfileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 机型库替身:替身自带一份三档真机身份的目录,足以驱动选择路径
 * (品牌 → 型号 → 一次写全 BRAND/MANUFACTURER/MODEL/DEVICE)。
 * [result] = null 复刻「资产读不到」的形态,那条路必须与「目录为空」分别可测。
 */
class FakeDeviceCatalog(
    private val result: DeviceCatalog?,
) : DeviceCatalogService {
    override suspend fun catalog(): DeviceCatalog? = result

    companion object {
        /** 两档真机身份:够驱动「选品牌 → 选型号 → 一次写全四项」整条路径。 */
        val READY = DeviceCatalog(
            brands = listOf(
                DeviceBrandEntry(
                    key = "google",
                    displayName = "Google",
                    buildBrand = "google",
                    manufacturer = "Google",
                    models = listOf(
                        DeviceModelEntry(model = "Pixel 7 Pro", name = "", device = "raven", type = "mob"),
                        DeviceModelEntry(model = "Pixel 8", name = "", device = "tokay", type = "mob"),
                    ),
                ),
                DeviceBrandEntry(
                    key = "xiaomi",
                    displayName = "Xiaomi",
                    buildBrand = "Xiaomi",
                    manufacturer = "Xiaomi",
                    models = listOf(
                        DeviceModelEntry(model = "2211133C", name = "Xiaomi 13", device = "fuxi", type = "mob"),
                    ),
                ),
            ),
        )
    }
}

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

/**
 * 运营商库替身。两条自带项正好各自钉住一条裁决:
 * `CN` 是国家码合法形态(三字段一次写全),`AU/CC/CX` 是数据集里真实存在的多地区串
 * (必须**只留空国家码**,不拆不猜)。[result] = null 复刻「资产读不到」,
 * 与「目录为空」分列可测。
 */
class FakeCarrierCatalog(private val result: List<CarrierEntry>?) : CarrierCatalogService {

    override suspend fun carriers(): List<CarrierEntry>? = result

    companion object {
        val READY = listOf(
            CarrierEntry(name = "China Mobile", plmn = "46000", countryCode = "CN", countryName = "China"),
            CarrierEntry(name = "Telstra", plmn = "50501", countryCode = "AU/CC/CX", countryName = "Australia"),
        )
    }
}
