package com.lioisme.modev.core.model

/**
 * 画像传输信封与导入结论(V47 T2,R-1 对基线模板迁移链的复刻)。
 *
 * 与基线 `TemplateBundle` 同构:**自带 schema 版本**且版本不匹配即整包拒绝
 * (不做跨版本猜测解析)。基线另有二维码通道(deflate + base64url + `guise1:` 前缀),
 * Modev 侧前缀与压缩属传输层细节,落在 WP-6 的 codec,不影响本信封形状。
 *
 * 全部参数带默认值 ⇒ Kotlin 生成无参构造器 ⇒ Gson 反序列化保默认值语义
 * (AGENTS 实锤:无 no-arg 构造器时 Gson 走 `Unsafe.allocateInstance` 跳过默认值表达式)。
 */
data class ProfileEnvelope(
    /** 信封格式版本(不匹配即整包拒绝)。 */
    val schemaVersion: Int = SCHEMA_VERSION,
    /** 导出时刻(epoch millis,仅呈现用)。 */
    val exportedAt: Long = 0L,
    /** 画像条目。 */
    val items: List<ProfileTransferItem> = emptyList(),
) {
    companion object {
        /** 当前信封版本。 */
        const val SCHEMA_VERSION = 1

        /** 单包条目上限(防超大包压垮解析)。 */
        const val MAX_ITEMS = 200

        /** 导入文本字符上限(与基线 2,000,000 同档)。 */
        const val MAX_IMPORT_CHARS = 2_000_000

        /**
         * 导入文件字节上限(UI 侧读盘前的第一道闸)。
         *
         * 字符闸在解析前拦不住「先 slurp 一个 5GB 文件」这一步 ⇒ 读盘必须按
         * `statSize` 硬拒。取字符闸 ×4(UTF-8 上界,留转义余量)。
         */
        const val MAX_IMPORT_BYTES = MAX_IMPORT_CHARS * 4L
    }
}

/** 信封内的一条画像(DIY 名与摘要随包携带;内置身份与账本列不外传)。 */
data class ProfileTransferItem(
    val name: String = "",
    val summary: String = "",
    val profile: DeviceProfile = DeviceProfile.EMPTY,
)

/** 导入逐条结论。 */
enum class ProfileImportVerdict {
    /** 已落库。 */
    IMPORTED,

    /** 同内容画像已在库内,跳过不重复创建。 */
    DUPLICATE,

    /** 名称为空白,回落默认名后仍导入。 */
    IMPORTED_NAME_FALLBACK,

    /** 空画像(零字段)被拒。 */
    REJECTED_EMPTY,

    /** 含非法取值(未过 `ProfileFieldCatalog` 校验)被拒 —— 禁钳制、禁静默改写。 */
    REJECTED_INVALID,

    /** 超出条目上限被拒。 */
    REJECTED_LIMIT,

    /** 仅校验未落库。 */
    DRY_RUN_OK,
}

/** 导入结果汇总(UI 提示与 MCP 出参同一份,禁止两侧各算一遍)。 */
data class ProfileImportOutcome(
    val imported: Int = 0,
    val skipped: Int = 0,
    val rejected: Int = 0,
    val schemaRejected: Boolean = false,
    val verdicts: List<ProfileImportVerdict> = emptyList(),
) {
    companion object {
        /** 空结果(解析失败/无内容)。 */
        val EMPTY = ProfileImportOutcome()
    }
}
