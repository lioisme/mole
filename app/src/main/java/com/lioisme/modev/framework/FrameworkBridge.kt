package com.lioisme.modev.framework

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 现代框架服务桥:注册 [XposedServiceHelper] 监听,接收框架投递的 [XposedService]。
 *
 * - 仅宿主进程调用([ModevApp.onCreate]),注入进程不运行本类;
 * - legacy 框架(API ≤93)不投递服务,流恒为 null,上层自然降级;
 * - 服务投递依赖模块在现代框架中已启用。
 */
object FrameworkBridge {

    private val _service = MutableStateFlow<XposedService?>(null)

    /**
     * 当前可用的框架服务。
     * 公开类型收敛为 [Any?]:调用方 lambda 若在签名中携带 `XposedService?`,
     * 生成的 checkcast 会在 legacy 变体(运行时缺 service 库)首次发射即抛
     * NoClassDefFoundError——即使值为 null。消费方必须在非空分支内自行
     * `as XposedService` 转型,保证 legacy 空值路径零类解析。
     */
    val serviceFlow: StateFlow<Any?> = _service

    // M16: CAS 幂等注册——check-then-act 竞态下仅一个线程执行注册,其余直接返回
    private val registered = AtomicBoolean(false)

    /** 幂等注册(重复调用安全,仅首次生效)。 */
    fun ensureRegistered() {
        if (!registered.compareAndSet(false, true)) return
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
