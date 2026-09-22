package com.lioisme.modev.core.model

/**
 * 能力域归属(V37 D-0 基线,双域拆分单一事实源):
 *
 * - [LISTEN] 监听域:被动采集,仅产出记录,不改变宿主行为
 *   (算法/数据库/行为/偏好/WebView/系统信息 + 网络请求监控 + 弹窗/控件/Activity 观测 + 检测监控);
 * - [FEATURE] 功能域:主动干预,改变宿主可见行为
 *   (退出拦截/崩溃预防/解除录屏/弹窗关键词拦截/SSL 绕过/代理 VPN 隐藏/设备环境伪装)。
 *
 * 类别归属在 [CategoryTaxonomy] 逐类标注;功能域类别的**干预动作本身**(非仅记录)
 * 必须经 master ∧ kind 门控(FeatureGate 红线,见 AGENTS.md V37 契约)。
 */
enum class CapabilityDomain {
    /** 监听域:被动采集。 */
    LISTEN,

    /** 功能域:主动干预。 */
    FEATURE,
}

/** 对侧域(穷尽 `when`:新增域编译期强制登记,杜绝 `!!`/默认回落)。 */
val CapabilityDomain.opposite: CapabilityDomain
    get() = when (this) {
        CapabilityDomain.LISTEN -> CapabilityDomain.FEATURE
        CapabilityDomain.FEATURE -> CapabilityDomain.LISTEN
    }
