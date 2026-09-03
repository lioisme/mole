# V34 监听配置一等公民化 + 入口重构

> 审计日期: 2026-08-28 | 完成日期: 2026-08-28 | 目标: Apple/Google 级验收标准

## 背景

V33 新增 3 种 RecordType（UI交互 / 系统信息 / 进程控制），后端完备，但前端配置 UI 未补齐。
「监听设置」入口命名过窄（仅覆盖监听，不含增强功能），需通用化重命名并上移。

## 目标

| # | 目标 | 验收标准 |
|---|------|----------|
| G1 | V33 三类功能在 PerAppListenSettingsScreen 一等公民化 | master switch + kind 子开关 + switchLabelRes 映射 |
| G2 | 入口通用化重命名并上移 | 从「监听设置」改为「功能配置」，位于 AppMonitorScreen 最上方 |
| G3 | 所有 override 计数/重置包含 V33 字段 | listenSummary + reset tally 完整 |
| G4 | 测试门禁通过 | 531 单测 + lint 0 error + 字符串 parity |

---

## 任务分解与执行状态

### T1 — 字符串资源补齐与对齐 `values/strings.xml` + `values-zh/strings.xml`
- [x] 新增 `kind_ui_DIALOG_SHOW` / `kind_ui_ACTIVITY_START` / `kind_ui_ACTIVITY_RESUME`（en + zh）
- [x] 重命名 `listen_settings_entry` → "Feature Configuration" / "功能配置"
- [x] 删除旧键 `kind_ui_DIALOG_LOCATION` / `kind_ui_ACTIVITY_TRANSITION`（零引用确认后删除）
- [x] 删除死键 `listen_row_dialogLocation` / `listen_row_dialogKeyword` / `listen_row_widgetText` / `listen_row_activityTransition`（仅定义无引用）
- [x] 同步更新 `detail_empty_hint` / `danger_reset_listen_msg` 文案

### T2 — PerAppListenSettingsViewModel 补齐 3 个 setter
- [x] `setUiMaster(value: Boolean)` — V33 UI交互总开关
- [x] `setSystemMaster(value: Boolean)` — V33 系统信息总开关
- [x] `setProcessControlMaster(value: Boolean)` — V33 进程控制总开关

### T3 — PerAppListenSettingsScreen 补齐 3 个 master switch 行
- [x] `listen-row-master-ui` — UI交互监听总开关
- [x] `listen-row-master-system` — 系统信息监听总开关
- [x] `listen-row-master-process` — 进程控制监听总开关

### T4 — PerAppListenSettingsScreen 补齐 3 个 SettingsSection
- [x] UI交互 5 类别 section（`RecordType.UI_INTERACTION`）
- [x] 系统信息 3 类别 section（`RecordType.SYSTEM`）
- [x] 进程控制 3 类别 section（`RecordType.PROCESS_CONTROL`）

### T5 — switchLabelRes() 补齐 11 个 V33 映射
- [x] UiInteractionKind 5 个映射（DIALOG_SHOW / DIALOG_KEYWORD / WIDGET_TEXT / ACTIVITY_START / ACTIVITY_RESUME）
- [x] SystemKind 3 个映射（LOGCAT / APP_INFO / SIGNATURE）
- [x] ProcessKind 3 个映射（EXIT_INTERCEPT / CRASH_PREVENT / SCREEN_RECORD）

### T6 — listenSummary() 补齐 V33 override 计数
- [x] `override.uiListen` / `override.systemListen` / `override.processControlListen`

### T7 — PerAppListenSettings reset tally 补齐 V33 override 计数
- [x] `override.uiListen.count()` / `override.systemListen.count()` / `override.processControlListen.count()`

### T8 — AppMonitorScreen 入口通用化重命名 + 上移
- [x] 入口文案从「监听设置」改为「功能配置」（`listen_settings_entry` 统一重命名）
- [x] 入口从「监听类别」section 内移出，独立为「功能配置」section（StatsCard 之后、监听类别之前）

### T9 — 残留旧字符串键清理
- [x] `kind_ui_DIALOG_LOCATION` / `kind_ui_ACTIVITY_TRANSITION` — RecordRow.kt 引用已更新为新键，旧键已删除
- [x] `listen_row_dialogLocation` / `listen_row_activityTransition` — 确认零引用后删除
- [x] 更新 `RecordRow.kt:uiInteractionKindLabelRes()` 使用新键

### 测试验证
- [x] `.\gradlew.bat :app:testModernDebugUnitTest` — BUILD SUCCESSFUL
- [x] `.\gradlew.bat :app:lintModernRelease` — BUILD SUCCESSFUL (0 error)
- [x] StringResourceParityTest — en/zh 键数一致（随单测通过）
- [x] HardcodedStringConventionTest — ui 包零中文（随单测通过）

---

## 完成记录

| 时间 | 任务 | 备注 |
|------|------|------|
| 2026-08-28 | T1 字符串资源 | 新增 3 kind 键 + 重命名入口 + 清理 6 死键 + 同步 4 处引用 |
| 2026-08-28 | T2 ViewModel setter | 3 个 setXxxMaster 方法 |
| 2026-08-28 | T3 master switch 行 | 3 个 ListenRow + testTag |
| 2026-08-28 | T4 SettingsSection | 3 个 kind 子开关 section（11 kind） |
| 2026-08-28 | T5 switchLabelRes | 11 个枚举→资源映射 |
| 2026-08-28 | T6 listenSummary | +3 master override 计数 |
| 2026-08-28 | T7 reset tally | +3 master override.count() |
| 2026-08-28 | T8 入口重构 | 独立 section + 文案「功能配置」|
| 2026-08-28 | T9 清理 | 旧键删除 + RecordRow 引用更新 |
| 2026-08-28 | TODO 修复 | RecordDetailScreen 5 个 toJson() 桩→工作实现（Network/DeviceEnv/UiInteraction/System/Process） |
| 2026-08-28 | 死资源清理 | 删除 39 个零引用字符串键（en+zh 同步），lint warnings 82→43 |
| 2026-08-28 | 测试验证 | 531 单测 pass + lint 0 error + 43 warnings（全为预存版本锁/PrivateApi） |

## 变更文件清单

| 文件 | 变更 |
|------|------|
| `app/src/main/res/values/strings.xml` | +3 kind 键, 重命名 3 键, 删除 45 死键（含 39 死资源清理） |
| `app/src/main/res/values-zh/strings.xml` | 同步中文 |
| `app/src/main/java/.../apps/PerAppListenSettingsScreen.kt` | +3 setter, +3 master row, +3 section, +11 label 映射, +3 tally |
| `app/src/main/java/.../apps/AppMonitorScreen.kt` | 入口上移 + listenSummary +3 计数 |
| `app/src/main/java/.../components/RecordRow.kt` | 3 处 kind label 引用更新 |
| `app/src/main/java/.../ui/logs/RecordDetailScreen.kt` | 5 个 toJson() 桩→工作实现 |
