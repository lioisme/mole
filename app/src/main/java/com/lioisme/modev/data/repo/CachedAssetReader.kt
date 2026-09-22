package com.lioisme.modev.data.repo

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 随包 JSON 资产的「读一次、缓存住、失败冷却」闸(V48 抽出)。
 *
 * 存在的理由 = 现在真有两个同形状的读者:`devices.json`(约 1.9 MB / 8533 机型)与
 * `carriers.json`(134 KB / 1597 运营商)。把 [DeviceCatalogRepository] 原样再抄一份
 * 就会得到两处各自漂移的缓存/冷却/计数逻辑 —— 本文件收口的正是那三条口径:
 *
 * - **一次读盘 + 一次解析**:命中 [cached] 后不再触碰磁盘;
 * - **null 不缓存**:null 的含义是「现在读不到」而非「永远读不到」,故只记失败时刻,
 *   [retryCooldownMs] 窗口内直接返回 null(不缓存 null,冷却过了仍会重试);
 * - **[attempts] 计数**:让「不重复读盘」「不重试风暴」两条口径可被测试直接观测。
 *
 * 解码一律由调用方注入的 [parse] 承担(工程内每类资产各有一个唯一解码入口,
 * 如 `parseDeviceCatalog` / `parseCarrierCatalog`),本类不自建 Gson、不自判版本。
 */
internal class CachedAssetReader<T : Any>(
    private val assets: AssetManager,
    private val assetName: String,
    private val parse: (String) -> T?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryCooldownMs: Long = RETRY_COOLDOWN_MS,
) {

    @Volatile
    private var cached: T? = null

    @Volatile
    private var failedAt: Long? = null

    private val loadMutex = Mutex()

    /** 实际开文件的次数(缓存命中不计);自增只在 [loadMutex] 内发生故无需原子类。 */
    @Volatile
    var attempts: Int = 0
        private set

    /** 读资产;缺失 / IO 异常 / 形态不符一律收敛为 null(不抛)。 */
    suspend fun read(): T? {
        cached?.let { return it }
        return loadMutex.withLock<T?> {
            cached?.let { return@withLock it }
            val now = clock()
            val sinceFailure = failedAt?.let { now - it }
            if (sinceFailure != null && sinceFailure < retryCooldownMs) return@withLock null
            val loaded = withContext(Dispatchers.IO) { readOnce() }
            if (loaded == null) failedAt = now else cached = loaded
            loaded
        }
    }

    private fun readOnce(): T? = runCatching {
        attempts++
        assets.open(assetName).bufferedReader().use { reader -> parse(reader.readText()) }
    }.getOrNull()

    companion object {
        /** 失败重试冷却窗口(毫秒)。 */
        const val RETRY_COOLDOWN_MS = 5_000L
    }
}
