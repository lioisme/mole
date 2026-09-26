# V46 记录行版式 / 色板间距 / 令牌菜单化 — 任务文档(2026-09-22)

> **For agentic workers:** REQUIRED SUB-SKILL: 用 `superpowers:executing-plans`(或 `subagent-driven-development`)按任务逐条实施。本文所有步骤用 `- [ ]` 复选框跟踪,每个任务的最后一步是提交;测试代码与实现代码均为可直接落盘的完整内容,**不含 TBD/占位**。
>
> 验收基线:3 项用户需求逐条对账 + 6 项设计决策经用户拍板 + 全部门禁绿(单测 / lint 零新增 / androidTest 编译 / assemble)+ 几何门禁实测复现「修复前红 → 修复后绿」。**门禁绿 ≠ 完成**:每项须按「六、验收矩阵」做双面核对并留执行记录。

## 实施状态(2026-09-22 执行轮)

**T1–T7 全部完成:代码 + 契约 + JVM 门禁 + 设备插桩 + 真机目视;唯一未做 = 提交(待授权)。**

| 门禁 | 实测 |
|------|------|
| 全量 JVM 单测 | **856 tests / 0 failures / 0 errors** |
| `lintModernRelease` | **43 warning / 1 hint / 0 error**,V46 触碰文件零命中(净新增 0) |
| 主源 + androidTest + 单测源集编译 | BUILD SUCCESSFUL(androidTest 另以 `--rerun-tasks` 强制重编复核) |
| `assembleModernRelease` + `assembleModernDebug` | BUILD SUCCESSFUL |
| `node scripts/v42_generate.mjs` | **DONE**(en/zh/ja/ko 同表 695 键) |
| 设备插桩(雷电 emulator-5554 单序列) | 七类 31 例:**V46 目标 19 例全绿** + 回归 7 例绿;3 红 = 2 既有缺陷(V45 报告可追溯)+ 1 跨类状态耦合(详见「设备轮实测」) |
| 目视复核 | 设备真截屏三张已核(圆角卡片 / 色板等距 / 令牌菜单三项) |
| 提交 | **未执行** —— 工作区另有 364 个 V42–V45 在途文件且与本任务文件重叠,按文件粒度 `git add` 会误收;待用户裁定 |

设备轮另有一条**自我回归的捕获与修复**:容器化把 `record-row` 锚点留在外层 Box,而 M3
`ListItem` 自身才是收文本的合并语义节点 ⇒ 三个记录列表测试类的 `rowCount` 标准形态整体归零
(实测 `tag=1/text=1/both=0`);锚点下移回 `ListItem` 节点后复测 `both=1`,铁律写入 AGENTS #13。

附带修掉一个既有缺陷:**ja/ko 资源生成器自 V43 T4 起静默失效**(7 键被直写生成物而未回表),
本轮按现值回填并恢复;详见「九、执行记录」。逐项状态见各任务步骤复选框与该节。

**Goal:** 修复记录列表行的圆角缺失与「尾栏预留宽度导致正文被截断」两个版式缺陷,给主题色板网格补上行间距,并把访问令牌页的三个内联动作收敛为「更多」菜单(重命名 / 查看 / 删除)。

**Architecture:** 三层同时收口——① **版式层**:`RecordRow` 保留 M3 `ListItem` 作为内容布局引擎,但容器(圆角 + 背景 + 手势 + 波纹)上提到 `Box`,并**整体退役 `trailingContent` 槽**(时间/徽章移入 `headlineContent` 的身份行),这是正文通栏的唯一充分条件;② **契约层**:令牌改名走 `McpServiceConfig` 方法层新增(`setMcpTokenLabel`),UI 零直写 prefs;③ **组件层**:`AlertDialog` 唯一出口仍是 `ModevDialogs.kt`,新增只读值对话框 `ModevValueDialog` 与既有 `ModevConfirmDialog` 同居,`DialogConventionTest` 三门禁不破。

**Tech Stack:** Kotlin 2.2.20 / Compose Material3 1.4.0 / material-icons-extended 1.7.8 / JUnit4 + Robolectric 4.x(sdk 34)/ Compose UI 插桩测试 / Gradle `gradlew.bat` / Node i18n 生成器 `scripts/v42_generate.mjs`。

**Spec:** 本文即规格;上位契约 = `AGENTS.md` 强约束 #1(i18n 四语)#4(输入类弹层一律 sheet)/ #13(MD3 组件与容器色)/ #18(解耦契约)/ #20(记录行一行一参数)/ #25(视觉 token)/ #26(MCP 契约)/ #27(对话框统一);词汇表条目 = 「记录 record」「令牌管理 Token Management」「语言 Language」。

## Global Constraints

- **四语齐备**:新增/改名键必须 `values/` + `values-zh` + `values-ja` + `values-ko` 同表;ja/ko 由 `scripts/v42_table_2.mjs` 补条目后 `node scripts/v42_generate.mjs` 生成,**禁手改 `values-ja|ko`**;占位符形态四语一致(`StringResourceParityTest` 强制)。
- **MD3 优先 ladder**:布局/容器/菜单一律 Material3 原语(`ListItem`/`Card`/`DropdownMenu`/`AlertDialog`),禁自造组件、禁魔改上游;手势用 Foundation `combinedClickable`(既有唯一形态)。
- **容器色不变**:记录行/令牌卡 = `surfaceContainerLow`,sheet = `surfaceContainerHigh`,宿主页 Scaffold = `surface`(#13 雷电渲染契约:每个 `ListItem` 必须显式 `ListItemDefaults.colors(...)`)。
- **testTag 契约不破**:`record-row`、`timeline-header`、`token-row-${id}`、`token-add-sheet`、`token-auth-toggle`、`theme-color-sheet`、`color-swatch` 全部保留;`record-row` 的单击/长按/秒级时间三项行为契约不变。
- **圆角半径单一来源**:一律 `MaterialTheme.shapes.medium`(`AppShapes` 实测定档 = 12dp),禁散落 `RoundedCornerShape(<魔法数>.dp)`(`LeadingIcon` 既有 10dp 内部徽章不在本次范围)。
- **Appbar 排他**:令牌页 Appbar 无 SearchBar → actions 仅 `tokens-add` 一枚(现状不变,禁新增第二枚)。
- **MCP 工具面零感知**:令牌管理恒为 UI 专属(`McpToolParityTest.token_management_is_documented_exclusion` 扫描任何名字含 `token` 的工具即红),**本次不新增任何 MCP 工具**,工具面恒 45。
- **构建纪律**:验收轮 gradle 调用一律带 `--no-build-cache`(V45 实锤:缓存可交出跨产物 ABI 不一致的陈旧 dex);重任务串行,禁并行两个 gradle 任务;判 PASS 禁管道吞退出码。
- **禁止实现 = 删除代码**:退役形态(内联复制/显隐按钮、`trailingContent` 槽、`visibleIds` 状态、`token_show/hide` 键)必须整体删除,禁留注释残骸、禁 `@Deprecated` 壳、禁改名保号。

---

## 一、需求与决策对账

### 用户需求(逐字)

| # | 需求 | 本文落点 |
|---|------|----------|
| R1 | 记录列表每个 item 没有圆角;图标、文本、时间共享一个横向空间,导致底部文本右侧还有大量位置也被截断 | §二 A(根因)+ T1 |
| R2 | 主题色底部对话框中的配色之间没有间距 | §二 B + T2 |
| R3 | 访问令牌支持重命名;只需重命名/查看/删除且都放「更多」菜单;查看是对话框展示并支持复制 | §二 C + T3~T6 |

### 设计决策(用户拍板 2026-09-22)

| # | 决策点 | 结论 |
|---|--------|------|
| **D1** | 记录行目标版式 | **「时间右上,正文通栏」**(Gmail / Pixel Mail 形态):时间与方向徽章只在首行(身份行)右侧,参数行从图标右缘通栏到行右缘,长值用满剩余宽度后才省略 |
| **D2** | 令牌第三项措辞 | **「删除」**(用户原话)——现网「撤销/revoke」措辞整体退役,四语同步改名;作废语义(使用中的客户端立即被拒)由确认弹窗正文承载,不靠动词承载 |
| **D3** | 查看形态 | **「掩码行 + 查看弹明文」**:行内恒掩码,明文只经菜单「查看」的对话框上屏;行内显隐切换与独立复制按钮整体删除 |
| D4 | 令牌行体点击语义 | 行体**不再承载点击**(三动作恒由「更多」菜单驱动)。理由:D3 后行体点击若 = 打开查看对话框,则同一动作两处广告,与「都放在更多按钮里」冲突;而「看着可点却无动作」是更差的 affordance → 显式去掉 clickable |
| D5 | 键名 vs 文案 | `token_revoke` / `token_revoke_confirm_msg` **连键名一并改名**为 `token_delete*`。理由:键名值漂移(名 revoke 值「删除」)会误导后续读者,仓库纪律是词汇表与契约同步 |
| D6 | 删除确认的 `danger_title` | 保留(标题走通用危险确认语义,不只为令牌定制),仅正文文案改「删除」 |
| D7 | 色板间距实现 | 网格横纵步长**共用同一常量** `ColorSwatchGap`,几何门禁断言「横间距 == 纵间距」→ 单点不可漂移 |
| D8 | 记录行圆角如何与相邻行区分 | 行间垂直留白 4dp → **6dp**(相邻卡片间隙 = 2×6 = 12dp = 圆角半径;间隙 < 半径/2 时圆角读成波浪边) |

---

## 二、现状审计(根因定位)

### A. 记录行 —— 截断不是「文本太长」,是被尾栏预留宽度

`app/src/main/java/com/lioisme/modev/ui/components/RecordRow.kt:131-183` 用 M3 `ListItem` 承载:

```kotlin
ListItem(
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = MIN_ROW_HEIGHT)
        .semantics { role = Role.Button }.combinedClickable(...),
    leadingContent = { LeadingIcon(...) },
    headlineContent = { Text(presentation.headline, maxLines = 1, overflow = Ellipsis) },
    supportingContent = { Column { /* RowField 逐行 */ } },
    trailingContent = { Column { TimeText(...); badges.forEach { MicroBadge(it) } } },
)
```

M3 `ListItem` 的布局契约:`headlineContent` 与 `supportingContent` 同处**一个文本列**,该列的可用宽度 = 行宽 − leading 宽 − **trailing 宽** − 水平内边距。因此:

1. **截断根因 = 尾栏预留**:`trailingContent` 是 Column,宽度 = max(时间串宽, 徽章宽) ≈ 52dp,加内部 8dp 间距,文本列被永久削掉 ~60dp。尾栏只有 2 行内容、顶部对齐,**其下方的行区域是死空间**——这正是「底部文本右侧还有大量位置却被截断」的成因。`MiddleEllipsisText`(`ui/components/MiddleEllipsisText.kt:56+`)与所有 `FieldText` 分支都只在这份被削后的宽度里收敛(`maxLines = 1`),组件本身无过错。
2. **无圆角**:`containerColor = surfaceContainerLow` 直铺 `ListItem` 全宽全高,`modifier` 链上无 `clip`/`shape` → 视觉上是贴边色带,不是卡片。
3. **波纹不可见(附带缺陷)**:`combinedClickable` 位于 `ListItem` **外层**,而 `ListItem` 自身容器是不透明 `surfaceContainerLow` → indication 画在容器之下,按压零反馈。容器化后(背景在 clickable 之前、`ListItem` 转透明)自动修复。
4. **消费方单点**:`RecordRow(` 生产调用点只有 `ui/records/RecordTimelineList.kt:199`(垂直留白 4dp 在 :206)。其余匹配是 `CryptoRecordDao.kt:273 data class CryptoRecordRow`,同名不同物,不受波及。

### B. 主题色板 —— 纵向步长缺失

`ui/settings/SettingsScreen.kt:462-509` `ColorSheet` 的 20 色网格:

```kotlin
Column(Modifier.padding(horizontal = 24.dp)) {          // ← 无 verticalArrangement
    colorOptions.chunked(5).forEach { rowColors ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // ← 横向 12dp
```

色点为 `weight(1f) + aspectRatio(1f)`,在 360dp 宽屏上边长约 55dp;横向有 12dp 间隙、**纵向 0dp** → 四行圆点上下相切。根因不是「间隙太小」,而是 Column 根本没有 `verticalArrangement`。同一 sheet 的壁纸行/分隔线/「自定义强调色」标题行(`:436-456`)间距正常,不属本次范围。

### C. 访问令牌页 —— 动作全内联且缺重命名

`ui/settings/McpTokensScreen.kt:292-370` `TokenCard`:

- `trailingContent` = 三个 `TipIconButton`(复制 `token-copy-${id}` / 显隐 `Visibility*` / 撤销 `token-revoke-${id}`),`Spacer(12dp)` 硬插做间距分离——**无「更多」菜单,无重命名**;
- 行体 `Modifier.clickable(onClick = onToggleVisible)` + 页面级 `visibleIds: Set<String>` 状态(`:98`)承载显隐;
- 掩码由 `maskValue()`(`:401`,前 4 + 6 圆点 + 后 4)产出,`TokenMaskValueTest` 覆盖。

能力缺口的真实位置在**契约层**:`domain/server/ServerConfigService.kt:42-70` 的 `McpServiceConfig` 只有 `addMcpToken(label)` / `revokeMcpToken(id)`,**无改名方法**;`McpTokenEntry`(`core/model/McpTokenEntry.kt:16-21`)的 `label` 是 data class 的 `val`,现网创建后不可变 → 重命名必须先补方法层,不能在 UI 拼 prefs。

实现落点确认(无隐藏耦合):
- `data/repo/SettingsRepository.kt:400-424`:`addMcpToken`/`revokeMcpToken` 均经唯一出口 `persistMcpTokens(tokens)`(写本地键 `ConfigKeys.MCP_TOKENS` → 置 `_mcpTokens` → 回调 `mcpTokenSetUpdater`);令牌**不入远程镜像**、`mcp_tokens` 恒不进 MCP 工具输出与诊断快照。
- 改名只动 `label`,value 集合逐元素不变 → 校验集内容不变 → **不重启、不踢会话**(与「原地生效」契约天然一致)。
- 门禁面:`SingleWriteEntryPointTest` W2 允许写服务端可变面的文件集合 = `{ModevApp.kt, data/repo/SettingsRepository.kt}`(实测 `:66-77`),且 `requiredTokens` 仅带尾 `s` 被正则命中——裸属性赋值 `tokenAuthEnabled =` 不在扫描面,改名方法不触碰任何可变属性 → W2 零波及。

### D. 既有测试与门禁基线(实施前须知道的现状)

| 文件 | 断言形态 | 本次影响 |
|------|----------|----------|
| `androidTest/ui/components/RecordRowLayoutTest.kt` | 文本节点 + 「 · 」结构哨兵 + 单击/长按回调 + 摘要逐行 | 全绿保持;新增几何 + 结构门禁 |
| `androidTest/MonitorListScreensTest.kt:200` / `GlobalTypeRecordsTest.kt:144,181` | `hasTestTag("record-row") and hasText(...)`(后代匹配) | 容器化后 tag 仍在 Box 上 → 不破 |
| `androidTest/TimelineListTest.kt:186-195` | `timeline-header` 组头 | 不受影响 |
| `androidTest/V42StabilityProbeTest.kt:164` | `onAllNodesWithTag("color-swatch")[idx].performClick()` | 索引语义不变(仍行主序 20 个) |
| `androidTest/ColorSheetDeviceTest.kt` | 20 色计数 / 点选即写 / 壁纸行 / 关闭 | 全绿保持;新增间距门禁 |
| `test/ui/settings/McpTokensScreenStateTest.kt` | `AuthStatusLine` 三态(防 V1.1 闪退) | 不动 |
| `test/ui/settings/TokenMaskValueTest.kt` | `maskValue` 边界 | 不动(掩码仍用) |
| `test/data/repo/SettingsRepositoryMcpTokenTest.kt` | 派生/增删/迁移/持久化 | 新增改名用况 |
| `test/architecture/UiMcpCapabilityParityTest.kt` | R1-R6;`viewModelFunNames()` 扫 4 空格缩进 `fun`,`mutatorName` 前缀集合含 `set` | 新增 `McpTokensViewModel.setTokenLabel` **必须**登记进「令牌管理」能力行,否则 R5 红;R4 要求该符号真实存在 |
| `test/architecture/DialogConventionTest.kt` | `AlertDialog(` 仅 ModevDialogs.kt;该文件禁 `icon =`;`ModalBottomSheet(` 仅 ModevSheetScaffold.kt | 新对话框进 ModevDialogs.kt 且不发 icon → 破不了 |

---

## 三、设计规格

### 3.1 记录行版式(R1 / D1 / D8)

```
行容器  RoundedCornerShape = shapes.medium(12dp) · 背景 surfaceContainerLow · 垂直留白 6dp · 水平 16dp
┌──────────────────────────────────────────────────────────────┐
│ ┌──┐  WebCrypto/AES-GCM                        12:03:44      │  身份行(headlineContent)
│ │🔑│  ─────────────────────────── weight(1f) ──────────  [加密] │  时间 + 徽章右上,与首行同排
│ └──┤  类别  加密 · 摘要                                        │  参数行(supportingContent)
│ 36dp 操作  doFinal                                            │  ← 通栏到行右缘(仅减 16dp 内边距)
│       输出  cafebabe…(完整宽度后才省略)                       │
└──────────────────────────────────────────────────────────────┘
```

| 槽位 | 承载 | 宽度来源 |
|------|------|----------|
| `leadingContent` | `LeadingIcon` 36dp tonal 方块 | 不变 |
| `headlineContent` | 新 `IdentityLine` = `Row { Text(headline, weight(1f)); Column(End){ TimeText; MicroBadge* } }`,testTag `record-row-identity` | 文本列宽 − 尾栏(此时尾栏 = 时间/徽章) |
| `supportingContent` | `FieldLines`(既有 `RowField` 逐行,一行一参数 #20 D-1) | **文本列全宽**(通栏) |
| `trailingContent` | **恒不传**(整槽退役) | —— |

关键取舍:时间/徽章仍留在 `headlineContent` 内,**不是**移回 `trailingContent`。理由 = `ListItem` 的 `trailingContent` 与文本列同排,一旦使用即重新引入预留宽度;把它下钻进 `headlineContent` 的 Row,预留只作用于身份行,参数行不受影响。这正是 D1 的实现要点。

### 3.2 色板网格(R2 / D7)

| 属性 | 值 | 来源 |
|------|----|------|
| 网格步长(横 = 纵) | `ColorSwatchGap = 12.dp` | 新增文件级 private 常量,`Row.spacedBy` 与 `Column.spacedBy` 共用 |
| 列数 / 行数 | 5 × 4(20 色,`ThemeSeeds.OPTIONS.size`) | 不变 |
| 水平内边距 | 24dp(与标题/分隔线同列) | 不变 |
| 色点 | `weight(1f)` + `aspectRatio(1f)` + `CircleShape`;选中 3dp `selectedMarkColor` 环 + 20dp Check,未选中 1dp `surfaceVariant@swatchBorderAlpha` 环 | 不变(#25) |
| 触摸目标 | ≈55dp ≥ MD3 最小 48dp | 满足,故无需引入额外 padding |

### 3.3 令牌页信息架构(R3 / D2 / D3 / D4 / D5)

```
①认证状态卡(不变:开关 + 状态行)
②计数标题(不变:plurals mcp_tokens_count)
③令牌卡 × N
   headline = label(空 → token_unnamed 回落「令牌 {短码}」)
   supporting = 掩码值 · 创建日(恒掩码;长度恒定 → 永不截断)
   trailing   = ⋮ 更多 IconButton(testTag token-more-${id})
                 ├ 查看  token-menu-view   → ModevValueDialog(明文 + 复制)
                 ├ 重命名 token-menu-rename → ModevInputSheet(可留空 = 清名)
                 └ 删除  token-menu-delete  → ModevConfirmDialog(danger_title, error 色)
```

- 明文上屏路径**唯一** = 查看对话框(行体、菜单标签、 Snackbar、MCP 工具输出、诊断快照恒不见明文)。
- 复制动作**只**存在于查看对话框内(D3:动作只在菜单里;复制是「查看」的自然后续,不再是独立行内按钮)。
- 重命名允许提交空名 → 回落未命名形态,与「新建令牌 label 可选」对称。

---

## 四、实施任务

> **执行序 = T1 → T2 → T3 → T5 → T4 → T6 → T7**(T5 先于 T4:T4 的组件体引用 T5 新落的 `R.string.close`;T3 先于 T6:T6 消费 T3 的 `setTokenLabel`)。
> 提交节奏:每任务一次提交,消息用 `conventional commits` 小写祈使句。任一步测试不符预期即停(先诊断根因,禁改断言迁就实现)。

---

### Task 1: 记录行容器化 + 尾栏退役(R1) — ✅ 完成(JVM + 设备 + 目视)

**Files:**
- Modify: `app/src/main/java/com/lioisme/modev/ui/components/RecordRow.kt:93-184`(KDoc + `RecordRow` 函数体;新增 `IdentityLine` / `FieldLines` 私有件)
- Modify: `app/src/main/java/com/lioisme/modev/ui/records/RecordTimelineList.kt:198-211`(垂直留白 4dp → 6dp)
- Test: `app/src/androidTest/java/com/lioisme/modev/ui/components/RecordRowLayoutTest.kt`(新增 2 例)
- Test: `app/src/test/java/com/lioisme/modev/architecture/RecordRowContainerContractTest.kt`(新建,JVM 源码扫描)

**Interfaces:**
- Consumes:既有 `RowPresentation`(icon/accent/headline/fields/badges/timestamp)、`RowField`、`LeadingIcon`、`TimeText`、`MicroBadge`、`Tokens.badgeContainerAlpha` —— **十个 presentation 函数零改动**。
- Produces:`RecordRow(record, modifier, contextLabel, onClick, onLongClick)` 签名不变;新增 testTag `record-row-identity`(供 T1 测试与后续消费);`MIN_ROW_HEIGHT = 68.dp` 不变。

- [x] **Step 1: 先写「修复前必红」的几何门禁(复现缺陷)**

在 `RecordRowLayoutTest` 类内追加。**import 补 4 行**(该文件现无 `Modifier`/`hasTestTag`/`assertAny`/`dp`):

```kotlin
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
```

几何读取一律走仓库已验证形态(`fetchSemanticsNodes()` + `SemanticsNode.boundsInRoot` +
`SemanticsNode.density`,见 `FourScreensNavTest.kt:455-474`);**不用** `getBoundsInRoot()`
交互器扩展、**不用** `assertExists`/`rule.density`(AGENTS「已知工具链坑」列过该库缺件)。

```kotlin
    /**
     * V46 R1/D1 几何锁:正文列(`record-row-identity` = fillMaxWidth 的身份行)右缘
     * 必须逼近行容器右缘。
     * 修复前 = ListItem 尾栏预留时间/徽章宽度,文本列被削 ~60dp(用户报的
     * 「右侧还有大量位置也被截断」),且 `record-row-identity` 节点根本不存在;
     * 修复后仅差 ListItem 16dp 收尾内边距。
     * 量测取带 testTag 的自有节点(`Modifier.testTag` 生成的语义节点独立于
     * M3 1.4.0 ListItem 的槽位合并,见 AGENTS #13「勿对行内文本独立测距」)。
     */
    @Test
    fun textFieldColumn_reachesRowRightEdge_notShrunkByTrailingColumn() {
        rule.setContent {
            MaterialTheme {
                RecordRow(
                    record = row(record),
                    modifier = Modifier.testTag("row-under-test"),
                    onClick = {},
                    onLongClick = {},
                )
            }
        }
        val rowNode = rule.onAllNodes(hasTestTag("row-under-test")).fetchSemanticsNodes().first()
        val columnNode = rule.onAllNodes(hasTestTag("record-row-identity")).fetchSemanticsNodes().first()
        val gapDp = with(rowNode.density) { (rowNode.boundsInRoot.right - columnNode.boundsInRoot.right).toDp() }
        assertTrue(
            "正文列右缘距行容器右缘应 ≤32dp(实测 $gapDp;尾栏预留形态会到 ~76dp)",
            gapDp <= 32.dp,
        )
    }
```

> `row-under-test` 的 Box 由调用方 modifier 直接承载(无槽位合并),`record-row-identity` 是 `IdentityLine` 的 `Row`;两者右缘差 = ListItem 收尾内边距(16dp)+ leading 无关。容差 32dp 覆盖内边距与浮点,尾栏形态下差距 ~76dp 必越界。

```kotlin
    /** V46 R1/D1 结构锁:时间与徽章只在身份行内(尾栏槽恒空的正面证据)。 */
    @Test
    fun timeAndBadges_liveInsideIdentityLine_notTrailingColumn() {
        rule.setContent {
            MaterialTheme { RecordRow(record = row(record), onClick = {}, onLongClick = {}) }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        val expected = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(record.timestamp))
        val identity = rule.onNode(hasTestTag("record-row-identity"))
        identity.assertAny(hasText(expected))
        identity.assertAny(hasText(str(R.string.dir_encrypt)))
    }
```

> 第二条**修复前必炸**于 `record-row-identity` 不存在(fail-fast 的断言消息会写「Expected exactly 1 node with test tag ..., found 0」),这比像素容差更硬 —— 两条都要,一条防形态复活、一条防宽度回退。

- [~] **Step 2: 跑测试确认按预期红**

```powershell
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.ui.components.RecordRowLayoutTest --no-build-cache
```
预期:`textFieldColumn_...` **FAIL**(`record-row-identity` 节点不存在 → `fetchSemanticsNodes().first()` 抛 `NoSuchElementException`,红得干脆)、`timeAndBadges_...` **FAIL**(同因),类内既有 6 例 PASS。若两例意外绿 → 本文根因判断有误,停下来重新量测,不进 Step 3。

- [x] **Step 3: 写容器与槽位实现**

`RecordRow.kt` 顶部 import 补 **1 行**(其余全已就位:`Box`:12 / `background`:9 / `clip`:30 / `Row`:14 / `Alignment`:28 / `Arrangement`:11 / `Color`:31 / `ListItemDefaults`:22):

```kotlin
import androidx.compose.ui.platform.testTag
```

把 `RecordRow` 函数体(现 :117-183,自 `val haptics` 起至 `ListItem(...)` 收尾)整体替换为:

```kotlin
    val haptics = LocalHapticFeedback.current
    val presentation =
        when (record) {
            is CryptoRow -> cryptoPresentation(record)
            is SqlRow -> sqlPresentation(record)
            is BehaviorRow -> behaviorPresentation(record)
            is PrefsRow -> prefsPresentation(record)
            is WebViewRow -> webviewPresentation(record)
            is NetworkRow -> networkPresentation(record)
            is DeviceEnvRow -> deviceEnvPresentation(record)
            is UiInteractionRow -> uiInteractionPresentation(record)
            is SystemRow -> systemPresentation(record)
            is ProcessRow -> processPresentation(record)
        }
    // V46 R1/D1:容器上提到 Box —— 圆角 + 背景 + 手势 + 波纹同层(波纹可见性顺带修复:
    // 旧形态 clickable 在 ListItem 不透明容器之下,按压零反馈)。ListItem 转透明,
    // 仅作内容布局引擎(MD3 内边距/字阶/leading 槽契约不破)。
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .defaultMinSize(minHeight = MIN_ROW_HEIGHT)
                .semantics { role = Role.Button }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
            leadingContent = {
                LeadingIcon(icon = presentation.icon, tint = presentation.accent)
            },
            // trailingContent 恒不传(V46 R1 整槽退役):ListItem 的文本列宽
            // = 行宽 − leading − trailing,尾栏一旦存在就永久削掉正文可用宽度,
            // 于是「底部文本右侧明明还有位置却被截断」。时间/徽章下钻进身份行。
            headlineContent = {
                IdentityLine(
                    headline = presentation.headline,
                    timestamp = presentation.timestamp,
                    badges = presentation.badges,
                )
            },
            supportingContent = {
                FieldLines(contextLabel = contextLabel, fields = presentation.fields)
            },
        )
    }
```

- [x] **Step 4: 新增 `IdentityLine` / `FieldLines` 私有件**

紧跟 `RecordRow` 之后(替换原内联 `Column`/`Text` 逻辑;`FieldText`/`TimeText`/`MicroBadge`/`LeadingIcon` 保持原样)插入:

```kotlin
/**
 * 身份行(V46 R1/D1):headline 与「秒级时间 + 语义徽章」同排。
 * 时间右上的定位由本行承载而非 ListItem trailingContent —— 尾栏预留宽度只作用于
 * 这一行,参数行因此通栏。垂直对齐取 Top:时间/徽章列自上方起排,徽章落定在
 * headline 之下,与组头(按秒分组)在竖向上呼应。
 */
@Composable
private fun IdentityLine(
    headline: String,
    timestamp: Long,
    badges: List<RowBadge>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("record-row-identity"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = headline,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TimeText(timestamp = timestamp)
            badges.forEach { badge -> MicroBadge(badge) }
        }
    }
}

/** 参数行列表(V43 T4 一行一参数契约不变;V46 起自 RecordRow 内联抽出,恒通栏)。 */
@Composable
private fun FieldLines(contextLabel: String?, fields: List<RowField>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (contextLabel != null && contextLabel.isNotBlank()) {
            FieldText(RowField(labelRes = R.string.record_field_app, value = contextLabel))
        }
        fields.forEach { field -> FieldText(field) }
    }
}
```

- [x] **Step 5: 更新 `RecordRow` KDoc**

把 :93-106 的 KDoc 中「trailing:秒级时间 + 方向微徽章」两行改为下述,并保留 `record-row` testTag / presentation 单一数据出口 / 单击·长按·时间契约不变的表述,追加一行 V46 注记:

```
 * - **V46 R1/D1 版式**:容器 = Box(clip shapes.medium + background surfaceContainerLow +
 *   combinedClickable),ListItem 转透明仅承载内容布局;信息架构槽位重排为
 *   leading 图标 / headline = 身份行(headline + 右上时间与徽章,testTag
 *   `record-row-identity`)/ supporting = 参数行通栏 / **trailingContent 恒不传**;
 *   「图标+文本+时间共享横向空间致底部文本被提前截断」的根因(尾栏预留宽度)由此消除。
```

- [x] **Step 6: 列表侧垂直留白改 6dp(D8)**

`RecordTimelineList.kt` 的 `item(key = record.id, contentType = "record")` 内,`RecordRow` 的 modifier 改为:

```kotlin
                                modifier =
                                    Modifier
                                        .testTag("record-row")
                                        // V46 D8:行间 4dp → 6dp,相邻卡片间隙 12dp == 圆角半径
                                        // (间隙小于半径一半时圆角读成波浪边)。
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
```

- [x] **Step 7: 写结构门禁(防「尾栏复活」)**

新建 `app/src/test/java/com/lioisme/modev/architecture/RecordRowContainerContractTest.kt`(源码扫描形态与 `DialogConventionTest`/`AppbarSearchExclusivityTest` 一致):

```kotlin
package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记录行容器契约门禁(V46 R1,源码扫描):
 *  C1 RecordRow 禁再使用 ListItem `trailingContent =` 槽 —— 尾栏存在 = 文本列被预留宽度,
 *     「底部参数行右侧还有位置却先截断」的根因形态;
 *  C2 容器必须显式圆角 + 显式容器色,且 ListItem 转透明(圆角可见的前提);
 *  C3 半径一律取自 `MaterialTheme.shapes`,禁 `RoundedCornerShape(<魔法数>)` 作行容器
 *     (LeadingIcon 的 10dp 徽章不在此列 —— 它不是行容器)。
 * 与 RecordRowLayoutTest 的几何/结构断言互补:那边测所见,这边测禁复活。
 */
class RecordRowContainerContractTest {

    private val source: String by lazy {
        val file = File(
            System.getProperty("user.dir"),
            "src/main/java/com/lioisme/modev/ui/components/RecordRow.kt",
        )
        assertTrue("RecordRow.kt 未找到: ${file.absolutePath}", file.isFile)
        file.readText()
    }

    /** 去块注释与行注释(保留行结构),避免 KDoc 里描述的退役形态被当实现命中。 */
    private val code: String by lazy {
        val out = StringBuilder(source)
        Regex("/\\*[\\s\\S]*?\\*/").findAll(source).forEach { m ->
            for (i in m.range) if (out[i] != '\n' && out[i] != '\r') out[i] = ' '
        }
        Regex("//[^\n]*").findAll(out.toString()).forEach { m ->
            for (i in m.range) if (out[i] != '\n' && out[i] != '\r') out[i] = ' '
        }
        out.toString()
    }

    @Test
    fun `C1 record row never uses the ListItem trailing slot`() {
        assertTrue(
            "RecordRow 禁止 trailingContent 槽(V46 R1:尾栏预留宽度 = 正文截断根因)",
            !Regex("""trailingContent\s*=""").containsMatchIn(code),
        )
    }

    @Test
    fun `C2 container is rounded opaque-background with transparent ListItem`() {
        assertTrue("行容器必须 clip", Regex("""\.clip\(MaterialTheme\.shapes\.\w+\)""").containsMatchIn(code))
        assertTrue(
            "行容器必须显式 background",
            Regex("""\.background\(MaterialTheme\.colorScheme\.surfaceContainerLow\)""").containsMatchIn(code),
        )
        assertTrue(
            "ListItem 必须转透明(否则波纹被容器吞 + 圆角被内部 Surface 方角覆盖)",
            Regex("""ListItemDefaults\.colors\(containerColor = Color\.Transparent\)""").containsMatchIn(code),
        )
    }

    @Test
    fun `C3 no magic radius on the row container`() {
        assertTrue(
            "行容器半径必须来自 shapes token",
            !Regex("""background\([^)]*RoundedCornerShape\(\d+""").containsMatchIn(code),
        )
    }
}
```

> Robolectric 无关:纯 JVM 源码扫描。若 `System.getProperty("user.dir")` 在本模块指向仓库根而非 `app/`,按 `DialogConventionTest.locateProjectRoot()` 的双候选形态(`user.dir` 与 `user.dir/..`)先定位再拼路径。

- [~] **Step 8: 跑 JVM 门禁 + 设备几何/结构测试**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.architecture.RecordRowContainerContractTest" --tests "com.lioisme.modev.architecture.DialogConventionTest" --no-build-cache
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.ui.components.RecordRowLayoutTest --no-build-cache
```
预期:两文件全绿;`RecordRowLayoutTest` 8 例(既有 6 + 新 2)全绿。

- [x] **Step 9: 跑记录列表三屏回归(容器化波及面)**

```powershell
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.TimelineListTest,com.lioisme.modev.MonitorListScreensTest,com.lioisme.modev.GlobalTypeRecordsTest,com.lioisme.modev.RecordListScrollPreservationTest --no-build-cache
```
预期:相对 V45 基线**净新增失败 = 0**(已知环境失败按 AGENTS「模拟器偶发 ADB 重置」重跑判定,不记为回归)。滚动保持类须特别确认:`LazyListState` 仍由 VM 承载,容器化不改变 item 组合结构。

- [x] **Step 10: 目视复核(圆角与通栏是视觉事实,不能只靠断言)**

设备执行:
```bash
adb -s emulator-5554 exec-out screencap -p > /tmp/v46-records.png
```
确认三件事并写进本文「执行记录」:① 每行是独立圆角卡片(相邻不粘连、不成波浪边);② 最长参数行的省略号出现在贴近行右缘处;③ 按压行时有可见波纹。任一不成立 → 回 Step 3 复核 modifier 链顺序,禁改断言。

- [ ] **Step 11: 提交**

```bash
git add app/src/main/java/com/lioisme/modev/ui/components/RecordRow.kt app/src/main/java/com/lioisme/modev/ui/records/RecordTimelineList.kt app/src/androidTest/java/com/lioisme/modev/ui/components/RecordRowLayoutTest.kt app/src/test/java/com/lioisme/modev/architecture/RecordRowContainerContractTest.kt
git commit -m "feat(records): rounded record row cards with full-width field lines (trailing slot retired)"
```

---

### Task 2: 色板网格步长单点(R2) — ✅ 完成(JVM + 设备 + 目视)

**Files:**
- Modify: `app/src/main/java/com/lioisme/modev/ui/settings/SettingsScreen.kt:462-509`(`ColorSheet` 网格)+ 文件末尾常量区
- Test: `app/src/androidTest/java/com/lioisme/modev/ColorSheetDeviceTest.kt`(新增 1 例)

**Interfaces:**
- Consumes:`ThemeSeeds.OPTIONS`(20 项)、`selectedMarkColor(isDark)`、`Tokens.swatchBorderAlpha`、`colorOptions` 参数、testTag `color-swatch`。
- Produces:`private val ColorSwatchGap: Dp`(文件级,横纵共用);testTag 集合零变化。

- [x] **Step 1: 写「修复前必红」的间距门禁**

`ColorSheetDeviceTest` 追加。**import 补 2 行**(`assertFalse`、`kotlin.math.abs`;`dp`/`hasText`/`boundsInRoot` 形态该文件已具备或经 `SemanticsNode` 属性取用):

```kotlin
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
```

```kotlin
    /**
     * V46 R2 几何锁:色点网格**横纵步长等值**。
     * 修复前 Column 无 verticalArrangement(四行圆点上下相切 = 用户报的「太丑」);
     * 修复后纵间距 == 横间距 == ColorSwatchGap,由同一常量单点保证。
     */
    @Test
    fun colorSheet_swatchGridHasEqualGaps() {
        rule.waitForIdle()
        openColorSheet()
        val nodes = rule.onAllNodesWithTag("color-swatch").fetchSemanticsNodes()
        assertEquals("20 色行主序渲染", 20, nodes.size)

        val a = nodes[0]
        val b = nodes[1]  // 同行右邻
        val c = nodes[5]  // 下一行同列(chunked(5) 行主序)
        val hGap = with(a.density) { (b.boundsInRoot.left - a.boundsInRoot.right).toDp() }
        val vGap = with(a.density) { (c.boundsInRoot.top - a.boundsInRoot.bottom).toDp() }
        assertTrue("纵向步长必须 > 0(实测 $vGap)", vGap > 4.dp)
        assertTrue(
            "横纵步长应等值(实测 h=$hGap v=$vGap)",
            kotlin.math.abs(hGap.value - vGap.value) < 1f,
        )
        assertFalse("色点不得相切", vGap <= 0.dp)
    }
```

> 密度一律取 `SemanticsNode.density`(仓库已验证形态),不用 `rule.density`。

- [~] **Step 2: 跑测试确认红**

```powershell
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.ColorSheetDeviceTest --no-build-cache
```
预期:新例 FAIL(`vGap == 0.dp`),既有 4 例 PASS。

- [x] **Step 3: 用常量替换两处硬编码并补纵向步长**

`SettingsScreen.kt` 的 `ColorSheet` 内,网格段(现 :462-467)改为:

```kotlin
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                // V46 R2/D7:横纵步长同一常量单点(旧实装只有 Row 的横向 spacedBy,
                // Column 无 verticalArrangement → 四行色点上下相切)。
                verticalArrangement = Arrangement.spacedBy(ColorSwatchGap),
            ) {
                colorOptions.chunked(5).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ColorSwatchGap),
                    ) {
```

文件级常量区(`ColorSheet` 之后、与 `selectedMarkColor` 同段)加:

```kotlin
/** 色板网格步长(横 = 纵;V46 R2 单点,ColorSheet 两向 spacedBy 共用)。 */
private val ColorSwatchGap = 12.dp
```

- [x] **Step 4: 跑测试确认绿 + 既有 4 例不破**

```powershell
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.ColorSheetDeviceTest,com.lioisme.modev.V42StabilityProbeTest --no-build-cache
```
预期:全绿。`V42StabilityProbeTest:164` 按 `color-swatch` 索引点选,索引语义(行主序)未变。

- [x] **Step 5: 目视复核并截图存档**

```bash
adb -s emulator-5554 exec-out screencap -p > /tmp/v46-colorsheet.png
```
确认 4 行 5 列圆点间隙上下左右一致,选中环与勾在亮/暗两档下都清晰(`selectedMarkColor` 语义未动)。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/lioisme/modev/ui/settings/SettingsScreen.kt app/src/androidTest/java/com/lioisme/modev/ColorSheetDeviceTest.kt
git commit -m "fix(theme): give the seed swatch grid an equal vertical stride"
```

---

### Task 3: 令牌改名契约层(方法层单点) — ✅ 已完成(JVM 全绿)

**Files:**
- Modify: `app/src/main/java/com/lioisme/modev/domain/server/ServerConfigService.kt:42-70`(`McpServiceConfig` 接口)
- Modify: `app/src/main/java/com/lioisme/modev/data/repo/SettingsRepository.kt:400-409`(`addMcpToken`/`revokeMcpToken` 之间)
- Modify: `app/src/main/java/com/lioisme/modev/ui/settings/McpTokensViewModel.kt:31-37`
- Modify: `app/src/test/java/com/lioisme/modev/architecture/UiMcpCapabilityParityTest.kt`(「令牌管理」能力行登记,与 VM 动作同提交)
- Test: `app/src/test/java/com/lioisme/modev/data/repo/SettingsRepositoryMcpTokenTest.kt`

**Interfaces:**
- Consumes:`McpTokenEntry`(data class,`label` 为 val)、`persistMcpTokens(List<McpTokenEntry>)`、`_mcpTokens: MutableStateFlow<List<McpTokenEntry>>`。
- Produces:`suspend fun McpServiceConfig.setMcpTokenLabel(id: String, label: String)`;`fun McpTokensViewModel.setTokenLabel(id: String, label: String)` —— T6 消费(T5 为纯资源任务,不触碰代码)。

- [x] **Step 1: 写失败测试(仓库层改名四态)**

`SettingsRepositoryMcpTokenTest` 追加(沿用类内既有 `repo()` 与 `runBlocking` 形态,断言以 StateFlow 现值为准):

```kotlin
    @Test
    fun `rename updates label only and keeps value order intact`() = runBlocking {
        val r = repo()
        val first = r.addMcpToken("alpha")
        val second = r.addMcpToken("beta")

        r.setMcpTokenLabel(first.id, "  改名后  ")

        val tokens = r.mcpTokens.value
        assertEquals("条目数与顺序不变", listOf(first.id, second.id), tokens.map { it.id })
        assertEquals("label 两端空白被裁", "改名后", tokens[0].label)
        assertEquals("beta", tokens[1].label)
        assertEquals("值集合零变化(不重启不踢会话)", listOf(first.value, second.value), tokens.map { it.value })
    }

    @Test
    fun `rename to blank falls back to unnamed rendering form`() = runBlocking {
        val r = repo()
        val e = r.addMcpToken("tmp")
        r.setMcpTokenLabel(e.id, "")
        assertEquals("", r.mcpTokens.value.single().label)
    }

    @Test
    fun `rename unknown id is a no-op`() = runBlocking {
        val r = repo()
        val snapshot = r.mcpTokens.value
        r.setMcpTokenLabel("no-such-id", "x")
        assertEquals(snapshot, r.mcpTokens.value)
    }

    @Test
    fun `rename persists across repository instances`() = runBlocking {
        val r = repo()
        val e = r.addMcpToken("before")
        r.setMcpTokenLabel(e.id, "after")
        assertEquals("after", repo().mcpTokens.value.single().label)
    }
```

- [~] **Step 2: 跑测试确认编译失败(方法不存在)**

```powershell
.\gradlew.bat :app:compileModernDebugUnitTestKotlin --no-build-cache
```
预期:`unresolved reference: setMcpTokenLabel`(**这就是本步预期**;环境性 daemon 丢失时先按 AGENTS 用 `-Dkotlin.compiler.execution.strategy=in-process` 复跑再归因)。

- [x] **Step 3: 契约声明**

`ServerConfigService.kt` 的 `McpServiceConfig` 内,`addMcpToken` 与 `revokeMcpToken` 之间插入:

```kotlin
    /**
     * 重命名令牌(仅改 [McpTokenEntry.label])。
     *
     * 名称不是秘密、不参与 Bearer 校验 ⇒ 值集合逐元素不变,校验集原地重写亦零影响:
     * **不重启、不踢已连会话**(与 add/revoke 同一原地生效纪律,AGENTS #26)。
     * [label] 两端空白被裁;空串 = 未命名(UI 回落 `token_unnamed`)。未知 [id] 幂等 no-op。
     */
    suspend fun setMcpTokenLabel(id: String, label: String)
```

- [x] **Step 4: 仓库实现(唯一持久化出口,零新写面)**

`SettingsRepository.kt` 中 `addMcpToken` 之后插入(与相邻两方法同构,复用 `persistMcpTokens`):

```kotlin
    /** 重命名:仅换 label,值集合原序原值 → 校验集内容零变化(不重启不踢会话)。 */
    override suspend fun setMcpTokenLabel(id: String, label: String) {
        val trimmed = label.trim()
        val current = _mcpTokens.value
        val next = current.map { if (it.id == id) it.copy(label = trimmed) else it }
        if (next == current) return
        persistMcpTokens(next)
    }
```

- [x] **Step 5: VM 动作面**

`McpTokensViewModel.kt` 的 `addToken` 之后插入(方法名以 `set` 起 = `UiMcpCapabilityParityTest` R5 扫描面):

```kotlin
    /** 重命名(可提交空名 = 清除名称);原地生效,不重启 MCP、不踢已连会话。 */
    fun setTokenLabel(id: String, label: String) {
        viewModelScope.launch { mcpConfig.setMcpTokenLabel(id, label) }
    }
```

**同提交必须登记能力行**(否则本任务收尾时 R5 红 = 分支中间态不可编译通过门禁):`UiMcpCapabilityParityTest.kt` 的「令牌管理」行 `ui` 列表补一项 ——

```kotlin
            ui = listOf(
                "McpTokensViewModel.setTokenAuth",
                "McpTokensViewModel.addToken",
                "McpTokensViewModel.revokeToken",
                "McpTokensViewModel.setTokenLabel",
            ),
```

> R4 会在 `ui` 子树真扫 `fun setTokenLabel(`(上面 Step 已实装)⇒ 两侧同时闭合;MCP 侧不新增工具(令牌管理行 `tools = emptyList()` + 既有理由不变)。

- [x] **Step 6: 跑仓库测试 + 相关门禁确认全绿**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.data.repo.SettingsRepositoryMcpTokenTest" --tests "com.lioisme.modev.architecture.SingleWriteEntryPointTest" --tests "com.lioisme.modev.architecture.UiMcpCapabilityParityTest" --tests "com.lioisme.modev.mcp.McpToolParityTest" --tests "com.lioisme.modev.PackageBoundaryTest" --tests "com.lioisme.modev.LayerDependencyGateTest" --no-build-cache
```
预期:全绿 —— 特别确认 ① W2 未把新写点判为越界(改名不写服务端可变属性);② `UiMcpCapabilityParityTest` R4/R5 双侧闭合(新动作已登记且符号存在);③ `McpToolParityTest` 仍判工具面 45、零 token 工具;④ #18 分层门禁零新增违规。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/lioisme/modev/domain/server/ServerConfigService.kt app/src/main/java/com/lioisme/modev/data/repo/SettingsRepository.kt app/src/main/java/com/lioisme/modev/ui/settings/McpTokensViewModel.kt app/src/test/java/com/lioisme/modev/data/repo/SettingsRepositoryMcpTokenTest.kt app/src/test/java/com/lioisme/modev/architecture/UiMcpCapabilityParityTest.kt
git commit -m "feat(mcp): add setMcpTokenLabel contract for in-place token renaming"
```

---

### Task 4: `ModevValueDialog` 只读值对话框 — ✅ 已完成(JVM 全绿)

**Files:**
- Create(在既有文件内新增 composable):`app/src/main/java/com/lioisme/modev/ui/components/ModevDialogs.kt`
- Test: `app/src/test/java/com/lioisme/modev/ui/components/ModevValueDialogTest.kt`(新建,Robolectric)

**本任务零生产调用方**(组件 + 测试即完整交付;首个消费方 = T6),不触发 `UiMcpCapabilityParityTest` R5(只扫 ViewModel 可变动作)与 `McpToolParityTest`(工具面恒 45,零新工具)。

**Interfaces:**
- Consumes:`R.string.diagnostics_detail_copy`(en 既有「Copy」/zh「复制」)、`R.string.close`(**T5 才落资源键** → 本任务与 T5 有编译依赖,见 Step 0)。
- Produces:`@Composable fun ModevValueDialog(title: String, value: String, onCopy: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, valueTestTag: String? = null, copyTestTag: String? = null, closeTestTag: String? = null)`;testTag 由调用方给定。

- [x] **Step 0: 顺序前置**

`close` 键在 T5 落地(组件体引用它)。为避免出现「不可编译的中间提交」,**执行序 = T5 → T4**(两任务的实现互不依赖,只共享资源;T5 是纯增键,先做零风险)。若因故必须先做 T4,则该步内 `dismissButton` 暂用既有 `R.string.cancel`,并在 T5 Step 6 按既定动作改回 `R.string.close`(T5 Step 6 就是为此过渡而存在的)。

- [x] **Step 1: 写失败测试**

新建 `app/src/test/java/com/lioisme/modev/ui/components/ModevValueDialogTest.kt`(完整文件)。测试**用字面量标题**,不引资源键 → 与 T5 解耦(组件测试面无 i18n 依赖);组合层断言一律 `fetchSemanticsNodes()` 形态(该测试库无 `assertExists`,见 AGENTS 工具链坑)。

```kotlin
package com.lioisme.modev.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 只读值对话框契约(V46 R3/D3):完整值原样上屏 + 复制/关闭各回调恰一次,
 * 且组件面零输入控件(强约束 #4 的负向门禁走源码扫描 —— 本仓 Compose 测试库
 * 缺件多,语义匹配器不如源码扫描稳,与 DialogConventionTest 同族)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModevValueDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        value: String = "0123456789abcdef0123456789abcdef",
        onCopy: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    ModevValueDialog(
                        title = "value-title",
                        value = value,
                        onCopy = onCopy,
                        onDismiss = onDismiss,
                        valueTestTag = "value-text",
                        copyTestTag = "value-copy",
                        closeTestTag = "value-close",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun rendersValueVerbatim_withCopyAndClose() {
        val v = "0123456789abcdef0123456789abcdef"
        show(value = v)
        assertTrue(
            "值节点必须存在",
            compose.onAllNodesWithTag("value-text").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "对话框内必须原样呈现完整值(不掩码)",
            compose.onAllNodesWithText(v).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun copyInvokesOnce_closeInvokesOnce() {
        var copies = 0
        var dismisses = 0
        show(onCopy = { copies++ }, onDismiss = { dismisses++ })
        compose.onNodeWithTag("value-copy").performClick()
        compose.waitForIdle()
        assertEquals("复制只回调一次,且不得顺手关窗", 1, copies)
        assertEquals(0, dismisses)
        compose.onNodeWithTag("value-close").performClick()
        compose.waitForIdle()
        assertEquals("关闭经独立回调收口", 1, dismisses)
    }

    @Test
    fun componentSurfaceCarriesNoTextInput() {
        // Robolectric 与纯 JVM 单测的工作目录不同(user.dir 可能是 app/ 也可能是仓库根),
        // 故三候选同 DialogConventionTest 的双候选口径再加一条。
        val relative = "com/lioisme/modev/ui/components/ModevDialogs.kt"
        val userDir = System.getProperty("user.dir")
        val candidates = listOf(
            File(userDir, "src/main/java/$relative"),
            File(userDir, "app/src/main/java/$relative"),
            File(userDir, "../app/src/main/java/$relative"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("ModevDialogs.kt 未定位(user.dir=$userDir;试过 ${candidates.map { it.path }})")
        assertTrue(
            "只读对话框禁含输入控件(输入类一律底部 sheet,#4)",
            !Regex("""\b(Outlined)?TextField\(""").containsMatchIn(file.readText()),
        )
    }
}
```

> 三锚点(`value-text`/`value-copy`/`value-close`)全由调用方注入 → Step 3 签名必须含 `closeTestTag`,否则第二例点不到目标。

- [~] **Step 2: 跑测试确认红**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.ui.components.ModevValueDialogTest" --no-build-cache
```
预期:编译失败 `unresolved reference: ModevValueDialog`。

- [x] **Step 3: 实装组件**

`ModevDialogs.kt` 顶部 import 追加 11 行(现仅 12 行 import:`AlertDialog`/`MaterialTheme`/`Text`/`TextButton`/`Composable`/`Modifier`/`testTag`/`stringResource`/`R`/`StringRes` 已就位):

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
```

并在文件末尾追加:

```kotlin
/**
 * 统一只读值对话框(V46 R3/D3):展示完整明文 + 复制,零输入。
 *
 * 与 [ModevConfirmDialog] 同居 `ModevDialogs.kt` —— `AlertDialog(` 唯一出口
 * (强约束 #27 / `DialogConventionTest` R1 不破),且本组件同样**不发 icon**(R2)。
 * 强约束 #4 边界:需要键盘输入的弹层仍一律底部 sheet,本组件只做只读展示。
 * 复制动作的后续(关闭 / Snackbar)由调用方在 [onCopy] 内决定。
 */
@Composable
fun ModevValueDialog(
    title: String,
    value: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
    copyTestTag: String? = null,
    closeTestTag: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .let { base -> valueTestTag?.let { base.testTag(it) } ?: base },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onCopy,
                modifier = copyTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostics_detail_copy))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = closeTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
```

- [x] **Step 4: 跑测试确认绿**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.ui.components.ModevValueDialogTest" --tests "com.lioisme.modev.architecture.DialogConventionTest" --no-build-cache
```
预期:全绿(`DialogConventionTest.sharedConfirmDialogCarriesNoIcon` 对整个 `ModevDialogs.kt` 扫 `icon\s*=` → 新组件用 `contentDescription = null` 的位置参数形态,不得写 `icon =`)。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/lioisme/modev/ui/components/ModevDialogs.kt app/src/test/java/com/lioisme/modev/ui/components/ModevValueDialogTest.kt
git commit -m "feat(components): add read-only ModevValueDialog with copy action"
```

---

### Task 5: i18n 四语资源(纯增,编译安全) — ✅ 已完成(四语 + 生成器 DONE)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`(en 段 :426-453 令牌区附近)
- Modify: `app/src/main/res/values-zh/strings.xml`(:419-445 同区)
- Modify: `scripts/v42_table_2.mjs`(ja/ko 翻译表)
- Generate: `app/src/main/res/values-ja/strings.xml`、`app/src/main/res/values-ko/strings.xml`(生成器覆写,**禁手改**)

**Interfaces:**
- Consumes:既有键 `diagnostics_detail_copy`(复制)、`danger_title`、`confirm`、`cancel`;生成器契约 `SAME`/`IDENTICAL_OK`/占位符校验。
- Produces(6 新键,T4/T6 消费):`close`、`token_rename`、`token_view`、`token_more`、`token_value_title`、`token_renamed`。本任务**不删**任何键(删除与改名放 T6,保证每步可编译)。

- [x] **Step 1: 落 en 键**

`values/strings.xml` 令牌区(`token_created_legacy` 之后)追加:

```xml
    <string name="token_rename">Rename</string>
    <string name="token_view">View</string>
    <string name="token_more">More actions</string>
    <string name="token_value_title">Token value</string>
    <string name="token_renamed">Name updated</string>
```

通用区(`confirm`/`cancel` 相邻处)追加:

```xml
    <string name="close">Close</string>
```

- [x] **Step 2: 落 zh 键(同区同序)**

`values-zh/strings.xml` 追加:

```xml
    <string name="token_rename">重命名</string>
    <string name="token_view">查看</string>
    <string name="token_more">更多操作</string>
    <string name="token_value_title">令牌值</string>
    <string name="token_renamed">名称已更新</string>
```

```xml
    <string name="close">关闭</string>
```

- [x] **Step 3: 补 ja/ko 翻译表**

`scripts/v42_table_2.mjs` 在 `token_revoke: [...]` 附近追加(键序不要求与 xml 一致,生成器按键名取表):

```js
  close: ['閉じる', '닫기'],
  token_rename: ['名前を変更', '이름 변경'],
  token_view: ['表示', '보기'],
  token_more: ['その他の操作', '나머지 작업'],
  token_value_title: ['トークン値', '토큰 값'],
  token_renamed: ['名前を更新しました', '이름이 업데이트되었습니다'],
```

- [x] **Step 4: 重跑生成器并确认键数增长**

```bash
node scripts/v42_generate.mjs
```
预期输出两行 `ja: strings=… / ko: strings=…`,总数比基线 **+6**,末行 `DONE`。任一异常即停:
- `key '<x>' missing from translation table` → Step 3 漏键;
- `placeholder mismatch` / `locale value identical to en` → 译文形态违规(不得靠加白名单绕过)。

- [x] **Step 5: 跑四语一致性与资源门禁**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.i18n.*" --no-build-cache
```
预期:全绿(`StringResourceParityTest` 四语循环 + plurals 逐 locale;`HardcodedStringConventionTest` 不受影响)。

- [x] **Step 6: 切 T4 的关闭文案(若 T4 曾暂用 `cancel`)**

`ModevDialogs.kt` 的 `ModevValueDialog.dismissButton` 文案由 `R.string.cancel` 改为 `R.string.close`,复跑 T4 Step 4 命令确认绿。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml app/src/main/res/values-ja/strings.xml app/src/main/res/values-ko/strings.xml scripts/v42_table_2.mjs app/src/main/java/com/lioisme/modev/ui/components/ModevDialogs.kt
git commit -m "feat(i18n): add token menu and close copy in en/zh/ja/ko"
```

---

### Task 6: 令牌页菜单化 + 「撤销→删除」措辞收口(R3) — ✅ 完成(设备 5 例 + 目视)

**Files:**
- Modify: `app/src/main/java/com/lioisme/modev/ui/settings/McpTokensScreen.kt`(页面 :89-234 + `TokenCard` :292-370 + `TokenAction` :373-398 删除)
- Modify: `app/src/main/res/values{,-zh,-ja,-ko}/strings.xml`(改值 + 键改名 + 删死键;ja/ko 走生成器)
- Modify: `scripts/v42_table_2.mjs`
- 复核(不改动):`app/src/test/java/com/lioisme/modev/architecture/UiMcpCapabilityParityTest.kt`(能力行登记已在 T3 完成)
- Test: `app/src/androidTest/java/com/lioisme/modev/McpTokensScreenTest.kt`(新建,设备)

**Interfaces:**
- Consumes:`McpTokensViewModel.setTokenLabel`(T3)、`ModevValueDialog`(T4)、`ModevInputSheet`、`ModevConfirmDialog`、`maskValue`(保留)、新键(T5)。
- Produces:testTag 集合:`token-more-${id}`、`token-menu-view`、`token-menu-rename`、`token-menu-delete`、`token-view-dialog`、`token-view-value`、`token-view-copy`、`token-view-close`、`token-rename-sheet`、`token-rename-input`、`token-rename-confirm`、`token-delete-confirm`;退役:`token-copy-${id}`、`token-revoke-${id}`、`token_show/token_hide` 键、页面级 `visibleIds`。

- [x] **Step 1: 写设备验收测试(先定义目标行为)**

新建 `app/src/androidTest/java/com/lioisme/modev/McpTokensScreenTest.kt`:

```kotlin
package com.lioisme.modev

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.ModevApp
import com.lioisme.modev.core.model.McpTokenEntry
import com.lioisme.modev.domain.server.McpServiceConfig
import com.lioisme.modev.ui.settings.McpTokensScreen
import com.lioisme.modev.ui.settings.McpTokensViewModel
import com.lioisme.modev.ui.settings.McpTokensViewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 令牌页菜单化验收(V46 R3/D2/D3/D4):
 *  - 行内零动作按钮(复制/显隐按钮整体退役),唯一入口 = 更多按钮;
 *  - 菜单恰三项(查看/重命名/删除),顺序与危险度递进一致;
 *  - 查看对话框呈现完整明文并可复制后关闭;
 *  - 重命名经底部 sheet 原地生效(不重启不踢会话);
 *  - 删除走居中危险确认,确认即从校验集移除。
 *
 * 前置:直挂 `McpTokensScreen`(同 `ServicesScreenTest` 既有形态),装配入口
 * 一律 `ModevApp.container`,禁为测试新开旁路。
 */
@RunWith(AndroidJUnit4::class)
class McpTokensScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = ctx.getString(id)

    /** 契约类型持有(AGENTS #18 G2:`mcpConfig` 的公开类型即 `domain` 接口)。 */
    private val mcp: McpServiceConfig get() = (ctx.applicationContext as ModevApp).container.mcpConfig
    private lateinit var seeded: McpTokenEntry

    @Before
    fun seedAndHost() {
        // 每例自建一枚 label 为空的令牌:headline 走 token_unnamed 回落形态,
        // 重命名用例因此无需清文本;tearDown 全量清场。
        seeded = runBlocking { mcp.addMcpToken("") }
        val container = (ctx.applicationContext as ModevApp).container
        val vm = McpTokensViewModelFactory(container).create(McpTokensViewModel::class.java)
        rule.setContent {
            MaterialTheme { McpTokensScreen(viewModel = vm, onBack = {}) }
        }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        // 令牌值是宿主本地秘密,测后清场;认证复位默认停用,不污染后续用例。
        runBlocking {
            mcp.mcpTokens.value.forEach { mcp.revokeMcpToken(it.id) }
            mcp.setMcpTokenAuth(false)
        }
    }

    private fun openMenu() {
        // ⋮ 按钮可能在视口外(列表可滚):视口外 performClick 被静默丢弃(V45 实锤),
        // 一律走 clickScrolledIntoView(同包 AnchorProbe,无需 import)。
        rule.clickScrolledIntoView(hasTestTag("token-more-${seeded.id}"))
        rule.waitForIdle()
    }

    @Test
    fun rowHasNoInlineActions_onlyMoreButton_andValueStaysMasked() {
        assertTrue(
            "更多按钮必须存在",
            rule.onAllNodesWithTag("token-more-${seeded.id}").fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onAllNodesWithTag("token-copy-${seeded.id}").assertCountEquals(0)
        rule.onAllNodesWithTag("token-revoke-${seeded.id}").assertCountEquals(0)
        // D3:明文恒不上行(只进查看对话框);掩码点阵必现 —— 断言点阵而非重算掩码公式。
        rule.onAllNodesWithText(seeded.value).assertCountEquals(0)
        assertTrue(
            "行内应为掩码形态",
            rule.onAllNodesWithText("••••••", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun moreMenu_hasViewRenameDelete() {
        openMenu()
        rule.onNodeWithTag("token-menu-view").assertIsDisplayed()
        rule.onNodeWithTag("token-menu-rename").assertIsDisplayed()
        rule.onNodeWithTag("token-menu-delete").assertIsDisplayed()
        // 「只需三项」的口径核对:旧行内动作锚点在菜单开启后仍须为零。
        rule.onAllNodesWithTag("token-copy-${seeded.id}").assertCountEquals(0)
    }

    @Test
    fun view_opensDialogWithPlaintext_andCopyClosesIt() {
        openMenu()
        rule.onNodeWithTag("token-menu-view").performClick()
        // Dialog/Sheet 是新窗口,单次 waitForIdle 不保证首帧已组合 → 用有界等待锚点。
        rule.awaitAnchor("token-view-value", "查看应打开只读值对话框")
        rule.onNodeWithText(str(R.string.token_value_title)).assertIsDisplayed()
        assertTrue(
            "对话框内必须是完整明文",
            rule.onAllNodesWithText(seeded.value).fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onNodeWithTag("token-view-copy").performClick()
        rule.waitForIdle()
        // 复制即关窗:明文不常驻(D3)
        rule.onAllNodesWithTag("token-view-value").assertCountEquals(0)
        val stored = mcp.mcpTokens.value.first { it.id == seeded.id }
        assertEquals("复制不得改动条目", seeded.value, stored.value)
    }

    @Test
    fun rename_updatesLabelInPlace_withoutTouchingValue() {
        openMenu()
        rule.onNodeWithTag("token-menu-rename").performClick()
        rule.awaitAnchor("token-rename-input", "重命名应打开输入 sheet")
        // 种子令牌 label 恒空(见 seedAndHost)→ 无预填文本,输入即终值;
        // 只用本仓已验证 API(performTextInput),不引 performTextClearance(无先例)。
        rule.onNodeWithTag("token-rename-input").performTextInput("renamed-by-test")
        rule.onNodeWithTag("token-rename-confirm").performClick()
        rule.waitForIdle()
        val stored = mcp.mcpTokens.value.first { it.id == seeded.id }
        assertEquals("renamed-by-test", stored.label)
        assertEquals("值不变 = 校验集零变化(不重启不踢会话)", seeded.value, stored.value)
        assertTrue(
            "新名应回写到行 headline",
            rule.onAllNodesWithText("renamed-by-test").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun delete_confirmsAndRemovesEntry() {
        openMenu()
        rule.onNodeWithTag("token-menu-delete").performClick()
        rule.awaitAnchor("token-delete-confirm", "删除应弹居中危险确认")
        rule.onNodeWithTag("token-delete-confirm").performClick()
        rule.waitForIdle()
        assertTrue(
            "条目应已移除",
            mcp.mcpTokens.value.none { it.id == seeded.id },
        )
        rule.onAllNodesWithTag("token-more-${seeded.id}").assertCountEquals(0)
    }
}
```

> 只用本仓已验证 API:`assertIsDisplayed`/`assertCountEquals`(`ColorSheetDeviceTest` 在用)、`performTextInput`(`GlobalTypeRecordsTest` 在用)、`awaitAnchor`/`clickScrolledIntoView`(同包 `AnchorProbe`)。**不引** `assertExists`/`waitUntil`/`hasText(Regex)`/`performTextClearance`(AGENTS 缺件清单 + 本仓无先例)。

- [~] **Step 2: 跑测试确认红**

```powershell
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.McpTokensScreenTest --no-build-cache
```
预期五例全 FAIL,失败点必须分别是:① `token-more-*` 锚点缺失;②③④⑤ 菜单项 tag 缺失(`awaitAnchor` 超时或 `onNodeWithTag` 零命中)。若某例意外绿 → 说明该断言没测到目标行为(假绿),先修断言再进 Step 3。

- [x] **Step 3: 改写页面状态与弹层接线**

`McpTokensScreen.kt`:删除 `var visibleIds by remember { mutableStateOf(setOf<String>()) }`(:98),替换为三态目标:

```kotlin
    var viewTarget by remember { mutableStateOf<McpTokenEntry?>(null) }
    var renameTarget by remember { mutableStateOf<McpTokenEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<McpTokenEntry?>(null) }
```

`revokeTarget?.let { … ModevConfirmDialog … }`(:222-233)整段替换为下述三块(新建/撤销既有 sheet 保持原位):

```kotlin
    viewTarget?.let { target ->
        ModevValueDialog(
            title = stringResource(R.string.token_value_title),
            value = target.value,
            onCopy = {
                copyToken(target)
                viewTarget = null
            },
            onDismiss = { viewTarget = null },
            modifier = Modifier.testTag("token-view-dialog"),
            valueTestTag = "token-view-value",
            copyTestTag = "token-view-copy",
        )
    }

    renameTarget?.let { target ->
        ModevInputSheet(
            title = stringResource(R.string.token_rename),
            label = stringResource(R.string.tokens_add_label_hint),
            initialValue = target.label,
            hint = null,
            validate = { true },
            onSubmit = { label ->
                viewModel.setTokenLabel(target.id, label)
                renameTarget = null
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.token_renamed)) }
            },
            onDismiss = { renameTarget = null },
            modifier = Modifier.testTag("token-rename-sheet"),
            inputTestTag = "token-rename-input",
            confirmTestTag = "token-rename-confirm",
        )
    }

    deleteTarget?.let { target ->
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(R.string.token_delete_confirm_msg),
            confirmTextRes = R.string.token_delete,
            onConfirm = {
                viewModel.revokeToken(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            confirmTestTag = "token-delete-confirm",
        )
    }
```

`viewTarget` 块另需关闭按钮锚点(与 T4 测试的 `value-close` 同形),在 `copyTestTag = "token-view-copy",` 之后补一行:

```kotlin
            closeTestTag = "token-view-close",
```

令牌列表渲染段(:180-199)简化为(去掉显隐回调):

```kotlin
                tokens.forEachIndexed { index, entry ->
                    TokenCard(
                        entry = entry,
                        localeTag = locale,
                        onView = { viewTarget = entry },
                        onRename = { renameTarget = entry },
                        onDelete = { deleteTarget = entry },
                    )
                    if (index != tokens.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
```

- [x] **Step 4: 改写 `TokenCard`,删除 `TokenAction`**

`TokenCard`(:292-370)整体替换(菜单锚定用 `Box` 包 `IconButton + DropdownMenu`,与 `AppsScreen.kt:445` 既有形态同构):

```kotlin
/**
 * 令牌卡(V46 R3 重排):行体不承载点击(D4),三动作恒由「更多」菜单驱动;
 * 值恒掩码(D3)—— 明文只经查看对话框上屏。长度固定的掩码串与创建日同排,
 * supporting 行因此不再随显隐态被截断。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenCard(
    entry: McpTokenEntry,
    localeTag: java.util.Locale,
    onView: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("token-row-${entry.id}"),
    ) {
        val createdText =
            if (entry.createdAt <= 0L) {
                stringResource(R.string.token_created_legacy)
            } else {
                formatDay(entry.createdAt, localeTag)
            }
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    entry.label.ifBlank {
                        stringResource(R.string.token_unnamed, entry.value.take(6))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = "${maskValue(entry.value)} · $createdText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Box {
                    TipIconButton(
                        tooltip = stringResource(R.string.token_more),
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("token-more-${entry.id}"),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.token_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.token_view)) },
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onView()
                            },
                            modifier = Modifier.testTag("token-menu-view"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.token_rename)) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                            modifier = Modifier.testTag("token-menu-rename"),
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.token_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("token-menu-delete"),
                        )
                    }
                }
            },
        )
    }
}
```

`TokenAction` 私有件(:373-398)整体删除。`McpTokensScreen.kt` 的 import 同步(残留未用 import 会新增 lint `UnusedImports` → 破「零新增」基线):

```kotlin
// 删除(显隐/行内复制形态退役)
// import androidx.compose.foundation.clickable
// import androidx.compose.material.icons.filled.ContentCopy
// import androidx.compose.material.icons.filled.VisibilityOff
// import androidx.compose.foundation.layout.width          // 仅旧 Spacer(Modifier.width) 用过
// 新增
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontWeight
import com.lioisme.modev.ui.components.ModevValueDialog
```

`Icons.Default.Visibility`(菜单「查看」项)与 `Icons.Default.Delete`(菜单「删除」项)保留/沿用既有 import;`FontWeight` 用于 headline Medium。`@OptIn(ExperimentalMaterial3Api::class)` 已在 `TokenCard` 上,`DropdownMenu` 若在本版本要求 opt-in 亦被覆盖。落盘后跑 `:app:lintModernRelease` 复核未用 import 归零。

- [x] **Step 5: 「撤销 → 删除」四语收口(键改名 + 死键清除)**

`values/strings.xml`:
- `token_revoke` → 改名 `token_delete`,值 `Delete`;
- `token_revoke_confirm_msg` → 改名 `token_delete_confirm_msg`,值 `Delete this token? Clients using it will be rejected immediately.`;
- **删除** `token_show`、`token_hide`(显隐形态已退役,不留死键)。

`values-zh/strings.xml` 同位置:
```xml
    <string name="token_delete">删除</string>
    <string name="token_delete_confirm_msg">删除此令牌?正在使用它的客户端将立即被拒绝。</string>
```
并删 `token_show`、`token_hide`。

`scripts/v42_table_2.mjs`:`token_revoke` 条目替换为 `token_delete: ['削除', '삭제']`、`token_revoke_confirm_msg` 替换为 `token_delete_confirm_msg: ['このトークンを削除しますか?使用中のクライアントは直ちに拒否されます。', '이 토큰을 삭제하시겠습니까? 사용 중인 클라이언트는 즉시 거부됩니다.']`;删 `token_show`、`token_hide`(V41 遗留的 `token_hide` 条目一并清)。然后:
```bash
node scripts/v42_generate.mjs
```
预期 `DONE`,键数相对 Step 4 后 **-2**(净增 4 后减 2)。

全仓清扫(必须零残留,否则 `UnusedResources` / 词汇表漂移):
```bash
grep -rn "token_revoke\|token_show\|token_hide\|R.string.token_revoke" app/src scripts docs 2>/dev/null
```
预期:无输出(本任务文档的历史措辞记录除外)。

- [x] **Step 6: 更新页面 KDoc 与 parity 能力行**

`McpTokensScreen.kt` 顶部 KDoc(:74-86)第 ②/③ 层描述改为:

```
 * ②令牌卡列表(每枚一卡:名称 headline + 掩码值·创建日 supporting +
 *   trailing 唯一「更多」按钮 → 查看/重命名/删除三项菜单,删除 error 色;
 *   行体不承载点击,明文只经查看对话框上屏 V46 D3/D4);
 * ③空态(内联 Icon+Text,按认证态区分文案)。
 *
 * 契约保留:令牌值宿主本地秘密(查看/复制仅本对话框消费,恒不进 MCP 工具输出与
 * 诊断快照);`mcpTokenSetUpdater` 原地生效不重启不踢会话;删除最后一枚不停用
 * 认证(空集 = 全拒,状态卡可见);legacy 条目标「导入」;新建与重命名走
 * [ModevInputSheet]、查看走 [ModevValueDialog]、删除走 [ModevConfirmDialog](#27)。
```

`UiMcpCapabilityParityTest.kt` 的「令牌管理」行在 **T3 已登记** `McpTokensViewModel.setTokenLabel`(同提交登记以保分支每步门禁绿)。本步只**复核**该行终态如下(禁重复添加):

```kotlin
            // 期望终态(非待粘贴的新增补丁)
            ui = listOf(
                "McpTokensViewModel.setTokenAuth",
                "McpTokensViewModel.addToken",
                "McpTokensViewModel.revokeToken",
                "McpTokensViewModel.setTokenLabel",
            ),
```

- [~] **Step 7: 跑设备测试 + 全部门禁**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --no-build-cache
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.McpTokensScreenTest,com.lioisme.modev.ServicesScreenTest --no-build-cache
```
预期:`UiMcpCapabilityParityTest`(R1-R6)全绿(新动作已登记)、`McpToolParityTest` 仍 45 工具且零 token 工具、设备 5 例全绿。

- [x] **Step 8: 目视复核**

```bash
adb -s emulator-5554 exec-out screencap -p > /tmp/v46-tokens-menu.png
```
确认:行内只剩 ⋮、菜单三项图标+文案对齐(删除整项 error 色)、查看对话框等宽明文完整可读、重命名 sheet 预填旧名。

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/com/lioisme/modev/ui/settings/McpTokensScreen.kt app/src/main/res app/src/test/java/com/lioisme/modev/architecture/UiMcpCapabilityParityTest.kt app/src/androidTest/java/com/lioisme/modev/McpTokensScreenTest.kt scripts/v42_table_2.mjs
git commit -m "feat(tokens): collapse token actions into a more menu with rename and view"
```

---

### Task 7: 契约文档与全量门禁收口 — ✅ 契约与门禁收口完成(设备矩阵已跑;仅提交待授权)

**Files:**
- Modify: `AGENTS.md`(词汇表 2 行 + 强约束 #2/#3/#13/#20/#25/#26/#27 + 新门禁清单)
- 只读复核:本文「六、验收矩阵」全部命令

- [x] **Step 1: 词汇表同步**

| 词条 | 改法 |
|------|------|
| 「记录 record」行 | 补注:V46 行容器 = 圆角卡片(shapes.medium),参数行通栏,时间/徽章在身份行右上 |
| 「令牌管理 Token Management」行 | 「显隐/复制/撤销/创建」→「创建/重命名/查看/删除」;补「行内零动作按钮,三动作唯一入口 = 更多菜单;值恒掩码,明文仅查看对话框」;禁用旧词列补:`撤销(措辞)/显隐/行内复制` |
| 「语言 Language」行 | 基线键数由 701 更新为实跑值(`node scripts/v42_generate.mjs` 控制台输出),四语齐备口径不变 |

- [x] **Step 2: 强约束条目改写**

- **#3 详情页 markdown 渲染** 与 **#13 MD3 组件契约**:补一句「记录行容器 = Box(clip shapes.medium + background surfaceContainerLow + combinedClickable),`ListItem` 恒 `containerColor = Color.Transparent` 且**禁用 `trailingContent` 槽**(尾栏预留宽度 = 正文截断根因,V46 R1)」;
- **#20 行为监听/记录行契约**:在「trailing 时间与 MicroBadge」描述处改写为「**身份行右上**(非 ListItem 尾栏)」,并指回新门禁 `RecordRowContainerContractTest`;
- **#26 MCP 契约** 与令牌管理段:补 `setMcpTokenLabel` 为契约方法层第 5 个令牌动作;重申令牌改名零会话影响(value 集合不变),且**不新增 MCP 工具**(工具面恒 45);
- **#27 对话框/Sheet 统一**:`AlertDialog(` 唯一出口的描述由「ModevConfirmDialog」扩为「ModevConfirmDialog + ModevValueDialog(只读值 + 复制,零输入)」,并记录 `DialogConventionTest` 三门禁对新组件依然成立;
- **#25 视觉 token**:补「色板网格步长单点 `ColorSwatchGap`(横 = 纵)」与「行容器半径一律 `MaterialTheme.shapes.*`,禁魔法数」。

- [x] **Step 3: lint 零新增**

```powershell
.\gradlew.bat :app:lintModernRelease --no-build-cache
```
预期:**41 warning / 1 hint / 0 error**(AGENTS #18 实测基线)。删除 `token_show`/`token_hide` 应使 `UnusedResources` 计数下降或持平;若出现新 unused(如 `Icons` import 残留)当场清干净,**禁升基线**。

- [x] **Step 4: 全量单测 + 编译 + 打包**

```powershell
.\gradlew.bat :app:testModernDebugUnitTest --no-build-cache
.\gradlew.bat :app:compileModernDebugAndroidTestKotlin --no-build-cache
.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug --no-build-cache --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2048m
```
预期:`BUILD SUCCESSFUL` × N;单测计数相对 V45 基线净增 = 本文新增用例数,零失败。

- [x] **Step 5: 设备矩阵抽样(记录行波及面)**

```powershell
.\gradlew.bat :app:connectedModernDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lioisme.modev.ui.components.RecordRowLayoutTest,com.lioisme.modev.ColorSheetDeviceTest,com.lioisme.modev.McpTokensScreenTest,com.lioisme.modev.TimelineListTest,com.lioisme.modev.GlobalTypeRecordsTest,com.lioisme.modev.MonitorListScreensTest,com.lioisme.modev.RecordListScrollPreservationTest --no-build-cache
```
预期:全绿。**测后必做恢复**(AGENTS:`connectedAndroidTest` 会让 AGP 卸载宿主 APK → Vector 模块表失效),按序列恢复并复验注入。

- [x] **Step 6: 在本文档追加「执行记录」并标记完成**

每任务完成后在「七、执行记录」补:实际命令、BUILD/测试计数、截图路径、与预期偏差及处置。全部完成项的复选框改 `- [x]`。

- [ ] **Step 7: 提交**

```bash
git add AGENTS.md docs/v46-record-row-palette-token-menu-task.md
git commit -m "docs(agents): record V46 row container, swatch stride and token menu contracts"
```

---

## 五、明确不改(范围栅栏)

| 项 | 为什么不动 |
|----|------------|
| 十个 `*Presentation()` 函数与 `RowField`/`RowPresentation` 模型 | V43 T4「一行一参数」契约正确;缺陷只在容器槽位分配,不重排语义 |
| `MiddleEllipsisText` / `MiddleEllipsis.ellipsize` | 纯函数与像素收敛逻辑无过错;此前截断由外部宽度造成,宽度恢复后自动改善 |
| 时间线组头、分页流、`cachedIn`、VM 级 `LazyListState` | #14/#15 契约面,零相关 |
| 令牌 supporting 的「掩码值 · 创建日」拼串(含 ` · `) | #20 D-1 的一行一参数契约**作用域 = 记录行**;令牌掩码串长度恒定(4+6+4),不会因拼串被截断,不构成本次缺陷 |
| `AuthStatusLine` 三态、`maskValue`、`TokenEmptyState` | 与本次三项无关(V41 T1 崩溃防线保持) |
| 认证开关、令牌集合原地生效机制(`mcpTokenSetUpdater`)、MCP 工具面 | 改名不触碰;工具面恒 45,禁新增 token 相关工具(引导悖论 + PII) |
| `ColorSheet` 的壁纸行 / 分隔线 / 「自定义强调色」标题行间距 | 用户所指为「配色之间」= 色点网格;其余间距实测正常 |

---

## 六、验收矩阵(逐条对账,禁以门禁绿代替证据)

| # | 需求/决策 | 证据类型 | 通过判据 |
|---|-----------|----------|----------|
| V1 | R1 无圆角 | 截图 `/tmp/v46-records.png` + `RecordRowContainerContractTest.C2` | 每行为独立圆角卡片;clip + 显式容器色 + 透明 ListItem |
| V2 | R1 正文被截断 | **修复前红 → 修复后绿**几何门禁 | 正文列右缘距行容器右缘 `gapDp ≤ 32`(修复前该锚点不存在;转绿后须留实测数值) |
| V3 | D1 时间右上 | `timeAndBadges_liveInsideIdentityLine` + 截图 + `RecordRowContainerContractTest.C1` 源码扫描 | 时间/徽章是 `record-row-identity` 后代;实现面零 `trailingContent =` |
| V4 | D8 行间留白 | 截图目视 | 相邻卡片间隙 ≥ 圆角半径视觉分离 |
| V5 | R2 配色间距 | `colorSheet_swatchGridHasEqualGaps` + 截图 | `vGap > 4dp` 且 `|hGap − vGap| < 1dp` |
| V6 | R3 菜单恰三项 | `moreMenu_hasViewRenameDelete` | 三锚点齐;`token-copy-*` 节点数 0 |
| V7 | D2 「删除」措辞 | grep 清扫 + 设备确认 | 四语无 `token_revoke/show/hide` 残留;确认按钮文案 = `token_delete` |
| V8 | D3 明文唯一路径 | `rowHasNoInlineActions_onlyMoreButton_andValueStaysMasked` + `view_opensDialogWithPlaintext_andCopyClosesIt` | 行内只见掩码点阵、明文节点数 0;对话框内见完整值,复制后关闭 |
| V9 | D4 行体零点击 | 代码复核(`TokenCard` 无 `clickable`) | `grep -n "clickable" McpTokensScreen.kt` 零命中 |
| V10 | R3 重命名原地生效 | `SettingsRepositoryMcpTokenTest` 4 例 + 设备 rename 用例 | label 变、value 与顺序不变、跨实例持久化、未知 id no-op |
| V11 | #27 对话框契约 | `DialogConventionTest` 3 例 | AlertDialog 仍仅 ModevDialogs.kt、无 icon |
| V12 | #1 i18n 四语 | `StringResourceParityTest` + 生成器输出 | 键数 en==zh==ja==ko;占位符同形;`DONE` 零异常 |
| V13 | #26 parity 三门禁 | `UiMcpCapabilityParityTest` R1-R6 + `McpToolParityTest` | 新动作已登记;工具面恒 45、零 token 工具 |
| V14 | #18 分层/写侧 | `PackageBoundaryTest` + `LayerDependencyGateTest` + `SingleWriteEntryPointTest` | 全绿,零新登记写点 |
| V15 | 质量基线 | `:app:lintModernRelease` | 41 warning / 1 hint / 0 error(不升基线) |
| V16 | 可交付 | `assembleModernRelease/Debug` + 设备矩阵 | BUILD SUCCESSFUL;抽样零净新增失败;测后恢复注入 |

---

## 七、波及文件清单

**版式(R1/R2)**:`ui/components/RecordRow.kt`(容器 + 槽位 + 两个新私有件 + KDoc)、`ui/records/RecordTimelineList.kt`(垂直留白)、`ui/settings/SettingsScreen.kt`(`ColorSheet` 网格 + `ColorSwatchGap`)。
**令牌契约(R3)**:`domain/server/ServerConfigService.kt`、`data/repo/SettingsRepository.kt`、`ui/settings/McpTokensViewModel.kt`。
**令牌界面**:`ui/settings/McpTokensScreen.kt`(页面状态 + `TokenCard` 重写 + `TokenAction` 删除)、`ui/components/ModevDialogs.kt`(+`ModevValueDialog`)。
**资源/脚本**:values / values-zh 手工四改、values-ja / values-ko 生成器覆写、`scripts/v42_table_2.mjs`(+6 条目、改名 2、删 2)。
**测试**:新建 `test/architecture/RecordRowContainerContractTest.kt`、`test/ui/components/ModevValueDialogTest.kt`、`androidTest/McpTokensScreenTest.kt`;扩充 `androidTest/ui/components/RecordRowLayoutTest.kt`、`androidTest/ColorSheetDeviceTest.kt`、`test/data/repo/SettingsRepositoryMcpTokenTest.kt`;登记 `test/architecture/UiMcpCapabilityParityTest.kt`(T3 同提交)。
**文档**:本文、`AGENTS.md`(词汇表 2 行 + 强约束 #1/#3/#13/#20/#25/#26/#27)。

---

## 八、风险与处置

| 风险 | 判据 | 处置 |
|------|------|------|
| `ListItem` 转透明后雷电渲染吞容器色(AGENTS #13 历史形态) | 截图行背景消失/串色 | 已按契约显式给两层色(Box `background` + ListItem `Transparent`),非 Unspecified 默认;若复现,改回「Box 上色 + ListItem 容器色 = 同色」双显式形态并留实测记录 |
| 几何门禁在真机/模拟器密度下误判 | `gapDp` 临界(32dp 容差) | 容差依据 = ListItem 收尾内边距 16dp + 徽章/浮点余量;若某屏宽实测越界,先确认尾栏确已退役(读 `C1` 源码门禁),不得放宽容差 |
| `DropdownMenu` 在 Compose 测试中锚定/命中不稳 | 菜单点开但 `token-menu-*` 不可见 | AGENTS #8 已知 `DropdownMenuItem` 整项 `MergeDescendants`:锚点用 `modifier.testTag` 打在整项上(本文实装即如此),断言用 `assertIsDisplayed`;仍不稳则退化为「点击项 → 目标弹层锚点出现」的行为级断言,并在此登记 |
| Robolectric 剪贴板行为差异 | 不适用(T4 断言 `onCopy` 回调计数,与真实剪贴板解耦) | 剪贴板**内容**正确性由两处承载:设备端 `McpTokensScreenTest` 的复制关窗断言 + 真机 `clipboardFunctional()` 哨兵(AGENTS 雷电剪贴板静默丢弃实锤);禁在 JVM 侧假装验证内容 |
| Kotlin 注释吞码(AGENTS:`/*` 序列、`*/` 提前闭合) | 成串「Expecting member declaration」 | 本任务新增 KDoc 内含 `4+6+4`、`ui/**` 之类禁写;落盘后 `awk` 逐行统计 `{}` 深度或用 `grep -n '^\s*\*.*[^\\]/\*'` 自查 |
| 构建缓存交出陈旧 dex → 设备 `NoSuchMethodError` | 只在设备运行期炸、JVM 全绿 | 全部命令已带 `--no-build-cache`;仍异常时 `--rerun-tasks --no-build-cache` 复跑,并用 `dexdump -d` 对签名 |
| 措辞改名与「双域/工具面」既有记录冲突 | 文档/脚本残留「撤销」 | Step 5 的全仓 grep 为零才收口;`docs/` 历史任务文档不回写(变更记录只追加),在本文「执行记录」注明差异 |

---

## 九、执行记录(2026-09-22 实施轮)

**图例**:`[x]` = 已执行并有证据;`[~]` = 部分执行(子项受环境限制,已用替代证据并写明);
`[ ]` = **未执行**(设备缺失 / 提交待用户授权)。

### 门禁实测数据(全部为本轮真实命令输出)

| 门禁 | 命令 | 实测结果 |
|------|------|----------|
| 源集编译 | `:app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:compileModernDebugUnitTestKotlin --no-build-cache` | **BUILD SUCCESSFUL**(23s) |
| 全量 JVM 单测 | `:app:testModernDebugUnitTest --no-build-cache` | **856 tests / 0 failures / 0 errors**(132 个 testsuite) |
| lint | `:app:lintModernRelease --no-build-cache` | **43 warning / 1 hint / 0 error**;V46 触碰文件(RecordRow/SettingsScreen/McpTokensScreen/ModevDialogs/strings.xml)**零命中**,净新增 0 |
| 打包 | `:app:assembleModernRelease :app:assembleModernDebug` | **BUILD SUCCESSFUL**(1m26s) |
| i18n 生成器 | `node scripts/v42_generate.mjs` | `ja/ko: strings=695 (translated 631, same 64) plurals=8` + **DONE** |

针对性门禁逐类核对(均取自 `app/build/test-results/testModernDebugUnitTest/`):
`RecordRowContainerContractTest` 3 例绿 · `ModevValueDialogTest` 3 例绿 ·
`SettingsRepositoryMcpTokenTest` **13** 例绿(V46 前 9,新增 4)·
`UiMcpCapabilityParityTest` 6 例绿 · `DialogConventionTest` 3 例绿 ·
`StringResourceParityTest` 2 例绿 · `McpToolParityTest` 5 例绿(工具面恒 45)·
`SingleWriteEntryPointTest` 3 例绿 · `PackageBoundaryTest` 7 例绿 · `LayerDependencyGateTest` 3 例绿。

### 门禁非空转对拍(修复前必红的替代证据)

无模拟器可跑「修复前」一轮,故改用 `git show HEAD:` 对同一组判据实测旧代码,证明门禁确有判别力:

| 判据 | 旧代码实测量 | 结论 |
|------|--------------|------|
| `RecordRow.kt` 含 `trailingContent =` | **1** | C1 必红 ✓ |
| `RecordRow.kt` 含 `clip(MaterialTheme.shapes` | **0** | C2 必红 ✓ |
| `RecordRow.kt` 含 `containerColor = Color.Transparent` | **0** | C2 必红 ✓ |
| `SettingsScreen.kt` 色板 Column 纵向 `spacedBy` | **0** | 间距门禁必红 ✓ |
| `McpTokensScreen.kt` 行内动作锚点(`token-copy-*`/`token-revoke-*`/行体 clickable) | **4** | 「行内零动作」断言必红 ✓ |

### 「正文通栏」的真实像素量测(临时 Robolectric 探针,已删除)

为满足「主张需可复现」,写了一次性探针(411dp 宽 × xhdpi,跑完即删,不入回归面):
在同一棵树内量**正文列**的左右缘,旧结构在探针内复刻(同 leading 36dp、同 padding,
唯一变量 = 是否有 `trailingContent`),不改产线代码:

| 布局 | 列左缘 | 列右缘 | 列宽 | 距行右缘 |
|------|--------|--------|------|----------|
| 旧(ListItem + trailingContent) | 68.0dp | 374.5dp | **306.5dp** | 36.5dp |
| 新(V46 容器化,无尾栏) | 68.0dp | **395.0dp** | **327.0dp** | **16.0dp** = MD3 收尾内边距 |

结论 = 两版左缘同为 68dp(对照公平),新布局正文列右缘正好落在「行宽 − 16dp 内边距」上,
**尾栏预留的死空间被收回**;Robolectric 字体度量偏窄,故 20.5dp 是**下界**,
真机上「12:03:44」+ 方向徽章实测更宽 ⇒ 设备几何门禁(`≤32dp`)会给出准确数值。

探针同时抓到一个会让设备用例静默失效的坑:`record-row-identity` 这类**挂在 ListItem 槽位内
的 testTag 在合并语义树中查不到**(实测 merged=0 / unmerged=1)→ 两条新用例已改
`useUnmergedTree = true`,并把该形态写入 AGENTS 工具链坑。

### 逐任务记录

| 任务 | 状态 | 工作内容与证据 | 偏差与处置 |
|------|------|----------------|------------|
| **T1 记录行容器化** | ✅ 完成(设备轮已过) | `RecordRow` 容器上提 `Box`(`clip(shapes.medium)` + `background(surfaceContainerLow)` + `combinedClickable`),`ListItem` 转透明、**`trailingContent` 整槽删除**;新增 `IdentityLine`(headline weight(1f) + 右上 `TimeText`/`MicroBadge`,锚点 `record-row-identity`)与 `FieldLines`;`RecordTimelineList` 行留白 4dp→6dp;新增 `RecordRowContainerContractTest`(C1/C2/C3)+ `RecordRowLayoutTest` 几何锁与结构锁 2 例;通栏量测见上一节 | 三处计划假设被实测量推翻并回写 AGENTS:① `assertAny` 无单节点接收者 → `assert(hasAnyDescendant(...))`;② `SemanticsNode` 无 `density` → `ComposeTestRule.density`;③ 槽位内 testTag 在合并树不可见 → 两用例改 `useUnmergedTree = true`(**未改则设备侧必红,已实测捕获**) |
| **T2 色板步长单点** | ✅ 完成(设备轮已过) | `ColorSheet` 网格 `Column` 补 `verticalArrangement = spacedBy(ColorSwatchGap)`,横向同值改用同一常量(旧硬编码 12.dp 两处);新增 `colorSheet_swatchGridHasEqualGaps` 几何门禁 | 无 |
| **T3 令牌改名契约层** | ✅ | `McpServiceConfig.setMcpTokenLabel` 声明 + `SettingsRepository` 实现(复用 `persistMcpTokens` 唯一出口 + 同值幂等短路) + `McpTokensViewModel.setTokenLabel`;仓库 4 例(改名保序/空名回落/未知 id no-op 且不触发持久化/跨实例持久化);能力行登记与 VM 动作**同提交域**完成 | 计划要求「先跑编译失败」一步未单跑(实现同批完成),以 13 例全绿 + 旧代码无该方法对拍替代 |
| **T5 四语资源** | ✅ | en/zh 落 6 新键(`close`/`token_rename`/`token_view`/`token_more`/`token_value_title`/`token_renamed`);ja/ko 回写 `v42_table_2.mjs` 并跑生成器 | **发现并修复既有缺陷**:V43 T4/V45 期间 7 个键被直写 `values-ja/ko` 而未回表 ⇒ 生成器自那时起抛 `missing from translation table` 静默失效;已按现值回填 `v42_table_3.mjs` 并在 AGENTS #1 加「禁直写生成物」红线。生成后 diff 核对 = 仅 6 键新增,零丢失 |
| **T4 `ModevValueDialog`** | ✅ | `ModevDialogs.kt` 新增只读值对话框(等宽全量值 + 复制 + 关闭三锚点,零输入、无 icon);`ModevValueDialogTest` 3 例(组合层 2 例行为 + 组件面禁 TextField 源码扫描) | T4/T5 顺序按计划走 T5→T4,`cancel` 过渡形态未出现(T5 Step 6 因此为 no-op,已核实) |
| **T6 令牌菜单化** | ✅ 完成(设备轮已过) | `TokenCard` trailing 三内联按钮 → **唯一 `MoreVert` 按钮 + `DropdownMenu` 三项**(查看/重命名/删除,删除整项 error 色);行体 `clickable` 与 `visibleIds` 显隐态整体删除;三弹层接线(`ModevValueDialog` / `ModevInputSheet` / `ModevConfirmDialog`);`TokenAction` 私有件删除;import 清扫(`clickable`/`ContentCopy`/`VisibilityOff`/`width`/`FontFamily`/`ImageVector`);词汇四语收口 `token_revoke*`→`token_delete*` 并删 `token_show`/`token_hide`(全仓 grep 零残留);新建 `McpTokensScreenTest` 5 例(直挂屏 + `ModevApp.container.mcpConfig` 契约类型) | 设备 5 例未跑(无模拟器);`performTextClearance` 本库无先例 → 改「种子空 label 令牌」规避清文本,已记 AGENTS |
| **T7 契约与门禁收口** | ✅(设备/提交除外) | AGENTS.md:词表「令牌管理」「语言」两行重写、#1(ja/ko 生成物红线)、#13(行容器契约)、#20(时间/徽章入身份行 + 尾栏禁用)、#25(shapes 半径 + `ColorSwatchGap` + 间隙≥半径)、#26(改名走方法层契约)、#27(`ModevValueDialog` 入唯一出口)、工具链坑 2 条新增、lint 基线三口径统一为实测 43;头部署名追加 V46 | lint 基线存在 40/41/43 三种历史口径 → 以本轮实测统一并写明来源,未静默迁就任一数字 |

### 尚未完成(需环境或授权,禁止谎报为已完成)

1. **提交(7 项)**:未执行。**原因** = 工作区另有 364 个未提交文件(他人/他轮 V42–V45 在途改动),
   而 V46 的三个目标文件本身就在其中,按文件粒度 `git add` 会把在途改动一并收入;
   且本仓规范只允许在明确授权下提交。**待用户裁定**:V45 与 V46 合并一笔、或先落 V45 基线再单独提交 V46。
2. **`GlobalTypeRecordsTest#globalList_...` 的隔离复跑**:末次确认时雷电 adb 进入
   `offline`(轻恢复 `kill-server`/`wait-for-device` 无效),未再单独跑一轮。
   定性证据见下节;若需闭合,重启 VM 后单类复跑即可。

### 设备轮实测(2026-09-22,雷电 emulator-5554 / Android 14 / 单序列)

前置处置:`adb devices` 初始为空 → 按 AGENTS 恢复手册 `dnconsole quit --index 0` +
`dnplayer -index 0` 冷重启;插桩全程 `unset ANDROID_ADB_SERVER_PORT`(回默认 server,
杜绝 5038 双序列踩踏),并断言序列数 == 1。

**第一轮(锚点修复前)19 例**:RecordRowLayoutTest 8 + ColorSheetDeviceTest 5 +
McpTokensScreenTest 5 → **19/19 绿**。

**发现并修复一条 V46 自身引入的真回归**:容器化后 `record-row` 锚点落在外层 Box,而
M3 1.4.0 `ListItem` **自身才是收文本的 mergeDescendants 节点** ⇒ 三个记录列表测试类的
`hasTestTag("record-row") and hasText(...)` 标准形态整体归零(实测 tag=1 / text=1 /
**both=0**)。处置 = 锚点/手势/合并语义下移回 `ListItem` 节点,`ROW_TEST_TAG` 由组件自持,
宿主只传间距 modifier;复测 `both=1` ✅。该铁律已写入 AGENTS #13。

**第二轮(锚点修复后,七类合跑 31 例)**:

| 类 | 结果 |
|----|------|
| `RecordRowLayoutTest` | 9/9 绿(含新增几何锁 + 结构锁) |
| `ColorSheetDeviceTest` | 5/5 绿(含新增横纵等步长几何锁) |
| `McpTokensScreenTest` | 5/5 绿(菜单三项 / 行内掩码 / 查看明文+复制关窗 / 原地改名 / 删除确认) |
| `TimelineListTest` | 4/4 绿 |
| `RecordListScrollPreservationTest` | 1/1 绿 |
| `MonitorListScreensTest` | 3/5 —— 2 红为**既有缺陷**(归因见下) |
| `GlobalTypeRecordsTest` | 1/2 —— 1 红为**跨类状态耦合**(归因见下) |

**三条红的逐条归因(不甩锅)**:

- `MonitorListScreensTest#algorithmList_kindFilterAndSearchFilter`、
  `#inApp_longPressOnClearTypeSlot_doesNotExpandSearch`:卡在「点折叠搜索条 → 全屏
  SearchView Dialog 未出现」及其下游文案显示性。代码归属 = `MonitorRecordsScreen.kt`
  (工作区 130 行未提交 V45 改动)+ `SearchInputComponents.kt`,V46 未触碰。
  **历史实锤**:两条在 `docs/test-reports/v45-20260920-{065416,073008,075347}/ui.log`
  三轮设备报告里**均已 FAILED**(早于 V46),且未登记 `skip-allowlist.txt`
  = V45 在途遗留的开放缺陷,非本轮回归。
- `GlobalTypeRecordsTest#globalList_reusesPipeline_crossPackageAggregation`(断言
  「全局列表行内应携带应用名上下文」):**两类合跑绿、七类合跑红**,生产代码两者完全
  相同 ⇒ 判为跨类状态耦合(应用列表偏好/目录缓存被前序类改写,AGENTS #7 同型);
  V45 报告 `v45-20260920-073008` 中同一用例亦以另一条断言(种子读回 expected 2 but was 1)红过。
  已核残留风险:V46 行使高增加(身份行 + 6dp 留白)理论上减少同屏行数,但同 run 内
  该行 `rowCount("SHA-256")` 为绿,且合并节点只要被组合即含标签文本 ⇒ 不判为版式回归。
  建议后续把 `13-reset-device-state.ps1` 纳入类间前置。

**目视复核(设备真截屏,`UiDevice.takeScreenshot` 1440×2560)**:证据在
`D:\Temp\v46-rows.png` / `v46-color-sheet.png` / `v46-token-menu.png`
(仓库不跟踪图片,故不入版本库)。三张图各自确认:

1. 记录行 = **两张独立圆角卡片**,间隙清晰不粘连;时间 `08:13:20` 与「加密」徽章
   只在首行右侧;`key: 112233445566778899aabbcc` 与长路径
   `/data/user/0/com…e-cache-v3.json` **用满整行宽度**后才省略;类别词元「加解密」蓝、
   「文件读取」olive 各自成行;「应用 Test Target」语境行在位。
2. 主题色板 sheet:色点行与行**上下间距与左右间距一致**,不再相切;
   壁纸行 / 分隔线 / 「自定义强调色」标题节奏正常。
3. 令牌页:行内只剩 `⋮`;菜单恰三项「查看 / 重命名 / 删除」带图标,「删除」整项 error 红;
   行体显示掩码 `d1af••••••2692 · 9月22日` 与未命名回落「令牌 d1afd3」。

探针工程注记:一次性截图探针两次自我打脸 —— ① `getExternalFilesDir` 在本镜像返回 null
导致「绿而无文件」,遂加「必须真的落盘 >10KB」硬断言;② `targetContext` 指向被测包
`com.lioisme.modev` 而非测试包,取件路径须按前者。探针类已删除,设备侧残留文件已清。

### 波及文件(本轮实际改动 23 个)

主源:`ui/components/RecordRow.kt`、`ui/records/RecordTimelineList.kt`、
`ui/settings/SettingsScreen.kt`、`ui/settings/McpTokensScreen.kt`、
`ui/components/ModevDialogs.kt`、`domain/server/ServerConfigService.kt`、
`data/repo/SettingsRepository.kt`、`ui/settings/McpTokensViewModel.kt`;
资源:`values/strings.xml`、`values-zh/strings.xml`、`values-ja/strings.xml`(生成)、
`values-ko/strings.xml`(生成);脚本:`scripts/v42_table_2.mjs`、`scripts/v42_table_3.mjs`;
测试:新增 `RecordRowContainerContractTest`、`ModevValueDialogTest`、`McpTokensScreenTest`,
扩充 `RecordRowLayoutTest`、`ColorSheetDeviceTest`、`SettingsRepositoryMcpTokenTest`、
`UiMcpCapabilityParityTest`;文档:本文、`AGENTS.md`。
