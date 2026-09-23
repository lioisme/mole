package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext

/**
 * 画像强制档 · `android_id`(V49 WP-D3,基线 `UniquelyIdHook` 同族)。
 *
 * 四条读口,一条都不能少:`Settings.Secure.getString` / `getStringForUser` 与
 * `Settings.System.getString` / `getStringForUser`(基线两条 `getStringForUser`,
 * Modev 条件档只有 Secure 一条 ⇒ 走 System 读 android_id 的检测项此前无人管)。
 *
 * 键空间只有 `android_id` 一项 ⇒ 按**键**分工:本类只碰这一个键,其他 Setting 键恒放行;
 * 条件档 [EmulatorNetworkHooker] 在同一键上让位(画像填了就以画像为准)。
 */
class ProfileAndroidIdHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private fun profileValue(): String? {
        val profile = config().feature.deviceProfile
        if (!ProfileField.ANDROID_ID.isSetIn(profile)) return null
        return ProfileField.ANDROID_ID.stringOf(profile).lowercase()
    }

    override fun install() {
        for (table in listOf(SECURE, SYSTEM)) {
            hookReturn(
                KIND, OP, table, "getString",
                resolverType, stringType,
            ) { keyed(it, 1, profileValue()) }
            hookReturn(
                KIND, OP, table, "getStringForUser",
                resolverType, stringType, intType,
            ) { keyed(it, 1, profileValue()) }
        }
    }

    /** 只有 [keyIndex] 那个参数等于 `android_id` 时才改写返回值。 */
    private fun keyed(invocation: Invocation, keyIndex: Int, value: String?): String? =
        if (invocation.getArg(keyIndex) as? String == ANDROID_ID_KEY) value else null

    private companion object {
        val KIND = DeviceEnvKind.PROFILE_ANDROID_ID
        val OP = DeviceEnvOperation.ANDROID_ID_WRITE
        const val SECURE = "android.provider.Settings\$Secure"
        const val SYSTEM = "android.provider.Settings\$System"
        const val ANDROID_ID_KEY = "android_id"
        val resolverType: Class<*> = android.content.ContentResolver::class.java
        val stringType: Class<*> = String::class.java
        val intType: Class<*> = Int::class.javaPrimitiveType!!
    }
}
