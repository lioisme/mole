package com.lioisme.modev.hook

import com.lioisme.modev.core.model.HookSwitch

/**
 * 监听能力探针(ADR-3):采集器与注入基础设施解耦的最小契约。
 *
 * - [recordType]:该探针产出记录的监听类型(与宿主管道 [com.lioisme.modev.core.codec.RecordCodecRegistry]
 *   注册类型一一对应,ACK/存储/路由按此分发);
 * - [install]:装配本能力所需的全部 hook(探针自持实例状态,禁止全局可变)。
 *
 * 新增监听能力 = 实现本接口 + 在 [com.lioisme.modev.hook.HookInstaller] 注册,
 * 传输/存储/UI 管线零改动。
 */
interface Probe {

    val recordType: String

    fun install(context: ProbeContext)
}

/**
 * 探针装配上下文(ADR-3):注入侧共享设施集合,探针在 [Probe.install] 时按需取用。
 * 不可变;全部成员线程安全(见各类型契约)。
 */
class ProbeContext(
    /** 统一 Hook 注册入口(委托 Platform,单点失败不阻塞其余)。 */
    val engine: HookEngine,
    /** 捕获配置快照(每次调用现读,变更即生效)。 */
    val config: () -> HookSwitch,
    /** 配置变更订阅器(V37 T0.2):安装期一次性动作的延迟评估通道;测试可省略(空订阅)。 */
    val configWatcher: ConfigWatcher = ConfigWatcher(),
    /** 上下文追踪器(同一算法操作链路的关联)。 */
    val tracker: CryptoContextTracker,
    /** 记录出口(有界队列 + 写前日志)。 */
    val sink: RecordSink,
    /** 主包名(记录归档键,分离进程归并后)。 */
    val packageName: String,
)