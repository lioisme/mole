# TASK: momo/牛头人 全量 ADB 测试 (V35 完整验收)

> 创建日期: 2026-08-29
> 环境: LDPlayer emulator-5554, Android 14 (SDK 34), Vector Xposed 2.2, Magisk (Zygisk)
> 通过标准: 核心检测项全绿 + 兼容性项标注为跳过(已知不可绕过项不阻塞)
> 最终产出: 检测报告截图 + 每项预期/实际对照表

---

## 一、设备环境基线(emulator-5554 实测)

| 维度 | 当前值 | momo 风险 | 我们的 Hooker |
|------|--------|-----------|-------------|
| SELinux | Permissive, `u:r:magisk:s0` | 🔴 | `RootExecHooker`(getenforce→"Enforcing") |
| su binary | `/system/bin/su` → `./magisk` | 🔴 | `RootFileHooker`(43 paths) |
| Magisk binaries | `/sbin/magisk`, magisk32, magisk64, resetprop, supolicy | 🔴 | `ProcMountInfoHooker` + `RootFileHooker` |
| Magisk mounts | `/proc/self/mountinfo` 9 entries (tmpfs magisk overlays) | 🔴 | `ProcMountInfoHooker` |
| Zygisk | `Magisk : zygisk64: pre specialize` 活跃 | 🔴 | **无专用 Hooker** |
| `install_non_market_apps` | **1** (已开启) | 🟡 | `SettingsHooker`(→0) |
| `adb_enabled` | 0 | ✅ | `SettingsHooker`(→0 保底) |
| `development_settings` | null | ✅ | `SettingsHooker`(→0 保底) |
| `oem_unlock` | null | ✅ | `SettingsHooker`(→0 保底) |
| TracerPid | 0 | ✅ | `ProcStatusHooker`(→"0") |
| LDPlayer model | `25102RKBEC` | 🟡 | `EmulatorBuildHooker` |
| LDPlayer build | `UQ1A.240205.07241537` | 🟡 | `EmulatorBuildHooker` |

---

## 二、执行记录

### Phase 0: 构建 + 安装 ✅ 完成
- `assembleModernDebug` → APK 安装到设备
- V35 全部 hooker 代码就绪

### Phase 1: 安装检测工具 ✅ 完成
- momo v4.4.1 (`io.github.vvb2060.mahoshojo`) — 通过 ghfast.top 代理下载
- NativeTest/牛头人 (`icu.nullptr.nativetest`) — 通过 b1.sxqingyi.com 下载
- 两个 APK 均已安装

### Phase 2: 配置 Vector Xposed 作用域 ✅ 完成
- Vector CLI (`/data/adb/lspd/cli`) 成功添加 3 个应用到 Modev 作用域
- `scope ls` 确认: `com.lioisme.modev, io.github.vvb2060.mahoshojo, icu.nullptr.nativetest`

### Phase 3: 启用干预型 Hooker ✅ 完成
- 通过 ADB 写入 `modev_settings.xml` per-app override 配置
- 修复 Bug1: `ProxyHideHooker` hook 抽象方法 `ProxySelector.select()` 导致异常传播，杀死全部探针安装
- 修复 Bug2: `HookInstaller` probe 循环无 try-catch，一个失败全部失败
- 修复 Bug3: Vector Xposed 不绑定 `XposedService` → RemotePreferences 返回 null → per-app override 无法读取 → 改为磁盘文件兜底
- 修复 Bug4: 磁盘兜底条件 `channel.currentConfig == HookSwitch.DEFAULT` 不满足（Vector 返回全局配置） → 改为始终尝试磁盘 override
- 最终确认: `DeviceEnvProbe` 成功安装，18 个干预型 hook 全部启用

### Phase 4: momo v4.4.1 全量检测 ❌ 失败

**momo 结果: 环境损坏 (黄色脸)**

**失败原因: "检测到破坏行为（例如 MagiskHide），其他检测无法继续。在生产环境中，这足够证明系统已被修改。"**

关键分析:
1. **这是 Magisk/Zygisk 层级的检测**，不是我们 hook 层级的问题
2. Momo 检测到 Zygisk 在 `specialize` 阶段对进程进行了操作 (`Magisk : zygisk64: pre specialize`)
3. 这意味着 Magisk/Zygisk 本身暴露了"系统已被修改"的证据
4. 我们的 Java 层 hooks 无法拦截 native 层的 Zygisk 检测
5. 一旦 momo 检测到 Zygisk/MagiskHide，**所有其他检测项被跳过**（"其他检测无法继续"）
6. 因此无法验证我们的 19 个 deviceenv hooker 是否有效

### Phase 5: NativeTest/牛头人 ⏸ 未执行
- 因 Phase 4 已确定平台级阻塞，nativetest 测试结果无参考价值

---

## 三、根本原因分析

### 阻塞层级: Magisk/Zygisk 平台层

```
┌─────────────────────────────────┐
│  Momo 检测引擎                   │
│  ├─ Zygisk/MagiskHide 检测 ← 🔴 │ ← 我们在这里被拦截
│  ├─ Root 检测                    │
│  ├─ Xposed 检测                  │
│  ├─ Emulator 检测                │
│  └─ 其他环境检测                  │
└─────────────────────────────────┘
         ↑ 无法到达
┌─────────────────────────────────┐
│  Modev V35 Hooks (Java 层)       │
│  ├─ DeviceEnvProbe               │
│  ├─ NetworkProbe                 │
│  ├─ UiInteractionProbe           │
│  └─ ProcessControlProbe          │
└─────────────────────────────────┘
         ↑ 在此之下
┌─────────────────────────────────┐
│  Magisk + Zygisk 平台层          │
│  └─ zygisk64 pre_specialize     │ ← 被 momo 识别为"破坏行为"
└─────────────────────────────────┘
```

### 关键限制
1. **Modev 是 Xposed 模块**，必须运行在 Xposed 框架（Vector）之上
2. **Vector 运行在 Magisk/Zygisk** 之上
3. **Momo 能检测到 Zygisk** 的存在和行为
4. **Java 层 hook 无法拦截 native 层 Zygisk 检测**
5. 这是所有基于 Magisk+Xposed 方案的**结构性限制**

### 可能的改进方向(超出 Modev V35 范围)
1. **KernelSU / APatch** 替代 Magisk (内核级 root，更难检测)
2. ** Shamiko / Zygisk Assistant** 隐藏 Zygisk 痕迹
3. **更早的注入时机** (native 层 hook，在 Zygisk specialize 之前)
4. **pTrace** 直接注入 (绕过 Xposed 框架)

---

## 四、代码修复记录(本次测试发现)

| Bug | 描述 | 修复 | 文件 |
|-----|------|------|------|
| Bug1 | `ProxyHideHooker` hook 抽象方法 `ProxySelector.select()` → `IllegalArgumentException` 传播杀死全部 probe 安装 | 添加 `Modifier.isAbstract` 检查 + 跳过 | `ProxyHideHooker.kt:143` |
| Bug2 | `HookInstaller` probe 安装循环无隔离，一个失败全部失败 | 每个 probe 独立 try-catch | `HookInstaller.kt` |
| Bug3 | Vector Xposed 不绑定 XposedService → RemotePreferences 返回 null → per-app override 配置无法下发 | 磁盘文件兜底 (`/data/local/tmp/modev_config_{pkg}.json`) | `HookInstaller.kt` |
| Bug4 | 磁盘兜底条件 `currentConfig == HookSwitch.DEFAULT` 不满足(Vector 返回全局配置) | 改为始终尝试磁盘 override | `HookInstaller.kt` |
| Bug5 | `EmulatorBuildHooker.hookBuildStaticFields()` 缺少 FINGERPRINT/MODEL/PRODUCT/MANUFACTURER | 添加到 fieldMap | `EmulatorBuildHooker.kt:49` |

---

## 五、完成状态

| Phase | 状态 | 结果 |
|-------|------|------|
| Phase 0 | ✅ 完成 | APK 构建安装成功 |
| Phase 1 | ✅ 完成 | momo + nativetest 安装成功 |
| Phase 2 | ✅ 完成 | Vector 作用域配置成功 |
| Phase 3 | ✅ 完成 | 18 个干预 hook 启用，磁盘配置兜底工作 |
| Phase 4 | ❌ 失败 | MagiskHide 被检测 → 平台级阻塞 |
| Phase 5 | ⏸ 跳过 | Phase 4 阻塞导致无法验证 |
| Phase 6 | ✅ 完成 | 5 个 Bug 修复 |
| Phase 7 | 📝 完成 | 本报告 |
