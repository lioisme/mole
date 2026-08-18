package com.lioisme.modev.domain.server

import kotlinx.coroutines.flow.StateFlow

/**
 * 注入客户端连接监视(P5-1 包边界,2026-08-17):
 * UI 层只依赖本抽象,不触碰 [com.lioisme.modev.data.server.ConfigServer] 实现细节;
 * 实现位于 data.server(ConfigServer 实现本接口),单测可注入假实现。
 */
interface ConnectionMonitor {
    /** 当前已连接(握手成功)的注入客户端包名集合。 */
    val connectedPackagesFlow: StateFlow<Set<String>>
}