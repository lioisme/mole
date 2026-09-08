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
 * 定向测试(字符串资源,2026-08-18 V15;V42 T5 四语升级):
 * 全部支持 locale(en 默认 + zh/ja/ko)资源键全量互备 + 占位符参数形态一致
 * (格式串在任意两语言间不可出现参数数量/顺序/类型漂移 —— 否则运行期
 * NumberFormatException / 占位丢失)。新键遗漏即失败,防「切换语言后部分
 * 文本不翻译」。V42 增 plurals 逐 locale 标识符断言(缺失会静默回落 en)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringResourceParityTest {

    private val placeholders = Pattern.compile("""%(?:\d+\$)?,?[ds]""")

    /** 非 en locale 集合(与 LocaleManager.SUPPORTED_TAGS / locales_config.xml 同表)。 */
    private val nonEnLocaleTags = listOf("zh", "ja", "ko")

    /**
     * 双语必须一致的键(非缺失翻译):品牌名、专有名词(算法名/技术前缀)、
     * 语言自名、纯格式串。新增一致键须先确认是专有名词而非漏译。
     * V42:该列表对 zh/ja/ko 三语共用 —— 亦即 ja/ko 与 en 同文仅限本列表
     * (生成器 scripts/v42_generate.mjs 同源,例外 = confirm 的 "OK")。
     */
    private val IDENTICAL_ALLOWED = setOf(
        "app_name",
        "lang_en",
        "lang_zh",
        "lang_ja",
        "lang_ko",
        // 日韩语确认键即 "OK"(生成器 IDENTICAL_OK 同源)
        "confirm",
        "feature_profile_xiaomi13",
        "filter_type_prefs",
        "kind_base64",
        "kind_mac",
        // V37:机型品牌名双语同形(专有名词,非漏翻)
        "feature_profile_pixel7pro",
        "feature_profile_xiaomi13",
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
        // V33:技术/品牌名保留拉丁
        "kind_system_LOGCAT",
        "op_process_system_exit",
        "op_process_kill_process",
        // V35:API/方法名保留拉丁
        "op_device_env_sys_prop_get",
        "op_device_env_proc_status_read",
        "op_device_env_proc_mount_read",
        "op_device_env_proc_cgroup_read",
        "op_device_env_proc_net_read",
        "op_device_env_settings_global_get",
        "op_device_env_settings_secure_get",
        "op_device_env_content_query_settings",
        "op_device_env_phone_device_id",
        "op_device_env_phone_imei",
        "op_device_env_phone_subscriber_id",
        "op_device_env_phone_sim_serial",
        "op_device_env_phone_line1_number",
        "op_device_env_cpu_available_processors",
        "op_device_env_cpu_proc_info",
        "op_device_env_cpu_sysconf",
        "op_device_env_battery_int_property",
        "op_device_env_battery_is_charging",
        "op_device_env_battery_changed",
        "op_device_env_build_device",
        "op_device_env_build_board",
        "op_device_env_build_hardware",
        "op_device_env_build_brand",
        "op_device_env_build_host",
        "op_device_env_build_user",
        "op_device_env_build_display",
        "op_device_env_build_serial",
        "op_device_env_build_type",
    )

    private fun localizedResources(tag: String) =
        RuntimeEnvironment.getApplication().createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) },
        ).resources

    @Test
    fun allStringsPresentInAllLocales_withMatchingPlaceholders() {
        val app = RuntimeEnvironment.getApplication()
        val en = app.resources
        val localized = nonEnLocaleTags.associateWith { tag -> localizedResources(tag) }

        val fields = R.string::class.java.fields
        var checked = 0
        for (field in fields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers).not()) continue
            val id = field.getInt(null)
            val key = field.name

            val enText = en.getString(id)
            for ((tag, res) in localized) {
                val resId = res.getIdentifier(key, "string", app.packageName)
                assertTrue("key '$key' missing in values-$tag (falls back to default)", resId != 0)

                val text = res.getString(resId)
                assertEquals(
                    "placeholder mismatch for '$key' ($tag): en=$enText $tag=$text",
                    placeholderSet(enText),
                    placeholderSet(text),
                )
                if (key !in IDENTICAL_ALLOWED) {
                    assertNotEquals(
                        "identical text for '$key' in en and $tag (possible missing translation)",
                        enText,
                        text,
                    )
                }
            }
            checked++
        }
        assertTrue("no string resources found", checked > 0)
    }

    /** plurals 逐 locale 存在性(V42 T5):缺失会静默回落 en,必须显式拦截。 */
    @Test
    fun allPluralsPresentInAllLocales() {
        val app = RuntimeEnvironment.getApplication()
        val localized = nonEnLocaleTags.associateWith { tag -> localizedResources(tag) }

        val pluralsFields = R.plurals::class.java.fields
        assertTrue("no plurals resources found", pluralsFields.isNotEmpty())
        for (field in pluralsFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers).not()) continue
            val key = field.name
            for ((tag, res) in localized) {
                val resId = res.getIdentifier(key, "plurals", app.packageName)
                assertTrue("plurals '$key' missing in values-$tag", resId != 0)
            }
        }
    }

    private fun placeholderSet(text: String): Set<String> {
        val matcher = placeholders.matcher(text)
        val out = mutableSetOf<String>()
        while (matcher.find()) out.add(matcher.group())
        return out
    }
}
