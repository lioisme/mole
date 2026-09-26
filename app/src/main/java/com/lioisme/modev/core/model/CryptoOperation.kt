package com.lioisme.modev.core.model

/**
 * 算法调用中的具体操作阶段。
 */
enum class CryptoOperation {
    /** 初始化(设置密钥/IV/方向)。
     *  V50 孤儿整改:原 `GET_INSTANCE` 恒零发射 —— 变换名已由 `Cipher.init(String)` 第一实参
     *  捕获进 `CryptoRecord.algorithm`(每条记录都带),另发一条事件零信息增量、
     *  却把高频算法面的记录数翻倍 ⇒ 删除,不补钩。 */
    INIT,

    /** 分块更新输入 */
    UPDATE,

    /** 收尾计算(doFinal / digest / doFinal(Mac);Cipher 的 WRAP/UNWRAP/未知方向亦归此项) */
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

    /**
     * 加密收尾(`Cipher.doFinal`,init 方向 = ENCRYPT_MODE)。
     * V29 曾把这两个值当 WebCrypto 迁移遗留,但 V50 起它们是 **Cipher 真操作**:
     * 方向在 `init` 即确知,故 doFinal 按方向发射(不再统一发 DO_FINAL),
     * 详情页加/解密流向角色([CryptoFlowRoles] 的 cipherRoles)由此真正可达。
     */
    ENCRYPT,

    /** 解密收尾(`Cipher.doFinal`,init 方向 = DECRYPT_MODE)。 */
    DECRYPT,
}