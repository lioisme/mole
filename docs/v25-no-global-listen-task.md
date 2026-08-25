# V25 任务文档 · 无全局监听配置 / 采集项每应用化 / 状态胶囊视觉整改

**项目**:Modev — Kotlin/Compose(M3) Xposed 监听模块
**基线**:V24 完成态(单测 412 绿 / 插桩 81 绿 / lint 0 error · 37 warning)
**状态图例**:[ ] 待办 · [~] 进行中 · [x] 已完成
**前置决策**(用户确认):五个采集微调项**每应用化**,不固化

---

## 0. 目标与非目标

### 目标
| # | 需求 | 决策结果 |
|---|------|----------|
| G1 | 移除「算法监听」「数据库监听」两个全局设置页的全部代码与功能;每个应用拥有自己的监听设置 | 页面/路由/入口/写入链路全删;默认配置收敛为内置最佳推荐值(`HookSwitch.DEFAULT`,测试强制与 CategoryTaxonomy 对齐);五个独占微调项并入每应用监听设置页 |
| G2 | 应用详情页状态胶囊「点紧挨文字」整改 | `StatusPill` 点↔文字 **0dp → 6dp**(对齐全 App 惯例),内边距对齐 TimelineHeader 惯例 |

### 非目标
- 不动 wire 协议(VERSION 恒 4,payload 恒为普通 `HookSwitch`)。
- 不动注入侧 `HookInstaller` 兜底读取逻辑(D2 键保留自愈)。
- 不重构每应用页既有交互范式(Switch 直拨、底部 sheet 输入沿用契约 4/13)。
- `capabilityRegistry`(V19 起死代码,零消费者)本期不顺带清理。

## D 决策记录

- **D1 微调项每应用化**:`AppListenOverride` 扩展 5 个可空字段(`logStack/maxBytesPerField/ignoreStackPrefixes/maxResultRows/maxResultChars`),null = 取内置默认;resolve 叠加;存储键/格式向后兼容。
- **D2 `switch_global` 键处置**:键保留为内部默认模板种子——init 强制写入 `HookSwitch.DEFAULT`(一次性清除存量用户自定义全局值),此后无任何 UI 写入路径;注入侧零改动,陈旧安装自愈。
- **D3 写入链路收口**:`SettingsRepository.set()` 与 `ConfigServer.broadcastEffectiveConfig()` 随全局页删除(死代码零容忍);定向推送 `pushToPackage` 保留。
- **D4 状态胶囊**:`spacedBy(6.dp)` + 内边距对齐 TimelineHeader 惯例(horizontal 10 / vertical 3)。

---

## 执行进度总览

### P0 基线验证
- [x] P0.1 全量单测 + lint 基线留档 — 单测 412 绿;lint 37 warning / 0 error(V24 收口报告)

### P1 模型层:微调项每应用化 + 写入链路收口
- [x] P1.1 AppListenOverride 扩展 5 可空字段(isDefault 八位判定/空表归零);EffectiveSwitch.resolve 叠加 5 字段(ignoreStackPrefixes 非空整表替换)
- [x] P1.2 SettingsRepository:删 set()/switch 快照;init 无条件 writer.set(DEFAULT) 重置(D2 迁移,陈旧安装自愈);KDoc 重述「默认配置 = 内置推荐值」
- [x] P1.3 ConfigServer:删 broadcastEffectiveConfig();KDoc 注明 pushToPackage 为唯一推送入口
- [x] P1.4 JVM 测试:JsonTest +4 例(选项 round-trip/null 省略/空表归零/畸形容错/遗留兼容);EffectiveSwitchTest +3 例(选项叠加/前缀整表替换/isDefault 边界);SettingsRepositoryTest 重写(init 强制重置迁移断言 ×3);SettingsRepositoryOverrideTest 重写(纯偏差语境 + 流重发射/恢复回落);ConfigServer 两文件广播用例删除
- [x] P1.5 单测绿

### P2 全局监听设置页全量退役
- [x] P2.1 删 6 文件:AlgorithmSettingsScreen/ViewModel、DatabaseSettingsScreen/ViewModel、androidTest 两屏测(共 660+ 行)
- [x] P2.2 导航收口:Routes 删 ALGORITHM_SETTINGS/DATABASE_SETTINGS + KDoc 图修订;Nav 删 import/接线/两 destination;SettingsScreen 功能区收敛二入口(数据管理/服务与诊断)+ KDoc;SettingsViewModel 清空为无流形态
- [x] P2.3 设施迁移:SettingsSheets.kt → ui/components/(包名+KDoc 更新,.NET API 字节级搬迁防中文毁损);SettingsRowItems.kt → ui/apps/PerAppSettingRows.kt(SwitchRowItem/OptionRowItem 存活,OptionRowItem 增 modifier 参数供 testTag)
- [x] P2.4 i18n(en/zh 各 6 键):删 settings_capability_enabled/disabled、algorithm/database_monitor_master_hint、algorithm/database_monitor_title;复用键(section_record_options/section_db_result_limits/db_max_result_*/switch_log_stack/capture_*)全部真实接线至每应用页
- [x] P2.5 FourScreensNavTest 手术:删两全局页导航腿与已删键引用(:239/:249-296),设置页改断言二入口;筛选菜单断言更新
- [x] P2.6 编译零残留(grep ALGORITHM_SETTINGS/DatabaseSettingsScreen 仅剩历史 docs)+ 相关测试绿

### P3 每应用监听设置页扩展(记录选项 + 结果闸门)
- [x] P3.1 分区扩展:「记录选项」(logStack ListenRow + maxBytesPerField/ignoreStackPrefixes OptionRow)、「结果闸门」(maxResultRows/maxResultChars OptionRow);恢复默认行计数含全部八类偏差
- [x] P3.2 VM:setters ×5(setLogStack/setMaxBytesPerField/setIgnoreStackPrefixes 空表归零/setMaxResultRows/setMaxResultChars);行值回显走 effective 流
- [x] P3.3 Sheet 接线:MaxBytesSheet/IgnoreStackSheet/IntLimitSheet×2(hide 动画后回调;limitValue ≤0 显 ∞);testTag listen-row-log-stack/max-bytes/ignore-stack/result-rows/result-chars
- [x] P3.4 插桩 PerAppListenSettingsTest +optionSheets_editCaptureLimits_andPersistDeviations(maxBytes→2048、resultRows→50 双链路 E2E)— 绿
- [x] P3.5 设备插桩绿(雷电 x64 emulator-5554 / Android 14)

### P4 状态胶囊视觉整改
- [x] P4.1 StatusPill:内部 Row spacedBy(6.dp) + padding(horizontal 10 / vertical 3)(消除 0dp 挤贴与 start6/end10 非对称补偿)
- [x] P4.2 一致性复核:FourScreensNavTest 几何断言(contentDescription 纵向成员关系)不受影响 — 全量回归绿

### P5 收尾与门禁终验
- [x] P5.1 AGENTS.md:契约 16 重述(V25 无全局配置收敛)、契约 12/13 修订(IntLimitSheet 入口移交/功能区二入口)、词汇表「默认配置」释义更新
- [x] P5.2 终验:单测 412 例全绿 + 设备插桩 78 例全绿(1 skip=debug-only)+ lint 0 error / 37 warning(基线持平,零 UnusedResources 新增)

---

## 1. 详细规格

### 1.1 模型层(P1)

```kotlin
// AppListenOverride(V25)新增 —— 全部可空,null = 取内置默认
val logStack: Boolean? = null,
val maxBytesPerField: Int? = null,
val ignoreStackPrefixes: List<String>? = null,
val maxResultRows: Int? = null,
val maxResultChars: Int? = null,
```

- isDefault 八位全空才为 true;resolve 追加五段 `?.let { copy }`;JSON Gson 默认省略 null。
- 迁移(D2):`SettingsRepository.init` 无条件 `writer.set(HookSwitch.DEFAULT)`。

### 1.2 退役清单(P2)

| 文件 | 动作 |
|------|------|
| AlgorithmSettingsScreen/ViewModel、DatabaseSettingsScreen/ViewModel | 整文件删除 |
| androidTest 两屏测 | 整文件删除 |
| ModevRoutes.kt:26-27 + KDoc | 删两常量、注释修订 |
| ModevNav.kt imports/接线/destinations | 删 |
| SettingsScreen.kt 两回调 + 功能区前两入口 | 删;SettingsViewModel.switchState 删 |
| SettingsSheets.kt → ui/components/(三 sheet 存活) | 迁移 |
| SettingsRowItems.kt → ui/apps/PerAppSettingRows.kt | 迁移 |

### 1.3 每应用页最终分区(P3)

```
总开关(算法/数据库) → 算法 12 类 → 数据库 5 类
→ 记录选项(section_record_options):记录堆栈 / 单字段字节上限(∞=≤0) / 忽略栈前缀
→ 结果闸门(section_db_result_limits):结果行数上限 / 结果字节上限
→ 操作:恢复默认配置(有偏差时渲染)
```

testTag:`listen-row-log-stack`、`listen-row-max-bytes`、`listen-row-ignore-stack`、`listen-row-result-rows`、`listen-row-result-chars`。

## 2. StatusPill 规格(P4)

现状(AppMonitorScreen.kt:303-322):内部 Row 无间距参数,点↔文字 0dp;padding start6/end10/top3/bottom3。

目标:`spacedBy(6.dp)` + `padding(horizontal = 10.dp, vertical = 3.dp)`。依据:全 App 点↔文字惯例 6dp(AppsScreen:447/LegendRow:441);几何敏感测试(FourScreensNavTest:406-446)仅断言纵向成员关系,安全。

## 3. i18n 台账

| 动作 | 键 |
|------|-----|
| 删除(en/zh) | settings_capability_enabled/disabled、algorithm_monitor_master_hint、database_monitor_master_hint、algorithm_monitor_title、database_monitor_title |
| 复用(不动) | section_record_options、section_db_result_limits、db_max_result_*、switch_log_stack、capture_* 、algorithm/database_monitor_master、section_monitor_kinds、switch_*(17) |
| 新增 | 无 |

## 4. 测试计划

JVM:JsonTest/EffectiveSwitchTest 扩展;SettingsRepository 两测重写(init 重置迁移);ConfigServer 广播用例修剪。
插桩:两屏测删除;FourScreensNav 手术;PerAppListenSettingsTest +2 例;全量回归。

门禁(P5):单测全绿 / 插桩全绿 / lint 0 error ≤37 warning / 弃用 API 零引入。

## 5. 工程纪律

- 改文件一律 edit/write 工具;每阶段完成即勾选 + 完成日志追加。
- 插桩前置 `ANDROID_SERIAL=emulator-5554`;gradle 重任务串行(--no-daemon -Xmx2048m)。

## 7. 完成日志(逐项追加)

- P0:基线确认 — 单测 412 例全绿;lint 37 warning / 0 error。
- P1:模型层完成 — AppListenOverride 扩展 `logStack/maxBytesPerField/ignoreStackPrefixes/maxResultRows/maxResultChars` 五可空字段(空前缀表归零);resolve 五段叠加;SettingsRepository 删 set()/switch 快照、init 无条件重置 switch_global 为内置默认(D2,历史全局自定义一次清除 + 远程镜像自愈);ConfigServer 删 broadcastEffectiveConfig(pushToPackage 成为唯一推送入口)。JVM 测试矩阵扩展后全绿。
- P2:全局页退役 — 两屏两 VM 两 androidTest 整删;Routes/Nav/SettingsScreen/SettingsViewModel 收口;SettingsSheets 迁 ui/components、SettingsRowItems 迁 ui/apps/PerAppSettingRows(.NET API 字节级搬迁,规避 PowerShell 中文毁损);i18n 双语各删 6 死键;FourScreensNavTest 导航腿手术。编译零残留。
- P3:每应用页扩展 — 新增「记录选项」「结果闸门」两分区(logStack 行内 Switch + 四个 sheet 数值行,≤0 显 ∞);VM setters ×5 即写即推;恢复默认计数覆盖八类偏差;插桩新增选项 sheet 双链路 E2E(maxBytes 2048 / resultRows 50 持久化断言)。
- P4:StatusPill 视觉整改 — 点↔文字 0dp → 6dp(spacedBy),padding 对齐 TimelineHeader 惯例(horizontal 10 / vertical 3);几何敏感测试不受影响,全量回归验证。
- P5:终验 — **单测 412 例全绿;设备插桩(雷电 x64 emulator-5554)78 例全绿(1 skip = debug-only);lint 0 error / 37 warning 与基线持平**。AGENTS.md 契约 16 重述(V25 无全局配置收敛)、契约 12/13 修订、词汇表更新。V25 全阶段收口。
