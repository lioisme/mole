package com.lioisme.modev.ui.common

import com.lioisme.modev.R
import com.lioisme.modev.domain.scope.ScopeChangeResult

/**
 * 作用域变更结果 → 本地化提示(V23 消重):此前 AppsViewModel 与
 * AppMonitorViewModel 各持一份私有映射;domain 零硬编码契约不变,
 * 文案资源解析仍由 UI 层(Snackbar)承担。
 */
fun scopeChangeMessage(packageName: String, result: ScopeChangeResult): UiMessage = when (result) {
    is ScopeChangeResult.ScopeRequested ->
        UiMessage(R.string.apps_scope_requested, listOf(packageName))
    is ScopeChangeResult.ScopeRemoved ->
        UiMessage(R.string.apps_scope_removed, listOf(packageName))
    is ScopeChangeResult.Failed -> when (result) {
        ScopeChangeResult.Failed.FrameworkNotReady ->
            UiMessage(R.string.apps_scope_failed_not_ready)
        ScopeChangeResult.Failed.ServiceNotConnected ->
            UiMessage(R.string.apps_scope_failed_service)
        ScopeChangeResult.Failed.UserRejected ->
            UiMessage(R.string.apps_scope_failed_rejected)
        ScopeChangeResult.Failed.Timeout ->
            UiMessage(R.string.apps_scope_failed_timeout)
        ScopeChangeResult.Failed.RemoveFailed ->
            UiMessage(R.string.apps_scope_failed_remove, listOf(packageName))
    }
}
