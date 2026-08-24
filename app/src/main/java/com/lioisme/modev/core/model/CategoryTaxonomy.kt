package com.lioisme.modev.core.model

/**
 * 记录类别分类法(单一事实源,V22):类型([RecordType])↔类别(kind 名)↔默认启用态↔
 * 噪点分级↔展示顺序的唯一来源,取代 UI 中对 [CryptoKind.entries]/[SqlKind.entries]
 * 的硬编码枚举遍历。
 *
 * - 设置页类别开关、记录筛选菜单、未来 MCP 元信息均以此为驱动;
 * - [id] 与枚举名一致([CryptoKind.name]/[SqlKind.name]),对齐 [HookSwitch] 字段、
 *   存储 `kind` 列、UI 标签函数([kindLabelRes]/[sqlKindLabelRes]);
 * - 标签解析仍由 UI 资源函数承担,本类保持 Android 资源无关(可纯 JVM 单测)。
 *
 * 噪点分级([NoiseTier])用于指导默认启用态与(未来)UI 提示,不参与存储/筛选语义。
 */
enum class NoiseTier {
    /** 高逆向价值,默认启用。 */
    SIGNAL,

    /** 价值一般,默认启用但可关注。 */
    NOISE,

    /** 实际意义低、高频噪声,默认停用。 */
    NOISE_HIGH,
}

/**
 * 单一类别元数据。
 *
 * @param id       类别标识,等于对应枚举名([CryptoKind]/[SqlKind])。
 * @param type     所属记录类型([RecordType.CRYPTO]/[RecordType.DATABASE])。
 * @param defaultEnabled 新安装默认是否启用(噪点类别默认关)。
 * @param noiseTier 噪点分级(仅文档/排序用)。
 * @param order    同类型内展示顺序(升序)。
 */
data class RecordCategory(
    val id: String,
    val type: RecordType,
    val defaultEnabled: Boolean,
    val noiseTier: NoiseTier,
    val order: Int,
)

/** 记录类别分类法(进程级单例,不可变)。 */
object CategoryTaxonomy {

    private val entries: List<RecordCategory> = buildList {
        // 算法类别(顺序对齐原设置页,保持用户肌肉记忆)
        add(RecordCategory(CryptoKind.CIPHER.name, RecordType.CRYPTO, true, NoiseTier.SIGNAL, 0))
        add(RecordCategory(CryptoKind.MESSAGE_DIGEST.name, RecordType.CRYPTO, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(CryptoKind.MAC.name, RecordType.CRYPTO, true, NoiseTier.SIGNAL, 2))
        add(RecordCategory(CryptoKind.SIGNATURE.name, RecordType.CRYPTO, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(CryptoKind.KEY_PARAM.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 4))
        add(RecordCategory(CryptoKind.KEY_GENERATION.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 5))
        add(RecordCategory(CryptoKind.KEY_FACTORY.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 6))
        add(RecordCategory(CryptoKind.KEY_STORE.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 7))
        add(RecordCategory(CryptoKind.KEY_AGREEMENT.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 8))
        add(RecordCategory(CryptoKind.RANDOM.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 9))
        add(RecordCategory(CryptoKind.BASE64.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 10))
        add(RecordCategory(CryptoKind.WEB_CRYPTO.name, RecordType.CRYPTO, false, NoiseTier.NOISE_HIGH, 11))
        // 数据库类别(顺序对齐原设置页)
        add(RecordCategory(SqlKind.OPEN.name, RecordType.DATABASE, false, NoiseTier.NOISE_HIGH, 0))
        add(RecordCategory(SqlKind.QUERY.name, RecordType.DATABASE, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(SqlKind.DML.name, RecordType.DATABASE, true, NoiseTier.SIGNAL, 2))
        add(RecordCategory(SqlKind.EXEC.name, RecordType.DATABASE, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(SqlKind.TRANSACTION.name, RecordType.DATABASE, false, NoiseTier.NOISE_HIGH, 4))
    }

    /** 全部类别(按 type 分组、组内按 order;稳定输出)。 */
    fun all(): List<RecordCategory> =
        entries.sortedWith(compareBy({ it.type.ordinal }, { it.order }))

    /** 指定类型的类别(按 order 升序)。 */
    fun forType(type: RecordType): List<RecordCategory> =
        entries.filter { it.type == type }.sortedBy { it.order }

    /** 按类型 + id 取类别(非法返回 null,协议/存储漂移兜底)。 */
    fun byId(type: RecordType, id: String): RecordCategory? =
        entries.firstOrNull { it.type == type && it.id == id }

    /**
     * 将 [kinds] 收敛到 [recordType] 所属类别集合(精准查询保证,防跨类型 kind 名碰撞):
     * - [kinds] 为 null/空集 → 返回 `null`(不过滤,语义同 [RecordFilter] 全集);
     * - [recordType] 为 null/未知 → 原样返回(无类型约束时不收敛);
     * - 否则返回与类型合法类别相交的集合,**可能为空 `Set`**(调用方据其短路为「无匹配」)。
     */
    fun validateKinds(recordType: String?, kinds: Set<String>?): Set<String>? {
        if (kinds.isNullOrEmpty()) return null
        val type = RecordType.fromWire(recordType) ?: return kinds
        val valid = forType(type).map { it.id }.toSet()
        return kinds.intersect(valid)
    }

    /**
     * 将 [operations] 收敛到 [recordType] 所属操作的合法集合(与 [validateKinds] 同规)。
     * 操作枚举([CryptoOperation]/[SqlOperation])按类型分属,跨类型名碰撞概率低但同样收敛。
     */
    fun validateOperations(recordType: String?, operations: Set<String>?): Set<String>? {
        if (operations.isNullOrEmpty()) return null
        val type = RecordType.fromWire(recordType) ?: return operations
        val valid = when (type) {
            RecordType.CRYPTO -> CryptoOperation.entries.map { it.name }.toSet()
            RecordType.DATABASE -> SqlOperation.entries.map { it.name }.toSet()
        }
        return operations.intersect(valid)
    }

    /** 默认启用的类别 id 集合(供 HookSwitch 默认推导/校验)。 */
    fun defaultEnabledIds(type: RecordType): Set<String> =
        forType(type).filter { it.defaultEnabled }.map { it.id }.toSet()
}
