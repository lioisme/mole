package com.lioisme.modev.ui.detail.detail

import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.WebViewRecord

/**
 * 详情锚点导航(V28 纯 Kotlin 化,原 FilterChip 行退役):
 * 锚点清单由纯函数按记录实际内容动态生成,同一份清单同时驱动
 * markdown 目录(TOC)条目与屏幕装配侧的 LazyColumn 索引簿记——
 * 「目录可见性」与「可滚动性」由同一数据源保证,永不失配。
 */

/** 锚点:key = LazyColumn item 索引簿记键(与屏幕装配侧严格同源);labelRes 复用区块标题资源。 */
data class DetailAnchor(val key: String, val labelRes: Int)

/** 算法记录锚点:数据流(双侧齐备)/ 密钥材料(任一字段)/ 参数 / 调用栈。 */
fun cryptoAnchors(record: CryptoRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.input != null && record.output != null) {
        anchors += DetailAnchor("flow", R.string.section_zone_data_flow)
    }
    val hasKeyMaterial = record.key != null || record.keyAlias != null || record.iv != null ||
        record.salt != null || record.aad != null || record.publicKey != null
    if (hasKeyMaterial) anchors += DetailAnchor("keys", R.string.section_zone_key_material)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    return anchors
}

/** SQL 记录锚点:语句 / 路径 / 绑定参数 / 结果行 / 结果文本 / 参数 / 调用栈。 */
fun sqlAnchors(record: SqlRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.sql != null) anchors += DetailAnchor("sql", R.string.section_sql)
    if (record.dbPath != null) anchors += DetailAnchor("dbpath", R.string.section_db_path)
    if (!record.bindArgs.isNullOrEmpty()) anchors += DetailAnchor("bindargs", R.string.section_bind_args)
    if (!record.rows.isNullOrEmpty()) anchors += DetailAnchor("rows", R.string.section_result_rows)
    if (record.resultText != null) anchors += DetailAnchor("result", R.string.section_result_text)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    return anchors
}

/** 行为记录锚点:命令 / 内容预览 / 参数 / 调用栈(按存在性)。 */
fun behaviorAnchors(record: BehaviorRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (!record.command.isNullOrEmpty()) anchors += DetailAnchor("command", R.string.section_command)
    if (record.content != null) anchors += DetailAnchor("content", R.string.section_content)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    return anchors
}

/** 偏好记录锚点:值 / 参数 / 调用栈(按存在性)。 */
fun prefsAnchors(record: PrefsRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.value != null) anchors += DetailAnchor("value", R.string.section_value)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    return anchors
}

/** WebView 记录锚点:脚本 / 参数 / 调用栈(按存在性)。 */
fun webviewAnchors(record: WebViewRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.script != null) anchors += DetailAnchor("script", R.string.section_script)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    return anchors
}

/** 网络环境监听记录锚点。 */
fun networkAnchors(record: NetworkRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    if (!record.requestHeaders.isNullOrEmpty()) anchors += DetailAnchor("req-headers", R.string.section_req_headers)
    if (!record.responseHeaders.isNullOrEmpty()) anchors += DetailAnchor("resp-headers", R.string.section_resp_headers)
    return anchors
}

/** 设备环境监听记录锚点。 */
fun deviceEnvAnchors(record: DeviceEnvRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    return anchors
}
