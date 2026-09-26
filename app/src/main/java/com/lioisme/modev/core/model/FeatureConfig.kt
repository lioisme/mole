package com.lioisme.modev.core.model

/**
 * 功能域数据段:与监听布尔开关(HookSwitch 平铺位)分离的
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
    /** 伪装画像档位 id( = **画像库主键**,不再是内置档位名;null/空 = 不伪装)。 */
    val spoofProfileId: String = "",
    /**
     * 画像字段覆写(V47 T2 起为**只读遗留通道**:无 UI 入口、MCP 声明面已移除,
     * 存量用户值仍生效。裁决沿革见 docs/history/TASK-V47-SPOOF-PLAN.md 决策 D-3 修订)。
     * 生效顺序:先取 [deviceProfile] 展开快照,再以本表逐项覆盖。
     */
    val spoofOverrides: Map<String, String> = emptyMap(),
    /**
     * **宿主展开后的画像快照**(V47 T2 / 决策 D-12):`spoofProfileId` → 画像库正文的解析结果。
     *
     * 为什么快照也要随配置下发,而不是让注入侧自己按 id 查库:
     * 画像正文住在 Room,而注入侧的三条取配置路径都不经宿主进程
     * (`/data/local/tmp/modev_config_{pkg}.json` 由外部推送、`switch_global` + `override_*`
     * 直读、wire 断连时的磁盘兜底)—— 只带 id 就必然要求跨进程读 Room。
     * 同一文件已有先例:[dialogKeywordRules] 就是宿主解析后的产物,而库侧的
     * `keywordIds` 恒不下发。故:**id 是宿主侧绑定键,快照是注入侧消费物**。
     *
     * 空画像([DeviceProfile.EMPTY])= 无画像生效;注入侧禁按 id 自行反查。
     */
    val deviceProfile: DeviceProfile = DeviceProfile.EMPTY,
    /**
     * 自适应掩蔽(条件档,V47 T2 / 决策 D-5):画像**未填**的字段上,是否仍按模拟器
     * 特征族做「只在脏值上改」的条件掩蔽。
     *
     * 与画像强制档严格分档:填了画像 = 无条件写入;本开关只管画像留空的字段。
     * 迁移来路:旧 `spoofProfileId = "auto"`(纯条件掩蔽、无固定机型)⇒ 本位置 true
     * 且画像解绑,以免一次升级把「条件档」静默升级成「无条件改写」。
     */
    val adaptiveMask: Boolean = false,
    /** Root 包名黑名单追加项(与内置基线并集,只增不删)。 */
    val extraRootPackages: List<String> = emptyList(),
    /** Xposed 类名前缀追加项(与内置前缀并集)。 */
    val extraXposedPrefixes: List<String> = emptyList(),
    /** Frida 端口追加项(与内置 27042/27043 并集)。 */
    val extraFridaPorts: List<Int> = emptyList(),
    /** proc/maps/mountinfo/cgroup 掩码关键词追加项(与内置词表并集)。 */
    val extraMaskKeywords: List<String> = emptyList(),
)
