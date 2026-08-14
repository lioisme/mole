package com.lioisme.modev.core.codec

/**
 * 注入进程 ↔ 宿主 App 的 TCP 行协议定义。
 *
 * 消息格式:单行 JSON,含 [KEY_TYPE] 与 [KEY_PAYLOAD] 两个顶层字段。
 * 注入侧为客户端,连接宿主 App 的 [PORT];宿主侧为服务端。
 */
object WireProtocol {
    const val PORT = 46299

    const val MAGIC = "MDEV"
    const val VERSION = 1

    // ---- 消息类型 ----
    /** 注入侧 → 宿主:握手 {magic, version, packageName, processName} */
    const val TYPE_HELLO = "hello"

    /** 宿主 → 注入侧:配置推送 {packageName, switch} */
    const val TYPE_CONFIG = "config"

    /** 注入侧 → 宿主:算法记录 {record} */
    const val TYPE_RECORD = "record"

    /** 双向心跳 */
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"

    // ---- 顶层字段 ----
    const val KEY_TYPE = "t"
    const val KEY_PAYLOAD = "p"
}