package com.lioisme.modev.core.model

import com.lioisme.modev.core.util.BytesView

/**
 * 一条完整的算法调用记录(不可变)。
 *
 * @param id 宿主侧数据库主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号(协议 v3 可靠性:宿主 ACK 与幂等去重依据;0 = 无)
 * @param recordType 记录类型([RecordTypes],V10 起;默认 crypto,向后兼容)
 * @param packageName 目标包名
 * @param kind 算法类别
 * @param operation 操作阶段
 * @param algorithm 算法描述,如 "AES/CBC/PKCS5Padding"、"MD5"、"HmacSHA256"
 * @param provider 提供者,如 "Conscrypt" / "AndroidKeyStoreBCWorkaround"
 * @param direction Cipher 方向:ENCRYPT_MODE=1 / DECRYPT_MODE=2 / WRAP_MODE=3 / UNWRAP_MODE=4
 * @param key 密钥双态视图;AndroidKeyStore 硬件密钥时为 null,见 [keyAlias]
 * @param iv 初始化向量双态视图
 * @param keyAlias AndroidKeyStore 密钥别名(密钥材料不可导出时使用)
 * @param keyExportable 密钥材料是否可导出(硬件密钥为 false)
 * @param input 输入数据(明文/密文/摘要数据)
 * @param output 输出数据(密文/明文/摘要值/签名)
 * @param salt PBE 盐(与 iv 语义区分)
 * @param aad AEAD 附加认证数据(Cipher.updateAAD)
 * @param publicKey 公钥数据(密钥对公钥 / 密钥协商对端公钥)
 * @param params 标量参数承载(迭代次数/密钥长度/tag 长度/keysize/曲线名/flags 等),键值对稳定可扩展
 * @param verifyResult 最近一次验签结果
 * @param processName 目标进程名(多进程 App 区分主/子进程)
 * @param contextId 关联同一算法实例多次调用的对象标识(实例存活期内唯一)
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈(最内层在前)
 * @param timestamp 毫秒时间戳
 */
data class CryptoRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.CRYPTO,
    val packageName: String,
    val kind: CryptoKind,
    val operation: CryptoOperation,
    val algorithm: String,
    val provider: String? = null,
    val direction: Int? = null,
    val key: BytesView? = null,
    val iv: BytesView? = null,
    val keyAlias: String? = null,
    val keyExportable: Boolean = true,
    val input: BytesView? = null,
    val output: BytesView? = null,
    val salt: BytesView? = null,
    val aad: BytesView? = null,
    val publicKey: BytesView? = null,
    val params: Map<String, String>? = null,
    val verifyResult: Boolean? = null,
    val processName: String? = null,
    val contextId: Long = 0L,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): CryptoRecord = copy(seq = seq)
}