package com.lioisme.modev.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 设备画像库存条目(V47 T2,基线 `Template` 的 Modev 化):
 * 一行 = 一份**可复用画像**,画像正文以 [DeviceProfile] 的 JSON blob 承载于
 * [profileJson](与基线同构:一字段一列会把 41 列的演进成本压在每次迁移上,
 * 而 blob + `ignoreUnknownKeys` 让新增字段零迁移)。
 *
 * 与基线的两处有意偏离:
 *  1. 基线把内置模板账本拆成第二张表 `BundledTemplateState`;本表**内联**账本列
 *     ([builtinSeedId]/[builtinVersion]/[builtinFingerprint]/[managed]/[deleted]),
 *     语义等价、少一次迁移与一次 join;
 *  2. 基线区分 COMMON/EXCLUSIVE 两型模板;Modev 的「可复用画像 + 每应用 `profileId`
 *     绑定」已吸收 EXCLUSIVE 语义,故此处**不存在类型列**(见 AGENTS V47 决策 D-3)。
 *
 * [deleted] 为删除墓碑:内置档被用户删除后恒不再随升级复活。
 */
@Entity(
    tableName = SpoofProfileEntity.TABLE_NAME,
    indices = [Index(value = ["builtinSeedId"])],
)
data class SpoofProfileEntity(
    /** 画像 id(uuid 无横线,或内置档稳定键 `builtin:<seedId>`)。 */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** 用户可见名称。 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 用户可见摘要(可空)。 */
    @ColumnInfo(name = "summary")
    val summary: String,

    /** 画像正文 = [DeviceProfile] 序列化 JSON。 */
    @ColumnInfo(name = "profileJson")
    val profileJson: String,

    /** 内置档稳定键;null = 用户 DIY 画像。 */
    @ColumnInfo(name = "builtinSeedId")
    val builtinSeedId: String?,

    /** 内置档内容版本(仅内置行有值)。 */
    @ColumnInfo(name = "builtinVersion")
    val builtinVersion: Int?,

    /** 内置档内容指纹(升级时判「用户是否改过」的依据)。 */
    @ColumnInfo(name = "builtinFingerprint")
    val builtinFingerprint: String?,

    /** 内置行是否仍受账本管辖(用户改过 ⇒ false ⇒ 不再原地覆盖)。 */
    @ColumnInfo(name = "managed")
    val managed: Boolean,

    /** 删除墓碑(内置档删后恒不复活)。 */
    @ColumnInfo(name = "deleted")
    val deleted: Boolean,

    /** 创建时刻(epoch millis)。 */
    @ColumnInfo(name = "createTime")
    val createTime: Long,

    /** 更新时刻(epoch millis)。 */
    @ColumnInfo(name = "updateTime")
    val updateTime: Long,
) {
    companion object {
        /** 表名唯一事实源(禁在 DAO/迁移 SQL 写字面量)。 */
        const val TABLE_NAME = "spoof_profile"
    }
}
