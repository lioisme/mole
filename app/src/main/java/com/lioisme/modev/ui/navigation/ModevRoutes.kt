package com.lioisme.modev.ui.navigation

import android.net.Uri

/**
 * 路由常量与参数编解码单一事实源(2026-08-17 V10 设置分级;
 * 2026-08-18 V14 R5 更名;V18 应用监听入口;V19 T4 监听设置合并;
 * V20 记录列表单应用语境化,「查看记录」界面退役):
 * APPS(根页) → APP_MONITOR(每应用监听入口:算法记录 / 数据库记录)
 * → RECORD_DETAIL(每条记录);
 * SETTINGS → MONITOR_SETTINGS(监听设置,算法/数据库合并单页)/ DATA_SETTINGS
 * (数据管理)/ DIAGNOSTICS_SETTINGS(服务与诊断)。
 * 路由字符串(apps/{packageName}、record/{recordId})自 V3 起保持稳定;
 * records/algorithm、records/database 于 V20 带包名参数化(单应用记录列表);
 * settings/algorithm、settings/database 于 V19 合并为 settings/monitor
 * (旧两路由无存量引用)。
 */
object ModevRoutes {
    const val APPS = "apps"
    const val APP_MONITOR = "apps/{packageName}/monitor"
    const val RECORD_DETAIL = "record/{recordId}"
    const val SETTINGS = "settings"
    const val MONITOR_SETTINGS = "settings/monitor"
    const val DATA_SETTINGS = "settings/data"
    const val DIAGNOSTICS_SETTINGS = "settings/diagnostics"
    const val ALGORITHM_RECORDS = "records/algorithm/{packageName}"
    const val DATABASE_RECORDS = "records/database/{packageName}"

    fun appMonitor(packageName: String): String = "apps/${enc(packageName)}/monitor"

    fun algorithmRecords(packageName: String): String = "records/algorithm/${enc(packageName)}"

    fun databaseRecords(packageName: String): String = "records/database/${enc(packageName)}"

    fun recordDetail(recordId: Long): String = "record/${enc(recordId.toString())}"

    // Uri.encode/decode 与 navigation-compose 的内部解码语义一致(%XX 转义,`+` 保留字面),
    // 取代 URLEncoder/URLDecoder(后者把空格编为 `+`,nav 解码后 `+` 无法还原)。
    fun enc(s: String) = Uri.encode(s)

    fun dec(s: String?) = Uri.decode(s ?: "")
}