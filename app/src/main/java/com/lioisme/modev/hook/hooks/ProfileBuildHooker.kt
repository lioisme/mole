package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.hook.ProbeContext

/**
 * 画像强制档 · `android.os.Build` 一族(V49 WP-D1)。
 *
 * 与 [EmulatorBuildHooker] 的分工是**两种不同语义**,不合并:
 *  - 条件档:仅在检出模拟器脏值时掩蔽,值来自内置基线;
 *  - 本类:画像**填了哪一项就无条件写哪一项**,值源恒为 `feature.deviceProfile`
 *    (宿主侧已把「所绑画像 ⊕ 该应用覆写」展开成快照下发,注入侧永不回查画像库)。
 *
 * 三条约束:
 *  1. **空值零副作用**:该项未填 ⇒ 一个字段都不碰。这是本组开关可以默认开的前提 ——
 *     「填了」才是意图,开关只是撤销手段;
 *  2. **改配置可重放**:不用一次性 `AtomicBoolean`。用户改画像会推来新快照,一次性标记
 *     会让「改了但没生效」成为常态;这里按字段记 `lastApplied`,值没变不动手、动了手才发记录;
 *  3. **写是否被 ART 接受,JVM 档不可证**:`Build` 的静态字段是 `static final`,
 *     Robolectric/JVM 的反射语义与 ART 不同,所以本类把「该写什么」([plannedWrites],纯函数)
 *     与「怎么写」(反射)分开 —— 前者逐字段直测,后者由 WP-F 设备档在真靶程序里判决。
 *     写失败(反射抛异常)时**不发记录**:宁缺条目,也不留下「自称改了」的假审计。
 */
class ProfileBuildHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    /** 字段全名 → 上次成功写进去的值(幂等与「变了才发记录」的判据)。 */
    private val lastApplied = HashMap<String, String>()

    override fun install() {
        applyOnce()
        configWatcher.register { applyOnce() }
    }

    /** 可被配置变更反复调用:逐项按计划落地,门控关的那一族一项都不写。 */
    internal fun applyOnce() {
        for (write in plannedWrites(config().feature.deviceProfile)) {
            if (!featureOn(write.kind)) continue
            apply(write)
        }
    }

    /**
     * 计划表(纯函数,零反射零 Android):画像填了哪几项 ⇒ 就该写哪几项。
     * 顺序恒为「身份八项 → Build.ID → RELEASE → SDK_INT」,与 [ProfileField] 声明序同源。
     */
    internal fun plannedWrites(profile: DeviceProfile): List<BuildWrite> = buildList {
        for ((field, name) in IDENTITY_FIELDS) {
            if (!field.isSetIn(profile)) continue
            add(BuildWrite(BUILD_CLASS, name, field.stringOf(profile), KIND_IDENTITY, OP_IDENTITY))
        }
        for ((field, name) in VERSION_STRING_FIELDS) {
            if (!field.isSetIn(profile)) continue
            add(BuildWrite(VERSION_CLASS, name, field.stringOf(profile), KIND_VERSION, OP_VERSION))
        }
        if (ProfileField.SDK_INT.isSetIn(profile)) {
            ProfileField.SDK_INT.stringOf(profile).toIntOrNull()?.let { sdk ->
                add(
                    BuildWrite(
                        owner = VERSION_CLASS,
                        fieldName = "SDK_INT",
                        value = sdk.toString(),
                        kind = KIND_VERSION,
                        operation = OP_VERSION,
                        integral = true,
                    ),
                )
            }
        }
    }

    /** 单项落地:当前值已等于期望值则不动手;动手失败不留记录、不记 lastApplied(下次仍会重试)。 */
    private fun apply(write: BuildWrite) {
        val key = "${write.owner}.${write.fieldName}"
        if (lastApplied[key] == write.value) return
        val clazz = runCatching { Class.forName(write.owner) }.getOrNull() ?: return
        val javaField = runCatching { clazz.getDeclaredField(write.fieldName) }.getOrNull() ?: return
        javaField.isAccessible = true
        val receiver: Any? = null
        val original: String?
        val changed: Boolean
        if (write.integral) {
            val desired = write.value.toIntOrNull() ?: return
            val current = runCatching { javaField.getInt(receiver) }.getOrNull()
            original = current?.toString()
            if (current == desired) {
                lastApplied[key] = write.value
                return
            }
            changed = runCatching { javaField.setInt(receiver, desired) }.isSuccess
        } else {
            val current = runCatching { javaField.get(receiver) as? String }.getOrNull()
            original = current
            if (current == write.value) {
                lastApplied[key] = write.value
                return
            }
            changed = runCatching { javaField.set(receiver, write.value) }.isSuccess
        }
        if (!changed) return
        lastApplied[key] = write.value
        emit(
            kind = write.kind,
            operation = write.operation,
            originalValue = original,
            maskedValue = write.value,
            targetProperty = key,
            interventionAction = ACTION_BLOCKED,
        )
    }

    /** 一条计划写入(归属类别与操作随行携带,门控判定因此不需要回查表)。 */
    internal data class BuildWrite(
        val owner: String,
        val fieldName: String,
        val value: String,
        val kind: DeviceEnvKind,
        val operation: DeviceEnvOperation,
        val integral: Boolean = false,
    )

    private companion object {
        const val ACTION_BLOCKED = "BLOCKED"
        const val BUILD_CLASS = "android.os.Build"
        const val VERSION_CLASS = "android.os.Build\$VERSION"
        val KIND_IDENTITY = DeviceEnvKind.PROFILE_BUILD_IDENTITY
        val KIND_VERSION = DeviceEnvKind.PROFILE_BUILD_VERSION
        val OP_IDENTITY = DeviceEnvOperation.BUILD_IDENTITY_WRITE
        val OP_VERSION = DeviceEnvOperation.BUILD_VERSION_WRITE

        /** 身份八项(= `ProfileKind.PROFILE_BUILD_IDENTITY` 承载的全部字段)。 */
        val IDENTITY_FIELDS: Map<ProfileField, String> = linkedMapOf(
            ProfileField.BRAND to "BRAND",
            ProfileField.MANUFACTURER to "MANUFACTURER",
            ProfileField.MODEL to "MODEL",
            ProfileField.PRODUCT to "PRODUCT",
            ProfileField.DEVICE to "DEVICE",
            ProfileField.BOARD to "BOARD",
            ProfileField.HARDWARE to "HARDWARE",
            ProfileField.FINGERPRINT to "FINGERPRINT",
        )

        /** 版本族里的两个字符串字段(`SDK_INT` 另走 setInt)。 */
        val VERSION_STRING_FIELDS: Map<ProfileField, String> = linkedMapOf(
            ProfileField.BUILD_ID to "ID",
            ProfileField.ANDROID_VERSION to "RELEASE",
        )
    }
}
