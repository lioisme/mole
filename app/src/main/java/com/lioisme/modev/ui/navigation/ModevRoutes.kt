package com.lioisme.modev.ui.navigation

import android.net.Uri

/**
 * 路由常量与参数编解码单一事实源(2026-08-17 V10 设置分级;
 * 2026-08-18 V14 R5 更名;V18 应用监听入口;V19 T4 监听设置合并;
 * V20 记录列表单应用语境化,「查看记录」界面退役;
 * V21 T3 监听设置拆分还原;V22 R2 新增关于页 / 开源许可页;
 * V24 G2 全部记录页(records/all)退役,记录浏览收敛为每应用两列表;
 * V25 全局监听设置两页(settings/algorithm、settings/database)退役 ——
 * 默认配置收敛为内置推荐值,监听配置唯一入口 = 每应用「监听设置」):
 * APPS(根页) → APP_MONITOR(每应用监听入口:算法记录 / 数据库记录)
 * → RECORD_DETAIL(每条记录);
 * SETTINGS(通用设置)→ DATA_SETTINGS(数据管理)/ DIAGNOSTICS_SETTINGS(服务与诊断)
 * / ABOUT(关于)→ LICENSES(开源许可)→ LICENSE_DETAIL(许可全文)。
 * 路由字符串(apps/{packageName}、record/{recordId})自 V3 起保持稳定;
 * records/algorithm、records/database 于 V20 带包名参数化(单应用记录列表);
 * settings/algorithm、settings/database 于 V19 合并为 settings/monitor,
 * V21 拆分还原,V25 随「无全局监听配置」决策整体退役。
 */
object ModevRoutes {
    const val APPS = "apps"
    const val APP_MONITOR = "apps/{packageName}/monitor"
    const val RECORD_DETAIL = "record/{recordId}"
    const val SETTINGS = "settings"
    const val DATA_SETTINGS = "settings/data"
    const val DIAGNOSTICS_SETTINGS = "settings/diagnostics"
    const val ABOUT = "settings/about"
    const val LICENSES = "settings/licenses"
    const val LICENSE_DETAIL = "settings/licenses/{index}"
    const val ALGORITHM_RECORDS = "records/algorithm/{packageName}"
    const val DATABASE_RECORDS = "records/database/{packageName}"
    /** 每应用行为记录列表(V27 一等公民)。 */
    const val BEHAVIOR_RECORDS = "records/behavior/{packageName}"
    /** 每应用偏好记录列表(V28 一等公民)。 */
    const val PREFS_RECORDS = "records/prefs/{packageName}"
    /** 每应用 WebView 记录列表(V29 一等公民)。 */
    const val WEBVIEW_RECORDS = "records/webview/{packageName}"
    /** 每应用监听设置页(V24 二态偏差)。 */
    const val APP_LISTEN_SETTINGS = "apps/{packageName}/listen-settings"

    fun appMonitor(packageName: String): String = "apps/${enc(packageName)}/monitor"

    fun appListenSettings(packageName: String): String = "apps/${enc(packageName)}/listen-settings"

    fun algorithmRecords(packageName: String): String = "records/algorithm/${enc(packageName)}"

    fun databaseRecords(packageName: String): String = "records/database/${enc(packageName)}"

    fun behaviorRecords(packageName: String): String = "records/behavior/${enc(packageName)}"

    fun prefsRecords(packageName: String): String = "records/prefs/${enc(packageName)}"

    fun webviewRecords(packageName: String): String = "records/webview/${enc(packageName)}"

    fun licenseDetail(index: Int): String = "settings/licenses/${index}"

    fun recordDetail(recordId: Long): String = "record/${enc(recordId.toString())}"

    // Uri.encode/decode 与 navigation-compose 的内部解码语义一致(%XX 转义,`+` 保留字面),
    // 取代 URLEncoder/URLDecoder(后者把空格编为 `+`,nav 解码后 `+` 无法还原)。
    fun enc(s: String) = Uri.encode(s)

    fun dec(s: String?) = Uri.decode(s ?: "")
}