package com.lioisme.modev.core.model

/**
 * 捕获配置键单一事实源(ADR-5):
 * 宿主侧([com.lioisme.modev.data.repo.SettingsRepository])与
 * 注入侧([com.lioisme.modev.framework.RemotePrefsBridge])必须引用本对象,
 * 禁止双端各写字面量(键漂移即双端失联)。
 */
object ConfigKeys {

    /** 配置键前缀(旧版按包配置与全局配置共用)。 */
    const val SWITCH_PREFIX = "switch_"

    /** 全局捕获配置键(V10):全应用共享一份,与包名解耦。 */
    const val GLOBAL_SWITCH = SWITCH_PREFIX + "global"
}