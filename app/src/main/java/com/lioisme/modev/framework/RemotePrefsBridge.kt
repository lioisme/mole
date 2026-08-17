package com.lioisme.modev.framework

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.StateFlow

/**
 * 现代框架远程偏好桥:任意进程(宿主或注入)注册 [XposedServiceHelper] 监听,
 * 获取 [XposedService] 并访问 RemotePreferences(框架 DB)。
 *
 * - 宿主进程([ModevApp])写入配置镜像与令牌;注入进程([ModevModule])只读配置 + 变更监听;
 * - legacy 变体不含 service 库(compileOnly),全部访问经 [runCatching] 兜底后自然降级为 null;
 * - 配置 key 统一前缀 [KEY_SWITCH_PREFIX],令牌 key 见 [TOKEN_KEY]。
 */
object RemotePrefsBridge {

    const val PREFS_KEY = "modev_config"
    const val TOKEN_KEY = "token"
    const val KEY_SWITCH_PREFIX = "switch_"

    /** 当前可用的框架服务(可空 = 无现代框架服务;公开为 Any? 防 legacy 类解析,见 FrameworkBridge)。 */
    val serviceFlow: StateFlow<Any?> = FrameworkBridge.serviceFlow

    private val registered = AtomicBoolean(false)

    /** 幂等注册(重复调用安全,仅首次生效)。 */
    fun ensureRegistered() {
        // 统一走 FrameworkBridge 注册 XposedServiceHelper 监听
        // (libxposed-service 库内限制每进程 1 个 listener,避免重复注册)
        if (registered.getAndSet(true)) return
        FrameworkBridge.ensureRegistered()
    }

    /** 当前进程的框架服务(可空);as? 对缺失类返回 null,legacy 安全。 */
    fun currentService(): XposedService? = FrameworkBridge.serviceFlow.value as? XposedService

    /** 当前进程可用的远程偏好;无服务或访问失败时返回 null。 */
    fun prefs(): SharedPreferences? =
        currentService()?.let { svc -> runCatching { svc.getRemotePreferences(PREFS_KEY) }.getOrNull() }

    /** 握手令牌(host 生成写入;无服务时 null)。 */
    fun token(): String? = prefs()?.getString(TOKEN_KEY, null)

    /** 全局捕获配置键(V10):配置与包名解耦,全应用共享一份。 */
    const val GLOBAL_SWITCH_KEY = KEY_SWITCH_PREFIX + "global"
}