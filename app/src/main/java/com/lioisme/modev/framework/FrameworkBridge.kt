package com.lioisme.modev.framework

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 现代框架服务桥:注册 [XposedServiceHelper] 监听,接收框架投递的 [XposedService]。
 *
 * - 仅宿主进程调用([ModevApp.onCreate]),注入进程不运行本类;
 * - legacy 框架(API ≤93)不投递服务,流恒为 null,上层自然降级;
 * - 服务投递依赖模块在现代框架中已启用。
 */
object FrameworkBridge {

    private val _service = MutableStateFlow<XposedService?>(null)

    /** 当前可用的框架服务(可空 = 无现代框架服务)。 */
    val serviceFlow: StateFlow<XposedService?> = _service

    @Volatile
    private var registered = false

    /** 幂等注册(重复调用安全,仅首次生效)。 */
    fun ensureRegistered() {
        if (registered) return
        registered = true
        // legacy 变体不含 service 库(compileOnly),此处捕获 NoClassDefFoundError 后自然降级
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    _service.value = service
                }

                override fun onServiceDied(service: XposedService) {
                    if (_service.value === service) {
                        _service.value = null
                    }
                }
            })
        }
    }
}