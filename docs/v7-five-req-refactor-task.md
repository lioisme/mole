# Modev UI 五需求重构任务文档(V7)

| 项 | 内容 |
|---|---|
| 版本 | 2026-08-17 V7(五需求:详情页 AppBar 规范化 / 专业导出 / 应用行重构 / 排序与置顶 / 捕获设置入筛选菜单) |
| 目标 | 按 R1–R5 重构 Modev 前端,设计风格与交互范式继续对齐 LIDE 基准;代码质量、架构、测试与文档达到苹果/谷歌大厂验收标准 |
| 质量红线 | R2(仅 Material3 官方组件、SearchBar 约束)、R3(无描述性文案)维持;P0 违规项一律阻断发版;单测/lint/i18n 对等/实机走查全绿方算完成 |

## 1. 需求条款(审计基准)

| # | 条款 |
|---|---|
| R1 | 算法详情页 AppBar 仅含:返回、标题、上一条、下一条、更多;AppBar 固定高度,标题过长单行省略。 |
| R2 | 更多菜单内仅一个功能:以专业文件格式(JSON,.json)导出算法详情全部数据。 |
| R3 | 主界面应用列表:作用域框选控件置于行尾(右侧);删除运行按钮。 |
| R4 | 应用列表默认按名称排序(社区规范 = Locale Collator,中文按拼音);设置页新增「已作用域应用置顶」开关(默认关)。 |
| R5 | 算法列表页(应用界面)捕获设置入口移入筛选菜单底部,与主界面「设置」项同范式。 |

## 2. 设计决策(用户已确认)

1. 导出格式 **JSON(.json)**,复用现有 `toJson()` 序列化,SAF `ACTION_CREATE_DOCUMENT` 落盘。
2. 详情页复制能力**全部删除,仅留导出**(逐区块复制、堆栈头复制一并移除)。
3. 名称排序用 **`Collator.getInstance(Locale.getDefault())`**,大小写不敏感、稳定排序。
4. 算法列表页头部「启动」按钮**保留**(不在 R3 范围)。

## 3. 违规审计清单(基线,2026-08-17)

| # | 级别 | 位置 | 违规内容 |
|---|---|---|---|
| V1.1 | P0 | RecordDetailScreen.kt:167-187 | 顶栏 actions 超规:额外直挂分享/复制JSON/复制文本三按钮。 |
| V1.2 | P0 | RecordDetailScreen.kt:146-188 | 无「更多」(MoreVert)按钮。 |
| V1.3 | P0 | ModevTopBar.kt:28 | 标题无 maxLines=1/ellipsis,长标题撑高 AppBar,破坏固定高度。 |
| V1.4 | P1 | RecordDetailScreen.kt:150/156 | 更多按钮可用性契约未定义(记录为空时应禁用)。 |
| V2.1 | P0 | RecordDetailScreen.kt:608-626 | 无 SAF 文件导出,仅 ACTION_SEND 分享。 |
| V2.2 | P0 | RecordDetailScreen.kt:479-481,370-377 | 详情页全部复制能力需删除,仅留导出。 |
| V2.3 | P0 | RecordDetailScreen.kt:629-660 | toJson 为 private,导出无法复用。 |
| V2.4 | P1 | strings.xml ×2 | record_copy_json/record_copy_text/record_share 删;新增导出/更多等键。 |
| V2.5 | P1 | RecordDetailScreen.kt | copyQuick/copyBuilt/share/toShareText/shareIntent/常量/copyToClipboard 死代码链。 |
| V2.6 | P2 | AndroidManifest.xml:40-50 + file_paths.xml | FileProvider 孤儿基础设施,清理。 |
| V2.7 | P1 | FourScreensNavTest.kt:122 | record_copy_json 测试锚点失效。 |
| V3.1 | P0 | AppsScreen.kt:387-391 | Checkbox 在行首,须移行尾。 |
| V3.2 | P0 | AppsScreen.kt:448-459 | 行尾 PlayArrow 运行按钮,删除。 |
| V3.3 | P1 | AppsScreen.kt:94,204,277,374 + ModevNav.kt:94 | onLaunch 全链删除(算法列表页 119 行保留)。 |
| V3.4 | P1 | AppsViewModel.kt:56 | AppRow.launchable 死字段。 |
| V3.5 | P2 | AppsScreen.kt:460-467 | Checkbox 移尾后窄屏布局走查。 |
| V4.1 | P1 | AppCatalogRepository.kt:53 | 码点排序非社区规范,改 Collator。 |
| V4.2 | P0 | SettingsScreen/SettingsViewModel | 无「已作用域应用置顶」设置项与持久化。 |
| V4.3 | P0 | AppsViewModel.kt:96-136 | 无置顶逻辑,排序双源风险。 |
| V4.4 | P1 | strings.xml ×2 | 置顶开关文案缺失。 |
| V5.1 | P0 | AlgorithmListScreen.kt:265-267 | 捕获设置入口在内容区,须入筛选菜单。 |
| V5.2 | P0 | AlgorithmListScreen.kt:406-447 | KindFilterMenu 缺「分隔线 + 捕获设置」项。 |
| V5.3 | P1 | AlgorithmListScreen.kt:394-402 | CaptureSettingsEntry 死代码。 |
| V5.4 | P1 | FourScreensNavTest.kt:78,92 | 入口断言路径重写。 |

## 4. 架构设计

### 4.1 排序与置顶(单一事实源)

- 新增 `AppSort`(core/util):`nameComparator()` → `Collator.getInstance(Locale.getDefault())` 比较 label,相等回退 packageName(全序稳定)。**终态排序唯一在 AppsViewModel**。
- AppsViewModel combine:filter → map → sortedWith(置顶开启时先 `!scoped` 升序,再名称比较器;关闭时仅名称比较器)。
- 新增 `AppListSettings`(object,仿 ThemeSettingsManager):`PREF_NAME="modev_applist"`、`KEY_PIN_SCOPED="pin_scoped_on_top"` 默认 false;`StateFlow<Boolean>` + init/set;ModevApp.onCreate 初始化。

### 4.2 详情页 AppBar 与导出

- ModevTopBar 标题 `maxLines=1, overflow=Ellipsis`(固定高度守卫,全局受益)。
- RecordDetailScreen actions 收敛为:上一条 / 下一条 / 更多(MoreVert + DropdownMenu);菜单仅「导出 JSON」一项;更多按钮 `enabled = record != null && loaded`。
- 导出:CreateDocument("application/json") 契约;后台 Default 构建 toJson + runCatching;初始文件名 `modev-record-{id}.json`;成功/失败 snackbar。
- 删除全部复制/分享链路与死代码;toJson/toJsonObject 提级 internal;FileProvider 清理。

### 4.3 应用行(R3)

- Checkbox 移行尾(文本列之后);删除运行按钮、onLaunch 参数链、AppRow.launchable。

### 4.4 捕获设置入口(R5)

- KindFilterMenu 底部:HorizontalDivider + Settings 图标 DropdownMenuItem(文案 detail_tab_settings),点击关菜单 + onOpenCaptureSettings;折叠条与全屏 SearchView 两处 filterSlot 均接入;删除内容区入口行。

### 4.5 设置页(R4)

- SettingsViewModel 暴露 pinScoped StateFlow + setter(委托 AppListSettings);SettingsScreen 新增「应用列表」分区 SettingsToggleItem(PushPin 图标)。

## 5. 变更清单(完成即勾选)

- [x] C1 `ui/components/ModevTopBar.kt`:✅ 标题单行省略(maxLines=1 + Ellipsis,注释注明 V7 固定高度守卫)。
- [x] C2 `ui/logs/RecordDetailScreen.kt`:✅ AppBar 五元素(返回/标题/上一条/下一条/更多菜单);更多菜单唯一「导出 JSON」(SAF CreateDocument、后台 Default 构建 + IO 写流、export_ok/export_failed snackbar);删除 copyQuick/copyBuilt/share/toShareText/shareIntent/SHARE_DIRECT_MAX_CHARS/SHARE_DIR 死链;toJson/toJsonObject 提级 internal;ByteSection/byteSectionItems/LoadedContent 去 onCopyHex。
- [x] C3 `ui/apps/AppsScreen.kt`:✅ AppRowItem Checkbox 移行尾;删 PlayArrow 运行按钮、testTag("app-launch")、onLaunch 参数链。
- [x] C4 `ui/apps/AppsViewModel.kt`:✅ 删 AppRow.launchable;combine 注入 AppListSettings.pinScoped;终态 sortedWith(置顶 compareBy{!scoped} + nameComparator,否则仅名称);排序唯一在 ViewModel(单一事实源)。
- [x] C5 `data/repo/AppCatalogRepository.kt`:✅ 删除 repository 内 lowercase 排序,注释注明终态排序唯一在 ViewModel。
- [x] C6 新增 `AppListSettings`(ui/apps):✅ object + SharedPreferences(modev_applist/pin_scoped_on_top)+ StateFlow,仿 ThemeSettingsManager 范式,init 由 Application 调用。
- [x] C7 `ModevApp.kt`:✅ init(this)。
- [x] C8 `ui/apps/AlgorithmListScreen.kt`:✅ KindFilterMenu 加 onOpenCaptureSettings,两处 filterSlot 接入;菜单底部 HorizontalDivider + Settings 项(detail_tab_settings);删内容区 item("capture-settings-entry") 与 CaptureSettingsEntry 组件及 SettingsItem import;注释更新。
- [x] C9 `SettingsScreen.kt`:✅ 「应用列表」分区 SettingsToggleItem(PushPin、settings_pin_scoped);**偏差**:未动 SettingsViewModel,设置页直连 AppListSettings(与 ThemeSettingsManager 同范式,View 层单行收集 StateFlow)。
- [x] C10 `ui/ModevNav.kt`:✅ 删 AppsScreen onLaunch 传参(AlgorithmListScreen onLaunch=launchApp 保留)。
- [x] C11 `res/values*/strings.xml`:✅ 删 record_copy_json/record_copy_text/record_share;新增 record_more/record_export/export_ok/settings_apps_section/settings_pin_scoped;en/zh 对等(G4 校验)。
- [x] C12 `AndroidManifest.xml` + `res/xml/file_paths.xml`:✅ 删 FileProvider provider 与 file_paths.xml;同时删除 SearchInputComponents.copyToClipboard 死代码(唯一引用者已随 C2 移除)。
- [x] C13 `FourScreensNavTest.kt`:✅ 详情页地标改 record_more → 菜单内 record_export(仅断言不落盘);R5 用例走筛选菜单路径(detail_tab_settings 在菜单内点击);根页断言 app-launch=0、算法列表页断言 app-launch=1;列表返回断言改用 app-launch 计数标记。
- [x] C14 `ModevTopBarTest.kt`:✅ 长标题单行守卫(边界高度 ≤32dp;assertHeightIsAtMost 在现 Compose 版本已移除,改 boundsInRoot 断言)。
- [x] C15 新增 `AppSortTest`(test/.../ui/apps):✅ 5 用例——拼音序(zh)、大小写不敏感(PRIMARY 强度 + 回退包名)、相等回退包名、默认 locale、稳定性;AppSort 比较器加 PRIMARY 强度实现大小写/声调不敏感。
- [x] C16 `docs/`:✅ 本文档;DEVELOPMENT.md / README 同步见下。

## 6. 质量门禁(全部通过才算完成)

- [x] G1 `compileModernDebugKotlin` EXIT=0(含 compileLegacyDebugKotlin、compileModernDebugAndroidTestKotlin 全绿)。
- [x] G2 `testModernDebugUnitTest` 全绿(含 AppSortTest 5/5)。
- [x] G3 `lintModernDebug` 0 error。
- [x] G4 en/zh 字符串 Compare-Object 0 diff(PARITY_OK)。
- [x] G5 全源码 grep 无死资源残留(record_share/record_copy_*/copyToClipboard 已零引用删除;AppInfo.launchable 保留——AlgorithmListScreen 头部启动按钮仍用;其余命中均为 data-class copy())。

## 7. 风险与边界

- SAF 导出无法在插桩测试断言完整落盘,以「菜单项存在 + 点击不崩溃 + 序列化器单测」覆盖;落盘正确性走实机。
- Collator 依赖系统 locale,语言切换后排序自动变化,无需额外重建。
- 导出内容为单条记录,与 toJson 现状一致。