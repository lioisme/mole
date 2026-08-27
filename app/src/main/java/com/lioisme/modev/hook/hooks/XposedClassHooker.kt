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
        val lower = name.lowercase()
        return XPOSED_KEYWORDS.any { lower.contains(it) }
    }

    companion object {
        private val XPOSED_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "edxposed", "de.robv",
        )
    }
}
