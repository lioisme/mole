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

    /** Base64 编码 / 解码 */
    ENCODE,
    DECODE,
}