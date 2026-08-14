package com.lioisme.modev.hook.hooks

import io.github.libxposed.api.XposedInterface

/**
 * Chain 参数便捷访问:越界时返回 null 而非抛异常,保证拦截逻辑永不因参数形状崩溃。
 */
internal fun XposedInterface.Chain.getArgOrNull(index: Int): Any? =
    if (index < getArgs().size) getArgs()[index] else null