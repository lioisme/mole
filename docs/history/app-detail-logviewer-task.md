# Modev 任务书 V2:主界面=应用列表 + 应用配置界面(SearchBar in AppBar,内容仅日志)

**文档版本**: v1.1 | **创建日期**: 2026-08-16 | **状态**: 已完成(验收全绿)
**适用范围**: `modev` 项目 `ui` 层 / 导航层 / i18n 资源(数据层零改动)
**前置说明**: 本任务书 **取代并撤销** `docs/launch-root-refactor-task.md`(该文档所描述的「设置页为启动首页」方向与用户最终意图相悖,已回退)。
**技术约束**: 纯 Kotlin + Jetpack Compose Material3(MD3),禁止 View 系统 UI / 第三方 UI 框架
**质量目标**: 代码质量、架构设计达到苹果 / 谷歌等大厂验收标准(可读性、单一职责、状态唯一、无障碍、性能、门禁)。

---

## 0. 需求(用户最终口径,已确认)

1. **主界面(启动页)= 应用列表**。
2. **点击应用 → 应用配置界面**(AppDetailScreen)。
3. **应用配置界面**:SearchBar 放进 **AppBar**;内容区**仅展示该应用的日志列表**;界面内有「**算法列表入口**」。
4. **日志界面(全局 LogsScreen)**:AppBar 内**仅放置一个 SearchBar**;筛选按钮**置于该 SearchBar 内部**(trailing 槽);内容区**仅展示算法列表**。
   - > 澄清记录(2026-08-16 追加):用户多次确认——AppBar 不得有多余按钮,**只放一个 SearchBar**;筛选功能**内嵌于 SearchBar**(不放到 AppBar 外,也不放到内容区);内容区**仅算法列表**。此规格与现有 LogsScreen 实现一致,属「验收确认」,无结构性代码改动(SearchInputComponents 曾临时加 `filterSlot` 默认值以支撑「筛选移出方案」,该方案已撤销,默认值已还原)。

### 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 启动页 | **应用列表`APPS`**(撤销 V1 的 SETTINGS 根页) |
| D2 | 捕获设置(8 类算法开关/移出作用域/清空日志) | **保留,移到独立入口**:AppBar「捕获设置」图标进入内嵌子视图,内容区保持仅日志 |
| D3 | 「算法列表入口」形态 | **AppBar 图标按钮** → 全局日志页(`LOGS`) |
| D4 | 设置页入口 | **从应用配置界面 AppBar 进入**(`SETTINGS`) |
| D5 | 范围红线 | 数据层(仓库/DB/hook/框架)零改动;不新增路由;不引入 NavigationBar / fade 转场 |
| D6 | 无害保留 | `ModevTopBar.showBack` 参数及测试保留(默认 true 非破坏);`AppsScreen.onBack` 参数保留(根页默认空操作) |

---

## 1. 审计结论(现状 → 差距)

| 文件 | 现状 | 差距 |
|------|------|------|
| `ModevNav.kt` | V1 后:`startDestination=SETTINGS`,SETTINGS 为根页兜底 finish,APPS 为次级页 | **撤销**:回 `APPS` 根页;SETTINGS 还原 BackGuarded |
| `SettingsScreen.kt` | V1 后:工具区含「应用列表」条目(接线 `onAppsClick`) | 移除该条目;`showBack` 参数保留默认 true |
| `strings.xml / values-zh` | V1 新增 `settings_apps` / `settings_apps_subtitle` | 删除(避免 UnusedResources) |
| `AppDetailScreen.kt` | 顶栏=返回+应用名;内容=StatusRow+双 Tab(日志/捕获设置),搜索条嵌 Tab 内带「类别筛选+视图切换」双菜单 | **重构**:顶栏 SearchBar;内容仅日志列表;三入口(算法列表/捕获设置/设置) |
| `AppsViewModel.kt` (AppDetailViewModel) | `Tab.LOGS/SETTINGS` 双视图 | 收敛为 `LOGS/CAPTURE`,数据流不变 |
| `LogsScreen.kt` | AppBar=SearchBar,Sbar 内仅筛选按钮 | **已合规**,仅验收 |

---

## 2. 改动清单与执行顺序

### T1. 任务书文档(本文件)+ 旧书标注撤销
- 创建本文件;在 `launch-root-refactor-task.md` 头部标注「已被 V2 取代并撤销」。

### T2. `ModevNav.kt` — 恢复应用列表根页
- `startDestination = APPS`;APPS 分支恢复根页 `BackHandler{ if(!popGuarded()) finish }`,转场 `QUARTER`;`AppsScreen(onBack 不再传,默认空操作)`。
- SETTINGS 分支:还原 `BackGuarded` 包裹、`onBack={popGuarded()}`、转场 `FULL`;移除根页兜底与 `showBack=false`;移除 `onAppsClick` 接线。

### T3. `SettingsScreen.kt` — 移除「应用列表」条目
- 工具区仅保留「算法日志」;`onAppsClick` 参数移除;`showBack` 参数保留默认 true。

### T4. i18n — 删 V1 遗留键
- `values`+`values-zh` 删除 `settings_apps` / `settings_apps_subtitle`。

### T5. `AppDetailViewModel.kt` — Tab 收敛
- `enum Tab { LOGS, CAPTURE }`;`selectedTab` 初始 `LOGS`;`selectTab` 保留;数据流(records/switchState/connected/scopeState/appInfo)零改动。

### T6. `AppDetailScreen.kt` — 顶栏 SearchBar + 内容仅日志 + 三入口(核心)
- 顶栏:`TopAppBar`navigationIcon=返回;title=SearchBar(`CollapsedSearchInput` + 全屏 `ExpandedFullScreenSearchBar`,`showBack` 于 AppBar 层处理);actions=3×`TipIconButton`:算法列表(`Icons.Outlined.Terminal`)/ 捕获设置(`Icons.Default.Tune`)/ 设置(`Icons.Default.Settings`)。
- 内容:仅渲染 `records` 分页日志列表(空态 / loading / retry),赛选保留 1 个类别按钮;移除 `StatusRow`、`TabRow`、`ViewSwitchMenu`。
- 捕获设置内嵌子视图(迁移原 `SettingsTab`,含 StatusRow);`BackHandler`:捕获子视图 → 先切回日志,再按才弹栈。
- 搜索状态机红线全守(无参 `rememberSearchBarState` / `searchActive` / 唯一动画驱动 / snapTo 兜底 / pendingNavigation)。

### T7. 导航接线(含于 T6 `/ModevNav`)
- AppDetail → `LOGS`:`navigateGuarded(LOGS)`;AppDetail → `SETTINGS`:`navigateGuarded(SETTINGS)`。
- `ModevNav` 给 AppDetailScreen 传 `onOpenLogs` / `onOpenSettings` 回调。

### T8. 质量门禁
- `:app:compileModernDebugKotlin`;`:app:testModernDebugUnitTest`;`:app:lintModernDebug`(零新增告警);`:app:assembleModernRelease`(含 lintVital);`:app:connectedModernDebugAndroidTest` 全绿。

### T9. 真机走查(自动化脚本闭环)
- 冷启动=应用列表 → 点击应用进 AppDetail(顶栏 SearchBar)→ 捕获设置子视图往返 → 算法列表全局页往返 → 设置页往返 → 根页返回退出。

### T10. 文档逐项标记完成 + 验收标准勾选。

---

## 3. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 任务书 + 旧书标注 | [x] | 新建本任务书;`launch-root-refactor-task.md` 头部加醒目撤销声明,指向 V2。 |
| T2 | ModevNav 恢复 APPS 根页 | [x] | `startDestination=APPS`;APPS 分支恢复根页兜底 `BackHandler{ popGuarded() ?: finish }` + `QUARTER`;SETTINGS 还原 `BackGuarded`/`FULL`/`popGuarded`;移除 `showBack=false`、`onAppsClick`、单列 `NavOptions` 引用。 |
| T3 | SettingsScreen 移除应用列表条目 | [x] | 「工具」区仅保留算法日志;删除 `onAppsClick` 参数与条目;`showBack` 保留默认 true(非根页显示返回箭头),文档注释同步。 |
| T4 | i18n 删 V1 遗留键 | [x] | 删除 `settings_apps` / `settings_apps_subtitle`(en+zh);键集校验 en/zh 回归完整。 |
| T5 | AppDetailViewModel Tab 收敛 | [x] | `Tab { LOGS, CAPTURE }`,初始 `LOGS`;records/switchState/appInfo/connected/messages 数据流零改动。 |
| T6 | AppDetailScreen 重构(核心) | [x] | 重写:顶栏(T1)= `TopAppBar` 返回 + **SearchBar(title 槽,`CollapsedSearchInput showBack=false` + 类别筛选)** + 三入口(算法列表 Terminal / 捕获设置 Tune / 设置 Settings,经 TipIconButton);内容区**仅日志列表**(Paging 空态/loading/retry,无 StatusRow/Tab);捕获设置迁为内嵌子视图(StatusRow + 8 类开关 + 移出作用域 + 清空日志);`BackHandler` CAPTURE 优先回 LOGS;搜索状态机红线全守(searchState 提升至 Screen 层共享折叠条与全屏窗);同步给 `CollapsedSearchInput` 增加 `showBack` 参数(默认 true,既有调用方非破坏)。 |
| T7 | 导航接线(详情→日志/设置) | [x] | `ModevNav` 传 `onOpenLogs={navigateGuarded(LOGS)}` / `onOpenSettings={navigateGuarded(SETTINGS)}`;返回链全部过 NavigationGate。 |
| T8 | 质量门禁全绿 | [x] | `compileModernDebugKotlin`(仅既有告警)、`testModernDebugUnitTest` 全绿、`lintModernDebug` 0 error / **27 warning(回基线)**、`assembleModernRelease`(含 lintVital)通过、`connectedModernDebugAndroidTest` **16/16**。本任务引入的孤儿键 `cd_switch_view` / `detail_tab_logs` 已删除清零。 |
| T9 | 真机走查闭环 | [x] | emulator-5554 实测:冷启动=应用列表 → 点应用进 AppDetail(顶栏「返回/筛选+搜索提示」+ 算法日志/捕获设置/设置 三入口;内容仅日志空态「暂无记录…」)→ 捕获设置子视图(标题+离线/框架服务未连接+8 开关)返回回日志 → 算法列表进全局日志页(空态文案不同 + 仅「返回/筛选」两按钮,符需求2)返回回详情 → 设置入口进设置页(显示/工具…)两次返回回应用列表根 → 根页返回退出 App(finish)。 |
| T10 | 文档标记完成 | [x] | 本节账本 + §4 验收全部勾选。 |

---

## 4. 验收标准(Definition of Done)

**信息架构 / 功能**
- [x] 冷启动直达**应用列表**;根页空闲返回 → 退出 App。——实测:冷启动=应用列表;根页返回 finish。
- [x] 点击应用 → **应用配置界面**:顶栏 = 返回 + SearchBar + 三入口(算法列表/捕获设置/设置)。——实测 content-desc:返回/筛选/算法日志/捕获设置/设置 + 搜索提示文案。
- [x] 内容区**仅该应用的日志列表**(分页/空态/loading/retry/类别筛选);无 StatusRow/Tab。——实测空态「暂无记录…开启捕获设置…」,无状态 chip。
- [x] 捕获设置入口 → 子视图(8 类开关/移出作用域/清空日志),返回先回日志再弹栈。——实测:捕获设置页标题+离线 chip+开关;返回回日志视图。
- [x] 算法列表入口 → 全局日志页(`LOGS`,AppBar=SearchBar+唯一筛选),返回回详情。——实测:全局页空态文案不同,仅「返回/筛选」,**符需求 2**;返回回详情。
- [x] 设置入口 → 设置页(`SETTINGS`),返回回详情。——实测:设置页(显示/工具…)→两次返回回应用列表根。

**搜索 / 组件规范**
- [x] AppDetail 与全局 Logs 的 `rememberSearchBarState` 全部无参;状态机红线零回归。——代码审计:AppDetail 单例提升至 Screen/searchState 折叠条与全屏共享;全局 Logs 未动。
- [x] 全屏 SearchView 返回仅收状态;收起后统一导航(pendingNavigation)。——沿用既有 pendingNavigation 机制。
- [x] 全部 IconButton 长按 tooltip + 振动(TipIconButton);禁用态规格不回归。——三入口/筛选均 TipIconButton。

**质量门禁**
- [x] `assembleModernRelease`(含 lintVital)与 `lintModernDebug` 通过,零新增告警。——0 error / 27 warning(回基线)。
- [x] `testModernDebugUnitTest` / `connectedModernDebugAndroidTest` 全绿。——单测全绿 / 插桩 16 例全绿。
- [x] 双语言(en/zh)键集 diff=0;`settings_apps*`/`cd_switch_view`/`detail_tab_logs`/`cd_settings` 已删净。——133/133 键对一致。
- [x] 本次改动面仅限 `ui/apps`、`ui/ModevNav`、`components/SearchInputComponents`、`strings`、任务文档;数据层零改动。——逐项核对;`compile` 仅既有告警(WireCodec/LogRepository/ScopeRepository/AppsViewModel 基线)。

---

## 5. 风险与注意事项

| 风险 | 对策 |
|------|------|
| AppDetail 顶栏水平空间紧张(返回+SearchBar+3 icons) | plain `TopAppBar`:返回在 navigationIcon,SearchBar 作 title 自适应;清除按钮仅输入时出现(现有行为)腾空间 |
| 捕获子视图返回误退出 | `BackHandler`:CAPTURE 态先 `selectTab(LOGS)`;仅 LOGS 态才弹栈 |
| 搜索与导航并发 | 导航统一走 NavigationGate + 收起动画协程;全屏内返回不直接导航 |
| V1 撤销残留(字符串/入口) | T3/T4 专项清理 + lint UnusedResources 复核 |
| 三入口与筛选按钮语义混淆 | 工具提示逐一语义化:算法列表=Terminal/cd_logs、捕获设置=Tune、设置=Settings |
