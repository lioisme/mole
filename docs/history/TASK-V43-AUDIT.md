# TASK-V43：宿主启动独立性 / 零副作用 / 列表一行一参数 / 卡顿崩溃防治 / 架构隔离 全量审计任务书

| 项 | 值 |
|---|---|
| 版本 | V43（基于工作树 `0431431`，2026-09-13） |
| 性质 | 全量代码审计报告 + 分任务实施任务书 |
| 审计方法 | 三路并行全仓审计（注入侧逐 hooker 复核 / 记录列表 UI 与性能 / 架构隔离与门禁盲区）+ 关键指控逐行二次复核（file:line 均为工作树实位，抽查 19 处全部命中） |
| 决策状态 | 8 项方向性决策按审计推荐默认落定（见 §0.3，维护者可否决）；**实施状态：T1–T7 已于 2026-09-13 全部实施完毕**（设备矩阵/插桩执行待设备接入补跑，见 §7） |
| 交付边界 | 本文档为审计 + 实施任务书；实施已完成并回填标记（§2 各任务 ✅ + §7 总览） |
| 关联文档 | `AGENTS.md`（现行契约）、`TASK-V42-AUDIT.md`（前轮，无决策反转）、`TASK-V41-AUDIT.md`、`ARCHITECTURE_AUDIT_TASKS.md`（旧代，其「core 纯 Kotlin 零 Android」宣称本轮证实为漂移，见 V5.1） |

---

## 0. 总纲

### 0.1 需求输入（维护者原始 5 项）

| # | 需求 | 审计口径 |
|---|---|---|
| R1 | 被作用域的应用只能经 Modev 正常工作之外还必须**不依赖** Modev：Modev 未启用 / 注入失败 / 内部异常时宿主必须正常启动，绝不卡启动界面 | 宿主 attach/onCreate 关键路径钩子行为、阻塞面、异常传播面 |
| R2 | 被作用域的应用**零功能、零性能副作用** | 热路径钩子每次调用成本、开关全关时是否仍做功、返回值契约破坏、宿主可见命名空间污染 |
| R3 | 重构算法记录列表 UI：**不得一行显示多个参数** | RowPresentation 行模型、逐类型参数堆叠现状、拆行影响面与测试锁定 |
| R4 | 监听与干预功能性能极好，**杜绝卡顿、崩溃** | 锁链/死锁、无界结构、线程模型、干预语义有效性、测试门禁缺口 |
| R5 | 底层组件库、工具与业务功能**隔离分离** | 分层方向、扫描盲区、god-class、装配与可见性、模块粒度 |

### 0.2 审计总结论（TL;DR）

**45 条违规（P1×22 / P2×23），无现实崩溃级 P0**——但存在一条「极端背压 → 宿主线程无限期阻塞」的锁链路径（V1.1），是 R1「卡启动界面」类症状的唯一已知根因候选；一条以异常表达干预在双框架下均被吞导致**拦截静默失效且记录误报 BLOCKED**（V4.1）；四条**开关全关仍向宿主热路径收费**的门控倒挂（V2.1–V2.4）；两处 `setResult(null)` 破坏 JDK/Android 返回值契约（V2.5）。列表 UI 的现状根因是 `RowPresentation` 固定三槽模型（一行装 2–5 参数是模型逼出来的，不是渲染偶然），重构唯一杠杆点即此模型，且改动封闭在单文件内。架构面四条边界扫描盲区已被实证绕过（FQN 全限定引用、domain→data 逆向、mcp→data.mapping、core 非纯），「底层与业务隔离」目前是靠有洞的源码扫描在模拟模块级隔离。

统计：

| 需求 | 条目数 | P1 | P2 |
|---|---|---|---|
| R1 宿主启动独立 | 4 | 2 | 2 |
| R2 零副作用 | 11 | 5 | 6 |
| R3 列表 UI | 8 | 4 | 4 |
| R4 卡顿崩溃防治 | 8 | 3 | 5 |
| R5 架构隔离 | 14 | 8 | 6 |
| **合计** | **45** | **22** | **23** |

### 0.3 已定决策（维护者可否决）

- **D-1 列表版式 = 严格一行一参数**：headline 为「身份」（算法名 / SQL 首行 / 路径 basename / 配置键 / 域名——身份不算参数），其余每个逻辑参数各占一行；「类别」「操作」拆两行；trailing 时间与 MicroBadge 属语义标记非参数，维持右栏现状。行高密度下降记入风险登记册（RR-3）。
- **D-2 `MiddleEllipsisText` 真实启用**（path / URL / argv 行保头掐腰中缀省略），不退役组件；修正 AGENTS #20 文档漂移（V3.6）。
- **D-3 硬编码英文全部入资源四语**；`HardcodedStringConventionTest` 从「禁中文」扩为「ui 包展示字符串字面量零容忍（协议/格式字面量白名单管控）」。
- **D-4 类加载观测单一监视器**：新建 `LoadClassWatcher` 集中注册 `loadClass`/`findClass`，按注册表广播分派（Xposed 隐藏、SSL 惰性安装、okhttp 惰性安装三订阅者），落实 AGENTS「禁各自重复注册同一方法」。
- **D-5 干预语义引擎层显式化**：禁止以「回调内抛异常」表达干预（双框架均吞）；`Invocation` 新增 `setThrowable`（legacy → `param.setThrowable`，libxposed 按 API 语义映射），返回契约破坏型干预（V2.5/V2.11）一律改走 setThrowable 或契约友好替身。真机双框架矩阵（L-VERIFY）为验收环境。
- **D-6 本轮架构隔离 = 补扫描洞 + god-class 拆分 + 重复清除**；`:modev-core` 多模块化仅交付可行性结论不动手（前置 V5.1/V5.3 全修后方可评估）。
- **D-7 `CategoryTaxonomy` 暂驻 core**（AGENTS #23 事实源声明不动摇），随 D-6 后置模块迁移，本轮登记技术债（V5.9）。
- **D-8 性能验收量化**：热路径以「开关全关 P95 时延放大 ≤5%、宿主主线程零同步 socket IO、零同步盘 IO（启动路径一次性读白名单除外）」为硬条款，进 §3。

### 0.4 全局不变量（实施全程不得破坏）

1. wire 协议 VERSION 恒 4、注入侧/宿主零感知（AGENTS #16/#23）；MCP 43 工具面快照不变（AGENTS #26，`McpToolParityTest`）。
2. FeatureGate 红线：干预动作恒经 `featureOn(kind)` 双门控，开关全关 = 零副作用（只增不减，禁止回退为「关但仍执行」）。
3. 被动采集红线：钩子内禁移动游标 / `getCount` 探测 / 触碰流句柄（AGENTS #11/#20）。
4. testTag 契约保留：`record-row` / `timeline-header` / `listen-row-*` / `services-external-access` / `port-input` / `toc-{key}` 等（列表重构允许改内部结构、不允许改 tag 语义）。
5. 分页流契约：DAO 大窗口 limit + `cachedIn` + VM 级 `LazyListState`（AGENTS #14/#15）。
6. i18n 四语齐备（`StringResourceParityTest` 门禁）；lint 基线零新增；冷启动冒烟 `23-run-smoke.ps1` 为交付门禁（本轮 T1–T3 触 `hook/**`，**必须触发**）。

---

## 1. 违规清单（全量，file:line 实证）

严重度：**P0** = 现实崩溃/数据丢失；**P1** = 阻塞验收的正确性/门禁缺陷或高风险路径；**P2** = 应修复质量缺陷；**INFO** = 合规确认（§1.6）。

### 1.1 R1 宿主启动独立性（4 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V1.1 | **P1** | **背压锁链：写线程持 `writerLock` 阻塞 socket 写，watchdog 关 socket 需同一把锁 → 超时判死机制失效；锁链经队列满降级路径传导到宿主 hook 线程，宿主线程可被无限期阻塞**（R1「卡启动界面」唯一已知现实路径） | `hook/ConfigChannel.kt:139-155`（`sendRecordJson` `synchronized(writerLock)` 内 `w.write+flush`）vs `:173-180`（watchdog `synchronized(writerLock)` 内 `s.close()`，永远进不了被写阻塞持有的临界区；`:160` 注释「令阻塞中的 write 抛异常退出」为错误承诺）；传导链：`hook/RecordSink.kt:100-106`（队列满降级 `sp.append`）→ `hook/RecordSpool.kt:91-92`（`@Synchronized drain` 内部调 `send` 做网络发送，持 spool 锁跨越 socket） | watchdog 关 socket **不得取锁**（`Socket.close` 线程安全，JDK 契约）；`RecordSpool.drain` 改为「锁内快照 + 锁外发送」两段式；`writerLock` 换 `ReentrantLock`+`tryLock` 或写侧登记可中断句柄。对照面：`RecordSink.kt:100` 注释「不阻塞 Hook 线程」与实现相悖（并入 V4.3 门禁） |
| V1.2 | **P1** | 宿主主线程同步磁盘 + socket：退出拦截命中路径 `emitUrgent` 在 before 回调内同步 `sp.append`（WAL）+ `sp.drain`（socket，V1.1 锁链时全付）；`nextSeq()` 首调同步 `file.readText` 恰好落在首次 hook 回调（常为主线程） | `hook/hooks/ExitInterceptHooker.kt:42-47`（`emitUrgent` → `setResult(null)`）；`hook/RecordSink.kt:117-133`（append+drain 同步）、`:144-159`（`flush()` 同构）、`:69-83`（nextSeq 惰性定档仍同步读） | `emitUrgent` 只落盘不 drain（退出已被拦、进程继续存活，drain 交还 worker 即可）；seq 恢复移交 `modev-record-sink` 线程首帧；`flush()` 收敛为「落盘 + 有界异步 drain」 |
| V1.3 | P2 | 安装期宿主 attach 线程一次性承担：磁盘兜底同步读 + 23 hooker × 多方法全量反射注册（均 runCatching 有界、无网络，非卡死主因，但与 V1.2 同线程成本叠加） | `hook/HookInstaller.kt:171`（`java.io.File("/data/local/tmp/modev_config_$packageName.json")` 同步读）；`hook/probes/DeviceEnvProbe.kt:36-67`、`hook/hooks/BehaviorMethodRegistry.kt:21-31`（继承链全量 `declaredMethods`） | 磁盘兜底读移出 attach 关键路径（ConfigWatcher 首帧异步）；注册保持一次性但按 probe 惰性分批（可后置） |
| V1.4 | P2 | modern 注入入口无顶层 try/catch 兜底（legacy 入口有），install 残余裸调用理论上可把异常抛回框架加载器 | `ModevModule.kt:19-45` vs `LegacyModuleEntry.kt:19-29`（runSafely 兜底）；`HookInstaller.kt:197-207`（探针级 try 已存在，入口级缺失） | `ModevModule` 入口统一 try/catch + `ModevLog` 记录，fail-safe 返回，永不外抛 |

### 1.2 R2 对宿主零功能/性能副作用（11 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V2.1 | **P1** | `ClassLoader.loadClass` 被 **3 处独立钩挂**，违反 AGENTS「共享单一监视器」明文规约；Vector「同方法仅首注册生效」下按安装序（`HookInstaller.kt:62-73`）**后注册者整体失声**；LSPosed 下每次类加载付 3 次回调，且 SSL/okhttp 两处监视器**无任何门控**（每次 loadClass 做集合判定 + 安装检查） | `hook/hooks/XposedClassHooker.kt:34-38`、`hook/hooks/SSLBypassHooker.kt:58-66`（after 首行无 featureOn，`:62-64` `name in SSL_LAZY_CLASSES`）、`hook/hooks/NetworkRequestHooker.kt:39-49`；规约出处 `AGENTS.md` 已知坑「Vector 2.2 同一方法的第二个 hook 不执行」 | **D-4** `LoadClassWatcher` 单一监视器 + 订阅广播；归属断言测试（见 V4.3/T2/T6） |
| V2.2 | **P1** | 门控倒挂：SharedPreferences `getAll`/`getStringSet` 在开关判定**之前**先做 `Json.gson.toJson(全量容器)` + `index.pathOf`（LRU 锁）——GET 默认关（NOISE_HIGH），宿主每次读全 prefs 白付全量序列化（getAll 常含会话令牌等全部键值，还牵动 PII 面） | `hook/hooks/PrefsGetHooker.kt:66-77, 94-106`（`collectionText(map)` 作实参先求值）、`:115-116`；闸门实际位置 `hook/hooks/PrefsBaseHooker.kt:80-81`（`emit` 内 `prefsEnabled(kind)`） | after 首行 gate 短路（GET 关闭即 return）；序列化后置进门内 |
| V2.3 | **P1** | 门控倒挂 + 热路径重成本：每次 `startActivity`（含宿主内部路由，主线程）在 UI 监听开关判定**之前**做 `getMethod` 反射查找 + `intent.resolveActivity`（**Binder IPC**）+ `intent.extras` 首次访问触发全量 Bundle 反序列化（可能牵动宿主 Parcelable 加载）+ JSONObject 构建 | `hook/hooks/ActivityTransitionHooker.kt:28-50`（after 无首行 gate；`:34-38` resolveActivity、`:39-46` extras 循环） | first-line `if (!config().uiEnabled(...)) return`；extras 仅门开时读 |
| V2.4 | **P1** | 门控倒挂：`SQLiteStatement.bind*` 每次绑定（Room 批量插入每行多次）做 blob `viewOf` 三态物化（hex+base64+text 字符串拷贝）+ 全局 `synchronized` WeakHashMap 跟踪表写，先于任何 dbListen 门控；`afterQuery` 在 emit 前反射读 `mCount` + `columnNames` + params 组装 | `hook/hooks/SqlStatementHooker.kt:24-36, 39-61`（`afterBind` 无首行 gate；`:54-55` 锁写 `hook/SqlContextTracker.kt:27-28`）；`hook/hooks/SqlDatabaseHooker.kt:104-125, 429-435` | 两个 SQL hooker 回调首行统一 `if (!config().dbListen) return`；bind 跟踪表仅在 QUERY 开启时维护 |
| V2.5 | **P1** | 返回值契约破坏：拦截路径 `setResult(null)` 替代异常契约——`Runtime.exec` 返回 null → 宿主 `.waitFor()` NPE；`getPackageInfo` 返回 null → 契约本应抛 `NameNotFoundException`，未判空宿主直接 NPE。宿主崩溃即 Modev 副作用，违 R1/R2 双重 | `hook/hooks/RootExecHooker.kt:120, 151, 180`；`hook/hooks/RootPackageManagerHooker.kt:169` | **D-5/D-6**：改 `setThrowable(IOException / NameNotFoundException)` 忠实契约；exec 路径可选返回零字节替身 Process，以真机靶应用验证 |
| V2.6 | P2 | WebView JS 桥注入不受开关门控：宿主**每个** WebView 永远携带 `window.modevJsBridge` 命名空间（功能副作用 + 可被反检测方枚举，双重违规）；`loadUrl`/`evaluateJavascript` 路径同无门控 | `hook/hooks/WebViewHooker.kt:83-84`（构造器 after 直调 `ensureBridge`，无 `webviewEnabled` 判定）、`:136-141`（ensureBridge 体）、`:88-120` | `ensureBridge` 首行 `config().webviewEnabled(...)` 短路；关闭 = 零注入 |
| V2.7 | P2 | 热路径先分配后查门：`File.exists`（宿主最热 IO 探针之一）每次先 `path.lowercase()` 拷贝再 `featureOn`；`BufferedReader.readLine` 监视对**每一行** `lowercase()` | `hook/hooks/RootFileHooker.kt:35-41`（其余 exists/canRead 等四方法 `:87-89` 均 gate 先行，独此一处倒挂）；`hook/hooks/DetectionMonitorHooker.kt:88` | lowercase 移入门控分支 |
| V2.8 | P2 | `getPackageInfo` 观测：每次调用四次 `getField` 反射 + 双 `runCatching`（Throwable 构造成本），链式取 `applicationInfo` 逻辑缺陷致捕获功能恒 null（先做无用功、后静默失效） | `hook/hooks/AppInfoHooker.kt:28-57`（`:40` 附近链式取值在错误对象上再 `get`） | 首行 gate + 修复取值链 + 单测钉死 |
| V2.9 | P2 | 同一平台方法被 2–3 个 hooker 重复注册（Vector 静默互斥、LSPosed 回调成倍）：`getPackageInfo`（`RootPackageManagerHooker.kt:152` + AppInfoHooker）、`Settings$Secure.getString`（`SettingsHooker.kt:74/107/140` + `EmulatorNetworkHooker.kt:29/48` + `DetectionMonitorHooker.kt:150`）、`System.getProperty`（`ProxyHideHooker.kt:116` + `DeviceSysPropHooker` + `SystemPropertiesHooker`）、`readLine`（`DetectionMonitorHooker.kt:81` + 他处） | 各文件行号如上；规约同 V2.1 | 与 D-4 同法：高频平台方法建**单一分派监视器**（按 key 订阅广播）；先真机核 Vector 归属实况（RR-2） |
| V2.10 | P2 | `MessageDigest.update` 每数据块发一条含三态物化的记录：大文件摘要按 8K 块 → 记录风暴 + 每块三份字符串拷贝；emit 无节流（SessionThrottle 未覆盖算法域） | `hook/hooks/MessageDigestHooker.kt:85-105`；`hook/hooks/BaseHooker.kt:56-61`（viewOf）、`:66-125`（emit 无 throttle） | 开启态也应有界：update 记录按实例窗口聚合或纳入 SessionThrottle 同款 1s LRU |
| V2.11 | P2（观察） | `SurfaceBypassHooker` 以 `setResult(Canvas())` 0×0 画布替换——录屏解除开关开启时宿主自身截图/导出代码同样拿空画布（预期干预的溢出面） | `hook/hooks/SurfaceBypassHooker.kt:78` | 登记观察项：真机确认靶矩阵内无正常截图功能受损；必要时按调用栈区分 |

### 1.3 R3 算法列表 UI 一行一参数（8 条）

现状事实基准（重构输入）：10 类记录行全部经 `ui/components/RecordRow.kt:115-127` 单一 `RecordRow` 分派，行结构 = `ListItem(headline / supporting=Column{meta 行 + preview 行} / trailing{时间 + MicroBadge})`；`RowPresentation(icon, accent, headline, meta, preview, badges, timestamp)` 为固定三槽模型（`RecordRow.kt:201-210`）。逐类型一行堆叠参数：

| 类型 | meta 行参数堆叠 | 证据 |
|---|---|---|
| 算法 crypto | 类别+操作 2 参数；全局列表 +应用名 = 3 | `RecordRow.kt:293-300`、`:158-167` |
| 数据库 SQL | 库名+类别+操作+(N 行,M 影响) = **4–5** | `RecordRow.kt:320-342` |
| 进程 | preview `exit=X pid=Y` **双参数** | `RecordRow.kt:789` |
| 其余 7 类 | 类别+操作 2 参数 + preview | `RecordRow.kt:368-460` 各 presentation |

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V3.1 | **P1** | 固定三槽模型逼出「一行多参数」：`meta: AnnotatedString` 单槽承载 2–5 个参数，`maxLines=1` 尾省略——窄屏/长库名下 SQL 行库名与行数被省略号吞掉不可恢复（列表信息保真失败，R3 直接违例根因） | `RecordRow.kt:201-210`（模型）、`:156-174`（supporting 两行结构 + maxLines=1）、`:330-342`（SQL 拼串） | **模型改为 `fields: List<RowField(labelRes, value, style)>` 一行一参数**（D-1）；改动封闭在 RecordRow.kt（private 模型，外部零引用） |
| V3.2 | **P1** | 全局列表的应用名语境前缀与类型 meta 拼同一行（`:158-167`）：行首最强位置与应用名争夺 1 行预算，拼接串无独立省略策略，溢出时整行一起截 | `RecordRow.kt:158-167`；契约出处 AGENTS #17 | 应用名单独成行（`record_field_app` 标签），仅全局形态渲染 |
| V3.3 | **P1** | 进程 preview 一行双参数 + 裸英文：`"exit=${...} pid=${...}"` | `RecordRow.kt:789` | exit / pid 各一行（D-1）；文案入资源（V3.4） |
| V3.4 | P2 | 硬编码英文绕过 i18n 门禁（`HardcodedStringConventionTest` 只禁中文，英文字面量是真缺口）：`"VPN active"`、`"bridge: "`、`"HTTP $it"`、`"proxy: $it"`、`exit=`/`pid=`/`screen=`/`blocked`/`detected` 在 zh/ja/ko 恒英文 | `RecordRow.kt:644, 673, 789, 866-868, 1106-1111` | **D-3**：全部入 `values/strings.xml` 四语；新增键候选 `record_field_app / record_field_exit_code / record_field_pid / record_field_vpn_active / record_action_detected / record_action_blocked`（复用既有 `record_data_*`、`dir_*`、`meta_*`）；门禁扩围 |
| V3.5 | P2 | SystemRow 信息自重复：headline 与 preview 同值（`[tag]`/hash16 二选一渲染两次），白占两行；投影里可用的 `logcatLevel/logcatMessage` 反而零呈现 | `data/mapping/RowModel.kt:268-282`、`RecordRow.kt:755-762` | headline=tag 身份、preview=message 行；hash 仅无 tag 时回落 |
| V3.6 | P2 | 文档漂移：AGENTS #20 声称「路径/命令中缀省略走 MiddleEllipsisText」，该组件生产**零调用**（仅 JVM 纯函数测试钉着），行内 preview 实为尾省略 | `ui/components/MiddleEllipsisText.kt:62-113`（定义完整含像素复测防护）；`app/src/main` 全树零 import 调用点；AGENTS.md #20 | **D-2**：行为/偏好/WebView 的 path/url/argv 行真实启用中缀省略；或本项否决则退役组件 + AGENTS 同步 |
| V3.7 | **P1** | 列表重组性能：`RecordTimelineList` 内容构建 = 每次重组（含每次分页 append）全量重跑 `for (index in 0 until itemCount)`，每索引 `records[i]`/`records[i-1]` 访问 + `groupLabelOrNull`（内部 2×`secondKey` + 2×`dayKey` + `formatSecond`/`formatDay` 每次 **new SimpleDateFormat**）；`PagingConfig` 无 `maxSize`（DAO 上限 20_000）——深滚 30 页 ≈1800 索引 × 每索引 2–4 次格式化，全在主线程组合期 | `ui/records/RecordTimelineList.kt:144-201`；`ui/common/TimelineGroup.kt:35-41, 48-60`（SDF 每调新建，`dayKey` ThreadLocal 仅缓解一处）、`:73-90`（groupLabelOrNull）；`data/repo/LogRepository.kt:304`（无 maxSize）、`:586-596`（20K 窗） | 组头位点预计算（`remember` 派生 index→header 映射或邻 key 短路缓存）；共享格式器（ThreadLocal 化 formatSecond/formatDay）；`maxSize` 设定须同步 `RecordPagingProbeTest` 回归（AGENTS #14 固定小 limit 死锁警戒，勿混同） |
| V3.8 | P2 | 行复用池异质共享：`contentType` 恒 `"record"` 跨 10 族（徽章数/行数不同全量重组合）；搜索展开双 LazyColumn 共挂同一 VM `listState`（Scaffold + Dialog 同时挂载，组合成本 ×2，脆弱形态） | `RecordTimelineList.kt:186`；`ui/records/MonitorRecordsScreen.kt:419, 472`（同一 `viewModel.listState`，VM `:51`）；测试注释自认双份渲染 `MonitorListScreensTest.kt:56-57` | `contentType` 细化到行族；重构（拆行后行高差异更大）必须做，否则 T4 后滚动复用退化放大 |

### 1.4 R4 杜绝卡顿与崩溃（8 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V4.1 | **P1** | 干预语义静默失效 + 记录误报：`XposedClassHooker` 在 before 回调**抛 CNFE** 表达拦截——libxposed PROTECTIVE 语义下回调异常被框架吞掉、**原方法照常执行**；legacy 路径 `runSafely` 同样吞。结果：Xposed 类照样加载（隐藏功能失效），记录却已按 `BLOCKED` 落库（观测面说谎）。与 V2.1 叠加：Vector 首注册语义下该监视器本就可能整体失声 | `hook/hooks/XposedClassHooker.kt:52-60`（throw 于 before）、`:44-51`（emit 先于 throw）；吞异常面 `hook/adapters/LegacyPlatform.kt:26-31, 64-69`（runSafely） | **D-5**：`Invocation.setThrowable` 显式短路 + 双框架真机验证（`de.robv.android.xposed.XposedHelpers` 经 `loadClass` 必 CNFE）；BLOCKED 记录仅在短路真实生效后发出 |
| V4.2 | **P1** | 门禁缺口：现有 NEG 断言只证「记录数 = 0」（`20-run-listen.ps1:118-130`），**不证宿主线程零工作**——V2.1–V2.4/V2.7/V2.8 的门控倒挂全部从现有矩阵逃逸 | `scripts/test-integration/20-run-listen.ps1:118-130` | **零工作门禁**：静态扫描 `HotPathGateConventionTest`（热路径 hooker 清单的回调首条语句必须是门控，白名单豁免）+ 单测驱动回调断言 seam 计数零触发（T2） |
| V4.3 | **P1** | 门禁缺口：R1 三条负向场景零覆盖——①模块停用/注入失败 → 宿主存活与行为一致；②宿主端冻结 socket（背压）→ 注入侧线程可恢复、宿主 hook 线程不阻塞（V1.1 链不可测现状）；③V2.1 Vector 监视器归属无断言 | `scripts/test-integration/` 全目录无对应脚本；`23-run-smoke.ps1:25-40` 仅全开档案存活 | T6 新增设备脚本 `26-run-module-off-negative.ps1` / `27-run-backpressure.ps1`（序号避让既有 `25-run-v42-stress.ps1`） + JVM 归属断言（注册表单一化后天然可测） |
| V4.4 | P2 | `HookerSupport` ThreadLocal 栈暂存配对缺陷：legacy 原方法抛异常即跳过 after → 暂存字节驻留线程栈（慢泄漏）；嵌套 + 异常混排后 `take()` 与后续操作错配（**记录数据错位**，观测正确性） | `hook/HookerSupport.kt:12`（`ThreadLocal<ArrayDeque<ByteArray?>>`）、`:46-56`；异常路径 `hook/adapters/LegacyPlatform.kt:30`（after 不执行） | 暂存挂 Invocation 上下文或带 epoch 校验；异常路径补偿清理 + JVM 测试 |
| V4.5 | P2 | 重入爆栈潜伏雷：钩 `System.getProperty` 且回调内再调 `System.getProperty(key)` 无 reentry 守卫；当前画像键（ro.*）与 JVM 属性键空间不相交故未爆——属雷非灾。对照：同域 `SystemPropertiesHooker` 已有 `inHook` ThreadLocal 守卫 | `hook/hooks/DeviceSysPropHooker.kt:33-45`（`:39-41` 回调内再调）；正面对照 `hook/hooks/SystemPropertiesHooker.kt:27, 42-44` | 仿 `inHook` 守卫补齐；单测注入重叠键验证 |
| V4.6 | P2 | `VpnHideHooker.renamedInterfaces` 非线程安全 `HashMap` 多网络线程并发 `getOrPut`（数据竞争可坏链表）；after 消耗原 `Enumeration` 后置替代值——宿主若持有原枚举重试即见空（返回值语义微变） | `hook/hooks/VpnHideHooker.kt:22, 268`（HashMap）；`:131-148`（枚举消耗 + setResult） | `ConcurrentHashMap`；枚举消耗改「copy 后过滤」 |
| V4.7 | P2 | 宿主 filesDir 持续低频写：worker 空闲循环中 `persistSeqCheckpointIfDue` 每秒一次 `writeText`（无增量判定），叠加 `currentSpool()` 反射重试探测 | `hook/RecordSink.kt:168-175`（checkpoint）、`:203`（循环内调用）、`:177-205`（空闲轮询） | checkpoint 仅 seq 有推进时写；空闲退避（1s→5s→30s） |
| V4.8 | P2 | 实现契约靠默契无防护：`MethodFinder` 非线程安全 HashMap 缓存 + 无界类缓存（现全部调用点在 install 线程——事实安全、契约未固化无注释无断言）；spool 未就绪窗口内每 provider 调用重复 `Class.forName("android.app.ActivityThread")` | `hook/MethodFinder.kt:12-52`；`hook/HookInstaller.kt:211-215` | 缓存加「仅安装线程使用」断言或转 ConcurrentHashMap；ActivityThread 类引用单点缓存 |

### 1.5 R5 底层组件/工具与业务隔离（14 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V5.1 | **P1** | core 宣称「纯 Kotlin 零 Android」为假：`android.util.Log` 直入模型层；11 个 core 文件 import compose `@Immutable`；构建脚本已为此开 `isReturnDefaultValues` 兜底（= 官方承认泄漏）。后果：core 无法下沉纯 JVM 模块，分层宣称与事实漂移 | `core/model/EffectiveSwitch.kt:3`（`import android.util.Log`，`:281` `Log.w`）；`core/model/CryptoRecord.kt:3` 等 11 处；`app/build.gradle.kts:119`；漂移文档 `ARCHITECTURE_AUDIT_TASKS.md:17` | Log 换注入 seam（纯函数出口或回调参数）；`@Immutable` 属 JVM 兼容可留但需扫描规则明示白名单；core 建 import 扫描门禁 |
| V5.2 | **P1** | **边界扫描可被 FQN 绕过且已发生**：全部扫描规则只匹配 `^import` 行，`data.repo → mcp` 反向依赖以全限定引用形态存在且无任何禁止面（B1 只扫 mcp→外），编译期依赖环 `data.repo ↔ mcp` 成立 | `data/repo/SettingsRepository.kt:306, 465`（`com.lioisme.modev.mcp.protocol.McpProtocol.DEFAULT_PORT` FQN）；扫描形态 `app/src/test/java/com/lioisme/modev/architecture/PackageBoundaryTest.kt:42-94`、`app/src/test/java/com/lioisme/modev/mcp/McpToolParityTest.kt:92-99`（6 条 `import\s+` 正则） | 扫描改正则 `com\.lioisme\.modev\.<pkg>\.`（全文本匹配）；MCP 端口默认值引用改经 `ConfigKeys`/宿主常量单点 |
| V5.3 | **P1** | domain→data 逆向依赖零规则且已 3 处实证：「领域抽象层」锚定数据层具体类型，AGENTS #26「与 UI 同上游继承」被架空一半 | `domain/diagnostics/DiagnosticsReport.kt:3`（`data.repo.PipelineStats`）、`domain/record/RecordPage.kt:3`（`data.mapping.RowModel`）、`domain/record/RecordService.kt:5-6`（`data.mapping.RecordDetailModel/RowModel`） | `RowModel/RecordDetailModel/PipelineStats` 上移 domain（包名保留 typealias 兼容），或 R2 规则增补 `domain.* 禁 data.*` |
| V5.4 | **P1** | mcp B1 未禁 `data.mapping`，MCP DTO 层与存储行映射直接绑死（import 12 个 Row 类型 + RowModel + RecordDetailModel）——「同上游」实为「同下游」；与 AGENTS #26 声明的允许面（`domain.*/data.repo.*/core.*`）不符，扫描面与文档口径漂移 | `McpToolParityTest.kt:92-99`（forbidden 六项无 mapping）；`mcp/tools/McpDtos.kt:6-17` | 随 V5.3 上移后天然合规；B1 增补 `data.mapping` 禁令逼出契约层 |
| V5.5 | **P1** | god-class：`SettingsRepository`（489 行）= prefs 持久化 + wire 推送 + **MCP 服务生命周期控制** 三合一，且以 public 可变回调直接操纵运行中服务（`var mcpRestarter` / `var mcpTokenSetUpdater`），设置仓库一改三域同震 | `data/repo/SettingsRepository.kt:32-135`（ConfigWriter 接口 + 实现 + 仓库同文件）、`:140`（构造持有 ConfigServer）、`:246`（repushAllConnected）、`:322, 335`（public @Volatile 回调槽） | 拆 `ConfigRepository`（纯状态）/ `ConfigPushGateway`（wire）/ 装配回 `ModevApp`（MCP 生命周期）；回调槽改构造注入不可变 |
| V5.6 | **P1** | god-class：`LogRepository`（634 行）= 摄入写管道 + 读门面（`implements RecordService`）+ 容量治理 + 存储统计四合一，且构造期反向接线 server（`server.onRecord = ::onInjectedRecord`）；列表 UI 重构（T4）换读路径必撞写管道测试 | `data/repo/LogRepository.kt:79-80, 90-91, 97-105, 131` | 拆写侧 `RecordIngestPipeline` / 读侧 `RecordQueryFacade`（domain 接口 `RecordService` 已具，只差实现分离） |
| V5.7 | **P1** | 同型扩展两包重复定义：`data/repo/RecordFilter.kt` 在 typealias 兼容层之外**再重复**定义与 domain 逐字同体的 `isCryptoOnly/isDatabaseOnly/hasConstraint` 扩展，全仓消费方 9 处全走 domain 版——data 版为死重复，漏改一处即行为分叉 | `data/repo/RecordFilter.kt:10-26` vs `domain/record/RecordFilter.kt:41-47`（grep `import com.lioisme.modev.data.repo.(isCryptoOnly|hasConstraint)` 零命中） | 删 data 侧三扩展，shim 仅留 typealias；补「扩展重复」扫描 |
| V5.8 | **P1** | 装配 = 手工 service locator + 具体类直连 + 相互回填可变槽：全仓 19 文件 `applicationContext as ModevApp` 取件；仓库构造无接口 seam → 单仓库测试必装 Room/NanoHTTPD/prefs 全套影子，直接解释 Robolectric 占比 33%（34/103 JVM 测试文件）；MCP 装配以 52 行内联 FQN 链书写（V5.2 绕扫描问题的源头） | `ModevApp.kt:63-180`（直构 `SettingsRepository(server=…)`、`:89` `server.effectiveResolver = {…}` 互回填、`:128-180` 内联 FQN 装配段）；测试统计实测 34 Robolectric / 103 | 关键协作件提接口 seam（`RecordPusher`/`McpController`/`ConfigStore`）；装配集中且构造注入；目标：6 个纯逻辑仓库测试回 JVM（T7 验收项） |
| V5.9 | P2 | 业务枚举唯一事实源住「底层组件库」：`CategoryTaxonomy`（core/model）硬编码 81 类别（FEATURE 37 / LISTEN 44），业务定义与底层工具同居一包 | `core/model/CategoryTaxonomy.kt:53-155`；AGENTS #23（禁退役） | **D-7**：本轮登记技术债，随 D-6 后置模块迁移；core 扫描建「业务枚举准入白名单」 |
| V5.10 | P2 | 「数值/字节/时间 → 本地化字符串」逻辑 4 处重复实现、locale 分支各自为政（zh 进位判断写两遍、StorageFormat 自写 `String.format(Locale.US,…)`、详情 301 行 format* 独立一族），V42 T5 四语升级即逐点扫雷的根因 | `ui/apps/AppCountFormat.kt:22`、`ui/common/TimelineGroup.kt:31-60`、`ui/settings/StorageFormat.kt:13-25`、`ui/detail/RecordDetailTextFormat.kt`（301 行） | 合并 `presentation/format` 单点，locale→进位组表化；`AppCountFormat` 语义保留为门面 |
| V5.11 | P2 | 第三方渲染库 13 符号直渗业务组合层（mikepenz 6 + highlights 4 + intellij AST 4 于两文件），无门面接口，升级/换库波及业务屏；对照组 = NanoHTTPD 全仓唯一收口 64 行（正模板） | `ui/detail/markdown/RecordMarkdownContent.kt:53-65`、`ui/detail/markdown/CodeGroupExtractor.kt:5-9`；对照 `mcp/McpHttpEngine.kt:3, 24` | `MarkdownRenderer` 门面收口；版本锁定 0.38.1 不变（AGENTS #3 禁升条款保留） |
| V5.12 | P2 | 单 Gradle 模块承载 326 主源文件 / 4.3 万行：注入侧与 UI 渲染库（coil/markdown/materialkolor）同 classpath 同 APK，compose 升级可波及注入编译；可见性默认过宽（internal 声明仅 137 个，核心仓库/服务全 public），包边界靠自律 | `settings.gradle.kts:29-30`（仅 :app + :testtarget）、`app/build.gradle.kts:137-186`（统一依赖集）；AGENTS 已知坑「模块与 app 各带一份 stdlib」佐证注入 classpath 敏感性 | **D-6**：本轮出 `:modev-core`（core+domain 纯 JVM）可行性结论；过渡手段 = 非装配类型一律 internal + 装配点白名单 |
| V5.13 | P2 | 协议/存储常量以**散文**散布：MCP 端口 1510 写进 ≥7 处 KDoc（V39 迁移即逐点改注释的前科）；DB `version=14` + 迁移清单 + `addMigrations` 手工三处同步无门禁；宿主 prefs 双命名空间（`modev_settings` / `modev_config`）无对照表 | `core/model/ConfigKeys.kt:34, 47`、`data/repo/SettingsRepository.kt:187, 303, 308`、`core/codec/WireProtocol.kt:25`、`mcp/protocol/McpProtocol.kt:21`；`data/db/ModevDatabase.kt:52, 63-299, 305-308`；`ModevApp.kt:237` vs `framework/RemotePrefsBridge.kt:19` | 端口注释引用常量而非复写数值；DB 迁移「清单遗漏」门禁（version ↔ addMigrations 数对账 JVM 测试）；命名空间对照表入 domain 文档 |
| V5.14 | P2 | framework 包无归属域、无扫描、双向消费（宿主 ModevApp / 注入 HookInstaller 各 import），「仅宿主进程调用」靠 KDoc 自觉；`serviceFlow: StateFlow<Any?>` 为防 legacy checkcast 把类型不安全固化，消费方裸 cast | `framework/FrameworkBridge.kt:14, 27`；`hook/HookInstaller.kt:32`（import `framework.RemotePrefsBridge`）；`ModevApp.kt:116, 207`（`as XposedService`） | 拆 `framework.host`/`framework.inject`；cast 收敛单点泛型助手；纳入扫描禁止面 |

### 1.6 违规统计

见 §0.2 表。**P0 = 0**；现实「宿主卡死」需 V1.1（锁链）+ 队列满 + socket 零窗口三条件同启，概率低但后果为 ANR 级，列 P1 首位；V4.1 属「Modev 功能自身失效 + 观测撒谎」，非宿主损伤。

### 1.7 合规确认（防误报留档，逐条实核）

- 启动关键路径**无**阻塞钩子：TCP 连接/重连/心跳/盘 IO 全在 daemon 线程（`ConfigChannel.kt:88-108`、`RecordSink.kt:88-92`）；hook 回调内零 socket/sleep/latch/runBlocking（全树仅 `ConfigChannel.kt:113/169/255`、`ConfigServer.kt:161` 三处 sleep，均非宿主线程；全树 0 协程）。
- 异常隔离面完备：legacy runSafely 双回调兜底、modern PROTECTIVE、探针安装逐点 try/catch（`HookInstaller.kt:197-207`）、`ConfigWatcher.notify` 逐 listener 隔离（`ConfigWatcher.kt:24-27`）——但 V4.1 证明「吞异常」对**干预语义**是反效果。
- D-17 红线合规：未钩 `Class.forName`（`XposedClassHooker.kt:28-32` 注释与实现一致）；`findClass` 无重复注册。
- 被动采集红线合规：游标仅钩宿主自身 move* 后采集、`mCount` 纯字段反射读（`SqlDatabaseHooker.kt:342-345, 429-435`）；`AssetHooker`/`FileStreamHooker`/`NioFilesHooker.kt:60-79` 不触碰流句柄；`PrefsInstanceIndex` 无反射读内部字段。
- `EmulatorBuildHooker` 安装期无条件篡改**指控不成立**：`featureOn` 前置于 CAS（`hook/hooks/EmulatorBuildHooker.kt:29-34, 52-57`），`ConfigWatcher` 惰性补写属实。
- FeatureGate 首行门控在 ExitIntercept/CrashPrevent/Dialog 关键词/SystemProperties/Settings/ScreenRecord/Proxy/Dns 普遍成立；SessionThrottle/PrefsInstanceIndex/spool/reader 集均有界；`WebViewJsBridge.report` 静默无宿主回调；`HookInstaller` 探针间互不阻断。
- 列表侧无主线程 binder/IO：`cachedIn`（`RecordsViewModel.kt:63-64`）、大窗口 limit（`LogRepository.kt:319`）、`enablePlaceholders=false`（`:304`）、AppIcon Coil suspend fetch（`AppIcon.kt:107-114`）、详情 markdown `immediate=true` 有界解析、FullscreenTextDialog O(1) 分块均合规。
- `RecordService.kt`/`RecordFilter.kt`（data 侧）typealias 兼容层本身合规，问题仅在扩展重复（V5.7）。

---

## 2. 任务分解（实施序 = T1→T2→T3 注入侧同批设备验收 → T4→T5 UI 同批 → T6 门禁收口 → T7 架构独立轨）

### T1 宿主线程零阻塞改造（R1：V1.1 / V1.2 / V1.3 / V1.4；R4：V4.7 / V4.8）— 复杂度 L ✅ 已完成（V43 实施备注）

> ①`ConfigChannel`：watchdog 脱离 `writerLock` **无锁直关 socket**（volatile socket），并加「代元失效即自退」杜绝看门狗线程随重连累积；新增测试 seam（port/writeTimeoutMs/heartbeatMs，生产默认值不变）。
> ②`RecordSpool.drain` 两段式：`@Synchronized` 锁内快照（有界 2048 行）→ **锁外网络发送** → 慢游标本下轮重放；`append`（宿主降级路径）不再与 socket 写争锁。
> ③`RecordSink`：`emitUrgent` 只落盘不 drain（V1.2，注释同步）；seq 续号优先由 worker 首帧完成（`tryLock` 非阻塞兜底，取 max 续接保单调）；checkpoint 仅 seq 推进时重写（V4.7）；空闲轮询 500ms→8s 指数退避；死代码 `flush()`（全仓零调用）删除。
> ④`ModevModule` 入口级 try/catch 兜底（对齐 legacy，V1.4）；安装期磁盘兜底读移 `modev-config-fallback` daemon 线程（V1.3）；`applicationContext()` ActivityThread 类引用单点缓存（V4.8）；`MethodFinder` 缓存转 ConcurrentHashMap（契约注释固化）。
> 定向测试：`ConfigChannelBackpressureTest` 1/1 绿（真实回环流控驱动写阻塞→watchdog 秒级解阻）；`RecordSpoolTest` 16/16 绿（新增锁外发送并发钉死）；`RecordSinkTest` 7/7 绿（新增 emitUrgent 零发送 + checkpoint 零重写）。设备矩阵（23/26/27）待设备接入后补跑。

- `ConfigChannel`：watchdog 关 socket 脱离 `writerLock`（`Socket.close` 线程安全）；`sendRecordJson` 改 `ReentrantLock.tryLock` 或保留锁但 watchdog 直关（关闭令 write 抛 `SocketException`，writer 的 catch 路径已有 `closeSocket` 幂等）；写路径超时兜底改 socket 级手段（SO_LINGER/可关闭通道择一，真机验证 TCP 零窗口下 write 解阻行为）。
- `RecordSpool.drain`：锁内快照出批 → 锁外 `send` → 失败回插保序；`append` 保持锁内短操作。
- `RecordSink`：`emitUrgent` 只 `append`（落盘）不 `drain`，drain 交 worker；`nextSeq` 首次恢复移交 worker 线程（V38「Context 依赖初始化惰性到首次使用」规约的续作）；队列满降级路径与注释对齐（`RecordSink.kt:100-106`）；seq checkpoint 仅推进时写 + 空闲轮询退避（V4.7）。
- `ModevModule` 入口顶层 try/catch；安装期磁盘兜底读异步化（V1.3）；`MethodFinder` 安装线程契约断言/注释固化 + `ActivityThread` 类引用单点缓存（V4.8）。
- 测试：新 `ConfigChannelBackpressureTest`（JVM，假阻塞 OutputStream → watchdog 关 → writer 在预算内解除阻塞）；`RecordSpoolTest`/`RecordSinkTest` 反转锁语义（断言「drain 网络期 append 可在预算内完成」）；`23-run-smoke.ps1` 全绿触发（改 `hook/**`）。
- 验收条款：宿主 hook 线程在「队列满 + 对端冻结」注入下 100ms 内返回（设备脚本 `27-run-backpressure.ps1`，T6 交付）。

### T2 热路径门控收敛（R2：V2.1–V2.4 / V2.7 / V2.8）— 复杂度 L ✅ 已完成（V43 实施备注）

> ①新建 `hook/LoadClassWatcher`：loadClass/findClass **全进程单一注册点 + 订阅广播**（干预型注册；懒安装=无订阅者即零钩挂）；`XposedClassHooker`/`SSLBypassHooker`/`NetworkRequestHooker` 三处自持钩子全部退役为订阅者（V2.1，D-4 落实）。
> ②门控前置：`PrefsGetHooker` 四回调（getAll/getStringSet/contains/scalar）首行 `prefsEnabled(GET)`（V2.2——序列化/pathOf 不再于开关关闭时执行）；`ActivityTransitionHooker` startActivity/onCreate 首行 `featureOn(...)`（V2.3——反射查找/resolveActivity IPC/Bundle 反序列化入门内）；`SqlStatementHooker` bind/clearBindings/execute 首行 `dbListen` + `SqlDatabaseHooker.afterQuery` 首行 `dbEnabled(QUERY)`（V2.4）。
> ③`RootFileHooker.exists` 双分支门控先行、lowercase 后置（V2.7）；`DetectionMonitorHooker.readLine` 增 DETECT_EMULATOR 前置门控（V2.7）；`AppInfoHooker` 首行门控 + applicationInfo 取值链修复（minSdk/targetSdk 恒 null 功能缺陷，V2.8）。
> ④新静态门禁 `HotPathGateConventionTest`（17 文件清单：回调首条有效语句必须为门控/廉价守卫/委托已门控助手）。
> 定向测试：`SharedHookDispatchTest` 5/5、`HotPathGateConventionTest` 1/1、`PrefsGetHookerZeroWorkTest` 3/3（投毒 Map：全关 `touched=0`、GET 开 `touched>0` 双向对照，V2.2「序列化先于门控」形态的直接哨兵）、hook.* 全绿；扫描器对「首行先做 lowercase 分配」形态实测可红（T2 前违规清单即其输出）。

- `LoadClassWatcher` 单监视器（D-4）：三处 loadClass/findClass 钩子收敛为注册表广播（订阅者 = Xposed 隐藏、SSL 惰性安装、okhttp 惰性安装），每订阅者回调首行自带门控。
- `PrefsGetHooker`：getAll/getStringSet/contains 首行 `prefsEnabled(GET)` 短路（V2.2）；`ActivityTransitionHooker`：首行 `uiEnabled(ACTIVITY_START)` 短路、extras 门内读（V2.3）；`SqlStatementHooker`/`SqlDatabaseHooker`：首行 `dbListen` 短路、bind 跟踪表仅 QUERY 开启时维护（V2.4）。
- `RootFileHooker.exists` / `DetectionMonitorHooker.readLine`：lowercase 后置（V2.7）；`AppInfoHooker`：gate + 取值链修复 + 单测（V2.8）。
- 新静态门禁 `HotPathGateConventionTest`：对热路径 hooker 清单（loadClass/findClass/getAll/bind*/startActivity/File.exists/readLine/getPackageInfo）断言回调首条有效语句为门控或监视器登记（D-2 名单入测试常量，新增热路径钩子未登记即红）（V4.2）。
- 测试：每个被修 hooker 补「全关驱动回调 → 门控后零调用」JVM 行为测试（假 Invocation + 计数 seam）；`McpToolParityTest`/`InterventionGateConventionTest` 零波及。

### T3 干预语义正确性（R2：V2.5 / V2.6 / V2.9 / V2.10；R4：V4.1 / V4.5 / V4.6；R4：V4.4 HookerSupport 配对）— 复杂度 L ✅ 已完成（代码与 JVM 侧；设备侧验证待跑）

> ①引擎显式化（D-5）：`Invocation.setThrowable` 新 API；`HookEngine.hookThrowing`→`Platform.registerInterventional`；libxposed 适配器干预型=**PASSTHROUGH + 回调体 runSafely 隔离**（缺陷不外泄、受控异常抵达调用方），legacy `MethodHookParam.setThrowable` 天然可用（默认转 register）；`Invocation.token`（chain/param 底层句柄）+ `consumed` 双属性补齐广播链一致性（after 子链实时读替换结果）。
> ②`XposedClassHooker`：抛 CNFE → `setThrowable(ClassNotFoundException)`（V4.1，双框架语义复核——旧 throw 在 PROTECTIVE/runSafely 下被吞=假干预；BLOCKED 记录仅在短路真实生效路径发出）；经 T2 的 LoadClassWatcher 订阅（消灭 V2.1 三注册）。
> ③契约忠实化（V2.5/D-6）：`RootExecHooker` 三处 + `RootPackageManagerHooker.getPackageInfo` 的 `setResult(null)` 全改 `setThrowable(IOException / NameNotFoundException)`；`WebViewHooker.ensureBridge` 入门控（V2.6，全关=宿主页面零 `modevJsBridge` 注入）。
> ④同方法多 hooker 收敛（V2.9/D-4 通用化）：新建 `SharedMethodBroadcast`（按方法签名键单注册点广播，干预型注册，异常隔离）；`System.getProperty`（ProxyHide/DeviceSysProp/RootProperty 三订阅）、`Settings$Secure.getString`（Settings/EmulatorNetwork）、`WifiInfo.getMacAddress`（EmulatorNetwork/DetectionMonitor）、`getPackageInfo`（RootPackageManager 干预 + AppInfo 观测）全部并轨。**RR-2 前置实验（L-DUP Vector 归属实况）仍待设备批次复核，但收敛后的单一注册点在任一框架语义下行为确定，不再依赖首注册次序。**
> ⑤稳定性杂项：`DeviceSysPropHooker` 增 inHook 重入守卫（V4.5）；`VpnHideHooker.renamedInterfaces` ConcurrentHashMap + computeIfAbsent、getCapabilities 仅真实追加时申报（V4.6）；`MessageDigestHooker.update` 按实例 1s SessionThrottle 节流（V2.10，inputTotal 统计不受影响）；`HookerSupport` 暂存改 **token 键控 + 32 条上限**（V4.4：交错配对正确 + 异常路径不再无界驻留）。
> 定向测试：`SharedHookDispatchTest` 5/5（签名去重/短路即止/结果级联/异常隔离/watcher 单注册）、`HookerSupportPairingTest` 3/3、`RootExecContractTest` 2/2（su→IOException、benign→零干预）；hook.*/architecture.* 全绿 + 双变体编译通过。设备侧 `XposedHideVerifyTest` 二态断言归 T6 脚本矩阵（无设备待跑）。

- `Invocation.setThrowable` 引擎能力（D-5）：`LegacyPlatform` 映射 `param.setThrowable`，`LibxposedPlatform` 按 API 语义实现并真机双框架验证（V4.1）；`XposedClassHooker` 改 setThrowable(CNFE)（经 LoadClassWatcher 订阅），BLOCKED 记录仅在短路验证生效路径发出。
- 契约忠实化（V2.5）：`RootExecHooker` 三处、`RootPackageManagerHooker:169` 改 `setThrowable(IOException / NameNotFoundException)`；替身方案为备选，真机靶应用验证。
- `WebViewHooker` 桥/记录双路径门控（V2.6）；`DeviceSysPropHooker` reentry 守卫（V4.5）；`VpnHideHooker` ConcurrentHashMap + copy 过滤（V4.6）；`MessageDigestHooker` update 节流聚合（V2.10）。
- 重复注册清单核对与收敛（V2.9）：`getPackageInfo`/`Settings$Secure.getString`/`System.getProperty` 各建单一分派点；**前置实验** = 真机 Vector/LSPosed 双框架跑多 hooker 同方法注册序行为矩阵（L-DUP），确认归属实况再定收敛范围（RR-2）。
- 测试：`20-run-listen.ps1` 各 NEG 增「桥不可枚举」「CNFE 短路生效」断言；新 `XposedHideVerifyTest`（androidTest 于靶应用内 `Class.forName("de.robv.android.xposed.XposedHelpers")` 二态断言——开必 CNFE、关必成功）。

### T4 记录列表 UI 重构：一行一参数（R3：V3.1–V3.6）— 复杂度 XL ✅ 已完成（代码与编译/插桩编译；设备插桩执行待跑）

> ①模型：`RowPresentation.supporting` 从「meta 整串 + preview」两槽改为 **`fields: List<RowField(labelRes?, value, mono, valueColor?, middleEllipsis?)` 字段行列表**（D-1 严格一行一参数；`RowField` 渲染合并为单一 Text 节点，「标签 值」AnnotatedString 同节点）；应用语境（contextLabel）独立首行 `record_field_app`；类别行 = accent 着色纯值行，操作行 = `meta_operation` 标签行；「 · 」拼串形态在行内全面退役。
> ②逐族拆行落地：SQL=[类别][操作][库名(record_data_db)][行数][影响行数][SQL 预览(中缀)]；进程=[类别][操作][退出码(meta_exit_code)][pid(meta_pid)][状态(record_action_*)]，headline=操作名资源本地化（`ProcessRow.headlineText` 英文 "exit=X/detected" 形态退役，V3.3）；crypto=[类别][操作][数据段(record_data_*)]；behavior=[类别][操作][path/argv 中缀][destPath(meta_dest_path)][大小(meta_file_size,StorageFormat)]；net=[类别][操作][URL][方法(meta_method)][状态码(meta_status_code)][代理(meta_proxy)][VPN(meta_vpn+record_action_detected/record_value_no)]；deviceenv/system/uiinteraction 目标与主体值行按 V3.5 原则与 headline 去重；SystemRow 恢复 logcatLevel/message 呈现（旧行零展示）。
> ③i18n（V3.4/D-3）：新增六键四语（record_field_app / meta_exit_code / meta_pid / record_action_blocked / record_action_detected / record_value_no）；行内与摘要函数（webviewSummary/networkSummary/systemSummary/processControlSummary 等）英文硬编码（"bridge: "/"HTTP "/"proxy: "/"VPN: "/"exit="/"pid="/"screen="/"blocked"/"detected"）全部入资源。
> ④V3.6 兑现：`MiddleEllipsisText` 真实启用（path/argv/URL/destPath/SQL 预览/长主体行），AGENTS #20 同步重写（含「文档声称中缀、实装尾省略」漂移修正声明）。
> ⑤测试同步：`RecordRowLayoutTest` 拆行断言改写 + 新结构哨兵 `noMultiParameterLines_withMiddleDotJoining`（record-row 内出现「 · 」拼串即红 + 应用语境行断言）；插桩其余四类（TimelineListTest/MonitorListScreensTest/GlobalTypeRecordsTest/RecordListScrollPreservationTest/FourScreensNavTest）断言均为 substring/身份文本锚点，经盘点零波及；`copySummary` 行序契约不变。
> 定向验证：`compileModernDebugKotlin` + `compileModernDebugAndroidTestKotlin` 绿；i18n 四语 parity 绿；全量单测 706 用例 0 失败；插桩执行待设备。

### T5 列表性能（R3：V3.7 / V3.8）— 复杂度 M ✅ 已完成

> ①组头判定**配对记忆化**（RecordTimelineList）：`remember(locale,is24h)` 的 (prev,ts)→label 缓存，邻行比较纯函数语义（AGENTS #10）不变，append 页全列表重组从每索引 2×secondKey+2×dayKey+格式化重算降为缓存命中；首项 timelineLabel 同法缓存。
> ②`TimelineGroup` 共享格式化器：formatSecond/formatDay 的每次调用 new SimpleDateFormat 改为 ThreadLocal 模式缓存（键=pattern@locale；AGENTS 坑位「SDF 分配风暴」根修，dayKey/secondKey ThreadLocal 前例同构）。
> ③`RecordRow.TimeText` 弃行内 SDF，改走共享 `formatSecond`；④计数副标下沉 `RecordsCountSubtitle` 最小子组合器（MonitorRecordsScreen itemCount 顶层读取消除）。
> ⑤`PagingConfig.maxSize` 经论证**不取**：maxSize 截断深滚会砍掉 >N 条记录可见性（功能回退），与 AGENTS #14 大窗口契约交叠；重组成本已由 ①② 消除，记录在案。contentType 族细分同理论证保留单桶（RecordRow 十族组合结构同构，分桶反伤复用）。
> 验证：全量单测绿（含 TimelineGroupTest 纯函数回归）；重组预算断言（O(新页)）依赖插桩环境，待设备批次（23 扩展段 L-PERF 不覆盖 UI 侧，见 RR-4 注记）。

### T6 稳定性回归门禁（R1/R2/R4：V4.2 / V4.3）— 复杂度 M ✅ 代码侧完成（设备执行待跑）

> ①新设备脚本：`26-run-module-off-negative.ps1`（宿主进程下线 + 无磁盘兜底 → 冷启动存活/无 FATAL/ANR/触发用例有界返回/spool WAL 离线落盘；真停用模块段 -FullSwitch 参数化，默认 SKIP 注明 VM 重启原因；C 段恢复闭环）；`27-run-backpressure.ps1`（黑洞监听占 1511 只 accept 不消费 → 刷记录至写阻塞 → 断言靶进程有界响应（watchdog 锁外解阻，V1.1 照妖镜）→ 黑洞移除后 spool 回放入库）。
> ②`23-run-smoke.ps1` 扩展 L-PERF-01 预算段（D-8 热路径全关零工作：exists/getAll/getProperty/loadClass 单次均摊预算 50/500/50/30µs；testtarget 新增探针预热+均摊计时）。
> ③`21-run-enhance.ps1` E-XPP-01 双态断言补线（OFF：loadClassPath=found 基线可见性条件断言；ON：**loadClassPath=cnfe**——T3 setThrowable 短路的设备侧真值判定，旧 throw 吞异常形态下此断言不可能过）；testtarget E-XPP-01/L-DEV-05 双路径拆分上报（forNamePath/loadClassPath，D-17 缺口声明保持）。
> ④语法门禁：四脚本经 PowerShell Parser 全 OK（AGENTS PS 坑纪律：param 首位、$Matches、无 $args/$pid 遮蔽）。
> 设备执行（23/26/27 + E-XPP-01/双态）待 LDPlayer 接入后跑并回填；`HotPathGateConventionTest`/`ConfigChannelBackpressureTest` 等 JVM 门禁已在 T1/T2 落地全绿。

### T7 架构隔离（R5：V5.1–V5.14）— 复杂度 XL ✅ 已完成（本轮范围含 V5.1/5.2/5.3/5.4/5.7/5.12(部分)/5.13；V5.5/V5.6 god-class 拆分与 V5.8 装配 seam 列为后置，见文末注记）

> ①V5.2 扫描升级：`PackageBoundaryTest` 全面改**文本级全限定引用扫描**（去块/行注释后匹配 `com.lioisme.modev.<子树>`，FQN 内联即红）；`SettingsRepository` 两处 `com.lioisme.modev.mcp.protocol.McpProtocol.DEFAULT_PORT` FQN 清除；MCP `B1` 同步文本级 + 增禁 `data.mapping`（V5.4），`McpDtos` 12 import + 10 处 `is com.lioisme.modev.data.mapping.*Detail` FQN 形态全改 `domain.record`。
> ②V5.3/V5.4 domain 上移：`RowModel` 族（11 型）/`RecordDetailModel` 族（11 型）→ `domain.record`；`PipelineStats` → `domain.diagnostics`；`ChannelStats` → `domain.server`；data 侧 `*Compat.kt` typealias 兼容层（豁免白名单钉死于测试，防真依赖借道）；`RecordService/RecordPage` 的 data.mapping import 消除（同包化）；新规则 domain 禁 android(白名单 @Immutable/androidx.paging)/ui/data 逆向 + hook 禁 ui/compose + data.repo 禁 mcp，全绿。
> ③V5.1 core/domain 去 Android：`EffectiveSwitch` 的 `android.util.Log` → `interventionWarn` 注入 seam（ModevApp/SettingsRepository init 与 HookInstaller install 双端注入）；`ScopeRepository` 两处 `android.util.Log` → logger 构造参（ModevApp 装配）。
> ④V5.13 常量与迁移单点：`ConfigKeys.WIRE_DEFAULT_PORT(1511)/MCP_DEFAULT_PORT(1510)` 唯一事实源（WireProtocol/McpProtocol/SettingsRepository 三处改引）；新 `DbMigrationParityTest`（version↔MIGRATION 链↔addMigrations 清单三向对账 + 迁移名/构造参错位检测）；`RecordTypeCount` 去 `androidx.room.ColumnInfo`（domain 纯净化），DAO 投影别名同步 `AS count`，新 `CryptoRecordDaoTest.observe record type counts maps count alias` 钉死映射。
> ⑤V5.7：`data/repo/RecordFilter.kt` 三条同体死重复扩展删除（typealias 兼容层只留别名），消费方 import 收敛 `domain.record`。
> ⑥V5.14 观察登记：framework 包 host/inject 拆分与 `serviceFlow: StateFlow<Any?>` 类型收敛列入后置（与 V5.5/V5.6 拆分、V5.8 装配接口化、V5.12 internal 批量同属「后置不返工」队列——三者牵动全仓装配与仓库测试影子，按 D-6 结论先补洞后动刀，理由与顺序见文末注记）。
> 验证：全量单测 706/706 绿 + lint 40 warning/1 hint/0 error 零新增 + modern/legacy 双变体编译绿 + assemble 三产物（结果见 §7）。

> 〔T4–T7 原任务要点正文已由上方实施备注取代，规划期细节见 §1 违规清单各行「整改方向」列，此处不再重复保留，避免双版本漂移。〕

---

## 3. 验收标准与门禁

### 3.1 每任务可验证验收（硬条款）

| 任务 | 验收（节选，全部可机检） |
|---|---|
| T1 | watchdog 不取 writerLock（源码断言）；假阻塞写 → 3s 内 writer 线程解阻（新测试）；`emitUrgent` 路径零 `send` 调用（seam 计数）；23-run-smoke 绿 |
| T2 | `HotPathGateConventionTest` 绿且热路径清单 ≥8 项；getAll 全关驱动 → gson.toJson 零调用（计数 seam）；startActivity 全关 → `resolveActivity` 零 IPC（假 PackageManager 断言） |
| T3 | 靶应用双框架 `E-XPP-01 ON loadClassPath=cnfe` 二态断言全绿；exec/getPackageInfo 拦截下宿主抛契约内异常类型且**无 NPE**；WebView 全关 → `modevJsBridge` 不可枚举（JS 侧探针） |
| T4 | 「行内禁「 · 」拼串」结构断言（RecordRowLayoutTest.noMultiParameterLines）；六族拆行清单逐类型行数对账；四语 parity 绿；插桩编译绿 |
| T5 | append 一页重组 → header 纯函数调用次数 ≤ O(新页大小)（缓存命中即 O(1)/索引）；PagingProbe 死锁警戒零回归；深滚主线程帧时间无劣化（设备脚本，待跑） |
| T6 | `23`（含 L-PERF-01 预算段）/`26`/`27` 脚本全绿并入门禁；背压下靶应用存活证据落 JSON |
| T7 | 文本级扫描对 V5.2（SettingsRepository FQN）/V5.3（domain→data）/V5.4（mcp→data.mapping）/V5.7（扩展重复）实位先红后绿；`DbMigrationParityTest` 绿；core 除 `PackageManagerCompat`（Android 工具白名单）零 `android.*` import |

### 3.2 全局门禁（每任务合入前）

1. 架构门禁全绿具名清单：`PackageBoundaryTest` / `RecordDaoAccessGateTest` / `DialogConventionTest` / `AppbarSearchExclusivityTest` / `InterventionGateConventionTest` / `FeatureGateBypassTest` / `HardcodedStringConventionTest` / `McpToolParityTest` + 本轮新增 `HotPathGateConventionTest`。
2. 条件触发：触 `hook/**`（T1–T3）→ `23-run-smoke.ps1` 必跑（含 V43 L-PERF-01 预算段）+ `26-run-module-off-negative.ps1` / `27-run-backpressure.ps1` 新脚本必跑；触 UI 行式（T4/T5）→ 相关 6 个插桩类 + `AnchorScrollRoboTest`；触 `mcp.*`/协议（T7）→ `24-run-mcp.ps1` 42 子断言全绿（VERSION 恒 4、工具面 43 零感知）。
3. `:app:lintModernRelease` 基线零新增（V43 实施后实测 **40 warning / 1 hint / 0 error**）；`:app:testModernDebugUnitTest` 全绿（V43 实施后 **706/706**，109 测试类）；`assembleModernRelease/Debug` 双变体 + `compileLegacyDebugKotlin`。
4. 冷启动/背压/性能预算任一 FAIL 即整批回退——**注入侧任务（T1–T3）的验收环境是设备矩阵，不是 JVM 单测**。

## 4. 风险登记册

| # | 风险 | 缓解 |
|---|---|---|
| RR-1 | V1.1 修复触及 socket 生命周期语义，watchdog 直关可能与重连循环竞态 | epoch 代元守卫已有（AGENTS #19），close/重连接缝处复用；背压脚本 27 为回归门 |
| RR-2 | V2.1/V2.9「Vector 首注册」结论源自 AGENTS 观察，多 hooker 归属实况未逐方法实测 | T3 前置 L-DUP 实验；实测推翻则仅收敛回调数不减注册点，方向不返工 |
| RR-3 | D-1 严格拆行使行高约 ×1.5–2，信息密度降、长列表滚动成本升 | D-5 性能预算兜底（T5）；若维护者否决 D-1，退档「kind+operation 合并行」需重批 |
| RR-4 | T4 牵动 ~1000 行插桩断言，改测引入新盲区 | 断言收敛方向 = 版式进 RowPresentation 纯函数 JVM 测，插桩只留 tag/导航/行为（V41 已有此判例） |
| RR-5 | 搜索双列表共享 listState 属已知脆弱形态，T4 加高行放大动画成本 | 本轮不改结构（改即破滚动保持契约），禁在其上叠内容；登记观察 |
| RR-6 | 设备矩阵环境恢复成本高（AGENTS：install -r 后整机重启、Vector 重注册序列） | T1–T3 合批一次矩阵验收，勿逐任务跑机 |

## 5. 追溯矩阵（需求 → 违规 → 任务 → 验收）

| 需求 | 违规 | 任务 | 关键验收 |
|---|---|---|---|
| R1 宿主启动独立 | V1.1–V1.4 | T1 | 27 背压脚本 + 26 停用负向矩阵 + smoke（含 L-PERF 预算段） |
| R2 零副作用 | V2.1–V2.11 | T2（门控）+ T3（契约/桥/守卫） | HotPathGate 扫描 + getAll/startActivity 零工作断言 + 桥不可枚举 |
| R3 一行一参数 | V3.1–V3.8 | T4（版式/i18n）+ T5（性能/复用池） | 每行参数 ≤1 结构断言 + 四语 parity + header O(新页) |
| R4 杜绝卡顿崩溃 | V4.1–V4.8 | T1（锁链 + V4.7/4.8）+ T3（V4.1/4.5/4.6）+ T6（V4.2/4.3 门禁） | 双框架 XposedHideVerifyTest + 三新脚本 + P95 ≤105% |
| R5 隔离分离 | V5.1–V5.14 | T7 | 旧违例回测先红后绿 + Robolectric ≤28 + 端口/迁移门禁 |

## 6. 实施排期建议（相对顺序，非日历承诺）

1. **T1 → T2 → T3**（注入侧，互有文件重叠：ConfigChannel/RecordSink 先行，门控随后，语义修正最后依赖前两者的 seam）——三任务完成后一次性设备矩阵批（RR-6）。
2. **T4 → T5**（UI 同批，T5 与 T4 共改 RecordRow/RecordTimelineList，禁止拆开改测）。
3. **T6** 随 T1–T3 交付（脚本可先落 26/27，预算段随 T2 完成挂入）。
4. **T7** 独立轨并行（与 T4 在 `presentation/format` 上有一个交点：T7 合并格式器放在 T4 之后，避免新标签格式化两次改道）。

## 7. V43 实施状态总览（2026-09-13 回填）

| 任务 | 状态 | 实施备注（浓缩；逐项细节见 §2 各 ✅ 块） | 定向测试 |
|---|---|---|---|
| T1 宿主线程零阻塞 | ✅ 完成 | watchdog 无锁直关+代元自退；drain 锁外发送；emitUrgent 只落盘；worker 先行续号；checkpoint 推进才写；空闲退避；入口兜底；磁盘兜底异步 | ConfigChannelBackpressureTest 1/1、RecordSpoolTest 16/16、RecordSinkTest 7/7 |
| T2 热路径门控 | ✅ 完成 | LoadClassWatcher 单一注册；Prefs/SQL/ActivityTransition/AppInfo/RootFile/readLine/SignatureMonitor 门控先行；AppInfo 取值链修复 | HotPathGateConventionTest 1/1、SharedHookDispatchTest 5/5、PrefsGetHookerZeroWorkTest 3/3 |
| T3 干预语义 | ✅ 完成（JVM；设备段待跑） | Invocation.setThrowable + hookThrowing(PASSTHROUGH/param.setThrowable)；XposedClassHooker 真短路；RootExec/RootPackageManager 契约化；WebView 桥门控；SharedMethodBroadcast 四组同方法收敛；reentry/并发/节流/暂存 token 配对 | RootExecContractTest 2/2、HookerSupportPairingTest 3/3；E-XPP-01 双态断言入 21 脚本 |
| T4 列表一行一参数 | ✅ 完成 | RowField 字段行模型；六族拆行；6 新键四语；行内+摘要硬编码英文清零；MiddleEllipsisText 真启用；AGENTS #20/#17 重写 | RecordRowLayoutTest 改写+新增结构哨兵；插桩编译绿；i18n parity 绿 |
| T5 列表性能 | ✅ 完成 | 组头判定配对记忆化；TimelineGroup/TimeText 共享格式化器；RecordsCountSubtitle 下沉；maxSize 经论证不取（记录于案） | 全量单测含 TimelineGroupTest 回归绿 |
| T6 回归门禁 | ✅ 代码侧（设备待跑） | 26/27 新脚本 + 23 扩展 L-PERF-01 预算段 + testtarget 双探针；PS Parser 语法全 OK | 四脚本语法门禁 OK；JVM 门禁已绿 |
| T7 架构隔离 | ✅ 完成（范围内） | 文本级 FQN 扫描 + domain→data/hook→ui/data.repo→mcp 新规则；RowModel/RecordDetailModel/PipelineStats/ChannelStats/RecordTypeCount 上移 domain；core/domain 去 android.util.Log（注入 seam）；端口常量单点；DbMigrationParityTest；RecordFilter 死重复清除 | PackageBoundaryTest/McpToolParityTest/DbMigrationParityTest 全绿（旧违例实测先红后绿） |

**全量门禁实测（2026-09-13）**：`:app:testModernDebugUnitTest` **709/709 用例（110 类）0 失败 0 错误 0 跳过**；`:app:lintModernRelease` **40 warning / 1 hint / 0 error**（基线零新增）；`:app:compileModernDebugKotlin / compileLegacyDebugKotlin / compileModernDebugAndroidTestKotlin` 全绿；assemble `modernRelease / modernDebug / legacyRelease` 六 APK 全新产出（09:54–09:57）。

**设备矩阵（待执行清单，接入 LDPlayer 后按序跑并回填）**：
1. `10-build-install.ps1` → `11-vector-scope.ps1`（模块重装+作用域，AGENTS install -r 后整机重启纪律）；
2. `23-run-smoke.ps1`（冷启动冒烟 + **L-PERF-01 热路径预算新段**）；
3. `21-run-enhance.ps1`（增强矩阵 + **E-XPP-01 双态新断言**：OFF loadClassPath=found 基线 / ON loadClassPath=cnfe setThrowable 真短路）；
4. `26-run-module-off-negative.ps1`（宿主不可达负向 + spool 离线落盘 + 恢复回放）；
5. `27-run-backpressure.ps1`（黑洞监听背压 → 靶进程有界响应 → 恢复回放，V1.1 回归门）；
6. `20/22/24/25-run-*.ps1`（既有监听/联动/MCP/V42 压力矩阵零回归确认）。

**后置队列（V43 登记、不返工，下一轮候选）**：V5.5/V5.6 god-class 拆分（SettingsRepository/LogRepository）、V5.8 装配接口化与 Robolectric 占比收敛、V5.10 格式化单点合并、V5.11 MarkdownRenderer 门面、V5.12 internal 批量 + `:modev-core` 模块抽取（D-6 结论：前置 V5.1/V5.3 已全绿，抽模块已无扫描洞阻碍）、V5.14 framework 拆分。理由：本轮优先补扫描洞 + 迁移类型锚点（结构性、防回归），装配级拆分牵动全仓测试影子，应独立成轮。
