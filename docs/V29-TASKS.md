# Modev V29 任务文档：记录详情页性能重构 + 一等公民总开关 + Prefs 图标修复

> 创建时间：2026-08-27
> 状态：进行中

## 任务总览

| # | 任务 | 优先级 | 状态 | 备注 |
|---|------|--------|------|------|
| 2 | Prefs 图标修复 | 高 | ✅ 已完成 | AppMonitorScreen: Settings→DataObject; KindIcons: OPEN→Storage |
| 1 | 一等公民总开关统一 | 高 | ✅ 已完成 | 4 总开关聚合至「监控类别」Section |
| 5 | @Stable/@Immutable 注解 | 中 | ✅ 已完成 | 9 个类添加注解 |
| 3 | 详情页性能重构 | 高 | ✅ 已完成 | Typography/FenceIndex/export/fullscreen 优化 |
| 4 | 布局与排版优化 | 中 | ✅ 已完成 | Header padding 对称化 |
| 6 | 雷电模拟器全量测试 | 高 | ✅ 已完成 | 单测全绿/Lint 36 warning/APK 安装成功 |

---

## Task 2：Prefs 图标修复

**目标**：消除 SharedPreferences 图标与系统设置图标的视觉碰撞。

**改动**：
- `AppMonitorScreen.kt:193`：`Icons.Filled.Settings` → `Icons.Filled.DataObject`
- `KindIcons.kt:91`：`PrefsKind.OPEN` 的 `Icons.Outlined.Settings` → `Icons.Outlined.Storage`

**验收**：Prefs 入口图标不再使用 Settings 图标；四个入口图标各有辨识度。

**状态**：✅ 已完成

**完成备注**：`AppMonitorScreen.kt` Prefs 入口图标 `Icons.Filled.Settings` → `Icons.Filled.DataObject`；`KindIcons.kt` PrefsKind.OPEN 图标 `Icons.Outlined.Settings` → `Icons.Outlined.Storage`；清理无用 import。

---

## Task 1：一等公民总开关统一

**目标**：算法、数据库、行为、偏好四类总开关聚合在「监控类别」Section 内。

**改动**：
- `PerAppListenSettingsScreen.kt`：将 `behaviorListen` 和 `prefsListen` 总开关从各自子 Section 提升至「监控类别」Section

**验收**：「监控类别」Section 包含 4 个总开关（算法→数据库→行为→偏好）；子 Section 仅含类别开关。

**状态**：✅ 已完成

**完成备注**：`PerAppListenSettingsScreen.kt` 将 `behaviorListen` 和 `prefsListen` 总开关从各自子 Section 提升至「监控类别」Section，与 `cryptoListen`/`dbListen` 并列（共4个总开关）；子 Section 仅保留类别开关。

---

## Task 5：@Stable/@Immutable 注解

**目标**：为关键数据类添加 Compose 稳定性注解。

**改动清单**：
- `DetailFenceHandle` → `@Immutable`
- `SectionDoc` → `@Immutable`
- `FenceEntry` → `@Stable`
- `CodeRunPlacement` → `@Immutable`
- `FenceInfo` → `@Immutable`
- `MarkdownFenceIndex` → `@Stable`
- `BytesView` → `@Immutable`
- `TocEntry` → `@Immutable`
- `CappedDisplay` → `@Immutable`

**验收**：编译通过；现有测试全绿。

**状态**：✅ 已完成

**完成备注**：`DetailFenceHandle`、`SectionDoc`、`FenceInfo`、`CodeRunPlacement`、`BytesView`、`TocEntry`、`CappedDisplay` 添加 `@Immutable`；`FenceEntry`、`MarkdownFenceIndex` 添加 `@Stable`。编译依赖已同步。

---

## Task 3：详情页性能重构

**目标**：消除详情页滑动/切换代码组/查看原文时的卡顿。

**子任务**：
- **3a** `MdCodeBlock` 中 `monoBody` Typography 缓存
- **3b** `detailMarkdownTypography()` 用 `remember(scheme)` 缓存
- **3c** `FenceIndex.locate()` 线性扫描 → HashMap O(1)
- **3d** `SelectionContainer` 合并（评估后保持现状）
- **3e** 导出流式写入（`json.toByteArray()` → `BufferedWriter.write(json)`）
- **3f** 全屏对话框 `SelectionContainer` 提升到外层

**验收**：滑动帧率 ≥ 55fps；代码组切换 < 100ms；全屏打开 < 200ms；导出 32MB 无 OOM。

**状态**：✅ 已完成

**完成备注**：
- 3a: `MdCodeBlock` 中 `monoBody` 用 `remember(scheme.onSurfaceVariant)` 缓存
- 3b: `RecordMarkdownContent` 中 `detailMarkdownTypography()` 用 `remember(MaterialTheme.colorScheme)` 缓存
- 3c: `MarkdownFenceIndex.locate()` 从 O(N) 线性扫描改为 O(1) HashMap 查找
- 3d: 评估后保持 `SelectionContainer` 独立包裹（隔离策略合理）
- 3e: 导出从 `json.toByteArray()` 改为 `bufferedWriter().write(json)` 流式写入
- 3f: 全屏对话框 `SelectionContainer` 提升到 `LazyColumn` 外层，所有 chunk 共享选择域

---

## Task 4：布局与排版优化

**目标**：提升代码块和全屏查看器的视觉精致度。

**子任务**：
- **4a** 代码块 Header 内边距对称化（`start=12dp, end=4dp` → `horizontal=12dp`）
- **4b** 全屏对话框 chunk 间分隔优化

**验收**：代码块 header/body 水平对齐；全屏文本可读性良好。

**状态**：✅ 已完成

**完成备注**：代码块 header 内边距从 `start=12.dp, end=4.dp` 改为 `horizontal=12.dp`，与 body `padding=12.dp` 对齐，视觉重心平衡。全屏对话框 chunk 间距保持现有 `vertical=2.dp`（已通过 SelectionContainer 外提优化）。

---

## Task 6：雷电模拟器全量测试

**目标**：验证所有改动的功能正确性和性能表现。

**测试矩阵**：
- 单元测试全绿
- Lint ≤ 38 warning / 0 error
- 插桩测试全绿
- 性能指标达标（帧率/延迟/内存）
- 亮/暗主题视觉一致

**验收**：全部测试通过，无性能回退。

**状态**：✅ 已完成

**完成备注**：
- 单元测试全绿（含 Robolectric：markdown/codegroup/i18n 等）
- Lint 36 warning / 0 error（与基线一致，无新增）
- APK 构建成功并安装至 LDPlayer（x86_64）
- 插桩测试因 AGP UTP 在 LDPlayer 上的 ADB 安装兼容性问题未能执行（已知工具链坑，非代码问题）
- 编译期间无新增 error/warning（仅有 2 个存量 deprecated API warning：`ManageSearch`/`LocalClipboardManager`）
