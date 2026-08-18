# Modev 任务书 V13:详情页去折叠 + 整段复制 + 列表徽章移除与空白治理

**文档版本**: v1.0 | **创建日期**: 2026-08-17 | **状态**: 执行中
**适用范围**: `ui/logs/RecordDetailScreen.kt`、`ui/components/RecordRow.kt`、`ui/apps/AppRecordsScreen.kt`、`ui/apps/AppsScreen.kt`、`ui/apps/AppsViewModel.kt`、`ui/ModevNav.kt`、`ui/components/SearchInputComponents.kt`、`ui/components/SettingsComponents.kt`、`ui/settings/SettingsScreen.kt`、`core/util/LineEstimate.kt`(删除)、i18n 资源(en/zh)、测试三件套
**质量目标**: 代码质量、架构设计达到苹果/谷歌大厂验收标准(单一事实源、虚拟化与性能底线、无障碍语义、i18n 零硬编码、门禁全绿)

---

## 0. 需求条款(用户口径)

| # | 需求 |
|---|------|
| R1 | 算法详情界面:**不要折叠收起长文本**(当前超 8 行区块默认收起);**长按复制要求复制整段文本**,而不是一段文本只能一次复制一部分(当前逐块 SelectionContainer 被 4K 块边界截断) |
| R2 | 算法列表界面:item 前带字母的类别徽章卡片**无用,删除** |
| R3 | 算法列表界面:item 中字段文本空缺时**不得遗留空白区域**(已确认 = 固定最小高度 68dp + 垂直居中造成的卡片内上下留白) |

## 1. 全量审计(现状 + 根因)

### 1.1 R1a 详情页折叠 — 违规清单

| # | 位置 | 违规内容 |
|---|------|----------|
| V1-1 | `RecordDetailScreen.kt:268` | 折叠状态机 `userToggledFold`(rememberSaveable,List<String> XOR 语义) |
| V1-2 | `RecordDetailScreen.kt:292,307,317,327,337,347,357` | 7 个字节区块 `needsFold(...) xor ("key" in userToggledFold)` 默认收起判定 |
| V1-3 | `RecordDetailScreen.kt:474-546` | `ByteSectionHeader`:AnimatedVisibility 折叠预览(截断至阈值行数)/ 内联全量互斥 + 「展开/收起」TextButton |
| V1-4 | `RecordDetailScreen.kt:578-603` | `byteSectionItems` 展开态条件组合(`if (!folded && needsFold(...))`) |
| V1-5 | `RecordDetailScreen.kt:610-639` | `FoldMetrics`/`needsFold`/`rememberFoldMetrics` 行数估算体系 + 两个私有常量 |
| V1-6 | `RecordDetailScreen.kt:549-550` | `textPreviewOf` 截断预览函数 |
| V1-7 | `core/util/LineEstimate.kt`(全文件)+ `LineEstimateTest.kt` | 折叠专用纯函数;去折叠后全工程唯一引用方消失 → **死代码,整体删除** |
| V1-8 | `ByteSectionTest.kt:127-273` | 6 个折叠契约用例(默认折叠/预览截断/双向切换/互斥过渡)需重写为无折叠契约 |

**影响**:大数据区块(如 1MB 密钥)默认仅显示 ≤8 行预览,取证信息不可直接获取;与「详情页 = 完整数据」的产品语义冲突。折叠按钮/预览/动画为纯冗余 UI 复杂度。

### 1.2 R1b 长按复制仅限分块 — 违规清单

| # | 位置 | 违规内容 |
|---|------|----------|
| V2-1 | `RecordDetailScreen.kt:581-600` | 逐块 `SelectionContainer`:单块 4096 字符,选择范围被块边界截断;2MB hex = 512 块,整段需 512 次复制 |
| V2-2 | 复制语义缺口 | 无「整段复制」出口;完整数据唯一出口 = 导出 JSON,缺失文本级整段复制路径 |

**已合规(保留不动)**:`MetadataBlock` 整块 SelectionContainer(`:400-433`)、`SectionText` 单 Text 整段可选(`:666-676`)、堆栈逐帧 SelectionContainer(单帧=单行,语义自洽)。

**技术约束(DR2 依据)**:`SelectionContainer` 的选择手势注册表会消费子树长按事件,自定义 `combinedClickable`/`pointerInput` 长按与其互斥,不可共存 → 必须二选一。

### 1.3 R2 列表字母徽章 — 违规清单

| # | 位置 | 违规内容 |
|---|------|----------|
| V3-1 | `RecordRow.kt:94-95` | `KindBadge` 调用 + `BADGE_TO_CONTENT_GAP`(14dp) |
| V3-2 | `RecordRow.kt:143-161` | `KindBadge` 组件本体(28dp 圆角方块 + 15% 淡底 + 类别色字母) |
| V3-3 | `RecordRow.kt:182-191` | `KIND_BADGE_SIZE`/`BADGE_TO_CONTENT_GAP`/`KIND_BADGE_ALPHA`/`KIND_BADGE_CORNER` 常量 |
| V3-4 | `RecordRow.kt:211-224` | `kindSymbol` 类别→字母映射(12 分支) |
| V3-5 | `RecordRowLayoutTest.kt:80-88` | `badge_showsKindLetterW_forWebCrypto` 用例 |
| V3-6 | `KindColorTokensTest.kt:73-88` | `kindBadgeLetterMeetsAaOnTintedBadgeForBothModes` 用例语义过时(15% 淡底承载方变更为 DirectionChip) |

**冗余论证**:徽章字母 = 类别色,与 operation 行类别色**双重承载同一信息**;删徽章后信息零丢失。

### 1.4 R3 卡片内上下留白 — 违规清单(用户已确认)

| # | 位置 | 违规内容 |
|---|------|----------|
| V4-1 | `RecordRow.kt:89` | `heightIn(min = RECORD_ROW_MIN_HEIGHT)` 固定最小高度 68dp |
| V4-2 | `RecordRow.kt:91` | `verticalAlignment = Alignment.CenterVertically` → 短内容(无方向/无数据段的 3 行卡片)内部上下留白 |
| V4-3 | `RecordRow.kt:194` | `RECORD_ROW_MIN_HEIGHT` 常量 |

### 1.5 全量审计其余违规(同族问题 + P0 功能缺陷)

**P0 功能/无障碍缺陷(本次一并修复):**

| # | 位置 | 违规内容 | 处置 |
|---|------|----------|------|
| V5-1 | `AppsViewModel.kt:185-192` | `refresh()` 无 try/finally:异常时 `refreshing` 恒 true,下拉刷新永久卡死 | T6 |
| V5-2 | `AppsViewModel.kt:195-203` | `toggle()` 无 try/finally:异常时 `pending` 不清除,勾选框永久禁用 | T6 |
| V5-3 | `AppsViewModel.kt:285-297` | `removeScope()` 缺 `ScopeRequested` 分支 → 请求未确认误报「移除失败」 | T6 |
| V5-4 | `AppsScreen.kt:479-485` | `countText` locale 缺陷:语言=跟随系统 + 中文系统时资源已切 zh 但 `wanScale` 仍 1000 → 万级缩放错误 | T7 |
| V5-5 | `ModevNav.kt:147` | RECORD_DETAIL 缺参 → 渲染空白卡死页(与 APP_RECORDS `:125-128` 自动 pop 处理不一致) | T8 |
| V5-6 | `SearchInputComponents.kt:137-147` | 折叠搜索框 pointer-only 无 semantics,TalkBack 不可达 | T9 |
| V5-7 | `SettingsComponents.kt:105-137` | 开关行 `clickable` 非 `toggleable`,Switch 节点无标签(TalkBack 读「开关,开」无名称,13 处实例化) | T10 |
| V5-8 | `SettingsScreen.kt:472-497` | 色板 swatch 无 contentDescription(20 色网格对 TalkBack 不可用) | T10 |
| V5-9 | `SettingsScreen.kt:318-395` | Sheet 选项行 `clickable` 非 `selectable`,选中态不播报 | T10 |
| V5-10 | `AppsScreen.kt:423` | 记录数徽章缺失时无条件 `Spacer(6.dp)` 残留间距(同族「空缺留白」) | T7 |
| V5-11 | `AppsScreen.kt:404-407` | `combinedClickable(onClick, onLongClick = null)` = 裸 `clickable` + 强制 Experimental API + 死长按 | T7 |
| V5-12 | `RecordRow.kt:63,84-87` | 长按回调默认空实现但**恒触发触觉反馈**(死交互);KDoc 承诺「长按复制记录文本」未接线 | T5 |

**P1/P2(记录备案,后续迭代,本次不实施):**
`SettingsInfoItem` 值 2 行省略无展开/复制(`SettingsComponents.kt:161-169`)、Diagnostics 统计区默认收起(`DiagnosticsScreen.kt:61,132`)、ignoreStackPrefixes 仅显数量(`CaptureSettingsScreen.kt:178-184`)、框架错误消息不可复制(`DiagnosticsScreen.kt:106-116`)、包名无法复制(`AppsScreen.kt:440-447`)、SearchState `pending` 非 saveable、SearchBar 恒组合双实例、MaxBytesDialog 无校验(∞/0 双表示)、`CapabilityUiRegistry.all()` 死代码、`SwitchRowItem` 纯透传、三份重复行布局、数据/诊断入口混入 Capabilities 分组、`"∞"`/`"?"` 硬编码、`Color.White/Black`/alpha/dp 字面量、SettingsComponents 死 import、孤儿注释、`Spacer(padding)` 非惯用、M3 区块标题偏离、`fillMaxWidth(0.92f)` 魔法数、手写搜索条指标、NavigationGate 文档与实现不符、Theme 明暗双份 60-token、`enc()` 无意义编码、NavTransitions QUARTER 混合位移、BackHandler 全限定名、`items` 内 `last()` O(n)。

## 2. 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 去折叠语义 | 所有字节区块**恒展开**;**保留** `ChunkedText` 惰性分块 + LazyColumn 虚拟化(性能底线:1MB×3 字段 + 1MB 文本不卡顿不闪退,既有插桩契约回归);`ByteSectionHeader` 简化为「标题 + 字节数 + 复制按钮」footer |
| D2 | 整段复制语义 | 每块 Text 挂 `pointerInput(detectTapGestures.onLongPress)`(无 semantics 污染,512 块不产生 512 个 button)→ 剪贴板写入**整段**:hex 块长按复制全文 hex,text 块长按复制全文 text;触觉反馈 + Snackbar(含区块名 + 字符数);**a11y 兜底** = footer 复制图标按钮(TipIconButton,contentDescription 本地化);移除逐块 SelectionContainer(与长按互斥,见 1.2 约束) |
| D3 | 列表行布局 | 删除徽章与间距常量;删除 `heightIn(min=68dp)` + 垂直居中 → 内容自适应高度、顶对齐;字段空串 `isNotBlank()` 归一不渲染 |
| D4 | 列表长按复制 | 修复死交互:`RecordRow.onLongClick` 由 `AppRecordsScreen` 接线 → 复制记录摘要文本(算法/操作/方向/数据段/时间/包名,多行)+ Snackbar;新增 `@Composable CryptoRecord.copySummary()` 单一事实源 |
| D5 | i18n | 新增 3 键:`section_copy`(footer 按钮)、`record_copy_done`(含 `%1$s` 区块名 + `%2$d` 字符数)、`record_copied`(列表摘要);en/zh 同步,键对 diff=0。**`section_expand`/`section_collapse` 保留**(`DiagnosticsScreen.kt:203` 统计折叠复用),仅移除详情页引用 |
| D6 | 删除范围 | `LineEstimate.kt` + `LineEstimateTest.kt` 整体删除(grep 确认仅详情页折叠 + 测试引用);`ChunkedText.kt` 保留 |
| D7 | P0 修复 | AppsViewModel try/finally + ScopeRequested 分支;countText 改按解析后 locale 判定;ModevNav 缺参弹栈;搜索框 semantics;Settings toggleable/selectable/contentDescription |

## 3. 改动清单(执行顺序)

- [ ] T1 本任务书落盘
- [ ] T2 i18n:en/zh 新增 `section_copy`/`record_copy_done`/`record_copied`
- [ ] T3 `ui/logs/RecordDetailScreen.kt` 重构(核心):去折叠 + 整段复制 + ByteSectionHeader 简化
- [ ] T4 删除 `core/util/LineEstimate.kt` + `LineEstimateTest.kt`
- [ ] T5 `ui/components/RecordRow.kt` + `ui/apps/AppRecordsScreen.kt`:去徽章 + 空白治理 + 长按复制摘要
- [ ] T6 `ui/apps/AppsViewModel.kt`:try/finally + ScopeRequested 分支
- [ ] T7 `ui/apps/AppsScreen.kt`:countText locale 修复 + 6dp 间距条件化 + clickable 简化
- [ ] T8 `ui/ModevNav.kt`:RECORD_DETAIL 缺参弹栈
- [ ] T9 `ui/components/SearchInputComponents.kt`:折叠搜索框 semantics(role+onClick)
- [ ] T10 `ui/components/SettingsComponents.kt` + `ui/settings/SettingsScreen.kt`:toggleable/selectable/contentDescription
- [ ] T11 测试:`ByteSectionTest` 重写、`RecordRowLayoutTest` 更新、`KindColorTokensTest` 语义更新、删 `LineEstimateTest`
- [ ] T12 质量门禁:双变体单测 + lint + androidTest 编译
- [ ] T13 文档执行记录勾选 + 验收收尾

## 4. 测试计划

| 层 | 用例 | 断言要点 |
|----|------|----------|
| 插桩(重写) | `ByteSectionTest` 无折叠契约 | ①小数据全量可见单块;②大数据惰性分块(末块滚动前不在树,滚动后组合);③hex 块长按 → 剪贴板 = 全文 hex;④text 块长按 → 剪贴板 = 全文 text;⑤footer 复制按钮 → 剪贴板 = 全文 hex;⑥全量 hex 永不单节点;⑦300K hex/text 惰性滚动回归 |
| 插桩 | `RecordRowLayoutTest` | 删 badge 用例;新增 ⑧长按回调仍触发;⑨`copySummary()` 含算法/操作/时间/包名 |
| 单测 | `LineEstimateTest` | 删除(死代码) |
| 单测 | `KindColorTokensTest` | 用例更名 + 注释指向 DirectionChip(15% 淡底承载方) |
| 回归 | `FourScreensNavTest`/`RecordDetailViewModelTest`/`SettingsComponentsTest` | 零改动跑绿 |

## 5. 验收标准(Definition of Done)

- [x] R1:详情页无任何折叠/收起控件与预览截断;任意区块长按任一块 → 剪贴板获得整段 hex/text;1MB×3 字段 + 1MB 文本记录滚动不卡顿(插桩契约全绿);
- [x] R2:列表无徽章、无 `kindSymbol`;内容左缘全列表对齐;
- [x] R3:卡片内容自适应高度,无上下留白;空串字段不渲染;
- [x] P0:五处缺陷修复,新增行为有测试锚点;
- [x] 门禁:`testModernDebugUnitTest`/`testLegacyDebugUnitTest` 全绿、`lintModernDebug`/`lintLegacyDebug` 0 error、`compileModernDebugAndroidTestKotlin`/`compileLegacyDebugAndroidTestKotlin` 通过;
- [x] 本任务书落盘,执行记录逐项勾选,备注与代码一致。
- [x] 实机验证(模拟器 14 API34,双变体 APK adb 安装):R1 详情页 dump 无任何展开/收起节点;长按分块整段复制经 Snackbar 确认——密钥 64 字、输入数据 6000 字(2 块)、输出数据 9000 字(3 块);R2 列表无徽章节点,label/包名左对齐;R3 稀疏记录(MD5 无 direction/无 key)行与详情均无空节/留白;截图 docs/screenshots_manual/v13_01_home.png、v13_02_records_list.png、v13_03_detail_big.png、v13_04_detail_bottom.png、v13_05_detail_sparse.png;验证数据经 run-as sqlite3 种子化(kind 须为 CryptoKind 类别枚举,算法名属 algorithm 列)。

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| 去折叠后大区块组合成本上升 | 保留 ChunkedText 惰性分块 + LazyColumn 虚拟化;插桩契约断言末块滚动前不在树 |
| 剪贴板写入 2MB 文本兼容性 | Android 剪贴板无文本长度硬限;Snackbar 反馈字符数让用户感知大小 |
| SelectionContainer 移除后失去局部选择 | 用户明确要求整段复制优先;MetadataBlock/SectionText/堆栈保留原生选择 |
| pointerInput 长按与列表滚动冲突 | detectTapGestures 内建 tap/drag 判定,滚动不受影响 |
| 删字符串/常量波及既有测试 | grep 全量确认引用后删除;`section_expand/collapse` 因 Diagnostics 复用保留 |
| 插桩剪贴板断言环境差异 | 用 instrumentation context 读 `ClipboardManager.primaryClip`,纯 API 无 UI 依赖 |

## 7. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 任务书落盘 | ✅ | 本文件落盘,含全量审计 1.1–1.5 + DR 1–8 + 验收清单 |
| T2 | i18n 键 | ✅ | values + values-zh 增 section_copy / record_copy_done / record_copied;section_expand/collapse 保留(Diagnostics 复用) |
| T3 | 详情页重构 | ✅ | 去折叠状态机/预览/按钮/动画(FoldMetrics·needsFold·rememberFoldMetrics·textPreviewOf·SectionEnter/Exit 全删);恒展开惰性分块;长按整段复制(copyFull)+ footer 复制按钮;SelectionContainer 仅留元数据/别名/参数/堆栈 |
| T4 | 删除 LineEstimate | ✅ | LineEstimate.kt + LineEstimateTest.kt 删除(全局零引用已核实);ChunkedText 保留 |
| T5 | 列表行改造 | ✅ | 徽章/KindBadge/kindSymbol/68dp minHeight/CenterVertically 删除,内容顶对齐自适应高;长按复制摘要接线(copySummary 纯函数 + valuableDataPair 单一事实源,操作/方向标签资源化 operationLabelRes/directionLabelRes) |
| T6 | AppsViewModel | ✅ | refresh()/toggle() try/finally 复位刷新与 pending 态;removeScope() 补 ScopeRequested 分支(删 else 兜底) |
| T7 | AppsScreen | ✅ | countText 改 LocalConfiguration 判定(修跟随系统时误判英文);记录数徽标间距条件化;combinedClickable→clickable |
| T8 | ModevNav | ✅ | RECORD_DETAIL 参数缺失 → popWhenReady 兜底(与 APP_RECORDS 同款),消除空屏 + 返回死循环 |
| T9 | 搜索框语义 | ✅ | 折叠搜索条主体补 semantics(role=Button + onClick 展开) |
| T10 | Settings 无障碍 | ✅ | SettingsToggleItem→toggleable(Role.Switch)+ Switch clearAndSetSemantics;语言/外观/壁纸单选行→selectable(Role.RadioButton);色块 contentDescription=十六进制值 |
| T11 | 测试更新 | ✅ | ByteSectionTest 重写 8 项(去折叠契约 + 整段复制断言);RecordRowLayoutTest 徽章测试→无徽章断言 + copySummary 拼接契约;KindColorTokensTest 徽章→方向胶囊 |
| T12 | 质量门禁 | ✅ | 双变体 compile + unit test + lint 全绿;插桩 40 项 39 通过 1 跳过(实机 14 版);lint 仅遗留 Pre-existing 警告(RecordRow ModifierParameter 为历史签名,PluralsCandidate 与 bytes_count 同规) |
| T13 | 验收收尾 | ✅ | 验收标准逐项核对;P1/P2 后续迭代项按 §1.5 留档;工作树变更未提交(待用户确认);实机验证完成(R1/R2/R3 证据见 §5 末条;环境遗留:模拟器 LSPosed Vector 框架已启用但作用域 API 未连通,详情页上下条按钮初始 disabled 为加载竞态,加载后状态正确) |
