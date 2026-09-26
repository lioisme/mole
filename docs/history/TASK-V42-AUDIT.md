# TASK-V42：搜索槽长按门禁/图标底图治理/端口可用性校验/主题色回退/日韩本地化 全量审计任务书

| 项 | 值 |
|---|---|
| 版本 | V42（基于 V41 工作树，2026-09-08） |
| 性质 | 全量代码审计报告 + 分任务实施任务书 |
| 审计方法 | 3 路并行代码审计（搜索条与图标渲染 / 端口链路与主题色机制 / i18n 基础设施），根因结论经源码逐行人工复核（SearchState/ModevSheetScaffold/SettingsScreen 全文精读；file:line 均为工作树实位） |
| 决策状态 | 6 项方向性决策按审计推荐默认落定（见 §0.3，维护者可否决） |
| 交付边界 | 本文档为唯一交付物；**实施待维护者确认本文档后另行启动** |
| 关联文档 | `AGENTS.md`（现行契约）、`TASK-V41-AUDIT.md`（上一轮任务书，本文档 **D-1 部分反转其 D-3 决策**并留存证据链） |

---

## 0. 总纲

### 0.1 需求输入（维护者原始 5 项）

| # | 需求 | 类型 |
|---|------|------|
| R1 | SearchBar 中的按钮长按时不会进入 SearchView 视图 | 行为 |
| R2 | 应用列表界面不要用默认图标作为底图——有的应用图标不规则，底下的底图很丑 | 视觉 |
| R3 | 端口功能修改时，要求校验是否可用（比如是否有权限用、是否端口已经被使用等） | 行为/校验 |
| R4 | 主题色功能要求恢复原来的样式和机制，现在的太丑了 | 视觉/回退 |
| R5 | 新增支持日文和韩文 | 功能/i18n |

### 0.2 审计总结论（TL;DR）

- **5 项需求全部成立**，共 15 条条目（P1×3 / P2×12，§1 逐条列证），无 P0（零崩溃/数据丢失面）。
- **R1 根因 = V40 T5.1 迁移到标准 M3 `SearchBarDefaults.InputField` 的固有副作用**：槽按钮（`records-clear-type` 清空钮/筛选菜单/返回/根页设置钮）位于 BasicTextField decoration box 内，按钮 clickable 只消费 tap 不消费长按，长按经焦点链路触发 `onExpandedChange(true)` → `SearchController.onInputActiveChange` → `LaunchedEffect` 唯一动画驱动器展开全屏 SearchView。V41 D-3 曾将「聚焦即展开」整体定为 MD3 标准接受——本次按维护者新决策**部分反转**：仅抑制「槽按钮按压引发的展开意图」，文本区长按选词展开保持 MD3 标准不变。修复面收敛在 `SearchInputField` 单组件（槽按压追踪 + 展开意图门禁），确定性依据 = `rememberSearchController` 的 `LaunchedEffect` 是唯一动画驱动（SearchState.kt:87-95，源码红线 #3），守卫意图回调即闭环。
- **R2 根因 = `AppIcon.kt` 的 `PlaceholderIconUnderlay`（primaryContainer 圆形首字母底块）恒在组合于 AsyncImage 之下**且加载成功后不移除——不规则/透明图标透出「底图」。修复 = 底图改状态驱动（仅加载中/失败显示）；图标本体恒原样绘制。共用组件一处修，应用列表（40dp）/每应用监控 Hero（56dp）/关于页（64dp）三处同体生效。
- **R3 现状仅有 1..65535 范围校验**（UI sheet 一处 + 仓库防御性 clamp 一处）；无端口占用探测、无特权端口（<1024，Android 非特权进程必 `EACCES`）拦截、无 wire/MCP 双服务冲突检查、无输入长度上限；且 **wire ConfigServer 绑定失败零观测面**（logcat + 指数退避无限静默重试，仅 MCP 有 `bindError` → 服务页错误行），诊断页地址回退常量进一步掩蔽故障。修复四件套：domain 层 `PortValidator`（特权/冲突/探测三道闸）+ `ModevPortSheet` 升级异步校验（busy 态 + 行内错误硬阻止保存）+ ConfigServer 补 `bindError` StateFlow（对位 MCP 可观测性）+ 输入 maxLength。**ConfigServer/McpServer 绑定协议零改动**，校验全部发生在编辑链路。
- **R4 经 git 考古定位**：原版（v8 `8f5907b`，2026-08-17）= 5 列圆形色点网格 + `selectedMarkColor`（暗白/亮黑）3dp 边框 + Check 勾选；v39（`33e3ad4`）被替换为 FilterChip + hex 十六进制文本标签的 FlowRow（现状，「丑」的确切来源）。机制层（`ThemeConfig`/`ThemeSettingsManager`/壁纸跟随/HCT seed 引擎）自 v8 起**从未改变**——「恢复机制」实为「恢复选择器 UI」。修复 = 忠实复刻 v8 色板视觉（非惰性 5×4 网格，规避 sheet 内惰性布局嵌套），容器保留 ModevSheet（V39 统一契约不回退），字符串零变化。
- **R5 为标准 i18n 扩展**：`LocaleManager` 的 resolve/wrap/setLanguage 全链路 tag 无关（ja/ko 零逻辑改动），缺口集中在——语言枚举三常量、`locales_config.xml` 两行、**values-ja/values-ko 全量资源（693 strings + 8 plurals ×2）**、语言选择器 UI、`StringResourceParityTest` 双语硬编码重构为四语循环、`AppCountFormat` 万进位阈值（ja/ko 与 zh 同为 10^4 组）、`TimelineGroup.formatDay` 日韩分支。parity 门禁升级四语后完备性由测试强制。
- 全部整改**不触注入侧与 wire 协议**（VERSION 恒 4 零感知），MCP 工具面 43 不变；冷启动冒烟门禁不触发（零 `hook/**` 改动）。

### 0.3 已定决策（按审计推荐默认落定，维护者可否决）

| 决策 | 内容 |
|---|---|
| D-1 | **长按抑制范围 = 仅槽按钮引发的展开意图**：`SearchInputField` 槽容器（leading/trailing 全部按钮区）加按压追踪（观测 down→up/cancel，不消费事件、不改变按钮自身行为含 tooltip），`onExpandedChange` 门禁在「槽按压进行中 ∪ 释放后 400ms 宽限期」内拒绝 `true` 展开意图，收起意图恒放行。文本区长按选词→聚焦展开**保持 MD3 标准**（该行为是长按选词的固有前提）。V41 D-3 相应修订为「文本区聚焦展开 = 标准；槽按钮长按不展开 = 定制门禁」，`SearchInputComponentsTest.tap_expandsViaFocusIntent` 注释同步。已否替代：`TipIconButton` 改 `combinedClickable` 消费长按——与 TooltipBox 长按手势语义耦合、全局共用组件波及面大、且无法阻断 M3 内部焦点路径，否决。 |
| D-2 | **AppIcon 底图状态驱动**：`PlaceholderIconUnderlay` 仅在加载中/失败态组合（AsyncImage `onState` 驱动，Success 即移除），图标本体恒原样绘制——**不加裁切、不加形状约束**（尊重不规则图标的原生形态，正是本需求诉求）；三处共用点（AppsScreen/AppMonitorScreen/AboutScreen）随组件同体生效，零调用方改动。 |
| D-3 | **端口校验失败 = 硬阻止保存**：确认 → busy → IO 线程校验 → 通过 `dismissWith(onConfirm)` / 失败显示对应行内错误并保持 sheet 打开（不可带病写入）。校验序：范围（沿用）→ **特权端口 <1024 = 硬错误**（Android 应用进程绑定 <1024 必 `EACCES`，无合法放行场景）→ **双服务冲突 = 硬错误**（wire 端口 ≠ MCP 端口）→ **占用探测 = 硬错误**（`ServerSocket` 试绑，`reuseAddress=true` 与服务自绑语义一致；**自身当前端口豁免探测**——重存原值属合法 no-op；探测地址随统一外部访问开关 `0.0.0.0`/`127.0.0.1`）。探测为即时快照，最终裁决 = 真实绑定结果（bindError 行兜底，见 RK-2）。 |
| D-4 | **wire ConfigServer 补齐绑定失败观测面**（对位 MCP 既有形态）：新增 `bindError: StateFlow<String?>`（绑定成功清空、失败置位），服务页 wire 分区新增绑定错误行（标题/值双新键，形态与 MCP 错误行逐位对称）；ConfigServer 静默指数退避重试机制**保留**（观测面只增不改）；诊断复制快照 `DiagnosticsReport` **不变**（MCP 两行既有口径不动，避免快照文本测试连锁漂移）。 |
| D-5 | **主题色板忠实复刻 v8 视觉**：5 列圆形色点（`aspectRatio(1f)` + `CircleShape`）+ 选中标记 = `selectedMarkColor`（暗色恒白/亮色恒黑）3dp 边框 + 居中 20dp Check 图标；20 色固定数量 → **非惰性 5×4 布局**（`chunked(5)` × Row），规避 ModevSheet 纯 Column 容器内嵌套惰性布局的约束冲突；壁纸行/分隔线/标题行沿用现状（与 v8 逐位一致）；**ModevSheet 容器与 V39 对话框契约不回退**（raw `ModalBottomSheet` 不恢复）；`ThemeSeeds.OPTIONS`/`ThemeConfig`/`ThemeSettingsManager`/`Theme.kt` 机制层零改动。 |
| D-6 | **四语 parity 全量强制**：values-ja/values-ko 一次到位（693 strings + 8 plurals 全量，无渐进降级）；`StringResourceParityTest` 重构为四 locale 循环（缺键/占位符形态/与 en 同文三项逐 locale 断言）并**扩展 plurals 校验**（既有测试不查 plurals，防 ja/ko 缺失静默回落 en）；技术标识词（kind/operation/`IDENTICAL_ALLOWED` 面）保持英文原样，`lang_ja`=「日本語」/`lang_ko`=「한국어」自名键入 `IDENTICAL_ALLOWED`；ja/ko plurals 按 CLDR 仅 `other` 量项；翻译基线 = zh 语义 + en 结构，核心产品词（记录/监听/功能域等）在 T5 内先行建立 ja/ko 术语表保证全库一致。 |

### 0.4 全局不变量（实施全程不得破坏）

1. **i18n parity（随 T5 升级四语）**：新增/改写字符串 en/zh/ja/ko 同步、占位符形态一致（`StringResourceParityTest`）；退役键必须四语同步删除（防 lint UnusedResources 漂移）；ui 包禁中文字面量（`HardcodedStringConventionTest`，随 T5 可选扩展 Kana/Hangul）；色值 hex/`0.0.0.0` 等技术字面量不在禁用面。
2. **搜索状态机红线**（SearchState.kt 头注 5 条全守）：`rememberSearchBarState` 无参、`searchActive` 独立意图、`LaunchedEffect` 唯一动画驱动、收起后导航同协程、snapTo 兜底——T1 门禁只拦截「展开意图」，不新增动画驱动路径。
3. **Appbar 排他 + MD3 组件契约**（AGENTS #24/#13）：记录列表页 actions 恒空不动；槽按钮仍为 M3 `IconButton`+TooltipBox 形态，仅外层门禁。
4. **对话框/Sheet 契约**（AGENTS #27）：`ModevPortSheet` 升级后仍系 `ModevSheet` 直出（hide 动画收口、imePadding 单内边距、surfaceContainerHigh）；`ModevInputSheet` 零改动（MaxBytes/IgnoreStack/IntLimit 等 5 消费方零波及）；`AlertDialog(` 只允许 ModevDialogs.kt 的扫描面不变。
5. **P5-1 包边界**：ui 禁 import `data.server`——端口校验逻辑置 `domain/server/PortValidator`（UI→domain 为 B2 允许上游）；`ServicesViewModel` 边界注释（:24）语义保持。
6. **MCP 红线**（AGENTS #26）：工具面 43 冻结（`McpToolParityTest`）；`mcp.*` 层零改动（MCP 端口校验在 UI/VM/domain 层）；令牌值恒不进输出。
7. **wire 协议零感知**：`ConfigServer` 仅加观测 StateFlow，协议/连接/重试/epoch 机制不动，VERSION 恒 4。
8. **主题机制零变化**：`ThemeConfig`/`ThemeSettingsManager`/`Theme.kt`（resolveColorScheme/generateScheme/ModevTheme）不动；`SettingsThemeContrastTest` 全绿不回退。
9. **双域词汇表纪律**：新增术语（端口校验链/色板形态/四语）入词汇表；退役形态（FilterChip hex 色板、恒显图标底图）按需入禁用旧词。
10. **lint 门禁**：`lintModernRelease` 0 error、不新增 warning（基线 49）；新增键 ×4 locale 与退役 import（FilterChip/FlowRow）随删抵消增量。
11. **注入侧零触碰**：本次全部为宿主 UI/仓库/domain 层，`hook/**` 不动——不触发 `23-run-smoke.ps1`；若实施中意外波及注入侧则升级全量门禁。

---

## 1. 违规清单（全量，file:line 实证）

严重度：P1 = 阻塞验收的正确性/门禁缺陷；P2 = 应修复的质量/视觉/体验缺陷。

### 1.1 R1 搜索条按钮长按展开 SearchView（2 条）

**机制触发链（V1.1，经组件源码 + M3 1.4.0 字节码双向核实）**：槽按钮位于 `SearchBarDefaults.InputField` 的 `trailingIcon`/`leadingIcon` 槽（`SearchInputComponents.kt:57-96`）——即 BasicTextField decoration box 内部。按钮的 `IconButton` clickable 仅消费 tap-up；**长按不被消费**，指针事件沿 decoration box 祖先链继续到达 BasicTextField 的焦点/选词手势逻辑 → TextField 获焦 → M3 `onFocusChanged`（isFocused 且折叠态）回调 `onExpandedChange(true)` → `SearchController.onInputActiveChange`（`SearchState.kt:66-69`，现有守卫仅滤「动画期间重复意图」，无法区分长按来源）→ `searchActive=true` → `LaunchedEffect`（:87-95）`animateToExpanded()` → 全屏 SearchView。同时 `TipIconButton` 的 TooltipBox 长按显示 tooltip（设计行为，保留）。点击（tap）不触发——clickable 消费 up 后焦点链路不启动，与用户观察「仅长按」吻合。

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V1.1 | P2 | 搜索条槽按钮（`records-clear-type` 清空钮/筛选菜单/返回/根页设置钮）长按误触全屏 SearchView 展开；且展开与 tooltip 同帧叠加（长按本意 = 看 tooltip） | `ui/components/SearchInputComponents.kt:57-96`（InputField 装配，槽无任何长按防护）；`ui/components/SearchState.kt:66-69`（守卫仅防动画期重复）；`ui/components/TipIconButton.kt:25-57`（TooltipBox 长按 tooltip）；消费点 = `MonitorRecordsScreen.kt:362-401/:436-464`（折叠/展开两态 + `ClearTypeAction` :501-514）、`AppsScreen.kt:149-194/:296-333`（leadingSlot 设置钮） | T1：`SearchInputField` 槽容器按压追踪（`pointerInput` + `awaitEachGesture`，仅观测不消费）+ `onExpandedChange` 门禁（D-1）；收起意图恒放行；展开态槽不受影响 |
| V1.2 | P2 | 测试/契约缺口：`SearchInputComponentsTest.tap_expandsViaFocusIntent`（:121-133）将「聚焦展开」锁定为标准契约并明文记录「长按选词同样聚焦展开 = 标准形态」——**无槽按钮长按的负向断言**；`AppbarSearchExclusivityTest`（:33-46）仅扫 Appbar actions 排他；V41 D-3 决策与本次需求冲突需显式反转 | `app/src/androidTest/.../ui/components/SearchInputComponentsTest.kt:121-155`；`app/src/test/.../architecture/AppbarSearchExclusivityTest.kt:33-46`；`TASK-V41-AUDIT.md` D-3 | T1：新增 4 条行为探针（action/filter/leading 槽长按不展开 + 文本区 tap 仍展开回归 + 按钮功能保持）；探针注释与 AGENTS #24 契约同步（D-1 反转记录） |

### 1.2 R2 应用图标底图（2 条）

**根因触发链（V2.1）**：`AppIcon` 对**每个**图标恒组合 `PlaceholderIconUnderlay`（`AppIcon.kt:42-43`）——primaryContainer 圆形 + 包名首字母（:57-71），注释自述「加载成功后被 AsyncImage 覆盖」，但 AsyncImage（:44-52，fetcher :85-94 直接回 `DrawableResult(drawable)`）**不裁切、不挖空**：透明/不规则图标无法覆盖底层色块，字母圆底透出 = 「丑底图」。加载完成后底图也永不移除（无状态跟踪）。

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V2.1 | P2 | 图标占位底图恒在组合且加载成功后不移除 → 不规则/透明图标透出 primaryContainer 字母圆底；底图色（`primaryContainer`）也未走 Tokens（对照 emptyIcon* token 纪律） | `ui/components/AppIcon.kt:42-53`（Box: underlay 恒组合 + AsyncImage 叠加）、`:57-71`（PlaceholderIconUnderlay）、`:85-94`（Fetcher 无形状处理）；共用点 `AppsScreen.kt:550`（40dp 主诉界面）、`AppMonitorScreen.kt:408`（56dp Hero）、`AboutScreen.kt:66`（64dp） | T2：底图改 `onState` 状态驱动（Loading/Error 显示、Success 移除，D-2）；图标本体恒原样绘制；底图 testTag `app-icon-placeholder` 供测试锚定 |
| V2.2 | P2 | `AppIcon`/占位逻辑零测试（test/androidTest 全库无 `AppIcon|Placeholder` 命中），渲染行为无回归防线 | 测试目录 grep 零命中 | T2：新增 Robolectric 渲染测试（fake `ImageLoader` 注入参数，默认生产 loader 零波及）：失败态底图在、成功态底图移除两断言 |

### 1.3 R3 端口可用性校验（4 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V3.1 | P2 | 端口编辑全链路仅有范围校验：sheet 层 `validate = { it.toIntOrNull() in 1..65535 }`（同步布尔，无原因分型）+ 仓库层防御性 `coerceIn(1, 65535)`——「是否可用」零校验 | `ui/components/ModevSheetScaffold.kt:188-208`（ModevPortSheet，validate :203 经 ModevInputSheet :167-178 同步通道）；`data/repo/SettingsRepository.kt:196/:348`（双 clamp） | T3：`domain/server/PortValidator`（校验序见 D-3）+ sheet 异步化（V3.2 同体） |
| V3.2 | P1 | 三道可用性闸门全部缺失：①特权端口 <1024 可录入（Android 非特权进程绑定必 `EACCES`，wire/MCP 均应用进程 → 必然失败却静默通过）；②wire/MCP 双服务端口可设为相同（无冲突检查，后保存者把先保存者顶下线）；③端口被外部进程占用无探测（MCP 侧仅事后 `bindError`，wire 侧连事后都没有） | 全主源无 `isPortAvailable`/占用探测逻辑（`ServerSocket` 仅 ConfigServer 自绑一处）；冲突面：`ServicesScreen.kt:174-197` 两 sheet → `ServicesViewModel.setWirePort/:52-54`、`setMcpPort/:79-81` 直写 | T3：PortValidator 特权/冲突/探测三闸（D-3：硬阻止保存）；`ModevPortSheet` 升级异步校验（确认 → busy → IO 校验 → 通过 `dismissWith(onConfirm)` / 失败行内错误保持打开；`ModevInputSheet` 零改动）；输入 `maxLength = 5`（ModevPortSheet 现未传，:196-207） |
| V3.3 | P1 | wire ConfigServer 绑定失败**零观测面**：bind 失败仅 `Log.w` + 指数退避（2s→30s）无限静默重试，无 bindError StateFlow、服务页无错误行；诊断 VM 地址回退 `?: BIND_LOOPBACK` 常量进一步掩蔽（未绑定也显示 127.0.0.1）——对照 MCP 侧 `bindError` StateFlow → 服务页错误行完整形态，同一语义两服务不对称 | `data/server/ConfigServer.kt:124-161`（acceptLoop；bind 失败 catch :146-156 仅 logcat）、`:54`（boundAddress 无 error 伴随）；`ui/settings/ServicesScreen.kt:161-168`（仅 MCP 错误行）；`ui/settings/DiagnosticsViewModel.kt:45-48`（回退掩蔽）；MCP 对照 `mcp/McpServer.kt:57-59/:66-93` | T3（D-4）：ConfigServer 新增 `bindError: StateFlow<String?>`（成功清空/失败置位，对位 McpServer 形态）→ `ModevApp` 装配接线 → `ServicesViewModel` 出口 → 服务页 wire 分区错误行（双新键 `wire_bind_error_title`/`wire_bind_error`）；退避重试机制保留；诊断快照/回退口径不动（决策显式排除面） |
| V3.4 | P2 | 边界细节：错误文案单一（`settings_port_invalid` 仅范围语义），无法承载特权/占用/冲突分型；无 maxLength 用户可输入任意位数字 | `values/strings.xml:421-422`（en "Port must be 1–65535"）；`ModevSheetScaffold.kt:196-207`（maxLength 未传） | T3：新键 `settings_port_privileged`/`settings_port_in_use`/`settings_port_conflict` 双语；maxLength=5 |

### 1.4 R4 主题色板形态（1 条）

**git 考古链**：v8 `8f5907b`（2026-08-17）引入 `ThemeConfig`/`ThemeSettingsManager`/`ThemeSeeds` + ColorSheet——色板为 **5 列 `LazyVerticalGrid` 圆形色点**：`aspectRatio(1f).clip(CircleShape).background(color)`，选中 = `selectedMarkColor(color, isDark)`（暗恒白/亮恒黑，注释「任意 seed 下清晰可见」）3dp border + 居中 20dp Check；v30–v37 色板未动；**v39 `33e3ad4`**（`git log -S` 实证：删除 `LazyVerticalGrid`/`GridCells`，新增 `FilterChip`）替换为 FilterChip + `#RRGGBB` hex 文本标签 FlowRow（现状 `SettingsScreen.kt:437-465`）。机制层（壁纸跟随/HCT seed/持久化）v8 起未变——**「机制」无需恢复，「样式」需回退**。20 个 hex 标签 chip 多行换行 = 视觉噪源（维护者「太丑」的直接对应物）。

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V4.1 | P2 | 色板选择器 v39 形态（FilterChip+hex 标签 FlowRow，20 项多行换行）劣化于 v8 原版（5 列圆形色点网格 + 对比安全选中标记）；v8 实现可经 `git show 8f5907b:app/src/main/java/com/lioisme/modev/ui/settings/SettingsScreen.kt`（ColorSheet 区段）完整取回 | 现状 `ui/settings/SettingsScreen.kt:398-468`（FlowRow FilterChips :437-465，hex 标签 :448）；原版 v8 blob ColorSheet 区段（LazyVerticalGrid + selectedMarkColor helper）；字符串五键（theme_color/follow_wallpaper/custom_accent/system_wallpaper/pick_accent）v8 引入后从未增删，零需变更 | T4（D-5）：色板区复刻 v8 视觉（非惰性 5×4 网格 + selectedMarkColor 边框勾选），退役 FilterChip/FlowRow/hex 标签；ModevSheet 容器保留；机制层与字符串零变化；`SettingsThemeContrastTest`（scheme 级）不受影响 |

### 1.5 R5 日韩本地化（6 条）

| # | 严重度 | 违规 | 证据 | 整改方向 |
|---|---|---|---|---|
| V5.1 | P2 | 支持语言枚举硬编码三常量（system/en/zh），无 ja/ko 标签；下游选择器 when 分支、`LanguageSheet` 单选行同源缺失（ja/ko 无法选择） | `i18n/LocaleManager.kt:46-48`（TAG_SYSTEM/TAG_EN/TAG_ZH）；`ui/settings/SettingsScreen.kt:118-124`（副标题 when）、`:285-317`（LanguageSheet 3 行 IconChoiceRow） | T5：`TAG_JA`/`TAG_KO` 双常量 + 副标题分支 + 2 行选择项（resolveTag/wrap/setLanguage 全链路 tag 无关，零逻辑改动——已核实 :80-83/:144-154/:121-132） |
| V5.2 | P2 | `locales_config.xml` 仅 en/zh 两项（Android 13+ 系统级 per-app 语言面板不出现日/韩） | `res/xml/locales_config.xml:6-7`；`AndroidManifest.xml:18`（localeConfig 已挂，零改动） | T5：+`<locale android:name="ja"/>`+`<locale android:name="ko"/>`，文件头「与 LocaleManager 同步」注释纪律保持 |
| V5.3 | P1 | **values-ja/values-ko 全量缺失**（R5 主体）：693 strings + 8 plurals（`listen_settings_custom_n`/`params_omitted`/`stack_more`/`detail_truncated_marker`/`record_data_rows_short`/`record_data_affected_short`/`rows_more`/`mcp_tokens_count`）×2 语言无资源，ja/ko 运行时静默回落 en | `values/`（693+8）/`values-zh/`（693+8）逐键清点；res 下唯一 locale 变体目录 = values-zh（另有 values-night/v31 色彩主题，非翻译面） | T5：新建两资源目录全量翻译（D-6 纪律：技术词保持英文、核心产品词先建术语表、plurals 仅 `other` 量项） |
| V5.4 | P2 | `StringResourceParityTest` 结构性双语（zh context 硬编码 :113-115；缺键/占位符/同文三断言只对 zh）——四语后既不拦截 ja/ko 缺键也不拦回落；**plurals 完全不在校验面**（ja/ko 缺 plurals 静默回落 en 无人知） | `app/src/test/.../i18n/StringResourceParityTest.kt:27`（占位符 regex）、`:33-107`（IDENTICAL_ALLOWED ~70 项）、`:109-144` | T5：重构四 locale 循环（en 默认 + zh/ja/ko 各 createConfigurationContext）+ plurals 逐 locale 标识符断言（新增）+ `IDENTICAL_ALLOWED` 增补 `lang_ja`/`lang_ko` |
| V5.5 | P2 | `AppCountFormat.compactThreshold` 仅 zh 走 10^4 万进位，ja/ko 落 10^3 分支——与 ICU 日韩原生进位组（万/만 = 10^4）错位，紧凑计数显示将提前进位（如 5000 → 「5千」而非日语习惯「5,000」） | `ui/apps/AppCountFormat.kt:22`（`locale.language.startsWith("zh")`）；ICU `CompactDecimalFormat` ja/ko 原生支持（:30-41 引擎本身零改动）；消费点 5 处经 `rememberEffectiveLocale` 全部自动获益 | T5：阈值谓词改 `locale.language in setOf("zh","ja","ko")`；`AppCountFormatTest` 增 ja/ko 紧凑/阈值用例（`12345→"1.2万"/"1.2만"`） |
| V5.6 | P2 | `TimelineGroup.formatDay` 仅 zh 有本日分支（`M月d日`），ja/ko 落 `MMM d`——Java `SimpleDateFormat` 日/韩 `MMM` 输出「9月 8」/「9월 8」形态（尾字截断，非母语惯形「9月8日」/「9월 8일」） | `ui/common/TimelineGroup.kt:46-53`（`locale.language == "zh"` 三元）；`secondKey/formatSecond` locale 无关（:21-39，零改动） | T5：formatDay 增 ja（`M\u6708d\u65e5`，与 zh 同形）/ko（`M\uC6D4 d\uC77C`，escape 形态过 `HardcodedStringConventionTest` Han 扫描）分支；JVM 直测三语输出 |

### 1.6 违规统计

| 需求 | 条目数 | P1 | P2 |
|---|---|---|---|
| R1 搜索槽长按 | 2 | — | 2 |
| R2 图标底图 | 2 | — | 2 |
| R3 端口校验 | 4 | 2 | 2 |
| R4 主题色板 | 1 | — | 1 |
| R5 日韩本地化 | 6 | 1 | 5 |
| **合计** | **15** | **3** | **12** |

---

## 2. 任务分解（T1→T6）

> 每任务含：目标 / 改动面 / 实施要点 / 测试影响 / 契约同步 / 验收标准。**每任务一个独立提交，契约同步随行为变更同提交落地**。T1/T2/T4 相互独立可任意序；T3、T5 独立大体量任务；T6 收尾终核。

### T1 搜索槽长按门禁（R1/V1.1-V1.2）— 复杂度 M ✅ **已完成（V42 实施备注：①`SearchInputField` 新增槽按压窗口——`slotPressWindow` pointerInput 修饰符挂 leadingIcon Box/trailingIcon Row 两容器（`awaitFirstDown(requireUnconsumed=false)` 仅观测不消费，公开 API 逐事件等待替代 foundation internal `awaitAllPointersUp`，try/finally 防取消路径残留）；②`gatedOnActiveChange` 门禁：按压中 ∪ 释放后 400ms 宽限（`SLOT_EXPAND_GRACE_MS`，吸收 M3 延迟焦点回调）内拒绝展开意图，收起恒放行——状态机红线零触碰（不新增动画驱动路径）；③按钮 click/tooltip 行为零改变（TipIconButton 形态未动）；④SearchInputComponentsTest 新增 4 探针：action/filter/leading 三槽长按零展开 + 文本区单击仍展开回归锚，`tap_expandsViaFocusIntent` 注释契约反转声明。编译验证：主源 + androidTest 编译通过，AppbarSearchExclusivityTest 绿；探针设备运行随 T6 插桩窗口执行）**

- **改动面**：`ui/components/SearchInputComponents.kt`（唯一行为变更点）；`app/src/androidTest/.../ui/components/SearchInputComponentsTest.kt`（新增探针）；`SearchInputComponents.kt` 头注契约行；`AGENTS.md` #24。
- **实施要点**：
  1. **槽按压追踪**（私有修饰符，仅观测不消费——按钮点击/长按 tooltip 行为零改变）：
     `private fun Modifier.slotPressTracker(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); onChange(true); awaitAllPointersUp(); onChange(false) } }`——挂于 `leadingIcon` 内容容器与 `trailingIcon` Row（:64-94）两处；`clear` TipIconButton/`actionSlot`/`filterSlot` 全部覆盖（三槽同源）。
  2. **展开意图门禁**：`SearchInputField` 内持 `slotPressed: Boolean` 与 `slotReleasedAt: Long`（`SystemClock.uptimeMillis()`），包装 `onExpandedChange`——`value == true && (slotPressed || uptimeMillis() - slotReleasedAt < 400L)` 时静默拒绝，`value == false` 恒放行（收起路径零触碰，状态机红线 #2/#3 不破坏）。**400ms 宽限期吸收 M3 延迟焦点回调**（SearchState.kt:60-64 实证延迟 ~100ms 形态存在）。
  3. 展开态（全屏 SearchView）同组件复用，门禁在已展开态无副作用（意图本就不会重复生效）。
  4. **探针先行（TDD）**：先写 red 探针再实现，防「门禁假闭环」（RK-4）。
- **测试影响**（`SearchInputComponentsTest` 新增，组件级探针、测试自备带 testTag 槽 composable）：
  - `longPress_onActionSlot_doesNotExpand` / `longPress_onFilterSlot_doesNotExpand` / `longPress_onLeadingSlot_doesNotExpand`：`performTouchInput { longClick() }` → 展开计数 == 0；
  - `tap_onQueryText_stillExpands`：文本区 tap → 展开计数 ≥1（V1.2 契约反转的回归锚——文本区标准行为不回退）；
  - `click_onActionSlot_stillFires`：按钮 onClick 计数保持（门禁不误伤点击）；
  - 既有 `tap_expandsViaFocusIntent`（:121-133）注释同步（D-1 反转声明）；其余 slot 语义用例（:77-118）回归。
- **契约同步**：`SearchInputComponents.kt` 头注（:18-25）追加点：「V42：槽按钮长按不展开（槽按压门禁 + 400ms 宽限）；文本区长按选词聚焦展开保持 MD3 标准」；AGENTS #24 追加同句；`TASK-V41-AUDIT.md` D-3 由本文档 D-1 显式取代（不在 V41 文档改笔，避免历史任务书失真）。
- **验收**：真机/插桩——长按 `records-clear-type`/筛选/返回/设置钮：仅 tooltip 显示、不进全屏搜索；长按文本区仍展开（选词场景）；按钮点击功能回归全绿。

### T2 AppIcon 底图状态驱动（R2/V2.1-V2.2）— 复杂度 S ✅ **已完成（V42 实施备注：①占位改 `onState` 状态驱动——`showPlaceholder` 按 `remember(packageName)` 重置（Lazy 行复用回加载态），`state !is Success` 即隐藏（Loading/Error/Empty 保持首字母兜底）；②底图挂 testTag `app-icon-placeholder`；③`ImageLoader` 参数化注入点（默认 null = 生产共享 loader，三处调用方零改动）；④图标本体恒原样绘制不裁切；⑤新增 `AppIconPlaceholderTest`（Robolectric @Config 34）双用例：成功态移除 / 失败态保持（fake ImageLoader 注入 + 两用例异包名规避 MemoryCache 跨用例命中 + 无 waitUntil 库下轮询封装）。定向测试：AppIconPlaceholderTest 2/2 绿）**

- **改动面**：`ui/components/AppIcon.kt`（单文件）；新增 `app/src/test/java/com/lioisme/modev/ui/components/AppIconPlaceholderTest.kt`（Robolectric）。
- **实施要点**：
  1. `AppIcon` 增 `var showPlaceholder by remember(packageName) { mutableStateOf(true) }`；`Box` 内底图改 `if (showPlaceholder) PlaceholderIconUnderlay(...)`（:42-43 处收敛）；
  2. `AsyncImage` 挂 `onState = { state -> showPlaceholder = state !is AsyncImagePainter.State.Success }`——Loading/Error/Empty 保持底图（失败不裸黑），Success 移除（D-2）；
  3. `PlaceholderIconUnderlay` 挂 `Modifier.testTag("app-icon-placeholder")`；AsyncImage 内容 **不加 clip/shape**（尊重不规则图标原生形态）；
  4. `ImageLoader` 参数化：`loader: ImageLoader = LocalContext.current.rememberAppIconLoader()` 既有逻辑抽默认值（@VisibleForTesting/internal），测试注入 fake（error 态 / success 态两路）。
- **测试影响**：新增 Robolectric 渲染测试两用例（失败态 → `app-icon-placeholder` 存在；success Drawable → 不存在）；`AppsScreen`/`MonitorListScreensTest` 等既有插桩回归（AppIcon 行为面零 API 变化，调用方 :550/:408/:66 零改动）。
- **契约同步**：AGENTS #25（视觉 token 契约）追加一行：「AppIcon 底图 = 加载中/失败态限定（`app-icon-placeholder`），成功即移除；图标本体禁裁切」。
- **验收**：应用列表不规则图标（用户主诉样例）无底色透出；加载瞬间/失败仍有首字母兜底；三处共用点视觉一致。

### T3 端口可用性校验 + wire bindError 观测（R3/V3.1-V3.4）— 复杂度 L ✅ **已完成（V42 实施备注：①新增 `domain/server/PortValidator.kt`（纯 Kotlin 零 Android 依赖，P5-1 边界内）——校验序 = 范围 → 特权 <1024（PRIVILEGED_PORT_MAX=1023）→ 双服务冲突（Conflict(otherLabel) 分型）→ 自身当前端口豁免 → `ServerSocket` 试绑探测（`reuseAddress=true`，Dispatchers.IO，地址随统一外部访问开关）；②`ModevPortSheet` 重写——`ModevSheet` 直出（ModevInputSheet 零改动，5 消费方零波及），maxLength=5 + digitOnly、范围本地同步预拒（零 IO）、确认 → busy（`port-checking` 标记 + 双钮禁用）→ `validatePort` 注入回调 → Available 走 `dismissWith(onConfirm)` / 其余 `PortCheck` 持状态组合侧映射分型文案（`errorLabel` @Composable——修复初版 stringResource 非组合上下文编译错）；③ServicesViewModel `validateWirePort/validateMcpPort` 组装（self/other 互为冲突面，`settings.*.value` 现值直读）+ `wireBindError` 出口；④ConfigServer `_bindError` StateFlow（bind 成功清空 :137、失败置位 catch，退避重试机制零改动）→ ModevApp 工厂接线 → 服务页 wire 分区错误行（Warning 图标 + `wire_bind_error_title/wire_bind_error`，与 MCP 行形态对称）；⑤新键 6 条双语（privileged/in_use/conflict/checking + wire_bind 两键）。测试：新增 PortValidatorTest 7 用例（四分支 + 占位/空闲/自身豁免/0.0.0.0 路径）全绿；ConfigServer 三测试类回归绿；DialogConventionTest/PackageBoundaryTest 绿）**

- **改动面**：新增 `domain/server/PortValidator.kt`；`ui/components/ModevSheetScaffold.kt`（ModevPortSheet 重写，ModevInputSheet 零改动）；`ui/settings/ServicesScreen.kt`/`ServicesViewModel.kt`；`data/server/ConfigServer.kt`；`ModevApp.kt`（装配接线）；`values/strings.xml`/`values-zh/strings.xml`；新增 `app/src/test/java/com/lioisme/modev/domain/server/PortValidatorTest.kt`；`ServicesScreenTest.kt` 扩展。
- **实施要点**：
  1. **PortValidator**（domain 层，纯 JVM 可测，UI 禁 import data.server 边界内）：
     `sealed interface PortCheck { OutOfRange; Privileged; Conflict(otherLabel: String); InUse; Available }`；
     `suspend fun check(candidate: Int, selfPort: Int, selfLabel: String, otherPort: Int?, otherLabel: String, externalAccess: Boolean): PortCheck`——校验序 = 范围（防御，sheet 已滤）→ `candidate < 1024 → Privileged` → `candidate == otherPort → Conflict` → `candidate == selfPort → Available`（**自身豁免**：重存原值 no-op，不探测）→ 探测：`withContext(Dispatchers.IO)` 内 `ServerSocket().apply { reuseAddress = true }` 试绑 `InetSocketAddress(if (externalAccess) "0.0.0.0" else "127.0.0.1", candidate)`（**reuseAddress=true 与 ConfigServer 自绑 :128 语义一致**，TIME_WAIT 不假阳性、活跃监听必 EADDRINUSE）→ `BindException/SocketException → InUse`，成功即关 → Available。
  2. **ModevPortSheet 重写**（仍 `ModevSheet` 直出，AGENTS #27 契约面不变；`ModevInputSheet` 不动）：签名增 `validatePort: suspend (Int) -> PortCheck`（ServicesViewModel 供参）；内部：数字键盘 + `maxLength = 5` + digitOnly；确认点击 → `busy = true`（确认钮禁用）→ 校验 → `Available → sheet.dismissWith { onConfirm(port) }` / 其余 → busy=false + `isError` + supportingText 分型（`settings_port_privileged`/`settings_port_in_use`/`settings_port_conflict`/沿用 `settings_port_invalid`）；范围预判本地同步拒（busy 前置省 IO）。
  3. **ServicesViewModel**：新增 `validateWirePort(port)`/`validateMcpPort(port)`——读既有流现值（`serverPort`/`mcpPort`/`externalAccess`）组装 PortValidator 入参（self=本服务当前端口、other=对端端口、otherLabel=对端名）；两 sheet 调用点（`ServicesScreen.kt:174-197`）改传 validatePort。
  4. **ConfigServer bindError**（D-4）：新增 `private val _bindError = MutableStateFlow<String?>(null)` + 公开 `bindError: StateFlow<String?>`；bind 成功（:141 后）置 null，失败 catch（:146-156）置 `t.message ?: class simpleName`（**退避重试机制原样保留**，error 流仅观测）；`ModevApp` 装配接线 → `ServicesViewModel` 出口 `wireBindError` → `ServicesScreen` wire 分区错误行（`SettingsInfoItem` + Warning 图标 + `wire_bind_error_title`/`wire_bind_error(%1$s)`，形态与 MCP 行 :161-168 逐位对称，置于 wire 端口行下）。
  5. 服务端绑定协议/epoch/重启链零改动；`SettingsRepository.setServerPort/setMcpPort` 的 clamp 保留（防御纵深）。
- **测试影响**：
  - 新增 `PortValidatorTest`（JVM）：特权/冲突/自身豁免/范围四分支纯断言 + 探测两用例（本地起真实 `ServerSocket` 占位 → InUse；随机空闲端口 → Available——JVM 直跑零 Android 依赖）；
  - `ServicesScreenTest`（插桩）：wire 错误行渲染断言（bindError 置位/清空两态）；port sheet 行为用例（非法端口 → 错误文案 + sheet 不关 + 偏好未写；合法空闲端口 → 正常保存）；
  - `ConfigServerTest`/`ConfigServerLifecycleTest` 回归（bindError 新流不破坏既有矩阵）；`SettingsRepositoryTest` 回归。
- **契约同步**：AGENTS 新增强约束 **#29 端口校验链**（PortValidator 校验序/自身豁免/探测地址随统一外部访问开关/硬阻止语义/`ModevPortSheet` 异步形态）；#19 追加一行「端口探测地址消费统一外部访问开关」；#27 补「ModevPortSheet = 自带异步校验的 ModevSheet 直出形态」；词汇表新增「端口校验 Port Validation」条目。
- **验收**：录入 80 → 特权错误行内提示、不保存；录入对端服务端口 → 冲突错误；录入被占用端口 → 占用错误；录入合法空闲端口 → 保存且服务重启成功；重存自身当前端口 → 直接放行；人为制造 bind 失败（另一进程占位后强写）→ 服务页出现 wire/MCP 各自错误行；parity/lint 绿。

### T4 主题色板回退 v8 视觉（R4/V4.1）— 复杂度 S ✅ **已完成（V42 实施备注：①ColorSheet 色板区回退 v8 视觉——非惰性 `chunked(5)`×Row 5×4 网格（尾行 Spacer weight 占位保等宽节奏）、CircleShape 色点 + `selectedMarkColor(LocalIsDarkTheme)`（暗白/亮黑）3dp 边框 + 20dp Check、未选中 1dp `surfaceVariant`@`Tokens.swatchBorderAlpha`(0.3f 新增 token)；②退役 FilterChip/FlowRow/hex 标签及 import（ExperimentalLayoutApi 随删）；③机制层（ThemeConfig/ThemeSeeds/壁纸/seed 引擎）与字符串五键零变化；④ModevSheet 容器保留。定向测试：SettingsThemeContrastTest + HardcodedStringConventionTest + DialogConventionTest 全绿，compileModernDebugKotlin 通过；LanguageHotSwitchTest 插桩回归随 T6 设备窗口执行）**

- **改动面**：`ui/settings/SettingsScreen.kt`（ColorSheet 色板区 :435-465 重写）；退役 import（FilterChip/FlowRow/aspectRatio 视最终形态）。
- **实施要点**：
  1. 色板区替换为**非惰性 5×4 网格**（20 色 `ThemeSeeds.OPTIONS` 固定数量，`chunked(5)` × Row + `Modifier.weight(1f).aspectRatio(1f)`）——规避 ModevSheet 纯 Column（:84-93 无 scroll 容器）内嵌套惰性布局约束冲突，v8 的 LazyVerticalGrid 形态不做机械搬运（D-5）；
  2. 复刻 v8 色点视觉（自 `git show 8f5907b:app/.../SettingsScreen.kt` 取回）：`clip(CircleShape).background(color)` + 选中 `border(3.dp, markColor, CircleShape)` + 居中 `Check` 20dp + `semantics { selected }`；未选中 `border(1.dp, surfaceVariant.copy(alpha=0.3f))`；
  3. 复活 `selectedMarkColor(color, isDark): Color = if (isDark) White else Black` helper（暗恒白/亮恒黑，任意 seed 对比安全）；isDark 来源 = 现行主题推导（`AppearanceMode`+`isSystemInDarkTheme`，或 theme 包既有 composition local，实施时取现存者）；
  4. 壁纸行（:413-418）/分隔线/`pick_accent` 标题行/「选择即写不关 sheet」语义全部保持（与 v8 逐位一致）；
  5. 退役：FilterChip/FlowRow/hex 标签及其 import（全文件唯一消费点核实后删）。
- **测试影响**：`SettingsThemeContrastTest`（scheme 级 20 seed×亮暗）回归即验收——选择器 UI 无既有测试锁定（V41 审计同结论）；`LanguageHotSwitchTest`（经通用区渲染路径）回归；可选：Robolectric 渲染断言 20 色点 selected 语义（低优先，不强求）。
- **契约同步**：AGENTS 词汇表新增「主题色板」条目（v8 圆形色点网格形态为唯一形态；FilterChip hex 色板入禁用旧词）；#25 无涉（无新 token）。
- **验收**：色板为 4 行 5 列圆点、选中白/黑边框+勾、选择即生效不关弹层；hex 标签零残留；机制层（壁纸/seed/持久化）行为与改前逐位一致。

### T5 日韩本地化（R5/V5.1-V5.6）— 复杂度 L ✅ **已完成（V42 实施备注：①values-ja/values-ko 全量落地——经生成器 `scripts/v42_generate.mjs` + 三张翻译表 `scripts/v42_table_1..3.mjs` 产出（en 骨架逐行替换，保留注释顺序；637 键翻译 + 64 技术同文键回落 en + confirm/xiaomi13 显式 IDENTICAL_OK），701 strings + 8 plurals（other-only），`scripts/i18n_analyze.mjs check` 独立校验（键完备/占位符/同文/other 量项）零失败——生成管线可复现、禁手改顺序；②LocaleManager `TAG_JA/TAG_KO` + `SUPPORTED_TAGS` 唯一枚举；③locales_config.xml +ja/ko；④设置页副标题分支 ×2 + LanguageSheet 两行；⑤lang_ja/lang_ko 自名键（en/zh 同文）；⑥AppCountFormat 阈值 CJK 三语 10^4（CJK_10_4_LANGUAGES 集合）；⑦TimelineGroup.formatDay ja/ko 母语惯形分支（escape 形态）；⑧StringResourceParityTest 重构四语循环（缺键/占位符[regex 升级支持 %1$,d]/同文三断言 ×zh/ja/ko）+ 新增 allPluralsPresentInAllLocales；白名单增补 lang_ja/lang_ko/confirm/feature_profile_xiaomi13；⑨LanguageHotSwitchTest 新增 ja→ko 热切换用例（零重建断言 + 自名选项 + 资源驱动文案）。定向测试：i18n 包全绿（parity 四语 + plurals + LocaleManager）、AppCountFormatTest ja/ko 绿、TimelineGroupTest ja/ko 绿、HardcodedStringConventionTest 绿；androidTest 编译通过（设备运行随 T6 窗口）**

- **改动面**：新增 `values-ja/strings.xml`、`values-ko/strings.xml`（各 693 strings + 8 plurals）；`i18n/LocaleManager.kt`；`res/xml/locales_config.xml`；`ui/settings/SettingsScreen.kt`（副标题分支 + LanguageSheet）；`values/strings.xml`/`values-zh/strings.xml`（lang_ja/lang_ko 两键）；`ui/apps/AppCountFormat.kt`；`ui/common/TimelineGroup.kt`；`StringResourceParityTest.kt`（重构）；`AppCountFormatTest.kt`/`LocaleManagerTest.kt`（扩展）；`TimelineGroupTest`（如有 JVM 直测则扩展）；`LanguageHotSwitchTest.kt`（androidTest 扩展）。
- **实施要点**：
  1. **术语表先行**（写入本任务提交说明，保证 693 键全库一致）：核心产品词 ja/ko 定档——例：记录 record＝ja「記録」/ko「기록」；监听域 Listen Domain＝ja「リッスン」/ko「리슨」；功能域 Feature Domain＝ja「機能」/ko「기능」；作用域 scoped＝ja「スコープ済」/ko「스코프됨」；运行中 Running＝ja「実行中」/ko「실행 중」；实施时按此表外推，禁止同词多译；
  2. **values-ja/values-ko 全量**：以 zh 语义 + en 结构为基线翻译；技术标识词（kind/operation/`IDENTICAL_ALLOWED` 面 ~70 键：`MESSAGE_DIGEST` 等枚举字面、section 技术名、`app_name` 等）**保持英文原样**；8 个 plurals 各仅 `<item quantity="other">`（ja/ko CLDR 规则）；占位符形态与 en 逐位一致（`%1$s`/`%1$d`/`%1$,d` 禁重排）；`lang_ja`=「日本語」/`lang_ko`=「한국어」自名键（en/zh 两文件同步添加，全 locale 同文）；
  3. **LocaleManager**：+`const val TAG_JA = "ja"`/`TAG_KO = "ko"`（:46-48 区）；其余零改动（resolveTag/wrap 缓存/setLanguage 已 tag 无关，已逐行核实）；
  4. **locales_config.xml**：+ja/ko 两 `<locale>` 项，文件头同步注释更新；
  5. **SettingsScreen**：副标题 when 增 `TAG_JA → stringResource(R.string.lang_ja)`/`TAG_KO → …` 分支（:118-124）；`LanguageSheet` 增两 `IconChoiceRow`（:309-314 后，图标沿用 `Icons.Default.Language`，dismissWith 收口同既有三行）；
  6. **AppCountFormat**：`compactThreshold` 谓词改 `locale.language in setOf("zh","ja","ko")`（:22）；引擎/消费点零改动；
  7. **TimelineGroup.formatDay**：模式选择改 when——`"zh", "ja" → "M\u6708d\u65e5"`、`"ko" → "M\uC6D4 d\uC77C"`、else `"MMM d"`（:46-53；escape 形态过 HardcodedStringConventionTest Han 扫描——ja 的 月/日 为 Han 字符必须 escape，ko 월/일 为 Hangul 本不在扫描面、统一 escape 求一致）；
  8. **StringResourceParityTest 重构**：locale 集 = en（默认资源）+ zh/ja/ko（各 `createConfigurationContext`）；三断言逐 locale（非零标识符 / 占位符集与 en 相等 / 非 `IDENTICAL_ALLOWED` 键文本 ≠ en）；**新增 plurals 校验**（8 个 plurals 名逐 locale `getIdentifier(name,"plurals",…)` 非零）；`IDENTICAL_ALLOWED` 增 `lang_ja`/`lang_ko`；可选加固：占位符 regex 扩展支持 `%1$,d` 形态（现 regex 漏逗号，V41 T1 崩溃类缺陷的静态防线补强）；
  9. **可选加固**：`HardcodedStringConventionTest` CJK 扫描面扩展 Kana（\u3040-\u30ff）+ Hangul（\uac00-\ud7af）——ja/ko 文本只应存在于资源。
- **测试影响**：
  - `StringResourceParityTest` 重构后四语全量强制（本任务核心门禁）；
  - `AppCountFormatTest` +ja/ko 用例（`compact(12345, Locale.JAPANESE)=="1.2万"`、`Locale.KOREAN=="1.2만"`、阈值断言 9999/10000 边界）；`LocaleManagerTest` +ja/ko resolveTag/持久化用例；TimelineGroup 相关 JVM 直测 +ja/ko 输出断言；
  - `LanguageHotSwitchTest`（插桩）+ja→ko 切换用例：断言经 `R.string.settings_title` 资源解析（避免字面量硬编码）且零 Activity 重建（沿用 :49-91 生命周期计数法）；`DebugHotSwitchTest` 零改动。
- **契约同步**：AGENTS **#1 全文升级**（i18n = en/zh/ja/ko 四语齐备；plurals ja/ko 仅 other）；词汇表新增「语言 Language」条目（五选项 system/en/zh/ja/ko；自名键纪律）；版本头 V42 行（T6 汇总时统一落）。
- **验收**：语言面板五项、切换 ja/ko 全屏文本即时刷新零重建；`locales_config` 系统面板出现日/韩；四语 parity 全绿；计数紧凑格式与日期分组头在 ja/ko 下母语惯形；`AppCountFormatTest`/`LanguageHotSwitchTest` 绿。

### T6 文档卫生与全量门禁 — 复杂度 M（收尾汇总）✅ **已完成（V42 实施备注：①AGENTS 增量沉淀——版本头追加 V42 行；#1 全文升级四语（SUPPORTED_TAGS 唯一枚举/plurals other-only/生成器同源白名单）；#24 追加槽长按门禁；#25 追加 AppIcon 底图规则 + swatchBorderAlpha token；#27 补 ModevPortSheet 异步形态；#19 补探测地址一行；新增 #29 端口校验链；词汇表新增「端口校验」「语言」两条目。②全量单测 `:app:testModernDebugUnitTest`：**103 个测试类 / 687 用例 / 0 失败 0 错误**（首跑 RecordSinkTest seq 轮询用例 1 例超时抖动——hook 包零触碰、单独重跑 5/5 绿、全量重跑全绿，确认为环境性轮询窗口抖动非代码缺陷）。③`lintModernRelease`：**0 error / 49 warning，与基线持平零新增**。④`assembleModernRelease + assembleModernDebug` 双变体打包通过。⑤静态核查：SettingsScreen 零 FilterChip/FlowRow/hex 标签残留（仅注释退役声明）；TAG_JA/TAG_KO/PortValidator/bindError/app-icon-placeholder/slotPressWindow 新面全部就位。⑥24-run-mcp.ps1 核对：脚本无 `mcp_port`/prefs 键耦合（grep 零命中），MCP 工具面/协议零改动，无需重跑。⑦**插桩抽样未执行——三查 adb（默认端口 + LDPlayer 5038）均无设备在线**；待设备窗口补跑清单：SearchInputComponentsTest（长按门禁 4 探针 + 既有 6 用例）、ServicesScreenTest（端口校验 + wire 错误行）、LanguageHotSwitchTest（ja/ko 热切换 + 既有 2 用例）、MonitorListScreensTest/AppIcon 回归、FourScreensNavTest；执行须按 AGENTS 已知坑序列（force-stop 前置 + 单序列定向 + 测后恢复 11-vector-scope.ps1 + 整机重启）**

- **命令序列（Windows）**：
  1. `.\gradlew.bat :app:testModernDebugUnitTest`（全量，含 PortValidatorTest/AppIconPlaceholderTest/四语 parity/AppCountFormat ja/ko/既有架构门禁）；
  2. `.\gradlew.bat :app:lintModernRelease`（0 error、基线 49 warning 不增——新增键 ×4 locale 与退役 import/键随删抵消）；
  3. `.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug`；
  4. androidTest 抽样（单序列 `adb -s` 定向、`am instrument` 前 force-stop、测后恢复序列 11-vector-scope.ps1 + 整机重启——AGENTS 已知坑）：`SearchInputComponentsTest`（长按门禁探针）、`ServicesScreenTest`（端口校验 + wire 错误行）、`LanguageHotSwitchTest`（ja/ko 切换）、`MonitorListScreensTest`/`AppsScreen` 相关（AppIcon 回归）、`FourScreensNavTest`（导航回归）。
- **静态核查**（完成时执行并入验收记录）：
  `rg -n "FilterChip|FlowRow|PlaceholderIconUnderlay|settings_port_invalid" app/src/main/java/com/lioisme/modev/ui/settings/SettingsScreen.kt app/src/main/java/com/lioisme/modev/ui/components/AppIcon.kt` ——期望：SettingsScreen 零 FilterChip/FlowRow 残留；AppIcon 的 PlaceholderIconUnderlay 仅存于条件组合分支；`rg -n "TAG_JA|TAG_KO|values-ja|values-ko|PortValidator|bindError"` 核对新面完整。
- **AGENTS.md 终核**（各任务契约同步汇总）：版本头 V42 增量行；#1（四语）、#24（槽长按门禁）、#25（AppIcon 底图规则）、#27（ModevPortSheet 异步形态）、#19（探测地址一行）、新 #29（端口校验链）、词汇表三新增条目（端口校验/主题色板/语言）+ 禁用旧词追加（FilterChip hex 色板/恒显图标底图）。
- **24-run-mcp.ps1**：MCP 工具面/协议/鉴权零改动（端口校验在编辑链路），无需重跑；核对脚本是否直写 `mcp_port` prefs（设备侧矩阵），如命中需确认经校验链口径。

---

## 3. 验收标准与门禁

### 3.1 每任务验收条款汇总

| 任务 | 可验证验收（节选硬条款） |
|---|---|
| T1 | 槽按钮长按 = 仅 tooltip、零展开（三槽探针锁定）；文本区 tap/长按选词展开回归；按钮点击功能回归；搜索状态机 5 条红线不动 |
| T2 | 不规则图标无底色透出；加载中/失败仍有首字母兜底；成功态 `app-icon-placeholder` 零存在（Robolectric 断言）；三共用点零调用方改动 |
| T3 | 特权/冲突/占用三闸硬阻止（行内错误 + sheet 不关 + 偏好未写）；自身当前端口直接放行；`PortValidatorTest` 全绿；ConfigServer `bindError` 双态渲染于服务页；`ModevInputSheet` 零改动（既有 5 消费方回归） |
| T4 | 5×4 圆形色点 + selectedMarkColor 边框勾选；hex 标签/FilterChip 零残留；机制层行为逐位不变（SettingsThemeContrastTest 回归） |
| T5 | 四语 parity 全绿（含 plurals 校验）；语言面板五项 + ja/ko 热切换零重建；`AppCountFormat` ja/ko 万进位；`formatDay` ja/ko 母语惯形；技术词全库英文原样 |
| T6 | 单测全绿；lint 0 error/无新增 warning；assemble 4 APK 通过；插桩抽样绿（含测后恢复记录）；静态核查零残留；AGENTS 终核逐项 |

### 3.2 全局门禁

- 架构门禁测试全绿：`StringResourceParityTest`（四语重构后）、`HardcodedStringConventionTest`、`RecordDaoAccessGateTest`、`AppbarSearchExclusivityTest`、`DialogConventionTest`（ModevPortSheet 重写后仍合规）、`McpToolParityTest`（43 冻结）、`InterventionGateConventionTest`/`FeatureGateBypassTest`（零涉改，回归）、`SettingsThemeContrastTest`、`RecordPagingProbeTest`、`AnchorScrollRoboTest`、`SearchInputComponentsTest`（新探针）。
- 冷启动冒烟：本整改**零 `hook/**` 触碰**，无需 `23-run-smoke.ps1`；若实施波及注入侧则触发全量门禁。
- wire/MCP 兼容：wire VERSION 恒 4（`core/codec` 未改）；MCP 协议面/工具面未改——`24-run-mcp.ps1` 仅在核对出 prefs 键耦合时重跑相关子集。

---

## 4. 风险登记册

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| RK-1 | 端口探测假阳性/假阴性：TIME_WAIT 窗口、双服务自身监听、reuseaddr 语义差异 | 中 | 探测 `reuseAddress=true` 与 ConfigServer 自绑（:128）语义一致；自身当前端口豁免探测；校验序前置冲突检查分流双服务情形；探测定位为即时快照非终审 |
| RK-2 | 探测通过后、真实绑定前端口被抢（TOCTOU） | 低 | 最终裁决 = 真实绑定：bindError 观测面（T3 第 4 件套）兜底，失败在服务页可见、用户重试即可 |
| RK-3 | 长按门禁 400ms 宽限期误伤「长按按钮后极快点击文本区」的展开 | 低 | 极端时序窗口小且可重复点击；探针锁定文本区直接 tap/长按不受影响；宽限值实施期可调（探针断言与常量同源） |
| RK-4 | M3 内部展开路径若非纯 `onExpandedChange` 驱动（字节码分析或有出入）→ 守卫假闭环 | 中 | TDD 纪律：探针先行 red → 实现 → green；若守卫不闭环，升级方案 = `SearchController` 增设显式 expandIntent 门禁参数（LaunchedEffect 唯一动画驱动保证可拦截） |
| RK-5 | 693×2 翻译的完备性/一致性（同词多译、术语漂移） | 中高 | D-6 纪律：术语表先行落定、技术词白名单保持英文、四语 parity 门禁强制（缺键/占位符/同文三断言）、核心屏插桩抽查 |
| RK-6 | ja/ko plurals 误加 one 量项或漏 plurals 键（静默回落 en） | 低 | CLDR 仅 other 纪律写入实施要点；parity 测试新增 plurals 逐 locale 标识符断言（V5.4 同体闭合） |
| RK-7 | lint 基线漂移（新键 ×4 locale、退役 import、新 API 引入弃用告警） | 中 | 退役键/import 随删抵消；新代码禁弃用 API（AGENTS #5）；T8 lint 门禁硬核对 |
| RK-8 | 大体量 XML（693 键 ×2）引入编码/转义事故（PS 管道毁文件已知坑；`&quot;`/`'` 转义形态） | 中 | 一律 edit 工具/UTF-8 字节级读写，禁 PowerShell 管道改写（AGENTS 工具链坑条目）；提交前 parity + 编译双验证 |
| RK-9 | androidTest 测后 AGP 卸载宿主 → Vector 作用域失效（已知坑） | 中 | 抽样排后/测后即恢复序列（重装 + 11-vector-scope.ps1 + 整机重启）；单序列定向执行 |
| RK-10 | `locales_config.xml` 与 LocaleManager 枚举失步（历史纪律靠注释） | 低 | 文件头同步注释更新；可选：parity 测试侧扩展读取 xml 断言与 TAG 常量集一致（不强求） |
| RK-11 | 契约（AGENTS）与实现短期不一致误导后续贡献者 | 中 | 每任务契约同步同提交；T6 终核清单逐条核对 |

---

## 5. 追溯矩阵（需求 → 违规 → 任务 → 验收）

| 需求 | 违规条目 | 任务 | 验收锚点 |
|---|---|---|---|
| R1 搜索槽长按 | V1.1-V1.2 | T1 | §3.1 T1 行 |
| R2 图标底图 | V2.1-V2.2 | T2 | §3.1 T2 行 |
| R3 端口校验 | V3.1-V3.4 | T3 | §3.1 T3 行 |
| R4 主题色回退 | V4.1 | T4 | §3.1 T4 行 |
| R5 日韩本地化 | V5.1-V5.6 | T5 | §3.1 T5 行 |
| 横切 | 契约同步（各节「契约同步」） | T6 终核 | §3.1 T6 行 |
| 横切 | 门禁与恢复序列 | T6 | §3.2 + §4 RK-7/RK-8/RK-9 |

---

## 6. 实施排期建议（相对顺序，非日历承诺）

```
T4(主题色板回退, S) ──┐ 零依赖,先行快赢(每任务独立提交)
T2(AppIcon 底图, S) ──┘
        ↓
T1(搜索槽长按门禁, M) ──→ TDD 探针先行(RK-4 缓解),契约反转记录随提交
        ↓
T3(端口校验 + bindError, L) ──→ domain 层新增 + sheet 重写 + 观测面,面最大
        ↓
T5(日韩本地化, L) ──→ 术语表先行 → 资源全量 → 门禁重构 → 格式化收口(可与其他任务并行推进)
        ↓
T6(AGENTS 终核 + 全量门禁 + 静态核查 + 插桩抽样)
```

---

*本文档由 V42 全量审计产出；所有 file:line 为 2026-09-08 工作树实位，实施时如行号漂移以标识符为准。审计证据链：3 路并行探查（搜索条与图标渲染/端口链路与主题色机制/i18n 基础设施）+ 关键结论人工逐行复核（SearchState、SearchInputComponents、ModevSheetScaffold、SettingsScreen 全文精读）+ git 考古（主题色板 v8→v39 演变，`8f5907b`/`33e3ad4` 双提交实证）。*

---

## 7. V42 实施状态总览（2026-09-08）

| 任务 | 状态 | 验证 |
|---|---|---|
| T1 搜索槽长按门禁 | ✅ 已完成 | 主源 + androidTest 编译绿；AppbarSearchExclusivityTest 绿；4 探针设备运行待窗口 |
| T2 AppIcon 底图状态驱动 | ✅ 已完成 | AppIconPlaceholderTest 2/2 绿 |
| T3 端口可用性校验 + bindError | ✅ 已完成 | PortValidatorTest 7/7 绿；ConfigServer 回归绿；门禁绿 |
| T4 主题色板回退 v8 | ✅ 已完成 | SettingsThemeContrastTest + 架构门禁绿 |
| T5 日韩本地化 | ✅ 已完成 | 四语 parity + plurals 门禁绿；i18n/AppCount/Timeline 定向绿；ja→ko 热切换设备运行待窗口 |
| T6 全量门禁 | ✅ 已完成（插桩待设备） | 单测 103 类/687 例全绿；lint 0 err/49 warn 持平；assemble 双变体绿；静态核查零残留；**插桩抽样因设备离线未执行**（补跑清单见 T6 备注） |

**遗留事项**：设备插桩抽样（T6 备注 ⑦ 清单）待 LDPlayer 窗口恢复后执行，其余验收条款全部达成。
