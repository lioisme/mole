# V38 任务书：MCP 服务（Agent 一等公民）

- 状态：**已完成**（2026-09-05 立项，2026-09-05 全门禁验收通过；详见 §12 验收记录）
- 前置审计：三路并行代码全量审计（架构/UI 能力面、服务端/配置链路、测试设施）+ 关键契约精读
- 上游契约：`AGENTS.md`（V37 双域架构）；V38 增量沉淀已追加至 `AGENTS.md` §26

---

## 0. 需求映射（验收总纲）

| # | 需求 | 本任务书落点 |
|---|------|--------------|
| R1 | 核心功能与前端界面完全解耦分离 | §3 边界规则 B1–B4 + `McpLayerBoundaryTest`；§7 解耦净化 D1–D3 |
| R2 | 标准、规范、专业、面向 Agent 的 MCP | §4 协议设计（spec 2025-06-18 Streamable HTTP 全符合性）；§5 工具 schema 全量 JSON Schema |
| R3 | MCP 与前端同为一等公民，除前端专属外零阉割 | §5 工具面 43 个 = UI 能力清单逐行对照；§5.3 UI 专属明示排除 |
| R4 | MCP 与前端共同继承同一上游 | §5.2 每工具「上游 API」列 = 对应 ViewModel 消费的同一 API；§7 D5 上游白名单门禁防旁路 |
| R5 | 不破坏现有功能 | §9.3 回归门禁（unit 全绿 / lint 零新增 / 4 变体 assemble / 23-run-smoke / 既有 androidTest 抽样） |
| R6 | 完成后子任务测试：雷电模拟器 ADB 安装启用 MCP，全功能测试循环至全绿 | §9.2 `scripts/test-integration/24-run-mcp.ps1` 矩阵（协议 6 + 记录 7 + 配置 2 + 资产 2 + 服务 2 + 应用/作用域/危险 3 = 22 项，全部附预期结果），失败→分析→修复→重跑循环 |

---

## 1. 背景与定位

Modev（Kotlin/Compose M3 Xposed 模块，双域架构：监听域 + 功能域）已完成 V37 双域拆分。数据层在 V22 就预埋了 MCP 契约：

- `domain/record/RecordService.kt` KDoc：「前端(ViewModel)与未来 MCP 均仅依赖本接口」；`queryPage/create/update/delete` 标注「供 MCP / 导入 / 编辑 / 导出」。
- `domain/record/RecordFilter.kt`：「REST/MCP 层唯一依赖面，未来 HTTP API 直接映射本契约」。
- `docs/record-layer-redesign.md` T7：「MCP-ready 契约，本期仅契约不实现 server」。

本任务把该契约落地为**进程内常驻的 MCP (Model Context Protocol) 服务器**：外部 Agent（Claude / 自研 Agent / 脚本）经 `adb forward` 或局域网直连，获得与 Compose 前端完全同源的能力面。

## 2. 现状审计结论

### 2.1 上游能力面（UI 消费 → MCP 复用，完全同源）

| 上游 | 关键 API | 既有消费者 |
|------|----------|-----------|
| `LogRepository`（实现 `RecordService`） | `queryPage/count/get/neighbor/create/update/delete/deleteByFilter/clearPackage/clearByType/clearAll/storageUsage/observeTypeCounts/categoryCounts/observeRecordCounts/statsFlow` | RecordsViewModel / RecordDetailViewModel / DataSettingsViewModel / DiagnosticsViewModel |
| `SettingsRepository` | `effectiveSwitch/effectiveSwitchFlow/overrideFor/setOverride/removeOverride/globalFeature/setGlobalFeature/setServerExternalAccess/setServerPort/serverPort/overrides` | PerAppListenSettingsViewModel / PerAppFeatureSettingsViewModel / DiagnosticsViewModel |
| `KeywordLibraryRepository` | `entries/add/update/setEnabled/remove` | KeywordLibraryViewModel / PerAppFeatureSettingsViewModel |
| `AppCatalogRepository`（`AppCatalogSource`） | `apps/loading/refresh` | AppsViewModel |
| `AppListSettings` | `pinScoped/showSystem/showModules` 流 + `setX` 写路径 | AppsViewModel（解耦契约 #18 唯一写入口） |
| `ScopeRepository` | `state/toggle/remove/refresh/syncForeground` | AppsViewModel / AppMonitorViewModel |
| `ConnectionMonitor`（P5-1 抽象） | `connectedPackagesFlow/boundAddress` | DiagnosticsViewModel |
| `CategoryTaxonomy` / `FeatureGroups` / `SpoofProfiles` | 域完备清单（LISTEN 44 / FEATURE 37 / 档位 auto·pixel7pro·xiaomi13） | 监听/功能设置页分区驱动 |

### 2.2 服务端先例（MCP 镜像的成熟模式）

`data/server/ConfigServer.kt`：raw TCP（newline-JSON，端口 1510）、`@Volatile epoch` 生命周期守卫、`restart() = close()+start()`、`externalAccess` 回环/全接口改绑、handshake token 校验、`pushToPackage/pushToAllConnected` 定向/全量推送、`effectiveResolver` 每会话解析。**MCP 与 ConfigServer 完全独立生命周期**（独立端口/独立开关/互不 restart），wire 协议 VERSION 恒 4 零触碰。

### 2.3 解耦摩擦点（审计发现）

| # | 发现 | 处置 |
|---|------|------|
| F1 | `data/mapping/RowModel.kt` 携带 `androidx.compose.runtime.Immutable`（presentation 注解渗入核心层） | MCP 建独立纯 Kotlin DTO 映射层（§7 D3），不向 MCP 泄漏 Compose 类型；注解本身保留（Compose 跳过优化），豁免固化于 KDoc |
| F2 | `RecordService.create/update` 签名暴露 `data.db.CryptoRecordEntity`（domain→data.db 反向依赖，违反自身 KDoc） | 引入纯 Kotlin `domain/record/RecordDraft`（§7 D1） |
| F3 | `domain/record/RecordTypeCount` 携带 `androidx.room.ColumnInfo`（Room POJO 映射必需） | 豁免 + KDoc 固化理由 |
| F4 | 诊断文案装配困在 `ui/settings/DiagnosticsScreen.kt#copyDiagnostics()`（UI 专属，MCP `diagnostics` 工具无法复用） | 抽取纯函数 `domain/diagnostics/DiagnosticsReport`（§7 D2），双端共用 |
| F5 | `core/model/EffectiveSwitch` 引用 `android.util.Log`（core 唯一 Android 依赖） | 本期容忍（注入侧/宿主共用源集），豁免固化 |
| F6 | UI 直 import `data.repo`（仓库层 = 核心出口，PackageBoundaryTest R3 已固化 UI 禁触 `data.db`） | 维持；MCP 层采用与 UI 完全相同的 allowlist（B2），对称即公平 |

### 2.4 测试设施盘点

`scripts/test-integration/tt-lib.ps1`：LDPlayer14 x64（`127.0.0.1:5555`，`ANDROID_ADB_SERVER_PORT=5038`，adb 二进制 `D:\leidian\LDPlayer14\adb.exe`）、`WaitDevice`（冷重启自愈）、`ResetState`、`RunCase`（`am start -n com.lioisme.modev.testtarget/.TriggerActivity --es domain X --es case Y`）、`Q()`（run-as 零 su SQL 对账）、`GetTt`（`TT_RESULT` logcat 断言）、`Assert/DumpResults`（JSON → `99-report.ps1` 聚合）。矩阵脚本 `20-run-listen / 21-run-enhance / 22-run-linkage / 23-run-smoke`。**脚本层无任何 HTTP 客户端先例** → MCP 矩阵新增 `Invoke-RestMethod` JSON-RPC 客户端 + `adb forward tcp:15110 tcp:15110`（本任务首创，封装进 `tt-mcp-lib.ps1`）。

## 3. 总体架构设计

### 3.1 分层与依赖方向

```
Compose UI (ui/*) ─────────┐
                           ├──> [唯一上游] domain/* + data/repo/* + core/*
MCP tools (mcp/tools/*) ───┘         │
                                     ├──> data/db (Room)  ← 仅仓库层可触
                                     └──> data/server/ConfigServer（注入侧 wire，V38 零改动）
MCP transport (mcp/*)  ── NanoHTTPD HTTP/1.1 ── Agent（adb forward / LAN）
```

### 3.2 新增包结构

```
mcp/
  McpServer.kt              // 生命周期：start/stop/restart、@Volatile epoch 守卫（镜像 ConfigServer）、
                            // enabled/port/externalAccess volatile、session 表、自省（server_status 消费）
  McpHttpEngine.kt          // NanoHTTPD 子类：唯一端点 POST /mcp 路由 + Bearer 校验 + 限额
  protocol/
    McpProtocol.kt          // PROTOCOL_VERSION="2025-06-18"、SUPPORTED 协商集、能力声明、serverInfo
    JsonRpc.kt              // JSON-RPC 2.0 请求/响应/错误码（Gson），单请求单响应
    McpSession.kt           // session id（UUID）、initialized 状态、协商版本
  tools/
    McpTool.kt              // interface: name/description/inputSchema/execute(args)->JsonElement
    McpToolRegistry.kt      // 注册表、schema 校验、dispatch、错误包装（业务错误→isError）
    McpDtos.kt              // RowModel/RecordDetailModel/HookSwitch/… → 纯 JSON DTO（纯 Kotlin，可 JVM 直测）
    RecordTools.kt          // 14 工具
    AppTools.kt             // 6 工具（含 applist_prefs）
    ListenConfigTools.kt    // 4 工具
    FeatureConfigTools.kt   // 4 工具
    GlobalAssetTools.kt     // 8 工具
    ServerTools.kt          // 4 工具
    ScopeTools.kt           // 3 工具
```

### 3.3 包边界规则（源码扫描强制）

| 规则 | 内容 | 强制手段 |
|------|------|----------|
| B1 | `mcp.*` 禁 import `ui.*` / `androidx.compose.*` / `data.db.*` / `hook.*` / `data.server.*`（ConfigServer 细节经 `ConnectionMonitor` 抽象消费） | `McpLayerBoundaryTest`（复制 `InterventionGateConventionTest` 骨架） |
| B2 | `mcp.*` 允许依赖 `domain.*` / `data.repo.*` / `core.*`（与 UI 同一 allowlist —— 「同一起游继承」的对成性由测试锁定） | 同上 |
| B3 | `domain.*` 维持既有 R2（禁 `android.*`/`ui`）；R3（UI 禁 `data.db`）维持 | `PackageBoundaryTest` 既有规则零回退 |
| B4 | MCP 面向 Agent 的文本一律英文（非用户可见文本，`HardcodedStringConventionTest` 仅扫 `ui` 包，天然豁免；豁免理由于 KDoc 固化） | 评审约定 |

## 4. MCP 协议设计（标准符合性）

- **规范版本**：MCP `2025-06-18`（Streamable HTTP）；initialize 协商接受 `2024-11-05` / `2025-03-26` / `2025-06-18`，客户端未声明或未知 → 回落 `2025-06-18`。
- **传输**：单端点 `POST /mcp`（请求体 JSON-RPC，响应 `application/json`，Content-Length 收尾——无服务端主动流，故 `GET /mcp` → 405，`DELETE /mcp` → 200 关会话）；`notifications/initialized` → 202 空体。
- **会话**：`initialize` 成功响应携带 `Mcp-Session-Id`（UUID）响应头；后续请求缺失/失效 → HTTP 404（客户端重初始化，spec 语义）。
- **JSON-RPC 纪律**：单请求单响应（`2025-06-18` 已移除 batching，收到数组 → `-32600`）；错误码 `-32700 parse / -32600 invalid request / -32601 method not found / -32602 invalid params / -32603 internal`；**工具业务错误走 `result.isError=true`**（协议错误与业务错误严格分型）。
- **方法面**：`initialize` / `notifications/initialized` / `ping` / `tools/list` / `tools/call`；能力声明仅 `tools`（记录与配置为结构化数据，`resources/prompts` 双通道徒增一致面，不声明）；`serverInfo = {name:"modev", version:BuildConfig.VERSION_NAME}` + `instructions`（Agent 定位说明）。
- **tools/call 返回**：`content:[{type:"text", text:<pretty JSON>}]`（文本兜底必给），有结构化输出的工具同时给 `structuredContent`。
- **认证**：
  - 默认（回环 127.0.0.1）：免 Token——adb forward 场景零配置开箱可用；
  - `mcp_external_access=true`（0.0.0.0）：强制 `Authorization: Bearer <mcp_token>`，缺失/错误 → 401。Token 为宿主本地自动生成 UUID，诊断页展示/复制；**独立于 ConfigServer 的 requireToken**（MCP 客户端非注入进程，不走 RemotePrefs 链路）。
- **防御限额**：请求体 ≤ 1 MiB（超限 413→错误响应）；`pageSize` 钳制 1..200；`record_get` 大字段 1 MiB 截断守卫（响应内 `truncated:true` 标志）；日志 `ModevMcp` 仅落 method+tool+耗时，**不落记录内容**（PII 纪律）。
- **HTTP 引擎选型**：NanoHTTPD 2.3.1（约 +100KB，零传递依赖，最终版无 `NewerVersionAvailable` lint 罚分；仅作 HTTP 解析引擎，不用其静态文件/WebSocket 能力——历史 CVE 面不暴露）。

## 5. 工具面清单（43 个，R3 parity 核心）

### 5.1 清单（输入/输出/上游/对应 UI）

**记录组（14）**

| 工具 | 输入要点 | 输出要点 | 上游 API | 对应 UI |
|------|----------|----------|----------|---------|
| `records_query` | recordType?/packageName?/kind?/operation?/query?/timeStart?/timeEnd?/sort?/page=0/pageSize=50 | `{items[],total,page,pageSize}` | `RecordService.queryPage(RecordFilter,…)` | 10 个记录列表页 |
| `records_count` | 同上过滤参数 | `{count}` | `RecordService.count` | 列表 totalCount |
| `records_type_counts` | — | `[{recordType,count}]` | `observeTypeCounts` | 数据管理页 |
| `records_category_counts` | recordType | `{kind:count}` | `categoryCounts` | 筛选菜单徽标 |
| `record_get` | id | 详情全字段 JSON（1MiB 截断守卫） | `RecordService.get` → `RecordDetailModel` | 详情页（markdown 渲染为 UI 专属，MCP 返回结构化数据） |
| `record_neighbor` | id/newer | `{id|null}` | `RecordService.neighbor` | 详情上一条/下一条 |
| `record_create` | RecordDraft | `{id}` | `RecordService.create(RecordDraft)` | （MCP 导入，契约预埋） |
| `record_update` | id+RecordDraft | `{ok}` | `RecordService.update` | （MCP 编辑） |
| `record_delete` | id | `{ok}` | `RecordService.delete` | （MCP 删） |
| `records_delete_by_filter` | 过滤参数 | `{deleted}` | `deleteByFilter`（无约束拒绝语义保留） | 记录页「清空本类」 |
| `records_clear_package` | packageName | `{deleted}` | `clearPackage` | AppMonitor 清空 |
| `records_clear_type` | recordType | `{deleted}` | `clearByType` | 数据管理按类型 |
| `records_clear_all` | **confirm:true 必填** | `{deleted}` | `clearAll` | 数据管理清空全部 |
| `storage_usage` | — | `{bytes}` | `storageUsage` | 数据管理存储占用 |

**应用组（6）**：`apps_list`（query?/showSystem?/showModules?/pinScoped? → 行集；上游 `AppCatalogSource.apps`+`ScopeRepository.state`+`observeRecordCounts`+`connectedPackagesFlow`，查询参数为查询级覆盖不写持久化）、`app_monitor`（packageName → 状态 pills + 十类计数，对应 AppMonitorScreen）、`app_launch`（上游 PackageManagerCompat + startActivity；Android 10+ BAL 限制下宿主后台可能被系统拒，返回 `launched:false` 并注明）、`app_clear_records`（confirm 必填）、`applist_prefs_get` / `applist_prefs_set`（经 `AppListSettings.setPinScoped/setShowSystem/setShowModules` 唯一写路径，对应筛选菜单）。

**监听配置组（4）**：`listen_settings_get`（→ `{effective:HookSwitch JSON, override, deviationCount}`）、`listen_settings_set`（字段级 merge：10 master / kinds 监听 id / logStack / maxBytesPerField / ignoreStackPrefixes / maxResultRows / maxResultChars / fileContentCapture / ignorePathPrefixes；**kinds 条目值 null = 回默认**；上游 `overrideFor`+`setOverride`，推送链路 `pushToPackage` 自动）、`listen_settings_reset`、`taxonomy_listen`（LISTEN 44 类：type/kind/domain/noiseTier/defaultOn，与 UI 分区同源 `CategoryTaxonomy`）。

**功能配置组（4）**：`feature_settings_get/set/reset`（功能域字段：dialogKeywords / spoofProfileId / spoofOverrides / extra* 四组 / keywordIds / keywordFollowGlobal + 4 master + kinds 功能 id；与监听组共享 `AppListenOverride` 读改写）、`taxonomy_feature`（FEATURE 37 类 6 组，同源 `FeatureGroups`）。

**全局资产组（8）**：`keywords_list/add/update/remove`（`KeywordLibraryRepository`，对应关键词库页）、`spoof_profiles_list`（内置档位 + 当前 global 值）、`spoof_global_set`（profileId/覆写增删 → `setGlobalFeature` 推送链路）、`blacklist_list`（四组 extra*，内置基线∪追加语义声明）、`blacklist_edit`（add/remove {kind,values}）。

**服务与诊断组（4）**：`server_status`（wire：port/boundAddress/externalAccess/connectedPackages；mcp：enabled/port/bound/sessionCount；framework：ScopeState 摘要）、`server_set_external_access`（wire 语义，与诊断页开关同义）、`server_set_port`（wire 端口）、`diagnostics`（结构化 PipelineStats + `DiagnosticsReport` 文本，双端同源）。

**作用域组（3）**：`scope_state`、`scope_add` / `scope_remove`（上游 `ScopeRepository.toggle/remove`；**异步语义声明**：现代框架 `requestScope` 弹系统确认通知，`ScopeRequested ≠ 已生效`，结果码透传）。

### 5.2 parity 机制

`McpToolParityTest`（JVM）：冻结 43 工具名快照 + 逐工具上游白名单断言（源码扫描 `execute` 体仅允许 §2.1 上游 allowlist import）——新增 UI 能力必须同步 MCP 工具（否则 parity 测试红），防「旁路上游」与「功能阉割」双向漂移。

### 5.3 UI 专属功能（明示排除，非阉割）

主题/语言切换（LocaleManager）、markdown 渲染/代码组/锚点导航（`record_get` 返回结构化 JSON 承载同信息）、复制/全屏查看器/SAF 导出、搜索框 UI 交互、**MCP 自身开关/端口/Token 管理**（传输层自举自毁，仅诊断页 UI 管理）、关于/开源许可页。

## 6. 配置与安全设计

- 新 `ConfigKeys`（全宿主本地，不入远程镜像）：`mcp_enabled`（默认 **true**）、`mcp_port`（默认 **15110**）、`mcp_external_access`（默认 false）、`mcp_token`（自动生成）。
- `SettingsRepository` 增量：四个 StateFlow + setter（镜像 `setServerExternalAccess` 模式：持久化 → volatile/StateFlow → `scope.launch { mcpServer.restart() }`）。
- `AppContainer`：`val mcpServer by lazy { McpServer(...) }`，init 按 enabled 启动；进程内常驻（无前台 Service，与 ConfigServer 同现实：宿主进程被系统回收即下线，文档明示）。
- 诊断页新增「MCP 服务」分区：启用开关（`diagnostics-mcp-enabled`）/ 外部访问开关（`diagnostics-mcp-external`，风险副标题）/ 端口行（底部 sheet，1..65535）/ Token 行（点击显示+复制）/ 状态行；新增 i18n 双语键 ~12 个（`StringResourceParityTest` 自动门禁）。

## 7. 核心解耦增量（不改行为净化）

- **D1 `RecordDraft`**：`domain/record/RecordDraft.kt` 纯 Kotlin 写入模型；`RecordService.create/update` 改签名；`LogRepository` 内映射 `Draft→Entity`；`RecordServiceCrudTest` 更新。消除 F2。
- **D2 `DiagnosticsReport`**：`domain/diagnostics/DiagnosticsReport.kt` 纯函数（数据入参 + 标签入参注入）；`DiagnosticsScreen.copyDiagnostics` 与 `diagnostics` 工具共用。消除 F4。
- **D3 `McpDtos`**：RowModel/RecordDetailModel → JSON 的纯 Kotlin 映射（专用 `GsonBuilder().serializeNulls()`——SQL NULL 必须落 `null`，Gson 2.13 实锤）。缓解 F1。
- **D4**：`McpLayerBoundaryTest`（B1/B2 源码扫描）。
- **D5**：`McpToolParityTest`（§5.2）。

## 8. 实施里程碑

| 里程碑 | 内容 | 出口 | 状态（2026-09-05） |
|--------|------|------|------|
| M0 | 本任务书落盘 | 文档评审 | ✅ 完成 |
| M1 | D1/D2 + 回归 | `RecordServiceCrudTest` 绿 | ✅ 完成：`RecordDraft`（纯 Kotlin 写入模型）+ `RecordDraftMappers`（实体映射收敛 data.mapping）+ `DiagnosticsReport`（标签/数据入参注入的纯函数）；`RecordService.create/update` 改签名，`storageUsage` 上提为契约成员 |
| M2 | NanoHTTPD 依赖 + protocol/ + engine + `McpServer` 生命周期 | `McpJsonRpcTest` / `McpServerLifecycleTest`（真回环 socket）绿 | ✅ 完成：NanoHTTPD 2.3.1；`JsonRpc`/`McpProtocol`/`McpSessionManager`/`McpHttpEngine`（路由闭包传输无关）/`McpServer`（epoch 守卫 + Bearer）；`McpServerLifecycleTest` 13 用例真回环绿 |
| M3 | 43 工具 + DTO + 注册表/校验/错误包装 | 6 个分组测试类 + `McpToolParityTest` 绿 | ✅ 完成：记录 14/应用 6/监听 4/功能 4/资产 8/服务 4/作用域 3 = 43；`McpDtos`（serializeNulls + 1MiB 截断守卫）；parity 快照 + B1 边界源码扫描双门禁绿 |
| M4 | ConfigKeys/SettingsRepository/AppContainer + 诊断页 UI + i18n | 插桩 `DiagnosticsScreenTest` 增量绿 | ✅ 完成：四键（mcp_enabled/port/external_access/token）+ 三 StateFlow + setter 重启链；AppContainer 惰性装配 + mcpRestarter；诊断页 MCP 分区四行 + 端口 sheet + Token 掩码显隐；双语 8 键。注：「状态行」由启用开关 + `server_status` 工具承载，未做轮询状态行（避免无观测源的伪实时文案） |
| M5 | 门禁：全量 unit / lint 49 基线零新增 / 4 变体 assemble / `23-run-smoke` 回归 | 全绿 | ✅ 完成：unit 630/630、lint 0 error/49 warning、4 APK 签名 OK、smoke 6/6（并修复脚本 `$pid` 与只读自动变量冲突的假 PASS） |
| M6 | 集成测试子任务：`24-run-mcp.ps1`（§9.2）循环至全绿 + `99-report` + `AGENTS.md` V38 增量沉淀 | 矩阵 22 项全 PASS | ✅ 完成：42/42 子断言全 PASS（协议 6 + 记录 7 + 配置 2 + 资产 2 + 服务 2 + 应用/作用域/危险 3）；循环修复 5 项根因（§12）；AGENTS.md §26 沉淀完成 |

## 9. 测试计划

### 9.1 金字塔

- JVM 纯函数：`JsonRpc` 编解码/错误码、`McpProtocol` 协商、`McpDtos` 映射（含 NULL 语义）、`DiagnosticsReport`、schema 校验。
- Robolectric（`@Config(sdk=[34])`）：`RecordTools`（种子 DB 对账）、`ListenConfigTools` / `FeatureConfigTools`（merge 语义 + isDefault 收敛）、`GlobalAssetTools`。
- 真回环 socket：`McpServerLifecycleTest`（镜像 `ConfigServerLifecycleTest`：启动/重启 epoch/会话失效/401）。
- androidTest：`McpEndToEndTest`（进程内起随机端口 server → HttpURLConnection 走 initialize→tools/list→records_query）。

### 9.2 设备集成矩阵（R6 子任务，`24-run-mcp.ps1` + `tt-mcp-lib.ps1`）

环境：`10-build-install`（app-modern-x86_64-debug.apk）→ `11-vector-scope` → `EnsureHost` → `adb forward tcp:15110 tcp:15110`；客户端 = PowerShell `Invoke-RestMethod` JSON-RPC（Mcp-Session-Id 会话保持）。种子 = `RunCase` 真实触发 + `Q()` SQL 对账（双通道一致性原则）。

| # | 断言 | 预期 |
|---|------|------|
| M-PROTO-01 | initialize | `serverInfo.name=="modev"`、版本协商合法、`Mcp-Session-Id` 头存在 |
| M-PROTO-02 | tools/list | 43 工具齐备且与快照一致，schema 非空 |
| M-PROTO-03 | 错误语义 | 未知 method→-32601；缺参→-32602；坏 JSON→-32700；batch→-32600 |
| M-PROTO-04 | ping | `{}` |
| M-PROTO-05 | 会话失效 | 无/错 session → 404；重 initialize 恢复 |
| M-PROTO-06 | auth | 回环免 Token 可用；`server_set_external_access` 不影响 MCP 自绑定 |
| M-REC-01..06 | RunCase crypto/db/behavior/prefs 种子 | `records_query` total 与 `Q()` 计数相等；kind/query 过滤、分页（page/pageSize/total 三元一致）、`record_get` 字段与 DB 行对账、`record_neighbor` 方向正确 |
| M-REC-07 | 写路径 | create→id 回读；update→get 断言生效；delete→count-1 |
| M-CFG-01 | 监听设置闭环 | `listen_settings_set` 关 FILE_READ → RestartTarget → RunCase 读用例 → count==0（镜像 L 矩阵负轮）→ reset → count>0 |
| M-CFG-02 | 功能设置闭环 | 开 exitIntercept → E-EXIT 用例 → TT_RESULT blocked 断言（镜像 E 矩阵） |
| M-ASSET-01 | 关键词库 | add→list 含→`feature_settings_get` 该包规则含新词（推送链路）→remove |
| M-ASSET-02 | 画像/黑名单 | spoof_global_set=pixel7pro → 读回一致；blacklist_edit 追加→读回 |
| M-SRV-01 | 端口并存 | `su netstat` 见 127.0.0.1:1510 与 127.0.0.1:15110 |
| M-SRV-02 | diagnostics | 与 DB 计数/statsFlow 对账 |
| M-APP-01 | apps | `apps_list` 含 testtarget 且 scoped；`app_monitor` 十类计数对账；`applist_prefs_set` 读回 |
| M-SCOPE-01 | scope_state | 含 testtarget（`scope_add` 依赖系统确认弹窗 → SKIP-eligible） |
| M-DANGER-01 | 危险工具 | `records_clear_all` 无 confirm → -32602；confirm:true → 清空成功 |

### 9.3 回归门禁（R5）

`:app:testModernDebugUnitTest` 全绿；`lintModernRelease` 基线 49 warning / 0 error 零新增；`build-all.ps1` 4 APK；`23-run-smoke.ps1` 全绿（全功能 ON 冷启动无 FATAL/ANR）；既有 androidTest 抽样（DiagnosticsScreen/PerAppListen/PerAppFeature）。

### 9.4 验收清单

协议 6 + 记录 7 + 配置 2 + 资产 2 + 服务 2 + 应用/作用域/危险 3 = **22 项集成断言全 PASS**；单元/Robolectric/androidTest 全绿；lint 零新增；parity 测试锁定 43 工具；AGENTS.md V38 契约沉淀完成。

## 10. 风险与对策

| 风险 | 对策 |
|------|------|
| Gson 默认省略 JsonNull（SQL NULL 键静默丢失） | `McpDtos` 专用 `serializeNulls` 实例 |
| KDoc 内书写 `get*/put*` 形态会以 `*/` 提前终结注释 | 沿用既有规则：注释写「get/put/remove 系列」 |
| taxonomy 本地化 label 属 UI 资源 | `taxonomy_*` 返回 kind id + defaultOn，不返回 label（声明于 schema description） |
| `app_launch` 受 Android 10+ 后台启动限制 | 尝试 + 捕获 + 返回 `launched:false`，schema 注明 |
| scope_add 异步确认 | 结果码透传 + SKIP-eligible 矩阵项 |
| 宿主进程回收 → MCP 下线 | 与 ConfigServer 同现实，文档明示；foreground service 列为后续任务 |
| lint `NewerVersionAvailable` | NanoHTTPD 2.3.1 为最终版，0 新增 |
| 危险工具误操作 | `confirm:true` 强制 + 工具描述标注危险等级 |

## 11. AGENTS.md V38 增量沉淀（已完成）

- 词汇表增补：`MCP 服务`（进程内 Streamable HTTP，端口 15110，默认回环免 Token；外部访问独立开关强制 Bearer）、`工具面 43`（McpToolParityTest 冻结快照）。
- 强约束 #26：mcp 包边界（B1/B2）+ parity 上游白名单；工具面与 UI 能力同步演进；记录大字段 1MiB 截断守卫；`serializeNulls` 专用 Gson。
- 测试矩阵：`24-run-mcp.ps1` 为 MCP 交付门禁。

## 12. 验收记录（2026-09-05 首轮全绿；2026-09-06 终版补全，全门禁通过）

### 12.1 集成矩阵循环修复的根因（R6 子任务实录）

首轮矩阵 19 PASS / 23 FAIL，逐项定位并修复五类根因后收敛至 42/42：

1. **PowerShell 参数名 `$args` 与自动变量冲突**（`tt-mcp-lib.ps1`）：`function McpTool([string]$name, [object]$args)` 实参不绑定，`arguments` 恒序列化为 `[]` → 服务端收零参默认执行（records_query 返回全量 705、分页 pageSize 恒 50、必填参数工具全 -32602）。改 `$toolArgs` 并加 `[tool-error]` 透传打印。同类冲突 `$pid` 与只读 `$PID`（`23-run-smoke.ps1`/`check-spool.ps1`）一并修复——该冲突使存活判定恒真（拿 shell 自身 PID 匹配 `\d`），属假 PASS 隐患。
2. **PS 5.1 无 `-SkipHttpErrorCheck`**（PS 6+ 参数）：M-PROTO-03c/03d 语句终止错误致 `$raw` 为 null。改 try/catch 读响应流（服务端对协议错误恒回 HTTP 200，本不需跳过）。
3. **M-CFG-01 设计缺陷**：注入侧有效配置 = 推送全局 ⊕ 磁盘偏差（V36 D-1），磁盘 `FILE_READ=true` 优先级高于宿主推送 false，原「关 FILE_READ 断言 0」不可达。重设计为正向闭环：MCP 开 FILE_READ（默认关）→ 记录出现 → MCP 关 → 无新增 → reset → 偏差清零；用例改用 L-BHV-07（readAllBytes，L-BHV-01 是 FILE_WRITE）。
4. **关键词库写后读竞态**（`GlobalAssetTools`）：`entries` StateFlow 经 Room 失效追踪异步刷新，add 后立即 list 有竞态。新增 `awaitSnapshot`（`first{predicate}` + 3s 超时）收口 add/update/remove 三写路径。
5. **注入侧 seq 续号缺陷**（`RecordSink`，V38 顺带修复的存量数据丢失 bug）：`seqCounter` 构造期一次性 `restoreSeq()`，注入早于 Application 时 `applicationContext()` 为 null → 每次进程重启 seq 从 1 重发 → 宿主 `(packageName, processName, seq)` 唯一索引 + REPLACE 静默覆盖上一代记录（矩阵跨阶段重启即触发，表现为「记录凭空消失」）。改为首次发号惰性续号（文件路径可见才定档，已发号不回溯），`RecordSinkTest` 新增回归用例。

### 12.2 环境自愈（矩阵稳定性）

- **宿主进程回收**：无前台服务下 LDPlayer 后台回收宿主（矩阵中段连接拒绝/会话失效）。`McpRpc` 内置自愈：status -1/404 → `EnsureHost` + 重握手 + 重试一次（`-NoRecover` 豁免 M-PROTO-05 的 404 语义断言组）。
- **种子阶段通道门控**：改用 `ResetState`（推配置→重启靶→起宿主→REGISTRY 暖机回放旧 spool→排空→清库）+ `WaitChannel`（`server_status.wire.connectedPackages` 证通道建立），杜绝「未连接即断言」假阴性。
- **androidTest 卸载副作用**：AGP 测后卸载宿主 APK → Vector modules 行删除 + enabled 复位；测后需重装 + `enabled=1` + scope 行 + 重启方可恢复注入（本轮已恢复并验证）。

### 12.3 UI 契约对齐（存量测试债清理）

- 诊断页端口行自 V30 起收敛为「端口号」（地址/作用域后缀经剪贴板报告承载），`DiagnosticsScreenTest` 三用例仍按 V26 全文案断言 → 对齐现契约：分区断言含 MCP 区、行存在性断言、开关经 `diagnostics-bind-all` testTag 锚定 + VM 改绑观测。
- 雷电镜像剪贴板静默丢弃写入（`setPrimaryClip` 后 `hasClip=false`，直写亦如此）→ `copyDiagnostics` 用例加 `clipboardFunctional()` 哨兵探测，不通环境降级为「Snackbar 证处理器执行」，内容同源由 `DiagnosticsReport` JVM 单测 + MCP `diagnostics` 工具矩阵双端覆盖。
- 功能页组标题与 master 行同文案「进程守护」→ `SettingsSection` 增可选 `modifier` 参，组头挂 `feature-group-header-{GROUP}` testTag；画像 sheet Column 补 `verticalScroll`（选项溢出可滚 + `performScrollTo` 有滚动父）。
- MCP「外部访问」与 wire 同名 → 改「MCP 外部访问」/「MCP external access」消歧。
- `McpEndToEndTest` 受平台明文 HTTP 拦截 → 新增 `src/debug` 清单 + `network_security_config`（仅放行 127.0.0.1/localhost，release 零波及）。

### 12.4 门禁结果汇总

| 门禁 | 结果 |
|------|------|
| `:app:testModernDebugUnitTest` 全量 | 630/630 绿（含 MCP 5 类 41 用例 + RecordSink 续号回归） |
| `:app:lintModernRelease` | 0 error / 49 warning（基线零新增） |
| `scripts/build-all.ps1` | 4 APK（release/debug × arm64/x86_64）签名 OK |
| `23-run-smoke.ps1` | 6/6 绿（全功能 ON 冷启动无 FATAL/ANR + L-SMOKE-01 帧语义） |
| `24-run-mcp.ps1` | 42/42 子断言全 PASS（§9.2 矩阵 22 项） |
| androidTest 抽样 | 12/12 绿（McpEndToEnd + DiagnosticsScreen + PerAppListen + PerAppFeature） |

### 12.5 遗留与后续

- 宿主前台服务（MCP/ConfigServer 常驻化）——任务书既定后续项。
- `McpLayerBoundaryTest` 独立类未单列：B1 边界扫描已并入 `McpToolParityTest.mcp_layer_boundary_allowlist`（同源门禁，语义等价）。
- 雷电镜像剪贴板缺陷为环境问题非应用缺陷；真机可恢复全量剪贴板断言（探测自动启用）。

---

## 12.6 终版验收增补（2026-09-06）

### 12.6.0 终版门禁汇总

| 门禁 | 结果 |
|------|------|
| :app:testModernDebugUnitTest 全量 | 630/630 绿 |
| :app:lintModernRelease | 0 error / 49 warning（基线零新增） |
| scripts/build-all.ps1 | 4 APK 签名 OK |
| 23-run-smoke.ps1 | 6/6 绿（after-setResult 修复后全部干预生效下冷启动） |
| 20-run-listen.ps1（L 矩阵） | 70 PASS / 0 FAIL / 8 SKIP |
| 24-run-mcp.ps1（MCP 矩阵） | 42/42 全 PASS（含 M-CFG-01b 断言窗口自愈重试） |
| androidTest 抽样 | 12/12 绿（剪贴板内容断言实跑通过） |
| E 矩阵 SSL 分段实证 | E-SSL-01/02/03/04/05 单轮二态+记录全绿；06 需 testtarget 配 networkSecurityConfig（SKIP-eligible） |

### 12.6.1 注入侧存量产品缺陷修复（V38 回归测试挖出，均经真机分段实证）

1. **WebViewHooker emitGeneric 绕闸**（V3x）：loadData/loadDataWithBaseURL 发射 PAGE_LOAD 不经 enabled(PAGE_LOAD) 闸 → master 关仍产记录（L 矩阵 webview NEG total=1 实锤）。补闸。
2. **LibxposedPlatform after-setResult 丢弃**（V31 起）：拦截器恒 return result → 现代路径全部「结果改写型」干预（maps 掩蔽/栈过滤/传感器伪装/VPN·DNS 隐藏/SSL 结果替换）在 Vector 2.x 上空操作。修复：after 短路值优先。实证 E-XPP-03 dirtyLines 3→0、E-XPP-02 xposedFrames 0、smoke 6/6。
3. **SSLBypassHooker 五项**（V31 起，均 E 矩阵实锤）：
   - SSLContext.init after 反射在 Android 14 conscrypt 字段布局下失败 → 改 setArg(1) 入参改写（D-11 同路径）；
   - native 握手只经 Platform 两个**包私有 static** 桥 checkServerTrusted(X509TrustManager,X509Certificate[],String,AbstractConscryptSocket|ConscryptEngine)（dexdump 实锤）→ getDeclaredMethod+setAccessible 补装；
   - okhttp 等 app dex 类不经 java 层 loadClass（ART native fast path）+ 模块与 app 双份 kotlin-stdlib（Function0 Class 引用不等 → getDeclaredMethod 必败）→ ProbeContext.appClassLoader 显式装载 + SSLContext.init 回调补装触发点；
   - hookMethodSafe 记录 kind 参数化（pinner/verifier 曾误记 SSL_TRUST_MANAGER）；
   - hookNetworkConfig 记录误打 SSL_CONTEXT/SSL_INIT → SSL_NETWORK_CONFIG/SSL_CONFIG_CHECK。

### 12.6.2 矩阵脚本与用例缺陷（假阴性/假 PASS 链）

- 20 矩阵单元素嵌套数组扁平化（process 域）→ 逗号算子；DetectionMonitor 让位设计下 dev-all 断言 DETECT_* 不可达 → 独立 detect-obs 域；
- GetTt 数组结果致 Assert [bool] 绑定失败（SSL 二态断言自 V36 起从未真正执行）→ [string] 强转；
- ResetState 清库竞态（spool 排空 ≠ 入库完成）→ 3s 落库窗口；
- E-SSL profile 单开 pin/verifier 时 trust 链先拒 → 组合开档；
- testtarget 用例：pinner pattern 带端口 IAE、localhost 与 ip.p12 证书 SAN 不匹配 → 修正。
- tt-mcp-lib McpRpc 宿主回收自愈（status -1/404 → EnsureHost+重握手+重试一次）；WaitSpoolDrain 前置 EnsureHost；M-CFG-01b 断言窗口自愈重试。
- 99-report.ps1 扩展聚合 L/E/C/S/M 五轮（MCP 矩阵产物移 D:\Temp\modev-tt\mcp-round.json）。

### 12.6.3 E 矩阵整轮与聚合说明

- LDPlayer VM 当日 20+ 次死亡（主机内存压力，用户进程占满），E 矩阵整轮被 SEC 域 screencap（V35 已列 LDPlayer EGL fastpipe 缺陷）起步崩溃拖累；SSL 五路以分段实证（每路单轮二态+记录全绿）验收。
- 聚合 100% 终验建议真机（arm64）或独立稳定模拟器——与 V35 报告结论一致。
- androidTest 测后已按恢复序列重装 + enabled=1 + scope 行 + 重启，注入已验证恢复。
