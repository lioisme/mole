package com.lioisme.modev.hook

import java.lang.reflect.Executable

/**
 * 框架无关的调用视图(libxposed 与 legacy Xposed 双框架共用)。
 *
 * 生命周期由框架适配器驱动:适配器在【原方法执行前】构造一次本视图调用 [MethodHook.before],
 * 在【原方法正常返回后】构造携带结果的视图调用 [MethodHook.after]。
 * 与 libxposed 的 intercept 语义严格对齐:before ≈ proceed 之前,after ≈ proceed 之后。
 */
abstract class Invocation {

    /** 被调用的方法/构造器;框架未提供时(如 legacy 构造器 hook)为 null。 */
    abstract val executable: Executable?

    /** 调用实例;静态方法/构造器为 null。 */
    abstract val thisObject: Any?

    /** after 阶段的原方法返回值;before 阶段恒为 null。 */
    abstract val result: Any?

    /**
     * 本次调用的底层句柄(V43 T3):before/after 两次视图共享同一 token,
     * [HookerSupport] 据此做**按调用配对**的入参暂存——原方法抛异常跳过 after 时,
     * 残留条目受容量上限回收,不再依赖 ThreadLocal 栈的时序配对(V4.4:数据错位与
     * 驻留泄漏双修)。
     */
    open val token: Any? get() = this

    /** before 阶段是否已被短路(setResult/setThrowable 已生效):广播侧据此停止后续干预订阅。 */
    open val consumed: Boolean get() = false

    /**
     * 替换原方法返回值(仅 before 阶段有效):
     * - 设置后原方法不再执行(libxposed)或结果被覆盖(legacy);
     * - 供 SSL 绕过/代理隐藏/VPN 隐藏等干预型 hook 使用。
     */
    open fun setResult(value: Any?) {
        throw UnsupportedOperationException("setResult not supported by this Invocation")
    }

    /**
     * 以异常结束本次调用(V43 T3/D-5):原方法不执行,[t] 抛给调用方——
     * 忠实还原 JDK/Android「查不到抛 NameNotFoundException」类契约,
     * 替代 `setResult(null)` 破坏返回值契约导致宿主 NPE 的旧式干预。
     *
     * 语义约束:必须经干预型注册([HookEngine.hookThrowing])挂载的钩子才生效——
     * libxposed PROTECTIVE 模式会吞回调异常并照常执行原方法(旧「回调内抛 CNFE」
     * 实现静默失效的根因);干预型注册使用 PASSTHROUGH + 回调体内 runSafely
     * 隔离,业务缺陷异常不外泄,唯有本方法设置的异常会抵达调用方。
     */
    open fun setThrowable(t: Throwable) {
        throw UnsupportedOperationException("setThrowable not supported by this Invocation")
    }

    /**
     * 改写第 index 个实参(仅 before 阶段有效,原方法以新值执行):
     * 供 FLAG_SECURE 位清除等「修改入参而非短路调用」的干预使用(D-11)。
     */
    open fun setArg(index: Int, value: Any?) {
        throw UnsupportedOperationException("setArg not supported by this Invocation")
    }

    /** 实参个数。 */
    abstract val argCount: Int

    /**
     * 越界安全取值:index 越界返回 null,绝不抛出,
     * 保证拦截逻辑永不被参数形状击穿(替代重构前的 ChainExt.getArgOrNull)。
     */
    fun getArg(index: Int): Any? = if (index in 0 until argCount) getArgUnsafe(index) else null

    protected abstract fun getArgUnsafe(index: Int): Any?
}

/**
 * 框架无关的拦截回调。两个阶段均允许空实现;
 * 实现类内部自行负责开关判断与异常安全(开关关闭时零记录开销)。
 */
abstract class MethodHook {

    /** 原方法执行前调用;此时 [Invocation.result] 恒为 null。 */
    open fun before(invocation: Invocation) {}

    /**
     * 原方法正常返回后调用;仅当原方法无异常时触发
     * (与 libxposed 的 proceed() 抛出即跳过的既有行为一致)。
     */
    open fun after(invocation: Invocation) {}
}
