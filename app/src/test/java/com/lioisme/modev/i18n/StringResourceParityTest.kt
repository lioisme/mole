package com.lioisme.modev.i18n

import android.content.res.Configuration
import android.os.LocaleList
import com.lioisme.modev.R
import java.util.Locale
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 定向测试(字符串资源,2026-08-18 V15):
 * 双语(en/zh)资源键全量互备 + 占位符参数形态一致(格式串在两种语言间
 * 不可出现参数数量/顺序/类型漂移 —— 否则运行期 NumberFormatException /
 * 占位丢失)。新键遗漏即失败,防「切换语言后部分文本不翻译」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringResourceParityTest {

    private val placeholders = Pattern.compile("""%(?:\d+\$)?[ds]""")

    /**
     * 双语必须一致的键(非缺失翻译):品牌名、专有名词(算法名/技术前缀)、
     * 语言自名、纯格式串。新增一致键须先确认是专有名词而非漏译。
     */
    private val IDENTICAL_ALLOWED = setOf(
        "app_name",
        "lang_en",
        "lang_zh",
        "filter_type_prefs",
        "kind_base64",
        "kind_mac",
        "op_prefs_get_string",
        "op_prefs_get_int",
        "op_prefs_get_long",
        "op_prefs_get_float",
        "op_prefs_get_boolean",
        "op_prefs_get_string_set",
        "op_prefs_get_all",
        "op_prefs_contains",
        "op_prefs_put_string",
        "op_prefs_put_int",
        "op_prefs_put_long",
        "op_prefs_put_float",
        "op_prefs_put_boolean",
        "op_prefs_put_string_set",
        "op_prefs_remove",
        "op_prefs_clear",
        "record_data_key",
        "record_data_alias",
        "record_data_out",
        "record_data_in",
        "section_iv",
        "section_repr_base64",
        "section_repr_hex",
        "version",
        // 纯占位符拼接格式串(locale 无关,V22 许可列表行;V23 补录豁免)
        "licenses_row_format",
        // 监听地址:port 行值(地址:端口,V26 参数化,locale 无关)
        "settings_port_value",
        // WebView 专有名词(V29)
        "filter_type_webview",
        "meta_url",
        "meta_bridge",
    )

    @Test
    fun allStringsPresentInBothLocales_withMatchingPlaceholders() {
        val app = RuntimeEnvironment.getApplication()
        val en = app.resources
        val zh = app.createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag("zh"))) },
        ).resources

        val fields = R.string::class.java.fields
        var checked = 0
        for (field in fields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers).not()) continue
            val id = field.getInt(null)
            val key = field.name

            val zhId = zh.getIdentifier(key, "string", app.packageName)
            assertTrue("key '$key' missing in values-zh (falls back to default)", zhId != 0)

            val enText = en.getString(id)
            val zhText = zh.getString(zhId)
            assertEquals(
                "placeholder mismatch for '$key': en=$enText zh=$zhText",
                placeholderSet(enText),
                placeholderSet(zhText),
            )
            if (key !in IDENTICAL_ALLOWED) {
                assertNotEquals(
                    "identical text for '$key' in en and zh (possible missing translation)",
                    enText,
                    zhText,
                )
            }
            checked++
        }
        assertTrue("no string resources found", checked > 0)
    }

    private fun placeholderSet(text: String): Set<String> {
        val matcher = placeholders.matcher(text)
        val out = mutableSetOf<String>()
        while (matcher.find()) out.add(matcher.group())
        return out
    }
}