package com.lioisme.modev.core.model

import com.lioisme.modev.core.codec.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V47 T2 画像编解码与内容指纹:确定性、敏感性、信封版本闸。
 *
 * 指纹是内置档升级判定的全部依据 —— 它若随字段写入顺序漂移,升级就会覆盖用户改过的档;
 * 若对内容变化不敏感,升级就不再补档。两条都在本类锁死。
 */
class ProfileCodecTest {

    private val pixel = BuiltinDeviceProfiles.specFor(BuiltinDeviceProfiles.PIXEL_7_PRO)!!
        .profile

    @Test
    fun `canonical text covers every catalog field in catalog order`() {
        val lines = ProfileCodec.canonicalText(pixel).trimEnd('\n').lines()
        assertEquals(ProfileField.entries.size, lines.size)
        assertEquals(
            ProfileFieldCatalog.jsonKeyOf(ProfileField.entries.first()),
            lines.first().substringBefore('='),
        )
        assertEquals("brand=google", lines.first())
    }

    @Test
    fun `fingerprint is independent of the order fields were written in`() {
        val a = ProfileField.BRAND.withText(DeviceProfile.EMPTY, "google")
        val b = ProfileField.MODEL.withText(DeviceProfile.EMPTY, "Pixel 7 Pro")
        val merged1 = ProfileField.MODEL.withText(a, "Pixel 7 Pro")
        val merged2 = ProfileField.BRAND.withText(b, "google")
        assertEquals(merged1, merged2)
        assertEquals(ProfileCodec.fingerprint(merged1), ProfileCodec.fingerprint(merged2))
        assertTrue(ProfileCodec.sameContent(merged1, merged2))
    }

    @Test
    fun `fingerprint changes when any single field changes`() {
        val base = ProfileCodec.fingerprint(pixel)
        for (field in ProfileField.entries) {
            val perturbed = perturb(field, field.stringOf(pixel))
            assertNotEquals(
                "$field 的变化未改变指纹(扰动值 $perturbed 回落到了缺省态)",
                base,
                ProfileCodec.fingerprint(field.withText(pixel, perturbed)),
            )
        }
    }

    /**
     * 按取值形态造一个**必然生效**的扰动值:BOOL 必须给 "true"、DOUBLE 必须给可解析小数 ——
     * 否则 withText 会回落缺省哨兵,指纹不变,测的就不是「内容变 ⇒ 指纹变」。
     */
    private fun perturb(field: ProfileField, current: String): String = when (field.type) {
        ProfileValueType.BOOL -> if (current == "true") "" else "true"
        ProfileValueType.INT,
        ProfileValueType.NETWORK_TYPE,
        ProfileValueType.SCREENSHOT_MODE,
        -> ((current.toIntOrNull() ?: 0) + 1).toString()

        ProfileValueType.DOUBLE -> ((current.toDoubleOrNull() ?: 0.0) + 0.5).toString()
        ProfileValueType.TEXT -> "${current}x"
    }

    @Test
    fun `json round trip preserves every field`() {
        val decoded = ProfileCodec.decode(ProfileCodec.encode(pixel))
        assertEquals(pixel, decoded)
    }

    @Test
    fun `decode tolerates unknown keys but rejects malformed json`() {
        val encoded = ProfileCodec.encode(pixel)
        val withExtra = encoded.dropLast(1) + ",\"noSuchKey\":\"whatever\"}"
        assertNull(ProfileCodec.decode("not json at all"))
        assertEquals(
            "未知键必须被忽略(前向兼容:新客户端写出的档在旧客户端仍可解析)",
            pixel,
            ProfileCodec.decode(withExtra),
        )
    }

    @Test
    fun `invalid profiles are rejected before write`() {
        assertNull(ProfileCodec.firstInvalid(DeviceProfile.EMPTY))
        val badImei = ProfileField.IMEI.withText(DeviceProfile.EMPTY, "490171581696171")
        assertEquals(ProfileField.IMEI, ProfileCodec.firstInvalid(badImei)?.first)
        assertEquals(ProfileFieldError.IMEI_FORMAT, ProfileCodec.firstInvalid(badImei)?.second)
        assertFalse(ProfileCodec.isValid(badImei))
        assertTrue(ProfileCodec.isValid(pixel))
    }

    @Test
    fun `envelope gate enforces schema version and size caps`() {
        val good = ProfileEnvelope(items = listOf(ProfileTransferItem("n", "s", pixel)))
        val text = Json.gson.toJson(good)
        assertEquals(good.items.size, ProfileCodec.decodeEnvelope(text)?.items?.size)
        assertNull("版本不符必须整包拒绝", ProfileCodec.decodeEnvelope(text.replace("\"schemaVersion\":1", "\"schemaVersion\":99")))
        assertNull("空文本拒绝", ProfileCodec.decodeEnvelope("   "))
        assertNull("超限文本不进解析", ProfileCodec.decodeEnvelope("x".repeat(ProfileEnvelope.MAX_IMPORT_CHARS + 1)))
    }
}
