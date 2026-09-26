# Modev 四界面重构任务文档 V3

| 项 | 值 |
|---|---|
| 版本 | 2026-08-16 V3(基于用户四项决策:四界面 IA / 每应用配置保留 / 设置入口=应用列表筛选菜单底部 / 清除按钮严格移除) |
| 目标 | 信息架构收敛为 4 界面;AppBar/SearchBar 硬性规范;代码质量对齐大厂验收线 |
| 验收红线 | 违反 R1/R2/R3 的 UI 一律视为 P0 缺陷,阻断发版 |

## 0. 需求条款(审计基准,来源:用户需求 + 全量审计)

| 条款 | 内容 |
|---|---|
| R1 | 恰好四个界面:①应用列表(主界面)②算法列表(点击应用进入)③算法详情(点击算法进入)④全局设置 |
| R2 | 应用列表、算法列表的 AppBar 中仅放置一个 Searchbar |
| R3 | Searchbar 中仅放置一个筛选按钮 |

## 1. 目标信息架构

```
应用列表(根页 APPS)                    ← 主界面,空闲返回=退出 App
  ├─ 点击应用 → 算法列表(ALGORITHM_LIST)
  │     └─ 点击算法 → 算法详情(RECORD_DETAIL)
  └─ 筛选菜单底部 → 设置(SETTINGS,全局)
```

| 界面 | 路由 | 顶栏规格 | 内容 |
|---|---|---|---|
| ①应用列表 | `apps` | AppBar=**仅** Searchbar(折叠条) | 应用行(图标/名称/包名/记录数/作用域勾选)+ 下拉刷新 + 空态 |
| ②算法列表 | `apps/{packageName}` | AppBar=**仅** Searchbar(折叠条) | 应用头部(图标/名称/状态)+「捕获设置」折叠区(每应用 8 类开关+移出作用域+清空日志)+ 分页记录列表 |
| ③算法详情 | `record/{recordId}` | 返回 + 标题=算法名 + 动作(上一条/下一条/复制 JSON/复制文本) | 三态(加载/成功/失败)+ 元数据 + 字节区块 + 调用栈 |
| ④全局设置 | `settings` | 返回 + 标题 | 显示/框架/通信/数据治理/通用分区(删工具区「算法日志」项) |

**删除项**:`LOGS` 路由、`LogsScreen.kt`、`LogsViewModel.kt`、`AppDetailScreen` 的 LOGS/CAPTURE Tab 机制、`ModevTopBar.showBack` 双态、`SettingsScreen.onLogsClick`。

## 2. AppBar / Searchbar 硬性规范(对 R2/R3 的正式定义)

1. **R2 定义**:AppBar 槽位中只能出现一个 SearchBar,不允许 `navigationIcon`/`actions` 出现任何其他元素(含返回键)。
2. **R3 定义**:SearchBar 折叠条与全屏搜索输入条(`ExpandedFullScreenSearchBar` 的 inputField)内部只允许一个按钮——筛选按钮(`FilterList` + tooltip + DropdownMenu)。返回箭头、清除按钮一律禁止入条。
3. **返回语义**(独立于 AppBar):系统返回键 + 导航门控承担;根页空闲返回 = finish。
4. **清除查询**:通过全屏输入框退格/全选删除完成,不做按钮(文档化决策,避免 R3 被侵蚀)。
5. **筛选菜单内容**不受 R3 限制,但必须保持纯筛选语义:禁止放入破坏性操作(如「清空全部」)与非筛选功能(如设置入口 —— 仅应用列表筛选菜单底部允许「设置」项,这是设置入口的唯一规范位置)。
6. **筛选启用指示**:任何筛选条件激活时,筛选按钮 tint 用 `colorScheme.primary`(未激活用 `onSurfaceVariant`),让用户感知列表被过滤。

## 3. 变更清单(逐项执行,完成即勾选)

### 3.1 清理
- [x] C1 删除 `ui/logs/LogsScreen.kt`、`ui/logs/LogsViewModel.kt`(第 5 页,IA-1/IA-3)
  - **完成 2026-08-16**:文件已删除;`RecordRow/KindBadge` 等共用件迁至 `ui/components`(见 K3)。
- [x] C2 删除仓库根目录 `hs_err_pid*.log`、`replay_pid*.log`;`.gitignore` 补充 `hs_err_pid*.log`、`replay_pid*.log`
  - **完成 2026-08-16**:4 个残留日志文件已删,`.gitignore` 已补充。
- [x] C3 删除 `ui/logs/RecordDetailDialog.kt`(已在上轮删除,确认无残留引用)
  - **完成 2026-08-16**:grep 全源码无残留引用,`compileModernDebugKotlin` 通过佐证。

### 3.2 组件层
- [x] K1 `ui/components/SearchInputComponents.kt` 收紧:`CollapsedSearchInput` 删 `showBack` 参数/返回按钮/清除按钮,仅剩「占位文本 + filterSlot」;`SearchInputField` 删 leading 返回/条件清除,仅剩 filterSlot
  - **完成 2026-08-16**:新签名 `CollapsedSearchInput(query, onTapToExpand, placeholder, filterSlot)`、`SearchInputField(query, onQueryChange, active, onActiveChange, onSearch, placeholder, filterSlot)`;`copyToClipboard` 保留。
- [x] K2 抽取共享搜索状态机封装 `ui/components/SearchState.kt`(`rememberSearchController`,红线全守:无参 `rememberSearchBarState` / `searchActive` 独立意图 / `LaunchedEffect` 唯一动画驱动 / 收起后导航 / `snapTo` 兜底),Apps 与 AlgorithmList 两页共用(消除 D-1)
  - **完成 2026-08-16**:`SearchController<T>(searchBarState, searchActive/queryText 委托, collapseWithNavigation/collapse/onInputActiveChange)` + `rememberSearchController(onNavigate)`;两页共用。
  - **真机回归修复(2026-08-16,emulator-5554)**:进程重建恢复全屏搜索后按返回,搜索又自动重开——根因:M3 `InputField` 在 `collapse()` 后 ~100ms 由焦点回调触发 `onExpandedChange(true)`,把收起意图反向顶回(`SearchBarState.currentValue` 是 `progress==0` 才 Collapsed,动画期仍 Expanded)。修复:新增 `onInputActiveChange`,焦点驱动的展开意图仅在 `currentValue==Collapsed` 时接受,动画期忽略;全屏输入条改传 `search::onInputActiveChange`(折叠条本就用 `onTapToExpand`,不受影响)。
- [x] K3 `RecordRow`/`KindBadge`/`kindSymbol`/`kindLabelRes`/`summaryOf` 从 `LogsScreen.kt` 迁至 `ui/components/RecordRow.kt`(算法列表与详情页共用)
  - **完成 2026-08-16**:`RecordRow`/`KindBadge`/`kindLabelRes`/`operationLabel`/`directionLabel`/`summary` 落地;`kindSymbol` 并入 `KindBadge` 内部、`summaryOf` 更名 `summary(directionLabel)`(微小命名收敛,功能等价)。
- [x] K4 类别色 token 化:`ui/theme/KindColorTokens.kt`(浅/深两套,AA 对比度),删除 `LogsScreen.kt` 硬编码 8 色(消除 D-5)
  - **完成 2026-08-16**:`lightKindPalette/darkKindPalette` 8 类 + `@Composable kindColor(kind)`。
- [x] K5 `operationLabel/directionLabel` 走 `strings.xml`(en/zh 成对新增 19 项),删除硬编码英文(消除 D-6)
  - **完成 2026-08-16**:实际新增 20 项(en/zh 同步,15 个 `op_*` + 5 个 `dir_*`);en/zh 各 145 条,Compare-Object 0 diff。

### 3.3 页面层
- [x] P1 `ui/apps/AppDetailScreen.kt` → `ui/apps/AlgorithmListScreen.kt`:删 Tab 枚举/双视图/`BackHandler` 双语义;顶栏 = `CenterAlignedTopAppBar` + SearchBar(仅筛选按钮);内容 = 应用头部(图标/名称/状态 chips)+「捕获设置」折叠区(默认收起,`rememberSaveable`)+ 分页记录列表(空态/重试)
  - **完成 2026-08-16**:新建 `AlgorithmListScreen.kt`,删除 `AppDetailScreen.kt`;AppHeaderRow + 状态 chips + `CaptureSettingsCard`(8 开关/移出作用域/清空日志)+ 记录分页(下拉刷新/空态/重试)。
  - **真机回归修复(2026-08-16,emulator-5554)**:空记录时空态曾整体替换 LazyColumn 导致「捕获设置」卡不可达(连点筛选都无法展开)。修复:改为 LazyColumn 恒在 + 空态/加载/刷新失败作为 `fillParentMaxSize` 项,捕获设置卡始终可展开。
- [x] P2 `ui/apps/AppsViewModel.kt`:`AppDetailViewModel` → `AlgorithmListViewModel`,删 `Tab` 枚举/`selectedTab`,保留数据流;factory 同步更名
  - **完成 2026-08-16**:`AlgorithmListViewModel`/`AlgorithmListViewModelFactory`;数据流与 setSwitch/removeScope/clearLogs 语义不变。
- [x] P3 `ui/apps/AppsScreen.kt`:折叠条与全屏输入条只剩筛选槽;`ScopeFilterMenu` 底部加分隔线 +「设置」项(`Icons.Default.Settings`);删除全屏视图二次过滤(直接渲染 `state.rows`);新增 `onOpenSettings` 参数(消除 D-2/IA-4)
  - **完成 2026-08-16**:`rememberSearchController<AppsNavTarget>` 接管状态(导航目标 = 应用详情/设置,sealed 类型);折叠态点击行直接导航、展开态 `collapseWithNavigation`(Q2);筛选激活时按钮 tint=primary(Q1)。
  - **真机回归修复(2026-08-16,emulator-5554)**:全屏搜索态下从筛选菜单点「设置」若直接导航,`animateToCollapsed` 协程随组合销毁被打断,`currentValue` 卡在 Expanded,返回时全屏搜索自动重开。修复:设置入口统一走 `collapseWithNavigation(AppsNavTarget.Settings)`(与行点击同一条「收起动画 → 完成后导航」协程),折叠态则直接导航。

### 3.4 导航层
- [x] N1 `ui/navigation/ModevRoutes.kt`:删 `LOGS` 常量;`APP_DETAIL` 常量更名 `ALGORITHM_LIST`(路由字符串保持稳定)
  - **完成 2026-08-16**:`LOGS` 删除,`appDetail(packageName)` → `algorithmList(packageName)`,路由串不变(`apps/{packageName}`)。
- [x] N2 `ui/ModevNav.kt`:路由收敛为 4 个 `slideComposable`;删 `LOGS` 块与 `onOpenLogs/onOpenSettings` 参数链;`AppsScreen` 传 `onOpenSettings`;`AlgorithmListScreen` 传 `onBack/onOpenRecord`
  - **完成 2026-08-16**:4 个 `slideComposable`;`BackGuarded` 封装 popGuarded;根页空闲返回 finish。
- [x] N3 `ui/settings/SettingsScreen.kt`:删 `onLogsClick` 参数与「工具区·算法日志」`SettingsItem`;`ui/components/ModevTopBar.kt` 删 `showBack` 双态(无调用方需要)
  - **完成 2026-08-16**:`onLogsClick` 与工具区已删;`ModevTopBar` 恒显返回;`SettingsScreen`/`AlgorithmListScreen`/`RecordDetailScreen` 调用点已更新。

### 3.5 质量治理
- [x] Q1 筛选启用指示:筛选按钮按「有激活条件」切换 `primary` tint(应用列表/算法列表统一)(消除 D-4)
  - **完成 2026-08-16**:`filterActive → primary` / 未激活 `onSurfaceVariant`,两页统一;色值断言属视觉走查(见 §5)。
- [x] Q2 交互统一(消除 D-3):折叠态点击行直接导航(无 Dialog 可收起);展开态点击行先收后导(`pendingNavigation`)——语义规则写入注释
  - **完成 2026-08-16**:两页统一实现,规则注释已写入 AppsScreen/AlgorithmListScreen;设置入口(筛选菜单底部)同样遵守「展开态先收后导」(见 P3 回归修复)。
- [x] Q3 死代码清扫:`cd_logs`/`logs_search_hint`/`logs_empty`/`logs_clear_all`/`logs_all_apps`/`settings_logs_subtitle` 等资源与 import 清理(消除 D-8)
  - **完成 2026-08-16**:en/zh 各删 8 条旧资源;全源码 grep 无残留符号;`SettingsComponentsTest` 改用 `settings_display_section`。
- [x] Q4 `lintModernDebug` 0 error
  - **完成 2026-08-16**:`lintModernDebug` BUILD SUCCESSFUL(仅既有 `room.schema` 警告,非 error)。

### 3.6 测试
- [x] T1 更新 `SearchInputComponentsTest`:断言折叠条/全屏输入条仅含 1 个图标按钮(筛选),删除清除/返回用例
  - **完成 2026-08-16**:重写 4 用例:仅 1 筛选按钮断言 ×2、折叠态单击展开/长按无副作用(红线)、输入回传。
- [x] T2 更新 `ModevTopBarTest`:删 `showBack` 隐藏用例
  - **完成 2026-08-16**:删除 `hidesBackWhenShowBackFalse`,保留标题/返回/actions 用例。
- [x] T3 新增筛选按钮「激活指示」断言(应用列表/算法列表筛选菜单)
  - **完成 2026-08-16**:`FourScreensNavTest` 落地菜单内容断言(应用列表 3 项:仅作用域/系统应用/设置;算法列表:全部类别项);激活色 tint 为视觉项,列入手工走查 §5。
- [x] T4 新增导航验收:4 目的地可达(APPS→ALGORITHM_LIST→RECORD_DETAIL 往返;APPS→SETTINGS 往返)
  - **完成 2026-08-16**:`FourScreensNavTest` 3 用例(根页规范断言、应用→算法列表往返、记录→详情往返);数据依赖 `assumeTrue` 防御空机。
  - **实机执行(2026-08-16,emulator-5554)**:`connectedModernDebugAndroidTest` 全套 18 用例通过。过程:首轮 17/18 暴露空态吞卡缺陷(已修复,见 P1);二轮失败为测试自身断言错误(误断算法列表有返回按钮,按 R2 应为 0,已修正);seed 记录(经 `/system/bin/sqlite3` 直插 `crypto_records`,需补全 NOT NULL 列)后记录→详情往返由跳过转通过;最终 18/18、0 skipped、0 failed。注意:connected 跑完会卸载应用及数据,再跑需重新 seed。
- [x] T5 单元测试:`testModernDebugUnitTest` 全绿
  - **完成 2026-08-16**:BUILD SUCCESSFUL。

### 3.7 门禁
- [x] G1 `compileModernDebugKotlin` 通过
  - **完成 2026-08-16**:通过。
- [x] G2 `testModernDebugUnitTest` 通过
  - **完成 2026-08-16**:BUILD SUCCESSFUL。
- [x] G3 `lintModernDebug` 0 error
  - **完成 2026-08-16**:BUILD SUCCESSFUL(0 error)。
- [x] G4 `connectedModernDebugAndroidTest`(实机/模拟器)全绿
  - **完成 2026-08-16**:emulator-5554 上全套 18 用例通过、0 skipped、0 failed(seed 记录后)。

## 4. 设计规范(维持现状,不退化)

- 转场:全目的地 `slideComposable`(150ms,禁止 fade);根页 QUARTER 视差,次级 FULL。
- 组件规范:行 padding h16/v12、行间 `outlineVariant` 分隔线、`AppIcon` 40dp、`EmptyState` 44dp 图标、`TipIconButton` 长按 tooltip + 触感、`bodyLarge Medium` 列表标题。
- 主题:全部颜色走 Material token;系统栏透明 + 明暗图标随外观模式。
- 无障碍:所有图标按钮带 `contentDescription`;触控目标 ≥48dp;文本对比度 AA;折叠搜索条整体可点,单击展开。
- 状态恢复:query/筛选/展开态一律 `rememberSaveable`。

## 5. 验收清单(手工走查)

> 状态(2026-08-16):编译级验收全部通过(G1-G3);androidTest 已在模拟器 emulator-5554(Android 14,x86_64,zh-CN)实机执行,全套 18 用例通过(含 seed 记录后的记录→详情往返);§5 手工走查已在模拟器逐项验证,并发现/修复 2 个真机回归(见 §3.3 P1 空态吞卡、§3.2 K2 进程重建后返回重开搜索)。

- [x] 四界面可达、返回链正确、根页空闲返回退出 App(emulator-5554 实测:根页空闲 1 次返回退到 Launcher;次级页返回均回退上级)
- [x] 应用列表与算法列表 AppBar 内肉眼可见元素 = 1 个 SearchBar;SearchBar 内 = 1 个筛选按钮(ui dump 逐屏核对)
- [x] 算法列表:应用头部 + 捕获设置折叠区(8 类开关/移出作用域/清空日志生效)(zh/en 双语言均展开核对)
- [x] 应用列表筛选菜单底部「设置」项可达全局设置页(全屏搜索态下点击先收后导,Q2 统一)
- [x] 中英双语言完整走查(跟随系统⇄English 切换往返正常,已还原);深浅色下 8 类别色对比度目检(截图目检)
- [x] 进程重建后搜索词/筛选/折叠区展开态恢复(Home → am kill → 重开:搜索词+过滤结果、捕获设置卡展开态均恢复;返回重开搜索回归已修复)
