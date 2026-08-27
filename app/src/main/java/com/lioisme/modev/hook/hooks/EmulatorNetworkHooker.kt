package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.util.Random

class EmulatorNetworkHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val seed = packageName.hashCode().toLong()
    private val random = Random(seed)

    private val stableMac by lazy { generateStableMac() }
    private val stableAndroidId by lazy { generateStableAndroidId() }

    override fun install() {
        hookGetMacAddress()
        hookAndroidId()
    }

    private fun hookGetMacAddress() {
        val clazz = runCatching { Class.forName("android.net.wifi.WifiInfo") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getMacAddress") }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_NETWORK)) return
                val original = invocation.result as? String ?: return
                if (isEmulatorMac(original)) {
                    invocation.setResult(stableMac)
                    emit(
                        kind = DeviceEnvKind.EMULATOR_NETWORK,
                        operation = DeviceEnvOperation.GET_MAC_ADDRESS,
                        originalValue = original,
                        maskedValue = stableMac,
                    )
                }
            }
        })
    }

    private fun hookAndroidId() {
        val clazz = runCatching { Class.forName("android.provider.Settings\$Secure") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getString", android.content.ContentResolver::class.java, String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_NETWORK)) return
                val key = invocation.getArg(1) as? String ?: return
                if (key != ANDROID_ID_KEY) return
                val original = invocation.result as? String ?: return
                if (isEmulatorAndroidId(original)) {
                    invocation.setResult(stableAndroidId)
                    emit(
                        kind = DeviceEnvKind.EMULATOR_NETWORK,
                        operation = DeviceEnvOperation.ANDROID_ID,
                        originalValue = original,
                        maskedValue = stableAndroidId,
                    )
                }
            }
        })
    }

    private fun isEmulatorMac(mac: String): Boolean {
        if (mac.isBlank() || mac == "02:00:00:00:00:00") return true
        val lower = mac.lowercase()
        return EMULATOR_MAC_PREFIXES.any { lower.startsWith(it) }
    }

    private fun isEmulatorAndroidId(id: String?): Boolean {
        if (id.isNullOrBlank()) return true
        val lower = id.lowercase()
        return EMULATOR_ANDROID_ID_PATTERNS.any { lower.startsWith(it) }
    }

    private fun generateStableMac(): String {
        val r = Random(seed + 1)
        return MAC_PREFIX + List(3) { "%02x".format(r.nextInt(256)) }.joinToString(":")
    }

    private fun generateStableAndroidId(): String {
        val r = Random(seed + 2)
        return buildString {
            repeat(16) { append("%02x".format(r.nextInt(256))) }
        }
    }

    companion object {
        private const val ANDROID_ID_KEY = "android_id"
        private const val MAC_PREFIX = "02:42:ac:11:"

        private val EMULATOR_MAC_PREFIXES = listOf(
            "00:00:00", "08:00:27", "52:54:00", "02:42:ac",
        )

        private val EMULATOR_ANDROID_ID_PATTERNS = listOf(
            "unknown", "emulator",
        )
    }
}
