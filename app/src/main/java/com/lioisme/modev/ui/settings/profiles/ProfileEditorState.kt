package com.lioisme.modev.ui.settings.profiles

import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileConflict
import com.lioisme.modev.core.model.ProfileEditorForm
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileFieldError
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.core.model.ProfileResolution
import com.lioisme.modev.core.model.ProfileValues
import com.lioisme.modev.core.model.ProfileValueType
import com.lioisme.modev.core.model.stringOf
import kotlin.random.Random

/**
 * 画像编辑器的**可视态派生层**:[ProfileEditorForm] 是编辑文本与校验结论的
 * 唯一事实源,本类只把那份表单**投影成 UI 要画的形状**(身份预览卡 / 生效组 / 矛盾 /
 * 保存闸),并收口「写入 → 通知 UI 重组」这一侧的动作。
 *
 * 为什么单独成类而不是让 Composable 直接读表单:
 *  1. 指纹与三元组的一致性判定是**产品语义**(自相矛盾的画像会被真实反作弊直接冻结设备),
 *     放在纯 Kotlin 里才能 JVM 直测,也不会只在某一条渲染分支上被算一次;
 *  2. 本类零 Compose、零 Android ⇒ 注入侧预览、诊断页与单测可共用同一份投影;
 *  3. 分区行序、槽集、随机化范围一律向 [ProfileFieldCatalog] 与表单委派,
 *     UI 里不出现第二份字段清单(与 AGENTS 「四视图对账」同一红线)。
 *
 * [ProfileEditorViewModel] 每次变更后自增一个 revision 状态流驱动重组;
 * 本类自身**不是** observable 状态容器(避免 41 个 MutableState 的散点)。
 */
class ProfileEditorState(
    initial: DeviceProfile = DeviceProfile.EMPTY,
    private val randomProvider: () -> Random = { Random.Default },
) {

    /** 编辑态与校验结论的唯一事实源。 */
    val form: ProfileEditorForm = ProfileEditorForm(initial)

    /** 来源族到行序(目录驱动;UI 逐分区渲染,禁自造顺序。V49:分区即类别)。 */
    fun rowsByKind(): Map<ProfileKind, List<ProfileField>> = form.rowsByKind()

    fun textOf(field: ProfileField): String = form.textOf(field)

    fun isFilled(field: ProfileField): Boolean = form.isFilled(field)

    fun errorOf(field: ProfileField): ProfileFieldError? = form.errorOf(field)

    /** 该行是否可就近随机(目录裁决,非 UI 猜测)。 */
    fun isRandomizable(field: ProfileField): Boolean = ProfileFieldCatalog.isRandomizable(field)

    /** 已填且合法的参数数(非法输入不计入,与 [groups] 同口径)。 */
    fun filledCount(): Int = form.filledCount()

    /** 当前输入会安装哪些干预组(实时预览「这次改动装什么钩子」)。 */
    fun groups(): List<ProfileKind> = form.previewGroups()

    /** 当前输入的配置矛盾(内联提示,不阻断)。 */
    fun conflicts(): List<ProfileConflict> = form.previewConflicts()

    /** 设备身份预览卡内容。 */
    fun identity(): ProfileIdentityPreview = identityOf(form.snapshot())

    /** 首个校验错误(UI 在保存闸处点名要修哪一项)。 */
    fun firstError(): Pair<ProfileField, ProfileFieldError>? = form.firstError()

    fun hasErrors(): Boolean = form.hasErrors()

    /** 保存闸 = 有改动 且 无非法输入 且 至少一项生效。 */
    val canSave: Boolean get() = form.isDirty() && form.canSave

    fun isDirty(): Boolean = form.isDirty()

    fun snapshot(): DeviceProfile = form.snapshot()

    fun commit() = form.commit()

    fun loadFrom(profile: DeviceProfile) = form.loadFrom(profile)

    // ------------------------------------------------------------------ 写入面

    /** 文本类写入(sheet 提交;空白归一为「取消该项干预」由表单负责)。 */
    fun update(field: ProfileField, raw: String) = form.update(field, raw)

    fun clear(field: ProfileField) = form.clear(field)

    fun clearAll() = form.clearAll()

    /**
     * 批量写入(机型库选择等「一次改数项」的通路)。
     *
     * 逐字段 `update` 会留下中间帧:型号已换而品牌未换的那一帧,屏上显示的正是
     * 自相矛盾的画像。整批写完再由调用方 `bump` 一次 ⇒ 屏上只见结论,不见半成品。
     */
    fun applyValues(values: Map<ProfileField, String>) {
        values.forEach { (field, raw) -> form.update(field, raw) }
    }

    /** 布尔项:关 = 清空(false 即缺省态,与哨兵语义同构,不留 "false" 文本)。 */
    fun writeBool(field: ProfileField, value: Boolean) {
        form.update(field, if (value) "true" else "")
    }

    /**
     * 枚举槽写入:选中「不干预」槽即清空编辑态。
     *
     * 不归一会让两个计数器分叉:[ProfileEditorForm.isFilled] 看编辑文本非空,
     * 而 `DeviceProfile.filledCount` 看模型哨兵 —— 后者才算生效。清空是唯一同时
     * 满足两者的写法。
     */
    fun writeSlot(field: ProfileField, slot: Int) {
        form.update(field, if (slot == unhookSlotOf(field)) "" else slot.toString())
    }

    /** 就近随机(仅目录 RANDOMIZABLE 内生效,其余为 no-op)。 */
    fun randomizeField(field: ProfileField): Boolean = form.randomizeField(field, randomProvider())

    /** 整组随机(同一品牌世代运营商同源派生);返回被写入的字段数。 */
    fun randomizeAll(): Int = form.randomizeAll(randomProvider())

    companion object {

        /** 枚举槽字段的合法槽位集:**由目录 validate 反推**,UI 零手抄槽表。 */
        fun acceptedSlots(field: ProfileField): List<Int> = when (field.type) {
            ProfileValueType.NETWORK_TYPE, ProfileValueType.SCREENSHOT_MODE ->
                SLOT_PROBE_WINDOW.filter { ProfileFieldCatalog.validate(field, it.toString()) == null }

            else -> emptyList()
        }

        /** 该形态的「不生效」槽。 */
        fun unhookSlotOf(field: ProfileField): Int = when (field.type) {
            ProfileValueType.NETWORK_TYPE -> ProfileValues.NET_UNHOOK
            ProfileValueType.SCREENSHOT_MODE -> ProfileValues.SCREENSHOT_UNHOOK
            else -> ProfileValues.UNSET_INT
        }

        /**
         * 身份一致性投影:指纹前缀 `brand/product/device` 与三元组逐段对账。
         *
         * 任一要素缺失即 [ProfileIdentityCheck.Unknown] —— 缺字段不是矛盾,
         * 把它报成矛盾会把用户推向「随便填点什么消红」的反向操作。
         */
        fun identityOf(profile: DeviceProfile): ProfileIdentityPreview {
            val triple = listOf(profile.brand, profile.product, profile.device)
            val tripleComplete = triple.all { it.isNotEmpty() }
            val prefix = fingerprintPrefixOf(profile.fingerPrint)
            val triplePrefix = triple.joinToString("/")
            val check = when {
                prefix.isEmpty() || !tripleComplete -> ProfileIdentityCheck.Unknown
                prefix == triplePrefix -> ProfileIdentityCheck.Consistent
                else -> ProfileIdentityCheck.Mismatched
            }
            return ProfileIdentityPreview(
                brand = profile.brand,
                manufacturer = profile.manufacturer,
                model = profile.model,
                product = profile.product,
                device = profile.device,
                androidVersion = profile.androidVersion,
                sdkInt = ProfileField.SDK_INT.stringOf(profile),
                fingerprint = profile.fingerPrint,
                fingerprintPrefix = prefix,
                triplePrefix = triplePrefix,
                check = check,
            )
        }

        /** 指纹首个 `:` 之前的三段(形态非法或为空返回空串,交由校验器报错)。 */
        fun fingerprintPrefixOf(fingerprint: String): String =
            if (fingerprint.isEmpty() || !fingerprint.contains(':')) "" else fingerprint.substringBefore(':').trim()
    }
}

/**
 * 编辑器的**一帧可视快照**:把 [ProfileEditorState] 的全部派生值一次算完,
 * 交成一枚不可变值对象。
 *
 * 为什么要这一层而不是让 Composable 直接读表单:Compose 的跳过判定建立在参数上,
 * 读一个「内部可变 Map」的界面不会随写入重组(要么整屏漏刷新,要么靠
 * `revision` 计数器把参数强行弄脏)。快照做参数,刷新点即数据变化点,
 * 也让 JVM 侧断言可以直接比对整帧(`ProfileEditorStateTest`)。
 */
data class ProfileEditorView(
    val filledCount: Int,
    val dirty: Boolean,
    val canSave: Boolean,
    val identity: ProfileIdentityPreview,
    val groups: List<ProfileKind>,
    val conflicts: List<ProfileConflict>,
    val rowsByKind: Map<ProfileKind, List<ProfileField>>,
    val texts: Map<ProfileField, String>,
    val errors: Map<ProfileField, ProfileFieldError>,
    val firstError: Pair<ProfileField, ProfileFieldError>?,
)

/** 逐字段取当前编辑文本与校验结论(行序 = 目录序)。 */
fun ProfileEditorState.toView(): ProfileEditorView = ProfileEditorView(
    filledCount = filledCount(),
    dirty = isDirty(),
    canSave = canSave,
    identity = identity(),
    groups = groups(),
    conflicts = conflicts(),
    rowsByKind = rowsByKind(),
    texts = ProfileField.entries.associateWith { textOf(it) },
    errors = ProfileField.entries.mapNotNull { field ->
        errorOf(field)?.let { field to it }
    }.toMap(),
    firstError = firstError(),
)

/** 一致性档(身份预览卡的状态色与文案唯一判定)。 */
enum class ProfileIdentityCheck {
    /** 指纹前缀与三元组逐段相同。 */
    Consistent,

    /** 不一致(真机反作弊据此冻结设备,故恒显式警示)。 */
    Mismatched,

    /** 要素不全,尚不可判。 */
    Unknown,
}

/** 设备身份预览卡的内容(全部由当前编辑态派生)。 */
data class ProfileIdentityPreview(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val product: String,
    val device: String,
    val androidVersion: String,
    val sdkInt: String,
    val fingerprint: String,
    val fingerprintPrefix: String,
    val triplePrefix: String,
    val check: ProfileIdentityCheck,
) {
    /** 卡内是否已有任何身份要素可展示(全空时卡走空态文案)。 */
    val isEmpty: Boolean get() = brand.isEmpty() && manufacturer.isEmpty() && model.isEmpty() &&
        product.isEmpty() && device.isEmpty() && fingerprint.isEmpty()
}

/** 画像保存结果(UI 据此出 Snackbar,VM 零资源感知)。 */
enum class ProfileSaveResult { Saved, Rejected }

/**
 * 每应用画像绑定的**三态呈现**(V47 WP-5 C)。
 *
 * [Missing] 与 [Unbound] 必须分色分文:绑了却库里没有 = 用户在界面上做了选择但
 * 伪装不会发生,静默按「不伪装」处理正是 GAP-02 的形态。
 */
sealed interface ProfileBindingDisplay {
    /** 未绑定 = 跟随全局档。 */
    data object Unbound : ProfileBindingDisplay

    /** 绑定有效。 */
    data class Bound(
        val name: String,
        val builtinSeedId: String?,
        val filledCount: Int,
    ) : ProfileBindingDisplay

    /** 绑定了库里不存在的 id。 */
    data class Missing(val id: String) : ProfileBindingDisplay
}

/** [ProfileResolution] 三态 → 呈现三态(穷尽 `when`,零合并)。 */
fun ProfileResolution.toBindingDisplay(): ProfileBindingDisplay = when (this) {
    ProfileResolution.Unbound -> ProfileBindingDisplay.Unbound
    is ProfileResolution.Bound -> ProfileBindingDisplay.Bound(
        name = entry.name,
        builtinSeedId = entry.builtinSeedId,
        filledCount = entry.filledCount,
    )

    is ProfileResolution.Missing -> ProfileBindingDisplay.Missing(id)
}

/** 槽位探测窗(与 MCP 目录工具同法:合法集由目录裁决,本窗只决定枚举范围)。 */
private val SLOT_PROBE_WINDOW: IntRange = -10..40
