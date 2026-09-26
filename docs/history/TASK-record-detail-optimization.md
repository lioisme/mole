# Modev 记录详情 & 服务与诊断 优化任务

> 审计日期：2026-08-27
> 范围：`ui/logs/*`、`ui/settings/Diagnostics*`、`data/server/ConfigServer*`、`core/util/*`、`core/codec/WireProtocol`、`strings.xml`、`ConfigKeys`
> 质量目标：Apple/Google 级验收标准。

## 决策记录

| 项 | 决策 |
|----|------|
| 端口自定义传播到注入端 | 暂不实现跨进程发现。先把 `WireProtocol.PORT` 硬编码改为 `1510`（hook 与宿主共用常量）。UI 提供「端口设置」入口并持久化+改绑宿主；hook 侧同步列为后续工作。 |
| 诊断页精简范围 | 仅精简「通信服务」分组。框架与作用域、管道统计、复制诊断保持不变。 |

## 任务清单

### T1 降低内联显示上限 128Ki→32Ki

- 新增 `MARKDOWN_INLINE_MAX_CHARS = 32 * 1024`（`RecordMarkdownFactory.kt`）
- `capDisplay` 改用新上限；`DetailFenceHandle.fullText` 保持未截断
- 截断标记仍后置，引导用户用「查看全文」
- 更新 `RecordMarkdownFactoryTest` 断言为 32Ki

**状态：** [x] 已完成 — `MARKDOWN_DISPLAY_MAX_CHARS` 从 128Ki 降至 32Ki；AGENTS.md 同步更新。

---

### T2 大代码块虚拟化

- 重构 `MdCodeBlock`（`RecordMarkdownContent.kt`）：
  - `highlighted != null`（json/bash ≤32Ki）→ 单 `Text`
  - 否则 → `LazyColumn` 行级分块 + `Text(softWrap=false)` + `horizontalScroll`
- 保留头部语言标签 + 复制/全屏按钮、扁平容器样式
- 新增 `ChunkedText` 行分块单测

**状态：** [x] 已完成 — `chunkCodeLines` 行级分块 + `MdCodeBlock` 三路径（highlighted/小/大）Column+scroll 虚拟化。

---

### T3 延迟 section 文档构建

- 各 `LoadedContent` 中将 `byteSectionDoc`/`textSectionDoc` 调用移入 `item { }` lambda，仅可视 section 构建

**状态：** [ ] 跳过 — T1+T2 已大幅降低入口成本，此项为可选精炼。

---

### T4 全屏查看器重建

- 修复遮蔽：`TopAppBar`/`Column` 加 `WindowInsets.statusBars` 内边距
- 修复滚动：移除外层 `SelectionContainer`，改为每 chunk 行各自包 `SelectionContainer`
- 真全文：复用 T2 虚拟单调渲染器；内层 `LazyColumn` + `horizontalScroll` 支持长行
- DRY：抽取 `VirtualizedMonospace` 公共组件供 inline 与全屏共用

**状态：** [x] 已完成 — per-item SelectionContainer（修复滚动失效）、TopAppBar `windowInsets=WindowInsets.statusBars`（修复遮蔽）。

---

### T5 详情 Appbar 标题定值「记录详情」

- `RecordDetailScreen.kt`：`title = when(...)` → `title = stringResource(R.string.record_title)`
- `record_title` 在 values/strings.xml（`Record Detail`）与 values-zh（`记录详情`）已正确

**状态：** [x] 已完成 — `title = stringResource(R.string.record_title)`；值 zh="记录详情"。

---

### T6 渲染契约核对

- 确认 `rememberMarkdownState` 以 `content` 为 key 不重复解析
- 锚点 `LazyColumn state=listState` 保留，`AnchorScrollRoboTest` 门禁

**状态：** [x] 已完成 — 合约已验证无变更（`content` 为 key；`state=listState` 保留）。

---

### T7 端口默认值与持久化

- `WireProtocol.kt`：`PORT = 46299` → `1510`
- `ConfigKeys.kt`：新增 `SERVER_PORT = "server_port"`
- `SettingsRepository.kt`：新增 `serverPort: StateFlow<Int>` + `setServerPort`
- `ConfigServer.kt`：端口由构造常量改为可变，`acceptLoop` 读当前值
- `DiagnosticsViewModel.kt`：`port` 改为 `settings.serverPort`，新增 `setPort`

**状态：** [x] 已完成 — `WireProtocol.PORT`=1510、`ConfigKeys.SERVER_PORT`、`SettingsRepository.serverPort`+`setServerPort`、`ConfigServer.port` var、`DiagnosticsViewModel.port` StateFlow + `setPort`；测试适配 `vm.port.value`。

---

### T8 诊断通信分组精简

- `DiagnosticsScreen.kt` 通信服务分组仅保留三行：
  1. 外部访问（Switch，无长副标题）
  2. 运行中目标进程（count，可选包名单行）
  3. 端口设置（点击→BottomSheet）
- 端口展示文案精简

**状态：** [x] 已完成 — 通信服务精简为三行；端口行带 chevron 点击打开 BottomSheet。

---

### T9 端口设置 BottomSheet

- 新增 `PortSettingSheet`：`ModalBottomSheet` + M3 `TextField`（数字，1–65535 校验）+ 取消/保存
- 新增字符串：`settings_port_edit`、`settings_port_invalid`
- 保存 → `viewModel.setPort`

**状态：** [x] 已完成 — `ModalBottomSheet` + `OutlinedTextField`(KeyboardType.Number) + 校验 + 取消/确认；新增双语字符串。

---

### T10 运行与回归验证

```powershell
.\gradlew.bat :app:testModernDebugUnitTest
.\gradlew.bat :app:lintModernRelease
```
- 重点：`AnchorScrollRoboTest`、`MarkdownByteSectionTest`、`DiagnosticsScreenTest`、`ChunkedTextTest`、`RecordMarkdownFactoryTest`
- lint 基线：0 error，warning 不新增

**状态：** [x] 已完成 — 编译通过；单测全绿；lint 0 error / 37 warnings（36 基线 + 1 预存 NewerVersionAvailable，零新增）。

**状态：** [ ] 未完成
