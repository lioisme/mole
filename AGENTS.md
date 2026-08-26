# AGENTS.md

Modev — Kotlin/Compose (Material3) Xposed 模块,算法监听(加解密/签名/密钥/BASE64/WebView)+ 数据库监听 + 行为监听(文件系统/Assets/Shell)+ 偏好监听(SharedPreferences)+ 宿主双进程日志。
构建与质量契约(2026-08-20 审计沉淀 V21;2026-08-25 V24 二态监听/记录收敛/详情重设计增量沉淀;2026-08-26 V26 详情页 markdown 化/外部监听开关/诊断详情弹层增量沉淀;2026-08-26 V27 记录列表重构/行为监听一等公民增量沉淀;2026-08-26 V28 详情页纯 markdown 化/偏好监听一等公民增量沉淀)。

## 构建 / 测试(Windows,gradlew.bat)

```powershell
# 全量单测(含 Robolectric:Room / LocaleManager / 资源一致性 / 数值格式等)
.\gradlew.bat :app:testModernDebugUnitTest

# 指定测试
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.i18n.*"

# 打包(modern 双框架变体;legacy 用于旧 LSPosed;build-all.ps1 打 4 个 APK)
.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug

# 设备 UI 测试(编译 + 安装运行;需已连接设备;指定类可传
# -Pandroid.testInstrumentationRunnerArguments.class=类A,类B,单方法用 类#方法)
.\gradlew.bat :app:assembleModernDebugAndroidTest
.\gradlew.bat :app:connectedModernDebugAndroidTest

# lint 门禁(基线:36 warning / 0 error,V28 维持;提交前必须跑,禁止新增 warning)
.\gradlew.bat :app:lintModernRelease
```

- 前置:JDK 17+(本机 21)、SDK android-36、build-tools ≥34(见 build-all.ps1)。
- 变体名:`modern`(默认,Vector 现代加载)/ `legacy`(Xposed API 82)。
- 设备 UI 测试后 AGP 会卸载被测 APK;失败详情在
  `app/build/outputs/androidTest-results/connected/debug/flavors/modern/TEST-*.xml`
  (PowerShell `[xml]` 解析,`failure` 是字符串属性)。

## 词汇表(全 App 统一,禁止旧词残留)

| 词 | 含义 | 禁用旧词 |
|----|------|----------|
| 记录 record(s) | 算法监听捕获条目 | 日志 log(s) |
| 数据库监听 | SQL 门面/语句/游标结果采集能力(5 类:open/query/DML/exec/transaction) | SQL 注入日志、db hook 日志 |
| 运行中 Running | 进程存活态 | 在线 Online |
| 已作用域 / 未作用域 | Hook 作用域态 | 在作用域 / 未在作用域 |
| 已接收 / 丢弃 / 被拒 / 写入失败 / 超容量裁剪 | 统计区文案(原因括号后缀) | 队列溢出丢弃等 |
| 默认配置 | 内置推荐值 `HookSwitch.DEFAULT`(无 UI;`switch_global` 仅为注入侧兜底种子,init 强制重置) | 全局设置(联动语义)、跟随全局 |
| 自定义偏差 / 恢复默认 | 每应用二态覆盖(V24)与清除动作 | 覆盖=三态、强制开/关、重置为全局 |
| 代码组 CodeGroup | 多表示字节区块的标签页代码块(mikepenz codeFence 扩展点聚合连续 text/base64/hex 围栏) | 三态分段、SegmentedButton(V26 退役) |
| 外部访问 External Access | 服务监听 0.0.0.0(默认停用 = 127.0.0.1 仅本机);诊断页开关即写即重启改绑 | 监听 IP、开放端口 |
| 行为监听 Behavior Listen | 文件读/写/删/重命名/列目录 + Assets 资源 + Shell 命令三类域(V27 一等公民,与算法/数据库并列) | 文件日志、hook 日志 |
| 内容捕获 fileContentCapture | 仅「随调用即可得」的整读 API 正文预览(Files.readAllBytes/readAllLines/write/copy 与 AssetManager.open 元数据;流式路径绝不触碰句柄) | 文件内容抓取、全量读取 |

## 强约束(改了会挂测试/降级质量)

1. **i18n**:用户可见文本一律走 `values/strings.xml`(en)+ `values-zh/strings.xml`;
   新增键必须双语齐备、占位符形态一致(`StringResourceParityTest` 强制);
   `ui` 包源码字符串字面量禁止中文(`HardcodedStringConventionTest` 强制)。
   语言解析:`LocaleManager.resolveTag` = App 内显式选择优先,仅「跟随系统」
   时读系统 per-app locale;重建路径须经 `MainActivity.attachBaseContext` 调
   `LocaleManager.refreshForRecreation`;locale 敏感值用 `remember(context)`
   (包 Context 换实例,不残留旧 locale)。
2. **数值格式**:记录数/统计用 `AppCountFormat`(locale 驱动;zh ≥1万、其他 ≥1K
   平台紧凑格式 ≤1 位小数,以下千分位)。禁止自造单位(万+/千+/K+ 后缀)。
   资源格式串大数用 `%1$,d`。
3. **详情页 markdown 渲染(V26,取代 V21 三态双层契约)**:全部内容区块
   (元数据表/字节/参数/堆栈/SQL)以单一 markdown 文档经 mikepenz
   `multiplatform-markdown-renderer-m3` 渲染(`RecordMarkdownContent` 单管线);
   多表示字节 = 连续 text/base64/hex 围栏聚合为**代码组标签页**(文案复用
   `section_repr_*`,选中态 `rememberSaveable` 按 runFirstOrdinal 记忆),
   单表示退化为单代码卡;Hero 徽章卡与锚点芯片保留原生(D1 边界)。
   版本锁定 **0.38.1**(stdlib 2.2.21 与 Kotlin 2.2.20 同 minor;0.39+/0.44 需
   stdlib 2.3/2.4 超编译器 metadata 上限,**禁升**)。显示上限
    `MARKDOWN_DISPLAY_MAX_CHARS=32Ki`(工厂钳制「所见」,标记行 plurals
   `detail_truncated_marker` 恒置于全部围栏之后——夹在围栏间会打断代码组聚合);
   完整内容经 `DetailFenceHandle` 全量句柄走复制(4M 护栏)/全屏查看器
   (`FullscreenTextDialog`,`ChunkedText` O(1) 分块虚拟化);导出 32M 护栏不变。
   工厂(`RecordMarkdownFactory`)纯 Kotlin 零 UI import,本地化文案参数注入;
   围栏安全 = 内容反引号最长 run+1;GFM 表格用紧凑管道格式 `|Cell|`(解析器会
   把 '| Cell |' 的管道前空格留成单元格尾随字符)。区块恒 1 个 LazyColumn item,
   锚点簿记契约(mark/advance 与 DSL 发射严格同序)不变;
   **LazyColumn 必须显式 `state = listState`**(V26 曾遗漏致锚点滚动静默失效,
   回归测试 `AnchorScrollRoboTest`)。
4. **输入即底部 sheet**:带键盘输入的弹层一律 `ModalBottomSheet`
   (`imePadding` 即可——M3 已内部消费导航栏 inset,**禁止再叠
   `navigationBarsPadding`**,双内边距叠加 = 弹窗底部黑带根因 V21 T2;
   弹窗显式 `containerColor = surfaceContainerHigh`、宿主页 Scaffold 显式
   `containerColor = surface`,hide 动画后回调);
   仅危险确认/关于类保留居中 `AlertDialog`。诊断信息完整值弹层
   (`DiagnosticsDetailSheet`)同规;`SettingsInfoItem` 可选参
   `valueMaxLines`/`onClick`(onClick 追加 chevron,null 零波及既有调用)。
5. **弃用 API 零容忍**:新增源码一律不得引入已弃用 API;`getInstalledApplications`
   走 `core/util/PackageManagerCompat.kt` 统一入口(API 33+ `ApplicationInfoFlags`,
   旧系统弃用分支);`getLaunchIntentForPackage(String)` 全 API 无 flags 重载且从未
   弃用,直接使用即可。`lintModernRelease` 零新增(基线 36 warning/0 error,V27 由 37 净降 1,
   见上文构建命令)。
6. **计数文本复数**:en 带 `%d` 的计数文案用 `<plurals>`(one/other),zh 仅
   `other`;禁止 `<string>` 直接内嵌 `%d + 英文单词`(PluralsCandidate lint)。
7. **Xposed 模块识别双通道**:`isXposedModule` 判定 = 通道 A(`installedApplications`
   一次 `GET_META_DATA` 取 meta-data 含 `xposedmodule` 或 `xposedminversion`,
   零额外 binder)→ 未命中包走通道 B(惰性 zip 只读中央目录查
   `assets/xposed_init` / `META-INF/xposed/java_init.list`,异常降级非模块,
   按包缓存,随 `refresh()` 失效)。`AppListSettings.showXposedModules` 默认
   **false**(模块行默认隐藏,Modev 自身亦隐藏);测试需先 `setShowModules(true)`
   并复位 `setOnlyScoped(false)`(仅作用域按进程过滤会藏行,勾选后持久化,
   跨用例污染)。
8. **筛选菜单语义**:M3 `DropdownMenuItem` 整项 `MergeDescendants` 合并文本与
   OnClick;只读 `Checkbox(onCheckedChange=null)` 不暴露 `ToggleableState`
   语义——勾选态断言不可行,改行为级断言(点击项 → 行现/行隐)。
9. **RecordFilter 查询契约**:`LogRepository.paged(filter)` / `counts(filter)`
   唯一入口(REST/MCP 未来仅依赖 `LogRepository` + `RecordType` + `RecordFilter`);
   `recordType` 可空 = 全部类型。DAO `observeCountByFilter`/`observePagedRowsByFilter`
   用哨兵计数语义(0=过滤关闭,1=启用),空集合须传哨兵值 + `toBindList()`;
   `row.toRowModelOrNull()` 对非法 kind/operation 枚举名返回 null(行被丢弃,
   种子数据必须用合法枚举,如 `MESSAGE_DIGEST`/`CIPHER`/`MAC` +
   `DIGEST`/`ENCRYPT`/`SIGN`)。
10. **时间线分组契约**:`TimelineGroup.secondKey`/`dayKey`/`formatSecond`/
    `formatDay`(纯函数,跨日组头带日期前缀);组合侧邻行比较定组起始
    (`i==0 || prev==null || secondKey 不同`),列表手动索引循环
    (`record==null` 占位,`item(key="h-"+secondKey)` + `item(key=record.id)`);
    分页 prepend 边界 `prev==null` 不渲染头。插桩断言组头须
    `hasTestTag("timeline-header") and hasAnyDescendant(hasText(...))` 限定
    (同秒组头与行尾时间同串,裸 `hasText` 会多命中)。
11. **数据库监听被动采集**:结果集逐行采集仅发生于 App 自身迭代之后;
    hook 内**禁止**移动游标/调用 `getCount` 等探测 API(改变 App 行为);
    行/字节闸门触发即截断补发 `QUERY_RESULT`;未迭代的游标 rowCount=null
    且无结果记录。记录行模型 sealed 分派(`SqlRow`/`CryptoRow`),经
    `toRowModelOrNull` 对非法 kind/operation 枚举名返回 null。
12. **数据库监听开关契约**:5 类开关(open/query/DML/exec/transaction)默认开
    (内置推荐值);每应用类别开关单击即写即推(`pushToPackage` 定向);
    结果上限为「行数 + 字节」双闸门(`IntLimitSheet` 底部输入,禁居中 Dialog,
    V25 起编辑入口 = 每应用「结果闸门」分区);`kind` 列跨枚举共用
    (CryptoKind.name / SqlKind.name),筛选菜单记录类型 + 类别双组单选。
13. **MD3 组件契约 + 设置页功能区**:设置类控件一律 M3 组件
    (`ListItem`/`AssistChip`/`TextButton`/`DropdownMenuItem`…),
    `CollapsedSearchInput` 为唯一例外(KDoc 固化,经 D4 单向决策);
    M3 1.4.0 `ListItem` 内容槽**合并为单一语义节点**(文本节点 = 整行高、
    节点 Text 列表含全部槽文本)——插桩勿对行内文本独立测距,锚定断言用
    `onNodeWithText` 精确匹配合并节点即可。设置页功能区二入口(V25 收敛,
    算法/数据库两全局监听页已整体退役):数据管理
    (`settings/data`)/ 服务与诊断(`settings/diagnostics`,三分区 +
    复制诊断信息);监听配置唯一入口 = 每应用「监听设置」;全局列表页
    `records/algorithm`/`records/database`;
    危险清理(按类型/全部)一律居中 `AlertDialog` 确认,无回收站;
    计数/存储值断言必须资源驱动(`getString` 带格式参,zh 渲染 "2 条"
    非 "2")。设置页卡片容器色 = `surfaceContainerLow`(V21 T1 决策经
    E1/R1 修订最终定档,与 LIDE SettingsSection 逐字一致;T1 曾升
    `surfaceContainerHighest` 应对雷电 ListItem 渲染缺陷——根因 = M3 1.4.0
    ListItem 内部 Surface 默认容器色 Unspecified,嵌套卡内触发雷电转译 bug
    吞掉卡片色;**所有 ListItem 必须显式
    `colors = ListItemDefaults.colors(containerColor = <所在容器色>)`**
    (卡内 = Low / sheet 行 = High / 无容器列表 = surface),显式色后雷电
    渲染修复,卡片回退 Low;`SettingsThemeContrastTest` 纯 JVM 全 20 seed ×
    亮/暗强制:卡片/背景 ≥1.046(亮)/1.078(暗)、onSurface-on-卡片 ≥4.5、
    primary-on-surface ≥4.5,禁止改回 surfaceContainerHighest)。
14. **分页流契约(V20 R3.1 设备测试实锤,两条)**:Room 2.7.2 生成式
    `LimitOffsetPagingSource` 的计数查询 = `COUNT(*) FROM (<原 SQL>)`,
    原 SQL 自带固定 `LIMIT :limit OFFSET :offset` 会钳制 itemCount →
    固定小 limit 时 Refresh 后 nextKey=null、分页死锁于第一页(任何 >60 条
    列表只显示 60 条)。**DAO 分页查询的 `limit` 参数必须传大窗口上限**
    (仓库 `PAGING_WINDOW_CAP = MAX_RECORDS`,外层 loadSize 由 Room 动态
    注入),禁止传 pageSize。列表页 `records` 流一律 `cachedIn(viewModelScope)`
    多播缓存(`PagingData` 每实例单收集:同一实例被第二个 LazyPagingItems
    收集即抛 `Attempt to collect twice from pageEventFlow`——冷流重收集/
    StateFlow WhileSubscribed 热流在详情往返重组合时都会触发;cachedIn 包装
    MulticastedPagingData 为官方解法)。回归测试:`RecordPagingProbeTest`
    (直驱 DAO PagingSource Refresh/Append + 固定小 limit 警戒断言)。
15. **Android 组件契约(Compose 列表)**:`LazyPagingItems` 手动索引循环
    (`for (index in 0 until items.itemCount)`)+ VM 级 `LazyListState`
    承载滚动保持;`LazyPagingItems.get(index)` 内建 accessHint 触发分页
    (无需额外 onLoadAround 接线);`enablePlaceholders=false` 时
    `itemCount` = 已加载数。分页翻页验证须种子数据 > 2 页(如 120 条),
    否则分页路径永不触达(旧测试盲区)。
16. **二态偏差每应用监听(V24;V25 无全局配置收敛)**:有效配置 =
    内置推荐默认值(`HookSwitch.DEFAULT`,测试强制与 CategoryTaxonomy 对齐;
    **无全局设置 UI**——`switch_global` 键仅作注入侧兜底种子,
    `SettingsRepository.init` 无条件重置,历史用户自定义一次清除)⊕ 该应用
    稀疏二态偏差;模型 = `AppListenOverride`(masters×2 + kinds 17 项 +
    V25 微调项 `logStack/maxBytesPerField/ignoreStackPrefixes/maxResultRows/
    maxResultChars`,全部可空,null = 取默认);`EffectiveSwitch.resolve`
    产出普通 `HookSwitch` —— wire 协议/注入侧零感知(VERSION 恒 4);
    `readKind`/applyKinds 为读写互逆映射,新增类别必须同步双端;
    存储 = 每包一键 `override_{pkg}`,惰性创建、零偏差即删键;
    JSON 兼容 V23 三态遗留(`"ON"/"OFF"` + `kindOverrides` 键名容忍解析,
    写入恒为新格式);覆盖键读本地权威(`getLocal`),默认键读优先远程;
    下发 = ConfigServer per-session resolve(`effectiveResolver` +
    `pushToPackage` 定向推送;V25 起 `broadcastEffectiveConfig` 已随全局页
    退役,**禁止恢复「同一份广播全量」**);注入侧兜底(HookInstaller)
    直读两键后 resolve。设置页 = 行内 M3 Switch(`listen-row-*` testTag,
    整行 toggleable + 内层 Switch clearAndSetSemantics),单击即写即推;
    记录选项/结果闸门数值行经底部 sheet 编辑(MaxBytesSheet/IgnoreStackSheet/
    IntLimitSheet,≤0 显 ∞);「恢复默认配置」行仅在有偏差时渲染(testTag
    `listen-reset`,副标题 plurals `listen_settings_custom_n`);
    测试种子 hook 开关默认值见 V18(dbOpen 等噪点类别默认关),集成测试
    setUp 须显式开。
17. **记录浏览收敛(V24)**:`records/all`(GlobalRecordsScreen)V24 退役,
    记录浏览 = 每应用两列表(`records/algorithm|database`,
    `RecordsViewModelFactory(recordType, packageName)` 预设必传非空,
    VM 可变面仅 query/kindName,`totalCount` 留作对账面);
    详情页锚点芯片与分区头同文案——插桩断言一律
    `onAllNodesWithText(...).onFirst()` 多节点安全形态,禁裸
    `onNodeWithText(<区块标题>)`。
18. **解耦契约(V23 R4)**:`PackageBoundaryTest` 新增规则——ui 子树禁
    import `data.db.*`(存储细节由仓库出口承担,如存储占用 =
    `LogRepository.storageUsage()`,DB+wal+shm 三文件和);UI 变更
    应用列表偏好一律经 `AppsViewModel.setPinScoped/setShowSystem/
    setShowModules`(UI 零 import `AppListSettings`,其 init 缓存
    applicationContext,setX 无 Context 参数);作用域变更提示文案
    统一 `ui/common/scopeChangeMessage`,状态语义分派统一
    `ui/common/connectionStatus/scopeStatus`;Room 显式 WAL
    (`ModevDatabase.get`)、批量插入显式 `@Transaction`;
    lint 基线更新:**38 → 37 warnings / 0 errors**(V23 清理死资源键
    detail_remove_scope/listen_settings_title/record_copied/
    settings_pin_scoped_hint/settings_apps_section/detail_framework_error,
    新增计数文案必须 plurals 如 listen_settings_custom_n)。
19. **服务监听绑定契约(V26)**:`ConfigServer.externalAccess`(volatile,
    默认 false = 绑定 `ConnectionMonitor.BIND_LOOPBACK`;true = 
    `BIND_ALL_INTERFACES`);偏好键 `server_external_access`
    (`SettingsRepository`,宿主本地 prefs,**不入远程镜像**——仅宿主进程消费);
    写路径唯一 = `setServerExternalAccess`(持久化 + volatile 同步置位 +
    异步 `restart()`);restart 带 epoch 代元守卫(旧 accept 线程不触碰新代
    running 状态),已连接会话由注入侧重连循环恢复;注入侧恒连回环,
    wire 协议 VERSION 恒 4 零感知。UI 观测经 domain `ConnectionMonitor.
    boundAddress`(P5-1:ui 禁 import data.server,VM 组合
    `listeningAddress` 回退口径)。诊断页开关 testTag
    `diagnostics-bind-all`,默认停用,风险提示进副标题。
20. **行为监听一等公民契约(V27)**:第三记录类型 `RecordType.BEHAVIOR`
    (wire=`behavior`),单表扩展(ADR-1,迁移 v8→v9 新增 10 可空列:
    path/destPath/command/fileSize + content 五元组);wire VERSION 恒 4
    零感知(`recordType` 泛型信封即扩展点)。注入侧 = `BehaviorProbe`
    装配五 Hooker(FileMeta/FileStream/NioFiles/Asset/Shell),emit 闸门序:
    config 快照 → `behaviorEnabled(kind)`(volatile 前置)→ `PathGate.isIgnored`
    (内置 9 系统前缀 + modev_spool/modev_seq_ 自写消除 + 用户
    ignorePathPrefixes 叠加)→ 栈 denylist → `SessionThrottle`(LRU cap256,
    同键 1s 窗口去重)→ 组装;**被动红线**:内容预览仅限「随调用即可得」
    (readAllBytes/readAllLines 返回值、write 实参、AssetFileDescriptor.length),
    流式 API 与 open() 返回的流绝不触碰。开关模型:HookSwitch +master
    behaviorListen + kinds 7 项(FILE_WRITE/DELETE/RENAME/SHELL_EXEC 默认开;
    FILE_READ/LIST/ASSET_READ 默认关)+ 微调 fileContentCapture/
    ignorePathPrefixes(AppListenOverride 偏差项同步扩展);新增行为类别必须
    同步五处:BehaviorKind/BehaviorOperation.kind 归属/CategoryTaxonomy 条目/
    HookSwitch.behaviorEnabled/EffectiveSwitch.readKind+applyKinds(互逆测试
    强制)。UI 三入口对齐:`records/behavior/{pkg}` 路由、AppMonitor 第三
    EntryRow(testTag `monitor-entry-behavior`)、StatsCard 三段比例条;
    监听设置页行为分区 testTag:`listen-row-master-behavior` /
    `listen-row-file-content` / `listen-row-ignore-path`;数据管理 ClearTarget
    三类型。列表行统一脚手架(V27):`RecordRow` → RowPresentation 单一数据出口 +
    RowScope.RecordListCard(leading 36dp 类别色@13% 徽章 KindIcons 映射 + headline
    mono Medium + 元行 AnnotatedString 类别词元着色 + 尾栏 MicroBadge);
    路径/命令中缀省略走 `MiddleEllipsisText`(纯函数 MiddleEllipsis.ellipsize
    JVM 直测);计数副标消费 `RecordsViewModel.totalCount`;长按复制摘要三族齐备
    (copySummary/sqlRowSummary/behaviorSummary)。
21. **详情页纯 markdown 化(V28)**:Appbar 以下信息内容 100% markdown 承载
    (identity `## 标题+粗体上下文行` / TOC 导航行 / H2 节根+H3 子表示),
    原生 Hero 卡、FilterChip 锚点行、ZoneHeader 色带、流向图标行全部退役;
    **D2.1:目录条目是导航控件非信息区块**——`RecordTocContent` 原生可点击行
    (testTag `toc-{key}`,标签与节标题同源),渲染库 markdown 链接的语义节点
    为 0×0 bounds LinkTestMarker(坐标点击不可达,勿走该链路);
    渲染层:`markdownTypography()` 显式字阶(H2=titleMedium SemiBold 等 16 槽,
    取代库默认 display* 巨型标题)+ 扁平代码块(`md-code-block`,
    surfaceContainerLowest+8dp 圆角+outlineVariant@40% 描边,零 elevation;
    SecondaryTabRow;rememberSaveable 无显式 key);高亮 = Highlights 引擎直连
    (`dev.snipme:highlights`,**禁手搓分词器**;json→JAVASCRIPT、bash/shell→SHELL,
    M3 同源 SyntaxTheme 亮暗自适应,`HIGHLIGHT_MAX_CHARS=32Ki` 降级纯 mono);
    params/bindArgs/rows 以真实 JSON 承载(D3:`formatParamsJson/
    formatBindArgsJson/formatRowsNdjson`,Gson 必须 serializeNulls 否则 SQL NULL
    丢失;json 围栏不参与代码组聚合);`export_too_large` 两处拒绝均带格式参。
22. **偏好监听一等公民契约(V28)**:第四记录类型 `RecordType.PREFS`
    (wire=`prefs`),迁移 v9→v10 仅新增 `prefKey TEXT`(值域复用 content 五元组、
    路径复用 path 列);注入侧 = PrefsProbe 三 Hooker(Open 构造器+edit 登记 /
    Get 全家桶+contains+getAll / Editor put*+remove+clear),emit 闸门序与行为
    逐位一致,**实例索引 PrefsInstanceIndex 双 LRU(cap256)杜绝反射读内部字段**
    (构造器 after 的 thisObject=新实例,登记依赖它——插桩驱动测试须传实例);
    开关模型:prefsListen master + kinds 5 项(OPEN/PUT/REMOVE/CLEAR=SIGNAL 默认开,
    GET=NOISE_HIGH 默认关)+ 零新增微调项(值捕获复用 fileContentCapture、路径降噪
    复用 ignorePathPrefixes);commit/apply 不挂钩(D5)。UI 四入口对齐:
    `records/prefs/{pkg}`、AppMonitor 第4 EntryRow(`monitor-entry-prefs`)、
    StatsCard 四段比例条(min weight 0.02f)、监听设置 prefs 分区
    (`listen-row-master-prefs`;**switchLabelRes 中 PrefsKind.OPEN/CLEAR 与 SQL
    kind 名碰撞,分支必须前置**)。新增类别五处同步清单同 #20
    (+PrefsRecordMappers dispatch 双臂 + LogRepository writeBatch/registry +
    HookInstaller probes/jsonOf + CapabilityKind.PREFS_LISTEN)。

## 已知工具链坑

- **Kotlin `-no-jdk` 下 `java.text.NumberFormat.getCompactNumberInstance` 编译期
  Unresolved**(内嵌 JDK 11 类元数据;AGP `--system jdkImage` 同坑)。
  方案:紧凑数字用 `android.icu.text.CompactDecimalFormat`(API 24+);
  相关单测须 Robolectric(纯 JVM 下 android.icu 是桩)。
- 新增 Java 源:AGP javac 同样受 `--system jdkImage` 限制,勿依赖 JDK 12+ 平台 API。
- 本 Compose 测试库无 `hasToggleableState`/`waitUntil`/`hasText(Regex)`/
  `assertExists`/`performScrollBy` 与 `SemanticsProperties.OnClick`
  (用 `SemanticsActions.OnClick`);`onRoot`/`printToLog`/`onFirst` 是扩展需
  显式 import;`onRoot()` 在弹窗打开时有 2 个 root 会报错,须
  `onAllNodes(isPopup())`;节点存在断言用
  `onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty()`。
- **单一 LazyColumn item(整段 `SettingsSection` 为一个 item)内,段内行
  `performScrollToNode` 不可靠**(实测滚动停在 161px、目标行仍在组合窗口外,
  仅跨 item 滚动稳定);整段 item 会整段组合,段内行断言存在即可;段尾行先
  滚动到相邻 item 再断言存在/可见。
- 断言计数/数值文案须资源驱动:zh 设备渲染 "2 条"(`settings_total_records_value`
  zh 带后缀),裸数字精确匹配失败;存储值断言防「Room 首查才建库文件」竞态
  (生产 `remember` 快照取 0 B)——测试先 `openOrCreateDatabase` 预建文件。
- 模拟器偶发 ADB `Connection reset`/`Failed to start Emulator console`(测试
  本身 XML 全绿但 Gradle 报驱动失败):重跑即可;跑前 `adb devices` 确认设备在线。
- 并行跑两个 gradle 任务可能触发守护进程 OOM(「daemon disappeared」),lint 等
  重任务串行执行。多项目/IDE 守护进程滞留(Xmx8G 等)会压垮页面文件
  (OS errno 1455/daemon 崩溃):assemble 前清理闲置守护进程,
  重任务用 `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2048m` 逐变体
  串行执行。
- **Robolectric 双模式均不支持 `EXPLAIN QUERY PLAN`**(legacy/native shadow
  都剥离 EXPLAIN 前缀直接执行原查询,返回普通结果集)——查询计划级验证留待
  真机插桩;JVM 门禁用「sqlite_master 索引存在性 + 行为对账」组合
  (`ExplainQueryPlanProbeTest`)。
- **PowerShell 管道处理含中文源码会毁文件**:`Get-Content | -replace |
  Set-Content` 以系统 ANSI 解码 UTF-8,中文注释乱码且尾字节可吞换行把下一行
  代码并入注释(V23 实锤)。改文件一律用 edit 工具或 .NET API 字节级读写
  (`[IO.File]::ReadAllBytes/WriteAllBytes + Encoding.UTF8`)。
- androidx.sqlite `SimpleSQLiteQuery` 不解析 `:name` 命名参数(手写 SQL 直绑
  需用 `?` 占位);Robolectric 下 `?N` 编号占位触发 SQLITE_MISMATCH,用纯 `?`
  按序绑定。
- **mikepenz markdown-renderer(V26)**:①默认在 Dispatchers.Default 异步解析
  (Loading 态渲染空 Box),Robolectric 下与 waitForIdle 竞态——一律走核心
  `rememberMarkdownState(content=…, immediate=true)` + m3 的 state 重载
  (**m3 门面未透传 immediate**,直接 content 重载传不进去);②GFM 表格单元格
  解析器保留管道前空格为尾随字符 → 序列化用紧凑 `|Cell|`;③文档级围栏索引按
  content 字符串 remember 缓存,节点定位用 start/endOffset(跨解析实例稳定)。
- 详情页锚点滚动静默失效的形态:**LazyColumn 漏传 `state = listState`** 时
  animateScrollToItem 打在孤立状态上零异常零移动(firstVisible 恒 0);
  回归测试 `AnchorScrollRoboTest`(Robolectric 全装配驱动,秒级)。
- 截断标记/任何引用块插在连续围栏之间会打断代码组聚合(CodeGroupExtractor
  以块级相邻为准)→ 工厂恒将标记后置于全部围栏之后。
- **KDoc 注释内禁止书写 `get*/put*` 形态**:`*/` 会提前终结块注释,余文被当
  顶层声明解析出成串 Syntax error;且 KSP2 只报 `[MissingType] Element
  'CryptoRecordEntity' references a type that is not present`(掩盖真实行号)。
  定位法:临时摘除 `ksp(libs.androidx.room.compiler)` 跑 compile 看真实诊断,
  修毕恢复。写法用「get/put/remove 系列」。
- **Gson 树序列化默认省略 JsonNull 成员**(gson 2.13 实测):SQL NULL 必须
  `GsonBuilder().serializeNulls()` 才落 `"value": null`,否则键静默丢失。
- mikepenz 0.38.1 的 MarkdownComponents 无 textLink 钩子;markdown 链接语义
  节点为 0×0 bounds(测试坐标点击不可达)→ 页内导航一律走原生可点击行
  (见强约束 #21 D2.1),勿依赖链接点击链路。
- lint NewerVersionAvailable 按声明依赖逐条计数:直依赖锁定旧版必 +1;
  仅传递使用的库不要显式声明(高亮引擎经 `dev.snipme:highlights` 直依赖并锁
  1.1.0,Kotlin 2.2.0 构建 metadata 安全;禁随上游盲升)。

## 测试金字塔(新增行为必须有测试)

- 纯函数 → `app/src/test` 普通 JUnit(ChunkedText / Hex / RecordMarkdownFactory /
  CodeGroupExtractor / TimelineGroup / RecordType…)。
- Android 框架依赖 → Robolectric(`@Config(sdk=[34])`,resources + android.icu 可用)。
- 组合/导航/手势 → `app/src/androidTest` Compose UI 测试(实机或模拟器)。
