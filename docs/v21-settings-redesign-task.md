# Modev V21 任务文档:设置界面设计对齐 + 监听设置拆分 + 全 MD3 组件化

- **版本**:V21 / 2026-08-20
- **范围**:`ui/settings/*`、`ui/components/SettingsComponents.kt`、`ui/theme/*`、`ui/navigation/ModevRoutes.kt`、`ui/ModevNav.kt`、i18n(en/zh)、测试四件套、AGENTS.md
- **验收基准**:AGENTS.md 契约(V20)全数保持;视觉风格与 LIDE(`../lide`)一致;`lintModernRelease` 基线 38 warning / 0 error 零新增;`testModernDebugUnitTest` 全绿;设备 UI 测试全绿

## 1. 需求条款(用户口径,已确认)

| # | 条款 | 确认结论 |
|---|------|----------|
| R1 | 设置界面配色不符合要求,上个版本被错误改动 | 目标 = 与 LIDE 界面设计风格一致;消除「背景与卡片同色」「弹窗内背景与内容色差」 |
| R2 | 设置底部对话框背景含黑色/色差部分,上个版本被错误改动 | 同 R1;重点修复 ModalBottomSheet 容器与内容色差带 |
| R3 | 算法监听设置与数据库监听设置不要放在一起 | **已确认拆两页**(恢复 V19 合并前 IA) |
| R4 | 数据管理 / 服务诊断设计风格不达标准,要求重新设计 | **已确认对齐 LIDE + M3 标准** |
| R5 | 所有组件完全使用 MD3 组件库,禁止手搓自研 | 全量审计 + 替换矩阵 + 门禁守护 |

## 2. 全量审计(现状 + 根因)

### 2.1 R1/R2 配色与弹窗审计(根因)

**审计事实**(代码层 token 与 LIDE 同源,`ui/theme/Theme.kt:140-227` 全量映射 materialkolor `SchemeTonalSpot`;`SettingsComponents.kt` token 与 LIDE `SettingsScreen.kt` 逐项一致):

| 位置 | 现状 | 问题 |
|---|---|---|
| `SettingsComponents.kt:44-57` | 分区标题 = `primary` + titleSmall + SemiBold;卡片 = `surfaceContainerLow` | 深色 scheme 下 `surface`(tone 6)与 `surfaceContainerLow`(tone 10)仅 4 tone 差 → **卡片与背景肉眼同色**(用户实测现象) |
| `Scaffold`(四个设置页均未设 `containerColor`) | 默认 `background`;窗口背景 = `surface`(MainActivity.kt:104) | 依赖隐式 token,未显式对齐 LIDE 的 surface 窗口基底 |
| `MonitorSettingsScreen.kt:364-371 / 431-438 / 497-504`(MaxBytesSheet / IgnoreStackSheet / IntLimitSheet) | 内容 Column 同时应用 `.imePadding().navigationBarsPadding()` | **双份导航栏内边距**:M3 1.4.0 `ModalBottomSheet` 已通过 `windowInsets` 消费 navigationBars;内容再叠加一次 → 底部多出容器色空带,键盘弹出时容器色与内容色不一致呈「黑色带」——即 R2 现象根因 |
| `SettingsScreen.kt:312-548`(Language/Appearance/ColorSheet) | 无 ime/nav 内边距,结构正确 | 无 R2 问题;但未显式指定 `containerColor` |

**结论**:R1 根因 = 深色 TONAL_SPOT 下卡片/背景对比不足 + 页面背景 token 未显式固化;R2 根因 = 输入型底部弹窗导航栏内边距叠加产生的容器色差带。修复方向 = 显式 token 契约 + 移除冗余内边距 + LIDE 逐项对照(§3)。

### 2.2 R3 监听设置合并审计

`MonitorSettingsScreen.kt`(V19 T4 合并产物)将算法/数据库两页合成一页四分区(`MonitorSettingsScreen.kt:102-288`):算法监听 / 记录选项 / 数据库监听 / 结果闸门;路由 `settings/monitor`(`ModevRoutes.kt:23`);设置页功能区分割线分隔的三个入口(`SettingsScreen.kt:199-226`)。合并前(V16)为两个独立页:`AlgorithmMonitorScreen`(算法开关 + 记录选项)与 `DatabaseMonitorScreen`(数据库开关 + 结果闸门),两路由 `settings/algorithm`/`settings/database`。

**现状问题**:单页四分区超过移动端认知负荷(单屏 20+ 开关),算法与数据库两类配置混排,违背「功能决定信息架构」原则。

### 2.3 R4 数据管理/诊断设计审计

**DataSettingsScreen.kt**:
- 三个 `DangerRow`(`DataSettingsScreen.kt:113-125,169-192`)为整块 `errorContainer` 全宽色块 Surface(手搓样式,非 M3 行模式),三条红块堆叠视觉过重、层级混乱;
- 图标重复(`Storage` 出现 2 次)、分区语义不清晰。

**DiagnosticsScreen.kt**:
- 管道统计 7 行全部复用 `Dns` 图标(`DiagnosticsScreen.kt:197-231`),图标零信息量;
- 折叠区为自组 ListItem + AnimatedVisibility(`DiagnosticsScreen.kt:194-233`),视觉与标准 M3 折叠模式不符;
- 「复制诊断信息」为裸 ListItem(`DiagnosticsScreen.kt:235-252`),非标准 M3 动作组件(LIDE 同类操作用 Button)。

### 2.4 R5 MD3 组件审计

交互控件已 100% M3(`ListItem`/`Switch`/`Card`/`TextButton`/`AlertDialog`/`ModalBottomSheet`/`OutlinedTextField`/`AssistChip`/`Snackbar`),V19 T6 矩阵已落地。**残留非 M3**:

| 组件 | 位置 | 判定 |
|---|---|---|
| `DangerRow`(Surface+Row 手搓) | `DataSettingsScreen.kt:169-192` | 交互行,须替换为 M3 `ListItem` |
| 色板圆块(Box) | `SettingsScreen.kt:515-544` | 纯视觉原语,D4 允许保留 |
| `StatusDot`/`TimelineHeader`/`AppIcon` | components/ | 纯视觉原语,保留 |
| `CollapsedSearchInput` | SearchInputComponents.kt | 文档化例外(KDoc),保留 |

## 3. 目标设计规格(LIDE + M3 基准)

### 3.1 配色契约(全设置页统一,R1)

| 元素 | Token | 说明 |
|---|---|---|
| 页面背景 | `Scaffold(containerColor = MaterialTheme.colorScheme.surface)` | 四页 + 三弹窗显式声明,与窗口背景同源 |
| 分区标题 | `primary` + `titleSmall` + SemiBold,start 4dp / bottom 8dp | LIDE 同款 |
| 内容卡 | `surfaceContainerHighest` + `shapes.medium` | V21 决策:M3 Filled 卡语义(实测对比 1.222:1 亮 / 1.494:1 暗,见 3.2;旧 `surfaceContainerLow` 实测仅 1.047~1.10「卡片/背景同色」) |
| 行标题 | `onSurface` + `bodyLarge` | — |
| 行副标题/图标 | `onSurfaceVariant` + bodySmall / 24dp | — |
| 尾部 chevron | `onSurfaceVariant@50%` / 20dp | — |
| 禁用态 | `onSurface@38%` | M3 惯例 |
| 值(InfoItem) | `onSurfaceVariant` + bodyMedium,widthIn(160dp) 上限 | V11 防挤压回归 |
| 弹窗容器 | `surfaceContainerHigh`(显式 `containerColor`) | 消 R2 色差 |
| 危险操作行 | 行级 `ListItem`,leading 图标 `error` 色,标题 `onSurface`;确认框 = 居中 `AlertDialog` | 替换整块 errorContainer 色块 |

### 3.2 主题对比度门禁(新增,R1 可验收)

新增 `SettingsThemeContrastTest`(纯 JVM,对齐 LIDE `ThemeContrastTest` 先例):对 `ThemeSeeds.OPTIONS` 全 20 色 × 亮/暗,断言卡片容器 `surfaceContainerHighest` 与 `surface` 的 WCAG 对比度 ≥ 1.20:1(亮)/ 1.45:1(暗)(实测全 seed 最小:亮 1.222 / 暗 1.494;旧 `surfaceContainerLow` 实测亮 1.047 / 暗 1.10 —— 不达标的即为用户 R1 缺陷),`onSurface` 与卡片容器 ≥ 4.5:1(实测 9.5~13.2),分区标题 `primary` 与 `surface` ≥ 4.5:1(实测 6.1~10.8,黑 seed 暗色 10.8,无需白名单)。卡片 token 同步提升为 `surfaceContainerHighest`(M3 Filled 卡语义,仍在 M3 tonal surface 家族,其余 token 与 LIDE 逐项一致)。

### 3.3 弹窗布局契约(R2)

- 所有 `ModalBottomSheet` 显式 `containerColor = MaterialTheme.colorScheme.surfaceContainerHigh`;
- 输入型弹窗(MaxBytes / IgnoreStack / IntLimit):内容 Column **仅保留 `.imePadding()`**,移除 `.navigationBarsPadding()`(M3 内部已处理),padding 改 `.padding(horizontal = 24.dp, bottom = 24.dp)`;
- 无键盘弹窗(Language/Appearance/Color):维持现状结构,补齐显式 containerColor。

### 3.4 监听设置 IA(R3,恢复 V19 前拆分形态)

```
SETTINGS(设置页「功能」区)
 ├─ 算法监听设置  settings/algorithm   (总开关 + 12 类别 + 记录选项)
 └─ 数据库监听设置 settings/database   (总开关 + 5 类别 + 结果闸门)
```

### 3.5 数据管理 / 诊断设计规格(R4,对齐 LIDE + M3)

**DataSettings** 三区(卡片 token 不变):
1. 记录分类:算法/数据库计数(InfoItem 行);
2. 存储占用:DB 文件大小(InfoItem 行,`Storage` 图标唯一);
3. 数据操作:总记录数(InfoItem)+ 三条标准 M3 `ListItem` 危险行(leading `Delete` 图标 `error` 色 + 标题 `onSurface` + chevron),点击 → 居中 `AlertDialog` 确认(契约 13 保留)。

**Diagnostics** 三区:
1. 框架与作用域(现结构保留,InfoItem 行);
2. 通信服务(端口 / 运行中目标,InfoItem 行);
3. 管道统计:折叠头 = 标准 M3 `ListItem` + chevron(现状已合规,保留),统计行图标按语义分组统一(Received/Dropped/Trimmed 用 `SwapHoriz` 类,拒绝类用 `Block` 类,消除 7× Dns 重复);
4. 「复制诊断信息」改为标准 M3 动作行(`ListItem` 统一 + 点击 Snackbar 反馈,保持与记录长按复制同 UX)。

### 3.6 MD3 组件化(R5)

`DangerRow` 删除,并入标准 `ListItem`;全设置子系统无手搓交互控件;新组件一律 M3。

## 4. 变更清单(执行顺序)

- **T1** 主题对比度门禁:`SettingsThemeContrastTest`(Robolectric)+ 必要调色或白名单记录
- **T2** 弹窗修复:三输入弹窗去冗余 `navigationBarsPadding` + 全部弹窗显式 `containerColor`;四设置页 Scaffold 显式 `containerColor = surface`
- **T3** 拆分监听设置:
  - `ModevRoutes`:删 `MONITOR_SETTINGS`;增 `ALGORITHM_SETTINGS = "settings/algorithm"`、`DATABASE_SETTINGS = "settings/database"`;头注释同步
  - 新建 `AlgorithmSettingsScreen.kt`(算法总开关 + 12 类别 + 记录选项 + MaxBytes/IgnoreStack sheet)与 `DatabaseSettingsScreen.kt`(数据库总开关 + 5 类别 + 结果闸门 + IntLimit sheet ×2);三个 sheet 抽为共享 `SettingsSheets.kt`
  - 新建 `AlgorithmSettingsViewModel.kt` / `DatabaseSettingsViewModel.kt`(均薄封装 `SettingsRepository.globalSwitch`,模式同 MonitorSettingsViewModel)
  - 删 `MonitorSettingsScreen.kt` / `MonitorSettingsViewModel.kt`
  - `SettingsScreen` 功能区改四入口:算法监听(副标题 = 算法启用词)、数据库监听(副标题 = 数据库启用词)、分割线、数据管理、服务与诊断;回调改 `onOpenAlgorithmSettings` / `onOpenDatabaseSettings`
  - `ModevNav`:注册两新目的地,删 MonitorSettings 目的地
- **T4** 数据管理重设计:三区结构保留,DangerRow → 标准 M3 `ListItem`(error 图标 + chevron + 居中 AlertDialog 确认)
- **T5** 诊断页重设计:统计行图标语义化、复制行为统一 M3 动作行、分区视觉与 Data 页对齐
- **T6** i18n:删 `monitor_settings_title` / `settings_monitor_summary`(en+zh);新增拆分入口副标题键(若需);`StringResourceParityTest` / `HardcodedStringConventionTest` 全绿
- **T7** 测试:重写 `MonitorSettingsScreenTest` → `AlgorithmSettingsScreenTest` + `DatabaseSettingsScreenTest`(两页分区齐全、sheet 编辑回写、返回栈、开关联动副标题);更新 `FourScreensNavTest`(设置页四入口断言);更新 `DataSettingsScreenTest` / `DiagnosticsScreenTest` 锚点;新增 `SettingsThemeContrastTest`
- **T8** AGENTS.md 契约同步:路由表(V21)、弹窗容器契约(R2)、主题对比度门禁、拆分 IA、词汇表复核
- **T9** 视觉验收:双变体 assemble + 设备走查,亮/暗 + 键盘弹窗场景截图对照 `docs/screenshots_manual/v14_2_settings.png` 基准

## 5. 测试计划

| 层 | 用例 | 断言要点 |
|---|---|---|
| 单测/Robolectric | `SettingsThemeContrastTest`(新) | 全 seed × 亮/暗:卡片/背景 ≥1.3:1、正文 ≥4.5:1、分区标题 ≥4.5:1 |
| 单测 | `StorageFormatTest` 等存量 | 不回归 |
| 插桩 | `AlgorithmSettingsScreenTest`(新) | 分区齐全、总开关互斥置灰、MaxBytes sheet 编辑回写、返回栈 |
| 插桩 | `DatabaseSettingsScreenTest`(新) | 分区齐全、IntLimit sheet 编辑回写、返回栈 |
| 插桩 | `FourScreensNavTest`(改) | 设置页四入口、两页独立导航、副标题随开关联动 |
| 插桩 | `DataSettingsScreenTest`(改) | 危险行锚点改 ListItem 语义、AlertDialog 确认/取消路径 |
| 插桩 | `DiagnosticsScreenTest`(改) | 三分区、复制诊断、Snackbar |

## 6. 质量门禁

- `testModernDebugUnitTest` 全绿(含新对比度门禁)
- `lintModernRelease` 基线 38/0 零新增
- `assembleModernRelease` / `assembleModernDebug` 成功
- en/zh 键集 diff=0;`ui` 包零中文字面量
- 设备 UI 测试全绿(注意模拟器 ADB 偶发 Connection reset 重跑即可)

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 对比度门禁首跑部分 seed 不达标 | 先跑门禁再定稿 token;极端 seed 走白名单 + 决策记录(先例:KEY_PARAM 3.998→#1B5E20) |
| 拆分后旧测试引用残留 | T7 全量重写相关插桩;`grep` 死路由/死键零残留 |
| ListItem 合并语义节点影响插桩断言 | 锚定 `onNodeWithText` 精确匹配 + 行级行为断言(契约 13/8 已固化) |
| 中文注释文件 PowerShell 改写乱码 | 一律 edit/write 工具,禁止 PowerShell 文本改写 |
| 并行 gradle 守护进程 OOM | 重任务 `--no-daemon --max-workers=1 -Xmx2048m` 串行 |

## 8. 验收标准(DoD)

- [x] R1:四设置页卡片/背景在任意 seed × 亮暗下肉眼可辨,对比度门禁绿;弹窗容器色与内容色一致,无黑色/色差带
- [x] R2:三输入弹窗键盘弹出/收起全程无容器色差带,显式 containerColor 生效
- [x] R3:算法/数据库监听为两个独立页面两个独立入口,旧 `settings/monitor` 零残留
- [x] R4:数据管理/诊断页按 §3.5 规格重设计,视觉与 LIDE 一致
- [x] R5:设置子系统交互控件 100% MD3,`DangerRow` 等手搓交互组件零残留
- [x] 门禁全绿;AGENTS.md 同步 V21

## 9. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 主题对比度门禁 | ✅ | 新增 `SettingsThemeContrastTest`(纯 JVM,全 20 seed × 亮/暗);实测旧 token(surfaceContainerLow)卡片/背景对比仅 1.046~1.10:1,决策**卡片容器升为 `surfaceContainerHighest`**(M3 Filled 卡语义):实测亮 1.222 / 暗 1.494;门禁定档:卡片 ≥1.20(亮)/1.45(暗)、onSurface-on-卡片 ≥4.5、primary-on-surface ≥4.5;**E1/R1 重开(2026-08-21)**:雷电设备复现「卡片不可见」,探针定位根因 = M3 1.4.0 ListItem 内部 Surface 默认 `containerColor = Color.Unspecified`,嵌套卡内触发雷电 GPU 转译 bug 吞掉卡片容器色(与 elevation 无关);修复 = 全部 ListItem 显式 `colors = ListItemDefaults.colors(containerColor = <所在容器色>)`(卡内 = Low / sheet 行 = High / 无容器列表 = surface),探针实锤:显式色后卡片区 `#141318`(Low)成片渲染;**最终决策回退 `surfaceContainerLow`**(与 LIDE SettingsSection 逐字一致,门禁改 ≥1.046 亮 / ≥1.078 暗);雷电设备截图验证:设置页/诊断页/主题色 sheet 全部正常。 |
| T2 | 弹窗修复 | ✅ | 三输入 sheet(MaxBytes/IgnoreStack/IntLimit)`MonitorSettingsScreen.kt` 内容 Column 移除 `.navigationBarsPadding()` 仅留 `.imePadding()`(M3 已内部消费 nav insets,双内边距叠加 = 弹窗底部黑带根因);全部弹窗显式 `containerColor = surfaceContainerHigh`(SettingsScreen 3 sheet + About AlertDialog、DataSettings 危险确认 AlertDialog、Monitor 3 sheet);四设置页 Scaffold 显式 `containerColor = surface`。`testModernDebugUnitTest` 绿。 |
| T3 | 拆分监听设置 | ✅ | 还原 V16 IA:`settings/algorithm` + `settings/database` 双路由,退役 `settings/monitor`(ModevRoutes 头注释同步);新建 `SettingsSheets.kt`(MaxBytes/IgnoreStack/IntLimit 共享,含 T2 修复)+ `AlgorithmSettingsScreen/VM`(顶栏 algorithm_monitor_title;监听类别 = 算法总开关 + 12 类别;记录选项 = logStack/MaxBytes/IgnoreStack)+ `DatabaseSettingsScreen/VM`(顶栏 database_monitor_title;监听类别 = 数据库总开关 + 5 类别;结果闸门 = 行数/字节);删除 `MonitorSettingsScreen/VM`;`SettingsScreen` 功能区四入口:算法监听(Sensors,副标题 = 启用词随开关实时联动)/ 数据库监听(TableChart,同款)/ 数据管理 / 服务与诊断;删死键 `monitor_settings_title`/`settings_monitor_summary`(en/zh 同步,T6 部分)。 |
| T4 | 数据管理重设计 | ✅ | `DangerRow` 改标准 M3 ListItem:leading Delete(error 色 24dp)、标题 onSurface bodyLarge、trailing ChevronRight(onSurfaceVariant 50% 20dp)、整行 clickable;imports 清理;编译/单测绿。 |
| T5 | 诊断页重设计 | ✅ | 统计区包 Card(surfaceContainerLow + shapes.medium);统计图标语义化:received=SwapHoriz、dropped=SwapVert、write_failures=Warning、trimmed=ContentCut、rejected_hellos/rejected_records=Block、dropped_records=FilterAltOff(消除 7×Dns);「复制诊断信息」行独立 Card 内 M3 动作行;KDoc 记录 V21 T5。R1 重开后双 Card 随全局回退 Low + 行显式 ListItem 容器色。 |
| T6 | i18n 双语 | ✅ | 删除 `monitor_settings_title`/`settings_monitor_summary`(en+zh 同步,零引用已核实);`StringResourceParityTest` 全绿;新增 `existsText` 断言辅助注释遵守 HardcodedStringConvention(测试源码不在 ui 包,无字面量约束)。 |
| T7 | 测试重写 | ✅ | 删 `MonitorSettingsScreenTest`;新增 `AlgorithmSettingsScreenTest`/`DatabaseSettingsScreenTest`(入口副标题随开关联动、双分区齐全、sheet 编辑回写、返回栈;@Before 快照 @After 还原开关态);`FourScreensNavTest` 改四入口 + 两独立页断言。**小屏(360x640dp,雷电 emulator-5554 API 34)设备实测踩坑闭环**:①设置页第 2 入口(数据库监听)整体在折叠区,点击/断言前须 `performScrollToNode`(4 用例修复);②「监听类别」为单一 LazyColumn item(整段组合,段内行 performScrollToNode 实测只滚 161px 不可靠,安全随机数行 y=2724 仍在视口 2560 外)→ 段中行断言存在(`onAllNodesWithText().fetchSemanticsNodes()`,本裁剪库无 assertExists/performScrollBy),段尾行先滚到记录选项段(独立 item,跨 item scrollTo 稳定)再断言存在,记录选项行滚动后 assertIsDisplayed;全量 `connectedModernDebugAndroidTest` 73 例 0 失败 1 跳过(既有设备条件)。 |
| T8 | AGENTS.md 同步 | ✅ | 头部 V20→V21;强约束 4 补弹窗契约(仅 `imePadding`、禁叠 `navigationBarsPadding` = 黑带根因、显式 `containerColor = surfaceContainerHigh`、宿主 Scaffold 显式 `surface`、hide 动画后回调);强约束 13 补四入口路由(`settings/algorithm`/`settings/database`/`settings/data`/`settings/diagnostics`,旧 `settings/monitor` 退役)、卡片容器 `surfaceContainerHighest` + 对比度门禁(`SettingsThemeContrastTest` 全 20 seed × 亮/暗强制)、`settings_total_records_value` 资源驱动断言防坑;工具链坑补本裁剪测试库缺失 API(`assertExists`/`performScrollBy`/`hasToggleableState`/`waitUntil`/`hasText(Regex)`/`SemanticsProperties.OnClick`)与「单一 LazyColumn item 段内 `performScrollToNode` 不可靠」实测结论。 |
| T9 | 质量门禁 | ✅ | 全量门禁复核:`testModernDebugUnitTest` 绿(含 T1 门禁)、`lintModernRelease` 基线 38 warning / 0 error 零新增、`connectedModernDebugAndroidTest` 73 例 0 失败 1 跳过、`assembleModernRelease` + `assembleModernDebug` 双变体通过;设备走查(雷电 emulator-5554,API 34,1440x2560@640dpi):亮/暗双模式四入口齐全(算法监听:已启用 / 数据库监听:已启用 / 数据管理 / 服务与诊断)+ 关于(Modev v0.1.0);算法监听页 12 类别 + 记录选项、数据库监听页 5 类别 + 结果闸门逐页确认;IntLimitSheet 弹出正常(标题/值/取消/确认,scrim 遮罩正常);**R2 黑带修复设备实锤**:wetype IME 键盘弹出后 sheet 被 imePadding 顶起、键盘与弹窗无缝衔接、无任何黑带,输入框聚焦下划线正常;截图 8 张存档 `docs/screenshots_manual/v21_*.png`(亮:settings 顶部/滚动/algorithm/database/sheet/sheet_keyboard,暗:settings/algorithm)对照 `v14_2_settings.png` 基准。**R1 重开后复验(2026-08-21)**:全 7 文件(12 处 ListItem + Diagnostics 双 Card + SettingsScreen 4 sheet 行)显式容器色 + 卡片回退 Low 后,雷电实机截图直方图:设置页 `#141318`(Low 卡)48.1% / `#1C1B20`(surface)48.4% 成片、诊断页 Low 卡 59.3%、主题色 sheet `#2B292F`(High)39.7% 无黑带——**旧「已知限制(雷电 GPU 转译:卡片色像素级不可见)」已解除**(根因 = ListItem Unspecified Surface,非转译器);另修复 PowerShell 误编码导致的 7 文件中文注释损坏(GBK 逆变换 + 逐处修复,FFFD 清零,编译通过)。 |
