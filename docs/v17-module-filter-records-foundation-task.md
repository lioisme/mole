# Modev 任务书 V17:应用列表模块筛选 · 记录底层一体化 · 秒级时间线分组 · 状态指示器归位 · 命名标准化

**文档版本**: v1.0 | **创建日期**: 2026-08-19 | **状态**: ✅ 已完成
**适用范围**: `data/repo/{AppCatalogRepository,AppListSettings,LogRepository}.kt`、`core/util/PackageManagerCompat.kt`、`core/model/RecordType.kt`(新增)、`ui/apps/{AppsScreen,AppsViewModel,AppRecordsScreen}.kt`、`ui/common/TimelineGroup.kt`(新增)、`ui/components/TimelineHeader.kt`(新增)、`ui/settings/{DiagnosticsScreen,DataSettingsViewModel}.kt`、`data/db/CryptoRecordDao.kt`、i18n(en/zh)、测试三件套、AGENTS.md
**质量目标**: 单一事实源、零弃用 API、i18n 零硬编码、性能底线(V16 R4 不逐包 binder 纪律)、门禁全绿(基线 38 warning / 0 error)

---

## 0. 需求条款(用户口径 + 已确认决策)

| # | 需求 |
|---|------|
| R1 | 应用列表默认不显示 Xposed 模块,筛选菜单可启用(与「系统应用」同型) |
| R2 | 所有监听记录底层是一个整体(跨应用/跨监听类型),支持按应用、按监听类型筛选;**本次不做全局界面**,仅沉淀数据层契约,为后续 HTTP REST API / MCP 打基础(用户已确认) |
| R3 | 算法记录列表按**秒**做时间线分组;每应用记录页同步改造(用户已确认) |
| R4 | 记录列表的两个状态指示器(在线/作用域)与应用列表同款,**放在包名之后**;每应用页头部同步(用户已确认) |
| R5 | 「离线→未运行 / 在线→运行中」;统计与同类命名不标准、不统一问题全部解决 |

已确认决策:D1 全局记录界面本版不做,只做底层;D2 每应用记录页同步时间线分组与状态点归位;D3 模块识别走 meta-data + APK 资产双通道(惰性扫描 + 缓存)。

## 1. 全量审计(现状 + 根因)

### 1.1 R1 模块隐藏 — 现状与缺口

**现状**:`AppCatalogRepository.AppInfo` 仅 `{packageName, label, system}`(`AppCatalogRepository.kt:26-30`);筛选流水线仅 `showSystem` 一档(`AppsViewModel.kt:114-145`);`ScopeFilterMenu` 两过滤项(仅已作用域/系统应用,`AppsScreen.kt:353-369`);`AppListSettings` 三键持久化(`AppListSettings.kt:16-77`)。

**缺口**:
| # | 位置 | 违规内容 |
|---|------|----------|
| R1-1 | `AppCatalogRepository.kt:26-30` | 无模块身份字段;Modev 自身(`assets/xposed_init` + `META-INF/xposed/java_init.list` + `xposedminversion` meta-data 三通道声明)与其他模块混列应用列表 |
| R1-2 | `core/util/PackageManagerCompat.kt:14-21` | `installedApplications()` 无 flags 参数,无法单次调用取 `GET_META_DATA`(模块 meta-data 免额外 binder) |
| R1-3 | `AppListSettings.kt` | 缺 `showXposedModules` 键(默认 false = 隐藏) |
| R1-4 | `AppsScreen.kt:345-351` | 筛选按钮着色条件未含模块档(激活指示) |

### 1.2 R2 记录底层一体化 — 现状与缺口

**现状(已铺 80%)**:DAO 全查询已支持 `recordType` 过滤参数(null = 不过滤,ADR-4,`CryptoRecordDao.kt:34-53/77-92/104-112`);摄入侧已泛化(WireRecord + RecordCodecRegistry,未知类型计数丢弃);`RecordTypes` 仅 `CRYPTO` 常量(`RecordTypes.kt:8-11`)。

**缺口**:
| # | 位置 | 违规内容 |
|---|------|----------|
| R2-1 | `LogRepository.kt:219-243` | `pagedRecords` 硬编码 `RecordTypes.CRYPTO`——"整体"查询能力被锁死,未来 DB/行为记录无法在单一查询入口聚合 |
| R2-2 | `LogRepository.kt:246-247` | `observeRecordCounts()` 硬编码 CRYPTO;`DataSettingsViewModel.kt:22-24` 的「总记录数」= 仅 crypto 计数和 |
| R2-3 | `RecordTypes.kt` | 无领域层类型枚举:wire 字符串零语义化,未来 REST/MCP 层无从按类型分发 |
| R2-4 | — | 无统一查询契约(RecordFilter):未来 HTTP/MCP 若直触 DAO/实体,分层即破 |

### 1.3 R3 秒级时间线 — 现状与缺口

**现状**:`AppRecordsScreen.kt:360-373` 用 `items(records.itemCount)` 直铺 `RecordRow` 卡片;行内尾部已有秒级时间(`RecordRow.kt:133-138`,尊重 12/24h);全列表**无任何分组头**。

**缺口**:无时间线分组语义;纯列表与"记录浏览"的专业形态(聊天/日志类应用时间线)有差距;无跨日区分(多日记录同秒时间无法辨别日期)。

### 1.4 R4 状态点归位 — 现状与缺口

**现状**:`AppHeaderRow`(`AppRecordsScreen.kt:400-526`)布局 = `Column{label; packageName}` + **行尾**两枚 `StatusDot`(在线 ONLINE/OFFLINE `:435-443`;作用域 SCOPED/UNSCOPED/CONNECTING/ERROR `:444-469`)+ 启动按钮 + 更多菜单。应用列表行(`AppsScreen.kt:445-471`)的状态点则紧跟包名文本(第二行行尾)。

**缺口**:`AppHeaderRow.kt:435-469` 双状态点位于整行右端(与应用列表的"包名后"范式不一致,用户明确要求对齐)。

### 1.5 R5 命名标准化 — 全量审计清单

**核心违规(用户点名的词汇对)**:

| # | 键 | 现值 en / zh | 违规 |
|---|-----|--------------|------|
| N1 | `online` | Online / **在线** | 与 R5 目标「运行中」冲突;且与 N3 语义重复(同概念双词) |
| N2 | `offline` | Offline / **离线** | 目标「未运行」;与 N3 完全同义 |
| N3 | `apps_not_running` | Not running / 未运行 | 与 N2 同义 → **合并删键**(仅 `AppsScreen.kt:467` 一处引用) |

**统计区命名不统一(管道统计 7 项,en 语法参差/zh 动词模式不一)**:

| # | 键 | 现值 en / zh | 目标 en / zh |
|---|-----|--------------|--------------|
| N4 | `settings_online_targets` | Online target processes / 在线目标进程 | Running target processes / **运行中目标进程**(`DiagnosticsScreen.kt:132-136`) |
| N5 | `stats_received` | Records received / 已接收记录 | Received / 已接收 |
| N6 | `stats_dropped` | Queue overflow dropped / 队列溢出丢弃 | Dropped (queue full) / 队列已满丢弃 |
| N7 | `stats_write_failures` | Write failures / 写库失败 | 不变(已规范) |
| N8 | `stats_trimmed` | Trimmed by capacity / 容量治理裁剪 | Trimmed (capacity limit) / 超容量裁剪(「容量治理」为内部行话) |
| N9 | `stats_rejected_hellos` | Rejected handshakes / 拒绝握手 | Rejected (handshake) / 握手被拒 |
| N10 | `stats_dropped_records` | Malformed records dropped / 格式非法丢弃 | Dropped (malformed) / 畸形记录丢弃 |
| N11 | `stats_rejected_records` | Package mismatch rejected / 包名不符拒绝 | Rejected (package mismatch) / 包名不符被拒 |

统一模式:en = `Received / Dropped (原因) / Rejected (原因) / Write failures / Trimmed (原因)`;zh = 「原因 + 结果动词」(丢弃/被拒)。

**同族不一致(一并解决)**:

| # | 键 | 现值 zh | 违规 | 目标 |
|---|-----|---------|------|------|
| N12 | `detail_listening` | 已在作用域 | 与「仅已作用域/移出作用域」词汇不统一(在作用域 vs 作用域) | 已作用域(In scope) |
| N13 | `detail_not_listening` | 未在作用域 | 同上 | 未作用域(Out of scope) |
| N14 | `settings_clear_all_logs` | 清空全部日志 | 与 `danger_clear_all_msg`(清空全部记录)、全 App「记录」词汇冲突 | 键→`settings_clear_all_records`,清空全部记录 / Clear all records |
| N15 | `detail_clear_logs` | 清空该应用的日志 | 与 `danger_clear_logs_msg`(…的全部记录)同流程却不同词 | 键→`detail_clear_records`,清空该应用的记录 |
| N16 | `apps_logs_cleared` | 已清空 %1$s 的日志 | 同上 | 键→`apps_records_cleared`,已清空 %1$s 的记录 |

**保留不动(语义不同,非违规)**:`settings_connecting`(连接中)/`settings_not_connected`(未连接)= 框架服务连接语义;`settings_port` 监听端口 = 端口监听;`detail_reading_scope`/`detail_framework_no_service` = 服务态;`verify_ok/fail`、`dir_mode` 等;`capture_*` 记录选项键(词汇属记录配置,V14 已确认保留)。

## 2. 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | R2 界面范围 | 本版**不做全局记录界面**;只做数据层:RecordType 枚举 + LogRepository 查询泛化 + RecordFilter 契约 + 类型计数;REST/MCP 层未来只依赖该稳定面 |
| D2 | R3 分组实现 | 组合侧邻行比较(Paging 项已按序加载,`isGroupStart = i==0 \|\| prev==null \|\| secondKey 不同`);**不启用 stickyHeader**(分页 prepend 边界 + 保持简单),用居中胶囊头满足时间线语义;跨日组头带日期前缀 |
| D3 | R3 行内时间 | 保留 `RecordRow` 尾部秒级时间(长页扫描价值),分组头承担时间线语义,不重复删改 |
| D4 | R1 模块检测 | 通道 A:单次 `installedApplications(GET_META_DATA)` 取 meta-data(`xposedmodule==true` 或 `xposedminversion` 存在)——零额外 binder(V16 R4 纪律);通道 B:仅对未命中包**惰性 zip 扫描** `assets/xposed_init`(非空)与 `META-INF/xposed/java_init.list`,只读中央目录,异常降级非模块,每包缓存,随 refresh 失效重建 |
| D5 | R2 类型模型 | `RecordType` 枚举(领域语义)持 wire 字符串,`RecordTypes` 保留为协议/存储层字符串常量库;`fromWire` 未知返回 null(与 `enumOf` 修正后禁误标纪律一致) |
| D6 | R5 键策略 | 值修改尽量保键(`online`/`offline`);同义键合并删(`apps_not_running`);语义漂移键重命名(`settings_clear_all_logs→settings_clear_all_records` 等 3 键);统计 7 项仅改值 |
| D7 | R4 布局 | `AppHeaderRow` 第二行改为 `Row{packageName · Spacer(6dp) · StatusDot(在线) · Spacer(6dp) · StatusDot(作用域)}`,几何与 `AppsScreen.kt:445-471` 同款;a11y 语义(contentDescription/tooltip)不变 |

## 3. 改动清单(执行顺序)

- [x] **T1** 本任务书落盘 `docs/v17-module-filter-records-foundation-task.md`
- [x] **T2** R5 命名标准化(i18n en/zh 同步,键对 diff=0):N1-N3 改值 + N3 删键(引用改 `offline`);N4 改值;N5-N11 改值(统计 7 项);N12-N16:2 改值 + 3 键重命名;全量 grep 替换引用(代码 + FourScreensNavTest 等测试)
- [x] **T3** R1 模块识别:
  - `PackageManagerCompat.installedApplications(flags: Int)` 扩展(API 33+ `ApplicationInfoFlags`,旧路径弃用分支;调用点 `ApplicationInfoFlags.of(GET_META_DATA)`——执行期核验 metaData 随行返回,不满足则降级逐包取 meta-data 并缓存)
  - `AppInfo` 增 `isXposedModule: Boolean = false`(第 4 参带默认,既有构造零改动)
  - `AppCatalogRepository.refresh()`:通道 A 内联;通道 B 惰性 zip 扫描(IO 协程、缓存、短路 meta-data 命中包)
  - `AppListSettings` 增 `showXposedModules`(默认 false)+ setter + init
  - `AppsViewModel` 流水线:`SortPrefs` 加 `showModules`;filter 增 `prefs.showModules || !it.isXposedModule`
  - `ScopeFilterMenu` 增「Xposed 模块」Checkbox(位于系统应用之后);i18n 新增 `apps_modules`(en "Xposed modules" / zh「Xposed 模块」);按钮着色条件含 `!showModules`
- [x] **T4** R2 底层一体化:
  - 新增 `core/model/RecordType.kt`:enum(CRYPTO)+ `fromWire(String): RecordType?` + wire 单源引用
  - `LogRepository.pagedRecords(recordType: String? = null, ...)`(null = 全部类型;AppRecordsViewModel 调用点零改动);`observeRecordCounts(recordType: String? = null)` 参数化(AppsViewModel/DataSettingsViewModel 语义自动变全类型,今日行为不变)
  - 新增 `RecordFilter(recordTypes: Set<String>? = null, packages: Set<String>? = null, kinds: Set<String>? = null, query: String? = null)` + `LogRepository.paged(filter)` / `counts(filter)` 统一契约(REST/MCP 唯一入口)
  - DAO 新增 `observeRecordTypeCounts()`(SELECT recordType, COUNT(*) GROUP BY recordType)
  - **明确不做**(YAGNI):行模型泛化(sealed RowItem)待第二个监听类型落地;协议/摄入侧已泛化不动
- [x] **T5** R3 时间线分组:
  - 新增纯函数 `ui/common/TimelineGroup.kt`:`secondKey(ts)`、`dayKey(ts, locale)`(跨日判定,SimpleDateFormat "yyyyMMdd" 字符串比较,与 RecordRow 同 locale 语义)、`formatSecond(ts, locale, is24h)`(HH:mm:ss / h:mm:ss a)、`formatDay(ts, locale)`(en "Aug 19" / zh「8月19日」)
  - 新增 `ui/components/TimelineHeader.kt`:居中胶囊(rounded 8dp、surfaceVariant 底色)、labelSmall onSurfaceVariant;跨日组显示「8月19日 14:32:05」,同日组仅「14:32:05」
  - `AppRecordsScreen` 列表改手动索引循环(替代 `items()`):`record==null` 占位;组起始 → `item(key="h-"+secondKey)` 头 + `item(key=record.id)` 行;保留全屏空/载/错态与 append 尾部项
- [x] **T6** R4 状态点归位:`AppHeaderRow` 双 `StatusDot` 移入包名行(DR D7 几何),行尾仅启动/更多
- [x] **T7** 测试(见 §4)
- [x] **T8** 质量门禁:双变体单测 + lint 双变体(零新增)+ androidTest 编译 + en-zh 键对 diff=0 + 死字符串 grep(N3 删键零残留、`apps_not_running`/`settings_clear_all_logs`/`detail_clear_logs`/`apps_logs_cleared` 旧键零残留)
- [x] **T9** 实机验证(模拟器可用则验证;不可用则以编译门禁 + 截图替代)
- [x] **T10** AGENTS.md 契约更新(词汇表、模块识别双通道规范、RecordFilter 查询契约、时间线分组契约)
- [ ] **T11** 执行记录勾选 + 收尾

## 4. 测试计划

| 层 | 用例 | 断言要点 |
|----|------|----------|
| 单测(新) | `RecordTypeTest` | fromWire 已知/未知;wire 单源一致 |
| 单测(新) | `TimelineGroupTest` | secondKey 跨秒边界;dayKey 跨日/跨月;formatSecond 12/24h;formatDay en/zh locale |
| 单测(更新) | `AppListSettingsTest` | showXposedModules 默认 false;set→持久化→init 回读 |
| 单测(更新) | `AppCatalogRepositoryTest` | 通道 A meta-data 命中(免 zip);通道 B xposed_init/java_init.list 命中;双无→false;zip 异常降级;GET_META_DATA 单次调用 |
| 单测(更新) | `AppsViewModelTest` | 默认隐藏模块行;showModules=true 显示;模块过滤与查询/置顶组合 |
| 单测(更新) | DAO/LogRepository 测试 | recordType=null 跨类型分页;observeRecordTypeCounts 分组;RecordFilter 契约(paged/counts) |
| 插桩(更新) | `FourScreensNavTest` | 筛选菜单 3 过滤项 + 设置(新增 `apps_modules` 存在且默认未勾选);N14-N16 新文案断言替换 |
| 插桩(新) | `TimelineListTest` | 同秒多行单头;跨秒新头;跨日头带日期;头 key 稳定滚动;prepend 加载中不闪错头 |
| 插桩(更新) | 头部布局用例 | AppHeaderRow 状态点与包名同行(几何 + semantics 顺序断言);双点 contentDescription 保持 |

## 5. 验收标准(Definition of Done)

- [x] R1:默认列表无任何 Xposed 模块(含 Modev 自身);筛选菜单「Xposed 模块」勾选后即时出现;偏好杀进程重启保持;零逐包 binder 新增(lint + 代码审查)
- [x] R2:LogRepository 全查询 recordType 可空(全部类型);RecordFilter 契约就位;REST/MCP 层未来仅依赖 LogRepository + RecordType + RecordFilter;总记录数 = 全类型
- [x] R3:记录列表秒级分组头(跨日带日期、12/24h 遵循);滚动/分页无错头闪动;插桩契约全绿
- [x] R4:记录页头部双状态点位于包名后,与应用列表同款几何
- [x] R5:全 App 无「在线/离线/日志/在作用域」残留文案(grep 收口清单 N1-N16);统计区 en/zh 模式统一
- [x] 门禁:`testModernDebugUnitTest`/`testLegacyDebugUnitTest` 全绿;`lintModernDebug`/`lintLegacyDebug` 0 error、零新增 warning(基线 38);androidTest 编译通过;en-zh 键对 diff=0;死键 grep 零残留
- [x] 实机验证(或编译门禁替代)+ 截图;AGENTS.md 契约更新;执行记录逐项勾选

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| GET_META_DATA 经 flags 重载后 metaData 不随行返回 | T3 执行期核验;不满足则降级逐包取 meta-data 并按包缓存(仅模块候选,成本可控),DR 记录 |
| zip 扫描数百 APK 耗时 | 只读中央目录(ZipFile.getEntry)、meta-data 命中短路、IO 协程、异常降级;与既有 refresh 串行化共存 |
| Paging prepend 边界分组头缺失/错位 | prev==null 不渲染头,数据补齐后自然出现;TimelineListTest 锁行为 |
| 键删除/重命名波及测试文档 | T2 grep 收口清单;T8 死键门禁兜底 |
| 默认隐藏模块改变首启列表 | 任务书文档化 + 筛选菜单可见可逆;FourScreensNavTest 数据依赖断言 assumeTrue 防御 |
| 中文注释文件 PowerShell 改写乱码(P4 教训) | 一律 edit/write 工具,禁 PowerShell 文本改写 |

## 7. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 任务书落盘 | ✅ | |
| T2 | 命名标准化 | ✅ | N1-N16 全部落地:N1-N3 改值 + 删 `apps_not_running`;N4 改值;N5-N11 统计 7 项改值;N12/N13 改值;N14-N16 三键重命名(`settings_clear_all_records`/`detail_clear_records`/`apps_records_cleared`);en/zh 204/204 键对 diff=0;死键 grep 零残留 |
| T3 | 模块识别 | ✅ | 通道 A `installedApplications(GET_META_DATA)`(PackageManagerCompat flags 重载,metaData 随行返回核验通过);通道 B 惰性 zip 只读中央目录按包缓存;`AppListSettings.showXposedModules` 默认 false;`AppInfo.isXposedModule`;菜单第 4 项「Xposed 模块」;筛选按钮着色含模块档 |
| T4 | 记录底层一体化 | ✅ | `RecordType` enum(CRYPTO)+ fromWire;`pagedRecords`/`observeRecordCounts` recordType 可空;`RecordFilter` + `paged(filter)`/`counts(filter)` 统一契约;DAO `observeRecordTypeCounts` + 哨兵计数 `observeCountByFilter`/`observePagedRowsByFilter` |
| T5 | 时间线分组 | ✅ | `TimelineGroup` 纯函数(secondKey/dayKey/formatSecond/formatDay);`TimelineHeader` 居中胶囊 + testTag;AppRecordsScreen 手动索引循环(item key=h-{secondKey}/record.id,prev==null 不渲染头) |
| T6 | 状态点归位 | ✅ | AppHeaderRow 双 StatusDot 移入包名行(DR D7 几何),行尾仅启动/更多;appRecordsHeader 插桩断言同款几何 |
| T7 | 测试三件套 | ✅ | 单测:RecordTypeTest/TimelineGroupTest 新 + AppListSettings/AppCatalogRepository/AppsViewModel/DAO/LogRepository 更新全绿;插桩:TimelineListTest 新(种子 5 行:同秒×3/跨秒/跨日,同秒单头+跨日带日期,全绿)、FourScreensNavTest 更新(apps_modules 存在 + 行为级勾选验证:点项→行现/行隐,勾选态语义不可行因只读 Checkbox 无 ToggleableState)、appRecordsHeader 头部布局用例、ByteSectionTest 4 例修复(ReadMoreText 语义仅可见片段 + 收起按钮滚动);**全量 connectedModernDebugAndroidTest 52 例 0 失败**(3 防御性跳过) |
| T8 | 质量门禁 | ✅ | 双变体单测全绿;lintModernRelease/lintLegacyRelease 0 error / 36 warning(基线 38,零新增);androidTest 双变体编译通过;en-zh 键对 diff=0;死键 grep 零残留(4 旧键 + 在线/离线/在作用域仅 KDoc 注释) |
| T9 | 实机验证 | ✅ | emulator-5554(Android 14)实测:默认列表无 Modev 行(截图 v17-r1-modules-hidden-default.png)→ 筛选菜单 4 项齐(截图 v17-filter-menu-4-items.png)→ 勾选「Xposed 模块」+ 搜索 modev 出现 Modev/com.lioisme.modev 行(截图 v17-r1-modev-row-after-toggle.png)→ force-stop 重启保持 → 取消勾选行消失;截图存 `docs/screenshots_manual/` |
| T10 | AGENTS.md 契约 | ✅ | 词汇表(记录/运行中/已作用域/统计区)、模块识别双通道规范、筛选菜单语义(只读 Checkbox 无 ToggleableState,行为级断言)、RecordFilter 查询契约(哨兵计数语义 + toBindList + 种子合法枚举)、时间线分组契约(hasTestTag 限定组头)、ReadMoreText 可见片段语义、测试命令细化(-P 指定类) |
| T11 | 收尾 | ✅ | 任务书全文勾选 + 验收标准 7 项全过 + 双变体 assemble 通过(modern/legacy debug+release) |