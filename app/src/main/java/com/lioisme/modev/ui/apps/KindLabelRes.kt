package com.lioisme.modev.ui.apps

import androidx.annotation.StringRes
import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.ui.components.behaviorKindLabelRes
import com.lioisme.modev.ui.components.deviceEnvKindLabelRes
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.networkKindLabelRes
import com.lioisme.modev.ui.components.prefsKindLabelRes
import com.lioisme.modev.ui.components.processKindLabelRes
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.systemKindLabelRes
import com.lioisme.modev.ui.components.uiInteractionKindLabelRes
import com.lioisme.modev.ui.components.webviewKindLabelRes

/**
 * kind → 显示标签资源解析(V44 R1-3 收敛:**(类型,id) 限定键唯一入口**,
 * 委派各记录类型的穷尽枚举标签表(ui/components 各 Labels 文件)——
 * 那些表即全 App 唯一事实源(设置页行标签 = 筛选菜单 = 列表行 = 详情元数据同文案)。
 *
 * 旧形态双缺陷已消除:
 * - 裸 id 字符串 when 表按分支序 first-wins,`SIGNATURE` 的 SystemKind 分支恒不可达
 *   (系统信息签名行被误标为算法签名),`OPEN` 依赖「偏好前置」脆弱约定;
 * - 本表曾与 [com.lioisme.modev.ui.components.kindLabelRes] 各自持表,
 *   同 kind 双资源键(同一类别同时存在 switch_* 与 kind_* 两个标签)。
 *
 * 未知 (type,id) → [R.string.unknown_error] 兜底(理论不可达:调用方恒传
 * taxonomy 已登记类别;[KindLabelResExhaustiveTest] 锁全类别非兜底)。
 */
internal object KindLabelRes {

    @StringRes
    fun resolve(type: RecordType, kindId: String): Int = when (type) {
        RecordType.CRYPTO -> CryptoKind.entries.firstOrNull { it.name == kindId }
            ?.let(::kindLabelRes)
        RecordType.DATABASE -> SqlKind.entries.firstOrNull { it.name == kindId }
            ?.let(::sqlKindLabelRes)
        RecordType.BEHAVIOR -> BehaviorKind.entries.firstOrNull { it.name == kindId }
            ?.let(::behaviorKindLabelRes)
        RecordType.PREFS -> PrefsKind.entries.firstOrNull { it.name == kindId }
            ?.let(::prefsKindLabelRes)
        RecordType.WEBVIEW -> WebViewKind.entries.firstOrNull { it.name == kindId }
            ?.let(::webviewKindLabelRes)
        RecordType.NETWORK -> NetworkKind.entries.firstOrNull { it.name == kindId }
            ?.let(::networkKindLabelRes)
        RecordType.DEVICE_ENV -> DeviceEnvKind.entries.firstOrNull { it.name == kindId }
            ?.let(::deviceEnvKindLabelRes)
        RecordType.UI_INTERACTION -> UiInteractionKind.entries.firstOrNull { it.name == kindId }
            ?.let(::uiInteractionKindLabelRes)
        RecordType.SYSTEM -> SystemKind.entries.firstOrNull { it.name == kindId }
            ?.let(::systemKindLabelRes)
        RecordType.PROCESS_CONTROL -> ProcessKind.entries.firstOrNull { it.name == kindId }
            ?.let(::processKindLabelRes)
    } ?: R.string.unknown_error

    /**
     * 记录类型 → 其 master 行的显示标签(V48 R-2 单点):功能页渲染这 10 行、
     * 监听页的跨域提示也要**报出同一个名字**,两处各写一份字面量就会各说各话。
     * 穷尽 `when`,新增类型即编译期点名。
     */
    @StringRes
    fun masterLabelRes(type: RecordType): Int = when (type) {
        RecordType.CRYPTO -> R.string.algorithm_monitor_master
        RecordType.DATABASE -> R.string.database_monitor_master
        RecordType.BEHAVIOR -> R.string.behavior_monitor_master
        RecordType.PREFS -> R.string.prefs_monitor_master
        RecordType.WEBVIEW -> R.string.webview_monitor_master
        RecordType.SYSTEM -> R.string.listen_row_master_system
        RecordType.NETWORK -> R.string.network_monitor_master
        RecordType.DEVICE_ENV -> R.string.device_env_master
        RecordType.UI_INTERACTION -> R.string.listen_row_master_ui
        RecordType.PROCESS_CONTROL -> R.string.listen_row_master_process
    }
}
