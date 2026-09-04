package com.lioisme.modev.core.model

/**
 * 功能域数据段(V37 T1.2):与监听布尔开关(HookSwitch 平铺位)分离的
 * **「干预参数」承载体**——此前功能域只有布尔开关,干预数据仅 dialogKeywords 一项
 * 且无 UI 入口;本段扩展为完整的功能数据面:
 *
 * - [dialogKeywords]:弹窗拦截有效词表(宿主端由全局关键词库 ∧ 每应用启用集
 *   在 push 时解析产出,注入侧只消费结果列表);
 * - [spoofProfileId] + [spoofOverrides]:设备/网络伪装画像选择与字段覆写
 *   (见 [SpoofProfiles],内置档位 + 可编辑画像);
 * - extra* 系列:检测隐藏黑名单的**用户追加项**(内置基线只增不删,
 *   语义 = 内置 ∪ 追加)。
 *
 * 序列化:作为 [HookSwitch] 的嵌套 `feature` JSON 对象随 wire `switch` 字段透传
 * (信封 VERSION 恒 4 零感知);旧配置缺失本段时 Gson 给 JVM 默认值,
 * [HookSwitch.fromJson] 据 configVersion 迁移扁平 [HookSwitch.dialogKeywords]。
 */
data class FeatureConfig(
    /** 弹窗拦截有效关键词(宿主端解析产物;空 = 不拦截)。
     *  V37 T2.1 起为 CONTAINS 语义兼容通道;结构化规则见 [dialogKeywordRules](优先)。 */
    val dialogKeywords: List<String> = emptyList(),
    /** 弹窗拦截有效规则(文本 + 匹配模式;非空时优先于 [dialogKeywords])。 */
    val dialogKeywordRules: List<KeywordRule> = emptyList(),
    /** 伪装画像档位 id([SpoofProfiles.AUTO] 默认;档位缺失回落 AUTO)。 */
    val spoofProfileId: String = SpoofProfiles.AUTO,
    /** 画像字段覆写(键形态见 [SpoofProfiles.resolve])。 */
    val spoofOverrides: Map<String, String> = emptyMap(),
    /** Root 包名黑名单追加项(与内置 28 项并集)。 */
    val extraRootPackages: List<String> = emptyList(),
    /** Xposed 类名前缀追加项(与内置前缀并集)。 */
    val extraXposedPrefixes: List<String> = emptyList(),
    /** Frida 端口追加项(与内置 27042/27043 并集)。 */
    val extraFridaPorts: List<Int> = emptyList(),
    /** proc/maps/mountinfo/cgroup 掩码关键词追加项(与内置词表并集)。 */
    val extraMaskKeywords: List<String> = emptyList(),
)
