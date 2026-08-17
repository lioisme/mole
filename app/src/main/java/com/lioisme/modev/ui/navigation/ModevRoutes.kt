package com.lioisme.modev.ui.navigation

import android.net.Uri

/**
 * 路由常量与参数编解码单一事实源(2026-08-16 V4 收敛为五界面):
 * APPS(根页) → ALGORITHM_LIST(每应用记录)→ RECORD_DETAIL(每算法);
 * ALGORITHM_LIST → CAPTURE_SETTINGS(每应用捕获设置);SETTINGS 全局。
 * 路由字符串(apps/{packageName}、apps/{packageName}/capture、record/{recordId})自 V3 起保持稳定。
 */
object ModevRoutes {
    const val APPS = "apps"
    const val ALGORITHM_LIST = "apps/{packageName}"
    const val CAPTURE_SETTINGS = "apps/{packageName}/capture"
    const val RECORD_DETAIL = "record/{recordId}"
    const val SETTINGS = "settings"

    fun algorithmList(packageName: String): String = "apps/${enc(packageName)}"

    fun captureSettings(packageName: String): String = "apps/${enc(packageName)}/capture"

    fun recordDetail(recordId: Long): String = "record/${enc(recordId.toString())}"

    // Uri.encode/decode 与 navigation-compose 的内部解码语义一致(%XX 转义,`+` 保留字面),
    // 取代 URLEncoder/URLDecoder(后者把空格编为 `+`,nav 解码后 `+` 无法还原)。
    fun enc(s: String) = Uri.encode(s)

    fun dec(s: String?) = Uri.decode(s ?: "")
}
