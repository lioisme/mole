# Modev 语言热切换与设置信息架构重组任务文档(V9)

| 项 | 内容 |
|---|---|
| 版本 | 2026-08-17 V9 |
| 需求 | R1:切换语言不重建 Activity、不丢导航/搜索/滚动状态,与主题切换同体验;R2:全量重排前端功能与设置,按功能、层级、领域(部门)组织 |
| 已确认决策 | A:语言切换不写系统 per-app locale,纯热切换; B:「服务与诊断」单页 + 统计折叠; C:全局设置维持筛选菜单单入口(AppBar 仅 SearchBar 规范不动) |
| 质量红线 | 单测/lint/i18n 双语对等/实机走查全绿;P0 违规阻断发布;无描述性注释(代码即文档);沿用 repo 既有红线 |

## 1. 需求条款

| # | 条款 |
|---|---|
| R1 | 设置页选择语言后:**零 Activity 重建**——导航栈、搜索态、滚动位、sheet 全部保留,仅文本重组;行为与主题切换完全同构(StateFlow + CompositionLocal) |
| R2 | 设置页按「显示 / 语言与地区 / 应用列表 / 数据 / 服务与诊断 / 关于」六分区重组;管道统计默认折叠;捕获设置页按「算法捕获 / 记录选项 / 危险区」分组 |

## 2. 架构设计

### 2.1 R1:语言热切换

```
LocaleManager(改造)
 ├─ localeFlow: StateFlow<String>            // 内存单一事实源(对齐 ThemeSettingsManager)
 ├─ setLanguage: 持久化 + flow 更新 + Locale.setDefault   // 删除 setApplicationLocales 分支
 └─ wrap(base): 按 tag 出 createConfigurationContext      // 保留(启动 attachBaseContext + 组合内热切换共用)

MainActivity.setContent:
 ├─ val tag by LocaleManager.localeFlow.collectAsState()
 ├─ val localizedContext = remember(tag) { LocaleManager.wrap(this) }
 └─ CompositionLocalProvider(LocalContext provides localizedContext) { ModevTheme { NavHost } }
```

原理:`stringResource` / `LocalContext.getString` 读取 `LocalContext.current.resources`,覆写该
CompositionLocal 后全站调用点自动以新语言重组;Activity 本体 configuration 不变 → 系统不重建。
`Locale.setDefault` 保证 `java.text`(时间/排序 Collator)同步;SYSTEM 项恢复进程启动时系统默认。

### 2.2 R2:信息架构(功能域分层)

```
① 应用域   应用列表(根页): 搜索/筛选/作用域/排序/置顶/状态
② 捕获域   捕获设置(每应用): 算法捕获 × 记录选项 × 危险区
③ 记录域   算法记录列表 → 记录详情(筛选/浏览/导出)
④ 设置域   偏好(显示/语言/列表) → 数据 → 服务与诊断 → 关于
```

设置页六分区:

| 序 | 分区 | 内容 | 来源 |
|---|---|---|---|
| 1 | 显示 | 外观模式、主题色(sheet) | 不变 |
| 2 | 语言与地区 | 语言(hot-swap) | 通用→上移 |
| 3 | 应用列表 | 已作用域应用置顶 | 不变 |
| 4 | 数据 | 清空全部日志(危险块)+ 记录总数 | 扩展 |
| 5 | 服务与诊断 | 框架与作用域 + 通信服务 + 管道统计折叠卡(默认收起) | 三分区合一 |
| 6 | 关于 | 版本 + 关于 Dialog | 通用(精简) |

捕获设置三卡片:算法捕获(11 类开关)/ 记录选项(堆栈 + 单字段上限 + 忽略栈前缀)/ 危险区。

## 3. 任务清单

### P1:R1 语言热切换

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| P1-1 | LocaleManager:新增 localeFlow;setLanguage 删除 setApplicationLocales;SYSTEM 恢复系统默认 Locale | ✅ 已完成 | `i18n/LocaleManager.kt`:`localeFlow` StateFlow 内存单一事实源;`setLanguage` 仅持久化 + flow 更新 + `Locale.setDefault`(SYSTEM 恢复 init 捕获的进程启动系统默认);删除系统 per-app locale 写入与 TIRAMISU 分支;`systemAppLocalesTag` runCatching 防御(Robolectric 无系统服务时安全);`wrap` 双路径共用(启动 attachBaseContext + 组合热切换) |
| P1-2 | MainActivity.setContent:按 tag 出包装 context,CompositionLocalProvider 覆写 LocalContext | ✅ 已完成 | `ui/MainActivity.kt`:collect `localeFlow` → `remember(tag){ wrap(this) }`(配置级资源缓存,切换廉价)→ 覆写 LocalContext 包 NavHost;原理:stringResource 读 `LocalContext.current.resources`,零重建全站生效 |
| P1-3 | SettingsScreen:删除 recreate/resolveActivity;语言副标题收集 localeFlow | ✅ 已完成 | `ui/settings/SettingsScreen.kt`:语言 sheet `onSelect` 仅 `setLanguage`(删除 API 分支 recreate 与 `resolveActivity` 链及 Build/Activity/ContextWrapper import);`currentLanguageTag` 改 collect `localeFlow`,副标题/勾选态实时刷新 |
| P1-4 | AppsViewModel:combine 增 localeFlow 触发,排序随语言即时重排 | ✅ 已完成 | `ui/apps/AppsViewModel.kt`:combine 增 `LocaleManager.localeFlow` 触发器——AppSort 按调用时 `Locale.getDefault()` 现建比较器,重算即按新语言 Collator 排序(兑现 AppSort.kt 注释承诺) |
| P1-5 | LocaleManager 单元测试(Robolectric) | ✅ 已完成 | `test/.../i18n/LocaleManagerTest.kt` 5 例:resolveTag 三源优先级 / setLanguage 更新 flow+持久化+跨 re-init 恢复 / SYSTEM 恢复进程默认 Locale / wrap 出 zh 资源 / SYSTEM 恒返原 context;`Locale.setDefault` 还原于 tearDown |

### P2:设置页六分区重组

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| P2-1 | strings.xml zh/en:新增语言/服务/关于分区标题,移除废弃分区标题 | ✅ 已完成 | 新增 `settings_language_section` / `settings_service_section` / `settings_about_section` / `settings_total_records(_value)`;移除 `settings_framework_section` / `settings_server_section` / `settings_general_section`;`settings_stats_section` 保留为统计折叠头行标题;双语对等(全量核对) |
| P2-2 | SettingsScreen:六分区重组 + 管道统计折叠卡(AnimatedVisibility + 方向图标,rememberSaveable) | ✅ 已完成 | 六分区序:显示 → 语言与地区 → 应用列表 → 数据 → 服务与诊断 → 关于;统计折叠卡 `StatsToggleRow`(Insights 图标 + 展开↓/收起↑ 方向指示,contentDescription 复用展开/收起)+ AnimatedVisibility,`rememberSaveable` 跨重组存活;服务与诊断单卡内:框架状态 → 通信服务 → 分隔线 → 统计 |
| P2-3 | 数据分区:记录总数只读行(SettingsViewModel 聚合) | ✅ 已完成 | `ui/settings/SettingsViewModel.kt` 新增 `totalRecords`(`observeRecordCounts()` 聚合求和);数据分区 = 总记录数(Storage 图标)+ 清空全部日志危险块 |

### P3:捕获设置页分组

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| P3-1 | CaptureSettingsScreen:算法捕获(11 类)/ 记录选项(堆栈+上限+前缀)SettingsSection 分组,危险区保留 | ✅ 已完成 | `ui/apps/CaptureSettingsScreen.kt`:复用既有 `section_algorithms` / `section_record_options` 资源;算法捕获卡 11 类开关;记录选项卡 = 堆栈开关 + 分隔线 + `OptionRowItem`(上限 ∞ 值 / 忽略前缀计数,primary 值 + 提示副行,Dialog 入口);危险区(移出作用域/清空日志)DangerBlock 保留 |

### P4:回归与收尾

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| P4-1 | 全量单元测试 + lint(modern/legacy)+ androidTest 编译验证 | ✅ 已完成 | `testModernDebugUnitTest` / `testLegacyDebugUnitTest` 全绿(含新增 LocaleManagerTest 5 例);`lintModernDebug` / `lintLegacyDebug` 无错误;`compileModern/LegacyDebugAndroidTestSources` 编译通过;新增 `LanguageHotSwitchTest`(androidTest,断言 onDestroy=0 + 导航栈保留 + 文本即时切换,运行需实机) |
| P4-2 | 任务文档逐项核销 | ✅ 已完成 | 本文档 |

## 4. 验收标准

1. R1:语言切换后无 Activity 重建(onDestroy 不触发)、导航栈/搜索词/滚动位保留;全站文本即时更新;
   排序按新语言重排;时间格式跟随;重启保持;「跟随系统」可用。
2. R1.1(V9.1 补充):语言切换过程无整屏闪烁——点选语言后先播语言面板退场动画,动画完成后再切语言,
   杜绝「面板窗口瞬时消失 + 全站文本同帧切换」的叠影闪烁。
3. R2:设置页六分区、统计默认折叠且状态存活;捕获设置三卡片分组零回归;双语对等无死资源。
4. 单测/lint 全绿。

## 5. 验证记录(2026-08-17)

| 项 | 结果 |
|---|---|
| `testModernDebugUnitTest` / `testLegacyDebugUnitTest`(含 LocaleManagerTest 5 例) | ✅ 全绿 |
| `lintModernDebug` / `lintLegacyDebug` | ✅ 无错误 |
| `compileModern/LegacyDebugAndroidTestSources`(含新增 LanguageHotSwitchTest) | ✅ 编译通过 |
| `connectedModernDebugAndroidTest`(x64 模拟器 Android 14,25 例含 LanguageHotSwitchTest/FourScreensNavTest/ModevTopBarTest) | ✅ 25 例全过(2 skip 为数据依赖防御) |
| V9.1 无闪切换修复(2026-08-17):点选语言 → 面板退场动画(抓帧 a06 实证:面板半滑出 + 屏幕仍旧语言) → 动画完成后切换(抓帧 a07:面板消失 + 新语言)。复现方式:模拟器双相 screencap 抓帧。 | ✅ 实证无叠影 |
| 实机走查 / androidTest 运行(需已 root + 框架环境,非本环境可验) | ⏳ 待真机执行 |

### 5.1 V9.1 无闪切换修复记录(2026-08-17)

- **现象**:热切换(零重建)已生效后,切换语言时整个设置界面仍可见闪烁。
- **根因**:点选语言 = `LocaleManager.setLanguage`(localeFlow 更新,全站文本**当帧**切换)+
  `showLanguageSheet = false`(M3 1.4.0 ModalBottomSheet 移除组合时窗口**瞬时关闭、无退场动画**,
  字节码 `ModalBottomSheetKt` 实证)——双瞬变同帧叠加,整屏内容跳变即「闪烁」。
- **修复**(`SettingsScreen.kt` + `LocaleManager.kt`):
  1. 语言 sheet 状态提升 `rememberModalBottomSheetState()`,点选后在协程内
     `sheetState.hide()`(标准退场动画)→ 动画完成后 `setLanguage` → 再移除组合。
     语言切换只在静态屏上发生,无任何动画与文本切换的叠影。
  2. `LocaleManager.wrap` 改为**纯函数**:移除组合内 `Locale.setDefault` 副作用;
     `Locale.setDefault` 收敛于 `init`/`setLanguage`;包装 Context 按标签
     `ConcurrentHashMap` 缓存(切换热路径零新建、零平台开销)。
- **实证**:模拟器抓帧序列 `[面板全开·旧语言] → [面板半滑出·仍旧语言] → [面板消失·新语言]`。
- **顺带修正两处测试基建**(模拟器实测暴露,与语言功能无关):
  1. `ModevTopBarTest.longTitleStaysSingleLineWithinFixedHeight` 以像素直比 `32f`,
     640dpi 设备上 32dp=128px 恒红 → 改经 `rule.density` 换算。
  2. `LanguageHotSwitchTest` 断言「返回后筛选按钮」经未包装的 Application 上下文取
     `cd_filter` 仍得中文,切换后 UI 已英文 → 改英文字面量「Filter」断言。