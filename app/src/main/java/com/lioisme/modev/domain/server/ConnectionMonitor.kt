package com.lioisme.modev.domain.server

import kotlinx.coroutines.flow.StateFlow

/**
 * 注入客户端连接监视(P5-1 包边界,2026-08-17):
 * UI 层只依赖本抽象,不触碰 [com.lioisme.modev.data.server.ConfigServer] 实现细节;
 * 实现位于 data.server(ConfigServer 实现本接口),单测可注入假实现。
 */
interface ConnectionMonitor {
    companion object {
        /** 回环绑定(V26 前的唯一行为,保持默认语义)。 */
        const val BIND_LOOPBACK = "127.0.0.1"

        /** 全接口绑定(外部访问开启时;需用户显式开启,默认停用)。 */
        const val BIND_ALL_INTERFACES = "0.0.0.0"
    }

    /** 当前已连接(握手成功)的注入客户端包名集合。 */
    val connectedPackagesFlow: StateFlow<Set<String>>

    /**
     * 当前监听绑定地址(V26 外部访问):[BIND_LOOPBACK] / [BIND_ALL_INTERFACES];
     * 未绑定(服务未启动/重绑退避中)= null。诊断页展示消费。
     */
    val boundAddress: StateFlow<String?>

    /**
     * 最近一次绑定失败原因(null = 无错误 / 绑定成功清空)。
     * V45 ISO-6:原为 wire 独有 + UI 直读 `McpServer` 具体类属性,现入契约由
     * 双服务共同实现(服务页对称错误行,AGENTS #29)。
     */
    val bindError: StateFlow<String?>
}
