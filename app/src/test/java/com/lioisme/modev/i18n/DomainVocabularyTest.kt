package com.lioisme.modev.i18n

import android.content.res.Configuration
import android.os.LocaleList
import com.lioisme.modev.R
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 定向测试(V48 R-1 / R-2 词汇域一致性):
 * 「作用域」是本产品最要害的一个布尔量,它同时管辖两域(被动采集 + 主动干预)的全部开关。
 * V48-DFC-01 的失败形态就是把这个动作称作「监听」—— 那既误导用户(以为只关监听),
 * 又违反 `AGENTS.md` 词汇表的禁用旧词红线。
 *
 * 判据取**用户可见结果**(四语实际渲染的文本),不取「某个键名是否存在」这类代理指标。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DomainVocabularyTest {

    private val app by lazy { RuntimeEnvironment.getApplication() }

    /** 与 `StringResourceParityTest` 同法取指定 locale 的资源。 */
    private fun text(tag: String, key: String): String {
        val res =
            if (tag == "en") {
                app.resources
            } else {
                app.createConfigurationContext(
                    Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) },
                ).resources
            }
        val id = res.getIdentifier(key, "string", app.packageName)
        assertTrue("string '$key' absent", id != 0)
        return res.getString(id)
    }

    /**
     * 四语禁止形态:把「作用域」这个动作说成监听/监视(它同时关掉全部干预功能)。
     * 逐 locale 给禁用词,是因为该漂移正是「只改一语」造成的(键名与 en 都对、zh 却仍是旧词)。
     */
    private val bannedScopeWords =
        mapOf(
            "en" to Regex("(?i)monitor|listen|watch|log"),
            "zh" to Regex("监听|监视|观测|日志"),
            "ja" to Regex("監視|监听"),
            "ko" to Regex("모니터|감시|로그"),
        )

    @Test
    fun scopeSwitchNeverCallsScopingListening_inAnyLocale() {
        bannedScopeWords.forEach { (tag, banned) ->
            val rendered = text(tag, "app_monitor_scope_row")
            assertFalse("[$tag] scoping must not be named as listening: $rendered", rendered.contains(banned))
        }
    }

    /** 正名断言(逐字):更名必须是「作用域」这一动作,而不是随手换一个近义词。 */
    @Test
    fun scopeSwitchUsesTheAgreedFourLocaleWordings() {
        assertTrue(text("zh", "app_monitor_scope_row").contains("作用域"))
        assertTrue(text("en", "app_monitor_scope_row").contains("Scope"))
    }

    /**
     * 同一个布尔量的两种呈现必须同名词:开关行说「作用域」而状态徽章说「已监听」
     * 会让用户以为存在两件事(V48 审计登记的 detail_listening 键名/值漂移残留)。
     */
    @Test
    fun scopeStatePillsShareTheScopeNoun() {
        assertTrue(text("zh", "detail_listening").contains("作用域"))
        assertTrue(text("zh", "detail_not_listening").contains("作用域"))
        assertFalse(text("zh", "detail_listening").contains("监听"))
        assertFalse(text("zh", "detail_not_listening").contains("监听"))
    }

    /** 四语全表(与 `LocaleManager.SUPPORTED_TAGS` 同集)。 */
    private val localeTags = listOf("en", "zh", "ja", "ko")

    /**
     * V48-DFC-02:两个域页必须**在四种语言里都可区分**,且被动采集页不许占「功能」一词。
     * 更名前的实锤形态:`listen_settings_entry` 与 `feature_settings_entry` 的 ja/ko 值
     * 逐字符相同(機能設定 / 기능 설정)—— 两屏在两种语言里就是同一个名字。
     */
    @Test
    fun theTwoDomainPagesHaveDistinctNamesInEveryLocale() {
        localeTags.forEach { tag ->
            val listen = text(tag, "listen_settings_entry")
            val feature = text(tag, "feature_settings_entry")
            assertNotEquals("[$tag] two different pages rendered one name: $listen", listen, feature)
        }
        val zhListen = text("zh", "listen_settings_entry")
        assertTrue("listen page must own 监听: $zhListen", zhListen.contains("监听"))
        assertFalse("listen page must not borrow 功能: $zhListen", zhListen.contains("功能"))
        assertTrue(text("zh", "feature_settings_entry").contains("功能"))
    }

    /**
     * 两页的「恢复默认配置」确认必须是同一动作的对称句(同动词、同句式,只换域词)。
     * 旧形态一个是「将…全部功能配置恢复为默认」、另一个是「清除…全部功能自定义并恢复默认」,
     * 读起来像两件不相关的操作,而实现是按域清除的一对镜像(`resetOverride(pkg, LISTEN/FEATURE)`)。
     */
    @Test
    fun resetConfirmationsAreSymmetricPerDomain() {
        val listen = text("zh", "danger_reset_listen_msg")
        val feature = text("zh", "danger_reset_feature_msg")
        val suffix = "恢复为默认?"
        assertTrue("listen: $listen", listen.startsWith("将 ") && listen.endsWith(suffix))
        assertTrue("feature: $feature", feature.startsWith("将 ") && feature.endsWith(suffix))
        assertTrue(listen.contains("监听设置"))
        assertTrue(feature.contains("功能设置"))
    }

    /** 空列表提示必须同时指向两页(10 类里 4 类归功能页管,只指监听页 = 死胡同)。 */
    @Test
    fun emptyListHintPointsAtBothDomainPages() {
        val hint = text("zh", "detail_empty_hint")
        assertTrue("hint must name both pages: $hint", hint.contains("监听设置") && hint.contains("功能设置"))
    }
}
