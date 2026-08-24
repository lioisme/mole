# AGENTS.md

Modev — Kotlin/Compose (Material3) Xposed 模块,算法监听(加解密/签名/密钥/BASE64/WebView)+ 宿主双进程日志。
构建与质量契约(2026-08-20 审计沉淀,V21)。

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

# lint 门禁(基线:38 warning / 0 error;提交前必须跑,禁止新增 warning)
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
3. **详情页三态双层渲染**:字节区块 = SegmentedButton 三态(文本/Base64/Hex,
   `rememberSaveable` 每区块记忆,无文本时隐藏文本段),内容按所选表示渲染;
   分档按所选表示字符数 `sectionRenderMode(...)`——≤128 KiB 字符 = 整段单
   `SelectionContainer`(Tier A);超过 = 分块虚拟化(Tier B,`ChunkedText`
   O(1) 取块,Tier B 三表示均走分块);摘要/列表行保持 hex 首 24 字符不膨胀。
   `BytesView.of` 有硬保险丝(16M hex / 8M text 字符),截断 hex 恒为纯前缀
   (截断标记由 UI 资源渲染);复制 >4M 字符、导出 >32M 字符拒绝并提示。
   **ReadMoreText(com.webtoonscorp)预览的语义文本仅含可见片段且「… 展开更多」
   并入同一节点**——插桩断言用短前缀 substring 匹配,勿对全量内容做精确匹配;
   长区块「收起」按钮在内容末尾,断言/点击前须 `performScrollToNode`。
4. **输入即底部 sheet**:带键盘输入的弹层一律 `ModalBottomSheet`
   (`imePadding` 即可——M3 已内部消费导航栏 inset,**禁止再叠
   `navigationBarsPadding`**,双内边距叠加 = 弹窗底部黑带根因 V21 T2;
   弹窗显式 `containerColor = surfaceContainerHigh`、宿主页 Scaffold 显式
   `containerColor = surface`,hide 动画后回调);
   仅危险确认/关于类保留居中 `AlertDialog`。
5. **弃用 API 零容忍**:新增源码一律不得引入已弃用 API;`getInstalledApplications`
   走 `core/util/PackageManagerCompat.kt` 统一入口(API 33+ `ApplicationInfoFlags`,
   旧系统弃用分支);`getLaunchIntentForPackage(String)` 全 API 无 flags 重载且从未
   弃用,直接使用即可。`lintModernRelease` 零新增(基线 38 warning/0 error,
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
12. **数据库监听开关契约**:5 类开关(open/query/DML/exec/transaction)默认开,
    设置页类别开关即时广播生效;结果上限为「行数 + 字节」双闸门
    (`IntLimitSheet` 底部输入,禁居中 Dialog);`kind` 列跨枚举共用
    (CryptoKind.name / SqlKind.name),筛选菜单记录类型 + 类别双组单选。
13. **MD3 组件契约 + 设置页功能区**:设置类控件一律 M3 组件
    (`ListItem`/`AssistChip`/`TextButton`/`DropdownMenuItem`…),
    `CollapsedSearchInput` 为唯一例外(KDoc 固化,经 D4 单向决策);
    M3 1.4.0 `ListItem` 内容槽**合并为单一语义节点**(文本节点 = 整行高、
    节点 Text 列表含全部槽文本)——插桩勿对行内文本独立测距,锚定断言用
    `onNodeWithText` 精确匹配合并节点即可。设置页功能区四入口:
    算法监听(`settings/algorithm`,总开关 + 12 类别 + 记录选项)/
    数据库监听(`settings/database`,总开关 + 5 类别 + 结果闸门)
    (V21 拆分还原,旧合并页 `settings/monitor` 已退役)/ 数据管理
    (`settings/data`)/ 服务与诊断(`settings/diagnostics`,三分区 +
    复制诊断信息);全局列表页 `records/algorithm`/`records/database`;
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
16. **三态继承每应用监听(V23)**:覆盖模型 =
    `TriState{INHERIT,ON,OFF}` + `AppListenOverride`(总开关×2 + 类别×17
    逐项三态),`EffectiveSwitch.resolve(global, override)` 产出普通
    `HookSwitch` —— wire 协议/注入侧**零感知** TriState(VERSION 恒 4);
    存储 = 每包一键 `override_{pkg}`(`ConfigKeys.OVERRIDE_PREFIX`,
    与 V10 已清理的 `switch_{pkg}` 隔离),惰性创建、全 INHERIT 即删键;
    覆盖键读本地权威(`getLocal`,只由宿主写),全局键维持读优先远程;
    下发 = ConfigServer per-session resolve(`effectiveResolver` 回调 +
    `pushToPackage(pkg)` 定向推送;全局变更 `broadcastEffectiveConfig()`
    对每会话各自 resolve,**禁止恢复「同一份广播全量」**——会冲刷已覆盖
    应用的有效配置);注入侧兜底(HookInstaller)直读 `switch_global` +
    `override_{pkg}` 后 resolve;测试种子数据 hook 开关默认值见 V18
    (dbOpen 等噪点类别默认关),集成测试 setUp 须显式开。
17. **统一记录页与列表收敛(V23)**:全部记录浏览走 `records/all`
    (`GlobalRecordsScreen`,入口仅应用列表顶栏);三页共享
    `RecordTimelineList` + `RecordsViewModel`(presetTypes/presetPackages
    锁定单应用语境);D6:类别筛选仅在生效记录类型唯一时可选
    (`KindFilterMenu(enabled=...)`,VM `buildFilter` 归一化兜底),
    类型切换自动失效已选类别。
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

## 测试金字塔(新增行为必须有测试)

- 纯函数 → `app/src/test` 普通 JUnit(ChunkedText / Hex / SectionRenderMode /
  TimelineGroup / RecordType…)。
- Android 框架依赖 → Robolectric(`@Config(sdk=[34])`,resources + android.icu 可用)。
- 组合/导航/手势 → `app/src/androidTest` Compose UI 测试(实机或模拟器)。
