package com.lioisme.modev.hook.hooks

import android.content.Intent
import android.os.BatteryManager
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * 画像强制档 · 电池读数一族(V49 WP-D2,基线 `BatteryHook` 同族)。
 *
 * 两个读口:
 *  - `BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)` —— 新 API 通路;
 *  - sticky `ACTION_BATTERY_CHANGED` Intent 的 `getIntExtra(EXTRA_LEVEL)` 与
 *    `getIntExtra(EXTRA_SCALE)`(恒 100,与画像的 0..100 同标度)。
 *
 * 与 [DeviceBatteryHooker] 的分工按**键空间**而非整族:画像只管电量百分比,充电计数器/
 * 电流/充电状态仍归条件档 —— 两边写的 prop id 不相交,因此无需整族让位(共享单一注册点后
 * 各订阅者只看自己的键,这正是 [SharedMethodBroadcast] 存在的理由)。
 *
 * `Intent.getIntExtra` 是**极热**方法(每个 Intent 读取都过),所以回调首句恒为门控,
 * 其后两次键比较,任一不命中立即返回(`HotPathGateConventionTest` 盯本文件)。
 */
class ProfileBatteryHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    /**
     * 节流集(V50):旧形态 `audited: Int?` 只按电量值去重 ⇒ 两条读口互相吞记录
     * (getIntProperty 先发过 80,则 sticky Intent 的 80 永不发射 = BATTERY_CHANGED
     * 换了操作名仍然恒零发射)。键 = `操作名:值`,每口各自节流。
     */
    private val audited = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun install() {
        subscribeIntProperty()
        subscribeIntentExtra()
    }

    private fun subscribeIntProperty() {
        val method = runCatching {
            BatteryManager::class.java.getMethod("getIntProperty", Int::class.javaPrimitiveType)
        }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!featureOn(DeviceEnvKind.PROFILE_BATTERY)) return
                    val level = targetLevel(config().feature.deviceProfile) ?: return
                    if (invocation.getArg(0) as? Int != BatteryManager.BATTERY_PROPERTY_CAPACITY) return
                    val current = result as? Int ?: return
                    if (current != level) invocation.setResult(level)
                    audit(level, original = current.toString(), target = "BatteryManager.getIntProperty")
                }
            },
        )
    }

    private fun subscribeIntentExtra() {
        val method = runCatching {
            Intent::class.java.getMethod("getIntExtra", String::class.java, Int::class.javaPrimitiveType)
        }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!featureOn(DeviceEnvKind.PROFILE_BATTERY)) return
                    val key = invocation.getArg(0) as? String ?: return
                    if (key != BatteryManager.EXTRA_LEVEL && key != BatteryManager.EXTRA_SCALE) return
                    val level = targetLevel(config().feature.deviceProfile) ?: return
                    val value = if (key == BatteryManager.EXTRA_LEVEL) level else SCALE
                    val current = result as? Int ?: return
                    if (current != value) invocation.setResult(value)
                    audit(
                        level,
                        original = current.toString(),
                        target = "Intent.getIntExtra($key)",
                        operation = DeviceEnvOperation.BATTERY_CHANGED,
                    )
                }
            },
        )
    }

    /**
     * 审计发射。操作名按**读口**分派(V50 孤儿整改):sticky `ACTION_BATTERY_CHANGED` 的
     * extras 读取是一条独立且靶程序更常用的通路(旧 `DeviceBatteryHooker` 的类注释至今
     * 广告着这条通路),此前两条读口都发 BATTERY_WRITE ⇒ 枚举里的 BATTERY_CHANGED
     * 恒零发射、筛选恒空。分派后「改的是哪张读口」在记录里可读。
     */
    private fun audit(
        level: Int,
        original: String,
        target: String,
        operation: DeviceEnvOperation = DeviceEnvOperation.BATTERY_WRITE,
    ) {
        if (!audited.add("${operation.name}:$level")) return
        emit(
            kind = DeviceEnvKind.PROFILE_BATTERY,
            operation = operation,
            originalValue = original,
            maskedValue = "$target -> $level",
            targetClass = "BatteryManager",
            targetProperty = target,
            interventionAction = ACTION_BLOCKED,
        )
    }

    companion object {
        /** `EXTRA_SCALE` 恒改写为 100:画像的电量本身就是百分比(基线同判)。 */
        const val SCALE = 100
        private const val ACTION_BLOCKED = "BLOCKED"

        /**
         * 目标电量(纯函数):未填 ⇒ null。界 = catalog 的 `0..BATTERY_MAX`,不另抄一份。
         * 填 0 是合法意图(「电量耗尽」),所以判据是 `isSetIn` 而不是「大于 0」。
         */
        internal fun targetLevel(profile: DeviceProfile): Int? {
            if (!ProfileField.BATTERY_LEVEL.isSetIn(profile)) return null
            val range = requireNotNull(ProfileFieldCatalog.rangeOf(ProfileField.BATTERY_LEVEL))
            return profile.batteryLevel.takeIf { it in range }
        }
    }
}
