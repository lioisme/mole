package com.lioisme.modev.core.model

/**
 * 画像库条目:一行 = 一份**可复用画像**。
 *
 * 内置档的**展示名不进存储**([name] 对内置行恒为空串),由 ui 侧按 [builtinSeedId]
 * 取四语资源:基线把 name/summary 一起算进内容指纹,于是切换语言会让「用户未改过」
 * 的判定失真(语言变了即指纹变了)。本设计把身份与呈现分离,语言切换不再影响账本判定。
 *
 * [managed] = false 表示内置行已被用户改过 ⇒ 升级只补新档、绝不原地覆盖。
 */
data class DeviceProfileEntry(
    /** 画像 id(uuid 无横线,或内置键 `builtin:<seedId>`)。 */
    val id: String,
    /** 用户自定义名称;内置行为空串(名称走资源)。 */
    val name: String,
    /** 用户自定义摘要;内置行为空串(摘要走资源)。 */
    val summary: String,
    /** 画像正文。 */
    val profile: DeviceProfile,
    /** 内置档稳定键;null = 用户 DIY 画像。 */
    val builtinSeedId: String? = null,
    /** 内置档内容版本(账本判「该不该更新」)。 */
    val builtinVersion: Int? = null,
    /** 内置行是否仍受账本管辖。 */
    val managed: Boolean = true,
    val createTime: Long = 0L,
    val updateTime: Long = 0L,
) {
    /** 是否随包发布的内置档。 */
    val isBuiltin: Boolean get() = builtinSeedId != null

    /** 已填字段数(UI 摘要与「本画像生效几项」同源)。 */
    val filledCount: Int get() = profile.filledCount
}

/**
 * 每应用画像绑定的解析结论。
 *
 * 三态必须可区分,**禁止把「未绑定」与「绑了但库里没有」合并为一个 null** ——
 * 后者若静默按「不伪装」处理,就是旧 [SpoofProfiles.resolve] 把未知 id 回落成 auto
 * 的那类静默失效(AGENTS 实锤 GAP-02:任何 DIY 画像写入即无效且无提示)。
 */
sealed interface ProfileResolution {

    /** 该应用未绑定任何画像 ⇒ 不伪装。 */
    data object Unbound : ProfileResolution

    /** 绑定有效,命中画像库条目。 */
    data class Bound(val entry: DeviceProfileEntry) : ProfileResolution

    /** 绑定了不存在的 id(画像被删或数据损坏)⇒ UI 须显式提示,MCP 须回业务错误。 */
    data class Missing(val id: String) : ProfileResolution

    /** 取正文;未绑定与缺失一律为空画像(下发面恒可序列化)。 */
    fun profileOrEmpty(): DeviceProfile = when (this) {
        is Bound -> entry.profile
        Unbound,
        is Missing,
        -> DeviceProfile.EMPTY
    }
}
