# V23 任务书:统一记录中心 · 每应用监听设置 · 应用详情重构

> 状态:**DONE(2026-08-24)** | 前置:V22 | 基线:commit @2026-08-24
> 关联:`docs/record-layer-redesign.md`(V22 记录数据层一体化)、`docs/v21-settings-redesign-split-task.md`
> 词汇遵循 AGENTS.md 词汇表(记录 / 数据库监听 / 运行中 / 已作用域)。
> 进度标记:每项完成后勾选 `[x]` 并附「✅ 备注」。
>
> **验收结果**:全量单测 425 用例全绿;`lintModernRelease` 0 errors / 37 warnings(<基线 38);
> androidTest 编译通过(插桩用例待设备执行);协议 wire 零变更(VERSION=4)。
>
> **设备实测(雷电模拟器 emulator-5554,Android 14 / x86_64,2026-08-24)**:
> `connectedModernDebugAndroidTest` **83 用例:82 通过 / 1 跳过(FourScreensNavTest assumeTrue 防空机设计)/ 0 失败**;
> 修复 4 类设备测试问题:①屏外应用行需滚动定位(AppMonitorRedesign/PerAppListenSettings);
> ②V23 重构后旧断言残留(更多菜单退役→操作卡+作用域 Switch;pressBack 关弹层语义误用);
> ③存储值断言对齐 DB+wal+shm 新口径 + 异步流轮询;
> ④设备版 `DatabaseProbeTest` setUp 显式全开开关(与 JVM 版同款 V18 默认值适配)。
> ADB 冒烟:UI 遍历 5 步全过(全局记录页/设置页/数据管理页进出)+ 进程存活 +
> logcat 全程零 FATAL。

---

## 1. 背景与审计结论

对 hook/codec/framework/domain/data/ui 七个子树全量审计后的现状判定:

| # | 需求 | 现状基线 | Gap |
|---|---|---|---|
| R1 | 记录统一存放 + 按应用/类型/类别筛选 | 数据层**已就绪**:单表 `crypto_records` v8、`RecordFilter`(recordTypes/packages/kinds/operations/query/timeRange 多值 IN + 哨兵计数)、`RecordService` 契约面(MCP-ready) | UI 无全局记录页;两列表页被路由锁死为单应用×单类型 |
| R2 | SQLite3 高效规范存储 | Room 2.7.2 / 61 列多态单表 / 6 索引 / v1→v8 迁移链 / 批量写 32条·200ms / REPLACE 幂等 / 双层裁剪(全局 20000·每包 5000)/ `fallbackToDestructiveMigration(false)` | 达标,仅需加固(WAL 显式化、事务注解、存储占用口径、越界引用收敛) |
| R3 | 每应用自定义监听 | **不存在**。V10 曾主动移除 per-app 配置(`switch_{pkg}` → `switch_global`) | 全链路新建:三态继承模型 → 远程偏好 → config 定向推送 → 注入侧消费 → UI |
| R4 | UI/功能完全分离 | domain 接口化 + `PackageBoundaryTest` 三规则硬门禁,UI 零 import hook/data.server | 3 处残留:DataSettingsScreen 直读 DB 文件、AppsScreen 直调 `AppListSettings` 单例 setter、ScopeState→状态色映射在两处 UI 重复 |
| R5 | 应用详情页重构 | `AppMonitorScreen.kt` 466 行(Screen+VM+Factory 混装),头部行+双行入口卡,含死代码 `globalSwitch` | 整页重设计 + VM 拆分 |

## 2. 目标与非目标

**目标**
- G1 全局记录页:跨应用/跨类型统一浏览,筛选维度 = 应用(多选)+ 记录类型 + 类别 + 关键字(timeRange 字段保留给 MCP,本期不暴露 UI)。
- G2 存储加固:显式 WAL、批量插入事务注解、存储占用经仓库出口、EXPLAIN QUERY PLAN 回归探针。
- G3 三态继承式每应用监听设置:17 类别 + 2 总开关各自「跟随全局/强制开/强制关」,改全局自动传导未覆盖项;协议 wire 格式**零变更**(版本保持 4)。
- G4 解耦闭环:`PackageBoundaryTest` 新增规则(ui 禁 import data.db),消除全部现存越界调用。
- G5 应用详情页按 §4.6 规格重构,新增「监听设置」入口承载 G3。

**非目标**
- MCP server 实现(仅继续强化 RecordService 契约,backlog);
- FTS5 全文索引(20000 行上限内 LIKE 全表扫 <10ms,不做,见 ADR-6);
- 底部导航 IA 重排(维持单栈 + 四界面主链);
- 旧两列表页路由退役(本期以共享组件收敛实现,路由收敛留后续版本)。

## 3. 架构决策记录

| # | 决策 | 理由与代价 |
|---|---|---|
| D1 | **三态继承模型**:`TriState{INHERIT,ON,OFF}` + `AppListenOverride`,resolve 产物仍是普通 `HookSwitch` | 未覆盖项随全局自动传导;注入侧与 wire 协议零感知 TriState,modern/legacy 双变体天然工作。代价:resolve 纯函数需全分支单测 |
| D2 | **override 存储 = 每包一键** `override_{pkg}`(本地 SP `modev_settings` + 远程偏好双写),惰性创建,全 INHERIT 即删键 | 与 V10 已清理的旧 `switch_{pkg}` 前缀隔离;避免数百包全量键膨胀;复用 MirroredConfigWriter 双写通道 |
| D3 | **config 推送改为 per-session effective**:onHello 与任何配置变更时,服务端按 `session.packageName` resolve 后下发 | 协议载荷 `{packageName, switch}` 字段原样利用;注入侧消费路径零改动。装配上 ConfigServer 经回调 `effectiveResolver: (String) -> HookSwitch` 取数,避免 server↔settings 循环依赖 |
| D4 | **注入侧兜底升级**:HookInstaller 直读 `switch_global` 后追加读 `override_{selfPkg}` 并 resolve | 模块 App 未运行场景尽量尊重 per-app;legacy 变体无远程偏好 runCatching 自然跳过 |
| D5 | **单表保留 + 加固**(用户确认):不拆表、不冷热分离 | 列表投影已 substr 预览、详情才全量物化,稀疏列无实际 IO 惩罚;拆表将作废 v8 迁移链、RecordFilter 契约与 RecordPagingProbeTest 全链 |
| D6 | 全局记录页筛选:**类型单选(默认全部),类别菜单仅单选类型时启用**;应用多选走 ModalBottomSheet 选择器;关键字沿用 SQL LIKE;timeRange 本期不暴露 UI | 跨类型 + 类别混选 UX 混乱;符合 AGENTS.md 强约束 12 双组单选语义延伸 |
| D7 | 列表渲染收敛为共享组件 `RecordTimelineList`(filter 驱动)+ 单一 `RecordsViewModel`,新旧三页共用 | 消除三套列表实现;旧页退化为预置 filter 薄壳 |
| D8 | 详情页入口路由本期不变(`record/{id}`);`records/algorithm/{pkg}`、`records/database/{pkg}` 保留 | 控制爆炸半径;FourScreensNavTest 主链零回归 |

## 4. 详细设计

### 4.1 领域模型(新增 `core/model/`)

```kotlin
// TriState.kt(新)
enum class TriState { INHERIT, ON, OFF }

// AppListenOverride.kt(新,Gson 可序列化,缺字段补 INHERIT)
data class AppListenOverride(
    val cryptoListen: TriState = TriState.INHERIT,
    val dbListen: TriState = TriState.INHERIT,
    val kindOverrides: Map<String, TriState> = emptyMap(),  // key = CryptoKind.name / SqlKind.name
) {
    val isDefault: Boolean          // 全 INHERIT
    fun toJson(): String
    companion object {
        val DEFAULT: AppListenOverride
        fun fromJson(json: String?): AppListenOverride   // 非法 JSON / 未知 kind 名 → 忽略该项(runCatching)
    }
}

// EffectiveSwitch.kt(新,纯函数对象)
object EffectiveSwitch {
    /** INHERIT 取 global 值;ON/OFF 强制覆盖。未知 kind 名忽略。 */
    fun resolve(global: HookSwitch, override: AppListenOverride?): HookSwitch
}
```

`HookSwitch` 本体**不改**(wire 兼容);`anyEnabled` 等派生属性在 resolve 产物上自动正确。

### 4.2 存储层(`data/repo/SettingsRepository.kt` 扩展)

```kotlin
fun overrides(): StateFlow<Map<String, AppListenOverride>>        // 内存态,init 时全量加载
fun overrideFor(pkg: String): AppListenOverride                    // 缺省 DEFAULT
fun effectiveSwitch(pkg: String): HookSwitch                       // resolve(globalSwitch.value, overrideFor(pkg))
suspend fun setOverride(pkg: String, o: AppListenOverride)         // o.isDefault → 走 removeOverride;否则双写 override_{pkg} + 内存更新
suspend fun removeOverride(pkg: String)                            // 删本地+远程键 + 内存更新
```

副作用:`setOverride/removeOverride` 后通知 ConfigServer 定向推送(§4.3);`set()`(全局)后由 server 对**每个 session** 各自 resolve 下发。MirroredConfigWriter 泛化为读写任意键(现硬编码 GLOBAL_SWITCH,抽出 `put/get(key)` 内核)。

### 4.3 传输层(`data/server/ConfigServer.kt`)

```kotlin
// 新增装配回调(ModevApp.AppContainer 接线,防循环依赖)
var effectiveResolver: ((String) -> HookSwitch)? = null

// 行为变更
onHello(session)      → push(resolve(effectiveResolver(session.packageName)))   // 原:全局一份
broadcastConfig()     → for each session: push(resolver(session.packageName))   // 原:同一份广播
pushToPackage(pkg)    → 仅匹配 session 推送其 effective                          // 新增,setOverride 调用
```

wire 编解码(`WireCodec.config`)零变更,`WireProtocol.VERSION` 保持 4;`ConfigServerTest` 扩展双包 session 隔离断言。

### 4.4 Room 加固(`data/db/`)

| 项 | 内容 |
|---|---|
| WAL | `Room.databaseBuilder(...).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)`(AUTOMATIC→显式,防 OEM 差异) |
| 事务 | `insertAll` 显式 `@Transaction`;`trim/trimPackage/deleteByFilter` 评估补注 |
| 存储占用 | 仓库出口 `LogRepository.storageUsage(): Long`(suspend,DB + `-wal` + `-shm` 三文件之和);DataSettingsScreen 弃直读 `ModevDatabase.NAME` |
| 索引验证 | Robolectric 探针:主查询路径 `EXPLAIN QUERY PLAN` 断言走既有索引,防未来索引退化 |

### 4.5 全局记录页(新 `ui/records/GlobalRecordsScreen.kt`)

- 路由:`records`(常量 `ModevRoutes.RECORDS_ALL`);入口 = AppsScreen 顶栏搜索条操作区新增「全部记录」TipIconButton;设置页功能区**不设**第二入口。
- `RecordsViewModel`(重构 `MonitorRecordsViewModel` 泛化):唯一状态源 `RecordFilterUiState(recordTypes, packages, kindName, query)` → combine → `flatMapLatest { recordService.observe(filter) }` → `cachedIn`;D6 联动规则在 VM 归一化(kindName 仅当单类型时进 filter)。
- 共享组件 `RecordTimelineList`(抽自 `MonitorRecordsScreen.kt`):手动索引循环、时间线分组、append/prepend 三态、VM 级 LazyListState——三页共用。
- 应用多选选择器:`ModalBottomSheet(containerColor=surfaceContainerHigh)` + 包内搜索 + Checkbox 列表,数据源 `recordService.observePackages()`;激活筛选以 FilterChip 行展示。
- 计数:标题副标题显示 `count(filter)`(`AppCountFormat.compact`)。

### 4.6 应用详情页重构(`ui/apps/AppMonitorScreen.kt` 整改)

```
ModevTopBar(应用名,返回)
├ Hero 卡(surfaceContainerLow,16dp 圆角)
│   AppIcon 56dp · 应用名 titleLarge · 包名 bodySmall
│   状态徽章行:ScopeBadge(已作用域/未作用域/读取中) + RunBadge(运行中/离线)   ← StatusColorTokens
│   ListItem:作用域开关 Switch(trailing,pending 态禁用)
├ 统计概览卡
│   大数字总记录数(displaySmall,AppCountFormat.compact)
│   类型分布双色比例条(CryptoRow/SqlRow 权重,KindColorTokens 语义色)+ 图例计数
├ 功能入口卡(SettingsSection,ListItem×3 + chevron)
│   算法记录 › count   |   数据库记录 › count   |   监听设置 › 「跟随全局」/「自定义 N 项」
├ 操作卡
│   启动应用(launchable 才渲染)| 清空该应用记录(error 色,居中 AlertDialog 确认)
```

工程整改:VM 拆出 `apps/AppMonitorViewModel.kt`(删死代码 globalSwitch);ScopeState→StatusKind 映射抽 `ui/common/StatusSemantics.kt` 与 AppsScreen 消重;testTag 补齐(`app-scope-switch`/`app-stats-card`/`monitor-entry-listen-settings` 等);所有 ListItem 显式 containerColor(AGENTS.md 强约束 13)。

### 4.7 每应用监听设置页(新 `ui/settings/perapp/PerAppListenSettingsScreen.kt`)

- 路由:`apps/{packageName}/listen-settings`;详情页「监听设置」入口。
- 结构:说明行 + 总开关区 2 行 + 算法 12 行 + 数据库 5 行;每行 trailing = 态文本 TextButton(跟随/开/关)→ 点击弹 ModalBottomSheet 单选(RadioButton 角色);顶部「重置为跟随全局」动作(确认后 removeOverride)。
- 生效链路:setOverride → SettingsRepository → ConfigServer.pushToPackage → 注入侧 currentConfig 更新,Probe 下一次调用生效。

### 4.8 解耦收紧(R4)

| 项 | 动作 |
|---|---|
| 边界测试 | `PackageBoundaryTest` 新增规则:ui.\* 禁 import `data.db.*`;豁免名单保持空 |
| DataSettings | 存储占用改走 VM(repo 出口);顺带修 remember 快照竞态注释 |
| AppsScreen | `AppListSettings.set*` 直调改经 `AppsViewModel.setListPreference(...)`(object 单例留在 data/repo,仅调用路径过 VM) |
| 主题/语言 | `ThemeSettingsManager`/`LocaleManager` 由 UI 直 collect 属 V9 既定约定,ADR 记录豁免理由,不收敛 |

## 5. i18n 规划(en + zh 同步,`StringResourceParityTest` 门禁)

新键约 50 个:`records_all_*`、`filter_app_chip`、`app_picker_*`、`app_monitor_scope_switch`、`stats_total`、`distribution_legend_*`、`listen_settings_*`、`tri_inherit/tri_on/tri_off`、`reset_confirm_*`、`cd_*` 若干。占位符形态双语一致;计数文案走既有 plurals 规则(强约束 6)。

## 6. 测试计划

| 层 | 用例 |
|---|---|
| JVM 纯函数 | `EffectiveSwitchTest`(3³ 分支×未知 kind 忽略×isDefault)、`AppListenOverrideJsonTest`(缺字段/非法 JSON/往返) |
| Robolectric | `SettingsRepositoryOverrideTest`(双写/删键/isDefault 删除语义/旧键隔离)、`ConfigServerOverrideTest`(双包 session 各自 effective、定向推送、resolver 未装配降级全局)、`StorageUsageTest`、`ExplainQueryPlanProbeTest`、`PackageBoundaryTest` 新规则 |
| 插桩 | `GlobalRecordsScreenTest`、`AppMonitorRedesignTest`、`PerAppListenSettingsTest`、`FourScreensNavTest` 回归 |
| 门禁 | `lintModernRelease` 零新增(基线 38 warning)、`testModernDebugUnitTest` 全绿 |

## 7. 验收标准(逐需求)

- **R1** 从应用列表顶栏进入全局记录页;不筛时展示全部应用两类记录的时间线;应用多选/类型/类别/关键字四维任意组合结果与 `RecordService.count` 一致;120 条种子翻页到底。
- **R2** DB 以 WAL 模式打开;批量插入单事务;`storageUsage()` = DB+wal+shm 且设置页数值与之相等;EXPLAIN 探针绿。
- **R3** 应用 A 设「CIPHER 强制关」,A 进程下一次 Cipher 调用不再产生记录,其他应用不受影响;全局关闭 CIPHER 后未覆盖应用同步停止;重置后恢复;杀掉模块 App 进程后 A 的 override 仍生效(注入侧兜底)。
- **R4** ui 包零 `data.db` import(测试门禁);设置页存储值来自仓库;应用列表偏好经 VM 变更。
- **R5** 详情页呈现 Hero/统计分布/三入口/操作区;「监听设置」行副标题正确反映 跟随全局/自定义 N 项;危险操作均居中 AlertDialog;i18n 双语齐备;lint 零新增。

## 8. 实施顺序与任务追踪

### M1 领域与存储
- [x] T1.1 `core/model/TriState.kt` ✅ 含 ofName 容错(null/未知回落 INHERIT)
- [x] T1.2 `core/model/AppListenOverride.kt` ✅ 手动 JsonObject 解析:未知 kind 名白名单过滤、非法三态名降级、normalized 剔除 INHERIT 条目、isDefault 即删键语义
- [x] T1.3 `core/model/EffectiveSwitch.kt` ✅ 17 类别平铺字段映射;总开关 OFF 仍压制强制开的类别(HookSwitch.enabled 语义不破坏);null/default 覆盖零拷贝短路
- [x] T1.4 SettingsRepository overrides API ✅ ConfigWriter 泛化 put/remove/getLocal/localKeysWithPrefix;override 键本地权威(区别全局键读优先远程)、双写远程镜像;setOverride 全默认即删键;effectiveSwitchFlow 组合流;init 预载过滤非法 JSON
- [x] T1.5 `LogRepository.storageUsage()` ✅ DB+wal+shm 三文件和,构造注入 databaseFile provider,缺省返回 0;ModevApp 装配接线
- [x] T1.6 单测 ✅ EffectiveSwitchTest(11 用例)、AppListenOverrideJsonTest(9)、SettingsRepositoryOverrideTest(6)、StorageUsageTest(3)全绿

### M2 传输层
- [x] T2.1 ConfigServer per-session resolve ✅ configProvider→effectiveResolver(按包解析单一机制);onHello 推该包有效配置;broadcastConfig(switch)→broadcastEffectiveConfig()(每会话独立 resolve,防全局值冲刷已覆盖应用);新增 pushToPackage 返回推送数
- [x] T2.2 ModevApp 装配接线 ✅ effectiveResolver = settings::effectiveSwitch;SettingsRepository.set 改调 broadcastEffectiveConfig;setOverride/removeOverride 后定向推送
- [x] T2.3 注入侧兜底 ✅ HookInstaller 读 switch_global + override_{selfPkg} 经 EffectiveSwitch.resolve 后 updateConfig;监听器改为任一键变化全量重读(规避远程偏好新增键事件粒度不可靠风险);legacy 变体自然跳过
- [x] T2.4 测试 ✅ ConfigServerOverrideTest(4 用例:握手按包隔离/pushToPackage 定向+零泄漏/broadcast 每包各自值/resolver 缺失降级);ConfigServerTest 既有 8 用例迁移新 API 全绿;WireCodec 协议零变更(VERSION 保持 4)

### M3 解耦前置(R4)
- [x] T3.1 PackageBoundaryTest 新增 ui 禁 import `data.db.*` ✅ 规则 R3(V23),豁免名单保持空
- [x] T3.2 DataSettingsScreen 存储占用改走 VM ✅ DataSettingsViewModel.storageBytes(invalidated 触发器 flatMapLatest 快照读);清理动作后自动重取;计数流保持 Room 实时流;UI 删 ModevDatabase/LocalContext 直读
- [x] T3.3 AppsScreen 列表偏好经 AppsViewModel ✅ AppListSettings 去 Context 化(init 缓存 applicationContext,与 ThemeSettingsManager 同范式);setPinScoped/setShowSystem/setShowModules 三方法经 VM 下发;UiState 补 pinScoped 投影;UI 零 import AppListSettings;12 处测试调用点机械迁移
- [x] T3.4 StatusSemantics 抽取 ✅ ui/common/StatusSemantics.kt:connectionStatus/scopeStatus(kind+文案资源)单一事实源;AppsScreen 行内与 AppMonitorScreen 头部消重;AppHeaderRow 去 scoped 冗余参数

### M4 全局记录页
- [x] T4.1 RecordsViewModel 泛化 ✅ preset 锁定语境(单应用页)/全局页类型+应用可变;D6 归一化在 buildFilter(kind 仅单类型生效);setTypeFilter 切 null 自动失效 kind;setPackages 空集=不限;totalCount 对账流;cachedIn 多播;旧 MonitorRecordsViewModel 退役
- [x] T4.2 RecordTimelineList 共享组件 ✅ 自 MonitorRecordsScreen 抽取(手动索引循环/时间线分组/三态/append 态);旧双页改薄壳复用
- [x] T4.3 GlobalRecordsScreen ✅ 类型三段 SegmentedButton(D6)+ 应用 FilterChip(plurals 计数)+ AppPickerSheet(ModalBottomSheet+imePadding+包名搜索);KindFilterMenu 增 enabled 联动置灰
- [x] T4.4 路由 RECORDS_ALL + AppsScreen 顶栏入口 ✅ TipIconButton(ListAlt,cd_records_all)与设置按钮并排;单一入口原则
- [x] T4.5 i18n 键(en+zh)✅ records_all_*/filter_type_*/filter_apps_selected(plurals one|other|zh other)/app_picker_*/cd_filter_apps/cd_records_all 共 14 组
- [x] T4.6 测试 ✅ RecordsViewModelTest(Robolectric 内存库,5 用例:语境锁定/跨类型聚合+应用多选/类型收窄/D6 类别联动/SQL 类别)全绿;GlobalRecordsScreenTest 插桩(入口/聚合/SegmentedButton 收窄/sheet 多选/返回)编译通过

### M5 详情页重构 + 每应用监听设置页
- [x] T5.1 AppMonitorViewModel 拆分 ✅ 独立文件;删 V20 globalSwitch 死代码;新增 scoped/pending/listenOverride 流与 toggleScope;scopeFailedMessage 双份私有映射收敛 ui/common/ScopeMessages.kt(scopeChangeMessage)
- [x] T5.2 AppMonitorScreen 重构 ✅ Hero 卡(56dp 图标/titleLarge 名称/双状态胶囊 StatusPill(StatusColorTokens AA 色)/作用域 Switch 行 toggleable 单语义);统计概览卡(总数 displaySmall 大字+类型分布双色比例条 KindColorTokens 同源+图例计数,零记录隐藏分布条);功能入口卡三行(+监听设置摘要「跟随全局」/「自定义 N 项」);操作卡(启动/危险清空行 error 色);死代码删除;全部 ListItem 显式容器色(契约 13)
- [x] T5.3 PerAppListenSettingsScreen ✅ 三态继承页:总开关×2+算法12+数据库5 行(CategoryTaxonomy 驱动排序),行尾态按钮→TriStateSheet 单选即写即推;重置行→居中 AlertDialog 确认 removeOverride;PerAppListenSettingsViewModel(setMaster/setKind/reset,setOverride 全默认即删键契约复用)
- [x] T5.4 i18n 键(en+zh)✅ app_monitor_scope_row/listen_settings_entry/follow_global/custom_n(%1$d 占位一致)/tri_inherit/on/off/listen_reset_action/danger_reset_listen_msg/app_monitor_actions_section
- [x] T5.5 测试 ✅ AppMonitorRedesignTest(Hero/Switch/统计卡图例/监听设置摘要/清空取消非破坏)、PerAppListenSettingsTest(三态持久化/重置回落)插桩编译通过;i18n 双门禁全绿;另修 V22 遗留 licenses_row_format 误报(ParityTest IDENTICAL_ALLOWED 补录纯占位符格式串豁免)

### M6 加固收尾
- [x] T6.1 Room 显式 WAL + `insertAll @Transaction` ✅ `setJournalMode(WRITE_AHEAD_LOGGING)`(显式表达意图防 OEM 差异);批量插入整批单事务原子落库,KDoc 注明与 ACK 语义对齐
- [x] T6.2 索引/管道回归探针 ✅ `ExplainQueryPlanProbeTest`:6 索引存在性门禁(sqlite_master)+ WAL/@Transaction 加固后端到端冒烟(插入→RecordFilter 计数/类型收窄→REPLACE 幂等对账)。注:EXPLAIN QUERY PLAN 级命中验证受 Robolectric 驱动限制(双模式均剥离 EXPLAIN 前缀),留待真机插桩,已沉淀 AGENTS.md 已知坑
- [x] T6.3 门禁全绿 ✅ 全量单测 BUILD SUCCESSFUL;lint **0 errors / 37 warnings(<基线 38)**——清理 V23 引入项(listen_settings_custom_n 改 plurals、listen_settings_title/detail_remove_scope 死键、SettingsRepository put/remove KTX 化)+ 顺手清 4 个工作区既有死资源键(record_copied/settings_pin_scoped_hint/settings_apps_section/detail_framework_error);androidTest 编译通过(FourScreensNavTest 同步 V23 重构后断言:更多菜单退役→操作卡+作用域 Switch)
- [x] T6.4 AGENTS.md 增补 V23 契约 ✅ 强约束 16(三态继承 per-app 监听)/17(统一记录页与列表收敛)/18(解耦契约 + lint 基线 38→37);已知坑补三条(Robolectric EXPLAIN 不可用、PowerShell 中文源码毁文件、SimpleSQLiteQuery 不解析 :name 与 ?N)

### 附:既有问题修复(非 V23 范围,基线回绿必要项)
- [x] F1 `LogRepository.deleteByFilter` 空约束短路失效 ✅ 历史 PowerShell 编码事故把 `if (!filter.hasConstraint) return 0L` 吞进注释行 → 空 filter 整表删除(ScratchInsertTest 守卫探针红);已恢复独立语句
- [x] F2 `SqlDatabaseIntegrationTest` setUp 9 用例红 ✅ V18 噪点类别默认关(dbOpen=false)后 setUp 未显式开 → OPEN_DATABASE 永不产出;setUp 显式 dbOpen/dbQuery/dbDml/dbExec/dbTransaction = true(关闭态由专项用例覆盖)
- [x] F3 `RecordServiceCrudTest.categoryCounts` 种子数据违规 ✅ 测试自插小写非法枚举名("cipher")违反强约束 9(生产 Codec 层严格校验 kind 合法);修正为合法枚举种子
- [x] F4 `StringResourceParityTest` licenses_row_format 误报 ✅ 纯占位符格式串(locale 无关)被「同文疑似漏译」检查误伤;IDENTICAL_ALLOWED 白名单补录并注释理由

## 9. 风险与缓解

| 风险 | 缓解 |
|---|---|
| RemotePreferences 键监听粒度不明(能否感知 `override_{pkg}` 新增键) | 注入侧 onChanged 一律重读本包相关键;ConfigServer TCP 推送为主通道,远程偏好仅为兜底 |
| legacy 变体模块 App 未运行时 override 失效 | D4 直读兜底覆盖 modern;legacy 场景退化为全局,TCP 连接建立后立即校正 |
| 详情页重构触碰强约束 3/4/10(三态渲染/sheet/时间线) | 复用既有组件与 testTag 断言范式;插桩断言按 AGENTS.md 已知坑规避 |
| 三页共用组件回归波及旧页 | D8 旧路由不动,共享组件以旧页现有插桩作回归网 |
