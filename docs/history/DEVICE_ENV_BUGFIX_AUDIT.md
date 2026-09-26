# Modev 设备环境(DeviceEnv)功能 — BUG 审计与修复任务文档

> 审计日期: 2026-08-28
> 审计范围: 全部 13 个 DeviceEnv 子功能(4 大检测域)
> 测试环境: 雷电模拟器(LDPlayer x64), 新版框架已安装生效
> 目标: 全部子功能启用时目标应用无卡顿、崩溃、闪退

---

## 一、问题概述

启用设备环境的全部子功能后,目标应用卡在启动界面并崩溃。
崩溃机制: Hook 成功执行但返回了错误的值(PROTECTIVE 模式确保异常不传播)。

---

## 二、定位到的 BUG

### BUG #1 [P0 — 崩溃根因] RootFileHooker 路径匹配过于宽松

- **文件**: `hook/hooks/RootFileHooker.kt:128-129`
- **问题**: `matchesRootPath()` 使用 `contains()` 子串匹配,`ROOT_PATHS` 首项 `"/su"` 会误杀
  任何包含 `su` 子串的合法路径(`/data/user/0/...`、`/data/success/...` 等)
- **崩溃机制**: App 启动检查自身数据目录文件 → `File.exists()` 返回 false → 加载失败 → 崩溃
- **状态**: [x] 已修复 — 移除过短的 `"/su"` 项;`matchesRootPath`/`matchesEmulatorPath` 改为
  精确匹配 + 前缀分隔符匹配(`startsWith(pattern+"/")`),杜绝子串误杀

### BUG #2 [P1] EmulatorBuildHooker 返回不一致的伪造值

- **文件**: `hook/hooks/EmulatorBuildHooker.kt:73-95`
- **问题**: MANUFACTURER="Xiaomi" + MODEL="Pixel 7 Pro" + FINGERPRINT 含 `rheroqltetv`
  组合矛盾,高级反篡改系统检测到不一致后冻结/崩溃
- **状态**: [x] 已修复 — 重写 `getRealisticValue()` 返回两组一致配置(Pixel 7 Pro 组 / 小米 13 组);
  未知模拟器指纹不干预(返回原值);EMULATOR_KEYWORDS 补充 Nox/BlueStacks/LDPlayer 特征

### BUG #3 [P1] RootPropertyHooker 误修改非 root 指示值

- **文件**: `hook/hooks/RootPropertyHooker.kt:56-62`
- **问题**: 无条件替换 `ro.debuggable`/`ro.secure` 等属性,即使原始值并非 root 指示值
  (如 LDPlayer 上 `ro.debuggable` 可能已经是 "0"),修改合法值导致 App debug 路径断裂
- **状态**: [x] 已修复 — 新增 `isRootIndicativeValue()` 校验,仅在原始值确实是 root 指示值
  (如 `ro.debuggable=1`)时才干预,已在正常状态的属性不修改

### BUG #4 [P1] XposedStackHooker 安装时守卫导致功能失效

- **文件**: `hook/hooks/XposedStackHooker.kt:13-16`
- **问题**: `install()` 中 `if (!config().xposedStack) return` 在安装时检查,
  后续启用该功能时 Hook 未注册,功能静默失效
- **状态**: [x] 已修复 — 移除安装时守卫,始终注册 Hook;回调内添加 `config().xposedStack` 运行时检查

### BUG #5 [P1] XposedClassHooker 安装时守卫导致功能失效

- **文件**: `hook/hooks/XposedClassHooker.kt:22-26`
- **问题**: 同 BUG #4,`install()` 中 `if (!config().xposedClass) return`
- **状态**: [x] 已修复 — 移除安装时守卫,始终注册 Hook;回调内已有运行时检查

### BUG #6 [P2] XposedMapsHooker 内存泄漏

- **文件**: `hook/hooks/XposedMapsHooker.kt:24-25,50-51`
- **问题**: `mapsReaders`/`mapsReaderPaths` 集合无限增长,长时间运行 OOM
- **状态**: [x] 已修复 — 添加 `MAX_TRACKED_READER_COUNT=64` 容量上限;
  `hookReadLine` 中 reader 返回 null(流结束)时自动清理集合条目

### BUG #7 [P2] DetectionMonitorHooker 与干预型 Hooker 双重 Hook

- **文件**: `hook/hooks/DetectionMonitorHooker.kt:22-50`
- **问题**: 安装时检查干预型 Hooker 状态,配置变化后可能双重 Hook 同一 API
- **状态**: [x] 已修复 — 所有 6 个 monitor 回调内添加运行时检查,
  如果对应干预型 Hooker 已启用则跳过监控(如 `config().rootFile` 时跳过 monitorFileExists)

---

## 三、修复任务清单(按开发顺序)

| # | 任务 | 优先级 | 文件 | 状态 |
|---|------|--------|------|------|
| 1 | 修复 RootFileHooker 路径匹配 | P0 | RootFileHooker.kt | [x] |
| 2 | 修复 EmulatorBuildHooker 伪造值一致性 | P1 | EmulatorBuildHooker.kt | [x] |
| 3 | 修复 RootPropertyHooker 原始值校验 | P1 | RootPropertyHooker.kt | [x] |
| 4 | 修复 XposedStackHooker 安装守卫 | P1 | XposedStackHooker.kt | [x] |
| 5 | 修复 XposedClassHooker 安装守卫 | P1 | XposedClassHooker.kt | [x] |
| 6 | 修复 XposedMapsHooker 内存泄漏 | P2 | XposedMapsHooker.kt | [x] |
| 7 | 修复 DetectionMonitorHooker 双重 Hook | P2 | DetectionMonitorHooker.kt | [x] |
| 8 | 运行单元测试 | - | - | [x] |
| 9 | 构建 APK | - | - | [x] |
| 10 | 设备压力测试(全量子功能) | - | - | [x] |

---

## 四、修复日志

### 2026-08-28 — BUG #1 [P0] RootFileHooker 路径匹配
- `matchesRootPath()`: `contains()` → 精确匹配 + `startsWith(pattern+"/")` + `startsWith(pattern+".")`
- `matchesEmulatorPath()`: 同步改为前缀分隔符匹配
- `ROOT_PATHS`: 移除过短的 `"/su"` 项(误杀率极高)
- **验证**: 编译通过,lint 0 error

### 2026-08-28 — BUG #2 [P1] EmulatorBuildHooker 伪造值一致性
- 重写 `getRealisticValue()`: 返回两组一致配置(Pixel 7 Pro / 小米 13)
- 未知模拟器指纹不干预(返回原值)
- `EMULATOR_KEYWORDS`: 补充 `vbox`/`genymotion`/`nox`/`bluestacks`/`ldnews`
- **验证**: 编译通过

### 2026-08-28 — BUG #3 [P1] RootPropertyHooker 原始值校验
- 新增 `isRootIndicativeValue()` 静态方法
- 仅在原始值确实是 root 指示值(如 `ro.debuggable=1`)时才干预
- **验证**: 编译通过

### 2026-08-28 — BUG #4 [P1] XposedStackHooker 安装守卫
- 移除 `install()` 中的 `if (!config().xposedStack) return`
- 在两个 Hook 回调内添加 `if (!config().xposedStack) return` 运行时检查
- **验证**: 编译通过

### 2026-08-28 — BUG #5 [P1] XposedClassHooker 安装守卫
- 移除 `install()` 中的 `if (!config().xposedClass) return`
- 回调内已有运行时检查(无需额外修改)
- **验证**: 编译通过

### 2026-08-28 — BUG #6 [P2] XposedMapsHooker 内存泄漏
- 新增 `MAX_TRACKED_READER_COUNT = 64` 容量上限
- `hookFileReader`: 超过容量上限时跳过追踪
- `hookReadLine`: reader 返回 null(流结束)时自动清理集合条目
- **验证**: 编译通过

### 2026-08-28 — BUG #7 [P2] DetectionMonitorHooker 双重 Hook
- 6 个 monitor 回调内添加运行时检查:
  - `monitorFileExists`: `if (config().rootFile) return`
  - `monitorGetStackTrace`: `if (config().xposedStack) return`
  - `monitorReadLine`: `if (config().xposedMaps) return`
  - `monitorGetDefaultSensor`: `if (config().emulatorSensor) return`
  - `monitorGetMacAddress`: `if (config().emulatorNetwork) return`
  - `monitorBuildFields`: `if (config().emulatorBuild) return`
- **验证**: 编译通过

### 2026-08-28 — 构建与验证
- `assembleModernDebug`: BUILD SUCCESSFUL (11s)
- `testModernDebugUnitTest`: BUILD SUCCESSFUL
- `lintModernRelease`: BUILD SUCCESSFUL — **0 error / 70 warning**(全部预存,无新增)
- APK 安装到 LDPlayer: Success

### 2026-08-28 — 设备压力测试(全 DeviceEnv ON)

**测试配置**: 全部 13 个子功能启用(rootFile/rootExec/rootPackageManager/rootProperty/xposedClass/xposedStack/xposedMaps/emulatorBuild/emulatorFile/emulatorSensor/emulatorNetwork/detectRoot/detectEmulator)

| 测试轮次 | 应用数 | 结果 |
|---------|--------|------|
| 1 轮 × 7 应用 | 7 | 7/7 PASS |
| 10 轮 × 3 应用(冷启动循环) | 30 | 30/30 PASS |
| 5 轮 × 7 应用(扩大验证) | 35 | 35/35 PASS |
| **总计** | **72** | **72/72 PASS (100%)** |

- 崩溃检查: Zero FATAL EXCEPTION
- 测试应用: TooX, Jellyflix, Via Browser, LIDE, Squircle IDE, ModevTester, Depan
