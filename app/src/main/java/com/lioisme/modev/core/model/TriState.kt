package com.lioisme.modev.core.model

/**
 * 三态覆盖(V23,每应用监听设置的原子语义):
 *
 * - [INHERIT] 跟随全局([HookSwitch] 对应位原样生效);
 * - [ON] 强制启用(无论全局如何);
 * - [OFF] 强制停用(无论全局如何)。
 *
 * resolve 产物仍是普通 [HookSwitch](见 [EffectiveSwitch.resolve]),
 * wire 协议与注入侧零感知本枚举(modern/legacy 双变体天然工作)。
 */
enum class TriState {

    /** 跟随全局配置。 */
    INHERIT,

    /** 强制启用。 */
    ON,

    /** 强制停用。 */
    OFF;

    companion object {

        /**
         * 名称 → 枚举(存储/协议漂移兜底):未知或 null 一律回落 [INHERIT]
         * (宁可漏覆盖,不可误停用/误启用)。
         */
        fun ofName(name: String?): TriState = when (name) {
            ON.name -> ON
            OFF.name -> OFF
            else -> INHERIT
        }
    }
}
