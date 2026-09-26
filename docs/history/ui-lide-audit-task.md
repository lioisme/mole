# Modev 前端 LIDE 风格全量审计与对齐任务文档

| 项 | 值 |
|---|---|
| 版本 | 2026-08-16 V4(全部任务完成:G1-G7 全绿,含真机 Redmi 走查) |
| 目标 | 全量审计 Modev 前端 vs LIDE 前端(唯一风格基准源 `../lide`),使设计风格 100% 一致;仅 MD3 组件;界面无描述性/解释性文本;代码质量对齐苹果/谷歌大厂验收标准 |
| 验收红线 | R1/R2/R3 违反项一律视为 P0 缺陷,阻断发版 |

## 1. 需求条款(审计基准,来源:用户需求)

| 条款 | 内容 |
|---|---|
| R1 | Modev 前端设计风格与 LIDE 前端 **100% 一致**(非无脑照搬,结合产品定位与实际功能:功能决定控件/组件) |
| R2 | 仅使用 Material3 官方组件,禁止手搓自研、禁止将就;AppBar 中若有 SearchBar 则不允许出现其他控件,SearchBar 内部左右两侧允许控件按钮 |
| R3 | 界面不添加描述性、解释性文本(状态值、错误态、瞬态反馈、placeholder、contentDescription 除外) |
| R4 | 代码质量与架构达到大厂验收标准:单一事实源、逐参数 token 对齐、对比度门禁、i18n 双语言同步、测试与 lint 全绿 |

## 2. 审计结论(双项目逐项比对,2026-08-16)

### 2.1 已 100% 对齐(逐参数核实,维持现状)

| 领域 | LIDE 基准 | Modev 现状 | 结论 |
|---|---|---|---|
| 主题 | `ui/theme/Theme.kt`(materialkolor SchemeTonalSpot,AppShapes 8/12/16) | `Theme.kt` 同源同构 | 一致 |
| 主题配置 | `ThemeConfig.kt`(ThemeSeeds 20 色 / AppearanceMode / useWallpaper 默认 true / seed 0xFF1976D2) | 同款 | 一致 |
| 主题存储 | `SettingsManager.kt`(StateFlow+SharedPreferences) | `ThemeSettingsManager.kt` 同款 | 一致 |
| 字阶 | M3 官方 Typography | `Typography.kt` 同款 | 一致 |
| 导航 | `NavTransitions.kt`(150ms slide / FULL / QUARTER / 禁 fade)+ `NavigationGate.kt`(16ms 轮询 / 5s 超时) | `NavTransitions.kt` / `NavigationGate.kt` 常量级一致 | 一致 |
| MainActivity | enableEdgeToEdge + systemBarStyleFor + LocaleManager.wrap + LaunchedEffect(isDark) | 同构 | 一致 |
| 顶栏 | `LideTopBar.kt`(surface 容器 / onSurface 标题 / TipIconButton 返回) | `ModevTopBar.kt` 同款 | 一致 |
| 按钮 | `TipIconButton`(TooltipBox + Above + 长按 LongPress haptic) | 同款 | 一致 |
| 空态 | LogcatScreen 空态:44dp icon / onSurfaceVariant@50% / bodyMedium 单行 | `EmptyState.kt` 同款 | 一致 |
| 设置组件 | `SettingsScreen.kt` 内 SettingsSection/Item/ToggleItem/StepperItem(icon 24dp / 间距 16dp / chevron 20dp@50% / 禁用 onSurface@38% / surfaceContainerLow 卡片 / subtitle 可选) | `SettingsComponents.kt` 同款(含 subtitle 参数) | 一致 |
| 设置 Sheet | ModalBottomSheet + titleLarge SemiBold + 行 24/14 + 选中 primary SemiBold + Check 20dp;ColorSheet 5 列网格 / CircleShape / 选中 3dp / 未选中 1dp surfaceVariant@0.3 | 同款 | 一致 |
| 详情页 | `LogcatDetailScreen.kt` 三态(Loading 转圈 + bodyMedium / Failed error + Button 重试 / Loaded 元数据行 + 0.4 分隔线 + 复制 Button icon+6dp+text + Monospace 正文) | `RecordDetailScreen.kt` 同款契约 | 一致 |
| 筛选菜单 | RadioButton 单选 + 分隔线 + error 项 | AppsScreen/AlgorithmListScreen 同款 | 一致 |
| 选中标记色 | `selectedMarkColor` = 暗色恒白 / 亮色恒黑 | `SettingsScreen.kt:570` 同款 | 一致 |

### 2.2 差距清单(本次修复)

| # | 差距 | 位置 | 修复 |
|---|---|---|---|
| G1 | 徽章圆角 8dp(shapes.small),注释与 LIDE 均为 6dp | `RecordRow.kt:114` | T1 |
| G2 | 描述性/解释性文本 15 处(scopeHint 卡、空态第二行、开关副标题 ×9、使用说明区块、免责声明、壁纸说明等) | strings.xml ×2 + 4 个屏幕文件 | T2 |
| G3 | SearchBar 内无返回/清除按钮,与 LIDE SearchInputComponents 不一致 | `SearchInputComponents.kt` + 2 屏 | T3 |
| G4 | 死代码 `SwitchRow.kt` 无引用 | `ui/components/SwitchRow.kt` | T5 |
| G5 | 缺类别色对比度门禁测试(LIDE 有 ThemeContrastTest 对应项) | 测试目录 | T6 |

### 2.3 文档化偏差(用户决策,维持不视为缺陷)

| # | 偏差 | 依据 |
|---|---|---|
| D1 | 设置项仅值类副标题(外观值/主题色值/语言值/版本号),无 LIDE 的描述类副标题 | R3 红线 + 用户决策 |
| D2 | 根页(应用列表)折叠 SearchBar 无返回按钮(根页无返回语义);二级页折叠条有返回 | 功能决定组件 + 用户决策(SearchBar 内允许按钮) |
| D3 | RecordRow 行高 64dp(算法+操作+摘要三行内容) vs LIDE LogRow 56dp(消息+元数据两行) | 用户决策(内容更多属语义适配) |
| D4 | ColorSheet 不显示 wallpaper_desc 副标题(LIDE 有) | R3 红线 |
| D5 | 详情页 Failed 文案保留「记录不存在或已被清理」(= LIDE logcat_detail_missing 同类错误态) | 错误态属状态显示,非解释文本 |

## 3. 变更清单(逐项执行,完成即勾选)

### 3.1 组件层
- [x] T1 `RecordRow.kt` 徽章圆角:删除 `MaterialTheme.shapes.small`(8dp)改用常量 `KIND_BADGE_CORNER = 6.dp`(LIDE LEVEL_BADGE_CORNER 同款),注释与实现一致;行高 64dp 维持(决策 D3)
- [x] T3 `SearchInputComponents.kt` 恢复 LIDE 同款按钮:`SearchInputField` 增加 leading 返回(`onBack` 可空,空=隐藏)+ trailing 条件清除(query 非空时)+ filterSlot;`CollapsedSearchInput` 增加 leading 返回 + trailing 条件清除;新增 `clear_search` 字符串(en/zh)。备注:onBack/onClear 均可空,空值过滤在组件内,调用方只表达意图——根页折叠条 `onBack = null`(D2),两页展开条 onBack = 收起+清焦点(LIDE 同款语义)

### 3.2 页面层
- [x] T2 删除描述性文本(15 处,en+zh 双 locale + 代码位同步):
  - 应用列表:scopeHint 卡片整体移除(AppsScreen 卡块 + AppsViewModel `scopeHint` 字段与 combine 分支 + `apps_scope_hint_*` ×3 字符串);空态单行化(`apps_empty_no_apps`/`apps_empty_no_match` 去第二行)
  - 算法列表:开关副标题 `switch_*_desc` ×9 全部移除(SwitchRowItem 去 subtitle 参数及 9 处调用);`detail_remove_scope` 去括号说明
  - 算法详情:`detail_empty` 单行化
  - 设置页:删除「使用说明」整区块(`settings_usage`/`settings_usage_steps`)、`settings_no_service_hint` 长段(NO_SERVICE 分支仅保留「未连接」状态值)、`settings_disclaimer`(关于弹窗)、`wallpaper_desc`(ColorSheet,D4)
  - 保留:Snackbar 瞬态反馈、错误态文案、placeholder、contentDescription、值类副标题

### 3.3 测试
- [x] T4 值类副标题验证(无代码改动):SettingsScreen 已含外观值/主题色值/语言值/版本号四类副标题(SettingsScreen.kt:149/160/250/256),与决策一致,仅在文档记录
- [x] T3-配套 更新 `SearchInputComponentsTest`(重写 5 例:参数显隐/清除交互/输入回传/长按红线)+ `FourScreensNavTest.appRowOpensAlgorithmList`(算法列表返回按钮 0→1,注释同步)
- [x] T6 新增 `KindColorTokensTest`:类别色 vs 主题 surface、徽章字母 vs 15% 淡底合成色,亮/暗 × 8 类全部 ≥ 4.5:1(materialkolor SchemeTonalSpot 同源算法,LIDE ThemeContrastTest 同款门禁;种子 0xFF1976D2 与 ThemeConfig 同源)。备注:门禁首跑暴露真实缺陷——浅色 KEY_PARAM(#2E7D32)淡底对比度 3.998:1,已按门禁调深至 `#1B5E20`(Material 900),全色板双模式全过

### 3.4 清理
- [x] T5 删除 `ui/components/SwitchRow.kt`(无引用死代码,已 grep 全源码确认零引用;AlgorithmListScreen 内私有 `SwitchRowItem` 为活动代码,保留)

## 4. 设计规范(维持现状,不退化)

- 转场:全目的地 `slideComposable`(150ms,禁止 fade);根页 QUARTER 视差,次级 FULL。
- 搜索状态机红线:`rememberSearchBarState` 无参 / `searchActive` 独立意图 / `LaunchedEffect` 唯一动画驱动 / 收起路径「收起动画 → 完成后导航」同一协程 / `snapTo` 兜底。
- 搜索条按钮:返回/清除置于 SearchBar 内部左右两侧(LIDE 同款);`onBack`/`onClear` 可空,空=无该语义(根页隐藏返回);清除仅 query 非空时显示;长按 tooltip 语义保留。
- 空态:44dp icon(语义图标)+ onSurfaceVariant@50% + bodyMedium 单行居中。
- 设置项:值类副标题(跟随系统/浅色/深色/跟随壁纸/自定义强调色/语言值/版本号);禁止描述句。
- 行规范:徽章固定列 28dp + 12dp 间距 + 内容列;行高 64dp;padding h16/v10;行末 outlineVariant@0.4 细分隔线;徽章圆角 6dp。
- 主题:materialkolor TONAL_SPOT 单一色彩来源;类别色 = KindColorTokens 单一事实源,门禁测试守护对比度。

## 5. 质量门禁(全部通过才算完成)

- [x] G1 `compileModernDebugKotlin` 通过(2026-08-16 实跑 EXIT=0;修复 1 处漏 Import `Icon`)
- [x] G2 `testModernDebugUnitTest` 全绿(71 例,含新增 KindColorTokensTest 2 例;首跑门禁暴露 KEY_PARAM 淡底 3.998:1,调色后全绿)
- [x] G3 `lintModernDebug` 0 error(EXIT=0)
- [x] G4 en/zh 字符串资源 Compare-Object 0 diff(129 keys 完全对等)
- [x] G5 全源码 grep 无已删资源残留引用(仅剩 AlgorithmListScreen 私有 SwitchRowItem 活动代码,非资源引用)
- [x] G6 connected androidTest 全绿(`connectedModernDebugAndroidTest` 实跑 19 例 0 失败,含重写后的 SearchInputComponentsTest 5 例与 FourScreensNavTest 3 例,模拟器 emulator-5554)
- [x] G7 实机走查(2026-08-16,Redmi 25102RKBEC / Android 14,adb UI Automator 逐屏 dump 验证):根页仅筛选按钮无返回、搜索展开返回按钮、输入过滤+清除按钮出现/消失、算法列表搜索条内返回按钮、9 开关无副标题、空态单行、设置页值类副标题(跟随系统/深色/跟随壁纸/语言/版本)、外观 Sheet 深色切换副标题联动、关于弹窗无免责声明、导航往返;实机无框架服务故算法详情页与 RecordRow 徽章视觉无法验证(契约已由模拟器测试覆盖,对比度由 G2 门禁守护),已在文档标注
## 附录 V5:捕获设置独立成页(2026-08-16,用户需求)

**需求**:捕获设置与算法记录列表必须为两个独立界面。

**信息架构(四界面 → 五界面)**:APPS(根页) → ALGORITHM_LIST(每应用记录列表)→ RECORD_DETAIL(每算法);ALGORITHM_LIST → CAPTURE_SETTINGS(每应用捕获设置);SETTINGS 全局。

**变更**:
- [x] ModevRoutes 新增 CAPTURE_SETTINGS = "apps/{packageName}/capture"(五界面注释同步)
- [x] 新建 CaptureSettingsScreen.kt:ModevTopBar(标题=捕获设置)+ 9 开关卡(surfaceContainerLow)+ 移出作用域(仅 scoped)+ 清空日志(errorContainer);与记录列表页共享同一 AlgorithmListViewModel 实例(ModevNav 同 key "algo-list-\"),开关/作用域/消息单一数据源
- [x] AlgorithmListScreen 重构为纯记录列表:删除 CaptureSettingsCard 折叠卡与 SwitchRowItem/DangerBlock(迁至新页),替换为 CaptureSettingsEntry 入口行(SettingsItem 语义:icon + 标题 + chevron,恒显示不吞);onOpenCaptureSettings 参数贯通
- [x] ModevNav 注册 CAPTURE_SETTINGS 目的地(slideComposable + BackGuarded + 空包名兜底)
- [x] FourScreensNavTest 增强:入口行点击 → 捕获设置页断言(switch_cipher 显示 / switch_log_stack 存在 / cd_back=1 / cd_filter=0)→ 返回记录列表 → 返回根页(开关页标志断言覆盖,危险块因模拟器视口懒组合不参与断言)
- [x] 门禁:compileModernDebugKotlin ✓ / 	estModernDebugUnitTest 71 例 ✓ / lintModernDebug ✓ / connectedModernDebugAndroidTest 19 例 ✓
- [x] 实机走查(Redmi 25102RKBEC / Android 14):记录列表页入口行 ✓、入口 → 捕获设置页(顶栏标题 + 9 开关 + 清空日志)✓、开关切换状态联动 ✓、返回导航 ✓、无筛选按钮 ✓
