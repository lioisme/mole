package com.lioisme.modev.core.model

/**
 * 算法类别。每个类别对应一类 Hooker 与一个独立功能开关。
 */
enum class CryptoKind {
    /** 摘要算法:MD5 / SHA-1 / SHA-256 / SM3 等(MessageDigest) */
    MESSAGE_DIGEST,

    /** 对称/非对称加解密:AES / DES / RSA 等(Cipher) */
    CIPHER,

    /** 带密钥的哈希:HMAC-MD5 / HMAC-SHA1 / HMAC-SHA256 等(Mac) */
    MAC,

    /** 数字签名:RSA / ECDSA(Signature) */
    SIGNATURE,

    /** 密钥载体与参数:SecretKeySpec / IvParameterSpec / GCMParameterSpec / PBEKeySpec 构造 */
    KEY_PARAM,

    /** 密钥生成:KeyGenerator / KeyPairGenerator */
    KEY_GENERATION,

    /** 密钥还原:KeyFactory / SecretKeyFactory */
    KEY_FACTORY,

    /** 密钥库:KeyStore(别名/加载/取键/证书链) */
    KEY_STORE,

    /** 密钥协商:KeyAgreement(ECDH/DH 共享密钥) */
    KEY_AGREEMENT,

    /** 随机数:SecureRandom(种子/随机字节/IV 来源) */
    RANDOM,

    /** 编码转换:Base64(android.util.Base64 / java.util.Base64) */
    BASE64,
}
