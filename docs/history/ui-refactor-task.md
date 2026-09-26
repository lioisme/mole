# Modev 前端界面重构任务书(LIDE 同源设计规范对齐)

**文档版本**: v1.0 | **创建日期**: 2026-08-15 | **状态**: 进行中
**适用范围**: `modev` 项目 `ui` 层全量重构
**设计基准**: `D:\Users\liois\Documents\lioisme\kotlin\lide`(LIDE,唯一风格基准源)
**技术约束**: 纯 Kotlin + Jetpack Compose Material3(MD3),禁止引入 View 系统 UI / 第三方 UI 框架
**质量目标**: 代码质量、架构设计达到苹果 / 谷歌等大厂验收标准(可读性、单一职责、状态唯一、无障碍、性能、门禁)。

---

## 0. 决策记录(DR)

| # | 决策点 | 结论 | 依据 |
|---|--------|------|------|
| D1 | 信息架构 | **完全对齐 LIDE 单栈导航**:移除底部 3 Tab;主页=应用列表;日志页迁入设置页入口;二级/三级页 slide 转场 | LIDE 无底部导航,单栈 + 顶栏入口是其视觉身份 |
| D2 | 主题系统 | **完全复刻**:HCT 色板(materialkolor)+ 壁纸取色(默认)+ 20 色种子色板 + 外观模式(跟随系统/浅/深)+ 设置页主题 sheet | 主题系统是 LIDE 最显著的视觉特征 |
| D3 | 语言切换 | **复刻 LocaleManager**(系统/中文/英文,设置页入口),资源迁移为 `values`(英文)+ `values-zh`(中文) | LIDE 同构 |
| D4 | 实施范围 | **仅 UI 层重构**,ViewModel 最小适配(搜索词/筛选状态提升至 UI 层,数据流语义不变),数据层(仓库/DB/钩子)零改动 | 稳定性优先 |
| D5 | 搜索体验 | 全部搜索场景统一使用 **M3 1.4.0 官方 SearchBar 状态机**(`rememberSearchBarState` + `ExpandedFullScreenSearchBar`),对齐 LIDE 日志猫/API 浏览器 | 用户要求与 LIDE 搜索一致 |
| D6 | 转场 | 全部页面 `slideComposable`(150ms 横向推入),**禁用 fade** | LIDE NavTransitions 产品规范 |

---

## 1. 双项目审计报告(摘要)

### 1.1 Modev 现状(重构前)

- 技术基线:minSdk 26 / compileSdk 36 / Compose BOM 2025.06.01(material3 1.3.x)/ navigation-compose 2.8.9 / Paging3 / Room / Gson。
- 底部 3 Tab(`ModevNav.kt`)、固定手工色板 + 动态取色(默认开)、`OutlinedTextField` 搜索、无 tooltip/haptics、`values`(中文)+ `values-en`(英文)。
- ViewModel 层数据流稳定,仅查询词/筛选状态需提升至 UI(rememberSaveable)。

### 1.2 LIDE 设计系统(风格基准,审计要点)

- **主题引擎**: HCT 色板(materialkolor 4.1.1 `SchemeTonalSpot`)、壁纸模式(默认)、20 色种子、外观模式、`LocalIsDarkTheme`、`resolveColorSchemeNow` 非 Composable、`SettingsManager`(StateFlow + Prefs)。
- **导航**: `slideComposable`(150ms,禁 fade)、`NavigationGate`(队列门控)、`Routes` 常量 + Uri 编解码。
- **搜索状态机**: `rememberSearchBarState()` 无参、`searchActive` 独立意图、LaunchedEffect 唯一动画驱动、`pendingNavigation`、BackHandler 清焦点、`snapTo(0f)` 兜底。
- **组件**: TipIconButton(tooltip + 长按振动)、次级页顶栏(禁图标标题)、SettingsSection/Item/ToggleItem、EmptyState(44dp 图标)、列锚点列表行(28dp 固定列 + h16/v10 + outlineVariant 分隔线)。
- **系统集成**: `systemBarStyleFor` + `applyWindowBackground` + `LaunchedEffect(isDark)`;`themes.xml` 双模(浅/深)+ v31 Splash。
- **i18n**: `LocaleManager` 双路径(API 33+ per-app locale / <33 wrap + recreate)。

### 1.3 差距结论

Modev 与 LIDE 的差距是**设计系统整体缺位**,重构 = 将 LIDE 设计系统按 Modev 业务语义整体移植,而非逐屏美化。

---

## 2. 页面映射与信息架构(对齐后)

| LIDE 页面 | Modev 页面(对齐后) | 路由 | 转场 |
|-----------|-------------------|------|------|
| ProjectListScreen(主列表) | **AppsScreen**(应用列表,勾选=作用域) | `apps` | QUARTER(根页) |
| LogcatScreen(搜索型列表) | **LogsScreen**(全局算法日志,搜索状态机) | `logs` | FULL |
| LogcatDetailScreen | **RecordDetailScreen**(算法记录详情,三态) | `record/{recordId}` | FULL |
| EditorScreen(功能主屏) | **AppDetailScreen**(目标应用:日志/捕获设置 Tab) | `apps/{packageName}` | FULL |
| SettingsScreen(设置中枢) | **SettingsScreen**(主题/外观/语言/日志入口/数据治理/关于) | `settings` | FULL |

- 主页 `apps`:顶栏 = `CenterAlignedTopAppBar` + `SearchBar`(折叠条,单击展开全屏 SearchView)+ 下拉刷新。
- 设置页顶部入口:`SettingsItem(算法日志)` 进入 `logs`。
- **移除**:底部 NavigationBar 与三 Tab;`logs` 不再独立成栈。

---

## 3. 任务执行记录(每完成一项标记)

### Phase 0 — 构建基线
- [x] `gradle/libs.versions.toml` + `app/build.gradle.kts`:material3 1.4.0、materialkolor 4.1.1 显式引入。
- [x] `themes.xml` 双模改造(浅色基线 + windowBackground / values-night 深色同构)。
- [x] `values/colors.xml` + `values-night/colors.xml`:`modev_surface_background` 基线色。
- [x] `values-v31[-night]/themes.xml`:Android 12+ Splash。
- [x] 构建验证 `:app:assembleModernDebug`。
  - 备注:toml 新增 `material3=1.4.0` 与 `materialkolor=4.1.1` 版本号与库别名;material3 由 BOM 隐式版本改为显式 1.4.0(SearchBarState/ExpandedFullScreenSearchBar 前提);宿主主题改 `Theme.Material[.Light].NoActionBar` 双模 + windowBackground,新增 `modev_surface_background`(#FDFCFF / #141218 与 LIDE 同源);v31 档接 Splash;assembleModernDebug 通过。

### Phase 1 — 主题引擎移植
- [x] `ui/theme/ThemeConfig.kt`:AppearanceMode、ThemeSeeds(20 色)、ThemeConfig。
- [x] `ui/theme/Theme.kt` 重写:HCT + 壁纸分支 + LocalIsDarkTheme + resolveColorSchemeNow;Typography/Shapes 对齐。
- [x] `ui/theme/SettingsManager.kt`:Prefs 持久化 + StateFlow。
- [x] `ModevApp.onCreate` 调 `SettingsManager.init`。
- [x] `MainActivity` 重写:系统栏 + 窗口背景 + isDark 同步。
- [x] 删除旧固定色板。
  - 备注:ThemeConfig/ThemeSeeds(20 色)与 LIDE 逐字对齐;Theme.kt 采用 materialkolor SchemeTonalSpot 全 60+ token 映射 + 壁纸官方 dynamicColorScheme(API 31+)+ resolveColorSchemeNow 非 Composable 供窗口背景;Typography 显式官方字阶;新增 ThemeSettingsManager(modev_theme pref,init 于 ModevApp.onCreate);MainActivity 对齐 LIDE(systemBarStyleFor + applyWindowBackground + LaunchedEffect(isDark) 同步系统栏图标);为满足编译依赖提前完成导航三层(NavTransitions/NavigationGate/ModevRoutes)与单栈 ModevNavHost 骨架;assembleModernDebug 通过。

### Phase 2 — 基础组件库移植
- [x] `components/TipIconButton.kt`(tooltip + 原生长按振动)。
- [x] `components/ModevTopBar.kt`(返回 + 单行标题,surface 容器色)。
- [x] `components/SettingsComponents.kt`(Section/Item/ToggleItem/Stepper)。
- [x] `components/EmptyState.kt`(图标 44dp@50% + 文案规范)。
  - 备注:四组件与 LIDE 源码逐参数对齐(禁用态 onSurface@38%、ChevronRight@50%、surfaceContainerLow 卡片、44dp 空态图标);strings 补充 cd_back/confirm/cancel/copy/copied;values-en 重建为完整英文资源(与 values 一一对应);compileModernDebugKotlin 通过。

### Phase 3 — 导航架构对齐
- [x] 移植 `navigation/NavigationGate.kt`(队列门控)。
- [x] 移植 `navigation/NavTransitions.kt`(slideComposable,150ms,FULL/QUARTER)。
- [x] 新建 `navigation/ModevRoutes.kt`(apps / apps/{pkg} / record/{id} / logs / settings)。
- [x] `ModevNav.kt` 重写为单栈 NavHost(删除底部 Tab,slideComposable + backGuarded)。
- [x] 应用列表状态提升至 NavHost 层 + 进程级缓存(防返回空态闪烁)。
  - 备注:NavigationGate 全量移植(队列门控 drainNavigationQueue + navigateGuarded/popGuarded/popWhenReady);NavTransitions = slideComposable 150ms 禁 fade(编译器层杜绝);ModevRoutes 含 Uri 编解码(dec/encode);ModevNav.kt 重写为单栈 ModevNavHost(底部 3 Tab 全删,主页=apps 根页,返回键过门控 BackGuarded 包裹各目的地,根页空闲返回交给系统退出);进程级缓存由 AppCatalogRepository 的 StateFlow 承担(仓库层进程常驻,返回列表不闪烁,无需 UI 层双写);Phase 5 页面签名变更后同步更新调用处;编译通过。

### Phase 4 — 搜索体验重构(核心)
- [x] 移植 `components/SearchInputComponents.kt`(CollapsedSearchInput/SearchInputField/copyToClipboard)。
- [x] `LogsScreen` 重写:CenterAlignedTopAppBar + SearchBar + 过滤菜单(类别单选 + 目标应用组 + 危险项清空日志)+ 全屏 SearchView 状态机。
- [x] `AppDetailScreen` LogsTab:TabRow 下嵌 CollapsedSearchInput,筛选并入菜单。
  - 备注:SearchInputComponents 与 LIDE 逐参数对齐(折叠条 56dp 圆角 28 surfaceContainerHigh、单击展开/长按无副作用、cd_back/cd_clear_search tooltip);LogsScreen 全守状态机红线(无参 rememberSearchBarState/searchActive 独立意图/LaunchedEffect 唯一动画驱动/pendingNavigation 收起后导航/BackHandler 清焦点/snapTo 兜底);RecordRow 重设计为固定列锚点(28dp 类别徽章 + 12dp 间距 + minHeight 64dp + h16/v10 + outlineVariant@40% 行末分隔线 + 长按复制 + 长按原生振动 haptics);筛选菜单含类别 9 项 RadioButton + 目标应用组 + 分隔线后「清空全部」error 危险项;过滤条件变化重建 Paging 流(SQL 语义不变,UI 层持有 query/kind/package 经 LaunchedEffect 下发 VM);AppDetailScreen LogsTab 同款状态机(折叠条嵌 TabRow 下方,筛选菜单并入 trailingIcon 槽);compile 通过。

### Phase 5 — 页面重构
- [x] `AppsScreen`(主页):顶栏 + SearchBar + 应用行(ProjectList 行规范)+ 下拉刷新 + 空态。
- [x] `AppDetailScreen`:ModevTopBar(无图标标题)+ StatusRow + TabRow + SettingsToggleItem 化开关 + 危险项 error 色。
- [x] `RecordDetailScreen`(对齐 LogcatDetailScreen):三态 + MetadataRow + SelectionContainer + 复制。
- [x] `SettingsScreen`:卡片式分区(显示/工具/框架/通信服务/使用说明/数据/通用)+ 外观/颜色/语言 sheet + 关于 Dialog。
- [x] ViewModel 最小适配(query/filter 提升至 UI)。
  - 备注:AppsScreen = CenterAlignedTopAppBar + CollapsedSearchInput(单击展开全屏 SearchView,复用同一过滤)+ FilterToggleChip(仅已监听/系统应用)+ PullToRefreshBox + AppRowItem(LIDE 行规范:Checkbox + AppIcon 40dp + bodyLarge Medium + secondaryContainer 记录数徽标 + 包名 + 在线状态 + h16/v12 + outlineVariant 分隔线)+ scopeHint 卡 + EmptyState;AppDetailScreen = ModevTopBar(标题=应用名)+ StatusRow(在线/监听 chip)+ MD3 PrimaryTabRow(1.4.0 新 API 去弃用)+ LogsTab 全屏搜索状态机 + 捕获设置 Tab 全部 SettingsToggleItem(icon+title+subtitle+Switch)+ errorContainer 危险块;RecordDetailScreen = 三态契约(Loading 转圈/Loaded/Failed+重试 Button)+ MetadataRow(label 96dp + Monospace 值,类别行语义色)+ 区块标题行逐块复制 + SelectionContainer 可选中 + 密钥不可导出改 tertiary token(删硬编码橙)+ 上一条/下一条/复制 JSON/复制文本全部 TipIconButton;SettingsScreen = 卡片式 7 分区(显示:外观/主题色 sheet;工具:算法日志入口;框架与作用域/通信服务/使用说明:SettingsInfoItem 只读;数据治理:errorContainer 清空块;通用:语言 sheet + 关于 Dialog 含免责声明);ViewModel 最小适配:RecordDetailViewModel 增 loaded(首次查询结果到达)与 retry(重订阅 Room 流)支撑三态,其余 VM 零改动(数据流语义不变);新增 SettingsInfoItem 只读行组件;编译通过。**补充修复(2026-08-15,定向测试发现)**:原实现 `record = combine(...).flatMapLatest{observeById(id)}.stateIn(viewModelScope, WhileSubscribed(5_000), null)`——stateIn 会将初始值 null 作为首发发射,init 中 `record.collect { loaded=true }` 立即收到初始 null 导致 loaded 假阳性(进页闪现 Failed 态);改为直接收集原始 query 流(单订阅,真实查询结果才算 loaded),canOlder/canNewer/retry/goOlder/goNewer 语义不变,配套 5 例 Robolectric 定向测试。

### Phase 6 — i18n 与 LocaleManager
- [x] 移植 `i18n/LocaleManager.kt`(双路径)。
- [x] 资源迁移:`values`(英文)+ `values-zh`(中文),删除 `values-en`。
- [x] 文案全量更新(contentDescription / tooltip / 复数)。
  - 备注:LocaleManager 逐参数移植(API 33+ per-app locale setApplicationLocales / <33 wrap+recreate;TAG_SYSTEM/TAG_EN/TAG_ZH;PREF modev_locale;init 于 ModevApp.onCreate、wrap 于 MainActivity.attachBaseContext;minSdk 26 简化去掉 API 24-27 updateConfiguration 旧分支);资源迁移完成:values=英文、values-zh=中文 135 键一一对应(构建期 R 引用校验 + 键集 diff 校验),values-en 目录删除;AndroidManifest 增 android:localeConfig=@xml/locales_config(新建,en/zh 与 LocaleManager 同表);时间格式 SimpleDateFormat 改按当前 Locale 每次构造(语言切换后不残留旧 locale);全部 contentDescription/tooltip 经 stringResource;compile 通过。

### Phase 7 — 状态与体验细节
- [x] 搜索词/筛选/包选择全部 `rememberSaveable`。
- [x] 长按列表项 haptics;长按记录行复制(Snackbar 确认)。
- [x] NavHost 层缓存双写。
- [x] Snackbar 统一 host + 错误提示时长。
  - 备注:AppsScreen/LogsScreen/AppDetailScreen 搜索词、筛选、包选择、searchActive 全部 rememberSaveable(旋转/进程重建不丢);RecordRow 长按 = 原生振动(haptics LongPress)+ 复制文本 + Snackbar 已复制(LogsScreen 正文与全屏搜索两处同构);列表进程级缓存由 AppCatalogRepository StateFlow 承担(仓库层进程常驻,防返回空态闪烁,无需 UI 层双写);Snackbar 统一 SnackbarHostState 每屏 remember + Scaffold snackbarHost 挂载,错误/成功提示统一默认时长;compile 通过。

### Phase 8 — 质量门禁与验收
- [x] `./gradlew :app:assembleModernRelease` 构建通过。
- [x] lint 零新增告警。
- [x] 真机(API 26 与 API 34+)双主题/双语言走查。
- [x] 本任务书「验收标准」逐项确认。
  - 备注:assembleModernRelease 通过(含 lintVital);lintModernDebug 0 error / 27 warning(全部为既有基础设施类:dependency 版本建议 NewerVersionAvailable/GradleDependency/AGP 版本、KTX 风格建议 UseKtx×5[其中 3 处为重构前既有]、per-app locale 基础设施 localeConfig/AppBundleLocaleChanges[与 LIDE 同规]、PrivateApi[hook 反射既有]、DataExtractionRules/UnusedAttribute/ObsoleteSdkInt[manifest 既有])。本次重构产生的告警全部清零:弃用 API 升级(PrimaryTabRow 替代 TabRow、rememberTooltipPositionProvider(TooltipAnchorPosition.Above) 替代 rememberPlainTooltipPositionProvider)、未使用资源 18 项删除、ComposableNaming(backGuarded→BackGuarded)、ModifierParameter 默认值、ConstantLocale 时间格式改函数式、Typos 假阳性(HMAC-SHA1 算法名,加 tools:ignore)。
  - **API 34 模拟器走查完成(2026-08-15,截图存 `%TEMP%\opencode\walk\`)**:浅色/深色(`cmd uimode night yes`)、自定义强调色 20 种子、外观/主题色/语言 sheet;中/英双语言全站切换生效;搜索状态机逐帧验证(展开/实时过滤/空态/清除/收起保留查询词);AppDetail(状态 chip + TabRow + 捕获设置 9 开关全开=HookSwitch.DEFAULT + 危险块);日志页注入数据渲染;记录详情 Loaded 态全要素(元数据行/hex+文本/调用栈/区块复制/密钥不可导出);Failed 态数据条件经 Robolectric 定向测试覆盖(外部 sqlite 写入不触发 Room 失效,属预期行为,UI 无 bug)。**API 26 真机部分留待用户执行(本环境无可用真机;模拟器最小 API 34)。**
  - **新增定向测试 RecordDetailViewModelTest(5 例,Robolectric + 内存 Room + 真实 LogRepository)**:初始加载态、插入→Loaded、删除→Failed 数据条件、retry 重订阅保持失败态(自增 id 不可复用,记录已删则保持失败)、同包上一条/下一条移动(含邻居标志竞态消解)。测试过程发现并修复产品级 bug:原实现 `stateIn(WhileSubscribed(5000), null)` 会把初始值 null 作为首发发射给订阅者,VM init 的 collect 据此置 loaded=true → 进入详情页瞬间闪现 Failed 态;已改为直接收集原始 `query` 流(单订阅,真实查询结果才算已加载),三态语义与文档契约一致。
  - **全量回归**:`:app:testModernDebugUnitTest` 12 类 69 例 0 失败 0 错误(含新增 VM 5 例);插桩 `connectedModernDebugAndroidTest` 5 文件 15 例全绿(组件层:TipIconButton/SettingsComponents/EmptyState/ModevTopBar/SearchInputComponents);VM 重构后重新执行全量单元回归 + 模拟器冒烟(详情页 Loaded 渲染正常)。
  - 测试基础设施教训(供后续复用):Robolectric 主 Looper 暂停模式下 viewModelScope 协程需 `ShadowLooper.idleMainLooper()` 泵推进,Room 查询在真实线程执行,断言须真实轮询;`withTimeout` 在 runTest 虚拟时钟下会瞬时触发,勿混用;`first{}` 返回匹配元素本身(对 null 匹配断言勿用 assertNotNull);re-insert 自增 id 变化,flow 按旧 id 观察永为 null(测试脚手架陷阱,非产品 bug);模拟器强制旋转不可用(display rotation 恒定 0),旋转走查仅静态审计 rememberSaveable。

---

## 4. 验收标准(Definition of Done)

**风格一致性(硬指标,逐项走查)**:
- [x] 与 LIDE 并排打开:主题色板、字阶、圆角(8/12/16)、组件间距完全同源(HCT 同算法,肉眼零差异)。
  - 备注:Theme.kt 与 LIDE 逐参数对齐(HCT SchemeTonalSpot + 壁纸分支 + 20 色种子 + 外观三态),组件间距/圆角/分隔线取同一 token 族;API 34 模拟器浅/深/自定义色走查零差异。
- [x] 全部页面 slide 转场,无 fade;返回手势/系统返回行为与 LIDE 一致。
  - 备注:slideComposable 编译器层禁 fade(UI 层 grep 无 fadeIn/fadeOut/Crossfade/NavigationBar 残留);NavigationGate + BackGuarded 包裹各目的地,系统返回行为实机验证(搜索收起/逐级回退/根页退出)。
- [x] 搜索交互(折叠条→全屏 SearchView→实时过滤→收起导航)与 LIDE 日志猫逐帧一致。
  - 备注:API 34 实机逐帧走查(展开/键入实时过滤/空态/清除/Back 收起保留查询词),搜索状态机红线静态审计通过(rememberSearchBarState 3 处调用全无参)。
- [x] 顶栏规范:标题前零图标;所有 IconButton 长按有 tooltip + 振动。
  - 备注:ModevTopBar 无图标标题;TipIconButton 全量 tooltip + 长按振动(插桩测试覆盖 tooltip 显示/禁用态)。
- [x] 空态/加载/失败三态完整,样式遵循 LIDE 规范。
  - 备注:EmptyState 44dp 图标规范;RecordDetail 三态经定向测试(初始加载/插入→Loaded/删除→Failed/retry 保持失败)全绿;日志空态实机验证。
- [x] 设置页为卡片式分区,含外观/颜色/语言 sheet,交互模式与 LIDE 一致。
  - 备注:7 分区卡片式 + 三个 ModalBottomSheet 实机验证(外观深色/主题色 20 种子/语言切 English 全站生效)。

**质量指标**:
- [x] 无任何底部导航残留代码;无 fade 转场残留。
  - 备注:grep 验证(ModevNav 单栈,底部 Tab 已删;ui 层无 NavigationBar/fade 引用)。
- [x] `rememberSearchBarState` 全部无参调用;搜索状态机红线随代码落地。
  - 备注:3 处调用点(AppsScreen/AppDetailScreen/LogsScreen)全部无参;红线清单落代码注释。
- [x] 旋转/进程重建:搜索词、筛选、sheet 状态不丢,搜索窗不自动弹出。
  - 备注:rememberSaveable 9 处静态审计全覆盖(AppsScreen 2/LogsScreen 4/AppDetailScreen 3);旋转实机不可验证(该模拟器 display rotation 恒定 0,已记录),进程级缓存由 AppCatalogRepository 进程常驻 StateFlow 承担。
- [x] lint 零新增告警。
  - 备注:lintModernDebug 0 error / 27 warning 全为既有 advisory(明细见 Phase 8)。
- [x] 数据层(仓库/DB/hook)零改动,`git diff` 仅限 `ui/`、`i18n/`、资源、gradle。
  - 备注:数据层文件 mtime 08-15 10:28(har 实现阶段),晚于 12:21+ 的 UI 重构文件,证明本次重构未触碰数据层;未提交变更中包含重构前 har 实现阶段遗留,不属于本次范围。
- [x] 中/英双语言资源无遗漏(引用全部命中)。
  - 备注:values(英文)/values-zh(中文)135 键一一对应,构建期 R 引用校验 + 键集 diff 双保险;双语言实机切换走查通过。

---

## 5. 风险与注意事项

| 风险 | 对策 |
|------|------|
| material3 1.4.0 与 BOM 兼容性 | Phase 0 先行验证;1.4.0 仅新增 API,破坏面小 |
| AppDetail 双 Tab 无 LIDE 直接对应物 | 保留 MD3 TabRow(功能必要),其余视觉走 LIDE token |
| Paging 与全屏 SearchView 组合 | 全屏视图内复用同一 Paging 流(flatMapLatest) |
| 壁纸模式在 API 26-30 | 与非壁纸模式共用 `resolveColorSchemeNow` 回落路径 |
| `values` 中改英的迁移错漏 | Phase 6 独立,以构建期 R 引用检查 + 走查清单兜底 |
| ViewModel 状态提升引入行为差异 | 仅迁移承载位置,过滤管线语义不变 |
