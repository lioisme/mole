# V50 交付物 2 / 全量审计整改任务计划(主文档)

> **执行状态(V50 第一轮自驱动,2026-09-24)**:WP-A 的 S1/S3/S4 已落码并带反证,
> 孤儿数 27+1 → **0**;需求 5 的入口/元数据契约门禁已落地;设备一次性全量验收见
> **交付物 4 `docs/v50-test-ledger.md`**。
> 逐条完成登记在 `docs/v50-capability-inventory.md` §7;**本文各表保留原计划口径,
> 完成项以 ✅ 前缀标注,未标记者仍是待办**(禁把计划当进度)。

> 生成日期:2026-09-24。基于 6 路并行全量审计 + 本次门禁实跑 + 本次对 3 项最关键结论的源码级复核。
> 配套:交付物 1 `docs/v50-capability-inventory.md`(能力清单与命名)、
> 交付物 3 `docs/v50-xposed-api-compat.md`(Xposed API 兼容分析与方案)。
>
> **本轮的定性**:今日**无红色门禁**(`:app:testModernDebugUnitTest --rerun --no-build-cache`
> = 1174 用例 / 0 失败;`:app:lintModernRelease` = 43 warning / 1 hint / 0 error，与基线逐字相符)。
> 但审计抓出 **4 条「绿着的失效」**——门禁全绿而功能实际不工作。这类才是本仓最需要验收标准的地方，
> 也是本计划把 P0 全数判给它们而非给外观重构的原因。

---

## 0. 证据基线(本轮实测，后续每轮须复跑对账)

## 0.2 本轮执行实况(2026-09-24 自驱动第一轮)

| WP | 内容 | 状态 | 证据 |
|---|---|---|---|
| **WP-A** | 4 条静默失效 + MCP 门槛 | **A1/A2(部分)/A3/A4 ✅,A5 ⬜** | 交付物 4 §0 R2–R4;反证轮 R3 |
| **WP-B** | 性能 | **⬜ 全部未动**(B0 度量门禁是前置,未落地前不动 B1–B5) | 见 §2 取舍 |
| **WP-C** | 组件工厂化 12 项 | **⬜ 全部未动** | 见 §3 |
| **WP-D** | 画像逐参数可选 8 项行级入口 | **⬜ 全部未动**(依赖 A1 已满足,可下轮即刻开工) | A1 是它的前置 |
| **WP-E** | legacy/LSPosed | **E3 ✅ / E5 ✅ / E1 ⬜(结论改判,见下)/ E2 ⬜ 需设备档授权 / E4 ⬜ / E6 ⬜** | `FrameworkEntrypointContractTest` |
| **WP-F** | 文档与仓库卫生 | **F1 ✅(AGENTS 8 处 + README 2 处 + 代码内注释 4 处)/ F2 ⬜ / F3 ⬜ / F4 ⬜ / F5 ✅** | 交付物 1 §6 逐条 |
| 需求 2「无孤儿」 | 门禁 + 清零 | **✅ 27+1 → 0** | `FeatureOrphanGateTest` O1–O4 全绿 |

> **E1 的结论改判(重要,别再照原计划做)**:原计划写「`PlatformContractParityTest` 双适配器对账」。
> 实测**这条路在 JVM 走不通**:API 82 桩 jar 的 `XC_MethodHook$MethodHookParam` 构造器是**包私有**,
> 且 `setResult/getResult` 等桩方法体一律抛 `Stub!` ⇒ 既不能构造也不能子类化(类是 final)。
> libxposed 侧同理需要 mock `Chain`。**适配器语义的唯一真判据是设备档**(交付物 3 E2),
> JVM 侧本轮改为钉「契约面 + 元数据 + 类加载安全」(`FrameworkEntrypointContractTest` 6 条),
> 这是可达的最大机器面,不是退让。
>
> **WP-B/WP-C 未做的理由(不是遗漏)**:本轮三条硬需求(兼容、无孤儿、全量测试)都需要
> **设备矩阵作为出口判据**;性能与组件层的改动会大面积触碰同一批文件,把它们塞进同一轮
> 会让「一次性全量测试通过」的归因变得不可能(改了 30 个 UI 文件后再跑矩阵,
> 任何一条红都说不清是功能改动还是 UI 改动引入的)。顺序纪律 = 先让功能真生效,再动呈现与性能。



| 项 | 值 | 备注 |
|---|---|---|
| JVM 单测 | **1174 用例 / 0 失败 / 0 跳过**(165 结果 XML) | `--rerun --no-build-cache`(首跑 11s 是 UP-TO-DATE 假绿，已强制真跑复核) |
| lint | 43 warning / 1 hint / 0 error | 近期提交只触及 `docs/test-reports/*.json`，净新增 0 成立 |
| legacy 变体编译 | **BUILD SUCCESSFUL**，arm64-v8a + x86_64 debug APK 产出 | 但**从未上设备**(见 WP-E) |
| 结构门禁 | 28 个源码扫描/对账测试全绿 | 清单见交付物 1 §0 与本文 §7 |
| 能力计数 | 89 = LISTEN 44 + FEATURE 45;ProfileField 41 / ProfileKind 20 / DeviceEnvKind 29 / 已接管 8 族;MCP 50;Room 15;四语 860+13 | AGENTS.md 多组数字过期(更正表见交付物 1 §6) |

### 0.1 已实锤的 4 条「静默失效」(源码级二次复核，非 agent 转述)

| id | 失效 | 我复核到的事实 |
|---|---|---|
| **S1** | **每应用画像微调(V49 R-2)不落盘不回读** | `AppListenOverride` 里 `profileOverrides` 只有 4 处站点:构造器参数 `:89`、`isDefault :142`、`deviationCount :177`、`clearDomain :227`。**`fromJson`(`:296-340`)无该字段解析臂， companion 里无 `FIELD_PROFILE_OVERRIDES` 常量** ⇒ 磁盘/远程镜像里的该键被读成 null。`toJson` = `gson.toJson(this)` 反射写出 ⇒ **写得出、读不回**。全部 21 个消费方(`SettingsRepository.kt:298`、`PerAppFeatureSettingsScreen.kt:197/235/241`、`FeatureConfigTools.kt:136-160`)都在同一进程内存 StateFlow 上工作 ⇒ 会话内一切正常，**重启即丢，注入侧磁盘兜底恒无此项**。`AppListenOverrideJsonTest`(233 行)对 `spoofOverrides`/`resolvedProfile`/行为项都有往返用例，**唯独 `profileOverrides` 零命中** |
| **S2** | **MCP 默认开 + 默认零认证，且 Agent 可自行翻公网** | `MCP_ENABLED` 默认 `true`(`SettingsRepository.kt:565`)、`tokenAuthEnabled` 默认 `false`(`:588`)⇒ 出厂态 = 127.0.0.1:1510 全读全写零凭证(同设备任意进程)。且 `server_set_external_access` 是无 `confirm` 的工具(`ServerTools.kt:53-69`)⇒ Agent 可**自行**把绑定改到 0.0.0.0，把「用户显式选择」的语义(#19)变成「Agent 显式选择」 |
| **S3** | **一条死行 + 一条死钩，都绿着** | ① `CryptoKind.WEB_CRYPTO`:taxonomy 登记、UI 有开关行、wire 有位、`anyCryptoEnabled` 受它影响，`grep -rn WEB_CRYPTO app/src/main` 在 `core/model`/`ui` 之外**零命中** ⇒ 勾选后开关亮、永不出记录。② `DetectionMonitorHooker.BUILD_FIELDS = ["getFINGERPRINT","getMODEL","getPRODUCT","getMANUFACTURER"]`(`:245-247`)经 `clazz.getMethod(field)`(`:209`)解析 —— `android.os.Build` 这些是 public static final **字段，无同名 getter** ⇒ 4 个全部 `NoSuchMethodException` ⇒ `?: continue` 咽掉 ⇒ **Build 字段检测监控自始未装钩**，且该函数不走 `unresolvedHookPoints`，「列出的钩点必须全部解析得到」的门禁看不见它 |
| **S4** | **画像在非常规通路上整体空** | `resolvedProfile`(磁盘兜底/TCP 快照通道)有解析臂(`fromJson:323`)、有读取(`EffectiveSwitch.kt:79`)，但**全仓 main 源零写入方** ⇒ 不经 TCP 时 `feature.deviceProfile` 恒 EMPTY。叠加 S1，画像下发只有「宿主活连接」一条独木桥 |

> **为什么这四条是 P0**:它们都满足同一个模式 —— *实现存在、测试存在、门禁绿、用户拿到的是假功能*。
> 本仓 AGENTS 已经为这个模式记了三次实锤(`RecordSink` 未 `start()`、`readText` 扩展函数、@hide 方法)，
> 四条里 S1/S3/S4 是**同一病族的第四次到第七次发作**。因此每条 P0 的完成判据都包含
> 「**一条会因该缺陷而红的测试**」，而不是「实现完成」。

---

## 1. WP-A 功能正确性 P0(先做，全部是 bug 不是重构)

> **V50 第一轮实况**:A1 ✅ / A2 ✅(仅 confirm 闸;默认值取舍见下)/ A3 ✅ / A4 ✅ / A5 ⬜ 未做。
> **A2 的取舍(为什么没翻 `MCP_ENABLED` 默认值)**:回环无认证是 V40 D-4 的**显式决策**
> (「0.0.0.0 无认证 = 用户显式选择」,风险语义由令牌页状态行与确认弹窗承载),
> 翻默认值会与既有契约 + 整套 MCP 交付门禁(`24-run-mcp.ps1` 42 子断言)对冲,属一次契约变更而非 bug 修复。
> 本轮只修**真正破契约的那一半** —— Agent 可单方把绑定翻到 0.0.0.0,那是 Agent 的选择、不是用户的;
> 已给 `server_set_external_access` 补 `confirm` 必填(与其余 6 个破坏性工具同门槛)。

| # | 任务 | 方案(取最省的一刀) | 触碰文件 | 验收判据(必须先红后绿) |
|---|---|---|---|---|
| **A1** | 修 S1:画像微调持久化闭环 | `fromJson` 补 `profileOverrides = stringMapOf(obj, FIELD_PROFILE_OVERRIDES)` + 常量一行(`stringMapOf` 已存在，V37 为 `spoofOverrides` 写的，**直接复用**，不写新解码器) | `AppListenOverride.kt`、`AppListenOverrideJsonTest.kt` | 新用例 `profile_overrides_roundtrip_symmetric`:写→`toJson`→`fromJson`→等值。**同轮补一条通用门禁**:`ProfileFieldOverridePersistenceTest` 反射取 `AppListenOverride` 全部属性名，与 `fromJson` 源码内出现的 `FIELD_*` 集对账，缺失即红且豁免须带理由(镜像 `RecordWriteModelParityTest` P1 的做法) —— 这一条让「加字段忘加解析臂」永久不可能 |
| **A2** | 修 S2:MCP 默认态与外网写面 | 两处最小改动:①`server_set_external_access` 补 `confirm: true` 必填(注册表已强制 `additionalProperties` 与 required，一行声明 + handler 一次 `!= true` 判定，与既有 6 个危险工具同形态);②`MCP_ENABLED` 默认改 `false`(用户第一次打开服务页显式启用)。`tokenAuthEnabled` 默认 false **可保留**(回环 + 未显式开启 = 本机自用是合理默认)，但要在诊断页显式渲染「MCP 正在无认证监听 127.0.0.1」 | `ServerTools.kt`、`SettingsRepository.kt`、`DiagnosticsScreen`/`DiagnosticsReport` | `McpToolsTest` 加「缺 confirm = -32602」用例(既有 6 工具已有先例可抄);默认值变更走 `McpToolParityTest` 之外的仓库默认值断言 |
| **A3** | 修 S3-②:Build 字段监控死钩 | 二选一(倾向前者，改动小且语义正确):把 `BUILD_FIELDS` 改成**读静态字段**(`getStaticField` + `Field.get(null)`)，或整段删除并登记「Build 字段监控已由画像/条件档承担」。**同时**把 `monitorBuildFields` 纳入 `unresolvedHookPoints`(它现在藏在私有 `runCatching` 里) | `DetectionMonitorHooker.kt`、`DeviceEnvBaseHooker.kt` | 新用例「`BUILD_FIELDS` 列出的每个钩点都必须解析得到」——即本仓已为 `CellIdentity`/`readText` 两案定下的收口形态，照抄 |
| **A4** | 修 S3-①:WEB_CRYPTO 死行退役 | 删 `CryptoKind.WEB_CRYPTO` + `HookSwitch.webView` 位 + `EffectiveSwitch.readKind/applyKinds` 双臂 + `kind_webview` 键(四语) + `RecordRow` 分支。**不新增替代钩子**(WebCrypto 已由 `WebViewKind.JS_CRYPTO` 真实覆盖，交付物 1 §5.1 N1) | `CryptoKind.kt`、`HookSwitch.kt`、`EffectiveSwitch.kt`、`CategoryTaxonomy.kt`、`RecordRow.kt`、四语 `strings.xml` | 五处同步(#20/#22 的十站链)由既有穷尽 `when` + `CapabilityMapTest` 当场拦;计数快照 89→88/44→43 同步更新 |
| **A5** | 声明面即生效面补洞(MCP schema) | ①`profileFieldEntries`/`kindEntries`/`fields`/`overridesAdd` 四个嵌套 object 补内部键 schema(handler 已在守卫，只是没广告);②`pageSize` 越界从 `coerceIn` 静默钳制改硬拒(与 #30②「不钳制」同口径) | `ListenConfigTools.kt`、`FeatureConfigTools.kt`、`SpoofProfileTools.kt`、`RecordTools.kt` | 一条负向用例:未知内层键 → `-32602` |

**A1 是唯一「改了可能撕裂 KSP 增量缓存」风险的项**(AGENTS 记过 OOM 撕裂 `kspCaches`)——
按计划排在批量改动最前，跑一次全绿即锁定基线。

---

## 2. WP-B 性能(需求 3)—— 先加度量，再动代码

**顺序纪律**:B0 未完成前，任何「已优化」的措辞都不准写进验收文档。
理由:本仓 androidTest 29 个文件**全部是功能/几何断言，零帧计数** —— 没有度量就没有 P0/P1 判据。

| # | 任务 | 方案 | 判据 |
|---|---|---|---|
| **B0** | **度量门禁落地** | ①新建 `:macrobenchmark` 模块(AOSP 模板):`BaselineProfileGenerator` + 3 个滚动/进入 CUJ(应用列表滚动、记录列表滚动含后台灌流 50 rec/s、详情进入);②app debug 接 `androidx.metrics:metrics-performance`(JankStats)一行 | jank 率 < 1% 入门禁脚本;Baseline Profile 同时白捡首帧收益(minSdk 26 ⇒ API<28 无 Profile Installer，需登记) |
| **B1** | **F1 分页取窗(P0，唯一真正的滑动卡顿嫌疑)** | 现状:`LogRepository.kt:343-352` 把 `limit = PAGING_WINDOW_CAP`(= `MAX_RECORDS` = **20,000**，`:525/:536`)硬写进自建 SQL 交给 `@RawQuery` 的 `LimitOffsetPagingSource`,而 Room 对该查询**原样执行** ⇒ 每次 load/失效刷新物化**整个 2 万行窗口**(cursor→20k entity→20k RowModel→Paging diff)，而活流落库节奏 32 条/200ms ⇒ 表每动一次一轮全窗刷新。**注意 AGENTS #14「外层 loadSize 由 Room 动态注入」这句对 raw query 不成立**(V20 固定小 limit 的死锁现象本身就证明 LIMIT 按原样生效)→ 文档一并更正。改法:自定义 `PagingSource`，计数直接用既有 `RecordQueryFactory.count()`，取页用 `sliceRows(rows(filter, limit = loadSize, offset = key))` —— **谓词单源契约 #9 不破** | 扩 `RecordPagingProbeTest`:单次 `load()` 返回行数 ≤ 2×pageSize(今天该断言必红);设备档 B0 复测滚动 jank |
| **B2** | **F2 详情 markdown 双解析** | 每个区块首帧解析同一文档**两遍**:`rememberMarkdownState(immediate=true)` 一遍 + 围栏索引自己 `MarkdownParser(...).buildMarkdownTreeFromString` 一遍(`RecordMarkdownContent.kt:216-222`、`RecordDetailSections.kt:37`)。改法:一次解析双消费(自建 AST 同时喂两边)，围栏索引改从已产文档派生 | 详情进入帧「解析总耗时」< 8ms(Trace 区间);`AnchorScrollRoboTest` 不破 |
| **B3** | **F4 release 未开 R8** | `isMinifyEnabled = false`(`app/build.gradle.kts:63`)⇒ `material-icons-extended` 全量类入 APK、启动期零裁剪。改法:开 R8 + 精确 keep 族(Gson 反射模型、Room 实体、`assets/xposed_init`/`java_init.list` 引用的两个入口类、Highlights 资源)。**这是本轮风险最高的性能改动**(注入入口类被 shrink 掉 = 静默不注入)，故排在 B0 度量之后、并强制跑注入矩阵 | APK 体积/方法数对比;`23-run-smoke` + 注入矩阵全绿;**装机步 MD5 对账不破**(AGENTS 缓存陈旧 dex 坑) |
| **B4** | F5/F7 搜索与详情观测面 | 搜索加 `debounce(250ms)`(今天每键 2 次 24 列 `%LIKE%` 全扫，且与活流失效刷新叠加);详情 `observeById` 改一次性 `first()` + 仅在 `recordId` 变化时重算 neighbor | 20k 种子连打 10 字符，语句执行数 ≤ 3;详情驻留 + 100 rec/s，每秒语句执行 < 2 |
| **B5** | F6/F8–F13 卫生(打包做，不单列轮次) | 两个 sealed 接口加 `@Stable`(`RowModel.kt:31`、`RecordDetailModel.kt:19`)、三个配置模型加 `@Immutable`;21 处整屏 `verticalScroll` 中行数 > 40 的设置页改 LazyColumn;`ProfileFieldControls.kt:246-248` 每键重跑 `TimeZone.getAvailableIDs()` + 600 个 option 构建 → `remember`;组头记忆化两 `HashMap` 加上限;3 处 `collectAsState` → `collectAsStateWithLifecycle`;Coil `DrawableResult` 是否入内存缓存**先做实验再定档**(F3) | compose-compiler reports 临时开启后录改前/改后重组计数;grep 归零 |

**明确不做**:引入 `enableStrongSkipping`(Kotlin **2.2.20** 起默认开启，全仓无禁用参数 = 无需加)、
stability config file(本仓无 RxJava，`@Immutable` 直接标注更便宜)、以及任何「性能优化」名义的重写。

---

## 3. WP-C 组件库工厂化(需求 2)

### 3.1 先定「胜者」—— 用户钦定的那一个样式

参考实现 = 数据管理页「清空全部记录」(`DataSettingsScreen.kt:148-158` 调 `ModevConfirmDialog`
`ModevDialogs.kt:39-77`):居中 `AlertDialog` + `containerColor = surfaceContainerHigh` +
**零 icon** + 标题/正文裸 `Text` + 确认/取消都是 `TextButton`(destructive 染 `error`，否则 `primary`)。
**本轮统一的定义不是「所有弹层变一个形状」，而是「同一件事只有一个函数可调用」**——
确认类归居中框(含图标、含输入)本来就是两条被禁止的旧轨(#4、#27 已写明)。

### 3.2 「工厂化」的四件套(缺一即不算工厂)

每个原语必须同时具备:①**唯一组件文件**;②**testTag 形参**(跨四语文本锚定不可靠);
③**一条源码扫描门禁**(禁第二实现，镜像 `DialogConventionTest` 的写法);
④**从既有最佳站点抽出**(不是新写一个理想件 —— 新写件与既有件并存 = 第 3 轨)。

| # | 原语 | 胜者与理由 | 收编对象 | 门禁 | 标记 |
|---|---|---|---|---|---|
| **C1** | 只读值弹层 | `ModevValueDialog`(`ModevDialogs.kt:93`，与参考框同文件同容器色同按钮族) | `DiagnosticsDetailSheet`(`DiagnosticsScreen.kt:363-404`，`ModevSheet` + `SelectionContainer` 自拼) | `DialogConventionTest` 加 R4:「值 + 复制」形态只允许出现在 `ModevDialogs.kt` | 违规(B1) |
| **C2** | 确认框锚点 | 保持 `ModevConfirmDialog`;**11 站点统一补 `confirmTestTag`**(参考那站恰恰缺) | 7 个缺锚点站点 | `DialogAnchorsTest`:每个 `ModevConfirmDialog(` 实参块必含 `confirmTestTag =` | 违规 |
| **C3** | 空态 | **新** `ModevEmptyState(icon, title, hint?, modifier)`,直接复刻既有形态(`size(56.dp)` + `CircleShape` + `emptyIconAlpha` 三元组,`EmptyState` 组件 V40 退役后留了 7 份内联) | `AppsScreen.kt:225/341`、`RecordTimelineList.kt:95`、`LicensesScreen.kt:77`、`KeywordLibraryScreen.kt:135`、`McpTokensScreen.kt:453`、`SpoofProfileLibraryScreen.kt:355` | 该三元组只允许 1 文件 | 重复(B6) |
| **C4** | 开关行 | 保 `ToggleListItem`(`PerAppSettingRows.kt:31`，唯一带 `testTag` 形参，最贴 #16 契约) | `SettingsToggleItem`(`SettingsComponents.kt:128`)、`ScopeToggleRow`(`AppMonitorScreen.kt:468`) | `Switch(` 只允许出现在该行组件文件 | 重复 ×3(B2) |
| **C5** | 入口/信息行 | 保 `SettingsInfoItem`(`SettingsComponents.kt:202`，已有 `valueMaxLines/onClick` 扩展点)，补 `valueTone`/`enabled` | `SettingsItem:78`、`OptionRowItem`、`EntryRow`、`CatalogPickerRow`、2 处内联 | 卡片内 `ListItem(` 只允许 `components/*` + `ModevSheetScaffold.kt` | 重复 ×6(B3) |
| **C6** | 危险/添加动作行 | **新** `ModevActionRow(tone, icon, title, supporting?, testTag)`，复刻 `DangerRow`(`DataSettingsScreen.kt:180`) | 5 处(「添加行」两份逐字同构) | `headlineContent` 染 `error/primary` 的 `ListItem` 只允许 1 文件 | 重复 ×5(B4) |
| **C7** | 行容器 + 尾菜单 | **保 `RecordRow` 的容器形态**(全仓唯一满足 V46 R1/C2 契约:`Box(clip(shapes.medium) + background + combinedClickable)` + `ListItem` 转透明 ⇒ 圆角与**可见波纹**兼得)。新 `ModevRowMenu(items)` 收「更多」三项 | `McpTokensScreen.kt:335-439`(Card+透明 ListItem)与 `SpoofProfileLibraryScreen.kt:534-652`(裸 ListItem)两套;其余 23 处 `ListItem` 行无圆角 | `Icons.Filled.MoreVert` 只允许 1 文件;并把 `RecordRowContainerContractTest` 的扫描面从 1 文件扩到「所有可点击行」 | 重复 + 违规(B5、**C-新1**) |
| **C8** | 卡片/页壳 | `SettingsSection` 增可选 `title`(或抽 `ModevCard`);新 `ModevPage` 收 21× `Scaffold(containerColor = surface)` + 8× SnackbarHost 样板 + `UiMessage` 泵(现仅 1 消费者) | 7 份裸 `Card`;19 屏 | `CardDefaults.cardColors(` / `Scaffold(` 各只允许 1 文件 | 重复(B7、B8) |
| **C9** | Tooltip | **现状已合规**:24 个图标动作全走 `TipIconButton`(零裸 `IconButton`)——**不立项重构**，只删 2 个本地适配器(`TooltipAction`、`TipIconButtonRow`)并把 Appbar 动作提为 `ModevAppbarAction` | 2 处 | `IconButton(` 只允许 `TipIconButton.kt`(今日即绿，零成本) | 优化(B11) |
| **C10** | 选择器/筛选/搜索壳 | `ModevChoiceSheet` 加 `leadingIcon`/`trailingCheck` 变体吸收 `IconChoiceRow`;sheet 内嵌套 `ExposedDropdownMenu`(`KeywordLibraryScreen.kt:318`)改回 `ModevChoiceSheet`(弹层套弹层 = AGENTS 坑②遮罩吞点击的成因);`ModevFilterMenu` 收 `KindFilterMenu`/`ScopeFilterMenu`(**其三项复制三遍**);`ModevSearchBarHost` 收折叠/展开两调用点 | 3 + 2 + 2 处 | `selectable(`/`RadioButton(` 的 `onClick` 只允许 sheet 组件文件内为 null(修 **B10 双焦点回归**:`SpoofProfileLibraryScreen.kt:683-691`);`Icons.Default.FilterList` 只允许 1 文件 | 违规+重复 |
| **C11** | token 落地 | B13 全清单换 `Tokens.*`/`shapes.*`/`Dimens.*`;补 `dividerAlpha`/`badgeShape` | `SettingsComponents.kt:112/141/168/238`、`RecordMarkdownContent.kt:401/446`、`FullscreenTextDialog.kt:92`、`LicensesScreen.kt:166`、`RecordRow.kt:327/362`、`AppMonitorScreen.kt:593-609`、26× `Spacer(24.dp)` | 新 `DesignTokenConventionTest` 三扫(`copy(alpha = 0.` / `RoundedCornerShape(` / `Spacer(Modifier.height(<n>.dp))`)，白名单 = `theme/*` + `markdownTypography` | 违规(#25) |
| **C12** | 死支清理 | `ModevConfirmDialog` 的 `destructive = false` 分支**11 站点无一使用** → 要么删该分支，要么给非危险确认(重命名)开真实用例 | `ModevDialogs.kt:46,63-67` | 两分支各 ≥1 站点 | 死码(B14) |

> **明确不做**:#27 既有约定已覆盖对话框/sheet 外壳本体(实测 `AlertDialog(`/`ModalBottomSheet(`
> 外溢数 = **0**)，本轮**不重写** `ModevSheet` 家族，只补它没管住的三轨(只读值/确认/空态)。
> 也**不**为「组件库」这个名字新建 module —— 18 个文件在 `ui/components/` 已是单一出口，
> 缺的是门禁不是目录。

---

## 4. WP-D 画像逐参数自选(需求 4)

**审计结论先行**:「有一个统一的『选择真实机型』选项」这件事的真相是 ——
**画像编辑器与微调页共用同一套行组件 `ProfileFieldControls`，行级弹层选择器已经存在**(枚举/槽位/
可搜列表 + 「改为手动填写」回落),缺的是**8 个字段的行级入口**:它们的候选表在资产里躺着，
但只有画像编辑器里的**成对捷径**(`pickDevice`/`pickCarrier`/`pickAndroidRelease`)能用到。
所以本 WP 是「把已有资产接到已有行上」,不是「新建选择器体系」。

| # | 任务 | 方案 | 判据 |
|---|---|---|---|
| **D1** | 8 字段行级选择器 | `ProfileFieldControls` 的行级弹层按 `ProfileField.kind` 分派到**既有**目录:`BUILD_IDENTITY` 四项 → `DeviceCatalogRepository`(25 品牌/8,075 机型，非 Android 平台已在解码口剔除);`SIM` 两项 → `CarrierCatalogRepository`(1,597 条);`BUILD_VERSION` 两项 → `ProfileValueSets` 的 37 对 release↔SDK。**读口与缓存全复用 `CachedAssetReader`**(词汇表已明文:第三本目录必须复用它，禁再抄 reader) | 逐字段弹层可选 + 读不到资产时 `Unavailable` 上屏点名(**禁**退化成「库里没机型」);`ProfileFieldControls` 现有「排除成对捷径」的注释 `:56-58` 同步删除 |
| **D2** | `ProfileOptionSource` 补 `androidReleases()` | 让 release/SDK 候选表成为**第四个 `optionsFor` 域**(现状:密度档/核数档已是，SDK 只可手打) | `ProfileOptionSourceTest` 逐值断言 + `pickAndroidRelease` 与行级选择器落值对账 |
| **D3** | LANGUAGE 补区域形态 | 校验器允许 `zh-Hans-CN`(`ProfileFieldCatalog.kt:237-240`)而选择器只给裸二字码 → 用 `Locale.getISOCountries` 组合补全(**禁**再手抄第三份清单) | 选择器首项恒留「改为手动填写」 |
| **D4** | MCP 同权(parity 契约) | ①`spoof_profile_catalog` 的 `kind` 增第四/五本目录(option 域候选表);②`profileFieldEntries` 支持成对写(release↔SDK 不可分写，#31⑤);③`randomizeField`/`randomizeAll` 目前**只有 UI 有、MCP 无**(目录已广告 `randomizable` 旗标但无入口)→ 补一个工具或在描述里点名回落 | `UiMcpCapabilityParityTest` R2/R5 归属对账通过 |
| **D5** | 不立项的部分(显式记录) | `product`/`board`/`hardware` **恒不给选择器**(上游不提供这三个值，凑一个就是自造「真实机型从不存在的字段组合」——词汇表红线);开放域 13 项保持「手打 + 形态校验 + 就近随机」 | 现状即终态，写进本文件避免下轮再审 |

---

## 5. WP-E legacy / LSPosed 兼容(需求 5)

**完整分析见交付物 3**。此处只列工作包(推荐方案 = 保留双 APK,补验证与元数据;
否决「单 APK 双入口」:旧框架设备上 `ModevModule` 超类运行时缺失即 `NoClassDefFoundError`):

| # | 任务 | 判据 |
|---|---|---|
| E1 | `PlatformContractParityTest`:同一套钩子语义分别跑在 `LegacyPlatform`(API 82 桩 + 提供 `returnEarly` 的仿真 param)与 `LibxposedPlatform`(mock `Chain`)上 —— 短路判定/异常隔离/广播链可见性/after `setResult` 不丢(V38 回归) | 5 条语义 × 2 适配器全绿;**当前两个适配器引用数 = 0，此包做完前「支持旧 API」这句话没有证据** |
| E2 | legacy 设备验收矩阵(`10-build-install.ps1` 增 `-Variant legacy`;该脚本与 `run-acceptance.ps1:259`、`25-run-v42-stress.ps1:61-68` 现均硬编码 `assembleModernDebug`) | 注入矩阵 + `23-run-smoke` 在 legacy APK 上跑通;设备不可得时写**具名前置**，禁以「门禁绿」代替 |
| E3 | `module.prop` 对齐:modern 补 `id=`/`name=`/`version=`(现状只有 4 行 API 参数)，legacy 补 `targetApiVersion`;description 与实际支持矩阵一致 | JVM 门禁:两变体 `module.prop` 的 `id == applicationId` |
| E4 | 框架能力探测上屏(诊断页一行:当前变体 / 框架是否投递 `XposedService` / 走的是哪个入口) | 只读快照，legacy 分支零类解析 |
| E5 | `checkcast` 纪律门禁:`FrameworkBridge.serviceFlow` 消费方签名禁现 `XposedService`(转型只允许在 `XposedScopeService` 包装点) | `PackageBoundaryTest` R7 或独立测试 |
| E6 | 四项开放问题实测(Vector 是否回落 `xposed_init`、LSPosed 是否因缺 `id` 拒载 modern、LSPosed 现代路径是否向注入进程投递 service、`returnEarly` 在两家实现里是否都存在) | 结论写进交付物 3 §5，替换掉当前「待实测」标注 |

---

## 6. WP-F 契约 / 文档 / 仓库卫生

| # | 任务 | 判据 |
|---|---|---|
| F1 | 交付物 1 §6 的 12 条文档漂移更正(AGENTS 8 处 + `CapabilityMap.kt:4` + `FeatureGroups.kt:4` + `FeatureConfigTools.kt:15` + `DeviceEnvKind.kt:4` + README 2 处) | 数字与门禁实测一致 |
| F2 | **文档快照测试**:AGENTS/README 里的能力计数、Room 版本、i18n 键数与代码派生值对账 | 新增一个 JVM 测试(镜像 `BaselineDataParityTest` 思路)，否则数字永远靠口口相传过期 |
| F3 | 无门禁条款批量补闸:`cachedIn(viewModelScope)`(#14)、禁叠 `navigationBarsPadding`(#4)、markdown 版本锁 0.38.1(#3)、core 零 android(现**无**门禁而 `PackageManagerCompat` 合法 import ⇒ 条款表述自相矛盾，先改文档再落 R7+豁免集)、「MCP 重启恒异步」(#19)、`@Suppress` 允许清单(现 32 处无登记面) | 每个约 30–60 行扫描，模式已有 7 个先例可镜像 |
| F4 | 仓库卫生(**只动规则与归档，不删任何人的工作**):`twilight-latest.apk` 25.4MB 被 git 跟踪、0 字节垃圾文件 `$DB` 被跟踪、根目录 23 个 `TASK-*/TEST-*.md` 应入 `docs/`、`.gitignore:35` 的 `docs/test-reports/v45-*/` 规则对 78 个已跟踪文件无效(形同虚设)、根目录 40+ 个 `hs_err_pid*.log`/`replay_pid*.log` | 逐项 `git ls-files` 对账;新增「单文件 >5MB 拒绝入库」检查;`docs/` 补 INDEX(现 52 份 md 无索引) |
| F5 | 已知工具链坑两条新增登记:①`getMethod("getFINGERPRINT")` 型死钩(与 `readText`/`@hide` 同族，**判据 = 照抄上游钩点名单前先在测试里证明它存在**);②「反射读字段的监控路径必须与钩方法路径共用同一 `unresolvedHookPoints` 观测面，否则门禁看不见」 | 写进 AGENTS 坑段 |

---

## 7. 门禁清单(本轮实测为绿的 **30** 个结构测试 —— 整改不得使其变红)

`architecture/` 14 个:`PackageBoundaryTest`、`LayerDependencyGateTest`、`SingleWriteEntryPointTest`、
`RecordWriteModelParityTest`、`RecordDaoAccessGateTest`、`DbMigrationParityTest`、`DialogConventionTest`、
`RecordRowContainerContractTest`、`AppbarSearchExclusivityTest`、`HardcodedStringConventionTest`、
`InterventionGateConventionTest`、`FeatureGateBypassTest`、`HotPathGateConventionTest`、
`DetailAnchorContractTest`、`MinSdkApiConventionTest`。
`core/model/` 9 个:`CapabilityMapTest`、`CategoryTaxonomyTest`、`DeviceEnvKindTest`、
`ProfileFieldCatalogTest`、`DeviceProfilePropertyParityTest`、`ProfileLabelsTest`、
`BaselineDataParityTest`、`AndroidOnlyDataTest`、`ProfileHookPlanTest`。
`i18n/` 2 个:`StringResourceParityTest`、`DomainVocabularyTest`。
`mcp/` 2 个:`McpToolParityTest`、`UiMcpCapabilityParityTest`。
其余:`ModevValueDialogTest`、`ProbeDispatchContractTest`、`InjectionAssemblyContractTest`、
`DatabaseGovernanceTest`、`ExplainQueryPlanProbeTest`、`RecordPagingProbeTest`、
`SettingsThemeContrastTest`、`KindLabelResExhaustiveTest`。

**门禁自身的两个洞(本轮实锤)**:
1. `BaselineDataParityTest` 硬依赖 `third_party/Guise_Reborn-main`,而 `third_party/` **被 gitignore**
   (`.gitignore:23`)⇒ 新克隆/CI 上全量单测**必红**(今日绿只因本机有未跟踪副本)。修法:
   对账所需 3 文件 `git add -f` 入库或镜像到 `app/src/test/resources`,并加「门禁测试禁读 gitignored 路径」扫描。
2. `McpToolParityTest.frozen_50_tool_names` 只断言 expected ⊆ declared(`:70-71`),超集靠另一处计数;
   三条扫描全部锚定 `private fun X() = schemaTool(` / `name = "…"` **文本形态** ⇒
   手写 `McpTool` 接口实现可整体逃逸(**理论洞，当前无实例**)。

---

## 8. 执行排期与依赖

```
WP-B B0(度量) ─┬─> B1(分页) ─> B2 ─> B4 ─> B5
               └─> B3(R8) 必须晚于 B0 且强制跑注入矩阵
WP-A A1..A5    独立、最先做(纯 bug，无前置)
WP-C C1..C12   仅 C11(token) 与 C7(行容器/扫描面) 与 WP-B B5 有文件冲突 ⇒ C7/C11 排 B5 之后
WP-D D1..D4    依赖 A1(覆写先落盘，否则微调页改了也白改)
WP-E E1..E6    E1 可即刻起;E2/E6 需设备档授权(Magisk/root 档位 = 共享设备侧操作，须先问使用者)
WP-F F1/F2     与 A4(89→88) 同轮做，否则数字改两次
```

| 轮次 | 内容 | 规模直觉 | 出口判据 |
|---|---|---|---|
| 轮 1 | A1 + A3 + A4 + F1 | 小(4 文件族 + 3 测试) | 4 条失效各有「先红后绿」证据;89→88 快照同步 |
| 轮 2 | A2 + A5 + F3 前 3 项 + §7 洞 1 | 小-中 | MCP 默认态与 schema 声明面对齐 |
| 轮 3 | B0 + B1 | 中(新模块 + 1 个 PagingSource) | jank 门禁可跑;单次 load 行数断言绿 |
| 轮 4 | C1–C7(违规+重复主体) | 中-大(约 30 文件) | 每原语「唯一件 + 门禁 + testTag」三件套 |
| 轮 5 | C8–C12 + B2 + B5 | 中 | token 扫描三扫归零;详情解析 <8ms |
| 轮 6 | D1–D4 | 小-中(接已有资产) | 需求 4 逐字段可选项全绿 + MCP 同权 |
| 轮 7 | B3 + B4 | 中(R8 风险) | release 产物 + 注入矩阵全绿 + MD5 对账 |
| 轮 8 | E1–E6 | 中 + 设备档 | 交付物 3 §5 四项有实测结论 |
| 轮 9 | F4 + F5 + 命名 N2/N4/N8/N9/N10/N11 | 大(跨四语 + 路由 + androidTest 键名) | #31⑥:`grep 键名` 面全改;`DomainVocabularyTest` 扩面后绿 |

---

## 9. 验收标准(「大厂验收」口径 —— 本轮的判据本身)

一条工作项算完成,当且仅当:

1. **可复现**:门禁带 `--no-build-cache` 跑绿(AGENTS 实锤:构建缓存可交出跨产物 ABI 不一致的陈旧 dex);
   判定 PASS 看 `BUILD` 行与结果 XML，**不看管道退出码、不看后台通知的 exit 0**。
2. **有反证**:该缺陷对应的最小测试**在改动前是红的**。改前不红的测试不算验收证据
   (本仓四次实锤:绿在空列表 / 绿在假夹具 / 绿在恒真断言)。
3. **两面齐**:JVM 绿 ≠ 设备生效。凡涉及注入(钩子、画像、R8 keep),必须**靶程序读回**
   或注入矩阵出记录才算生效证据(V49 结论:「别再用记录条数当写入证据，要读字段现值对账」)。
4. **契约同步**:改了分类/命名/开关面,同一提交内改 AGENTS + README + 交付物 1;改了 kind,
   走十站原子链(#30);删了资源键,`grep 键名` 连 `app/src/androidTest` 一起清(#31⑥)。
5. **UI 实证**:组件层改动需在设备/模拟器上看过一眼真实渲染(雷电转译 bug、波纹被吞、
   弹层遮罩吞点击都是**只在真机上存在**的缺陷形态);不能测就写明「未做设备验证」。
6. **不撒谎**:环境阻塞必须用 scope 步证据 + 先问使用者,禁止拿手搓探测判「框架死了」并写 ⛔
   (V49 已实锤误判一次)。

---

## 10. 需求映射与未覆盖项

| 需求 | 本文位置 | 状态 |
|---|---|---|
| 1 分类整理命名 | 交付物 1 全文 + 本文 WP-F | 清单已成，命名整改 18 条待做(N1–N18) |
| 2 组件工厂化 | 本文 WP-C(12 项) | 现状实测:对话框/sheet 外壳**本体已合规**(外溢 0),真实重复在**只读值弹层、空态、行组件、卡片、页壳**五处 |
| 3 性能上限 | 本文 WP-B(度量先行) | 唯一 P0 = F1 取窗形态;**零帧度量门禁**是本轮最大方法论缺口 |
| 4 逐参数可选 | 本文 WP-D | 主体是「8 个字段的行级入口」，资产与组件均已在仓 |
| 5 旧 API 兼容 | 交付物 3 全文 + 本文 WP-E | **代码已双 API;验证与元数据未跟上**(两个真实适配器测试引用数 = 0) |

**审计未能覆盖、需下一轮补的**:①注入侧 88 个 Hooker 的逐钩点存在性全量扫描(本轮只做了
LISTEN 域抽样 + `BUILD_FIELDS` 一案，`@hide`/扩展函数两族的**存量**未穷尽);
②wire 协议在 legacy 框架下的 per-session 解析对账;③记录表 110+ 列的存储放大与导出路径实测;
④四语文案的母语审校(本轮只验键集与占位符一致，未验语感)。
