package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * DNS 查询监控 Hooker:拦截 DNS 解析 API,记录 DNS 查询行为,
 * 为网络环境分析提供 DNS 层面的可见性。
 *
 * 覆盖 API:
 * - InetAddress.getByName(String):主机名→IP 解析
 * - InetAddress.getAllByName(String):主机名→所有 IP 解析
 * - InetAddress.getHostName:IP→主机名反解析
 *
 * 设计决策:仅记录不干预(观测型),DNS 劫持检测属于安全审计范畴,
 * 不应干预正常 DNS 解析流程。
 */
class DnsHideHooker(
    context: ProbeContext,
) : NetworkBaseHooker(context) {

    override fun install() {
        hookGetByName()
        hookGetAllByName()
        hookGetHostName()
    }

    private fun hookGetByName() {
        val clazz = runCatching { Class.forName("java.net.InetAddress") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getByName", String::class.java) }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(NetworkKind.REQUEST)) return
                val host = invocation.getArg(0) as? String ?: return
                if (isLocalHost(host)) return
                val result = invocation.result as? java.net.InetAddress ?: return
                val ip = result.hostAddress ?: "unknown"
                emit(
                    kind = NetworkKind.REQUEST,
                    operation = NetworkOperation.REQUEST_START,
                    params = mapOf(
                        "type" to "dns_query",
                        "host" to host,
                        "ip" to ip,
                    ),
                )
            }
        })
    }

    private fun hookGetAllByName() {
        val clazz = runCatching { Class.forName("java.net.InetAddress") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getAllByName", String::class.java) }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(NetworkKind.REQUEST)) return
                val host = invocation.getArg(0) as? String ?: return
                if (isLocalHost(host)) return
                val results = invocation.result as? Array<*> ?: return
                val ips = results.filterIsInstance<java.net.InetAddress>()
                    .mapNotNull { it.hostAddress }
                    .joinToString(",")
                emit(
                    kind = NetworkKind.REQUEST,
                    operation = NetworkOperation.REQUEST_START,
                    params = mapOf(
                        "type" to "dns_query_all",
                        "host" to host,
                        "ip" to ips,
                    ),
                )
            }
        })
    }

    private fun hookGetHostName() {
        val clazz = runCatching { Class.forName("java.net.InetAddress") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getHostName") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(NetworkKind.REQUEST)) return
                val result = invocation.result as? String ?: return
                if (isLocalHost(result)) return
                val thisObj = invocation.thisObject as? java.net.InetAddress ?: return
                val thisHost = thisObj.hostAddress ?: "unknown"
                emit(
                    kind = NetworkKind.REQUEST,
                    operation = NetworkOperation.REQUEST_START,
                    params = mapOf(
                        "type" to "dns_reverse",
                        "ip" to thisHost,
                        "hostname" to result,
                    ),
                )
            }
        })
    }

    private fun isLocalHost(host: String): Boolean {
        return host == "localhost" || host == "127.0.0.1" || host == "::1" ||
            host.startsWith("192.168.") || host.startsWith("10.") ||
            host.startsWith("172.")
    }
}
