package com.lioisme.modev.core.codec

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * 全工程统一 Gson 实例(ADR-5):一致的配置(关闭 HTML 转义),
 * 保证同一对象在注入侧编码与宿主侧解码的序列化结果完全一致
 * (默认转义的 Gson 会因 `<` 等字符产生双端不一致,历史曾多实例并存)。
 */
object Json {
    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
}
