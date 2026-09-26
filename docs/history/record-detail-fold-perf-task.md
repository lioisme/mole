# Modev 任务书 V11:记录详情折叠语义 + 大数据性能 + 文案收敛 + 设置行对齐

**文档版本**: v1.1 | **创建日期**: 2026-08-17 | **状态**: 执行中
**适用范围**: `ui/logs/RecordDetailScreen.kt` + `ui/components/SettingsComponents.kt` + `ui/settings/{CaptureSettingsScreen,SettingsScreen}.kt` + `core/util` + i18n 资源(en/zh)
**质量目标**: 代码质量、架构设计达到苹果/谷歌大厂验收标准(状态机单一事实源、主线程零大分配、虚拟化、渐进披露、可测试性、门禁全绿)

---

## 0. 需求条款(用户口径)

| # | 需求 |
|---|------|
| R1 | 详情页字节区块:**被折叠时显示「展开」按钮,未折叠时显示「收起」按钮**;仅**超出行数**的区块默认收起,否则正常全量展示 |
| R2 | 优化详情页性能:数据量过大时滑动不卡顿、不掉帧、不闪退崩溃 |
| R3 | 界面无废话文案(例:「全局设置,对所有已作用域应用生效」) |
| R4 | 设置界面 Key/Value 布局:Value 过长不得挤压 Key 导致错位(例:服务与诊断「框架」行) |

## 1. 全量审计(现状 + 根因)

### 1.1 R1 折叠/展开语义 — 现状与缺口

- 按钮文案逻辑**已正确**:`RecordDetailScreen.kt:483-485` `if (expanded) 收起 else 展开`,即折叠→「展开」、展开→「收起」,满足 R1 前半句。
- **缺口 1(核心)**:展开态初始恒为空列表(`RecordDetailScreen.kt:254` `expandedSections` 初始 `emptyList()`)——所有区块(含 16 字节 IV)一律默认折叠,与数据大小无关,违反「只有超出行数才默认收起」。
- **缺口 2**:折叠预览上限 = `BytesView.DEFAULT_PREVIEW_CHARS = 4096` 字符(≈96 行,`BytesView.kt:55`),远大于「行数」直觉阈值,折叠预览与折叠判定阈值脱钩。
- **缺口 3(次要)**:`rememberSaveable` 无 key,上/下条导航后旧记录的折叠态残留跨记录复用。
- 完整数据双态已具备:`BytesView.preview(maxChars)` 纯函数(`BytesView.kt:17-21`)可直接复用为行数阈值截断。

### 1.2 R2 性能 — 根因审计(全链路)

| # | 根因 | 位置 | 危害 |
|---|------|------|------|
| A | 展开态 `view.hex.chunked(EXPAND_CHUNK_CHARS)` 在主线程**全量物化 chunk 列表**;`LoadedContent` 每次重组(切换任一区块折叠态/record 变化)对所有展开区块**重复执行** | `RecordDetailScreen.kt:521` | 2MB hex→512 子串≈4MB 主线程分配/次;3 字段展开=12MB/次重组 → 切换时卡顿 |
| B | `view.text`(可打印大数据,Base64 等,上限 1MB 字符)在折叠/展开两态均渲染为**单个巨型 Text**(`RecordDetailScreen.kt:461-464`),且被 `SelectionContainer` 包裹(`:459`) | `RecordDetailScreen.kt:459-464` | 滚动进入该区块单帧排版百万级字形 → 长帧/ANR(v3.2 仅修 hex 展开路径,text 遗留隐患) |

**已具备的保障(维持不动)**:LazyColumn 逐区块虚拟化(`:258`)、hex 4K 惰性分块仅可视块组合(`:520-530`)、导出后台构建(`:129-138`)、邻接 id 投影(`LogRepository.kt:201-206`)、列表 SQL 侧预览投影(`CryptoRecordDao.kt:77-109`)。

**内存账目**(上限 `maxBytesPerField=1MB`):单字段 hex 2,097,152 字符 ≈ 4MB(UTF-16),文本另计 ≤2MB;记录最多 7 字节字段 ≈ 42MB——详情页全量加载是取证语义(D2 既定决策,不改)。

### 1.3 R3 废话文案 — 全量扫描结论

| # | 文案 | 位置 | 判定 |
|---|------|------|------|
| 1 | 全局设置,对所有已作用域应用生效(capture_global_hint) | `CaptureSettingsScreen.kt:85-92`、strings en:161/zh:162 | **删除**(用户点名) |
| 2 | 密钥材料不可导出(硬件/KeyStore)(key_not_exportable) | `RecordDetailScreen.kt:487-493`、en:146/zh:147 | **整条删除**(用户确认):在 IV/盐/附加数据/输入/输出等**非密钥区块**下也错误显示,语义错误且冗余——导出性已由元数据行「可导出 ✓」(`:404-406`)与密钥别名区块标题承载 |
| 3 | 三个二级入口副标题(capture_global_subtitle/data_settings_subtitle/diagnostics_settings_subtitle) | `SettingsScreen.kt:185/197/203`、strings en:160,163,165/zh:161,164,165 | **全部删除**(用户确认),入口行仅保留标题+chevron |
| — | 保留项(审计通过) | Snackbar 瞬态、错误态、placeholder、contentDescription、危险操作确认、`capture_*_hint`(对话框功能性输入提示)、`bytes_truncated`(状态标记) | 维持 |

### 1.4 R4 Key/Value 错位 — 根因

`SettingsInfoItem`(`SettingsComponents.kt:142-168`):value `Text` **无 weight**。Compose `Row` 测量协议对无 weight 子项授予**无限最大宽度**,长 Value(如「Vector (libxposed 102) + 版本号」)按内容全宽参与布局 → 标题(weight 1f)仅剩残宽,中文 Key 被逐字折行(「框/架」竖排),行内视觉错位。`DiagnosticsScreen.kt:92-94`「框架」行即复现点。
同文件其余组件(`SettingsItem`/`SettingsToggleItem`/`SettingsStepperItem`/`OptionRowItem`)标题均在 `Column(weight 1f)` 内,**仅 `SettingsInfoItem` 存在此缺陷**。

## 2. 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 行数阈值 | `COLLAPSED_MAX_LINES = 8`;每行字符数运行时估算:`charsPerLine = (屏宽dp − 32dp 容器内边距) / (字号dp × 字距系数)`,mono 0.6em / 常规 0.5em;估算为纯函数可单测,兜底下限 16 字符 |
| D2 | 折叠状态机 | 单列表 `userToggledFold`(rememberSaveable,key=`record.id`,导航切记录自动重置);`effectiveFolded(key) = needsFold(key) XOR (key ∈ userToggledFold)`——大数据默认折叠(点「展开」→ 反转为展开),小数据默认展开(点「收起」→ 反转为折叠),双向切换互为逆操作 |
| D3 | 折叠预览上限 | 折叠预览字符数 = 阈值行数 × charsPerLine(替代 4096);`BytesView.preview(maxChars)` 纯函数复用,`DEFAULT_PREVIEW_CHARS` 保留为默认回退 |
| D4 | chunk 惰性化 | 新增 `ChunkedText(text, chunkSize)`:size O(1) 计算、`get(i)` 惰性 substring;`items(count = chunks.size)` 按需取块——**零主线程全量物化、零重复分配**;text 分块复用同一机制 |
| D5 | 巨型 text 治理 | 折叠态 text 截断至阈值行数;展开态按 4K 字符惰性分块(与 hex 同规);`SelectionContainer` 仅包裹小内容(预览/短全量) |
| D6 | 文案收敛 | 删除 5 个字符串键(capture_global_hint / key_not_exportable / 三入口副标题),en/zh 同步,键对 diff=0 |
| D7 | SettingsInfoItem | value Text 增加 `Modifier.weight(1f, fill = false)`(测量上限 50% 行宽,超出走现有 maxLines=2 + Ellipsis);title 维持 weight(1f) 不受挤压 |
| D8 | 数据库/模型 | **零改动**(纯 UI 层;`CryptoRecord`/`BytesView`/DAO/Repository/ViewModel 均不变) |

## 3. 改动清单(执行顺序)

### T1 `core/util/` 新增 `ChunkedText.kt`(纯 Kotlin,可 JVM 单测)
- `class ChunkedText(text: String, chunkSize: Int)`:`val size`(=ceil,len/chunkSize,空串=0)、`operator fun get(i: Int): String`(substring 惰性,越界 index 直接取尾块);
- 常量 `EXPAND_CHUNK_CHARS = 4096` 自 `RecordDetailScreen.kt:497` 迁入(单一事实源,`RecordDetailScreen` 引用)。

### T2 `ui/logs/RecordDetailScreen.kt` — 折叠语义 + 性能(核心)
- `LoadedContent`:状态改 `rememberSaveable(record.id) { mutableStateOf<List<String>>(emptyList()) }`(userToggledFold,切记录重置——修缺口 3);
- 新增行数估算:`charsPerLine(typography, density, containerWidth, monospace)` + `needsFold(hexLength) = hexLength > charsPerLine × COLLAPSED_MAX_LINES`(remember 缓存);
- `byteSectionItems` 重构:header(标题 + 折叠预览[text 截断 + hex 截断,均 ≤ 阈值行数]/短全量 + footer 字节数 + 「展开/收起」按钮)+ 展开态 chunk items(**hex chunks + text chunks** 均为 `ChunkedText` 惰性取块,`:520-530` 的 `chunked()` 物化删除——修根因 A);
- `ByteSection` 移除 `exportableNote` 参数与 `key_not_exportable` 文案(修 1.3-2),`view.text` 不再直渲染巨型单 Text(修根因 B),`SelectionContainer` 仅包裹小内容;
- 按钮文案:`effectiveFolded ? section_expand : section_collapse`(满足 R1 前半句,按钮恒显)。

### T3 `ui/components/SettingsComponents.kt`
- `SettingsInfoItem`(D7):value Text 增加 `Modifier.weight(1f, fill = false)`,注释更新(修 R4)。

### T4 i18n `values/strings.xml` + `values-zh/strings.xml`
- 删除 5 键:`capture_global_hint`、`key_not_exportable`、`capture_global_subtitle`、`data_settings_subtitle`、`diagnostics_settings_subtitle`;en/zh 同步删,键对 diff=0。

### T5 `ui/settings/CaptureSettingsScreen.kt`
- 删除顶部 `item(key = "global-hint")` 及其 Text(`:85-92`)。

### T6 `ui/settings/SettingsScreen.kt`
- 三处 `SettingsItem` 的 subtitle 置空(不传参):`:185`(capabilitySubtitleRes)、`:197`、`:203`;删除 `capabilitySubtitleRes` 函数(死代码)。

## 4. 测试计划

| 层 | 用例 | 断言要点 |
|----|------|----------|
| 单测(新) | `ChunkedTextTest` | size/get 边界:空串、整倍、余数、越界兜底;零物化(不预分配,由实现保证) |
| 单测(新) | 行数阈值纯函数 | ==阈值不折叠、超 1 字符折叠、0/1 行兜底、min 下限 |
| 单测 | `BytesViewTest` | 零改动(`preview(maxChars)` 纯函数无变化) |
| 插桩 | `ByteSectionTest` 重写契约 | ①小数据默认展开+「收起」按钮+全量可见;②大数据默认折叠+「展开」按钮+预览截断至阈值行;③点击双向切换文案;④text 折叠截断;⑤300K hex 惰性滚动(保留既有用例);⑥新增 text 大块惰性滚动(末块滚动前不在树) |
| 插桩 | `SettingsComponentsTest` 新增 | `infoItem_longValue_doesNotSqueezeTitle`:长 Value + 短 Key,Key 单行完整显示(高度断言)、Value 可见省略 |
| 插桩 | `FourScreensNavTest` | grep 已确认无 global-hint 断言,零改动(回归跑绿即可) |
| 回归 | `RecordDetailViewModelTest` | 零改动(D8) |

## 5. 验收标准(Definition of Done)

- [x] R1:小数据区块默认全量展示、按钮「收起」;超行数区块默认折叠(≤8 行预览)、按钮「展开」;点击双向切换;上/下条导航折叠态重置;
- [x] R2:1MB×3 hex + 1MB 可打印文本记录,全区块展开、顶底滚动、反复折叠切换,插桩契约全绿;代码审计确认主线程零 chunk 列表物化、零巨型单 Text;
- [x] R3:5 个字符串键删除、零代码残留引用(grep 全量确认),en/zh 键对 diff=0;
- [x] R4:「框架」行 Key 单行不折行、Value 省略,与其余行左锚点对齐;
- [x] 门禁:`testModernDebugUnitTest`/`testLegacyDebugUnitTest` 全绿、`connectedModernDebugAndroidTest` 全绿、`lintModernDebug` 零新增、`assembleModernRelease` 通过;
- [x] 本任务书落盘 `docs/record-detail-fold-perf-task.md`,执行记录逐项勾选。

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| 行数估算与真机字号/屏宽偏差 | 字距系数取保守值(mono 0.6em/常规 0.5em)+ 16 字符下限兜底;预览永不超全量 |
| 插桩断言受模拟器字号影响 | 断言用节点存在/文案语义,不用像素宽度 |
| 删字符串波及既有测试 | grep 全量确认零引用后删除;已确认 FourScreensNavTest 无引用 |
| 折叠语义变化影响既有习惯 | 状态机为单列表 XOR,逐区块独立,切换记录重置,行为可预期 |
| text 分块破坏跨块选择 | 与既有 hex 分块一致的限制;完整数据出口=导出 JSON(既有唯一出口) |

## 7. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | ChunkedText 惰性分块 | [x] | 新增 `core/util/ChunkedText.kt`(`size`=ceil、`get(i)` 惰性 substring、越界返回 `""`、`DEFAULT_CHUNK_CHARS=4096`)+ `core/util/LineEstimate.kt`(`charsPerLine`/`needsFold`/`collapsedPreviewChars`,阈值 8 行、mono 0.6em/常规 0.5em、16 字符下限兜底);配套单测 `ChunkedTextTest`(5 例)+ `LineEstimateTest`(4 例)全绿。 |
| T2 | 详情页折叠语义 + 性能重构 | [x] | `RecordDetailScreen.kt` 重构:状态机 `userToggledFold: List<String>`(rememberSaveable key=record.id,切记录重置)+ `effectiveFolded = needsFold XOR toggle`;`ByteSectionHeader` 按钮恒显(折叠=「展开」/展开=「收起」);`byteSectionItems` 新签名(sectionKey/titleRes/view/metrics/folded/onToggleFolded);展开态 hex 与 text 双路 `ChunkedText` 惰性组合,折叠预览与全文区 `SelectionContainer`(chunks 不包裹);`rememberFoldMetrics` 用 `screenWidthDp-32`(LazyColumn padding 16×2)估行数,sp≈dp;`textPreviewOf` 边界兜底;文件头注释更新。 |
| T3 | SettingsInfoItem 宽度约束 | [x] | `SettingsComponents.kt` value 改 `Modifier.weight(1f, fill=false)`(有界测量 ≤50% 行宽省略),标题恒单行;新增插桩用例 `infoItem_longValue_doesNotSqueezeTitle`(标题 `assertHeightIsEqualTo(24.dp)` + 长值完整语义显示)全绿。 |
| T4 | i18n 5 键删除 | [x] | `values/strings.xml` + `values-zh/strings.xml` 同步删除 `capture_global_hint`/`key_not_exportable`/`capture_global_subtitle`/`data_settings_subtitle`/`diagnostics_settings_subtitle`;grep 全源码零残留引用(仅 `RecordDetailScreen.kt:456` 注释提及,已属无害);en/zh 键对仍对等。 |
| T5 | CaptureSettingsScreen global-hint 删除 | [x] | 移除「全局设置,对所有已作用域应用生效」提示 item;`FourScreensNavTest` 已确认无该文案断言,回归全绿。 |
| T6 | SettingsScreen 入口副标题移除 | [x] | 移除「捕获设置」/「数据与存储」/「服务与诊断」三入口副标题;删除 `capabilitySubtitleRes` 函数及相应 import。 |
| T7 | 测试补全 | [x] | 单测:`ChunkedTextTest` 5 例(分块/惰性/越界/跨界合并/多块),`LineEstimateTest` 4 例(估算/阈值/预览/兜底);插桩重写 `ByteSectionTest` 7 例——小数据默认展开+「收起」、大数据默认折叠+阈值预览+「展开」、点击双向切换、text 折叠截断、300K hex 惰性滚动(末块不在树→scrollToNode 后可见)、300K text 惰性滚动、展开态全量 hex 永非单节点;新增 `infoItem_longValue_doesNotSqueezeTitle`。 |
| T8 | 质量门禁 | [x] | `compileModernDebugKotlin` 通过;`testModernDebugUnitTest` 全绿;`testLegacyDebugUnitTest` 全绿;`compileModernDebugAndroidTestKotlin` 通过;`connectedModernDebugAndroidTest` 30 例(29 通过 + 1 既有 assumeTrue 空数据跳过)全绿;`lintModernDebug` 0 error;`assembleModernRelease` 通过。 |
