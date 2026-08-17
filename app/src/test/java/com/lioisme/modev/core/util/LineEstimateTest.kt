package com.lioisme.modev.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 行数阈值折叠估算契约(2026-08-17 V11):判定边界、预览截断、下限兜底。 */
class LineEstimateTest {

    @Test
    fun chars_per_line_uses_monospace_advance() {
        // 328dp 容器 / 12sp mono:328 / (12 × 0.6) ≈ 45
        assertEquals(45, LineEstimate.charsPerLine(328f, 12f, monospace = true))
        // 常规文本系数 0.5:328 / (14 × 0.5) ≈ 46
        assertEquals(46, LineEstimate.charsPerLine(328f, 14f, monospace = false))
    }

    @Test
    fun chars_per_line_floors_to_min() {
        // 极窄容器兜底,避免阈值小到产生空预览
        assertEquals(LineEstimate.MIN_CHARS_PER_LINE, LineEstimate.charsPerLine(20f, 20f, monospace = true))
    }

    @Test
    fun needs_fold_boundary_is_exclusive() {
        // 恰等于阈值行数 → 不折叠(全量展示)
        assertFalse(LineEstimate.needsFold(45 * 8, charsPerLine = 45))
        // 超 1 字符 → 折叠
        assertTrue(LineEstimate.needsFold(45 * 8 + 1, charsPerLine = 45))
        // 空/小数据 → 不折叠
        assertFalse(LineEstimate.needsFold(0, charsPerLine = 45))
        assertFalse(LineEstimate.needsFold(16, charsPerLine = 45))
    }

    @Test
    fun collapsed_preview_chars_never_exceeds_full() {
        assertEquals(45 * 8, LineEstimate.collapsedPreviewChars(1_000, charsPerLine = 45))
        assertEquals(100, LineEstimate.collapsedPreviewChars(100, charsPerLine = 45))
        assertEquals(0, LineEstimate.collapsedPreviewChars(0, charsPerLine = 45))
    }
}
