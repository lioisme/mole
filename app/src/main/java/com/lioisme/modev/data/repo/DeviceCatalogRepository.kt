package com.lioisme.modev.data.repo

import android.content.res.AssetManager
import com.lioisme.modev.core.model.DeviceCatalog
import com.lioisme.modev.core.model.parseDeviceCatalog
import com.lioisme.modev.domain.config.DeviceCatalogService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 真机目录仓库(V47):随包 `devices.json` 的**唯一读者**,实现 `domain.config` 的
 * [DeviceCatalogService] 契约(容器 public 出口只露接口,G2 闸;实现类型仅装配可见)。
 *
 * 三条口径:
 * - **一次读盘 + 一次解析**:资产约 1.9 MB / 8533 条机型记录,选择器逐键过滤时重复读文件
 *   纯属浪费 ⇒ 成功结果落 [cached] 后不再触碰磁盘(`@Volatile` + [loadMutex] 双检,
 *   沿用 `SpoofProfileRepository.synchronizeBuiltins` 的幂等闸形态)。
 * - **失败不伪装成功,但记冷却**:读不出 / 解不开返回 null(不抛),并记下失败时刻,
 *   [RETRY_COOLDOWN_MS] 窗口内直接返回 null 不再重开文件 —— 没有这道闸,资产真缺失时
 *   每次重组都会重试一次必败 IO;有这道闸也仍是「重试」语义而非缓存 null,
 *   因为 null 的含义是「现在读不到」(见契约 KDoc),不是「永远读不到」。
 * - 解码一律经 [parseDeviceCatalog](唯一解码入口,含版本闸),本类不自建 Gson、不自判版本。
 */
internal class DeviceCatalogRepository(
    private val assets: AssetManager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val assetName: String = ASSET_NAME,
) : DeviceCatalogService {

    @Volatile
    private var cached: DeviceCatalog? = null

    @Volatile
    private var failedAt: Long? = null

    private val loadMutex = Mutex()

    /**
     * 实际开文件的次数(缓存命中不计)—— 「不重复读盘」与「失败不重试风暴」两条口径的
     * 直接观测面;自增只在 [loadMutex] 内发生故无需原子类。测试与诊断读它,业务读 [catalog]。
     */
    @Volatile
    private var attempts: Int = 0

    internal val readAttempts: Int get() = attempts

    override suspend fun catalog(): DeviceCatalog? {
        cached?.let { return it }
        return loadMutex.withLock<DeviceCatalog?> {
            cached?.let { return@withLock it }
            val now = clock()
            val sinceFailure = failedAt?.let { now - it }
            if (sinceFailure != null && sinceFailure < RETRY_COOLDOWN_MS) return@withLock null
            val loaded = withContext(Dispatchers.IO) { readOnce() }
            if (loaded == null) failedAt = now else cached = loaded
            loaded
        }
    }

    /** 读盘 + 解码;资产缺失(FileNotFoundException)/ IO 异常 / JSON 破损一律收敛为 null。 */
    private fun readOnce(): DeviceCatalog? = runCatching {
        attempts++
        assets.open(assetName).bufferedReader().use { reader -> parseDeviceCatalog(reader.readText()) }
    }.getOrNull()

    companion object {
        /** 随包资产名(与 `app/src/main/assets` 同名)。 */
        const val ASSET_NAME = "devices.json"

        /** 失败重试冷却窗口(毫秒)。 */
        const val RETRY_COOLDOWN_MS = 5_000L
    }
}
