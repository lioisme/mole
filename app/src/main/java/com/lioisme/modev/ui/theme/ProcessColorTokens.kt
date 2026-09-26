package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.ProcessKind

/**
 * 进程控制监听类别色:亮/暗主题各 3 色,与 KindColorTokens 同构;
 * 统计比例条 + 列表徽章共用。
 */
internal fun processKindColorForMode(kind: ProcessKind, isDark: Boolean): Color =
    if (isDark) {
        when (kind) {
            ProcessKind.EXIT_INTERCEPT -> Color(0xFFE37E7E)
            ProcessKind.CRASH_PREVENT -> Color(0xFFE3B07E)
            ProcessKind.SCREEN_RECORD -> Color(0xFFB07EE3)
        }
    } else {
        when (kind) {
            ProcessKind.EXIT_INTERCEPT -> Color(0xFFBF2A2A)
            ProcessKind.CRASH_PREVENT -> Color(0xFFBF8A2A)
            ProcessKind.SCREEN_RECORD -> Color(0xFF6F2ABF)
        }
    }

@Composable
fun processKindColor(kind: ProcessKind): Color =
    processKindColorForMode(kind, LocalIsDarkTheme.current)
