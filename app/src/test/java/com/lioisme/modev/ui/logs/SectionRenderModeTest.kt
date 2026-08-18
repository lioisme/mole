package com.lioisme.modev.ui.logs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 双层渲染档位判定契约(2026-08-18 V15):
 * ≤ [SELECTABLE_CAP_CHARS] 字符 → 整段单 SelectionContainer(Tier A);
 * 超过 → 分块虚拟化(Tier B,防巨型单 Text 布局/OOM)。
 */
class SectionRenderModeTest {

    @Test
    fun at_or_below_cap_is_single_selectable() {
        assertEquals(SectionRenderMode.SINGLE_SELECTABLE, sectionRenderMode(0))
        assertEquals(SectionRenderMode.SINGLE_SELECTABLE, sectionRenderMode(SELECTABLE_CAP_CHARS))
    }

    @Test
    fun above_cap_is_chunked() {
        assertEquals(SectionRenderMode.CHUNKED, sectionRenderMode(SELECTABLE_CAP_CHARS + 1))
        assertEquals(SectionRenderMode.CHUNKED, sectionRenderMode(Int.MAX_VALUE))
    }

    @Test
    fun short_content_within_4096_is_single_selectable() {
        assertEquals(SectionRenderMode.SINGLE_SELECTABLE, sectionRenderMode(4096))
    }
}