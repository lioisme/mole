package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.NetworkKind

/*
 * 网络环境监听类别色 token:
 * SSL 类别 = 深蓝,代理 = 橙色,VPN = 紫色,请求 = 青色。
 */

private val lightPalette: Map<NetworkKind, Color> =
    mapOf(
        NetworkKind.SSL_CONTEXT to Color(0xFF00639B),
        NetworkKind.SSL_TRUST_MANAGER to Color(0xFF00639B),
        NetworkKind.SSL_OKHTTP_PINNER to Color(0xFF00639B),
        NetworkKind.SSL_OKHTTP_VERIFIER to Color(0xFF00639B),
        NetworkKind.SSL_WEBVIEW to Color(0xFF00639B),
        NetworkKind.SSL_NETWORK_CONFIG to Color(0xFF00639B),
        NetworkKind.PROXY_SYSTEM_PROP to Color(0xFF8A4D00),
        NetworkKind.PROXY_CONNECTIVITY to Color(0xFF8A4D00),
        NetworkKind.PROXY_SETTINGS to Color(0xFF8A4D00),
        NetworkKind.PROXY_OKHTTP to Color(0xFF8A4D00),
        NetworkKind.VPN_CAPABILITIES to Color(0xFF7A3F8F),
        NetworkKind.VPN_INTERFACE to Color(0xFF7A3F8F),
        NetworkKind.VPN_NETWORK_INFO to Color(0xFF7A3F8F),
        NetworkKind.VPN_LINK_PROPERTIES to Color(0xFF7A3F8F),
        NetworkKind.REQUEST to Color(0xFF006B5E),
    )

private val darkPalette: Map<NetworkKind, Color> =
    mapOf(
        NetworkKind.SSL_CONTEXT to Color(0xFF8FD0FF),
        NetworkKind.SSL_TRUST_MANAGER to Color(0xFF8FD0FF),
        NetworkKind.SSL_OKHTTP_PINNER to Color(0xFF8FD0FF),
        NetworkKind.SSL_OKHTTP_VERIFIER to Color(0xFF8FD0FF),
        NetworkKind.SSL_WEBVIEW to Color(0xFF8FD0FF),
        NetworkKind.SSL_NETWORK_CONFIG to Color(0xFF8FD0FF),
        NetworkKind.PROXY_SYSTEM_PROP to Color(0xFFFFB980),
        NetworkKind.PROXY_CONNECTIVITY to Color(0xFFFFB980),
        NetworkKind.PROXY_SETTINGS to Color(0xFFFFB980),
        NetworkKind.PROXY_OKHTTP to Color(0xFFFFB980),
        NetworkKind.VPN_CAPABILITIES to Color(0xFFDBA3EC),
        NetworkKind.VPN_INTERFACE to Color(0xFFDBA3EC),
        NetworkKind.VPN_NETWORK_INFO to Color(0xFFDBA3EC),
        NetworkKind.VPN_LINK_PROPERTIES to Color(0xFFDBA3EC),
        NetworkKind.REQUEST to Color(0xFF80CBC4),
    )

internal fun networkKindColorForMode(kind: NetworkKind, isDark: Boolean): Color =
    if (isDark) darkPalette.getValue(kind) else lightPalette.getValue(kind)

@Composable
fun networkKindColor(kind: NetworkKind): Color = networkKindColorForMode(kind, LocalIsDarkTheme.current)
