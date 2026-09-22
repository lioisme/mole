package com.lioisme.modev.domain.server

/**
 * 通道统计:拒绝/丢弃计数,供宿主设置页展示(V43 T7/V5.3:自 data.server 上移
 * domain——观测契约归领域层,传输实现细节不外渗;ConfigServer 为唯一写入方)。
 */
data class ChannelStats(
    val rejectedHellos: Long = 0L,
    val droppedRecords: Long = 0L,
    val rejectedRecords: Long = 0L,

    /** 未携带令牌但被放行的握手数(LSPosed legacy / Vector 注入路径)。 */
    val trustedNoToken: Long = 0L,
)
