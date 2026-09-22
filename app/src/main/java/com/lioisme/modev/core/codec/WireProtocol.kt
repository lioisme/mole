package com.lioisme.modev.core.codec

/**
 * 注入进程 ↔ 宿主 App 的 TCP 行协议定义。
 *
 * 消息格式:单行 JSON,含 [KEY_TYPE] 与 [KEY_PAYLOAD] 两个顶层字段。
 * 注入侧为客户端,连接宿主 App 的 [PORT];宿主侧为服务端。
 *
 * ## v2 → v3 破坏性变更(2026-08,可靠性)
 * - `VERSION` 2 → 3;
 * - record payload 新增 `seq`(注入侧进程级单调序号);
 * - 新增宿主 → 注入 `ack` 消息(批量确认:seq 及之前的记录已持久化,注入侧据此删除写前日志行);
 * - hello 的 `packageName` 收敛为主包名(分离进程归并),`processName` 保留原值。
 * 双端同 APK 升级,无跨版本兼容诉求。
 *
 * ## v3 → v4 破坏性变更(2026-08-17,监听平台化,ADR-1)
 * - `VERSION` 3 → 4;
 * - record 语义泛型化:载荷为「类型化记录信封」([WireRecord]),`recordType` 标识监听能力
 *   (crypto / 未来 database / behavior / interaction…),`seq` 仍随载荷承载;
 * - 宿主侧经 [RecordCodecRegistry] 按 `recordType` 类型化分发,未知类型计数丢弃
 *   (不再按 CryptoRecord 形状硬校验),为多监听类型铺路;
 * - 记录载荷形状本身(字段/类型)不变,仅分发语义泛化。
 */
object WireProtocol {
    /** 注入通道默认端口(V39:1510 让位 MCP,wire 迁 1511;双端同 APK 常量单点,宿主自定义经 server_port 偏好重放)。 */
    const val PORT = com.lioisme.modev.core.model.ConfigKeys.WIRE_DEFAULT_PORT

    const val MAGIC = "MDEV"
    const val VERSION = 4

    // ---- 消息类型 ----
    /** 注入侧 → 宿主:握手 {magic, version, packageName, processName, token?} */
    const val TYPE_HELLO = "hello"

    /** 宿主 → 注入侧:配置推送 {packageName, switch} */
    const val TYPE_CONFIG = "config"

    /** 注入侧 → 宿主:类型化记录信封 {seq, recordType, …载荷字段} */
    const val TYPE_RECORD = "record"

    /** 宿主 → 注入侧:批量落库确认 {seq}(seq 及之前的记录已持久化) */
    const val TYPE_ACK = "ack"

    /** 双向心跳 */
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"

    // ---- 顶层字段 ----
    const val KEY_TYPE = "t"
    const val KEY_PAYLOAD = "p"

    /** hello payload 中的握手令牌(现代变体;legacy 不带) */
    const val KEY_TOKEN = "token"

    /** record payload 中的进程级单调序号 / ack payload 中的确认游标 */
    const val KEY_SEQ = "seq"

    /** hello payload 中的协议魔数与版本 */
    const val KEY_MAGIC = "magic"
    const val KEY_VERSION = "version"

    /** record payload 中的记录类型标识(监听能力类型,见 [com.lioisme.modev.core.model.RecordTypes])。 */
    const val KEY_RECORD_TYPE = "recordType"
}