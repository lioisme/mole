package com.lioisme.modev.core.model

/*
 * 能力表(V48 R-2):把后端 81 个类别投影成「用户在哪儿改它、在哪儿看它、谁发射它」。
 *
 * 本文件**不是**第二份类别清单 —— 行集合恒由 [CategoryTaxonomy.all] 派生,功能域分区恒由
 * [FeatureGroups] 派生,新增/删除类别都不需要动这里。它只登记两样分类法本身没有携带的事实:
 *  1. [Backstage] 每记录类型对应的注入侧探针、记录列表路由常量、管辖它的 master 位名;
 *  2. 跨域闸规则(见 [crossDomainListen]) —— V48-DFC-03 的根因:一个记录类型内两域混居时,
 *     监听域类别画在监听页,而管着它的 master 只画在功能页,用户勾选后什么都不发生。
 *
 * 消费方:`CapabilityMapTest`(门禁)、监听页跨域提示、AGENTS 强约束 #31 的分类表。
 * core 子树零 `android.*`(AGENTS #18),故分区与路由一律存**资源名/常量名**,由 UI 侧解析。
 */

/** 一个类别的用户可见落点。 */
data class CapabilityRow(
    /** 类别 id(= 对应枚举名,与 `CategoryTaxonomy.id` 同值)。 */
    val kind: String,

    /** 所属记录类型。 */
    val type: RecordType,

    /** 能力域(取自分类法,本表不自行判定)。 */
    val domain: CapabilityDomain,

    /** 所在分区标题的 `R.string` 键名(功能域 = 其 `FeatureGroups` 组;监听域 = 类型分区或环境监听)。 */
    val sectionKey: String,

    /** 管辖该类别的 master 位名(`HookSwitch` 属性名)。 */
    val master: String,

    /** 该类型记录列表的路由常量名(`ModevRoutes` 字段名)。 */
    val routeKey: String,

    /** 发射该类别记录的注入侧探针类名。 */
    val probe: String,
)

/** 表内唯一随记录类型变化的一对多事实(10 类型 → 10 master / 10 路由 / 10 探针)。 */
private data class Backstage(
    val master: String,
    val routeKey: String,
    val probe: String,
    val listenSection: String,
)

/** 全表(顺序 = `CategoryTaxonomy.all()`:按类型、类型内按 order)。 */
private val capabilityRows: List<CapabilityRow> = run {
    val backstage: Map<RecordType, Backstage> =
        mapOf(
            RecordType.CRYPTO to Backstage("cryptoListen", "ALGORITHM_RECORDS", "AlgorithmProbe", "filter_type_crypto"),
            RecordType.DATABASE to Backstage("dbListen", "DATABASE_RECORDS", "DatabaseProbe", "filter_type_database"),
            RecordType.BEHAVIOR to Backstage("behaviorListen", "BEHAVIOR_RECORDS", "BehaviorProbe", "filter_type_behavior"),
            RecordType.PREFS to Backstage("prefsListen", "PREFS_RECORDS", "PrefsProbe", "filter_type_prefs"),
            RecordType.WEBVIEW to Backstage("webviewListen", "WEBVIEW_RECORDS", "WebViewProbe", "filter_type_webview"),
            RecordType.SYSTEM to Backstage("systemListen", "SYSTEM_RECORDS", "SystemProbe", "filter_type_system"),
            RecordType.NETWORK to Backstage("networkListen", "NETWORK_RECORDS", "NetworkProbe", "filter_type_network"),
            RecordType.DEVICE_ENV to Backstage("deviceEnvListen", "DEVICE_ENV_RECORDS", "DeviceEnvProbe", "filter_type_device_env"),
            RecordType.UI_INTERACTION to Backstage("uiListen", "UI_INTERACTION_RECORDS", "UiInteractionProbe", "filter_type_ui_interaction"),
            RecordType.PROCESS_CONTROL to Backstage("processControlListen", "PROCESS_CONTROL_RECORDS", "ProcessControlProbe", "filter_type_process_control"),
        )

    /** 功能域类别 → 它所在功能卡的分区键(与功能页渲染同源,不走第二份清单)。 */
    val featureSection: Map<Pair<RecordType, String>, String> =
        FeatureGroups.ALL.flatMap { group ->
            FeatureGroups.kindRefs(group).map { ref -> ref to "feature_group_${group.name.lowercase()}" }
        }.toMap()

    CategoryTaxonomy.all().map { category ->
        val stage = backstage.getValue(category.type)
        val foreignMaster = AppListenOverride.masterDomainOf(category.type) == CapabilityDomain.FEATURE
        CapabilityRow(
            kind = category.id,
            type = category.type,
            domain = category.domain,
            sectionKey =
                when {
                    category.domain == CapabilityDomain.FEATURE ->
                        featureSection.getValue(category.type to category.id)
                    // 监听域类别但其 master 归功能页 ⇒ 监听页把它归进「环境监听」分区。
                    foreignMaster -> "listen_section_env_observe"
                    else -> stage.listenSection
                },
            master = stage.master,
            routeKey = stage.routeKey,
            probe = stage.probe,
        )
    }
}

/** 消费入口:全表是不可变 List(直接给),按类型的视图走方法。 */
object CapabilityMap {

    /** 全表。 */
    val entries: List<CapabilityRow> = capabilityRows

    /** 指定类型的行。 */
    fun of(type: RecordType): List<CapabilityRow> = capabilityRows.filter { it.type == type }

    /** 同类型内两域混居的记录类型(跨域闸存在的证据)。 */
    val crossDomainTypes: Set<RecordType> =
        capabilityRows.groupBy { it.type }
            .filter { (_, rows) -> rows.map { it.domain }.toSet().size > 1 }
            .keys

    /**
     * 监听域里被功能页 master 管辖的类别 —— 监听页据此渲染「它的总开关在功能设置页」的点名行,
     * 而不是让用户勾完什么都不知道(V48-DFC-03 的出口)。
     */
    val crossDomainListen: List<CapabilityRow> =
        capabilityRows.filter {
            it.domain == CapabilityDomain.LISTEN &&
                AppListenOverride.masterDomainOf(it.type) == CapabilityDomain.FEATURE
        }
}
