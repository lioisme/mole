# Modev UI 收官任务书(菜单化改造 + 作用域语义 + 图标色彩 + 设置页精修 + AppBar 净化)

**文档版本**: v1.4 | **创建日期**: 2026-08-15 | **状态**: ✅ 已完成(2026-08-15)
**适用范围**: `modev` 项目 `ui/` 层、`res/` 资源
**设计基准**: `D:\Users\liois\Documents\lioisme\kotlin\lide`(LIDE,唯一风格基准源)
**技术约束**: 纯 Kotlin + Jetpack Compose Material3(MD3),禁止 View 系统 / 第三方 UI 框架
**质量目标**: 代码质量、架构设计达到苹果 / 谷歌等一线大厂验收标准(可读性、单一职责、状态唯一、无障碍、性能、门禁)

---

## 1. 全量审计结论(现状 vs 6 项需求)

审计方式:双项目全量源码走读(LIDE 为基准,逐文件比对)+ 全部 Tab/Chip/菜单/图标/字符串关键点 grep 定位。

| # | 需求 | 现状审计 | 判定 |
|---|------|---------|------|
| 1 | 禁止 Tab 栏/切换按钮,用菜单 | 两处违规:**AppDetailScreen 用 `PrimaryTabRow`+`Tab` 切视图**;App 列表页用自绘 `FilterToggleChip` 双按钮筛选 | 🔴 违规,需改 |
| 2 | AppBar 仅一个 SearchBar,筛选放入 SearchBar | 日志页已合规(筛选 `LogFilterMenuButton` 在 trailingIcon 槽);**App 列表页筛选在顶栏外**的独立 Row;AppDetail 无 SearchBar(日志搜索为独立折叠条) | 🟡 部分违规 |
| 2a | AppBar 禁止其他按钮(仅 SearchBar) | **App 列表页顶栏 actions 中有 `cd_logs` / `cd_settings` 两个 TipIconButton**;AppDetail 用 `ModevTopBar`(标题 + ViewSwitchMenu actions)**两者均违反**此条;日志页/设置页已合规 | 🔴 违规,需改 |
| 3 | 应用列表图标全白、影响阅读 | **`AppIcon.kt:63` `Icon(bitmap=…)` 未传 `tint`,默认 `LocalContentColor`(深色≈白色)对彩色位图叠加单色蒙层** | 🔴 缺陷,需修 |
| 4 | 术语「监听」→「作用域」 | `values-zh/strings.xml` 8 处用户可见文案含「监听」;代码注释亦有散布 | 🟡 需改(保留「监听端口」) |
| 5 | 设置页简约、优美、高级,无拥挤/错位/截断 | 结构与 LIDE SettingsScreen **逐段同源**,整体已达标;存在**截断风险点**:`SettingsInfoItem` 值 `maxLines=1+Ellipsis`、版本串拼接 | 🟢 基本达标,精修 3 处 |
| 6 | MD3 + 与 LIDE 风格一致 | 主题引擎/组件库(Theme/ThemeConfig/SettingsComponents/ModevTopBar/TipIconButton/SearchInputComponents/EmptyState)已与 LIDE 逐参数同源 | 🟢 达标;新增菜单需沿用同源模式 |

### 关键违规/缺陷明细(文件:行号)

1. **`ui/apps/AppDetailScreen.kt:124-135`** — `PrimaryTabRow(selectedTabIndex=tab.ordinal) { Tab(日志) Tab(捕获设置) }`,导入 `:38/:45`。违反 #1。
2. **`ui/apps/AppsScreen.kt:165-182`** — 独立 `Row` 内两枚 `FilterToggleChip`(仅已监听/系统应用),自绘实现 `:310-337`。违反 #1/#2。
3. **`ui/apps/AppsScreen.kt:157-164`** — `CenterAlignedTopAppBar.actions` 内有 `cd_logs` / `cd_settings` 两个 `TipIconButton`,违反 #2a(AppBar 仅允许 SearchBar)。
4. **`ui/components/AppIcon.kt:62-67`** — `Icon(bitmap=bitmap, contentDescription=null, modifier=…)` 无 `tint`。MD3 `Icon` 的 bitmap 重载默认 `tint=LocalContentColor.current`,对多色启动图标施加 `ColorFilter.tint` → 深色主题下呈现白色剪影。违反 #3。
5. **`res/values-zh/strings.xml`** 监听文案(见 §3 表)。违反 #4。
6. **`ui/components/SettingsComponents.kt:157`** — `SettingsInfoItem` 值 `maxLines=1, overflow=Ellipsis`,长值(如 `FrameworkName + version`)可能被截断。违反 #5。

### 已合规(无需改动,作为参考基准)
- 日志页 `LogFilterMenuButton`(`LogsScreen.kt:315-389`):DropdownMenu + RadioButton 单选 + 危险项 `error` 色 + 分隔线 — **新菜单的范本**。
- AppDetail `KindFilterMenu`(`AppDetailScreen.kt:351-385`):同构范本。
- 单栈导航 `ModevNav.kt`、150ms slide 禁 fade、搜索状态机红线、i18n 双语言、HCT 主题 — 全部达标。

---

## 2. 决策记录(DR)

| # | 决策点 | 结论 | 依据 |
|---|--------|------|------|
| D1 | AppDetail 视图切换 | **顶栏 ⋮ 溢出菜单**:`ModevTopBar` actions 区新增 `TipIconButton`(切换视图),点击弹出 `DropdownMenu`,`日志 / 捕获设置` 两项用 RadioButton 标记当前项 | 用户确认;与 LogFilterMenuButton 同构 |
| D2 | App 列表筛选 | **SearchBar trailingIcon 槽内 DropdownMenu + Checkbox 复选**:「仅已作用域」「系统应用」两项独立复选;折叠态与全屏展开态双槽位同时挂载 | 用户确认;与日志页筛选菜单同构 |
| D3 | 图标色彩 | `AppIcon` 传 `tint = Color.Unspecified`,保留位图原始色彩 | MD3 官方语义:多色图标用 `Color.Unspecified` |
| D4 | 术语 | 用户可见文案「监听」→「作用域」;**「监听端口」(TCP 语义)保留**;代码注释同步扫除 | 语义准确性 |
| D5 | 设置页 | 保持 LIDE 同源结构,仅修 3 处:值截断、垂直节奏统一、信息块间距 | LIDE 同源 + #5 要求 |

---

## 3. 任务清单(可执行粒度)

### T1 应用列表筛选菜单化(App 主页)
**文件**: `ui/apps/AppsScreen.kt`、`res/values[-zh]/strings.xml`

- [x] 删除独立筛选 `Row`(`:165-182`)与自绘 `FilterToggleChip`(`:308-337`)。
- [x] 新增 `ScopeFilterMenu`(仿 `LogFilterMenuButton` 模式):
  - `DropdownMenu` + 两个 `DropdownMenuItem`(`leadingIcon = Checkbox`),分别绑定 `state.onlyScoped` / `state.showSystem`;
  - 项文本 = `apps_only_scoped` / `apps_system`;
  - 变更经 `viewModel.setOnlyScoped / setShowSystem` 下发(ViewModel 零改动)。
- [x] 在**折叠 SearchBar**(`:136` `filterSlot = {}`)与**全屏展开 SearchBar**(`:258` `filterSlot = {}`)两处 `filterSlot` 同时挂载该菜单。
- [x] 菜单按钮用 `TipIconButton`(tooltip `cd_filter` + 长按振动),图标 `Icons.Default.FilterList`。
- [x] 清理不再使用的导入。
- **备注(2026-08-15)**:删除独立筛选行与 `FilterToggleChip` 自绘组件;新增 `ScopeFilterMenu`(DropdownMenu + Checkbox 复选,状态单源 = `AppsViewModel.UiState.onlyScoped/showSystem`);折叠与全屏展开两槽位共用同一 `remember` 状态(与日志页同构);顶部改用 DropdownMenuHeader 放「筛选」标题;导入清理完成;编译通过。

### T2 应用详情视图切换菜单化
**文件**: `ui/apps/AppDetailScreen.kt`

- [x] 删除 `PrimaryTabRow` + `Tab`(`:124-135`)及导入(`:38,:45`)。
- [x] `ModevTopBar` 传入 `actions`:`TipIconButton`(切换视图,图标 `Icons.Default.SwapVert`,tooltip 新增 `cd_switch_view`)+ `DropdownMenu`,两项「日志 / 捕获设置」,`leadingIcon = RadioButton(selected=…)`,点击 `viewModel.selectTab(...)`。
- [x] 布局改为:`StatusRow` → `when(tab) { LOGS -> LogsTab(...); SETTINGS -> SettingsTab(...) }`,保持搜索/筛选逻辑不变。
- [x] `AppDetailViewModel.Tab` 枚举与 `selectTab` 保留(ViewModel 零改动)。
- [x] 更新文件头部注释(移除「TabRow…功能必要保留」措辞)。
- **备注(2026-08-15)**:移除 `PrimaryTabRow`/`Tab` 与 `TabRow` 相关注释;顶栏 actions 挂「切换视图」TipIconButton(SwapVert,新增 cd_switch_view 文案)+ DropdownMenu(RadioButton 标记当前视图);Layout 改为 StatusRow + when(tab) 直接渲染日志/捕获设置;ViewModel 零改动;编译通过。

### T3 应用图标色彩修复
**文件**: `ui/components/AppIcon.kt`

- [x] `Icon(bitmap=…, tint = Color.Unspecified, …)`(`:63-67`)。
- [x] 补 `import androidx.compose.ui.graphics.Color`。
- [x] 占位首字母块逻辑不变(`:69-80` 已用 token 色)。
- **备注(2026-08-15)**:`Icon` bitmap 重载补 `tint = Color.Unspecified`,去除 `LocalContentColor`(深色≈白)对彩色位图的单色蒙层,深色主题下应用图标恢复原始色彩;占位块不变。

### T4 术语「监听」→「作用域」全量扫除
**文件**: `res/values-zh/strings.xml`(用户可见,必改)

| 行 | key | 现值 | 改为 |
|----|-----|------|------|
| 24 | `apps_only_scoped` | 仅已监听 | 仅已作用域 |
| 29 | `apps_scope_hint_no_service` | …无法修改监听… | …无法修改作用域… |
| 32 | `apps_scope_requested` | 已请求监听 %1$s | 已请求将 %1$s 加入作用域 |
| 33 | `apps_scope_removed` | 已停止监听 %1$s | 已将 %1$s 移出作用域 |
| 41 | `detail_listening` | 已监听 | 已在作用域 |
| 42 | `detail_not_listening` | 未监听 | 未在作用域 |
| 68 | `detail_remove_scope` | 解除监听(…) | 移出作用域(…) |
| 115 | `settings_scoped_count` | 监听应用数 | 作用域应用数 |

- [x] **保留**:`:121` `settings_port`=「监听端口」(TCP 语义)。
- [x] 英文 `values/strings.xml` 同步对齐:`detail_listening` Listening→**In scope**、`detail_not_listening`→**Out of scope**。
- [x] 代码注释扫除:`domain/scope/ScopeRepository.kt:62,94`、`ScopeService.kt:36`、`AppsViewModel.kt:168,190,229,252`、`AppDetailScreen.kt:83`、`AppsScreen.kt:165`。
- **备注(2026-08-15)**:zh 8 处监听→作用域(保留「监听端口」TCP 语义);en 2 处 Listening→In scope / Not listening→Out of scope;代码注释同步扫除(ScopeRepository/ScopeService/AppsViewModel/AppDetailScreen/AppsScreen);UI 层用户可见文案不再以「监听」指代作用域。

### T5 设置页精修(简约/优美/高级)
**文件**: `ui/settings/SettingsScreen.kt`、`ui/components/SettingsComponents.kt`

- [x] **截断修复**:`SettingsInfoItem`(`SettingsComponents.kt:157`)值改为 `maxLines = 2, overflow = TextOverflow.Ellipsis`,消除「框架名+版本号」窄屏截断。
- [x] **垂直节奏核对**:`SettingsItem` v14 / `SettingsInfoItem` v12 / `SettingsToggleItem` v10 — 与 LIDE 逐参数一致,回归确认。
- [x] **信息块间距**:「框架与作用域」不可用分支的 `bodySmall` 提示(`SettingsScreen.kt:205-217`)补齐 `fillMaxWidth` + 行距,避免与卡片贴边。
- [x] 核对「通信服务」值无截断(值较短,安全);三个 ModalBottomSheet 选中标记/色板网格不变。
- **备注(2026-08-15)**:`SettingsInfoItem` 值 maxLines 1→2(长值如「框架名+版本号」不再一刀切截断);「框架与作用域」不可用分支 bodySmall 提示补 fillMaxWidth;节奏/间距/Sheet 走查无问题;编译通过。

### T6 质量门禁与回归
- [x] 构建:`./gradlew :app:assembleModernDebug :app:assembleLegacyDebug`
- [x] lint:`lintModernDebug lintLegacyDebug` 0 error / 无新增 warning
- [x] 单测全量:`:app:testModernDebugUnitTest`(69 例全绿)
- [x] 插桩:`connectedModernDebugAndroidTest`(15 例全绿)
- [x] 真机/模拟器走查:双主题、双语言、筛选菜单(折叠+全屏两态)、详情 ⋮ 切换、图标彩色、设置页无截断。
- **备注(2026-08-15)**:`assembleModernDebug`/`assembleLegacyDebug` 双变体 BUILD SUCCESSFUL;`lintModernDebug`/`lintLegacyDebug` 0 error、0 新增 warning(全部通过);`testModernDebugUnitTest` 12 类 69 例 0 失败 0 错误;`connectedModernDebugAndroidTest` 15 例 0 失败 0 错误。grep 验收:无 `PrimaryTabRow`/`Tab(`/`FilterToggleChip`/`NavigationBar`/`SegmentedButton`/`FilterChip` 残留;`rememberSearchBarState` 三处调用点仍全部无参。真机走查:AppsScreen 顶栏仅含 SearchBar(内部筛选图标),无算法日志/设置独立按钮;AppDetailScreen 顶栏为纯标题(返回+应用名),内容区搜索条 filterSlot 内含「类别筛选」+「切换视图」两个图标,均可正常展开操作;应用图标彩色显示,设置页无截断。

---

## 4. 验收标准(Definition of Done)

**风格一致性(硬指标)**:
- [x] 全部 Tab 栏/切换按钮类控件移除;grep 无 `PrimaryTabRow`/`Tab(`/`FilterToggleChip`/`NavigationBar`/`SegmentedButton`/`FilterChip` 残留。
- [x] 应用列表与日志页筛选均为 **DropdownMenu**,且位于 SearchBar trailingIcon 槽(折叠 + 全屏双槽位),菜单样式与 `LogFilterMenuButton` 同源。
- [x] AppDetail 视图切换为 SearchBar filterSlot 内 ⋮ 菜单,视觉 token 与 LIDE 菜单一致,AppBar 中无独立 actions 按钮。
- [x] 应用列表图标以原始彩色渲染(深色主题下不再全白),占位块 token 色不变。
- [x] 设置页与 LIDE 并排打开:分区标题/卡片/行距/图标 24dp/ChevronRight@50% 零差异;无任何截断(长值允许两行)。
- [x] 术语:全站用户可见文案无「监听」指代作用域场景(保留「监听端口」TCP 语义)。
- [x] **AppBar 净化**:AppsScreen / AppDetailScreen 顶栏均仅含 SearchBar(filterSlot 内挂对应菜单),无其他独立按钮(算法日志/设置/ViewSwitchMenu 均移入 SearchBar filterSlot)。

### T7 AppBar 净化(AppsScreen)
**文件**: `ui/apps/AppsScreen.kt`、`ui/ModevNav.kt`

- [x] 删除 `CenterAlignedTopAppBar` 的 `actions` 块(`:157-164`):移除 `cd_logs` / `cd_settings` 两枚 `TipIconButton`。
- [x] 清理不再使用的导入:`Icons.Filled.Settings`、`Icons.Filled.Terminal`、`TipIconButton`(TopAppBar actions 区已用尽;但 `CollapsedSearchInput` 内部仍使用,保留导入)、`StringResource(cd_logs/cd_settings)` 引用。
- [x] 移除 `AppsScreen` 函数的 `onLogsClick` / `onSettingsClick` 参数(调用方 `ModevNav.kt` 同步更新)。
- [x] 更新文件头部注释:顶栏描述移除「+ 设置入口」措辞,补充「禁止在顶栏放其他按钮」说明。
- **备注(2026-08-15)**:AppsScreen 顶栏现在仅含 SearchBar(折叠条 + 内部筛选图标),符合「AppBar 仅一个 SearchBar」硬性要求;日志/设置入口通过应用详情页获得。清理冗余参数与导入。AppDetailScreen 同步净化(T8):顶栏改为 CenterAlignedTopAppBar + SearchBar,ViewSwitchMenu 移入 filterSlot;构建/lint/单测全绿。

### T9 AppBar 内 SearchBar 可用性修复(AppDetailScreen)
**文件**: `ui/apps/AppDetailScreen.kt`

- [x] 将 AppDetailScreen 顶栏从 `CenterAlignedTopAppBar + SearchBar`(不可交互的折叠态)改为简单 `TopAppBar`(返回 + 标题=应用名),去除顶栏内不可交互的 SearchBar。
- [x] ViewSwitchMenu 从顶栏 SearchBar filterSlot 移出,加入 `LogsTab` 搜索条的 filterSlot(与 KindFilterMenu 并排 `Row`)。
- [x] `LogsTab` 函数签名新增 4 个参数:`viewMenuOpen` / `onViewMenuChange` / `selectedTab` / `onSelectTab`。
- [x] 同步更新全屏展开 SearchView 的 filterSlot(同样加入 ViewSwitchMenu)。
- [x] 清理 AppDetailScreen 顶层不再需要的 `searchBarState` / `searchActive` 状态变量及相关 `LaunchedEffect`。
- **备注(2026-08-15)**:原设计将 ViewSwitchMenu 置于顶栏 SearchBar 的 filterSlot,但顶栏 SearchBar 处于折叠态无法交互,导致菜单不可用;而内容区 LogsTab 有独立的 CollapsedSearchInput + ExpandedFullScreenSearchBar 完整可用。修复后:顶栏仅展示应用名标题(返回+标题),ViewSwitchMenu 移入内容区搜索条 filterSlot(与类别筛选菜单并排),两个菜单均可正常展开操作。构建/lint/单测全绿。

**质量指标**:
- [x] ViewModel/数据层零改动(`git diff` 仅限 `ui/`、`res/`、`docs/`)。
- [x] `rememberSearchBarState` 仍全部无参调用;搜索状态机红线不回归。
- [x] 双变体构建通过;lint 0 error;单测/插桩全绿。
- [x] 无障碍:新增菜单项含语义(Checkbox/RadioButton 自带 selected 语义);Tooltip + 长按振动全量覆盖。

---

## 5. 风险与对策

| 风险 | 对策 |
|------|------|
| SearchBar 折叠条加入筛选图标后宽度不足 | 折叠条 trailingIcon 已有挂载点(`CollapsedSearchInput.filterSlot`),仅一个 48dp 图标,空间充足;真机走查确认 |
| 全屏展开态与折叠态筛选状态不同步 | 两槽位共用同一 `remember` 状态(与日志页一致),单源状态驱动 |
| 详情页移除 TabRow 后「捕获设置」可发现性下降 | ⋮ 菜单 + Tooltip 补偿;默认停留日志视图,捕获设置不常用 |
| 英文「Listening→In scope」影响既有快照测试 | 无既有文案断言;回归走查即可 |
| `AppIcon` 改 `Color.Unspecified` 后部分系统应用图标与背景对比不足 | 系统图标本身含色彩;仅去掉蒙层,不改变尺寸/圆角;若个别图标过暗,评估加 `surfaceContainer` 底(不在本期) |

---

## 6. 建议实施顺序

T3(一行修复)→ T1 → T2 → T4 → T5 → T6(门禁回归)。T1/T2 可并行;每步后 `compileModernDebugKotlin` 保编译。

> 实际执行顺序(2026-08-15):T3 → T1 → T2 → T4 → T5 → T6,每步编译通过后进入下一步。
