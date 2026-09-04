package com.lioisme.modev.hook

import com.lioisme.modev.core.model.HookSwitch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 配置变更订阅器(V37 T0.2):承载「安装期一次性动作」类钩子(如 Build 静态字段改写)
 * 的延迟评估通道——此类动作无法在每次调用点重做,须在配置推送/变更到达时重新判定
 * 开关并按幂等语义补执行。
 *
 * - 线程安全(CopyOnWriteArrayList,注册少、通知频低);
 * - 监听器异常逐个隔离,单点失败不影响其余监听器与配置通道;
 * - 注册时机在 [HookInstaller.install] 内(探针装配期),通知源为
 *   [ConfigChannel] 的 onConfig 回调(RemotePreferences 直读 / TCP 推送 / 偏差更新)。
 */
class ConfigWatcher {

    private val listeners = CopyOnWriteArrayList<(HookSwitch) -> Unit>()

    fun register(listener: (HookSwitch) -> Unit) {
        listeners += listener
    }

    fun notify(config: HookSwitch) {
        for (listener in listeners) {
            runCatching { listener(config) }
        }
    }
}
