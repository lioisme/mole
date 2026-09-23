package com.lioisme.modev.core.model

import kotlin.random.Random

/**
 * 画像编辑器表单态(V47 T3,基线 `ModuleConfigState` 的 Modev 化):
 * **每个字段一份编辑中文本 + 一个校验结论**,是 UI 与 [DeviceProfile] 之间唯一的中间层。
 *
 * 为什么值得单独成类而不是让 Compose 直接持有 41 个 `MutableState`:
 *  1. 「输入清空即取消该项干预」「非法值不得进入可保存快照」这两条是**产品语义**,
 *     不是渲染细节 —— 放在纯 Kotlin 里才能 JVM 直测(基线把它写在 41 个 State 加一个
 *     `updateConfigFromState` 显式映射里,新增字段极易漏接一侧);
 *  2. 本类不认得 Compose、资源与 Android ⇒ 注入侧预览、宿主诊断页与单测可共用;
 *  3. 行序、分区、生效组预览全部向 [ProfileFieldCatalog] 与 [ProfileHookPlan] 委派,
 *     UI 里不再出现第二份字段清单(§2.1 四视图对账的「UI 表单视图」这一路由此可证)。
 *
 * 不变式:[snapshot] **忠实反映输入,绝不静默改写**;是否可持久化由 [hasErrors] 决定,
 * 仓库层再原子拒绝一次([ProfileCodec.isValid]) —— 两道防线都不是唯一防线,
 * 而「把非法值悄悄丢掉」会让用户存进一份与自己输入不同的画像。
 */
class ProfileEditorForm(
    initial: DeviceProfile = DeviceProfile.EMPTY,
    private val samples: ProfileRandomSource = BuiltInProfileRandomSource,
) {

    /** 编辑态缓冲,键 = 全部画像字段,值 = 文本("" = 未填)。 */
    private val texts: MutableMap<ProfileField, String> =
        ProfileField.entries.associateWith { it.stringOf(initial) }.toMutableMap()

    /** 打开表单时的基线快照(用于「未改动则保存按钮禁用」判定)。 */
    private var baseline: DeviceProfile = snapshot()

    /** 目录声明序的字段列表(UI 逐分区渲染时的稳定行序来源)。 */
    val fields: List<ProfileField> get() = ProfileField.entries

    /** 来源族 → 行序(直接委派目录,杜绝第二份清单;V49 起分区即类别)。 */
    fun rowsByKind(): Map<ProfileKind, List<ProfileField>> = ProfileFieldCatalog.byKind

    /** 该字段当前编辑文本。 */
    fun textOf(field: ProfileField): String = texts[field].orEmpty()

    /** 该字段的校验结论(null = 合法,含空值)。 */
    fun errorOf(field: ProfileField): ProfileFieldError? =
        ProfileFieldCatalog.validate(field, textOf(field))

    /** 该字段是否处于「已填」态(编辑态判定,非模型哨兵判定)。 */
    fun isFilled(field: ProfileField): Boolean = textOf(field).isNotEmpty()

    /** 已填**且合法**的字段数(UI 摘要「本画像生效几项」同源;非法输入不计入)。 */
    fun filledCount(): Int = fields.count { isFilled(it) && errorOf(it) == null }

    /** 任一字段存在校验错误 ⇒ 保存必须被阻止(仓库层还会再拒一次)。 */
    fun hasErrors(): Boolean = fields.any { errorOf(it) != null }

    /** 可保存:无校验错误且至少有一项生效(零字段画像无可伪装内容)。 */
    val canSave: Boolean get() = !hasErrors() && filledCount() > 0

    /** 首个错误(UI 定位提示用;字段序 = 目录序,故错误顺序稳定)。 */
    fun firstError(): Pair<ProfileField, ProfileFieldError>? =
        fields.firstNotNullOfOrNull { field -> errorOf(field)?.let { field to it } }

    /** 写入编辑文本(空白归一为空串,即取消该项干预)。 */
    fun update(field: ProfileField, raw: String) {
        texts[field] = raw.trim()
    }

    /** 清空单项干预。 */
    fun clear(field: ProfileField) {
        texts[field] = ""
    }

    /** 清空整张表单(等价于「全部不伪装」)。 */
    fun clearAll() {
        ProfileField.entries.forEach { texts[it] = "" }
    }

    /** 以一份画像正文重置表单(编辑既有档 / 复制为 DIY 的入口)。 */
    fun loadFrom(profile: DeviceProfile) {
        ProfileField.entries.forEach { texts[it] = it.stringOf(profile) }
        baseline = snapshot()
    }

    /** 与打开时(或上次 [commit])的基线是否有差异 ⇒ 保存按钮可用性。 */
    fun isDirty(): Boolean = snapshot() != baseline

    /** 落定当前内容为新基线(保存成功后调用)。 */
    fun commit() {
        baseline = snapshot()
    }

    /**
     * 取当前表单正文。
     *
     * 逐字段经 [ProfileField.withText] 解析:数值不可解析时回落缺省哨兵(那是「这一项没填」
     * 的自然结果),但**形态非法而可解析的值原样保留** —— 由 [hasErrors] 与仓库层共同把关,
     * 不在此处悄悄丢掉,否则用户会存进一份和自己输入不一样的画像。
     */
    fun snapshot(): DeviceProfile =
        fields.fold(DeviceProfile.EMPTY) { acc, field ->
            field.withText(acc, textOf(field))
        }

    /** 当前快照会编译出哪些干预组(UI 实时预览「这次改动会装哪些钩子」)。 */
    fun previewGroups(): List<ProfileKind> = ProfileHookPlan.activeGroups(snapshot())

    /** 当前快照的配置矛盾。 */
    fun previewConflicts(): List<ProfileConflict> = ProfileHookPlan.conflicts(snapshot())

    /**
     * 就近随机:为该字段生成一个**必然合法且必然生效**的值并写入编辑态。
     *
     * 只覆盖 [ProfileFieldCatalog.RANDOMIZABLE] 内的字段;其余字段(主板/硬件/DPI/语言/
     * 基站/应用版本/隐私开关)调用即 no-op —— 与基线一致:那些不是「随机一下」有意义的事。
     */
    fun randomizeField(field: ProfileField, random: Random = Random.Default): Boolean {
        if (!ProfileFieldCatalog.isRandomizable(field)) return false
        val value = generatedText(field, random) ?: return false
        texts[field] = value
        return true
    }

    /**
     * 一键随机:整组同源派生(同一台设备 + 同一 Android 世代 + 同一运营商),
     * 而不是逐字段独立抽样 —— 后者会造出「Pixel 机型配小米 fingerprint」这类自相矛盾画像。
     *
     * 返回被写入的字段数。
     */
    fun randomizeAll(random: Random = Random.Default): Int {
        val pool = ProfileValueSets.recentAndroidReleases()
        val release = pool[random.nextInt(pool.size)]
        // 配对值回查唯一事实源:整组随机与逐项随机、与编辑器选择器说的是同一张表。
        val sdk = checkNotNull(ProfileValueSets.sdkForRelease(release)) { "$release 在 Android 公开表里无对应 API level" }
        val brand = samples.brandAt(random.nextInt(samples.brandCount()))
        val device = brand.device
        val buildId = ProfileGenerators.randomBuildId(release, random)
        val carrier = samples.carrierAt(random.nextInt(samples.carrierCount()))
        val lat = ProfileGenerators.randomLatitude(random)
        val lon = ProfileGenerators.randomLongitude(random)

        val writes = mapOf(
            ProfileField.BRAND to brand.brand,
            ProfileField.MANUFACTURER to brand.manufacturer,
            ProfileField.MODEL to brand.model,
            ProfileField.PRODUCT to device,
            ProfileField.DEVICE to device,
            ProfileField.ANDROID_VERSION to release,
            ProfileField.SDK_INT to sdk,
            ProfileField.BUILD_ID to buildId,
            ProfileField.FINGERPRINT to ProfileGenerators.deriveFingerprint(
                brand = brand.brand,
                product = device,
                device = device,
                release = release,
                buildId = buildId,
                random = random,
            ),
            ProfileField.NETWORK_TYPE to NETWORK_SLOTS[random.nextInt(NETWORK_SLOTS.size)].toString(),
            ProfileField.WIFI_SSID to ProfileGenerators.randomString(10, random),
            ProfileField.WIFI_BSSID to ProfileGenerators.randomMacAddress(random),
            ProfileField.WIFI_MAC_ADDRESS to ProfileGenerators.randomMacAddress(random),
            ProfileField.SIM_OPERATOR to carrier.plmn,
            ProfileField.SIM_OPERATOR_NAME to carrier.name,
            ProfileField.SIM_COUNTRY to carrier.country,
            ProfileField.ANDROID_ID to ProfileGenerators.randomAndroidId(random),
            ProfileField.IMEI to ProfileGenerators.randomImei(random),
            ProfileField.PHONE_NUM to ProfileGenerators.randomPhoneNum(random),
            ProfileField.LONGITUDE to lon.toString(),
            ProfileField.LATITUDE to lat.toString(),
            ProfileField.BATTERY_LEVEL to ProfileGenerators.randomBatteryLevel(random).toString(),
        )
        var written = 0
        writes.forEach { (field, value) ->
            if (ProfileFieldCatalog.isRandomizable(field)) {
                texts[field] = value
                written++
            }
        }
        return written
    }

    private fun generatedText(field: ProfileField, random: Random): String? = when (field) {
        ProfileField.BRAND, ProfileField.MANUFACTURER, ProfileField.MODEL, ProfileField.PRODUCT,
        ProfileField.DEVICE, ProfileField.SIM_OPERATOR_NAME, ProfileField.VERSION_NAME,
        -> null // 身份串与运营商名不独立随机:整组派生才有意义,交给 randomizeAll

        ProfileField.BUILD_ID -> ProfileGenerators.randomBuildId(
            textOf(ProfileField.ANDROID_VERSION).ifEmpty { "14" },
            random,
        )

        ProfileField.FINGERPRINT -> {
            val buildId = ProfileGenerators.randomBuildId(
                textOf(ProfileField.ANDROID_VERSION).ifEmpty { "14" },
                random,
            )
            // 基线同构:随机 fingerprint 顺带改写 Build ID,保证两段自洽。
            texts[ProfileField.BUILD_ID] = buildId
            ProfileGenerators.deriveFingerprint(
                brand = textOf(ProfileField.BRAND).ifEmpty { "google" },
                product = textOf(ProfileField.PRODUCT).ifEmpty { textOf(ProfileField.DEVICE).ifEmpty { "raven" } },
                device = textOf(ProfileField.DEVICE).ifEmpty { "raven" },
                release = textOf(ProfileField.ANDROID_VERSION).ifEmpty { "14" },
                buildId = buildId,
                random = random,
            )
        }

        ProfileField.SDK_INT -> {
            // 配对值一律回查 ProfileValueSets(唯一事实源)。release 还空着时先就近补一个
            // release 再回查 —— 绝不写一个与 release 无关的 SDK(旧实装硬写 "34" 就是猜)。
            val release = textOf(ProfileField.ANDROID_VERSION).ifEmpty {
                ProfileValueSets.recentAndroidReleases().last().also { texts[ProfileField.ANDROID_VERSION] = it }
            }
            ProfileValueSets.sdkForRelease(release) ?: ""
        }

        ProfileField.ANDROID_VERSION -> {
            val pool = ProfileValueSets.recentAndroidReleases()
            pool[random.nextInt(pool.size)]
        }
        ProfileField.NETWORK_TYPE -> NETWORK_SLOTS[random.nextInt(NETWORK_SLOTS.size)].toString()
        ProfileField.WIFI_SSID -> ProfileGenerators.randomString(10, random)
        ProfileField.WIFI_BSSID, ProfileField.WIFI_MAC_ADDRESS -> ProfileGenerators.randomMacAddress(random)
        ProfileField.SIM_OPERATOR -> samples.carrierAt(random.nextInt(samples.carrierCount())).plmn
        ProfileField.SIM_COUNTRY -> samples.carrierAt(random.nextInt(samples.carrierCount())).country
        ProfileField.ANDROID_ID -> ProfileGenerators.randomAndroidId(random)
        ProfileField.IMEI -> ProfileGenerators.randomImei(random)
        ProfileField.PHONE_NUM -> ProfileGenerators.randomPhoneNum(random)
        ProfileField.LONGITUDE -> ProfileGenerators.randomLongitude(random).toString()
        ProfileField.LATITUDE -> ProfileGenerators.randomLatitude(random).toString()
        ProfileField.BATTERY_LEVEL -> ProfileGenerators.randomBatteryLevel(random).toString()
        else -> null
    }

    private data class BrandSample(val brand: String, val manufacturer: String, val model: String, val device: String)
    private data class CarrierSample(val plmn: String, val name: String, val country: String)

    private companion object {
        val NETWORK_SLOTS = listOf(
            ProfileValues.NET_WIFI,
            ProfileValues.NET_MOBILE_4G,
            ProfileValues.NET_MOBILE_5G,
        )
    }
}

/**
 * 随机派生的**样本来源**契约(V47 T3):品牌机型与运营商样本从本接口取,
 * 不在表单里硬编码 —— 否则 WP-4 引入 `devices.json`/`carriers.json` 目录时
 * 会凭空多出第二份机型事实源。
 *
 * [BuiltInProfileRandomSource] 是给定的最小默认(三档内置机型同源);
 * WP-4 的目录实现替换它即可,表单与测试无需改动。
 */
interface ProfileRandomSource {
    fun brandAt(index: Int): BrandProfileSample
    fun brandCount(): Int
    fun carrierAt(index: Int): CarrierProfileSample
    fun carrierCount(): Int
}

/** 一个可用于随机派生的机型身份组(三段互不猜,与基线 `resolveManufacturer` 同规则)。 */
data class BrandProfileSample(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
)

/** 一个可用于随机派生的运营商样本(选中即同时回填 PLMN / 名称 / 国家)。 */
data class CarrierProfileSample(
    val plmn: String,
    val name: String,
    val country: String,
)

/** 内置默认随机源:与 [BuiltinDeviceProfiles] 三档同源,不引入第二份机型表。 */
object BuiltInProfileRandomSource : ProfileRandomSource {

    private val brands = BuiltinDeviceProfiles.BUILTINS.map { spec ->
        BrandProfileSample(
            brand = spec.profile.brand,
            manufacturer = spec.profile.manufacturer,
            model = spec.profile.model,
            device = spec.profile.device,
        )
    }

    private val carriers = listOf(
        CarrierProfileSample("46000", "China Mobile", "CN"),
        CarrierProfileSample("46001", "China Unicom", "CN"),
        CarrierProfileSample("46011", "China Telecom", "CN"),
    )

    override fun brandAt(index: Int): BrandProfileSample = brands[index % brands.size]
    override fun brandCount(): Int = brands.size
    override fun carrierAt(index: Int): CarrierProfileSample = carriers[index % carriers.size]
    override fun carrierCount(): Int = carriers.size
}
