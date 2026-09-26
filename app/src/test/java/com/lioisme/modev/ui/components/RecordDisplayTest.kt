package com.lioisme.modev.ui.components

import android.content.Context
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 算法展示名单一事实源契约(V16 R1 空白治理):
 * algorithm 为 null/空串/纯空白 → 回退类别名(恒有,杜绝列表行/详情标题/摘要空白);
 * 正常值原样透传。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordDisplayTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun record(algorithm: String): CryptoRecord = CryptoRecord(
        packageName = "com.example.app",
        processName = "com.example.app",
        threadName = "t",
        kind = CryptoKind.SIGNATURE,
        operation = CryptoOperation.SIGN,
        algorithm = algorithm,
        provider = null,
        direction = 1,
        iv = null,
        key = null,
        input = BytesView(hex = "deadbeef", text = null, totalLength = 4, truncated = false),
        output = null,
        timestamp = 1_700_000_000_000,
    )

    @Test
    fun blankAlgorithm_fallsBackToKindLabel() {
        val label = context.getString(R.string.kind_signature)
        assertEquals(label, record(algorithm = "").displayAlgorithm(context))
        assertEquals(label, record(algorithm = "   ").displayAlgorithm(context))
    }

    @Test
    fun realAlgorithm_passesThrough() {
        assertEquals("SHA256withRSA", record(algorithm = "SHA256withRSA").displayAlgorithm(context))
    }
}
