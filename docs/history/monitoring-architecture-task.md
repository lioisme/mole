# Modev 监控架构生产化任务文档(监听平台化)

> 名称:**Modev 监听平台化重构** · 范围:`core / hook / data / domain / framework / ui` 全层
> 目标:① 算法监听全部使用标准、规范、专业、高效的方法;② 界面 / 算法监听 / 日志严格分离解耦,为后续**数据库监听、行为监听、交互监听**等能力铺路(算法监听只是能力之一);③ 代码质量与架构设计达到大厂(Google/Apple)验收标准。
> 状态图例:`⬜ 待办` → `🔄 进行中` → `✅ 已完成`
> 设计约束(用户确认):协议允许破坏性变更(VERSION 4,双端同 APK 升级,无跨版本兼容诉求);本次仅架构预留,不实现任何新监听类型;文档为执行依据,按阶段自驱动实施,完成即标记。

---

## 1. 现状审计结论(2026-08-17 全量审计)

### 1.1 必须保留的既有优势(重构红线,不得回退)

| 项 | 说明 |
|---|---|
| 注入侧 ↔ UI 零耦合 | `hook.*` 包无任何 UI import,双框架 Platform 适配器抽象正确 |
| 记录可靠性链路 | WAL(seq 前缀 + 双游标 + fsync 检查点)→ at-least-once → 宿主唯一索引幂等吸收 → ACK 裁剪 |
| Room 迁移纪律 | schema 导出、手写迁移、`fallbackToDestructiveMigration(false)` |
| 大数据投影优化 | `observePagedRows` SQL 侧 substr 预览、邻居查询仅投影 id |
| 测试资产 | 25+ 单测 + 10+ 插桩,协议/服务器/DAO/Spool/Sink 均有覆盖 |

### 1.2 审计问题清单(严重级别:严重 / 中等 / 轻微)

**严重**

| # | 位置 | 问题 |
|---|---|---|
| S1 | `ConfigServer.kt:203` + `LogRepository.kt:123-132` | ACK 会话聚合失效:每记录新建 lambda 作 HashMap 键,批量 ACK 退化为逐条,协议开销 O(批次)翻倍 |
| S2 | `AppsViewModel.kt:91-146` | 应用列表全量重排:捕获期每 200ms 落库 → counts 变化 → 全量过滤 + Collator 排序,主线程抖动 |
| S3 | `AppsScreen.kt:475-479` | countText 本地化语义错误:en 资源 "K+" 却以 10⁴ 为除数(20000→"2K+"),en `apps_count_qian` 为裸 "+" |

**中等**

| # | 位置 | 问题 |
|---|---|---|
| M1 | `WireCodec.kt:78-87`、`RecordTypes.kt` | 协议/存储/DAO/UI 全链绑定 `CryptoRecord`;`recordType` 预留零消费,`recordOf` 无类型分发,非 crypto 记录被静默丢弃 |
| M2 | `ConfigServer.kt:38-39`、`ModevApp.kt:125` | `onRecord` 全局可变回调 + lazy 装配副作用,装配顺序被扰动即静默丢记录 |
| M3 | `AppsViewModel.kt:95,214`、`DiagnosticsViewModel.kt:23` | 3 个 ViewModel 直接消费 `ConfigServer` 具体类(连接状态/服务器是 UI 依赖的实现细节) |
| M4 | `CaptureSettingsViewModel.kt:18` 等 4 处 | 4 个 ViewModel 持有整个 `AppContainer`,无法单测、依赖不可注入 |
| M5 | `LogRepository.kt` 全文件 | 五合一:摄入/背压/批处理/容量治理/统计 + 三份 47 字段手工映射(toEntity/toModel/toRowModel) |
| M6 | `LogRepository.kt:320-321` | `enumOf` 未知枚举静默回退首个常量 → 数据误标为 MESSAGE_DIGEST(比丢弃更危险) |
| M7 | `SettingsRepository.kt:86,88` vs `RemotePrefsBridge.kt:20,46` | 配置键双源字面量(`switch_global`/`switch_`),漂移即双端失联 |
| M8 | `HookSwitch.kt:24,28,30` + `BytesView.kt:49,52` + `RecordSpool.kt:243` | 字节上限魔法数跨 4 文件重复,改一处他处失配 |
| M9 | `CryptoRecord.kt:43` | `direction` 魔法数字(1-4)仅靠 KDoc,UI 双处各自映射 |
| M10 | `CryptoRecordDao.kt:45-109` + `LogRepository.kt:218-314` | 两份 10 字段 LIKE SQL 重复;三份手写映射,模型演进需多处同步 |
| M11 | `ConfigServer.kt:63-65`、`LogRepository.kt:57-59` | `bumpStats` 非原子读改写,并发丢统计 |
| M12 | `LogRepository.kt:64-65` | 背压 `acquire()` 无超时(客户端线程可无限挂起),`trySend` 结果忽略 |
| M13 | `CryptoRecordDao.kt:161-180` | trim 用 NOT IN + 全表排序,无 `timestamp` 单列索引 |
| M14 | `RecordSink.kt:88-93` | seq 持久化为 hook 线程文件 I/O(每 100 条/1s),热路径磁盘写 |
| M15 | `HookManager.kt:27-33` | object 单例 + `lateinit` 成员,12 Hooker 硬编码装配列表,不可测 |
| M16 | `FrameworkBridge.kt:32-34` | `ensureRegistered` check-then-act 非原子 |
| M17 | `ScopeRepository.kt:118,124,137,154` | domain 层硬编码中文错误文案直接进 Snackbar,破坏 i18n |
| M18 | `DiagnosticsScreen.kt:123,156-167` | 传输层细节泄漏进 UI(`WireProtocol.PORT`、`ChannelStats`) |
| M19 | `SettingsScreen.kt:186-189,290-297` | `when(CapabilityKind)` 三处硬编码,能力入口半解耦;`Capability.enabled` 零消费 |
| M20 | `LogRepository.kt:358,354` 等 | 死代码:`PipelineStats.dropped` 永不赋值、`SettingsStepperItem` 无调用、`AppRow.enabled` 无读取 |

**轻微**(合并归类,实施时顺带处理)

| # | 位置 | 问题 |
|---|---|---|
| L1 | 全工程 | 5 个 Gson 实例,HTML 转义配置不一致(仅 WireCodec 关闭) |
| L2 | `WireCodec.kt:75-76` | `isHelloCompatible` 被用于校验 config 推送,名称与语义不符(改名 `isProtocolCompatible`) |
| L3 | `LogRepository.kt:284,289` | paramsJson/stackJson 反序列化失败静默降级无日志 |
| L4 | `ModevNav.kt:50` | `as ModevApp` 硬转型(预览/测试替身崩溃) |
| L5 | `MainActivity.kt:76-90` vs `Theme.kt:35-40` | 深色判定双实现,须人工同步 |
| L6 | `RecordDetailScreen.kt:112-115` | 4 个平行 StateFlow 独立收集,整屏重组 |
| L7 | `AppListSettings.kt` 位于 `ui.apps` 包 | 持久化基础设施分层倒挂 |
| L8 | `RecordDetailScreen.kt:679-710` | `toJson` 序列化置于 UI 文件 |
| L9 | `RecordDetailScreen.kt:798` | `detailTimeFmt` 每次 new SimpleDateFormat |
| L10 | `SettingsScreen.kt:513-516` | `selectedMarkColor` 首参未用 |

---

## 2. 目标架构与架构决策(ADR)

### 2.1 目标分层(单向依赖,禁止反向)

```
┌─────────────────────────────────────────────────────────┐
│ ui/          界面:Compose 屏幕,只依赖 domain 接口与展示注册表 │
├─────────────────────────────────────────────────────────┤
│ domain/      能力(Capability)+ 作用域(Scope)+ 端口抽象        │
├─────────────────────────────────────────────────────────┤
│ data/        通用记录存储 + 传输服务端口 + 配置仓库 + 类型化映射  │
├─────────────────────────────────────────────────────────┤
│ core/        纯 Kotlin:通用模型/协议编解码/工具(双端共用,可单测) │
├─────────────────────────────────────────────────────────┤
│ hook/        注入进程:采集器(Probe)+ 引擎 + 通道 + WAL 出口      │
│              (对 data/ui 完全不可见,仅经 core 协议对向通信)     │
└─────────────────────────────────────────────────────────┘
```

### ADR-1 通用记录信封(多监听类型的基石)✅ 已决策

- 新增 `core/codec/WireRecord`:`{ seq, recordType, payload(JsonObject) }` 通用线协议信封;`seq` 提升至信封层,ACK 与类型解耦;
- 协议 `WireProtocol.VERSION 3 → 4`(破坏性,用户确认允许);
- `CryptoRecord` 保持 24 字段完整模型(线协议 v4 载荷形状不变),其编解码收敛为 `CryptoRecordCodec`;
- 新增 `RecordCodecRegistry`(decode 注册表):`recordType → decode(payload)`;未知类型计数丢弃,不再静默吞并;
- `ConfigServer.onRecord` 签名泛型化:`(WireRecord, (Long) -> Unit) -> Unit`,不再依赖 `CryptoRecord`;
- 宿主摄入管线(LogRepository)按注册表路由,算法记录解码失败计入 `PipelineStats.dropped`(修复死统计字段)。

### ADR-2 ACK 聚合修复 ✅ 已决策

- `ClientSession` 持有**单一身份稳定**的 ACK 回调实例(而非每记录新建 lambda);
- `LogRepository.ackBatch` 以回调身份聚合取最大 seq,每批每会话只回一个游标(O(批次));
- 聚合逻辑抽为纯函数 `AckAggregator.groupMax`,可单测。

### ADR-3 采集器抽象(Probe)✅ 已决策

- 新增 `hook/Probe` 接口:`{ recordType, install(ProbeContext) }`;`ProbeContext` 承载 engine/config/tracker/sink/packageName;
- 12 个 Hooker 收敛为 `AlgorithmProbe`(recordType=crypto),装配清单移出 HookManager;
- `HookManager` object 单例 → `HookInstaller` 构造注入类(platform/packageName/processName/remotePrefsProvider/probes),入口(ModevModule/LegacyModuleEntry)各自实例化,消除进程级可变单例;
- 新增监听类型 = 新 Probe + 注册进 installer,引擎/通道/WAL 零改动。

### ADR-4 存储与映射泛化 ✅ 已决策

- DAO 全部查询增加 `recordType: String?` 过滤参数(null = 不过滤),宿主管线显式传 `RecordTypes.CRYPTO`;
- 新增 `timestamp` 单列索引(trim 全表排序优化),DB v4 → v5 手写迁移;
- 三份手写映射抽取为 `data/mapping/CryptoRecordMappers.kt`(internal 扩展函数,可单测);
- `enumOf` 行为修正:未知枚举 → 记录丢弃 + 统计计数(禁误标)。

### ADR-5 常量单一事实源 ✅ 已决策

- `core/model/RecordLimits`:字节上限(默认单字段 1MB、hex 2×、spool 单行 4MB);
- `core/model/ConfigKeys`:`switch_global` / `switch_` 前缀,`SettingsRepository` 与 `RemotePrefsBridge` 同源引用;
- `core/model/CipherDirection`:方向模式枚举(1-4),UI 标签映射消费枚举;
- 统一 `core/codec/Json.gson`(disableHtmlEscaping)全工程唯一 Gson 实例。

### ADR-6 依赖收窄与端口化 ✅ 已决策

- `ConfigServer` 保持单类但对外只暴露领域语义:记录接收(`onRecord: (WireRecord, ack)`)、连接状态流、通道统计、配置广播;UI/仓库不得再依赖其内部;
- 4 个 container 型 ViewModel 收窄为窄依赖;`SettingsViewModel.capabilities` 响应式化;
- 能力展示注册表 `ui/settings/CapabilityUiRegistry`(icon/title/route),消灭 `when(CapabilityKind)`;
- domain 错误码化:`ScopeChangeResult.Failed` 携带 `FailureReason` 枚举,UI 映射文案;
- `AppListSettings` 移入 `data/repo`(分层修正);
- 传输常量(`WireProtocol.PORT`)经 ViewModel 暴露,UI 不再直接引用。

### ADR-7 热路径与可靠性加固 ✅ 已决策

- seq 持久化移出 hook 线程(RecordSink 工作线程统一检查点);
- `bumpStats` 改为 `MutableStateFlow.update`(CAS 原子);
- 背压 `acquire` 加超时护栏(超时丢弃并计数,绝不无限挂起客户端线程);
- `FrameworkBridge.ensureRegistered` 改 `AtomicBoolean`。

---

## 3. 分阶段任务书

### P1 基础设施:协议 v4 与通用信封(ADR-1/2/5)

| # | 任务 | 验收标准 | 状态 |
|---|---|---|---|
| P1-1 | 新增 `RecordLimits`/`ConfigKeys`/`CipherDirection`/`Json`(core/model + core/codec),替换全工程魔法数与 Gson 实例 | 编译通过;单测覆盖方向枚举映射与常量一致性 | ✅ 2026-08-17 |
| P1-2 | 新增 `WireRecord` + `RecordCodecRegistry` + `CryptoRecordCodec`;`WireCodec` 泛型化(record 带信封级 seq),VERSION=4,`isHelloCompatible` 更名 | `WireCodecTest`/`WireCodecAuthTest` 适配全绿;新增未知类型丢弃用例 | ✅ 2026-08-17 |
| P1-3 | `ConfigServer.onRecord` 泛型化 + 会话级稳定 ACK 回调(修复 S1);`bumpStats` 原子化(M11) | `ConfigServerTest` 全绿;新增单会话多记录仅 1 条 ACK 的用例 | ✅ 2026-08-17 |
| P1-4 | `LogRepository` 摄入泛型化:注册表路由、ACK 聚合(AckAggregator 纯函数)、`enumOf` 修正(M6)、解码失败入 `dropped` 统计(M5/M20)、paramsJson/stackJson 失败日志(L3) | 单测:聚合/枚举容错/mapper;RecordDetailViewModelTest 全绿 | ✅ 2026-08-17 |
| P1-5 | `ModevApp` 装配显式化:`LogRepository.start()` 显式接线 onRecord,消除 lazy 副作用(M2) | 无行为回退;SettingsRepositoryTest/ConfigServerTest 全绿 | ✅ 2026-08-17 |

**P1 实施备注(2026-08-17)**:
- 常量收敛:魔法数全部改引 `RecordLimits`(BytesView/HookSwitch/RecordSpool),配置键改引 `ConfigKeys`(SettingsRepository/RemotePrefsBridge 双端同源),Gson 全工程统一 `Json.gson`(ModevApp/HookManager/WebViewEvent/WireCodec/LogRepository),`CipherDirection.fromMode` 供展示层消费。
- 协议 v4:信封级 `seq` 与 `recordType` 由载荷承载(与模型字段同值,编码侧强制统一);`recordOf` 缺失任一即丢弃;未知 recordType 信封完整保留由注册表裁决;`isHelloCompatible` → `isProtocolCompatible`(ConfigChannel 同步)。
- ACK 修复(S1):`ClientSession.ack` 单例回调身份稳定,`AckAggregator.maxSeqPerSession` 纯函数按身份聚合;新增 ConfigServerTest「单会话多记录回调身份唯一」与「缺 recordType 畸形丢弃」用例;未解码记录仍回执(防 WAL 永久重放),落库失败仅回执未解码部分。
- 摄入泛型化:LogRepository 消费 `WireRecord` + 注册表解码,`pagedRecords`/`observeById`/邻居/计数/包列表全部显式 `RecordTypes.CRYPTO`;未知枚举/畸形 → 计数丢弃不误标。
- 本阶段顺带前移 P2-1(DAO 全查询 `recordType` 过滤参数,null=不过滤)+ P2-3(三份映射抽取 `data/mapping/CryptoRecordMappers.kt` 为 internal,`toModelOrNull`)+ P2-4 背压超时护栏(10s 窗口内阻塞,超时丢弃计数),避免二次返工;`LogRepository.observe` 死代码删除。
- 测试:203 例双变体全绿(新增 RecordCodecRegistryTest 4 例、AckAggregatorTest 3 例、DAO 类型过滤 3 例、ConfigServer 新增 2 例、WireCodec/CryptoRecordCodec 边界 4 例)。

### P2 数据层泛化(ADR-4)

| # | 任务 | 验收标准 | 状态 |
|---|---|---|---|
| P2-1 | DAO 全查询加 `recordType` 过滤参数;宿主管线显式传 `RecordTypes.CRYPTO` | `CryptoRecordDaoTest` 适配全绿;新增按类型过滤用例 | ✅ 2026-08-17(前移至 P1) |
| P2-2 | `timestamp` 单列索引 + DB v4→v5 迁移 | schema 5.json 导出;迁移后既有数据完整 | ✅ 2026-08-17 |
| P2-3 | 三份映射抽取 `data/mapping/CryptoRecordMappers.kt`(internal),`toModel` 改 `toModelOrNull` | mapper 单测覆盖全字段往返 + 未知枚举 | ✅ 2026-08-17(前移至 P1) |
| P2-4 | 背压超时护栏(M12);死代码清理(M20:`SettingsStepperItem`/`AppRow.enabled`/无用 DAO 查询,先 grep 确认) | 构建全绿;无死引用 | ✅ 2026-08-17(护栏前移至 P1) |

**P2 实施备注(2026-08-17)**:
- v4→v5 迁移:仅新增 `index_crypto_records_timestamp` 单列索引(纯增量,无数据变更);迁移测试构造严格复刻的 v4 库(含 v2/v3/v4 全部列与既有索引),迁移后断言存量数据完整 + 索引就位;`composite indexes exist` 用例并入新索引断言。
- 死代码清理:DAO `observe`(仓库侧已删,列表/详情/邻居全走投影与按需物化)、`SettingsStepperItem`(无调用方)、`AppRow.enabled`(写入未消费,AppInfo.enabled 为真实消费方)全部移除;AppSortTest 构造参数同步。
- 背压护栏:待入库槽位阻塞窗口 10s(超时丢弃并计数,绝不无限挂起客户端线程),写失败路径仍保留 WAL 重放语义。

### P3 算法监听专业化(ADR-3/7)

| # | 任务 | 验收标准 | 状态 |
|---|---|---|---|
| P3-1 | `Probe`/`ProbeContext`/`AlgorithmProbe` + `HookInstaller` 替换 `HookManager` object;双入口改造 | 双变体编译通过;无全局 lateinit 残留 | ✅ 2026-08-17 |
| P3-2 | seq 持久化移出 hook 线程(M14);`FrameworkBridge` 原子注册(M16) | 单元测试全绿;代码审查无 hook 线程文件 I/O | ✅ 2026-08-17 |
| P3-3 | 采集热路径审计:emit 单次取配置快照、堆栈采集成本说明、Base64/WebView 低频不优化 | 文档备注;无新增分配热点 | ✅ 2026-08-17 |

**P3 实施备注(2026-08-17)**:
- ADR-3 落地:`Probe { recordType; install(ProbeContext) }` 契约;`AlgorithmProbe` 为唯一装配清单(12 类 Hooker,新增能力=追加 Probe);`HookInstaller` 取代 `HookManager` object——全部状态收敛为实例字段,channel/sink 循环依赖经 `by lazy` 解析,幂等由 `@Synchronized install()` 保证;**`HookManager.kt` 已删除**,`hook` 包内无任何 lateinit 残留。
- 12 个 Hooker 构造签名统一收敛为 `(context: ProbeContext)`,`BaseHooker` 自 `context` 派发 engine/config/tracker/sink/packageName;双入口(`ModevModule`/`LegacyModuleEntry`)改为构造 `HookInstaller(...).install()`。
- M14:`RecordSink.emit` 仅写 `@Volatile seqCheckpoint`(Hook 线程零 I/O),工作线程每轮循环落盘(条数/时间双窗口);修复无 spool 直发分支 `continue` 跳过持久化的缺陷(重构为 if/else)。
- M16:`FrameworkBridge.ensureRegistered` 改 `AtomicBoolean.compareAndSet` 原子注册,消除 check-then-act 竞态。
- P3-3 热路径:emit 只读一次配置快照(开关判断与组装共用,避免双读不一致);堆栈采集仅 `logStack` 开启时执行且成本与 `maxStackDepth` 线性相关(注释说明);Base64/WebView 为低频路径(默认关闭),不引入特化优化;装配期新增分配均为一次性(每进程一次),运行时零新增。

### P4 UI 解耦(ADR-6 + 修复)

| # | 任务 | 验收标准 | 状态 |
|---|---|---|---|
| P4-1 | countText 本地化修复(S3):纯函数抽取 + en/zh 资源对齐 + 单测 | 20000→"20K+"/"2万+" 用例通过 | ✅ 2026-08-17 |
| P4-2 | AppsViewModel 流水线拆分(S2):排序移出热 combine,置顶用稳定分区(O(n) 无比较器) | 计数变化不触发排序(代码审查 + 测试) | ✅ 2026-08-17 |
| P4-3 | RecordDetailScreen 4 个 StateFlow 收敛为单一 `DetailUiState`(L6) | RecordDetailViewModelTest 全绿;`detailTimeFmt` 缓存(L9) | ✅ 2026-08-17 |
| P4-4 | 4 个 container 型 ViewModel 收窄(M4);工厂同步 | 单测可注入;构建全绿 | ✅ 2026-08-17 |
| P4-5 | `ScopeChangeResult.Failed` 错误码化(M17)+ en/zh 文案 | Snackbar 文案 i18n;scope 测试适配 | ✅ 2026-08-17 |
| P4-6 | `AppListSettings` 移入 `data/repo`(L7);`CapabilityUiRegistry` 消灭 when(CapabilityKind)(M19);`Capability.enabled` 消费 + capabilities 响应式 | 设置页能力区显示启用态;导航/图标注册驱动 | ✅ 2026-08-17 |
| P4-7 | `DiagnosticsScreen` 传输常量经 VM 暴露(M18);`ModevNav` 硬转型防御(L4);`toJson` 移入映射层(L8);`selectedMarkColor` 清理(L10) | 构建全绿;UI 无 `core.codec.WireProtocol` import | ✅ 2026-08-17 |

**P4 实施备注:**
- P4-1:`AppCountFormat.compact(count, formatWan, formatQian)` 纯函数只负责档位分派,缩放由 UI 按 `LocaleManager.currentLanguageTag` 决定(zh 万=÷10000,en 恒 K=÷1000);`AppCountFormatTest` 4 例覆盖档位/边界/兜底。过程中发现旧实现 en 20000→"2K+" 语义错误,随本任务一并修正为 "20K+"。
- P4-2:流水线拆为 `CatalogBase × SortPrefs`(stable)→ `sortedRows`(过滤+排序+`AppSort.pinScoped` 稳定分区,不依赖计数/连接态)→ `uiState` 投影(combine counts×connected×prefs×scope + `distinctUntilChanged`);计数变化只重投影、不重排;`AppSort` 泛型化 `nameComparator<T>(locale, label, key)`;`AppRow.enabled` 死字段(P2 清单)顺手清除。
- P4-3:`DetailUiState(record/loaded/canOlder/canNewer, failed)` 替代 4 个 StateFlow;屏内单次 `collectAsStateWithLifecycle`;`MetadataBlock` 内 `remember { detailTimeFmt() }`(L9);`toJson()/toJsonObject` 迁入 `data/mapping/CryptoRecordMappers.kt`(L8),屏内显式 import。
- P4-4:SettingsViewModel(capabilityRegistry)、CaptureSettingsViewModel(settings)、DataSettingsViewModel(logs)、DiagnosticsViewModel(scopeRepository, server, logs);工厂直传 `container.X`,移除整体 container 依赖(M4)。
- P4-5:`ScopeChangeResult.Failed` 5 个 data object(FrameworkNotReady/ServiceNotConnected/UserRejected/Timeout/RemoveFailed),domain 零文案;`apps_scope_failed` 拆为 5 条 en/zh 串;UI 层 `scopeFailedMessage` 映射(AppsViewModel + AppRecordsViewModel.removeScope),`ScopeRepositoryTest` 适配 UserRejected。
- P4-6:`AppListSettings` 移至 `data/repo`(L7,三处 import 同步);`CapabilityUiRegistry(kind/icon/titleRes/onClick)` 消灭 when(CapabilityKind)(M19),`ModevNav` 内 `remember { register(CRYPTO_CAPTURE, Tune, capture_settings_title) { navigateGuarded(CAPTURE_SETTINGS) } }`;SettingsScreen 消费 `Capability.enabled`(已启用/未启用 subtitle),`settings_capability_enabled/disabled` 双语串。
- P4-7:DiagnosticsViewModel 暴露 `port = WireProtocol.PORT`(M18),DiagnosticsScreen 去 WireProtocol import;`ModevNav` 入口 `as? com.lioisme.modev.ModevApp` null 即 return(L4);`selectedMarkColor` 删除(L10)。
- 期间事故:一次 PowerShell 批量替换按 ANSI 读 UTF-8 源文件,乱码写回 `ModevApp/AppsViewModel/AppsScreen` 三文件 → 编译失败;`git checkout` 恢复后以 edit 工具重做全部改动(P4-1/4-2/4-5/4-6 相关),双变体 210 例全绿后收尾(教训:中文注释文件禁用 PowerShell 文本改写)。

### P5 质量门禁(验收)

| # | 任务 | 验收标准 | 状态 |
|---|---|---|---|
| P5-1 | 包边界架构测试(源码 import 扫描):ui 不依赖 hook/data.server 实现、domain 不依赖 android 文案 | 架构规则用例全绿 | ✅ 2026-08-17 |
| P5-2 | 新增单测:AckAggregator/AppCountFormat/CryptoRecordMappers/RecordCodecRegistry/CipherDirection/AppSort 分区 | 全绿 | ✅ 2026-08-17 |
| P5-3 | `testModernDebugUnitTest` + `testLegacyDebugUnitTest` + `lintModernDebug` + `lintLegacyDebug` 全绿 | 零 error | ✅ 2026-08-17 |
| P5-4 | 文档收尾:本表逐项标记 + 备注;风险册更新 | 文档自洽 | ✅ 2026-08-17 |

**P5 实施备注:**
- P5-1:`PackageBoundaryTest`(architecture 包)扫描 `src/main/java` 全部 .kt 的 package/import:R1 = ui 子树禁 import `hook`/`data.server`;R2 = domain 子树禁 import `android.*`/`ui`;另禁通配 import。扫描中发现并修复真实耦合:AppsViewModel(两处)/DiagnosticsViewModel 直接依赖 `data.server.ConfigServer` → 新增 domain 抽象 `ConnectionMonitor`(仅暴露 `connectedPackagesFlow`),`ConfigServer` 实现之,UI 参数类型收敛为接口,工厂不变(容器仍传 ConfigServer)。domain 无 android 依赖原样满足。源码目录缺 package 行的防御:BOM 前缀剥离。
- P5-2:补 `CryptoRecordMappersTest`(全字段 round-trip/未知枚举拒标/行投影/JSON 导出)与 `CipherDirectionTest`(模式值/fromMode 未知值);AckAggregator/RecordCodecRegistry/AppCountFormat/AppSort 分区此前已具备。关键发现:纯 JVM 单测下 `isReturnDefaultValues = true` 使 android.jar 桩 org.json 的 put() 返回 null → toJson 无法直测;改为 Robolectric(`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34])`)后 org.json 为真实实现,用例全绿(实证二分测试确认,随后删除临时用例)。
- P5-3:双变体 224 例全绿(modern 221 + 架构 3);`lintModernDebug`/`lintLegacyDebug` 均 0 error、34 warning(全为既有项:版本更新提示、KTX 建议、PrivateApi[注入侧固有]、PluralsCandidate 等,不阻断)。
- P5-4:本节 + §5 标记;风险册追加「源文件编码事故」「org.json 单测桩化」两条(见 §4)。

---

## 4. 风险册

| 风险 | 影响 | 缓解 |
|---|---|---|
| 协议 v4 破坏性变更 | 双端同 APK 升级,旧版注入进程握手即被拒 | 版本强校验已有;升级步骤不变;README 提示覆盖安装 |
| ACK 聚合修复引入回归 | ACK 漏发 → 注入侧 WAL 永久滞留 | ConfigServerTest 新增单会话多记录单 ACK 用例;幂等吸收兜底 |
| 枚举容错改为丢弃 | 未来版本记录在旧版被丢 | 计数暴露(dropped 统计),旧版本就不支持新类型展示 |
| Room v5 迁移 | 迁移 SQL 错误 → fallback 拒绝打开 | 手写迁移 + schema 导出 + DAO 测试在迁移后库上跑 |
| 采集器重构(Probe) | 装配遗漏导致部分类别不捕获 | 12 Hooker 全量清单保持;双变体编译 + 实机矩阵 |
| 源文件编码事故(P4) | PowerShell ANSI 读 UTF-8 源文件乱码写回 → 编译失败、改动丢失 | 中文注释文件禁用 PowerShell 文本改写,一律 edit/write 工具;git checkout 恢复后重做并复盘 |
| org.json 单测桩化(P5) | `isReturnDefaultValues` 使 android.jar 桩 org.json 返回 null,toJson 断言失真 | org.json 相关单测用 Robolectric(sdk 34)跑真实实现;其余纯 JVM 用例不受影响 |

---

## 5. 实施记录(自驱动执行,完成即标记)

> 按 P1 → P2 → P3 → P4 → P5 顺序推进;每阶段结束跑 `testModernDebugUnitTest` + `testLegacyDebugUnitTest`,全部通过才进入下一阶段。

| 阶段 | 状态 | 备注 |
|---|---|---|
| P1 基础设施 | ✅ 已完成 | 见 §3 P1 备注;双变体 203 例全绿 |
| P2 数据层 | ✅ 已完成 | 见 §3 P2 备注;schema 5.json 导出;双变体全绿 |
| P3 算法监听专业化 | ✅ 已完成 | 见 §3 P3 备注;双变体全绿 |
| P4 UI 解耦 | ✅ 已完成 | 见 §3 P4 备注;双变体 210 例全绿;期间编码事故已修复(见备注) |
| P5 质量门禁 | ✅ 已完成 | 见 §3 P5 备注;架构 3 例 + 双变体 224 例全绿;双变体 lint 0 error |
