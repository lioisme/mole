package com.lioisme.modev.domain.config

import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 每应用配置契约(监听域 + 功能域偏差;V45 ML-1 = 需求 2「数据层⟂方法层严格隔离」与
 * 需求 3「UI 与 MCP 同为方法层一等公民」在配置面的落地)。
 *
 * 此前配置面无 domain 契约:6 个 ViewModel + 4 个 MCP 工具组直连 `data.repo.SettingsRepository`
 * 具体类(全工程 55 + 12 行 `import ...data.*`),存储门面即方法层门面 —— 无法替换存储、
 * 无法做替身、双端共享实现细节。本契约是 [com.lioisme.modev.domain.record.RecordService]
 * 在配置面的同构补齐:
 *
 * - 有效配置 = 内置默认([HookSwitch.DEFAULT])⊕ 该包稀疏二态偏差,解析唯一入口
 *   [EffectiveSwitch.resolve](AGENTS #16),关键词库叠加由实现方内部完成,消费方不感知;
 * - 写路径唯一 = [setOverride](整份偏差覆盖写)+ [resetOverride](按能力域清除):
 *   持久化 + 内存热更 + 定向推送在实现内收口,消费方不得自行落盘或直调推送
 *   (AGENTS #19 语义);两域消费方**永不**互清(V45 T3.5 / DUAL-12);
 * - 零偏差即等价删除存储键(实现方保证),[overrideFor] 恒返回非 null 快照(免空判)。
 */
interface ListenConfigService {

    /** 默认配置流(V25:恒为内置推荐值,无任何 UI 写入路径)。 */
    val globalSwitch: StateFlow<HookSwitch>

    /** 已存在的每应用自定义偏差(包名 → 偏差;仅含非零偏差项)。 */
    val overrides: StateFlow<Map<String, AppListenOverride>>

    /** 该包偏差快照(未定制返回 [AppListenOverride.DEFAULT])。 */
    fun overrideFor(packageName: String): AppListenOverride

    /** 该包有效配置快照(默认 ⊕ 偏差,同步读)。 */
    fun effectiveSwitch(packageName: String): HookSwitch

    /** 该包有效配置流(默认/偏差/关键词库任一变更即重发射)。 */
    fun effectiveSwitchFlow(packageName: String): Flow<HookSwitch>

    /** 写入该包偏差(零偏差归一为删除;变更后定向推送该包会话)。 */
    suspend fun setOverride(packageName: String, override: AppListenOverride)

    /**
     * 清除该包**某一能力域**的偏差,另一域原样保留(V45 T3.5 / DUAL-12)。
     *
     * 恢复默认必须是域内的:监听页与功能页各有一行「恢复默认配置」,
     * 而契约此前只有全量清键的 `removeOverride` ⇒ 在监听页点恢复会连带抹掉
     * 用户的功能域定制(画像档位、黑名单追加、关键词子集),反之亦然 ——
     * 双域契约(AGENTS #23)在写侧有闸、清侧没有,是同一状态机上的口径分叉。
     * 「两域一起清」不再是契约方法:实现方在零偏差时自行删键(见 [setOverride])。
     */
    suspend fun resetOverride(packageName: String, domain: CapabilityDomain) {
        setOverride(packageName, overrideFor(packageName).clearDomain(domain))
    }
}

/**
 * 全局功能资产契约(伪装画像选择 + 检测黑名单追加项;词表走
 * [com.lioisme.modev.domain.asset.KeywordLibraryService])。
 *
 * 全局功能配置是所有作用域包的 `feature` 默认值,变更须由实现方
 * 重播注入侧种子 + 向全部在线会话重推(AGENTS #23),消费方只读写配置本身。
 */
interface FeatureService {

    /** 全局功能配置(画像 + 黑名单追加;默认空 = 内置行为)。 */
    val globalFeature: StateFlow<FeatureConfig>

    /** 写入全局功能配置(持久化 + 种子重播 + 全量重推,均由实现方内部收口)。 */
    suspend fun setGlobalFeature(config: FeatureConfig)
}
