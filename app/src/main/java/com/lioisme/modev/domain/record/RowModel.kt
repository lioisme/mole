package com.lioisme.modev.domain.record

import androidx.compose.runtime.Immutable
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation

/**
 * 列表行轻量模型(ADR-10,数据库监听 T9):
 * 数据库投影行([CryptoRecordRow])按 [recordType] 分派为本密封族,
 * 算法行复用轻量 [CryptoRecord](投影预览),数据库行承载 SQL 摘要等投影列,
 * 行为行(V27)承载路径/命令等投影列,
 * 列表/时间线/分页/邻接场景零大字段物化。
 */
sealed interface RowModel {
    val id: Long
    val timestamp: Long

    /** 记录来源包名(V19 全局监听列表行内包名语境)。 */
    val packageName: String
}

/** 算法记录行(投影预览,字段语义同列表页既有 [CryptoRecord] 轻量复用)。 */
@Immutable
data class CryptoRow(
    val record: CryptoRecord,
) : RowModel {
    override val id: Long get() = record.id
    override val timestamp: Long get() = record.timestamp
    override val packageName: String get() = record.packageName
}

/** 数据库记录行(SQL 摘要预览 + 结果元数据;零大字段物化)。 */
@Immutable
data class SqlRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: SqlKind,
    val operation: SqlOperation,
    val databaseName: String,
    val sqlPreview: String?,
    val rowCount: Int?,
    val affectedCount: Int?,
    val rowId: Long?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp
}

/**
 * 行为记录行(V27;路径/命令摘要预览,零内容物化——content 仅存实体列,
 * 详情页按需全量读取)。
 */
@Immutable
data class BehaviorRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: BehaviorKind,
    val operation: BehaviorOperation,
    /** 主体路径(asset 名);shell 为 null。 */
    val path: String?,
    /** 重命名/移动目标路径(RENAME)。 */
    val destPath: String?,
    /** Shell argv(EXEC)。 */
    val command: List<String>?,
    val fileSize: Long?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp

    /** 列表 headline 单一事实源:文件 basename / 命令首段(shell);恒非空。 */
    fun headlineText(): String? = when (kind) {
        BehaviorKind.SHELL_EXEC ->
            command?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }
        else ->
            path?.trimEnd('/')?.ifEmpty { "/" }?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
    }
}

/**
 * 偏好记录行(V28;键/文件摘要预览,零值域物化——value 仅存实体 content 列,
 * 详情页按需全量读取)。
 */
@Immutable
data class PrefsRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: PrefsKind,
    val operation: PrefsOperation,
    /** 配置键(get/put/remove 系列;getAll/clear 为 null);getAll/clear 为 null)。 */
    val prefKey: String?,
    /** 配置文件路径(OPEN 恒有;其余经实例映射尽力回填)。 */
    val path: String?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp

    /** 列表 headline 单一事实源:配置键 / 文件 basename;恒非空。 */
    fun headlineText(): String? =
        prefKey?.takeIf { it.isNotBlank() }
            ?: path?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
}

/**
 * WebView 记录行(V29 一等公民;URL/域名/桥名摘要预览,零脚本物化——script 仅存实体列,
 * 详情页按需全量读取)。
 */
@Immutable
data class WebViewRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: WebViewKind,
    val operation: WebViewOperation,
    /** 目标 URL(PAGE_LOAD/EVALUATE_JS)。 */
    val url: String?,
    /** URL 域名(列表预览用)。 */
    val urlHost: String?,
    /** JS 桥名称(ADD_JS_INTERFACE)。 */
    val bridgeName: String?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp

    /** 列表 headline 单一事实源:域名 / 操作名;恒非空。 */
    fun headlineText(): String? =
        urlHost?.takeIf { it.isNotBlank() }
            ?: bridgeName?.takeIf { it.isNotBlank() }
}

/**
 * 网络环境监听记录行(V30 一等公民;URL/域名/方法摘要预览,零内容/堆栈物化)。
 */
@Immutable
data class NetworkRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: NetworkKind,
    val operation: NetworkOperation,
    /** 请求 URL(REQUEST 类别)。 */
    val url: String?,
    /** URL 域名(列表预览用)。 */
    val urlHost: String?,
    /** HTTP 方法(GET/POST 等)。 */
    val method: String?,
    /** HTTP 状态码(响应)。 */
    val statusCode: Int?,
    /** 代理主机(PROXY 类别)。 */
    val proxyHost: String?,
    /** VPN 是否活跃(VPN 类别)。 */
    val vpnActive: Boolean?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp

    /** 列表 headline 单一事实源:域名 / 方法+URL / 操作名;恒非空。 */
    fun headlineText(): String? =
        urlHost?.takeIf { it.isNotBlank() }
            ?: method?.takeIf { it.isNotBlank() }?.let { m ->
                url?.takeIf { u -> u.isNotBlank() }?.let { "$m $it" }
            }
            ?: operation.name
}

/**
 * 设备环境监听记录行(V31 一等公民;目标路径/类名/属性名摘要预览,零堆栈物化)。
 */
@Immutable
data class DeviceEnvRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: DeviceEnvKind,
    val operation: DeviceEnvOperation,
    /** 被检测的文件路径(可空)。 */
    val targetPath: String?,
    /** 被检测的类名(可空)。 */
    val targetClass: String?,
    /** 被检测的属性名(可空)。 */
    val targetProperty: String?,
    /** 是否为干预记录(true=隐藏,false=仅监控;V34+ 由 interventionAction 替代)。 */
    val intervention: Boolean,
    /** 干预动作标记("BLOCKED"/"DETECTED"/null;V34 审计增强)。 */
    val interventionAction: String? = null,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp

    /** 列表 headline 单一事实源:目标路径 / 类名 / 属性名 / 操作名;恒非空。 */
    fun headlineText(): String? =
        targetPath?.takeIf { it.isNotBlank() }
            ?: targetClass?.takeIf { it.isNotBlank() }
            ?: targetProperty?.takeIf { it.isNotBlank() }
            ?: operation.name
}

/**
 * UI 交互监听记录行(V33 一等公民;弹窗标题/Activity 名摘要预览,零堆栈物化)。
 */
@Immutable
data class UiInteractionRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: UiInteractionKind,
    val operation: UiInteractionOperation,
    /** 弹窗标题(DIALOG_*)。 */
    val dialogTitle: String?,
    /** 弹窗消息(DIALOG_*)。 */
    val dialogMessage: String?,
    /** 控件文本(WIDGET_*)。 */
    val widgetText: String?,
    /** Activity 名(ACTIVITY_*)。 */
    val activityName: String?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp

    /** 列表 headline 单一事实源:弹窗标题 / Activity 名 / 操作名;恒非空。 */
    fun headlineText(): String? =
        dialogTitle?.takeIf { it.isNotBlank() }
            ?: activityName?.takeIf { it.isNotBlank() }
                ?.substringAfterLast('.')
            ?: widgetText?.takeIf { it.isNotBlank() }?.take(40)
            ?: operation.name
}

/**
 * 系统信息监听记录行(V33 一等公民;Logcat tag/签名哈希摘要预览,零堆栈物化)。
 */
@Immutable
data class SystemRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: SystemKind,
    val operation: SystemOperation,
    /** Logcat tag(LOGCAT)。 */
    val logcatTag: String?,
    /** Logcat level(LOGCAT)。 */
    val logcatLevel: String?,
    /** Logcat message 预览(LOGCAT)。 */
    val logcatMessage: String?,
    /** 签名哈希(SIGNATURE)。 */
    val signatureHash: String?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp

    /** 列表 headline 单一事实源:Logcat tag / 签名哈希 / 操作名;恒非空。 */
    fun headlineText(): String? =
        logcatTag?.takeIf { it.isNotBlank() }?.let { "[$it]" }
            ?: signatureHash?.takeIf { it.isNotBlank() }?.take(16)?.let { "$it…" }
            ?: operation.name
}

/**
 * 进程控制记录行(V33 一等公民;退出码/异常类型摘要预览,零堆栈物化)。
 */
@Immutable
data class ProcessRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: ProcessKind,
    val operation: ProcessOperation,
    /** System.exit 参数(EXIT_INTERCEPT)。 */
    val exitCode: Int?,
    /** killProcess pid(EXIT_INTERCEPT)。 */
    val processPid: Int?,
    /** 异常类名(CRASH_PREVENT)。 */
    val crashType: String?,
    /** 崩溃拦截标记(CRASH_PREVENT;V34+ 由 interventionAction 替代)。 */
    val crashBlocked: Int?,
    /** 录屏拦截标记(SCREEN_RECORD;V34+ 由 interventionAction 替代)。 */
    val screenRecordBlocked: Int?,
    /** 干预动作标记("BLOCKED"/"DETECTED"/null;V34 审计增强)。 */
    val interventionAction: String? = null,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp
    // V43 T4:进程行 headline 由 UI 侧以资源本地化承担(操作名 + 拆分参数行);
    // 旧 headlineText() 硬编码英文("exit=…"/"detected")随 V3.3 违规一并清除。
}
