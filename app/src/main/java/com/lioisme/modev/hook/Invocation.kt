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