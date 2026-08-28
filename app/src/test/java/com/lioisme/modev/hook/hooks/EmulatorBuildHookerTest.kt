package com.lioisme.modev.hook.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorBuildHookerTest {

    // ── isEmulatorIndicator (companion-level testable logic) ─

    @Test
    fun `generic fingerprints are detected`() {
        assertTrue(isEmulatorIndicator("generic_x86/sdk_phone_x86/12345"))
        assertTrue(isEmulatorIndicator("google_sdk/generic_x86:12"))
        assertTrue(isEmulatorIndicator("emulator emulator"))
    }

    @Test
    fun `emulator-specific fingerprints are detected`() {
        assertTrue(isEmulatorIndicator("Android/sdk_gphone64_x86_64/12/"))
        assertTrue(isEmulatorIndicator("vbox86p-userdebug 9"))
        assertTrue(isEmulatorIndicator("Genymotion-9.0.0"))
        assertTrue(isEmulatorIndicator("nox-userdebug 9"))
        assertTrue(isEmulatorIndicator("Bluestacks Build/1.0"))
        assertTrue(isEmulatorIndicator("Android SDK built for x86"))
    }

    @Test
    fun `real device fingerprints are NOT detected`() {
        assertFalse(isEmulatorIndicator("google/raven/raven:14/UP1A.231105.001/10754064:user/release-keys"))
        assertFalse(isEmulatorIndicator("Xiaomi/13/13:14/UP1A.231105.001/V816.0.23.0.UNACNXM:user/release-keys"))
    }

    // ── getRealisticValue consistency ────────────────────────

    @Test
    fun `generic emulator gets Pixel 7 Pro group`() {
        val fp = getRealisticValue("FINGERPRINT", "generic_x86")
        val model = getRealisticValue("MODEL", "generic_x86")
        val product = getRealisticValue("PRODUCT", "generic_x86")
        val mfr = getRealisticValue("MANUFACTURER", "generic_x86")

        assertEquals("Pixel 7 Pro", model)
        assertEquals("raven", product)
        assertEquals("Google", mfr)
        assertTrue(fp.startsWith("google/raven/"))
    }

    @Test
    fun `vbox emulator gets Xiaomi 13 group`() {
        val fp = getRealisticValue("FINGERPRINT", "vbox86p-userdebug 9")
        val model = getRealisticValue("MODEL", "vbox86p-userdebug 9")
        val product = getRealisticValue("PRODUCT", "vbox86p-userdebug 9")
        val mfr = getRealisticValue("MANUFACTURER", "vbox86p-userdebug 9")

        assertEquals("2211133C", model)
        assertEquals("fuxi", product)
        assertEquals("Xiaomi", mfr)
        assertTrue(fp.startsWith("Xiaomi/13/"))
    }

    @Test
    fun `unknown emulator values return original`() {
        val original = "some-random-value"
        assertEquals(original, getRealisticValue("FINGERPRINT", original))
        assertEquals(original, getRealisticValue("MODEL", original))
    }

    @Test
    fun `real device values return original`() {
        val original = "google/raven/raven:14/UP1A.231105.001/10754064:user/release-keys"
        assertEquals(original, getRealisticValue("FINGERPRINT", original))
    }

    // ── Extracted pure functions (mirror EmulatorBuildHooker companion) ──

    private val EMULATOR_KEYWORDS = listOf(
        "generic", "sdk", "emulator", "google_sdk",
        "goldfish", "ranchu", "vbox", "genymotion",
        "nox", "bluestacks", "ldnews",
    )

    private fun isEmulatorIndicator(value: String): Boolean {
        val lower = value.lowercase()
        return EMULATOR_KEYWORDS.any { lower.contains(it) }
    }

    private fun getRealisticValue(field: String, original: String): String {
        val lower = original.lowercase()
        return when {
            lower.contains("generic") || lower.contains("sdk") ||
                lower.contains("emulator") || lower.contains("goldfish") ||
                lower.contains("ranchu") -> when (field) {
                "FINGERPRINT" -> "google/raven/raven:14/UP1A.231105.001/10754064:userdebug/dev-keys"
                "MODEL" -> "Pixel 7 Pro"
                "PRODUCT" -> "raven"
                "MANUFACTURER" -> "Google"
                else -> original
            }
            lower.contains("vbox") || lower.contains("genymotion") -> when (field) {
                "FINGERPRINT" -> "Xiaomi/13/13:14/UP1A.231105.001/V816.0.23.0.UNACNXM:user/release-keys"
                "MODEL" -> "2211133C"
                "PRODUCT" -> "fuxi"
                "MANUFACTURER" -> "Xiaomi"
                else -> original
            }
            else -> original
        }
    }
}
