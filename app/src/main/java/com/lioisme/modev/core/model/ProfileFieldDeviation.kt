package com.lioisme.modev.core.model

/**
 * 每应用画像覆写的合并与反推(V49 R-2 唯一实现点)。
 *
 * 为什么必须有唯一一处:同一条语义有两个消费方 ——
 *  1. **宿主展开**(`SettingsRepository.expandProfile`)要「所绑画像 ⊕ 本应用覆写 → 下发快照」;
 *  2. **每应用微调区**要反向算出「这份编辑文本相对所绑画像改了哪几项」。
 * 两边各写一遍必然分叉(分叉的形态就是「屏上显示已定制 3 项,下发的是 2 项」)。
 *
 * 纯 Kotlin 零 Android(可注入日志由调用方承担),因此两条方向都在 JVM 直测面内。
 */
object ProfileFieldDeviation {

    /** 合并结果:`profile` 恒可下发,`rejected` 点名被丢掉的键(空 = 全部落进去)。 */
    data class Merged(val profile: DeviceProfile, val rejected: List<String>)

    /**
     * 把覆写逐项叠到 [base] 上。
     *
     * 未知键与非法值**不钳制、不猜**(AGENTS #30 第二条):整项跳过并登记进 `rejected`,
     * 由调用方上屏或落日志 —— 悄悄丢掉会让用户存进一份与自己输入不同的画像。
     */
    fun apply(base: DeviceProfile, deviations: Map<String, String>?): Merged {
        if (deviations.isNullOrEmpty()) return Merged(base, emptyList())
        var next = base
        val rejected = mutableListOf<String>()
        for ((key, raw) in deviations) {
            val field = ProfileFieldCatalog.fieldByJsonKey(key)
            if (field == null) {
                rejected += key
                continue
            }
            if (ProfileFieldCatalog.validate(field, raw) != null) {
                rejected += key
                continue
            }
            next = field.withText(next, raw)
        }
        return Merged(next, rejected)
    }

    /**
     * 反向:编辑文本相对所绑画像 [base] 的稀疏覆写集(值 = 编辑态字符串)。
     *
     * 恒含空串形态 —— 「把画像里填过的一项清成空」本身就是用户的定制意图,必须能表达;
     * 归一规则与 [apply] 互逆:值与画像现值相同的项**不出现在结果里**(否则每项都会变成
     * 幽灵偏差,`isDefault` 与恢复默认行的计数会一路虚涨)。
     */
    fun between(base: DeviceProfile, texts: Map<ProfileField, String>): Map<String, String>? {
        val out = LinkedHashMap<String, String>()
        for (field in ProfileField.entries) {
            val edited = (texts[field] ?: "").trim()
            if (edited == field.stringOf(base).trim()) continue
            out[ProfileFieldCatalog.jsonKeyOf(field)] = edited
        }
        return out.ifEmpty { null }
    }
}
