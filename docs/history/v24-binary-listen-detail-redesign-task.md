# V24 任务文档 · 二态监听配置 / 全部记录页移除 / 记录详情重设计

**项目**:Modev — Kotlin/Compose(M3) Xposed 算法与数据库监听模块
**基线**:V23 完成态(lint 37 warning / 0 error;全测试绿)
**状态图例**:[ ] 待办 · [~] 进行中 · [x] 已完成

---

## 0. 目标与非目标

### 目标
| # | 需求 | 决策结果 |
|---|------|----------|
| G1 | 每应用监听类别改为纯「启用/停用」二态;存在一份**默认配置**(最佳推荐);支持一键恢复默认 | 删除 TriState 三态模型,改为「默认配置 ⊕ 稀疏二态偏差」叠加模型 |
| G2 | 移除「全部记录」界面(`records/all`);数据层统一存储保持不变 | 整页、路由、入口、专属 i18n、专属测试全量退役 |
| G3 | 记录详情页重设计:性能强、功能强、美观简约高级 | 结构重组 + 视觉规范化,硬保障引擎(三态/分档/闸门)**原样保留** |
| G4 | 仅 MD3 组件库,禁止自研/第三方 UI 组件 | 全程 M3 1.4.0 + 既有两项豁免(CollapsedSearchInput-D4、ReadMoreText) |

### 非目标
- 不改 wire 协议(`VERSION` 恒 4,payload 恒为普通 `HookSwitch`)。
- 不动数据层存储结构(Room 单表、`LogRepository.paged/counts` 契约 9)。
- 不新增 REST/MCP 能力。
- 不重构列表三页(`records/algorithm|database`)与设置页功能区布局。

## D 决策记录

- **D1 覆盖语义(用户确认)**:`有效配置 = 默认配置 ⊕ 该应用稀疏二态偏差`;无第三态;未定制项实时等于默认配置现值;恢复默认 = 清除该包全部偏差。备选「首编全量快照」否决。
- **D2 迁移**:`override_{pkg}` 键名不变;容忍式双格式解析(旧 TriState `"ON"/"OFF"/"INHERIT"` + `kindOverrides` → 新 bool),写入恒为新格式。
- **D3 定制标识**:行级不加噪音标记;恢复默认行副标题显示 `N 项已自定义`(plurals);应用页入口摘要 isDefault → 「使用默认配置」。

---

## 执行进度总览

### P0 基线验证
- [x] P0.1 全量单测跑通留档(`:app:testModernDebugUnitTest`) — UP-TO-DATE 全绿
- [x] P0.2 lint 门禁基线确认(37 warning / 0 error) — 报告留档 2026-08-24

### P1 模型层(二态偏差替换三态)
- [x] P1.1 删除 `core/model/TriState.kt`、`EffectiveSwitch.kt`;改写 `AppListenOverride.kt`(bool 偏差 + 双格式迁移解析) — TriState 删除;新 JSON `kinds` 键 + V23 遗留容忍解析
- [x] P1.2 新 EffectiveSwitch(bool 叠加 resolve + readKind 读写互逆);适配 `SettingsRepository` / `HookInstaller`(签名不变零改动) — SettingsRepository 去 normalized 化,KDoc 术语同步
- [x] P1.3 JVM 测试重写:EffectiveSwitchTest(13 例)/ AppListenOverrideJsonTest(10 例含旧格式迁移矩阵)/ SettingsRepositoryOverrideTest 适配 / RecordsViewModelTest 收缩(预设语境必选)
- [x] P1.4 单测绿 — 412 tests BUILD SUCCESSFUL(--no-daemon -Xmx2048m,守护进程 OOM 规避)

### P2 每应用监听设置页重建
- [x] P2.1 PerAppListenSettingsScreen:TriStateSheet → 行内 M3 Switch(ListenRow:整行 toggleable+clearAndSetSemantics);恢复默认行(isDefault 隐藏 + N 项自定义副标题) — VM 增 effective 流(默认配置实时传导回显)
- [x] P2.2 AppMonitorScreen.listenSummary 适配(`listen_summary_default` / plurals)
- [x] P2.3 i18n:删 tri_* 与 follow_global;增 listen_summary_default;hint/reset 文案改写(en+zh)
- [x] P2.4 插桩 PerAppListenSettingsTest 重写(拨动持久化/回拨/恢复默认 E2E)— 2 例绿
- [x] P2.5 设备插桩测试绿(雷电模拟器 emulator-5554,Android 14)

### P3 移除全部记录界面
- [x] P3.1 删 GlobalRecordsScreen.kt + 路由(RECORDS_ALL)+ ModevNav 接线 + AppsScreen 入口/ListAlt — GlobalRecordsScreenTest 同删
- [x] P3.2 RecordsViewModel 收缩为预设专用(preset 必传;删 typeFilter/packageFilter/setPackages/knownPackages;totalCount 留作对账面)
- [x] P3.3 测试处置:删 GlobalRecordsScreenTest;RecordsViewModelTest 重写为预设语境 4 例;FourScreensNavTest 补根页无记录入口负向断言(绿)
- [x] P3.4 i18n 清理:9 组独占键删除;records_all_title→monitor_records_caption 改名(AppMonitor 统计卡说明续用)
- [x] P3.5 编译零残留引用 + lint 无 UnusedResources 新增 + 相关测试绿 — 全绿

### P4 记录详情页重设计
- [x] P4.1 机械拆分:VM 迁出(RecordDetailViewModel.kt)+ 引擎迁出(RecordDetailSections.kt;byteSectionItems/textSectionItems 返回 item 数供锚点簿记,API 兼容,ByteSectionTest 不改通过)
- [x] P4.2 HeroHeader 身份卡(detail/HeroHeader.kt:kind 徽章 Chip(kindColor@15%)+ 操作 Chip + 算法 titleLarge mono + 方向/验签 StatusChip + 时间戳;整卡 SelectionContainer)
- [x] P4.3 AnchorChips 锚点导航(detail/AnchorChips.kt:cryptoAnchors/sqlAnchors 纯函数 + LazyRow FilterChip;装配期 AnchorBookkeeping 与 DSL 发射同源簿记 → animateScrollToItem)
- [x] P4.4 MetaGrid 两列标量网格(detail/MetaGrid.kt:条件行语义保留,奇数末项补位)
- [x] P4.5 区块外壳:ZoneHeader/FlowArrow/堆栈帧保留;记录切换回顶(LaunchedEffect(id))
- [x] P4.6 DetailHeroAnchorsTest 插桩 2 例绿;零新增 i18n 键(复用 section_*/meta_*);死键 meta_time/meta_algorithm/meta_db_name 双语清除

### P5 收尾与门禁终验
- [x] P5.1 AGENTS.md 契约 16 重写(二态偏差)、17 改写(records/all 退役+多节点断言规范)、词汇表增「默认配置/自定义偏差/恢复默认」
- [x] P5.2 终验:单测 412 例全绿 + 设备插桩 81 例全绿(1 skip=debug-only)+ lint 0 error / 37 warning(V23 基线持平,零新增)+ 弃用 API 零引入

---

## 1. G1 — 每应用二态监听配置

### 1.1 语义定义(D1)

```
有效配置(某应用) = 默认配置(switch_global) ⊕ 该应用自定义偏差(稀疏,仅存偏离项)
```

- 默认配置由通用设置 → 算法监听/数据库监听两页编辑(两页保留不动);未被定制的应用有效值恒等于默认配置当前值。
- 任一开关拨动即产生一条显式偏差(bool);恢复默认 = 删 `override_{pkg}` 键。
- 默认配置变更经 `ConfigServer.broadcastEffectiveConfig()` 每会话各自 resolve 下发(契约 16 定向推送机制原样保留)。

### 1.2 模型层

```kotlin
data class AppListenOverride(
    val cryptoListen: Boolean? = null,
    val dbListen: Boolean? = null,
    val kinds: Map<String, Boolean> = emptyMap(), // 仅存偏差条目
)
```

- `isDefault` = 三者皆空 → 调用方删存储键。
- 新 JSON:`{"cryptoListen":true,"kinds":{"CIPHER":false}}`(缺字段 = 默认);旧 TriState 格式容忍解析(D2)。
- `EffectiveSwitch.resolve(default, o)`:null/isDefault 零拷贝回传;kinds 沿用 17 路 when 映射,未知 kind 忽略。

### 1.3 管线触点

| 层 | 文件:行 | 改动 |
|----|---------|------|
| 仓库 | `data/repo/SettingsRepository.kt:158-206,208-222` | 类型随新模型;setOverride/removeOverride/pushOverride 流程不变 |
| 服务端 | `data/server/ConfigServer.kt` | 零代码改动(resolver 签名 `(String) -> HookSwitch?` 不变) |
| 注入侧 | `hook/HookInstaller.kt:96-116` | :103-105 类型引用换名,逻辑逐行等价 |
| 协议 | `core/codec/WireProtocol.kt:28` | VERSION 恒 4,断言不回退 |

### 1.4 每应用设置页(PerAppListenSettingsScreen.kt)

- 每行直接承载 M3 `Switch`,套用 `SettingsToggleItem` 模式(SettingsComponents.kt:122-185):整行 toggleable(role=Switch)、内层 Switch onCheckedChange=null + clearAndSetSemantics、禁用态 onSurface@38%。单击即写偏差 + 定向推送。
- 四段结构维持:总开关×2 → 算法 12 类(CategoryTaxonomy.forType(CRYPTO)) → 数据库 5 类 → 操作区。
- testTag:`listen-row-master-crypto` / `listen-row-master-db` / `listen-row-{KIND_ID}`。
- 操作区:「恢复默认配置」ListItem(testTag `listen-reset`)+ 居中 AlertDialog 确认;isDefault 时隐藏;副标题 plurals `listen_settings_custom_n`。
- 删除:TriStateSheet(:327-356)/TriStateRow(:290-325)/triStateLabel(:358-365)。

## 2. G2 — 移除全部记录界面(退役清单)

| 文件 | 动作 |
|------|------|
| `ui/records/GlobalRecordsScreen.kt`(367 行) | 整文件删除 |
| `ui/navigation/ModevRoutes.kt:35` | 删 RECORDS_ALL;头部 KDoc 修订 |
| `ui/ModevNav.kt:35,112-114,185-198` | 删 import/onOpenRecords 接线/destination/records-all VM 工厂 |
| `ui/apps/AppsScreen.kt:20,100,164-173` | 删参数/入口 TipIconButton/ListAlt import |
| `ui/records/RecordsViewModel.kt` | preset 必传非空;删 typeFilter/packageFilter/setTypeFilter/setPackages/knownPackages/totalCount;buildFilter 直通化 |
| `RecordService.observePackages` 链 | 保留(契约 9 MCP 未来消费者;DAO 测试独立覆盖) |

i18n 删除:`records_all_search/filter_type_all/filter_apps_any/cd_filter_apps/app_picker_*(4)/cd_records_all` + plurals `filter_apps_selected`;`records_all_title`→`monitor_records_caption`(机械改名)。`filter_type_crypto/database` 保留。

## 3. G3 — 详情页重设计规格

```
LazyColumn(stable keys)
 ├─ item("hero")     HeroHeader:Card(shapes.large,surfaceContainerLow)
 │                    ① kind 徽章 AssistChip(kindColor@15% 容器 + kindColor 标签)
 │                    ② operation AssistChip(surfaceVariant)
 │                    ③ 算法 titleLarge + 平台 monospace
 │                    ④ 方向/验签 AssistChip(primary/error tint leadingIcon)
 │                    ⑤ 时间戳 labelMedium(remember(context) SimpleDateFormat)
 │                    整卡单一 SelectionContainer(继承可选中语义)
 ├─ item("anchors")  AnchorChips:LazyRow(FilterChip)按内容动态生成;
 │                    点击 animateScrollToItem(itemIndex);标签 remember(record.id) 一次求值
 ├─ item("meta")     MetaGrid:两列 Column×Row(weight 1f),label bodySmall 上/value bodyMedium 下;
 │                    条件渲染保留(provider/process/thread/context/direction/exportable/verify;SQL 计数行);
 │                    keyExportable=false → error 色
 ├─ 数据流区(zone-dataflow/input/arrow/output)· 密钥区 · SQL 各段(保留)
 ├─ params / stack(300 帧上限 + omitted plurals,保留)
 └─ bottom-spacer
```

红线(引擎零回归):
- byteSectionItems/textSectionItems/sectionRenderMode/ChunkedText/BytesView 硬保险丝/ReadMoreText 预览/Tier A SelectionContainer/Tier B 分块 —— 全部签名不变(ByteSectionTest 12 用例直接驱动内部 API)。
- 三态 SegmentedButton 可用表示隐藏规则不变;bytes_count(%1$,d)+截断标记资源驱动;复制菜单三项可用性门控不变。
- TopBar prev/next(neighborIdOf id 投影)+ More(SAF 导出 JSON,estimateJsonChars 预检 + Default/IO 分线程)流程不变。
- 文件拆分纯机械搬移:internal 可见性保持;HardcodedStringConventionTest/PackageBoundaryTest 全程绿。

明确不做:页内查找、分享面板、hex 编辑器、帧级展开分析。

## 4. G4 — MD3 合规边界

- 允许:material3 1.4.0 稳定组件、compose.foundation(LazyColumn/LazyRow)、豁免 CollapsedSearchInput(D4)/ReadMoreText、icons-extended。
- 禁止:Canvas 自绘、第三方 UI 库、非豁免手搓控件。StatusDot/EmptyState/TimelineHeader/StatsCard 不在改造面内,不扩散处理。
- 视觉一律 token:colorScheme.* + AppShapes + Typography 角色;禁裸 fontSize;alpha 惯例(muted 0.5f/badge 0.15f)。
- ListItem 显式 containerColor;危险确认居中 AlertDialog;输入弹层 ModalBottomSheet(本期无新增)。

## 5. 测试计划

JVM:EffectiveSwitchTest/AppListenOverrideJsonTest 重写(含迁移矩阵);SettingsRepositoryOverrideTest 适配;ConfigServer*/引擎族/RecordDetailViewModelTest 不改。
插桩:PerAppListenSettingsTest 重写;AppMonitorRedesignTest 适配摘要键;GlobalRecordsScreenTest 删;FourScreensNavTest 补负向断言;ByteSectionTest/CryptoDetailZonesTest 不改为红线;新增 DetailHeroAnchorsTest。

门禁(P5 终验):
1. `.\gradlew.bat :app:testModernDebugUnitTest`
2. `.\gradlew.bat :app:connectedModernDebugAndroidTest`
3. `.\gradlew.bat :app:lintModernRelease` → 0 error / ≤37 warning
4. 弃用 API 零引入;gradle 重任务串行

## 6. 工程纪律

- 改文件一律 edit/write 工具(PowerShell 管道毁中文源码实锤)。
- 每阶段完成即在本档勾选并附一行备注;提交粒度按阶段。
- 插桩测试前置:`adb connect 127.0.0.1:5555`(雷电模拟器)。

## 7. 完成日志(逐项追加)

- P0:基线确认 — 单测全绿(UP-TO-DATE),lint 0 error / 37 warning(2026-08-24 报告)。
- P1:模型层完成 — TriState 删除;AppListenOverride 改 bool 偏差(`cryptoListen/dbListen: Boolean?` + `kinds`),V23 遗留 JSON 容忍解析无损迁移;EffectiveSwitch 重写(resolve + readKind 互逆);HookInstaller/ConfigServer 零改动(签名不变);SettingsRepository 去 normalized 化。JVM 测试重写后 412 例全绿。
- P2(主源集):监听设置页 Switch 行重建(TriStateSheet 退役);恢复默认行按需渲染 + 定制计数副标题;AppMonitor 摘要键适配;i18n en/zh 双语同步。插桩已重写待设备验证。
- P3:records/all 全链退役(页面/路由/入口/VM 收缩/9 组 i18n 键删除/records_all_title 改名 monitor_records_caption);单测绿。
- P2/P4 设备验证(雷电 x64 模拟器 emulator-5554 / Android 14):
  - 首轮定向 6 例:PerAppListenSettings 2 例全绿(E2E 拨动持久化+恢复默认链路);
  - 锚点芯片与分区头同文案导致裸 onNodeWithText 双匹配 — CryptoDetailZonesTest 四处断言改 `onAllNodesWithText(...).onFirst()` 多节点安全形态(已沉淀 AGENTS.md 契约 17),DetailHeroAnchorsTest 轮询断言同步修正;
  - cd_flow 计数断言错位(锚点滚动后数据流区滚出组合窗口)— 移除错位断言,唯一性由 zones 测试在正确语境断言;
  - 终态:**单测 412 例全绿;插桩全量 81 例全绿(1 skip = debug-only)**。
- P4 文档化偏差(2 处,均记录于测试 KDoc):① CryptoDetailZonesTest 算法行断言改为 detail-hero 锚点(V24 算法名由顶栏+Hero 双处承载,meta 长表网格化后不再含算法行,generate 记录算法出现次数仍为 2 断言保持);② 同上多节点安全形态改造。
- lint 收口:重设计产生 3 个 UnusedResources(meta_time/meta_algorithm/meta_db_name)→ 死键双语清除 → **0 error / 37 warning 与 V23 基线持平**。
- P5:AGENTS.md 契约 16 重写、17 改写、词汇表增补;FourScreensNavTest 补负向断言并复跑绿。全部阶段收口。
