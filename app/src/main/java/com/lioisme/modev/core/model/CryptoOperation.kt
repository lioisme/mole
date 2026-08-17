package com.lioisme.modev.core.model

/**
 * 算法调用中的具体操作阶段。
 */
enum class CryptoOperation {
    /** 获取算法实例 */
    GET_INSTANCE,

    /** 初始化(设置密钥/IV/方向) */
    INIT,

    /** 分块更新输入 */
    UPDATE,

    /** 收尾计算(doFinal / digest / doFinal(Mac)) */
    DO_FINAL,

    /** 摘要计算(MessageDigest.digest) */
    DIGEST,

    /** 签名(Signature.sign) */
    SIGN,

    /** 验签(Signature.verify) */
    VERIFY,

    /** 读取 IV(Cipher.getIV,覆盖 GCM/CBC 随机 IV) */
    GET_IV,

    /** 密钥/参数对象构造 */
    CONSTRUCT,

    /** 生成密钥(KeyGenerator / KeyPairGenerator) */
    GENERATE,

    /** 还原私钥 / 公钥 / 对称密钥 */
    GENERATE_PRIVATE,
    GENERATE_PUBLIC,
    GENERATE_SECRET,

    /** 从密钥库取键(KeyStore.getKey / getEntry / getCertificateChain) */
    GET_KEY,

    /** 写入密钥库(KeyStore.setKeyEntry / setCertificateEntry) */
    SET_KEY,

    /** 删除密钥库条目(KeyStore.deleteEntry) */
    DELETE_KEY,

    /** 加载密钥库(KeyStore.load) */
    LOAD,

    /** 密钥协商阶段(KeyAgreement.doPhase) */
    DO_PHASE,

    /** 生成随机种子(SecureRandom.generateSeed) */
    GENERATE_SEED,

    /** Base64 编码 / 解码 */
    ENCODE,
    DECODE,
}