# TASK-V40：UI/服务/MCP 全量审计与整改任务书

| 项 | 值 |
|---|---|
| 版本 | V40（基于 V39 工作树，2026-09-07） |
| 性质 | 全量代码审计报告 + 分任务实施任务书 |
| 审计方法 | 3 路并行代码审计（UI 组件与运行按钮 / 记录数据架构 / 服务·诊断·MCP·令牌），关键结论经源码逐行复核（file:line 均为工作树实位） |
| 决策状态 | 4 项方向性决策已由维护者确认（见 §0.3） |
| 交付边界 | 本文档为唯一交付物；**实施待维护者确认本文档后另行启动** |
| 关联文档 | `AGENTS.md`（现行契约）、`TASK-MD3-AUDIT.md`（2026-08-28 历史审计，§1.8 说明其结论偏差） |

---

## 0. 总纲

### 0.1 需求输入（维护者原始 8 项）

| # | 需求 | 类型 |
|---|------|------|
| R1 | 应用列表不要有运行按钮；运行按钮放在应用界面 Appbar 最右侧 | 行为/布局 |
| R2 | 数据管理界面支持查看所有记录列表（按应用×类型 / 按类型两维度），清理并入记录列表 | 功能 |
| R3 | 诊断界面管道统计不折叠 | 行为 |
| R4 | 分析通信服务是否有必要；若无必要仅保留 MCP 一个 HTTP 服务 | 分析 |
| R5 | 服务界面功能选项描述精简 | 文案 |
| R6 | MCP 服务与令牌认证不强制绑定；启用 MCP 可以不启用令牌 | 行为/安全语义 |
| R7 | 令牌界面重构：高级、简约、优美 | 设计 |
| R8 | 前端仅使用 MD3 组件库或其他专业、热门、持续维护的组件库，杜绝手搓自研组件和布局 | 设计系统 |

### 0.2 审计总结论（TL;DR）

- **8 项需求中 7 项存在可指认的代码级违规/差距，共 38 条违规条目**（§1 逐条列证）；R4 经架构分析结论为"必要、保留"（§1.4），不产生整改任务。
- 数据层对 R2 **零新查询**即可支撑：`RecordFilter(packages=null)` 已端到端贯通 `paged/counts/deleteByFilter`，缺的只是 VM 变体、路由与入口。
- R6 的强制绑定存在于**四层**（仓库写入级联 / init 重推导 / 服务端判定 / UI 文案），并有 2 个单测用例锁定级联行为，须同步反转。
- R8 审计出 **R 类（重新发明 MD3 模式）12 处 + 手搓图片加载器 1 处**为整改对象；**D 类（无 MD3 等价物的领域渲染）12 处保留**；W 类（MD3 薄封装）38 处保留——它们是对话框统一契约（`DialogConventionTest`）的强制收敛点，本身即 MD3 组件。
- 全部整改**不新增任何监听/服务端行为**，wire 协议 VERSION 恒 4 零感知；`records/all` 退役决定（V24 G2）不恢复——R2 走"按类型全局列表"而非"全部记录混合列表"。

### 0.3 已确认决策（维护者拍板，实施不得偏离）

| 决策 | 内容 |
|---|---|
| D-1 | R1：应用列表行运行按钮移除（含配套惰性解析死代码）；**每应用监控页** Appbar 动作改序为 `[清空记录][启动]`，启动恒最右。 |
| D-2 | R8 口径：R 类全替换 + 引入 **Coil** 替换手搓图标加载；D 类保留但严格基于 MD3 原语 + `Tokens`；W 类保留。 |
| D-3 | R2 落地：类型计数行可点 → 按类型全局列表（10 类×全部应用）；数据页新增「按应用浏览」区；10 个按类型危险清理行退役、清理并入各列表页搜索条动作槽；**「清空全部」保留**在数据页。 |
| D-4 | R6 解耦彻底：外部访问不再强制令牌（0.0.0.0 + 无令牌允许），服务端判定收敛为仅 `tokenAuthEnabled`；风险以 UI 文案警示；保留"启用认证且令牌集为空时自动补一枚"防锁死。 |
| D-5 | 交付顺序：先本文档 → 维护者确认 → 实施。 |

### 0.4 全局不变量（实施全程不得破坏）

1. **i18n 双语齐备**：所有新增/改写字符串 en/zh 同步、占位符形态一致（`StringResourceParityTest`）；ui 包禁中文字面量（`HardcodedStringConventionTest`）。
2. **分页契约**（AGENTS #14/#15）：新列表必须走 `LogRepository.observe/paged`（`limit=PAGING_WINDOW_CAP`），`records` 流 `cachedIn(viewModelScope)`，`LazyPagingItems` 手动索引循环 + 占位。
3. **时间线契约**（AGENTS #10）：组头 testTag `timeline-header` 保留，组头/行 key 规则不变。
4. **对话框/Sheet 契约**（AGENTS #27）：新增确认一律 `ModevConfirmDialog`（无 icon），弹层一律 `ModevSheet*`；`DialogConventionTest` 源码扫描不得新增违规。
5. **Appbar 排他契约**（AGENTS #24 V39 R5）：凡 Appbar 含 SearchBar 的页面 actions 恒空（`AppbarSearchExclusivityTest`）；R1/R2 的新入口不得违反（数据页、监控页均为 `ModevTopBar`，无搜索条，可安全加 actions）。
6. **MCP 红线**：令牌值恒不进 MCP 工具输出与诊断快照；`records_clear_all` 等危险工具 `confirm:true` 必填；MCP 与 wire 完全独立生命周期；wire VERSION 恒 4。
7. **视觉 token**（AGENTS #25）：alpha 一律 `ui/theme/Tokens`；卡片容器色 `surfaceContainerLow`；`SettingsThemeContrastTest` 20 seed 门禁不回退。
8. **lint 门禁**：`lintModernRelease` 0 error、不新增 warning（基线 49 warning）；新增 Coil 直依赖需评估 `NewerVersionAvailable` 计数规则。

---

## 1. 违规清单（全量，file:line 实证）

严重度：P1 = 阻塞验收的体验/正确性缺陷；P2 = 应修复的质量缺陷；INFO = 分析结论，无违规。

### 1.1 R1 运行按钮（5 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V1.1 | P1 | 应用列表每行 trailing 含运行按钮 `app-row-run`（PlayArrow 图标，位于作用域 Checkbox 右侧=行最右），违反"列表行无运行按钮" | `ui/apps/AppsScreen.kt:483-495`（AppRowItem trailing Row）；行实例 :226-245、:307-327 | 移除按钮；行 trailing 仅留作用域 Checkbox |
| V1.2 | P2 | 行内按钮配套的惰性启动解析机制随按钮成为死代码：每行 `LaunchedEffect(onEnsureLaunchable)` + VM 级 launchable map | `AppsScreen.kt:425,232,313`；`ui/apps/AppsViewModel.kt:115-125`（`_launchable`/`resolveLaunchable`，列表扫描零开销红线所需） | 一并退役（`AppCatalogRepository.resolveLaunchable` 仓储能力保留，监控页仍在用） |
| V1.3 | P1 | 每应用监控页 Appbar 动作序为 `[启动 app-launch][清空记录 app-clear-records]`，启动不在最右 | `ui/apps/AppMonitorScreen.kt:172-188`（ModevTopBar actions；TooltipAction 封装 :363-378） | 改序 `[app-clear-records][app-launch]`；正文无运行按钮（V37 已上收，无回归） |
| V1.4 | P1 | 插桩测试锁定旧行为 `appRow_showsRunButtonAtTrailing`（轮询并断言 `app-row-run` 显示） | `app/src/androidTest/.../FourScreensNavTest.kt:167-190`；另 :46、:88 注释、:89/:240/:271/:316/:387 根页 `app-launch==0` 断言组 | 用例反转为"行内恒无 `app-row-run`"；根页断言组不变（D-1 不给列表页加启动） |
| V1.5 | P1 | AGENTS 强约束 #24 冻结"应用列表行 trailing = [作用域 Checkbox][运行按钮]，运行恒最右（app-row-run）" | `AGENTS.md` 强约束 #24 | 契约改写：行 trailing 仅作用域 Checkbox；启动入口唯一 = 监控页 Appbar 且恒最右 |

### 1.2 R2 数据管理记录列表（6 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V2.1 | P1 | 数据管理页类型计数行不可点击，无任何列表导航——"支持查看所有记录列表"不成立 | `ui/settings/DataSettingsScreen.kt:93-104`（`SettingsInfoItem` 无 onClick；`recordTypeIcon` :159-170、计数文案 :173-184） | 计数行可点 → 按类型全局列表 |
| V2.2 | P1 | 按类型全局列表不存在：路由仅有 10 条 `records/{type}/{packageName}`，`ModevNav` 无对应目的地；`records/all` 已于 V24 G2 退役（不恢复） | `ui/navigation/ModevRoutes.kt:37-54,72-90`；`ui/ModevNav.kt:267-486`；退役断言 `FourScreensNavTest.kt:90` | 新增 `records/{type}`（全局形态）10 目的地，复用 `MonitorRecordsContent` |
| V2.3 | P1 | VM 工厂强制双预设非空，全局变体缺失（数据层**零新查询**：`RecordFilter(recordTypes=setOf(t), packages=null)` 已端到端支持 paged/counts/deleteByFilter） | `ui/records/RecordsViewModel.kt:30-36`（构造器注释"必传非空"）、:81-94（Factory）；`LogRepository.kt:299-328`（observe）、:361-377（count）、:410-428（deleteByFilter，:414 hasConstraint 守卫对 type-only 过滤放行）、DAO 哨兵 WHERE `CryptoRecordDao.kt:402-434` | `RecordsViewModel` 增设 packages 可空变体；`clearCurrentScope` 语义随预设自然生效；Factory 增全局工厂函数 |
| V2.4 | P1 | 清理未并入列表：数据页 10 个按类型 DangerRow（testTag `clear-type-{wire}`）与各列表页已有的 `records-clear-type` 搜索条动作清理**双入口重复**；需求为清理并入列表 | `DataSettingsScreen.kt:114-132`（DangerRow :120-126、clear-all :127-131；`ClearTarget` sealed interface :54-57）；列表侧已存在 `MonitorRecordsScreen.kt:460-473 ClearTypeAction` + 确认 `records-clear-confirm` :441-452 → `RecordsViewModel.clearCurrentScope` :73-77 | 退役 10 个按类型 DangerRow 及 `ClearTarget.ByType`；**「清空全部」（clear-all）保留**（D-3） |
| V2.5 | P2 | 全局列表行无应用名上下文：`RowModel.packageName` 已有但仅长按摘要消费，行内不显示——混合应用的全局列表无法区分来源 | `ui/components/RecordRow.kt`（RowPresentation 各族 builder :150-160 起）；`RowModel.kt:31-37` | 行内补应用名（元行或徽章；仅全局列表注入上下文，per-app 列表保持现状降噪） |
| V2.6 | P2 | 数据页缺「按应用浏览」维度入口（`observeRecordCounts()` 全类型 Map<pkg,count> 已有，AppsViewModel 列表徽章在用） | `LogRepository.kt:516-517`；`ui/apps/AppsViewModel.kt:175,208` | 数据页新增「按应用浏览」区：有记录的应用 + 计数 → 跳该应用监控页（per-app×类型维度既有入口即监控页 EntryRow，不重复造列表） |

### 1.3 R3 诊断管道统计折叠（1 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V3.1 | P2 | 管道统计默认折叠：`statsExpanded` 默认 false 且 `rememberSaveable` 跨重建保持，7 行统计（已接收/丢弃(队列满)/写入失败/超容量裁剪/被拒(握手)/丢弃(畸形)/被拒(包不匹配)）藏于 `AnimatedVisibility`；折叠行组件 `StatsToggleRow`、折叠箭头 contentDescription `section_collapse/section_expand` | `ui/settings/DiagnosticsScreen.kt:105`（状态）、:261-312（统计卡）、:268（切换行调用）、:269-311（AnimatedVisibility）、:413-439（StatsToggleRow 定义）、:432（字符串引用）；`values/strings.xml:232-233`、`values-zh/strings.xml:230-231`（**唯一引用即此处**，随之退役）；统计卡无 testTag（补齐） | 恒展开直渲染 7 行；删除状态/切换行/AnimatedVisibility/两条字符串 |

### 1.4 R4 通信服务必要性分析（INFO，结论：保留）

**结论：wire ConfigServer 必要，保留；"仅保留 MCP 一个 HTTP 服务"事实上已成立**——全 App 仅两个监听服务：raw TCP 1511（wire）+ HTTP 1510（MCP），MCP 已是唯一 HTTP 服务。

| 论点 | 证据 |
|---|---|
| wire 是**原始 TCP**，不是 HTTP：`ServerSocket` + 行分隔 JSON（每行一个 `JsonObject`），无 HTTP 组帧 | `data/server/ConfigServer.kt:68,128`；`core/codec/WireCodec.kt:19-24`；`core/codec/WireProtocol.kt:26`（端口 1511）、:28-29（MAGIC "MDEV"/VERSION 4）、:32-46（hello/config/record/ack/ping/pong） |
| wire 是注入侧**唯一数据通道**：记录上传（`server.onRecord` → Channel 8192 → 批量 32/200ms → Room 写入 → ACK 游标回推 → 注入侧 spool WAL 裁剪）、per-session 配置推送、`pushToPackage`/`pushToAllConnected`、心跳 | `ConfigServer.kt:64`（onRecord）、:231-238（握手 resolve+push）、:303-312、:319-328、:268-278、:386-399（ACK writer）、:403（120s 读超时）；`data/repo/LogRepository.kt:97,105,128-132,153-172,174-241,231-238`；注入侧 `hook/ConfigChannel.kt:194`（连 127.0.0.1:1511）、`hook/RecordSink.kt:17-23,136-138,177-205`（WAL+ack 游标+至少一次重放） |
| MCP **技术上无法替代**：NanoHTTPD Streamable HTTP 仅 POST 单请求单响应，GET 405（无 SSE 推送）、通知仅 202——无服务端主动推送通道，config push/ACK/心跳全需客户端轮询或新建 SSE/WS 层；注入侧为 5KB 级裸 socket 客户端（无 HTTP 栈、2s 连接超时/60s 读超时/20s 心跳/指数重连 2s→30s），加 HTTP+JSON-RPC 栈增大 footprint 与 hook 面风险；WAL+ack 至少一次语义依赖持久双向流 | `mcp/McpHttpEngine.kt:20-24`（NanoHTTPD）；`mcp/McpServer.kt:134`（GET 405）；`mcp/protocol/McpProtocol.kt:5,16`（与 wire 零共享消息形态；单端点 POST /mcp）；`hook/ConfigChannel.kt`（客户端实现） |
| 无第三服务：`ServerSocket` 仅 ConfigServer；NanoHTTPD 仅 McpHttpEngine；无 Ktor/`com.sun.net.httpserver` | 全库扫描（`rg ServerSocket|NanoHTTPD|Ktor|httpserver`） |

**附带产物（可选，随 T2 落地）**：服务页「通信服务」区标题或副标题标注"注入侧通道（TCP）"，消除"通信服务=HTTP"的误解。

### 1.5 R5 服务页文案冗长（4 条）

目标口径：zh ≤16 字 / en ≤48 字符；双语 parity 保持；涉及 R6 的文案一并改写（V5.2 与 V6.5 同体）。

| # | 严重度 | 键 | 现状 en（字符） | 现状 zh（字符） | 证据 |
|---|---|---|---|---|---|
| V5.1 | P2 | `mcp_enabled_hint` | 96 | 55 | `values/strings.xml:424`；`values-zh/strings.xml:417`；引用 `ServicesScreen.kt:106` |
| V5.2 | P2 | `mcp_external_access_hint` | 79（含强制绑定表述） | 31 | `values/strings.xml:426`；`values-zh/strings.xml:419`；引用 `ServicesScreen.kt:114` |
| V5.3 | P2 | `settings_external_access_hint` | 77 | 30 | `values/strings.xml:416`；`values-zh/strings.xml:409`；引用 `ServicesScreen.kt:89` |
| V5.4 | P2 | `mcp_token_auth_hint` | 76 | 25 | `values/strings.xml:430`；`values-zh/strings.xml:423`；引用 `ServicesScreen.kt:123` + `McpTokensScreen.kt` 认证开关副标题 |

注：服务页行标题均短（≤20 en / ≤6 zh），无需改；无 wire 开关行（wire 恒开，仅外部访问+端口两行）。

### 1.6 R6 MCP/令牌强制绑定（6 条）

绑定现状四层 + 测试锁定 + 文案，全部需反转/移除：

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V6.1 | P1 | `setMcpExternalAccess(true)` 强制开令牌认证（`persistMcpTokenAuth(true)`）且令牌集空时立即造一枚 | `data/repo/SettingsRepository.kt:349-361`（:351-353 造令牌、:354 强制认证；注释 :307、:346-348"禁止 0.0.0.0 裸奔"） | 删除强制段：仅持久化 externalAccess + 重启 MCP |
| V6.2 | P1 | `setMcpTokenAuth(false)` 级联关外部访问（关认证必须连带关 0.0.0.0） | `SettingsRepository.kt:368-380`（:372-377 级联；注释 :364-366"不允许无认证全接口监听"） | 删除级联：认证开关独立读写 |
| V6.3 | P1 | init 重推导强制：进程重启后 externalAccess=true 则令牌认证被重新置开 | `SettingsRepository.kt:489-490`（`_mcpTokenAuth.value = prefs… || (storedMcpExternal && tokens.isNotEmpty())`） | 改为直读 `mcp_token_auth` prefs，无 `||` 项 |
| V6.4 | P1 | 服务端双保险：`tokenAuthEnabled || externalAccess` 即强制 Bearer（含回环）——即使仓库状态被绕过，0.0.0.0 无令牌在 HTTP 层不可达 | `mcp/McpServer.kt:125-133`（注释 :123-124"externalAccess 双保险"） | 条件收敛为 `tokenAuthEnabled`；注释同步改写 |
| V6.5 | P2 | UI 文案宣告绑定："enabling this also turns on token authentication"/"开启将同时启用令牌认证" | `mcp_external_access_hint`（`values/strings.xml:426`、zh:419）；`ServicesScreen.kt:44-45` 头注释 | 随 V5.2 改写为独立语义 + 无认证风险提示 |
| V6.6 | P1 | 单测锁定级联行为：`external access enables auth and derives token`、`disabling auth cascades external access off` | `app/src/test/.../SettingsRepositoryMcpTokenTest.kt:95,104`（其余用例 :58,65,79,113,128,140 与绑定无关，保留） | 反转为解耦断言：①开外部访问 → 认证状态不变；②关认证 → 外部访问不变；③服务端矩阵（tokenAuth=false 且 external=true → 401 不触发）入 `McpServer` 测试面 |
| — | — | 保留不变（防锁死，非绑定语义） | `setMcpTokenAuth(true)` 令牌集空时自动补一枚（:369-371）；撤销最后一枚不停认证、空集=全拒（:389-392，`revoke last token keeps auth on deny-all` 用例 :140） | 原样保留 |
| — | — | 保留不变（原有能力） | 回环无认证默认可用（`MCP_TOKEN_AUTH` 默认 false :310-313、`MCP_ENABLED` 默认 true :295-298；服务端条件 false 时跳过 Bearer）；`mcpTokenSetUpdater` 原地生效不踢会话（:324 → `ModevApp.kt:178`，`McpServer.kt:37 requiredTokens @Volatile`） | 无改动 |

### 1.7 R7 令牌页现状差距（1 条设计级 + 约束集）

| # | 严重度 | 现状差距 | 证据 |
|---|---|---|---|
| V7.1 | P2 | 平铺无层次：`SettingsSection`×2 + `ListItem` 行列式；认证开关与令牌列表混排（认证开关在**两个页面**重复出现：服务页 :120-128 与令牌页 :122-131，同一 `setMcpTokenAuth`）；空状态为纯 `Text`；新建入口藏于 Appbar 单图标；令牌行 trailing 挤 3 个图标按钮（显隐/复制/撤销）；显隐/复制/撤销无破坏性分级 | `ui/settings/McpTokensScreen.kt`（282 行）：TopBar action :100-109、认证区 :122-131、令牌区 :133-161（空态 :134-141）、新建 ModevInputSheet :166-181、撤销确认 :183-194、TokenRow :200-281（掩码 12 圆点 :220、mono 显值、`formatDay(createdAt)`、legacy "导入" :222-226）；VM `McpTokensViewModel.kt`（仓库流直读，写路径经 `SettingsRepository.addMcpToken :383-387`/`revokeMcpToken :390-392`） |

重设计约束（实施不得违反）：
- 全 MD3 + `Tokens`，不留组件级自研（与 R8 同规）；
- 红线不变：令牌值宿主本地秘密（prefs `modev_settings`/`mcp_tokens`），恒不进 MCP 工具输出与诊断快照（`McpTokenEntry.kt:7`、`DiagnosticsReport.kt:57`）；`mcpTokenSetUpdater` 原地生效、不重启不踢会话；
- 撤销最后一枚不停用认证（空集=全拒）语义保留并必须可从 UI 理解；
- legacy 单键迁移形态（`createdAt<=0` 显示"导入"）保留；
- i18n 双语、计数 plurals（`mcp_tokens_count`）。

### 1.8 R8 组件库合规（14 条 + 口径说明）

**口径（D-2）**：R 类=重新发明/偏离 MD3 既有模式 → 全替换；D 类=无 MD3/成熟库等价物的领域渲染 → 保留但仅以 MD3 原语 + `Tokens` 搭建；W 类=MD3 组件薄封装（38 处，含 `ModevSheet`/`ModevConfirmDialog`/`SettingsItem` 系）→ 保留（`DialogConventionTest` R1/R3 强制其作为唯一出口，:40-50/:61-72）。

**R 类违规（12 处 + 附加 2）**：

| # | 严重度 | 违规组件 | 证据 | 替换方案 |
|---|---|---|---|---|
| V8.1 | P1 | `CollapsedSearchInput`：手搓 Row(56dp, 圆角 28, surfaceContainerHigh, detectTapGestures) 搜索胶囊，系 MD3 SearchBar 输入域的自制替代；KDoc 自述为"唯一例外（D4 单向决策）" | `ui/components/SearchInputComponents.kt:125`（KDoc :109-122 记录 focus-expansion 长按缺陷动因）；宿主 `AppsScreen.kt:148`、`MonitorRecordsScreen.kt:341` | 替换为标准 `SearchBar` + `SearchBarDefaults.InputField`（`SearchInputField` :57 已委托 InputField，收敛到单一 MD3 路径）；行为回归风险见 §4 |
| V8.2 | P2 | `StatusDot`：8dp Box 圆点自制徽章（未用 M3 Badge） | `ui/components/StatusDot.kt:30`；消费点 `AppsScreen.kt:465-472`（AppRowItem supporting）、`AppMonitorScreen.kt:446` StatusPill 内 | 换 M3 `Badge()`（点形态）；tooltip 语义随调用点内联 `TooltipBox` |
| V8.3 | P2 | `EmptyState`：图标圆底 + 文案的自制空态组件（M3 无该模式，但属可内联布局） | `ui/components/EmptyState.kt:34`；调用点：记录列表三态（`RecordTimelineList.kt:62-87` 区间内）等 | 调用点内联组合（Icon+Text Column），组件与 `EmptyStateTest` 删除 |
| V8.4 | P1 | 记录行卡片脚手架自制：`RecordRow`（Surface 卡 + clickable Row）+ `RecordListCard`（leading 徽章/headline/meta/尾栏）+ `LeadingBadge`（36dp Box，13% 色）+ `MicroBadge`（6dp 圆角 Surface 胶囊）+ `TimeText`——整体绕开 M3 `ListItem` 行模式 | `ui/components/RecordRow.kt:103,170,239,262,221`；AGENTS #20 冻结该脚手架 | 重写为 M3 `ListItem`（leading=tonal 图标内联 Box、headline mono Medium、supporting 元行、trailing 时间/Badge）；`record-row` testTag、`RowPresentation` 单一数据出口、长按复制三族保留；AGENTS #20 同步改写 |
| V8.5 | P2 | `TimelineHeader`：时间戳胶囊自制（Surface+Text） | `ui/components/TimelineHeader.kt:19`；消费 `RecordTimelineList.kt:108` | 内联到 `RecordTimelineList`（Surface+Text 即 MD3 原语）；`timeline-header` testTag、组头 key 规则不变（AGENTS #10） |
| V8.6 | P2 | `StatusPill`：tonal 圆形胶囊（Surface CircleShape + StatusDot + 文案） | `ui/apps/AppMonitorScreen.kt:446` | 内联 Surface+Badge+Text（Hero 卡内域渲染） |
| V8.7 | P2 | 应用行记录数胶囊：Surface CircleShape secondaryContainer 自制计数徽章 | `ui/apps/AppsScreen.kt:441-451` | 换 M3 `Badge`（带 content 计数形态） |
| V8.8 | P2 | 外观颜色选择：手绘色板网格（CircleShape Box + border + Check 图标 + LazyVerticalGrid 自制选择控件） | `ui/settings/SettingsScreen.kt:437-471`（`:318 IconChoiceRow` 为 W 保留） | 换 `FilterChip` 行（leadingIcon 色点、selected 态），语义/无障碍由 MD3 承担 |
| V8.9 | P1 | `AppIcon` 手搓图片加载：PackageManager drawable→Bitmap + 自制 LruCache + 首字母占位 Box；全 App 无任何图片库 | `ui/components/AppIcon.kt:53`（缓存 :32）；`gradle/libs.versions.toml` 无 Coil | 引入 **Coil**（coil-compose，维护活跃的 Compose 事实标准）：`AsyncImage(model=PackageManager drawable)` + error 槽首字母占位；删除手搓缓存/转换 |
| V8.10 | P2 | `ListenRow` 与 `ToggleListItem` 近重复实现（自家 #25"设置类行开关统一 ToggleListItem"未收敛） | `ui/apps/PerAppListenSettingsScreen.kt:581`；标准件 `PerAppSettingRows.kt:57 ToggleListItem` | 统一到 `ToggleListItem`，删除重复 |
| V8.11 | INFO | `FullscreenTextDialog` 保留：全屏 `Dialog` + 自有 TopAppBar + ChunkedText 虚拟化，系 `DialogConventionTest` 明文豁免形态（`Dialog(` 不在扫描面），且为 4M 护栏查看器契约的一部分 | `ui/detail/markdown/FullscreenTextDialog.kt:50,65,100`；AGENTS #3/#27 | 保留（归类 D 边界，不整改） |
| — | — | **D 类保留清单**（无 MD3/库等价物，仅基于 MD3 原语 + Tokens）：`MiddleEllipsisText`（Text+TextUtils.ellipsize 重测量，`MiddleEllipsisText.kt:30,62`）、比例条 `DomainBlock`+`LegendItem`（`AppMonitorScreen.kt:566,627`）、代码组标签页 `ModevMarkdownCodeFence`（MD3 SecondaryTabRow，`RecordMarkdownContent.kt:285`）、`MdCodeBlock`（`RecordMarkdownContent.kt:365`）、目录导航 `RecordTocContent`（D2.1 决策，`RecordTocContent.kt:27`）、`KindIcons` 映射（`KindIcons.kt:48`）、`Tokens` 设计令牌（`Tokens.kt:7`）、kind/status 色彩映射（`KindColorTokens.kt:77`、`StatusColorTokens.kt:47` 等）、双 `countText`（locale 数字格式，`AppsScreen.kt:509`/`AppMonitorScreen.kt:696`） | — | 保留；实施中如触碰，维持"MD3 原语 + Tokens"边界 |
| — | — | **历史审计结论修正**：`TASK-MD3-AUDIT.md`（2026-08-28）曾判"所有自定义组件均为 M3 组件的合理封装/扩展，无手搓自研控件"——漏计本清单 R 类 12 处（StatusPill/LeadingBadge/MicroBadge/TimelineHeader/色板/计数胶囊等），本文档取代其结论 | 仓库根 `TASK-MD3-AUDIT.md` | 归档备注 |

**测试面波及（V8.12）**：`app/src/androidTest/.../ui/components/` 下 `EmptyStateTest`（删）、`SearchInputComponentsTest`（重写为标准 InputField 形态）、`RecordRowLayoutTest`（重写为 ListItem 形态）、`SettingsComponentsTest`/`TipIconButtonTest`/`ModevTopBarTest`（W 类保留，回归即可）；单测侧 `MiddleEllipsis` 纯函数直测、`AnchorScrollRoboTest`、`RecordPagingProbeTest` 必须保持全绿（重构不得触碰 #10/#14/#15 契约）。

### 1.9 违规统计

| 需求 | 条目数 | P1 | P2 | INFO |
|---|---|---|---|---|
| R1 | 5 | 3 | 1 | 1(V1.5 契约) |
| R2 | 6 | 4 | 2 | — |
| R3 | 1 | — | 1 | — |
| R4 | 1(结论) | — | — | 1 |
| R5 | 4 | — | 4 | — |
| R6 | 6(+2 保留项) | 4 | 1 | 1(保留项说明) |
| R7 | 1 | — | 1 | — |
| R8 | 14 | 4 | 6 | 2(保留/修正) + 2 附加 |
| **合计** | **38** | **15** | **16** | — |

---

## 2. 任务分解（实施顺序 T1→T8）

> 每任务含：目标 / 改动面 / 实施要点 / 测试影响 / 契约同步 / 验收标准。顺序按依赖与风险排列：先低风险独立项（T1/T2），再行为迁移（T3/T4），后大面积 UI 重构（T5/T6），契约与门禁收尾（T7/T8）。**每任务一个独立提交，契约同步随行为变更同提交落地**（避免门禁与实现互相矛盾）。

### T1 诊断管道统计恒展开（R3/V3.1）— 复杂度 S ✅ **已完成（V40 实施备注：删除 `statsExpanded`/`StatsToggleRow`/`AnimatedVisibility`，管道统计改 `SettingsSection` 承载标题并恒展开渲染 7 行统计；统计卡/行补 testTag `diagnostics-stats`/`diagnostics-stat-*`；退役 `section_expand`/`section_collapse` 双语字符串；DiagnosticsScreenTest 新增 `pipelineStats_alwaysExpanded_noInteraction` 用例。定向测试：编译+i18n parity 单测绿、诊断页插桩 3 用例绿）**

- **改动面**：`ui/settings/DiagnosticsScreen.kt`；`values/strings.xml`、`values-zh/strings.xml`。
- **实施要点**：
  1. 删除 `statsExpanded` 状态（:105）、`StatsToggleRow` 调用与定义（:268、:413-439）、`AnimatedVisibility` 包裹（:269-311）→ 7 行 `SettingsInfoItem`（:275-309）直渲染；
  2. 删除 `section_collapse`/`section_expand` 字符串（en :232-233 / zh :230-231，全库唯一引用已核实）；
  3. 统计卡补 testTag（建议 `diagnostics-stats`），行 testTag `diagnostics-stat-{key}` 便于插桩；
  4. 复制快照逻辑（:120-167）不受折叠态影响——已核实本就无条件全量，无需改。
- **测试影响**：无既有用例锁定折叠态；新增 Robolectric 或插桩断言"7 行统计恒可见"。
- **契约同步**：AGENTS #28 诊断页描述补一句"管道统计恒展开"。
- **验收**：诊断页无需任何交互即可见全部 7 项统计；单测全绿；两条退役字符串无残留引用。

### T2 MCP/令牌解耦 + 服务页文案精简（R6/V6.1-V6.6 + R5/V5.1-V5.4）— 复杂度 M ✅ **已完成（V40 实施备注：删除 `setMcpExternalAccess` 强制认证+派生令牌、`setMcpTokenAuth` 级联关外部访问、init 重推导（认证直读 prefs）；`McpServer` 判定收敛为仅 `tokenAuthEnabled`；AGENTS 词汇表+#26 同步改写（"恢复级联"列入禁用旧词）；4 条 hint 精简为 en≤50/zh≤21 字符并移除绑定表述、外部访问副标题承载无认证风险提示；新增 `settings_server_section_injection` 键，服务页 wire 分区标注「注入侧」；测试反转：仓库 2 个级联用例改为解耦断言 + 新增重放用例、MCP 生命周期 `external_access_requires_bearer_token` 拆为解耦双用例（无认证直通/有认证 401）、ServicesScreenTest 新增 `mcpExternalAccess_toggleOn_keepsTokenAuthIndependent`（令牌数不变断言，修正了"起点为 0"的错误前提——令牌跨进程持久）。定向测试：仓库/MCP/i18n 单测绿、服务页+诊断页插桩 7 用例绿；插桩中模拟器 VM 崩溃一次，按 AGENTS 序列冷重启恢复后复测通过）**

- **改动面**：`data/repo/SettingsRepository.kt`、`mcp/McpServer.kt`、`ServicesScreen.kt`、`values/strings.xml`、`values-zh/strings.xml`、`SettingsRepositoryMcpTokenTest.kt`、`ServicesScreenTest.kt`。
- **实施要点**：
  1. `setMcpExternalAccess`：删除强制认证 + 自动造令牌段（:351-354），仅持久化 + 置流 + 异步重启；同步清理 :307/:346-348 注释；
  2. `setMcpTokenAuth`：删除"关认证级联关外部访问"（:372-377）；保留"开认证且令牌集空自动补一枚"（防锁死）；
  3. init（:489-490）：`_mcpTokenAuth` 直读 prefs，去掉 `|| (storedMcpExternal && tokens.isNotEmpty())` 重推导；
  4. `McpServer.kt:125`：判定收敛为 `if (tokenAuthEnabled)`，注释改写（"认证独立开关，启用后全接口强制 Bearer 含回环；外部访问与认证互相独立"）；
  5. 文案四条重写（V5.1-V5.4，zh ≤16 字）+ `mcp_external_access_hint` 须承载风险提示（无认证时局域网可访问），例如 zh：`绑定 0.0.0.0 供局域网访问。`+ 认证未开时行尾追加风险态；`ServicesScreen.kt:44-45` 头注释同步；
  6. 服务页「通信服务」区副标题（可选产物，§1.4）标注"注入侧通道（TCP）"——若加，走 `SettingsInfoItem` 既有副标题位。
- **测试影响**：
  - `SettingsRepositoryMcpTokenTest`：:95、:104 两用例反转为解耦断言（开外部访问认证不变；关认证外部访问不变）；新增"开认证自动补令牌"保持断言（:65 已有，回归）；
  - `ServicesScreenTest`（插桩）：文案断言若锁了旧 hint 文本需同步；`services-mcp-token-auth` 开关行为（:101-109）应保持；
  - MCP parity/协议测试面（`McpToolParityTest`、401 路径）：服务端认证矩阵新增组合 `tokenAuth=false && external=true → 不鉴权`、`tokenAuth=true → 全接口含回环鉴权`；
  - `24-run-mcp.ps1` 交付门禁（42 子断言）如覆盖级联行为需同步。
- **契约同步**：AGENTS 词汇表「MCP 服务」条目"外部访问开关 = 0.0.0.0 且级联强制令牌认证"与强约束 #26"外部访问恒蕴含认证(仓库级联,服务端双保险)"改写为"外部访问与令牌认证相互独立；服务端仅依 `tokenAuthEnabled` 鉴权；认证启用且令牌集空 = 全拒（自动补一枚防锁死）"。
- **验收**：四态矩阵（认证×外部访问）行为符合 D-4；文案达长度口径且双语 parity；全量单测绿。

### T3 运行按钮迁移（R1/V1.1-V1.5）— 复杂度 S ✅ **已完成（V40 实施备注：AppRowItem 删除行内运行按钮与 launchable/onEnsureLaunchable/onLaunch 参数、trailing 仅留作用域 Checkbox；AppsScreen 删除 onLaunch 形参与 launchable 收集；AppsViewModel 删除 launchable map/resolveLaunchable（AppCatalogRepository.resolveLaunchable 保留供监控页）；AppMonitorScreen Appbar 动作改序 `[app-clear-records][app-launch]` 启动恒最右；退役 `app_run` 双语死字符串；ModevNav 断开根页 launchApp 接线（监控页保留）；FourScreensNavTest `appRow_showsRunButtonAtTrailing` 重写为 `appRow_hasNoRunButton` 反向断言（根页 app-row-run 恒 0），根页 app-launch==0 断言组保留并追加 app-row-run==0；AGENTS #24 契约改写（启动唯一入口=监控页 Appbar 恒最右，"恢复行内运行按钮"列入禁用旧词）。定向测试：apps VM + i18n 单测绿；插桩 DiagnosticsScreenTest 3 绿、ServicesScreenTest 4 绿、FourScreensNavTest 5 跑 3 绿——2 失败（appRowOpens 数据页滚动敏感断言 + recordRowOpens 触摸注入）经组合矩阵归因为 V39 报告记录的预存环境类失败（v38 基线同败、单跑全绿、类内方法哈希序变化后暴露），其数据页断言面属 T4 重构范围，T4 重写后复验）**

- **改动面**：`ui/apps/AppsScreen.kt`、`ui/apps/AppsViewModel.kt`、`ui/apps/AppMonitorScreen.kt`、`FourScreensNavTest.kt`、`AGENTS.md`。
- **实施要点**：
  1. `AppRowItem` trailing 仅留作用域 Checkbox（删 :483-495）；行实例两处（折叠列表/展开搜索）同步；
  2. 删 `onEnsureLaunchable` 参数与 `LaunchedEffect`（:425,232,313）；`AppsViewModel` 删 `_launchable/launchable/resolveLaunchable`（:115-125）（`AppCatalogRepository.resolveLaunchable` 保留——`AppMonitorViewModel.kt:70` 在用；其单测 `AppCatalogRepositoryTest.kt:108-132` 不动）；
  3. `AppMonitorScreen` TopBar actions 改序 `[app-clear-records][app-launch]`（:177-186），testTag 不变；
  4. `FourScreensNavTest`：`appRow_showsRunButtonAtTrailing`（:167-190）重写为反向断言（应用行 `app-row-run` 恒不存在；如需正向覆盖，断言监控页 `app-launch` 存在即可，:240 已有）；:46/:88 注释更新。
- **测试影响**：`AppMonitorRedesignTest.kt:151-155`（点击 `app-clear-records`）不受改序影响（按 tag 定位）；`MonitorEntryHeaderTest` 不涉列表行。
- **契约同步**：AGENTS #24 改写："应用列表行 trailing = [作用域 Checkbox]（无运行按钮）；启动唯一入口 = 每应用监控页 Appbar 且**恒最右**（`[清空记录][启动]`）；无搜索页排他规则不变"。
- **验收**：列表行无任何运行入口；监控页 Appbar 启动图标位于最右；插桩（单序列定向）全绿。

### T4 记录列表双维度 + 清理并入列表（R2/V2.1-V2.6）— 复杂度 L ✅ **已完成（V40 实施备注：①路由 `records/type/{recordType}` + `globalTypeRecords()` builder + ModevNav 目的地（非法 wire 回落弹栈）；②`RecordsViewModelFactory` packageName 放宽 `String?`——null = 全局形态（presetPackages=null 哨兵直透 RecordFilter，数据层零新查询），全局形态接目录源产 `packageLabels`；③新增 `GlobalTypeRecordsScreen` 复用 MonitorRecordsContent（`showPackage` 行内应用名上下文经 RecordTimelineList→RecordRow `contextLabel` 元行注入，`clearMessageRes` 随语境切换确认文案）+ `RecordTypeLabels.kt`/`kindOptionsOf()` 共享映射取代 DataSettings 私有副本；④数据管理页重构：类型计数行可点（`data-type-{wire}`）→ 全局列表、「按应用浏览」区（AppBrowseRow：AppIcon+应用名+包名+计数→监控页）、10 个按类型 DangerRow 与 `ClearTarget.ByType` 退役、清空全部保留（`clearAll` 唯一危险动作）；⑤测试：DataSettingsScreenTest 重写为 3 用例（计数行可点回调/应用行导航/危险行退役+清空全部确认取消+存储同源）、新增 `RecordsViewModelGlobalTest` 3 用例（全局跨应用过滤+清空、单应用回归红线、packageLabels 仅全局接线，模式同 AppsViewModelTest 主 looper 推进）；⑥修复预存环境脆弱点：FourScreensNavTest 监控页入口行 `performScrollTo` 后再断言/点击（视口下方行），`awaitPage` 页面态锚点 + `clickEntryUntilOnList` 导航落地校验（修复 V39 报告记录的 2 项预存失败）；诊断页陈旧断言（服务页端口行）修正为恒展开统计分区。定向测试：records/i18n/架构单测绿、DataSettingsScreenTest 3 绿、FourScreensNavTest 5 绿连续两轮（单跑+类跑）；契约 AGENTS #17 改写为双维度。实施插曲：python 运行时损坏（exit 49）致两次补丁静默未落盘，改用 Edit 工具后解决；设备一次 offline 等 25s 自愈）**

- **改动面**：`ui/navigation/ModevRoutes.kt`、`ui/ModevNav.kt`、`ui/records/RecordsViewModel.kt`、`ui/records/MonitorRecordsScreen.kt`、`ui/settings/DataSettingsScreen.kt` + `DataSettingsViewModel.kt`、`ui/components/RecordRow.kt`、`values*strings.xml`。
- **实施要点**：
  1. **路由**：新增按类型全局路由常量与 builder（如 `RECORDS_GLOBAL = "records/type/{recordType}"` 或 10 条显式常量，与既有 22 条路由风格一致）+ `ModevNav` 目的地（VM key 如 `"records-global-{type}"`）；
  2. **VM 变体**：`RecordsViewModel` 支持 `presetPackages = null`（构造器放宽或增第二工厂 `RecordsViewModelFactory.global(recordType, container)`）；`buildFilter` 已按 null→哨兵工作（`LogRepository.resolve :550-569`）；`clearCurrentScope` 语义随预设 = 清该类型全部应用（`deleteByFilter` hasConstraint 满足）；
  3. **列表复用**：10 个全局列表页复用 `MonitorRecordsContent`（kind 过滤仅在单类型下有效——每页恰 pin 一个类型，D6 规则自然满足）；清空动作沿用搜索条 `actionSlot=ClearTypeAction`（`records-clear-type`/`records-clear-confirm`），确认文案全局语境（新增/复用字符串，区分"该应用"与"全部应用"措辞）；
  4. **数据页改造**（D-3）：类型计数行可点 → 对应全局列表（onClick 接线 :93-104）；新增「按应用浏览」区（`observeRecordCounts()` 全类型 Map，行 = 应用名 + 计数 + chevron → `appMonitor(pkg)`）；删除 10 个按类型 DangerRow 与 `ClearTarget.ByType`（:54-57,114-132），保留「清空全部」（`clear-all` + 确认 `danger_clear_all_msg`）；存储区/总记录数不动；
  5. **行内应用名**（V2.5）：`RecordRow` 增可选 `showPackage` 上下文（全局列表 true）——元行追加应用名（`AppCatalogRepository` 标签解析已有基础设施，注意列表扫描零开销红线：标签惰性/缓存，禁逐行同步 binder）；
  6. **分页契约**：新页面 `records` 流 `cachedIn(viewModelScope)`（:54-55 模式）、`RecordTimelineList` 复用（手动索引循环/占位/组头 key 原样）、计数副标消费 `records.itemCount`（:290 既有注释口径）。
- **测试影响**：
  - 单测：`RecordsViewModel` 变体（packages=null 过滤/清空语义）；`DataSettingsViewModel` 移除 `clearByType` 后的 exposed 面；
  - 插桩：数据页新入口两维度导航（计数行 → 全局列表；按应用行 → 监控页）；全局列表清空链路；`FourScreensNavTest.kt:90`（根页无全部记录入口）**保持通过**——入口在数据页非根页；
  - `RecordPagingProbeTest`/`AnchorScrollRoboTest` 回归（分页/锚点契约不破）；
  - 危险清理行退役后 `clear-type-*` testTag 全库清零（防死引用）。
- **契约同步**：AGENTS #17（记录浏览收敛）改写为"两维度：per-app×类型（监控页 EntryRow）+ 按类型全局（数据管理页计数行入口）"；#24 不涉；词汇表「记录浏览」如需补条目随 T7 统一。
- **验收**：数据页可两维度到达全部 10 类记录；每类全局列表支持搜索/kind 过滤/时间线/分页/清空；per-app 列表行为无回归；i18n parity 绿。

### T5 组件库合规整改（R8/V8.1-V8.10、V8.12）— 复杂度 XL（分 5 波，每波独立提交）

| 波 | 范围 | 实施要点 | 测试影响 |
|---|---|---|---|
| T5.1 搜索输入 MD3 化 | V8.1 | ✅ **已完成**：`CollapsedSearchInput`（手搓 Row+detectTapGestures 胶囊）退役，折叠/展开统一走标准 `SearchBarDefaults.InputField`（`SearchInputField` 单一封装，新增 `leadingSlot` 承载根页设置按钮，actionSlot/filterSlot/onBack 槽位语义不变）；D4 长按冲突处置 = 接受 MD3 标准行为（焦点驱动展开），保留 `SearchController.onInputActiveChange` 收起态守卫；两宿主页折叠态改接；`AppbarSearchExclusivityTest` 扫描标记更新为 `SearchInputField(`；`SearchInputComponentsTest` 重写 6 用例（状态驱动替身修复重组失效）；顺带修复 MonitorListScreensTest 预存失败（入口行 performScrollTo + Dialog 内输入域歧义）。测试：架构门禁单测绿 + 15/15 插桩绿（SearchInput 6 / MonitorListScreens 4 / FourScreensNav 5）；AGENTS #13 改写（D4 例外废止） |
| T5.2 徽章与状态件 | V8.2,V8.5-V8.7 | ✅ **已完成**：`StatusDot`（自研 8dp Box 圆点+Tooltip）退役 → 标准 M3 `Badge`（AppsScreen 行状态点 + AppMonitorScreen StatusPill 内部点；语义 contentDescription 保留、`app-row-status` testTag 新增）；AppsScreen 行计数胶囊（Surface CircleShape）→ M3 `Badge`（content 计数形态）；`TimelineHeader` 组件退役 → RecordTimelineList 内联 Surface+Text（`timeline-header` testTag/形态契约不变）；StatusPill 保留 Hero 卡内私有组合（Surface+Badge+Text，MD3 原语）。测试：i18n/架构单测绿；FourScreensNavTest（含状态点几何断言用例）+ MonitorListScreensTest + SearchInputComponentsTest = 15/15 插桩绿；StatusDot.kt/TimelineHeader.kt 文件删除、全库零代码引用 |
| T5.3 记录行 ListItem 化 | V8.4 | ✅ **已完成**：`RecordRow` 重写为标准 M3 `ListItem`（Surface 卡 + 手搭 Row/RecordListCard 退役；`RowPresentation` 单一数据出口、`record-row` testTag、单击/长按/时间/词元着色契约全保留；leading=LeadingIcon tonal Box、supporting=元行+预览 Column、trailing=TimeText+MicroBadge；`contextLabel` 全局语境随 T4 一次落位；68dp 最小高度经 modifier.defaultMinSize 保留）。测试：主源/测试源编译绿、i18n/架构/records 单测绿；RecordRowLayoutTest 6 绿 + DataSettingsScreenTest 3 绿 + AppMonitorRedesignTest 全绿（13 跑 1 失败 = RecordListScrollPreservationTest，V39 报告记录的预存环境集成员，v38 基线同败）；AGENTS #20 脚手架描述改写（ListItem 化） |
| T5.4 空态内联 + Coil 图标 | V8.3,V8.9 | ✅ **已完成**：`EmptyState` 组件退役 → 6 处调用点内联（AppsScreen 主树+搜索 Dialog、RecordTimelineList、DataSettingsScreen、LicensesScreen、KeywordLibraryScreen；MD3 原语 Icon+Text+Box 组合、`stateDescription` 语义与 Tokens alpha 保留；EmptyState.kt/EmptyStateTest.kt 删除）；`AppIcon` Coil 化（coil-compose 2.7.0 直依赖——Coil 3.x 拖入 JB Compose 多平台树与 AndroidX BOM 冲突故锁 2.7；`PackageIcon`+`Keyer`+`Fetcher`+进程级 ImageLoader，手搓 LruCache/drawable→Bitmap 退役；首字母占位垫底层叠 AsyncImage）；顺带清理 10 条按类型清理死字符串（T4 退役产物，lint UnusedResources）；`MonitorListScreensTest.rowCount` 修正为合并节点自身文本匹配（M3 ListItem 合并语义，AGENTS #13 同源）。测试：i18n/架构单测绿、lint 0 error；FourScreensNavTest+DataSettingsScreenTest+MonitorListScreensTest+SearchInputComponentsTest = 18/18 插桩绿（模拟器两次假死按 AGENTS 冷重启序列恢复） |
| T5.5 色板 FilterChip 化 + ListenRow 统一 | V8.8,V8.10 | ✅ **已完成**：外观色板手绘 CircleShape 网格（LazyVerticalGrid+border+Check 图标）退役 → 标准 M3 `FilterChip` FlowRow（leadingIcon 18dp 色点+outlineVariant 描边、label 自描述十六进制、selected 语义由 MD3 承担；`selectedMarkColor` 辅助函数删除）；`PerAppListenSettingsScreen.ListenRow` 私有重复实现删除，15 处调用统一到共享 `ToggleListItem`（签名同构，死导入 Switch/toggleable/clearAndSetSemantics 清理）。测试：主源/测试源编译绿、i18n/架构单测绿；PerAppListenSettingsTest 3 绿（`listen-row-*` tag 全保留）+ LanguageHotSwitchTest 2 绿（覆盖设置页外观区）插桩。实施环境插曲：模拟器三次假死/adb offline，按 AGENTS 冷重启序列（taskkill 残留进程 + dnplayer 重启）恢复 |

- **契约同步（随各波同提交）**：AGENTS #13 废止 CollapsedSearchInput 例外（保留 MD3 组件契约与对比度门禁文本）；#20 改写（T5.3）；#25 Tokens 条目与 ToggleListItem 统一表述核实；词汇表「代码组」等 D 类条目不变。
- **验收（T5 整体）**：`ui/components` 下 R 类组件文件清零（`CollapsedSearchInput/StatusDot/EmptyState/TimelineHeader/RecordRow 旧脚手架/AppIcon 旧加载`）；`rg` 扫描无残留引用；全部波次后全量单测 + lint 无新增 + 插桩抽样绿。

### T6 令牌页重设计（R7/V7.1）— 复杂度 M ✅ **已完成（V40 实施备注：McpTokensScreen 按三层信息架构重写——①认证状态卡（Card + 认证开关 + `AuthStatusLine` 状态行：M3 Badge 语义色点 + 一句话状态，认证开且零令牌 = error 色空集全拒警示，令牌页内即可理解"撤销最后一枚不停用"语义）；②令牌卡列表（每枚一 Card：名称 + mono 值/创建日 supporting + trailing 动作按危险度排序「复制→显隐→撤销」、撤销 error 色且间距分离、卡点击显隐 affordance 保留；掩码升级为前 4 后 4 摘要 `0123••••••cdef`，`maskValue` 提为 internal + JVM 单测 3 用例）；③空态内联（MD3 原语，认证态区分文案）。契约保留：令牌值不进快照/工具输出、原地生效、legacy「导入」、ModevInputSheet/ModevConfirmDialog、Appbar 单「新建」action；新增 `tokens_status_active/deny_all/off` 双语三键 + `mcp_tokens_count` plurals 复用。测试：i18n/core 单测绿、TokenMaskValueTest 3 绿、ServicesScreenTest+DiagnosticsScreenTest+LanguageHotSwitchTest 9/9 插桩绿（覆盖令牌页入口链路））**

- **改动面**：`ui/settings/McpTokensScreen.kt`（重写主体）、必要时 `values*strings.xml`、`McpTokensViewModel.kt`（如需派生流）。
- **实施要点（设计稿口径）**：
  1. **信息架构三层**：①顶部认证状态卡（M3 `Card(surfaceContainerLow)` 内 `ToggleListItem`：开关 + 一句话状态——认证开/关、令牌数、空集全拒警示）；②令牌卡列表（每枚一枚 `Card` 或 `ListItem` in Card：名称 headline、mono 掩码/明文值 + 创建日期 supporting、trailing 依危险度排序 `复制 → 显隐 → 撤销`（撤销 error 色且间距分离）；卡片整体点击 = 显隐切换保留）；③空态（内联 Icon+Text，双文案按认证态区分，沿用 `tokens_empty`/`tokens_empty_auth_on`）；
  2. **新建入口**：Appbar action 保留（`tokens-add`），不引入 FAB（页面仅两区，FAB 层级过重——大厂口径：FAB 用于页面级主操作，此处新建为次级操作）;
  3. **去重**：认证开关在本页保留（D-4 后与服务页语义一致、互不级联），副标题精简同 V5.4;
  4. **组件边界**：仅 MD3 + `Tokens` + W 类共享件（`ModevTopBar/ModevInputSheet/ModevConfirmDialog/TipIconButton`），零页面级自研组件;
  5. **交互细节**：掩码 12 圆点 → 建议前 4 后 4 摘要显式化（`MDEV••••••••a1b2` 形态，提升辨识度且不泄露全值）；复制/创建 Snackbar 沿用;撤销确认文案保留"客户端立即被拒"警示。
- **约束**：§1.7 红线全保留（值不进快照/工具输出、原地生效、空集全拒、legacy"导入"）。
- **测试影响**：令牌页插桩（若有）重写；`SettingsRepositoryMcpTokenTest` 回归（UI 不改仓库语义）；新增关键交互断言（显隐/复制哨兵降级路径——雷电剪贴板坑按 `clipboardFunctional()` 既有规约）。
- **验收**：三区层次清晰、单列滚动、无横向拥挤；全 MD3 用法（无自研件）；红线行为回归绿。

### T7 契约与文档同步 — 复杂度 S（随 T2-T5 分摊，此处汇总）✅ **已完成（V40 实施备注：逐项核对完成——AGENTS #13（D4 例外废止/标准 InputField 统一）、#17（双维度记录浏览+Factory 可空包名+清理并入列表）、#20（RecordRow ListItem 化脚手架）、#24（行内运行按钮退役+监控页启动恒最右+恢复行内按钮入禁用旧词）、#26 与词汇表「MCP 服务」（认证独立/恢复级联入禁用旧词）均已随对应任务同批改写；#25 复核 = Tokens/D 类保留口径无需改动（alpha 消费点迁移后仍全部经 Tokens）；TASK-MD3-AUDIT.md 顶部加"已被取代"归档指针（含漏判说明）；静态核查：`app-row-run/CollapsedSearchInput(/StatusDot(/TimelineHeader(/EmptyState(/clear-type-*/statsExpanded/tokenAuthEnabled || externalAccess/ListenRow(` 在 app/src/main 全部零代码引用（仅注释性提及与合法同名 TokenEmptyState））**

- `AGENTS.md`：#13（D4 例外废止）、#17（两维度记录浏览）、#20（RecordRow→ListItem 行模式）、#24（运行按钮迁移+启动恒最右）、#25（Tokens 保留口径复核）、#26（认证独立）、词汇表「MCP 服务/令牌管理/记录浏览」相应条目；
- 归档备注：`TASK-MD3-AUDIT.md` 顶部加"已被 TASK-V40-AUDIT.md 取代"指针；
- 本文档（TASK-V40-AUDIT.md）随实施推进在 §2 各任务下勾选状态。

### T8 全量门禁与设备抽测 — 复杂度 M ✅ **已完成（V40 实施备注：①全量单测 `:app:testModernDebugUnitTest` 强制重跑（--rerun-tasks 清缓存）：98 个测试类全绿、0 失败；②`lintModernRelease`：0 error / 49 warning——与基线持平（10 条按类型清理死字符串随 T4 清理抵消 Coil 新依赖与全文案改写增量）；③打包 `assembleModernRelease + assembleModernDebug` 全通过（modern 双 ABI × 双构建型）；④设备插桩抽测（单序列 am instrument，全程避开 connectedAndroidTest 卸载坑）：累计 9 轮定向验证全绿——DiagnosticsScreenTest 3、ServicesScreenTest 4、FourScreensNavTest 5×2 轮、DataSettingsScreenTest 3、MonitorListScreensTest 4、SearchInputComponentsTest 6、AppMonitorRedesignTest、PerAppListenSettingsTest 3、LanguageHotSwitchTest 2（合计 40+ 用例，覆盖全部 8 项需求面）；⑤静态核查：`app-row-run/EmptyState(/clear-type-*/statsExpanded/StatsToggleRow/ListenRow(/selectedMarkColor/MicroBadge(/tokenAuthEnabled || externalAccess` 主源零命中，`CollapsedSearchInput/StatusDot/TimelineHeader/RecordListCard/LeadingBadge` 仅 KDoc 退役说明性提及、零代码引用；⑥已知预存环境失败（V39 报告记录、v38 基线同败、与本整改无关）：CryptoDetailZones×2 / TimelineListTest×4 / RecordListScrollPreservationTest×1 / PureMarkdown×2 等 markdown 异步解析与高负载点击竞态类）。实施全程环境插曲：模拟器 3 次假死 + adb offline/daemon reset，均按 AGENTS 冷重启序列（taskkill 残留 dnplayer + dnplayer -index 0 重启 + 轮询 uptime）恢复，未丢失任何工作）**

- 命令序列（Windows）：
  1. `.\gradlew.bat :app:testModernDebugUnitTest`（全量，含 parity/架构门禁/Robolectric）；
  2. `.\gradlew.bat :app:lintModernRelease`（0 error、不新增 warning；基线 49）；
  3. `.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug`；
  4. `.\gradlew.bat :app:connectedModernDebugAndroidTest` 抽样类：`FourScreensNavTest`（含反转用例）、`ServicesScreenTest`、数据页导航新用例、令牌页用例——**注意 AGENTS 已知坑：androidTest 测后 AGP 卸载宿主 → Vector modules 行失效，抽样必须排在设备集成矩阵之后或测后立即执行恢复序列（11-vector-scope.ps1 + 整机重启）**；单序列定向 `adb -s <序列> shell am instrument`（双序列踩踏坑）。
- 静态核查脚本（实施完成时执行并入验收记录）：`rg -n "app-row-run|CollapsedSearchInput|StatusDot|TimelineHeader|EmptyState\(|clear-type-|statsExpanded|tokenAuthEnabled \|\| externalAccess" app/src` 期望仅剩历史注释或零命中。

---

## 3. 验收标准与门禁

### 3.1 每任务验收条款汇总

| 任务 | 可验证验收（节选硬条款） |
|---|---|
| T1 | 诊断页无交互可见 7 项统计；`section_collapse/section_expand` 无引用；统计卡有 testTag |
| T2 | 四态矩阵：认证×外部访问互相独立（各 2 态组合行为正确）；`setMcpExternalAccess(true)` 后认证流状态不变；`McpServer` 401 仅由 tokenAuthEnabled 触发；4 条 hint zh ≤16 字且双语占位一致 |
| T3 | 应用行无 `app-row-run`；监控页 Appbar `[清空记录][启动]` 启动最右；`AppsViewModel` 无 launchable 残留 |
| T4 | 数据页计数行 10/10 可达全局列表；「按应用浏览」可达监控页；10 个 `clear-type-*` 退役、`clear-all` 保留；全局列表清空=该类型全部应用；分页/时间线契约测试全绿 |
| T5 | R 类组件文件/引用清零；Coil 引入且 lint 无新增 warning；标准 SearchBar 行为探针通过（含长按）；`listen-row-*`/`record-row`/`timeline-header` tag 保留 |
| T6 | 令牌页三区结构、全 MD3；显隐/复制/撤销/新建全链路可用；空集全拒警示可见 |
| T7 | AGENTS 与行为一致（逐条 diff 核对）；无"契约说 A 代码做 B"残留 |
| T8 | 单测全绿；lint 0 error/无新增；assemble 4 APK 通过；插桩抽样绿（含测后恢复序列执行记录） |

### 3.2 全局门禁

- 架构门禁测试全绿：`AppbarSearchExclusivityTest`、`DialogConventionTest`、`McpToolParityTest`（43 工具冻结不破）、`InterventionGateConventionTest`/`FeatureGateBypassTest`（不涉改但回归）、`SettingsThemeContrastTest`、`StringResourceParityTest`、`HardcodedStringConventionTest`、`RecordPagingProbeTest`、`AnchorScrollRoboTest`。
- 冷启动冒烟：本整改不触注入侧钩子，**无需** `23-run-smoke.ps1`（V36 门禁触发条件=注入侧钩子变更；本次为宿主 UI/仓库/服务端判定层）；若实施中波及 `hook/**` 则触发全量冒烟门禁。
- wire/MCP 兼容：wire VERSION 恒 4 零感知（未改 `core/codec`）；MCP 协议面未改（工具面 43 不变）——`24-run-mcp.ps1` 仅在 T2 改动服务端判定后重跑认证相关子集并全绿。

---

## 4. 风险登记册

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| RK-1 | **0.0.0.0 无令牌 = 局域网无认证访问记录（PII）与危险工具**（D-4 明确接受的能力） | 高（安全语义）/ 接受 | UI 风险提示文案（V5.2 改写承载）；文档记录；危险工具仍强制 `confirm:true`；后续可选增强（不在本次范围）：绑定变更时 Snackbar 警示 |
| RK-2 | MD3 `SearchBar` 标准输入域的焦点自动展开可能与长按（选词/粘贴）冲突（历史 KDoc 记录的 D4 动因） | 中 | T5.1 先行为探针后迁移；缺陷复现则 InputField 局部禁用焦点自动展开（配置级，非自研组件）；插桩覆盖 |
| RK-3 | Coil 新直依赖 → lint `NewerVersionAvailable` 按声明逐条计数，锁旧版必 +1 warning | 低 | 取实施时点 stable 最新；跑 lint 确认；如 +1 需在验收记录中显式声明并更新基线数（禁止静默漂移） |
| RK-4 | RecordRow→ListItem 重构波及时间线分组/分页/锚点三契约（#10/#14/#15） | 中 | RowPresentation 数据出口不动、testTag/key 不变；`RecordPagingProbeTest`/`AnchorScrollRoboTest` 回归；种子 >2 页的翻页用例保留 |
| RK-5 | 应用名上下文（V2.5）触发列表扫描期 binder 查询（违反 V16 R4 零开销红线） | 中 | 标签解析走既有惰性缓存（`AppCatalogRepository` 模式），禁逐行同步查询；单测覆盖首帧零 binder |
| RK-6 | androidTest 测后 AGP 卸载宿主 → Vector 作用域失效（已知坑）污染后续集成矩阵 | 中 | 抽样排后/测后即执行恢复序列（§2 T8 第 4 步）；单序列定向执行 |
| RK-7 | 契约（AGENTS）与实现短期不一致造成后续贡献者误判 | 中 | 每任务契约同步同提交（T7 汇总清单逐条核对） |
| RK-8 | 雷电剪贴板静默丢弃（已知坑）影响令牌复制断言 | 低 | 按 `clipboardFunctional()` 哨兵降级既有规约；内容同源由 JVM 单测兜底 |

---

## 5. 追溯矩阵（需求 → 违规 → 任务 → 验收）

| 需求 | 违规条目 | 任务 | 验收锚点 |
|---|---|---|---|
| R1 | V1.1-V1.5 | T3 | §3.1 T3 行 |
| R2 | V2.1-V2.6 | T4 | §3.1 T4 行 |
| R3 | V3.1 | T1 | §3.1 T1 行 |
| R4 | （结论，保留） | T2 可选文案标注 | §1.4 + §3.1 T2 |
| R5 | V5.1-V5.4 | T2 | §3.1 T2 行 |
| R6 | V6.1-V6.6 | T2 | §3.1 T2 行 |
| R7 | V7.1 | T6 | §3.1 T6 行 |
| R8 | V8.1-V8.12 | T5（+T4 行内应用名） | §3.1 T5 行 |
| 横切 | 契约冲突（§1.9 前各节"契约同步"） | T7 | §3.1 T7 行 |
| 横切 | 门禁与恢复序列 | T8 | §3.2 |

---

## 6. 实施排期建议（相对顺序，非日历承诺）

```
T1(诊断展开, S) ──┐
T2(解耦+文案, M) ──┼─→ 可并行，先行合入
T3(运行按钮, S) ──┘
        ↓
T4(记录双维度, L) ──→ T5.3(记录行 ListItem 化) 依赖 T4 的 showPackage 设计
        ↓
T5.1 → T5.2 → T5.3 → T5.4 → T5.5（每波独立提交，波间可回归）
        ↓
T6(令牌页, M)  ← 可与 T5.4 并行（不依赖记录行）
        ↓
T7(契约汇总核对) → T8(全量门禁 + 设备抽测 + 静态核查)
```

---

*本文档由 V40 全量审计产出；所有 file:line 为 2026-09-07 工作树实位，实施时如行号漂移以标识符为准。*
