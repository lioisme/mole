package com.lioisme.modev.ui.common

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lioisme.modev.i18n.LocaleManager
import java.util.Locale

/**
 * 应用实际生效 locale 的单一来源(V16 R5 语言切换修复):
 *
 * `LocalConfiguration`(Activity 窗口配置)不随热切换更新——包过 Context
 * (LocaleManager.wrap + refreshForRecreation 重建)的 `resources.configuration`
 * 才是生效配置,与 `stringResource` 解析路径同源。凡 locale 敏感的取值
 * (AppCountFormat 数字格式等)一律经本函数,禁止直读 LocalConfiguration。
 */
fun Context.effectiveLocale(): Locale =
    resources.configuration.locales[0] ?: Locale.getDefault()

/** 组合版:随 LocalContext 换实例重算(换包即新 locale,零旧值残留)。 */
@Composable
fun rememberEffectiveLocale(): Locale {
    val context = LocalContext.current
    return remember(context) { context.effectiveLocale() }
}

/**
 * Dialog/Popup 窗口组合的语言跟随资源(V16 R6 实机审计):
 *
 * 独立窗口(Dialog/Popup)组合只继承**创建时**的 CompositionLocal 快照,
 * 主树 provider(如 LocalContext 覆盖)后续更新不传入窗口——窗口内
 * `stringResource` 恒解析旧语言。本入口以 [LocaleManager.localeFlow]
 * 状态驱动窗口组合重组(状态写入可跨 Recomposer 传播),再经
 * [LocaleManager.wrap] 解析当前语言资源,窗口内文本一律改经本入口。
 */
@Composable
fun rememberLocaleAwareResources(): Resources {
    val tag by LocaleManager.localeFlow.collectAsState()
    val context = LocalContext.current
    return remember(tag, context) { LocaleManager.wrap(context).resources }
}
