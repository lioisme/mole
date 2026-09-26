# Modev V20 界面与信息架构重构 · 任务规格书(实施追踪)

- 版本:V20 / 2026-08-20
- 范围:界面信息架构收敛(单应用语境化)+ 记录列表滚动位置保持 + 算法详情页数据关系可视化 + 移除「查看记录」混合列表
- 验收基准:AGENTS.md 契约(V19)全数保持;`lintModernRelease` 基线 38 warning / 0 error 零新增;`testModernDebugUnitTest` 全绿;设备 UI 测试全绿
- 状态:**已完成**(M1–M6 全绿;R3.1 分页契约修复作为 V20 附加验收项落地)

## 产品决策(已确认)

1. 两记录入口副标题 = **每类型计数**(`AppCountFormat` 紧凑格式),承接被删「查看记录」入口的计数能力。
2. 入口页顶栏标题维持应用名(现状双显示,保持语境锚点)。

---

## 1. 背景与目标

当前信息架构(V18/V19)存在三处结构性缺陷:

1. **记录串应用**:应用监听入口页的「算法监听/数据库监听」指向*全局*列表页(`MonitorRecordsViewModel` 以 `packageName = null` 查询),从某应用的语境进入却看到**所有应用**的混合记录。
2. **入口页职责残缺**:「一键启动」按钮与应用级危险操作(移出作用域/清空记录)仅存在于「查看记录」页头部,而监听入口页无启动能力、无危险操作。
3. **冗余的「查看记录」**:`apps/{packageName}` 是「算法+数据库」混合列表,与拆分后的两列表页职责重叠。

独立质量问题:
- 记录列表进入详情再返回时**滚动位置丢失**(应用列表不丢,记录列表丢)——根因 = 冷 Paging 流重收集 → 新 generation → `LazyPagingItems` 瞬时空 → LazyColumn 越界钳制顶部。
- 算法详情页所有字节区块**视觉同构**,输入/输出/密钥材料/参数关系无表达,方向语义(明文↔密文)缺失。
- 「查看记录」头部组件(AppBar 与列表之间的应用信息行)应上移到应用监听入口页。

**目标(V20)**:
- 信息架构收敛为「应用列表(根页)→ 应用监听入口页(应用头部+一键启动+两记录入口)→ 算法/数据库记录列表(单应用过滤)→ 记录详情;设置四级不变」。
- 「查看记录」界面整体退役。
- 记录列表返回保持滚动位置(与应用列表同级体验)。
- 算法详情页按「数据流 / 密钥材料 / 参数 / 调用栈」分区,输入→输出关系显式化、方向语义化。

---

## 2. 目标信息架构(V20)

```
APPS(根页,不变)
 └─ APP_MONITOR  apps/{packageName}/monitor   ← 应用监听入口页(增强)
     ├─ AppHeaderRow:图标+应用名+包名+双状态点+一键启动+「更多」(移出作用域/清空该应用记录)
     ├─ 算法记录入口(records/algorithm/{packageName})   ← 单应用算法记录
     └─ 数据库记录入口(records/database/{packageName})  ← 单应用数据库记录
         └─ RECORD_DETAIL  record/{recordId}(不变;邻接已按包+类型限定)
SETTINGS(不变)
 └─ MONITOR_SETTINGS / DATA_SETTINGS / DIAGNOSTICS_SETTINGS(不变)
```

路由变更:
- `ALGORITHM_RECORDS = "records/algorithm/{packageName}"`(常量改带参路由)
- `DATABASE_RECORDS = "records/database/{packageName}"`
- `APP_RECORDS` 及其 `appRecords()` **删除**
- 新增工厂方法 `algorithmRecords(pkg)` / `databaseRecords(pkg)`

---

## 3. 任务清单与追踪

### M1 基础:字符串、纯函数、退役清理

- [x] 新增字符串(en + zh 双语,`StringResourceParityTest` 强制):
  - 入口标题:`algorithm_records_title` / `database_records_title`
  - 分区与元数据:`section_zone_data_flow` / `section_zone_key_material` / `meta_algorithm` / `cd_flow`
  - 数据流角色标签:`role_input_plaintext` / `role_output_ciphertext` / `role_input_ciphertext` / `role_output_plaintext` / `role_input_message` / `role_output_digest` / `role_output_mac` / `role_output_signature` / `role_output_verify` / `role_input_data` / `role_output_base64` / `role_input_base64` / `role_output_key`
- [x] 退役字符串(en/zh 同步删):`app_monitor_section_records` / `app_monitor_records` / `detail_search`
- [x] 新增纯函数 `cryptoFlowRoles(kind, operation, direction): Pair<Int?, Int?>`(ui/logs/CryptoFlowRoles.kt),未命中回落 null
- [x] 新增单测 `CryptoFlowRolesTest`(映射全分支 + 回落)

> 备注:lint 曾因 `record_types_crypto`/`record_types_db`(V20 单应用语境化退役类型筛选)报 2 个 UnusedResources,已从 en/zh 双删,代码零引用;lint 回到 38/0 基线。

### M2 导航与 ViewModel 重构

- [x] `ModevRoutes`:两记录路由带参化;删除 `APP_RECORDS` / `appRecords()`;新增 `algorithmRecords(pkg)` / `databaseRecords(pkg)`
- [x] `ModevNav`:删除 APP_RECORDS 目的地与接线;两列表目的地解析 packageName;VM key 带包;入口页回调改造
- [x] `MonitorRecordsViewModel`:新增必选 `packageName`;`records` 改**多播缓存流**(`cachedIn(viewModelScope)`,保 generation 稳定);新增 `listState: LazyListState`(VM 级列表状态)
- [x] `AppMonitorViewModel` 增强:`launchable` / `messages` / `clearLogs()` / `removeScope()` / 每类型计数 `cryptoCount`/`dbCount`;移除 `recordCount`

> 备注(R3.1 修正):原任务写「records 改热流 `stateIn WhileSubscribed(Int.MAX_VALUE)`」。设备测试实锤该形态在详情往返时抛 `IllegalStateException: Attempt to collect twice from pageEventFlow`(同一 PagingData 实例被返回重收集的 LazyPagingItems 二次收集,PageFetcherSnapshot 单收集守卫)。最终实现 = `cachedIn(viewModelScope)`(官方推荐):将每个 PagingData 包装为 MulticastedPagingData 多播流,UI 收集者永不直接触碰 snapshot 单次流;同样保 generation 稳定与 R3 滚动位置。

### M3 界面重构

- [x] `AppHeaderRow` 自 `AppRecordsScreen` 迁移至 `AppMonitorScreen`(M3 ListItem,含启动按钮 + 更多菜单)
- [x] 入口页头部接入 AppHeaderRow;入口卡片改版(新标题 + 每类型计数副标题);删除「查看记录」区
- [x] 危险操作(移出作用域/清空该应用记录)居中 `AlertDialog` 确认
- [x] 删除 `AppRecordsScreen.kt`;移除 `AppRecordsViewModel`/工厂/私有组件;清理 `AppsViewModel.kt` 未用 import
- [x] `RecordRow` 移除 `showPackage` 参数与包名渲染块(单应用语境冗余)

### M4 算法详情页分区化

- [x] 元数据块补 `meta_algorithm` 行(algorithm 原文,等宽值)
- [x] 数据流分区:输入块 → 居中流向箭头(`cd_flow`)→ 输出块;区块标题用角色标签
- [x] 密钥材料分区:key / keyAlias / iv / salt / aad / publicKey 归组
- [x] 分区头 = 通栏 tonal 条(图标 + titleSmall SemiBold),两级标题层级
- [x] 无数据不渲染空分区;params / stack 保持扁平区块

> 备注:数据流分区条件 `if (hasInput || hasOutput)` 在 KEY_GENERATION(仅 output)下仍渲染空分区头,设备测试抓出 → 改 `hasFlowZone = hasInput && hasOutput`(分区头+箭头仅双侧齐全渲染,孤侧区块独立渲染)。

### M5 测试同步与新增

- [x] 更新 `FourScreensNavTest`(新 IA:入口页头部+两入口;详情回退链路)
- [x] 更新 `MonitorListScreensTest`(单应用隔离:外部包行不出现;新入口标签)
- [x] 更新 `TimelineListTest`(算法列表/数据库列表分路径)
- [x] 新增 `MonitorEntryHeaderTest`(启动按钮、更多菜单、两入口)
- [x] 新增 `RecordListScrollPreservationTest`(≥120 条种子,深滚动→详情→返回,位置保持)
- [x] 新增 `CryptoDetailZonesTest`(分区头/角色标签/箭头;GENERATE 无数据流分区)

> 备注:`FourScreensNavTest.appRowOpens…` 曾在算法列表页误断言 `app-launch` 计数 1 → 改 `assertCountEquals(0)`(V20 启动按钮仅存于入口页头部)。

### M6 收尾

- [x] 全量单测 `testModernDebugUnitTest` 绿(349 + 新增探针/契约测试)
- [x] `lintModernRelease` 基线 38/0 零新增
- [x] `assembleModernRelease` + `assembleModernDebug` 成功
- [x] AGENTS.md 契约/词汇表/构建说明同步 V20
- [x] 本文档全任务标记完成 + 工作备注

### V20 附加验收项(分页契约修复,R3.1)

> 设备测试 `RecordListScrollPreservationTest` 深滚动暴露两个与分页相关的既有缺陷,均已修复并有回归测试,纳入 V20 验收:

- [x] **分页死锁(旧缺陷,首次被 >60 条测试触达)**:Room 2.7.2 `LimitOffsetPagingSource` 的计数查询 = `COUNT(*) FROM (<原 SQL>)`,原 SQL 自带固定 `LIMIT :limit OFFSET :offset`(仓库传 limit=60)→ itemCount 被钳制为 60 → Refresh 后 `nextKey=null` → 任何 >60 条列表只显示前 60 条。修复:`LogRepository` 新增 `PAGING_WINDOW_CAP = MAX_RECORDS`,`pagedRecords`/`paged` 的 DAO `limit` 参数传窗口上限(外层 loadSize 由 Room 动态注入)。
- [x] **回归测试**:`RecordPagingProbeTest`(Robolectric 驱动 DAO PagingSource 直测):大窗口下 Refresh(nextKey=60)+ Append(ALG-060..119,nextKey=null)全量翻页;固定小 limit(60) 断言 nextKey=null 死锁行为(警戒线)。

---

## 4. 非功能契约(强制)

1. i18n:全部新增/改动文本 en + zh 双语、占位符一致;`ui` 包无硬编码中文/英文;locale 敏感值走 `remember(context)`/`rememberEffectiveLocale`。
2. 数值格式:入口计数一律 `AppCountFormat`;资源格式串大数 `%1$,d`。
3. 详情页三态双层渲染契约(R4 改动不得触碰):SegmentedButton 三态、`sectionRenderMode` 分档、`BytesView.of` 保险丝、复制/导出护栏、ReadMoreText 预览——全数保持;分区头/字节块均为独立 LazyColumn item,严禁整区包进单一 Column/Card(保虚拟化)。
4. 弃用 API 零容忍;`lintModernRelease` 基线 38/0 零新增。
5. 复数:新增计数文案含 `%d` 用 `<plurals>`。
6. 组件契约:新控件一律 M3;危险操作确认用居中 `AlertDialog`。
7. 时间线分组契约 / RecordFilter 查询契约 / 筛选菜单语义 不改动。
8. 测试锚点:`app-launch`、`record-row`、`timeline-header` 保留;新增 `detail-zone`。

## 5. 完成定义(DoD)

- [x] M1–M6 全部验收标准满足;删除清单零残留引用。
- [x] 全量单测(含新增 CryptoFlowRolesTest/VM 多播流/分页探针)绿。
- [x] lint 基线零新增;双变体 assemble 成功。
- [x] 设备 UI 测试全绿(71/71,1 skipped;含滚动保持/分区/单应用隔离/入口头部)。
- [x] AGENTS.md 同步 V20。
