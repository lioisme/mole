package com.lioisme.modev.core.model

/**
 * 记录类型标识(V10 能力化架构):当前仅 [CRYPTO]。
 * 未来行为监听、交互监听、数据库监听、文件监听等记录类型按此扩展;
 * 存储与线协议以字符串承载,缺失时按 [CRYPTO] 解释(旧数据/旧协议向后兼容)。
 */
object RecordTypes {

    /** 算法捕获记录(JCA 调用)。 */
    const val CRYPTO = "crypto"
}
