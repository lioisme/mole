package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.RecordLimits
import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SqlResultRow
import com.lioisme.modev.core.model.kind
import com.lioisme.modev.core.model.SqlValueType
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import com.lioisme.modev.hook.ProcessIdentity
import java.lang.reflect.Method

/**
 * 数据库 Hooker 基类(ADR-3 镜像,T6):
 * 统一开关判断(db 类别)、记录组装与堆栈采集;所有子类拦截逻辑均在
 * [com.lioisme.modev.hook.ProbeContext.engine] 的 PROTECTIVE 模式下执行,
 * 异常不影响宿主。
 */
abstract class SqlBaseHooker(
    context: ProbeContext,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val sink: RecordSink = context.sink
    protected val packageName = context.packageName

    /**
     * 目标进程名(V44 T2.6):经 [com.lioisme.modev.hook.ProcessIdentity] 单例读取——
     * 旧形态每个 Hooker 基类各自 `by lazy` 读一次 `/proc/self/cmdline`,冷启动期
     * 约 50 个实例 = 50 次文件读 + 字节解码,而进程身份在进程内恒定不变。
     */
    protected val processName: String get() = ProcessIdentity.processName

    abstract fun install()

    /**
     * 全量捕获策略下的双态视图:上限闸 [com.lioisme.modev.core.model.HookSwitch.maxBytesPerField]
     * (默认 1MB,0=无限)伸缩;注入侧不承担 UI 截断职责,仅在超保险丝时标记 truncated。
     */
    protected fun viewOf(bytes: ByteArray?): BytesView? {
        if (bytes == null) return null
        val max = config().maxBytesPerField
        if (max <= 0) return BytesView.of(bytes)
        return BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
    }

    /**
     * 组装并发送一条数据库记录。开关关闭或命中调用栈 denylist 时直接跳过(零开销);
     * SQL/绑定参数超限在此截断(参数化执行路径的兜底,ADR-4)。
     */
    protected fun emit(
        operation: SqlOperation,
        databaseName: String,
        contextId: Long = 0L,
        provider: String? = null,
        dbPath: String? = null,
        sql: String? = null,
        bindArgs: List<SqlBindValue>? = null,
        rows: List<SqlResultRow>? = null,
        rowCount: Int? = null,
        affectedCount: Int? = null,
        rowId: Long? = null,
        resultText: String? = null,
        params: Map<String, String>? = null,
    ) {
        // 配置单次快照:开关判断与记录组装共用同一次读取(镜像 BaseHooker.emit)
        val switch = config()
        val kind = operation.kind
        if (!switch.dbEnabled(kind)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val sqlTruncated = sql != null && sql.length > RecordLimits.SQL_MAX_CHARS
        val record = SqlRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            databaseName = databaseName,
            provider = provider,
            dbPath = dbPath,
            sql = sql?.take(RecordLimits.SQL_MAX_CHARS),
            bindArgs = boundedBinds(bindArgs),
            rows = rows,
            rowCount = rowCount,
            affectedCount = affectedCount,
            rowId = rowId,
            resultText = resultText,
            params = if (sqlTruncated) params.orEmpty() + ("sqlTruncated" to "true") else params,
            processName = processName,
            contextId = contextId,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) {
                StackTraceFilter.capture(switch.maxStackDepth)
            } else {
                emptyList()
            },
        )
        sink.emit(record)
    }

    /** 绑定参数序列化字符预算截断(ADR-4:bindArgsJson 上限 64KB)。 */
    private fun boundedBinds(binds: List<SqlBindValue>?): List<SqlBindValue>? {
        if (binds == null || binds.isEmpty()) return binds
        var cost = 0
        val out = ArrayList<SqlBindValue>(binds.size)
        for (b in binds) {
            cost += (b.v?.length ?: 0) + (b.hex?.length ?: 0) + 16
            if (cost > RecordLimits.BIND_ARGS_MAX_CHARS) break
            out.add(b)
        }
        return out
    }

    /** 数据库名 = 路径 basename(兼容 / 与 \ 分隔符;Robolectric 宿主侧 Windows 路径)。 */
    protected fun dbNameOf(path: String?): String? =
        path?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }

    /** 单个绑定值序列化(blob 走双态视图;未知类型按 NULL)。 */
    protected fun valueToBind(i: Int, v: Any?): SqlBindValue? = when (v) {
        null -> SqlBindValue(i, SqlValueType.N)
        is String -> SqlBindValue(i, SqlValueType.S, v = v)
        is Long -> SqlBindValue(i, SqlValueType.L, v = v.toString())
        is Int -> SqlBindValue(i, SqlValueType.L, v = v.toString())
        is Double -> SqlBindValue(i, SqlValueType.D, v = v.toString())
        is Float -> SqlBindValue(i, SqlValueType.D, v = v.toString())
        is Boolean -> SqlBindValue(i, SqlValueType.L, v = if (v) "1" else "0")
        is ByteArray -> {
            val view = viewOf(v)
            SqlBindValue(i, SqlValueType.B, hex = view?.hex, len = view?.totalLength, truncated = view?.truncated ?: false)
        }
        else -> SqlBindValue(i, SqlValueType.N)
    }

    /** 按方法名注册 after hook(engine 的 PROTECTIVE 语义由框架保证)。 */
    protected fun hookAfter(methods: List<Method>, after: (Invocation, Any?) -> Unit) {
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    after(invocation, invocation.result)
                }
            })
        }
    }

    /** 按方法名注册 before hook。 */
    protected fun hookBefore(methods: List<Method>, before: (Invocation, Any?) -> Unit) {
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    before(invocation, null)
                }
            })
        }
    }

    /** 静态入口注册(语义与实例入口一致;engine 自行处理静态分发)。 */
    protected fun hookStaticAfter(methods: List<Method>, after: (Invocation, Any?) -> Unit) {
        hookAfter(methods, after)
    }
}
