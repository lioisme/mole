# Modev 记录可靠性 · 作用域同步 · 一键启动 任务文档

> 版本:1.0 · 2026-08-17
> 目标:① 捕获记录在**任何场景**下不丢失、不消失;② Modev 勾选态与框架(LSPosed/Vector)作用域**双向实时一致**;③ 一键启动被作用域应用。
> 状态图例:`⬜ 待办` → `🔄 进行中` → `✅ 已完成`
> 文档基线:commit `77e8670` + 工作区全部未提交改动(全量审计时点)

---

## 1. 产品意图(新增需求)

| # | 需求 | 含义 |
|---|---|---|
| R4 | 捕获记录永续 | 无论宿主被杀/重启、框架握手失败、legacy 无服务、分离进程、高吞吐、断连、进程崩溃,捕获记录都必须最终出现在对应应用的记录列表中,不因任何场景清空/丢失 |
| R5 | 作用域双向一致 | Modev 勾选 = 框架作用域变更;框架内(管理器)取消勾选 → 回到 Modev 勾选态必须同步消失,界面与真实状态恒一致 |
| R6 | 一键启动 | 应用列表/应用头部提供「启动」入口,一键拉起被作用域(及全部可启动)应用 |

## 2. 关键架构决策(ADR)

| # | 决策 | 理由 |
|---|---|---|
| ADR-R4-1 | **WAL 化写入**:注入侧所有记录「先落盘、再发送」(`RecordSpool` 成为常驻写前日志),宿主 ACK 后删行 | 进程任意时点被杀不丢(盘上恒有);比「断连才落盘」的现有设计消除内存队列窗口 |
| ADR-R4-2 | **at-least-once + 幂等**:协议 v3 记录携带进程级 `seq`,宿主落库后批量回 `ACK(seq)`;重复投递由 DB 唯一索引 `(packageName, processName, seq)` 幂等吸收 | 标准可靠投递语义;宿主「收到但未落库/崩溃于落库前」不再丢记录 |
| ADR-R4-3 | **null-token 放行落地**:`requireToken` 非空且 hello 无 token → 放行并告警计数(信任 scope 强制注入);非空不匹配仍拒绝 | 修复 REFACTORING.md M4-4 声明但未实现的行为;Vector 系框架注入进程无 binder,现状下**握手被永久拒绝、记录恒空** |
| ADR-R4-4 | **spool 引擎级能力**:不再以 `remotePrefsProvider` 是否存在决定 spool 启用,legacy 同样启用(路径仍取注入进程 filesDir,按进程名分文件) | 消除 legacy「宿主不可达即全丢」的结构性缺口;同时修复多进程共享同一 spool 文件的并发写损坏隐患 |
| ADR-R4-5 | **主包名归并**:`packageName` = `processName.substringBefore(':')`,`processName` 保留原值(区分分离进程) | 分离进程(`pkg:sub`)记录现被归档到 `pkg:sub` 键,应用页按 `pkg` 查询 → 列表「空」 |
| ADR-R4-6 | **宿主侧背压替代丢弃**:`pending` Channel 满时阻塞回调线程(经 socket 反压到注入侧 spool),不丢记录;`insertAll` 失败重试 3 次,仍失败不 ACK(注入侧保留待重放) | 关闭宿主侧唯一无补偿丢弃点 |
| ADR-R4-7 | **fsync 批量化**:append 仅 flush,每 [FSYNC_EVERY] 条或 [FSYNC_INTERVAL_MS] checkpoint 一次 `fd.sync()` | 保留崩溃持久性上限 ≤ 一批;消除逐条 fsync 的吞吐瓶颈 |
| ADR-R4-8 | **drain 不再重发在途行**:spool 维护 `ackedSeq`(宿主确认)与 `sentSeq`(已发送)双游标;断连时 `sentSeq` 回退到 `ackedSeq` 允许重放 | 防止「每收到一条记录就重发全部未确认行」的重复轰炸 |
| ADR-R5-1 | **生命周期刷新 + 前台轮询**:`ON_RESUME` 立即 `ScopeRepository.refresh()`;前台期间每 5s 轮询一次(只读 `getScope`) | 框架管理器是独立进程,101 API 无作用域变更回调;轮询+生命周期是唯一可靠信号源 |
| ADR-R5-2 | 刷新与 `toggle` 串行化(共用 `mutationMutex`),轮询不打断变更结果展示 | 防「用户勾选 → 轮询读到旧值 → 勾选态闪跳」竞态 |
| ADR-R6-1 | 启动入口 = `PackageManager.getLaunchIntentForPackage`(缓存于 `AppInfo.launchable`),`FLAG_ACTIVITY_NEW_TASK` 拉起;不可启动/被禁用应用禁用按钮 | 原生 API,零权限增量(`QUERY_ALL_PACKAGES` 已具备) |

## 3. 现状审计(基线与根因)

### 3.1 记录丢失/消失场景(需求 1) — 逐条证据

| 级别 | # | 场景 | 根因(证据) |
|---|---|---|---|
| **高** | L1 | **Vector 系框架捕获恒空** | 注入进程无 XposedService binder(`ModevModule.kt:25-33` 双路径均 null)→ hello 无 token(`ConfigChannel.kt:118`)→ 宿主已生成 `requireToken`(`ModevApp.kt:108-126`)→ `ConfigServer.onHello` 对 `token != required` 一律拒绝(`ConfigServer.kt:156-165`)**无 null 放行**。REFACTORING.md M4-4 声称的「null token 放行」未落地 → 全部记录滞留 spool 永不到宿主 |
| **高** | L2 | **legacy 变体宿主不可达即全丢** | `LegacyModuleEntry.kt:20` 不传 `remotePrefsProvider` → `HookManager.kt:59-67` spoolProvider 恒 null → `RecordSink.kt:71-72` 断连/发送失败即丢弃。开机时序、宿主被杀、端口冲突期间 legacy 无任何持久化 |
| **高** | L3 | **分离进程记录「消失」** | `ModevModule.kt:35-39`/`HookManager.kt:42-44` 以 `processName` 当 `packageName`;`BaseHooker.kt:91` 记录携带 `pkg:sub` → DB 归档键错误,应用页按主包查询恒空 |
| **高** | L4 | **宿主「收到未落库」即丢** | `RecordSpool.drainFile` 发送成功即整文件删除(`RecordSpool.kt:108-117`);宿主 `insertAll` 失败(`LogRepository.kt:85-89` writeFailures)或收到-落库窗口崩溃 → 记录永久丢失,无 ACK/重放语义 |
| 中 | L5 | **宿主侧无补偿丢弃** | `pending` Channel(8192)溢出即丢(`LogRepository.kt:56-59`);`insertAll` 失败不重试不保留 |
| 中 | L6 | **线程风暴** | `ConfigChannel.sendRecordJson` **每次发送新建 watchdog 线程**(`ConfigChannel.kt:65-66,85-96`);万级 MESSAGE_DIGEST 场景线程爆炸 → 进程卡死/被杀 → 内存队列(`RecordSink.kt:47-57`,2048 溢出丢最旧)记录丢失 |
| 中 | L7 | **spool 回放崩溃窗口** | `drainFile` 失败路径 `f.delete(); tmp.renameTo(f)`(`RecordSpool.kt:111-114`):tmp 未 fsync、delete→rename 间崩溃或 renameTo 失败 → 数据丢失 |
| 中 | L8 | **多进程共享 spool 文件** | 同包多进程各自创建 `RecordSpool(filesDir/modev_spool)`(`HookManager.kt:59-61`),并发 append/drain 同一文件 → 行交错损坏 |
| 低 | L9 | 容量治理删最旧 | `trim(20000)`/`trimPackage(5000)`(`LogRepository.kt:98-121`,`CryptoRecordDao.kt:118-137`)——万级场景用户可见记录被删;属设计行为,文档化并保留 |

### 3.2 作用域勾选不同步(需求 2)

| 级别 | # | 根因(证据) |
|---|---|---|
| **高** | S1 | `ScopeRepository.state` 仅由 `serviceFlow`(bind/die)与 `refreshTrigger` 驱动(`ScopeRepository.kt:38-54`);`refresh()` 注释自称「服务连接 / **onResume** / 变更后调用」(`:56`)但**全库无任何 onResume/Lifecycle 接线**(grep 证实),仅下拉刷新(`AppsViewModel.kt:147-154`)与 toggle 内部调用 → 框架管理器外部取消勾选后,Modev 进程存活、serviceFlow 不变 → 勾选态残留,界面与真实状态不一致 |
| 中 | S2 | `requestScope` 30s 超时(`ScopeRepository.kt:77-89`)或框架回调丢失后,仅靠手动下拉才可能同步;无自动补偿 |
| 低 | S3 | `AppsViewModel.refresh()` 中 `refreshing` 标志在 `scopeRepository.refresh()`(异步触发)完成前复位,下拉动画提前结束(轻微 UX) |

### 3.3 一键启动(需求 3)

新功能,无现存实现。现状 `AppInfo` 仅含 packageName/label/system/enabled(`AppCatalogRepository.kt:19-25`),无 launchable 判定与入口。

## 4. 目标架构

### 4.1 记录管线(WAL + ACK,双变体一致)

```
注入进程                                  宿主进程
Hooker.emit ──► RecordSink
                 ├─ spool.append(seq, json)    [WAL: 先落盘;fsync 每 100 条/500ms]
                 └─ drain: 仅发送 seq > sentSeq ──TCP──► ConfigServer.onRecord
                                                          └─► LogRepository 批量落库(≤32条/200ms)
                 ◄── ACK(最高已落库 seq) ──────────────────┘ 成功→ ACK;失败→重试3次→不 ACK
                 spool 删除 ≤ ackedSeq 的行
 断连: sentSeq 回退 ackedSeq;重连后 spool 重放未确认行 → 重复行由 UNIQUE 索引幂等吸收
```

### 4.2 协议 v3(破坏性,双端同 APK 升级)

- `VERSION` 2 → 3;record payload 增 `seq`(`CryptoRecord.seq`,注入侧进程级单调);
- 新增宿主 → 注入 `TYPE_ACK` `{seq}`(批量确认:seq 及之前已持久化);
- hello 的 `packageName` = 主包名(归并后),`processName` 保留原值。

### 4.3 DB v2 → v3

- `crypto_records` 增列 `seq INTEGER`(可空,历史行 null);
- 新增唯一索引 `idx_crypto_records_pkg_proc_seq(packageName, processName, seq)`;
- `@Insert(onConflict = REPLACE)` 幂等重放(重复行整体替换,内容一致)。

### 4.4 作用域同步(增量)

```
现有: serviceFlow 变化 / toggle 后 refresh
新增: ON_RESUME → refresh();前台每 5s → refresh()(与 toggle 共享 mutationMutex 串行化)
结果: 框架内任何变更,回到 Modev ≤ 5s 内勾选态一致;勾选/取消勾选 即时一致
```

### 4.5 一键启动

- `AppCatalogRepository.AppInfo` + `launchable: Boolean`(启动时/包广播时缓存);
- 应用列表行尾「启动」IconButton(`PlayArrow`,40dp 触达区,不可启动/禁用置灰或隐藏);
- 算法记录列表页头部状态区同款「启动」入口;
- 拉起:`context.startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))`;`contentDescription = "启动 {label}"`。

## 5. 任务清单

### M5 记录可靠性管线(需求 1)

- [ ] M5-1 **协议 v3**:`WireProtocol`(VERSION=3、`TYPE_ACK`、`KEY_SEQ`)+ `WireCodec`(record 带 seq、`ack(seq)` 编解码)+ `ConfigChannel` 收 ACK 回调;hello 归并主包名
- [ ] M5-2 **null-token 放行**:`ConfigServer.onHello` 增加「requireToken 非空 + token==null → 放行 + 告警日志 + `stats.trustedNoToken++`」;非空不匹配仍拒绝(补 REFACTORING M4-4 缺口)
- [ ] M5-3 **WAL 化 `RecordSpool`**:append 常驻(flush + 批量 fsync checkpoint);ACK 游标删行(`ackedSeq`/`sentSeq` 双游标,断连回退);行格式 `seq\tjson`;失败路径改「tmp fsync → rename 原子替换」;轮转(5MB)与损坏行计数保留
- [ ] M5-4 **legacy 启用 spool**:`HookManager.install` 统一创建 spoolProvider(不依赖 `remotePrefsProvider`),legacy 入口行为与 modern 一致
- [ ] M5-5 **主包名归并**:`HookManager.install` 拆 `packageName`/`processName`;`ModevModule`/`LegacyModuleEntry` 传主包名;服务端会话一致性校验按主包名
- [ ] M5-6 **宿主侧闭环**:`LogRepository` 溢出改背压(阻塞回调线程,socket 反压注入侧 spool,不丢);`insertAll` 失败重试 3 次,仍失败不 ACK;批量落库成功后 `server.ack(session, maxSeq)`;DB v2→v3 迁移(seq 列 + 唯一索引)+ `onConflict=REPLACE`
- [ ] M5-7 **线程治理**:`ConfigChannel.sendRecordJson` 改为每连接单共享 watchdog 线程(写活动时间戳驱动),消灭逐发送建线程

### M6 作用域双向一致(需求 2)

- [ ] M6-1 **生命周期刷新**:`ModevNavHost` 内 `LaunchedEffect + repeatOnLifecycle(RESUMED)`:进入 RESUMED 立即 `scopeRepository.refresh()`
- [ ] M6-2 **前台轮询**:RESUMED 期间每 5s `refresh()`;`ScopeRepository.refresh` 与 `toggle` 共用 `mutationMutex` 串行化(防勾选态闪跳)
- [ ] M6-3 **超时补偿**:`requestScope` 超时/失败路径已 `refresh()`(保留),补单测
- [ ] M6-4 **UI 一致性**:勾选态唯一渲染源 = `Ready.scope`(已满足),验收补真机用例(见 M8)

### M7 一键启动(需求 3)

- [ ] M7-1 `AppCatalogRepository`:`launchable` 字段 + 刷新(启动/包广播/下拉)
- [ ] M7-2 应用列表行尾启动按钮(`AppsScreen.AppRowItem`)
- [ ] M7-3 算法记录列表头部启动入口(`AlgorithmListScreen` 状态区)
- [ ] M7-4 i18n(zh/en 文案)+ 无障碍(contentDescription)+ 禁用态(无 launch intent / 应用禁用)

### M8 测试与验收

- [ ] M8-1 单测:`WireCodec` v3(seq/ACK/缺字段)、`RecordSpool` ACK 游标推进/断连回退/轮转/崩溃窗口、`ConfigServer` null-token 放行与错 token 拒绝(stats 断言)、`HookManager` 主包名归并、`ScopeRepository` 串行化
- [ ] M8-2 Robolectric:`CryptoRecordDaoTest` 增迁移 v2→v3 与唯一索引幂等(同 seq 重放不重复);`AppCatalogRepositoryTest` 增 launchable 映射
- [ ] M8-3 门禁:`gradlew lintModernDebug lintLegacyDebug` 0 error;`testModernDebugUnitTest testLegacyDebugUnitTest` 全绿;`assembleModernDebug assembleLegacyDebug` 成功
- [ ] M8-4 真机验收矩阵(见 §6)

## 6. 验收标准(大厂门槛)

| 维度 | 标准 |
|---|---|
| 正确性 | ① 宿主被杀/重启、断连、握手失败、legacy、分离进程场景下记录最终全部可见,无重复(唯一索引);② 框架管理器取消勾选 → 回到 Modev 勾选态 ≤5s 消失,双向一致;③ 一键启动拉起目标应用 |
| 性能 | 万级记录:发送路径零线程风暴(单 watchdog);WAL 写入不阻塞 Hook 线程;5s 轮询不造成可见卡顿 |
| 安全 | 认证保持:null-token 放行仅限「无 token」,**非空不匹配仍拒绝**;放行路径告警+计数可见;`allowBackup=false` 不变 |
| 质量 | lint 0 error、双变体单测全绿、双变体构建成功;迁移脚本经 Robolectric 验证 |
| 体验 | 勾选态恒与框架一致;启动按钮禁用/可用语义清晰;空态/错误态文案 i18n zh+en;无障碍 contentDescription |
| 架构 | 双变体共用 WAL 管线;协议/DB 版本化迁移;作用域刷新单一入口(ScopeRepository);UI 零业务逻辑 |

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| ACK 丢失 → 重放重复 | DB 唯一索引幂等,重复行整体替换(内容一致,无副作用) |
| WAL 常驻增加注入侧磁盘 IO | 批量 fsync(≤100 条/500ms)折中;5MB 轮转保护磁盘;记录为审计性质,崩溃窗口 ≤ 一批可接受 |
| Vector 无 binder 场景免认证面 | 仅 loopback + scope 强制注入前提;放行带告警日志与 stats 计数;非空不匹配仍拒绝 |
| 轮询与 toggle 竞态闪跳 | mutationMutex 串行化刷新与变更 |
| 5s 轮询 IPC 开销 | 只读 `getScope`,轻量;仅前台期间运行 |
| 协议 v3 与旧宿主混跑 | 双端同 APK 升级(既有先例,无跨版本兼容诉求) |

## 8. 版本与产物

- 协议:`WireProtocol` v2→v3(record+seq、TYPE_ACK);DB v2→v3(seq 列 + 唯一索引)
- 新增:无新依赖;改造:`WireProtocol`、`WireCodec`、`CryptoRecord`、`ConfigServer`、`ConfigChannel`、`RecordSink`、`RecordSpool`、`HookManager`、`ModevModule`、`LegacyModuleEntry`、`LogRepository`、`CryptoRecordDao`、`CryptoRecordEntity`、`ModevDatabase`(迁移)、`ScopeRepository`、`AppCatalogRepository`、`AppsScreen`、`AlgorithmListScreen`、`ModevNav`、i18n 资源
- 文档:本任务文档;REFACTORING.md 增补 M5-M8 状态

## 9. 实施日志

> 每完成一项任务在此登记:完成项 + 要点备注。

- [x] M5-1:✅ 协议 v3 全量落地:`WireProtocol` VERSION=3 + `TYPE_ACK`/`KEY_SEQ`;`WireCodec.seqOf` 加 `isJsonPrimitive && isNumber` 加固;`ConfigChannel.onAck(seq)`;hello 带主包名
- [x] M5-2:✅ `ConfigServer.onHello` null-token 放行 + `stats.trustedNoToken++`;非空不匹配仍拒绝
- [x] M5-3:✅ `RecordSpool` 全量重写:行格式 `seq\tjson`、`ackedSeq`/`sentSeq` 双游标、持续输出流、批量 fsync(100 条/500ms)、tmp fsync→rename 原子重写、5MB 轮转、损坏行清理;drain 发送纯 JSON(去掉 seq 前缀,修复测试暴露的真 bug)
- [x] M5-4:✅ `HookManager.install` 两变体统一创建 `RecordSpool(File(filesDir,"modev_spool"), sub=processName)`,legacy 与 modern 行为一致
- [x] M5-5:✅ `ModevModule`/`LegacyModuleEntry`(`lpparam.packageName ?: processName.substringBefore(':')`)主包名归并;服务端按主包名校验
- [x] M5-6:✅ `ConfigServer.onRecord: (CryptoRecord, (Long)->Unit)->Unit` + `ClientSession.sendAck(seq)`;`LogRepository` pending 通道 + `Semaphore(PENDING_CAPACITY)` 背压、insertAll 重试 3 次不 ACK、按会话批次 max(seq) 回执;DB v2→v3(seq 列 + `(packageName, processName, seq)` 唯一索引 + REPLACE 幂等,存量 NULL 互不冲突)
- [x] M5-7:✅ `ConfigChannel` 重写:每连接单共享 watchdog 线程,lastWriteMs 写活动时间戳驱动,closeSocket 中断,消灭逐发送建线程
- [x] M6-1:✅ `ModevNav.kt` `LaunchedEffect + repeatOnLifecycle(RESUMED)`:进入 RESUMED 立即 `refreshNow()`
- [x] M6-2:✅ RESUMED 期间每 5s 轮询;`ScopeRepository` 公开入口持 `mutationMutex`,内部 `refreshNow/addNow/removeNow` 无锁,防勾选闪跳
- [x] M6-3:✅ 超时/失败路径保留 refresh;`ScopeRepositoryTest` 补串行化与失败刷新单测
- [x] M6-4:✅ 勾选态唯一渲染源 `Ready.scope`(代码审查确认),真机验收归 M8-4
- [x] M7-1:✅ `AppCatalogRepository.AppInfo.launchable`(`getLaunchIntentForPackage`),刷新/包广播/下拉均更新
- [x] M7-2:✅ `AppsScreen` 行尾 PlayArrow 按钮(`testTag("app-launch")`),launchable=false 禁用
- [x] M7-3:✅ `AlgorithmListScreen` 头部 `AppHeaderRow` 启动入口,同禁用逻辑
- [x] M7-4:✅ `apps_launch` 中英文案 + contentDescription 无障碍
- [x] M8-1:✅ 单测:WireCodecTest(ack 往返/seqOf 非法/record 携带 seq)、RecordSpoolTest 重写 12 例(ACK 裁剪/断连回退/轮转 FIFO/丢最老/legacy seq0/进程分目录等)、ConfigServerTest 新建 5 例 loopback(hellos 放行拒绝/ACK 往返/包名不匹配)、ScopeRepositoryTest 串行化;修复 Stub! 异常——`testOptions.unitTests.isReturnDefaultValues=true`(Log 桩不再抛)
- [x] M8-2:✅ CryptoRecordDaoTest v2→v3 迁移(createV1Helper/V2Helper + V1_TABLE_DDL)、唯一索引幂等(REPLACE 同 seq 不重复)、null-seq 不冲突;AppCatalogRepositoryTest launchable 映射(Robolectric 4.14.1 用 addOrUpdateActivity + addIntentFilterForActivity)
- [x] M8-3:✅ 门禁全绿:`lintModernDebug`/`lintLegacyDebug` BUILD SUCCESSFUL、`testModernDebugUnitTest`/`testLegacyDebugUnitTest` 全绿(双变体合计 260 测试)、`assembleModernDebug`/`assembleLegacyDebug` 成功
- [ ] M8-4:—(待真机)
