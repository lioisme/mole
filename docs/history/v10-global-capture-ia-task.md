# Modev V10 重构任务文档:全局捕获设置 · 稳态状态流 · 能力化架构 · 设置分级

> 文档状态:✅ 全部完成(2026-08-17,单测双 flavor 全绿,androidTest 双 flavor 实机通过)
> 质量基线:状态流稳态、事件驱动、信息架构分层、可测试性 —— 对齐 Google Material 规范与 Apple HIG 工程纪律。

---

## 1. 目标与非目标

### 1.1 目标
1. **零闪烁**:任何界面停留期间,无定时触发的整体重组、状态翻转、文字闪现。
2. **全局捕获配置**:算法捕获设置全局唯一,取消逐应用独立配置。
3. **能力化架构**:以「监听能力(Capability)」为顶层抽象,算法捕获降级为能力之一,为行为/交互/数据库/文件监听预留同构扩展位。
4. **设置分级**:设置页仅承载通用设置;专用设置(捕获/数据/诊断)进二级设置页。

### 1.2 非目标(本轮明确不做)
- 不实现任何新监听能力(行为/交互/DB/文件)——只建立抽象与扩展位。
- 不做数据库表结构破坏性迁移(仅加可选类型列,兼容旧数据)。
- 不重写注入侧 Hooker 业务逻辑(仅改配置读取路径)。

---

## 2. 总体架构设计

### 2.1 状态流契约(需求 1)
- 取消前台 5 秒无条件轮询;作用域同步改为:服务连接拉取 + 框架侧事件驱动 + 低频静默兜底。
- 刷新路径**不得产生 `Connecting` 中间态**(仅服务首次连接时出现);无内容变化不得发射(`distinctUntilChanged`)。
- 状态流任何发射必须代表「值真实变化」。

### 2.2 全局捕获配置(需求 2)
- 配置键收敛为单一 `switch_global`(旧 `switch_{pkg}` 一次性迁移)。
- 宿主握手后推送全局配置;注入侧读/监听全局键;配置与包名解耦。
- `SettingsRepository` 重构为全局单一配置仓库,删除 `flowFor/configuredPackages` 等按包 API。

### 2.3 能力化架构(需求 3)
- 新增 `domain/capability/` 抽象包:`Capability` / `CapabilityKind` / `CapabilityRegistry`。
- 算法捕获落地为 `CryptoCaptureCapability`;设置页「功能」区由注册表驱动(未来新能力自动出现入口)。
- 命名收敛:算法中心化命名(页面/路由/文案)改为中性记录语义。
- 数据/协议增加记录类型维度(默认 `crypto`,向后兼容)。

### 2.4 设置分级(需求 4)
- 设置页(通用):显示 | 语言与地区 | 功能(能力注册表驱动的二级入口)| 关于。
- 二级页:捕获设置(`settings/capture`)、数据管理(`settings/data`)、服务与诊断(`settings/diagnostics`)。
- 应用级危险操作(移出作用域/清空该应用日志)移入应用记录页语境。

---

## 3. 任务清单与执行状态

### 里程碑 M1:状态流稳态(需求 1)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 删除 `ModevNav` 5s 无条件轮询 | ✅ 完成 | 2026-08-17:移除 `SCOPE_POLL_INTERVAL_MS` 与轮询,改为 `repeatOnLifecycle(RESUMED) { syncForeground() }`(ModevNav.kt) |
| T2 | `ScopeRepository` 事件驱动重构:刷新无中间态 + 内容判等 + distinctUntilChanged | ✅ 完成 | `lastEmitted` 判等:无变化 return@flow 不发射;Connecting 仅服务首连/框架切换出现;`syncForeground()` = 立即一次 + 30s 静默兜底;新增 3 个稳态单测(含共享调度器 + 挂起式 fake 观测 Connecting 瞬时态) |
| T3 | `AppsViewModel` 输出内容判等去重 | ✅ 完成 | uiState 链尾部加 `.distinctUntilChanged()`(AppsViewModel.kt) |
| T4 | 依赖稳态流(随 M1 自动收敛) | ✅ 完成 | 记录列表/详情页均为 SQL+Paging 数据流,无轮询依赖;`syncForeground` 内容判等保证前台停留零重组 |

### 里程碑 M2:全局捕获配置(需求 2)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T5 | `SettingsRepository` → 全局单一配置(`switch_global` + 旧键迁移) | ✅ 完成 | 新增 `ConfigWriter`/`MirroredConfigWriter`(KEY=`switch_global`,读优先远程);迁移按字典序取首份旧键→写全局键→清理本地+远程旧键;全局键存在时跳过迁移但仍清理旧键(修 1 bug);`flowFor/configuredPackages` 等按包 API 已删 |
| T6 | `ConfigServer` 握手推送全局配置、`broadcastConfig` 全量广播 | ✅ 完成 | `configProvider` 改无参签名;握手后无包过滤全量推送;`broadcastConfig(switch)` 遍历全部客户端(载荷仍带会话包名,协议兼容);新增双客户端全量广播单测 |
| T7 | 注入侧读/监听全局键(`HookManager`/`RemotePrefsBridge`) | ✅ 完成 | `RemotePrefsBridge.GLOBAL_SWITCH_KEY = "switch_global"`;`HookManager` 直读+监听全局键 |
| T8 | 捕获设置页全局化,迁移路由 `settings/capture`,移除应用级危险操作 | ✅ 完成 | 新 `ui/settings/CaptureSettingsScreen.kt` + 独立 `CaptureSettingsViewModel`;入口收敛至设置页「功能」区;危险操作(移出作用域/清空该应用日志)移交 `AppRecordsScreen`「更多」菜单 |

### 里程碑 M3:能力化命名与抽象(需求 3)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T9 | `domain/capability/` 抽象包 + `CryptoCaptureCapability` | ✅ 完成 | `Capability`/`CapabilityKind`(CRYPTO_CAPTURE)/`CapabilityRegistry`(枚举序,all())/`CryptoCaptureCapability`(globalSwitch 投影);ModevApp 注册;设置页「功能」区驱动源 |
| T10 | `AlgorithmList*` 更名收敛为 `AppRecords*` | ✅ 完成 | `AppRecordsScreen.kt`/`AppRecordsViewModel`(Factory 同步);旧文件已删;`ModevRoutes.ALGORITHM_LIST→APP_RECORDS`;VM 移除 settings 依赖(配置已全局化) |
| T11 | 记录类型维度(DB 可选列 + 协议 type 字段,向后兼容) | ✅ 完成 | 新增 `core/model/RecordTypes.kt`(CRYPTO="crypto");`CryptoRecord.recordType` 默认 "crypto";DB v4 + `MIGRATION_3_4`(ALTER TABLE ADD COLUMN,默认 'crypto');`toModel/toEntity` 双向贯通;WireCodec 注释说明非 crypto 记录按非法丢弃(未来能力分发接管) |
| T12 | 文案去算法中心化 | ✅ 完成 | en/zh:`app_description` 改为多能力定位;`detail_search` → "搜索记录…";`detail_tab_settings` 删除(新 `capture_settings_title`);新增 功能/数据管理/服务与诊断 等新键;`section_algorithms` 保留(捕获页内上下文准确) |

### 里程碑 M4:设置分级(需求 4)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T13 | 设置页收敛通用分区;数据/诊断迁入二级页 | ✅ 完成 | `SettingsScreen` 收敛为:显示 | 语言与地区 | 功能(注册表驱动 + 数据管理/服务与诊断入口)| 关于;新建 `DataSettingsScreen`/`DiagnosticsScreen` + 各自 ViewModel/Factory(路由 `settings/data`、`settings/diagnostics`);`UiMessage` 迁出至 `ui/common/` 供多页复用 |
| T14 | 全局捕获设置二级页接入(能力注册表驱动入口) | ✅ 完成 | 「功能」区 `capabilities.forEach` 渲染,图标/标题/副标题映射私有函数,`when(kind)` 路由;新增能力零改动自动出现入口 |
| T15 | 应用级危险操作移入 `AppRecordsScreen` | ✅ 完成 | 头部「更多」菜单:移出作用域(仅作用域时显示)/清空该应用日志,均带确认 Dialog;`clearLogs` 成功补 Snackbar(`apps_logs_cleared`) |
| T16 | 应用列表置顶开关归位筛选菜单 | ✅ 完成 | `ScopeFilterMenu` 增加「已作用域应用置顶」项(AppListSettings 单一事实源,两处菜单同步);`settings_apps_section` 字符串删除 |

---

## 3.1 测试与验证记录(2026-08-17)

- 单测:双 flavor(modern/legacy)全量通过。新增/更新:
  - `ScopeRepositoryTest`:+3(无变化不发射·实例稳定、刷新路径零 Connecting、首连 Connecting 瞬时态可观测)
  - `ConfigServerTest`:+1(全量广播至多客户端)
  - `SettingsRepositoryTest`:新文件 +4(旧键迁移与清理、全局键优先、无旧键回落默认、set 持久化)
  - `CryptoRecordDaoTest`:迁移测试补 `MIGRATION_3_4` 链(v1/v2 → v4)
- androidTest(API 34 模拟器,**modern 与 legacy 双 flavor 实机全通过**,26 用例/各 1 个数据依赖跳过):
  - `FourScreensNavTest` 重写适配 V10 路由集。实机适配(修复后):
    - 启动按钮仅对可启动应用渲染 → 循环逐个进入记录页寻找,可见行不可启动时上滑列表(模拟器首屏常为系统组件),找不到则 assume 跳过
    - 移出作用域仅已作用域应用显示 → 条件断言(清空该应用日志恒在)
    - 「设置」与同名应用行歧义 → `hasText + hasAnyAncestor(isPopup())` 限定弹层
    - 捕获页记录选项区在 LazyColumn 折叠区 → `performScrollToNode` 滚入可见
  - 修复 **legacy 变体启动崩溃(存量真 bug,实机测试暴露)**:`FrameworkBridge.serviceFlow` 公开类型原为 `StateFlow<XposedService?>`,消费方 lambda 生成的 checkcast 在 legacy 运行时(缺 libxposed-service 库)即使空值路径也触发 `NoClassDefFoundError`(宿主启动即崩)。修复:`serviceFlow` 公开为 `StateFlow<Any?>`;转型仅放非空分支(`ModevApp` 令牌块 / `scopeGateway` / `ModevModule` 远程偏好提供器),legacy 空值路径零类解析,自然降级不启用令牌认证。
- 编译:modern/legacy 双 flavor `compileDebugKotlin` + androidTest 编译全绿;lint 双 flavor 0 错误(32 条既有风格警告,非本次新增)。
- 遗留警告(非新增):`WireCodec.kt:81` 冗余判空、`ScopeRepository.kt:53` flatMapLatest opt-in 等。

---

## 4. 验收标准(逐需求、可测试)

**验收 1(零闪烁)**
- 应用列表页停留 ≥ 2 分钟:复选框无禁用闪烁、无行文字/图标翻转;Logcat 无周期性状态日志。
- 记录列表页停留:状态 chip 无「Reading scope…」周期性闪现。
- 自动化:`ScopeRepositoryTest` 断言 refresh 无变化时 `state` 不发射;UI 测试无重组风暴。

**验收 2(全局捕获设置)**
- 设置 → 捕获设置修改任一开关,作用于所有已作用域应用(注入侧即时生效,无需重启)。
- 存储断言:SharedPreferences/RemotePreferences 中仅存在 `switch_global`,无 `switch_*` 遗留。
- 单测:`ConfigServerTest`(握手推送全局配置)、`HookSwitchTest` 更新。

**验收 3(能力化定位)**
- 代码评审:新增能力只需实现 `Capability` 抽象 + 注册,无需改动路由/设置/通道主链路。
- 全仓检索:`AlgorithmListScreen`/`AlgorithmListViewModel` 不存在;`CryptoKind` 等保留但仅作 crypto 能力内部类型。
- 协议兼容:旧注入模块连接新版宿主仍可收发记录。

**验收 4(设置分级)**
- 设置页仅含:显示、语言与地区、功能(二级入口)、关于。
- 二级页可达性:设置 → 捕获设置 / 数据 / 服务与诊断,返回均弹栈。
- 导航测试(`FourScreensNavTest`)更新覆盖全部新路由。

---

## 5. 测试与质量门禁
- 单测:ScopeRepository、全局配置仓库、ConfigServer、WireCodec(兼容)、迁移逻辑覆盖新路径。
- androidTest:导航路由集、设置分级、捕获设置全局化。
- 门禁:全量单测通过、lint 零新增告警、编译产物可装配。
- 回归:语言热切换、主题切换、下拉刷新、搜索交互不回归。
