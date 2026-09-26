package com.lioisme.modev.data.repo

import android.content.res.AssetManager
import com.lioisme.modev.core.model.DeviceCatalog
import com.lioisme.modev.core.model.parseDeviceCatalog
import com.lioisme.modev.domain.config.DeviceCatalogService

/**
 * 真机目录仓库:随包 `devices.json` 的**唯一读者**,实现 `domain.config` 的
 * [DeviceCatalogService] 契约(容器 public 出口只露接口,G2 闸;实现类型仅装配可见)。
 *
 * V48 起「读一次 + 缓存 + 失败冷却 + 计数」四条口径收口在 [CachedAssetReader]
 * (与运营商目录共用);本类只保留资产名与解码入口这两件机型目录自己的事。
 * null 的语义见 [DeviceCatalogService](读不到 ≠ 空目录,禁回退 EMPTY)。
 */
internal class DeviceCatalogRepository(
    assets: AssetManager,
    clock: () -> Long = System::currentTimeMillis,
    assetName: String = ASSET_NAME,
) : DeviceCatalogService {

    private val reader = CachedAssetReader(assets, assetName, ::parseDeviceCatalog, clock)

    override suspend fun catalog(): DeviceCatalog? = reader.read()

    /** 实际开文件次数(缓存命中不计)—— 测试与诊断读它,业务读 [catalog]。 */
    internal val readAttempts: Int get() = reader.attempts

    companion object {
        /** 随包资产名(与 `app/src/main/assets` 同名)。 */
        const val ASSET_NAME = "devices.json"

        /** 失败重试冷却窗口(毫秒)。 */
        const val RETRY_COOLDOWN_MS = CachedAssetReader.RETRY_COOLDOWN_MS
    }
}
