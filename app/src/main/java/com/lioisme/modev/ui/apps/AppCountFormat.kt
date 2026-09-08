package com.lioisme.modev.ui.apps

import android.icu.text.CompactDecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * 记录数紧凑格式化(2026-08-17 S3 修复;2026-08-18 V15 规范化):
 * 按 [Locale] 使用平台紧凑数字格式(SHORT 形态,最多 1 位小数),
 * 中文系统 ≥1 万 → 「1.2万」,英文 ≥1 千 → 「1.2K」,以下走千分位整数
 * (「3,000」,不再出现「3千+」式自造单位)。
 * 纯函数、locale 驱动,测试可按语言断言;调用方传入 UI 实际生效 locale。
 *
 * 注:紧凑格式化用 [android.icu.text.CompactDecimalFormat](API 24+)而非
 * java.text.NumberFormat.getCompactNumberInstance —— 后者在 AGP `-no-jdk` /
 * --system jdkImage 编译链中解析不到(工具链类元数据裁剪,实测 Unresolved),
 * android.icu 包不受影响;minSdk 26 下两者运行期等价。
 */
object AppCountFormat {

    /** 万进位(10^4 组)语言集:zh/ja/ko 的 CJK 计数惯例(V42 T5)。 */
    private val CJK_10_4_LANGUAGES = setOf("zh", "ja", "ko")

    /**
     * 紧凑档位阈值:zh/ja/ko = 万进位组(CJK 计数惯例,ICU 原生 10^4 组),
     * 其余 = 千;以下用千分位整数(V42 T5 增 ja/ko)。
     */
    fun compactThreshold(locale: Locale): Long =
        if (locale.language in CJK_10_4_LANGUAGES) 10_000L else 1_000L

    /** 千分位整数(locale 分组,供统计数值/资源格式串 %1$,d 之外的场景)。 */
    fun grouped(
        count: Long,
        locale: Locale,
    ): String = NumberFormat.getNumberInstance(locale).format(count)

    fun compact(
        count: Long,
        locale: Locale,
    ): String {
        if (count < compactThreshold(locale)) {
            return NumberFormat.getNumberInstance(locale).format(count)
        }
        return CompactDecimalFormat
            .getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
            .apply { maximumFractionDigits = 1 }
            .format(count)
    }
}