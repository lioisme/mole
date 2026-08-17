package com.lioisme.modev.ui.navigation

import android.net.Uri

/**
 * 路由常量与参数编解码单一事实源(2026-08-17 V10 设置分级):
 * APPS(根页) → APP_RECORDS(每应用记录)→ RECORD_DETAIL(每条记录);
 * SETTINGS → CAPTURE_SETTINGS(全局捕获)/ DATA_SETTINGS(数据管理)/
 * DIAGNOSTICS_SETTINGS(服务与诊断)。
 * 路由字符串(apps/{packageName}、record/{recordId})自 V3 起保持稳定;
 * 捕获设置自 V10 起全局化,路由由 apps/{packageName}/capture 迁移为 settings/capture。
 */
object ModevRoutes {
    const val APPS = "apps"
    const val APP_RECORDS = "apps/{packageName}"
    const val RECORD_DETAIL = "record/{recordId}"
    const val SETTINGS = "settings"
    const val CAPTURE_SETTINGS = "settings/capture"
    const val DATA_SETTINGS = "settings/data"
    const val DIAGNOSTICS_SETTINGS = "settings/diagnostics"

    fun appRecords(packageName: String): String = "apps/${enc(packageName)}"

    fun recordDetail(recordId: Long): String = "record/${enc(recordId.toString())}"

    // Uri.encode/decode 与 navigation-compose 的内部解码语义一致(%XX 转义,`+` 保留字面),
    // 取代 URLEncoder/URLDecoder(后者把空格编为 `+`,nav 解码后 `+` 无法还原)。
    fun enc(s: String) = Uri.encode(s)

    fun dec(s: String?) = Uri.decode(s ?: "")
}
