package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.WebViewKind

/*
 * WebView 类别色 token(V29 一等公民):
 * 浅/深两套调色板,基于 Material 扩展色板选取,保证类别色字母与淡底在
 * 对应表面(浅色 surface≈#1C1B1F、深色 surface≈#E6E0E9 前景)上的对比度 ≥ AA。
 * 用法:webviewKindColor(kind) 经 LocalIsDarkTheme 自动取浅/深;徽章底 = 13% alpha 淡底。
 */

/** 浅色模式 WebView 类别色(前景为浅色 surface 上的深色文本,对比度 ≥ 4.5:1)。 */
private val lightPalette: Map<WebViewKind, Color> =
    mapOf(
        WebViewKind.PAGE_LOAD to Color(0xFF00639B),
        WebViewKind.EVALUATE_JS to Color(0xFF8A4D00),
        WebViewKind.JS_BRIDGE to Color(0xFF7A3F8F),
        WebViewKind.DEBUG to Color(0xFFB3261E),
        WebViewKind.JS_CRYPTO to Color(0xFF506800),
    )

/** 深色模式 WebView 类别色(前景为深色 surface 上的浅色文本,对比度 ≥ 4.5:1)。 */
private val darkPalette: Map<WebViewKind, Color> =
    mapOf(
        WebViewKind.PAGE_LOAD to Color(0xFF8FD0FF),
        WebViewKind.EVALUATE_JS to Color(0xFFFFB980),
        WebViewKind.JS_BRIDGE to Color(0xFFDBA3EC),
        WebViewKind.DEBUG to Color(0xFFF2B8B5),
        WebViewKind.JS_CRYPTO to Color(0xFFB3CF5B),
    )

/** 类别语义色(非 Composable 访问器,UI 与对比度门禁测试同源)。 */
internal fun webviewKindColorForMode(kind: WebViewKind, isDark: Boolean): Color =
    if (isDark) darkPalette.getValue(kind) else lightPalette.getValue(kind)

/** 类别语义色(随外观模式自动切换,调用方在 Composable 上下文使用)。 */
@Composable
fun webviewKindColor(kind: WebViewKind): Color = webviewKindColorForMode(kind, LocalIsDarkTheme.current)
