# TASK-V41：令牌崩溃/外部访问统一/设置信息架构 全量审计整改任务书

| 项 | 值 |
|---|---|
| 版本 | V41（基于 V40 工作树，2026-09-07） |
| 性质 | 全量代码审计报告 + 分任务实施任务书 |
| 审计方法 | 3 路并行代码审计（MCP 令牌链路 / 服务·诊断·设置结构与文案 / 数据管理·记录列表·数据层），根因结论经源码逐行复核（file:line 均为工作树实位） |
| 决策状态 | 4 项方向性决策按审计推荐默认落定（见 §0.3，维护者可否决）；崩溃根因确定性 ~99% |
| 交付边界 | 本文档为唯一交付物；**实施待维护者确认本文档后另行启动**（T1 为 P0 崩溃修复，可确认后立即先行） |
| 关联文档 | `AGENTS.md`（现行契约）、`TASK-V40-AUDIT.md`（上一轮任务书，本文档部分结论修正其 #19/#26 相关表述） |

---

## 0. 总纲

### 0.1 需求输入（维护者原始 8 项）

| # | 需求 | 类型 |
|---|------|------|
| R1 | 启用令牌认证后整个程序闪退，且每次再进入令牌界面仍闪退，要求修复 | 崩溃修复 |
| R2 | 统一外部访问功能开关；该开关既不属于通信服务也不属于 MCP | 行为/架构 |
| R3 | MCP 端口只显示端口，不必显示 IP | 展示 |
| R4 | 精简开关等功能项目的描述（例：外部访问启用显 `0.0.0.0`、停用显 `127.0.0.1`，言简意赅） | 文案 |
| R5 | 按类型、功能、归属、架构整理分类设置以及二级设置界面的所有功能 | 信息架构 |
| R6 | 数据管理界面不需要「按应用浏览」（主界面应用列表已实现该功能） | 功能收敛 |
| R7 | 所有数据在同一数据库，由统一的、专业的上层筛选器查询；记录等数据是一张大库，可按类型、应用筛选查询 | 数据架构 |
| R8 | 数据管理进入的记录列表与每应用进入的记录列表要求统一、可复用；数据管理记录列表长按 SearchBar 图标直接进入搜索视图，疑似两列表未复用，要求整改 | 复用/行为 |

### 0.2 审计总结论（TL;DR）

- **8 项需求中 6 项存在可指认的代码级违规/差距，共 19 条条目**（P0×1 / P1×2 / P2×14 / INFO×2，§1 逐条列证）；R7、R8 经全链路审计为**合规确认**（§2 单列），不产生重构任务，只补两条门禁/测试锁定。
- **R1 根因已定位为一处字符串资源/调用侧参数错配**：`McpTokensScreen.kt:259` 以 `(String, Int)` 双参调用仅含单整数占位符 `%1$,d` 的 `tokens_status_active` → `IllegalFormatConversionException` 组合期崩溃。启用认证时空集自动派生一枚令牌使 tokenCount≥1 必然触发；prefs 先写盘后置流，持久态合法但每次进入令牌页首帧组合即崩——与「启用后闪退 + 再进仍闪退」逐位吻合。修复面极小（调用侧单参化），但暴露 `McpTokensScreen` **零 UI 测试**的门禁缺口。
- **R2 现状为双键双开关分裂**（`server_external_access` / `mcp_external_access`），统一方案 = 单键单开关 + OR 迁移 + 双服务消费，**服务端（ConfigServer/McpServer）内部零改动**；令牌认证独立性（V40 D-4 语义）原样保持。
- **R3/R4 为展示层精简**：两端口行纯端口化（`settings_port_value` 保留供诊断复制快照）、4 条静态长 hint 改动态有效值或退役。IP 信息由统一外部访问开关副标题唯一承载。
- **R7 证据链**：单库 `ModevDatabase`（记录**单表** `crypto_records` 承载全部 10 类型 + `keyword_library`）+ `RecordFilter`（类型/应用/kind/query/时间/排序 7 维）+ `LogRepository.paged/counts` 唯一查询门面，生产代码 DAO 直调仅装配层 2 处、**零绕过**——「记录是一张大库、按类型/应用筛选」正是现状形态；唯一缺口是无自动化门禁阻止未来旁路。
- **R8 证据链**：数据管理进入的按类型全局列表（`records/type/{recordType}`）与每应用列表（10 条 `records/{type}/{pkg}`）**已收敛到同一渲染管线 `MonitorRecordsContent`**；「长按 SearchBar 图标进入搜索视图」= MD3 `SearchBarDefaults.InputField` 聚焦即展开的标准行为，全项目无任何 onLongClick 展开逻辑，**两条路由行为逐位一致**（旧「长按不展开」定制已随 V40 T5.1 退役并有行为探针锁定）——用户观察到的现象不是复用缺口，是 MD3 标准行为。唯一缺口：全局路由无端到端测试。
- 全部整改**不触注入侧与 wire 协议**（VERSION 恒 4 零感知），MCP 工具面 43 不变；`records/all` 退役决定（V24 G2）不恢复。

### 0.3 已定决策（按审计推荐默认落定，维护者可否决）

| 决策 | 内容 |
|---|---|
| D-1 | **统一外部访问开关放服务页新增「通用」分区**（中性归属，独立于「通信服务」「MCP 服务」两分区）：单一开关同时控制 wire ConfigServer 与 MCP McpServer 的绑定（启用=0.0.0.0 / 停用=127.0.0.1）。键统一：沿用 `SERVER_EXTERNAL_ACCESS` 为唯一事实源；`MCP_EXTERNAL_ACCESS` 退役为迁移输入（OR 合并后删除旧键，幂等）。令牌认证独立开关与判定不变（服务端鉴权仅由 `tokenAuthEnabled` 决定）。 |
| D-2 | **两个端口行均只显示端口**（wire 与 MCP 口径一致）；IP 由统一外部访问开关副标题唯一承载；`settings_port_value`（%1$s:%2$d）保留供诊断复制快照。 |
| D-3 | **长按/聚焦展开搜索保持 MD3 标准**（维持 V40 T5.1 决策：焦点驱动展开为标准行为，不恢复手搓「长按不展开」定制）；以新增全局路由 e2e 测试 + 两入口一致性探针锁定复用，并在本文档 §2.8 留存证据链。 |
| D-4 | **开关副标题动态有效值口径**：外部访问 on→`0.0.0.0` / off→`127.0.0.1`（技术字面量）；MCP 开关 on→`POST /mcp` / off→无副标题；令牌认证 on→`Bearer` / off→「无需令牌」（新短键）。风险语义不再进开关副标题，由令牌页状态行与确认弹窗承载。 |
| D-5 | 交付顺序：先本文档 → 维护者确认 → 实施；P0（T1）确认后立即先行。 |

### 0.4 全局不变量（实施全程不得破坏）

1. **i18n 双语齐备**：新增/改写字符串 en/zh 同步、占位符形态一致（`StringResourceParityTest`）；退役键必须同步删除（防 lint UnusedResources 漂移）；ui 包禁中文字面量（`HardcodedStringConventionTest`——`0.0.0.0`/`POST /mcp`/`Bearer` 等非中文技术字面量不在禁用面）。
2. **查询唯一入口**（AGENTS #9/#14）：全部记录读写仍走 `LogRepository` + `RecordFilter`；本次新增 `RecordDaoAccessGateTest` 门禁后该契约升级为源码扫描强制（T7）。
3. **分页/时间线契约**（AGENTS #10/#14/#15）：`PAGING_WINDOW_CAP`、`cachedIn`、手动索引循环、组头 key 规则、`AnchorScrollRoboTest`/`RecordPagingProbeTest` 全绿不回退。
4. **对话框/Sheet 契约**（AGENTS #27）：新增确认一律 `ModevConfirmDialog`，弹层一律 `ModevSheet*`；本次无新弹层需求。
5. **Appbar 排他契约**（AGENTS #24）：记录列表页 actions 恒空、清空入口在搜索条动作槽——T7 e2e 沿用既有 testTag（`records-clear-type`/`records-clear-confirm`），不新增 Appbar 动作。
6. **MCP 红线**（AGENTS #26）：令牌值恒不进 MCP 工具输出与诊断快照；工具面 43 冻结（`McpToolParityTest`）；MCP 与 wire 独立生命周期；服务端鉴权仅由 `tokenAuthEnabled` 决定；wire VERSION 恒 4。
7. **双域词汇表纪律**：退役的旧词（MCP 外部访问开关、wire 外部访问开关、按应用浏览、拦截与伪装分区名等）按需列入禁用旧词。
8. **lint 门禁**：`lintModernRelease` 0 error、不新增 warning（基线 49 warning）；退役字符串键随删抵消改动增量。

---

## 1. 违规清单（全量，file:line 实证）

严重度：P0 = 崩溃/数据丢失；P1 = 阻塞验收的正确性/门禁缺陷；P2 = 应修复的质量缺陷；INFO = 合规确认结论，无违规。

### 1.1 R1 令牌功能崩溃（4 条）

**根因触发链（V1.1）**：启用令牌认证 → `SettingsRepository.setMcpTokenAuth(true)`（`SettingsRepository.kt:362-368`）在空集时自动派生一枚令牌（:363-365）→ 进入令牌页首帧组合 `AuthStatusLine(authEnabled=true, tokenCount=1)` → `McpTokensScreen.kt:259` `stringResource(R.string.tokens_status_active, on, tokenCount)` 传参 `(String, Int)`，而资源仅含单占位符 `%1$,d`（`values/strings.xml:444` en "On · %1$,d token(s)"；`values-zh/strings.xml:436` zh "已启用 · %1$,d 枚令牌"）→ `String.format` 将 `%1$,d` 绑定到第 1 参 String → `IllegalFormatConversionException: d != java.lang.String`，组合期未捕获 → 整 App 闪退。`persistMcpTokenAuth`/`persistMcpTokens`（:382-395）先写盘后置流，崩溃不回滚持久态（`mcp_token_auth=true` + 1 枚令牌，该状态本身合法）→ 之后每次进入令牌页首帧组合即复现同一异常；服务页只渲染 plurals（`ServicesScreen.kt:143-147`，用法正确）不崩，故 App 能启动、唯令牌页崩。

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V1.1 | P0 | `tokens_status_active` 资源占位符（1 个 `%d`）与调用侧传参（String,Int）错配 → 组合期 `IllegalFormatConversionException` | `ui/settings/McpTokensScreen.kt:259`；`values/strings.xml:444`、`values-zh/strings.xml:436` | 调用侧收敛为单参 `stringResource(R.string.tokens_status_active, tokenCount)`（资源形态已足够精简，保留）；文内「已启用」前缀由各语言资源字面承载，无需运行时传状态词 |
| V1.2 | P1 | `McpTokensScreen` 无任何 UI 测试（test/androidTest 均无），V1.1 无人拦截；相邻 ServicesScreen 仅测开关行为（:98-129），不触状态行 | 测试目录 grep `McpTokens` 仅命中 `McpTokensViewModel` 工厂接线；`McpTokenEntryTest`（JVM）、`SettingsRepositoryMcpTokenTest`（仓库层）、`McpServerLifecycleTest`（服务端）均不渲染该屏 | 新增 Robolectric 渲染测试（模式同 `AnchorScrollRoboTest` 全装配驱动）：认证停用 / 认证开+≥1 令牌 / 认证开+0 令牌 三态断言 |
| V1.3 | P2 | 状态行另两分支传参被静默忽略（资源无占位符却传实参）：`tokens_status_deny_all, on`（:257）、`tokens_status_off, off`（:262），`off` 局部变量随之成为死值 | `McpTokensScreen.kt:255-263` | 两分支去实参，删 `off` 局部量（`mcp_state_off` 资源保留，其他消费点不受影响） |
| V1.4 | P2 | `mcpRestarter?.invoke()` 在调用方线程同步执行：VM 为 `viewModelScope.launch`（Main.immediate）→ `ModevApp.kt:166-176` 回调 → `McpServer.restart()` → NanoHTTPD `start()` 主线程开 ServerSocket；被 `McpServer.start()` catch-all（`McpServer.kt:85-91`）吞掉 → 主线程 jank / StrictMode 违规 / `NetworkOnMainThreadException` 被吞后**静默 bind 失败（服务端掉线、bindError 置位）**。wire 侧对照正确模式：`SettingsRepository.kt:176` `scope.launch { server.restart() }` | `SettingsRepository.kt:332`（setMcpEnabled）、`:342`（setMcpPort）、`:354`（setMcpExternalAccess）、`:367`（setMcpTokenAuth）；`ModevApp.kt:166-176` | 4 处 `mcpRestarter?.invoke()` 统一包 `scope.launch`（私有 `restartMcpAsync()` 收敛）；随 T2 同提交 |

### 1.2 R2 外部访问开关分裂（2 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V2.1 | P1 | 外部访问为**两个独立偏好键 + 两个独立 UI 开关 + 两条独立重启链**，无统一事实源：wire 键 `SERVER_EXTERNAL_ACCESS`（`core/model/ConfigKeys.kt:30`）↔ UI `services-wire-external`（`ServicesScreen.kt:88-95`，绑定 `setWireExternalAccess` → `SettingsRepository.kt:169-177`）；MCP 键 `MCP_EXTERNAL_ACCESS`（`ConfigKeys.kt:49`）↔ UI `services-mcp-external`（`ServicesScreen.kt:113-121`，绑定 `setMcpExternalAccess` → `SettingsRepository.kt:349-355` → `mcpRestarter`）。且 MCP 行 `enabled=mcpEnabled`、wire 行无条件渲染——同一语义两处形态还不对称 | 键定义两处；UI 两行；服务端消费两态（`ConfigServer.kt:131-134` bind 前读 `externalAccess`；`McpServer.kt:71-72` `start()` 按 `externalAccess` 选 bind 地址） | 单键单开关（D-1）：统一键 + OR 迁移删旧键 + 服务页「通用」分区单开关 `services-external-access`；服务端内部字段与判定零改动（双服务消费同一偏好值） |
| V2.2 | P2 | 契约文档失配：AGENTS 强约束 #19（:228-238）仍写「诊断页开关 testTag `diagnostics-bind-all`」——主源零命中（V39 已迁服务页、tag 为 `services-wire-external`），`DiagnosticsScreenTest.kt:85` 已以反向断言锁定「诊断页无该开关」；词汇表「外部访问」条目同源失配。T2 统一后契约需整体重写 | `AGENTS.md` 强约束 #19、词汇表「外部访问 External Access」行 | 随 T2 重写 #19 + 词汇表条目；`diagnostics-bind-all` 入禁用旧词 |

### 1.3 R3 端口行显示 IP（2 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V3.1 | P2 | MCP 端口行绑定时显示 `IP:端口`（经 `settings_port_value` = `%1$s:%2$d` 渲染 `127.0.0.1:1510`），未绑定时回落纯端口字面量——**同一行两种口径漂移** | `ServicesScreen.kt:131-138`（`mcpAddress?.let { stringResource(R.string.settings_port_value, it, mcpPort) } ?: mcpPort.toString()`） | 统一 `mcpPort.toString()` 纯端口 |
| V3.2 | P2 | wire 端口行恒显示 `IP:端口`（`wireListeningAddress` 未绑定时也回退 `127.0.0.1` 常量）——与「IP 由统一开关承载」的新口径重复（D-2）；且与 MCP 行修复后口径不一致 | `ServicesScreen.kt:96-101`；`ServicesViewModel.kt:36-39`（回退 `ConnectionMonitor.BIND_LOOPBACK`） | 统一 `wirePort.toString()` 纯端口；`settings_port_value` 保留（诊断复制快照 `DiagnosticsReport.kt:87-88`、`:107` 仍消费） |

### 1.4 R4 开关描述冗长（4 条）

目标口径（D-4）：开关副标题 = 动态有效值优先（zh ≤12 字 / en ≤28 字符）；风险解释退出副标题，由状态行/确认弹窗承载；双语 parity 保持。

| # | 严重度 | 键 | 现状 en / zh | 证据 | 目标 |
|---|---|---|---|---|---|
| V4.1 | P2 | `settings_external_access_hint` | "Listens on 0.0.0.0 for LAN access." / "监听 0.0.0.0,供局域网连接。" | `values/strings.xml:415`、`values-zh/strings.xml:408`；引用 `ServicesScreen.kt:91` | **退役**——副标题改动态绑定地址 `0.0.0.0`/`127.0.0.1`（随 T2 统一开关） |
| V4.2 | P2 | `mcp_enabled_hint` | "Agent endpoint, loopback by default." / "Agent 端点,默认仅本机访问。" | `values/strings.xml:423`、zh:416；引用 `ServicesScreen.kt:108` | on→`POST /mcp`（技术字面量）/ off→无副标题 |
| V4.3 | P2 | `mcp_external_access_hint` | "Binds 0.0.0.0; unauthenticated without token auth." / "绑定 0.0.0.0;未开令牌认证时局域网可直连。"（4 条 hint 中最重的风险双解释） | `values/strings.xml:425`、zh:418；引用 `ServicesScreen.kt:116` | **退役**（随统一开关，行本体删除） |
| V4.4 | P2 | `mcp_token_auth_hint` | "All requests, even loopback, need a token." / "所有请求(含回环)均需令牌。" | `values/strings.xml:429`、zh:422；引用 `ServicesScreen.kt:125` + `McpTokensScreen.kt:152` | on→`Bearer` / off→新键 `mcp_token_auth_off_hint`（en "No token needed" / zh "无需令牌"） |

注：令牌页三态状态行（`tokens_status_active/deny_all/off`）文案已足够精简，仅修 V1.1/V1.3 参数错配；令牌页空态 `tokens_empty_auth_on` 与撤销确认 `token_revoke_confirm_msg` 属状态/确认文案（非开关描述），保留。

### 1.5 R5 设置信息架构（2 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V5.1 | P2 | 根设置页分区按历史堆叠、未按归属分类：「显示」「语言与地区」两个独立分区（同为通用偏好）；「功能」分区混杂数据管理/服务/诊断三种归属（数据 vs 配置面 vs 观测面）；「拦截与伪装」分区名未对齐双域词汇表「功能域 Feature Domain」 | `ui/settings/SettingsScreen.kt:143-232`（分区序：显示 :143、语言 :169、功能 :182、拦截与伪装 :201、关于 :224） | 分区重组为**通用/数据/服务/功能域/关于** 五区（目标树见 §2.6），行与路由/回调零变化 |
| V5.2 | P2 | 服务页无中性归属分区：外部访问开关（语义上双服务共享）寄生在「通信服务(注入侧)」分区内，与 R2「既不属于通信服务也不属于 MCP」直接冲突；另 MCP 绑定错误行标题复用 `mcp_enabled`（标题「MCP 服务」值「绑定失败：…」），语义怪异 | `ServicesScreen.kt:87-102`（wire 分区含外部访问）、`:151-158`（绑定错误行） | 新增页首「通用」分区承载统一开关（随 T2）；绑定错误行标题改用独立键（随 T2 顺带，新键 `mcp_bind_error_title`） |

### 1.6 R6 数据管理「按应用浏览」冗余（1 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V6.1 | P2 | 数据管理页「按应用浏览」区与主界面应用列表功能重复：数据源 = `packageCounts.filterValues { it > 0 }`（仅有记录的应用），行形态 AppBrowseRow（AppIcon+应用名+包名+计数+chevron），跳转目标 `apps/{pkg}/monitor`（`ModevNav.kt:584-586`）——与主界面应用列表行点击目标完全一致（`ModevNav.kt:141-143`）；且主界面列表还承担全量目录/作用域管理/启动等更全职责。连带面：`DataSettingsViewModel` packageCounts/packageLabels 收集链、空态串 `apps_browse_empty`、分区标题 `settings_apps_browse_section`、`DataSettingsScreenTest` 应用行断言 | `ui/settings/DataSettingsScreen.kt:124-181`（分区 :125、数据源 :126-131、空态 :132-170、行调用 :172-180）、`:226-270`（AppBrowseRow 定义） | 整区退役 + AppBrowseRow 组件删除 + VM 接线拆除 + 两字符串退役 + 测试断言更新；**仓库方法 `observeRecordCounts` 保留**（`LogRepository.kt:516`，`AppsViewModel.kt:175,208` 列表徽章仍在消费） |

### 1.7 R7 单库统一筛选器（1 条合规确认 + 1 条门禁缺口）

| # | 严重度 | 结论/差距 | 证据 |
|---|---|---|---|
| V7.1 | INFO | **现状已合规**——「所有数据在同一数据库、由统一上层筛选器查询」正是现行架构：①单库 `ModevDatabase`（`data/db/ModevDatabase.kt:50-59`），记录**单表** `crypto_records` 承载全部 10 类型（`recordType` 列 + `kind` 列跨枚举共用，复合索引 `(recordType,kind)`/`(recordType,packageName,timestamp)`），仅另有一张功能域资产表 `keyword_library`；②统一上层筛选器 = `RecordFilter`（`domain/record/RecordFilter.kt:14-34`：recordTypes/packages/kinds/operations/query/timeRange/sort 七维 + `normalize()` 收敛）→ `LogRepository`（`RecordService` 门面，paged/counts/queryPage/deleteByFilter 全走 `CryptoRecordDao.WHERE_FILTER` 哨兵单口径）；③生产代码 DAO 直调仅装配层 2 处（`ModevApp.kt:69` 注入 LogRepository、`:94` 关键词库），UI/VM/REST/MCP **零绕过** | 如上 file:line；`PackageBoundaryTest`（ui 禁 import data.db）+ `McpToolParityTest` B1（mcp 禁 import data.db）已有 import 级约束 |
| V7.2 | P2 | 门禁缺口：现有关门禁均为 import 级扫描，**无调用级门禁**阻止未来在白名单外直接持有/调用 `cryptoRecordDao()`/`keywordLibraryDao()`（旁路 RecordFilter） | 全项目 `cryptoRecordDao()` 调用点仅 `ModevApp.kt:69`（+`LogRepository.kt`/测试），无强制保障 | 新增 `RecordDaoAccessGateTest` 源码扫描门禁（T7） |

### 1.8 R8 记录列表复用（1 条合规确认 + 1 条缺口）

| # | 严重度 | 结论/差距 | 证据 |
|---|---|---|---|
| V8.1 | INFO | **两条入口已复用，行为逐位一致**——①路由落地：`records/type/{recordType}`（`ModevRoutes.kt:59`，目的地 `ModevNav.kt:591-614`）→ `GlobalTypeRecordsScreen`（`MonitorRecordsScreen.kt:280-295`）；10 条 `records/{type}/{pkg}`（`ModevRoutes.kt:37-54`，目的地 `ModevNav.kt:267-486`）→ 10 个薄壳 Screen（`:104-272`）；二者**全部收敛到同一渲染管线 `MonitorRecordsContent`**（`:299-494`），差异仅 4 个参数（placeholder/kinds/showPackage/clearMessageRes）。②单源组件：同一 `SearchInputField`（折叠态，`SearchInputComponents.kt:41-96`）+ 同一 `ExpandedFullScreenSearchBar`（展开态 :436-479）+ 同一 `ClearTypeAction`（`records-clear-type` → `records-clear-confirm`，:501-514/:482-493）+ 同一 `RecordTimelineList`→`RecordRow`（`record-row` tag，`RecordTimelineList.kt:186-198`）+ 同一 VM 级 `listState`（`RecordsViewModel.kt:51`）+ 同一 `rememberSearchController`（`MonitorRecordsScreen.kt:313`）。③「长按 SearchBar 图标进入搜索视图」= MD3 `SearchBarDefaults.InputField`（`SearchInputComponents.kt:57`）**聚焦即展开**标准行为：全项目 `onLongClick/combinedClickable/longClick` 仅命中行长按复制（`RecordRow.kt:135-141`）与 tooltip（`TipIconButton.kt:37-41`），**无任何「长按展开搜索」定制逻辑**；手搓定制（含「长按不展开」）已随 V40 T5.1 退役，行为探针 `SearchInputComponentsTest.kt:122-133`（`tap_expandsViaFocusIntent`）锁定标准形态。两条路由共用同一状态机，不可能分叉 | 如上 file:line |
| V8.2 | P2 | 测试缺口：`records/type/{recordType}` 全局路由在 androidTest **无端到端覆盖**（`records/type|GlobalTypeRecords|data-type-` 于 androidTest 零命中）——用户对复用的疑虑正源于此不可验证性；全局列表的 `records-clear-type` 清空流程亦无插桩（仅 VM 层 `RecordsViewModelGlobalTest`） | androidTest 目录 grep | 新增全局路由 e2e + 两入口一致性探针（T7） |

### 1.9 违规统计

| 需求 | 条目数 | P0 | P1 | P2 | INFO |
|---|---|---|---|---|---|
| R1 | 4 | 1 | 1 | 2 | — |
| R2 | 2 | — | 1 | 1 | — |
| R3 | 2 | — | — | 2 | — |
| R4 | 4 | — | — | 4 | — |
| R5 | 2 | — | — | 2 | — |
| R6 | 1 | — | — | 1 | — |
| R7 | 2 | — | — | 1 | 1 |
| R8 | 2 | — | — | 1 | 1 |
| **合计** | **19** | **1** | **2** | **14** | **2** |

---

## 2. 任务分解（实施顺序 T1→T8）

> 每任务含：目标 / 改动面 / 实施要点 / 测试影响 / 契约同步 / 验收标准。顺序按依赖与风险排列：P0 崩溃先行（T1），架构统一（T2），展示精简（T3/T4，依赖 T2），收敛与重排（T5/T6），门禁锁定（T7），收尾（T8）。**每任务一个独立提交，契约同步随行为变更同提交落地**。

### T1 令牌页崩溃修复 + 状态行收口（R1/V1.1-V1.3）— 复杂度 S ✅ **已完成（V41 实施备注：`AuthStatusLine` 三分支全部单参化——`tokens_status_active` 仅传 tokenCount、deny_all/off 去实参、删 `on`/`off` 局部量；资源双语形态零改动（`mcp_state_on/off` 仍由诊断快照消费保留）；组件可见性 private→internal 供测试装配；KDoc 沉淀「传参与占位符逐位对齐」契约。新增 `McpTokensScreenStateTest`（Robolectric @Config(sdk=34)）三态：停用 / **启用+2 令牌（V1.1 崩溃路径，旧代码在此抛 IllegalFormatConversionException）** / 启用+0 令牌空集全拒。定向测试：`ui.settings.*` 包 BUILD SUCCESSFUL，McpTokensScreenStateTest 3/3 绿）**

- **改动面**：`ui/settings/McpTokensScreen.kt`；新增 `app/src/test/java/com/lioisme/modev/ui/settings/McpTokensScreenStateTest.kt`（Robolectric）。
- **实施要点**：
  1. `:259` 改单参 `stringResource(R.string.tokens_status_active, tokenCount)`（资源 `tokens_status_active` 双语形态保留不动——「On ·/已启用 ·」前缀由各语言资源字面承载，占位符与传参逐位对齐）；
  2. `:257`（deny_all）、`:262`（off）两分支去实参；删 `off` 局部量（:237）与 `mcp_state_off` 该点消费（资源保留）；
  3. 新增 Robolectric 渲染测试三态（模式同 `AnchorScrollRoboTest` 全装配驱动）：认证停用 → `tokens_status_off` 文案渲染；认证开 + ≥1 令牌 → `tokens_status_active` 渲染（**本用例对 V1.1 构成回归门禁**——旧代码在此态抛 `IllegalFormatConversionException`）；认证开 + 0 令牌 → `tokens_status_deny_all` 渲染且状态行 error 色。
- **测试影响**：纯新增；`SettingsRepositoryMcpTokenTest`/`McpServerLifecycleTest` 回归即可。
- **契约同步**：无强约束变更；可在「已知工具链坑」沉淀一条：`stringResource` 传参与资源占位符必须逐位对齐——多余参数被 `String.format` 静默忽略，类型错配即组合期 `IllegalFormatConversionException`，parity 测试拦不住调用侧，依赖 UI 渲染测试。
- **验收**：启用认证（空集自动派生一枚）→ 进入令牌页正常渲染、三态状态行正确；全量单测绿；崩溃路径无法复现。

### T2 统一外部访问开关 + MCP 重启线程修复（R2/V2.1-V2.2 + R1/V1.4）— 复杂度 M ✅ **已完成（V41 实施备注：①键统一——`SERVER_EXTERNAL_ACCESS` 升级为统一键 KDoc，`MCP_EXTERNAL_ACCESS` 退役为迁移输入；②仓库单写路径 `setExternalAccess`（持久化 → StateFlow → wire volatile 置位 + 异步 restart → `restartMcpAsync()` 异步回调 MCP）；③V1.4 修复——`setMcpEnabled/setMcpPort/setMcpTokenAuth` 三处同步 `mcpRestarter?.invoke()` 收敛进 `restartMcpAsync()`（scope.launch，恒非主线程）；④init OR 迁移（旧键读后删除、幂等、合并结果落统一键）；⑤退役 `_mcpExternalAccess` 流/`setMcpExternalAccess`/`setServerExternalAccess` 公开面，ModevApp/ServerTools/DiagnosticsViewModel/ServicesViewModel 全部改读统一流；⑥服务页新增「通用」分区单开关 `services-external-access`（副标题动态绑定地址经 VM `externalAccessAddress`），退役 `services-wire-external`/`services-mcp-external` 两行；⑦绑定错误行独立标题 `mcp_bind_error_title`；⑧MCP 工具 `server_set_external_access` 描述改统一语义（名称冻结不动，43 工具面零漂移）；⑨新增 `SettingsRepositoryExternalAccessTest` 5 用例（仅旧键合并/双真/双假/幂等重放/写路径不触令牌态）+ 既有仓库测试改统一 API；⑩AGENTS #19 全文重写、词汇表「外部访问」「MCP 服务」条目改写、禁用旧词追加。定向测试：data.repo+i18n+architecture+mcp 四包 BUILD SUCCESSFUL（ExternalAccessTest 5/5、McpTokenTest 9/9、RepositoryTest 6/6、McpToolParityTest 3/3、McpServerLifecycleTest 19/19）；主源+androidTest 源编译通过，退役 API 全库零残留）**

- **改动面**：`core/model/ConfigKeys.kt`、`data/repo/SettingsRepository.kt`、`ModevApp.kt`、`ui/settings/ServicesScreen.kt`、`ServicesViewModel.kt`、`values/strings.xml`、`values-zh/strings.xml`、`SettingsRepositoryMcpTokenTest.kt`（或新增 `SettingsRepositoryExternalAccessTest`）、`ServicesScreenTest.kt`、`AGENTS.md`。
- **实施要点**：
  1. **键统一（D-1）**：`SERVER_EXTERNAL_ACCESS`（`ConfigKeys.kt:30`）升级为唯一事实源（KDoc 改写「统一外部访问：wire + MCP 双服务消费」）；`MCP_EXTERNAL_ACCESS`（:49）退役为迁移输入——`init` 读到非空时 OR 合并（`统一值 = server键 || mcp键`）落统一键后**删除旧键**（幂等：旧键不存在即跳过）；
  2. **仓库单一写路径** `setExternalAccess(enabled)`：持久化 → StateFlow → wire `server.externalAccess` volatile 置位 → `scope.launch { mcpRestarter?.invoke() }`（mcpRestarter 回调回填 MCP 侧 `externalAccess` 后 restart，`ModevApp.kt:166-176` 既有逻辑复用）；退役 `setServerExternalAccess`/`setMcpExternalAccess` 公开面；
  3. **V1.4 修复同体**：`setMcpEnabled`/`setMcpPort`/`setMcpTokenAuth` 中 3 处 `mcpRestarter?.invoke()`（:332/:342/:367）统一收敛到私有 `restartMcpAsync()`（`scope.launch` 包裹）；
  4. **UI**：ServicesScreen 页首新增「通用」分区（标题新键 `services_common_section`，en "Common" / zh "通用"），单开关 `SettingsToggleItem`：testTag `services-external-access`、副标题 = 动态绑定地址（启用 `0.0.0.0` / 停用 `127.0.0.1`，技术字面量，与 `ConnectionMonitor.BIND_ALL_INTERFACES/BIND_LOOPBACK` 常量同形）；退役 `services-wire-external`（:88-95）与 `services-mcp-external`（:113-121）两行；MCP 绑定错误行（:151-158）标题改独立新键 `mcp_bind_error_title`（V5.2 附带）；
  5. **VM**：`ServicesViewModel` 出口收敛为单一 `externalAccess`/`setExternalAccess`，退役 wireExternalAccess/mcpExternalAccess/setWireExternalAccess/setMcpExternalAccess 四出口；`ServicesScreen.kt:44-45` 头注释同步；
  6. **服务端零改动**：`ConfigServer.externalAccess`（:49-51）与 `McpServer.externalAccess`（:34-42）字段、epoch restart、鉴权判定（`McpServer.kt:124-132` 仅 `tokenAuthEnabled`）全部不动——统一发生在偏好/UI 层。
- **测试影响**：
  - 新增迁移用例：①prefs 仅 `mcp_external_access=true` → init 后统一键 true、旧键清除；②两旧键均 false/缺失 → 统一键 false；③幂等（二次 init 不再改写）；④OR 合并（两旧键均 true → true）；
  - `SettingsRepositoryMcpTokenTest` 现有「外部访问与认证解耦」用例（:95/:104）改经统一开关断言（语义不变：开关切换不触碰令牌/认证态）；
  - `ServicesScreenTest`（插桩）：`services-wire-external`/`services-mcp-external` 退役断言 + 统一开关行为用例（切换 → 两服务地址同变，经 `wireMonitor.boundAddress`/`mcpBoundAddress` 观测）；`DiagnosticsScreenTest.kt:85` 反向断言保持；
  - `McpServerLifecycleTest`/ConfigServer 矩阵回归（外部无认证直通 :244、外部+认证 :260、空集全拒 :312 等语义不变）。
- **契约同步**：AGENTS 词汇表「外部访问」条目整体改写（单一开关/服务页通用分区/双服务消费/OR 迁移）；强约束 **#19 全文重写**（统一键 `server_external_access`、写路径 `setExternalAccess`、异步重启链、`services-external-access` tag、禁用旧词追加「MCP 外部访问开关」「wire 外部访问开关」「diagnostics-bind-all」「mcp_external_access」）；#26 MCP 条目同步一行（键退役）。
- **验收**：单开关切换 → 诊断复制快照两服务行同址（同绑 0.0.0.0 或 127.0.0.1）；旧双键用户迁移正确且幂等；鉴权仍仅由 tokenAuthEnabled 决定；重启链全程非主线程（StrictMode 无违规）。

### T3 端口行纯端口化（R3/V3.1-V3.2）— 复杂度 S（依赖 T2）✅ **已完成（V41 实施备注：随 T2 的 ServicesScreen 重写一并落地——wire 端口行 value 改 `wirePort.toString()`、MCP 端口行删 `mcpAddress?.let { settings_port_value(...) }` 口径漂移改 `mcpPort.toString()`；VM `mcpListeningAddress` 出口随行退役、`wireListeningAddress` 保留供插桩绑定期望轮询与诊断观测语义；`settings_port_value` 保留——诊断复制快照（DiagnosticsReport）仍消费 `IP:端口 (scope)` 完整信息。验证：主源+androidTest 编译通过，ServicesScreenTest 无端口文案断言需同步）**

- **改动面**：`ui/settings/ServicesScreen.kt`。
- **实施要点**：wire 行（:96-101）value 改 `wirePort.toString()`；MCP 行（:131-138）统一 `mcpPort.toString()`（绑定态不再经 `settings_port_value` 拼 IP）；IP 信息由 T2 统一开关副标题唯一承载；`settings_port_value` **保留**——诊断复制快照（`DiagnosticsReport.kt:87-88`、`:107`）仍消费完整 `IP:端口 (scope)` 信息。
- **测试影响**：`ServicesScreenTest` 端口行文案断言（如有）同步；诊断页快照断言不动。
- **契约同步**：词汇表「外部访问」条目补一句「端口行仅显示端口；IP:端口完整形态仅存于诊断复制快照」。
- **验收**：两端口行均仅显示数字；两行口径一致；诊断快照不回归。

### T4 开关副标题动态精简（R4/V4.1-V4.4）— 复杂度 S（依赖 T2）✅ **已完成（V41 实施备注：①`settings_external_access_hint`/`mcp_external_access_hint` 随 T2 统一开关行退役；②`mcp_enabled_hint` 退役——开态副标题改技术字面量 `POST /mcp`（文件级常量），关态空副标题（`SettingsToggleItem.subtitle: String = ""` 路径）；③`mcp_token_auth_hint` 退役——开态改 `Bearer`（internal 常量 `TOKEN_AUTH_BEARER_SUBTITLE`,服务页/令牌页同源共用）、关态新键 `mcp_token_auth_off_hint`（en "No token needed" / zh "无需令牌"）双语齐备；④风险语义收口：空集全拒由令牌页 error 色状态行承载。验证：退役键全库零代码引用；i18n parity + architecture 门禁 + McpTokensScreenStateTest 定向绿）**

- **改动面**：`ui/settings/ServicesScreen.kt`、`values/strings.xml`、`values-zh/strings.xml`。
- **实施要点**（按 §1.4 目标表执行）：
  1. `settings_external_access_hint` 退役（副标题改动态地址，随 T2 同体落地则本条归并 T2）；
  2. `mcp_enabled_hint` 改动态：on→`"POST /mcp"`（技术字面量）/ off→`""`（`SettingsToggleItem.subtitle: String = ""` 支持空串无副标题）；
  3. `mcp_external_access_hint` 退役（随统一开关行删除）；
  4. `mcp_token_auth_hint` 改动态：on→`"Bearer"` / off→新键 `mcp_token_auth_off_hint`（en "No token needed" / zh "无需令牌"，双语齐备过 parity）；令牌页（`McpTokensScreen.kt:152`）同键消费同步；
  5. 风险语义收口：空集全拒警示由令牌页状态行（error 色）承载、无认证外部访问风险由撤销/状态文案承载——开关副标题零风险长文。
- **测试影响**：`StringResourceParityTest`（退役 2-3 键、新增 1-2 键双语同步）；`ServicesScreenTest` 文案断言同步；`HardcodedStringConventionTest` 不受影响（技术字面量非中文）。
- **契约同步**：无强约束变更；词汇表「令牌管理」条目如引 hint 文案同步。
- **验收**：服务页全部开关副标题为动态有效值或空；退役键全库零引用、lint 无新增 UnusedResources；parity 绿。

### T5 数据管理退役「按应用浏览」（R6/V6.1）— 复杂度 S ✅ **已完成（V41 实施备注：①`DataSettingsScreen` 退役按应用浏览分区(:124-181)与 `AppBrowseRow` 组件、`onOpenApp` 形参,页面收敛为「记录分类/存储/数据」三区;②`DataSettingsViewModel` 退役 packageCounts/packageLabels 收集链与 `AppCatalogSource` 依赖(工厂签名同步收窄;仓库 `observeRecordCounts` 保留——`AppsViewModel` 徽章消费中);③`ModevNav` 拆除 `onOpenApp` 接线;④字符串退役:`settings_apps_browse_section`/`apps_browse_empty` 双语删除(唯一引用已核实);⑤`DataSettingsScreenTest` 重写计数行用例为退役红线断言(`data-app-*`==0、应用行文本零命中),保留危险行/清空全部/存储同源用例。验证:主源+androidTest 编译通过(实施插曲:python 运行时损坏 exit 49 复现,按既训改用 Edit 工具),i18n+architecture 门禁定向绿)**

- **改动面**：`ui/settings/DataSettingsScreen.kt`、`DataSettingsViewModel.kt`、`values/strings.xml`、`values-zh/strings.xml`、`app/src/androidTest/.../DataSettingsScreenTest.kt`、`ui/ModevNav.kt`（onOpenApp 接线拆除）。
- **实施要点**：
  1. 删分区（:124-181）与 `AppBrowseRow` 组件（:226-270）；`onOpenApp` 回调与 `ModevNav.kt:584-586` 接线拆除；
  2. `DataSettingsViewModel`：packageCounts/packageLabels 收集链退役——**仓库方法 `LogRepository.observeRecordCounts`（:516）保留**（`AppsViewModel` 列表徽章消费中），typeCounts/totalRecords/storageBytes 链路不动；
  3. 字符串退役：`settings_apps_browse_section`、`apps_browse_empty`（全库唯一引用核实后删）；`settings_total_records_value` 保留（类型计数行 :114-117 / 总记录行 :196 仍用）；
  4. 数据页收敛为「记录分类 / 存储 / 数据」三区；应用维度浏览唯一入口 = 主界面应用列表（全量目录 + 作用域管理 + 启动）。
- **测试影响**：`DataSettingsScreenTest` 应用行断言用例退役，保留/重写计数行可点、`clear-all` 确认、存储同源用例；`FourScreensNavTest` 如覆盖数据页应用行同步。
- **契约同步**：AGENTS #17 补一句「数据管理页按应用浏览区已退役（V41），应用维度浏览唯一入口 = 主界面应用列表」；禁用旧词追加「按应用浏览」。
- **验收**：数据页三区结构；`apps_browse`/`data-app-`/`AppBrowseRow` 全库零代码引用；仓库 observeRecordCounts 无回归（AppsViewModel 徽章用例绿）。

### T6 根设置页信息架构重排（R5/V5.1）— 复杂度 M ✅ **已完成（V41 实施备注：根页五分区按归属归类——「通用」(显示/主题色/语言与地区,三行并区)、「数据」(数据管理)、「服务与诊断」(服务+诊断)、「功能域」(关键词库/伪装画像/检测黑名单,对齐双域词汇表 Feature Domain)、「关于」(沿用);行内容/路由/回调零变化,仅分区容器重组。资源:新增 `settings_section_general/data/services/feature_domain` 双语 4 键,退役 `settings_display_section`/`settings_language_section`/`settings_features_section`/`settings_interception_section` 4 键(全库唯一消费点已核实并同步);`SettingsComponentsTest` 替身标题改引新键。验证:退役键全库零引用;主源+androidTest 编译通过;i18n parity + architecture 门禁定向绿）**

- **改动面**：`ui/settings/SettingsScreen.kt`（:143-232 分区结构）、`values/strings.xml`、`values-zh/strings.xml`（新分区标题键）、`FourScreensNavTest`/`LanguageHotSwitchTest`（如锁分区结构）。
- **实施要点（目标树，按归属分类）**：
  | 新分区 | 标题键（en / zh） | 行 |
  |---|---|---|
  | 通用 | `settings_section_general`（General / 通用） | 显示、语言与地区 |
  | 数据 | `settings_section_data`（Data / 数据） | 数据管理 |
  | 服务 | `settings_section_services`（Services / 服务） | 服务（配置面）、诊断（观测面） |
  | 功能域 | `settings_section_feature_domain`（Feature domain / 功能域） | 关键词库、伪装画像、检测黑名单 |
  | 关于 | `settings_about_section`（沿用） | 关于 |
  1. 行内容、路由、回调零变化（仅分区容器重组）；分区组件复用 `SettingsSection`；每分区前 `Spacer(24.dp)` 节奏保留；
  2. 「拦截与伪装」分区名退役 → 「功能域」（对齐双域词汇表 `Feature Domain`）；`settings_features_section`/`settings_display_section`/`settings_language_section`/`settings_interception_section` 四键随重组退役或合并（以最终结构为准，全库唯一引用核实后删）；
  3. 备选口径（可维护者否决）：保留「拦截与伪装」用户化措辞，仅做分区归并——若采纳，`settings_interception_section` 保留。
- **测试影响**：`LanguageHotSwitchTest`（外观区渲染路径）回归；`FourScreensNavTest` 设置→数据管理往返回归；新增分区结构断言（可选：五区标题存在性）。
- **契约同步**：AGENTS #28 服务/诊断拆分描述补根页分区树；词汇表「功能域」条目补 UI 分区锚点。
- **验收**：根页五分区按归属归类、无跨类混排；全部入口可达性回归绿；parity 绿。

### T7 门禁与锁定（R7/V7.2 + R8/V8.2）— 复杂度 M ✅ **已完成（V41 实施备注：①`RecordDaoAccessGateTest` 源码扫描门禁落位——`cryptoRecordDao()/keywordLibraryDao()` 调用白名单 = ModevApp(装配)/LogRepository/KeywordLibraryRepository/ModevDatabase(Room 声明点),越界即红;自验用例锁定白名单链路非空(ModevApp 持 accessor 接线、两仓库构造器消费 DAO 类型),防门禁空转假绿。实施修正:两仓库实为构造器注入消费(非直调 accessor),自验断言按类型引用校验。②新增 `GlobalTypeRecordsTest` 端到端(真机 x86_64 定向):根页设置 → 数据管理 `data-type-crypto` → 全局列表,断言复用一致性(Appbar 内仅 SearchBar/返回1+筛选1/设置按钮 0、`record-row` 行)、跨应用聚合(own+victim 双行)、行内应用名上下文(自身包目录标签——未安装包不入目录 contextLabel=null 属现网正确回落,首版断言假包名失败后修正)、MD3 聚焦展开搜索(Dialog 窗口内输入域,双输入域并存选择器与 MonitorListScreensTest 同款)、`records-clear-type` → `records-clear-confirm` → 该类型全部应用清空。实施插曲:androidTest APK 未随 compile 重打包致 ClassNotFoundException(补 assembleModernDebugAndroidTest)。设备验证:GlobalTypeRecordsTest 2/2 绿、RecordDaoAccessGateTest 2/2 绿）**

- **改动面**：新增 `app/src/test/java/com/lioisme/modev/architecture/RecordDaoAccessGateTest.kt`；新增 androidTest 全局记录路由用例（建议新建 `GlobalTypeRecordsTest.kt` 或并入 `MonitorListScreensTest`）。
- **实施要点**：
  1. **RecordDaoAccessGateTest**（源码扫描门禁，模式同 `DialogConventionTest`/`AppbarSearchExclusivityTest`）：扫描 app/src/main 中 `cryptoRecordDao()`/`keywordLibraryDao()` 调用点，白名单 = `ModevApp.kt`（装配）/`LogRepository.kt`/`KeywordLibraryRepository.kt`（tests 源集豁免）；越界即红——把「统一上层筛选器」从口头契约升级为强制门禁；自验：临时在任一 VM 引入直调 → 门禁变红 → 撤销；
  2. **全局路由 e2e**：种子跨包合法枚举数据（AGENTS #9：`MESSAGE_DIGEST`/`DIGEST` 等）→ 数据页 `data-type-{wire}` 行点击 → 断言 `records/type` 渲染同一管线：`record-row` 行存在、全局形态行内应用名上下文（`showPackage=true` 的 contextLabel）、Appbar 内 SearchBar 存在、actions 恒空（#24）→ `records-clear-type` 动作槽 → `records-clear-confirm` 确认 → 该类型全部应用清空；
  3. **两入口一致性探针**（可选但推荐）：同一 `SearchInputField` 展开/收起断言在 `records/type` 与 `records/{type}/{pkg}` 两路由各跑一遍——把「聚焦即展开 = MD3 标准行为且两入口一致」（D-3）锁进测试面。
- **测试影响**：全部为新增；执行注意 AGENTS 设备坑：单序列 `adb -s` 定向、`am instrument` 前 force-stop 宿主、测后恢复序列（androidTest 卸载宿主 → 11-vector-scope.ps1 + 整机重启）。
- **契约同步**：AGENTS #9 追加「DAO 直调门禁（RecordDaoAccessGateTest）」；#17 追加全局路由 e2e 锚点；#8/#13 无需动（行为探针已有）。
- **验收**：两门禁测试全绿；V8.2 缺口闭合——数据管理与每应用两入口的复用关系由测试面背书。

### T8 文档卫生与全量门禁 — 复杂度 M（收尾汇总）✅ **已完成（V41 实施备注：①AGENTS 增量沉淀——版本头追加 V41 行；#9 补 DAO 直调门禁(RecordDaoAccessGateTest 白名单)；#17 补「按应用浏览退役/唯一入口=主列表/contextLabel 无包名回落属正确行为」；#26 认证段风险文案口径改写(风险语义由令牌页状态行承载)；#19/#26/词汇表已于 T2 落地。②全量单测 `:app:testModernDebugUnitTest`:**101 个测试类全绿,0 失败**(含新增 McpTokensScreenStateTest 3、SettingsRepositoryExternalAccessTest 5、RecordDaoAccessGateTest 2)。③`lintModernRelease`:0 error / **49 warning,与基线持平零新增**(退役字符串键随删抵消)。④打包 `assembleModernRelease + assembleModernDebug` 全通过。⑤插桩抽样(单序列 am instrument,force-stop 前置,全程避开 connectedAndroidTest 卸载坑):ServicesScreenTest 4 绿(统一开关/旧双行退役/认证独立)、DataSettingsScreenTest 3 绿(退役红线/清空全部/存储同源)、DiagnosticsScreenTest 3 绿(反向断言保持)、GlobalTypeRecordsTest 2 绿(全局路由 e2e)、FourScreensNavTest 5 绿(IA 重排导航回归)、MonitorListScreensTest 4 绿(单应用列表回归)、SettingsComponentsTest+SearchInputComponentsTest+LanguageHotSwitchTest 14 绿——累计 35 插桩用例全绿。⑥静态核查零残留:`services-wire-external/services-mcp-external/AppBrowseRow/data-app-/onOpenApp/apps_browse_*/settings_external_access_hint/mcp_external_access*/mcp_enabled_hint/mcp_token_auth_hint/settings_display_section/settings_language_section/settings_features_section/settings_interception_section` 全部零代码引用;`MCP_EXTERNAL_ACCESS` 仅存迁移代码+ConfigKeys 退役注记;`tokens_status_active` 调用侧单参。⑦实施修正记录:语言热切换用例断言旧分区标题 "Language & Region" → 改 "General"(T6 波及);ServicesViewModel 退役 `wireListeningAddress` 死流(端口纯端口化后 UI 零消费,WhileSubscribed 无订阅者致测试读值恒初值——插桩轮询改经 `ConnectionMonitor.boundAddress` 真源)+ `externalAccessAddress` 派生流改纯函数映射(消除调度差)。已知预存环境集无新增失败）**

- **命令序列（Windows）**：
  1. `.\gradlew.bat :app:testModernDebugUnitTest`（全量，含新增 McpTokensScreenStateTest/迁移用例/RecordDaoAccessGateTest + 既有 parity/架构门禁）；
  2. `.\gradlew.bat :app:lintModernRelease`（0 error、基线 49 warning 不增；退役字符串键随删抵消）；
  3. `.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug`；
  4. androidTest 抽样（单序列定向 + 测后恢复序列）：`ServicesScreenTest`（统一开关）、`DataSettingsScreenTest`（无应用浏览）、`GlobalTypeRecordsTest`（新）、`FourScreensNavTest`（回归）、`DiagnosticsScreenTest`（反向断言保持）。
- **静态核查**（完成时执行并入验收记录）：
  `rg -n "services-wire-external|services-mcp-external|MCP_EXTERNAL_ACCESS|settings_apps_browse_section|apps_browse_empty|AppBrowseRow|data-app-|settings_external_access_hint|mcp_external_access_hint|settings_features_section|settings_interception_section|diagnostics-bind-all" app/src`
  期望：`MCP_EXTERNAL_ACCESS` 仅剩迁移代码 + ConfigKeys 退役注记；其余零代码引用；`rg "tokens_status_active"` 调用侧单参。
- **AGENTS.md 终核**（各任务契约同步的汇总核对）：#9（门禁）、#17（按应用浏览退役）、#19（统一外部访问重写）、#26（键退役）、词汇表「外部访问」条目、禁用旧词追加清单、版本头增量沉淀（V41 行）。
- **24-run-mcp.ps1**：MCP 服务端语义未变（工具面 43 / 鉴权判定 / 端口均不动）；核对脚本是否触 prefs 键 `mcp_external_access`（设备侧令牌矩阵），如命中需同步统一键后重跑认证子集。

---

## 3. 验收标准与门禁

### 3.1 每任务验收条款汇总

| 任务 | 可验证验收（节选硬条款） |
|---|---|
| T1 | 认证开+≥1 令牌态渲染无异常（P0 回归用例锁定）；三态状态行文案正确；`stringResource` 调用侧与占位符逐位对齐 |
| T2 | 单键单开关（统一键 + 旧键 OR 迁移删除 + 幂等）；单开关切换双服务同绑；鉴权仅由 tokenAuthEnabled 决定；重启链全程 `scope.launch` 非主线程；`services-wire-external`/`services-mcp-external` 零残留 |
| T3 | wire/MCP 两端口行均纯数字；`settings_port_value` 仅诊断快照消费 |
| T4 | 全部开关副标题 = 动态有效值或空；退役键零引用；新键双语齐备 |
| T5 | 数据页三区结构；`AppBrowseRow`/`apps_browse`/`data-app-` 零残留；仓库 `observeRecordCounts` 无回归 |
| T6 | 根页五分区按归属归类；入口可达性回归绿 |
| T7 | `RecordDaoAccessGateTest` 白名单外直调即红；全局路由 e2e 全绿（导航/复用断言/清空流程） |
| T8 | 单测全绿；lint 0 error/无新增 warning；assemble 4 APK 通过；插桩抽样绿（含测后恢复记录）；静态核查零残留 |

### 3.2 全局门禁

- 架构门禁测试全绿：`StringResourceParityTest`、`HardcodedStringConventionTest`、`PluralsCandidate`（lint）、`RecordDaoAccessGateTest`（新）、`AppbarSearchExclusivityTest`、`DialogConventionTest`、`McpToolParityTest`（43 工具冻结不破）、`InterventionGateConventionTest`/`FeatureGateBypassTest`（不涉改但回归）、`SettingsThemeContrastTest`、`RecordPagingProbeTest`、`AnchorScrollRoboTest`。
- 冷启动冒烟：本整改**不触 `hook/**`**（全部为宿主 UI/仓库/偏好层），无需 `23-run-smoke.ps1`；若实施中波及注入侧则触发全量门禁。
- wire/MCP 兼容：wire VERSION 恒 4 零感知（未改 `core/codec`）；MCP 协议面/工具面未改——`24-run-mcp.ps1` 仅在核对出 prefs 键耦合时重跑认证子集。

---

## 4. 风险登记册

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| RK-1 | **统一开关语义捆绑**：OR 迁移会把「曾仅开启 MCP 外部访问」的用户置为双服务同绑 0.0.0.0——wire 无令牌认证（raw TCP 配置通道），暴露面可能扩大 | 中高（安全语义） | D-1 决策明示「外部访问 = 双服务统一意图」并经维护者确认；迁移单次单向、行为在验收记录中显式声明；可选增强（超本次范围）：统一开关开启时 Snackbar 提示双服务改绑 |
| RK-2 | 单向迁移/降级回滚：回装旧版后读不到 `mcp_external_access`，MCP 外部访问回退停用 | 低 | 可接受（偏好键一次性迁移纪律同 V38 单键迁移）；文档记录 |
| RK-3 | 已崩溃用户的持久态（auth=true + 1 token）是否需数据修复 | 低 | 状态本身合法，T1 修复后进页即正常渲染；替代恢复路径 = 服务页关认证（服务页不渲染状态行不崩）；无需迁移 |
| RK-4 | 技术字面量（`0.0.0.0`/`POST /mcp`/`Bearer`）与 i18n 门禁兼容性 | 低 | `HardcodedStringConventionTest` 仅禁中文字面量；若未来门禁扩展再走 strings 占位 |
| RK-5 | 副标题动态空串在停用态的渲染（`subtitle: String = ""` 路径） | 低 | 组件已支持；插桩断言停用态无副标题文本 |
| RK-6 | androidTest 测后 AGP 卸载宿主 → Vector 作用域失效（已知坑） | 中 | 抽样排后/测后即执行恢复序列（11-vector-scope.ps1 + 整机重启）；单序列定向执行 |
| RK-7 | 契约（AGENTS）与实现短期不一致误导后续贡献者 | 中 | 每任务契约同步同提交（T8 终核清单逐条核对） |
| RK-8 | 新 e2e 用例的设备环境脆弱性（雷电双序列踩踏/VM 假死/高负载竞态） | 中 | 单序列 `adb -s` 定向、force-stop 前置、AGENTS 冷重启序列；断言以 testTag 精确锚定（合并节点语义） |

---

## 5. 追溯矩阵（需求 → 违规 → 任务 → 验收）

| 需求 | 违规条目 | 任务 | 验收锚点 |
|---|---|---|---|
| R1 令牌闪退 | V1.1-V1.4 | T1 + T2（V1.4） | §3.1 T1/T2 行 |
| R2 外部访问统一 | V2.1-V2.2 | T2 | §3.1 T2 行 |
| R3 端口纯显示 | V3.1-V3.2 | T3 | §3.1 T3 行 |
| R4 描述精简 | V4.1-V4.4 | T4（V4.1 随 T2） | §3.1 T4 行 |
| R5 设置 IA | V5.1-V5.2 | T6 + T2（V5.2） | §3.1 T6/T2 行 |
| R6 按应用浏览退役 | V6.1 | T5 | §3.1 T5 行 |
| R7 单库统一筛选器 | V7.1（合规）/V7.2 | T7 | §3.1 T7 行 |
| R8 记录列表复用 | V8.1（合规）/V8.2 | T7 | §3.1 T7 行 |
| 横切 | 契约同步（各节「契约同步」） | T8 终核 | §3.1 T8 行 |
| 横切 | 门禁与恢复序列 | T8 | §3.2 + §4 RK-6/RK-8 |

---

## 6. 实施排期建议（相对顺序，非日历承诺）

```
T1(令牌崩溃 P0, S) ──→ 确认后立即先行,独立提交
        ↓
T2(统一外部访问 + 重启线程修复, M) ──→ D-1/D-2 承载点,后续展示任务的地基
        ↓
T3(端口纯端口, S) ──┐ 依赖 T2(IP 由统一开关承载后端口行才去 IP)
T4(副标题动态化, S) ─┘ 同页小改,可合并为一个提交序列
        ↓
T5(按应用浏览退役, S) ──┐ 相互独立,可并行
T6(根页 IA 重排, M)  ──┤
T7(门禁与 e2e, M)   ──┘
        ↓
T8(AGENTS 终核 + 全量门禁 + 静态核查 + 插桩抽样)
```

---

*本文档由 V41 全量审计产出；所有 file:line 为 2026-09-07 工作树实位，实施时如行号漂移以标识符为准。审计证据链：3 路并行探查（令牌链路/设置结构与文案/数据与复用）+ 关键结论人工逐行复核（McpTokensScreen、ServicesScreen、SettingsRepository、DataSettingsScreen）。*
