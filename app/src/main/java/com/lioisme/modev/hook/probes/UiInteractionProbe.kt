package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.InterventionProbeMarker
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.ActivityTransitionHooker
import com.lioisme.modev.hook.hooks.DialogKeywordInterceptHooker
import com.lioisme.modev.hook.hooks.DialogLocationHooker
import com.lioisme.modev.hook.hooks.UiInteractionBaseHooker
import com.lioisme.modev.hook.hooks.WidgetTextHooker

/**
 * UI 交互监听探针(V33 一等公民):装配三 Hooker——对话框位置+返回键、
 * 对话框关键词拦截、控件文本赋值。
 * recordType = [RecordTypes.UI_INTERACTION]。
 */
class UiInteractionProbe : Probe, InterventionProbeMarker {

    override val recordType: String = RecordTypes.UI_INTERACTION

    /** V44 T2.4 冷启动裁剪:交互域(含弹窗关键词拦截干预)经 `uiListen` master ∧ kind 双门控。 */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.uiListen

    override fun install(context: ProbeContext) {
        val hookers: List<UiInteractionBaseHooker> =
            listOf(
                DialogLocationHooker(context),
                DialogKeywordInterceptHooker(context),
                WidgetTextHooker(context),
                ActivityTransitionHooker(context),
            )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
