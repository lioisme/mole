package com.lioisme.modev.hook

import com.lioisme.modev.core.util.BytesView
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 以对象实例为键关联算法上下文,把 getInstance → init → update → doFinal 串成一条完整链路。
 *
 * - 实例标识使用全局自增唯一 ID(替代 identityHashCode,消除跨代碰撞);
 * - 弱引用映射,不阻止 GC,天然防泄漏;
 * - 同一 WeakHashMap 承载 [contextFor]/[identity],一次查表两者复用。
 * - 单例:每个注入进程仅有一个跟踪器(HookManager 装配)。
 */
object CryptoContextTracker {

    private class Entry(val id: Long, val context: CryptoContext)

    private val entries = Collections.synchronizedMap(WeakHashMap<Any, Entry>())
    private val idCounter = AtomicLong(1)

    fun contextFor(instance: Any): CryptoContext =
        entries.getOrPut(instance) { Entry(idCounter.getAndIncrement(), CryptoContext()) }.context

    /** 实例存活期唯一标识,用于跨记录关联。 */
    fun identity(instance: Any): Long =
        entries.getOrPut(instance) { Entry(idCounter.getAndIncrement(), CryptoContext()) }.id

    /**
     * clone()/copy() 上下文传播:新实例继承算法/provider/密钥/IV/salt/AAD/参数等,标识重新分配。
     * BytesView 为不可变对象,可直接共享。
     */
    fun cloneContext(from: Any, to: Any) {
        val src = entries[from]?.context ?: return
        val copy = CryptoContext().apply {
            algorithm = src.algorithm
            provider = src.provider
            direction = src.direction
            key = src.key
            keyAlias = src.keyAlias
            keyExportable = src.keyExportable
            iv = src.iv
            salt = src.salt
            aad = src.aad
            aadData = src.aadData
            publicKey = src.publicKey
            gcmTagLen = src.gcmTagLen
            iterationCount = src.iterationCount
            keyLength = src.keyLength
            keysize = src.keysize
            lastInitIv = src.lastInitIv
            verified = src.verified
        }
        entries[to] = Entry(idCounter.getAndIncrement(), copy)
    }

    /** reset():重置累计量,保留密钥等初始化信息。 */
    fun reset(instance: Any) {
        entries[instance]?.context?.let {
            it.inputTotal = 0L
            it.outputTotal = 0L
            it.aadData = null
            it.aad = null
        }
    }

    fun remove(instance: Any) {
        entries.remove(instance)
    }
}

/**
 * 单个算法实例的累计上下文。
 */
class CryptoContext {
    var algorithm: String = ""
    var provider: String? = null
    var direction: Int? = null
    var key: BytesView? = null
    var keyAlias: String? = null
    var keyExportable: Boolean = true
    var iv: BytesView? = null
    var salt: BytesView? = null
    var aad: BytesView? = null

    /** AAD 累计原始字节(updateAAD 多次调用合并)。 */
    var aadData: ByteArray? = null
    var publicKey: BytesView? = null
    var gcmTagLen: Int? = null
    var iterationCount: Int? = null
    var keyLength: Int? = null
    var keysize: Int? = null

    /** init 阶段显式传入或由实现自动生成的 IV(供 Cipher.getIV 自动补采)。 */
    var lastInitIv: BytesView? = null

    /** update 阶段累计输入字节数(不保留字节,防内存膨胀)。 */
    var inputTotal: Long = 0L
    var outputTotal: Long = 0L

    /** 最近一次 verify 结果。 */
    var verified: Boolean? = null
}