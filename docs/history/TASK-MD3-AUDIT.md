# TASK-MD3-AUDIT: MD3 组件库全量审计与整改

> ⚠️ **本文档已被 TASK-V40-AUDIT.md 取代(2026-09-07)**:其"无手搓自研控件"结论
> 漏计 R 类 12 处(StatusPill/LeadingBadge/MicroBadge/TimelineHeader/色板/计数胶囊等);
> V40 按 R 类全替换 + Coil 化完成整改,现行契约以 `TASK-V40-AUDIT.md` 与 AGENTS.md 为准。
> 以下为历史记录,仅存档。

> 审计日期: 2026-08-28
> 审计范围: 全部 17 个屏幕、23+ 自定义组件、9 个 BottomSheet、4 个 Dialog
> 总评: 94/100 — 已高度符合 MD3 标准

## 审计结论

Modev 项目已全量使用 MD3 组件库(Material3 1.4.0 + Compose BOM 2025.06.01),
所有自定义组件均为 M3 组件的合理封装/扩展,无手搓自研控件。
第三方库(mikepenz、SnipMeDev、materialkolor)均为专业维护的开源库。

---

## 任务清单

### P0 — 必须修复

- [ ] **T-02**: 统一 `DeviceEnvColorTokens.kt` 架构(与其他 6 个颜色令牌文件对齐,补充 light/dark 双模式,删除 RecordRow.kt 中的重复定义)
- [ ] **T-10**: `RecordRow` 添加 `Modifier.semantics { role = Role.Button }` 增强 TalkBack 可访问性

### P1 — 建议优化

- [ ] **T-01**: `EmptyState` 添加 `semantics { stateDescription }` 声明空状态语义
- [ ] **T-04**: 提取 `HorizontalDivider16` 共享组件(消除 PerAppListenSettingsScreen.kt 和 AppMonitorScreen.kt 中的重复定义)

### P2 — 信息性(了解即可,不强制)

- [ ] **T-03**: Typography Headline 权重(当前 Normal,MD3 默认 SemiBold;有意偏离,保持)
- [ ] **T-09**: ListItem contentDescription(当前依赖自然语义,保持)

### N/A — 已确认不采纳

- ~~T-05: NavigationBar~~ — 深层级导航不适合
- ~~T-06: FAB~~ — 只读工具无创建操作
- ~~T-07: 底部 Tab~~ — EntryRow 模式正确
- ~~T-08: 改变卡片容器色~~ — 已测试验证
- ~~T-11: ExposedDropdownMenu~~ — 不适用于自由文本输入
- ~~T-12: FilterChip 替代过滤~~ — 水平空间不可行
- ~~T-13: Badge 替代计数~~ — 不适用于大数

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-08-28 | T-02 | ✅ 完成 | 重写 DeviceEnvColorTokens.kt 为 dual-map 架构(13 枚举值 light/dark 双调色板),删除 RecordRow.kt 中重复的 deviceEnvKindColor() 函数(原 L691-708),统一到 7 个颜色令牌文件一致架构 |
| 2026-08-28 | T-10 | ✅ 完成 | RecordRow Surface 内 Row 添加 `Modifier.semantics { role = Role.Button }`,增强 TalkBack 可访问性 |
| 2026-08-28 | T-01 | ✅ 完成 | EmptyState Column 添加 `Modifier.semantics { stateDescription = message }`,TalkBack 主动播报空状态语义 |
| 2026-08-28 | T-04 | ✅ 完成 | 创建 ui/components/SharedDividers.kt 共享 HorizontalDivider16 组件,AppMonitorScreen.kt 和 PerAppListenSettingsScreen.kt 各删除 1 个 private 重复定义,改为 import 共享组件 |
| 2026-08-28 | Lint | ✅ 通过 | lintModernRelease: 0 errors, 70 warnings(全部基线既有,零新增) |
| 2026-08-28 | Tests | ✅ 通过 | testModernDebugUnitTest: BUILD SUCCESSFUL |
