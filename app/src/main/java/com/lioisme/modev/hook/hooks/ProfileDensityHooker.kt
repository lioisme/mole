package com.lioisme.modev.hook.hooks

import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Display
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast
import kotlin.math.roundToInt

/**
 * 画像强制档 · 屏幕密度一族(V49 WP-D2,基线 `DisplayDensityHook` 同族)。
 *
 * 值源恒为 `feature.deviceProfile.densityDpi`:填了就把**每一处**密度读数改成它。
 * 密度此前零钩子(没有条件档可让位),所以本类是该族的唯一写入方。
 *
 * 三条实现约束:
 *  1. **热路径门控先行**:`getConfiguration`/`getDisplayMetrics` 在视图装配期被反复读,
 *     回调首句恒为 `featureOn`,其后才碰画像值(`HotPathGateConventionTest` 盯本文件);
 *  2. **单一注册点**:四个读口全经 [SharedMethodBroadcast.subscribe] —— 同一方法二次注册
 *     在 Vector 上静默失声(AGENTS「同一方法的第二个 hook 不执行」),而 `getConfiguration`
 *     本仓已有他人订阅;
 *  3. **就地改对象而非换返回值**:`DisplayMetrics`/`Configuration` 是框架缓存的实例,换返回值
 *     只会让调用方手里的旧引用仍是真值。两个 `applyDensity` 均**幂等**,所以每帧重跑也不会
 *     把 `screenWidthDp` 越缩越小。
 *
 * 留痕按「读口 + 值」去重:每帧一条会把记录管道刷满,而需要留痕的事实是「这一路被改成了这个值」。
 */
class ProfileDensityHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val audited = HashMap<String, Int>()

    override fun install() {
        applySystemResources()
        configWatcher.register { applySystemResources() }
        subscribe(Resources::class.java, "getDisplayMetrics", fromArg = null)
        subscribe(Resources::class.java, "getConfiguration", fromArg = null)
        subscribe(Display::class.java, "getMetrics", fromArg = 0)
        subscribe(Display::class.java, "getRealMetrics", fromArg = 0)
    }

    private fun subscribe(clazz: Class<*>, methodName: String, fromArg: Int?) {
        val method = runCatching {
            if (fromArg == null) clazz.getMethod(methodName)
            else clazz.getMethod(methodName, DisplayMetrics::class.java)
        }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!featureOn(DeviceEnvKind.PROFILE_DENSITY)) return
                    val dpi = targetDpi(config().feature.deviceProfile) ?: return
                    val target = if (fromArg == null) result
                    else runCatching { invocation.getArg(fromArg) }.getOrNull()
                    when (target) {
                        is DisplayMetrics -> applyDensity(target, dpi)
                        is Configuration -> applyDensity(target, dpi)
                        else -> return
                    }
                    audit(methodName, dpi)
                }
            },
        )
    }

    /** 框架单例 `Resources.getSystem()` 的两处缓存(基线同一动作)。 */
    internal fun applySystemResources() {
        if (!featureOn(DeviceEnvKind.PROFILE_DENSITY)) return
        val dpi = targetDpi(config().feature.deviceProfile) ?: return
        val system = runCatching { Resources.getSystem() }.getOrNull() ?: return
        runCatching { applyDensity(system.displayMetrics, dpi) }
        runCatching { applyDensity(system.configuration, dpi) }
        audit(SOURCE_SYSTEM, dpi)
    }

    private fun audit(source: String, dpi: Int) {
        if (audited[source] == dpi) return
        audited[source] = dpi
        emit(
            kind = DeviceEnvKind.PROFILE_DENSITY,
            operation = DeviceEnvOperation.DENSITY_WRITE,
            maskedValue = dpi.toString(),
            targetClass = "DisplayMetrics",
            targetProperty = source,
            interventionAction = ACTION_BLOCKED,
        )
    }

    companion object {
        const val SOURCE_SYSTEM = "resources.system"
        private const val ACTION_BLOCKED = "BLOCKED"

        /** `DENSITY_DEFAULT` 的数值(平台常量已弃用,直写基准 160 dpi)。 */
        private const val DENSITY_DPI_BASE = 160f

        /**
         * 目标密度(纯函数):未填 ⇒ null(一个字段都不碰)。
         * 界恒取 `ProfileFieldCatalog.rangeOf` —— **不在本文件另抄 72..1000**,否则会出现
         * 「存得进画像、钩子拒写」的静默分叉;取不到界 = 表被改坏,当场抛而不是静默失效。
         */
        internal fun targetDpi(profile: DeviceProfile): Int? {
            if (!ProfileField.DENSITY_DPI.isSetIn(profile)) return null
            val range = requireNotNull(ProfileFieldCatalog.rangeOf(ProfileField.DENSITY_DPI))
            return profile.densityDpi.takeIf { it in range }
        }

        /** dpi 直写,`density` 由 dpi 推导,字体缩放比保留(基线同式)。 */
        internal fun applyDensity(metrics: DisplayMetrics, dpi: Int) {
            val fontScale = if (metrics.density > 0f) metrics.scaledDensity / metrics.density else 1f
            metrics.densityDpi = dpi
            metrics.density = dpi / DENSITY_DPI_BASE
            metrics.scaledDensity = metrics.density * fontScale
        }

        /** 已是目标值时整段跳过(dp 缩放只在真换 dpi 的那一次发生)。 */
        internal fun applyDensity(configuration: Configuration, dpi: Int) {
            val original = configuration.densityDpi
            if (original > 0 && original != dpi) {
                val scale = original.toFloat() / dpi
                configuration.screenWidthDp = configuration.screenWidthDp.scaledBy(scale)
                configuration.screenHeightDp = configuration.screenHeightDp.scaledBy(scale)
                configuration.smallestScreenWidthDp = configuration.smallestScreenWidthDp.scaledBy(scale)
            }
            configuration.densityDpi = dpi
        }

        private fun Int.scaledBy(scale: Float): Int =
            if (this == Configuration.SCREEN_WIDTH_DP_UNDEFINED) this
            else (this * scale).roundToInt()
    }
}
