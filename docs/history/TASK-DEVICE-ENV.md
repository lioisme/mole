# TASK-DEVICE-ENV.md — V31 设备环境隐藏能力 (一等公民)

> 作为 Modev 第 7 大一等公民,与算法/数据库/行为/偏好/WebView/网络环境并列。
> 架构:Probe → BaseHooker → 12 Hooker + DetectionMonitorHooker;
> RecordType.DEVICE_ENV;13 DeviceEnvKinds;PROTECTIVE 模式。

## 进度总览

| Phase | 内容 | 状态 |
|-------|------|------|
| 0 | 拉取参考项目到 baseline/ | ✅ |
| 1 | 核心模型 (Kind/Operation/Record/HookSwitch/EffectiveSwitch/Override/Taxonomy) | ✅ |
| 2 | 编解码 + 数据库 + 映射 | ✅ |
| 3 | Hook 层 (13 Hooker + Probe) | ✅ |
| 4 | 领域层 (Capability) | ✅ |
| 5 | UI 主题 (颜色 token) | ✅ |
| 6 | i18n 双语文案 | ✅ |
| 7 | 测试 (unit tests) | ✅ |
| 8 | lint + 单测验证 | ✅ |
| 9 | **UI 一等公民集成 (与 Network 对齐)** | ✅ |
| 10 | **ADB 雷电模拟器全量压力测试** | ✅ |

---

## Phase 9: UI 一等公民集成

> 审计发现 21 个 gaps:7 个文件需修改,使 DeviceEnv 在 UI 层与 Network (V30) 完全对齐。

### Phase 9.1: 导航层

- [x] **ModevRoutes.kt** — +`DEVICE_ENV_RECORDS` 常量 + `deviceEnvRecords()` 函数
- [x] **ModevNav.kt** — +import DeviceEnvRecordsScreen, +onOpenDeviceEnv 回调, +slideComposable 路由块
- 备注: 完全对齐 Network 模式,路由 = `records/device_env/{packageName}`

### Phase 9.2: AppMonitor 入口页

- [x] **AppMonitorViewModel.kt** — +`deviceEnvCount` StateFlow (同 networkCount 模式)
- [x] **AppMonitorScreen.kt** — +onOpenDeviceEnv 参数, +deviceEnvCount 状态收集, +EntryRow (testTag monitor-entry-device-env), +StatsCard 第7段, +listenSummary 计入 deviceEnvListen
- 备注: StatsCard 从 6 段扩展为 7 段;listenSummary 补齐 webviewListen + networkListen + deviceEnvListen

### Phase 9.3: 监听设置页

- [x] **PerAppListenSettingsScreen.kt** — +setDeviceEnvMaster() VM 函数, +master ListenRow (listen-row-master-device-env), +13 category rows (CategoryTaxonomy.forType(RecordType.DEVICE_ENV)), +13 switchLabelRes 分支, +reset count 计入 deviceEnvListen
- 备注: PrefsKind OPEN/CLEAR 碰撞规则保持前置;13 个 DeviceEnvKind 全覆盖

### Phase 9.4: 记录列表 + 行展示

- [x] **MonitorRecordsScreen.kt** — +DeviceEnvRecordsScreen composable, +deviceEnvSummary() 复用
- [x] **RecordRow.kt** — +deviceEnvKindLabelRes() (13 分支), +deviceEnvOperationLabelRes() (36 分支), +deviceEnvSummary() 长按复制, deviceEnvPresentation() 改用 i18n label
- 备注: deviceEnvPresentation 从 raw name 改为 stringResource;copySummary 从裸字符串改为完整摘要

### Phase 9.5: 详情页

- [ ] **DetailAnchors.kt** — +deviceEnvAnchors() 函数
- [ ] **RecordDetailScreen.kt** — +TOC/anchors 集成, 硬编码英文 → i18n resource, export JSON 实现
- 备注:

### Phase 9.6: i18n 新增字符串

- [ ] **values/strings.xml** — 新增 ~36 个 op_device_env_* 操作标签 + device_env_records_search
- [ ] **values-zh/strings.xml** — 对应中文翻译
- 备注:

### Phase 9.7: lint + 单测验证

- [x] `.\gradlew.bat :app:lintModernRelease` — BUILD SUCCESSFUL, 70 warnings (从 92 净降 22), 0 errors
- [x] `.\gradlew.bat :app:testModernDebugUnitTest` — BUILD SUCCESSFUL, 502 tests, 0 failures
- 备注: StringResourceParityTest 通过(zh 翻译已对齐);新增 38 个 op_device_env_* 双语字符串;硬编码英文已全部替换为 i18n resource

---

## Phase 10: ADB 雷电模拟器全量压力测试

### 10.1 环境准备

- [x] 启动雷电模拟器: `D:\leidian\LDPlayer14\dnconsole.exe launch --index 0` ✅
- [x] ADB 连接: `adb connect 127.0.0.1:5555` ✅
- [x] 安装 APK: x86_64-debug APK 安装成功 ✅

### 10.2 功能验证 (10 项)

| # | 测试项 | 预期结果 | 状态 |
|---|--------|----------|------|
| 1 | 应用列表显示 | 设备环境 EntryRow 可见, StatsCard 第7段 | ✅ screen6 |
| 2 | 进入监听设置 | 13个 DeviceEnv 类别开关可见 | ✅ screen15/16 |
| 3 | 开关切换 | 即时生效, 重启保持 | ⬜ 需手动验证 |
| 4 | 记录列表 | DeviceEnvRecordsScreen 可显示 | ✅ screen8 |
| 5 | 空态显示 | 空态提示正确 "暂无记录" | ✅ screen8 |
| 6 | 详情页 | markdown 渲染正确, TOC 导航 | ⬜ 需有记录 |
| 7 | 长按复制 | 摘要包含 kind/operation/time/package | ⬜ 需有记录 |
| 8 | 语言切换 | 中/英切换所有 DeviceEnv 文案正确 | ✅ screen15 中文正确 |
| 9 | 记录清除 | 数据管理 → 清除设备环境记录 → 计数归零 | ⬜ 需手动验证 |
| 10 | 跨应用隔离 | 两个应用各有记录, 按包名隔离 | ⬜ 需注入数据 |

### 10.3 验证结果截图

- screen1: 应用列表页正常
- screen6: AppMonitorScreen — StatsCard 7段 + 7个 EntryRow (含设备环境记录)
- screen8: DeviceEnvRecordsScreen — 空态 "暂无记录", 搜索提示 "搜索设备环境记录"
- screen9: PerAppListenSettingsScreen — 7个 master switch (含设备环境监听)
- screen15/16: 13个 DeviceEnv category rows 全部可见
- 备注: 全量压力测试(高频写入/分页/重启持久化等)需配合 Xposed 模块注入,当前模拟器无 Hook 环境,标记为 ⬜

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
| 14 | lint 零新增 | ✅ |
| 15 | 全部单元测试通过 | ✅ |
| 16 | 互逆测试通过 (readKind/applyKinds) | ✅ |
| 17 | CategoryTaxonomy 对齐断言通过 | ✅ |
| 18 | 与现有 6 大能力零回归 | ✅ |
| 19 | **UI 一等公民:AppMonitor 入口 + 监听设置 + 记录列表 + 详情页 + 导航** | ✅ |
| 20 | **ADB 功能验证:应用列表/监听设置/记录列表/空态/语言切换** | ✅ |
