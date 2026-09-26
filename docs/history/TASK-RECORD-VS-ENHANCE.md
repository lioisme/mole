# TASK-RECORD-VS-ENHANCE: 记录监听与增强功能分离 + 设备环境"未知错误"修复

> 审计日期: 2026-08-29
> 版本基准: V35 (momo 增强完成后)
> 目标: 代码质量与架构设计达到 Apple/Google 大厂验收标准
> 标准: 编译零 error、lint 零新增 warning、测试覆盖关键路径、信息架构清晰

---

## 进度总览

| Phase | 内容 | 优先级 | 状态 |
|-------|------|--------|------|
| 1 | P0 修复:设备环境"未知错误"子功能标签缺失 | 紧急 | ✅ |
| 2 | P1 架构:记录监听与增强功能分离(双 Tab) | 高 | ⬜ (待排期) |
| 3 | P2 设计:信息架构优化(折叠/搜索/风险提示) | 中 | ⬜ (待排期) |
| 4 | P3 语义:命名与注释修正 | 低 | ✅ |
| 5 | 增强功能能力补全(弹窗拦截/退出拦截增强) | 高 | ⬜ (待排期) |
| 6 | 质量门禁:编译+lint+单测 | 必须 | ✅ |

---

## Phase 1: P0 修复 — 设备环境"未知错误" ✅

### T1.1 [P0] switchLabelRes() 补全 9 个 DeviceEnvKind 映射 ✅

**文件**: `ui/apps/PerAppListenSettingsScreen.kt:803-816`
**问题**: V35 momo 增强新增的 9 个 DeviceEnvKind 枚举值未注册到 switchLabelRes() 映射,导致设置页渲染时 fallthrough 到 `else -> R.string.unknown_error`
**修复**: 补全 EMULATOR_TELEPHONY/EMULATOR_CPU/EMULATOR_BATTERY/SYSTEM_PROPERTIES/PROC_STATUS/PROC_MOUNT_INFO/PROC_CGROUP/PROC_NET 共 9 个映射;同步修正注释"13 类别"→"21 类别"
**状态**: [x] 已完成 — 2026-08-29 — 编译通过,0 新增 warning

### T1.2 [P0] RecordRow.kt 穷举 when 分支验证 ✅

**文件**: `ui/components/RecordRow.kt`
**结果**: deviceEnvKindLabelRes(21 分支)和 deviceEnvOperationLabelRes(65 分支)均为穷举 when,无遗漏
**状态**: [x] 已完成 — 2026-08-29 — 无需修改

### T1.3 [P0] KindIcons.kt 图标映射验证 ✅

**文件**: `ui/components/KindIcons.kt:134-148`
**结果**: deviceEnv() 函数覆盖全部 21 个 DeviceEnvKind 枚举值,穷举 when,无遗漏
**状态**: [x] 已完成 — 2026-08-29 — 无需修改

### T1.4 [P0] DeviceEnvColorTokens.kt 色板补全 ✅ (审计发现的额外严重 bug)

**文件**: `ui/theme/DeviceEnvColorTokens.kt`
**问题**: 两个调色板(deviceEnvLightPalette/deviceEnvDarkPalette)仅含 13 个条目,缺少 V35 新增的 8 个 kind(EMULATOR_TELEPHONY/EMULATOR_CPU/EMULATOR_BATTERY/SYSTEM_PROPERTIES/PROC_STATUS/PROC_MOUNT_INFO/PROC_CGROUP/PROC_NET)。访问器 `getValue(kind)` 对缺失 key 抛出 `NoSuchElementException` → 运行时崩溃
**修复**: 补全 8 个 kind 的颜色条目;新增色组注释(Root 红/Xposed 橙/Emulator 紫/SysProp 青/Proc 蓝灰/Detect 灰)
**状态**: [x] 已完成 — 2026-08-29 — 编译通过,0 新增 warning

### T1.5 [P1] 单元测试覆盖 ✅

**文件**: `core/model/DeviceEnvKindTest.kt`, `core/model/CategoryTaxonomyTest.kt`
**新增测试**:
- DeviceEnvKindTest: emulator kinds V35 覆盖(7 项)、sysprop kind、proc kinds(4 项)默认关断言
- CategoryTaxonomyTest: 全类型 kind id 枚举合法性穷举断言、DeviceEnv 21 条目计数断言
**状态**: [x] 已完成 — 2026-08-29 — 全量测试通过

---

## Phase 4: P3 语义修正 ✅

### T4.1 [P3] kind_process_* 文案修正 ✅

**文件**: `res/values/strings.xml`, `res/values-zh/strings.xml`
**修复**:
- EN: "Exit Detect"→"Exit Intercept"、"Crash Detect"→"Crash Prevent"、"Screen Record Detect"→"Screen Record Bypass"
- ZH: "退出检测"→"退出拦截"、"崩溃检测"→"崩溃预防"、"录屏检测"→"录屏解除"
**状态**: [x] 已完成 — 2026-08-29 — 编译通过

### T4.2 [P3] DeviceEnvKind.kt 注释修正 ✅

**文件**: `core/model/DeviceEnvKind.kt`
**修复**: 注释从"干预类默认开"修正为"干预类默认关(安全优先,需显式启用)";注释从"四大子域"扩展为"六大子域";版本标注 V35 momo 增强扩展至 21 项
**状态**: [x] 已完成 — 2026-08-29

### T4.3 [P3] RecordType.kt 注释修正 ✅

**文件**: `core/model/RecordType.kt`
**修复**: DEVICE_ENV 注释从"Root 隐藏/Xposed 隐藏/模拟器隐藏/检测监控"修正为"干预型:Root/Xposed/模拟器隐藏 + 观测型:检测监控"
**状态**: [x] 已完成 — 2026-08-29

---

## Phase 6: 质量门禁 ✅

### T6.1 编译验证 ✅

- **操作**: `.\gradlew.bat :app:assembleModernDebug`
- **结果**: BUILD SUCCESSFUL in 11s, 41 actionable tasks
- **状态**: [x] 已完成

### T6.2 Lint 检查 ✅

- **操作**: `.\gradlew.bat :app:lintModernRelease`
- **结果**: BUILD SUCCESSFUL, 0 error, 48 warning
- **新增 warning**: 0 (48 个 warning 均为预存的 PrivateApi 等,Xposed 模块合理存在)
- **状态**: [x] 已完成

### T6.3 全量单元测试 ✅

- **操作**: `.\gradlew.bat :app:testModernDebugUnitTest`
- **结果**: BUILD SUCCESSFUL, 全部测试通过
- **新增测试**: 5 个(DeviceEnvKindTest × 3 + CategoryTaxonomyTest × 2)
- **状态**: [x] 已完成

---

## 变更文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `ui/apps/PerAppListenSettingsScreen.kt` | 修改 | T1.1: 补全 9 个 switchLabelRes 映射 + 注释修正 |
| `ui/theme/DeviceEnvColorTokens.kt` | 修改 | T1.4: 补全 8 个颜色条目(修复运行时崩溃) |
| `core/model/DeviceEnvKind.kt` | 修改 | T4.2: 注释修正(默认值/子域数/V35 标注) |
| `core/model/RecordType.kt` | 修改 | T4.3: DEVICE_ENV 注释区分干预/观测 |
| `res/values/strings.xml` | 修改 | T4.1: kind_process_* 文案修正(3 条) |
| `res/values-zh/strings.xml` | 修改 | T4.1: kind_process_* 中文文案修正(3 条) |
| `core/model/DeviceEnvKindTest.kt` | 修改 | T1.5: +3 个 V35 覆盖断言 |
| `core/model/CategoryTaxonomyTest.kt` | 修改 | T1.5: +2 个穷举合法性断言 |

---

## 验收标准

| # | 标准 | 状态 |
|---|------|------|
| 1 | 设备环境 21 个子功能标签全部正确显示(零"未知错误") | ✅ |
| 2 | switchLabelRes() 覆盖全部 60+ 个 kind ID | ✅ |
| 3 | RecordRow.kt/KindIcons.kt 穷举分支完整 | ✅ |
| 4 | DeviceEnvColorTokens.kt 色板覆盖全部 21 个 kind | ✅ |
| 5 | i18n 双语齐备(en + zh) | ✅ |
| 6 | lint 零新增 warning | ✅ |
| 7 | 全量单测通过 | ✅ |

---

## 待排期任务(P2 架构/P2 设计/P5 能力补全)

以下任务需单独排期执行(涉及大量 UI 重构和新 Hooker 开发):

1. **P1 架构分离**: AppMonitorScreen 双 Tab(记录监听/增强功能) — 预估 18.5h
2. **P2 设计优化**: 设置页分区折叠/搜索/风险提示 — 预估 10h
3. **P5 能力补全**: 弹窗拦截增强(dismiss/cancel/PopupWindow/DialogFragment) — 预估 15h

详见审计文档中的 Phase 2/3/5 任务清单。
