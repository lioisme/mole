# Task: DeviceEnv 全功能启用崩溃修复

> 创建: 2026-08-28
> 范围: 13 个 DeviceEnv 子功能启用后目标应用崩溃/卡顿修复
> 标准: Apple/Google 级代码质量 — 编译零 error、lint 零新增 warning、测试覆盖关键路径

---

## Phase 1: BUG 修复

### T1.1 [P0] RootFileHooker 路径匹配误杀
- **文件**: `hook/hooks/RootFileHooker.kt`
- **问题**: `matchesRootPath()` 使用 `contains()` 子串匹配，`"/su"` 误杀 `/data/user/0/...`
- **修复**: 移除过短的 `"/su"`; 改为精确匹配 + 前缀分隔符匹配(`startsWith(p+"/")`)
- **状态**: [x] 已完成

### T1.2 [P1] EmulatorBuildHooker 伪造值不一致
- **文件**: `hook/hooks/EmulatorBuildHooker.kt`
- **问题**: MANUFACTURER="Xiaomi" + MODEL="Pixel 7 Pro" 矛盾
- **修复**: 重写 `getRealisticValue()` 为两组一致配置; 未知模拟器返回原值
- **状态**: [x] 已完成

### T1.3 [P1] RootPropertyHooker 误修改合法值
- **文件**: `hook/hooks/RootPropertyHooker.kt`
- **问题**: 无条件替换 `ro.debuggable` 等属性
- **修复**: 新增 `isRootIndicativeValue()` 仅在原始值确实为 root 指示值时干预
- **状态**: [x] 已完成

### T1.4 [P1] XposedStackHooker 安装时守卫
- **文件**: `hook/hooks/XposedStackHooker.kt`
- **问题**: `install()` 中 `if (!config().xposedStack) return` 导致后续启用失效
- **修复**: 移除安装时守卫，回调内添加运行时检查
- **状态**: [x] 已完成

### T1.5 [P1] XposedClassHooker 安装时守卫
- **文件**: `hook/hooks/XposedClassHooker.kt`
- **问题**: 同 T1.4,`install()` 中 `if (!config().xposedClass) return`
- **修复**: 移除安装时守卫(初始方案);后发现 hook `Class.forName` 导致 libxposed 框架 bridge 改变 `ClassNotFoundException` 传播路径 → 目标 App ContentProvider 初始化崩溃
- **最终修复**: 移除 `hookClassForName()` 方法,仅保留 `hookLoadClass()` + `hookFindClass()`
- **状态**: [x] 已完成

### T1.6 [P2] XposedMapsHooker 内存泄漏
- **文件**: `hook/hooks/XposedMapsHooker.kt`
- **问题**: `mapsReaders` 集合无限增长
- **修复**: `MAX_TRACKED_READER_COUNT=64` + 流结束自动清理
- **状态**: [x] 已完成

### T1.7 [P2] DetectionMonitorHooker 双重 Hook
- **文件**: `hook/hooks/DetectionMonitorHooker.kt`
- **问题**: 与干预型 Hooker 双重 hook 同一 API
- **修复**: 6 个 monitor 回调内添加运行时检查
- **状态**: [x] 已完成

---

## Phase 2: 代码质量门禁

### T2.1 编译验证
- **操作**: `.\gradlew.bat :app:assembleModernDebug`
- **结果**: BUILD SUCCESSFUL
- **状态**: [x] 已完成

### T2.2 Lint 检查
- **操作**: `.\gradlew.bat :app:lintModernRelease`
- **结果**: 0 error / 70 warning (与基线一致,零新增)
- **状态**: [x] 已完成

### T2.3 单元测试
- **操作**: `.\gradlew.bat :app:testModernDebugUnitTest`
- **结果**: ALL TESTS PASSED
- **状态**: [x] 已完成

---

## Phase 3: 架构审查与重构

### T3.1 死代码清理
- **问题**: `DetectionMonitorHooker.monitorClassForName()` 从未调用
- **修复**: 移除死代码方法和 `DETECT_KEYWORDS` 列表
- **状态**: [x] 已完成

### T3.2 冗余前缀清理
- **问题**: `XposedClassHooker.XPOSED_PREFIXES` 含冗余条目
  - `"de.robv.android.xposed.core."` 被 `"de.robv.android.xposed."` 覆盖
  - `"de.robv.android.xposed.callbacks."` 同理
- **修复**: 移除冗余前缀(8→6 条)
- **状态**: [x] 已完成

### T3.3 Dead Fallback 清理
- **问题**: `EmulatorBuildHooker.hookBuildField()` 中 `clazz.getMethod(fieldName)` 分支永远失败
  - `Build.FINGERPRINT` 是字段不是方法，`getMethod("FINGERPRINT")` 必定抛异常
- **修复**: 移除死代码分支,添加架构限制注释
- **状态**: [x] 已完成

### T3.4 路径匹配逻辑提取
- **操作**: 将 `RootFileHooker` 路径匹配逻辑提取为 `DeviceEnvPathMatcher` 纯函数对象
- **收益**: 可脱离 Android 框架在 JVM 单测中验证
- **状态**: [x] 已完成

### T3.5 单元测试覆盖
- **操作**: 新增 3 个测试类,29 个测试用例:
  - `DeviceEnvPathMatcherTest`: 8 个用例 — 精确匹配/前缀/分隔符/边界/误杀防护
  - `RootPropertyHookerTest`: 12 个用例 — 全部属性×全部状态组合
  - `EmulatorBuildHookerTest`: 9 个用例 — 指示器检测/设备值一致性/保真度
- **状态**: [x] 已完成

---

## Phase 4: 集成测试

### T4.1 安装验证
- **操作**: 安装 APK 到 LDPlayer, 启动 Modev + 5 个目标应用
- **结果**: 全部正常启动,零崩溃
- **状态**: [x] 已完成

### T4.2 功能验证
- **操作**: 通过 `run-as` 写入全量 DeviceEnv override(JSON)到 SharedPreferences, 重启 Modev 同步 RemotePreferences, 启动暮光工具箱
- **结果**: 暮光工具箱正常启动,Activity 存活,零崩溃
- **根因**: XposedClassHooker hook `Class.forName` → libxposed `HookBridge.invokeOriginalMethod` 改变 `ClassNotFoundException` 传播 → `androidx.startup.InitializationProvider` 异常捕获失效
- **状态**: [x] 已完成

---

## 变更文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `hook/hooks/RootFileHooker.kt` | 修改 | P0 路径匹配修复 + 委托 DeviceEnvPathMatcher |
| `hook/hooks/EmulatorBuildHooker.kt` | 修改 | P1 伪造值一致性 + dead fallback 清理 |
| `hook/hooks/RootPropertyHooker.kt` | 修改 | P1 根特征值校验 + internal 可见性 |
| `hook/hooks/XposedStackHooker.kt` | 修改 | P1 安装守卫移除 |
| `hook/hooks/XposedClassHooker.kt` | 修改 | P1 安装守卫移除 + 冗余前缀清理 + 移除 hookClassForName(崩溃修复) |
| `hook/hooks/XposedMapsHooker.kt` | 修改 | P2 内存泄漏修复 |
| `hook/hooks/DetectionMonitorHooker.kt` | 修改 | P2 双重 Hook 修复 + 死代码清理 |
| `hook/hooks/DeviceEnvPathMatcher.kt` | 新增 | 路径匹配纯函数(可测试) |
| `hook/hooks/DeviceEnvPathMatcherTest.kt` | 新增 | 8 个单元测试 |
| `hook/hooks/RootPropertyHookerTest.kt` | 新增 | 12 个单元测试 |
| `hook/hooks/EmulatorBuildHookerTest.kt` | 新增 | 9 个单元测试 |

---

## 验收指标

| 指标 | 基线 | 当前 | 状态 |
|------|------|------|------|
| 编译 | BUILD SUCCESSFUL | BUILD SUCCESSFUL | [x] |
| Lint error | 0 | 0 | [x] |
| Lint warning | 70 | 70 | [x] |
| 单元测试 | PASSED | PASSED | [x] |
| 设备安装 | Success | Success | [x] |
| 应用启动 | - | 5/5 无崩溃 | [x] |
| 全量 DeviceEnv | - | 暮光工具箱正常启动 | [x] |
| 新增测试 | - | 29 个 | [x] |
