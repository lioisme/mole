package com.lioisme.modev.core.codec

import com.google.gson.JsonObject

/**
 * 类型化记录信封(v4,ADR-1):宿主侧分发的最小单元。
 *
 * - [seq]:注入侧进程级单调序号(ACK 游标依据,与记录类型解耦);
 * - [recordType]:监听能力类型标识(见 [com.lioisme.modev.core.model.RecordTypes]),
 *   由载荷自身承载([WireProtocol.KEY_RECORD_TYPE]),编码侧强制统一;
 * - [payload]:类型化载荷原始 JSON,由宿主 [RecordCodecRegistry] 按类型解码。
 *
 * 新增监听类型 = 定义载荷模型 + 注册解码器,协议/传输/存储管线零改动。
 */
data class WireRecord(
    val seq: Long,
    val recordType: String,
    val payload: JsonObject,
)