package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * /proc 文件扫描单一广播协调器(V44 T2.1,审计 P1 收编):
 *
 * 旧形态五件套(XposedMaps/ProcStatus/ProcNet/ProcMountInfo/ProcCgroup)+ DetectionMonitor
 * **各自独立注册** `FileReader(String)`、`BufferedReader(Reader)`、`BufferedReader.readLine`
 * (readLine ×6 注册、构造器 ×5 注册),且构造器回调**零门控**——全进程每一次
 * `new FileReader(path)` 都付 `File(path).canonicalPath`(native IO)+ 全局监视器锁,
 * **设备环境开关全关(默认)也照付**;LSPosed 下每行 readLine 6 倍回调分发;
 * Vector 2.2「同一方法仅首注册生效」则后 5 个注册被静默遮蔽 = proc 过滤整体失效。
 *
 * 收编模型(镜像 [SharedMethodBroadcast]/[LoadClassWatcher] 单一监视器先例):
 * - 三方法全进程各注册**一次**(经 [SharedMethodBroadcast.subscribe]);
 * - 门控先行:三回调首条语句 = `config.deviceEnvListen`(单 volatile)∧ 订阅表非空,
 *   关闭态宿主 IO 热路径**零成本**(仅一次 volatile 读 + 一次 isEmpty);
 * - 廉价路径判定:仅 `startsWith("/proc/")` 直配;间接形态(含 "/proc/" 但非绝对)才
 *   付一次 canonicalPath;其余路径直接放过(旧实现每次构造都 canonicalPath);
 * - reader 实例 → 目标文件映射 [ConcurrentHashMap](**identity** 语义:BufferedReader
 *   未覆写 equals,键即实例;读多写零、无锁),EOF 即移除;容量硬上限防泄漏;
 * - 行事件按 [ProcPath] 分发给已登记 [Handler];各 Handler 自判自身 kind 门控
 *   (干预 featureOn(kind)/监控 DETECT_* kind),协调器只承载 master 粗门。
 *
 * 登记序:由 [com.lioisme.modev.hook.probes.DeviceEnvProbe] 先 `install` 本协调器,
 * 再装各 proc Hooker(其 install 仅 addHandler)。
 */
class ProcScanBroadcaster(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    /** 被跟踪的 proc 伪文件类别(唯一谓词事实源:各 Handler 声明关注集)。 */
    enum class ProcPath { MAPS, STATUS, MOUNT_INFO, CGROUP, NET }

    /** proc 行事件处理器(proc Hooker 实现;经 [addHandler] 登记)。 */
    interface Handler {
        /** 该处理器对应能力 kind(记录/门控用)。 */
        val kind: DeviceEnvKind

        /** 关注的 proc 文件集合。 */
        val paths: Set<ProcPath>

        /**
         * readLine 行事件(协调器已保证 master 开 + 路径匹配 + 行非空)。
         * 实现自行判定自身 kind 门控;可 setResult 改写/吞行(干预型)。
         */
        fun onLine(invocation: Invocation, path: ProcPath, line: String)
    }

    private val handlers = CopyOnWriteArrayList<Handler>()

    /** FileReader 实例指纹(identityHashCode)→ 目标 proc 文件(构造器标记,BR 构造器消费)。 */
    private val fileReaderKinds = ConcurrentHashMap<Int, ProcPath>()

    /** BufferedReader 实例 → 目标 proc 文件(读多写零,identity 键无锁读;EOF 移除)。 */
    private val readerKinds = ConcurrentHashMap<BufferedReader, ProcPath>()

    fun addHandler(handler: Handler) {
        handlers += handler
    }

    override fun install() {
        // FileReader(String) 构造器:master 关 → 首行零成本放过。
        broadcast.subscribe(
            runCatching { FileReader::class.java.getConstructor(String::class.java) }.getOrNull(),
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!config().deviceEnvListen) return
                    if (handlers.isEmpty()) return
                    if (fileReaderKinds.size >= MAX_TRACKED) return
                    val path = invocation.getArg(0) as? String ?: return
                    val kind = classify(path) ?: return
                    fileReaderKinds[System.identityHashCode(invocation.thisObject)] = kind
                }
            },
        )
        // BufferedReader(Reader) 构造器:识别被跟踪的 FileReader 并登记 BR 实例。
        broadcast.subscribe(
            runCatching { BufferedReader::class.java.getConstructor(java.io.Reader::class.java) }.getOrNull(),
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!config().deviceEnvListen) return
                    if (handlers.isEmpty()) return
                    val inner = invocation.getArg(0) as? FileReader ?: return
                    val kind = fileReaderKinds[System.identityHashCode(inner)] ?: return
                    val reader = invocation.thisObject as? BufferedReader ?: return
                    if (readerKinds.size >= MAX_TRACKED) return
                    readerKinds[reader] = kind
                }
            },
        )
        // BufferedReader.readLine:master 粗门 → 路径查表 → 分发处理器。
        broadcast.subscribe(
            runCatching { BufferedReader::class.java.getMethod("readLine") }.getOrNull(),
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!config().deviceEnvListen) return
                    if (handlers.isEmpty()) return
                    val reader = invocation.thisObject as? BufferedReader ?: return
                    val path = readerKinds[reader] ?: return
                    val line = result as? String
                    if (line == null) {
                        // EOF/关闭:释放跟踪条目(泄漏上界=并发存活 reader 数)
                        readerKinds.remove(reader)
                        return
                    }
                    for (h in handlers) {
                        if (path in h.paths) {
                            runCatching { h.onLine(invocation, path, line) }
                        }
                    }
                }
            },
        )
    }

    /**
     * 廉价 proc 路径判定(V44 T2.1③):绝对 `/proc/` 前缀直配后缀类别(检测框架恒用
     * 绝对路径,含 /proc/self/... 与 /proc/<pid>/...);非常规形态仅在含 "/proc/"
     * 时付一次 canonicalPath(symlink/相对路径,罕见);其余零成本放过。
     */
    private fun classify(path: String): ProcPath? {
        if (path.startsWith("/proc/")) {
            return matchProc(path)
        }
        if (!path.contains("/proc/")) return null
        // 间接形态(symlink/相对路径,罕见):一次性 canonical 解析
        val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
        return if (canonical.startsWith("/proc/")) matchProc(canonical) else null
    }

    private fun matchProc(p: String): ProcPath? = when {
        p.endsWith("/maps") -> ProcPath.MAPS
        p.endsWith("/status") -> ProcPath.STATUS
        p.endsWith("/mountinfo") -> ProcPath.MOUNT_INFO
        p.endsWith("/cgroup") -> ProcPath.CGROUP
        p.endsWith("/net/tcp") || p.endsWith("/net/tcp6") ||
            p.endsWith("/net/udp") || p.endsWith("/net/udp6") -> ProcPath.NET

        else -> null
    }

    companion object {
        /** 跟踪表硬上限(防极端泄漏;与旧实现 MAX_TRACKED=64 同量级,双表取 128 宽上界)。 */
        private const val MAX_TRACKED = 128
    }
}
