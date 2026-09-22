package com.lioisme.modev.data.repo

import android.content.res.AssetManager
import com.lioisme.modev.core.model.CarrierEntry
import com.lioisme.modev.core.model.parseCarrierCatalog
import com.lioisme.modev.domain.config.CarrierCatalogService

/** 随包 `carriers.json` 的唯一读者;三条读盘口径与缓存全在 [CachedAssetReader] 内。 */
internal class CarrierCatalogRepository(
    assets: AssetManager,
    clock: () -> Long = System::currentTimeMillis,
    assetName: String = ASSET_NAME,
) : CarrierCatalogService {

    private val reader = CachedAssetReader(assets, assetName, ::parseCarrierCatalog, clock)

    override suspend fun carriers(): List<CarrierEntry>? = reader.read()

    internal val readAttempts: Int get() = reader.attempts

    companion object {
        const val ASSET_NAME = "carriers.json"
    }
}
