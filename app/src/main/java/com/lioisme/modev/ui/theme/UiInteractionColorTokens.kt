package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.UiInteractionKind

/**
 * UI 交互监听类别色(V33):亮/暗主题各 5 色,与 KindColorTokens 同构;
 * 统计比例条 + 列表徽章共用。
 */
internal fun uiInteractionKindColorForMode(kind: UiInteractionKind, isDark: Boolean): Color =
    if (isDark) {
        when (kind) {
            UiInteractionKind.DIALOG_SHOW -> Color(0xFF7EC8E3)
            UiInteractionKind.DIALOG_KEYWORD -> Color(0xFFE3A77E)
            UiInteractionKind.WIDGET_TEXT -> Color(0xFFA7E37E)
            UiInteractionKind.ACTIVITY_START -> Color(0xFFC87EE3)
            UiInteractionKind.ACTIVITY_RESUME -> Color(0xFFE3D57E)
        }
    } else {
        when (kind) {
            UiInteractionKind.DIALOG_SHOW -> Color(0xFF2A9BBF)
            UiInteractionKind.DIALOG_KEYWORD -> Color(0xFFBF6F2A)
            UiInteractionKind.WIDGET_TEXT -> Color(0xFF6FBF2A)
            UiInteractionKind.ACTIVITY_START -> Color(0xFF8F2ABF)
            UiInteractionKind.ACTIVITY_RESUME -> Color(0xFFBFA82A)
        }
    }

@Composable
fun uiInteractionKindColor(kind: UiInteractionKind): Color =
    uiInteractionKindColorForMode(kind, LocalIsDarkTheme.current)
