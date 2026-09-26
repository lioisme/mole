package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.PrefsEditorHooker
import com.lioisme.modev.hook.hooks.PrefsGetHooker
import com.lioisme.modev.hook.hooks.PrefsInstanceIndex
import com.lioisme.modev.hook.hooks.PrefsOpenHooker

/**
 * 偏好监听探针:SharedPreferences 读入配置文件 + Get/Put 全家桶。
 *
 * 装配三个 Hooker(共享 [PrefsInstanceIndex]):
 * - [PrefsOpenHooker]:配置文件载入(OPEN)+ edit 实例登记;
 * - [PrefsGetHooker]:get* 全家桶 / contains / getAll;
 * - [PrefsEditorHooker]:put* 全家桶 / remove / clear。
 */
class PrefsProbe : Probe {

    override val recordType: String = RecordTypes.PREFS

    /** V44 T2.4 冷启动裁剪:偏好域全部 kind 经 `prefsListen` master ∧ kind 双门控。 */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.prefsListen

    override fun install(context: ProbeContext) {
        val index = PrefsInstanceIndex()
        listOf(
            PrefsOpenHooker(context, index),
            PrefsGetHooker(context, index),
            PrefsEditorHooker(context, index),
        ).forEach { it.install() }
    }
}
