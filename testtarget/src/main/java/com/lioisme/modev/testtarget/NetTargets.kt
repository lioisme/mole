package com.lioisme.modev.testtarget

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

/**
 * 本机回环靶服务:HTTP(明文)+ TLS(自签证书,两个端口分别持 IP-SAN 证书与无匹配 SAN 证书)。
 * 全部监听 127.0.0.1 临时端口,零外部网络依赖;进程内懒启动单例。
 */
object NetTargets {

    @Volatile private var httpPort = -1
    @Volatile private var tlsIpPort = -1
    @Volatile private var tlsNoHostPort = -1

    private fun serve(tag: String, factory: () -> ServerSocket): Int {
        val ss = factory()
        Thread {
            runCatching {
                while (!ss.isClosed) {
                    val s: Socket = ss.accept()
                    Thread {
                        runCatching {
                            s.use {
                                val rd = BufferedReader(InputStreamReader(it.getInputStream()))
                                while (true) {
                                    val line = rd.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                                val body = """{"ok":true,"tag":"$tag"}"""
                                it.getOutputStream().writer().use { w ->
                                    w.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body")
                                    w.flush()
                                }
                            }
                        }
                    }.start()
                }
            }
        }.start()
        return ss.localPort
    }

    @Synchronized
    fun httpPort(ctx: Context): Int {
        if (httpPort < 0) httpPort = serve("http") { ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1")) }
        return httpPort
    }

    @Synchronized
    fun tlsIpPort(ctx: Context): Int {
        if (tlsIpPort < 0) tlsIpPort = serve("tls-ip") { tlsServerFactory(ctx, "testserver_ip.p12") }
        return tlsIpPort
    }

    @Synchronized
    fun tlsNoHostPort(ctx: Context): Int {
        if (tlsNoHostPort < 0) tlsNoHostPort = serve("tls-nohost") { tlsServerFactory(ctx, "testserver_nohost.p12") }
        return tlsNoHostPort
    }

    private fun tlsServerFactory(ctx: Context, asset: String): SSLServerSocket {
        val ks = KeyStore.getInstance("PKCS12")
        ctx.assets.open(asset).use { ks.load(it, "modevtest".toCharArray()) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, "modevtest".toCharArray())
        val sc = SSLContext.getInstance("TLS")
        sc.init(kmf.keyManagers, null, null)
        return sc.serverSocketFactory.createServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1")) as SSLServerSocket
    }
}
