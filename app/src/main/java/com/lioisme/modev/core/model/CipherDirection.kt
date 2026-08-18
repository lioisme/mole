package com.lioisme.modev.core.model

import javax.crypto.Cipher

/**
 * Cipher 方向模式枚举(ADR-5):替代 [CryptoRecord.direction] 的裸 Int 魔法数。
 * 模式值与 JCA 常量一致(1=加密 2=解密 3=包装 4=解包装);
 * 记录模型与数据库仍以 Int 承载(线协议/存储兼容),展示层一律消费本枚举。
 */
enum class CipherDirection(val mode: Int) {
    ENCRYPT(Cipher.ENCRYPT_MODE),
    DECRYPT(Cipher.DECRYPT_MODE),
    WRAP(Cipher.WRAP_MODE),
    UNWRAP(Cipher.UNWRAP_MODE),
    ;

    companion object {
        /** 按模式值查找;未知值返回 null(展示层回退「模式 N」)。 */
        fun fromMode(mode: Int?): CipherDirection? =
            mode?.let { m -> entries.firstOrNull { it.mode == m } }
    }
}