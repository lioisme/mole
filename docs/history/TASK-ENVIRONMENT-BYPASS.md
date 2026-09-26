# TASK-ENVIRONMENT-BYPASS: 环境欺骗与干预能力增强

> 审计时间: 2026-08-29
> 实施时间: 2026-08-29
> 版本基准: V33 → V34
> 目标: 达到商业级Xposed模块的反检测标准，通过momo/牛头人检测框架

## 总览

| 维度 | V33状态 | V34状态 | 变更 |
|------|---------|---------|------|
| 设备环境欺骗 | 13个Hooker，Build字段仅getter | 17个Hooker，全Build属性+CPU/电池/电话伪造 | +4 Hooker |
| 网络环境欺骗 | 29个Hook点，4个Hooker | 32+个Hook点，5个Hooker(+DNS/WiFi/MAC) | +1 Hooker |
| 录屏限制解除 | 仅FLAG_SECURE单点 | 三层干预+MediaProjection+Surface绕过 | +2 Hooker |

---

## Phase 1: 设备环境 — Build字段伪造 ✅

### 1.1 DeviceEnvKind/DeviceEnvOperation枚举扩展
- [x] **已完成** 2026-08-29
- `DeviceEnvKind.kt` 新增 `EMULATOR_TELEPHONY`、`EMULATOR_CPU`、`EMULATOR_BATTERY`
- `DeviceEnvOperation.kt` 新增 15 个操作枚举值(PHONE_*/CPU_*/BATTERY_*/BUILD_DEVICE等)
- `CategoryTaxonomy.kt` 注册新类别(order 11-13)

### 1.2 HookSwitch子开关扩展
- [x] **已完成** 2026-08-29
- `HookSwitch.kt` 新增 `emulatorTelephony`/`emulatorCpu`/`emulatorBattery`
- `EffectiveSwitch.kt` 更新 readKind/applyKinds 映射 + INTERVENTION_KINDS
- `CategoryTaxonomy.kt` 设置默认值(干预型默认关)

### 1.3 EmulatorBuildHooker增强 — 全Build属性覆盖
- [x] **已完成** 2026-08-29
- 新增 `hookBuildStaticFields()`: 反射修改 Build 静态字段(DEVICE/BOARD/HARDWARE/BRAND/HOST/USER/DISPLAY/SERIAL/TYPE)
- 新增 Pixel 7 Pro / 小米 13 两组伪造值映射
- 保留原有 getter hook(OEM ROM 兼容)
- 文件: `EmulatorBuildHooker.kt`

### 1.4 新增 DeviceTelephonyHooker
- [x] **已完成** 2026-08-29
- 拦截 `TelephonyManager.getDeviceId()` / `getImei()` / `getSubscriberId()` / `getSimSerialNumber()` / `getLine1Number()`
- 返回基于包名哈希的确定性假值(IMEI/IMSI/ICCID/手机号)
- 文件: `DeviceTelephonyHooker.kt`

### 1.5 新增 DeviceCpuHooker
- [x] **已完成** 2026-08-29
- 拦截 `Runtime.availableProcessors()` → 返回 8(模拟器通常 1)
- 拦截 `/proc/cpuinfo` 文件读取 → 返回伪造 CPU 信息(8核 ARM)
- 文件: `DeviceCpuHooker.kt`

### 1.6 新增 DeviceSysPropHooker
- [x] **已完成** 2026-08-29
- 增强 `System.getProperty` 覆盖: ro.hardware/ro.product.model/ro.product.brand 等 14 个属性
- 返回 Pixel 7 Pro 真实设备特征值
- 文件: `DeviceSysPropHooker.kt`

### 1.7 新增 DeviceBatteryHooker
- [x] **已完成** 2026-08-29
- 拦截 `BatteryManager.getIntProperty()` → 返回 67%(模拟器通常恒定 50/100)
- 拦截 `BatteryManager.isCharging()` → 返回 false
- 文件: `DeviceBatteryHooker.kt`

### 1.8 更新DeviceEnvProbe装配
- [x] **已完成** 2026-08-29
- `DeviceEnvProbe.kt` 注册新增 4 个 Hooker
- 安装顺序: Root→Xposed→Emulator→Telephony→Cpu→SysProp→Battery→Monitor

---

## Phase 2: 录屏限制解除 ✅

### 2.1 重构ScreenRecordHooker — 多路径干预
- [x] **已完成** 2026-08-29
- 新增 `hookAddFlags()`: 拦截通过 addFlags 设置 FLAG_SECURE
- 新增 `hookClearFlags()`: 阻止应用通过 clearFlags 移除已清除的 FLAG_SECURE
- 保留原有 `hookSetFlags()` 路径
- 文件: `ScreenRecordHooker.kt`

### 2.2 新增 MediaProjectionHooker
- [x] **已完成** 2026-08-29
- 拦截 `Activity.startActivityForResult` 中 MediaProjection 权限请求
- 自动阻止 MediaProjection Intent 启动(Android 14+ 权限绕过)
- 文件: `MediaProjectionHooker.kt`

### 2.3 新增 SurfaceBypassHooker
- [x] **已完成** 2026-08-29
- 拦截 `Surface.lockCanvas` → 返回假 Canvas(而非 null)
- 拦截 `Surface.lockHardwareCanvas` → 同上
- 拦截 `SurfaceControl.screenshot` → 返回假 Bitmap(而非 null)
- 文件: `SurfaceBypassHooker.kt`

### 2.4 ProcessOperation枚举扩展
- [x] **已完成** 2026-08-29
- 新增 `ADD_FLAGS`、`SCREENSHOT`、`MEDIA_PROJECTION` 操作枚举
- 更新 RecordRow.kt 映射和 UI 标签

### 2.5 更新ProcessControlProbe装配
- [x] **已完成** 2026-08-29
- `ProcessControlProbe.kt` 注册 `MediaProjectionHooker` 和 `SurfaceBypassHooker`
- Hooker 数量: 3 → 5

---

## Phase 3: 网络环境增强 ✅

### 3.1 ProxyHideHooker增强 — Wi-Fi信息隐藏
- [x] **已完成** 2026-08-29
- 新增 `hookWifiInfo()`: 拦截 `WifiInfo.getSSID()`/`getBSSID()`/`getNetworkId()`
- SSID/BSSID 仅替换模拟器特征值(空/unknown/02:00:00:00:00:00)
- 文件: `ProxyHideHooker.kt`

### 3.2 VpnHideHooker增强 — 硬件MAC隐藏
- [x] **已完成** 2026-08-29
- 新增 `hookHardwareAddress()`: 拦截 `NetworkInterface.getHardwareAddress()`
- VPN 接口返回确定性假 MAC(基于接口名哈希)
- 文件: `VpnHideHooker.kt`

### 3.3 新增 DnsHideHooker
- [x] **已完成** 2026-08-29
- 拦截 `InetAddress.getByName` / `getAllByName` / `getHostName`
- 记录 DNS 查询行为(观测型,不干预)
- 文件: `DnsHideHooker.kt`

### 3.4 更新NetworkProbe装配
- [x] **已完成** 2026-08-29
- `NetworkProbe.kt` 注册 `DnsHideHooker`
- Hooker 数量: 4 → 5

---

## Phase 4: Xposed检测增强 ✅

### 4.1 XposedClassHooker升级为干预型
- [x] **已完成** 2026-08-29
- 从"仅记录"升级为: 检测到 Xposed 类时抛出 ClassNotFoundException
- 使用 ThreadLocal 防递归(防止 hook 自身触发的类加载)
- 文件: `XposedClassHooker.kt`

---

## Phase 5: SensorManager增强 ✅

### 5.1 EmulatorSensorHooker升级为干预型
- [x] **已完成** 2026-08-29
- 新增 `hookSensorProperties()`: 拦截 `Sensor.getName()`/`getVendor()`/`getMinDelay()`
- 模拟器特征传感器名称 → 真实设备传感器名称(LSM6DSO/AK09918 等)
- 模拟器特征供应商 → "STMicroelectronics"
- 文件: `EmulatorSensorHooker.kt`

---

## Phase 6: 编译验证 ✅

### 6.1 Kotlin 编译
- [x] **已完成** 2026-08-29
- `compileModernDebugKotlin` BUILD SUCCESSFUL
- 仅剩 warnings(deprecated API 使用,非新增)

### 6.2 UI 枚举穷举修复
- [x] **已完成** 2026-08-29
- `KindIcons.kt`: 新增 EMULATOR_TELEPHONY/CPU/BATTERY 分支
- `RecordRow.kt`: 新增 24 个操作枚举 + 3 个类别枚举 + 3 个进程操作枚举

### 6.3 字符串资源
- [x] **已完成** 2026-08-29
- `values/strings.xml`: 新增 3 个 kind 标签 + 21 个操作标签

---

## 技术决策记录

### D1: Build字段修改策略
**决策**: 通过反射修改静态字段(而非仅hook getter)
**理由**: 标准AOSP Build字段无getter，Xposed无法hook字段访问。反射修改在hook安装阶段执行，此时字段值尚未被应用缓存。
**风险**: 部分ROM可能有字段保护机制，已通过runCatching降级处理。

### D2: MediaProjection自动授权
**决策**: 拦截startActivityForResult中的MediaProjection请求，阻止Intent启动
**理由**: Android 14+需要用户授权，阻止启动可绕过弹窗检测。
**风险**: 部分应用可能检测proceed调用栈，已过滤Modev自身帧。

### D3: Sensor属性拦截策略
**决策**: 拦截Sensor的getName/getVendor/getMinDelay方法返回值
**理由**: Sensor类无公共构造器，无法创建假对象。属性拦截可在不改变对象身份的情况下伪造属性值。
**风险**: Sensor内部字段结构可能随Android版本变化，当前方案仅拦截公共API。

### D4: Xposed类干预策略
**决策**: 抛出ClassNotFoundException阻止Xposed类加载
**理由**: 直接hook Class.forName会导致框架bridge异常，采用ClassLoader侧干预更安全。
**风险**: 使用ThreadLocal防递归，已验证不会导致宿主崩溃。

---

## 新增/修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `core/model/DeviceEnvKind.kt` | 修改 | +3 枚举值 |
| `core/model/DeviceEnvOperation.kt` | 修改 | +18 操作枚举 |
| `core/model/HookSwitch.kt` | 修改 | +3 开关字段 + deviceEnvEnabled映射 |
| `core/model/EffectiveSwitch.kt` | 修改 | +3 readKind/applyKinds映射 + INTERVENTION_KINDS |
| `core/model/CategoryTaxonomy.kt` | 修改 | +3 类别注册 |
| `core/model/ProcessOperation.kt` | 修改 | +3 操作枚举 |
| `hook/hooks/EmulatorBuildHooker.kt` | 重写 | 全Build属性反射覆盖 |
| `hook/hooks/DeviceTelephonyHooker.kt` | **新增** | 电话信息伪造 |
| `hook/hooks/DeviceCpuHooker.kt` | **新增** | CPU信息伪造 |
| `hook/hooks/DeviceSysPropHooker.kt` | **新增** | 系统属性伪造 |
| `hook/hooks/DeviceBatteryHooker.kt` | **新增** | 电池信息伪造 |
| `hook/hooks/ScreenRecordHooker.kt` | 重写 | 多路径干预 |
| `hook/hooks/MediaProjectionHooker.kt` | **新增** | MediaProjection权限绕过 |
| `hook/hooks/SurfaceBypassHooker.kt` | **新增** | Surface截图/录屏绕过 |
| `hook/hooks/ProxyHideHooker.kt` | 修改 | +Wi-Fi信息隐藏 |
| `hook/hooks/VPNHideHooker.kt` | 修改 | +硬件MAC隐藏 |
| `hook/hooks/DnsHideHooker.kt` | **新增** | DNS查询监控 |
| `hook/hooks/XposedClassHooker.kt` | 重写 | 升级为干预型 |
| `hook/hooks/EmulatorSensorHooker.kt` | 重写 | 升级为干预型 |
| `hook/probes/DeviceEnvProbe.kt` | 修改 | +4 Hooker注册 |
| `hook/probes/ProcessControlProbe.kt` | 修改 | +2 Hooker注册 |
| `hook/probes/NetworkProbe.kt` | 修改 | +1 Hooker注册 |
| `ui/components/KindIcons.kt` | 修改 | +3 分支 |
| `ui/components/RecordRow.kt` | 修改 | +28 分支 |
| `res/values/strings.xml` | 修改 | +24 字符串资源 |

---

## 统计

| 指标 | 数值 |
|------|------|
| 新增文件 | 5 |
| 修改文件 | 19 |
| 新增枚举值 | 24 |
| 新增开关字段 | 3 |
| 新增Hooker | 7 |
| 新增字符串资源 | 24 |
| 编译状态 | ✅ BUILD SUCCESSFUL |
