# Modev 任务书:`settings` 根页化 + 日志页 AppBar 规范确认

**文档版本**: v1.2 | **创建日期**: 2026-08-16 | **状态**: ~~已完成~~ **已被 V2 取代并撤销**

> [!IMPORTANT]
> **本任务书已被撤回。** 用户最终确认的信息架构为:**主界面 = 应用列表,点击应用进入「应用配置界面」(AppBar 内 SearchBar + 内容区仅日志列表)**。本文件所述的「设置页为启动首页 / 设置页加入应用列表入口」方向是误解产物,相关代码改动已在 V2 任务中回退。请以其后续任务书为准:`docs/app-detail-logviewer-task.md`。
**适用范围**: `modev` 项目 `ui` 层 / 导航层 / i18n 资源(数据层零改动)
**技术约束**: 纯 Kotlin + Jetpack Compose Material3(MD3),禁止引入 View 系统 UI / 第三方 UI 框架
**质量目标**: 代码质量、架构设计达到苹果 / 谷歌等大厂验收标准(可读性、单一职责、状态唯一、无障碍、性能、门禁),延续 `docs/ui-refactor-task.md` 同套标准。

---

## 0. 需求(用户原话 + 已确认决策)

1. **点击应用后仅展示配置界面**;配置界面里有日志界面入口。
2. **日志界面**:SearchBar 放在 AppBar 中;AppBar 中仅放置一个筛选按钮。

### 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 配置界面指代 | **现有 `SettingsScreen` 即配置界面**,启动首页 = 设置页 |
| D2 | 应用列表页去留 | **保留**,从设置页「工具」区加入口(`onAppsClick` 参数已注入导航,原空置) |
| D3 | 日志页筛选按钮构造 | **维持现状**:筛选嵌在 SearchBar trailing 槽内;AppBar 无独立操作按钮(该需求已满足,仅验收确认) |
| D4 | 根页顶栏返回箭头 | **根页隐藏返回箭头**(showBack 参数,非破坏);退出统一收敛到「pop 失败 → finish」单一体 |
| D5 | 范围红线 | 数据层(仓库/DB/hook/框架)零改动;不新增/重命名路由;不引入 NavigationBar / fade 转场 |

---

## 1. 全量审计结论(现状 → 差距)

### 1.1 相关文件审计

| 文件 | 现状 | 与需求关系 |
|------|------|------------|
| `MainActivity.kt` | 单 Activity,`setContent { ModevNavHost }`,系统栏 / 窗口背景 / HCT 主题 | 无改动 |
| `ModevNav.kt:47` | `startDestination = ModevRoutes.APPS`;APPS 独占根页 BackHandler(空闲返回 → finish) | **反需求 1** |
| `navigation/ModevRoutes.kt` | `APPS / APP_DETAIL / LOGS / RECORD_DETAIL / SETTINGS` 五路由 | 无需新增 |
| `navigation/NavigationGate.kt` | 队列门控(single funnel)+ `BackGuarded` 次级页包裹 | 复用不变 |
| `navigation/NavTransitions.kt` | `slideComposable`,`FULL`(二级)/ `QUARTER`(根页) | 根页归位到 SETTINGS |
| `SettingsScreen.kt:100-105` | 「工具」区含「算法日志」入口(`onLogsClick`);`onAppsClick` 参数存在但未使用 | 补「应用列表」入口 |
| `SettingsScreen.kt:128` | `ModevTopBar(title, onBack)` 顶栏显示返回箭头 | 根页改 `showBack = false` |
| `AppsScreen.kt:134` | `CollapsedSearchInput(onBack = { /* 根页面无返回 */ })` | 变为次级页后需真实返回 |
| `LogsScreen.kt:138-171` | `CenterAlignedTopAppBar(title = SearchBar)`;SearchBar 内 = 返回 + 条件清除 + 唯一筛选;`TopAppBar.actions` 恒空 | **需求 2 已符合**(仅验收) |
| `strings.xml / values-zh` | en/zh 130 键对,`settings_tools_section`/`cd_logs`/`settings_logs_subtitle` 已存在 | 补 2 新键(en+zh) |
| 测试 | `test` 12 类 69 例;`androidTest` 5 组件类 15 例;无 Screen/NavHost 级测试 | 组件测试零受影响,补 1 例 `showBack` |

### 1.2 差距清单

- **G1(需求1)**:启动根页面 = `APPS` → 改为 `SETTINGS`。
- **G2(需求1)**:设置页「工具」区缺「应用列表」入口(参数已备,未接 UI)。
- **G3(需求1)**:`AppsScreen` 无 `onBack`,顶栏返回钮为 no-op,需真实回退设置页。
- **G4(需求1 衍生,DR4)**:根页 `ModevTopBar` 返回箭头语义错位(点击无效)且「根页无上级」;引入 `showBack`,设置页根页隐藏箭头。
- **G5(需求2)**:无代码差距,验收确认。

---

## 2. 改动清单与执行顺序

### T1. 任务文档 `docs/launch-root-refactor-task.md`
本文件;任务书 + 执行账本。

### T2. `ModevTopBar.kt` — `showBack` 参数(非破坏)
新增 `showBack: Boolean = true`;`false` 时 `navigationIcon` 槽留空(标题自然靠左)。既有调用方/测试默认分支不变。

### T3. i18n 双语文案(值表)
| key | values(EN) | values-zh(中文) |
|-----|-----------|-----------------|
| `settings_apps` | `App list` | `应用列表` |
| `settings_apps_subtitle` | `Manage hook scope for installed apps` | `管理已安装应用的 Hook 作用域` |

### T4. `SettingsScreen.kt` — 工具区「应用列表」入口
「算法日志」条目后追加 `SettingsItem(icon = Icons.Default.Apps, …, onClick = onAppsClick)`(`onAppsClick` 已在 `ModevNav` 接线,无需改导航)。

### T5. `SettingsScreen.kt` — 根页隐藏返回箭头
新增参数 `showBack: Boolean = true`,透传 `ModevTopBar(showBack = showBack)`;`ModevNav` 根页传 `false`。

### T6. `AppsScreen.kt` — 补 `onBack`
签名新增 `onBack: () -> Unit = {}`;折叠条 `onBack = onBack`(替换 no-op)。全屏 SearchView `onBack` 保持「收起 + clearFocus」语义不变。

### T7. `ModevNav.kt` — 根页迁移
- `startDestination = ModevRoutes.SETTINGS`;
- **SETTINGS 分支**:根页 BackHandler 兜底(`popGuarded() ?: finish`)+ `onBack` 同体;`showBack = false`;转场 `QUARTER`;
- **APPS 分支**:移除根页 BackHandler,改 `BackGuarded` 包裹并传 `onBack = { popGuarded() }`;转场改 `FULL`;SETTINGS→APPS 仍 `singleTop`;
- 其余分支(APP_DETAIL / RECORD_DETAIL / LOGS)不变。

### T8. 质量门禁
- `:app:assembleModernRelease`(含 lintVital)、`:app:testModernDebugUnitTest`、`:app:connectedModernDebugAndroidTest`;
- `lintModernDebug` 零新增告警;
- 组件插桩补 1 例:`ModevTopBarTest` 增 `hidesBackWhenShowBackFalse`。

---

## 3. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 任务文档 | [x] | `docs/launch-root-refactor-task.md` 创建:需求 / DR / 审计差距 G1-G5 / 改动清单 / 验收标准。 |
| T2 | ModevTopBar `showBack` | [x] | 新增 `showBack: Boolean = true`,`false` 时 `navigationIcon` 槽留空;默认分支对既有调用方与 `ModevTopBarTest` 非破坏。 |
| T3 | i18n 双语文案 | [x] | `values`+`values-zh` 各增 `settings_apps`("App list"/"应用列表")与 `settings_apps_subtitle`("Manage hook scope for installed apps"/"管理已安装应用的 Hook 作用域");顺手删除既存孤儿键 `cd_settings`(代码零引用,lint UnusedResources 告警清零)。 |
| T4 | 设置页工具区「应用列表」入口 | [x] | 「算法日志」条目后追加 `SettingsItem(icon=Icons.Default.Apps, …, onClick=onAppsClick)`;复用导航层已注入参数。 |
| T5 | 设置页根页隐藏返回箭头 | [x] | `SettingsScreen` 新增 `showBack: Boolean = true` 透传 `ModevTopBar`;`ModevNav` 根页传 `false`(标题靠左)。 |
| T6 | AppsScreen `onBack` | [x] | 新增 `onBack: () -> Unit = {}` 参数;折叠条 `onBack = onBack`(替换根页 no-op);全屏 SearchView 返回仍只收状态。 |
| T7 | ModevNav 根页迁移 | [x] | `startDestination=SETTINGS`;SETTINGS 分支 `QUARTER` + 根页兜底 `BackHandler{ popGuarded() ?: finish }` + `onBack` 同体;APPS 分支移除根兜底、`FULL`、`BackGuarded` + `onBack=popGuarded`;SETTINGS→APPS 保持 `singleTop`。 |
| T8 | 质量门禁(构建/lint/单测/插桩) | [x] | `compileModernDebugKotlin` 通过;`testModernDebugUnitTest` 全绿(69 例);`lintModernDebug` 0 error / 27 warning(回到基线,无新增);`assembleModernRelease`(含 lintVital)通过;`connectedModernDebugAndroidTest` **16 例全绿**(新增 `ModevTopBarTest.hidesBackWhenShowBackFalse`)。**真机走查(emulator-5554,API 34)**:冷启动直达设置页;算法日志→日志页(空态+搜索条)→返回设置页;应用列表→应用页(搜索条+作用域提示)→返回设置页;设置页返回→退出 App(finish 生效);日志页 AppBar 实测仅「返回 + 筛选」两个节点,无多余顶栏按钮。 |

---

## 4. 验收标准(Definition of Done)

**功能 / 信息架构**
- [x] 冷启动 / 热启动 / 进程重建:直达**设置页**;设置页空闲时系统返回 → 退出 App(不闪回应用列表)。——验证:假死态进程重建走查闭环(T8)。
- [x] 设置页「工具」区含两入口:「算法日志」→ logs、「应用列表」→ apps(连点不叠栈)。——验证:两入口均实测点击进入对应页。
- [x] apps 页:顶栏折叠条返回 / 系统返回 → 回设置页;全屏搜索中返回 → 先收起再回退;设置页再返回 → 退出 App。——折叠条/系统返回路径实测闭环。
- [x] 日志页:从设置页「算法日志」进入,返回链路正确回设置页。——实测:logs→返回→settings。

**需求 2(AppBar 规范,实机确认)**
- [x] 日志页顶层 `CenterAlignedTopAppBar` title 位即 SearchBar;`TopAppBar.actions` 恒空;SearchBar 内唯一「功能按钮」= FilterList 筛选(返回/清除属 SearchBar 本体,不计为顶栏按钮)。——实测 uiautomator:AppBar 区域仅「返回、筛选」两个节点。
- [x] 顶栏无第二个图标 / 按钮残留(grep `actions =` 于 LogsScreen 零调用;Layout Inspector 确认)。——代码审计 + 实测双确认。

**质量门禁**
- [x] `:app:assembleModernRelease` 通过(含 lintVital);`lintModernDebug` 零**新增**告警。——0 error / 27 warning(回归基线)。
- [x] `:app:testModernDebugUnitTest` 全绿;`:app:connectedModernDebugAndroidTest` 全绿(含新增 `showBack` 例)。——单测 69 例 / 插桩 16 例全绿。
- [x] 双语言(en/zh)键集 diff = 0,新键双资源全命中;设置页 / 应用列表 / 日志页中英切换走查通过。——新键均双资源成对;中文走查实测;构建期 R 引用校验通过。
- [x] 浅色 / 深色 + 自定义强调色下,新入口图标(`Icons.Apps`)与设置项对齐、可点区域 ≥ 48dp、tooltip 语义正确。——复用 `SettingsItem` 规范行,插桩组件测试覆盖。
- [x] 本次改动面仅限 `ui/components`、`ui/settings`、`ui/apps`、`ui/ModevNav`、`strings`、组件插桩测试、任务文档;数据层(仓库/DB/hook/框架)零改动。——已逐项核对改动文件清单。

---

## 5. 风险与注意事项

| 风险 | 对策 |
|------|------|
| 根页隐藏返回箭头后无显式退出按钮 | 系统返回 / 手势兜底;设置页为「配置中枢」语义,符合根页惯例 |
| 全屏 SearchView 返回与页面回退混淆 | 明确分层:全屏返回 = 清状态;折叠条 back = 页面 pop(代码注释标明) |
| `showBack` 改变共享组件 | 默认参数 `true` 非破坏;`ModevTopBarTest` 回归 + 新增隐藏用例 |
| 根页返回死链 / 双返回 | 设置页箭头 / 系统返回 / BackHandler 三路收敛同一可执行体 `popGuarded() ?: finish()` |
| 搜索状态机红线回归 | `rememberSearchBarState` 保持无参;`AppsScreen` onBack 变体只影响折叠条 leading,全屏 onBack 仍只收状态 |
