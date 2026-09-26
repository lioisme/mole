# Modev V16 质量整改任务文档

- 版本:V16(审计基线:V15 代码库)
- 日期:2026-08-18
- 范围:六项质量需求(常驻参数 / 大数据健壮性 / 整段选择 / 应用列表加载 / 语言切换 / 官方组件合规)
- 状态图例:`[ ]` 待办 · `[~]` 进行中 · `[x]` 已完成

---

## 0. 审计结论摘要

代码库成熟度已较高(双层渲染 / 三态契约 / 语言热切换 / 容量护栏 / 测试金字塔齐备)。本任务为**定点整改 + 合规收尾**,不重写。

关键缺陷(审计定位):

| 编号 | 需求 | 根因定位 | 严重度 |
|------|------|---------|--------|
| R1 | 常驻参数空白 | hook 写入 `algorithm = ""`;展示层 `ifBlank` 缺失,列表行/详情标题/摘要出现空白 | 高 |
| R2 | 空/大数据不卡不崩 | keyAlias/params/stack 走无护栏单 Text;导出先构建后判大 | 中 |
| R3 | 整段选择 / 一键复制 | Tier B 跨块无法一次选中(平台限制);复制仅 hex,缺文本/全部 | 低(有兜底) |
| R4 | 应用列表加载慢 + 无 progress | 每应用 3 次 per-package binder 调用;仓库无 loading 态,首帧误显空态 | 高 |
| R5 | 语言切换不完整 | `LocalConfiguration` 不随热切换更新;全屏搜索 Dialog 窗口上下文不换 locale | 高 |
| R6 | 官方组件 / 标准实践 | `getInstalledApplications(int)` 等弃用 API;lint 门禁缺失 | 中 |

---

## 1. 任务清单与状态跟踪

| 阶段 | 任务 | 状态 | 完成备注 |
|------|------|------|---------|
| PH0 | 基线回归(全量单测) | [x] | 2026-08-18:`testModernDebugUnitTest` 全绿(BUILD SUCCESSFUL,基线无失败) |
| PH1 | R1 常驻参数空白治理(displayAlgorithm 兜底 + 标题/摘要 + 测试) | [x] | 2026-08-18:RecordRow 新增 displayAlgorithm(组合/非组合双版)并接入 headline、copySummary 首行、详情页标题;新增 RecordDisplayTest(Robolectric)与 RecordRowLayoutTest 空算法用例;全量单测绿 |
| PH2 | R4 应用列表加载(loading 态 + 惰性字段 + 接口化 + progress UI + 测试) | [x] | 2026-08-18:AppCatalogSource 接口化;AppInfo 移除 enabled/launchable,新增 resolveLaunchable(单次 binder + 缓存 + 互斥锁);loading StateFlow + 刷新串行化;AppsViewModel UiState.loading + 嵌套 combine 绕开 5 流上限;AppRecordsScreen 启动按钮按解析态渲染;AppsScreen 首帧 progress;AppCatalogRepositoryTest 重写 + 新增 AppsViewModelTest(fake 源);FourScreensNavTest 启动按钮有界轮询;全量单测绿 |
| PH3 | R5 语言切换修复(effectiveLocale 单一来源 + 搜索 Dialog locale 包装 + 测试) | [x] | 2026-08-18:新增 ui/common/EffectiveLocale.kt(Context.effectiveLocale + rememberEffectiveLocale);AppsScreen.countText 与 DiagnosticsScreen.stat 改经 effectiveLocale(弃 LocalConfiguration);展开搜索 Dialog 分支 CompositionLocalProvider(LocalContext provides LocaleManager.wrap(context)) 包裹;新增 EffectiveLocaleTest(Robolectric),LanguageHotSwitchTest 扩展搜索 Dialog 双语文断言;全量单测绿 + androidTest 编译通过 |
| PH4 | R2/R3 详情健壮性(文本区块分档 + params/stack 护栏 + Tier B 复制菜单 + 导出预检 + 测试) | [x] | 2026-08-18:新增 RecordDetailTextFormat.kt(formatParamsText 256字符/200条 + estimateJsonChars ×2 预检 + STACK_MAX_FRAMES=300);params/keyAlias 改 textSectionItems 分档渲染(折叠预览 + Tier A 整段 / Tier B 分块 + 复制);ByteSectionHeader 复制菜单化(文本/Hex/文本+Hex,缺失与超护栏项隐藏);导出构建前预检;新增 6 个双语字符串;RecordDetailTextFormatTest(JUnit)+ ByteSectionTest 复制菜单用例;全量单测绿 |
| PH5 | R6 合规收尾(弃用 API 现代化 + lint 门禁 + AGENTS.md 更新 + 全量回归) | [x] | 2026-08-18:getInstalledApplications 走新 PackageManagerCompat.kt 统一入口(API 33+ ApplicationInfoFlags 嵌套类重载,旧系统弃用分支);查明 getLaunchIntentForPackage 全 API 无 flags 重载且从未弃用,各调用点保持原 API;lintModernRelease 门禁:38 warning / 0 error,零新增(PluralsCandidate 由 6→4:params_omitted/stack_more 改 plurals 消除新增 2 项,getQuantityString 渲染);AGENTS.md 升级 V16(新增约束 5/6 + lint 门禁命令);全量单测 + lint + androidTest 编译绿 |

---

## 2. 各任务详情

### PH1 — R1 常驻参数空白治理

**根因**:`hook/hooks/*.kt` 11 个 Hooker 均 `algorithm = invocation.getArg(0) as? String ?: ""` → 空串可落库;展示层未做空值兜底:
- `RecordRow.kt:93` headline 直接渲染 `record.algorithm` → 空白
- `RecordDetailScreen.kt:196` 标题 `algorithm ?: record_title` → 空串穿透
- `RecordRow.kt:184` `copySummary` 首行空行

**方案(展示层兜底,不改取证语义)**:
- `RecordRow.kt` 新增 `displayAlgorithm()`(组合)/ `displayAlgorithm(context)`(非组合):`algorithm.ifBlank { 类别名 }`,类别名经 `kindLabelRes(kind)` 恒有。
- 列表行 headline、详情页标题、摘要首行三处改用。

**验收**:空 `algorithm` 记录 → 列表行/详情标题显示类别名、摘要首行非空;正常记录行为不变。
**测试**:`RecordDisplayTest`(Robolectric,context 版)+ `RecordRowLayoutTest` 增补空串用例。

### PH2 — R4 应用列表加载优化 + Progress

**根因**:`AppCatalogRepository.refresh()` 对每应用执行 `getApplicationLabel` + `getApplicationEnabledSetting` + `getLaunchIntentForPackage`(3 次 binder 往返);无 loading 态,首帧空列表误显「未发现应用」。

**方案**:
1. 数据层减负:`AppInfo` 移除 `enabled`(flags=0 语义下恒真)与 `launchable`(最贵),新增 `suspend fun resolveLaunchable(packageName): Boolean`(内存缓存,按需一次 binder);`AppRecordsViewModel` 异步解析,头部「启动」按钮按解析结果显示。
2. 加载态:仓库新增 `loading: StateFlow<Boolean>`(refresh 期间 true,并发防抖);`UiState.loading`;首帧 `rows.isEmpty() && loading` 显示居中 `CircularProgressIndicator` + `loading` 文案。
3. 接口化:`AppCatalogSource`(apps / loading / refresh / resolveLaunchable),VM 依赖接口,测试注入 fake。

**验收**:300+ 应用冷启动列表可见 ≤1.2s(实机对比);加载期间必有 progress,无误空态;启动按钮按需解析不阻塞列表。
**测试**:`AppCatalogRepositoryTest` 更新(loading 时序 + resolveLaunchable);新增 `AppsViewModelTest`(fake 源,loading 传播);androidTest `AppsLoadingTest`(fake 注入,progress → 列表)。

### PH3 — R5 语言切换不完整修复

**根因**(两个独立缺陷):
1. `LocalConfiguration.current` 源自 Activity 窗口配置,热切换只换 `LocalContext` → `AppsScreen.countText` / `DiagnosticsScreen.stat` 的数字格式不跟随应用语言,仅重启生效(显式选语言冷启动亦错)。
2. `ExpandedFullScreenSearchBar` 官方实现为独立 Dialog 窗口,内容用 Dialog WindowContext(Activity 配置)→ 展开搜索视图内全部文本不随热切换。

**方案**:
1. locale 单一来源:`ui/common/EffectiveLocale.kt` 新增 `Context.effectiveLocale()` 非组合函数 + `rememberEffectiveLocale()` 组合包装(读包装 Context 的 `resources.configuration`),替换两处 `LocalConfiguration` 取 locale。
2. 搜索 Dialog:`ExpandedFullScreenSearchBar` 分支内 `CompositionLocalProvider(LocalContext provides LocaleManager.wrap(context))` 包裹(标签缓存命中,零开销)。
3. 全量迁移 `FullScreenSearchBar`(1.4.0 非 Dialog 形态)列为 backlog:动效行为变化需实机走查,本版本不做。

**验收**:zh 系统 + 显式 en → 计数徽标/诊断统计即时 "1.2K" 形态;展开搜索视图文本即时切换;零 Activity 重建契约保持。
**测试**:`EffectiveLocaleTest`(Robolectric:wrap 后 resources 配置 locale 正确);`LanguageHotSwitchTest` 扩展(切换后展开搜索,断言「返回」cd 为英文——该节点仅存在于 Dialog 内)。

### PH4 — R2/R3 详情页健壮性

**方案**:
1. 文本区块分档(与字节区块同规):`textSectionItems`(keyAlias / params)→ 头(含复制按钮)+ ≤4096 全量 / >4096 折叠预览(ReadMoreText)+ 展开按 `sectionRenderMode` 走 Tier A 整段选择 / Tier B 分块 + 收起按钮。复制走既有 `copyFull` 护栏(4M)。
2. params 护栏:`formatParamsText` 纯函数——单键值 ≤256 字符、总条目 ≤200,超限由 UI 追加本地化省略标记(`params_omitted`);keyAlias 由分档保证渲染安全,不截断数据。
3. stack 护栏:渲染取前 300 帧 + 本地化省略标记(`stack_more`)。
4. R3 复制增强:字节区块复制按钮改为菜单「复制文本 / 复制 Hex / 复制全部」(文本缺失时隐藏对应项,不留空白)。
5. 导出预检:`estimateJsonChars` 纯函数(字符估算 ×2 保守倍率),构建前拒绝 >32M,免浪费构建。

**验收**:4MB 参数 / 1MB keyAlias / 500 帧 stack → 可开、滚动不卡、无 ANR;Tier B 可一键复制 text/hex/全部;>4M 拒写、>32M 拒导出并提示。
**测试**:`RecordDetailTextFormatTest`(formatParamsText / estimateJsonChars 纯函数);`SectionRenderModeTest` 保持;`ByteSectionTest` 扩展(复制菜单三项行为)。

### PH5 — R6 官方组件 / 标准实践合规

**方案**:
1. 弃用 API 现代化:`getInstalledApplications(int)` → 新增 `core/util/PackageManagerCompat.kt` 统一入口(API 33+ `PackageManager.ApplicationInfoFlags` 重载,旧系统弃用分支);`getLaunchIntentForPackage` 经核实全 API 无 flags 重载且从未弃用,直接使用;`getApplicationEnabledSetting` 随 PH2 删除(无替代调用)。
2. lint 门禁:AGENTS.md 增补 `:app:lintModernRelease`,禁新增弃用 API(零新增策略,基线 38 warning / 0 error)。
3. AGENTS.md 沉淀 V16 强约束:算法展示名兜底、locale 单一来源、文本区块分档、弃用 API 零容忍、计数文本复数。
4. 第三方 ReadMoreText(社区组件,Apache-2.0):登记为受控第三方,官方替换列为 backlog。

**验收**:`lintModernRelease` 零新增 warning;`testModernDebugUnitTest` 全绿。

---

## 3. 测试矩阵(新增)

| 测试 | 层 | 覆盖 |
|------|----|------|
| `RecordDisplayTest` | Robolectric | displayAlgorithm 空/正常兜底 |
| `EffectiveLocaleTest` | Robolectric | wrap 上下文 locale 解析 |
| `AppsViewModelTest` | Robolectric | loading 传播 / 排序不受 loading 影响 |
| `AppsLoadingTest` | androidTest | 首帧 progress → 列表(未实现:由 `AppCatalogRepositoryTest` loading 时序单测 + `FourScreensNavTest` 有界轮询覆盖,PH2 备注) |
| `RecordDetailTextFormatTest` | JUnit | formatParamsText / estimateJsonChars |
| `ByteSectionTest` 扩展 | androidTest | 复制菜单三项 |
| `LanguageHotSwitchTest` 扩展 | androidTest | 搜索 Dialog 内文本热切换 |
| `AppCatalogRepositoryTest` 更新 | Robolectric | loading 时序 / resolveLaunchable |

---

## 4. 决策记录(ADR-V16)

| 编号 | 决策 | 理由 | 备选 |
|------|------|------|------|
| V16-1 | 空算法名走展示层兜底(不改注入数据) | 保持取证语义;展示层单一入口 | 注入侧 normalize(弃) |
| V16-2 | `launchable`/`enabled` 惰性化 + 缓存 | 消灭最贵的两类 per-app binder 调用 | 预解析缓存(弃,复杂度) |
| V16-3 | 搜索 Dialog 内 wrap LocalContext(方案 B) | 最小改动、零动效风险;`FullScreenSearchBar` 迁移需实机走查,列 backlog | 迁移 FullScreenSearchBar(backlog) |
| V16-4 | 参数护栏 256 字符/项、200 项;stack 300 帧 | 防御性上限,阈值可调,不改数据 | — |
| V16-5 | lint 采用「零新增」策略 | 不背负存量告警债务 | 全量清零(过重) |

---

## 5. 实施日志

<!-- 每完成一项任务,在此追加记录,格式:日期 | 任务 | 内容与结果 -->
