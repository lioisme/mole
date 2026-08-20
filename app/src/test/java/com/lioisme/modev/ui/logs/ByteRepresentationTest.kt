package com.lioisme.modev.ui.logs

import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 三态表示决策纯函数(V19 T5,对齐 SectionRenderModeTest 风格)。 */
class ByteRepresentationTest {

    private fun view(
        hex: String = "6162616261626162616261626162616261626162",
        base64: String? = "YWJhYmFiYWJhYmFiYWJhYmFiYWI=",
        text: String? = "abababababababababab",
    ) = BytesView(
        hex = hex,
        base64 = base64,
        text = text,
        totalLength = 20,
        truncated = false,
    )

    @Test
    fun available_fullTriState_orderIsTextBase64Hex() {
        assertEquals(
            listOf(ByteRepresentation.TEXT, ByteRepresentation.BASE64, ByteRepresentation.HEX),
            view().availableRepresentations(),
        )
    }

    @Test
    fun available_textMissing_hidesText() {
        assertEquals(
            listOf(ByteRepresentation.BASE64, ByteRepresentation.HEX),
            view(text = null).availableRepresentations(),
        )
    }

    @Test
    fun available_base64Missing_oldRecord_dualState() {
        assertEquals(
            listOf(ByteRepresentation.TEXT, ByteRepresentation.HEX),
            view(base64 = null).availableRepresentations(),
        )
    }

    @Test
    fun available_bothMissing_hexOnly() {
        assertEquals(
            listOf(ByteRepresentation.HEX),
            view(text = null, base64 = null).availableRepresentations(),
        )
    }

    @Test
    fun default_textPresent_prefersText() {
        assertEquals(ByteRepresentation.TEXT, view().defaultRepresentation())
    }

    @Test
    fun default_textMissing_fallsBackHex() {
        assertEquals(ByteRepresentation.HEX, view(text = null).defaultRepresentation())
    }

    @Test
    fun representationText_mapsPerRepresentation() {
        val v = view()
        assertEquals("abababababababababab", v.representationText(ByteRepresentation.TEXT))
        assertEquals("YWJhYmFiYWJhYmFiYWJhYmFiYWI=", v.representationText(ByteRepresentation.BASE64))
        assertEquals("6162616261626162616261626162616261626162", v.representationText(ByteRepresentation.HEX))
    }

    @Test
    fun representationText_missingRepresentation_returnsNull() {
        assertNull(view(text = null).representationText(ByteRepresentation.TEXT))
        assertNull(view(base64 = null).representationText(ByteRepresentation.BASE64))
    }

    @Test
    fun representationChars_countsSelectedRepresentation() {
        val v = view()
        assertEquals(20, v.representationChars(ByteRepresentation.TEXT))
        assertEquals(28, v.representationChars(ByteRepresentation.BASE64))
        assertEquals(40, v.representationChars(ByteRepresentation.HEX))
        // hex 恒为各表示上界(Base64 ≈ 4/3 字节数 ≤ 2 字符/字节)。
        assertEquals(40, v.representationChars(ByteRepresentation.HEX))
    }

    @Test
    fun representationChars_missing_returnsZero() {
        assertEquals(0, view(text = null).representationChars(ByteRepresentation.TEXT))
    }
}