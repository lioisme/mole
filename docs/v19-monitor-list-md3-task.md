# Modev 任务书 V19:监听列表界面 · 内容三态(String/Base64/Hex) · MD3 组件化 · 设置页整理

**文档版本**: v1.0 | **创建日期**: 2026-08-20 | **状态**: 🚧 进行中
**适用范围**: `ui/navigation/ModevRoutes.kt`、`ui/ModevNav.kt`、`ui/apps/{AppsScreen,AppMonitorScreen}.kt`、`ui/settings/{AlgorithmMonitorScreen,DatabaseMonitorScreen,MonitorSettingsScreen*,SettingsScreen,DataSettingsScreen,DiagnosticsScreen,SettingsRowItems}.kt`、`ui/components/{RecordRow,SettingsComponents,KindFilterMenu*}.kt`、`ui/logs/RecordDetailScreen.kt`、`core/util/BytesView.kt`、`core/model/CryptoRecord.kt`、`data/db/{CryptoRecordEntity,ModevDatabase,CryptoRecordDao}.kt`、`data/mapping/CryptoRecordMappers.kt`、i18n(en/zh)、测试三件套、AGENTS.md
**质量目标**: 单一事实源、零弃用 API、i18n 零硬编码、全控件 MD3 组件化、性能底线(分块虚拟化/护栏不回归)、门禁全绿(基线 38 warning / 0 error)

---

## 0. 需求条款(用户口径 + 已确认决策)

| # | 需求 |
|---|------|
| R1 | 单击应用列表进入的应用监听入口页(算法监听 / 数据库监听 / 查看记录)存在;**单击「算法监听」「数据库监听」进入的界面是两种监听的记录列表界面(非设置界面)**;监听列表界面仅 AppBar(含搜索、筛选等内部组件)+ 列表;算法监听与数据库监听是两个独立界面,不合并 |
| R2 | 算法内容要求支持 **String、Base64 和 Hex 三种**表示 |
| R3 | 所有控件一律使用 **MD3 组件库**,禁止其他库或手搓自研(如对话框须经典 MD3 样式) |
| R4 | 整理设置界面的「数据管理 / 服务与诊断」等功能 |

已确认决策:
- D1 开关设置页去向:**合并为「监听设置」一页**(`settings/monitor`,四分区:算法类别 / 记录选项 / 数据库类别 / 结果闸门);设置页功能区仅一个入口「监听设置」
- D2 全局监听列表:**仅类别筛选 + 搜索词**(不增加按应用过滤);行含包名(`RecordRow.showPackage` 参数)
- D3 三态实施形态:**字节区块内 `SingleChoiceSegmentedButtonRow` 三选切换**(文本/Base64/Hex),复制菜单同步三项
- D4 MD3 严格度:**交互控件全部替换为 MD3 组件**;纯视觉原语(StatusDot/EmptyState/TimelineHeader/AppIcon 占位块/计数徽标/色板圆块/MetadataRow)保留为 MD3 原语组合

## 1. 全量审计(现状 + 根因)

### 1.1 R1 入口与导航差距

| # | 位置 | 现状 |
|---|------|------|
| E1-1 | `ui/apps/AppMonitorScreen.kt:146-183` | 入口页三入口已存在(V18 未提交) |
| E1-2 | `ui/ModevNav.kt:150-155` | `onOpenAlgorithm/Database → ALGORITHM_MONITOR/DATABASE_MONITOR` = `settings/algorithm`、`settings/database`,指向**开关设置页**(AlgorithmMonitorScreen/DatabaseMonitorScreen),非列表页 |
| E1-3 | `ui/settings/SettingsScreen.kt:198-222` | 设置页「功能」区 = 能力注册表双入口(同标题「算法监听/数据库监听」)+ 数据管理 + 服务与诊断;与列表页将同名混淆 |
| E1-4 | `ui/components/SearchInputComponents.kt` / `ui/apps/AppRecordsScreen.kt:608` | 列表基础设施已就绪:rememberSearchController、KindFilterMenu(双组单选)、RecordRow、TimelineHeader、Paging3 均可复用 |
| E1-5 | `data/repo/LogRepository.kt:231-256` | `pagedRecords(packageName, kindName, query, recordType)` 已支持 recordType 维度 → 全局列表零数据层改动 |

### 1.2 R2 三态差距

| # | 位置 | 现状 |
|---|------|------|
| E2-1 | `core/util/BytesView.kt:13-58` | 仅双态 `hex + text`(UTF-8 可打印判定),无 Base64 |
| E2-2 | `data/db/CryptoRecordEntity.kt:37-73` | 每字节字段 4 列(Hex/Text/Length/Truncated),无 B64 列 |
| E2-3 | `data/db/ModevDatabase.kt` | Room v6;迁移先例 = 纯增量 `ALTER TABLE ADD COLUMN`(MIGRATION_5_6) |
| E2-4 | `core/codec/CryptoRecordCodec.kt:21-23` | wire 经 Gson 整对象传输,新增可空字段双向兼容(新→旧忽略,旧→新 null) |
| E2-5 | `ui/logs/RecordDetailScreen.kt:780-907` | 复制菜单三项(文本/Hex/文本+Hex);双层渲染 Tier A/B + ChunkedText + 4M/32M 护栏已就绪 |

**关键约束**:Base64 必须捕获侧由原始字节生成并持久化;hex 为截断前缀,**运行时反推 Base64 会静默截断,禁止**。旧记录(base64=null)渲染降级双态(Base64 段隐藏)。

### 1.3 R3 MD3 合规差距(手搓交互控件清单)

| # | 组件 | 位置 | 替换 |
|---|------|------|------|
| E3-1 | `RetryRow` | AppRecordsScreen.kt:703 | `TextButton` |
| E3-2 | `SettingsItem/ToggleItem/InfoItem` | SettingsComponents.kt:64-182 | M3 `ListItem` |
| E3-3 | `OptionRowItem` | SettingsRowItems.kt:46 | M3 `ListItem` |
| E3-4 | `AppRowItem` | AppsScreen.kt:396 | `ListItem`(trailing=Checkbox) |
| E3-5 | `AppHeaderRow` | AppRecordsScreen.kt:466 | `ListItem` 结构 |
| E3-6 | `DirectionChip` | RecordRow.kt:219 | `AssistChip` |
| E3-7 | `StatsToggleRow` | DiagnosticsScreen.kt:188 | `ListItem` + chevron + AnimatedVisibility |
| E3-8 | 三个选择 Sheet 单选行 | SettingsScreen.kt:336/402/449 | `ListItem` + selectable(RadioButton 角色) |
| E3-9 | `CollapsedSearchInput` | SearchInputComponents.kt:120 | **例外项**:焦点/长按行为有文档化理由,保留 + KDoc 固化;强制替换须先过行为回归 |

已合规(不动):SearchBar/ExpandedFullScreenSearchBar、DropdownMenu 系列、Checkbox/RadioButton、ModalBottomSheet(输入编辑)、AlertDialog(危险/关于)、Switch、TextButton、Card、Snackbar、TooltipBox/PlainTooltip、TopAppBar 系列、OutlinedTextField、PullToRefreshBox、ReadMoreText。

### 1.4 R4 设置页差距

| # | 位置 | 现状 |
|---|------|------|
| E4-1 | `ui/settings/DataSettingsScreen.kt` | 仅「总记录数 + 清空全部」;无类型统计/存储占用/按类型清理 |
| E4-2 | `ui/settings/DiagnosticsScreen.kt` | 框架/端口/在线目标 + 手搓折叠统计;无动作能力 |
| E4-3 | `data/repo/LogRepository.kt:305` | `observeRecordTypeCounts()` 已有(分类统计基础) |

## 2. 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 开关设置页去向 | 合并 `MonitorSettingsScreen`(`settings/monitor`):①算法监听(总开关+12 类别)②记录选项(logStack/maxBytesPerField/ignoreStackPrefixes)③数据库监听(总开关+5 类别)④结果闸门(maxResultRows/maxResultChars);删除 AlgorithmMonitorViewModel/DatabaseMonitorViewModel;删除路由 ALGORITHM_MONITOR/DATABASE_MONITOR(无存量引用);`CapabilityUiRegistry` 双条目退场(领域 CapabilityRegistry 保留) |
| D2 | 全局列表范围 | `records/algorithm` / `records/database` 两路由;仅类别筛选(CryptoKind 12 / SqlKind 5 单选)+ 搜索词;`RecordRow` 增 `showPackage` 参数(默认 false);无应用头部/启动/危险操作;时间线分组复用 |
| D3 | 三态实施 | `BytesView.base64: String?`(捕获侧生成,NO_WRAP;上限 = HARD_MAX_HEX_CHARS×2/3);Room v7 迁移 7 列;wire Gson 自动携带;详情页 SegmentedButton 三选切换 + 复制菜单三项(移除「文本+Hex」);旧记录降级双态 |
| D4 | MD3 严格度 | 交互控件全换(§7 矩阵);视觉原语保留;CollapsedSearchInput 例外文档化 |

## 3. 改动清单(执行顺序)

- [x] **T1** 本任务书落盘 `docs/v19-monitor-list-md3-task.md`
- [x] **T2** 数据层三态:BytesView.base64 + 生成护栏;Room v7 迁移(7 列纯 ALTER TABLE);entity/映射/codec;`CryptoRecordMappersTest`/`BytesViewTest`/迁移 Robolectric 单测
- [x] **T3** 两个全局监听列表页:路由 `records/algorithm`/`records/database`;`AlgorithmRecordsViewModel`/`DatabaseRecordsViewModel`;`AlgorithmRecordsScreen`/`DatabaseRecordsScreen`(AppBar 仅 SearchBar + 搜索/筛选 + 分页时间线列表);共享 `KindFilterMenu` 抽取 `ui/components/KindFilterMenu.kt`(固定类型参数化);`RecordRow.showPackage`
- [x] **T4** 监听设置合并页:MonitorSettingsScreen/ViewModel(四分区 + 三个 sheet 迁入);ModevNav/SettingsScreen 功能区收敛(单入口「监听设置」,副标题 = 算法/数据库启用态组合文案);删除旧两设置页/VM/路由
- [x] **T5** 详情页三态渲染:字节区块 SegmentedButton(文本/Base64/Hex,rememberSaveable 每区块记忆,无文本时隐藏文本段);内容按所选表示渲染;Tier B 三表示 ChunkedText;复制菜单三项;摘要/列表行保持 hex 首 24 字符不膨胀
- [x] **T6** MD3 组件化矩阵(§7 逐项替换):RetryRow→TextButton;SettingsComponents 系列→ListItem;OptionRowItem→ListItem;AppRowItem→ListItem;AppHeaderRow→ListItem 结构;DirectionChip→AssistChip;StatsToggleRow→ListItem;三个选择 Sheet 行→ListItem;RecordRow→Card+ListItem;CollapsedSearchInput 例外 KDoc 固化
- [x] **T7** 数据管理页重构:分类计数(算法/数据库)+ 存储占用 + 按类型清理(危险 AlertDialog)
- [x] **T8** 服务与诊断页重构:分区化 + StatsToggleRow ListItem 化 + 「复制诊断信息」动作
- [x] **T9** i18n 双语补齐(键对 diff=0,plurals 规则)
- [x] **T10** 测试更新与新增(见 §4);FourScreensNavTest 同步新 IA
- [x] **T11** 质量门禁:双变体单测 + lint 零新增 + androidTest 编译 + en-zh 键对 diff=0 + 死键/死路由 grep
- [x] **T12** AGENTS.md 契约更新(路由表、三态契约、MD3 组件规则、监听设置单页)

## 4. 测试计划

| 层 | 用例 | 断言要点 |
|----|------|----------|
| 单测(新/更新) | `BytesViewTest` | base64 生成/截断/护栏上限;text 判定不回归 |
| 单测(更新) | `CryptoRecordMappersTest` | entity↔模型 base64 往返;旧 JSON 缺 base64 → null |
| Robolectric(新) | 迁移测试 | v6→v7 存量数据保留 + 新列默认 NULL |
| 单测(更新) | `RecordDetailTextFormatTest`/`SectionRenderModeTest` | 三态表示决策纯函数 |
| 插桩(新) | `MonitorListScreensTest` | 两列表页:AppBar 仅 SearchBar、筛选按钮 1 个、类别筛选行为级、搜索、空态、返回栈、行含包名 |
| 插桩(新) | `MonitorSettingsScreenTest` | 四分区、总开关互斥置灰、sheet 编辑、返回 |
| 插桩(更新) | `FourScreensNavTest` | 入口页→算法/数据库监听列表页断言;设置页功能区单入口「监听设置」;删除旧 settings/algorithm 断言 |
| 插桩(更新) | `ByteSectionTest` | 三态切换渲染、Base64 复制、Tier B Base64 分块(收起按钮 performScrollToNode) |
| 插桩(更新) | `DataSettings/Diagnostics` 相关 | 分类计数、按类型清理确认、复制诊断信息 |

## 5. 验收标准(Definition of Done)

- [x] R1:入口页两入口直达**全局记录列表界面**(非设置页);两界面独立;AppBar 内仅 SearchBar(含搜索/筛选);列表含类别筛选 + 搜索 + 时间线分组 + 包名行
- [x] R2:详情页字节区块三态可切换、复制三项可用;旧记录(无 base64)降级双态不崩;4M/32M/128K 护栏不回归
- [x] R3:交互控件 100% MD3(§7 矩阵);CollapsedSearchInput 例外有 KDoc 理由;对话框 = AlertDialog/ModalBottomSheet 经典样式
- [x] R4:设置页功能区 = 监听设置 / 数据管理 / 服务与诊断三入口;数据管理含分类计数 + 存储占用 + 按类型清理;诊断页分区 + 复制诊断信息
- [x] 门禁:双变体单测全绿;lint 零新增(基线 38);androidTest 编译通过;en-zh 键对 diff=0;死键/死路由 grep 零残留;AGENTS.md 契约更新

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| Room v6→v7 迁移破坏存量数据 | 纯增量 ALTER TABLE + Robolectric 迁移测试 |
| 旧注入侧/旧记录缺 base64 | 渲染降级双态(Base64 段隐藏) |
| RecordRow 换 ListItem 视觉回归 | RecordRowLayoutTest 同步更新 |
| CollapsedSearchInput 例外合规争议 | KDoc 文档化理由 + 可选的强制替换回归路径 |
| 并行 gradle 守护进程 OOM | 重任务 `--no-daemon --max-workers=1 -Xmx2048m` 串行 |
| 中文注释文件 PowerShell 改写乱码 | 一律 edit/write 工具,禁 PowerShell 文本改写 |

## 7. MD3 替换矩阵(执行核对表)

| # | 替换项 | 目标组件 | 状态 |
|---|--------|----------|------|
| M1 | RetryRow | TextButton | ✅ |
| M2 | SettingsItem | ListItem | ✅ |
| M3 | SettingsToggleItem | ListItem + trailing Switch | ✅ |
| M4 | SettingsInfoItem | ListItem | ✅ |
| M5 | OptionRowItem | ListItem | ✅ |
| M6 | AppRowItem | ListItem + trailing Checkbox | ✅ |
| M7 | AppHeaderRow | ListItem 结构 | ✅ |
| M8 | DirectionChip | AssistChip | ✅ |
| M9 | StatsToggleRow | ListItem + chevron | ✅ |
| M10 | 三个选择 Sheet 单选行 | ListItem + selectable | ✅ |
| M11 | RecordRow | Card + ListItem | ✅ |
| M12 | CollapsedSearchInput | 例外(KDoc 理由) | ✅ 保留 |

## 8. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 任务书落盘 | ✅ | 本文件创建,含里程碑跟踪 |
| T2 | 数据层三态 | ✅ | `Base64Util`(纯 Kotlin,RFC 4648,与 JDK 对拍测试);`BytesView` 增 `base64` 字段与生成(默认上限 = hex 同字节预算 ×2/3,硬保险丝 16M×2/3);Room v6→v7 迁移(7 列纯 ALTER TABLE);entity/映射/JSON 导出三态齐全;BytesViewTest/Base64UtilTest 新增 9 例、CryptoRecordMappersTest 增旧记录降级与 b64 往返、CryptoRecordDaoTest 增 v6→v7 迁移测试 + 三处迁移链补 MIGRATION_6_7;106 单测全绿 |
| T3 | 全局监听列表页 | ✅ | 路由 `records/algorithm`/`records/database`(slideComposable + BackGuarded + viewModel(key 隔离,Factory 带 recordType));`MonitorRecordsViewModel`(query/kindName 双流 flatMapLatest `pagedRecords(packageName=null,…)`);`MonitorRecordsScreen`(Algorithm/Database 双包装,AppBar 仅 SearchBar,展开搜索 + KindFilterMenu,分页时间线列表,行内包名);共享 `KindFilterMenu`/`RetryRow` 抽取;`RecordRow.showPackage` + `RowModel.packageName`;i18n 两搜索占位键;新增插桩 `MonitorListScreensTest` 4 例(类型隔离/AppBar 仅 SearchBar/类别筛选行为级/搜索过滤/返回栈)双设备(25102RKBEC + emulator-5554)全绿;插桩要点:行断言以 `hasTestTag("record-row")` 限定(搜索展开时 Scaffold + Dialog 双份列表),展开态收起须点 Dialog 内返回键(物理返回键不达 Dialog 窗口),`t14.db` 类库名文本走 substring |
| T4 | 监听设置合并页 | ✅ | 路由 `settings/algorithm`/`settings/database` 合并为 `settings/monitor`(旧路由退役,无存量引用;路由头注释同步);`MonitorSettingsScreen` + `MonitorSettingsViewModel`(switchState+setSwitch+Factory,对齐旧两 VM 形状)四分区:算法监听(总开关+12 类别)/记录选项(logStack+MaxBytes+IgnoreStack)/数据库监听(总开关+5 类别)/结果闸门(行数+字节);MaxBytesDialog/IgnoreStackDialog/IntLimitSheet 迁入为私有 MaxBytesSheet/IgnoreStackSheet/IntLimitSheet(ModalBottomSheet + hide 动画后回调);`SettingsViewModel` 重写(弃 capabilities,暴露 `switchState: StateFlow<HookSwitch>`);`SettingsScreen` 签名改 `onOpenMonitorSettings`(删 capabilityUi 参数),功能区单入口「监听设置」(Sensors 图标 + `settings_monitor_summary` 组合副标题:算法/数据库启用词,随开关实时联动)+ 分割线 + 数据管理 + 服务与诊断;删除 AlgorithmMonitorScreen/VM、DatabaseMonitorScreen/VM、`CapabilityUiRegistry` 五文件(领域层 CapabilityRegistry/CapabilityKind 按 DR 保留);`ModevNav` 删 capabilityUi 注册块与两旧路由,增 MonitorSettings 路由;新增插桩 `MonitorSettingsScreenTest` 2 例(入口副标题随总开关联动 + 四分区齐全 + MaxBytes sheet 编辑回写 1234 + 返回栈,@Before 快照 @After 还原开关态防污染),`FourScreensNavTest` 设置页/监听页断言改新 IA(单入口 + 四分区滚动断言);7 例双设备全绿;小清理:MonitorListScreensTest 删未用 pressBack/KeyEvent |
| T5 | 详情页三态渲染 | ✅ | 新增 `ByteRepresentation`(ui/logs,enum TEXT/BASE64/HEX)+ BytesView 扩展纯函数:availableRepresentations(缺文本隐藏 TEXT、base64=null 旧记录隐藏 BASE64)、defaultRepresentation(TEXT 优先退 HEX)、representationText/representationChars;`ByteSectionHeader` 增 SingleChoiceSegmentedButtonRow(按可用表示收敛,label = section_repr_* 新键,hex 恒在);复制菜单改三项(文本/Base64/Hex,删除「文本+Hex」合并项与 `section_copy_all` 死键,Hex 恒在且入口恒展示);byteSectionItems 折叠判定与 Tier 分档改以**所选表示**字符数为基准(短 text 恒全量不回退折叠预览);SelectableSectionContent/byteSectionChunks/SectionPreview 单表示化(分块 key 含表示名,切换表示即换键重组);CryptoLoadedContent rememberSaveable 每区块表示记忆(进程重建保持);新增单测 `ByteRepresentationTest` 10 例;ByteSectionTest 更新 2 例(复制菜单三项/缺失项隐藏)+ 新增 3 例(三态切换渲染/旧记录降级双态/Tier B Base64 分块 100K 字节);i18n 4 新键对(en/zh),StringResourceParityTest 白名单 +2(section_repr_base64/hex 专有名词,对齐 kind_base64 先例);14 例双设备全绿;踩坑记录:一次 PowerShell 文本替换误改测试字面量(违反 AGENTS 禁例),已用 edit 工具回修——教训重申中文/字符串文件一律 edit/write |
| T6 | MD3 替换矩阵 | ✅ | 全量审计:先例 M1(RetryRow 已是 TextButton)/M11(RecordRow 已是 Surface+ListItem 层级)复核通过,剩余 9 项全部落地——M2/M3/M4 `SettingsComponents.kt` 三组件改 M3 `ListItem`(SettingsItem=clickable+ChevronRight@50%、SettingsToggleItem=toggleable(role=Switch)+trailing Switch(clearAndSetSemantics 去重)、SettingsInfoItem=只读,值 Text widthIn(160dp) 上限保住 V11 长值防挤压回归);M5 `OptionRowItem`→ListItem(值 primary 尾部);M6 `AppRowItem`→ListItem(headline 行内含计数徽标、supporting 行内含 StatusDot,trailing Checkbox,testTag("app-row") 保留);M7 `AppHeaderRow`→ListItem 结构(leading AppIcon 40dp/supporting 包名+双 StatusDot/trailing Row=启动+更多菜单);M8 `DirectionChip`→M3 `AssistChip`(类别色淡底 15%+labelSmall,onClick=行点击下传 `CryptoRowContent(rowOnClick)`,行为不回归——原行内区域点击=进详情;删 KIND_BADGE_CORNER);M9 `StatsToggleRow`→ListItem+chevron(展开↑/收起↓,contentDescription 复用);M10 三个选择 Sheet 单选行(Language/Appearance/ColorSheet wallpaper 行)→ListItem+selectable(role=RadioButton,选中 primary SemiBold + Check 尾部,外留 8dp 保持 24dp sheet 留白对齐);M12 CollapsedSearchInput KDoc 标注「MD3 例外项(V19 T6 矩阵 M12)」显式化;中文注释文件全程 edit/write,compileModernDebugKotlin 通过(仅存量 warning);插桩回归发现 ListItem(1.4.0)内容槽**合并为单一语义节点**(文本节点=整行高):`infoItem_longValue` 改断整行 56dp(标题折行必撑高)、`appRecordsHeader` 状态点断言改「双点同行 + 中点位于头部整行中点之下(supporting 排)」;修复后 SettingsComponentsTest+RecordRowLayoutTest+MonitorSettingsScreenTest+FourScreensNavTest 双设备 10/10 全绿,全量单测绿 |
| T7 | 数据管理页重构 | ✅ | `ModevDatabase.NAME="modev.db"` 常量;`CryptoRecordDao.clearByType(recordType)` + `LogRepository.clearByType`;`DataSettingsViewModel` 重写(typeCounts StateFlow + clearByType(RecordType),删非响应式 countOf);新 `StorageFormat.formatBytes`(B/KiB/MiB/GiB,1 位小数,Locale.US,负值归 0)+ `StorageFormatTest` 5 例(修正 1024×1024-1 四舍五入为 "1024.0 KiB");`DataSettingsScreen` 重写三区:记录分类(算法/数据库计数)+ 存储占用(DB 文件大小,`remember(context)` 静态快照)+ 数据(总记录数 + 3 个 DangerRow(errorContainer Surface)危险清理),单 AlertDialog 按目标分发;新增 9 键对 en/zh;compileModernDebugKotlin 通过;全量单测绿(337 例);插桩新增 `DataSettingsScreenTest` 2 例(内存 Room 端到端:种子 2 crypto+3 db → 计数渲染 → 按类型清理确认路径 0/3;真实容器壳:存储值 == 生产同源计算 + 取消路径非破坏),双设备运行其中一次遇模拟器 ADB Connection reset(测试本身 XML 2/2 绿),重跑 BUILD SUCCESSFUL 全绿;踩坑两则:①计数/存储值断言须资源驱动(zh 设备渲染 "2 条" 非 "2",裸数字精确匹配失败);②Room 首查才建库文件,`remember` 快照取 0 B 与断言时 4096 B 竞态——测试先 `openOrCreateDatabase` 预建文件消除 |
| T8 | 服务与诊断页重构 | ✅ | 三分区:框架与作用域(`settings_framework_section` 新键)/ 通信服务(`settings_server_section` 新键,替代并删除整页同名旧键 `settings_service_section`)/ 管道统计(折叠头行即分区标题,去掉外层 SettingsSection 包裹消除双「管道统计」节点);页面底部新增「复制诊断信息」ListItem(ContentCopy,点击组装与页面行同源的剪贴板快照:框架行/端口行/在线行/统计 7 行全量收录(统计无论折叠与否),`"$label: $value"` 行格式,Snackbar `diagnostics_copied` 反馈,与记录长按复制同 UX);新增键对 4 个(en/zh):`settings_framework_section`/`settings_server_section`/`diagnostics_copy`/`diagnostics_copied`;compileModernDebugKotlin 通过;新增插桩 `DiagnosticsScreenTest` 2 例(三分区渲染 + 框架态行按 VM 实际态三态分支 + 端口/在线行;复制 → 读系统剪贴板断言端口子串/统计标签/框架行 + Snackbar),4 例(含 DataSettingsScreenTest 2 例)双设备模拟器全绿;插桩首跑暴露分区化后标题重复缺陷(统计分区标题与折叠行同串 → 2 节点),已按「折叠头即分区标题」收敛;踩坑:编辑多处闭合括号时旧串匹配错位(统计区 SettingsSection 闭合被误删、框架区多出孤儿闭合),编译逐次收敛修复 |
| T9 | i18n 双语补齐 | ✅ | en/zh 键集 diff=0(289/289,脚本核对);`StringResourceParityTest` 全绿(占位符形态一致,新增键均为成对添加);`HardcodedStringConventionTest` 全绿(ui 包零中文字面量,T7/T8 新代码合规) |
| T10 | 测试更新与新增 | ✅ | T7/T8 新增插桩 `DataSettingsScreenTest` 2 例 + `DiagnosticsScreenTest` 2 例(分类计数/按类型清理确认/复制诊断信息);`FourScreensNavTest` 复核新 IA 兼容(设置页功能区单入口监听设置 + 数据管理 + 服务与诊断 + 合并页四分区断言,4/4 绿 1 防御跳过) |
| T11 | 质量门禁 | ✅ | `testModernDebugUnitTest` + `testLegacyDebugUnitTest` 全绿;`lintModernRelease`/`lintLegacyRelease` 0 error / 37 warning(基线 38,零新增);androidTest 双变体编译通过;en-zh 键对 diff=0;死键 grep 零残留(`settings_service_section` 已随 T8 删除双端,旧路由 `settings/algorithm`/`settings/database` 仅 KDoc 历史注释) |
| T12 | AGENTS.md 契约更新 | ✅ | 版本头 V17→V19(2026-08-20);约束 3 改「详情页三态双层渲染」(SegmentedButton 三态 + rememberSaveable 每区块 + 无文本隐藏文本段 + 按所选表示分档 + Tier B 三表示分块);新增约束 13「MD3 组件契约 + 设置页功能区」(控件一律 M3 组件、CollapsedSearchInput 唯一例外、ListItem 1.4.0 内容槽合并单一语义节点 → 插桩勿行内独立测距、功能区三入口路由 + 旧路由退役、危险清理 AlertDialog、计数/存储值断言资源驱动);工具链坑补两条:zh 设备计数文案 "2 条" 须资源驱动断言、Room 首查建库竞态预建文件、模拟器 ADB Connection reset 重跑即可 |