# AGENTS.md

Modev — Kotlin/Compose (Material3) Xposed 模块,双域架构:监听域(算法/数据库/行为/偏好/WebView/系统信息 + 环境观测)+ 功能域(退出拦截/崩溃预防/解除录屏/弹窗关键词拦截/SSL 绕过/代理 VPN 隐藏/设备环境伪装)+ 宿主双进程日志。
构建与质量契约(2026-08-20 审计沉淀 V21;2026-08-25 V24 二态监听/记录收敛/详情重设计增量沉淀;2026-08-26 V26 详情页 markdown 化/外部监听开关/诊断详情弹层增量沉淀;2026-08-26 V27 记录列表重构/行为监听一等公民增量沉淀;2026-08-26 V28 详情页纯 markdown 化/偏好监听一等公民增量沉淀;2026-09-04 V37 双域拆分/FeatureGate 红线/关键词库与画像契约增量沉淀;2026-09-05 V38 MCP 服务/工具面 parity/注入侧 seq 续号修复增量沉淀)。

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

# lint 门禁(基线:49 warning / 0 error,V37 双域重构后实测;V34 曾 43,V35/V36 漂移 +6;提交前必须跑,禁止新增 warning)
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
| 监听域 Listen Domain | 被动采集能力域(算法/数据库/行为/偏好/WebView/系统信息 + 请求监控/检测监控/弹窗·控件·Activity 观测);`CategoryTaxonomy.domain = LISTEN` | 把干预功能称「××监听」 |
| 功能域 Feature Domain | 主动干预能力域(退出拦截/崩溃预防/解除录屏/弹窗关键词拦截/SSL 绕过/代理 VPN 隐藏/Root·Xposed·模拟器伪装);`domain = FEATURE` | 伪装成监听类型 |
| 关键词库 Keyword Library | 全局弹窗拦截关键词资产(Room `keyword_library`,text 唯一 + matchMode + 全局启停);每应用默认跟随全局,可显式子集 | 仅 override 扁平词表、无库 |
| 伪装画像 Spoof Profile | 设备/网络伪装值组(auto/pixel7pro/xiaomi13 内置档位 + 字段覆写);`SpoofProfiles` 为机型字面量唯一事实源 | 钩子内硬编码机型值 |
| 检测黑名单追加项 extra* | Root 包名/Xposed 前缀/Frida 端口/掩码词的用户追加项(内置基线 ∪ 追加,只增不删) | 覆盖内置基线 |
| MCP 服务 MCP Server | 进程内常驻 Streamable HTTP(spec 2025-06-18,端口 15110,单端点 POST /mcp);默认回环免 Token(adb forward 开箱即用);外部访问独立开关 = 0.0.0.0 + 强制 Bearer;与 wire ConfigServer 完全独立生命周期(独立端口/开关/restart,互不触碰,VERSION 恒 4 零感知) | 把 MCP 当 wire 通道扩展、恢复 resources/prompts 双通道 |
| 工具面 Tool Surface 43 | MCP 对 Agent 的能力清单(记录 14/应用 6/监听 4/功能 4/资产 8/服务 4/作用域 3),与 UI 能力逐行对照、同上游继承;`McpToolParityTest` 冻结快照 + B1 边界源码扫描双门禁 | 「MCP 子集」「UI 专属阉割」(§5.3 明示排除除外) |

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
   弃用,直接使用即可。`lintModernRelease` 零新增(基线 43 warning/0 error,V34 清理 39 死资源,
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
    lint 基线更新:**43 → 43 warnings / 0 errors**(V34 清理 39 死资源键
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
23. **双域拆分契约(V37,最高架构优先级)**:`CapabilityDomain { LISTEN, FEATURE }`
     为类别归属唯一事实源,`CategoryTaxonomy.RecordCategory.domain` **必填无默认**
     (新增类别未标域编译即红);FEATURE 37 类 / LISTEN 44 类,快照测试锁定
     (kind id 跨类型碰撞 OPEN/SIGNATURE,域完备断言必须用 (type,id) 对)。
     **FeatureGate 红线**:一切干预动作(setResult/字段改写/弹窗拦截/进程拦截/
     掩码)必须经基类 `featureOn(kind)` = master∧kind 双门控,**禁止裸布尔位直读**
     (`InterventionGateConventionTest` 源码扫描门禁 + `FeatureGateBypassTest`
     行为门禁;开关全关 = 零副作用,含安装期 Build 静态字段改写——
     EmulatorBuildHooker 经 `ConfigWatcher` 惰性幂等补写,永不无条件篡改)。
     功能域数据一律走 `HookSwitch.feature: FeatureConfig`(词表规则/画像/黑名单
     追加,wire 信封 VERSION 恒 4、configVersion=2 读旧写新迁移);干预功能 UI
     唯一入口 = 每应用「功能设置」(`apps/{pkg}/feature-settings`,六功能卡由
     `FeatureGroups` 驱动,完备性测试锁定 FEATURE 域↔组并集),监听页只留
     LISTEN 域(环境观测分区由 taxonomy 驱动);全局功能资产 = 设置页
     「拦截与伪装」区(关键词库 `settings/keywords` / 画像 `settings/profiles`
     / 黑名单 `settings/blacklist`,写 `feature_global` 本地键并入 switch_global
     种子);机型字面量唯一事实源 `SpoofProfiles`(钩子禁内置);
     关键词库 Room `keyword_library`(v14)+ 每应用 keywordIds/keywordFollowGlobal
     宿主端解析(`SettingsRepository.withKeywordRules`),库变更
     `pushToAllConnected` 重推。Capability 层(domain/capability)已整体退役,
     **禁止恢复**(双域 UI 唯一驱动源 = CategoryTaxonomy)。
24. **Appbar 动作契约(V37)**:每应用监控页「启动/清空记录」= TopBar actions
     (`app-launch`/`app-clear-records` testTag,正文操作卡退役);10 记录列表页
     统一「清空本类」Appbar 动作(`records-clear-type` → 居中确认
     `records-clear-confirm`,deleteByFilter 按预设语境);危险确认恒居中
     AlertDialog + surfaceContainerHigh。
25. **视觉 token 契约(V37 T4)**:alpha 魔法数一律 `ui/theme/Tokens`
     (pillContainerAlpha/badgeContainerAlpha/chevronAlpha/disabledContentAlpha/
     emptyIcon*/codeBorderAlpha);记录行内边距 16dp 统一(旧 14dp 已归一);
     设置类行开关统一 `ToggleListItem`(PerAppSettingRows 共享);
      kind→标签映射唯一入口 `KindLabelRes.resolve`(监听/功能两页共用,
      Prefs OPEN/CLEAR 碰撞前置规则不变)。
26. **MCP 服务契约(V38,Agent 一等公民)**:`mcp.*` 包边界 = B1(禁 import
      `ui.*`/`androidx.compose.*`/`data.db.*`/`hook.*`/`data.server.*`/`framework.*`,
      `McpToolParityTest.mcp_layer_boundary_allowlist` 源码扫描)+ B2(允许
      `domain.*`/`data.repo.*`/`core.*`,与 UI 同一上游 allowlist——「同一起游继承」
      由测试锁定);**工具面 parity**:`McpToolParityTest.frozen_43_tool_names` 冻结
      43 工具名快照,新增 UI 能力必须同步 MCP 工具(否则红),防「旁路上游」与
      「功能阉割」双向漂移;协议 = spec 2025-06-18 Streamable HTTP 单端点 POST /mcp,
      单请求单响应(batch→-32600),错误码分型(协议错误 -327xx/-326xx vs 业务错误
      `result.isError`),会话头 `Mcp-Session-Id`(restart 整体失效);认证 = 回环免
      Token、外部访问(0.0.0.0)强制 Bearer(宿主本地自动生成 UUID Token,诊断页
      展示/复制,独立于 ConfigServer requireToken);`McpDtos` 必须用专用
      `GsonBuilder().serializeNulls()`(SQL NULL 键否则静默丢失);记录大字段
      1MiB 截断守卫(响应内 `truncated:true`);`records_clear_all`/`app_clear_records`
      等危险工具 `confirm:true` 必填(缺失→-32602 协议错误);日志 `ModevMcp` 仅落
      method+tool+耗时不落记录内容(PII 纪律);MCP 与 wire ConfigServer 完全独立
      生命周期(独立端口 15110/开关/restart,互不触碰,VERSION 恒 4 零感知);
      交付门禁 = `24-run-mcp.ps1`(42 子断言,§9.2 矩阵 22 项)。

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
- **PowerShell 参数名/脚本变量禁用自动变量名 `$args`/`$pid`(V38 实锤,大小写不敏感
  即冲突)**:`function F([object]$args)` 实参不绑定(自动变量遮蔽),`$pid = ...`
  静默失败(只读)——集成脚本中表现为「arguments 恒空」「存活判定拿 shell 自身 PID
  恒真 = 假 PASS」。一律改 `$toolArgs`/`$pidOut`;新写脚本函数先 `Get-Help` 心查
  自动变量表(完整列表见 about_Automatic_Variables)。
- **PS 5.1 `Invoke-WebRequest` 无 `-SkipHttpErrorCheck`**(PS 6+ 参数):参数绑定
  失败 = 语句终止错误,响应变量为 null。非 2xx 断言一律 try/catch +
  `$_.Exception.Response.GetResponseStream()` 读体。
- **雷电 x64 镜像剪贴板静默丢弃写入**(V38 实锤):`ClipboardManager.setPrimaryClip`
  前台执行后 `hasPrimaryClip` 仍 false(直写亦然)。剪贴板内容断言必须经
  `clipboardFunctional()` 哨兵探测降级(Snackbar 证处理器执行),内容同源由
  DiagnosticsReport JVM 单测 + MCP diagnostics 工具双端兜底;真机自动恢复全断言。
- **connectedAndroidTest 测后 AGP 卸载宿主 APK → Vector modules 行删除 + enabled
  复位**(V38 实锤):后续注入矩阵全 FAIL(记录零捕获)且无崩溃日志,极难归因。
  恢复序列:重装 APK → 等 Vector 重注册 → `UPDATE modules SET enabled=1` +
  scope 行(`11-vector-scope.ps1`)→ **重启 VM**(lspd 缓存模块表,不重启不生效)→
  HookInstaller 日志验证。androidTest 抽样必须排在设备集成矩阵之后,或测后即恢复。
- **注入早于 Application 时,任何构造期一次性求值的 Context 依赖初始化都会静默退化**
  (V38 seq 续号 bug 根因):`RecordSink.seqCounter` 构造期 `restoreSeq()` 时
  `ActivityThread.currentApplication()` 为 null → 每次重启 seq 从 1 重发 → 宿主
  `(packageName,processName,seq)` 唯一索引 REPLACE 静默覆盖上一代记录(跨重启数据
  丢失,矩阵表现为「记录凭空消失」)。规约:Context 依赖初始化一律惰性到首次使用
  且路径可见才定档;spoolProvider 同款懒重试。回归 `RecordSinkTest`
  「file becomes visible after construction」。
- **现代路径 after-setResult 曾被拦截器丢弃(V38 修复,功能域全局影响)**:
  `LibxposedPlatform.intercept` 恒 `return result` → after 钩子里的 setResult
  (maps 掩蔽/栈过滤/传感器伪装/VPN·DNS 隐藏/SSL 结果替换)全部空操作。
  after 短路值必须优先返回。新增结果改写型 Hooker 后必须以对应二态断言验证
  (裸挂钩不触发 = 假修复)。
- **Android 14 握手验证只经 Platform 包私有桥(V38 dexdump 实锤)**:
  `Platform.checkServerTrusted(X509TrustManager,X509Certificate[],String,
  AbstractConscryptSocket|ConscryptEngine)` 是 static 包私有(access 0x0008 无
  PUBLIC 位),`getMethod` 不可见必须 `getDeclaredMethod+setAccessible`;参数类型
  为 hide 类需运行时反射解析。TrustManagerImpl 公开重载不在 native 握手路径上。
- **app dex 类必须经 app classloader 显式装载(V38 三重实锤)**:①ART 同 loader
  内类引用解析走 native fast path,`ClassLoader.loadClass` 监视器看不到(okhttp
  全系类);②模块注入类的 forName 委派链不含 app dex;③模块与 app 各带一份
  kotlin-stdlib,`Function0` 等双份 Class 按 `==` 比较 → getDeclaredMethod 必败。
  统一解法:`ProbeContext.appClassLoader` + `Class.forName(name,false,appLoader)`
  (initialize=false 零副作用),以 SSLContext.init/握手回调为补装触发点。
- **Vector 2.2 同一方法的第二个 hook 不执行**(V38 观察):两个 hooker 各自对
  `ClassLoader.loadClass` 注册钩子时,仅先注册者生效——需要类加载观察的 hooker
  共享单一监视器或改用显式预载,禁止各自重复注册同一方法。

## 测试金字塔(新增行为必须有测试)

- 纯函数 → `app/src/test` 普通 JUnit(ChunkedText / Hex / RecordMarkdownFactory /
  CodeGroupExtractor / TimelineGroup / RecordType…)。
- Android 框架依赖 → Robolectric(`@Config(sdk=[34])`,resources + android.icu 可用)。
- 组合/导航/手势 → `app/src/androidTest` Compose UI 测试(实机或模拟器)。

## V36 注入侧硬约束(2026-09-04 全功能验收沉淀,改前必读)

- **配置偏差层(V36 D-1)**:注入侧 `ConfigChannel` 持双态——`globalConfig`(宿主 TCP
  推送/远程偏好写)+ `localDeviation`(V35 磁盘兜底 `/data/local/tmp/modev_config_{pkg}.json`
  的 `AppListenOverride`)。有效配置恒为 `global ⊕ deviation`;**禁止**让推送路径直接覆盖
  currentConfig 或让磁盘兜底一次性 resolve 后即失效(Vector 2.2 不投递 XposedService,per-app
  偏差唯一可靠入口是磁盘兜底 + 偏差层)。回归测试 `ConfigChannelDeviationTest`。
- **主动心跳(V36 D-3)**:`ConfigChannel` 连接后每 20s 发 `ping`(远小于宿主 120s/注入侧
  60s 读超时)。协议定义 ping/pong 但**只收不发**会造成空闲半开连接 → 记录滞留 spool。
- **构造器/字段反射钩子(V36 D-2/D-11)**:libxposed 构造器 `chain.proceed()` 返回 void,
  新实例取 `invocation.thisObject`(非 result);声明于父类的字段(如 `Window.mFlags`)
  反射必须**沿 superclass 链**查找。入参改写型干预用 `Invocation.setArg`(libxposed 经
  `proceed(Array)`,legacy 经 args 写回),**禁止** before 清字段后放行原方法(会被设回)。
- **方法注册表含 protected(V36 D-13)**:`BehaviorMethodRegistry` 枚举 `methods ∪ 父类链
  declaredMethods`;`Activity.onCreate/onResume` 等 protected 生命周期此前漏装。
- **WebView 桥注册时机(V36 D-16)**:`addJavascriptInterface` 必须在 WebView 构造器
  **after**(before 阶段 provider 未就绪静默失败,JS 侧永远看不到 `modevJsBridge`);
  探针注入在 `WebViewClient.onPageFinished`(靶 App 须调 super)。
- **类探测钩面(V36 D-12b)**:ART `Class.forName` 走原生解析绕过 Java `loadClass`,
  Xposed 隐藏钩子必须**同时**钩 `ClassLoader.loadClass`/`BaseDexClassLoader.findClass`/
  `java.lang.Class.forName`;emit 须在 before **抛 CNFE 之前**(after 在异常路径不执行)。
- **HttpURLConnection/OkHttp 钩面(V36 D-8/D-9)**:抽象类 `getInputStream/getOutputStream`
  无方法体钩不触发,挂 `com.android.okhttp.internal.huc.*Impl`;应用侧 okhttp 类安装期未
  加载,经 loadClass 监视器**惰性安装**(幂等)。
- **集成测试环境(LDPlayer x64)**:靶程序 `:testtarget`(debuggable,run-as 零 su 查询);
  adb 独立端口 5038;`cached_apps_freezer` 禁用;VM 死亡 WaitDevice 冷重启自愈。掩蔽型
  device_env kind 在预干净模拟器「无脏可掩即零记录」属正确行为,矩阵标 SKIP。
- **禁止钩取依赖调用方栈帧语义的方法(V36 D-17 崩溃教训,硬约束)**:`Class.forName(String)`/
  `Class.forName(String,boolean,Class)` 等以 `Reflection.getCallerClass()` 取调用方 classloader
  的方法**一律不得钩取**——LSPlant 桥接帧(`HookBridge.invokeOriginalMethod`)替换真实调用方后,
  应用侧单参 `forName` 加载自身类全部以错误 loader 解析 → CNFE → 启动即崩(androidx.startup/okhttp
  首当其冲),且钩子无条件安装、**开关关闭也崩**。Xposed 类探测记录面仅靠 `ClassLoader.loadClass`/
  `BaseDexClassLoader.findClass`(实例方法,无 caller-frame 依赖,安全)。新增任何钩子前必须评估:
  ①是否依赖调用方帧;②是否在热路径无条件安装;③功能开关关闭时是否仍产生拦截开销。
- **冷启动冒烟门禁(V36)**:注入侧任何钩子变更后,必须跑 `scripts/test-integration/23-run-smoke.ps1`
  (全功能 ON 下逐个作用域 App 冷启动 + L-SMOKE-01 帧语义断言 + 驻留 ANR 检查)全绿方可交付;
  真机/稳定模拟器为最终聚合验收环境。