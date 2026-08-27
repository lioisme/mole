package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

class XposedClassHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        if (!config().xposedClass) return
        hookClassForName()
        hookLoadClass()
        hookFindClass()
    }

    private fun hookClassForName() {
        val method = runCatching {
            Class::class.java.getMethod("forName", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().xposedClass) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    invocation.setResult(null)
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.CLASS_FOR_NAME,
                        originalValue = name,
                        targetClass = name,
                    )
                }
            }
        })
    }

    private fun hookLoadClass() {
        val method = runCatching {
            ClassLoader::class.java.getMethod("loadClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().xposedClass) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    invocation.setResult(null)
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.LOAD_CLASS,
                        originalValue = name,
                        targetClass = name,
                    )
                }
            }
        })
    }

    private fun hookFindClass() {
        val clazz = runCatching { Class.forName("dalvik.system.BaseDexClassLoader") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("findClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().xposedClass) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    invocation.setResult(null)
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.FIND_CLASS,
                        originalValue = name,
                        targetClass = name,
                    )
                }
            }
        })
    }

    private fun isXposedClassName(name: String): Boolean {
        return XPOSED_PREFIXES.any { name.startsWith(it) }
    }

    companion object {
        private val XPOSED_PREFIXES = listOf(
            "de.robv.android.xposed.",
            "de.robv.android.xposed.core.",
            "de.robv.android.xposed.callbacks.",
            "com.topjohnwu.lsposed.",
            "org.lsposed.lspd.",
            "org.lsposed.lsplant.",
            "io.github.lsposed.edxposed.",
            "io.github.lsposed.lspd.",
        )
    }
}
