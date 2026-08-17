package com.lioisme.modev.domain.capability

import java.util.concurrent.ConcurrentHashMap

/**
 * 能力注册表:进程级单一集合,宿主按 [CapabilityKind] 查询/枚举。
 * 注册幂等(同 kind 后注册覆盖);线程安全(ConcurrentHashMap)。
 * 设置页「功能」区与能力设置路由以此为驱动源。
 */
class CapabilityRegistry {

    private val capabilities = ConcurrentHashMap<CapabilityKind, Capability>()

    fun register(capability: Capability) {
        capabilities[capability.kind] = capability
    }

    /** 已注册能力,按声明顺序(枚举序)稳定输出。 */
    fun all(): List<Capability> = capabilities.values.sortedBy { it.kind.ordinal }

    fun get(kind: CapabilityKind): Capability? = capabilities[kind]

    fun isRegistered(kind: CapabilityKind): Boolean = capabilities.containsKey(kind)
}
