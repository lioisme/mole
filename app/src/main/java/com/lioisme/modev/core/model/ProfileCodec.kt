package com.lioisme.modev.core.model

import com.lioisme.modev.core.codec.Json
import java.security.MessageDigest

/**
 * 画像编解码与规范化:JSON 正文、内容指纹、整表校验三件事的唯一实现处。
 *
 * **指纹的确定性来自 [ProfileFieldCatalog] 的字段序**,而不是 Gson 的字段序或
 * [DeviceProfile.toString] —— 三者都会随重构漂移。逐字段按目录序拼
 * `jsonKey=value` 再哈希,意味着「同一份画像内容永远得到同一指纹、
 * 内容任一字段变化必然改变指纹」,而这两条正是内置档升级判定的全部依据。
 *
 * 指纹**不含**画像名称与摘要(基线把名称算进指纹,于是切换语言会让「用户未改过」
 * 判定失真 —— 见 [DeviceProfileEntry] 的同条说明)。
 */
object ProfileCodec {

    /** 画像正文 → JSON。 */
    fun encode(profile: DeviceProfile): String = Json.gson.toJson(profile)

    /** JSON → 画像正文;不可解析返回 null(调用方须按「数据损坏」处理,禁回落默认档)。 */
    fun decode(json: String): DeviceProfile? = runCatching {
        Json.gson.fromJson(json, DeviceProfile::class.java)
    }.getOrNull()

    /**
     * 信封文本 → 条目列表。不可解析或版本不符返回 null。
     *
     * 字符上限先行拦截:超大包不得进入 Gson 解析(DoS 面)。
     */
    fun decodeEnvelope(text: String): ProfileEnvelope? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > ProfileEnvelope.MAX_IMPORT_CHARS) return null
        val envelope = runCatching {
            Json.gson.fromJson(trimmed, ProfileEnvelope::class.java)
        }.getOrNull() ?: return null
        if (envelope.schemaVersion != ProfileEnvelope.SCHEMA_VERSION) return null
        return envelope
    }

    /** 规范化文本:目录序逐字段 `jsonKey=value`,以换行分隔(零歧义,可直读比对)。 */
    fun canonicalText(profile: DeviceProfile): String = buildString {
        for (field in ProfileField.entries) {
            append(ProfileFieldCatalog.jsonKeyOf(field)).append('=').append(field.stringOf(profile)).append('\n')
        }
    }

    /** 内容指纹 = SHA-256(规范化文本),十六进制小写。 */
    fun fingerprint(profile: DeviceProfile): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalText(profile).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 整表校验:返回首个非法项(字段 + 错误);全合法返回 null。 */
    fun firstInvalid(profile: DeviceProfile): Pair<ProfileField, ProfileFieldError>? {
        for (field in ProfileField.entries) {
            val error = ProfileFieldCatalog.validate(field, field.stringOf(profile))
            if (error != null) return field to error
        }
        return null
    }

    /** 全部字段合法(可入库判定)。 */
    fun isValid(profile: DeviceProfile): Boolean = firstInvalid(profile) == null

    /** 两份画像内容是否等价(按规范化文本比对,与指纹同口径)。 */
    fun sameContent(a: DeviceProfile, b: DeviceProfile): Boolean =
        a.javaClass == b.javaClass && canonicalText(a) == canonicalText(b)
}
