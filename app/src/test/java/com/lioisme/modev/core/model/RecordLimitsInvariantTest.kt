package com.lioisme.modev.core.model

import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * 记录上限三角不变量测试(V45 T1.3 / ST-3,R1「底层数据架构专业」的可执行约束)。
 *
 * 旧形态三个上限各写一处且互不自洽:`DEFAULT_MAX_BYTES_PER_FIELD = 1MiB` 且
 * `MAX_CONTENT_MATERIALIZE_BYTES = 8MiB`,而 `WIRE_MAX_LINE_CHARS = 4Mi 字符` ——
 * 8MiB 字段仅 hex 一份就 16M 字符(超行上限 4 倍),1MiB 字段三态合计 ≈4.53M 字符
 * 亦超上限。**后果不是「截断」而是「整条记录永不可能送达」**:先序列化、先落 WAL,
 * 再在排空时被判为损坏行永久丢弃,并且每次判废触发一次整文件重写。
 *
 * 本测试把「任何合法配置产出的记录行恒 ≤ 传输上限」锁成断言,并钉死回退空间。
 */
class RecordLimitsInvariantTest {

    @org.junit.Test
    fun materializationCapKeepsEveryRecordTransportable() {
        val worst = RecordLimits.worstCaseContentChars(RecordLimits.MAX_CONTENT_MATERIALIZE_BYTES)
        assertTrue(
            "内容视图最坏字符数必须 ≤ 行上限 - 非内容预留:$worst vs " +
                "${RecordLimits.WIRE_MAX_LINE_CHARS - RecordLimits.NON_CONTENT_RESERVE_CHARS}",
            worst <= RecordLimits.WIRE_MAX_LINE_CHARS - RecordLimits.NON_CONTENT_RESERVE_CHARS,
        )
        assertTrue(
            "物化上限不得超过可送达上界",
            RecordLimits.MAX_CONTENT_MATERIALIZE_BYTES <= RecordLimits.MAX_SAFE_BYTES_PER_FIELD,
        )
    }

    /** 用户意图值大于可送达上界:必须被硬保险丝钳制(而非静默丢整条),故两者关系要显式成立。 */
    @org.junit.Test
    fun userFacingDefaultExceedsSafeBoundAndIsClamped() {
        assertTrue(
            "默认意图值本应大于安全上界(否则钳制说明失真)",
            RecordLimits.DEFAULT_MAX_BYTES_PER_FIELD > RecordLimits.MAX_SAFE_BYTES_PER_FIELD,
        )
        assertTrue(
            "BytesView 硬保险丝必须来自同一事实源",
            BytesView.HARD_MAX_HEX_CHARS == RecordLimits.MAX_CONTENT_MATERIALIZE_BYTES * 2,
        )
        // 钳制生效:任意用户配置经 BytesView 后不产出超上限视图(1MiB 意图 → 元数据 + 截断标记)
        val huge = ByteArray(RecordLimits.DEFAULT_MAX_BYTES_PER_FIELD) { 0x61 }
        val view = BytesView.of(
            huge,
            maxHexChars = RecordLimits.DEFAULT_MAX_HEX_CHARS,
            maxTextChars = RecordLimits.DEFAULT_MAX_TEXT_CHARS,
        )!!
        assertTrue(
            "hex 视图字符数不得越过硬保险丝:${view.hex.length} > ${BytesView.HARD_MAX_HEX_CHARS}",
            view.hex.length <= BytesView.HARD_MAX_HEX_CHARS,
        )
        assertTrue("base64 视图不得越过硬保险丝", (view.base64?.length ?: 0) <= BytesView.HARD_MAX_B64_CHARS)
        assertTrue("越界即必须声明截断(不得伪装成全量)", view.truncated)
        assertEquals(RecordLimits.DEFAULT_MAX_BYTES_PER_FIELD, view.totalLength)
    }

    /** 钉死回退:V44 T2.3 的 8MiB 取值破坏不变量,任何人上调都会在此红。 */
    @org.junit.Test
    fun previouslyShippedCapViolatedTheInvariant() {
        val eightMiB = 8 * 1024 * 1024
        assertTrue(
            "历史值 8MiB 必须被判定为不可送达(本测试是回退护栏)",
            RecordLimits.worstCaseContentChars(eightMiB) > RecordLimits.WIRE_MAX_LINE_CHARS,
        )
        assertTrue(
            "任何 ≥ 安全上界的取值都会破坏不变量",
            RecordLimits.worstCaseContentChars(RecordLimits.MAX_SAFE_BYTES_PER_FIELD + 1) >
                RecordLimits.WIRE_MAX_LINE_CHARS - RecordLimits.NON_CONTENT_RESERVE_CHARS,
        )
    }

    /** 其余有界文本列(sql/绑定参数/查询结果)与内容视图合计仍须在行上限内。 */
    @org.junit.Test
    fun boundedTextColumnsFitTogetherWithContent() {
        val total = RecordLimits.worstCaseContentChars(RecordLimits.MAX_CONTENT_MATERIALIZE_BYTES) +
            RecordLimits.SQL_MAX_CHARS.toLong() +
            RecordLimits.BIND_ARGS_MAX_CHARS.toLong() +
            RecordLimits.DEFAULT_MAX_RESULT_CHARS.toLong()
        assertTrue(
            "有界列合计 + 内容视图必须 ≤ 行上限:$total vs ${RecordLimits.WIRE_MAX_LINE_CHARS}",
            total <= RecordLimits.WIRE_MAX_LINE_CHARS,
        )
    }
}
