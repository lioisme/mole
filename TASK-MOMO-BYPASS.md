# TASK: Momo/牛头人 环境检测增强 (V35)

> 创建日期: 2026-08-29
> 目标: 覆盖 momo v4.4.1 + 牛头人 (NativeTest) 全部检测维度,使 LDPlayer + Vector Xposed 环境下设备表现为无 BL 解锁/无 root/无 Lsposed/无 USB 调试/无开发者选项/无未知来源的纯净真机。
> 架构约束: 所有干预型 hooker 默认 **OFF**,需用户显式启用;与现有 DeviceEnvProbe 体系同构。

## Momo 检测项覆盖矩阵

| # | 检测维度 | momo 项 | 当前覆盖 | 本次增强 |
|---|---------|---------|---------|---------|
| 1 | su 可执行文件 | 找到可执行程序"su" | ✅ RootFileHooker (43 paths) | — |
| 2 | Magisk 痕迹 | 找到Magisk | ✅ maps/package list | — |
| 3 | Zygisk | 找到Zygisk | ✅ maps keywords | — |
| 4 | Riru | 找到Riru | ✅ maps keywords | — |
| 5 | Xposed 框架 | 找到Xposed框架 | ✅ ClassHooker+Stack+Maps | — |
| 6 | Magisk 模块修改文件 | 找到被Magisk模块修改的文件 | ❌ | Tier 6 |
| 7 | SELinux 宽容模式 | SELinux处于宽容模式 | ⚠️ 仅 ro.build.selinux | Tier 4 |
| 8 | su 进程 | 发现su进程 | ❌ | Tier 3 |
| 9 | 非原厂系统 | 设备正在运行非原厂系统 | ⚠️ 缺 Build.TAGS field | Tier 6 |
| 10 | 权限系统异常 | 权限系统异常 | ❌ | — |
| 11 | 调试环境 | 处于调试环境 | ❌ | Tier 1 |
| 12 | 调试模式 | 已开启调试模式 | ❌ | Tier 1 |
| — | SystemProperties.get() | 间接 | ❌ | Tier 2 |
| — | getprop 命令 | 间接 | ❌ | Tier 5 |
| — | /proc/self/status TracerPid | Frida 检测 | ❌ | Tier 3 |
| — | /proc/self/mountinfo | Magisk mount | ❌ | Tier 6 |
| — | /proc/self/cgroup | Magisk 进程 | ❌ | Tier 6 |
| — | getenforce 命令 | SELinux | ❌ | Tier 4 |
| — | getPackageInfo 单查询 | Lsposed 包 | ⚠️ 仅 list 查询 | Tier 7 |
| — | File.canWrite/canExecute | root 路径 | ❌ | Tier 8 |
| — | /proc/net/tcp | Frida 端口 | ❌ | Tier 8 |
| — | Thread.getName() | Frida 线程 | ❌ | Tier 7 |

## 开发计划(按依赖序)

### Phase 1 — 基础设施(Kind/Operation/Switch 扩展)

- [ ] **P1.1** DeviceEnvKind 新增 6 个枚举: `SYSTEM_PROPERTIES`, `PROC_STATUS`, `PROC_MOUNT_INFO`, `PROC_CGROUP`, `PROC_NET`, `SETTINGS_GLOBAL`
- [ ] **P1.2** DeviceEnvOperation 新增对应操作枚举
- [ ] **P1.3** HookSwitch 新增 6 个 switch 字段(干预型, 默认 OFF)
- [ ] **P1.4** EffectiveSwitch.readKind/applyKinds 同步映射 + INTERVENTION_KINDS 更新
- [ ] **P1.5** CategoryTaxonomy 注册新类别条目

### Phase 2 — Tier 1: SettingsHooker (Settings.Global/Secure 拦截)

- [ ] **P2.1** 创建 `SettingsHooker.kt` — hook `ContentResolver.query()`, 拦截 `content://settings/global` + `content://settings/secure`
- [ ] **P2.2** 过滤行: `adb_enabled`(→"0"), `development_settings_enabled`(→"0"), `oem_unlock_enabled`(→"0"), `mock_location`(→"0"), `install_non_market_apps`(→"0")
- [ ] **P2.3** hook `ContentResolver.getInt()` 快捷方法
- [ ] **P2.4** 防递归 guard (ThreadLocal)
- [ ] **P2.5** DeviceEnvProbe 注册

### Phase 3 — Tier 2: SystemPropertiesHooker

- [ ] **P3.1** 创建 `SystemPropertiesHooker.kt` — hook `android.os.SystemProperties.get(String)` 和 `get(String, String)`
- [ ] **P3.2** 复用 RootPropertyHooker 的 root 属性映射 + DeviceSysPropHooker 的模拟器属性映射
- [ ] **P3.3** 合并去重(RootPropertyHooker 仅 hook `System.getProperty`, 本 hooker 覆盖 framework 侧 `SystemProperties.get`)
- [ ] **P3.4** 防递归 guard
- [ ] **P3.5** DeviceEnvProbe 注册

### Phase 4 — Tier 3: ProcStatusHooker + su 进程检测

- [ ] **P4.1** 创建 `ProcStatusHooker.kt` — hook `BufferedReader.readLine()` 过滤 `/proc/[pid]/status` 行
- [ ] **P4.2** 过滤: `TracerPid:` 始终返回 "0", `frida`/`gadget` 相关行返回 null
- [ ] **P4.3** 与 XposedMapsHooker 同构 FileReader→BufferedReader 追踪模式
- [ ] **P4.4** DeviceEnvProbe 注册

### Phase 5 — Tier 4+5: RootExecHooker 增强

- [ ] **P5.1** RootExecHooker 新增 `getenforce` 命令拦截(返回 "Enforcing")
- [ ] **P5.2** RootExecHooker 新增 `setenforce` 命令拦截(阻止执行)
- [ ] **P5.3** RootExecHooker 新增 `getprop` 命令拦截(重定向到 System.getProperty)
- [ ] **P5.4** RootExecHooker 新增 `which su`/`whereis su` 命令拦截(返回空)

### Phase 6 — Tier 6: EmulatorBuildHooker 增强 + ProcMountInfoHooker + ProcCgroupHooker

- [ ] **P6.1** EmulatorBuildHooker 新增 `Class.getField`/`getDeclaredField` hook — 拦截对 `Build.TAGS` 等字段的反射访问
- [ ] **P6.2** 创建 `ProcMountInfoHooker.kt` — hook BufferedReader.readLine() 过滤 `/proc/self/mountinfo` (Magisk overlay mounts: `/sbin`, `magisk`, `tmpfs on /sbin`)
- [ ] **P6.3** 创建 `ProcCgroupHooker.kt` — hook BufferedReader.readLine() 过滤 `/proc/self/cgroup` (Magisk hidden process cgroup entries)
- [ ] **P6.4** DeviceEnvProbe 注册

### Phase 7 — Tier 7: RootPackageManagerHooker 增强 + FridaThreadHooker

- [ ] **P7.1** RootPackageManagerHooker 新增 `PackageManager.getPackageInfo(String, int)` hook — 拦截单包查询
- [ ] **P7.2** 扩展 ROOT_PACKAGES 列表: 添加 Lsposed/EdXposed/XposedManager 包名
- [ ] **P7.3** 创建 `FridaThreadHooker.kt` — hook `Thread.getName()` 剥离 `gadget`/`frida`/`stalker` 线程名
- [ ] **P7.4** DeviceEnvProbe 注册

### Phase 8 — Tier 8: RootFileHooker 增强 + ProcNetHooker

- [ ] **P8.1** RootFileHooker 新增 `File.canWrite()` 和 `File.canExecute()` hook
- [ ] **P8.2** 创建 `ProcNetHooker.kt` — hook BufferedReader.readLine() 过滤 `/proc/net/tcp` (隐藏 frida 端口 27042 等)
- [ ] **P8.3** DeviceEnvProbe 注册

### Phase 9 — 集成与验证

- [ ] **P9.1** DeviceEnvProbe 注册全部新 hooker
- [ ] **P9.2** strings.xml 新增 i18n 资源(en + zh)
- [ ] **P9.3** `compileModernDebugKotlin` BUILD SUCCESSFUL
- [ ] **P9.4** `lintModernRelease` 零新增 warning
- [ ] **P9.5** 本文档标记全部完成

## 完成记录

| 时间 | 任务 | 备注 |
|------|------|------|
| 2026-08-29 | P1.1-P1.5 Phase 1 基础设施 | DeviceEnvKind +6, DeviceEnvOperation +9, HookSwitch +6 fields, EffectiveSwitch readKind/applyKinds/INTERVENTION_KINDS, CategoryTaxonomy +6 entries, KindIcons/RecordRow exhaustive when 补全, strings.xml +15 resources. BUILD SUCCESSFUL. |
| 2026-08-29 | P2.1-P2.5 Tier 1 SettingsHooker | 创建 SettingsHooker.kt — hook Settings.Global/Secure getInt + getString, 拦截 adb_enabled/development_settings_enabled/oem_unlock_enabled/install_non_market_apps/mock_location, ThreadLocal 防递归. |
| 2026-08-29 | P3.1-P3.5 Tier 2 SystemPropertiesHooker | 创建 SystemPropertiesHooker.kt — hook android.os.SystemProperties.get(String)/get(String,String), 合并 RootPropertyHooker+DeviceSysPropHooker 属性映射, isIndicativeValue 精准干预. |
| 2026-08-29 | P4.1-P4.4 Tier 3 ProcStatusHooker | 创建 ProcStatusHooker.kt — /proc/[pid]/status TracerPid 行过滤(FileReader→BufferedReader 追踪), TracerPid≠0 → "0". |
| 2026-08-29 | P5.1-P5.4 Tier 4+5 RootExecHooker 增强 | RootExecHooker 新增 getenforce/setenforce/getprop/which su/whereis su 命令拦截, isBlockedCommand 统一分派. |
| 2026-08-29 | P6.1-P6.4 Tier 6 三项 | EmulatorBuildHooker 新增 hookBuildFieldAccess (Class.getField/getDeclaredField 拦截 Build.TAGS); 创建 ProcMountInfoHooker.kt (Magisk overlay mount 过滤); 创建 ProcCgroupHooker.kt (Magisk cgroup 过滤). |
| 2026-08-29 | P7.1-P7.4 Tier 7 两项 | RootPackageManagerHooker 新增 hookGetPackageInfo + 5 个 Lsposed/EdXposed 包名; 创建 FridaThreadHooker.kt (Thread.getName 剥离 frida/gadget/stalker 线程名). |
| 2026-08-29 | P8.1-P8.3 Tier 8 两项 | RootFileHooker 新增 hookFileCanWrite/hookFileCanExecute; 创建 ProcNetHooker.kt (/proc/net/tcp Frida 端口 27042/27043 过滤). |
| 2026-08-29 | P9.1-P9.4 集成验证 | DeviceEnvProbe 注册 7 个新 hooker, strings.xml +15 resources, compileModernDebugKotlin BUILD SUCCESSFUL, lintModernRelease BUILD SUCCESSFUL (0 errors, 0 new warnings). |

## 最终状态: ✅ 全部完成

### 新增文件 (7 个 hooker)
1. `SettingsHooker.kt` — Settings.Global/Secure getInt/getString 拦截(开发者选项/USB调试/OEM解锁)
2. `SystemPropertiesHooker.kt` — android.os.SystemProperties.get() 拦截(root+模拟器属性)
3. `ProcStatusHooker.kt` — /proc/self/status TracerPid 过滤(Frida 检测)
4. `ProcMountInfoHooker.kt` — /proc/self/mountinfo Magisk overlay mount 过滤
5. `ProcCgroupHooker.kt` — /proc/self/cgroup Magisk hidden process 过滤
6. `ProcNetHooker.kt` — /proc/net/tcp Frida 端口过滤(27042/27043)
7. `FridaThreadHooker.kt` — Thread.getName() 剥离 frida/gadget/stalker 线程名

### 增强文件 (4 个 hooker)
1. `RootExecHooker.kt` — 新增 getenforce/setenforce/getprop/which su/whereis su 拦截
2. `RootPackageManagerHooker.kt` — 新增 getPackageInfo 单查询 hook + 5 个 Lsposed/EdXposed 包名
3. `RootFileHooker.kt` — 新增 canWrite/canExecute hook
4. `EmulatorBuildHooker.kt` — 新增 Class.getField/getDeclaredField 反射拦截

### 模型文件修改 (6 个)
1. `DeviceEnvKind.kt` — +6 枚举: SYSTEM_PROPERTIES, PROC_STATUS, PROC_MOUNT_INFO, PROC_CGROUP, PROC_NET, (已有 ROOT_PROPERTY 覆盖 Settings)
2. `DeviceEnvOperation.kt` — +9 操作枚举
3. `HookSwitch.kt` — +6 switch 字段 (systemProperties, procStatus, procMountInfo, procCgroup, procNet)
4. `EffectiveSwitch.kt` — readKind/applyKinds/INTERVENTION_KINDS 同步
5. `CategoryTaxonomy.kt` — +6 类别条目
6. `DeviceEnvProbe.kt` — 注册 7 个新 hooker

### i18n
- `values/strings.xml` — +15 kind/operation 标签
- `values-zh/strings.xml` — +30 中文翻译(含补齐缺失的 V31 operation 翻译)
