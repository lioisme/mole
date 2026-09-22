package com.lioisme.modev.domain.diagnostics

import com.lioisme.modev.domain.server.ChannelStats

/**
 * 宿主管道统计:收到/丢弃/写失败/裁剪 + 通道拒绝计数,供设置页展示
 * (V43 T7/V5.3:自 data.repo 上移 domain——诊断观测契约不再锚定数据层类型)。
 *
 * [bytesDropped] 与 [dropped] 分型(V45 T1.6 / ST-5):两条独立闸门各计一次,
 * 因为成因与处置完全不同 ——
 *  [dropped] = 条数闸门(32 条批 + 200ms 窗口消费不过来,通道 8192 槽满);
 *  [bytesDropped] = **字节闸门**(在途载荷总量超 `PENDING_MAX_BYTES`,
 *    旧形态只有条数上限,多 MB/条记录最坏可驻留 GB 级 → 宿主 GC 风暴/OOM)。
 * 只报合计数会把「大字段洪峰」误读成「消费太慢」,进而错下容量调参结论。
 */
data class PipelineStats(
    val received: Long = 0L,
    val dropped: Long = 0L,
    val bytesDropped: Long = 0L,
    val writeFailures: Long = 0L,
    val trimmed: Long = 0L,
    val channel: ChannelStats = ChannelStats(),
)
