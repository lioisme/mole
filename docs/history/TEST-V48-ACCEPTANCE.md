# V48 · 全量验收清单与压测记录

**执行入口**：JVM/构建档 = `gradlew` 逐条；设备档 = `scripts/test-integration/run-acceptance.ps1`（仓库既有编排器，阶段序 = preflight → build/unit/lint/assemble → install → scope → inject 矩阵 → 插桩 UI → MCP，SKIP 只有列入 `skip-allowlist.txt` 才不计 FAIL）。
**判定纪律**：预期一律写成**可判定的观测**（屏上文本 / 靶程序读回值 / 注册表内容 / 命令输出），禁「组件名不存在」这类代理指标；Gradle 步只认 `BUILD SUCCESSFUL` 行与结果 XML，不信管道退出码。
**结果记号**：✅ 符合预期 · ❌ 不符合（须归因）· ⬜ 未跑 · 🔵 进行中 · ⛔ 环境阻塞（阻塞因在靶机/宿主而非代码；**既不记 ✅ 也不记 ❌**，恢复后必须补跑）。

---

## G1 编译与静态门禁

| # | 被测项 | 方法 | 预期 | 结果 |
|---|---|---|---|---|
| G1-1 | 全量单测编译 + 执行 | `.\gradlew.bat :app:testModernDebugUnitTest --no-build-cache` | `BUILD SUCCESSFUL`；结果 XML 合计 failures+errors = 0 | ✅ |
| G1-2 | lint 门禁不漂移 | `.\gradlew.bat :app:lintModernRelease` | `0 errors, 43 warnings, 1 hint`（与 V46 基线逐字相同） | ✅ |
| G1-3 | release 打包（双 ABI） | `.\gradlew.bat :app:assembleModernRelease` | 成功产出 arm64-v8a + x86_64 两个 APK | ✅ |
| G1-4 | debug 打包 | `.\gradlew.bat :app:assembleModernDebug` | 成功 | ✅ |
| G1-5 | 插桩 APK 编译 | `.\gradlew.bat :app:assembleModernDebugAndroidTest` | 成功（V48 新增/改名的插桩用例全部编译过） | ✅ |
| G1-6 | Room 迁移链对账 | `DbMigrationParityTest` | version=15 与 MIGRATION 链、addMigrations 三处一致 | ✅ |
| G1-7 | 四语资源完整性 | `StringResourceParityTest` | 每键四语齐、占位符形态一致、plurals 逐 locale 存在；退役键无残留 | ✅ |

## G2 R-1 作用域更名（验收 = 用户实际看到的文字）

| # | 被测项 | 方法 | 预期 | 结果 |
|---|---|---|---|---|
| G2-1 | 监控页作用域开关四语文案 | `i18n.DomainVocabularyTest#scopeSwitchNeverCallsScopingListening_inAnyLocale` + `#scopeSwitchUsesTheAgreedFourLocaleWordings` | zh 含「作用域」、en 含「Scope」，四语均不含 监听/监视/monitor/listen | ✅ |
| G2-2 | 开关与状态徽章同名词 | `#scopeStatePillsShareTheScopeNoun` | 「已作用域 / 未作用域」，且不含「监听」 | ✅ |
| G2-3 | 两域页标题在四语互异 | `#theTwoDomainPagesHaveDistinctNamesInEveryLocale` | 监听页 ≠ 功能页（逐 locale），监听页不含「功能」 | ✅ |
| G2-4 | 两页重置确认对称 | `#resetConfirmationsAreSymmetricPerDomain` | 同起同止句式、各点自己域词 | ✅ |
| G2-5 | 空列表提示指向两页 | `#emptyListHintPointsAtBothDomainPages` | 同时含「监听设置」「功能设置」 | ✅ |
| G2-6 | 「总开关/Masters」层命名退役 | `#theMasterSwitchLabelsAreRetired` | 四语资源文件中零命中 | ✅ |
| G2-7 | 跨域点名句必须指名闸 | `#crossDomainHintNamesTheGatingSwitchAndItsPage` | 含 `%1$s` 参数与「功能设置」 | ✅ |
| G2-8 | 应用列表行尾 Checkbox = 同一个名词与锚点 | 插桩 `FourScreensNavTest#appRowScopeCheckbox_carriesTheGateNoun`（本轮补：行尾槽 testTag 只在**非合并树** ⇒ 用例按两棵树分别取锚点并让「带名词控件数 == 锚点数」对账）+ 监控页 `app-scope-row` 三条既有用例 | 两控件同名词、锚点齐备且数量对账 | ✅ 19:14 |
| G2-9 | 死路已灭且不复活 | 源码扫描 + `danger_remove_scope_msg` 零引用 | 键、`removeScope()`、`DangerAction` 均不存在 | ✅ |

## G3 R-2 前后端分类对齐

| # | 被测项 | 方法 | 预期 | 结果 |
|---|---|---|---|---|
| G3-1 | 能力表与分类法双射、零自抄 | `core.CapabilityMapTest#mapIsBijectionWithTaxonomyAndNeverRestatesIt` | 表行集 ≡ `CategoryTaxonomy.all()`；域一致 | ✅ |
| G3-2 | 分类快照冻结 | `#frozenClassificationSnapshot` | 81 行 / LISTEN 44 / FEATURE 37 / 跨域类型 = NETWORK·DEVICE_ENV·UI_INTERACTION | ✅ |
| G3-3 | 表不许指向空气 | `#everySectionKeyIsARealStringResource` `#everyRouteKeyIsARealNavigationRoute` `#everyProbeHasASourceFile` | 分区键在 `R.string`、路由常量在 `ModevRoutes`、探针类有源文件 | ✅ |
| G3-4 | 功能行分区 == FeatureGroups | `#featureRowsFollowFeatureGroupsExactly` | 逐 kind 相等（不许两套分组） | ✅ |
| G3-5 | 跨域闸必须可见 | `ui.apps.ListenEnvSectionRoboTest`（整屏渲染 4 例） | kind 开 + 外来 master 关 ⇒ 行上点名；master 开 / kind 关 ⇒ 不点名；渲染行集 == 表行集 | ✅ |
| G3-6 | master 标签与取值单点 | `ui.apps.KindLabelResExhaustiveTest` + `HookSwitch.masterFor` 穷尽 when | 功能页与监听页提示读同一函数 | ✅ |
| G3-7 | 一个类型一个标题键 | 资源扫描（`settings_records_*` 零命中）+ 插桩 `DataSettingsScreenTest`（两形态入口同源） | 十键已删，两处入口同源 | ✅（键 + 设备 19:14） |
| G3-8 | 监控页两行按域计数 | 插桩 `AppMonitorRedesignTest#hubEntriesCountTheirOwnDomain` | 仅功能域定制 ⇒ 监听行「使用默认配置」、功能行「1 项已自定义」 | ✅ 19:14 |
| G3-9 | 双域写侧闸不破 | `HookGate`+`McpToolsTest` 双域拒收 + `UiMcpCapabilityParityTest` R1–R6 | FEATURE kind 进 `listen_settings_set` 被拒；能力行与 UI 动作双向归属 | ✅ |

## G4 R-3 画像选型库

| # | 被测项 | 方法 | 预期 | 结果 |
|---|---|---|---|---|
| G4-1 | carriers 资产解码与形态钉桩 | `core.CarrierCatalogTest`（7 例，读真包 JSON） | 1597 条；PLMN 全 5–6 位且唯一；**恰 77 条国家码非两字母**；坏 JSON/空表判不可用；NOTICE 含出处与 revision | ✅ |
| G4-2 | 三字段一次写全 + 多地区码不写 | `#twoLetterCountryCodeWritesAllThreeFieldsAtOnce` / `#multiRegionCountryCodeNeverReachesProfile` | `AU/CC/CX` ⇒ `simCountry` 恒空（不拆不猜） | ✅ |
| G4-3 | 候选值恒过字段校验 | `#everyAssetValuePassesFieldValidation` | 1597×3 值逐一经 `ProfileFieldCatalog.validate` 通过 | ✅ |
| G4-4 | 资产通路四口径 | `data.repo.CarrierCatalogRepositoryTest`（5 例） | 真 assets 读通；二次调用零读盘（attempts=1）；缺文件判 null 不抛；冷却窗内不重试、窗后重试 | ✅ |
| G4-5 | 读不到 ≠ 空目录 | 同上 + `ProfileEditorRenderRoboTest#unavailableCarrierCatalog_isNamedOnTheRow` | UI 上屏点名「运营商库不可用」，禁空选择器 | ✅ |
| G4-6 | Android release↔SDK 成对 | `core.model.ProfileValueSetsTest`（6 例）+ `ProfileEditorRenderRoboTest#androidVersionPicking_fillsReleaseAndSdkTogether` | 两列等长/唯一/单调；表尾停 API 36；选 14 ⇒ SDK 同写 34；认不出的 release **整动作零写入** | ✅ |
| G4-7 | 密度档 / 核数档可选 | `ProfileOptionSourceTest`（5 项全表）+ `ProfileEditorRenderRoboTest#densityAndCoreFields_nowHaveCandidateTables` | 候选表存在、逐值过校验、整数按数值升序、恒留手填通路 | ✅ |
| G4-8 | 机型库仍守「不猜」 | `core.model.DeviceCatalogTest`（14 例，读真包） + `#devicePicking_fillsFourIdentityFields_andNeverGuessesProduct` | 一次写全 BRAND/MANUFACTURER/MODEL/DEVICE；PRODUCT/BOARD/HARDWARE 恒空 | ✅ |
| G4-9 | 内置档 9 台自洽 | `core.model.BuiltinProfileConsistencyTest`（8 例） | 恰填身份 13 项；指纹前缀与三元组一致、段与版本/Build ID 一致；release↔SDK 由 `ProfileValueSets` 裁决；MODEL 唯一、厂牌 ≥3 | ✅ |
| G4-10 | 整组随机一致性 | `core.model.ProfileEditorFormTest`（200 种子） | 同机型+同世代+同运营商；逐值合法；刻意不碰 BOARD/HARDWARE/密度/语言/定位；可注入随机源可复现 | ✅ |
| G4-11 | 共用资产读者未被改坏 | `data.repo.DeviceCatalogRepositoryTest` + `ProfileEditorRenderRoboTest` 机型两条 | `CachedAssetReader` 抽取后原 4 条口径逐条仍成立 | ✅ |
| G4-12 | 库页三段可区分 + 接管边界常驻 | `SpoofProfileLibraryRenderRoboTest`（6 例中新增 2） | 三段名两两不同；两段遗留自证「仍在生效」；`profile-takeover-status` 恒在屏 | ✅ |
| G4-13 | 画像库读写与账本 | `data.repo.SpoofProfileRepositoryTest`（18 例，真 Room） | 内置档安装幂等、收养不重复、墓碑不复活、改档脱管、导出导入闭环判重 | ✅ |
| G4-14 | 选型弹层端到端 | 插桩 `ProfileEditorFlowTest`（真机点选四张 sheet：机型两级 / 枚举语言 / Android 版本 / 运营商）+ 本轮补的两条（版本成对写、运营商三字段一次选全） | 点选后落值且脏态正确；「改为手动填写」通路可达 | ✅ 19:14（选择器是懒列表，用例必须走 sheet 自带搜索框 —— 这同时是产品给长名单的通路，见 AGENTS 新条） |

## G5 MCP 工具面（Agent 一等公民）

| # | 被测项 | 方法 | 预期 | 结果 |
|---|---|---|---|---|
| G5-1 | 工具名单冻结 | `mcp.McpToolParityTest#frozen_50_tool_names` + B1 边界扫描 | 恰 50 名、无漂移；mcp 子树零 `data.*`/`ui.*`/`hook.*` | ✅ |
| G5-2 | 令牌管理仍是明示排除 | `#token_management_is_documented_exclusion` | 零 token 工具（引导悖论 + PII） | ✅ |
| G5-3 | 声明面即生效面四条 | `mcp.tools.SpoofProfileToolsTest`（24 例） | 未声明参数 = -32602；`kind` enum 乱值 = -32602 而非回落；required 缺失与显式 null 同分型；`writable` 恰等于 UI 落值集 | ✅ |
| G5-4 | 目录分派三态 | `#devices catalogue…` `#carriers catalogue…` `#an unreadable asset…` | 品牌表 → 机型表 → 运营商过滤；未知 brand 显式 error；不可用回 `available:false`+reason，禁空表；200 行截断显式上报 | ✅ |
| G5-5 | 能力归属与动作单点 | `architecture.UiMcpCapabilityParityTest` R1–R6 | 无两工具广告同一可变上游；每行双向归属或登记理由；作用域能力行 = 两个双向开关（`removeScope` 已随死路移除） | ✅ |
| G5-6 | 写入模型与协议恒定 | `McpDtosTest` / `JsonRpcTest` / `McpServerLifecycleTest` | 载荷 schema 由模型派生；`truncated` 键恒在；时间戳恒 epoch millis；重启会话失效 | ✅ |
| G5-7 | MCP 端到端（真 HTTP） | 设备档 `24-run-mcp.ps1`（42 子断言，§9.2 矩阵） | 全 PASS；令牌认证开/关两态；错误码分型 | ⛔ 环境阻塞（V48-ENV-01：记录/配置两组的种源 = 注入侧真触发，靶机框架栈未运行 ⇒ 本行不记 ✅。旁证：协议面已由 G5-8 真 HTTP 覆盖，令牌两态只有 JVM 档 `McpServerLifecycleTest` 覆盖） |
| G5-8 | 插桩侧 MCP e2e | 插桩 `McpEndToEndTest` | 本机端口起服务 → tools/list 与真实调用一致 | ✅ 19:14（不依赖框架：服务与调用同在宿主进程） |

## G6 既有能力回归（记录面 / 注入面）

| # | 被测项 | 方法 | 预期 | 结果 |
|---|---|---|---|---|
| G6-1 | 谓词单点 + DAO 直调门禁 | `RecordQueryFactory` 相关 + `architecture.RecordDaoAccessGateTest` | 列表/计数/删除候选/邻接四路同谓词；DAO 调用白名单外零命中 | ✅ |
| G6-2 | 记录读写与实体对账 | `data.*` 22 类 + `RecordWriteModelParityTest` | 每列有映射器；类型化模型齐身份字段；domain 零 `androidx.room` | ✅ |
| G6-3 | 分页不死锁 | `RecordPagingProbeTest` | 大窗口 limit 下 Append 前进；固定小 limit 形态被点名 | ✅ |
| G6-4 | 详情页 markdown 契约 | `ui.detail.markdown.*`（5 类） | 单管线、代码组聚合、锚点滚动可达、围栏安全 | ✅ |
| G6-5 | 热路径门控与干预双闸 | `architecture.HotPathGateConventionTest`、`InterventionGateConventionTest`、`hook.hooks.FeatureGateBypassTest` | 干预动作恒过 `featureOn(kind)`；开关全关 = 零副作用 | ✅ |
| G6-6 | 注入装配与 seq 续号 | `hook.InjectionAssemblyContractTest`、`RecordSinkTest`、`RecordSpoolTest` | Context 依赖惰性定档；跨重启 seq 不回卷 | ✅ |
| G6-7 | UI 契约族 | `DialogConventionTest`、`AppbarSearchExclusivityTest`、`RecordRowContainerContractTest`、`SettingsThemeContrastTest`、`HardcodedStringConventionTest` | 弹层出口唯一；Appbar 含搜索则 actions 空；行圆角只在容器层；20 seed 对比度达标；ui 包零中文字面量 | ✅ |
| G6-8 | 记录监听矩阵（靶程序真触发） | 设备档 `20-run-listen.ps1` | 各类型记录按预期捕获，含 seq/截断/去噪断言 | ⛔ 环境阻塞（V48-ENV-01） |
| G6-9 | 干预能力矩阵 | 设备档 `21-run-enhance.ps1` | 退出拦截/崩溃预防/解除录屏/弹窗拦截/SSL/代理 VPN/Root·Xposed·模拟器 逐项二态成立 | ⛔ 环境阻塞（V48-ENV-01） |
| G6-10 | 宿主↔注入联动 | 设备档 `22-run-linkage.ps1` | 配置即写即推、per-session 生效、断连重连恢复 | ⛔ 环境阻塞（V48-ENV-01） |
| G6-11 | 冷启动冒烟（钩子变更后必跑） | 设备档 `23-run-smoke.ps1` | 全功能 ON 下逐个作用域 App 冷启动无崩、无 ANR、帧语义断言绿 | ⛔ 环境阻塞（V48-ENV-01；本轮 hook 子树零改动 ⇒ 该门禁的触发条件未满足） |
| G6-12 | 模块关闭负向 | 设备档 `26-run-module-off-negative.ps1` | 开关关闭 = 零记录、零篡改 | ⛔ 环境阻塞（V48-ENV-01；负向断言在无框架机器上「恒零记录」= 假绿，禁止以 SKIP 过关） |
| G6-13 | 背压/洪峰 | 设备档 `27-run-backpressure.ps1` | 超容量丢弃/被拒/写入失败计数各归其位，主链路不阻塞宿主 | ⛔ 环境阻塞（V48-ENV-01） |

## G7 插桩 UI 全量（25 个类，`connectedModernDebugAndroidTest`）

覆盖：`FourScreensNavTest` `MonitorEntryHeaderTest` `AppMonitorRedesignTest`（含 G2-8/G3-8）`MonitorListScreensTest` `GlobalTypeRecordsTest` `TimelineListTest` `RecordListScrollPreservationTest` `PerAppListenSettingsTest` `PerAppFeatureSettingsTest` `DataSettingsScreenTest`（G3-7）`ServicesScreenTest` `DiagnosticsScreenTest` `McpTokensScreenTest` `McpEndToEndTest` `CryptoDetailZonesTest` `PureMarkdownIdentityTocTest` `ProfileEditorFlowTest`（G4-14）`AppIconDeviceTest` `ColorSheetDeviceTest` `LanguageHotSwitchTest` `ExitConfirmTest` `DebugHotSwitchTest` `RecordQueryPlanTest`（EXPLAIN 计划级）`V42StabilityProbeTest`。
**门禁**：跑前默认 adb server 序列数必须恰为 1（`ANDROID_ADB_SERVER_PORT` 须清，否则与脚本轮 5038 双序列互相踩踏 = 大面积假失败）；`am instrument` 前 force-stop 宿主。

| # | 预期 | 结果 |
|---|---|---|
| G7-1 | 全部类 0 失败 0 错误（已知环境性 flake 须逐条重跑复核，不许放过） | ✅ **136 用例 / 0 失败 / 0 错误 / 1 SKIP**（19:14 XML；SKIP = `skip-allowlist.txt` EX-06 表内豁免：`FourScreensNavTest#recordRow…` 前置需真记录,复位步清空了库 ⇒ 不判红也**不算证据**）。首轮 6 红逐条单跑全部稳定复现 ⇒ 非 flake，四类根因见「设备档归因」小节，修完复跑两轮全绿 |
| G7-2 | 查询计划级：4000 行种子下零 `SCAN`、零 `USE TEMP B-TREE` | ✅ `RecordQueryPlanTest` 在同批 136 用例内绿（4000 行种子自建自清） |

## G8 资产进包核验

| # | 被测项 | 方法 | 预期 | 结果 |
|---|---|---|---|---|
| G8-1 | 两本目录随包 | `unzip -l` release/debug APK | `assets/carriers.json`(134,053B) + `carriers.NOTICE.txt` + `devices.json`(1,938,874B) + `devices.NOTICE.txt` 四件齐 | ✅ |
| G8-2 | 许可文本完整 | 包内 NOTICE 与仓库源文件逐字节对比 | 一致（禁改文件） | ✅ |

---

## 汇总与判定

| 档 | 项数 | 结果 |
|---|---|---|
| G1 编译与静态 | 7 | 7 ✅ |
| G2 更名 | 9 | 9 ✅ |
| G3 分类 | 9 | 9 ✅ |
| G4 选型库 | 14 | 14 ✅ |
| G5 MCP | 8 | 7 ✅ / 1 ⛔ 环境 |
| G6 回归 | 13 | 7 ✅ / 6 ⛔ 环境 |
| G7 插桩 UI | 2 | 2 ✅（136 用例 / 0 失败 / 1 表内豁免 SKIP） |
| G8 进包 | 2 | 2 ✅ |
| **合计** | **64** | **57 ✅ / 7 ⛔ 环境阻塞（注入面，非代码）** |

**设备档归因（09-23 19:14 轮实锤）**：清单里 7 项属注入面（G5-7 + G6-8…G6-13），本轮**未能取到证据,原因在靶机而非代码** —— LDPlayer VM 的 Magisk/Zygisk 整场未运行，LSPosed 与 Vector 两个 zygisk 模块同时不加载：`/sbin` 与 `/dev/.magisk` 不存在、`grep -c zygisk /proc/$(pidof zygote64)/maps` = 0、`ps -A` 无 magisk、`/data/adb/lspd/log/*` mtime 停在 09-19；`su` 仍可用（雷电自带 `ldinit`），所以「能 root」在这台机器上毫无指示力。已做过：改 `modules_config.db` 的 `apk_path` 到活路径并核过 → 整机冷重启（`uptime` 归零为证）→ 注入仍零、`scope` 步新加的归因探针直接点名 `environment: magisk/zygisk not running`。**恢复需要宿主 VM 的 root/Magisk 档位**（共享设备动作，可能打断其它会话），恢复后按 `scope → inject → mcp` 顺序跑且**不得 `install -r`**（覆盖安装会再次打断注入）。这 7 项在恢复前既不记 ✅ 也不记 ❌，记 ⛔。

**跑出来的六条红点全部落在测试侧**（逐条单跑稳定复现 ⇒ 非负载 flake；已修并复跑两轮全绿）：① 选型 sheet 是懒列表，未组合行不存在 ⇒ 必须走 sheet 自带搜索框；② DropdownMenu 退场遮罩吞点击 ⇒ 跨弹层动作前先 `awaitUntilGone`；③ 在单应用屏断全局形态的确认文案（#17 两形态两句）；④ `ListItem` 槽位 testTag 只在非合并树；⑤ V12 枚举字面量被抄死在断言里（`WEB_CRYPTO`/`ENCRYPT` → 现 `JS_CRYPTO`/`JS_ENCRYPT`）且消息为空；⑥ 同一条 ⑤ 顺带查明**探针回传的算法名从未落进记录**（登记 V48-DFC-15，不在 V48 三需求内越范围修）。四种静默形态已写入 `AGENTS.md`。

**设备档执行条件与代价（本轮已实际承担）**：插桩档跑前必须**默认 adb server 且序列数恰 1**（`ANDROID_ADB_SERVER_PORT` 要清，否则与脚本轮 5038 双序列互相踩踏 = 大面积假失败），跑完 AGP 会卸载宿主 APK 并复位 Vector 模块表 ⇒ 之后若还要跑注入面，须重装 + 改 `modules_config.db` 的 `apk_path` + 整机冷重启。本轮 19:14 的插桩全量正是在这个代价之后取的；注入面 7 项卡在靶机 Magisk/Zygisk 未运行（见下方归因），与 V48 代码无关。

## 执行记录

| 时间(UTC+8) | 档 | 命令 | 输出要点 |
|---|---|---|---|
| 09-23 08:2x | G1 | `testModernDebugUnitTest --no-build-cache` / `lintModernRelease` / 三个 assemble | 157 类 / 1114 用例 / 0 失败；lint 0e/43w/1h；三 APK 成功 |
| 09-23 08:2x | G2–G5, G8 | 定向与全量各门禁类 | 逐条符合预期（见各行） |
| 09-23 18:0x–19:3x | G7 设备档（`-Stage ui`） | `connectedModernDebugAndroidTest`（`--no-build-cache --max-workers=1`，默认 adb server 且序列数 == 1） | 首轮 133 用例 **6 失败** ⇒ 逐条单跑全部稳定复现 ⇒ 四类根因修完 + 补 3 条用例 ⇒ 两轮复跑 **136 用例 / 0 失败 / 0 错误 / 1 表内豁免 SKIP**。结果 XML 已归档 = `docs/test-reports/v48-instrumentation-20260923-1914.xml`（AGP 把整轮并成单棵 testsuite，`name` 属性恒是首个类名 `AppIconDeviceTest`，别据此判「只跑了一个类」；计数看 `tests/failures/skipped`） |
| 09-23 19:1x | G1 复跑（脚本与测试源改动后） | `:app:testModernDebugUnitTest --rerun --no-build-cache` | **157 类 / 1114 用例 / 0 失败 / 0 跳过**（结果 XML mtime 19:18，非 UP-TO-DATE 假绿） |
| 09-23 19:1x | G1 lint | `:app:lintModernRelease --rerun` | **0e / 43w / 1h**；本轮 main 源零改动（`git status app/src/main` 空），lint analyze 输入面未变 ⇒ 与 08:20 那次真跑同判 |
| 09-23 19:2x | G6/G7 注入面（`-Stage scope`） | `run-acceptance.ps1 -Stage scope`（不重装） | 作用域行 PASS、注入断言 FAIL 且**自带归因** `environment: magisk/zygisk not running` + 四行判据（见上「设备档归因」）⇒ 后续 inject/mcp 两步按门禁纪律不跑（跑了只会得到与代码无关的红，且 G6-12 这类负向断言在无框架机器上会**假绿**） |
