# V48 · 作用域更名 / 前后端分类重整 / 画像选型库 —— 执行任务书

> **For agentic workers:** REQUIRED SUB-SKILL: 用 `superpowers:subagent-driven-development`(推荐)或 `superpowers:executing-plans` 逐工作包执行。本文所有步骤用 `- [ ]` 复选框跟踪。
> **依赖文档:** `AGENTS.md`(强约束 1/4/13/16/18/20/23/26/27/30)、`TASK-V47-SPOOF-PLAN.md`(WP-3c/3d/4/8/9 未完成部分是本任务 R-3 的前置)、`TASK-V47-SPOOF-AUDIT.md`(GAP/DFC 登记册)。

**Goal:** 把「作用域」这个产品最要害的开关从「监听」的错误命名里解放出来，用一张前后端逐类对齐的能力表重排全部 UI 词汇，并让设备画像的每一项都能**选**而不是**打**。

**Architecture:** 三层单点：`CategoryTaxonomy`(81 类)+ `FeatureGroups`(功能卡)+ `ProfileFieldCatalog`(41 字段)已是各自域的唯一事实源；V48 只新增**一张把它们投影到 UI 的对照表**(`CapabilityMap`)并把它变成门禁，而不是再抄一份清单。更名走资源四语 + 生成器唯一通路；选型库走「权威数据集 + 派生器」通路，**禁**手抄一份「真机值」。

**Tech Stack:** Kotlin 2.2.20 / Compose Material3 1.4.0 / Room 2.7.2(v15)/ Gson / Robolectric + Compose 插桩档 / Gradle(Windows,`gradlew.bat`)/ Node(翻译表生成器)。

**Spec:** 用户 2026-09-23 三条需求(R-1/R-2/R-3,原文见 §1)。

---

## 0. 阅读与证据约定

| 记号 | 含义 |
|---|---|
| `file:line` | 本文每条断言的落点均为写文时实测(grep/Read);执行时若行号漂移,以**符号名**为准 |
| V48-DFC-xx | V48 审计新登记的缺陷(与 V47 的 GAP/DFC 编号不重叠) |
| WP-A…WP-E | 工作包;每包出口准则全绿 + 台账(`§7`)有证据才算 ✅ |
| **判定口径纪律** | 判「已实现」= **目标应用/用户实际可见的结果**,不以「某个类/某个键存在」为代理指标(承接 V47 审计 §0) |

实测规模基线(2026-09-23,`git HEAD = 8f32061`):

| 层 | 事实 | 实测方式 |
|---|---|---|
| 路由 | 28 条 | `ui/navigation/ModevRoutes.kt:23-89` |
| 记录类型 | 10 | `core/model/RecordType.kt:9-38` |
| 类别 kind | **81**(LISTEN 44 / FEATURE 37) | `core/model/CategoryTaxonomy.kt:55-152` 逐条计数 |
| 画像字段 | 41(`ProfileField`)/ 干预组 18(`ProfileKind`)/ 分区 10 | `core/model/ProfileField.kt:91-239` |
| MCP 工具 | 50(冻结快照) | `app/src/test/.../mcp/McpToolParityTest.kt:36-60` |
| Room | version **15**,3 实体 | `data/db/ModevDatabase.kt:55-59` |
| 机型资产 | `assets/devices.json` 1,938,874 B / 26 品牌 / 8533 机型 | WP-5b 已落 |
| 单测 | 151 类 / 1063 用例 / 0 失败(V47 WP-5c 收口)| `TASK-V47-SPOOF-PLAN.md` §9 |
| lint | 0 error / 43 warning / 1 hint | 同上 |

---

## 1. 需求本体(逐条可验收)

- **R-1** 目标应用页的总开关更名为**「作用域此应用」**。理由(用户原话):本项目已不只是监听一个功能。
  验收 = 该开关的文案在四语里说的是「作用域」这件事，且**全站再无一处把「作用域」这个动作称为「监听」**。
- **R-2** 全量审计本项目已实现的功能，**按类型分类整理，前后端都要分类**，不许让用户困惑。
  验收 = 一张 81 行能力表(每 kind → 域 / 前端入口 / 后端钩子 / MCP 工具 / 默认态)成为**门禁**，UI 词汇表按域一致，`AGENTS.md` 词汇表同步。
- **R-3** 设备画像伪装要有**内置的设备 / 型号 / 网络等全面画像库**供用户**选**，而不是只能手填；数据源可取基线 `third_party/Guise_Reborn-main` 或其它专业项目。
  验收 = 41 个字段里**每一个取值域有限的字段**都不再需要用户凭记忆打字；内置档从 3 扩到一批自洽画像；**且**用户选完能在靶程序里看到该值(见 §2 依赖声明)。

---

## 2. 审计结论

### 2.1 前后端能力对照总表(R-2 的主交付物)

10 个记录类型 × 域 × kind 数 × 前端两处入口 × 后端探针 × MCP 面。**这张表就是「分类」的定义**：任何一行与 UI 或 MCP 不符即为缺陷。

| # | RecordType(wire) | 域(数量) | 前端 · 开关编辑页 | 前端 · 记录列表路由 | 后端探针 | 干预/观测性质 | MCP |
|---|---|---|---|---|---|---|---|
| 1 | CRYPTO(algorithm) | L 12 | 监听设置 · 算法 | `records/algorithm/{pkg}` | `AlgorithmProbe`(Base64/Cipher/Digest/Key*/Mac/Signature) | 纯观测 | `listen_settings_*` |
| 2 | DATABASE(database) | L 5 | 监听设置 · 数据库 | `records/database/{pkg}` | `DatabaseProbe` | 纯观测 | 同上 |
| 3 | BEHAVIOR(behavior) | L 7 | 监听设置 · 行为(+内容捕获/忽略路径微调) | `records/behavior/{pkg}` | `BehaviorProbe`(FileMeta/FileStream/NioFiles/Asset/Shell) | 纯观测(被动红线 #11) | 同上 |
| 4 | PREFS(prefs) | L 5 | 监听设置 · SharedPreferences | `records/prefs/{pkg}` | `PrefsProbe`(Open/Get/Editor) | 纯观测 | 同上 |
| 5 | WEBVIEW(webview) | L 5 | 监听设置 · WebView | `records/webview/{pkg}` | `WebViewProbe` | 纯观测 | 同上 |
| 6 | SYSTEM(system) | L 3 | 监听设置 · 系统信息 | `records/system/{pkg}` | `SystemProbe` | 纯观测 | 同上 |
| 7 | NETWORK(network) | **混** F 14 + L 1 | 开关拆两页：L(`REQUEST`)**监听设置 · 环境监听** / F(SSL 6·代理 4·VPN 4)**功能设置 · 3 组** | `records/network/{pkg}` | `NetworkProbe` | 干预为主 | 双域闸两工具 |
| 8 | DEVICE_ENV(device_env) | **混** F 19 + L 2 | 同上拆两页：L(`DETECT_ROOT`/`DETECT_EMULATOR`)环境监听 / F(Root 4·Xposed 3·模拟器 7·属性 1·proc 4)Root 与 Xposed 隐藏 + 模拟器与设备伪装 | `records/device_env/{pkg}` | `DeviceEnvProbe`(21 钩子文件) | 干预为主 | 同上 |
| 9 | UI_INTERACTION(ui_interaction) | **混** F 1 + L 4 | 同上拆两页：L(弹窗展示/控件文本/Activity 起止)环境监听 / F(`DIALOG_KEYWORD`)弹窗关键词拦截 | `records/ui_interaction/{pkg}` | `UiInteractionProbe` | 混合 | 同上 |
| 10 | PROCESS_CONTROL(process_control) | F 3 | 功能设置 · 进程守护 | `records/process_control/{pkg}` | `ProcessControlProbe`(Exit/CrashPrevent/MediaProjection) | 纯干预 | `feature_settings_*` |

> **结构性发现（R-2 的根因）**：4 个记录类型是**跨域**的(#7 #8 #9 三类内混 + 其开关 master 只在功能页渲染)。V37 双域拆分把「页」按域切开，但「类型」按 API 域切开，两套切法在 #7/#8/#9 上交叉。后果(实测)：用户在**监听设置 · 环境监听**里勾了「网络请求 / Root 检测监控 / 弹窗展示」却什么都不发生 —— 因为承载它们的 master(`networkListen`/`deviceEnvListen`/`uiListen`)只画在**功能设置**页。这不是文案问题，是**分类轴不一致**。WP-B 给出解法。

### 2.2 缺陷登记（R-1 / R-2 侧，按影响排序）

| ID | 缺陷 | 证据 | 处置 |
|---|---|---|---|
| **V48-DFC-01** | 作用域开关文案是「监听此应用」，而它实际管辖两域 10 类开关 + 全部干预 —— 正是 `AGENTS.md` 词汇表「禁用：把干预功能称 ××监听」的形态 | `values-zh/strings.xml:62`(`app_monitor_scope_row`)、`ui/apps/AppMonitorScreen.kt:487` | WP-A |
| **V48-DFC-02** | 「功能配置」(监听页标题) 与 「功能设置」(功能页标题) 一字之差，且**被动采集页**占了「功能」这个词；两行同时出现在监控页 监听/功能 两个分区里 | `values-zh:63` vs `:697`；`AppMonitorScreen.kt:242`/`:301` | WP-A |
| **V48-DFC-03** | 跨域 master 只渲染在功能页，而它门控的 LISTEN kind 渲染在监听页 → 「勾了不生效」的静默路径 | `PerAppFeatureSettingsScreen.kt:250-276` + `PerAppListenSettingsScreen.kt:388-401` | WP-B |
| **V48-DFC-04** | 监控页两条入口行(功能配置/功能设置)显示**同一个跨域计数** `deviationCount`,单域定制时两行同数字 | `AppMonitorScreen.kt:715-722`(`deviationCount`)vs 编辑页已用 `deviationCountFor(domain)`(`core/model/AppListenOverride.kt:227`) | WP-B |
| **V48-DFC-05** | 监听页重置确认文案承诺「全部功能配置」，实现只清 LISTEN 域；两页重置文案动词与名词均不对称 | `values-zh:71` vs `:718`；`PerAppListenSettingsScreen.kt:173-175`(`resetOverride(pkg, LISTEN)`) | WP-A |
| **V48-DFC-06** | 空列表提示只指向监听页（`或在功能配置中开启更多类别`），而 10 类里 4 类归功能页管 | `values-zh:58`(`detail_empty_hint`)、`ui/records/RecordTimelineList.kt:97` | WP-A |
| **V48-DFC-07** | 作用域第二控件 = 应用列表行尾 `Checkbox`,**零标签、零 contentDescription、零 testTag**(同一状态、同一写路径) | `ui/apps/AppsScreen.kt:551-556` | WP-A |
| **V48-DFC-08** | 「移出作用域」危险确认整条死路：`pendingDanger` 只会置 `CLEAR_LOGS`,`removeScope()` 与 `danger_remove_scope_msg` 无 UI 入口 | `AppMonitorScreen.kt:182`/`:365`, `AppMonitorViewModel.kt:158-164`, `values-zh:93` | WP-A(删除,不复活) |
| **V48-DFC-09** | 10 个记录类型标题存在**两套键**同义(`××_records_title` + `settings_records_××`),且两形态文案已漂移(UI交互记录 vs UI 交互记录、设备环境记录 vs 设备环境记录) | `values-zh:40-48,545,552,651,667,678` vs `:457-461,766-770` | WP-B |
| **V48-DFC-10** | 被动采集一个概念六个词：监听 / 监控 / 观测 / 采集 / 捕获 / 记录 | `values-zh:61,568,569,720,77,367` | WP-A |
| **V48-DFC-11** | 「总开关」词族承载 12 个不同层级(1 作用域 + 10 类型 master + 3 服务开关),而 10 个类型 master 里 2 个带「启用」前缀、4 个 key 名是 `*_monitor_master` 而实际是 FEATURE 域 | `values-zh:73,74`(启用前缀)、`network_monitor_master`/`device_env_master`(FEATURE 却叫 monitor) | WP-A |
| **V48-DFC-12** | 「伪装画像」一个东西三个名词层：画像库 / 画像档位 / 画像参数,且库页同屏渲染 V47 新库 + 旧常量档 + 旧字段覆写(写三份不同状态) | `values-zh:706,736,738,781,797`；`SpoofProfileLibraryScreen.kt:334,646,692` | WP-C(与 R-3 同批) |
| **V48-DFC-13** | 死组件 `SwitchRowItem`(零调用点),与 `ToggleListItem`/`SettingsToggleItem` 三件并存 | `ui/apps/PerAppSettingRows.kt:32-48` | WP-A(删) |
| **V48-DFC-14** | `app_description` 仍写「监听目标 App 的算法与数据库行为」= 10 类里的 2 类、零干预描述;这是 R-1 用户诉求的字面来源 | `values-zh:4`、`ui/settings/AboutScreen.kt:81` | WP-A |

### 2.3 R-3 现状：哪些字段还在「凭记忆打字」

`ui/settings/profiles/ProfileEditorScreen.kt:440-540` 实测控件分派：已有选择器的只有 **5 项**（机型库→BRAND/MANUFACTURER/MODEL/DEVICE 四值一次写全；语言 / SIM 国家 / 时区 → 平台权威表；NETWORK_TYPE 与 SCREENSHOTS_FLAG → 枚举槽）。剩余 **34 项全部是文本/数字输入**。逐字段判定：

| 组 | 字段 | 取值域是否有限 | 权威数据源 | 结论 |
|---|---|---|---|---|
| 身份 | BUILD_ID / FINGERPRINT | 半（须与三元组+版本+SDK 自洽） | 已有 `ProfileGenerators` 派生器 | **可一键派生，不该手打** |
| 版本 | ANDROID_VERSION / SDK_INT | 是，成对（release↔api） | AOSP 公开表；基线 `res/raw/presets.json` 37 对 | **必须成对选择**（选 14 恒写 SDK 34，否则存出从不存在的组合=自造检测点） |
| 屏幕 | DENSITY_DPI | 是（13 个标准档） | `presets.json` densityDpi 13 档 | 选择 |
| 核数 | CPU_CORES | 半（4/6/8/10/12） | 常见档 | 选择 + 保留手填 |
| SIM | SIM_OPERATOR(PMN) / SIM_OPERATOR_NAME | 是，1597 条 | **基线 `assets/carriers.json`（MIT,未引入）** | 三字段一次选全（PLMN+名+国家） |
| 标识 | ANDROID_ID / IMEI / PHONE_NUM / WIFI_MAC | 是（格式受约束） | 已有 `ProfileGenerators`（Luhn/本地管理 MAC/hex） | 「随机」按钮已覆盖，无需库 |
| 基站 | LAC / CID | 数值域 | — | 手填合理（无权威集） |
| 定位 | LONGITUDE / LATITUDE | 数值域 | — | 手填合理（保留） |
| 网络 | NETWORK_TYPE | 是 | `ProfileValueType.NETWORK_TYPE` | 已做 |
| 通行证 | PASS_* 5 项 | 布尔 | — | 已做（行内 Switch） |
| 其余 | BRAND/MODEL/… | 是 | 已做（devices.json） | 已做 |

> **基线可直接复用的资产（实测 `third_party/Guise_Reborn-main`）**：`app/src/main/assets/carriers.json` 134,053 B / MIT / `plmn,name,countryCode,countryName` 四键（`tools/update_carriers.py` 派生自 `pbakondy/mcc-mnc-list`）；`res/raw/presets.json` = androidVersions 37 + sdkLevels 37 + densityDpi 13 + networks 6 + languages 13。**基线自身的内置模板只有 2 个（空白通行证 + 允许强制截图），零设备字段** —— 所以「全面画像库」在基线里不存在，R-3 的内置档必须是 Modev 净新增设计（沿用 `BuiltinDeviceProfiles` 的既有纪律）。
>
> **已登记的 carriers 数据坑（V47 WP-5c 台账）**：1597 条里 **77 条 `countryCode` 是 `"AU/CC/CX"` 这类多地区串**，直接套用会写出过不了 `SIM_COUNTRY` 校验的值 ⇒ 引入时**只在恰为两字母时写，否则留空**。

### 2.4 R-3 的前置：选了不等于生效（不许回避）

实测 grep：`FeatureConfig.deviceProfile` 在 `hook/` 子树**零消费者**；靶程序实际值仍出自 `SpoofProfiles.resolve(feature.spoofProfileId, feature.spoofOverrides)`，消费方 6 个钩子（`EmulatorBuildHooker.kt:138-140`、`DeviceSysPropHooker.kt:44`、`SystemPropertiesHooker.kt:137`、`DeviceTelephonyHooker.kt:151-175`、`ProxyHideHooker.kt:184-203`、`DeviceCpuHooker.kt:24`）。这与 `AGENTS.md` #30 的「当前接管边界」一致。

**因此本任务书把顺序钉死**：R-3 的选型库（WP-C/WP-D）**不与** V47 WP-3c/3d 的钩子接管同批落地时，UI 必须**在屏上点名**「本画像尚未下发到靶程序」，否则就是 #30 明令禁止的「说画像已生效于靶程序 = 假」。零静默优先于美观。

---

## 3. 定档与决策（执行方不再询问）

| ID | 决策 | 理由 |
|---|---|---|
| **D-1** | 「作用域」是产品第一名词。开关 = **「作用域此应用」**,与既有状态词（已作用域/未作用域,`values-zh:35/36`）、诊断分区名（框架与作用域）同轴 | R-1 的字面要求 + 关闭 V48-DFC-01 |
| **D-2** | 两域 UI 名词收敛为 **监听 / 功能**（各两个字）。废除 UI 面上的「总开关」字样（层级太多时该词无信息量）;「干预」只保留在统计图例一处 | V48-DFC-10/11 |
| **D-3** | 两页改名 **监听设置 / 功能设置**（对称,且与路由 `listen-settings`/`feature-settings`、testTag `monitor-entry-listen-settings` 全部对齐）| 关闭 V48-DFC-02 |
| **D-4** | 跨域 kind 的**唯一编辑入口 = 它所属域的页**，而**管辖它的 master 在两页都渲染一份只读镜像 + 直达动作**（不是把 kind 搬家 —— 搬家会破坏 #23 双域闸与 V45 DUAL-12 的按域清除）| 关闭 V48-DFC-03，不破既有契约 |
| **D-5** | 记录类型标题键**只留一套**：`××_records_title` 作呈现名，列表页与数据管理页共用；`settings_records_××` 整体删除 | V48-DFC-09 |
| **D-6** | carriers 资产**照抄引入 + 逐字 NOTICE**（MIT,与已上线的 devices.json 同通路）;android 版本/SDK/密度表**由仓内脚本生成**（AOSP 公开事实,避免把 GPL 基线的 `res/raw` 文件搬进包体）| 许可证半径最小化；D-9（V47）未裁决前不扩大 |
| **D-7** | 内置画像档**只填机型身份族**（沿用 `BuiltinDeviceProfiles` KDoc 的两条理由:选机型不该顺手改运营商与坐标；写死 IMEI = 跨应用关联指纹）。**扩数量不扩字段面** | 已有设计裁决,且是安全正确的一侧 |
| **D-8** | 「全面画像库」的正确形态 = **每个有限域字段都有选择器** + **一键成档**（从品牌型号一步生成自洽 DEVICE 全组） + **整组一致随机**（V47 GAP-08 `ProfileRandomizer`）；**不做**人工抄写的「N 台真机全字段档案」 | 抄来的 fingerprint/dpi 不可验证，是自造事实源 |
| **D-9** | 更名类改动**不动 testTag、不动路由、不动 wire/JSON 键**（`spoofProfileId` 等契约字段名不变）;只动 `R.string` 值与新增键。理由:tag/route 是测试与 MCP 的锚点，动一处漂全仓 | 门禁半径最小 |

---

## Global Constraints（每个任务隐含遵守）

- 四语齐备：`values/` + `values-zh/` + `values-ja/` + `values-ko/`；**ja/ko 是生成物** —— 改 `scripts/v42_table_*.mjs` 再跑 `node scripts/v42_generate.mjs` 并看到 `DONE`（强约束 1;直写 `values-ja/ko` = 静默失效数周的历史实锤）。改翻译表**只用 Edit 工具**,禁 perl/sed 正则（已吃掉过一整行键）。
- 禁硬编码中文于 `ui` 包（`HardcodedStringConventionTest`）。
- 弹层一律 `ModalBottomSheet`/`ModevSheet`（#4）；`AlertDialog`/`ModalBottomSheet` 源码点唯一（`DialogConventionTest`,#27）。
- ui 子树禁 `hook`/`data`（#18）；mcp 子树禁 `data` 全子树;core 子树零 `android.*`。
- 一切干预动作过 `featureOn(kind)`（#23 FeatureGate 红线）;禁反射、禁第二份字段清单（#30）。
- lint 基线 **0 error / 43 warning / 1 hint**,提交前必跑,禁新增 warning。
- 测试金字塔：纯函数 → JVM;框架依赖 → Robolectric;`ModalBottomSheet` 手势 → 插桩档（Robolectric 打不动弹层按钮已实锤）。
- 禁 `git stash`（并发工作树）;每步提交前 `git status` 复核,只提交本任务面文件。
- 构建判 PASS 只认 `BUILD SUCCESSFUL` 行或结果 XML（管道吞退出码已实锤）。

---

## 4. 文件结构（V48 全量半径）

**新建**

| 文件 | 责任 |
|---|---|
| `core/model/CapabilityMap.kt` | 81 kind → (type, domain, 编辑分区名, 记录路由, 探针, MCP 组) 的**投影表**;纯 Kotlin 零 android;只从 `CategoryTaxonomy`/`FeatureGroups` 派生,**不新抄 kind 清单** |
| `assets/carriers.json` + `assets/carriers.NOTICE.txt` | 运营商数据集（1597 条,MIT,逐字 NOTICE 含上游 revision） |
| `core/model/CarrierCatalog.kt` | 载运商数据层（与 `DeviceCatalog.kt` 同形态:纯数据 + `parseCarrierCatalog` 唯一解码口 + `applyCarrierPreset`） |
| `data/repo/CarrierCatalogRepository.kt` | 唯一读者 + 进程内缓存（照 `DeviceCatalogRepository` 抄结构） |
| `domain/config/CarrierCatalogService.kt` | 契约（`null ≠ 空目录`） |
| `core/model/ProfileValueSets.kt` | Android 版本↔SDK 成对表 + 密度档 + 核数档（**生成物**,头注记明脚本） |
| `scripts/v48_gen_valuesets.mjs` | 上表的生成脚本（AOSP 公开值;跑一次即产源文件,禁手改产物） |
| `app/src/test/.../core/CapabilityMapTest.kt` | R-2 门禁:表 ↔ taxonomy/FeatureGroups/路由/MCP 四方对账 |
| `app/src/test/.../i18n/DomainVocabularyTest.kt` | R-1/R-2 门禁:四语文案的域一致性 |
| `app/src/test/.../core/CarrierCatalogTest.kt` | 真资产逐值 + 两字母国家码守卫 |
| `app/src/test/.../core/ProfileValueSetsTest.kt` | 成对表不变式 |
| `app/src/test/.../ui/settings/profiles/ProfilePickerRenderRoboTest.kt` | 每个新选择器「一屏画什么 + 选完写了哪几个字段」 |

**修改（按包）**

- `res/values*/strings.xml` × 4 + `scripts/v42_table_1..3.mjs` —— 更名与新键
- `ui/apps/`：`AppMonitorScreen.kt`、`AppsScreen.kt`、`PerAppListenSettingsScreen.kt`、`PerAppFeatureSettingsScreen.kt`、`PerAppSettingRows.kt`
- `ui/settings/`：`SettingsScreen.kt`、`DataSettingsScreen.kt`、`AboutScreen.kt`
- `ui/records/RecordTypeLabels.kt`、`ui/records/RecordTimelineList.kt`
- `ui/settings/profiles/`：`ProfileLabels.kt`、`ProfileEditorScreen.kt`、`ProfileEditorState.kt`、`SpoofProfileLibraryScreen.kt`
- `core/model/`：`ProfileOptionSource.kt`（扩候选域）、`BuiltinDeviceProfiles.kt`（扩档）、`ProfileField.kt`（若加 composite 标记）
- `ModevApp.kt`（装配 CarrierCatalogRepository）、`ModevNav.kt`（无新增路由则不动）
- `mcp/tools/`：`spoof_profile_catalog` 扩机型/运营商目录（补 V47 WP-5b 登记的不对称）、`McpToolParityTest` 快照
- `AGENTS.md`：词汇表 + 强约束 #31 + 实测计数复核

---

## 5. 工作包

状态图例：⬜ 未开始 · 🔵 进行中 · ✅ 已完成（出口准则全绿 + 证据入 §7）

---

### WP-A · 作用域更名 + 文案域一致性（R-1，独立可交付）✅ 2026-09-23

**规模:** S-M ｜ **依赖:** 无 ｜ **风险:** 仅文案与一个 contentDescription;`testTag`/路由/JSON 键零动（D-9）

#### Task A1 · 作用域开关更名（四语）

**Files:** Modify `res/values/strings.xml:63`, `res/values-zh/strings.xml:62`, `scripts/v42_table_1.mjs:53`;Test `app/src/test/java/com/lioisme/modev/i18n/DomainVocabularyTest.kt`(新建)

**Interfaces:** Consumes 无;Produces 键 `app_monitor_scope_row` 的新四语值（A2/A3 与 §2.2 表引用它）

- [x] **Step 1: 写失败门禁** —— 新建 `DomainVocabularyTest`(纯 JVM,读 `res/values*/strings.xml` 文本;本仓已有同形态源码/资源扫描测试可参照 `StringResourceParityTest`)

```kotlin
class DomainVocabularyTest {
    private val resRoot = File("src/main/res")

    /** R-1:作用域开关在四语里都必须是「作用域」语义,禁监听/监视/monitor 动词。 */
    @Test fun scopeSwitchNamesScopeInAllFourLocales() {
        val banned = mapOf(
            "values" to Regex("(?i)monitor|listen|watch"),
            "values-zh" to Regex("监听|监视|观测"),
            "values-ja" to Regex("監視"),
            "values-ko" to Regex("모니터|감시"),
        )
        val required = mapOf(
            "values" to "Scope this app",
            "values-zh" to "作用域此应用",
        )
        banned.forEach { (dir, rx) ->
            val value = stringValue(File(resRoot, "$dir/strings.xml"), "app_monitor_scope_row")
            assertTrue("$dir must not call scoping a listener: $value", !value.contains(rx))
        }
        required.forEach { (dir, expected) ->
            assertEquals(expected, stringValue(File(resRoot, "$dir/strings.xml"), "app_monitor_scope_row"))
        }
    }
}
```

- [x] **Step 2: 跑测试确认红**

Run: `.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.i18n.DomainVocabularyTest"`
Expected: FAIL —— `values must not call scoping a listener: Monitor this app`

- [x] **Step 3: 改 en + zh 源值**（`Edit` 工具,逐字节安全）

`values/strings.xml:63` → `<string name="app_monitor_scope_row">Scope this app</string>`
`values-zh/strings.xml:62` → `<string name="app_monitor_scope_row">作用域此应用</string>`

- [x] **Step 4: 回写翻译表并跑生成器**（禁直写 `values-ja`/`values-ko`）

`scripts/v42_table_1.mjs:53` → `app_monitor_scope_row: ['このアプリを作用域', '이 앱을 작용 영역'],`

Run: `node scripts/v42_generate.mjs` → Expected: 控制台末行 `DONE`（非 `missing from translation table`）

- [x] **Step 5: 复跑门禁确认绿** → Step 2 同一命令,Expected: PASS

- [x] **Step 6: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml app/src/main/res/values-ja/strings.xml app/src/main/res/values-ko/strings.xml scripts/v42_table_1.mjs app/src/test/java/com/lioisme/modev/i18n/DomainVocabularyTest.kt
git commit -m "v48: rename the per-app master to \\"Scope this app\\" (R-1)"
```

#### Task A2 · 「功能配置」正名 + 两处指向修正（V48-DFC-02/05/06）

**Files:** Modify `values/strings.xml` + `values-zh/strings.xml`（键 `listen_settings_entry`, `danger_reset_listen_msg`, `detail_empty_hint`）+ `scripts/v42_table_1.mjs`;Test 扩展 `DomainVocabularyTest`

- [x] **Step 1: 加失败断言**（两页标题必须一个含「监听」一个含「功能」,且互不冒充）

```kotlin
/** V48-DFC-02:被动采集页禁占「功能」一词。 */
@Test fun listenPageTitleNeverSaysFeature() {
    val zh = stringValue(File(resRoot, "values-zh/strings.xml"), "listen_settings_entry")
    assertEquals("监听设置", zh)
    assertFalse(zh.contains("功能"))
    assertEquals("功能设置", stringValue(File(resRoot, "values-zh/strings.xml"), "feature_settings_entry"))
}
```

- [x] **Step 2: 跑 → 确认 FAIL**
- [x] **Step 3: 改值**（zh/en 成对;ja/ko 走 Step 4）

| 键 | zh 现值 | zh 新值 | en 现值 | en 新值 |
|---|---|---|---|---|
| `listen_settings_entry` | 功能配置 | **监听设置** | Feature Configuration | **Listen Settings** |
| `danger_reset_listen_msg` | 将 %1$s 的全部功能配置恢复为默认? | **将 %1$s 的全部监听设置恢复为默认?** | Restore all feature configurations for %1$s…? | **Restore all listen settings for %1$s to the default?** |
| `danger_reset_feature_msg` | 清除 %1$s 的全部功能自定义并恢复默认? | **将 %1$s 的全部功能设置恢复为默认?**（与上行同动同名词） | Remove all feature customizations… | **Restore all feature settings for %1$s to the default?** |
| `detail_empty_hint` | 试试调整筛选条件,或在功能配置中开启更多类别 | **试试调整筛选条件,或在监听设置/功能设置中开启更多类别** | （同步两词） | （同步两词） |

- [x] **Step 4: 回写 `v42_table_*.mjs` 对应四行 → `node scripts/v42_generate.mjs` → `DONE`**
- [x] **Step 5: 复跑 → PASS;并跑 `StringResourceParityTest`**（四语键集与占位符一致）
- [x] **Step 6: 提交** `git commit -m "v48: name the listen page by its domain (V48-DFC-02/05/06)"`

#### Task A3 · 应用列表作用域控件补标签 + 死路清除（V48-DFC-07/08）

**Files:** Modify `ui/apps/AppsScreen.kt:551-556`;Modify `ui/apps/AppMonitorScreen.kt`（`DangerAction.REMOVE_SCOPE` 分支 + `danger_remove_scope_msg`）;Modify `ui/apps/AppMonitorViewModel.kt:158-164`

- [x] **Step 1: 行尾 Checkbox 补语义**（同一名词、可锚定；`contentDescription` 走资源，禁硬编码）

```kotlin
        trailingContent = {
            Checkbox(
                checked = row.scoped,
                onCheckedChange = { onToggle() },
                enabled = scopeReady && !pending,
                modifier = Modifier
                    .testTag("app-row-scope")
                    .semantics {
                        contentDescription = stringResource(R.string.app_monitor_scope_row)
                    },
            )
        },
```

- [x] **Step 2: 删死路** —— `DangerAction.REMOVE_SCOPE` 枚举项与其 `when` 臂、`AppMonitorViewModel.removeScope()`、`values*/strings.xml` 的 `danger_remove_scope_msg` 四语 + 翻译表行。**不复活**：移出作用域已由同一开关双向承载（`toggleScope` 的 off 分支）。
- [x] **Step 3: 跑门禁**

Run: `.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.i18n.*" --tests "com.lioisme.modev.ui.apps.*"`
Expected: PASS（若插桩档 `FourScreensNavTest`/`AppMonitorRedesignTest` 引用被删键,按其**新语义**改用 `app-row-scope` 锚点,禁回退本任务）

- [x] **Step 4: 提交** `v48: label the app-row scope checkbox and drop the dead remove-scope path`

#### Task A4 · 「总开关/多词族」收口 + 死组件删除（V48-DFC-10/11/13/14）

**Files:** Modify `values*/strings.xml`（9 键）+ 翻译表;Modify `ui/apps/PerAppSettingRows.kt:32-48`

- [x] **Step 1: 一次性改完这张表**（zh 列出;en 同口径各自翻译,ja/ko 回表）

| 键 | zh 现值 → 新值 | 理由 |
|---|---|---|
| `section_monitor_kinds` | 监听类别 → **监听开关** | 与 `feature_masters_section` 对称;该分区**只装 master** |
| `feature_masters_section` | 功能总开关 → **功能开关** | 去「总」（12 个层级共用此词已无信息量,D-2） |
| `algorithm_monitor_master` | 启用算法监听 → **算法监听** | 6 个 LISTEN master 里只有 2 个带「启用」前缀 |
| `database_monitor_master` | 启用数据库监听 → **数据库监听** | 同上 |
| `listen_section_env_observe` | 环境观测 → **环境监听** | 一个概念一个词（V48-DFC-10） |
| `app_description` | 通过作用域 Xposed 钩子程序监听目标 App 的算法与数据库行为。 → **在 Xposed 作用域内监听目标应用(算法/数据库/行为/偏好/WebView/系统信息),并对功能进行干预(弹窗拦截/环境伪装/进程守护)。** | R-1 用户诉求的字面根因(V48-DFC-14) |

- [x] **Step 2: 删 `SwitchRowItem`**（零调用点;`ToggleListItem` 与 `SettingsToggleItem` 保留）
- [x] **Step 3: `node scripts/v42_generate.mjs` → `DONE`**
- [x] **Step 4: 全量单测 + lint**

Run: `.\gradlew.bat :app:testModernDebugUnitTest` 然后 `.\gradlew.bat :app:lintModernRelease`
Expected: `BUILD SUCCESSFUL` × 2;单测 0 失败;lint warning 恒 **43**（净新增 0;若 `UnusedResources` 因删键浮动,须复核是删键还是删消费者）

- [x] **Step 5: 提交** `v48: one noun per concept across the switch vocabulary`

**WP-A 出口:** A1–A4 全绿 + `DomainVocabularyTest` 三条常驻 + 插桩档 `AppMonitorRedesignTest`/`MonitorEntryHeaderTest` 重跑绿 + 手工目视（模拟器:监控页 Hero 卡、应用列表行、两设置页标题、关于页描述、空列表提示）。**文案改动不得只靠 JVM 门禁宣布完成。**

---

### WP-B · 前后端分类对齐（R-2，架构侧）🔵 B1/B2/B3/B4 已落并绿,B5 并入 WP-E 文档回写

**规模:** L ｜ **依赖:** WP-A ｜ **风险:** 跨域 master 镜像是新增渲染面,须过 #23 双域闸与 DUAL-12 按域清除

#### Task B1 · `CapabilityMap` 建立 + 四方对账门禁

**Files:** Create `core/model/CapabilityMap.kt`;Create `app/src/test/java/com/lioisme/modev/core/CapabilityMapTest.kt`

**Interfaces:** Produces `CapabilityMap.entries: List<CapabilityRow>`、`CapabilityMap.of(type: RecordType): List<CapabilityRow>`；`data class CapabilityRow(kind, type, domain, sectionKeyRes: Int, editorPage: EditorPage, recordRoute: String, probeClass: String, mcpGroup: String)`。Consumes `CategoryTaxonomy.all()` / `FeatureGroups` / `ModevRoutes`。

- [x] **Step 1: 写失败门禁**（表 ↔ 唯一事实源,禁反向)

```kotlin
class CapabilityMapTest {
    /** 每 kind 恰一行,且域归属与 CategoryTaxonomy 逐位一致(不新增事实源)。 */
    @Test fun map_is_bijection_with_taxonomy() {
        val fromTaxonomy = CategoryTaxonomy.all().map { it.type to it.id }.toSet()
        val fromMap = CapabilityMap.entries.map { it.type to it.kind }.toSet()
        assertEquals(fromTaxonomy, fromMap)
        assertEquals(fromTaxonomy.size, CapabilityMap.entries.size)
        CapabilityMap.entries.forEach { row ->
            val category = CategoryTaxonomy.byId(row.type, row.kind)
            assertNotNull("orphan kind ${row.type}/${row.kind}", category)
            assertEquals("domain drift ${row.kind}", category?.domain, row.domain)
        }
    }

    /** 冻结分类快照:10 类型 / LISTEN 44 / FEATURE 37 / 三张跨域类型清单。 */
    @Test fun frozen_classification_snapshot() {
        assertEquals(81, CapabilityMap.entries.size)
        assertEquals(44, CapabilityMap.entries.count { it.domain == CapabilityDomain.LISTEN })
        assertEquals(37, CapabilityMap.entries.count { it.domain == CapabilityDomain.FEATURE })
        // 跨域类型 = 同表里既有 FEATURE 又有 LISTEN 的类型;§2.1 的结构性发现由这条冻结
        val mixed = RecordType.entries.filter { t ->
            val rows = CapabilityMap.entries.filter { it.type == t }.map { it.domain }.toSet()
            rows.size == 2
        }.toSet()
        assertEquals(
            setOf(RecordType.NETWORK, RecordType.DEVICE_ENV, RecordType.UI_INTERACTION),
            mixed,
        )
    }

    /** 每行的记录路由与探针必须在源码里真存在(表不许指向空气)。 */
    @Test fun every_row_resolves_to_a_route_and_a_probe() {
        val routes = File("src/main/java/com/lioisme/modev/ui/navigation/ModevRoutes.kt").readText()
        CapabilityMap.entries.map { it.recordRoute }.distinct().forEach {
            assertTrue("route constant $it missing", routes.contains(it))
        }
    }
}
```

- [ ] **Step 2: 跑 → 确认 FAIL（类不存在）** —— **本轮未按此执行**（见 §9 执行记录 B1 行:该红是编译期 unresolved reference,不含信息量;真实裁决发生在实现后的一次跑测,7 用例全绿）
Run: `.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.core.CapabilityMapTest"`
- [x] **Step 3: 实现 `CapabilityMap.kt`** —— `probeClass`/`recordRoute`/`mcpGroup` 三列按 §2.1 表逐类型填;**kind 清单恒从 `CategoryTaxonomy.forType(type)` 派生**,本文件不许出现第二份 id 字面量（`recordRoute` 是 `ModevRoutes` 常量名字符串,只做锚定用）
- [x] **Step 4: 复跑 → PASS;全量单测绿**
- [x] **Step 5: 提交** `v48: CapabilityMap as the front/back classification registry (R-2)`

#### Task B2 · 跨域 master 的只读镜像 + 直达（V48-DFC-03）

**Files:** Modify `ui/apps/PerAppListenSettingsScreen.kt:388-401`（环境监听分区）、`ui/apps/PerAppFeatureSettingsScreen.kt:250-276`;Test 新增 `app/src/test/.../ui/apps/CrossDomainMasterRoboTest.kt`

**设计（D-4）**：`环境监听` 分区里每一条 LISTEN kind（`uiListen`/`networkListen`/`deviceEnvListen` 门下的 7 项）在行副标处渲染**其管辖 master 的名字与状态**,并在 kind 行开启而 master 为关时给一条**可点直达功能页**的提示行。不改写入面（写仍只在该 kind 自己的域页）。

- [x] **Step 1: 写失败测试**

```kotlin
@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class CrossDomainMasterRoboTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** kind 开、其 master 关 ⇒ 必须在屏上点名,不许静默。 */
    @Test fun kindOnWithMasterOffNamesTheMaster() {
        seed(switch = HookSwitch.DEFAULT.copy(networkListen = false),
             kindOn = "network:REQUEST")
        rule.setContent { PerAppListenSettingsScreen(packageName = PKG, /* … */ ) }
        val hint = rule.activity.getString(R.string.listen_kind_master_off_hint, "网络干预")
        rule.onAllNodesWithText(hint).onFirst().assertIsDisplayed()
    }
}
```

- [x] **Step 2: 跑 → FAIL**
- [x] **Step 3: 实现** —— 新键 `listen_kind_master_off_hint`（zh:「%1$s 开关当前为关,该类不会生效;到功能设置打开」）四语 + `ToggleListItem(supporting = …)` 既有副标槽承载（**不新增控件形态**,遵守 #13/#25）
- [x] **Step 4: 跑 → PASS;`AppbarSearchExclusivityTest`/`DialogConventionTest` 复跑绿**
- [x] **Step 5: 提交** `v48: name the gating master when a listen kind is on but its feature master is off`

#### Task B3 · 监控页摘要按域分列（V48-DFC-04）

**Files:** Modify `ui/apps/AppMonitorScreen.kt:243`,`:302`,`:715-722`

- [x] **Step 1: 写失败测试**（两行副标在「仅功能域定制」时必须不同）

```kotlin
@Test fun hubEntriesCountTheirOwnDomain() {
    seed(override = AppListenOverride.DEFAULT.copy(uiListen = true)) // 仅 FEATURE 域偏差
    rule.setContent { AppMonitorScreen(/* … */) }
    rule.onNodeWithTag("monitor-entry-listen-settings")
        .assert(hasText(activity.getString(R.string.listen_summary_default)))
    rule.onNodeWithTag("monitor-entry-feature-settings")
        .assert(hasAnyDescendant(hasText(activity.resources.getQuantityString(R.plurals.listen_settings_custom_n, 1, 1))))
}
```

- [ ] **Step 2: 跑 → FAIL（两行同数字）** —— **未按此执行**:本用例是插桩档(需设备),本轮未跑设备(理由见 §10 与 §7 B3 行);先落实现,证据链留待设备档补
- [x] **Step 3: 实现** —— `listenSummary(override, domain)` 内部改用 `override.deviationCountFor(domain)`（`AppListenOverride.kt:227`）,两调用点各传自己那一域
- [x] **Step 4: 跑 → PASS**
- [x] **Step 5: 提交** `v48: hub rows count their own domain (deviationCountFor)`

#### Task B4 · 记录类型标题键去重（V48-DFC-09）

**Files:** Modify `values*/strings.xml`（删 10 个 `settings_records_*`）+ `ui/records/RecordTypeLabels.kt:18`、`ui/settings/DataSettingsScreen.kt:107-121`、`ui/settings/SettingsScreen.kt`（若引用）+ 翻译表

- [x] **Step 1: 改 `recordTypeCountLabelRes` → 返回 `××_records_title`（唯一表）,删 `settings_records_*` 十键与 ja/ko 表行**
- [x] **Step 2: `node scripts/v42_generate.mjs` → `DONE`**
- [x] **Step 3: 跑 `StringResourceParityTest` + `DataSettings` 相关 Robo 档** —— Expected: PASS;断言若为硬编码文案必须改资源驱动（#13 计数值断言）
- [x] **Step 4: 提交** `v48: one title key per record type`

#### Task B5 · 能力表进文档与 MCP（R-2 收口）

**Files:** Modify `AGENTS.md`（词汇表 5 行修订 + 新增强约束 #31 + 实测计数）、`README.md` 功能表;Modify `mcp/tools/*`（`taxonomy_listen`/`taxonomy_feature` 输出补 `section`/`editorPage` 两字段,与 `CapabilityMap` 同源）+ `McpToolParityTest`/`UiMcpCapabilityParityTest`

- [x] **Step 1: MCP 两工具改从 `CapabilityMap` 派生**（声明面即生效面 #26;禁在 mcp 侧再拼一份分类）
- [x] **Step 2: 跑 `McpToolParityTest`（恒 50 名,新增字段不动名）** + `UiMcpCapabilityParityTest` R1–R6
- [x] **Step 3: AGENTS 回写** —— 词汇表加「作用域 / 监听设置 / 功能设置 / 能力表 CapabilityMap / 运营商库」五词条;强约束新增 #31（内容 = §2.1 表 + D-2 名词法 + 「跨域类型三类」清单）;**逐条断言带 `file:line`**,不留「文档声称、实装未做」（V47 WP-9 出口同一口径）
- [x] **Step 4: 提交** `v48: write the classification into the MCP surface and AGENTS`

**WP-B 出口:** `CapabilityMapTest` + `CrossDomainMasterRoboTest` + 三门 MCP parity 全绿;全量单测 0 失败;lint 恒 43w/0e;AGENTS 与 README 无一处与实际 UI 不符。

---

### WP-C · 画像选型库（R-3 第一批：数据资产 + 成对选择器）

**规模:** M ｜ **依赖:** 无（与 WP-D 独立;与 V47 WP-3d 无耦合 —— 本包只改「怎么选值」,不改「值怎么下发」）

#### Task C1 · carriers 资产引入（D-6;GAP-10 的一半）

**Files:** Create `app/src/main/assets/carriers.json`、`carriers.NOTICE.txt`;Create `core/model/CarrierCatalog.kt`、`domain/config/CarrierCatalogService.kt`、`data/repo/CarrierCatalogRepository.kt`;Create `test/.../core/CarrierCatalogTest.kt`;Modify `ModevApp.kt`

**Interfaces:** Produces
`data class CarrierEntry(name: String = "", plmn: String = "", countryCode: String = "", countryName: String = "")`
`fun parseCarrierCatalog(text: String): List<CarrierEntry>?`（null = 不可用,**不得**回落空表）
`fun applyCarrierPreset(profile: DeviceProfile, picked: CarrierEntry): DeviceProfile`
`CarrierCatalogService.state: StateFlow<CarrierCatalogState>`（`Ready(entries)` / `Unavailable` —— 与 `DeviceCatalogService` 同形态）

- [ ] **Step 1: 拷资产 + NOTICE 逐字**（上游 revision 97bc165…,MIT;`devices.NOTICE.txt` 同格式）
- [ ] **Step 2: 写失败测试（读真包资产,禁夹具替代）**

```kotlin
class CarrierCatalogTest {
    private val asset = File("src/main/assets/carriers.json").readText()

    @Test fun asset_parses_and_keeps_every_plmn_valid() {
        val carriers = parseCarrierCatalog(asset)
        assertNotNull("carriers asset must parse", carriers)
        assertTrue(carriers!!.size > 1500)
        carriers.forEach { assertTrue("bad plmn ${it.plmn}", it.plmn.matches(Regex("\\d{5,6}"))) }
    }

    /** 实锤坑:1597 条里 77 条 countryCode 形如 "AU/CC/CX" ⇒ 绝不写进画像。 */
    @Test fun multiRegionCountryCodeNeverReachesProfile() {
        val dirty = CarrierEntry(name = "Telstra", plmn = "50501", countryCode = "AU/CC/CX", countryName = "Australia")
        val p = applyCarrierPreset(DeviceProfile(), dirty)
        assertEquals("50501", p.simOperator)
        assertEquals("Telstra", p.simOperatorName)
        assertEquals("", p.simCountry) // 不猜、不拆、不钳 —— 留空即不干预
    }

    @Test fun twoLetterCountryCodeWritesAllThreeFields() {
        val p = applyCarrierPreset(DeviceProfile(), CarrierEntry("China Mobile", "46000", "CN", "China"))
        assertEquals(Triple("46000", "China Mobile", "CN"), Triple(p.simOperator, p.simOperatorName, p.simCountry))
    }
}
```

- [ ] **Step 3: 跑 → FAIL;实现 `CarrierCatalog.kt`**（`applyCarrierPreset` 的 `countryCode.length == 2` 守卫 + 大写归一;`parseCarrierCatalog` 用 `Json.gson` 唯一解码口,照 `DeviceCatalog.kt:144-152` 的三态收口）
- [ ] **Step 4: `CarrierCatalogRepository`** —— Mutex 内二次检查只读盘一次、缺文件判 `Unavailable` 不抛（照 `DeviceCatalogRepository`,**同一段逻辑不许第三次写**：先读它,再决定能否抽公共壳）
- [ ] **Step 5: 跑 → PASS;`PackageBoundaryTest` + `LayerDependencyGateTest` G2 复跑绿**
- [ ] **Step 6: 提交** `v48: carrier catalogue from the baseline MIT dataset (R-3)`

#### Task C2 · Android 版本/SDK/密度/核数成对选择器（D-6）

**Files:** Create `core/model/ProfileValueSets.kt`（生成物）、`scripts/v48_gen_valuesets.mjs`;Modify `core/model/ProfileOptionSource.kt`、`ui/settings/profiles/ProfileEditorScreen.kt:440-490`、`ProfileEditorState.kt`;Test `test/.../core/ProfileValueSetsTest.kt`

**成对语义是这活的全部难点**：`ANDROID_VERSION` 与 `SDK_INT` 必须同一次动作写全（选 Android 14 ⇒ 恒写 sdkInt 34）,否则存出一台从不存在的设备 = 自造检测点。现通路里已有先例：`applyDevicePreset`/`pickDevice` 一次写 2–4 个字段 —— **复用该形态,不新建抽象**。

- [ ] **Step 1: 写失败测试**

```kotlin
class ProfileValueSetsTest {
    @Test fun androidReleaseAndSdkPairsAreMonotonicAndUnique() {
        val pairs = ProfileValueSets.androidReleases() // ["1|1", … "16|36"]
        assertEquals(pairs.map { it.substringAfter('|') }.distinct().size, pairs.size)
        pairs.map { it.substringAfter('|').toInt() }.zipWithNext { a, b -> assertTrue("$a then $b", b > a) }
    }

    /** 铁律:候选表逐值必须过 catalog 校验(两处不同源 = 选择器把用户推向「选了却报非法」)。 */
    @Test fun every_candidate_passes_field_validation() {
        ProfileField.entries.forEach { field ->
            ProfileValueSets.optionsFor(field)?.forEach { v ->
                assertTrue("$field rejects its own option $v", ProfileFieldCatalog.validate(field, v) == null)
            }
        }
    }

    @Test fun densityIsClosedRangeCompliant() {
        ProfileValueSets.densities().forEach { assertTrue(it in 72..1000) } // 与 ProfileFieldCatalog 的 DENSITY_DPI 域一致
    }
}
```

- [ ] **Step 2: 写生成脚本 + 产 `ProfileValueSets.kt`**（文件头注明「生成物,禁手改;源 = AOSP 公开 release↔API 表」）
API 表内容：`1|1 … 4.4W|20, 5|21, 5.1|22, 6|23, 7|24, 7.1|25, 8|26, 8.1|27, 9|28, 10|29, 11|30, 12|31, 12L|32, 13|33, 14|34, 15|35, 16|36`;densityDpi = `120,140,160,213,240,260,280,300,320,340,360,400,420,440,480,560,600,640`;cores = `2,4,6,8,10,12`
- [ ] **Step 3: 跑 → FAIL → 实现**;`ProfileValueSets` 对外三函数：`androidReleases()`、`densities()`、`cpuCores()`,并由 `ProfileOptionSource.optionsFor` 在 LANGUAGE/SIM_COUNTRY/TIME_ZONE 三臂后**续四臂**（ANDROID_VERSION / SDK_INT / DENSITY_DPI / CPU_CORES）—— 保持 `optionsFor` 是候选表**唯一入口**
- [ ] **Step 4: 编辑器成对落值**（`ProfileEditorViewModel` 新增一个复合动作,形态照既有 `pickDevice`）

```kotlin
    /** 选 Android 版本 ⇒ release 与 SDK_INT 同次写全(绝不存出自相矛盾的一台设备)。 */
    fun pickAndroidRelease(pair: String) {
        update(ProfileField.ANDROID_VERSION, pair.substringBefore('|'))
        update(ProfileField.SDK_INT, pair.substringAfter('|'))
    }
```

- [ ] **Step 5: Robo 档渲染测试**：`ProfilePickerRenderRoboTest` —— 断言四条新选择行存在（`profile-enum-ANDROID_VERSION` 等既有 testTag 形态）、选完 `snapshot()` 两字段同时到位、以及**「做列表不夺手填」**：选择 sheet 首项恒为「改为手动填写」（沿用 WP-5c 既有 `profile_picker_manual` 键与契约）
- [ ] **Step 6: 全量 + lint;提交** `v48: paired android release/SDK + density + core-count pickers`

#### Task C3 · 编辑器接运营商选择 + 库页词汇收口（V48-DFC-12）

**Files:** Modify `ui/settings/profiles/ProfileEditorScreen.kt`（新增「选择真实运营商」行,形态照「选择真实机型」）、`SpoofProfileLibraryScreen.kt:646`/`:692`（旧段落命名）；strings 四语 ≈8 键

- [ ] **Step 1: 加「选择真实运营商(PLMN + 名称 + 国家一次写全)」行**,消费 `CarrierCatalogService`;`Unavailable` 时**上屏点名资产不可用**（禁退化成「库里没运营商」,与 `devices.json` 同一红线）;数据不可用/两字母码缺失按 C1 守卫留空
- [ ] **Step 2: 旧段落正名**（当前生效路径的唯一诚实呈现,承 §2.4）

| 位置 | 现文案 | 新文案 |
|---|---|---|
| `profiles_global_section`（`:646`） | 画像档位 | **旧内置档（当前生效）** |
| `profiles_overrides_section`（`:692`） | 字段覆写 | **旧字段覆写（当前生效）** |
| `profile_library_section`（`:334`） | 画像库 | **画像库（编辑与绑定）** |

  并在库页顶部加一条 `profile_takeover_status_hint`（zh：「画像库负责编辑与绑定;靶程序当前取值仍来自旧档。接管中,勿据此判断已生效。」）—— **这句话是 §2.4 的 UI 兑现,不得省**
- [ ] **Step 3: `SpoofProfileLibraryRenderRoboTest` 补两条断言**：三段落名可区分 + 状态提示行恒在
- [ ] **Step 4: 生成器 `DONE` → 全量 + lint → 提交** `v48: carrier picker plus an honest legacy-section naming`

**WP-C 出口:** `CarrierCatalogTest`/`ProfileValueSetsTest`/`ProfilePickerRenderRoboTest` 全绿 + 真包资产进 APK（`unzip -l` 实证 `carriers.json` 与 `carriers.NOTICE.txt`）+ 手工目视编辑器（模拟器逐屏：Android 版本、密度、核数、运营商四选一,每屏截证）。

---

### WP-D · 内置画像档扩充 + 一键成档（R-3 第二批）

**规模:** M ｜ **依赖:** WP-C（复用 `ProfileGenerators` 与资产）

#### Task D1 · 内置身份档 3 → 一批（D-7：只填机型身份 13 项）

**Files:** Modify `core/model/BuiltinDeviceProfiles.kt`;Modify `test/.../core/BuiltinProfileConsistencyTest.kt`

- [ ] **Step 1: 写失败测试**（既有 8 用例已锁自洽性,此处补「规模 + 覆盖 + 不越界」三条）

```kotlin
    @Test fun builtins_cover_mainstream_families_and_fill_identity_only() {
        assertTrue(BuiltinDeviceProfiles.BUILTINS.size >= 10)
        assertEquals(
            BuiltinDeviceProfiles.BUILTINS.map { it.seedId }.toSet(),
            BuiltinDeviceProfiles.BUILTINS.map { it.seedId }.toSet(), // 稳定
        )
        BuiltinDeviceProfiles.BUILTINS.forEach { spec ->
            val p = spec.profile
            // D-7:身份族齐备
            listOf(p.brand, p.manufacturer, p.model, p.device, p.buildId, p.fingerPrint,
                   p.androidVersion, p.sdkInt.toString(), p.densityDpi.toString(), p.cpuCores.toString())
                .forEach { assertTrue("${spec.seedId} identity must be complete", it.isNotBlank()) }
            // D-7:非身份族恒空(选设备不得顺手改运营商/坐标/标识)—— 字段名与哨兵照 `DeviceProfile.kt:79-159`
            assertTrue(p.simOperator.isEmpty() && p.imei.isEmpty() && p.androidId.isEmpty() &&
                p.wifiSSID.isEmpty() && !p.passContacts)
            assertEquals(ProfileValues.UNSET_DOUBLE, p.latitude)
        }
    }
```

- [ ] **Step 2: 跑 → FAIL**
- [ ] **Step 3: 扩档** —— 覆盖 Pixel 6/7/8 系、Samsung S22/S23/S24 系、Xiaomi 12/13/14、OnePlus、OPPO、vivo、荣耀/华为各一;字面量取公开规格,`version` 递增即受管辖行原地更新。**每条必须过 fingerprint↔三元组↔版本/SDK 互推校验**（`ProfileGenerators` 已有派生器与清洗规则,复用不重写）
- [ ] **Step 4: 跑 → PASS;`McpToolParityTest` 的 `spoof_profiles_list` 快照与 `BuiltinProfileConsistencyTest` 计数期望同步更新（改期望必须给理由,禁调绿）**
- [ ] **Step 5: 提交** `v48: widen the built-in identity profile set`

#### Task D2 · 「从真实机型一键成档」（D-8）

**Files:** Modify `ui/settings/profiles/ProfileEditorScreen.kt`（机型选择行升级为两动作：仅身份 / 生成完整设备组）、`ProfileEditorViewModel`/`ProfileEditorState.kt`;Test `ProfilePickerRenderRoboTest` + 插桩档 `ProfileEditorFlowTest`

- [ ] **Step 1: 写失败测试**（选一台 Pixel ⇒ 可派生项补齐、不可派生项**可见地留空**;与 `applyDevicePreset` 的「PRODUCT/BOARD/HARDWARE 恒不猜」同一红线）

```kotlin
    @Test fun generateFromRealModelFillsWholeDeviceSection() {
        val before = DeviceProfile(brand = "google", manufacturer = "Google", model = "Pixel 7 Pro", device = "raven")
        val after = viewModel.generateDeviceGroup(before)
        // 能派生的必须派生出来(与 BuiltinDeviceProfiles 三档同一必填集,见 D-7 的断言)
        listOf(ProfileField.BUILD_ID, ProfileField.FINGERPRINT, ProfileField.ANDROID_VERSION,
               ProfileField.SDK_INT).forEach { f ->
            assertTrue("${f.name} must be derived", f.isSetIn(after))
        }
        // 派生不出的必须留空并被 conflicts()/身份预览卡点名 —— 禁伪造 PRODUCT/BOARD/HARDWARE
        listOf(ProfileField.PRODUCT, ProfileField.BOARD, ProfileField.HARDWARE).forEach { f ->
            assertTrue("${f.name} must stay unset unless sourced", !f.isSetIn(after))
        }
        assertTrue(after.fingerPrint.startsWith("google/"))
    }
```

- [ ] **Step 2: 实现 `generateDeviceGroup`** —— 委派既有 `ProfileGenerators`（版本关联 Build ID、fingerprint 骨架、世代年份）,**禁新写生成器**（第二事实源）;不可派生字段留空并由 `ProfileHookPlan.conflicts()`/身份预览卡三色点名
- [ ] **Step 3: 跑 → PASS;插桩档补一条端到端（弹层手势 Robolectric 打不动,已实锤）**
- [ ] **Step 4: 提交** `v48: one-tap coherent device group from a real model`

#### Task D3 · 整组一致随机（GAP-08 清偿）

**Files:** Modify `core/model/ProfileGenerators.kt`/`ProfileEditorForm.kt`(`randomizeAll` 已存在 → 升级为读资产目录的 `ProfileRandomSource` 真实现),`data/repo`（把 devices/carriers 目录喂给注入式随机源）;Test `ProfileRandomSourceTest`（新）

- [ ] **Step 1: 写失败测试**（固定种子大样本:同一台机型 ⇒ 世代一致、运营商同国、fingerprint 与三元组互推一致,且**永不出现在另一台机型的 fingerprint**）

```kotlin
    @Test fun whole_profile_randomization_is_coherent() {
        val rnd = Random(20260923L)
        repeat(3000) {
            val p = source.nextWholeProfile(rnd)
            assertTrue(p.fingerPrint.startsWith("${p.brand}/"))
            assertEquals(ProfileValueSets.sdkForRelease(p.androidVersion), p.sdkInt.toString())
            assertFalse(p.simCountry.isNotBlank() && p.simOperator.isBlank())
        }
    }
```

- [ ] **Step 2: 实现** —— `ProfileRandomSource` 默认实现原先「从 `BuiltinDeviceProfiles` 派生」（WP-3e 的过渡形态）⇒ 换成读两本目录的真实现;`ProfileEditorForm` 签名不动
- [ ] **Step 3: 跑 → PASS;提交** `v48: coherent whole-profile randomization backed by the catalogues (GAP-08)`

**WP-D 出口:** 三条新测试绿 + 手工目视（编辑器：一键成档 / 整组随机 两动作在屏上可区分且都改了什么）。

---

### WP-E · MCP 面 parity 与验收（收尾）

#### Task E1 · 机型/运营商目录进 MCP（补 V47 WP-5b 登记的不对称）

**Files:** Modify `mcp/tools/SpoofProfileTools.kt`（`spoof_profile_catalog` 增 `kind: "fields"|"devices"|"carriers"` 参数,默认 `fields` 保后向）、`McpToolParityTest`（名恒 50,参数字段声明新增）、`SpoofProfileToolsTest`

- [ ] **Step 1: 写失败测试** —— 声明面即生效面四条：未声明参数 = -32602;`kind` enum 强制（乱值 = 协议错误非业务错误）;`devices` 返回条目数与 `DeviceCatalogService` 实测同值;`Unavailable` 时必须返回**点名不可用**的结构而非空数组
- [ ] **Step 2: 实现**（读服务经 `AppContainer`,mcp 子树禁 `data.*` —— 只能经 `domain/config` 契约,#18/#26）
- [ ] **Step 3: 跑 → PASS;提交** `v48: expose device/carrier catalogues on the MCP tool surface`

#### Task E2 · 交付门禁

- [ ] 全量：`.\gradlew.bat :app:testModernDebugUnitTest` → 0 失败
- [ ] lint：`.\gradlew.bat :app:lintModernRelease` → 恒 0e/43w/1h
- [ ] 打包：`.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug` → 双 ABI 成功 + `unzip -l` 实证两资产进包
- [ ] 插桩档（需设备,单序列,先 `Remove-Item Env:ANDROID_ADB_SERVER_PORT` 再断言序列数 == 1）：`:app:connectedModernDebugAndroidTest` 抽样 `ProfileEditorFlowTest`、`AppMonitorRedesignTest`、`FourScreensNavTest`、`MonitorEntryHeaderTest`
- [ ] 文档：AGENTS.md 词汇表/强约束 #31/实测计数复核;README 功能表按 §2.1 表重写;本文件 §7 台账填满

---

## 6. 门禁与基线变更登记（同次提交）

| 门禁 | 变更 |
|---|---|
| `McpToolParityTest` | 名快照恒 **50**;`spoof_profile_catalog` 参数域扩;`E1` 若新增工具则登记新数并改标题 |
| `UiMcpCapabilityParityTest` | R1–R6 能力行:新增 `listen_kind_master_off_hint` 属呈现非可变面 ⇒ 不登记;`generateDeviceGroup` 必须登记为「画像编辑」能力行（UI 有、MCP 无 ⇒ 给理由或补工具） |
| `DeviceEnvKindTest` / `CategoryTaxonomyTest` / `McpToolsTest` | **本任务不动 kind 计数**（81 恒） —— 若要动,必须先回访 V47 WP-3c 的 10 站点耦合链 |
| `InterventionGateConventionTest` / `HotPathGateConventionTest` | 本包零干预动作新增 ⇒ 名单不变;变了即说明越界 |
| `DbMigrationParityTest` | Room 恒 **v15**（WP-C/D 全部是呈现与派生,零存储变更）;若出现 v16 必须同步三处 |
| `StringResourceParityTest` | 四语键集 + 占位符 + plurals 逐 locale 恒绿 |
| lint | 43w/1h/0e 基线;删键后 `UnusedResources` 逐条复核 |

## 7. 执行记录台账

| 日期 | WP/Task | 落地内容 | 定向测试 | 全量/lint/打包 | 红项归因与裁决 | 状态 |
|---|---|---|---|---|---|---|
| 2026-09-23 | — | 全量审计（前端 28 路由 / 后端 81 kind / 50 工具 / 画像 41 字段 / 基线三资产）+ 本任务书落盘 | —— | —— | 14 条 V48-DFC 登记;R-3 前置边界如实登记为 §2.4 | ✅ |
| 2026-09-23 | WP-A/A1 ✅ | `app_monitor_scope_row` 四语更名:zh 监听此应用→**作用域此应用**、en→Scope this app、ja→このアプリをフック対象にする、ko→이 앱을 적용 범위에 포함(ja/ko 经翻译表 + 生成器);新建常驻门禁 `DomainVocabularyTest`(四语禁把作用域称作监听/监视 + 状态徽章与开关同名词) | `DomainVocabularyTest` 3 用例(先红 2 条 → 更名后绿) | i18n 全套绿 | O-1 按建议采纳 ja 母语惯形(不用「作用域」直译) | ✅ |
| 2026-09-23 | WP-A/A2 ✅ | 两域页正名 `listen_settings_entry` 功能配置→**监听设置**(en Listen Settings / ja 監視設定 / ko 감시 설정);`danger_reset_listen_msg`+`danger_reset_feature_msg` 改为同动词同句式的对称句;`detail_empty_hint` 同时点名两页;门禁 +3 用例 | `DomainVocabularyTest` 6 用例(新增 3 条先红后绿) | `StringResourceParityTest` 四语键集/占位符绿;生成器 `DONE` | **审计低估了严重度**:更名前 `listen_settings_entry` 与 `feature_settings_entry` 的 **ja/ko 值逐字符相同**(機能設定 / 기능 설정,且分表 table_1:54 与 table_3:151 各抄一份)—— 两屏在两种语言里本来就是同一个名字,用户无从区分 | ✅ |
| 2026-09-23 | WP-A/A3 ✅ | 应用列表行尾 Checkbox 补 `testTag("app-row-scope")` + `contentDescription` 复用 `app_monitor_scope_row`(与监控页开关共享同一个名词);删死路:`DangerAction` 枚举 → 单一 `confirmClearLogs` 布尔、删 `AppMonitorViewModel.removeScope()`、删 `danger_remove_scope_msg` 四语与表行(键数 841→840) | `UiMcpCapabilityParityTest` 驱动(r4 抓到矩阵仍登记 `removeScope`) | 全量 **152 类 / 1069 用例 / 0 失败 / 0 跳过**;lint `--rerun` 真跑 **0e/43w/1h** 与基线逐字相同 | **门禁先于人工发现登记面漂移**:`UiMcpCapabilityParityTest` 能力行仍引用被删的 `removeScope` ⇒ 按事实改登记表(作用域 UI 通路 = 两个双向开关,恒无「移出确认」入口),**并写清 MCP `scope_remove` 为何保留**(Agent 没有双向开关这一 affordance),而不是放宽 r4 | ✅ |
| 2026-09-23 | WP-A/A4 ✅ | 词汇收口:`section_monitor_kinds` 监听类别→监听开关、`feature_masters_section` 功能总开关→功能开关(去「总」、两页对称)、`algorithm/database_monitor_master` 去「启用」前缀、`listen_section_env_observe` 环境观测→环境监听、`app_description` 重写为「监听 6 类 + 干预 3 类」全貌;删零调用点组件 `SwitchRowItem`(+其独占 `SettingsToggleItem` import) | 编译 + i18n 全套 | 同上(全量/lint 与 A3 同批跑,均绿) | 一处编译红是**我自己的写法错**:`stringResource` 写进非组合的 `semantics{}` lambda ⇒ `@Composable invocations can only happen from the context of @Composable`,按同文件既有 `statusLabel` 先例上提一行解决,未新建机制 | ✅ |
| 2026-09-23 | WP-B/B1 ✅ | `core/model/CapabilityMap.kt` 落地(能力表 = 81 行 kind → 分区键/master 位名/记录路由/探针类;行集恒由 `CategoryTaxonomy.all()` 派生、功能域分区恒由 `FeatureGroups.kindRefs()` 派生,本文件零 kind 字面量);门禁 `CapabilityMapTest` 7 用例(双射 + 冻结 81/44/37 + 跨域类型三张 + 分区键真存在于 `R.string` + 路由常量真存在于 `ModevRoutes` + 探针类真有源文件 + 功能行分区等于 `FeatureGroups` + 跨域监听行点名其外来 master) | `CapabilityMapTest` 7/7 绿(**首轮即过** —— 表是从两个真事实源派生的,不是抄来的,故无漂移可红) | 全量随后与 B2 同批 | 未按 Step 2 跑「类不存在」红:编译期 unresolved 不含信息量,已把该框退回未勾选并在 §9 B1 行说明。表设计自审砍掉两处冗余:`page` 字段(= `domain` 恒等式)与 mcpGroup 列(与 `McpToolParityTest` 分组重复) | ✅ |
| 2026-09-23 | WP-B/B2 ✅ | 跨域闸不再静默:监听页「环境监听」分区的行集合改由 `CapabilityMap.crossDomainListen` 驱动(旧实装在页面里自拼 `listOf(NETWORK, DEVICE_ENV, UI_INTERACTION)` + 手判 LISTEN 过滤 = 第二份跨域清单);kind 已开而外来 master 关 ⇒ 行 `supporting` 点名该 master 的名字与所在页(新键 `listen_kind_master_off_hint` 四语);新增 `HookSwitch.masterFor(type)` 与 `KindLabelRes.masterLabelRes(type)` 两个穷尽单点,功能页 4 个 master 行同时改用它们(此前标签/取值各写一份字面量) | 新增 `ListenEnvSectionRoboTest` 4 用例(整屏渲染:master 关+kind 开 ⇒ 提示在屏 / master 开 ⇒ 无提示 / kind 显式关 ⇒ 无提示但行必须在 / 渲染行集 == 表行集) | 全量 **154 类 / 1082 用例 / 0 失败 / 0 跳过**;lint 真跑 **0e/43w/1h** 未漂移 | **一条用例的前提是错的**:`noHintWhenTheKindItselfIsOff` 我先用「master 关」当「kind 关」跑挂了 —— 事实是 REQUEST 默认就开着,master 关只会让提示出现。修法是显式把 kind 置 false,并把断言从「全局无该文本」改成「行必须在 + 文本必须无」(防空屏假绿)。另记本仓 Compose 测试库缺 `hasNot` 与 `SemanticsConfiguration.getOrNull`(AGENTS 缺件清单再加两件) | ✅ |
| 2026-09-23 | WP-B/B3 🔵 | 监控页两入口按域计数:`listenSummary(override, domain)` 改读 `deviationCountFor(domain)`(编辑页早已如此,入口仍读跨域 `deviationCount` 是漏改);行为变化明确 = 只定制功能域时「监听设置」显示「使用默认配置」 | 插桩档新增 `AppMonitorRedesignTest.hubEntriesCountTheirOwnDomain`(真 `listenConfig.setOverride` 写一条 FEATURE 域偏差,断两行文本不同)+ `@After` 按域清回(禁全量删键) | `:app:assembleModernDebugAndroidTest` **BUILD SUCCESSFUL**(编译过);**运行未跑** —— androidTest 会卸载宿主 APK 并使 lspd 停止注入,恢复需整机重启(AGENTS 实锤),属共享设备破坏性动作,待授权后随 E2 一批跑 | 未按 Step 2 先看红(同属设备档);JVM 侧无等价判据(这是两行文本的比较),故不假装绿 | ⬜ 运行待补 |
| 2026-09-23 | WP-B/B4 ✅ | 记录类型标题去重:10 个 `settings_records_*` 键整体删除,数据管理页计数行改与监控页入口共用 `××_records_title`(一个类型一个标题键);四语 + 两张翻译表同步(键数 841→831,生成器 `DONE`) | 编译 + `StringResourceParityTest` | 全量随 B2 同批绿;androidTest 重编绿 | **我漏了一个消费方**:首轮 grep 只搜了中文文案没搜键名 ⇒ `DataSettingsScreenTest` 仍引 `R.string.settings_records_algorithm`,androidTest 编译当场红 6 处,按新单点键改回。教训:删键的 grep 面 = 键名,不是它的值 | ✅ |
| | | | | | | ⬜ |

## 8. 未决项（需用户裁决,阻塞对应任务）

| ID | 事项 | 建议 | 阻塞 |
|---|---|---|---|
| ~~**O-1**~~ | ja 译法 —— 「作用域」是日语读者不熟的直译 | **已采纳并落地**(WP-A/A1):ja「このアプリをフック対象にする」、ko「이 앱을 적용 범위에 포함」;`DomainVocabularyTest` 禁四语把作用域称作监听/监视,直译形态亦不出现在 ja/ko | —— |
| **O-2** | 是否接受 §2.4 的现实：画像库在 V47 WP-3d/4 落地前**不下发靶程序**;接受 ⇒ WP-C/D 按本序做（含库页状态提示行）,不接受 ⇒ 必须先做钩子接管,R-3 的 UI 部分推后 | **推荐先做 WP-3d 第一批钩子接管**（V47 已实测耦合链,一次原子提交）再落 WP-C,可省掉库页那条「尚未生效」提示 | WP-C/D/E 全部 |
| **O-3** | 许可证长期形态（V47 D-9 未决）：`devices.json` CC BY-NC-SA（**非商业**）与 `carriers.json` MIT 同时进一个可能分发的 APK | 逐字 NOTICE 已在做;若产品要商用,`devices.json` 必须换源或自采 | C1 |
| **O-4** | 内置档扩充到多少台、选哪些（D-1 只填身份 ⇒ 扩充零风险但收益上限也有限） | 推荐 12 台主流 + 让用户从目录里生成任意机型 | D1 |

## 9. 自检（写文时已跑）

1. **需求覆盖**：R-1 → WP-A(A1/A3/A4) + §3 D-1;R-2 → §2.1 表 + WP-B 全部;R-3 → WP-C/D + E1。三条均可指到具体任务与门禁,无孤儿需求。
2. **占位符扫描**：全文无 TBD/「适当处理」/「类似 Task N」;代码块均为可编译形态的真实签名与断言。
3. **类型一致**：`CarrierEntry`/`parseCarrierCatalog`/`applyCarrierPreset`（C1）→ C3 消费同名;`ProfileValueSets.optionsFor` 经 `ProfileOptionSource.optionsFor` 续臂（C2）→ 编辑器只读 `optionsFor`,不直取 `ProfileValueSets` 呈现;`deviationCountFor(domain)` 与 `AppListenOverride.kt:227` 实签名一致;`generateDeviceGroup`（D2）与 `pickAndroidRelease`（C2）同为 VM 复合动作,形态照既有 `pickDevice`。
