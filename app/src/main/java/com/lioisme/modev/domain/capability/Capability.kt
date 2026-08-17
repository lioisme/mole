package com.lioisme.modev.domain.capability

import kotlinx.coroutines.flow.StateFlow

/**
 * 监听能力抽象:Modev 的能力面(算法捕获 / 未来行为监听、交互监听、数据库监听、
 * 文件监听等)统一经本接口暴露。
 *
 * - 宿主侧:设置页「功能」区、能力设置路由均由 [CapabilityRegistry] 驱动;
 * - 注入侧:能力负责装配自身的钩子/采集逻辑(算法捕获对应 HookManager 注册表)。
 *
 * 新增能力 = 实现本接口 + 注册进 [CapabilityRegistry],不改动导航/设置/通道主链路。
 */
interface Capability {

    /** 能力类型(注册表键)。 */
    val kind: CapabilityKind

    /** 能力是否处于启用状态(能力内部聚合开关的领域投影)。 */
    val enabled: StateFlow<Boolean>
}
