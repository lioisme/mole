package com.lioisme.modev.mcp.tools

import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.RecordType

/**
 * master 偏差应用共享映射(V44 D4:listen/feature 两工具组单一事实源;
 * [when] 对 [RecordType] 穷尽——新增记录类型未映射即编译红,杜绝第三张手工清单)。
 * master 名 = [RecordType.wire],注入侧/协议零感知。
 */
internal fun applyMaster(current: AppListenOverride, type: RecordType, value: Boolean?): AppListenOverride =
    when (type) {
        RecordType.CRYPTO -> current.copy(cryptoListen = value)
        RecordType.DATABASE -> current.copy(dbListen = value)
        RecordType.BEHAVIOR -> current.copy(behaviorListen = value)
        RecordType.PREFS -> current.copy(prefsListen = value)
        RecordType.WEBVIEW -> current.copy(webviewListen = value)
        RecordType.NETWORK -> current.copy(networkListen = value)
        RecordType.DEVICE_ENV -> current.copy(deviceEnvListen = value)
        RecordType.UI_INTERACTION -> current.copy(uiListen = value)
        RecordType.SYSTEM -> current.copy(systemListen = value)
        RecordType.PROCESS_CONTROL -> current.copy(processControlListen = value)
    }

/** 全部 master 名(schema enum 派生,与 [applyMaster] 同集合)。 */
internal val allMasterWires: List<String> = RecordType.entries.map { it.wire }

/** 功能域 master 名 = 属 FEATURE 域类别的类型派生(与功能设置页四总开关同集合)。 */
internal val featureMasterWires: List<String> =
    CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE).map { it.type }.distinct().map { it.wire }
