package com.lioisme.modev.hook

import com.lioisme.modev.core.util.BytesView
import java.util.Collections
import java.util.WeakHashMap

/**
 * 以对象实例为键关联算法上下文,把 getInstance → init → update → doFinal 串成一条完整链路。
 * 弱引用映射,不阻止 GC,天然防泄漏。
 */
class CryptoContextTracker {

    private val contexts = Collections.synchronizedMap(WeakHashMap<Any, CryptoContext>())

    fun contextFor(instance: Any): CryptoContext =
        contexts.getOrPut(instance) { CryptoContext() }

    fun remove(instance: Any) {
        contexts.remove(instance)
    }

    companion object {
        /** 对象标识,用于跨记录关联(宿主侧可做分组)。 */
        fun identity(instance: Any): Long = System.identityHashCode(instance).toLong()
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

    /** update 阶段累计输入字节数(不保留字节,防内存膨胀)。 */
    var inputTotal: Long = 0L
    var outputTotal: Long = 0L

    /** 最近一次 verify 结果。 */
    var verified: Boolean? = null
}