package com.lioisme.modev.testtarget

/**
 * 用例注册表(单一事实源):与 docs/V35-FULL-FEATURE-ACCEPTANCE-TASK.md 验收矩阵逐一对应。
 * 编排脚本经 `am start -n ./.TriggerActivity --es domain <域> --es case <ID>` 驱动;
 * `--es domain list` 输出全部用例 ID(TT_RESULT case=REGISTRY result=...)。
 */
object TestCases {

    /** 域 → 有序用例 ID。 */
    val DOMAINS: Map<String, List<String>> = linkedMapOf(
        "crypto" to listOf(
            "L-CRY-01", "L-CRY-02", "L-CRY-03", "L-CRY-04", "L-CRY-05", "L-CRY-06",
            "L-CRY-07", "L-CRY-08", "L-CRY-09", "L-CRY-10", "L-CRY-11", "L-CRY-12",
            "L-CRY-13", "L-CRY-14",
        ),
        "db" to listOf(
            "L-DB-01", "L-DB-02", "L-DB-03", "L-DB-04", "L-DB-05", "L-DB-06",
            "L-DB-07", "L-DB-08", "L-DB-09", "L-DB-11", "L-DB-12",
        ),
        "behavior" to listOf(
            "L-BHV-01", "L-BHV-02", "L-BHV-03", "L-BHV-04", "L-BHV-05", "L-BHV-06",
            "L-BHV-07", "L-BHV-08", "L-BHV-09", "L-BHV-10", "L-BHV-11", "L-BHV-12",
            "L-BHV-13",
        ),
        "prefs" to listOf(
            "L-PRF-01", "L-PRF-02", "L-PRF-03", "L-PRF-04", "L-PRF-05", "L-PRF-06",
        ),
        "webview" to listOf(
            "L-WV-01", "L-WV-02", "L-WV-03", "L-WV-04", "L-WV-05", "L-WV-06", "L-WV-07",
        ),
        "network" to listOf("L-NET-01", "L-NET-02"),
        "deviceenv" to listOf(
            "L-DEV-01", "L-DEV-02", "L-DEV-03", "L-DEV-04", "L-DEV-05", "L-DEV-06",
            "L-DEV-07", "L-DEV-08", "L-DEV-09", "L-DEV-10", "L-DEV-11", "L-DEV-12",
            "L-DEV-13", "L-DEV-14", "L-DEV-15", "L-DEV-16", "L-DEV-17", "L-DEV-18",
            "L-DEV-19", "L-DEV-20", "L-DEV-21",
        ),
        "ui" to listOf("L-UI-01", "L-UI-02", "L-UI-03", "L-UI-04", "L-UI-05"),
        "system" to listOf("L-SYS-01", "L-SYS-02", "L-SYS-03"),
        "process" to listOf("L-PRC-01"),
        // ---- 增强二态(ON 轮断言干预生效;OFF 轮断言原始行为) ----
        "enhance-secure" to listOf("E-SEC-01", "E-SEC-02", "E-SEC-03", "E-SF-01"),
        "enhance-proc" to listOf("E-MP-01", "E-EXIT-01", "E-EXIT-02", "E-CRASH-01"),
        "enhance-dialog" to listOf("E-DLG-01", "E-DLG-02"),
        "enhance-ssl" to listOf(
            "E-SSL-01", "E-SSL-02", "E-SSL-03", "E-SSL-04", "E-SSL-05", "E-SSL-06",
        ),
        "enhance-proxy" to listOf("E-PRX-01", "E-PRX-02", "E-PRX-03", "E-PRX-04"),
        "enhance-vpn" to listOf("E-VPN-01", "E-VPN-02", "E-VPN-03", "E-VPN-04", "E-DNS-01"),
        "enhance-root" to listOf("E-ROOT-01", "E-ROOT-02", "E-ROOT-03", "E-ROOT-04"),
        "enhance-xposed" to listOf("E-XPP-01", "E-XPP-02", "E-XPP-03"),
        "enhance-emulator" to listOf(
            "E-EMU-01", "E-EMU-02", "E-EMU-03", "E-EMU-04", "E-EMU-05", "E-EMU-06", "E-EMU-07",
        ),
        "enhance-procfile" to listOf("E-PRC-01", "E-PRC-02", "E-PRC-03", "E-PRC-04", "E-PRC-05"),
        // ---- 联动 ----
        "linkage" to listOf("C-GATE-01", "C-THR-01", "C-MULTI-01"),
        // ---- 冷启动冒烟(D-17 防回归:caller-classloader 帧语义完整性) ----
        "smoke" to listOf("L-SMOKE-01"),
    )

    val ALL: List<String> = DOMAINS.values.flatten()

    fun domainOf(case: String): String? = DOMAINS.entries.firstOrNull { case in it.value }?.key
}
