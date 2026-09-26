package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.SqlKind

/*
 * 记录类别色 token(2026-08-16 V3,四界面重构,取代 LogsScreen 硬编码 8 色):
 * 浅/深两套调色板,基于 Material 扩展色板选取,保证类别色字母与淡底在
 * 对应表面(浅色 surface≈#1C1B1F、深色 surface≈#E6E0E9 前景)上的对比度 ≥ AA。
 * 用法:kindColor(kind) 经 LocalIsDarkTheme 自动取浅/深;徽章底 = 15% alpha 淡底。
 * V17 数据库监听:SQL 类别另立色板 [sqlKindColorForMode](与算法色板互不干扰,
 * 同屏混排可区分)。
 */

/** 浅色模式类别色(前景为浅色 surface 上的深色文本,对比度 ≥ 4.5:1)。 */
private val lightPalette: Map<CryptoKind, Color> =
    mapOf(
        CryptoKind.CIPHER to Color(0xFF00639B),
        CryptoKind.MESSAGE_DIGEST to Color(0xFF8A4D00),
        CryptoKind.MAC to Color(0xFF7A3F8F),
        CryptoKind.SIGNATURE to Color(0xFFB3261E),
        CryptoKind.KEY_PARAM to Color(0xFF1B5E20),
        CryptoKind.KEY_GENERATION to Color(0xFF006A60),
        CryptoKind.KEY_FACTORY to Color(0xFF795500),
        CryptoKind.BASE64 to Color(0xFF495057),
        CryptoKind.KEY_STORE to Color(0xFF00696E),
        CryptoKind.KEY_AGREEMENT to Color(0xFF3A5BA9),
        CryptoKind.RANDOM to Color(0xFF8E4A56),
    )

/** 深色模式类别色(前景为深色 surface 上的浅色文本,对比度 ≥ 4.5:1)。 */
private val darkPalette: Map<CryptoKind, Color> =
    mapOf(
        CryptoKind.CIPHER to Color(0xFF8FD0FF),
        CryptoKind.MESSAGE_DIGEST to Color(0xFFFFB980),
        CryptoKind.MAC to Color(0xFFDBA3EC),
        CryptoKind.SIGNATURE to Color(0xFFF2B8B5),
        CryptoKind.KEY_PARAM to Color(0xFF9ED7A3),
        CryptoKind.KEY_GENERATION to Color(0xFF74D2C3),
        CryptoKind.KEY_FACTORY to Color(0xFFF0DE7E),
        CryptoKind.BASE64 to Color(0xFFB2BAC2),
        CryptoKind.KEY_STORE to Color(0xFF82D9DE),
        CryptoKind.KEY_AGREEMENT to Color(0xFFB7C6FF),
        CryptoKind.RANDOM to Color(0xFFF3BAC5),
    )

/** 浅色模式 SQL 类别色(Material 扩展色板,对比度 ≥ 4.5:1)。 */
private val sqlLightPalette: Map<SqlKind, Color> =
    mapOf(
        SqlKind.OPEN to Color(0xFF006B58),
        SqlKind.QUERY to Color(0xFF345FA8),
        SqlKind.DML to Color(0xFF9A4B00),
        SqlKind.EXEC to Color(0xFF006A6B),
        SqlKind.TRANSACTION to Color(0xFF7C4A9E),
    )

/** 深色模式 SQL 类别色。 */
private val sqlDarkPalette: Map<SqlKind, Color> =
    mapOf(
        SqlKind.OPEN to Color(0xFF79D8BF),
        SqlKind.QUERY to Color(0xFFB2C8FF),
        SqlKind.DML to Color(0xFFFFBB8F),
        SqlKind.EXEC to Color(0xFF6FD4D6),
        SqlKind.TRANSACTION to Color(0xFFD6AFF0),
    )

/** 类别语义色(非 Composable 访问器,UI 与对比度门禁测试同源)。 */
internal fun kindColorForMode(kind: CryptoKind, isDark: Boolean): Color =
    if (isDark) darkPalette.getValue(kind) else lightPalette.getValue(kind)

/** 类别语义色(随外观模式自动切换,调用方在 Composable 上下文使用)。 */
@Composable
fun kindColor(kind: CryptoKind): Color = kindColorForMode(kind, LocalIsDarkTheme.current)

/** SQL 类别语义色(非 Composable 访问器,同算法色板同规)。 */
internal fun sqlKindColorForMode(kind: SqlKind, isDark: Boolean): Color =
    if (isDark) sqlDarkPalette.getValue(kind) else sqlLightPalette.getValue(kind)

/** SQL 类别语义色(随外观模式自动切换)。 */
@Composable
fun sqlKindColor(kind: SqlKind): Color = sqlKindColorForMode(kind, LocalIsDarkTheme.current)
