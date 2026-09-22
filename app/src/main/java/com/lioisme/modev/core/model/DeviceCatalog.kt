package com.lioisme.modev.core.model

import com.lioisme.modev.core.codec.Json

/*
 * 真机目录(品牌 → 机型)数据层(V47,复刻基线 `com.houvven.guise.device.DeviceCatalogRepository`
 * 的模型与解码,以及 `com.houvven.guise.ui.routing.editor.DevicePreset` 的两条写入语义;
 * 文件头注释形态沿用 `ui/theme/KindColorTokens.kt`)。
 *
 * 目的:让用户从**真实机型**里挑,而不是手打 Build.MODEL / Build.DEVICE。
 * 数据随包发布在 `app/src/main/assets/devices.json`(约 1.9 MB / 26 品牌 / 8533 条机型记录,
 * 派生自 KHwang9883/MobileModels-csv,CC BY-NC-SA 4.0;来源与上游修订号见同目录
 * devices.NOTICE.txt,该修订号由 `DeviceCatalogTest` 与目录内容逐字对账)。
 *
 * 与基线的三处刻意差别:
 *  1. 本文件**零 android 依赖**(AGENTS:core 子树禁 android import)——读盘与缓存落在
 *     `data.repo.DeviceCatalogRepository`,这里只有数据与纯函数;
 *  2. 解码唯一入口是 [parseDeviceCatalog](全工程统一 Gson,ADR-5),不用 kotlinx.serialization;
 *  3. 查表用**惰性索引**([DeviceCatalog.brandIndex]) —— 基线在构造期 `associateBy`,
 *     而 Modev 侧同一实例要在选择器里逐键过滤复用,索引必须离散步建且不重复扫描。
 */

/**
 * 一条机型记录。字段的**可空性照抄资产形态**:`code` / `alias` / `versionName` 在目录里
 * 大量为 null,而 `model` / `name` / `device` / `type` 恒存在(空值以空串形态出现)。
 *
 * 全部参数带默认值 ⇒ Kotlin 生成无参构造器 ⇒ Gson 走「构造器 + 逐字段填充」路径,
 * 从而保住默认值语义(缺失键落默认值而非 null;AGENTS 已实锤 `Unsafe.allocateInstance`
 * 会跳过默认值表达式)。
 */
data class DeviceModelEntry(
    /** Build.MODEL 形态的机型代码,例 `2211133C`。 */
    val model: String = "",
    /** 市场名(可为中文、可含空格),仅呈现用。 */
    val name: String = "",
    /** Build.DEVICE 值;目录里大量机型该项为空串。 */
    val device: String = "",
    /** 开发代号,`resolvedDevice` 的第三顺位。 */
    val code: String? = null,
    /** 代号别名,`resolvedDevice` 的第一顺位。 */
    val alias: String? = null,
    /** 设备类别(mob / pad / watch / tv / ...),仅分类,不参与任何写入。 */
    val type: String = "",
    /** 版本变体名(如「全网通版」),非 Build 字段,不写进画像。 */
    val versionName: String? = null,
) {

    /**
     * 选中该机型时应写进 Build.DEVICE 的值:**alias → device → code**,逐段 trim 后
     * 取首个非空白(基线 `DeviceModel.resolvedDevice` 的 trim 与优先级逐字复刻 ——
     * 目录里 `device` 常为空串而 `alias` / `code` 才是真实代号)。
     */
    fun resolvedDevice(): String = alias?.trim().orEmpty().ifBlank {
        device.trim().ifBlank { code?.trim().orEmpty() }
    }

    /**
     * 选择器列表的呈现形态(唯一事实源,UI 逐字显示,禁在 ui 侧再拼一遍):
     * - 机型代码与市场名都有 = `"<model> (<name>)"`,例 `2211133C (Xiaomi 13)`;
     * - 只有其一 = 那一个(不会出现空的括号);
     * - 两者皆空白 = 空串。
     *
     * 两段先各自 trim 再拼接 ⇒ 永不产生双空格或行尾空格;市场名内部的单个空格按原样保留。
     */
    fun label(): String {
        val modelText = model.trim()
        val marketingName = name.trim()
        return when {
            marketingName.isEmpty() -> modelText
            modelText.isEmpty() -> marketingName
            else -> "$modelText ($marketingName)"
        }
    }
}

/**
 * 一个品牌目录项。四段身份串各有其用,**不可互相派生**:
 * [key] 是目录内部键(选择器按它记忆当前品牌,查表忽略大小写),[displayName] 是呈现名
 * (可为中文),[buildBrand] 才是写进 Build.BRAND 的值,[manufacturer] 写进 Build.MANUFACTURER。
 *
 * 资产里 [buildBrand] 的大小写形态并不统一(`Xiaomi` 与 `google` 两样都有),
 * 因此「按已配置的厂牌值认回目录项」须经 [DeviceCatalog.brandForConfiguredValue],
 * 禁止假设它等于 key。
 */
data class DeviceBrandEntry(
    val key: String = "",
    val displayName: String = "",
    val buildBrand: String = "",
    val manufacturer: String = "",
    val models: List<DeviceModelEntry> = emptyList(),
)

/**
 * 整本目录。身份查表走惰性索引 [brandIndex](首次查表时一次性建表,此后逐键 O(1)),
 * 数据类等值语义只看构造参数 ⇒ 索引不参与比较。
 */
data class DeviceCatalog(
    val schemaVersion: Int = SCHEMA_VERSION,
    /** 上游数据修订号(devices.NOTICE.txt 同值);null = 资产未标注。 */
    val sourceRevision: String? = null,
    val brands: List<DeviceBrandEntry> = emptyList(),
) {

    companion object {
        /** 本版本能理解的目录格式版本。 */
        const val SCHEMA_VERSION = 1

        /** 空目录(仅作比较基准与测试夹具;消费方拿到 null 时**不得**拿它当回落)。 */
        val EMPTY = DeviceCatalog()
    }

    /** 小写 key → 品牌项。同 key 重复时保留后者(与基线 `associateBy` 同行为)。 */
    private val brandIndex: Map<String, DeviceBrandEntry> by lazy {
        brands.associateBy { it.key.lowercase() }
    }

    /** 按目录键取品牌项(忽略大小写);无此项返回 null。 */
    fun brandByKey(key: String): DeviceBrandEntry? = brandIndex[key.lowercase()]

    /**
     * 从**用户已配置的取值**认回目录项:先按 key 查索引,再线性扫 buildBrand(忽略大小写)
     * 与 displayName(精确比对)。基线 `brandForConfiguredValue` 同一顺序 —— 打开编辑页时
     * BRAND 里存的可能是三种形态中的任意一种,认不出就没有「当前品牌」可选。
     */
    fun brandForConfiguredValue(value: String): DeviceBrandEntry? =
        brandByKey(value) ?: brands.firstOrNull {
            it.buildBrand.equals(value, ignoreCase = true) || it.displayName == value
        }
}

/**
 * 资产文本 → 目录。**唯一解码入口**(别处不得再 `fromJson(DeviceCatalog)`,
 * 否则版本闸与 null 语义会被绕过)。
 *
 * 返回 null 的全部情形(本函数不抛异常;调用方须把 null 上屏为「目录不可用」,
 * 不得静默显示空选择器 —— AGENTS 红线「零静默」):
 * - 文本空白、不是 JSON、或 JSON 形态与目录不符(Gson 抛异常即收口为 null);
 * - `schemaVersion` **高于** [DeviceCatalog.SCHEMA_VERSION]:不认识的格式不猜
 *   (与 [ProfileCodec.decodeEnvelope] 同一红线;现网资产恒为版本 1,低于 1 时不额外拒绝);
 * - 解析后零品牌:一本没有任何可选机型的目录,对用户与「读不到」等价。
 *
 * 未知键由 Gson 默认忽略 ⇒ 上游新增字段不会让本版本失去目录。
 */
fun parseDeviceCatalog(text: String): DeviceCatalog? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parsed = runCatching { Json.gson.fromJson(trimmed, DeviceCatalog::class.java) }.getOrNull()
        ?: return null
    if (parsed.schemaVersion > DeviceCatalog.SCHEMA_VERSION) return null
    if (parsed.brands.isEmpty()) return null
    return parsed
}

/**
 * 把选中的机型写进画像。[picked] 的编码形态是 `"<model>:<resolvedDevice>"`
 * (基线选择器出串同一格式),拆分即 `substringBefore` / `substringAfter`。
 *
 * **只动 MODEL 与 DEVICE,PRODUCT / BOARD / HARDWARE 一律不碰**:MobileModels 不提供
 * Build.PRODUCT,拿 MODEL 或 DEVICE 凑一个值就是伪造(基线 `applyDevicePreset` 同处注释
 * 的告诫)。伪造出来的「真实机型从不存在的字段组合」本身即检测点,比留空更糟。
 */
fun applyDevicePreset(profile: DeviceProfile, picked: String): DeviceProfile = profile.copy(
    model = picked.substringBefore(":"),
    device = picked.substringAfter(":", missingDelimiterValue = ""),
)

/**
 * 把选中的品牌身份写进画像:BRAND 取 [DeviceBrandEntry.buildBrand],
 * MANUFACTURER 取 [DeviceBrandEntry.manufacturer]。
 *
 * 制造商**有条件跟随**:仅当原值为空白、或原值等于本应用**上一 BRAND**(忽略大小写)时才覆写,
 * 用户自填的其它制造商保持不动 —— 基线 `applySelectedBrandIdentity` 的判据(其落盘侧
 * `resolveManufacturer` 用的是严格相等且不处理空白,本处取的是「用户点选品牌」那一动作,
 * 故复刻前者)。理由:制造商跟着厂牌走只在「制造商本来就是厂牌的复制品」时才无损。
 */
fun applyBrandIdentity(profile: DeviceProfile, brand: DeviceBrandEntry): DeviceProfile {
    val previousBrand = profile.brand
    val manufacturerFollowsBrand = profile.manufacturer.isBlank() ||
        profile.manufacturer.equals(previousBrand, ignoreCase = true)
    return profile.copy(
        brand = brand.buildBrand,
        manufacturer = if (manufacturerFollowsBrand) brand.manufacturer else profile.manufacturer,
    )
}
