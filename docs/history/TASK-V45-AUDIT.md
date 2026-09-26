# TASK-V45：底层数据架构第一性 / 数据层⟂方法层严格隔离 / UI 与 MCP 双一等公民 全量审计清单

| 项 | 值 |
|---|---|
| 版本 | V45（工作树基线 `0431431` + V43/V44 未提交增量，2026-09-19） |
| 性质 | 架构审计报告（findings register）。实施分解见 `TASK-V45-PLAN.md`，验收矩阵见 `TASK-V45-ACCEPTANCE.md` |
| 需求来源 | 维护者 5 项硬需求（§0.1）。**本轮需求 2/3 为新维度**：数据架构⟂方法层严格隔离、UI 与 MCP 同为方法层一等公民 |
| 审计方法 | 六路并行全仓审计（存储与写入通路 / 方法层与隔离 / UI 功能面清点 / MCP 工具面清点 / 测试资产清点 / 注入侧钩面清点）+ **承重指控逐行二次复核**（20 条亲验命中，见 §0.4 复核台账；代理报告 3 处数字错误已纠正：靶用例 131 非 101、taxonomy 81 非 82、目的地 27 非 28） |
| 基线证据 | `:app:testModernDebugUnitTest` **BUILD SUCCESSFUL 2m52s（2026-09-19 本机重跑，112 结果 XML / 726 @Test 全绿）**；设备 `emulator-5554` 在线（Android 14 / API 34 / x86_64 / magisk root / Vector + lspd 配置库在位） |
| 统计 | 违规 **55** 条：P0 **7** / P1 **18** / P2 **20** / P3 **10**（另有 5 项环境前置阻塞登记于 §5）。INJ-12 与 QA-9 为 **T0.6 基线冻结时新增**（审计六路报告未涵盖，见 §0.5）；**INJ-13 为 T4.1 补测试时发现并当批修复**（审计六路与 V41–V44 全周期均未观察到，见 §2 INJ 表） |

---

## 0. 总纲

### 0.1 需求输入与维护者口径

| # | 需求原文（摘要） | 本审计口径 | 结果 |
|---|---|---|---|
| R1 | 底层数据架构**十分高效、专业**；上层所有**查询、写入**方法全部基于此架构 | 索引可用性 / 扫描代价 / 读放大 / 写路径锁链与容量 / 一致性护栏 / 单一事实源收敛度 | **不达标**（ST 11 条，含 P0 1 / P1 5） |
| R2 | 底层数据架构与方法层**严格隔离分离解耦** | 依赖方向单向性 / 存储形状不上溢 / 类型别名与双入口清除 / 可执行门禁覆盖 | **不达标**（ISO 9 条，含 P0 1） |
| R3 | 前端界面、MCP **同为方法层的一等公民**，共同基于方法层这一底层核心 | 消费面是否只依赖 domain 接口 / 能力差集 / 写侧语义与校验强度一致性 | **不达标**（ML 8 + DUAL 11 条，含 P0 1 / 跨维 P0 2） |
| R4 | 全量功能/端点/测试场景/预期结果清单 + 雷电模拟器 ADB 前端全量测试 + MCP 全量测试，**两次均须一次性全量通过**，不符则定向审计→修复→二次测试直至一次性通过 | 可测性与「一次性通过」前置条件清点 | **当前必红**（§5 五项阻塞，QA 8 条） |
| R5 | 专业、详细、完整的测试任务文档（被测项目 / 测试方法 / 测试环境 / 结果预期） | 见 `TASK-V45-ACCEPTANCE.md` | 交付 |

### 0.2 结论（TL;DR）

**记录面（RecordService）已是合格的方法层，其余一切都不是。**

1. **R2/R3 同一条根因**：全仓只有 `domain.record.RecordService`（16 成员）一个 domain 契约。**配置面（开关/偏差/关键词库/画像/黑名单/令牌/端口/外部访问/列表偏好）在 domain 层没有任何接口**——`data.repo.SettingsRepository`（491 行）同时充当存储门面与方法层门面。结果：UI 6 个 ViewModel + MCP 4 个工具组**全部直连 data 层具体类**（`ui/` 内 `import com.lioisme.modev.data.*` 共 **55 行**，`mcp/` 内 12 行），需求 2 与需求 3 同时落空。
2. **R1 一条决定性实锤**：`WHERE_FILTER` 用「`:xCount = 0 OR col IN (:xs)`」哨兵 + 24 列 `LIKE '%q%'` 拼出**单一事实源谓词**（设计意图正确），但 SQLite **无法把参数化哨兵下推为索引约束**，`%` 前缀 LIKE 亦不可索引 → 列表 / 计数 / 删除 / 详情邻接四类查询在 110 列宽表上**恒为全表扫描**；6 个索引（含 1 唯一）里只有 `observeCategoryCounts` 与两个 clear 真正命中。「架构高效」不成立，且这是全 App 唯一读路径，属最高杠杆修复点。
3. **上限三角不自洽**：默认 `maxBytesPerField = 1 MiB` → 单字段三态 ≈4.54M 字符 > `WIRE_MAX_LINE_CHARS = 4M 字符`；`MAX_CONTENT_MATERIALIZE_BYTES = 8 MiB` → 仅 hex 一份 16M 字符 > 4M 上限 4 倍。**默认配置即产出「必被判为损坏行并永久丢弃、且每次判废触发 spool 整文件重写」的记录**。
4. **R4 起跑即红（两条 P0 数字漂移 + 一条能力不可用）**：`McpEndToEndTest.kt:67` 断言 **43** 工具而注册表实数 **46**、`24-run-mcp.ps1:29` `expect=43` → 前端插桩与 MCP 设备矩阵**各自首跑必红**；`masterValue` schema 声明 `string` 而实现 `require(isBoolean)` → **MCP 侧 master 开关三态不可用**（传布尔被协议层拒、传字符串被业务层拒）。
5. V44 已闭环项在位（§1，15 项亲验），本轮**不重复登记**；V44 Phase 3（MD3 设计语言散点）与 Phase 4（AGENTS 同步/终检）仍挂账，已在 PLAN 中作为继承项列出。

### 0.3 决策点（★ = 审计推荐；维护者可推翻，未应答即按 ★ 执行）

| 编号 | 议题 | 选项 | ★推荐与理由 |
|---|---|---|---|
| D-V45-1 | 哨兵谓词重构形态 | A `@RawQuery(SupportSQLiteQuery)` 动态拼 WHERE（无谓词不拼列）/ B 每维度部分索引 + UNION ALL 拆分 / C 维持现状 + FTS 旁路 | **A**：保持「删=筛=count」单一片段生成器（一个 Kotlin 函数产出 SQL），SQLite 可选索引；C 违背 R1，B 复杂度不成比例。代价：Room 编译期校验失效 → 必配 EXPLAIN 真机门禁（T1.1 验收项） |
| D-V45-2 | 大字段存储形态 | A 维持单表三态（现状）/ B 内容旁路表 `record_payload(id, repr, ordinal, blob)` / C 外部文件 + 路径 | **本轮 A + 列表投影零大字段（现状已达标），B 记入 ROADMAP**：B 是正确终态但触迁移 v15 + 全 Mapper 改写，风险应留给独立版本，不与查询重构同批落地 |
| D-V45-3 | 方法层新建接口粒度 | A 4 接口（`ConfigService`/`FeatureService`/`AssetService`/`ServerService`）/ B 按消费方逐屏接口 / C 单一大接口 | **A**：与 wire 配置面/功能资产面/服务运维面天然同构，UI 与 MCP 现有调用点可 1:1 落位；B 制造碎片化门面（重演双入口），C 复现 491 行巨接口 |
| D-V45-4 | `observe(PagingData)` 留否在 domain | A domain 出 `List`+游标契约，Paging 留在 data.ui 适配层 / B domain 保留 Paging 白名单 / C UI 也改 queryPage | **B + 显式登记**：`androidx.paging` 为纯 JVM 契约（无 android.*），改 A 需重写 11 个列表页与 `LazyPagingItems` 全套（AGENTS #15 契约），收益不抵风险；但**白名单从「豁免」升为「契约条目」并在测试中锁定**（ISO-4） |
| D-V45-5 | 28 个兼容 typealias | A 全删（消费方 import 一次改齐）/ B 保留并加豁免名单 / C 保留一个版本周期 + DeprecationWarning | **A**：需求 2「严格隔离」不允许 `data.mapping.*` 这类「存储包名下的 domain 类型」长期存在；改动为纯 import 替换（9 个 UI 文件 + MCP + data 自身），机器可验 |
| D-V45-6 | MCP 能力补齐范围 | A 只补「双端都能做」的写侧（`mcp_set_port`/`mcp_set_enabled`/`scope_add` 自动化）/ B 全量镜像 UI 动作 | **A**：令牌管理维持文档化排除（引导悖论 + PII，AGENTS #26 不变）；其余「只有 UI 能做」的操作全部补工具或在排除快照登记理由（DUAL-4） |
| D-V45-7 | 「一次性全量通过」判定口径 | A SKIP 计入未通过 / B SKIP 允许但须登记环境指纹豁免表 | **B + 硬上限**：SKIP 必须命中预登记豁免表（预干净模拟器「无脏可掩」、无 VPN 链路、需系统弹窗），**SKIP 集合外任何 FAIL = 本轮未通过**；口径写死在 `TASK-V45-ACCEPTANCE.md` §4 |

| D-V45-8 内容物化上限与传输上限自洽 | A 由行上限反推物化硬保险丝（用户意图值 `maxBytesPerField` 保留但被钳制，超限置 truncated）/ B 下调默认为 96KiB（改用户可见默认值）/ C 提高行上限到 32Mi 字符（内存更差） | **A（已实施 T1.3）**：旧值 8MiB 与 4Mi 字符行上限直接矛盾 ⇒ 合法配置必产出「永不可送达」记录（判废即永久丢弃 + 触发整文件重写）；A 使不变量按构造成立且不动用户可见默认值。**代价登记**：单字段实际可物化深度从「名义 1MiB」降为 96KiB——但旧配置在 1MiB/7 字段形态下本就整条丢失，属净改善；全量保真需 D-V45-2-B 的旁路表，已入 ROADMAP |

### 0.4 承重指控复核台账（我逐行亲验的 20 条）

| 条目 | 复核方式 | 结果 |
|---|---|---|
| 43/46 漂移 | 读 `McpEndToEndTest.kt:67`、`24-run-mcp.ps1:29`、`ModevApp.kt:125`；`grep -o 'name = "' mcp/tools/*.kt \| wc -l` = 46 | ✅ 实锤 |
| 配置面无 domain 接口 | 读 `domain/record/RecordService.kt` 全文（唯一接口）；`grep '^import ...data\.' ui/` = 55 行、`mcp/` = 12 行 | ✅ 实锤 |
| 哨兵谓词不可用索引 | 读 `CryptoRecordDao.kt:360-392`（`(:typeCount = 0 OR ...)` + 24×`LIKE :query`）；`CryptoRecordEntity.kt:16-24` 索引表 | ✅ 源码实锤；EXPLAIN 终裁待设备（PLAN T1.1 验收） |
| crypto_records 110 列 / 6 索引 / v14 | 解析 `app/schemas/.../14.json` | ✅ 实数 |
| 上限不自洽 | 读 `RecordLimits.kt:16/19/22/31/37` + `RecordSpool.kt:128-136,257`（判废即整文件重写） | ✅ 算术成立（1MB→2M hex+1.33M b64+1M text=4.33M>4M） |
| `masterValue` 类型冲突 | 读 `ListenConfigTools.kt:54`（`str(...)`）与 `:79-82`（`require(isBoolean)`）+ `McpToolRegistry.kt:85-92`（按声明 type 拒绝） | ✅ 实锤，双向不可用 |
| MCP 端口写绕过校验 | `grep -rn PortValidator app/src/main/java/` → 仅 `domain/` + `ui/`（`ModevSheetScaffold`、`ServicesViewModel`）；读 `ServerTools.kt:83-97` | ✅ 实锤 |
| `getPackageInfo` 双份注册 | 读 `SignatureMonitorHooker.kt:21-31`（`engine.hook(registry.named(...))`）vs `AppInfoHooker.kt:47-49`（注释明示与 RootPM 共享广播单点）+ `SharedMethodBroadcast.kt:9` | ✅ 结构性实锤；「本机 Vector 下签名记录是否丢」= 设备探针项（PLAN T5.2） |
| seq 检查点非 max 语义 | `grep -n seqCheckpoint RecordSink.kt` → `:85 private var`（普通 volatile 域）、`:131/:169` 直赋值 | ✅ 实锤 |
| 宿主摄入仅条数闸门 | 读 `LogRepository.kt:99,107,138`（`Channel(PENDING_CAPACITY)`+`Semaphore`）、`:562` `MAX_RECORDS=20_000` | ✅ 实锤 |
| wire 写入侧来源约束缺口 | 读 `ConfigServer.kt:214-235`（token 缺失=放行计数；`packageName ?: return` 会话停留未注册）、`:266-274`（`sessionPackage != null &&` 前置） | ✅ 实锤（未注册会话接受任意包名记录） |
| 28 个兼容 typealias | `grep -rn '^typealias' app/src/main/java/ \| wc -l` = 28；`ls data/mapping data/repo` 确认 6 个 Compat 文件 | ✅ 实数 |
| 靶用例数 | node 解析 `TestCases.kt`：唯一 ID **131**（L 84 / E 44 / C 3），22 域 | ✅ **纠正代理的 101/106** |
| 目的地数 | `grep -c slideComposable( ModevNav.kt` = 28 行（含 1 行 KDoc）= **27** 注册；`ModevRoutes` `const val` = 27 | ✅ 一致（代理「28 常量」含 KDoc 干扰） |
| taxonomy 条目 | `grep -c '^\s*add(RecordCategory(' ` = **81**（44 LISTEN / 37 FEATURE） | ✅ 一致 |
| 单测基线绿 | 本机重跑 `testModernDebugUnitTest` → BUILD SUCCESSFUL + 112 XML | ✅ |
| 设备环境 | `adb -s emulator-5554 shell su -c id` = uid=0 magisk；`/data/adb/lspd/config/modules_config.db` 在位；`dumpsys package com.lioisme.modev` versionName 0.1.0 / lastUpdate 2026-09-08 | ✅ 设备可测（V44 T2.7 的「本机无设备」前提已失效） |
| `observe` 已兑现 sort | 读 `RecordService.kt:21-25` KDoc（V44 Q1 契约） | ✅ V44 已闭环，本轮不登记 |
| 计数副标已回真 SQL | `grep -n totalCount RecordsViewModel.kt:71` + `MonitorRecordsScreen.kt:392,510-515`（消费 `viewModel.totalCount` 而非 `itemCount`） | ✅ V44 D1 已闭环 |
| 遗留 12 列查询已删 | `CryptoRecordDao.kt:46-50` 注释（V44 D5 删除 `observePaged/observePagedRows`） | ✅ V44 已闭环 |

---

### 0.5 基线冻结时新增（2026-09-19 T0.6）

| 项 | 实况 | 结论 |
|---|---|---|
| `:app:lintModernRelease` | **FAILED：9 errors（全为 `NewApi` / `java.lang.ClassValue`）+ 41 warnings + 1 hint** | ① INJ-12 = 本轮第 7 条 P0，且是唯一「低版本真机注入即崩」级缺陷；② AGENTS 现文「基线 40 warning / 0 error」与「43 warning / 0 error」两处数字**双双失真**（ML-8 低估），实为 41 + 9 error；③ V44 T4.1 备注「lint 未跑、assemble 绿」= 该 P0 在 V44 全周期未被任何门禁观察到，正是 §4.4「门禁绿≠完成」的反面教材 |
| `:app:assembleModernDebug` / `Release` / 全量单测 | BUILD SUCCESSFUL（与 lint 同批 `--continue`，无编译级回归） | 基线冻结仅在 lint 修复后成立 |
| 测试 harness 实测 | 新 `tt-env.ps1` 框架探针首跑恒空 → 归因 PS 5.1 原生参数引号剥离 | QA-9 登记（探针假阴性会级联成误标 SKIP） |

### 0.6 整改进度（2026-09-19 起，随 PLAN 台账同步）

| 状态 | 条目 |
|---|---|
| ✅ 已整改并验证 | DUAL-1（masterValue 三态）、DUAL-2（43/46 派生化）、INJ-12（ClassValue→ClassValueCache，lint 9 error 清零）、ST-3（上限三角 + 唯一裁决出口）、ST-4/ST-5（seq max + 原子写 + 损坏回退下界）、QA-1/BL-4（`run-acceptance.ps1` 编排真身）、QA-8/BL-5（环境参数化 + 指纹）、ST-1/ST-2 **JVM 侧**（谓词生成器 + `@RawQuery` 执行位，`WHERE_FILTER` 退役；真机 EXPLAIN 待设备窗口）、ML-8（lint 基线数字实测确立：0 error / 41 warning / 1 hint）、**Phase 2 全批**（ISO-1/2/3/5/6/7/8/9、ML-1/2/3/6、DUAL-3/10：契约族 + 容器双段化 + 兼容层清零 + 写侧单点 + `RecordDraft` 整体删除 + 4 件新门禁）、**ST-6**（新增登记：监控页/MCP `app_monitor` 逐类型 10 次 COUNT → `observeTypeCountsForPackage` 一次 GROUP BY，`RecordTypeCountParityTest` 锁口径）、**ST-5/ST-7**（在途载荷预算 + `bytesDropped` 分型；trim keyset 化 + `packagesOver` 探针）、**ST-11/QA-6**（多类型 kind/operation 并集收敛 + 表驱动回归；死查询 `observePackages` 删除；`keyword_library.createdAt` 索引经核实本不存在 ⇒ 审计口径误差更正）、**INJ-13**（`MethodFinder.clazz()` 缺席类写 null → `ConcurrentHashMap` NPE → 整探针静默失装；`MethodFinderTest` 11 例为回归锁）、**QA-2 部分**（T4.1 首批 29 例注入装配契约测：`MethodFinderTest` / `ProbeDispatchContractTest` / `InjectionAssemblyContractTest`，含 master↔探针一一对应、recordType 单射、装配清单注册完整性） |
| 🔵 挂起待证据 | ST-1 关键字面收窄（T1.2，取决于 `RecordQueryPlanTest` 真机计划）；ST-5/ST-7 与 T1.8-⑤ 的**执行计划/采集端**验证段与 T1.1 EXPLAIN 同批延后设备窗 |
| 🟡 部分完成 | QA-2（T4.1 注入装配契约测已落 29 例；**剩余 = 双框架适配器对称面**——`LibxposedPlatform`/`LegacyPlatform` 的 `consumed`/`setThrowable`/`setArg` 需要真实 `Chain`/`MethodHookParam` 句柄,既非 JVM 可及亦非无钩子插桩可及,归 Phase 5 设备矩阵；两个 API 均为 `compileOnly` 且无 test 侧对偶,已核实）；QA-7（T4.4 半边:SKIP 逐名台账 + 豁免表已落地,失败截图/FailureHandler 与 `@LargeTest` 超时待设备轮） |
| ⬜ 未开始 | ST-10 采集端长文本闸门、ST-6 spool 分段（T1.5，与 V43 锁链同批）、INJ-1..INJ-11（注入侧钩面整改，属 Phase 5）、QA-3/QA-4/QA-5/QA-6/QA-8 剩余段（T4.2 门控扫描补盲、T4.3 插桩资产补齐、T5.* 注入修复） |

---

## 1. V44 已闭环基线（实测在位，本轮禁止重复登记或回退）

`RecordService` 单门面 + DAO 直调白名单门禁；`WHERE_FILTER`/`ROW_SELECT` 单一事实源；遗留单值 12 列 LIKE 查询与死别名已删；`DEFAULT_PAGE_SIZE=60` 双端单源；`observe`/`queryPage` sort 契约统一；`keywordFollowGlobal=true→null` 双端归一；`apps_list` 默认读持久偏好 + domain `AppSort` 双端复用；`records_clear_package`/`clear_type` 补 `confirm`；`record_create/update` 补 taxonomy kind/operation 强校验；kind 存储限定键 `recordType:kind`（消除 OPEN/SIGNATURE 跨类型碰撞）；枚举⊆taxonomy 双向锁 + `KindLabelResExhaustiveTest`；MCP 补齐 3 工具（46）；`ProcScanBroadcaster` 单一广播收编；热路径门控先行；`RecordSink` 32MB 字节预算 + 溢出队列（hook 线程零序列化零 IO）；`logStack` 默认 false；`StripedWeakMap` 条带化；`ProcessIdentity` cmdline 单例。

> 含义：V45 不是「重做 V44」，而是把 V44 已建立的**记录面纪律**推广到**配置面/资产面/服务面**，并把 R1 的「高效」从「没有明显低效」提升到「有可执行的索引与代价门禁」。

---

## 2. 违规明细

严重度定义：**P0** = 阻断本轮验收或架构性倒置；**P1** = 违反硬需求且有实锤运行时代价；**P2** = 一致性/专业性缺口，影响可维护性与断言可信度；**P3** = 登记取舍。规模 S ≤0.5d / M ≤2d / L >2d。

### ST 底层数据架构效率与专业性（R1）

| ID | P | 证据 file:line | 现状 | 影响 | 整改 | 规模 |
|---|---|---|---|---|---|---|
| ST-1 | **P0** | `data/db/CryptoRecordDao.kt:360-392`；实体索引 `data/db/CryptoRecordEntity.kt:16-24` | 哨兵 OR 谓词 + 24 列 `%LIKE%`；`observePagedRowsByFilter(±Asc)`/`observeCountByFilter`/`deleteByFilter`/`sliceByFilter*` 四条主查询共用 | 110 列宽表全表扫描；6 索引形同虚设；LIKE 侧须物化 hex/text 大列 → 计数与列表随记录量线性劣化（20k 上限内不可控） | 按 D-V45-1 改动态 `SupportSQLiteQuery`（`@RawQuery(ObservationSpec)`），无谓词不出列；关键字检索改**受限列集 + 前缀**（可索引）或独立 FTS 虚表（决策 T1.1 附表） | L |
| ST-2 | P1 | `CryptoRecordDao.kt:274-300`（邻接双查询）+ `:161/:188`（切片） | `(ts<:x OR (ts=:x AND id<:y))` + 哨兵 OR + `ORDER BY timestamp DESC,id DESC LIMIT 1` | 详情页每按一次「上一条/下一条」= 一次全表扫描 + 排序（ST-1 的最痛放大点） | 随 ST-1 收口；邻接走 keyset（`(timestamp,id)` 复合索引显式声明） | M |
| ST-3 | P1 | `core/model/RecordLimits.kt:16,19,22,31,37`；`hook/RecordSpool.kt:128-136,257` | 1MiB 字段 → 三态 4.53M 字符 > 4Mi 行上限；8MiB 物化上限 → 单 hex 16M 字符 > 行上限 4× | 默认配置即产出永不可能送达的记录：先序列化、先落盘 → drain 判损坏 → **永久丢弃 + 每次判废整文件重写**（写放大叠 ST-6） | 由 `WIRE_MAX_LINE_CHARS` 反推物料上限，`RecordLimits` 内建立交叉不变量 + 新单测断言「任何合法配置产出的行恒 ≤ 上限」 | S |
| ST-4 | P1 | `hook/RecordSink.kt:85,131,169,195` | `seqCheckpoint` 普通 volatile 直赋值（非 max 语义），并发 emit 下低号可覆盖高号；持久化 `file.writeText`（`:200`）非原子、无 fsync | 检查点回退 → 重启续号撞号 → 宿主唯一索引 REPLACE **静默覆盖上一代记录**（V38 同类丢数回归面） | `AtomicLong.accumulateAndGet{maxOf}` + 复用同仓 `RecordSpool.kt:156-182` 的 temp+fsync+rename 原子重写原语 | S |
| ST-5 | P1 | `data/repo/LogRepository.kt:99,107,138`（条数闸门 8192）vs `hook/RecordSink.kt` 32MB 字节预算 | 注入侧有字节预算、宿主摄入侧仅条数 | 大字段风暴下宿主堆 OOM（8192×MB 级解析对象），双端闸门不对称 | pending 通道加 `contentWeight` 同口径字节预算 + 溢出计数入 `PipelineStats` | S-M |
| ST-6 | P1 | `hook/RecordSpool.kt:77-83,147-182`；`hook/ConfigChannel.kt:250` | 每个批量 ACK 在 ConfigChannel 读线程持监视器整文件重写 spool+old | ACK 线程阻塞 → socket 读停摆 → 同锁 `append` 阻塞 worker → 队列满 → 溢出丢弃（V43 T1 拆掉的锁链从 ACK 侧回归） | 分段文件（segment）+ 满段删除；trim 出锁出 ACK 线程 | M-L |
| ST-7 | P2 | `CryptoRecordDao.kt:300-332`（trim/trimPackage）；`LogRepository.kt:260-280` | `id NOT IN (SELECT … LIMIT)` + 每 500 条跑一次全库 `counts(null)` | trim 自身不走索引、每 500 条一次全表扫描 + 临时 B-tree | keyset 先查 cutoff 再 `DELETE WHERE timestamp < :cutoff`（走 `index_timestamp`）；每包计数增量维护 | S |
| ST-8 | P2 | 全工程 `PRAGMA/VACUUM/auto_vacuum/busy_timeout` 零命中；`data/db/ModevDatabase.kt:291-313` | 无 PRAGMA 调优、无 `incremental_vacuum`、无 `wal_checkpoint`、无 busy_timeout | 清库后文件永不收缩（`storageUsage` 长期虚高）；并发写易 SQLITE_BUSY 静默丢批 | `RoomDatabase.Callback` 统一 PRAGMA 出口 + trim/clear 后增量收缩 | M |
| ST-9 | P2 | `ModevDatabase.kt:291-313` | 无 `integrity_check` / 无 `DatabaseErrorListener` / 无备份或自愈 | 单文件损坏 = 全部记录丢失且无诊断证据 | 启动 quick_check→降级重建（保留 spool 重放）+ 错误回调落观测面 | M |
| ST-10 | P2 | `CryptoRecordDao.kt:336-353`；采集端 `ActivityTransitionHooker.kt:42-50`、`DialogKeywordInterceptHooker.kt:45-52`、`ShellHooker.kt:47` | 列表投影对 hex/sql 做了 `substr`，但 `command`/`dialogMessage`/`intentExtrasJson`/`widgetText`/`logcatMessage`/`path` 整列进列表且采集端无长度闸门 | 每页 60 行 × 无界文本 → 列表读放大与内存尖峰 | 采集端统一 `maxTextChars` 截断 + 列表侧 `substr` 预览 | S-M |
| ST-11 | P3 | `data/db/KeywordLibraryDao.kt:17-21`；`data/db/CryptoRecordDao.kt:243-250,321-322`；`data/mapping/*Compat.kt` | `keyword_library` 无 `createdAt` 索引（小表可容忍）；`observePackages`/`count(pkg)` 死查询；表名 `crypto_records` 承载 10 类记录 | 门禁噪声与检索歧义 | 死查询删除或标注契约归属；表名文档化为「统一记录表」（不改数据，零迁移风险） | S |

### ISO 数据层 ⟂ 方法层严格隔离（R2）

| ID | P | 证据 file:line | 现状 | 影响 | 整改 | 规模 |
|---|---|---|---|---|---|---|
| ISO-1 | **P1** | `domain/record/RecordDraft.kt:14-131`（≈110 字段逐列镜像实体）；`CryptoRecordEntity.kt:41-135` | 方法层写入模型的字段集 = 存储列集（含 `*Hex`/`*B64`/`*Truncated` 等**编码态列族**） | 存储形状上溢方法层：新增列必须改 domain 契约，UI/MCP 写面随存储漂移 | draft 按记录类型分片为密封族（内容只收字节 + 表示法意图，编码在 data 层落地）+ `RecordDraftColumnParityTest` 锁漂移 | M |
| ISO-2 | **P1** | `data/mapping/RowModelCompat.kt:8-28`、`RecordDetailModelCompat.kt:5-25`、`data/repo/{RecordService,RecordFilter,PipelineStatsCompat}.kt`、`data/db/CryptoRecordDao.kt:406`（合计 **28** typealias）；`grep '^import ...data.mapping' ui/` = 9 文件 | 同一 domain 类型有两个包名；UI 走 `data.mapping.RowModel`、MCP 走 `domain.record.RowModel`，`LogRepository.kt:36-40` 生产码仍 import 别名、`:307` 靠同包别名解析 | 消费方无法区分「存储」与「模型」；包边界门禁只能靠白名单放过（`PackageBoundaryTest.kt:60-64`）；需求 2 直接反面 | 按 D-V45-5 全删 + import 一次改齐 + `NoCompatTypealiasGateTest` 禁复活 | S |
| ISO-3 | P1 | `architecture/PackageBoundaryTest.kt:86-135` | 五条规则无一约束 `ui→data.repo`、`ui→data.mapping`、`mcp→data.repo`；`ui` 仅禁 `data.db`/`data.server` | 门禁结构性盲区：ISO-1/2、ML-1、DUAL-1 全部绿灯通过，「隔离」只存在于文档 | 规则升级：`ui.`/`mcp.` 子树文本级禁 `com.lioisme.modev.data.*`（例外仅 domain 已上移类型） | S |
| ISO-4 | P2 | `domain/record/RecordService.kt:3,25`（`androidx.paging` + `Flow<PagingData<RowModel>>`）、`RowModel.kt:3`（compose `@Immutable`）；豁免 `PackageBoundaryTest.kt:98` | 分页框架与 Compose 注解进入方法层契约，且 `PagingData` 仅 UI 消费 | 方法层为单一消费方定制；REST/其它消费方被迫承接 Paging 语义 | 按 D-V45-4 保留但**升为契约条目**：测试锁定 domain 可用 androidx 白名单 + 注释写明「Paging 为多消费方分页契约，非 UI 专属」 | S |
| ISO-5 | P2 | `ui/apps/PerAppFeatureSettingsScreen.kt:52-53`；`ui/settings/features/{KeywordLibraryScreen.kt:71,SpoofProfileScreen.kt:55-76,FeatureBlacklistScreen.kt:56-71}` | ViewModel/屏幕构造参数即 `AppContainer`（服务定位器），直取 `container.keywordLibrary`/`container.settings` | 依赖面 = 全容器（含 `mcp`、`ConfigServer`），类型系统不再表达「这一屏只用什么」 | VM 收窄为 domain 接口注入；`container` 仅装配层可见 | S |
| ISO-6 | P2 | `ui/settings/ServicesViewModel.kt:136-138`、`DiagnosticsViewModel*.kt:73`；`mcp/McpServer.kt:33-40` | UI 经容器读 `McpServer` 具体类 `boundAddress`/`bindError`；MCP 服务未实现 domain `ConnectionMonitor`（wire 侧已实现） | UI↔MCP 直接耦合；服务抽象只覆盖一半 | `McpServer : ConnectionMonitor`，UI 只持接口；`ServerAbstractionTest` 锁死 | S |
| ISO-7 | P2 | `data/repo/SettingsRepository.kt:195-203,347-354` | 配置门面把「偏好持久化 + 服务重启编排 + 关键字/画像/黑名单合并 + 令牌生命周期」四类职责写在一文件 491 行 | 无法单独测试/替换；任何消费方获得全部能力 | 随 ML-1 拆分：`domain` 接口 + `data` 内多实现（或一实现分文件委托） | M |
| ISO-8 | P3 | `data/repo/{SettingsRepository,LogRepository,KeywordLibraryRepository,AppCatalogRepository,AppListSettings}.kt` | domain 类型与 data 类型同包混住（`LogRepository implements domain.RecordService`） | 包名不再表达层 | 命名与包位约定入 AGENTS：`domain.*` 契约、`data.repo.*` 实现一律 `<Name>Repository` 且不被消费方点名 | S |
| ISO-9 | P3 | `data/repo/AppCatalogSource.kt`；`ui/records/RecordsViewModel.kt:12` | `AppCatalogSource` 抽象在 data 包，UI import 之 | 与 ISO-2 同类（抽象放错层） | 上移 domain | XS |

### ML 方法层完备性（R1 上层方法基于架构 + R3 底层核心）

| ID | P | 证据 file:line | 现状 | 影响 | 整改 | 规模 |
|---|---|---|---|---|---|---|
| ML-1 | **P0** | `domain/` 全包无配置/资产/服务/令牌接口；直连点：`ui/settings/{ServicesViewModel.kt:31,SettingsViewModel.kt:15,DiagnosticsViewModel.kt:28,McpTokensViewModel.kt:18,DataSettingsViewModel}.kt`、`ui/apps/AppMonitorViewModel.kt:11`、`PerAppListenSettingsScreen.kt:57`、`mcp/tools/{ListenConfigTools.kt:9,FeatureConfigTools.kt:12,GlobalAssetTools.kt:11,ServerTools.kt:6}.kt` | 需求 3 的「方法层这一底层核心」在记录面存在、**其余面不存在** | R1/R2/R3 三面同破：无法替换存储、无法 mock、双端共享 data 实现细节 | 按 D-V45-3 新建 `domain.config.ConfigService` / `domain.feature.FeatureService` / `domain.asset.AssetService`（关键词库+画像+黑名单）/ `domain.server.ServerService`（端口/外部访问/令牌元数据），`data.repo` 实现之，14 个消费点改注接口 | M |
| ML-2 | **P1** | `mcp/tools/McpTools.kt:5-8`、`data/repo/LogRepository.kt`（`statsFlow` 非契约成员）、`ui/settings/DiagnosticsViewModel.kt:27,60,73` | UI 与 MCP 为拿 `statsFlow` 注入 `LogRepository` **具体类** | 唯一合格门面的契约被实现类型旁路，观测面不在方法层上 | `PipelineStats` 观测面上提为 `RecordService.stats(): Flow<PipelineStats>`（或独立 `domain.diagnostics.PipelineMonitor`） | S |
| ML-3 | P1 | 能力差集：`RecordService` 6 成员单侧（`observe` 仅 UI、`queryPage`/`create`/`update`/`delete`/`categoryCounts` 仅 MCP，`mcp/tools/RecordTools.kt:61,129,144,153,95`） | 「同一核心」实为两副面孔 | 双端能力对称性无法证成 | 逐个成员登记：`@SingleConsumerSide` 白名单 + 缺口补齐（UI 补导入/编辑入口或显式承认单侧），`ContractMemberConsumptionTest` 门禁 | M |
| ML-4 | P2 | `domain/scope/ScopeRepository.kt`（具体类置于 domain）、`domain/diagnostics/DiagnosticsReport.kt`、`domain/server/PortValidator.kt`（object） | domain 内混住具体实现与静态工具 | 可替换性不一，测试替身成本按类浮动 | 统一「接口在 domain、实现在 data/framework」；纯函数工具可保留但须在契约表登记 | S |
| ML-5 | P2 | `mcp/tools/GlobalAssetTools.kt:75,98,112,132,151,218,269`、`ListenConfigTools.kt:129,139`、`FeatureConfigTools.kt:154,164`、`ScopeTools.kt:70,84`、`AppTools.kt:72,107` | 工具线程内 `runBlocking` 调 domain 挂起方法；`app_monitor` 串行 10 次取计数流首值 | 方法层协程契约不清：同一方法两侧调用形态不同（UI 走 Flow/协程、MCP 走 runBlocking） | domain 明确 suspend 契约 + MCP registry 提供协程执行器；计数走一次 `combine` | S |
| ML-6 | P2 | `ui/settings/SettingsViewModel.kt:15-19`（注入未用）；`CryptoRecordDao.kt:29,250,322` | 零成员 VM / 仅测试消费的 DAO 成员 | 方法层真实面被死门面掩盖 | 删除 | XS |
| ML-7 | P3 | `mcp/tools/RecordTools.kt:265-268,276-336` | `filterSchema`/`draftSchema` 共用 `props()`（59 属性），`parseFilter` 仅读 8 个 | 契约噪声；Agent 可传被静默忽略的分页/排序/搜索参数 | 拆三张 schema，逐参数可断言 | XS |
| ML-8 | P3 | 全仓无 ktlint/detekt/apiCheck；lint 无 baseline 文件且 AGENTS 基线数字自相矛盾（40 vs 43） | 风格/死码/公共 API 漂移无门禁，「零新增」不可自动判定 | 质量红线靠人工 | 生成 `lint-baseline.xml` + AGENTS 单一基线数字；死码由 QA-6 扫描门禁承担 | S |

### DUAL UI 与 MCP 双一等公民同源性（R3）

| ID | P | 证据 file:line | 现状 | 影响 | 整改 | 规模 |
|---|---|---|---|---|---|---|
| DUAL-1 | **P0** | `ListenConfigTools.kt:54` 声明 `str("masterValue")` vs `:79-82` `require(isBoolean)`；`McpToolRegistry.kt:85-92`（按声明 type 拒） ；`FeatureConfigTools.kt:73,93-95` 同构 | MCP 传布尔 → `-32602 expects string`；传 `"true"` → 业务错 | **Agent 完全无法切换 10 个 master 开关**，双端能力对等性直接破裂；设备矩阵首跑即暴露 | 改 `bool("masterValue")` 且 null=回默认（schema 支持 nullable 语义）+ 二态回归测（JVM 工具面 + 设备 M-CFG） | S |
| DUAL-2 | **P0** | `McpEndToEndTest.kt:67`（`assertEquals(43, tools.size())`）、`scripts/test-integration/24-run-mcp.ps1:29`（`expect=43`）、`ModevApp.kt:125` 注释 vs `McpTools.kt`/`McpToolParityTest`（46） | 工具数三方漂移 | **需求 4 要求的两次全量测试各自首跑必红** | 两处改 46 且**改为从 registry 派生**（测试与脚本禁写死数字）；`AGENTS` 词汇表同步 | XS |
| DUAL-3 | P1 | `ServerTools.kt:83-97`（`setServerPort` 直写，仅 clamp）vs `PortValidator`（仅 UI 消费，`ModevSheetScaffold.kt:203-269`、`ServicesViewModel.kt:98-118`） | 同一写操作：UI 走特权/冲突/占用三闸硬拒，MCP 静默 clamp 直写 | 校验位于 UI 组件而非方法层 = 写侧规则可被另一侧绕过（AGENTS #29 红线在 MCP 面失守） | 抽 domain `PortWriteGuard`，`setServerPort/setMcpPort` 内部强制 `PortValidator.check`；`PortWriteGuardTest` 门禁 | S |
| DUAL-4 | P1 | `ServerTools.kt:25-27`（`setMcpEnabled/setMcpPort` 无工具）；`McpToolParityTest.kt:71-90` 仅登记令牌排除 | MCP 自身开关/端口「只有 UI 能做」且未登记排除理由；令牌管理已登记（正当） | 双端能力集不对称，parity 门禁只锁名字不锁方向 | 按 D-V45-6 补 `mcp_set_enabled`/`mcp_set_port` 或在排除快照登记理由并锁 | S |
| DUAL-5 | P1 | `ui/settings/McpTokensScreen.kt` 全套 + `SettingsRepository.kt:376-392` | 令牌 CRUD 仅 UI（正当排除）；但**令牌状态经 UI 变更后 MCP 侧校验集原地生效**这一关键双端语义**无任何设备端到端测**（18/46 工具设备端从未 call 过） | 端到端证据缺口 | 矩阵补 MCP-TOKEN 组：UI 建令牌 → 认证开 → 无令牌调用必 401 → 带令牌 200 → 撤销后 401 | S |
| DUAL-6 | P2 | `mcp/tools/McpJson.kt:17,22-24`（`serializeNulls` + null 补 `JsonNull`）vs `ServerTools.kt:29-47`、`ScopeTools.kt:18-35`（裸 `addProperty`，可选键条件出现） | 同一协议两套 null 策略 | Agent 侧键存在性不可预判；断言脆弱 | 统一走 `McpJson`；`scope_state`/`server_status` 键集快照测试 | S |
| DUAL-7 | P2 | `mcp/tools/McpDtos.kt:41-42`（CryptoRow `fields` = `row.record`，其余 9 类 = `McpJson.tree(rowModel)` 含嵌套 `record`） | 列表行 DTO 结构按类型分裂 | `fields.*` 取值路径不统一，双端「同一数据」不可机械对账 | 统一 `fields` 为扁平展示字段 + 独立 `detail` 出口 | S |
| DUAL-8 | P2 | `mcp/tools/McpDtos.kt:29,113-114,142-144`（详情 1MiB 截断、`truncated` 仅在真时写入）vs `RecordLimits.kt:31`（物化 8MiB）；`row()` 无截断守卫 | 双端截断口径分叉、列表侧无标志 | 「同一核心不同视图」，UI 所见 ≠ MCP 所得 | 上限单源 + `truncated` 恒在 + 列表侧同口径守卫 | S |
| DUAL-9 | P2 | `ui/records/RecordsViewModel.kt:53-58`（UI 恒 DESC，无 ASC 入口）vs `RecordTools.kt:46`（`sort:"asc"`） | ASC 只在 MCP 可达 | 契约同源、能力面不同（登记为已知不对称或补 UI 排序面） | 补 UI 排序入口或 parity 排除登记 | S |
| DUAL-10 | P2 | `core/model/AppListenOverride.kt:179-188`；`ListenConfigTools.kt:72-75,89-100` | MCP `kindEntries` 接受裸 kind id（歧义归首个登记类型），UI 恒写限定键 `type:kind`；`listen_settings_set` 的 master 无 LISTEN 域闸（`feature_settings_set` 有） | 同一份 kinds 存储两侧可写入不同键空间/跨域偏差（V44 T1.1 修复的残留半面） | MCP 只收限定键 + listen 侧补域闸；非法键显式拒 | S |
| DUAL-11 | P3 | `mcp/tools/AppTools.kt:151-153,166-172`（`pinScopedOnTop`/`showXposedModules`）vs `AppListSettings.kt:30,37`（`pinScoped`/`showModules`）；`records_clear_package`(#11) 与 `app_clear_records`(#19) 同上游重复 | 名称漂移 + 动作重复 | 对账需换算；双入口双测 | 键名与存储名一致（或映射表登记）；重复工具择一保留并在 parity 快照标注 | S |

### INJ 注入侧静默失效（决定端到端测试能否拿到真实数据）

| ID | P | 证据 file:line | 现状 | 影响 | 整改 | 规模 |
|---|---|---|---|---|---|---|
| INJ-1 | P1 | `hook/hooks/SignatureMonitorHooker.kt:21-31` vs `AppInfoHooker.kt:47-49`、`SharedMethodBroadcast.kt:9` | `getPackageInfo` 上：广播 1 注册（AppInfo+RootPM 订阅）+ SignatureMonitor 又直挂第 2 注册 | Vector 2.2「同一方法第二个 hook 不执行」（AGENTS 实锤坑）⇒ **`SYSTEM:SIGNATURE`（默认开）本机可能恒零记录** | 改 `broadcast.subscribe`；新增「同 Executable 禁既 broadcast 又直挂」源码门禁 | S |
| INJ-2 | P1 | `hook/hooks/DetectionMonitorHooker.kt:127-143,146-172,203-232` vs `RootFileHooker.kt:32`、`EmulatorSensorHooker.kt:37`、`EmulatorBuildHooker.kt:102` | `File.exists()`/`getDefaultSensor(int)`/`Build.get*` 观测与干预**双份独立注册**（注释称「避免双重 Hook」但运行时门控只避免双重做功） | `DETECT_ROOT`/`DETECT_EMULATOR` 开启后**恒零记录**（本机即该通道）——正是验收矩阵 L-DEV-20/21 的假阴性来源 | 三处并入既有共享广播点；门禁同 INJ-1 | S |
| INJ-3 | P1 | `hook/hooks/ActivityTransitionHooker.kt:33-49` | `ACTIVITY_START` 默认开，after 内主线程做 `resolveActivity`（PMS Binder IPC）+ 全 Bundle `keySet()` 反序列化 + JSON 拼串，无节流 | 跳转密集 App 主线程额外 IPC/分配 → 掉帧、ANR 风险（违反 R4 的「不得卡顿闪退」隐含要求，AGENTS V36 红线） | extras 尺寸上限 + `SessionThrottle` + resolve 结果缓存 | S |
| INJ-4 | P2 | `core/model/CategoryTaxonomy.kt:66`（`CRYPTO:WEB_CRYPTO`）；`grep -rln CryptoKind.WEB_CRYPTO hook/` = 空 | 有 taxonomy 条目、有开关位、有 UI 行/图标/标签，**注入侧零发射器** | 用户开开关永远零记录（静默失效）；`anyCryptoEnabled` 判定被虚增 | 删除条目或由 WebViewProbe JS 事件双写；同步 4 处映射表 + `HookSwitchTest` 断言 | S |
| INJ-5 | P2 | `hook/hooks/EmulatorSensorHooker.kt:158-166` | `getMinDelay()==0 → setResult(5000)` **完全不 emit**（同文件其余 3 个干预均 emit） | 静默干预，违反 `interventionAction` 申报契约；二态断言无证据 | 补 emit + 行为二态测 | XS |
| INJ-6 | P2 | `hook/hooks/DnsHideHooker.kt:35,66,92` | 3 个 `InetAddress` 钩发成 `NetworkKind.REQUEST`（仅 `params[type]=dns_*` 区分），类名 Hide 实则只记录 | DNS 密集 App 记录放大、详情语义混淆 | 独立 kind（触五处同步清单）或 UI 按 params 分流 + `getHostName` 节流 | M |
| INJ-7 | P2 | `hook/hooks/MacHooker.kt:120-140`、`SignatureHooker` update 分支 | Cipher/MessageDigest 的 UPDATE 有 1s 节流，**Mac/Signature 无** | 分块 HMAC/签名每块三态物化 → GC 压力（与 V44 T2.3 修复同形态的漏项） | 复用同一节流器 | XS |
| INJ-8 | P2 | `hook/HookInstaller.kt:215-223` + `HookSwitch.DEFAULT` | legacy 变体无 RemotePreferences 直读；装配时 TCP 未到 → `currentConfig=DEFAULT`（10 master 全 true）→ **冷启动配置裁剪完全失效** | legacy 通道宿主冷启动一次性卡顿；与 modern 行为不对称（矩阵二态易误判） | legacy 装配前有界等待 ≤200ms 或把「legacy 不裁剪」写入验收基线并在文档标注 | S |
| INJ-9 | P3 | `testtarget/.../TestCases.kt:41` vs `TriggerActivity.kt:88-104` | 声明 `"process" → [L-PRC-01]`，但 `when(domain)` 无 `process` 分支 → `unknown-domain` | 靶用例总数账面 131，实可驱动 130；矩阵 1 项假失败 | 补分派或从 DOMAINS 删除；`tt-domain-coverage` 门禁断言「每个声明 ID 均有分派」 | XS |
| INJ-10 | P3 | `hook/RecordSink.kt:87`；`hook/RecordSpool.kt`（corrupt/dropped） | 注入侧丢弃/判废计数只在注入进程内存，无外发通道 | 宿主侧「注入丢了几条」不可观测，只能靠 seq 空洞反推（背压矩阵断言因此偏弱） | wire 心跳/新增 stats 消息带 3 计数 + 诊断页展示 | M |
| INJ-11 | P3 | `hook/hooks/SSLBypassHooker.kt:446-460,513`；`ProxyHideHooker.kt:159-232` | 混淆类发现走 `Class.forName`+`declaredFields` 全栈遍历，`discoveredClasses`/`renamedInterfaces` 无界 | 开启态每次握手付全栈扫描；容器只增不清 | 结果缓存 + 包名片段限定 + 容量上限 | S |
| **INJ-12** | **P0** | V44 T2.5 遗留：`hook/hooks/AppInfoHooker.kt:25`、`RootPackageManagerHooker.kt:35`、`SqlDatabaseHooker.kt:459` 用 `java.lang.ClassValue` 做热路径字段缓存（实测 `lintModernRelease` **9 errors / NewApi**，2026-09-19 T0.6 基线冻结时暴露） | `ClassValue` **API 34** 才有，模块 `minSdk = 26`：Android 8–13 宿主上 Hooker **构造期** `NoClassDefFoundError` → 注入即崩，且与功能开关无关（违反「装钩不得拖垮宿主」最高红线）；同时证伪 AGENTS「lint 基线 0 error」与 V44 T4.1「assemble 绿」结论（lint 当时未跑） | 低版本真机全量崩溃 | 新增 `hook/ClassValueCache.kt`（`StripedWeakMap` + Box 承载 null，语义对齐 ClassValue 且无 API 级别要求）三处替换；`ClassValueCacheTest`(5 例) + `MinSdkApiConventionTest`(源码扫描秒级前置门禁，lint 仍为终裁) | S |
| **INJ-13** | **P1** | `hook/MethodFinder.kt:50-55`（V45 T4.1 新增契约测时发现，2026-09-20） | 缺席类负缓存写成 `classCache[className] = clazz`，而 `clazz` 为 null：**`ConcurrentHashMap` 不接受 null 值 ⇒ 直接抛 `NullPointerException`**（类型标注 `Class<*>?` 让 Kotlin 编译期完全不报警） | 任一可选框架类在该 ROM 上缺席（conscrypt/`SSLParametersImpl` 变体、按 API 级别增删的类），`Hooker.install()` 的**第一条**缺席解析即上抛 → `HookInstaller` 的 per-probe catch 吞掉 ⇒ **整个监听域静默失装**（矩阵表现为「该类型恒零记录」，且只有一行 `探针安装失败` 日志） | `clazz()` 改双结构：命中缓存返回 / `absentClasses` 并发集记缺席（写 null 非法），缺席亦恒定返回 null 不重复 `forName`；回归锁 = `MethodFinderTest`（`missing_class_returns_null_and_is_repeatable`、`bootstrap_only_lookup_skips_app_classloader_targets` 两例在修复前必然 NPE） | S |

### QA 测试资产与门禁盲区（R4/R5 前置）

| ID | P | 证据 file:line | 现状 | 影响 | 整改 | 规模 |
|---|---|---|---|---|---|---|
| QA-1 | **P0** | 见 DUAL-2；另 `scripts/test-integration/` 18 个 ps1 中 **26-run-module-off-negative / 27-run-backpressure 不在任何编排内**，`run-all.ps1` 为 legacy Twilight 死脚本（不链编号脚本、无退出码、靶包不同），`00/10/12` 游离 | 无「一条命令跑全量」的编排真身 | 需求 4「一次性全量」不可机械达成、不可复现 | 新建 `run-acceptance.ps1`（严格序列 + 退出码 + JSON 台账）；旧 `run-all.ps1` 退役或转发 | M |
| QA-2 | P1 | `HookInstaller`/`MethodFinder`/`framework/` 3 桥/`adapters/` 2 个/`ModevModule`+`LegacyModuleEntry`/8 个 Probe —— 14 个注入热路径类**零具名测试**（141/338 main 文件从未被任一测试文件名引用） | 装配断裂只能整机冒烟兜底 | 故障定位半径极大 | 关键入口补 JVM 契约测试（registry/派发面/FakePlatform 对称面） | L |
| QA-3 | P1 | 设备端屏幕盲区：8 个 per-app 记录列表屏（behavior/prefs/webview/network/device_env/ui/system/process_control）、`settings/mcp-tokens` 全流程、`about`/`licenses×2`/`profiles`/`blacklist` **无设备级测试**；`RecordDetailScreen.kt` **整文件 0 testTag** | 目的地级矩阵近 1/3 屏无自动化锚点 | 按屏补插桩（组件级低成本）+ 详情页补 testTag | M |
| QA-4 | P1 | `McpToolParityTest.kt:58` 冻结名字快照；`24-run-mcp.ps1` 设备端仅触 28/46 | parity 只锁名字不锁语义/默认值/可达性；18 工具设备端从未 call | 「双端同源」无端到端证据 | 工具装配反射快照 + 逐工具设备端调用（`TASK-V45-ACCEPTANCE.md` §6 已列全） | M |
| QA-5 | P1 | `HookerSupport stash`、`HotPathGateConventionTest.kt:110-118`（**只剥 `//` 不剥块注释、只检首条语句、清单外 38 个具体 Hooker 不受约束**）；`FeatureGateBypassTest` 的 `FakePlatform` 不实现 `registerInterventional`/`setThrowable`/`consumed` | 门控扫描面 = 26/63；**所有 `setThrowable` 型干预（ROOT_EXEC/XPOSED_CLASS/ROOT_PACKAGE_MANAGER）JVM 侧零覆盖** | 红线可被绕过；异常型干预只能靠设备验 | 扫面补至全具体 Hooker + 剥块注释 + 「回调体前 N 条语句内」判定；FakePlatform 补 setThrowable 面 | M |
| QA-6 | P2 | `LogRepository.kt:573`（`PAGING_WINDOW_CAP = MAX_RECORDS`）；`RecordFilter.kt:23-33`（多类型选择时 kinds 不收敛）；`AppListenOverride.kt:323-333` | 窗口上限与容量解耦（放宽即复现分页死锁）、多类型跨类同名 kind 不收敛 | 隐性查询语义坑 | cap 由 maxRecords 派生 + 断言；多类型取各类型合法集并集收敛 | S |
| QA-7 | P2 | `testtarget` 与设备矩阵 SKIP 面（预干净模拟器「无脏可掩」、无 VPN 链路、需系统弹窗）；插桩 `assumeTrue×7`（FourScreensNavTest）与通过同色 | 「全绿」不证明能力存在 | SKIP 假绿 = 需求 4 的空转风险 | 环境指纹预登记 + 报告层 SKIP 台账（`ACCEPTANCE` §4.3 判定口径） | S |
| QA-8 | P2 | 插桩无 `@LargeTest`/无超时无失败截图；设备端 DB 升级链（2→14）无仪器化迁移测；脚本硬编码 `D:\leidian\LDPlayer14`、`127.0.0.1:5555`、`ANDROID_ADB_SERVER_PORT=5038`、`D:\Temp\opencode` | 单用例挂起可拖爆窗口；老版本升级崩溃不在矩阵内；换机即瘫 | FailureHandler 截图 + skipped 台账 + prepopulated DB 迁移插桩 + 环境参数化（读 `local.properties`） | M |
| QA-9 | P2 | 21 个 ps1 全量：`Get-Content \| Set-Content` 之外另有引号陷阱——PS 5.1 向**原生命令**传参时剥掉内嵌双引号（`adb shell "su -c \"ls /data/adb\""` 实抵 `su -c ls /data/adb` → 在调用者 cwd 执行 ls）；新写 `tt-env.ps1:95` 指纹探针首跑即因此恒空（已改单引号包参并注释） | 设备探针假阴性（“无框架”实为“探针失效”），会级联成 SKIP 误标 | 脚本层约定：设备侧 su 命令一律 `su -c '...'` 单引号形态；新增“探针自证”断言（指纹 rootUid 没有值 → 阶段 BLOCKED 而非 SKIP）；入 AGENTS 已知坑 | S |

---

## 3. 目标架构定档（V45 终态）

```
L3 消费层      ui/**（ViewModel 注 domain 接口）      mcp/**（tools 注 domain 接口）
                     └──────────── 同一契约面、同一实现、同一默认值与校验 ────────────┘
L2 方法层      domain.record.RecordService（已合格）
               domain.config.ConfigService  domain.feature.FeatureService
               domain.asset.AssetService    domain.server.ServerService(+PortWriteGuard)
               domain.app.*  domain.scope.*  domain.diagnostics.*        ← 纯 Kotlin，零 android.*
                                  ↑ 仅经此箭头
L1 存储与传输实现   data.repo.*Repository(implements)  data.db.*（DAO/实体/SQL/Room，包内私有）
                    data.server.ConfigServer(ingest)   hook.RecordSink/Spool（注入侧写入口 = RecordDraft）
L0 物理存储         Room(WAL,v15) / prefs / spool WAL 文件 / 关键词库表
```

**依赖规则（新门禁可执行化，PLAN Phase 6）**

1. `ui.*`、`mcp.*` 子树禁止出现 `com.lioisme.modev.data.` 的任何全限定引用（文本级扫描，含 `is`/内联 FQN）。
2. `domain.*` 子树禁止 `android.*`；androidx 白名单锁死为 `Immutable` + `paging.*`（D-V45-4）。
3. `data.db.*` 类型只允许出现在 `data.db`/`data.repo` 两包内（DAO 门禁已有，扩展至实体）。
4. 全仓禁止 `typealias` 跨越 `data.* → domain.*` 方向（ISO-2）。
5. 每个写入口（`set*/add*/revoke*/remove*/clear*/upsert*`）调用点必须落在 domain 接口实现内部（`SingleWriteEntryPointTest`）。
6. `RecordService` 每成员在 ui 与 mcp 各 ≥1 消费点，否则必须在单侧白名单显式登记（`ContractMemberConsumptionTest`）。
7. MCP 工具数/名/参数默认值由 registry 反射派生，**任何文档、测试、脚本不得写死数字**（DUAL-2 根因）。
8. 主查询谓词必须有真机 `EXPLAIN QUERY PLAN` 门禁，断言零 `SCAN crypto_records`（ST-1 终裁）。

---

## 4. 需求溯源

| 需求 | 承重条目 | 闭环任务（PLAN） | 防回归门禁 |
|---|---|---|---|
| R1 高效专业 + 上层全基于此 | ST-1..ST-11、ML-1..ML-3 | T1.1–T1.8、T2.1–T2.3 | EXPLAIN 设备门禁、`RecordLimitsCrossCheckTest`、`RecordPagingProbeTest`、`RecordDaoAccessGateTest` |
| R2 数据⟂方法严格隔离 | ISO-1..ISO-9 | T2.1–T2.6 | `LayerDependencyGateTest`、`NoCompatTypealiasGateTest`、`RecordDraftColumnParityTest` |
| R3 UI/MCP 双一等公民 | ML-1、DUAL-1..DUAL-11 | T2.4、T3.1–T3.8 | `McpToolParityTest`(语义版)、`UiMcpCapabilityParityTest`、`PortWriteGuardTest`、`McpToolAssemblyParityTest` |
| R4 全量清单 + 两次一次性通过 | QA-1..QA-8、INJ-1..INJ-11、DUAL-2 | T0.*、T4.*、T5.*、T6.1–T6.4 | `run-acceptance.ps1` 退出码 + SKIP 台账 + `ACCEPTANCE` §8 循环协议 |
| R5 测试任务文档 | — | 本轮交付 `TASK-V45-ACCEPTANCE.md` | 文档↔用例编号对账（`tt-domain-coverage`） |

---

## 5. 「一次性全量通过」的当前阻塞（必须先解除）

| # | 阻塞 | 证据 | 必红面 | 状态 |
|---|---|---|---|---|
| BL-1 | 工具数 43 vs 46 | `McpEndToEndTest.kt:67`、`24-run-mcp.ps1:29` | 前端插桩 + MCP 矩阵 | ✅ T0.1（改 registry 派生 + `server_status.mcp.toolCount` 双通道对账） |
| BL-2 | `masterValue` 类型冲突 → master 三态 MCP 不可用 | `ListenConfigTools.kt:54,79-82` | MCP 配置组 | ✅ T0.2（schema 改 `bool` + `registry_schema_and_handler_agree_on_masterValue_tristate` 锁协议路径） |
| BL-3 | 设备 APK 落后工作树 11 天（0.1.0 / 2026-09-08） | `dumpsys package com.lioisme.modev` | 全部端到端（V44 改动未安装） | 🔵 延至设备窗口（T0.3；代码每改必失效，过早安装 = 白装） |
| BL-4 | 无全量编排真身；26/27 脚本不属任何序列 | `run-all.ps1` 全文、`25-run-v42-stress.ps1:146-160` | 需求 4 达成性 | ✅ T0.4（新 `run-acceptance.ps1` + 20/21/22/23/11 补 FAIL 驱动退出码 + 24 补 `-SkipBuild`） |
| BL-5 | 设备序列策略：`tt-lib.ps1:5` 用 `127.0.0.1:5555`+5038 独立 server，Gradle instrument 见 `emulator-5554`；两序列同时挂载会互相踩踏（AGENTS 实锤坑） | `tt-lib.ps1:4-7`；本机实测：`adb connect 127.0.0.1:5555` 后 `adb devices` 出现双序列同一设备 | 插桩大面积假失败 | ✅ T0.5（`tt-env.ps1` 参数化 + 编排器 `Set-SingleSequence` 在阶段边界自动切序列，实测指纹可达） |
| **BL-6** | **lint 9 errors（`ClassValue` = API 34 vs minSdk 26）** | `lintModernRelease` 输出（2026-09-19） | G2 lint 门禁 + **低版本真机注入即崩**（INJ-12） | ✅ T0.7（`ClassValueCache` 替身 + 5 例契约测 + `MinSdkApiConventionTest` 前置门禁） |

> BL-1/BL-2 = PLAN Phase 0；BL-3/BL-4/BL-5 = PLAN T0.3–T0.5（含「instrument 前 disconnect TCP 序列、脚本阶段用 5038+TCP 单序列」的强制序列策略）。
