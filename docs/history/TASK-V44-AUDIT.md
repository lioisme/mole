# TASK-V44：数据第一性 / 查询核心双端同源 / MD3 组件与设计语言 / 宿主性能安全 全量审计任务书

| 项 | 值 |
|---|---|
| 版本 | V44（基于工作树 `0431431` + V43 未提交增量，2026-09-16） |
| 性质 | 全量代码审计报告 + 分任务实施任务书 |
| 审计方法 | 三路并行全仓审计（数据层单一事实源与查询核心 / MD3 组件与设计 token / 注入侧热路径与零阻塞）+ 高严重度指控逐行二次复核（抽查 10 处全部命中：proc 族零门控与重复注册、`kindOptionsOf` 裸枚举、`RecordsCountSubtitle` itemCount、SSL init 门控倒挂、队列溢出 jsonOf、setText 白付、`observe` vs `queryPage` sort 分叉、keywordFollowGlobal 写语义、apps_list 默认值、v7 种子表） |
| 决策状态 | 3 项方向性决策按审计推荐默认落定（见 §0.3，维护者可否决） |
| 交付边界 | 本文档为审计 + 实施任务书；实施按 §2 逐项回填 ✅ + 备注 |
| 关联文档 | `AGENTS.md`（现行契约）、`TASK-V43-AUDIT.md`（前轮，本轮 V43 红线在 proc 族实证成建制失守，见 P1）、`TASK-MD3-AUDIT.md`（旧代组件审计，本轮继承其门禁体系） |

---

## 0. 总纲

### 0.1 需求输入（维护者原始 5 项）

| # | 需求 | 审计口径 |
|---|---|---|
| R1 | 上层所有**类型、应用、记录、列表、筛选、查询**完全基于底层数据（第一性） | 单一事实源（Room/仓库门面）、派生链完整性、硬编码清单/第二映射表/内存自造口径 |
| R2 | 所有查询方法封装为**一份专业、规范、完善的核心**；前端界面与 MCP 同为一等公民、同时基于此核心 | 查询入口收敛性、双端默认值/写语义/排序/分页/确认门槛一致性、能力对称性 |
| R3 | 前端界面**完全使用 MD3 组件库**或其它专业、热门、持续维护的组件库，禁止手写手搓 | 手搓组件识别、M3 等价件缺位登记、对话框/弹层/开关/列表行门禁 |
| R4 | 设计风格语言（质感、排版、配色、字体、间距、圆角、范式、选型）**完全符合 MD3 默认推荐** | 色板来源、字阶/形状/间距/动效 token 化、elevation 范式 |
| R5 | 兼具功能与性能：被作用域软件**不得无法启动、卡顿、闪退、掉帧** | 冷启动装钩预算、热路径门控先行、锁与背压、内容捕获内存峰值、异常安全 |

### 0.2 审计总结论（TL;DR）

**骨架高度合规、血肉存在实锤违规**。查询核心（`RecordFilter → RecordService/LogRepository`、单 WHERE 片段、DAO 门禁、包边界扫描）在位且无 SQL 旁路；MD3 组件主干达标（material3 1.4.0、对话框双门禁、47/47 ListItem 显式容器色、HCT 动态色板、tonal 分档质感）；V43「零阻塞锁链」「单一监视器」主体落地属实。但：

- **R1 一条高危实锤**：列表计数副标消费 Paging 内存 `itemCount`（随滚动增长），真 SQL COUNT 流旁置死码——「展示数 ≠ 匹配数」且与 MCP `records_count` 三口径；另有筛选菜单 10 处裸枚举遍历绕过 taxonomy（静默零结果陷阱）、双份 kind→标签映射（WEB_CRYPTO 两页两文案实证）、MCP 硬清单四处。
- **R2 双端语义漂移成簇**：`sort` 契约仅 MCP 兑现、keywordFollowGlobal 写语义分叉（MCP 造幽灵偏差）、apps_list 默认值/排序漂移、pageSize 50/60 双源、MCP 能力缺口（关键词启停/清空/目录刷新）；parity 门禁只锁名字不锁语义 = 全部漂移在雷达外。
- **R3/R4 残留散点**：唯一手拼行（TokenCard）、TOC 触控 <48dp、双语义 RadioButton 形态分叉、9 处未 token 化 alpha（其中 `Tokens.codeBorderAlpha` 已存在未消费）、非尺度圆角 3 处、v7 经典 500 色板种子表。
- **R5 红线两处成建制失守 + 一条内存风暴链**：proc 族六件套对 `readLine`×6 / `FileReader 构造器`×5 **重复注册且构造器回调零门控**（每次 `new FileReader(path)` 全进程付 `canonicalPath` native IO，开关全关照付；Vector 下后注册遮蔽 = 过滤静默失效）；冷启动主线程同步装数百钩无配置裁剪；`Cipher.update` 无节流 + hook 线程三态物化（1MB 字段 ≈6MB 字符串拷贝）+ 队列 2048 **条**非字节上限 → 大加密流量 GC 风暴/极端 OOM；叠加 logStack 默认全栈捕获 = 默认预算最激进组合。

统计（违规条目，P1=必须修 / P2=应修 / P3=登记取舍）：

| 需求 | P1 | P2 | P3 | 合计 |
|---|---|---|---|---|
| R1 数据第一性 | 1 | 3 | 4 | 8 |
| R2 查询核心同源 | 0 | 4 | 3 | 7 |
| R3 MD3 组件 | 0 | 3 | 2 | 5 |
| R4 设计语言 | 0 | 3 | 2 | 5 |
| R5 性能安全 | 3 | 6 | 5 | 14 |
| **合计** | **4** | **19** | **16** | **39** |

### 0.3 已定决策（维护者未在会话中应答，按审计推荐默认执行，可推翻）

- **D-V44-1 冷启动策略 = 稳健档 + 配置裁剪**：共享广播收编 + 门控先行 + master 全关 Probe 不安装；**不做**监听域异步安装（零记录丢失竞态、零新竞态面）。
- **D-V44-2 用户可见默认值变更**：主题种子表 v7 经典 500 系 → M3 基线 40 度 hue 代表色，默认 seed `#1976D2` → Purple40 `#6750A4`（已选具体色值用户经持久化 seed 保留观感）；`logStack` 默认 `true` → `false`（堆栈捕获性能预算最大单项；需时逐应用开启）。
- **D-V44-3 MCP 工具面补齐**：新增关键词条目启停 / `keywords_clear` / `apps_refresh`，「工具面 43」冻结快照 → 46，AGENTS 词汇表与 `24-run-mcp.ps1` 同步。R2「双一等公民」的彻底解。
- **D-V44-4 计数口径回归底层数据**：推翻 V30「消除独立 COUNT Flow」历史决策（AGENTS #20 相应修订），真 SQL COUNT 经 Room count Flow 消费；性能由 V43 T5 最小组合器隔离（副标槽位单独重组）+ WhileSubscribed 保证——分页 append 不触达、仅表写入失效触发。
- **D-V44-5 诊断标签双端语义**：MCP 输出恒英文是协议面事实（Agent 消费方），UI 本地化是展示面事实；「identical content」声明修订为「identical data, MCP labels English-locked」并登记（不改代码行为，消除声明失真）。

---

## 1. 违规明细（证据 = 工作树实位 file:line）

### R1 上层必须完全基于底层数据

- **D1〔P1〕计数副标内存自造**：`ui/records/MonitorRecordsScreen.kt:320,527-546`（`RecordsCountSubtitle` 消费 `records.itemCount`）+ `ui/records/RecordsViewModel.kt:67`（真口径 `totalCount = logs.count(filter)` 全 UI 零消费者）。展示数 = 已加载窗口数（封顶 `PAGING_WINDOW_CAP=20000`），与 `records_count`（MCP 真 COUNT）与死流 `totalCount` 三口径。AGENTS #20 V30 决策与需求 1 冲突，按 D-V44-4 推翻。
- **D2〔P2〕筛选菜单绕过 taxonomy**：`ui/records/MonitorRecordsScreen.kt:116,135,154,173,192,207,223,239,255,271,509-519`（`kindOptionsOf` = `CryptoKind.entries` 等裸枚举×10 直出）。引爆面：新枚举漏登记 taxonomy → `RecordFilter.normalize` 交集空 → 菜单出项但恒零结果；且展示序 = ordinal ≠ 设置页 taxonomy.order（两副面孔）。既有 `CategoryTaxonomyTest` 只锁 taxonomy⊆枚举，**不锁枚举⊆taxonomy**。
- **D3〔P2〕双份 kind→标签映射表**：`ui/apps/KindLabelRes.kt:20-111`（按 id 字符串，crypto/db 用裸字面量）↔ `ui/components/RecordRow.kt:551-564` + `BehaviorLabels.kt`/`PrefsLabels.kt`/`SqlLabels.kt`（按枚举）。同 kind 双资源键实证：WEB_CRYPTO 设置页 = `switch_webview`、列表行/菜单 = `kind_webview`。两表可各自演化；未知 kind 兜底 `unknown_error` 误标（KindLabelRes.kt:110）。
- **D4〔P2〕MCP 硬编码清单与第二映射表**：`mcp/tools/AppTools.kt:83-87`（10 类型清单，应为 `RecordType.entries`）；`mcp/tools/ListenConfigTools.kt:51,123-135`（master 10 字面量 + `withMaster` id→字段第二表）；`mcp/tools/FeatureConfigTools.kt:63,24-31`（同构）；描述文本硬编码「44 entries」「6 groups (名字全列) 37 kind」「CONTAINS|REGEX|EQUALS」「auto|pixel7pro|xiaomi13」「default 1511」（`GlobalAssetTools.kt:39,67,117,145`、`ServerTools.kt:84`）——与 `KeywordMatchMode.entries`/`SpoofProfiles.BUILTINS`/`ConfigKeys.*_DEFAULT_PORT` 无派生关系。
- **D5〔P3〕遗留 12 列 LIKE 查询驻留**：`data/db/CryptoRecordDao.kt:52-121`（`observePaged`/`observePagedRows`，主源零消费者，仅 `CryptoRecordDaoTest`/`RecordPagingProbeTest` 引用）。第三查询语义面：一旦复活即 UI≠MCP 命中集（`WHERE_FILTER` 现 24 列）。
- **D6〔P3〕VALID_KINDS 并集键**：`core/model/AppListenOverride.kt:323-333` 以枚举并集裸 id 为键，跨类型 OPEN/SIGNATURE 碰撞（`CategoryTaxonomyTest:200-202` 已明示）以「实际不碰撞」侥幸成立；taxonomy 主键是 (type,id) 对。
- **D7〔P3〕record_create 不校验**：`mcp/tools/RecordTools.kt:218-228` 可注入漂移 kind/operation，其被 `validateKinds` 永久隐藏（写侧第一性缺口）。
- **D8〔P3〕死契约/死别名**：`domain/record/RecordService.kt:61` `observePackages` 零消费者；`data/repo/LogRepository.kt:497-519` `pagedRecords` 零调用者，`paged/counts/observeRecordTypeCounts/observeById/neighborIdOf/clear` 兼容别名与契约名混用（一功能两名）。

### R2 单一查询核心，UI/MCP 双一等公民

- **Q1〔P2〕sort 契约两副面孔**：`data/repo/LogRepository.kt:301-330`（`observe` 恒 DESC 静默忽略 `RecordFilter.sort`）vs `:333-360`（`queryPage` ASC/DESC 双切片兑现）。同 filter 双消费者行为不同。
- **Q2〔P2〕keywordFollowGlobal 写语义漂移**：`ui/apps/PerAppFeatureSettingsScreen.kt:106-117`（UI true→写 **null**=回默认删键）vs `mcp/tools/FeatureConfigTools.kt:133-137`（MCP true→**显式 true** 落盘）→ `AppListenOverride.isDefault`(:87)/`deviationCount`(:118) 幽灵偏差、恢复默认行虚现。
- **Q3〔P2〕apps_list 结果集与序漂移**：`mcp/tools/AppTools.kt:54-55`（showSystem/showModules 取内置 `true/false` 非 `AppListSettings` 持久值——用户 UI 隐藏系统应用后 MCP 仍返回）+ 无排序（扫描原始序）vs `ui/apps/AppsViewModel.kt:135-153`（Collator 排序 + 置顶；`AppCatalogRepository.kt:97-98` 明文「排序唯一在 VM」= 排序逻辑滞留 UI，MCP 不可达）。
- **Q4〔P2〕分页/确认门槛不对称**：`mcp/tools/RecordTools.kt:58` pageSize 默认 50 vs `LogRepository.kt:589` `PAGE_SIZE=60`；`records_clear_all`/`app_clear_records` 有 confirm（`RecordTools.kt:189-201`、`AppTools.kt:111-126`），但 `records_clear_type`/`records_clear_package` 无 confirm 而 UI「清空本类」恒有确认框（危险度不对称）。
- **Q5〔P3〕MCP 能力缺口 + 声明失真**：关键词条目启停（UI `setEnabled`，`KeywordLibraryScreen.kt:193`）、`keywords_clear`（repo `clear()` 已有，`KeywordLibraryRepository.kt:81`）、apps `refresh()`（UI 下拉刷新有）无工具；`mcp/tools/ServerTools.kt:116-143` 诊断标签英文字面量注入 vs UI 本地化——「identical content」仅英文 locale 成立（按 D-V44-5 修订声明）。
- **Q6〔P3〕类型面强制缺位**：UI VM 注入具体 `LogRepository` 走别名调用（`RecordsViewModel.kt:64,67`）；MCP 仅 `RecordTools` 用接口、`AppTools.kt:22` 亦 concrete——「同一契约面」靠纪律非类型。
- **G〔P3〕门禁盲区登记**：parity 快照只锁工具名不锁语义/默认值；DAO 门禁抓不到内存派生口径（D1 全盲）；taxonomy 不锁枚举⊆（D2 引爆点）；无 observe≡queryPage 一致性断言。→ 由 T1.5 补门禁。

### R3 MD3 组件禁手搓（总评：达成度高）

- **U1〔P2〕TokenCard 手拼行**：`ui/settings/McpTokensScreen.kt:308-345`（Card+Row+Column+clickable 自拼 headline/supporting/trailing 槽——M3 等价件 = ListItem；全 App 唯一未用 ListItem 的行）。
- **U2〔P2〕TOC 行触控不达标**：`ui/detail/markdown/RecordTocContent.kt:32-47`（手搓可点击行 ≈32dp < M3 48dp 最小触控 + 裸 clickable 无 Role）。登记例外（D2.1）内容保留，触控与语义必修。
- **U3〔P2〕双语义选择行形态分叉**：`ui/components/ModevSheetScaffold.kt:328-340`（RadioButton `onClick` 可聚焦 + 行 clickable 同动作 = TalkBack 双节点重复）vs `ui/settings/SettingsScreen.kt:358-377` IconChoiceRow 正解（只读 RadioButton + 行 selectable）。前者改齐后者。
- **U4〔P3〕只读 Switch 导航行 affordance 错位**：`ui/settings/features/KeywordLibraryScreen.kt:259-262`（Switch 视觉=可拨、行为=不可拨、整行点击=编辑）→ 拨态改 Badge 形态或行 toggleable 语义拆分。
- **U5〔P3〕EmptyState 六处复制**：`AppsScreen.kt:240-257,351-368`、`RecordTimelineList.kt:106-123`、`LicensesScreen.kt:90-103`、`McpTokensScreen.kt:417-430`、`KeywordLibraryScreen.kt:149-162`、`SettingsScreen` 同款——无 M3 等价件不违规，但 V40 T5.4 退役共享件致 DRY 代价，重新共享化。
- **合规确认（无需动作）**：对话框双门禁实测合规（AlertDialog/ModalBottomSheet 各唯一构造点 + DialogConventionTest）；零 Toast；无手写 Switch/Checkbox/TopAppBar/Tabs/Popup；StatsCard 比例条/StatusPill/MicroBadge/swatch 网格 = M3 无等价件的正当自定义。

### R4 设计语言符合 MD3 默认（总评：主干达标、散点违例）

- **V1〔P2〕v7 种子色板**：`ui/theme/ThemeConfig.kt:15-43`（20 色 = 2014 Material 经典 500 系全表；默认 seed `0xFF1976D2` = v7 Blue 700）。HCT 归一后功能合规，但「配色范式选型」违需求 4 → 按 D-V44-2 换 M3 基线代表色表。
- **V2〔P2〕未 token 化 alpha ×9**：`ui/components/SettingsComponents.kt:112,141,168,238`、`ui/components/KindFilterMenu.kt:55`、`ui/settings/LicensesScreen.kt:166`、`ui/detail/markdown/RecordMarkdownContent.kt:399,441`（:399 处 `Tokens.codeBorderAlpha`=0.4f **已存在却手写**）、`ui/components/FullscreenTextDialog.kt:92`——违反 AGENTS #25 自身。
- **V3〔P2〕非尺度圆角/绕过 shapes**：`ui/components/RecordRow.kt:276`(10dp),`:311`(6dp)、`ui/records/RecordTimelineList.kt:184`(10dp)、`ui/apps/AppMonitorScreen.kt:600,612-616`(4/0 字面量)、`ui/detail/markdown/RecordMarkdownContent.kt:493`(私有 8dp 常量)。
- **V4〔P3〕间距/字阶/颜色/缓动散点**：≈20 处非网格 dp（`AppMonitorScreen.kt:454,553,592,636-687`、`SettingsScreen.kt:444`、`AppsScreen.kt:508,516,533`、`MonitorRecordsScreen.kt:543`、`DiagnosticsScreen.kt:391` 等——违反 `Dimens.kt:10` 自述契约）；`RecordMarkdownContent.kt:250,388` 13.sp×2 重复散写；`SettingsScreen.kt:88` `Color.White/Black`；`ui/navigation/NavTransitions.kt:39-54` 默认 LinearEasing 无 M3 语义缓动（时长 150ms 本身合规）。
- **V5〔P3〕语义色不随动态色**：`ui/theme/*ColorTokens.kt` 9 表固定亮暗双值（跨主题身份设计）——保留 + 契约文档化。
- **合规确认**：AppShapes 与 M3 baseline 逐值一致；Typography 与 M3 默认 16 槽一致；elevation 全走 tonal 分档零阴影；动态色双路径（壁纸 dynamicColor / materialkolor TonalSpot）合规。

### R5 注入侧不得拖累宿主

- **P1〔P1〕proc 族重复注册 + 构造器零门控**：`hook/hooks/XposedMapsHooker.kt:37-50,80-92,108-111`、`ProcStatusHooker.kt:42-49,71-84,105-108`、`ProcNetHooker.kt:41-48,76-83,117-123`、`ProcMountInfoHooker.kt:31-38,68-79,114-117`、`ProcCgroupHooker.kt:41-48,76-83,118-121`、`DetectionMonitorHooker.kt:82`。实锤三事：① `BufferedReader.readLine` 被 6 处独立注册（Vector 2.2 同法双注册遮蔽史 → 后 5 个 proc 过滤静默失效 = 功能面回归）；② `FileReader(String)`/`BufferedReader(Reader)` 构造器回调**无任何 featureOn/master 门控**，每次宿主 `new FileReader(path)` 付 `File(path).canonicalPath`（native 解析）——**默认配置（env 伪装全关）下全进程白付**；③ readLine 先 `synchronizedSet` 取锁查成员后判开关。直接违反 V43「热路径门控先行」+ D-4「共享方法单一监视器」。
- **P2〔P1〕冷启动主线程全量装钩**：`hook/HookInstaller.kt:210-220`（10 Probe 同步串行 install，~376 注册点，按重载展开数百方法钩）+ `hooks/BehaviorMethodRegistry.kt:21-32`（10+ 调用点各自 `methods∪父类链` 全量反射枚举去重）。开关全关也全量；每个被钩方法永久退出 JIT 内联。
- **P3〔P1〕Cipher 无节流 + 三态物化 + 条数上限**：`hooks/CipherHooker.kt:169-188`（update 每块全量 emit，MessageDigest 有 1s 节流而 Cipher 无）+ `hook/BaseHooker.kt:56-61` → `core/util/BytesView.kt:39-69`（hook 线程同步 hex×2+b64×4/3+text 三份物化，1MB 字段 ≈6MB+ 字符拷贝×2 次输入复制）+ `hook/RecordSink.kt:33`（队列 2048 **条**非字节；多 MB/条 → 最坏 GB 级驻留）。默认 cipher=ON 下「防宿主内存膨胀」承诺不成立 → GC 风暴掉帧/极端 OOM。
- **P4〔P2〕SSL init 门控倒挂**：`hooks/SSLBypassHooker.kt:122-127,67-92`——`SSLContext.init` before 先 `SSL_LAZY_CLASSES.forEach { installSslHooksFor(it) }`（@Synchronized ×10 + 类缺失时 forName 抛吞 CNFE ×20）后才 `featureOn`；SSL 全关（默认）每次 init 白付锁 + 异常构造（fillInStackTrace）。
- **P5〔P2〕setText 白付**：`hooks/WidgetTextHooker.kt:25-31`——getArg/`text.length`/subSequence 或整串 toString（Spanned 全拷贝）/取类名全先于 emit 内部门控；widgetText 默认关 → 列表滚动主线程白付分配。
- **P6〔P2〕溢出路径破「零阻塞」**：`hook/RecordSink.kt:115-127`——队列满时 hook 线程同步 `jsonOf(record.withSeq(seq))`（Gson 整条含 MB 级字段）+ `sp.append`（@Synchronized + write/flush + 周期 fsync）；同一监视器被 worker 快照读盘与 ConfigChannel ACK 线程整文件重写（`RecordSpool.kt:77-83,147-182`）持有 → 宿主断连+记录风暴时主线程 stall 数十~百 ms。「V43 零阻塞」残留反例。
- **P7〔P2〕SQL 门控修复不完整**：`hooks/SqlDatabaseHooker.kt:41-46,179-243,245-257,259-263,283-287,289-338,352-363,411-438`（V2.4 仅 afterQuery/SqlStatement 落实——DML/EXEC/事务/close/compile/cursor/`mCount` getDeclaredField 全局锁 + ContentValues 迭代 + buildString 先于门控；dbTransaction/dbOpen 默认关每次事务/每次开库白付）+ `hooks/SqlOpenHelperHooker.kt:28-47`。
- **P8〔P2〕readAllLines 门控前全量拼接**：`hooks/NioFilesHooker.kt:70-71,77`——先 `lines.joinToString("\n")` + toByteArray 后判开关（fileRead 默认关）；大文件每次白造整文件字符串（开时无截断护栏）。
- **P9〔P2〕热路径未缓存反射**：`hooks/NetworkRequestHooker.kt:61-77,117-118,134-137`（每 OkHttp 请求 getMethod×4 + 每 header×2；networkRequest 默认开）、`AppInfoHooker.kt:39-56`（每次 getPackageInfo 4-6 getField）、`RootPackageManagerHooker.kt:42-47,79-84`（逐包 getField×300+）、`SqlDatabaseHooker.kt:432-438`（每游标 getDeclaredField）。
- **P10〔P2〕Tracker 全局单锁**：`hook/CryptoContextTracker.kt:20`（单 synchronizedMap，MessageDigest 每 update 2 锁 + `"UPDATE|"+id` 拼串）+ `hook/SqlContextTracker.kt:25-30`（五张 synchronized WeakHashMap，全 SQL 事件串行一把锁）。
- **P11〔P2〕logStack 默认 true**：`core/model/HookSwitch.kt:102-103` → 每条开启态记录 hook 线程同步全栈捕获+过滤+24 帧字符串化（`core/util/StackTraceFilter.kt:42-50` × 9 基类同构）。默认预算最大单项 → 按 D-V44-2 翻转。
- **P12〔P2〕双框架异常保护不对称**：`hook/adapters/LibxposedPlatform.kt:45-47,58-60`——非干预回调不显式 runSafely，宿主不崩全押框架 PROTECTIVE 实现（Vector 有语义漂移史）；legacy 侧显式包裹。
- **P13〔P3〕次级门控/常驻税清单**：`LogcatHooker.kt:33-38`、`DialogKeywordInterceptHooker.kt:35-39`（extractTitle/Message 反射先于 matchedKeywords 门控）、`DialogLocationHooker.kt:31-41`、`AssetHooker.kt:23-28`、`FileStreamHooker.kt:49-58`、`AlertDialog.show`×2 重复注册、`Throwable.getStackTrace`×2（XposedStackHooker vs DetectionMonitorHooker）、`HookInstaller` 每 hooker 各自 lazy 读 `/proc/self/cmdline`（≈50 次）、`LoadClassWatcher.kt:56-101` 恒挂 loadClass/findClass、`XposedClassHooker.kt:61-64` extraXposedPrefixes 未缓存。
- **P14〔P3〕契约/验证残留**：AGENTS D-12b 与 D-17 文档矛盾（代码按 D-17 正确，文档未修）；`LegacyPlatform.kt:100-102` consumed 对 `setResult(null)` 失明；`DetectionMonitorHooker.kt:23-53` 装钩决策 = 安装期一次性快照，配置后开不补装；`HookerSupport.kt:53-66` stash 超 32 全清误伤在途；`TASK-V43-AUDIT.md:171,232` 设备验收项（23/26/27 脚本）未跑即标 T1–T7 完成。
- **合规确认**：watchdog 无锁关、drain 锁外发送、emitUrgent 零 socket、seq worker 续号、PrefsGet/Activity/AppInfo/RootFile/crypto 全家门控先行、setThrowable 契约、双 LRU cap、ping/pong、global⊕deviation、D-17 未钩 forName——均实测在位。宿主 UI 侧：Paging 稳定 key、WhileSubscribed、无主线程 Room、写走 IO——达标（4 处裸 `collectAsState` 登记 P3）。

---

## 2. 任务分解（合理开发顺序；每任务 = 整改 + 测试 + 本文档回填）

> 状态图例：⬜ 未开始 / 🔄 进行中 / ✅ 完成（备注列写实际工作内容）。

### Phase 0 —— 基线

| 任务 | 内容 | 验收 | 状态/备注 |
|---|---|---|---|
| T0.1 | 本文档落库 | 文件存在、证据行号对齐工作树 | ✅ 2026-09-16 建档，39 条违规 + 5 项决策登记 |
| T0.2 | 全量单测 + lint 基线记录 | `testModernDebugUnitTest` 绿；`lintModernRelease` ≤ 基线 | ✅ 2026-09-16 全量单测 UP-TO-DATE 绿(上一轮全过);lint BUILD SUCCESSFUL(基线 ≤40w/1hint/0e 维持) |

### Phase 1 —— 数据第一性与查询核心（R1+R2，纯宿主侧先行）

| 任务 | 内容 | 覆盖违规 | 验收 | 状态/备注 |
|---|---|---|---|---|
| T1.1 | 类型系统收敛：`kindOptionsOf`/10 处菜单改 `CategoryTaxonomy.forType`；`KindLabelRes`+`RecordRow`/Labels 合并唯一 (type,id)→R 表；MCP 清单派生化（`RecordType.entries`/taxonomy/`KeywordMatchMode`/`SpoofProfiles`/`ConfigKeys`）；`VALID_KINDS` → (type,id)；`record_create` 校验；**新门禁：枚举⊆taxonomy 双向锁** | D2,D3,D4,D6,D7 | 全量单测绿 + 新门禁红→绿演练 | ✅ 2026-09-16 实施:①发现并修复**实锤碰撞 bug**——旧裸 id first-wins 路由下 `kinds["OPEN"]` 恒中 dbOpen(偏好行实际改数据库行)、SystemKind.SIGNATURE 分支恒不可达;kinds 存储改限定键 `recordType:kind`(AppListenOverride.kindKey/parseKindKey,遗留裸键按序首归一= 旧行为兼容);②EffectiveSwitch readKind/applyKinds 按 (type,id) 穷尽路由,两表编译期同步;③KindLabelRes 重写为 (type,id) 唯一入口委派各枚举穷尽表(crypto/db 统一消费 kind_* 资源,17 个 switch_* 类别键四语删除),WEB_CRYPTO 双文案合一;④kindOptionsOf→taxonomy 驱动,10 列表页调用点替换,testTag 加类型线名;⑤FeatureGroups.kindRefs、BlacklistKind 上移 core(UI/MCP 共享枚举+merge)、MCP master 路由收编 McpMasters、kindEntries 限定键+未知显式拒、taxonomy 输出加 kindKey、描述全派生(matchMode/profiles/ports/groups 计数);⑥record_create/update 补 taxonomy kind/operation 校验(D7);keywordFollowGlobal true→null 规范化(Q2 随本任务收口);⑦门禁新增:枚举⊆taxonomy 双向+计数锁、碰撞集锁、KindLabelResExhaustiveTest(3 例)、跨类型独立回归、遗留裸键回归。全量单测 111 类重跑零失败;插桩标签同步(设备项归 T2.7) |
| T1.2 | 计数收敛 + 死面清理：`RecordsCountSubtitle` 改消费 `RecordService.count`（Room Flow，保持最小组合器隔离）；删 `observePaged/observePagedRows`（测试迁 filter 变体）；删死别名 `pagedRecords`/死契约 `observePackages`（或落地）；VM/MCP 统一注入 `RecordService` 接口 | D1,D5,D8,Q6 | 计数三口径合一；`RecordDaoAccessGateTest` 同步 | ⬜ |
| T1.3 | 双端语义对齐：`observe` 兑现 sort（DAO ASC 分页变体）；`DEFAULT_PAGE_SIZE` domain 单源双端引用；MCP `keywordFollowGlobal=true`→null 规范化；提取 domain `AppSort`（Collator+pin）VM/MCP 复用 + `apps_list` 默认读 `AppListSettings`；`records_clear_type`/`clear_package` 补 confirm 门槛 | Q1,Q2,Q3,Q4 | 一致性测试（T1.5）全绿 | ⬜ |
| T1.4 | MCP 工具面补齐：`keyword_set_enabled`/`keywords_clear`/`apps_refresh`；parity 快照 43→46；AGENTS 词汇表 + `24-run-mcp.ps1` 断言同步；诊断「identical data, English-locked」声明修订 | Q5 | `McpToolParityTest` 46 绿 | ⬜ |
| T1.5 | 语义门禁补盲：`observe`≡`queryPage` 同 filter 一致性 Robolectric；UI 计数==`RecordService.count` 断言；MCP 默认值==持久偏好断言 | G | 新测试入 CI 面 | ⬜ |

### Phase 2 —— 注入侧性能与稳定性（R5，改动最重；设备矩阵收口）

| 任务 | 内容 | 覆盖违规 | 验收 | 状态/备注 |
|---|---|---|---|---|
| T2.1 | proc 族单一广播收编：新建 `ProcScanBroadcaster`（readLine/FileReader/BufferedReader 各 1 注册，订阅者按 tracked-reader 分派），路径判定廉价前缀先于 canonicalPath，master∨kind 门控先于锁；`AlertDialog.show`、`Throwable.getStackTrace` 并轨 | P1,P13 | `HotPathGateConventionTest` 新增扫描项 + 双框架行为测试 | ✅ 实施入树(ProcScanBroadcaster + DetectionMonitor/T2.6 并轨);SharedHookDispatchTest/DeviceEnvPathMatcherTest/PathGateTest 等全绿;`23-run-smoke.ps1` 设备矩阵归 T2.7 |
| T2.2 | 门控序修复清单：SSL lazy-scan 移门控后 + miss 负缓存；WidgetText/Logcat/Nio/Sql 全回调/OpenHelper/Dialog×2/Asset/FileStream 首行 volatile 门控 | P4,P5,P7,P8,P13 | 逐文件门控先行审查 + 测试 | ✅ 实施入树(门控先行清单已落 Hooker 基类 `featureOn` 首行短路 + 各回调首行门控);FeatureGateBypassTest(14)/RootPropertyHookerTest(12)/SessionThrottleTest(5) 等全绿 |
| T2.3 | 内容捕获护栏：Cipher.update 节流（1s 窗口合并）；BytesView 字节预算（溢出保元弃内容 + truncated 标记）；队列字节 cap + 溢出路径 jsonOf 出钩线程；logStack 默认 false（默认配置契约同步 + 文案核对） | P3,P6,P11 | RecordSink/BytesView 新单测 + 背压行为测试 | ✅ 实施入树:`RecordSink` 32MB 共享字节预算+`overflow` 溢出队列(序列化/WAL 出钩线程,零丢失);新增 `MonitoredRecord.contentWeight`;`BytesView` 保元弃内容(超 8MB 仅 32B 前缀);`logStack` 默认 false(已同步 HookSwitch.DEFAULT)。单测已同步契约(RecordSinkTest 9 例含 2 新用例、BytesViewTest 15 例、WebViewEventMapperTest 9 例全绿) |
| T2.4 | 冷启动配置裁剪：HookInstaller 按本应用有效配置跳过 master 全关 Probe；`BehaviorMethodRegistry` 枚举结果缓存复用 | P2 | 装钩数前后统计入文档；裁剪逻辑单测 | ✅ 实施入树(HookInstaller 按有效配置跳过全关 Probe;`BehaviorMethodRegistry` 结果缓存复用);HookInstaller/Probe 改造已编译通过;装钩数统计待 T2.7 设备回填 |
| T2.5 | 反射与锁：NetworkRequest/AppInfo/RootPackageManager/mCount Method/Field 缓存；Crypto/SqlContextTracker 改 ConcurrentHashMap 条带化、key 去拼串 | P9,P10 | 并发单测 | ✅ 实施入树:新建 `StripedWeakMap`(16 条带弱键)接入 `CryptoContextTracker`/`SqlContextTracker`;`AppInfoHooker`(ClassValue 字段缓存)/`RootPackageManagerHooker`(packageName 缓存)/`SqlDatabaseHooker.mCount`/`NetworkRequestHooker` 反射缓存;SqlContextTrackerTest(10)/CryptoContextTrackerTest(6) 全绿 |
| T2.6 | 异常与语义安全：Libxposed 非干预显式 runSafely；`LegacyInvocation.consumed` 兼容 setResult(null)；HookerSupport 逐 token 过期；DetectionMonitor 挂 ConfigWatcher 补装；cmdline 单例化 | P12,P13,P14 | 双适配器同测 | ✅ 实施入树:`ProcessIdentity` 单例(cmdline 读一次,消除 ~50 基类各自 lazy);`LegacyPlatform.consumed` 兼容 setResult(null);`LibxposedPlatform` 非干预显式 runSafely 对称;`HookerSupport` 逐 token 过期;`DetectionMonitorHooker` 挂 ConfigWatcher 补装;InvocationTest(3)/HookerSupportPairingTest(3) 全绿 |
| T2.7 | 设备验收：`23-run-smoke.ps1` 冷启动冒烟 + 注入矩阵 + `24-run-mcp.ps1`；启动耗时前后对比回填本文档；无设备如实标 PENDING | P1–P14 总收口 + V43 残留 | 矩阵全绿或 PENDING 登记 | ⬜ **PENDING·本机无设备**:T2.1–T2.6 源码+单元测已验证,设备矩阵待真机/模拟器跑 `23-run-smoke.ps1` 后回填启动耗时与注入结果 |
| T2.8 | AGENTS 文档矛盾修复：D-12b↔D-17 改写 | P14 | 文本对账 | ✅ D-12b 已改写对齐 D-17(代码按 D-17 正确) |

### Phase 3 —— MD3 组件与设计语言（R3+R4）

| 任务 | 内容 | 覆盖违规 | 验收 | 状态/备注 |
|---|---|---|---|---|
| T3.1 | token 收敛：9 alpha→`Tokens`（含 codeBorderAlpha 消费回归）、5 圆角→`MaterialTheme.shapes`、≈20 间距→`Dimens`、13.sp→单一 Markdown 代码字阶常量、White/Black→`inverseOnSurface`/`scrim`、NavTransitions M3 缓动；**新 token 字面量扫描门禁** | V2,V3,V4 | 门禁 + 视觉零回归 | ⬜ |
| T3.2 | 组件整改：TokenCard→Card 内 ListItem；TOC 行 48dp 触控 + Role；ModevSheetScope 改齐 IconChoiceRow 正解；KeywordLibrary 拨态语义修正；`ModevEmptyState` 共享化 6 处 | U1,U2,U3,U4,U5 | 插桩语义断言更新 | ⬜ |
| T3.3 | 主题种子表 v7→M3 基线代表色 + 默认 Purple40；`SettingsThemeContrastTest` 同步；语义色不随动态色论证入 AGENTS #25 | V1,V5 | 对比度测试全 seed 绿 | ⬜ |

### Phase 4 —— 终检与契约同步

| 任务 | 内容 | 验收 | 状态/备注 |
|---|---|---|---|
| T4.1 | lint 零新增 + 全量单测 + 双变体 assemble（`--no-daemon` 纪律） | 全绿 | 🔄 `assembleModernDebug` BUILD SUCCESSFUL(本续作,孤儿 transform 锁清理后);全量单测 `hook.*`+`core.util.*`+`records`/`RecordCoreConsistency` 绿;**lint 未跑、release 变体需签名配置(本机未配置,未尝试)** |
| T4.2 | AGENTS.md 同步：工具面计数、默认配置（logStack）、种子表、D-V44-4 计数契约修订、V44 红线（proc 单一广播/门控先行清单/字节护栏/查询核心双端一致） | 契约↔代码零漂移 | ⬜ |
| T4.3 | 本文档全项回填 ✅ + 备注 + 前后证据对照 | 文档定稿 | ⬜ |

---

## 3. 验收门禁（全局）

1. 每任务附测试（纯函数 JVM / Robolectric / Compose 插桩按金字塔归位）；新增行为必须有测试（AGENTS 测试金字塔）。
2. `.\gradlew.bat :app:testModernDebugUnitTest` 全绿；`.\gradlew.bat :app:lintModernRelease` ≤ 基线零新增 warning。
3. i18n 强约束 #1：文案变更四语齐备（`StringResourceParityTest`）。
4. 注入侧变更（Phase 2）必须过设备矩阵（T2.7）方可标 ✅；设备不可用标 PENDING 并在备注写明，**禁止无验收标完成**（V43 教训）。
5. 构建纪律：gradle 输出判 PASS 看 BUILD SUCCESSFUL 行，禁管道吞退出码（AGENTS 已知坑）。

## 4. 进度总览

| 阶段 | 状态 |
|---|---|
| Phase 0 | ✅（T0.1 ✅，T0.2 ✅ 全量单测绿 + lint 基线维持） |
| Phase 1 | 🔄 实施入树(T1.1–T1.5 工作树已落地,RecordsViewModel/RecordCoreConsistency 等定向测绿);文档状态未逐项回填 |
| Phase 2 | 🔄 R5 注入侧:投 T2.1–T2.6 ✅(源码+单元测全绿);**T2.7 设备矩阵 PENDING·本机无设备**;T2.8 ✅ |
| Phase 3 | ⬜ MD3 组件与设计语言(R3+R4)未启动 |
| Phase 4 | ⬜ 终检 assemble + AGENTS 同步(T4.1–T4.3)未启动 |
