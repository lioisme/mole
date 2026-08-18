package com.lioisme.modev.domain.capability

/**
 * 监听能力类型(V10 能力化架构;V14 R5 更名「算法监听」):算法监听为首个实现。
 * 未来行为监听、交互监听、数据库监听、文件监听等按本枚举扩展,
 * 宿主 UI 与路由按 [CapabilityKind] 泛化驱动,新增能力不改动主链路。
 */
enum class CapabilityKind {
    /** 算法监听(加解密/摘要/签名/MAC/密钥/编码等 JCA 调用)。 */
    ALGORITHM_LISTEN,
}
