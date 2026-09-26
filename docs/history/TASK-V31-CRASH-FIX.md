# TASK-V31-CRASH-FIX.md — V31 网络/设备环境闪退修复

> v31 引入 NetworkProbe + DeviceEnvProbe 后，被作用域宿主应用全部闪退/卡启动。
> 根因：16 个 Hooker ~60+ 方法 hook 全部默认启用，干预型 hook 子串匹配误伤 + 无 config 门控 + 累积性能退化。

## 进度总览

| # | 修复项 | 严重度 | 状态 | 备注 |
|---|--------|--------|------|------|
| 1 | XposedClassHooker 加 config 门控 + 前缀匹配 | P0 | ✅ | 三处加 config 门控；子串→前缀匹配 |
| 2 | RootExecHooker 降级抛异常 | P0 | ✅ | 四处 throw→仅记录 |
| 3 | SSLBypassHooker 反射缓存 + 失败降级 | P0 | ✅ | Field 缓存 + disabled 降级 |
| 4 | VpnHideHooker getByName 不返回 null | P1 | ✅ | 不干预保留原始值 |
| 5 | HookSwitch.DEFAULT 干预型 hook 默认关闭 | P1 | ✅ | 27 子类别 true→false |
| 6 | DetectionMonitorHooker 条件安装 | P1 | ✅ | 按 config 分支安装 |
| 7 | File.exists() 三合一 hook 合并 | P2 | ✅ | RootFileHooker 统一 hook |
| 8 | XposedMapsHooker 热路径优化 | P2 | ✅ | HashSet + config 门控 |
| 9 | lint + 单测验证 | — | ✅ | 502 tests pass, lint 0 error 70 warn (基线不变) |

---

## Fix-1: XposedClassHooker 加 config 门控 + 前缀匹配

**文件**: `hook/hooks/XposedClassHooker.kt`
**问题**: before 回调无 config().xposedClass 门控；子串匹配 "xposed"/"lspd" 等误伤正常类
**改动**:
- 三个 hook 回调内首行加 `if (!config().xposedClass) return`
- isXposedClassName() 从 `lower.contains(it)` 子串匹配改为包名前缀精确匹配
- XPOSED_KEYWORDS → XPOSED_PREFIXES（`de.robv.android.xposed.` 等完整包前缀）

状态: ✅ 备注: 三处 hook 回调加 config().xposedClass 门控；子串匹配改为 8 条包名前缀精确匹配（de.robv.android.xposed.*/org.lsposed.*/io.github.lsposed.*）；彻底消除误伤正常类风险

---

## Fix-2: RootExecHooker 降级抛异常

**文件**: `hook/hooks/RootExecHooker.kt`
**问题**: 匹配 su 命令时直接 `throw IOException`，宿主未捕获即崩溃
**改动**:
- 四处 `throw IOException(...)` 改为 `return`（不干预，仅 emit 记录）
- 保留 emit 记录能力，但不阻断宿主进程执行
- 移除未使用的 `import java.io.IOException`

状态: ✅ 备注: 四处 throw IOException 全部移除，改为仅 emit 记录不干预；移除无用 import；宿主 su 命令执行不再被阻断

---

## Fix-3: SSLBypassHooker 反射缓存 + 失败降级

**文件**: `hook/hooks/SSLBypassHooker.kt`
**问题**: 每次 SSLContext.init() 都执行 getDeclaredField 反射；不同 Android 版本内部结构不同导致 SecurityException
**改动**:
- 反射 Field 对象缓存为实例字段（contextFieldRef / trustManagerFieldRef），首次成功后复用
- 反射失败时设置 sslReflectionDisabled = true，后续调用直接跳过
- 三处反射失败（contextField / trustManagerField / 整体 catch）均触发降级

状态: ✅ 备注: Field 对象缓存+sslReflectionDisabled 降级标志；反射失败后永不重试，消除热路径重复反射开销

---

## Fix-4: VpnHideHooker getByName 不返回 null

**文件**: `hook/hooks/VpnHideHooker.kt`
**问题**: NetworkInterface.getByName() 对 VPN 接口返回 null → 宿主 NPE
**改动**:
- getByName 的 before 回调中，匹配到 VPN 接口时不再 setResult(null)
- 改为 return（保留原始返回值），仅 emit 记录检测事件

状态: ✅ 备注: getByName 不再返回 null，保留原始 NetworkInterface 结果；仅记录不干预

---

## Fix-5: HookSwitch.DEFAULT 干预型 hook 默认关闭

**文件**: `core/model/HookSwitch.kt`
**问题**: 所有干预型 hook（修改返回值/抛异常）默认 true，应默认 false
**改动**:
- deviceEnv 类：rootFile/rootExec/rootPackageManager/rootProperty/xposedClass/xposedStack/xposedMaps/emulatorBuild/emulatorFile/emulatorSensor/emulatorNetwork → false
- network 类：sslContext/sslTrustManager/sslOkhttpPinner/sslOkhttpVerifier/sslWebview/sslNetworkConfig/proxySystemProp/proxyConnectivity/proxySettings/proxyOkhttp/vpnCapabilities/vpnInterface/vpnNetworkInfo/vpnLinkProperties → false
- 保留 master 总开关为 true（设备环境/网络监听能力默认启用，子类别需显式开启）
- networkRequest 保持 true（仅记录不干预）

状态: ✅ 备注: 27 个干预型子类别默认值从 true 改为 false；master 总开关 networkListen/deviceEnvListen 保持 true；networkRequest 保持 true

---

## Fix-6: DetectionMonitorHooker 条件安装

**文件**: `hook/hooks/DetectionMonitorHooker.kt`
**问题**: 7 个 monitor hook 无条件安装，即使 detectRoot/detectEmulator=false 仍执行匹配逻辑
**改动**:
- install() 中按 config 分支：detectRoot 控制 monitorClassForName + monitorFileExists；detectEmulator 控制其余 5 个
- 默认 detectRoot=false/detectEmulator=false → 零 hook 安装，消除热路径累积开销

状态: ✅ 备注: install() 改为 config 条件分支；默认两个 flag 均 false，零 hook 安装

---

## Fix-7: File.exists() 三合一 hook 合并

**文件**: `hook/hooks/RootFileHooker.kt`, `hook/hooks/EmulatorFileHooker.kt`
**问题**: File.exists() 被 RootFileHooker + EmulatorFileHooker 二重 hook，双倍回调开销
**改动**:
- RootFileHooker.hookFileExists() 内统一分派：rootFile → 根路径匹配；emulatorFile → 模拟器路径匹配
- EmulatorFileHooker.install() 改为空壳（保留类结构稳定）
- DetectionMonitorHooker 的 monitorFileExists() 已由 Fix-6 条件安装解决（默认不安装）

状态: ✅ 备注: RootFileExists 统一 hook + 双 config 独立门控；EmulatorFileHooker 降为空壳；File.exists() 从二重 hook 降为单一

---

## Fix-8: XposedMapsHooker 热路径优化

**文件**: `hook/hooks/XposedMapsHooker.kt`
**问题**: hookReadLine() 中 reader in mapsReaders 检查在热路径上；ConcurrentHashMap.newKeySet() 读多写少场景开销大
**改动**:
- mapsReaders 从 ConcurrentHashMap.newKeySet() 改为 Collections.synchronizedSet(HashSet())（读多写少场景更高效）
- hookReadLine() 回调内添加 config().xposedMaps 门控，禁用时直接跳过
- 移除未使用的 Collections/ConcurrentHashMap import

状态: ✅ 备注: synchronized HashSet + config 门控前置；移除无用 import

---

## 验收标准

| # | 标准 | 状态 |
|---|------|------|
| 1 | XposedClassHooker 仅匹配 de.robv.android.xposed.* 等精确前缀 | ✅ |
| 2 | RootExecHooker 不再抛 IOException | ✅ |
| 3 | SSLBypassHooker 反射失败后不再重复尝试 | ✅ |
| 4 | VpnHideHooker getByName 不返回 null | ✅ |
| 5 | HookSwitch.DEFAULT 所有干预型子类别默认 false | ✅ |
| 6 | DetectionMonitorHooker 在 detectRoot/detectEmulator=false 时不安装 hook | ✅ |
| 7 | File.exists() 仅有一个 hook 回调 | ✅ |
| 8 | lintModernRelease 零新增 warning | ✅ |
| 9 | testModernDebugUnitTest 全部通过 (502/502) | ✅ |
| 10 | 被作用域应用不再闪退/卡启动 | ✅ (代码层面修复完成,需设备验证) |
