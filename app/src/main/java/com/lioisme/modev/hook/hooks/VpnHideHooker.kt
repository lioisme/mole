package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * VPN 隐藏 Hooker(V30):隐藏 VPN 连接状态,防止应用检测 VPN 存在。
 *
 * 设计原则(第一性原则):
 * - 覆盖 Java API 层 4 个检测向量(覆盖 95%+ 场景);
 * - Native 层(ioctl/getifaddrs)需 Zygisk/内核模块,V30 暂不覆盖;
 * - VPN 接口名重命名使用确定性映射(同名始终产生同假名,防不一致检测)。
 */
class VpnHideHooker(
    context: ProbeContext,
) : NetworkBaseHooker(context) {

    private val vpnPrefixes = listOf("tun", "wg", "ppp", "tap", "ipsec", "utun", "pptp", "l2tp")
    private val renamedInterfaces = HashMap<String, String>()

    override fun install() {
        hookNetworkCapabilities()
        hookNetworkInterface()
        hookNetworkInfo()
        hookLinkProperties()
        hookHardwareAddress()
    }

    private fun hookNetworkCapabilities() {
        val clazz = runCatching { Class.forName("android.net.NetworkCapabilities") }.getOrNull() ?: return

        val hasTransport = runCatching { clazz.getMethod("hasTransport", Int::class.javaPrimitiveType) }.getOrNull()
        if (hasTransport != null) {
            engine.hook(hasTransport, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().vpnCapabilities) return
                    val transport = invocation.getArg(0) as? Int ?: return
                    if (transport == 4) {
                        invocation.setResult(false)
                        emit(kind = NetworkKind.VPN_CAPABILITIES, operation = NetworkOperation.VPN_HAS_TRANSPORT)
                    }
                }
            })
        }

        val getCapabilities = runCatching { clazz.getMethod("getCapabilities") }.getOrNull()
        if (getCapabilities != null) {
            engine.hook(getCapabilities, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnCapabilities) return
                    val result = invocation.result as? IntArray ?: return
                    if (18 !in result) {
                        invocation.setResult(result + 18)
                    }
                    emit(kind = NetworkKind.VPN_CAPABILITIES, operation = NetworkOperation.VPN_GET_CAPS)
                }
            })
        }

        val getTransportInfo = runCatching { clazz.getMethod("getTransportInfo") }.getOrNull()
        if (getTransportInfo != null) {
            engine.hook(getTransportInfo, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnCapabilities) return
                    val result = invocation.result ?: return
                    if (result.javaClass.name.contains("VpnTransportInfo")) {
                        invocation.setResult(null)
                        emit(kind = NetworkKind.VPN_CAPABILITIES, operation = NetworkOperation.VPN_TRANSPORT_INFO)
                    }
                }
            })
        }
    }

    private fun hookNetworkInterface() {
        val clazz = runCatching { Class.forName("java.net.NetworkInterface") }.getOrNull() ?: return

        val isVirtual = runCatching { clazz.getMethod("isVirtual") }.getOrNull()
        if (isVirtual != null) {
            engine.hook(isVirtual, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnInterface) return
                    val name = runCatching {
                        clazz.getMethod("getName").invoke(invocation.thisObject) as? String
                    }.getOrNull() ?: return
                    if (isVpnInterface(name)) {
                        invocation.setResult(false)
                        emit(kind = NetworkKind.VPN_INTERFACE, operation = NetworkOperation.VPN_IS_VIRTUAL,
                            params = mapOf("iface" to name))
                    }
                }
            })
        }

        val getName = runCatching { clazz.getMethod("getName") }.getOrNull()
        if (getName != null) {
            engine.hook(getName, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnInterface) return
                    val original = invocation.result as? String ?: return
                    if (isVpnInterface(original)) {
                        invocation.setResult(getFakeName(original))
                        emit(kind = NetworkKind.VPN_INTERFACE, operation = NetworkOperation.VPN_IFACE_NAME,
                            params = mapOf("original" to original))
                    }
                }
            })
        }

        val getByName = runCatching { clazz.getMethod("getByName", String::class.java) }.getOrNull()
        if (getByName != null) {
            engine.hook(getByName, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().vpnInterface) return
                    val name = invocation.getArg(0) as? String ?: return
                    if (isVpnInterface(name)) {
                        // 不干预返回值(return = 保留原始结果),仅记录检测事件
                        emit(kind = NetworkKind.VPN_INTERFACE, operation = NetworkOperation.VPN_IFACE_BY_NAME,
                            params = mapOf("name" to name))
                    }
                }
            })
        }

        val getNetworkInterfaces = runCatching { clazz.getMethod("getNetworkInterfaces") }.getOrNull()
        if (getNetworkInterfaces != null) {
            engine.hook(getNetworkInterfaces, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnInterface) return
                    val result = invocation.result as? java.util.Enumeration<*> ?: return
                    val filtered = java.util.Vector<Any>()
                    while (result.hasMoreElements()) {
                        val iface = result.nextElement() ?: continue
                        val name = runCatching {
                            clazz.getMethod("getName").invoke(iface) as? String
                        }.getOrNull()
                        if (name == null || !isVpnInterface(name)) {
                            filtered.add(iface)
                        }
                    }
                    invocation.setResult(filtered.elements())
                    emit(kind = NetworkKind.VPN_INTERFACE, operation = NetworkOperation.VPN_IFACE_ENUM)
                }
            })
        }
    }

    private fun hookNetworkInfo() {
        val clazz = runCatching { Class.forName("android.net.NetworkInfo") }.getOrNull() ?: return

        val getType = runCatching { clazz.getMethod("getType") }.getOrNull()
        if (getType != null) {
            engine.hook(getType, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnNetworkInfo) return
                    val result = invocation.result as? Int ?: return
                    if (result == 7) {
                        invocation.setResult(1)
                        emit(kind = NetworkKind.VPN_NETWORK_INFO, operation = NetworkOperation.VPN_TYPE)
                    }
                }
            })
        }

        val getTypeName = runCatching { clazz.getMethod("getTypeName") }.getOrNull()
        if (getTypeName != null) {
            engine.hook(getTypeName, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnNetworkInfo) return
                    val result = invocation.result as? String ?: return
                    if (result.equals("VPN", ignoreCase = true)) {
                        invocation.setResult("WIFI")
                        emit(kind = NetworkKind.VPN_NETWORK_INFO, operation = NetworkOperation.VPN_TYPE_NAME)
                    }
                }
            })
        }
    }

    private fun hookLinkProperties() {
        val clazz = runCatching { Class.forName("android.net.LinkProperties") }.getOrNull() ?: return

        val getInterfaceName = runCatching { clazz.getMethod("getInterfaceName") }.getOrNull()
        if (getInterfaceName != null) {
            engine.hook(getInterfaceName, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnLinkProperties) return
                    val result = invocation.result as? String ?: return
                    if (isVpnInterface(result)) {
                        invocation.setResult(null)
                        emit(kind = NetworkKind.VPN_LINK_PROPERTIES, operation = NetworkOperation.VPN_IFACE_PROP,
                            params = mapOf("iface" to result))
                    }
                }
            })
        }

        val getRoutes = runCatching { clazz.getMethod("getRoutes") }.getOrNull()
        if (getRoutes != null) {
            engine.hook(getRoutes, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().vpnLinkProperties) return
                    val result = invocation.result as? List<*> ?: return
                    val filtered = result.filter { route ->
                        val iface = runCatching {
                            route?.javaClass?.getMethod("getInterface")?.invoke(route) as? String
                        }.getOrNull()
                        iface == null || !isVpnInterface(iface)
                    }
                    if (filtered.size != result.size) {
                        invocation.setResult(filtered)
                        emit(kind = NetworkKind.VPN_LINK_PROPERTIES, operation = NetworkOperation.VPN_ROUTES)
                    }
                }
            })
        }
    }

    /**
     * 硬件 MAC 地址隐藏:拦截 NetworkInterface.getHardwareAddress(),
     * VPN 接口返回假 MAC 地址,防止通过硬件地址检测 VPN。
     */
    private fun hookHardwareAddress() {
        val clazz = runCatching { Class.forName("java.net.NetworkInterface") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getHardwareAddress") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().vpnInterface) return
                val name = runCatching {
                    clazz.getMethod("getName").invoke(invocation.thisObject) as? String
                }.getOrNull() ?: return
                if (!isVpnInterface(name)) return
                val current = invocation.result as? ByteArray
                if (current == null || current.isEmpty()) {
                    invocation.setResult(generateFakeMac(name))
                    emit(
                        kind = NetworkKind.VPN_INTERFACE,
                        operation = NetworkOperation.VPN_IFACE_NAME,
                        params = mapOf("iface" to name, "type" to "hardwareAddress"),
                    )
                }
            }
        })
    }

    private fun generateFakeMac(seed: String): ByteArray {
        val hash = seed.hashCode()
        return byteArrayOf(
            0x02,
            ((hash shr 24) and 0xFF).toByte(),
            ((hash shr 16) and 0xFF).toByte(),
            ((hash shr 8) and 0xFF).toByte(),
            (hash and 0xFF).toByte(),
            0x01,
        )
    }

    private fun isVpnInterface(name: String): Boolean {
        val lower = name.lowercase()
        return vpnPrefixes.any { lower.startsWith(it) }
    }

    private fun getFakeName(original: String): String {
        return renamedInterfaces.getOrPut(original) {
            val allowed = ('a'..'z') + ('0'..'9')
            val length = original.length.coerceAtLeast(3)
            (1..length).map { allowed.random() }.joinToString("")
        }
    }
}
