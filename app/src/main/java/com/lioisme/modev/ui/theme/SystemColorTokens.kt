package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.SystemKind

/**
 * 系统信息监听类别色:亮/暗主题各 3 色,与 KindColorTokens 同构;
 * 统计比例条 + 列表徽章共用。
 */
internal fun systemKindColorForMode(kind: SystemKind, isDark: Boolean): Color =
    if (isDark) {
        when (kind) {
            SystemKind.LOGCAT -> Color(0xFF7EE3B0)
            SystemKind.APP_INFO -> Color(0xFFE3D57E)
            SystemKind.SIGNATURE -> Color(0xFF7E9FE3)
        }
    } else {
        when (kind) {
            SystemKind.LOGCAT -> Color(0xFF2ABF8A)
            SystemKind.APP_INFO -> Color(0xFFBFA82A)
            SystemKind.SIGNATURE -> Color(0xFF2A6FBF)
        }
    }

@Composable
fun systemKindColor(kind: SystemKind): Color =
    systemKindColorForMode(kind, LocalIsDarkTheme.current)
