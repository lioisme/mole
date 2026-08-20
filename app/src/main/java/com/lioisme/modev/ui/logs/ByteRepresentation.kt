package com.lioisme.modev.ui.logs

import com.lioisme.modev.core.util.BytesView

/**
 * 字节区块表示三态(V19 T5):文本 / Base64 / Hex。
 * 捕获侧按原始字节三态持久化(hex 恒为截断前缀,运行时反推 Base64 会静默截断,
 * 禁止);渲染层按所选表示展示,旧记录(base64=null)降级双态。
 */
internal enum class ByteRepresentation { TEXT, BASE64, HEX }

/**
 * 可用表示列表(纯函数):TEXT 缺文本隐藏;BASE64 缺失(旧记录)隐藏;HEX 恒在。
 * 顺序 = 分段按钮展示顺序(文本 / Base64 / Hex)。
 */
internal fun BytesView.availableRepresentations(): List<ByteRepresentation> = buildList {
    if (text != null) add(ByteRepresentation.TEXT)
    if (base64 != null) add(ByteRepresentation.BASE64)
    add(ByteRepresentation.HEX)
}

/** 默认表示(纯函数):TEXT 可用 → TEXT,否则 HEX(双态退 Hex)。 */
internal fun BytesView.defaultRepresentation(): ByteRepresentation =
    if (text != null) ByteRepresentation.TEXT else ByteRepresentation.HEX

/**
 * 所选表示内容(纯函数):TEXT/BASE64 缺失 → null(分段按钮隐藏对应段,
 * 不会以非法表示进入渲染);HEX 恒有。
 */
internal fun BytesView.representationText(repr: ByteRepresentation): String? = when (repr) {
    ByteRepresentation.TEXT -> text
    ByteRepresentation.BASE64 -> base64
    ByteRepresentation.HEX -> hex
}

/**
 * 所选表示字符数(折叠 / 分档判定基准,V19 T5 由 hex 改为所选表示):
 * text.length ≤ 字节数 ≤ hex.length、base64.length ≈ 4/3 字节数 ≤ hex.length,
 * hex 仍为各表示上界,但按所选表示判定可让短 text 恒全量展示(不回退折叠预览)。
 */
internal fun BytesView.representationChars(repr: ByteRepresentation): Int =
    representationText(repr)?.length ?: 0