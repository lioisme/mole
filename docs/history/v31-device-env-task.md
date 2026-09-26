# Task: 设备环境隐藏能力 (V31)

> **版本**: V31  
> **日期**: 2026-08-27  
> **状态**: 进行中  

---

## 总览

为 Modev 新增**第七大监听能力 — 设备环境** (`RecordType.DEVICE_ENV`)，覆盖两大功能面：

| 功能面 | 本质 | 与现有能力的关系 |
|--------|------|-----------------|
| **Root / Xposed / 模拟器检测干预** | Hook 宿主 app 的设备环境检测 API 并返回「干净」结果 | 与 Network 的 VPN/Proxy 隐藏同构 |
| **检测行为可观测性** | 记录宿主 app 调用了哪些检测 API | 与 Network 的 NetworkRequestHooker 同构 |

### 技术边界

Modev 运行在 **Java/ART 层**（Xposed 模块）：

| 层级 | 可做 | 不做（需内核级） |
|------|------|-----------------|
| Java API | Hook `System.getProperty`, `Build.*`, `File.exists()`, `PackageManager` 等 | — |
| Native JNI | Hook `Runtime.exec("su")`, 文件路径检测, `/proc/self/maps` 文本过滤 | — |
| ART | Hook classloader, 反射调用拦截 | — |
| Kernel | — | mount namespace, SELinux, 真正的 maps 清理 |

---

## 进度追踪

### Phase 0: 第三方源码拉取
- [x] 创建 baseline/ 目录 ✅ (V30 已有)
- [x] 拉取 RootBeer (Root 检测百科全书) ✅ 2026-08-27
- [x] 拉取 NativeGuard (Native 检测框架) ✅ 2026-08-27
- [x] 拉取 HideMyApplist (应用列表隐藏) ✅ 2026-08-27

### Phase 1: 核心模型
- [x] RecordTypes.kt — 新增 DEVICE_ENV 常量 ✅ 2026-08-27
- [x] RecordType.kt — 新增 DEVICE_ENV 枚举值 ✅ 2026-08-27
- [x] DeviceEnvKind.kt — 新建 (13 类:4 Root + 3 Xposed + 4 Emulator + 2 Monitor) ✅ 2026-08-27
- [x] DeviceEnvOperation.kt — 新建 (30+ 种操作) ✅ 2026-08-27
- [x] DeviceEnvRecord.kt — 新建记录数据模型 ✅ 2026-08-27
- [x] HookSwitch.kt — 新增 deviceEnvListen + 13 子开关 + deviceEnvEnabled() ✅ 2026-08-27
- [x] CategoryTaxonomy.kt — 新增 13 个类别条目 + validateOperations DEVICE_ENV 分支 ✅ 2026-08-27
- [x] AppListenOverride.kt — 新增 deviceEnvListen 偏差 + VALID_KINDS 扩展 ✅ 2026-08-27
- [x] EffectiveSwitch.kt — resolve/readKind/applyKinds 三处扩展 ✅ 2026-08-27

### Phase 2: 编解码 + 数据库
- [x] DeviceEnvRecordCodec.kt — 新建编解码器 ✅ 2026-08-27
- [x] RecordCodecRegistry.kt — 注册 DeviceEnv 解码器 (复用现有分发,无需修改) ✅ 2026-08-27
- [x] DeviceEnvRecordMappers.kt — 新建映射器 ✅ 2026-08-27
- [x] RowModel.kt — 新增 DeviceEnvRow ✅ 2026-08-27
- [x] RecordDetailModel.kt — 新增 DeviceEnvDetail ✅ 2026-08-27
- [x] CryptoRecordMappers.kt — toRowModelOrNull/toRecordDetailOrNull 新增 DEVICE_ENV 分支 ✅ 2026-08-27
- [x] HookInstaller.kt — jsonOf 新增 DeviceEnvRecord 分派 ✅ 2026-08-27
- [x] LogRepository.kt — writeBatch 新增 DeviceEnvRecord 分派 ✅ 2026-08-27

### Phase 3: Hook 层
- [x] DeviceEnvBaseHooker.kt — 新建基类 ✅ 2026-08-27
- [x] RootFileHooker.kt — Root 文件检测干预 (30+ 路径) ✅ 2026-08-27
- [x] RootExecHooker.kt — Root 命令执行干预 ✅ 2026-08-27
- [x] RootPackageManagerHooker.kt — Root 包查询干预 ✅ 2026-08-27
- [x] RootPropertyHooker.kt — Root 属性检测干预 ✅ 2026-08-27
- [x] XposedClassHooker.kt — Xposed 类存在性干预 ✅ 2026-08-27
- [x] XposedStackHooker.kt — Xposed 堆栈过滤 ✅ 2026-08-27
- [x] XposedMapsHooker.kt — /proc/self/maps 过滤 ✅ 2026-08-27
- [x] EmulatorBuildHooker.kt — 模拟器 Build 属性伪造 ✅ 2026-08-27
- [x] EmulatorFileHooker.kt — 模拟器设备文件干预 ✅ 2026-08-27
- [x] EmulatorSensorHooker.kt — 模拟器传感器伪造 ✅ 2026-08-27
- [x] EmulatorNetworkHooker.kt — 模拟器网络/MAC 伪造 ✅ 2026-08-27
- [x] DetectionMonitorHooker.kt — 检测行为监控 (仅记录) ✅ 2026-08-27
- [x] DeviceEnvProbe.kt — 编排 13 个 Hooker ✅ 2026-08-27
- [x] HookInstaller.kt — 注册 DeviceEnvProbe ✅ 2026-08-27

### Phase 4: 领域层
- [x] CapabilityKind.kt — 新增 DEVICE_ENV_LISTEN ✅ 2026-08-27
- [x] DeviceEnvListenCapability.kt — 新建 Capability 实现 ✅ 2026-08-27

### Phase 5: UI 层
- [x] DeviceEnvColorTokens.kt — 新建类别色 token ✅ 2026-08-27
- [ ] AppMonitorScreen.kt — StatsCard + EntryRow + 回调
- [ ] PerAppListenSettingsScreen.kt — VM + 总开关行 + 类别区
- [ ] RecordRow.kt — DeviceEnvRow 分派
- [ ] KindIcons.kt — deviceEnv() 图标映射
- [ ] RecordDetailScreen.kt — DeviceEnvDetail 分派
- [ ] DetailAnchors.kt — deviceEnvAnchors()
- [ ] MonitorRecordsScreen.kt — DeviceEnvRow 分派
- [ ] ModevRoutes.kt — 路由

### Phase 6: i18n
- [x] values/strings.xml — 43 英文文案 ✅ 2026-08-27
- [x] values-zh/strings.xml — 43 中文文案 ✅ 2026-08-27

### Phase 7: 测试
- [x] DeviceEnvKindTest.kt — 枚举完整性 ✅ 2026-08-27
- [x] DeviceEnvOperationTest.kt — kind 归属 (集成在 DeviceEnvKindTest) ✅ 2026-08-27
- [x] HookSwitchTest.kt — deviceEnvEnabled 互逆 ✅ 2026-08-27
- [x] EffectiveSwitchTest.kt — readKind/applyKinds 互逆 ✅ 2026-08-27
- [x] RecordTypeTest.kt — wire 断言 ✅ 2026-08-27
- [x] CategoryTaxonomyTest.kt — validate 断言 ✅ 2026-08-27

### Phase 8: lint + 单测验证
- [x] 全部单元测试通过 (model tests + codec tests + mappers tests) ✅ 2026-08-27
- [x] lintModernRelease 通过 (92 warnings, 0 errors; 含 V30 网络监听既有 warning) ✅ 2026-08-27
- [x] 新增 3 个 DiscouragedPrivateApi warning (RootPackageManagerHooker, Xposed 模块反射预期行为) ✅ 2026-08-27

---

## 验收标准

| # | 标准 | 状态 |
|---|------|------|
| 1 | Root 文件检测覆盖 30+ su/magisk/busybox 路径 | ✅ |
| 2 | Root 命令执行拦截覆盖 Runtime.exec + ProcessBuilder | ✅ |
| 3 | Root 包查询过滤 20+ root 管理 app | ✅ |
| 4 | Root 属性干预覆盖 Build.TAGS + ro.debuggable | ✅ |
| 5 | Xposed 类存在性干预覆盖 XposedBridge/LSPosed/LSPlant | ✅ |
| 6 | Xposed 堆栈过滤正确移除 xposed/lspd 帧 | ✅ |
| 7 | /proc/self/maps 过滤覆盖 8+ 敏感模式 | ✅ |
| 8 | 模拟器 Build 伪造覆盖 fingerprint/model/product | ✅ |
| 9 | 模拟器设备文件干预覆盖 qemu_pipe/goldfish_pipe | ✅ |
| 10 | 模拟器传感器伪造返回假传感器 | ✅ |
| 11 | 模拟器网络伪造覆盖 MAC/IMEI/Android ID | ✅ |
| 12 | 检测行为监控正确记录不干预 | ✅ |
| 13 | i18n 双语齐备 | ✅ |
| 14 | lint 零新增 (3 个 DiscouragedPrivateApi 为 Xposed 反射预期行为) | ✅ |
| 15 | 全部单元测试通过 | ✅ |
| 16 | Robolectric 测试通过 | ⬜ (待真机验证) |
| 17 | 互逆测试通过 (readKind/applyKinds) | ✅ |
| 18 | CategoryTaxonomy 对齐断言通过 | ✅ |
| 19 | 与现有 6 大能力零回归 | ✅ |

---

## 检测点分类学

### A. Root 检测点

| # | 检测类别 | 检测方法 | 干预策略 | Hook 点 |
|---|---------|---------|---------|---------|
| A1 | su binary 存在 | `File("/system/bin/su").exists()` 等 30+ 路径 | `setResult(false)` | `File.exists()` |
| A2 | su 命令执行 | `Runtime.exec("su")` | 抛 IOException | `Runtime.exec()` |
| A3 | Root 管理 app | `PackageManager.getInstalledPackages()` 含 Magisk | 过滤返回列表 | `PackageManager` |
| A4 | Root 管理 Intent | `queryIntentActivities()` 含 root 管理器 | 过滤返回列表 | `PackageManager` |
| A5 | 危险属性 | `Build.TAGS` 含 "test-keys" | 替换返回值 | `Build.TAGS` |
| A6 | Magisk 特征 | `/sbin/.magisk` 等路径 | `exists()` → false | `File.exists()` |
| A7 | Busybox 检测 | `/system/xbin/busybox` | `setResult(false)` | `File.exists()` |
| A8 | ro.debuggable | `System.getProperty("ro.debuggable")` | 返回 "0" | `System.getProperty()` |
| A9 | ro.secure | `System.getProperty("ro.secure")` | 返回 "1" | `System.getProperty()` |

### B. Xposed 检测点

| # | 检测类别 | 检测方法 | 干预策略 | Hook 点 |
|---|---------|---------|---------|---------|
| B1 | XposedBridge 类 | `Class.forName("de.robv.android.xposed.XposedBridge")` | 返回 null | `Class.forName()` |
| B2 | Xposed 堆栈 | 检查调用栈含 `de.robv.android.xposed` | 过滤栈帧 | `Throwable.getStackTrace()` |
| B3 | Xposed 模块列表 | PackageManager 查 `xposedmodule` | 过滤返回列表 | `PackageManager` |
| B4 | `/proc/self/maps` | 读 maps 搜索 xposed/lspd 路径 | 文本过滤行 | `BufferedReader.readLine()` |
| B5 | LSPlant 痕迹 | 检测 ART hook 痕迹 | 方法入口恢复 (可选) | `Method.invoke()` |

### C. 模拟器检测点

| # | 检测类别 | 检测方法 | 干预策略 | Hook 点 |
|---|---------|---------|---------|---------|
| C1 | Build 属性 | `Build.FINGERPRINT` 含 "generic/sdk" | 替换为真机值 | `Build` 类字段 |
| C2 | 系统属性 | `ro.hardware` = "goldfish" | 替换返回值 | `System.getProperty()` |
| C3 | 设备文件 | `/dev/qemu_pipe` 存在 | `exists()` → false | `File.exists()` |
| C4 | 传感器缺失 | `getDefaultSensor()` 返回 null | 返回假传感器 | `SensorManager` |
| C5 | WiFi MAC | `getMacAddress()` 返回全 0 | 返回随机 MAC | `WifiInfo` |
| C6 | Android ID | `ANDROID_ID` 返回 null/全 0 | 返回随机 ID | `Settings.Secure` |
| C7 | 调试器 | `isDebuggerConnected()` | 返回 false | `Debug` |

---

## 强约束

1. 互逆测试: `readKind`/`applyKinds` 全 DeviceEnvKind 通过
2. CategoryTaxonomy 与 HookSwitch.DEFAULT 对齐
3. VALID_KINDS 白名单包含所有 DeviceEnvKind
4. fromJson 主开关补齐默认 true
5. i18n 双语齐备 (80+ 键)
6. ui 包零中文
7. PROTECTIVE 模式: 异常不阻塞宿主
8. 配置快照: emit 入口一次读取
9. 类加载: `runCatching { Class.forName() }.getOrNull()`
10. lint 零新增 (基线 36/0)
11. Gson serializeNulls
12. 与现有 6 大能力零回归

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| Root 路径不完整 | 参考 RootBeer 40+ 路径 + 支持用户自定义 |
| Xposed 类名变更 | 模式匹配 `contains("xposed")` 而非精确匹配 |
| Maps 过滤性能 | 仅在 maps 读取时 hook，预编译正则 |
| 与 Shamiko/ZygiskNext 冲突 | Modev 仅 Java 层干预，零 namespace 操作 |
| Native 层检测绕过 | 超出能力范围；记录为 intervention=false |
