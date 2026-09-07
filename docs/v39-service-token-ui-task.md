# V39 服务/令牌/界面规范整改 — 任务文档(2026-09-06)

> 验收基线:8 项用户需求逐条对账;4 项设计决策经用户拍板;全量门禁单测 657 全绿、
> lint 回基线 49 warning/0 error、androidTest 编译通过、`assembleModernDebug` 成功。

## 一、决策记录(用户拍板)

| # | 决策 | 结论 |
|---|------|------|
| D1 | MCP 默认端口 1510 与 wire ConfigServer(注入通道,原占 1510)撞号 | **wire 迁 1511,MCP 独占 1510**(`WireProtocol.PORT=1511` / `McpProtocol.DEFAULT_PORT=1510`,双端同 APK 常量单点) |
| D2 | 令牌启用后的校验范围 | **全接口强制**(含回环 adb forward);默认停用 |
| D3 | 「管理令牌」形态 | **多令牌列表**(创建/命名/撤销/复制/显隐;不暴露为 MCP 工具,引导悖论 + PII) |
| D4 | 应用行「运行按钮放最右」 | **trailing 最右新增运行按钮**(与作用域 Checkbox 并存,不可启动应用隐藏) |

## 二、需求逐条对账(违规清单 → 修复)

### R1 MCP 端口默认 1510 + 可自定义
- 违规:`McpProtocol.DEFAULT_PORT=15110` 硬编码;文案/脚本内嵌 15110;wire 撞号。
- 修复:`WireProtocol.kt` PORT→1511、`McpProtocol.kt` DEFAULT_PORT→1510;自定义机制沿用既有 `mcp_port` 偏好 + `ModevPortSheet`(wire/MCP 共用唯一实现);`mcp_enabled_hint` 去端口/命令字面量;`tt-mcp-lib.ps1` 端口 1510 + `$script:MCP_TOKEN` Bearer 头支持;`24-run-mcp.ps1` M-SRV-01 断言改 1510 + tokenAuth=false。

### R2 MCP 监听 IP(默认 127.0.0.1 / 外部 0.0.0.0)
- 违规(质量项):字面量三处重复、无 boundAddress 可观测、无绑定失败态。
- 修复:`McpServer` 改用 `ConnectionMonitor.BIND_*` 常量;新增 `boundAddress: StateFlow<String?>` + `bindError: StateFlow<String?>`(对齐 wire ConfigServer 可观测性);`ModevApp` mcpSnapshot 消费实测值;服务页端口行展示 `地址:端口`。

### R3 令牌子系统(默认停用 / 启用派生 / 多令牌 / 管理界面)
- 违规:Token 恒生成无开关、单键无管理、装配期快照、显隐无 affordance。
- 修复(全量新建):
  - 模型 `core/model/McpTokenEntry.kt`(id/label/value/createdAt;SecureRandom 32 hex);
  - `ConfigKeys` 新增 `mcp_token_auth`/`mcp_tokens`(宿主本地,不入远程镜像;旧 `mcp_token` init 一次性迁移为条目并删键);
  - `SettingsRepository`:`mcpTokenAuth`(默认 false)/`mcpTokens` StateFlow + `setMcpTokenAuth`/`addMcpToken`/`revokeMcpToken`;**安全耦合**:开外部访问 ⇒ 自动开认证并即时派生;关认证 ⇒ 级联关外部访问(禁止 0.0.0.0 裸奔);撤销最后一枚不停用认证(空集 = 全拒,UI 提示);
  - `McpServer`:`token` 单值 → `tokenAuthEnabled` + `@Volatile requiredTokens: Set<String>`;启用后**回环同样 401**;令牌集合变更经 `mcpTokenSetUpdater` 原地生效(不重启 socket、不踢会话);
  - **管理界面** `ui/settings/McpTokensScreen.kt`(路由 `settings/mcp-tokens`):认证开关(`token-auth-toggle`)+ 令牌列表(掩码默认、行单击显隐、复制/撤销独立按钮、创建日 `formatDay`、导入条目标「导入」)+ 新建 `ModevInputSheet`(`token-add-sheet`)+ 撤销 `ModevConfirmDialog`(`token-revoke-confirm`);无搜索页 Appbar 仅 `tokens-add` 一枚(排他规范合规);
  - Parity:令牌管理**不暴露**为 MCP 工具,`McpToolParityTest.token_management_is_documented_exclusion` 门禁(工具面恒 43);`server_status` 输出补 `tokenAuth`+`tokenCount`(不含值)。

### R4 服务/诊断拆分(按功能类型分类)
- 违规:五分区混排、两个端口 sheet 95% 重复、端口值双处展示、令牌行错位诊断页、文案内嵌命令。
- 修复:
  - 新 `ui/settings/ServicesScreen.kt` + `ServicesViewModel.kt`(路由 `settings/services`):**配置面** = 通信服务(wire:外部访问开关/端口)+ MCP 服务(启用/外部访问/令牌认证/端口/令牌入口/绑定错误行);
  - `DiagnosticsScreen.kt` 瘦身为**运行时观测** = 框架与作用域 + 在线目标 + 管道统计折叠卡 + 复制报告;服务开关与令牌行整体迁出;
  - `SettingsScreen` 功能区三入口:数据管理 / 服务 / 诊断;
  - 端口编辑统一 `ModevPortSheet`(wire/MCP 共用,此前两份逐行重复收编);`settings_external_access_hint` 去端口参数;
  - `DiagnosticsReport` 快照补 MCP 两行(服务态 `地址:端口|Disabled` + 令牌认证 On/Off;令牌值恒不进快照,MCP `diagnostics` 工具同步同源)。

### R5 Appbar 含 SearchBar 禁止其它按钮
- 违规:10 个记录列表页 Appbar SearchBar + `records-clear-type` 并存;KDoc 自相矛盾;AGENTS 契约 24 固化违规形态。
- 修复:`CollapsedSearchInput`/`SearchInputField` 新增 `actionSlot`(trailing 区,清除与筛选之间);`MonitorRecordsContent` 删 Appbar actions,`records-clear-type` 移入折叠态 + 展开态搜索条(`ClearTypeAction`,同 testTag);AppsScreen 为合规样板(设置/筛选收条内);
- **门禁** `architecture/AppbarSearchExclusivityTest`:ui 内含 `CollapsedSearchInput(` 的文件禁止 `actions = {`;AGENTS 契约 24 已同步改写。

### R6 应用行运行按钮放最右
- 违规(现状反转):V7 起行内无运行按钮(trailing = 作用域 Checkbox),启动入口在监控页 Appbar。
- 修复:`AppsViewModel` 新增 `launchable: StateFlow<Map<String,Boolean>>` + `resolveLaunchable`(行组合惰性解析,复用 `AppCatalogRepository.resolveLaunchable` 缓存,V16 R4 红线「列表扫描零开销」不破);`AppRowItem` trailing = `Row { Checkbox; if (launchable) TipIconButton(PlayArrow) }`,**运行恒最右**(testTag `app-row-run`,contentDescription `app_run`);按钮消费点击不冒泡行导航;`ModevNav.launchApp` 下传;
- 测试:`FourScreensNavTest` 根页断言反转(app-launch=0 保留 + 新增 app-row-run 轮询存在)。

### R7 对话框统一 + 无图标
- 违规:7 处居中 AlertDialog 全部带 icon、全部 ~30 行手写复制;两个端口 sheet 重复;3 处输入 sheet + 4 处单选 sheet 各自手写;4 处 sheet dismiss 未走 hide 动画收口。
- 修复(统一组件 + 门禁):
  - `ui/components/ModevDialogs.kt`:`ModevConfirmDialog(title, message, onConfirm, onDismiss, confirmTextRes, destructive, confirmTestTag)` — **无 icon 参数**、`surfaceContainerHigh`、TextButton 对(破坏性 error 色)、confirmTestTag 锚点;7 处调用点全量替换(AppMonitorScreen/DataSettingsScreen/MonitorRecordsScreen/PerAppListenSettingsScreen/PerAppFeatureSettingsScreen/SpoofProfileScreen/KeywordLibraryScreen + 新令牌页);
  - `ui/components/ModevSheetScaffold.kt`:`ModevSheet`(外壳:titleLarge 标题 + `ModevSheetScope.dismissWith` **hide 动画后回调收口** + imePadding 单内边距 + surfaceContainerHigh)、`ModevInputSheet`(label 可空/hint/error/数字钳制/maxLength/minLines/inputTestTag/confirmTestTag)、`ModevPortSheet`(wire/MCP 共用)、`ModevChoiceSheet<T>`(ListItem + RadioButton 形态)、`ModevSheetButtonRow`(自定义内容 sheet 复用)、`SheetContentPadding`;
  - 收编:SettingsSheets 三件(MaxBytes/PrefixList×2/IntLimit → 薄封装,签名不变调用点零波及)、OverrideEditSheet、BlacklistAddSheet(`blacklist-input`/`blacklist-confirm` 保留)、KeywordEditSheet(`keywords-input`/`keywords-confirm`/`keywords-mode-*` 保留)、SpoofProfileSheet(`feature-profile-option-*` 保留)、SettingsScreen Language/Appearance/Color 三 sheet(修复 dismiss 直连偏差;V9.1 无闪切换语义由 dismissWith 保证)、DiagnosticsDetailSheet(`diagnostics-info-sheet` 保留);
  - **门禁** `architecture/DialogConventionTest`:ui 内 `AlertDialog(` 仅允许 ModevDialogs.kt;ModevDialogs 禁 `icon =`;`ModalBottomSheet(` 仅允许 ModevSheetScaffold.kt(`FullscreenTextDialog` 的 `Dialog(` 不在扫描面)。

### R8 返回键「再按一次退出程序」
- 违规:根页返回直接 `finish()`。
- 修复:`ui/common/DoubleBackExit.kt` 纯状态机(可注入时钟,JVM 直测;可空哨兵修「初值 0 首按即判退出」缺陷);`ModevNav` 根页 BackHandler 空闲分支:`popGuarded()` false → 窗口内二次按 `finish()` 否则 Snackbar;根页 SnackbarHost 上提至导航层(`rootSnackbarHostState` 下传 AppsScreen,与消息流共用宿主);新键 `exit_press_back_again`(zh「再按一次退出程序」);androidTest 出口 = `ExitConfirmTest` 待真机轮(见下)。

## 三、验证与门禁状态

| 门禁 | 状态 |
|------|------|
| `:app:testModernDebugUnitTest`(含新 McpTokenEntryTest / DoubleBackExitTest / SettingsRepositoryMcpTokenTest / DiagnosticsReportTest / DialogConventionTest / AppbarSearchExclusivityTest / parity 排除) | **657 全绿** |
| `:app:lintModernRelease` | **49 warning / 0 error(回基线,零新增)**;`state_on/off` 撞 androidx 私有资源已改名 `mcp_state_*`;`mcp_tokens_count` 转 plurals |
| `:app:compileModernDebugAndroidTestKotlin` + 既有测试适配(DiagnosticsScreenTest 拆分改写 / FourScreensNavTest 反转) | 通过 |
| `:app:assembleModernDebug` | 通过 |
| 设备矩阵(`23-run-smoke.ps1` / `24-run-mcp.ps1` / connectedAndroidTest 抽样) | ✅ **已完成**(2026-09-07 雷电实测):`24-run-mcp` 42/42、`23-run-smoke` 6/6、单设备 `am instrument` 全量 82 跑,V39 净新增失败 = 0(v38 基线对比证明 18 项预存在);详见 `docs/test-reports/round-20260907-v39-device.md` |

## 四、真机轮清单(✅ 全部完成 2026-09-07)

1. ✅ `24-run-mcp.ps1`:M-SRV-01a/b(1510 绑定 + tokenAuth=false)、M-PROTO-06(回环免 Token 默认态)——42/42 PASS。
2. ✅ wire 1511 端到端:`server_status.wire.connectedPackages` 含 testtarget,记录落库对账(M-REC 全绿);历史 `server_port` 自定义重放兼容由 init 逻辑保证。
3. ✅ `ServicesScreenTest` / `McpTokensScreen` 手动验收:认证开关派生令牌、撤销即时 401(不重启)、空集全拒、关认证级联关外部——全通过。
4. ✅ `ExitConfirmTest`:根页首按提示、窗口内二次返回 finish——插桩通过。
5. ✅ AGP 测后 APK 卸载恢复序列:重装 + `enabled=1` + scope + VM 重启 + 注入复验(records=25)。
6. ✅ 修复过程中发现的 2 项 V39 真实回归(ModevChoiceSheet 缺 verticalScroll、DiagnosticsDetailSheet 复制误调 onDismiss),复跑转绿。

## 五、波及文件清单

**端口/令牌核心**:`WireProtocol.kt`、`McpProtocol.kt`、`McpServer.kt`、`ModevApp.kt`、`ConfigKeys.kt`、新 `McpTokenEntry.kt`、`SettingsRepository.kt`、`McpServerLifecycleTest.kt`、`McpToolParityTest.kt`、`ServerTools.kt`。
**界面拆分**:新 `ServicesScreen.kt`/`ServicesViewModel.kt`、新 `McpTokensScreen.kt`/`McpTokensViewModel.kt`、`DiagnosticsScreen.kt`/`DiagnosticsViewModel.kt`、`SettingsScreen.kt`、`ModevRoutes.kt`、`ModevNav.kt`。
**对话框/Sheet 统一**:新 `ModevDialogs.kt`、新 `ModevSheetScaffold.kt`、`SettingsSheets.kt`(薄封装化)、7 处 AlertDialog 调用点 + 8 处 sheet 收编。
**Appbar/行按钮**:`SearchInputComponents.kt`(actionSlot)、`MonitorRecordsScreen.kt`、`AppsScreen.kt`、`AppsViewModel.kt`。
**返回键**:新 `DoubleBackExit.kt`。
**资源/脚本/文档**:`strings.xml`×2(新增 30+ 键、`mcp_enabled_hint`/`settings_external_access_hint` 去字面量)、`tt-mcp-lib.ps1`、`24-run-mcp.ps1`、`DiagnosticsReport.kt`(+JVM 单测)、本文件、AGENTS.md(契约 27/28)。

## 六、新门禁约定(AGENTS 同步摘要)

- **#27 对话框/Sheet 统一**:ui 内 `AlertDialog(` 仅 `ModevDialogs.kt`(禁 icon);`ModalBottomSheet(` 仅 `ModevSheetScaffold.kt`;危险确认居中无图标,输入弹层一律 sheet;`DialogConventionTest` 双门禁。
- **#28 Appbar 搜索排他 + 行内运行**:含 SearchBar 的 Appbar 零 actions(动作收 `actionSlot`/`filterSlot`/`leadingContent`),`AppbarSearchExclusivityTest` 门禁;应用行 trailing = [Checkbox][运行],运行恒最右、launchable 惰性解析。
