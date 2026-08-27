package com.lioisme.modev.hook.hooks

import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器文件检测 Hooker(V31):File.exists() hook 已合并至 [RootFileHooker],
 * 本类保留空壳以维持 [DeviceEnvProbe] 的 hookers 列表结构稳定。
 */
class EmulatorFileHooker(
    @Suppress("UNUSED_PARAMETER") context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        // File.exists() hook 已统一由 RootFileHooker 处理,避免同一方法被多重 hook。
    }
}
