# V28 任务文档:记录详情页纯 Markdown 化 × SharedPreferences 一等公民监听

| 项 | 值 |
|---|---|
| 版本 | V28(基于 V27 沉淀基线) |
| 日期 | 2026-08-26 |
| 关联契约 | AGENTS.md 强约束 #1–#20、构建质量契约(V26/V27 增量) |
| 变体 | modern / legacy 双框架同步 |
| Wire 协议 | VERSION 恒 4(泛型信封扩展点,零感知) |
| DB Schema | v9 → **v10** |

---

## 0. 任务追踪(实施过程中持续更新)

### P0 基建

- [x] P0-1 引入 `multiplatform-markdown-renderer-code@0.38.1` 依赖 + G-DEP-1 门禁
  - 备注:已完成。toml 新增 markdown-renderer-code + highlights 1.0.0 显式声明;依赖树核验:stdlib 2.3.0 升级为存量状态(materialkolor/compose-runtime 直接要求,非本次引入),新构件自身 metadata 安全(code=2.2.21 同线、highlights-jvm=Kotlin 2.0.20),编译通过。
- [x] P0-2 textLink 扩展点 spike 验证(0.38.1 实际 API)
  - 备注:已完成。javap/源码解包实证:0.38.1 MarkdownComponents **无 textLink 钩子**,链接点击经库默认 LinkInteractionListener → LocalUriHandler 出口;进一步 Robolectric 实证库链接语义节点为 0×0 bounds LinkTestMarker,坐标点击不可达 → 触发 D2.1 架构修订(TOC 行 = 原生导航控件,见 P1-A3)。Highlights/SyntaxTheme/Builder API 已核实。
- [x] P0-3 F9 `export_too_large` 二次拒绝漏格式参修复 + F10 `ByteRepresentation` 死代码清理 + RecordTypeTest 补 BEHAVIOR wire 断言
  - 备注:F9/F10 已完成(含 ByteRepresentationTest 一并删除);RecordTypeTest wire 断言并入 P2-B1(PREFS 落地时一次补齐 BEHAVIOR+PREFS)。

### P1 主线 A:详情页纯 Markdown 化

- [x] P1-A1 工厂层:`identityMarkdown`/`tocMarkdown`/H2 层级/`paramsJson`/bindArgs+rows NDJSON/箭头段/`MarkdownLangs.JSON`
  - 备注:已完成(经 D2.1 微调):identityMarkdown/mdSectionHeading/headingLevel 参数/FLOW_ARROW_LINE 落地;paramsJson/bindArgsJson/rowsNdjson 经 Gson serializeNulls(SQL NULL→JSON null);tocMarkdown 按 D2.1 退役(TOC=导航控件非信息区块)。
- [x] P1-A2 渲染层:markdownTypography 显式规范/扁平代码块(去卡)/`-code` 高亮管线(json→JS 分词、bash→SHELL、>64Ki 降级)/TOC anchor:// 链接拦截
  - 备注:已完成(D2.1 修订):detailMarkdownTypography(H2=titleMedium SemiBold 等 16 槽显式映射);MdCodeBlock 扁平化(surfaceContainerLowest+8dp 圆角+细描边,零 elevation,tag md-code-block);高亮经 Highlights 引擎同步分词(M3 同源 SyntaxTheme 亮暗自适应,json→JAVASCRIPT/bash→SHELL,HIGHLIGHT_MAX_CHARS=32Ki 降级);TabRow→SecondaryTabRow+rememberSaveable 去 key(弃用 API 清零);anchor:// 拦截链路按 spike 结论删除。
- [x] P1-A3 屏幕装配层:删除 HeroCard/AnchorChips 行/ZoneHeader/DetailFlowArrow;identity item → toc item → 节序列;锚点簿记与 handles 平移
  - 备注:已完成。HeroHeader.kt/AnchorChips.kt 删除(锚点纯函数迁至 detail/DetailAnchors.kt,detailTimeFmt 迁 Sections);三类型统一 identity→toc→节序列;flow H2 随 input 文档+箭头段内联;keys H2 随 key 文档;meta 带 H2 元数据标题+时间戳行;D2.1:RecordTocContent 原生导航行(testTag toc-{key})。
- [x] P1-A4 i18n 键 + 测试改造 + 单测全绿
  - 备注:已完成。新增 section_metadata/meta_timestamp(section_toc 随 D2.1 移除);工厂测试补 V28 六例(identity/H2 层级/json 不聚合行为断言);ParamsJsonTest 九例;MetaTableRoboTest 适配 metaSectionDoc;ui.logs.* + i18n.* 全绿(AnchorScrollRoboTest clickFound=true firstVisible=5)。

### P2 主线 B-core:SharedPreferences 监听(模型/存储/注入)

- [x] P2-B1 core model:`RecordTypes.PREFS="prefs"`/`RecordType.PREFS`/`PrefsKind`(5)/`PrefsOperation`(17)/`PrefsRecord`/Taxonomy+validateOperations/HookSwitch(prefsListen+kinds+prefsEnabled)/EffectiveSwitch 三臂/AppListenOverride+VALID_KINDS
  - 备注:已完成。全部默认值按 D4;HookSwitch.fromJson 补 prefsListen 兜底;fromWire 单源断言补齐(BEHAVIOR+PREFS)。
- [x] P2-B2 codec `PrefsRecordCodec` + 注册;DB MIGRATION_9_10(prefKey TEXT)+Entity+DAO(ROW_SELECT/WHERE_FILTER)+RowModel.PrefsRow+`PrefsRecordMappers`+dispatch 臂+LogRepository writeBatch/cryptoRegistry
  - 备注:已完成。迁移纯 ALTER TABLE;关键字 LIKE 扩 prefKey(新旧三处查询);wire VERSION=4 零变更。
- [x] P2-B3 注入侧:`PrefsProbe` + PrefsOpenHooker/PrefsGetHooker/PrefsEditorHooker + implToPath/editorToImpl LRU(cap256) + HookInstaller probes/jsonOf
  - 备注:已完成。闸门序与行为逐位一致;hide 类解析统一收敛 BehaviorMethodRegistry.classForName(参数间接,顺带消除 PrivateApi lint 触发面);值捕获受 fileContentCapture 闸门。
- [x] P2-B4 B-core 单测:codec roundtrip/值序列化五类/Migration9To10/Taxonomy-HookSwitch 对齐自动扩权
  - 备注:已完成。新增 PrefsRecordCodecTest/PrefsRecordMappersTest/migration v9→v10(含 createV9Helper);HookSwitchTest 增 prefs 对齐与映射用例;EffectiveSwitchTest 互逆经 taxonomy 迭代自动扩权。

### P3 主线 B-ui:SharedPreferences 监听(界面全触点)

- [x] P3-C1 UI 全触点:路由 records/prefs/{pkg}/AppMonitor 第4 EntryRow(monitor-entry-prefs)+StatsCard 四段/PrefsColorTokens/记录列表薄包装+PrefsRow 呈现+KindIcons.prefs+labels/监听设置 prefs 分区行/DataSettings ClearTarget.PREFS/CapabilityKind.PREFS_LISTEN
  - 备注:已完成。listenSummary 计数纳入 behavior/prefs master 偏差;switchLabelRes 的 OPEN/CLEAR 与 SQL kind 名碰撞已前置分支(强约束沉淀)。
- [x] P3-C2 详情页 prefs 分支(PrefsLoadedContent:identity→toc→value→params→stack)+prefsAnchors+导出估算+i18n 双语(op_prefs_*×17 等 27 键)
  - 备注:已完成。value 围栏语言按 params.type 自动 json/text;时间戳行走 meta_timestamp。

### P4 收尾

- [x] P4-1 全量单测 + lintModernRelease(基线 36 warning/0 error 零新增)+ assemble 双变体
  - 备注:已完成。单测 485+ 全绿;lint 经三轮收敛回基线 36(移除未引用的 -code 直依赖改 Highlights 直依赖并锁 1.1.0/Kotlin 2.2.0、删除 cd_flow 死资源、hide 类解析走参数间接);assembleModern Release/Debug 四 APK 产出。
- [x] P4-2 设备插桩测试(雷电模拟器)含 PrefsProbeInstrumentedTest + 纯 markdown 契约 UI 测试
  - 备注:已完成(emulator-5554/API14)。12 项定向插桩全绿:PrefsProbeTest×3(真机全链 OPEN/GET/PUT/REMOVE/CLEAR+路径回填+capture 闸门+关闭零产出)、PureMarkdownIdentityTocTest×2(身份区/TOC/元数据表+TOC 点击滚动)、CryptoDetailZonesTest(H2 节根/角色/↓段/密钥材料)、MarkdownByteSectionTest(V26 引擎回归)。踩坑沉淀:构造器 after 的 thisObject 必须传新实例;sink 异步到达需 await 后再 clear。
- [x] P4-3 AGENTS.md 契约沉淀(#21 纯 markdown 详情/#22 偏好监听)+ 本档收尾标记
  - 备注:已完成。另沉淀工具链坑 4 条(KDoc get*/put* 终结注释+KSP MissingType 掩蔽、Gson serializeNulls、mikepenz 0.38.1 无 textLink 且链接节点 0×0 bounds、NewerVersionAvailable 按声明依赖计数);词汇表/标题行更新偏好监听。

---

## 1. 背景与目标

**主线 A — 详情页纯 Markdown 化**:Appbar 以下全部区域仅由 markdown 单管线渲染,移除一切原生卡片类组件(Hero 卡、锚点芯片行、ZoneHeader 色带、代码卡 Surface 外壳),同时满足优美/简约/高级的视觉标准与性能要求,JSON 语法高亮落地。

**主线 B — SharedPreferences 监听一等公民**:第四记录类型,与算法/数据库/行为并列;覆盖「读入配置文件」(OPEN)与 Get/Put 全家桶(GET/PUT/REMOVE/CLEAR),完整复用 V27 行为监听已验证的全栈模式。

**非目标**:SQL/bash 高亮(bash 经 SHELL 引擎免费获得除外);commit/apply 落盘挂钩(D5 否决);mikepenz 渲染器升级(0.39+ 需 stdlib 2.3,超编译器 metadata 上限,禁升)。

## 2. 现状审计摘要(证据索引)

| # | 事实 | 位置 |
|---|---|---|
| F1 | 渲染单管线:`RecordMarkdownContent` 经核心 `rememberMarkdownState(immediate=true)` + `markdownComponents(codeFence=)` 唯一扩展点 | ui/logs/markdown/RecordMarkdownContent.kt:79-100 |
| F2 | 工厂纯 Kotlin 零 UI import,本地化参数注入;MARKDOWN_DISPLAY_MAX_CHARS=128Ki | RecordMarkdownFactory.kt:18-168 |
| F3 | 围栏安全 = 反引号最长 run+1;GFM 表格紧凑管道 `\|Cell\|` | 同上 :28-77 |
| F4 | 代码组聚合:连续 top-level text/base64/hex 围栏,ordinal 寻址 | CodeGroupExtractor.kt:91-149 |
| F5 | 锚点簿记:mark/advance 与 DSL 发射严格同序,每区块恒 1 item | RecordDetailScreen.kt:344-361 |
| F6 | D1 边界现状:HeroCard/锚点芯片/ZoneHeader 为原生组件 | HeroHeader.kt:182-193 等 |
| F7 | 护栏在屏幕层:复制 4M / 导出 32M / 显示 128Ki | RecordDetailScreen.kt:120-191 |
| F8 | 围栏语言现状仅 text/base64/hex/sql/bash,无 json、无任何高亮 | 全仓 grep |
| F9 | 存量缺陷:export_too_large 二次拒绝分支漏传 %1$d | RecordDetailScreen.kt:183 |
| F10 | 死代码:ByteRepresentation.kt(V26 前脚手架) | core/util/ByteRepresentation.kt |
| F11 | 新增记录类型 32 处触点清单(行为监听实证推导) | §5.7 |
| F12 | 注入侧闸门序与被动红线范式 | BehaviorBaseHooker.kt:60-98 等 |
| F13 | wire 泛型信封即扩展点 | WireProtocol.kt:25-62 |
| F14 | DAO 哨兵过滤/分页大窗口契约 | CryptoRecordDao.kt:135-171,376-415 |
| F15 | androidTest 对行为监听三入口零覆盖(既有缺口) | 全 androidTest grep |

## 3. 决策记录

| ID | 决策 | 理由 |
|---|---|---|
| D1 | 内容区 = Appbar 以下全域,100% markdown 渲染;身份信息并入文档头部,锚点导航改为 markdown 目录(TOC)链接 | 用户明确口径 |
| D2 | 保留多表示字节代码组标签页;渲染/分组/高亮一律基于成熟库(mikepenz `-code` 官方模块 + SnipMeDev Highlights),禁止手搓分词器 | 用户明确口径;官方模块在 0.38.1 精确存在,stdlib 2.2.21 与现锁一致 |
| D3 | JSON 高亮覆盖 params + bindArgs + rows(NDJSON);工厂改发真实 JSON | 用户选定推荐项 |
| D4 | SP 五类默认值按噪声分层惯例:OPEN/PUT/REMOVE/CLEAR=SIGNAL 默认开,GET=NOISE_HIGH 默认关 | 与行为监听 FILE_READ 同构 |
| D5 | 不挂钩 commit()/apply() | put 即应用调用面完整;落盘时机取证价值低且翻倍噪声 |
| D6 | SP 值捕获受既有全局微调 fileContentCapture 约束(默认 false) | 开关模型零新增微调项;GET 本身默认关,双闸降噪 |
| D7 | wire 名 "prefs";中文词汇「偏好监听」;路由 records/prefs/{pkg} | 词汇表延续 |

## 4. 主线 A 设计

### 4.1 目标形态

```
┌ TopAppBar ────────────────────────────────┐
│ item1 identity-md │ ## AES/CBC 加密        │  ← 原 HeroCard
│                   │ **CIPHER · ENCRYPT · 解密方向** │
│ item2 toc-md      │ ## 目录                 │  ← 原 AnchorChips 行
│                   │ - [数据流向](anchor://flow)     │
│                   │ - [密钥材料](anchor://keys) …   │
│ itemN section-md  │ ## 数据流向              │  ← 原 ZoneHeader 色带
│                   │ ### 明文(输入)           │
│                   │ ```text …代码组标签页```   │
│                   │ ↓                       │  ← 原 DetailFlowArrow 图标
│                   │ ### 密文(输出) …          │
└──────────────────────────────────────────┘
```

删除:HeroHeader 四组件族、AnchorChips 原生 FilterChip 行、ZoneHeader、DetailFlowArrow、CodeBlockCard 的 Surface 卡外壳。
不变:每区块恒 1 LazyColumn item、mark/advance 同序簿记、显式 state=listState、128Ki 显示钳制、4M 复制 / 32M 导出护栏、DetailCodeActions 出口、handles 与围栏发射顺序严格对齐。

### 4.2 依赖变更(toml)

```toml
[versions]
markdownRenderer = "0.38.1"   # 不变,禁升
[libraries]
markdown-renderer-code = { group = "com.mikepenz", name = "multiplatform-markdown-renderer-code", version.ref = "markdownRenderer" }
# 传递引入 dev.snipme:highlights:1.0.0(runtime)
```

门禁 G-DEP-1:依赖树核验 kotlin-stdlib 无 2.3+ 泄入、highlights 定格 1.0.0。失败预案:降级纯 mono 渲染常量开关。

### 4.3 工厂层

- 新增 `identityMarkdown(headline, accentLine)`:`## headline` + 粗体上下文行。
- 新增 `tocMarkdown(entries)`:`## 目录` + 无序列表 `[label](anchor://key)`。
- 各节标题升级 H2(byteSectionMarkdown/textSectionMarkdown 增加 headingLevel 参数;metaTableMarkdown 前置 `## 元数据`)。
- `paramsJson`(保序 pretty JSON,继承钳制)、bindArgs → JSON 数组、rows → NDJSON。
- 流向箭头:input 文档 trailer 追加居中段落 `↓`。
- `MarkdownLangs.JSON="json"`(不入 GROUP_LANGUAGES,json 围栏永不参与聚合)。
- 时间戳行进各类型 meta 表(meta_timestamp)。

### 4.4 渲染层

排版规范:h2=titleMedium 16sp SemiBold;h3=titleSmall SemiBold;正文 bodyMedium lineHeight22sp;表格 bodySmall;引用 bodySmall italic onSurfaceVariant;代码 13sp Mono lineHeight20sp;链接 primary Medium;分隔线 outlineVariant。

代码块扁平规范:surfaceContainerLowest + 8dp 圆角 + 1dp outlineVariant@40% 描边,零 elevation;顶部微型行(语言标签 + 复制/全屏),细分隔线,双向滚动正文(heightIn max 320dp)。testTag detail-code-card→md-code-block,detail-code-tabs 保留。

高亮管线:codeFence 内部委托 `-code` 高亮组件;json→SyntaxLanguage.JAVASCRIPT、bash/shell→SHELL,其余纯 mono;主题 SyntaxThemes 亮暗切换 remember(content,isDark) 缓存;HIGHLIGHT_MAX_CHARS=64Ki 超限降级纯文本。已知取舍:JS 分词下 JSON 键与字符串值同类着色。

TOC 链接拦截:markdownComponents.textLink(anchor:// 解析→onAnchorClick);兜底方案 TOC 全自定义组件。

### 4.5 屏幕装配层

三类型统一:identity item → toc item → 节序列;anchors 键集不变 + prefsAnchors;TOC 条目 = 锚点键 ∩ 实际存在节;文档构建纪律保留;identity/toc 文档 handles 空表。

## 5. 主线 B 设计

### 5.1 词汇

偏好监听 Prefs Listen = SharedPreferences 读入配置文件 + Get/Put/Remove/Clear 全家桶采集能力。OPEN = SharedPreferencesImpl 构造完成(磁盘载入时刻,进程内每文件一次)。

### 5.2 Hook 矩阵(boot classloader,registry boot-first,类缺失静默跳过)

| Hooker | 目标 | 方法 | 操作→Kind |
|---|---|---|---|
| PrefsOpenHooker | android.app.SharedPreferencesImpl | 首参 File 的声明构造器(after) | OPEN |
|  | 同上 | edit()(after,建立 editor→impl 映射) | — |
| PrefsGetHooker | SharedPreferencesImpl | getString/getInt/getLong/getFloat/getBoolean/getStringSet/contains/getAll(after) | GET_* / GET_ALL / CONTAINS → GET |
| PrefsEditorHooker | SharedPreferencesImpl$EditorImpl | putString/putInt/putLong/putFloat/putBoolean/putStringSet/remove/clear(after) | PUT_* → PUT;REMOVE;CLEAR |

### 5.3 数据模型

RecordTypes.PREFS="prefs";PrefsKind{OPEN,GET,PUT,REMOVE,CLEAR};PrefsOperation(17,构造器 kind 归属);PrefsRecord(seq,packageName,kind,operation,key?,value BytesView?,path?,params[type/mode/found],processName?,contextId,threadName,stack,timestamp)。

值序列化(fileContentCapture 闸门):String 原文;Set<String>/getAll→pretty JSON;原始类型 toString;contains 仅 params[found];超 maxBytesPerField 截断。

DB:MIGRATION_9_10 仅 ALTER TABLE crypto_records ADD COLUMN prefKey TEXT;path=配置文件路径,content 五元组=值,command/fileSize 恒 null;ROW_SELECT/WHERE_FILTER 关键字 LIKE 扩 prefKey。

### 5.4 开关默认值

prefsListen=true;prefsOpen/prefsPut/prefsRemove/prefsClear=true(SIGNAL,order 0/2/3/4);prefsGet=false(NOISE_HIGH,order 1)。HookSwitch.prefsEnabled(kind)+anyPrefsEnabled;EffectiveSwitch 三臂互逆;AppListenOverride.prefsListen:Boolean?+VALID_KINDS∪=PrefsKind.names;零新增微调项。

### 5.5 注入侧

PrefsProbe 注册 HookInstaller;PrefsRecordCodec(raw-JSON 枚举严检);emit 闸门序与行为逐位一致(config 快照→prefsEnabled→PathGate→栈 denylist→SessionThrottle("kind|op|path|key")→组装);被动红线仅取调用即可得实参/返回值;implToPath/editorToImpl 两有界 access-order LRU(cap 256)杜绝反射读内部字段。

### 5.6 i18n 新增键(27 ×双语)

prefs_monitor_master、kind_prefs_{open,get,put,remove,clear}、op_prefs_*×17、filter_type_prefs、prefs_records_title/_search、settings_records_prefs、settings_clear_prefs_records、danger_clear_prefs_msg。主线 A 追加:section_toc、meta_timestamp、section_value、meta_pref_file、meta_pref_key、meta_pref_type。

### 5.7 UI 全触点清单

路由 ModevRoutes/ModevNav;AppMonitor 第4 EntryRow+StatsCard 四段(min weight 0.02f)+theme/PrefsColorTokens;MonitorRecordsScreen 薄包装+RowModel.PrefsRow+headline(key?:basename)+prefsSummary+RecordRow.prefsPresentation+KindIcons.prefs;PerAppListenSettingsScreen master 行 listen-row-master-prefs+5 taxonomy 行 listen-row-prefs-{id};DataSettingsScreen ClearTarget.PREFS;CapabilityKind.PREFS_LISTEN+PrefsListenCapability;详情页 PrefsLoadedContent(identity→toc→value→params→stack)+prefsAnchors+导出估算器;mapping/PrefsRecordMappers(toEntity/toModelOrNull/toJson)+CryptoRecordMappers 双 dispatch 臂+LogRepository.writeBatch/cryptoRegistry。

## 6. 测试计划

| 层 | 新增 | 改造 |
|---|---|---|
| 纯 JVM | ParamsJsonTest、TocBuilderTest、PrefsRecordCodecTest、PrefsSerializationTest、RecordTypeTest 补 PREFS/BEHAVIOR wire | RecordMarkdownFactoryTest(H2 层级/箭头段/NDJSON)、HookSwitch↔taxonomy 自动纳入、EffectiveSwitchTest 互逆自动纳入、AppListenOverrideJsonTest 白名单 |
| Robolectric | Migration9To10Test、HighlightPipelineRoboTest、TocLinkRoboTest、PureMarkdownContractRoboTest | ExplainQueryPlanProbeTest/RecordPagingProbeTest(schema 联动) |
| androidTest | PureMarkdownIdentityTocTest(取代 DetailHeroAnchorsTest)、PrefsProbeInstrumentedTest(仿 DatabaseProbeTest)、MonitorEntryHeaderTest 第4入口、PerAppListenSettingsTest prefs 行 | CryptoDetailZonesTest 选择器 md 化、MarkdownByteSectionTest、MonitorListScreensTest/DataSettingsScreenTest prefs 分支、回填行为入口冒烟 |

门禁命令:testModernDebugUnitTest → lintModernRelease → assembleModernRelease/Debug → connectedModernDebugAndroidTest。

## 7. 风险登记册

| ID | 风险 | 缓解/回滚 |
|---|---|---|
| R1 | highlights 传递版本或 Kotlin metadata 不兼容 | G-DEP-1 首日验证;降级纯 mono 常量开关 |
| R2 | textLink 扩展点签名与预期不符 | P0 spike 兜底(TOC 全自定义组件) |
| R3 | GET 高频刷屏 | 默认关 + SessionThrottle + 有界队列 2048 |
| R4 | EncryptedSharedPreferences 记录到密文键值 | 已知局限写入 KDoc(红线:不解密) |
| R5 | EditorImpl/API 差异 | minSdk 26 域内声明构造器扫描 + 类缺失静默跳过 |
| R6 | 四段比例条窄屏挤压 | min-weight 0.02f 惯例延展 |

## 8. 验收清单(摘要)

□ Appbar 以下无任何 Card/Surface 卡组件 □ JSON 围栏高亮生效且 >64Ki 优雅降级 □ TOC 点击滚动精准 □ 复制/导出/显示护栏不变 □ SP 五类开关按 D4 默认,每应用二态覆盖单击即写即推 □ records/prefs/{pkg} 列表/筛选/搜索(prefKey)/时间线分组/长按复制齐备 □ 数据管理第四清理目标 □ 迁移 v9→v10 数据存活 □ wire VERSION=4 不变 □ en/zh parity 绿 □ lint 36 warning/0 error 零新增 □ 测试金字塔全绿。
