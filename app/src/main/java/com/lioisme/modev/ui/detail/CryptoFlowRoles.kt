package com.lioisme.modev.ui.detail

import com.lioisme.modev.R
import com.lioisme.modev.core.model.CipherDirection
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation

/**
 * 数据流角色解析(V20 R4 详情页数据关系可视化)。
 * 按 (kind, operation, direction) 将输入/输出字节区块映射为语义角色标签,
 * 未命中返回 (null, null) → UI 回落通用「Input / Output」。
 *
 * 语义约定:
 * - CIPHER:方向优先(direction 来自 init opmode),operation 兜底(WebView 等无 direction 来源);
 * - 摘要/MAC/签名:消息 → 结果;验签结果单列 meta_verify,输出仍标签名数据;
 * - BASE64:数据 ↔ Base64;
 * - 密钥类(kind 生成/还原/协商):输出 = 生成的密钥材料。
 */
internal fun cryptoFlowRoles(
    kind: CryptoKind,
    operation: CryptoOperation,
    direction: Int?,
): Pair<Int?, Int?> = when (kind) {
    CryptoKind.CIPHER -> cipherRoles(operation, CipherDirection.fromMode(direction))

    CryptoKind.MESSAGE_DIGEST -> when (operation) {
        CryptoOperation.DIGEST -> R.string.role_input_message to R.string.role_output_digest
        else -> null to null
    }

    CryptoKind.MAC -> when (operation) {
        CryptoOperation.SIGN -> R.string.role_input_message to R.string.role_output_mac
        else -> null to null
    }

    CryptoKind.SIGNATURE -> when (operation) {
        CryptoOperation.SIGN -> R.string.role_input_message to R.string.role_output_signature
        CryptoOperation.VERIFY -> R.string.role_input_message to R.string.role_output_verify
        else -> null to null
    }

    CryptoKind.BASE64 -> when (operation) {
        CryptoOperation.ENCODE -> R.string.role_input_data to R.string.role_output_base64
        CryptoOperation.DECODE -> R.string.role_input_base64 to R.string.role_output_data
        else -> null to null
    }

    CryptoKind.KEY_GENERATION, CryptoKind.KEY_FACTORY, CryptoKind.KEY_AGREEMENT -> when (operation) {
        CryptoOperation.GENERATE,
        CryptoOperation.GENERATE_PRIVATE,
        CryptoOperation.GENERATE_PUBLIC,
        CryptoOperation.GENERATE_SECRET,
        CryptoOperation.CONSTRUCT,
        CryptoOperation.DO_PHASE,
        -> null to R.string.role_output_key
        else -> null to null
    }

    CryptoKind.KEY_PARAM, CryptoKind.KEY_STORE, CryptoKind.RANDOM -> null to null
}

private fun cipherRoles(operation: CryptoOperation, direction: CipherDirection?): Pair<Int?, Int?> {
    val dir = direction ?: when (operation) {
        CryptoOperation.ENCRYPT -> CipherDirection.ENCRYPT
        CryptoOperation.DECRYPT -> CipherDirection.DECRYPT
        else -> null
    }
    return when (dir) {
        CipherDirection.ENCRYPT -> R.string.role_input_plaintext to R.string.role_output_ciphertext
        CipherDirection.DECRYPT -> R.string.role_input_ciphertext to R.string.role_output_plaintext
        else -> null to null
    }
}