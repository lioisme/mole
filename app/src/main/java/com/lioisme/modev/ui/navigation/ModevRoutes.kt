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
    /** 服务页(V39 拆分:wire/MCP 服务配置唯一入口,自「服务与诊断」分离)。 */
    const val SERVICES_SETTINGS = "settings/services"
    /** 诊断页(V39 拆分:框架/作用域/运行时统计/复制快照)。 */
    const val DIAGNOSTICS_SETTINGS = "settings/diagnostics"
    /** MCP 访问令牌管理页(V39:多令牌列表 + 认证开关)。 */
    const val MCP_TOKENS = "settings/mcp-tokens"
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
    /** 每应用网络环境监听记录列表(V30 一等公民)。 */
    const val NETWORK_RECORDS = "records/network/{packageName}"
    /** 每应用设备环境监听记录列表(V31 一等公民)。 */
    const val DEVICE_ENV_RECORDS = "records/device_env/{packageName}"
    /** 每应用 UI 交互监听记录列表(V33 一等公民)。 */
    const val UI_INTERACTION_RECORDS = "records/ui_interaction/{packageName}"
    /** 每应用系统信息监听记录列表(V33 一等公民)。 */
    const val SYSTEM_RECORDS = "records/system/{packageName}"
    /** 每应用进程控制监听记录列表(V33 一等公民)。 */
    const val PROCESS_CONTROL_RECORDS = "records/process_control/{packageName}"
    /**
     * 按类型全局记录列表(V40 T4 双维度浏览:类型 × 全部应用;
     * 数据管理页类型计数行入口,`records/type/{recordType}`)。
     */
    const val GLOBAL_TYPE_RECORDS = "records/type/{recordType}"
    /** 每应用监听设置页(V24 二态偏差)。 */
    const val APP_LISTEN_SETTINGS = "apps/{packageName}/listen-settings"
    /** 每应用功能设置页(V37 双域拆分:干预开关 + 关键词启用 + 画像选择)。 */
    const val APP_FEATURE_SETTINGS = "apps/{packageName}/feature-settings"
    /** 全局关键词库管理页(V37 T3.5)。 */
    const val KEYWORD_LIBRARY = "settings/keywords"
    /** 全局伪装画像库管理页(V37 T3.5 三常量单选页;V47 WP-5 改为可视化画像库列表)。 */
    const val SPOOF_PROFILES = "settings/profiles"
    /** 画像参数编辑器(V47 WP-5:41 项参数按形态落控件 + 实时生效预览)。 */
    const val PROFILE_EDITOR = "settings/profiles/{profileId}"

    /**
     * 画像草稿前缀(V47 WP-5):**库里没有对应行的新建态**。
     *
     * 新建必须走草稿而不是先落一行空画像 —— 画像库契约拒绝零字段条目
     * (`SpoofProfileService.create` 对空正文返回 null),且用户中途放弃不应
     * 在库里留下一行装不了任何钩子的垃圾。真实主键形态 = 32 位十六进制或
     * `builtin:<seedId>`,与本前缀不可能碰撞。
     */
    const val PROFILE_DRAFT_PREFIX = "draft:"

    /** 画像草稿编辑器路由(携带用户输入的名称)。 */
    fun profileDraft(name: String): String = "settings/profiles/${enc(PROFILE_DRAFT_PREFIX + name)}"

    /** 该主键是否草稿态(是则返回名称,否则 null)。 */
    fun draftNameOf(profileId: String): String? =
        profileId.substringAfter(PROFILE_DRAFT_PREFIX).takeIf { profileId.startsWith(PROFILE_DRAFT_PREFIX) }

    /** 全局检测黑名单追加页(V37 T3.5)。 */
    const val FEATURE_BLACKLIST = "settings/blacklist"

    fun appMonitor(packageName: String): String = "apps/${enc(packageName)}/monitor"

    fun appListenSettings(packageName: String): String = "apps/${enc(packageName)}/listen-settings"

    fun appFeatureSettings(packageName: String): String = "apps/${enc(packageName)}/feature-settings"

    fun algorithmRecords(packageName: String): String = "records/algorithm/${enc(packageName)}"

    fun databaseRecords(packageName: String): String = "records/database/${enc(packageName)}"

    fun behaviorRecords(packageName: String): String = "records/behavior/${enc(packageName)}"

    fun prefsRecords(packageName: String): String = "records/prefs/${enc(packageName)}"

    fun webviewRecords(packageName: String): String = "records/webview/${enc(packageName)}"

    fun networkRecords(packageName: String): String = "records/network/${enc(packageName)}"

    fun deviceEnvRecords(packageName: String): String = "records/device_env/${enc(packageName)}"

    fun uiInteractionRecords(packageName: String): String = "records/ui_interaction/${enc(packageName)}"

    fun systemRecords(packageName: String): String = "records/system/${enc(packageName)}"

    fun processControlRecords(packageName: String): String = "records/process_control/${enc(packageName)}"

    /** 按类型全局记录列表(wire 值作路径参数;非法值由目的地侧回落弹栈)。 */
    fun globalTypeRecords(recordType: String): String = "records/type/${enc(recordType)}"

    fun licenseDetail(index: Int): String = "settings/licenses/${index}"

    /** 画像参数编辑器路由(画像库主键含 `:` 前缀 ⇒ 一律 Uri.encode)。 */
    fun profileEditor(profileId: String): String = "settings/profiles/${enc(profileId)}"

    fun recordDetail(recordId: Long): String = "record/${enc(recordId.toString())}"

    // Uri.encode/decode 与 navigation-compose 的内部解码语义一致(%XX 转义,`+` 保留字面),
    // 取代 URLEncoder/URLDecoder(后者把空格编为 `+`,nav 解码后 `+` 无法还原)。
    fun enc(s: String) = Uri.encode(s)

    fun dec(s: String?) = Uri.decode(s ?: "")
}