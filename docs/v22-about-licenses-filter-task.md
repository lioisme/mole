# Modev V22 任务文档:应用列表筛选收敛 + 关于/开源许可界面 + 全 MD3 组件化

- **版本**:V22 / 2026-08-21
- **范围**:`ui/apps/*`、`data/repo/AppListSettings.kt`、`ui/settings/*`(SettingsScreen + 新建 AboutScreen/LicensesScreen)、`ui/components/RecordRow.kt`、`ui/navigation/ModevRoutes.kt`、`ui/ModevNav.kt`、构建(`gradle/libs.versions.toml`、root/app `build.gradle.kts`)、i18n(en/zh)、测试(单测 + 设备 UI)、AGENTS.md
- **验收基准**:AGENTS.md 现有契约(V21)除本文档修订外全数保持;`lintModernRelease` 基线 38 warning / 0 error 零新增;`testModernDebugUnitTest` 全绿;设备 UI 测试全绿;零弃用 API、零 M2 组件、零手搓交互行

## 1. 需求条款(用户口径,已确认)

| # | 条款 | 审计结论 | 确认决策 |
|---|------|----------|----------|
| R1 | 主界面筛选「仅作用域」删除,替换为设置页「已作用域应用置顶」 | 置顶开关**已存在**(V14 R7,`SettingsScreen.kt:189-198`,默认开);「仅作用域」仍残留在筛选菜单 | 删除 onlyScoped 全管线,置顶保持为唯一作用域排序偏好 |
| R2 | 完善关于界面与开源许可界面 | 关于 = 极简 AlertDialog(名称+版本+OK),零许可功能 | 升级为**独立关于页 + 独立开源许可页**,弃用弹窗 |
| R3 | 全部界面使用 MD3 组件库,禁止手搓自研 | 全量审计:除 `RecordRow` 外已 100% M3 | `RecordRow` 迁移 M3 `ListItem` |
| R4 | 业务/功能用成熟专业方案,组件遵守官方规范 | 许可数据零方案;其余已符合 | 许可 = `aboutlibraries` 插件自动生成(行业事实标准)+ 自建 M3 页面 |

## 2. 全量审计(现状 + 根因)

### 2.1 R1 筛选菜单审计

**现状管线**(「仅作用域」贯穿 4 层):

| 层 | 位置 | 内容 |
|---|---|---|
| 持久化 | `AppListSettings.kt:19,28-31,46,59-65` | `KEY_ONLY_SCOPED`/`_onlyScoped`/`setOnlyScoped` |
| 排序流水线 | `AppsViewModel.kt:61,74,92,99,125,133,141,164,169,195` | `UiState/SortPrefs/UiFilterPrefs/UiPrefs` 4 处字段 + 2 条 `combine` + `.filter { !prefs.onlyScoped \|\| ... }` |
| 主屏 UI | `AppsScreen.kt:166-167,274-275`(两处 filterSlot)、`:211`(空态条件)、`:352`(按钮着色)、`:361-367`(菜单项) | `ScopeFilterMenu` 三项:仅作用域/系统应用/Xposed 模块 |
| i18n | `values/strings.xml:20`、`values-zh/strings.xml:20` | `apps_only_scoped` |

**测试触点**:`FourScreensNavTest.kt:66,90,141`(含 `@After` 复位)、`AppListSettingsTest.kt:42-81`(默认值与持久化两用例)、6 个 androidTest 文件以 `setOnlyScoped(false, ctx)` 作清理(见 §5)。

**根因**:「仅作用域」= 硬过滤(藏行),「置顶」= 软排序(全量可见 + 已作用域优先)。V14 R7 已把置顶收敛进设置页,但仅作用域未同步退役,主屏筛选仍保留三项 —— 与「置顶」功能重叠且藏行损伤可发现性。

### 2.2 R2 关于/许可现状审计

| 项 | 现状 |
|---|---|
| 关于入口 | `SettingsScreen.kt:236-243` 分区末行 `SettingsItem(Info, about, subtitle=version)` → `showAboutDialog` |
| 关于弹窗 | `SettingsScreen.kt:295-317`:Info 图标 + 应用名(titleMedium)+ `Modev v0.1.0`(bodyMedium)+ OK。**无描述、无许可入口、无任何链接** |
| 可用但未用资源 | `app_description` 已存在双语(en/zh),从未被任何界面消费 |
| 许可功能 | **零**:无 res/raw、无资产、无 manifest 活动、无库、无 NOTICE |
| 版本信息 | `BuildConfig.VERSION_NAME = "0.1.0"`、`VERSION_CODE = 1`(`app/build.gradle.kts:27-28`) |
| 测试覆盖 | 无任何关于/许可断言(现状空窗) |
| 文档先例 | `audit-tasks.md` T3 曾定「关于框保持 AlertDialog」——被 R2 推翻,需同步修订 `AGENTS.md` 契约 4 |

**导航模板**(已确认,许可证页照抄):`ModevRoutes` 常量(`ModevRoutes.kt:19-29`)→ `SettingsScreen` 增 `onOpenX` 回调 → `ModevNav.kt` `slideComposable + BackGuarded + popGuarded`(`ModevNav.kt:242-249` Diagnostics 为范本)→ `ModevTopBar`(title + 返回,`ModevTopBar.kt:26-30`)。

### 2.3 R3 MD3 全量审计(54 文件,含对 material3 1.4.0 构件反编译核验)

**基线事实**:零 M2 组件导入;零弃用 API;零中文字面量;全部 `.sp` 仅在 `theme/Typography.kt` 且与官方字阶逐字一致;屏内 16dp、sheet 24dp;零 `navigationBarsPadding` 叠加;所有行组件 = `ListItem` 且显式 `containerColor`。

**结构性违规(1 处,本次必修)**:

| 组件 | 位置 | 判定 |
|---|---|---|
| `RecordRow` | `RecordRow.kt:75-110` | `Surface + Row + combinedClickable` 手搓卡片 —— 全 App 唯一结构性非 M3 交互行,恰为契约 13 明令禁止的模式 |

**小项(本次顺手修订)**:

| 项 | 位置 | 处理 |
|---|---|---|
| `TimelineHeader` 容器 token | `TimelineHeader.kt:25` `surfaceVariant` | → `surfaceContainerHigh`(1.4.0 容器家族指南;`surfaceVariant` 未弃用但属旧代) |
| 硬编码 `" · "` 拼接 | `RecordRow.kt:130,174,176` | → 资源格式串 `record_kind_operation_format`(借 ListItem 迁移一并落地) |
| 硬编码 `" ($rowCount, $affected)"` | `RecordRow.kt:213-215` | → 资源格式串(可选项) |

**文档化例外(保留,均已 KDoc 固化)**:`CollapsedSearchInput`(契约 13 唯一例外)、设置页色板网格(无 M3 对应物,含 semantics)、`StatusDot`/`AppIcon`/`EmptyState`(纯视觉原语)、`KindColorTokens`/`StatusColorTokens`(对比度门禁守护的自定义语义色板)。

### 2.4 R4 成熟方案审计

| 需求 | 方案 | 依据 |
|---|---|---|
| 许可数据收集 | `com.mikepenz.aboutlibraries:core` + Gradle 插件(官方) | 行业事实标准(Google/Microsoft 系开源 App 普遍使用);从依赖 POM 元数据自动收集**直接 + 传递**依赖与许可全文,零手工漂移;插件生成数据随变体自动更新 |
| 许可 UI | 自建 M3 页面(列表 → 详情两层级) | R3 强制全 MD3;`aboutlibraries-compose` 默认皮肤非 M3 定制,自建更可控且与全 App 视觉一致 |
| 关于页 | AOSP「Settings → About」范式 | 谷歌验收标准:标识区(图标/名称/版本/描述)+ 法律入口 + 版本行 |
| 筛选菜单 | 保留 `DropdownMenu + DropdownMenuItem + 只读 Checkbox` | Google Maps/Drive 同款社区惯例,契约 8 语义已固化;删除后仅剩 2 项,形态无需变更 |

## 3. 目标设计规格

### 3.1 R1 应用列表筛选收敛

- `ScopeFilterMenu`(`AppsScreen.kt:334-386`):删 `onlyScoped` 参数与「仅已作用域」菜单项,仅留「系统应用 / Xposed 模块」;KDoc 同步。
- 筛选按钮着色条件:`AppsScreen.kt:352` → `if (!showSystem || !showModules)`。
- 空态条件:`AppsScreen.kt:211` → `if (search.queryText.isNotBlank())`(query 空且无行 → `apps_empty_no_apps`)。
- `AppListSettings`:删 `KEY_ONLY_SCOPED`/`_onlyScoped`/`onlyScoped`/`setOnlyScoped`(孤儿 pref 键无害,不做数据迁移)。
- `AppsViewModel`:删 `UiState.onlyScoped`、`SortPrefs.onlyScoped`、`UiFilterPrefs.onlyScoped`、`UiPrefs.onlyScoped` 及两条 `combine` 中对应成员与 `.filter` 行。
- 置顶开关(`SettingsScreen.kt:189-198`)原样保留 —— 即 R1 的「替换目标」,默认开、即时重排,无需任何改动。

### 3.2 关于页规格(新路由 `settings/about`)

```
Scaffold(containerColor = surface) + ModevTopBar(about)
├─ 标识区(居中,h24 内边距):AppIcon(packageName=自身, 64dp)
│   app_name(headlineSmall, onSurface) / version 行(bodyMedium, onSurfaceVariant)
│   app_description(bodyMedium, onSurfaceVariant, 居中, 复用现有双语键)
└─ SettingsSection(settings_about_section):
    ├─ SettingsItem(Code 图标, licenses_title, subtitle=licenses_subtitle(%1$s=app_name)) → 开源许可页
    └─ SettingsInfoItem(Info 图标, about_version, value="Modev v0.1.0")
```

- `SettingsScreen.kt`:删 `showAboutDialog`/`AlertDialog` 块与相关 import;About 分区改为单行 `SettingsItem(about, subtitle=version)` → `onOpenAbout`。
- 路由注册:`ModevRoutes.ABOUT = "settings/about"` + `slideComposable`/`BackGuarded`/`popGuarded`(照 Diagnostics 模板)。

### 3.3 开源许可页规格(新路由 `settings/licenses`、`settings/licenses/{index}`)

- **构建接入**:root `build.gradle.kts` 声明插件 `com.mikepenz.aboutlibraries.plugin`(apply false);app 模块 apply 插件 + 依赖 `com.mikepenz.aboutlibraries:core`;版本取 12.x 最新稳定,与 AGP 8.13.2 兼容性见 §7 风险。
- **列表页**(`settings/licenses`):`ModevTopBar(licenses_title)` + `LazyColumn`;每行 = M3 `ListItem`(无容器列表 `containerColor = surface`),headline = `library.name`(bodyLarge),supporting = `licenses_row_format`(`%1$s · %2$s` = 版本 · 许可名,bodySmall onSurfaceVariant),trailing = ChevronRight,`key = library.id`;点击 → `navigateGuarded(settings/licenses/{index})`。数据经 `LibrariesLoader.loadLibraries(context)`(aboutlibraries core 标准入口,同步小数据量,`remember` 缓存)。
- **详情页**(`settings/licenses/{index}`,`NavType.IntType`):`ModevTopBar(library.name)`;库名(headlineMedium)+ 版本(bodyMedium onSurfaceVariant)+ 许可名列表 + 完整许可全文(`SelectionContainer` + bodySmall,多许可依次渲染);`index` 越界或库不存在 → `popWhenReady` 兜底(与 `recordId` 同款 `ModevNav.kt:140-143`)。

### 3.4 RecordRow 迁移规格(`RecordRow.kt:75-110`)

| M3 槽位 | 内容 |
|---|---|
| `headlineContent` | 算法/SQL headline(bodyLarge SemiBold Monospace,单行省略)—— 现状 L117-125 / L162-170 |
| `supportingContent` | Column:类别·操作行(labelMedium 类别色,`record_kind_operation_format`)+ `DirectionChip`(仅算法形态,保留 AssistChip)+ 数据段(labelMedium Monospace) |
| `trailingContent` | 秒级时间(labelSmall onSurfaceVariant),`Modifier.align(Alignment.Top)` 顶对齐(实现时验证 M3 1.4 默认对齐,必要时显式修正) |
| `modifier` | `combinedClickable(onClick, onLongClick)` + 触觉反馈(保留);`shape = MaterialTheme.shapes.large`、`colors = ListItemDefaults.colors(containerColor = surfaceContainerLow)` |

- 视觉保真:卡片视觉(large 圆角 + surfaceContainerLow 浮于 surface)+ 行间 8dp 留白(宿主 LazyColumn 间距,实现时核对 `MonitorRecordsScreen.kt` 保持)。
- 行为保真:单击进详情、长按复制摘要 + 触觉 —— `RecordListScrollPreservationTest`/`TimelineListTest` 守护。

### 3.5 i18n 规格(双语齐备,`StringResourceParityTest` 强制)

| 动作 | 键 | en | zh |
|---|---|---|---|
| 删除 | `apps_only_scoped` | — | — |
| 新增 | `about_version` | Version | 版本 |
| 新增 | `licenses_title` | Open source licenses | 开源许可 |
| 新增 | `licenses_subtitle` | Third-party components used by %1$s | %1$s 使用的第三方组件 |
| 新增 | `licenses_row_format` | %1$s · %2$s | %1$s · %2$s |
| 新增(可选) | `record_kind_operation_format` | %1$s · %2$s | %1$s · %2$s |
| 复用 | `app_description`/`about`/`version`/`settings_about_section` | 已存在 | 已存在 |

## 4. 实施计划(文件级任务分解)

| # | 任务 | 文件 | 要点 |
|---|---|---|---|
| T1 | 筛选收敛 | `AppListSettings.kt`、`AppsViewModel.kt`、`AppsScreen.kt`、strings×2 | §3.1 全管线删除;KDoc(V3 注释、`ScopeFilterMenu` 注释)同步 |
| T2 | 关于页 | 新建 `ui/settings/AboutScreen.kt`;`SettingsScreen.kt`、`ModevRoutes.kt`、`ModevNav.kt`、strings×2 | §3.2;删 AlertDialog 相关 import/状态 |
| T3 | 开源许可页 | root/app `build.gradle.kts`、`libs.versions.toml`;新建 `ui/settings/LicensesScreen.kt` + `LicenseDetailScreen.kt`;`ModevRoutes.kt`、`ModevNav.kt`、strings×2 | §3.3;IndexType 参数 + 越界兜底 |
| T4 | RecordRow 迁移 | `RecordRow.kt`、strings×2、核对 `MonitorRecordsScreen.kt` 间距 | §3.4;`" · "` 资源化 |
| T5 | MD3 小项 | `TimelineHeader.kt:25` | `surfaceVariant` → `surfaceContainerHigh` |
| T6 | 测试 | 见 §5 | 单测 + androidTest + lint 门禁 |
| T7 | 契约修订 | `AGENTS.md`、本文档落盘 | 见 §6 |

## 5. 测试计划

**单元测试**(`app/src/test`,Robolectric):
- `AppListSettingsTest.kt`:删 `set onlyScoped and showSystem persist and reload` 用例中 onlyScoped 分支与 `defaults` 用例断言;保留 pinScoped/showSystem/showModules 契约。

**设备 UI 测试**(`app/src/androidTest`):
- `FourScreensNavTest.kt`:删 `:66`(`@After` 复位 onlyScoped)、`:90`、`:141`(仅作用域断言/点击);保留 `:136`(`settings_pin_scoped` 不存在于菜单 —— 反向守护 R1);补断言:筛选菜单仅含「系统应用」「Xposed 模块」两项(`onAllNodes(hasText(...) and hasAnyAncestor(isPopup()))` 计数)。
- 其余 5 个文件(`CryptoDetailZonesTest`/`MonitorEntryHeaderTest`/`TimelineListTest`/`MonitorListScreensTest`/`RecordListScrollPreservationTest`):删除 `setOnlyScoped(false, ctx)` 清理调用。
- **新增 `AboutLicensesTest.kt`**:设置 → 关于 → 断言名称/版本/描述/「开源许可」入口 → 进入许可列表 → 断言 ≥1 个库行(如 androidx 系)→ 进入详情 → 断言许可文本含 "Apache License" 片段(长文本滚动用 `performScrollToNode`)。

**门禁**:`.\gradlew.bat :app:testModernDebugUnitTest`、`.\gradlew.bat :app:lintModernRelease`(基线 38w/0e 零新增)、`assembleModernDebugAndroidTest + connectedModernDebugAndroidTest`(重任务串行,守约 AGENTS.md 工具链坑)。

## 6. AGENTS.md 契约修订清单

1. **契约 4**:「仅危险确认/关于类保留居中 AlertDialog」→「仅危险确认保留居中 AlertDialog;关于/开源许可 = 独立页(`settings/about`、`settings/licenses`)」。
2. **契约 7**:「测试需先 `setShowModules(true)` 并复位 `setOnlyScoped(false)`」→ 删 onlyScoped 复位引用。
3. **契约 13**:设置页功能区四入口 → 五入口描述(功能四入口 + 关于页);MD3 例外清单更新(`RecordRow` 自清单移除,已 M3 化)。
4. **词汇表**:无变化(仅作用域非词汇表词)。

## 7. 风险与回退

| 风险 | 影响 | 应对 |
|---|---|---|
| aboutlibraries 插件与 AGP 8.13.2/Gradle 版本兼容性(插件对构建版本敏感) | T3 阻塞 | 回退:约 `res/raw/library_definitions.json` 手工维护(与插件生成物同构,core 可加载);再退:静态资源自解析 + 自建 M3 页 |
| `ListItem` trailing 对齐/形状与现卡片视觉差异 | 视觉回归 | 实现后人工比对截图;`TimelineListTest`/`RecordListScrollPreservationTest` 行为回归守护 |
| 许可全文超长文本渲染 | 详情页性能 | 许可文本 ≤ 数百 KB,`Text` 直渲足够;若超限走 `ChunkedText`(现成组件) |
| lint 新 warning | 门禁失败 | aboutlibraries core 无 UI,不触发 UI 类 lint;新增资源串遵守 Plurals/占位符形态 |

## 8. 验收检查单(苹果/谷歌级验收标准)

- [ ] 主屏筛选菜单无「仅作用域」,仅「系统应用 / Xposed 模块」;设置页「已作用域应用置顶」默认开、即时重排,行为级 UI 测试通过
- [ ] 关于页:图标/名称/版本/描述/开源许可入口/版本行全元素齐备,双语渲染正确,`AlertDialog` 代码零残留
- [ ] 开源许可页:列表 → 详情两层级可达,数据含全部直接 + 传递依赖(含 readmore-material3、materialkolor、gson、libxposed 等),许可全文可读可选中
- [ ] 全 App 交互组件 100% M3 `ListItem`/`Switch`/`Card`/`Button`/`Dialog`/`Sheet`,零手搓交互行(审计表 + lint 守护)
- [ ] 无弃用 API、无 M2 组件、无中文字面量、双语文案键齐备(`StringResourceParityTest`)
- [ ] 单元测试全绿、设备 UI 测试全绿、`lintModernRelease` 零新增 warning
- [ ] `AGENTS.md` 契约与本文档落盘于 `docs/v22-about-licenses-filter-task.md`

---

## 9. 实施状态跟踪(自驱动,逐项标记)

> 每完成一项任务,在此登记完成状态与工作内容备注。

| # | 任务 | 状态 | 完成备注 |
|---|---|---|---|
| T0 | 任务文档落盘 | ✅ 已完成 | 本文档创建于 `docs/v22-about-licenses-filter-task.md`,含需求条款/全量审计/设计规格/实施计划/测试计划/契约修订/风险/验收单 |
| T1 | 筛选收敛(仅作用域全管线删除) | ✅ 已完成 | `AppListSettings` 删 onlyScoped 区块(KEY/StateFlow/init/setter);`AppsViewModel` 删 UiState/SortPrefs/UiFilterPrefs/UiPrefs 4 处字段 + 2 条 combine 成员 + 过滤行;`AppsScreen` 删两处 filterSlot 参数、菜单项、tint/空态条件,KDoc 同步;strings 双语删 `apps_only_scoped`;单测 `AppListSettingsTest` 重构(删 onlyScoped 用例);androidTest 全量清理 `setOnlyScoped` 触点(6 文件),`FourScreensNavTest` 菜单断言改为「仅系统应用/Xposed 模块」两项计数;`:app:compileModernDebugKotlin` + `compileModernDebugUnitTestKotlin` 编译通过 |
| T2 | 关于页 | ✅ 已完成 | 新建 `ui/settings/AboutScreen.kt`(AOSP About 范式:64dp 图标 + 名称 headlineSmall + 版本 + `app_description` 描述 + 开源许可入口 SettingsItem + 版本 SettingsInfoItem 只读行);`SettingsScreen` 删 showAboutDialog/AlertDialog 块与 import,About 分区改为 `onOpenAbout` 导航,KDoc 同步;`ModevRoutes` 新增 `ABOUT`/`LICENSES`/`LICENSE_DETAIL` 常量 + `licenseDetail(index)`;`ModevNav` 注册 About 目的地 + 回调接线;strings 双语新增 `about_version`/`licenses_title`/`licenses_subtitle`/`licenses_row_format`;`:app:compileModernDebugKotlin` 编译通过 |
| T3 | 开源许可页 | ⏳ 待开始 | — |
| T4 | RecordRow 迁移 M3 ListItem | ⏳ 待开始 | — |
| T5 | TimelineHeader 容器 token 现代化 | ⏳ 待开始 | — |
| T6 | 测试(单测修订 + AboutLicensesTest + 门禁回归) | ⏳ 待开始 | — |
| T7 | AGENTS.md 契约修订 + 文档完结 | ⏳ 待开始 | — |