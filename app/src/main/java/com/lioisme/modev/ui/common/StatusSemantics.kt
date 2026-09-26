package com.lioisme.modev.ui.common

import androidx.annotation.StringRes
import com.lioisme.modev.R
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.theme.StatusKind

/**
 * 状态语义投影:连接态/作用域态 → [StatusKind] + 文案资源的
 * 分派唯一事实源。此前该映射在 AppsScreen 行内与 AppMonitorScreen 头部各写一份,
 * 语义漂移风险高;文案资源仍由渲染处 stringResource 解析(domain 零硬编码契约不变)。
 */
data class StatusSemantic(
    val kind: StatusKind,
    @StringRes val labelRes: Int,
)

/** 连接态语义(运行中 Running / 离线,词汇表规范词)。 */
fun connectionStatus(connected: Boolean): StatusSemantic =
    if (connected) {
        StatusSemantic(StatusKind.ONLINE, R.string.online)
    } else {
        StatusSemantic(StatusKind.OFFLINE, R.string.offline)
    }

/**
 * 作用域态语义四态分派:
 * Ready+已作用域 → 监听中(primary)/ Ready+未作用域 → 未监听 /
 * Connecting → 读取作用域中(琥珀)/ Unavailable:NO_SERVICE → 框架不可用(error),
 * 其余原因回落未监听。
 */
fun scopeStatus(state: ScopeState, packageName: String): StatusSemantic = when (state) {
    is ScopeState.Ready ->
        if (state.isScoped(packageName)) {
            StatusSemantic(StatusKind.SCOPED, R.string.detail_listening)
        } else {
            StatusSemantic(StatusKind.UNSCOPED, R.string.detail_not_listening)
        }
    ScopeState.Connecting -> StatusSemantic(StatusKind.CONNECTING, R.string.detail_reading_scope)
    is ScopeState.Unavailable ->
        if (state.reason == ScopeState.UnavailableReason.NO_SERVICE) {
            StatusSemantic(StatusKind.ERROR, R.string.detail_framework_no_service)
        } else {
            StatusSemantic(StatusKind.UNSCOPED, R.string.detail_not_listening)
        }
}
