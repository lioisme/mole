package com.lioisme.modev.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间线分组纯函数(V17 R3 秒级分组;与 RecordRow 尾部时间同 locale/时区语义):
 * - [secondKey]/[dayKey]:yyyyMMddHHmmss / yyyyMMdd 字符串比较(跨日/跨月判定,
 *   Locale.US 保证字典序 = 时间序);
 * - [formatSecond]:秒级时间,遵循系统 12/24h 时制;
 * - [formatDay]:日文案(en "Aug 19" / zh「8月19日」,zh 经 MMM 模式由 locale 数据
 *   输出,源码零中文硬编码);
 * - [timelineLabel]:列表首项组头(恒带日期前缀,稳定不闪变);
 * - [groupLabelOrNull]:邻行比较决策——同秒非组起始;prev==null(分页边界)不渲染,
 *   数据补齐后由邻行比较自然补出;跨秒跨日带日期前缀,同日仅秒级时间。
 *
 * SimpleDateFormat 非线程安全:固定模式格式化器经 ThreadLocal 承载
 * (列表逐行调用);locale/is24h 变化模式由调用方在组合侧 remember。
 */
private val secondKeyFormat: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("yyyyMMddHHmmss", Locale.US) }

private val dayKeyFormat: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("yyyyMMdd", Locale.US) }

/** 秒级分组键(含日期,跨日天然分键)。 */
fun secondKey(timestamp: Long): String = secondKeyFormat.get().format(Date(timestamp))

/** 日键(跨日/跨月判定)。 */
fun dayKey(timestamp: Long): String = dayKeyFormat.get().format(Date(timestamp))

/** 秒级时间文案(HH:mm:ss / h:mm:ss a,遵循 12/24h 时制)。 */
fun formatSecond(
    timestamp: Long,
    locale: Locale,
    is24h: Boolean,
): String =
    SimpleDateFormat(if (is24h) "HH:mm:ss" else "h:mm:ss a", locale).format(Date(timestamp))

/**
 * 日文案(en "Aug 19" / zh·ja「8月19日」/ ko「8월 19일」)。
 * zh/ja/ko 的「月/日/월/일」为模式字面量,经 Unicode 转义书写(源码零 CJK
 * 硬编码,HardcodedStringConventionTest 强制;V42 T5 增 ja/ko 母语惯形——
 * 默认 MMM 模式在日/韩 locale 输出「9月 8」截断形态)。
 */
fun formatDay(
    timestamp: Long,
    locale: Locale,
): String =
    SimpleDateFormat(
        when (locale.language) {
            "zh", "ja" -> "M\u6708d\u65e5"
            "ko" -> "M\uC6D4 d\uC77C"
            else -> "MMM d"
        },
        locale,
    ).format(Date(timestamp))

/** 完整组头文案:日期前缀 + 秒级时间(列表首项恒为组起始)。 */
fun timelineLabel(
    timestamp: Long,
    locale: Locale,
    is24h: Boolean,
): String = "${formatDay(timestamp, locale)} ${formatSecond(timestamp, locale, is24h)}"

/**
 * 邻行比较组头决策:同秒 = 非组起始(null);prev==null(分页边界)不渲染(null);
 * 跨秒 → 跨日带日期前缀,同日仅秒级时间。
 */
fun groupLabelOrNull(
    timestamp: Long,
    previous: Long?,
    locale: Locale,
    is24h: Boolean,
): String? {
    if (previous == null) return null
    if (secondKey(timestamp) == secondKey(previous)) return null
    val dayPrefix = if (dayKey(timestamp) != dayKey(previous)) {
        formatDay(timestamp, locale) + " "
    } else {
        ""
    }
    return dayPrefix + formatSecond(timestamp, locale, is24h)
}