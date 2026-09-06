package com.lioisme.modev.testtarget.cases

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.provider.Settings
import com.lioisme.modev.testtarget.NetTargets
import com.lioisme.modev.testtarget.Tt
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.security.KeyStore
import java.util.Collections
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** L-NET-* / E-SSL-* / E-PRX-* / E-VPN-* / E-DNS-01:网络域触发器(观测 + 干预二态)。 */
object NetworkCases {

    private fun trustManagerFor(ctx: Context, asset: String): X509TrustManager {
        val ks = KeyStore.getInstance("PKCS12")
        ctx.assets.open(asset).use { ks.load(it, "modevtest".toCharArray()) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    private fun httpsGet(url: String, factory: SSLSocketFactoryArg? = null): String {
        val conn = URL(url).openConnection() as HttpsURLConnection
        if (factory != null) {
            conn.sslSocketFactory = factory.sslSocketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> factory.allowHostname }
        }
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return try {
            conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
        } finally {
            runCatching { conn.errorStream?.close() }
        }
    }

    private class SSLSocketFactoryArg(
        val sslSocketFactory: javax.net.ssl.SSLSocketFactory,
        val allowHostname: Boolean,
    )

    fun run(ctx: Context, case: String) {
        when (case) {
            // ---- 观测 ----
            "L-NET-01" -> {
                val port = NetTargets.httpPort(ctx)
                val conn = URL("http://127.0.0.1:$port/plain").openConnection() as HttpURLConnection
                val code = conn.responseCode
                conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                Tt.result(case, "code" to code.toString())
            }
            "L-NET-02" -> {
                val port = NetTargets.httpPort(ctx)
                val client = OkHttpClient()
                client.newCall(Request.Builder().url("http://127.0.0.1:$port/okhttp").build()).execute().use {
                    Tt.result(case, "code" to it.code.toString())
                }
            }
            // ---- SSL 干预二态 ----
            "E-SSL-01" -> {
                val port = NetTargets.tlsIpPort(ctx)
                val sc = SSLContext.getInstance("TLS")
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)
                sc.init(null, tmf.trustManagers, null)
                val arg = SSLSocketFactoryArg(sc.socketFactory, true)
                val ok = runCatching { httpsGet("https://localhost:$port/s1", arg) }.isSuccess
                Tt.result(case, "handshake" to ok.toString())
            }
            "E-SSL-02" -> {
                // V38:默认 hostname verifier 对 localhost 不匹配 ip.p12 证书 SAN(V31
                // 时代用例缺陷;trust bypass 后仍被 hostname 验证拦下),改用与证书
                // 匹配的 127.0.0.1(服务器同一台)。
                val port = NetTargets.tlsIpPort(ctx)
                val ok = runCatching { httpsGet("https://127.0.0.1:$port/s2") }.isSuccess
                Tt.result(case, "handshake" to ok.toString())
            }
            "E-SSL-03" -> {
                val port = NetTargets.tlsIpPort(ctx)
                val tm = trustManagerFor(ctx, "testserver_ip.p12")
                val client = OkHttpClient.Builder()
                    .sslSocketFactory(
                        SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory,
                        tm,
                    )
                    .certificatePinner(
                        CertificatePinner.Builder()
                            // okhttp 4.x pattern 不允许携带端口(V38 实锤 IllegalArgumentException);
                            // 用与证书信任链匹配的 127.0.0.1 + 故意错误 pin,触发 CertificatePinner.check
                            // (V31 时代 localhost/无端口 + 正确信任链组合下 hostname 先失败,pin 路径不可达)。
                            .add("127.0.0.1", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                            .build(),
                    )
                    .build()
                val r = runCatching {
                    client.newCall(Request.Builder().url("https://127.0.0.1:$port/s3").build()).execute()
                        .use { it.code }
                }
                Tt.result(case, "code" to (r.getOrNull()?.toString() ?: "fail"), "err" to (r.exceptionOrNull()?.javaClass?.simpleName ?: "none"))
            }
            "E-SSL-04" -> {
                val port = NetTargets.tlsIpPort(ctx)
                val tm = trustManagerFor(ctx, "testserver_ip.p12")
                val client = OkHttpClient.Builder()
                    .sslSocketFactory(
                        SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory,
                        tm,
                    )
                    .build()
                val r = runCatching {
                    client.newCall(Request.Builder().url("https://127.0.0.1:$port/s4").build()).execute()
                        .use { it.code }
                }
                Tt.result(case, "code" to (r.getOrNull()?.toString() ?: "fail"), "err" to (r.exceptionOrNull()?.javaClass?.simpleName ?: "none"))
            }
            "E-SSL-06" -> {
                val port = NetTargets.tlsIpPort(ctx)
                val r = runCatching { httpsGet("https://127.0.0.1:$port/s6") }
                Tt.result(case, "handshake" to r.isSuccess.toString(), "err" to (r.exceptionOrNull()?.javaClass?.simpleName ?: "none"))
            }
            // ---- 代理隐藏二态(脚本轮次先设全局代理) ----
            "E-PRX-01" -> {
                val v = System.getProperty("http.proxyHost") ?: ""
                Tt.result(case, "proxyHost" to v)
            }
            "E-PRX-02" -> {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                // 反射调用规避 SDK 36 存根 ProxyInfo 与运行时 java.net.Proxy 的类型漂移
                val p = runCatching {
                    ConnectivityManager::class.java.getMethod("getDefaultProxy").invoke(cm)
                }.getOrNull()
                Tt.result(case, "defaultProxy" to (p?.toString() ?: "null"))
            }
            "E-PRX-03" -> {
                val v = Settings.Global.getString(ctx.contentResolver, "http_proxy") ?: "null"
                Tt.result(case, "globalHttpProxy" to v)
            }
            "E-PRX-04" -> {
                val sel = ProxySelector.getDefault().select(URI("https://proxy-probe.invalid"))
                Tt.result(case, "selector" to sel.joinToString(",") { it.type().name })
            }
            // ---- VPN 隐藏二态(无 VPN 链路时断言记录面 + 自检一致性) ----
            "E-VPN-01" -> {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val net = cm.activeNetwork
                val caps = net?.let { cm.getNetworkCapabilities(it) }
                val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                Tt.result(case, "hasVpn" to hasVpn.toString())
            }
            "E-VPN-02" -> {
                val names = Collections.list(NetworkInterface.getNetworkInterfaces()).map { it.name }
                Tt.result(case, "ifaces" to names.joinToString(","))
            }
            "E-VPN-03" -> {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val info = cm.activeNetworkInfo
                Tt.result(case, "type" to (info?.type?.toString() ?: "null"), "typeName" to (info?.typeName ?: "null"))
            }
            "E-VPN-04" -> {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val lp = cm.getLinkProperties(cm.activeNetwork)
                Tt.result(case, "routes" to (lp?.routes?.size?.toString() ?: "0"))
            }
            "E-DNS-01" -> {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val lp = cm.getLinkProperties(cm.activeNetwork)
                val dns = lp?.dnsServers?.joinToString(",") { it.hostAddress ?: "?" } ?: "none"
                val resolved = runCatching {
                    InetAddress.getAllByName("dns.google").firstOrNull()?.hostAddress
                }.getOrNull() ?: "fail"
                Tt.result(case, "dnsServers" to dns, "resolved" to resolved)
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
