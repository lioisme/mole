package com.lioisme.modev.domain.capability

/**
 * 监听能力类型(V10 能力化架构;V14 R5 更名「算法监听」;V17 数据库监听扩展)。
 * 未来行为监听、交互监听、文件监听等按本枚举扩展,
 * 宿主 UI 与路由按 [CapabilityKind] 泛化驱动,新增能力不改动主链路。
 */
enum class CapabilityKind {
    /** 算法监听(加解密/摘要/签名/MAC/密钥/编码等 JCA 调用)。 */
    ALGORITHM_LISTEN,

    /** 数据库监听(SQLite 打开/关闭/增删改查/事务/绑定参数/结果集)。 */
    DATABASE_LISTEN,

    /** 行为监听(文件系统读/写/删/重命名/列目录、Assets 资源、Shell 命令,V27 一等公民)。 */
    BEHAVIOR_LISTEN,
}
