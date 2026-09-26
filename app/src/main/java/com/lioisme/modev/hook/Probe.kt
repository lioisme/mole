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

    /**
     * 冷启动裁剪闸门(V44 T2.4/D-V44-1):本探针的全部能力是否可能在当前有效配置下
     * 生效。false = master 全关,装配期直接跳过**整组装钩**(数百个方法钩的反射枚举
     * + 注册 + 永久退出 JIT 内联的代价一并省下)。
     *
     * 默认 true(保守:未声明即恒装,不会因遗漏声明导致能力静默失效)。
     * 声明契约:仅在「本探针所有 kind 都被同一 master 覆盖」时收窄为 master 判定——
     * 若某 kind 不受 master 约束(如干预型独立开关),必须保持 true。
     *
     * 时序契约(D-V44-1 稳健档):判定只在装配期做一次,配置后开需进程重启生效
     * (不引入异步补装,零新竞态面);现代变体装配前已直读 RemotePreferences
     * 取到本应用有效配置,legacy 变体由 TCP 推送在装配前抵达。
     */
    fun enabledIn(switch: HookSwitch): Boolean = true
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
    /** 配置变更订阅器:安装期一次性动作的延迟评估通道;测试可省略(空订阅)。 */
    val configWatcher: ConfigWatcher = ConfigWatcher(),
    /** 上下文追踪器(同一算法操作链路的关联)。 */
    val tracker: CryptoContextTracker,
    /** 记录出口(有界队列 + 写前日志)。 */
    val sink: RecordSink,
    /** 主包名(记录归档键,分离进程归并后)。 */
    val packageName: String,
    /**
     * 目标 App classloader 惰性取用:注入早于 Application 时为 null,每次调用重试。
     * 模块注入类的 Class.forName 委派链不含 app dex → okhttp 等 app 类必须经此 loader
     * 显式装载(installSslHooksFor 消费)。
     */
    val appClassLoader: () -> ClassLoader? = { null },
    /**
     * 类加载观察单一监视器(V43 T2/D-4):loadClass/findClass 全进程仅注册一次,
     * 干预(Xposed 类隐藏)与观测(SSL/okhttp 惰性安装)一律经订阅表广播分派,
     * 杜绝多 hooker 重复注册同一方法的静默遮蔽与成倍回调。默认与 [engine] 同实例。
     */
    val loadClassWatcher: LoadClassWatcher = LoadClassWatcher(engine),
    /**
     * 热门平台方法单一广播注册器(V43 T3/D-4 通用化):getPackageInfo /
     * Settings$Secure.getString / System.getProperty 等多 hooker 共享方法
     * 全进程仅注册一个框架钩子,订阅者按序广播(V2.9)。默认与 [engine] 同实例。
     */
    val broadcast: SharedMethodBroadcast = SharedMethodBroadcast(engine),
)
