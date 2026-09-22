# TASK-V45-ACCEPTANCE：Modev 全量功能验收测试任务书（前端界面 + MCP 双轮一次性全量通过）

| 项 | 值 |
|---|---|
| 文档性质 | 验收测试任务书（Test Master Plan + 执行台账）。**本文档是需求 4/5 的唯一交付物**：全量功能与端点清单 + 测试场景 + 测试环境 + 测试方法 + 结果预期 |
| 被测项目 | Modev（`com.lioisme.modev`）Kotlin/Compose Xposed 模块 |
| 版本 | V45 / 2026-09-19 建档 |
| 关联 | `TASK-V45-AUDIT.md`（缺陷清单，测试 FAIL 归因指向其条目 ID）、`TASK-V45-PLAN.md`（修复任务与纪律 Z1–Z8）、`AGENTS.md`（契约事实源） |
| 通过定义 | §4.4：单轮零 FAIL 且全部 SKIP 命中预登记豁免表 → 该轮「一次性全量通过」；**两轮（前端 / MCP）均需达成** |

---

## 1. 被测项目

### 1.1 交付物与构建变体

| 项 | 值 |
|---|---|
| 模块 | `:app`（主模块）、`:testtarget`（靶程序 `com.lioisme.modev.testtarget`，debuggable，供注入侧驱动） |
| 变体 | `modern`（默认，`META-INF/xposed/java_init.list`，libxposed API 102 → **本机 Vector 通道**）/ `legacy`（Xposed API 82） |
| 验收变体 | **modern 必测全量**；legacy 至少过编译 + 冷启动冒烟（差异登记 INJ-8） |
| ABI | arm64-v8a + x86_64（本机用 x86_64） |
| 验证任务 | `:app:testModernDebugUnitTest` / `:app:lintModernRelease` / `:app:assembleModernDebug` / `:app:connectedModernDebugAndroidTest` / `:testtarget:assembleDebug` |
| 代码规模基线 | main 338 文件 / 44,530 行；单测 112 文件 726 @Test；插桩 31 文件 110 @Test（2026-09-19 实数） |

### 1.2 能力面清点（测试覆盖的枚举事实源，均为工作树实数）

| 面 | 实数 | 计数方法 | 对应用例组 |
|---|---|---|---|
| 导航目的地 | **27** | `ModevRoutes` 27 个 `const val` = `ModevNav` 27 个 `slideComposable` | §5 `UI-*` |
| 屏内+弹层可交互元素 | ≈409（动态行族按族计 1） | UI 清点（`ACCEPTANCE` 执行时按 T3.7 补锚点后复核） | §5 |
| testTag 模式 | 107（静态 88 + 动态 19） | `grep '\.testTag('` | §5 锚点列 |
| MCP 工具 | **46** | `grep -o 'name = "' mcp/tools/*.kt \| wc -l` | §6 `MCP-*` |
| JSON-RPC 协议面 | method 5 类 + 会话头 + 4 类错误码分型 + HTTP 7 状态码 | `McpServer`/`McpProtocol`/`JsonRpc` | §6 `MCP-PROTO-*` |
| wire 消息类型 | `hello`/`config`/`record`/`ack`/`ping`/`pong`（VERSION=4） | `WireProtocol.kt` | §6 `MCP-WIRE-*` |
| 记录类别 | **81**（44 LISTEN / 37 FEATURE）× 10 RecordType | `CategoryTaxonomy` | §7 `INJ-*` |
| 功能组 | 6 组 37 干预类 | `FeatureGroups` | §7 `FEA-*` |
| 靶用例 | **131** ID / 22 域（L 84 / E 44 / C 3） | node 解析 `TestCases.kt` | §7 驱动源 |
| Room | v14，`crypto_records` 110 列 + 6 索引（含 1 唯一），`keyword_library` 5 列 | `schemas/…/14.json` | §7 `NF-DB-*` |
| 语言 | en / zh / ja / ko | `LocaleManager.SUPPORTED_TAGS` | §7 `NF-I18N-*` |

---

## 2. 测试环境

### 2.1 主机侧

| 项 | 值 | 校验方式 |
|---|---|---|
| OS | Windows 11（10.0.26200）x64 | — |
| Shell | Git Bash（POSIX）+ PowerShell 5.1（集成脚本） | `$PSVersionTable` |
| JDK | 17+（本机 21） | `java -version` |
| Android SDK | android-36 + build-tools ≥34 | `sdkmanager --list_installed` 或 `local.properties` |
| adb（两份） | `D:\leidian\LDPlayer14\adb.exe`（脚本用）/ `D:\Android\platform-tools\adb.exe`（PATH，Gradle 用） | 实测两者在位 |
| 独立 adb server | `ANDROID_ADB_SERVER_PORT=5038`（脚本侧，隔离 IDE/其它工具互杀） | `tt-lib.ps1:6-7` |
| 磁盘 | `app/build` 可写；报告目录 `docs/test-reports/`（+ `artifacts/`） | — |

### 2.2 设备侧（雷电模拟器，2026-09-19 实测指纹）

| 项 | 实测值 | 采集命令 |
|---|---|---|
| 序列 | `emulator-5554`（Gradle 面）/ `127.0.0.1:5555`（脚本面，需 `adb connect`） | `adb devices -l` |
| 系统 | Android 14 / API 34 / x86_64 / `product=25102RKBEC` `device=unicorn` | `getprop ro.build.version.sdk` |
| root | magisk（`uid=0 … context=u:r:magisk:s0`） | `adb shell su -c id` |
| 注入框架 | Vector（`org.matrix.vector.manager`）+ lspd 配置库 `/data/adb/lspd/config/modules_config.db` 在位 | `su -c ls` |
| 冻结器 | `settings put global cached_apps_freezer disabled`（否则靶进程被冻 → 记录滞留） | `23-run-smoke.ps1` 前置 |
| 已装关键包 | `com.lioisme.modev`（宿主/模块）、`com.lioisme.modev.test`（androidTest APK）、`com.lioisme.modev.testtarget`（靶）、`mark.via`/`io.github.vvb2060.mahoshojo`/`icu.nullptr.nativetest` 等冒烟对象 | `pm list packages` |
| 端口 | MCP **1510**、wire **1511**（`ConfigKeys.MCP_DEFAULT_PORT`/`WIRE_DEFAULT_PORT`） | `netstat -tln` |
| 数据路径 | 宿主 DB `/data/user/0/com.lioisme.modev/databases/modev.db`；注入 spool `<files>/modev_spool/<proc>/`；磁盘兜底配置 `/data/local/tmp/modev_config_<pkg>.json` | `tt-lib.ps1:9-11` |

### 2.3 环境前置矩阵（每轮开始前必须逐条成立，任一不成立 = 本轮 BLOCKED 而非 PASS）

| ID | 前置 | 建立方式 | 判定 |
|---|---|---|---|
| ENV-01 | 设备在线且 `sys.boot_completed=1` | `adb wait-for-device` | 成立 |
| ENV-02 | **单序列策略**：插桩轮 `adb disconnect 127.0.0.1:5555`（只留 emulator-5554）；脚本轮反之（5038 server + TCP 序列）。**严禁两序列同场**（AGENTS 实锤踩踏坑） | Z4 纪律 | `adb devices` 恰 1 条 |
| ENV-03 | APK 为**本轮构建产物**（工作树↔设备一致） | `10-build-install.ps1`；禁止 `install -r`（AGENTS：覆盖安装后 lspd 不再注入）→ 走卸载重装或整机重启 | `dumpsys package` 安装时间 = 本轮 |
| ENV-04 | 模块已注册且作用域含靶 | `11-vector-scope.ps1`（写 `modules_config.db`）→ **重启 VM**（不重启不生效） | `logcat -s Modev` 见 `Loading module` |
| ENV-05 | 宿主进程存活且 MCP/wire 已绑 | `am start -n com.lioisme.modev/.ui.MainActivity` + `adb forward tcp:1510 tcp:1510` | `server_status` 返回 connectedPackages 非空 |
| ENV-06 | 磁盘兜底配置清零（避免残留偏差污染二态） | `12-config-push.ps1 -Clear` | `/data/local/tmp/modev_config_*.json` 不存在 |
| ENV-07 | 记录库初态已知（种子或清空） | `run-acceptance.ps1` 内 `ResetState` | `records_count` == 期望值 |
| ENV-08 | root/su 可用且 `netstat` 可读 | — | `su -c id` uid=0 |
| ENV-09 | 语言 = 断言基线 locale（默认跟随系统 → 测试内显式设定） | `LocaleManager.setLanguage` | UI 文案资源驱动取到同一 locale |
| ENV-10 | 无其它 adb server 抢占（IDE 守护、scrcpy） | 端口 5038 隔离 | `adb devices` 稳定不掉线 |

### 2.4 残留复位（每轮结束必做，保证下一轮幂等）

`records_clear_all` 之后的重播种、`listen_settings_reset`/`feature_settings_reset`、`keywords_clear`、`spoof_global_set` 回默认、`blacklist_edit` 回默认、`server_set_port`/`server_set_external_access` 回默认（**现脚本未复位端口/外部访问 = QA-8 整改项**）、`applist_prefs_set` 回默认、令牌与 `mcp_token_auth` 回默认、`settings delete global http_proxy`、删除 `/data/local/tmp/modev_config_*.json` 与 `bh1511.sh`、恢复 `cached_apps_freezer`。

---

## 3. 测试方法与工具

### 3.1 测试层级（自下而上，全部为可执行门禁）

| 层 | 工具 | 范围 | 命令 |
|---|---|---|---|
| G-J 纯 JVM 单测 | JUnit4 + kotlinx-coroutines-test | 纯函数（工厂/格式化/谓词/上限不变量/模型往返） | `gradlew :app:testModernDebugUnitTest` |
| G-R Robolectric | `@Config(sdk=[34])` | Room/DAO/仓库、资源与四语一致性、android.icu、markdown 状态 | 同上（同任务内） |
| G-S 源码扫描门禁 | 文本级 FQN 扫描测试 | 包边界、DAO 门禁、对话框约定、Appbar 排他、硬编码串、热路径门控、干预门控、**V45 新增 8 条** | 同上 |
| G-A Compose 插桩 | `createAndroidComposeRule<MainActivity>` / `createComposeRule()` | 27 目的地导航往返、手势（长按/滚动）、sheet/dialog、语义树断言 | `gradlew :app:connectedModernDebugAndroidTest`（ENV-02 单序列） |
| G-D 设备集成 | PowerShell + adb + su + logcat + run-as + on-device sqlite | 注入侧真实产记录、81 kind 二态、37 功能二态、背压、冷启动、spool 回放 | `run-acceptance.ps1`（T0.4 新建；序列见 §8.1） |
| G-M MCP 端到端 | `adb forward` + HTTP JSON-RPC（`tt-mcp-lib.ps1`） | 45 工具 + 协议面 + 认证矩阵 | `24-run-mcp.ps1`（补齐至 45 工具全覆盖） |

### 3.2 锚点与断言规范（写测试的硬规则）

| 规则 | 内容 |
|---|---|
| A1 | 定位优先 `testTag` → 次选 `contentDescription` → 末选文本；**文本断言一律资源驱动**（`getString(R.string.x, args)`，禁硬编码中文/英文，防 zh「2 条」≠"2"、plurals、locale 漂移） |
| A2 | 多节点同 tag（`app-row`/`record-row`/`timeline-header`/`license-row`/`color-swatch`×20/`port-input` 双 sheet 共用）→ 必须 `onAllNodesWithTag(...)[i]` 或 `onFirst()`；危险确认文案恒 `onAllNodesWithText(...).onFirst()`（AGENTS #17） |
| A3 | 勾选态禁断言（M3 只读 `Checkbox`/`RadioButton` 无 `ToggleableState`，AGENTS #8）→ 改**行为级**（点击 → 行现/隐、值变化） |
| A4 | 组头断言必须 `hasTestTag("timeline-header") and hasAnyDescendant(hasText(...))`（同秒组头与行尾时间同串） |
| A5 | sheet/dialog 关闭动作在 **hide 动画后回调** → 断言前等待退场；搜索槽按钮长按有 400ms 宽限期，勿断言「长按即展开」 |
| A6 | 弹层用 `onAllNodes(isPopup())`（`onRoot()` 弹窗期 2 root 报错）；无 `hasToggleableState`/`waitUntil`/`hasText(Regex)` 等 API（AGENTS 工具链坑） |
| A7 | 依赖条件渲染的元素（`listen-reset` 仅在有偏差时、`app-launch` 仅可启动时、`token-*` 仅有令牌时、`wire-bind-error-row` 仅绑定失败时）→ **前置状态显式构造**，不得依赖上一用例残留 |
| A8 | 详情页当前整屏零 testTag（QA-3/T3.7）→ 该屏用例在 T3.7 前标 BLOCKED，不记 PASS |
| A9 | MCP 断言必区分 `-32602`（协议错）与 `result.isError`（业务错）；`isError` **必须是 FAIL 而非黄字警告**（现 `tt-mcp-lib.ps1:78-81` 折成 null 会假 PASS，QA-4 整改项） |
| A10 | 数值断言禁写死随时间变化的量（记录数/存储字节）→ 断「相对变化」或「≥1」；工具数/类别数禁写死 → 从注册表派生（DUAL-2 根因治理） |

### 3.3 数据准备与复位

| 场景 | 准备 |
|---|---|
| 记录列表/详情 | 真实注入产记录（`am start -n …testtarget/.TriggerActivity --es domain <d> --es case <id>` → `WaitChannel` → `WaitSpoolDrain`）或 `dao.clearAll()+seed`（插桩面） |
| 每应用开关 | `listen_settings_reset` → 断默认 → 逐项 set → 断有效值与推送生效（需 `RestartTarget`，磁盘偏差优先于 TCP 推送） |
| 认证矩阵 | 令牌经 **UI** 创建（`settings/mcp-tokens`）→ `mcp_token_auth` 开 → 无头/错头/对头三态 → 撤销末枚（空集=全拒，不停用认证） |
| 破坏性用例 | 全部排在各轮**最后**（`records_clear_all`/`keywords_clear`/`clear-all`），执行后立即重播种 |

---

## 4. 结果预期与判定口径

### 4.1 单用例判定

| 结果 | 定义 |
|---|---|
| **PASS** | 前置成立 + 断言全部命中预期值 + 无异常 + （设备面）无崩溃/ANR（`am_crash`/`ANR` 日志零命中） |
| **FAIL** | 断言不符、抛异常、超时、目标元素不可达、崩溃/ANR、MCP `isError` 或 HTTP 非预期状态码 |
| **SKIP** | 前置条件在本环境**合法不成立**（§4.3 豁免表内），必须写入 SKIP 台账并注明豁免 ID |
| **BLOCKED** | 工具/锚点缺失（如 A8），**不得计入 PASS**，视为本轮未跑完 |

### 4.2 全局预期（每轮统一断言）

| 项 | 预期 |
|---|---|
| JVM+Robolectric+扫描门禁 | 726+ 例全绿，0 失败 0 错 |
| lint | `BUILD SUCCESSFUL`，warning ≤ 基线（基线数字由 T0.6 冻结并同步 AGENTS），**零新增** |
| 插桩 | `OK (N tests)` 且 N == 用例清单数（skipped 单列，不得混入通过数） |
| 设备矩阵 | 每脚本 `FAIL=0`；`SKIP` 全命中豁免表；JSON 台账存在且计数自洽（PASS+FAIL+SKIP == 声明总数） |
| 反垃圾 PASS | 每断言必须「计数已核对」（`CountChecked`），防循环内断言未展开 |
| 宿主健康 | 全程 `adb logcat -b crash` 零命中（宿主 + 5 冒烟 App + 靶）；无 ANR；`23-run-smoke.ps1` L-PERF 预算达标 |

### 4.3 SKIP 预登记豁免表（**表外 SKIP = FAIL**）

| 豁免 ID | 允许 SKIP 的条件 | 影响用例 |
|---|---|---|
| EX-01 | 预干净镜像「无脏可掩」（无 su 文件/无 Magisk 挂载/无 root 包） | `INJ-DEV-ROOT-*`、`FEA-ROOT-*` 部分二态 |
| EX-02 | 无活动 VPN 链路 | `FEA-VPN-01..04` |
| EX-03 | 框架不投递 XposedService（Vector 2.2）→ 作用域需经磁盘/DB 侧路 | `UI-*` 作用域写入确认链、`MCP-SCOPE-add` |
| EX-04 | 系统级确认弹窗不可自动化 | `MCP-SCOPE-add/remove` 的「请求成功」态（改断返回值枚举合法 + 状态机不崩） |
| EX-05 | WebView 非安全上下文无 `crypto.subtle` | `INJ-WV-JS_CRYPTO` |
| EX-06 | 设备未安装某冒烟对象 App | `NF-SMOKE-<pkg>` |
| EX-07 | 需真被 attach（Frida/gdb）才有脏数据 | `FEA-PROC-01` |

---

### 4.4 「一次性全量通过」的定义（需求 4 的判定契约）

1. **一轮 = 全量**：从 §8.1 序列第一步到最后一步**完整执行**，中途不跳过、不挑选、不续跑。
2. 一轮内 **FAIL = 0 且 BLOCKED = 0 且 SKIP ⊆ 豁免表** → 该轮记为「一次性全量通过」。
3. 只要出现 1 个 FAIL：本轮作废 → §9 缺陷登记 → 定向审计（指向 `TASK-V45-AUDIT.md` 条目或新条目）→ 修复 + 补测试 → **整轮从头重跑**（禁止「只重跑失败项后宣布通过」）。
4. 前端轮（§5）与 MCP 轮（§6）**各自独立**满足 2；两轮之间不得互相掩盖（前端轮通过后若又改动代码，前端轮需重跑）。
5. 每轮结果必须写入 §10 台账（含时间、构建号、设备指纹、PASS/FAIL/SKIP 计数、产物路径）。

---

## 5. 前端界面全量测试矩阵（第一轮：ADB 驱动，27 目的地）

驱动方式：`connectedModernDebugAndroidTest`（Compose 插桩）为主，`am start` + `uiautomator` 为导航兜底，`am instrument -w <testPkg>/androidx.test.runner.AndroidJUnitRunner`（单序列定向复跑）。

### 5.1 主界面与导航（`UI-NAV-*`）

| ID | 场景 | 锚点 | 预期结果 |
|---|---|---|---|
| UI-NAV-01 | 冷启动落根页 | `app-row` | 首屏 = `apps`；列表非空或空态文案（`apps_empty_no_apps`，资源驱动） |
| UI-NAV-02 | 根页双击退出 | `exit_press_back_again` | 首次返回出 Snackbar 不退出；2s 窗口内第二次 finish；窗口外重新计时 |
| UI-NAV-03 | 次级页返回 | `cd_back` | `popGuarded` 回上一层；滚动位置保持（VM 级 `LazyListState`） |
| UI-NAV-04 | 相邻 navigate 合并 | `app-row` 连点 | 只导航一次（`NavigationGate` 排队，无二次入栈） |
| UI-NAV-05 | 27 目的地往返 | 各屏首个锚点 | 每屏进入→返回栈恢复→无崩溃（`FourScreensNavTest` 扩展至 27/27） |
| UI-NAV-06 | 非法参数路由 | `records/type/notatype`、`settings/licenses/9999` | 静默 pop，不崩溃、不白屏 |

### 5.2 应用列表 `apps`（`UI-APPS-*`）

| ID | 场景 | 锚点 | 预期 |
|---|---|---|---|
| UI-APPS-01 | 行渲染与状态角标 | `app-row`、`app-row-status`、`app-icon-placeholder` | 图标成功态**无占位底图**；仅 `scoped` 行出角标；行内**无运行按钮**（`app-row-run` 恒不存在） |
| UI-APPS-02 | 搜索折叠→展开→输入→清除 | 输入区 + `clear_search` | 展开后输入生效、行按 label/pkg（ignoreCase）过滤；清除后复原；按钮仅 query 非空时出现 |
| UI-APPS-03 | 筛选三项（置顶/系统/模块） | 菜单文本 `settings_pin_scoped`/`apps_system`/`apps_modules` | 行为级：勾选后行增/隐 + 列表重排；**模块行默认隐藏**（`showXposedModules=false`），开后出现，测后复位 |
| UI-APPS-04 | 作用域勾选 | 行内 Checkbox | 状态转 `requested`→Snackbar `apps_scope_requested`；框架不可用时出 `scopeChangeMessage` 失败文案且**不崩** |
| UI-APPS-05 | 下拉刷新 | `PullToRefreshBox` | spinner → 目录与连接态刷新；无重复请求风暴 |
| UI-APPS-06 | 计数徽标格式 | 行 supporting | `AppCountFormat.compact`（zh ≥1万 / 其余 ≥1K 紧凑），禁自造单位 |
| UI-APPS-07 | 空态 | `apps_empty_no_match` | 搜索无命中出空态语义（`stateDescription`） |

### 5.3 应用监控页 `apps/{pkg}/monitor`（`UI-MON-*`）

| ID | 场景 | 锚点 | 预期 |
|---|---|---|---|
| UI-MON-01 | Hero 作用域开关 | `app-scope-row`、`app-scope-switch` | 单击 → 状态机流转 + Snackbar；`scopeReady=false` 时禁用不可点 |
| UI-MON-02 | 统计卡十段计数 | `app-stats-card` | `total>0` 时双域比例条渲染；数值 = 10 个 `observeRecordCounts` 之和（与 `records_count` 对账，见 §6 交叉项） |
| UI-MON-03 | 10 个记录入口 | `monitor-entry-{behavior,prefs,webview,system,ui-interaction,network,device-env,process-control}` + 算法/库（**当前无 tag → T3.7 后断**） | 每入口 → 对应 `records/{type}/{pkg}`；行值 = 该类型计数 |
| UI-MON-04 | 监听/功能设置入口 | `monitor-entry-listen-settings`、`monitor-entry-feature-settings` | 副标题：零偏差 = `listen_summary_default`；有偏差 = plurals `listen_settings_custom_n` |
| UI-MON-05 | Appbar 清空记录 | `app-clear-records` → 居中确认（`danger_clear_logs_msg`） | 确认 → 该包记录归零 + Snackbar `apps_records_cleared`；取消 → 零变化 |
| UI-MON-06 | Appbar 启动（恒最右） | `app-launch` | 仅可启动时渲染；点击切前台；动作序 = `[app-clear-records, app-launch]` |
| UI-MON-07 | 含搜索条页 actions 恒空红线 | 源码扫描 | 监控页无搜索条 → 允许 actions；10 记录页禁（`AppbarSearchExclusivityTest`） |

### 5.4 记录列表 11 目的地（`records/{type}/{pkg}` ×10 + `records/type/{type}`）（`UI-REC-*`）

| ID | 场景 | 锚点 | 预期 |
|---|---|---|---|
| UI-REC-01 | 行渲染「一行一参数」 | `record-row` | 类别/操作/库名/路径/状态各独立成行；**行内零「 · 」拼串**（`RecordRowLayoutTest.noMultiParameterLines` 结构哨兵）；标签走资源 |
| UI-REC-02 | 时间线组头 | `timeline-header` | 同秒一组；跨日组头带日期前缀（`TimelineGroup.formatDay` 母语形态）；分页 prepend 边界不重复出头 |
| UI-REC-03 | 计数副标 = 真 SQL COUNT | 副标（无 tag，按文本+位置） | `records_count_subtitle` 数值 == MCP `records_count`（同 filter）== 匹配总数；**滚动/翻页不改变该值**（D-V44-4 回归） |
| UI-REC-04 | 关键字搜索 | 搜索条 | 命中集 == `records_query(query=…)` 命中集（双端同源，ST-1 修复后必测） |
| UI-REC-05 | 类别筛选（含「全部」） | 菜单项（`record_types_all` + 类别；**T3.7 补 `kind-filter-*`**） | 选项来自 `CategoryTaxonomy.forType`（非裸枚举）；选后行数变化且与 `records_category_counts` 一致 |
| UI-REC-06 | 分页翻页与滚动保持 | `record-row` | 种子 >2 页（≥120 条）；append 正常（`PAGING_WINDOW_CAP` 不死锁于第一页）；返回列表位置保持 |
| UI-REC-07 | 行单击进详情 | `record-row` → `toc-*` | 详情标题恒 `record_title`；返回后位置保持 |
| UI-REC-08 | 行长按复制 | 同上 | 剪贴板 = 复制摘要（资源驱动）；Snackbar `record_copied_toast`；振动（本机剪贴板可被镜像吞 → `clipboardFunctional()` 降级，真机全断） |
| UI-REC-09 | 清空本类（搜索条动作槽） | `records-clear-type` → `records-clear-confirm` | 单应用态删「本应用该类型」；全局态删「该类型全部应用」；取消零变化；删后计数 0 |
| UI-REC-10 | 长按动作槽不展开搜索 | `records-clear-type` 长按 | 400ms 宽限内展开意图被抑制（`SearchInputComponentsTest` 三槽锁定） |
| UI-REC-11 | 12/24 小时制与 locale | 行尾时间 | 跟随系统时制；`RecordRowLayoutTest` 双制断言 |
| UI-REC-12 | 长值中缀省略 | 路径/命令/URL/SQL 行 | `MiddleEllipsis.ellipsize` 生效（非尾省略） |

### 5.5 记录详情 `record/{id}`（`UI-DET-*`，T3.7 锚点已落地：`detail-prev/next/more/export/retry` + `detail-code-tab-*`）

| ID | 场景 | 锚点 | 预期 |
|---|---|---|---|
| UI-DET-01 | 身份区 + 元数据表 | `toc-meta` | markdown 单管线渲染；元数据表为 GFM 紧凑 `|Cell|` |
| UI-DET-02 | TOC 点击滚动 | `toc-{key}` | 每 key 滚至对应 `section-{key}`（**必须显式 `state = listState`**，回归 `AnchorScrollRoboTest`）；无索引 key 静默 no-op（DEVICE_ENV params 缺陷 → 登记后修） |
| UI-DET-03 | 代码组标签页 | `detail-code-tabs` + `section_repr_*` | 连续 text/base64/hex 围栏聚合为标签页；选中态按 `runFirstOrdinal` 记忆；单表示退化单代码卡 |
| UI-DET-04 | 显示上限与截断标记 | 围栏 | 128Ki 所见上限；`detail_truncated_marker` **恒在所有围栏之后**（不得打断代码组） |
| UI-DET-05 | 复制（4Mi 护栏） | 复制按钮 | ≤4Mi 成功 `record_copy_done`；>4Mi `record_copy_too_large`；内容为全量（非所见） |
| UI-DET-06 | 全屏查看器 | `fullscreen-list` | `ChunkedText` O(1) 分块；关闭回原滚动位 |
| UI-DET-07 | 导出 JSON（32Mi 护栏） | 导出项 | SAF 创建 `modev-record-<id>.json`；成功 `export_ok`；超限 `export_too_large`（带格式参） |
| UI-DET-08 | 上一条/下一条 | 上/下条按钮 | 邻接正确（与 `record_neighbor` 同源）；首/末条禁用态 |
| UI-DET-09 | 高亮降级 | json/bash 围栏 | Highlights 引擎着色；>32Ki 降级纯 mono 不崩 |
| UI-DET-10 | 记录不存在 | 失败态 | `record_not_found` + 重试可恢复 |

### 5.6 每应用监听设置 `apps/{pkg}/listen-settings`（57 行）（`UI-LST-*`）

| ID | 场景 | 锚点 | 预期 |
|---|---|---|---|
| UI-LST-01 | 6 个 master 行 | `listen-row-master-{crypto,db,behavior,prefs,webview,system}` | 默认全 true；单击即写即推（`pushToPackage` 定向）；再点回 false |
| UI-LST-02 | 12+5+7+5+5+3 类别行 | `listen-row-{type}-{KIND}` | 默认值 == `CategoryTaxonomy.defaultEnabled`（逐项表驱动断言，含 crypto 前 4 开、db query/DML/EXEC 开、behavior write/delete/rename/shell 开、prefs get 关、webview jsCrypto 关、system logcat 关） |
| UI-LST-03 | 环境观测分区 6 行 | `listen-row-network-REQUEST`、`listen-row-device_env-DETECT_*`、`listen-row-ui-DIALOG_SHOW/ACTIVITY_START/ACTIVITY_RESUME` | 来自 taxonomy 驱动；Prefs `OPEN`/`CLEAR` 与 SQL kind 名碰撞分支**前置**（#22） |
| UI-LST-04 | 记录选项（栈/字节/忽略栈前缀） | `listen-row-log-stack`、`listen-row-max-bytes`、`listen-row-ignore-stack` | 默认 `logStack=false`、`maxBytes=1048576`；≤0 显 ∞；编辑走底部 sheet（禁居中 Dialog） |
| UI-LST-05 | 结果闸门双行 | `listen-row-result-rows`、`listen-row-result-chars` | 默认 100 / 262144；sheet 输入数字，非数字拒 |
| UI-LST-06 | 忽略路径前缀 | `listen-row-ignore-path` | 值 = 条数；多行 sheet 逐行 trim/去空/去重 |
| UI-LST-07 | 恢复默认行条件渲染 | `listen-reset` | 监听域零偏差时**不存在**（**V45 T3.5/DUAL-12**：判定改 `deviationCountFor(LISTEN)>0`，只有功能域定制时本页不再虚现该行）；任一监听偏差后出现且副标 plurals 计数按本域；确认 → 监听域键清、功能域保留 → 行消失 + 有效值回 DEFAULT |
| UI-LST-08 | 二态与 wire 零感知 | — | 改后 `listen_settings_get` 的 `effective` 与 UI 一致；wire `VERSION` 恒 4（协议测试） |

### 5.7 每应用功能设置 `apps/{pkg}/feature-settings`（46 行）（`UI-FEA-*`）

| ID | 场景 | 锚点 | 预期 |
|---|---|---|---|
| UI-FEA-01 | 4 个功能区 master | `feature-row-master-{ui,network,device-env,process}` | 默认 true；写后功能域生效 |
| UI-FEA-02 | 6 功能卡 37 类行 | `feature-group-header-{GROUP}`、`feature-row-{KIND}` | 组与类来自 `FeatureGroups`（完备性测试锁 FEATURE↔组）；默认仅 `DIALOG_KEYWORD` 开 |
| UI-FEA-03 | 关键词跟随全局 | `feature-keyword-follow-global` | 关 → 物化当前启用集为显式子集；`true` → 写 null（与 MCP 同语义，Q2 回归） |
| UI-FEA-04 | 空库引导 | `feature-keyword-goto-library` | 库空时出引导行 → 跳 `settings/keywords` |
| UI-FEA-05 | 关键词 chip 切换 | `feature-keyword-chip-{id}` | 仅 `enabled` 条目渲染；单击改启停并推注 |
| UI-FEA-06 | 画像选择 | `feature-profile-picker` → `feature-profile-option-{follow-global,auto,pixel7pro,xiaomi13}` | 点选即写 `spoofProfileId`；副标随档位 |
| UI-FEA-07 | 恢复默认 | `feature-reset` | **V45 T3.5/DUAL-12 已修**：两页共用同一存储键但按域清除 —— 功能页恢复只清功能域偏差（`spoofProfileId`/`extra*`/`keywordIds`/`keywordFollowGlobal` + FEATURE 类别 + 四 FEATURE master），监听页定制**必须原样保留**；行可见性与副标计数改 `deviationCountFor(FEATURE)`（监听域有偏差时功能页不再虚现「恢复默认」） |

### 5.8 设置族（`UI-SET-*` / `UI-DATA-*` / `UI-SVC-*` / `UI-DIAG-*` / `UI-TOK-*` / `UI-ASSET-*` / `UI-ABOUT-*`）

| ID | 场景 | 锚点 | 预期 |
|---|---|---|---|
| UI-SET-01 | 外观三选 | sheet 行（无 tag → 文本资源） | System/Light/Dark 即时生效且不重启；持久化 |
| UI-SET-02 | 主题色板 | `theme-color-sheet`、`color-swatch`×20、`system_wallpaper` | 选择即写不关闭；壁纸行出动态色；`SettingsThemeContrastTest` 全 seed 对比度红线（≥1.046/1.078、4.5/4.5） |
| UI-SET-03 | 语言热切换（四语） | sheet 5 行 | 选 ja/ko/zh/en → **不重建**全站文案切换；locale 敏感值无残留；`locales_config.xml` 与枚举同表 |
| UI-DATA-01 | 10 类型计数行 | `data-type-{crypto,db,behavior,prefs,webview,network,device_env,ui,system,process}` | 值 == `observeTypeCounts` == MCP `records_type_counts`；点击进全局类型列表 |
| UI-DATA-02 | 存储/总数只读行 | 文本 | `StorageFormat`/`settings_total_records_value`；先预建 DB 文件防首查竞态 |
| UI-DATA-03 | 清空全部（唯一危险行） | `clear-all` → 居中确认 | 确认 → 计数/占用归零；无回收站；**「按应用浏览」区不得复活** |
| UI-SVC-01 | 统一外部访问开关 | `services-external-access` | 副标动态 `0.0.0.0`/`127.0.0.1`；开后 wire+MCP 双服务 restart（MCP 恒异步），已连接会话由注入侧重连恢复 |
| UI-SVC-02 | wire/MCP 端口 sheet 校验链 | `port-setting-sheet`、`mcp-port-setting-sheet`、`port-input`、`port-confirm`、`port-checking` | 非数字/超范围 → `settings_port_invalid`；<1024 → `settings_port_privileged`；与对端同值 → `settings_port_conflict`；占用 → `settings_port_in_use`；**失败硬阻止保存**；重存原值 no-op 不探测 |
| UI-SVC-03 | 绑定错误行 | `wire-bind-error-row`、MCP 错误行 | 制造占用 → 错误行出现且文案含端口；成功绑定后清空 |
| UI-SVC-04 | MCP 启用/令牌认证 | `services-mcp-enabled`、`services-mcp-token-auth` | 关 → 1510 不再可连；认证开且令牌空 → 自动派生一枚（防锁死） |
| UI-SVC-05 | 令牌入口计数 | `services-tokens-entry` | 副标 plurals `mcp_tokens_count` == 实数 |
| UI-DIAG-01 | 框架/作用域/在线目标 | 行（无 tag） | 值随 `scopeRepository.state` 与 `connectedPackagesFlow` 实况；Ready 时可点 → `diagnostics-info-sheet` |
| UI-DIAG-02 | 统计 7 行 | `diagnostics-stats`、`diagnostics-stat-{received,dropped,write-failures,trimmed,rejected-hellos,dropped-records,rejected-records}` | 数值 = `PipelineStats` 同源；`AppCountFormat.grouped` 千分位随 locale |
| UI-DIAG-03 | 复制诊断快照 | 复制行 | 内容含 MCP 两行与 `IP:端口` 完整形态（唯一保留处）；**令牌值恒不进**；剪贴板降级策略（A 组规则） |
| UI-TOK-01 | 新建令牌 | `tokens-add` → `token-add-sheet` | 提交 → 行出现 + Snackbar `token_created`；label 空回落 `token_unnamed` |
| UI-TOK-02 | 显隐/复制/撤销 | `token-row-{id}`、`token-copy-{id}`、`token-revoke-{id}`→`token-revoke-confirm` | 掩码 `前4••••••后4`；卡片点击整卡显隐；撤销末枚 → 状态行 `tokens_status_deny_all`（error 色）且**不停用认证** |
| UI-TOK-03 | 认证开关与状态行 | `tokens-auth-card`、`token-auth-toggle`、`tokens-auth-status` | 三态文案 `tokens_status_off`/`_active(%d)`/`_deny_all` 正确 |
| UI-ASSET-01 | 关键词库 CRUD + 启停 | `keywords-add`、`keywords-row-{id}`、`keywords-delete-{id}`、`keywords-input`、`keywords-mode-{CONTAINS,REGEX,EQUALS}`、`keywords-confirm` | 空/重复提交被拒（trim 非空）；模式三选生效；启停即时推 `pushToAllConnected` |
| UI-ASSET-02 | 画像覆写 | `profile-override-add`、`profile-override-key/value`、`profile-override-delete-{key}` | key 重复 → `isError`（**当前无错误文案 = 缺陷 D-V45 附注**）；删除后回落画像值 |
| UI-ASSET-03 | 黑名单四区 | `blacklist-add-{ROOT_PACKAGES,XPOSED_PREFIXES,FRIDA_PORTS,MASK_KEYWORDS}`、`blacklist-chip-{KIND}-{value}`、`blacklist-input`、`blacklist-confirm` | 内置基线**只增不删**；FRIDA_PORTS 非 1..65535 → `settings_port_invalid`；chip 删除即时生效 |
| UI-ABOUT-01 | 关于/许可/详情 | `license-row`、`settings/licenses/{index}` | 版本行 = `version(app_name, VERSION_NAME)`；许可全文可选中；越界 index → pop；空 `aboutlibraries.json` → 空态 |

### 5.9 全局 UI 红线（`UI-RED-*`，源码扫描 + 插桩双锁）

`UI-RED-01` 对话框双门禁（`AlertDialog(`/`ModalBottomSheet(` 各唯一构造点、确认无 icon、危险态 error 色）；`UI-RED-02` 输入弹层恒 sheet + 单 `imePadding`（禁叠 `navigationBarsPadding`，双内边距 = 黑带根因）；`UI-RED-03` 含搜索条页 actions 恒空；`UI-RED-04` `ui` 包零中文字面量；`UI-RED-05` 四语资源齐备 + plurals 逐 locale；`UI-RED-06` 弃用 API 零新增 + lint ≤ 基线；`UI-RED-07` **`ui/` 零 `data.*` 引用**（V45 新增，Phase 2 出口断言）。

---

## 6. MCP 端点全量测试矩阵（第二轮：45 工具 + 协议面 + 认证 + wire）

驱动：`adb -s <序列> forward tcp:1510 tcp:1510` → HTTP `POST /mcp`（`Mcp-Session-Id` 头，认证启用后 `Authorization: Bearer`）。**`result.isError` 计 FAIL**（A9）。

### 6.1 协议面（`MCP-PROTO-*`）

| ID | 场景 | 请求 | 预期 |
|---|---|---|---|
| MCP-PROTO-01 | initialize | `protocolVersion:"2025-06-18"` | 200 + `serverInfo.name=="modev"` + 版本回显 + **响应头 `Mcp-Session-Id`** |
| MCP-PROTO-02 | 版本协商 | `"2024-11-05"` / `"2025-03-26"` / 未知串 | 分别回显 / 回落到 `2025-06-18` |
| MCP-PROTO-03 | notifications/initialized | 通知（无 id） | **202 空体** |
| MCP-PROTO-04 | ping | `method:"ping"` | 200 + `result:{}` |
| MCP-PROTO-05 | tools/list | — | 200 + 工具数 == registry 派生实数（禁硬编码）；每项含 `name/description/inputSchema` |
| MCP-PROTO-06 | tools/call 成功 | `records_type_counts` | 200 + `content[0].text` 为结果 JSON 串 + `structuredContent` 同对象 |
| MCP-PROTO-07 | 未知方法（有 id） | `method:"foo"` | 200 + `-32601` |
| MCP-PROTO-08 | 未知方法（通知） | 无 id | 202 |
| MCP-PROTO-09 | 批量请求 | 顶层数组 | 200 + `-32600`（batching 不支持） |
| MCP-PROTO-10 | 非 `/mcp` 路径 | `POST /nope` | 404 + `-32600 Not found` |
| MCP-PROTO-11 | 缺会话头 | 无 `Mcp-Session-Id` | 404 + `-32600 Session not found; re-initialize`（带原 id） |
| MCP-PROTO-12 | 非法 JSON | `{` | 200/400 + `-32700` |
| MCP-PROTO-13 | 缺 `method` | `{"jsonrpc":"2.0","id":1}` | `-32600` |
| MCP-PROTO-14 | 体上限 | >1MiB | 413（**现按 UTF-16 字符数判 = 缺陷 G17，多字节须实测**） |
| MCP-PROTO-15 | DELETE 会话 | `DELETE /mcp` + 头 | 200；会话作废后再用旧头 → 404 |
| MCP-PROTO-16 | GET(SSE) | `GET /mcp` | 405 + `SSE streaming not supported` |
| MCP-PROTO-17 | OPTIONS | `OPTIONS /mcp` | 405（**无 CORS = 登记项**，浏览器型 Agent 不可用需明示） |
| MCP-PROTO-18 | 工具超时 | 人为慢工具 >30s | 200 + `isError` "timed out"（若无慢工具则标 BLOCKED 并补可注入延迟） |
| MCP-PROTO-19 | restart 后会话失效 | 改端口/外部访问触发 restart 后调用 | 旧会话 404，新 initialize 成功 |
| MCP-PROTO-20 | capabilities | `initialize` 结果 | 仅 `tools`；无 resources/prompts |

### 6.2 记录组 14 工具（`MCP-REC-*`）

| ID | 工具 | 关键参数 | 预期 |
|---|---|---|---|
| MCP-REC-01 | `records_query` | `recordType`+`pageSize` | 200；`items/total/page/pageSize` 四元齐全；`total` == `records_count` == **UI 副标**（ST-1 交叉对账） |
| MCP-REC-02 | `records_query` | `sort:"asc"` vs 默认 | 首元素时间戳单调性正确（与 `observe` 同 filter 一致性） |
| MCP-REC-03 | `records_query` | `query`（路径/SQL/别名/命令 四类关键字） | 命中集 == UI 搜索命中集 |
| MCP-REC-04 | `records_query` | `pageSize:0` / `201` / 负 | clamp 1 / 200；越界不报错（登记 schema 语义） |
| MCP-REC-05 | `records_query` | 未知 `recordType` | 业务错 `isError`（**非** `-32602`，按 T3.2 统一裁决后断） |
| MCP-REC-06 | `records_count` | 多过滤组合 | 与 UI 计数、`records_query.total` 三方一致 |
| MCP-REC-07 | `records_type_counts` | — | 10 键齐全（含 0 值类型）；与 `data-type-*` 行值逐项一致 |
| MCP-REC-08 | `records_category_counts` | `recordType` 必填 | 缺 → `-32602`；非法 → `isError`；键集 ⊆ taxonomy 该类型 |
| MCP-REC-09 | `record_get` | 有效 id | 全字段；>1MiB 出现 `truncated:true`；与 UI 详情内容同源对账 |
| MCP-REC-10 | `record_get` | 不存在 id | `isError` "Record N not found" |
| MCP-REC-11 | `record_neighbor` | `newer:true/false` | 首/末条返回 `null` 且键存在（serializeNulls） |
| MCP-REC-12 | `record_create` | 合法 kind/operation | 返回 id，立即可 `record_get` |
| MCP-REC-13 | `record_create` | 非法 kind/operation/recordType、混入 `page` 等非 draft 键 | 全被拒（V44 D7 写侧强校验） |
| MCP-REC-14 | `record_update` | 合法/不存在 | 成功回显 + 复读一致；不存在 → `isError` |
| MCP-REC-15 | `record_delete` | 有效/不存在 | true / `isError` |
| MCP-REC-16 | `records_delete_by_filter` | 零约束 | 拒（无约束不整表删） |
| MCP-REC-17 | `records_delete_by_filter` | 带 kind+pkg | `deleted` 数 == 删前 `records_count` 同 filter |
| MCP-REC-18 | `records_clear_package` | 缺 confirm / false / true | `-32602` / `isError` / 成功且计数 0 |
| MCP-REC-19 | `records_clear_type` | 同上三态 | 同上，仅该类型清零 |
| MCP-REC-20 | `records_clear_all` | 缺 confirm / true | `-32602` / 全零（排在组末，之后重播种） |
| MCP-REC-21 | `storage_usage` | — | 字节数 >0 且 == UI 存储行；清库后回落（ST-8 收缩断言） |
| MCP-REC-22 | 全部 | 结构 | `fields` 结构在 10 类下一致（DUAL-7 修复后）；null 键策略一致（DUAL-6） |

### 6.3 应用组 6 工具（`MCP-APP-*`）

| ID | 工具 | 场景 | 预期 |
|---|---|---|---|
| MCP-APP-01 | `apps_list` | 无参 | 默认 == **UI 持久偏好**（`AppListSettings`）+ 排序 == `AppSort`（与 `app-row` 顺序一致）；字段含 `system/xposedModule/scoped/recordCount` |
| MCP-APP-02 | `apps_list` | `query` | 命中集 == UI 搜索 |
| MCP-APP-03 | `apps_list` | `showSystem/showModules` 显式 | 覆盖持久值；测后复位（`applist_prefs_set` teardown） |
| MCP-APP-04 | `apps_refresh` | — | 返回条目数 >0 且与列表一致 |
| MCP-APP-05 | `app_monitor` | 靶包 | `counts` 10 键 == `observeRecordCounts`；`running`/`scoped` 与 UI 一致 |
| MCP-APP-06 | `app_monitor` | 不存在包 | 行为登记（返回默认/`isError`，不得崩） |
| MCP-APP-07 | `app_launch` | 靶包 / 无 launcher 包 | `launched:true` / `false`，异常吞而不报（DUAL 语义登记） |
| MCP-APP-08 | ~~`app_clear_records`~~ | — | **V45 T3.5/DUAL-11 已删除**：与 `records_clear_package` 同上游（`RecordService.clearPackage`）两次广告 → 工具面 46→45，按包清理唯一入口留在记录组三元组；`UiMcpCapabilityParityTest.r1` 机器锁定「同一可变上游只允许一个工具」 |
| MCP-APP-09 | `applist_prefs_get/set` | 三偏好读写 | 回读一致；**V45 T3.4 键名已归一**：参数与返回键 = 契约属性名 `pinScoped`/`showSystem`/`showModules`（旧 `pinScopedOnTop`/`showXposedModules` 是同一状态的第三套叫法，已随 `AppListPreferences.View` 一并改名；脚本 `M-APP-01c` 同步） |

### 6.4 监听配置组 4（`MCP-LST-*`）

| ID | 工具 | 场景 | 预期 |
|---|---|---|---|
| MCP-LST-01 | `listen_settings_get` | 无偏差包 | `effective` == `HookSwitch.DEFAULT` ⊕ taxonomy；`deviationCount:0` |
| MCP-LST-02 | `listen_settings_set` | `master:"cryptoListen"`, `masterValue:true`（布尔） | **DUAL-1 修复后成功**；`override` 落偏差；`deviationCount` +1 |
| MCP-LST-03 | 同上 | `masterValue:null` | 回默认（键删/归一） |
| MCP-LST-04 | 同上 | 非法 master / 非法 kindKey / 裸 kind id | 显式 `isError`（DUAL-10） |
| MCP-LST-05 | 同上 | `kindEntries` 限定键 + 微调项（logStack/maxBytes/ignoreStack/rows/chars/fileContentCapture/ignorePathPrefixes） | 逐项生效并回读一致；空数组→null |
| MCP-LST-06 | `listen_settings_reset` | 有偏差后 | `deviationCount:0`；UI 同页 `listen-reset` 消失（双端一致）；**V45 T3.5/DUAL-12**：功能域偏差（画像/黑名单/关键词子集）必须原样保留，`deviationCount` 只剩功能域计数 |
| MCP-LST-07 | `taxonomy_listen` | — | 44 条；含 `kindKey/domain/defaultEnabled/noiseTier`；与 UI 行集逐项一致 |
| MCP-LST-08 | 推送生效 | set 后 `RestartTarget` | 注入侧行为变化（该 kind 记录现/隐） |

### 6.5 功能配置组 4（`MCP-FEA-*`）

| ID | 场景 | 预期 |
|---|---|---|
| MCP-FEA-01 | `feature_settings_get` | `effective.feature` 含词表/画像/黑名单合并结果 |
| MCP-FEA-02 | `feature_settings_set` master 非 FEATURE 域 | `isError`（域闸） |
| MCP-FEA-03 | `spoofProfileId:"pixel7pro"` + `spoofOverrides` + `keywordFollowGlobal:true` | 画像生效；**`true` 归一写 null**（与 UI 同语义） |
| MCP-FEA-04 | `feature_settings_reset` | 双端一致（含 `feature-reset` 行消失） |
| MCP-FEA-05 | `taxonomy_feature` | 6 组 37 类；`kindKey` 可回 `feature_settings_set`（自洽性） |

### 6.6 资产组 10（`MCP-AST-*`）

| ID | 场景 | 预期 |
|---|---|---|
| MCP-AST-01 | `keywords_list` | 空库 → `entries:[]` |
| MCP-AST-02 | `keywords_add` 默认模式 | `matchMode` 缺省 = `CONTAINS`；返回 `ok:true` |
| MCP-AST-03 | `keywords_add` 重复/空文本 | `ok:false`（非异常） |
| MCP-AST-04 | `keywords_add` 非法 matchMode | `isError` |
| MCP-AST-05 | `keywords_update` 有效/无效 id | 一致回读 / `isError` |
| MCP-AST-06 | `keyword_set_enabled` | 启停生效；UI 行内 Switch 同步（双端） |
| MCP-AST-07 | `keywords_clear` 缺 confirm/true | `-32602` / 成功且列表空 |
| MCP-AST-08 | `spoof_profiles_list` | 3 画像全字段 + `currentProfileId` + `currentOverrides` |
| MCP-AST-09 | `spoof_global_set` 未知 profileId / 非字符串覆写值 | `isError` / 静默丢弃（登记） |
| MCP-AST-10 | `blacklist_list`/`blacklist_edit` | 仅列追加项（内置基线不外露）；`action` add/remove；FRIDA_PORTS 非数值 → `isError`；测后复位 |
| MCP-AST-11 | 词表变更后 | `pushToAllConnected` → 注入侧拦截行为变化（与 §7 FEA-DLG 交叉） |

### 6.7 服务/诊断/作用域组（`MCP-SRV-*` / `MCP-DIAG-*` / `MCP-SCOPE-*`）

| ID | 场景 | 预期 |
|---|---|---|
| MCP-SRV-01 | `server_status` | wire+mcp+framework 三段齐全；**令牌值零可见**；`connectedPackages` == 诊断页在线目标 |
| MCP-SRV-02 | `server_set_external_access(true)` | 双服务改绑 0.0.0.0；**当前 MCP 会话立即失效** → 断言后立即回滚并重新 initialize（自伤型用例，必须收尾） |
| MCP-SRV-03 | `server_set_port` 合法端口 | 生效 + restart；注入侧重连恢复（测后回 1511 + `RestartTarget`，否则后续记录用例全挂） |
| MCP-SRV-04 | `server_set_port` 80 / 与 MCP 同值 / 越界 | **DUAL-3 修复后：80 与冲突必须被拒**（不再静默 clamp）；越界按裁决形态一致 |
| MCP-DIAG-01 | `diagnostics` | `stats` 7 字段 + `report` 行数组；数值与 `diagnostics-stat-*` 行同源；标签英文锁定声明成立 |
| MCP-SCOPE-01 | `scope_state` | `connecting/ready/unavailable` 之一；键集稳定性（DUAL-6 修复后恒在） |
| MCP-SCOPE-02 | `scope_add`/`scope_remove` | 返回值 ∈ 7 值枚举；已作用域 → `already_scoped` 且不调上游；**系统弹窗态走 EX-04 豁免** |

### 6.8 认证矩阵（`MCP-AUTH-*`）

| ID | 前置 | 请求 | 预期 |
|---|---|---|---|
| MCP-AUTH-01 | 认证关（默认） | 无 `Authorization` | 200（含 0.0.0.0 情形——外部访问与认证解耦，AGENTS #26） |
| MCP-AUTH-02 | 认证开 + 经 UI 建令牌 | 无头 / 错头 / `Bearer ` 空头 | 三态恒 **401 + `-32600 Unauthorized`** |
| MCP-AUTH-03 | 同上 | 正确 Bearer | 200；多令牌逐枚可用 |
| MCP-AUTH-04 | 撤销一枚 | 用被撤销令牌 | 401；**不重启不踢其余会话**（`mcpTokenSetUpdater` 原地生效） |
| MCP-AUTH-05 | 撤销末枚 | 任意请求 | 401（空集=全拒），认证开关**不停用** |
| MCP-AUTH-06 | 认证开 + 令牌空 | initialize | 200（自动派生一枚后自洽） |
| MCP-AUTH-07 | 回环 vs 外部 | 0.0.0.0 + 认证关 | 显式允许（用户选择），文档与 UI 状态行承载风险语义 |
| MCP-AUTH-08 | 令牌值泄露面 | `tools/list`+`server_status`+`diagnostics` 全文 | **零出现令牌值**（PII 红线） |

### 6.9 wire 协议面（`MCP-WIRE-*`，经 su/logcat/DB 观测）

| ID | 场景 | 预期 |
|---|---|---|
| MCP-WIRE-01 | 握手 magic/version 错 | 断开 + `rejectedHellos`++ |
| MCP-WIRE-02 | 握手无 token 且 `requireToken!=null` | 放行 + `trustedNoToken`++（legacy/Vector 通道） |
| MCP-WIRE-03 | token 非空不匹配 | 断开 + `rejectedHellos`++ |
| MCP-WIRE-04 | 记录包名 ≠ 会话包名 | `rejectedRecords`++ |
| MCP-WIRE-05 | **未注册会话（hello 缺 packageName）后直发记录** | **ST/安全实锤点**：现状接受任意包名（`sessionPackage != null` 前置）→ 登记为缺陷并断言修复后拒绝 |
| MCP-WIRE-06 | 4M 字符超长行 | 现状判损坏丢弃（ST-3）→ 修复后恒不产生 |
| MCP-WIRE-07 | 空闲 20s ping/pong | 连接不被半开；spool 持续排空 |
| MCP-WIRE-08 | 断连→重连 | 2s→30s 退避；未 ACK 行完整回放（`(pkg,proc,seq)` 唯一索引幂等） |
| MCP-WIRE-09 | `ack` 单游标语义 | 注入侧裁掉 `seq ≤ ackedSeq`；跨重启 seq 不撞号（ST-4） |
| MCP-WIRE-10 | 端口 restart epoch 代 | 旧 accept 线程退役，无双绑 |

---

## 7. 注入侧与非功能矩阵（第三轮，随 §8 序列执行）

### 7.1 记录产生性全量核对（`INJ-*`）

驱动方式：`am start -n com.lioisme.modev.testtarget/.TriggerActivity --es domain <域> --es case <ID>` → `logcat` 读 `TT_RESULT` → `WaitSpoolDrain` → 经 MCP `records_query(recordType,kind)` 与 UI 列表双向核对。**131 靶用例逐条有期望**（期望表 = 本文档执行时按 `TestCases.kt` 域表展开，域清单如下）：

| RecordType | kind（默认开 ✓/关 ✗） | 触发 API 摘要 | 靶域 | 期望 |
|---|---|---|---|---|
| CRYPTO | CIPHER✓ MESSAGE_DIGEST✓ MAC✓ SIGNATURE✓ KEY_PARAM✗ KEY_GENERATION✗ KEY_FACTORY✗ KEY_STORE✗ KEY_AGREEMENT✗ RANDOM✗ BASE64✗ **WEB_CRYPTO✗（无发射器=INJ-4）** | `Cipher`/`MessageDigest`/`Mac`/`Signature` 全链、23 个 Spec 构造器、`KeyStore`、`SecureRandom`、`Base64` | `crypto`(14 案) | 逐 kind：开 → ≥1 条且字段（algorithm/direction/key/iv/input/output 三态）齐；关 → 0 条且**零副作用** |
| DATABASE | OPEN✗ QUERY✓ DML✓ EXEC✓ TRANSACTION✗ | `SQLiteDatabase`/`SQLiteOpenHelper`/`SQLiteStatement`/游标迭代 | `db`(11) | 双闸门生效（100 行 / 256Ki 截断标记）；未迭代游标 rowCount=null 且无结果记录（被动红线 #11） |
| BEHAVIOR | FILE_READ✗ FILE_WRITE✓ DELETE✓ RENAME✓ LIST✗ ASSET_READ✗ SHELL_EXEC✓ | `File*`/`Files.*`/`AssetManager`/`Runtime.exec`/`ProcessBuilder` | `behavior`(13) | 正文仅在 `fileContentCapture=true`；流式路径零触碰句柄；PathGate 内置 9 前缀 + 自写文件消除 |
| PREFS | OPEN✓ GET✗ PUT✓ REMOVE✓ CLEAR✓ | `SharedPreferencesImpl` 构造器/getX/Editor.put* | `prefs`(6) | `value` 需内容捕获；`commit/apply` 恒不挂钩（D5） |
| WEBVIEW | PAGE_LOAD✓ EVALUATE_JS✓ JS_BRIDGE✓ DEBUG✓ JS_CRYPTO✗ | `loadUrl`/`evaluateJavascript`/构造器 after 注桥/`setWebContentsDebuggingEnabled`/JS 探针 | `webview`(7) | 桥注册在构造器 after（D-16）；探针在 `onPageFinished`；`crypto.subtle` 缺失走 EX-05 |
| NETWORK | REQUEST✓(LISTEN) + SSL_×6 / PROXY_×4 / VPN_×4（FEATURE） | `Http(s)URLConnectionImpl`/`OkHttp`/`InetAddress`；SSL/代理/VPN 面 | `network`(2) + `enhance-ssl`(6)/`enhance-proxy`(4)/`enhance-vpn`(5) | 二态：开 → 干预生效（握手成功/代理不可见/VPN 不可见）+ 记录；关 → 原始行为复现 |
| DEVICE_ENV | DETECT_ROOT✗ DETECT_EMULATOR✗(LISTEN) + ROOT_×4 / XPOSED_×3 / EMULATOR_×7 / SYSTEM_PROPERTIES / PROC_×4（FEATURE） | `File.exists`/`Runtime.exec`/`getPackageInfo`/`System.getProperty`/`Class.forName` 双路径/`Build`/`SensorManager`/`TelephonyManager`/`/proc/*` 五文件 | `deviceenv`(21) + `enhance-root`(4)/`enhance-xposed`(3)/`enhance-emulator`(7)/`enhance-procfile`(5) | **本轮重点**：INJ-2 双注册修复前 `DETECT_*` 预期 0 条（登记 FAIL 非 SKIP）；掩蔽类无脏可掩走 EX-01 |
| UI_INTERACTION | DIALOG_SHOW✓ ACTIVITY_START✓ ACTIVITY_RESUME✗ WIDGET_TEXT✗ + DIALOG_KEYWORD✓(FEATURE) | `AlertDialog.show`/`TextView.setText`/`startActivity`/`onCreate` | `ui`(5) + `enhance-dialog`(2) | 关键词命中 → 弹窗不出现 + `interventionAction=BLOCKED`；不命中 → 正常显示；空词表 = 真实停用 |
| SYSTEM | LOGCAT✗ APP_INFO✓ SIGNATURE✓ | `Log.*`/`getPackageInfo(flags)` | `system`(3) | **INJ-1 修复前 SIGNATURE 可能恒 0（本机 Vector）** → 断言取「设备实测出记录」为准 |
| PROCESS_CONTROL | EXIT_INTERCEPT / CRASH_PREVENT / SCREEN_RECORD（全 FEATURE，默认关） | `System.exit`/`Runtime.exit`/`killProcess`/UCEH/`dispatchUncaughtException`/`Window.setFlags`/`Surface.lockCanvas`/`SurfaceControl.screenshot`/MediaProjection | `process`(1，**INJ-9 分派缺失**)/`enhance-secure`(4)/`enhance-proc`(+4) | 二态：ON → `survived=true`、`screencap` 有像素方差、无授权弹窗；OFF → 进程退出/黑屏/弹窗现 |

**通用红线用例**：`INJ-GATE-01` 全开关关闭 → 零副作用（含 `Build` 静态字段不被无条件改写）；`INJ-GATE-02` 干预动作恒经 `featureOn(kind)`；`INJ-GATE-03` 被动采集不改变宿主行为（不移游标、不调 `getCount`）；`INJ-NEG-01` 模块 enabled=0 → 宿主完全独立可用（`26-run-module-off-negative.ps1`）。

### 7.2 非功能（`NF-*`）

| ID | 项 | 方法 | 预期 |
|---|---|---|---|
| NF-PERF-01 | 冷启动预算 | `23-run-smoke.ps1` L-PERF（全功能 ON 逐 App 冷启动） | 无可感知启动延迟劣化、无 ANR；裁剪生效时 logcat 见「冷启动裁剪:跳过探针」 |
| NF-PERF-02 | 查询代价 | **真机 `EXPLAIN QUERY PLAN`**（ST-1 门禁） | 主查询计划零 `SCAN crypto_records`；无 `USE TEMP B-TREE` 于热路径 |
| NF-PERF-03 | 列表读放大 | 每页 60 行物化字节测量 | ≤ 预算（T1.8 定档） |
| NF-BP-01 | 黑洞 1511 | `27-run-backpressure.ps1` | 阻塞写秒级判死、L-SMOKE-01 <25s 有界返回；黑洞移除后积压完整回放 |
| NF-CAP-01 | 容量与裁剪 | 灌入 > `MAX_RECORDS` | 全局 20k / 每包 5k 双层生效；`trimmed` 计数增长；trim 不走全表 |
| NF-DB-01 | 迁移链 | 设备侧 prepopulated 旧库升级 | v2→当前版本成功、数据保留、索引齐（`DbMigrationParityTest` 对账） |
| NF-DB-02 | 收缩与完整性 | 清库后 `storage_usage` | 显著回落（ST-8）；`integrity_check` ok |
| NF-SEQ-01 | 跨重启续号 | 重启靶进程 | 无 `(pkg,proc,seq)` 覆盖丢数（ST-4） |
| NF-I18N-01 | 四语 | `LanguageHotSwitchTest` + `StringResourceParityTest` | 零重建全站切换；四语键集与占位符一致 |
| NF-THEME-01 | 对比度 | `SettingsThemeContrastTest` 20 seed × 亮/暗 | 红线全绿 |
| NF-STAB-01 | 崩溃/ANR | `logcat -b crash` 全程采集 | 零命中（宿主 + 冒烟 5 App + 靶） |
| NF-PKG-01 | 双变体构建 | `assembleModernRelease assembleModernDebug`（+ legacy 编译） | BUILD SUCCESSFUL；apksigner 验签 |
| NF-DOC-01 | 清单对账 | 本文件 §1.2 数字 ↔ 代码/脚本 | 零漂移（禁硬编码数字，A10） |

---

## 8. 执行序列与循环协议

### 8.1 单轮全量序列（`scripts/test-integration/run-acceptance.ps1` 机械执行，V45 T0.4 已落地）

> **阶段序修正**（建档后据实测调整）：MCP 轮必须排在注入轮**之后**——`24-run-mcp.ps1` 原形态自带
> `install -r`，而覆盖安装后 lspd 不再向新进程注入（AGENTS 实锤坑）；现其支持 `-SkipBuild`，
> 构建/安装统一由编排器 `build/install/scope` 门禁阶段承担（ENV-03）。矩阵步计数以
> `DumpResults` 逐断言 JSON 为权威，**「无台账且退出码 0」判 FAIL**（20..23 旧形态恒退出 0 已修）。

```
[G0]    preflight   00-env-verify.ps1 + 设备指纹（ENV-01..10；失败 = 整轮 BLOCKED 立即中止）
[G1]    build       :app:assembleModernDebug + :testtarget:assembleDebug
[G2]    unit        :app:testModernDebugUnitTest（JVM + Robolectric + 扫描门禁全量）
[G2b]   lint        :app:lintModernRelease（error 恒 0；warning ≤ 基线零新增）
[G2c]   assemble    :app:assembleModernRelease + :app:assembleLegacyDebug
[G3]    install     10-build-install.ps1（卸载式，禁 install -r；-Reboot 可选整机重载 lspd）
[G4]    scope       11-vector-scope.ps1（ENV-04，验 `Loading module` 日志）
──────  第一轮 · 前端界面（Z4：插桩前 disconnect TCP，只留单序列）  ──────
[G5]    ui          :app:connectedModernDebugAndroidTest（§5 全量 UI-* 用例）
        → FAIL=0 且 BLOCKED=0 且 SKIP⊆豁免表 ⇒ 该轮通过；否则 §9 登记 → 定向审计 → 修复 → **从 G0 整轮重跑**
──────  注入轮（脚本面单序列：5038 server + TCP 序列）  ──────
[G6]    inject      23-smoke → 20-listen → 21-enhance → 22-linkage → 26-module-off → 27-backpressure
──────  第二轮 · MCP（末位，-SkipBuild 复用已装产物）  ──────
[G7]    mcp         24-run-mcp.ps1（§6 全量 MCP-* 用例：45 工具 + PROTO + AUTH + WIRE）
        → 同判定 → 未过则登记 → 修复 → **从 G0 整轮重跑**
[G8]    reset       Reset-TmpState（§2.4：磁盘兜底配置 / 黑洞脚本 / 全局代理 / 冻结器）
[G9]    report      v45-<utc>.{json,md} + 逐阶段日志 → docs/test-reports/（§10 回填）
```

### 8.2 循环协议（需求 4 字面执行）

| 步 | 动作 | 产物 |
|---|---|---|
| 1 | 按 §8.1 跑完整轮 | JSON + md 报告 |
| 2 | 有不符预期项 → §9 缺陷清单（逐条：现象/期望/实际/复现步骤/首轮证据） | 缺陷登记表 |
| 3 | **定向审计分析**：每条归因到 `TASK-V45-AUDIT.md` 既有条目（ST/ISO/ML/DUAL/INJ/QA/ID）或新增条目（含 file:line 证据） | 审计条目回填 |
| 4 | 修复（按 `TASK-V45-PLAN.md` 对应任务落地 + 补测试） | ✅ + 备注 |
| 5 | **二次测试 = 整轮重跑**（G1→G11，禁止只跑失败项） | 新台账行 |
| 6 | 仍未一次性通过 → 回到步 2 循环，直至前端轮与 MCP 轮各自「一次性全量通过」 | 双轮通过记录 |

> 终止条件：同一轮序列内前端与 MCP 均零 FAIL/BLOCKED 且 SKIP 全豁免；且期间无代码改动（有改动即作废该轮，须再跑一轮确认）。

---

## 9. 缺陷登记（模板 + 空表，执行期回填）

| 缺陷 ID | 轮次 | 用例 ID | 现象 | 期望 | 实际 | 复现步骤 | 归因条目 | 严重度 | 状态 |
|---|---|---|---|---|---|---|---|---|---|
| DEF-V45-001 | 前端-1 | `MonitorListScreensTest#databaseList_isolatedType_onlySearchBarInAppBar`、`PureMarkdownIdentityTocTest#identity_andToc_render_withMetaTable`、`RecordListScrollPreservationTest#scrollPositionSurvivesDetailRoundTrip`、`TimelineListTest#{timelineGroups_dbRowDetailShowsSqlSection, timelineGroups_dbKindFilterShowsOnlyDbKinds, timelineGroups_dbRowRendersWithDbNameAndSqlPreview}`、`ServicesScreenTest#(同型种子断言)` | 「种子写入须在读回中可见」期望值与实读值差一个固定偏移(1→2、6→**4006**、120→121) | 种子数 = 读回数 | 上一用例类 `RecordQueryPlanTest` 的 4000 行 EXPLAIN 种子未清理(宿主库跨用例类共享) | `adb -s emulator-5554 shell am instrument -w -e class com.lioisme.modev.TimelineListTest com.lioisme.modev.test/androidx.test.runner.AndroidJUnitRunner` | QA-3 / §2.4 | P1 | 🟡 已修待二轮:①`RecordQueryPlanTest` 补 `@After unseed()`;②新增 `13-reset-device-state.ps1`(删库文件 + 靶 spool + 语言/主题偏好)挂到编排 `ui` 阶段前 |
| DEF-V45-002 | 前端-1 → 前端-4 归因 | `ColorSheetDeviceTest`(4)/ `V42StabilityProbeTest#{stress_themeSwatch…, stress_portSheet…}`/ `LanguageHotSwitchTest#jaKo_hotSwitch_…` | 用例开头即「could not find any node that satisfies Text contains <资源文案>」(主题色 / 监听端口 / 设置页标题) | 目标行在语义树内可定位并可点 | **测试侧根因(非主题/语言残留)**:这些行自 V41 T6 根页 IA 五分区起已不在根页(色板/语言在设置页,端口在服务页),而用例始终**没有任何前置导航**,`createAndroidComposeRule<MainActivity>` 落地的是应用列表 ⇒ 文案恒不存在。`LanguageHotSwitchTest` 的 en/zh 例有导航故一直绿、ja/ko 例漏了故一直红 = 同文件内自相矛盾的实锤 | 前端-4 `-e class com.lioisme.modev.ColorSheetDeviceTest` | QA-3 / §3.2 锚点规范 | P1 | ✅ 已修(前端-4 实测转绿):`AnchorProbe.openSettings(cd)` / `openServices(cd, 服务行)` 统一前置(带锚点等待与「停在哪一屏」诊断),三族用例逐条补前置;ja/ko 例的自名断言改 `onAllNodesWithText(...).onFirst()` 多节点安全形态(当前语言的行副标与选项同名) |
| DEF-V45-003 | 前端-1 | `ServicesScreenTest#wireBindError_rowRendersOnFailure_andClearsOnRecovery` | 用例经 `settings.setServerPort(被占端口)` 制造绑定失败 → 抛 `PortRejectedException: InUse` | 绑定错误行渲染 | T2.4 写口硬闸把该注入路径合法拒了(偏好零写入) ⇒ 观测面改由**绑定层最终裁决**触发 | 同上 `-e class …ServicesScreenTest#wireBindError_…` | AGENTS #29 / T2.4 | P2 | ✅ 已修:用例改由服务端 seam(`ConfigServer.port` + `restart()`)注入失败,并在收尾经**合法写路径**复位端口;产品侧行为(用户永不会被写入不可用端口)正是契约本意 |
| DEF-V45-004 | 前端-1 → 前端-4 归因 | `CryptoDetailZonesTest#{cipherDetail_showsDataFlowZone…, generateDetail_hasNoDataFlowZone…}`、`PureMarkdownIdentityTocTest`(同口径 2 例) | 详情页只找到 1 处算法名文本(断言要 2 处);失败转储显示唯一节点在 `t=360..435`(内容区首行) | 算法文本可见(身份区承载) | **两因叠加**:①同 DEF-V45-009 的视口外入口行点击丢失(导航从未真正到详情页,故「滚动容器内找不到」);②到页后仍红 = 断言口径过期 —— 自 **v29** 起详情顶栏标题恒为静态资源 `record_title`(「记录详情」),V28 的「算法名双处(顶栏 + 身份区)」契约早已不成立,唯一承载处 = 身份区 markdown H2 | `-e class com.lioisme.modev.CryptoDetailZonesTest` | QA-3 / AGENTS #21 | P2 | ✅ 已修(前端-4/5 实测):①导航补 `clickScrolledIntoView` + 锚点前置;②断言按现行契约改写 = 顶栏 `record_title` 显示 + 算法名**恰一处**(身份区 H2),`PureMarkdownIdentityTocTest` 的详情页哨兵同步改 `detail-more` 锚点 + 身份区文本轮询 |
| DEF-V45-005 | 前端-1 → 前端-5 归因 | `FourScreensNavTest#appRow_hasNoRunButton`、`ModevTopBarTest#longTitleStaysSingleLineWithinFixedHeight`、`MonitorListScreensTest#inApp_longPressOnClearTypeSlot_doesNotExpandSearch`、`SearchInputComponentsTest#longPress_onActionSlot_doesNotExpand` | 「The component is not displayed!」/「动作槽点击必须保持生效」 | 结构断言成立 | 三条已归因:**双序列踩踏**(DEF-V45-011,前端-3 起消失)与视口外点击(DEF-V45-009);第四条为断言口径问题 = M3 `TooltipBox` 锚点在长按阈值处消费手势弹 tooltip,内层 `IconButton` 的 onClick **不该**触发,旧断言却要求「长按也算一次点击」 ⇒ 改断言为「长按 0 次触发动作 + 紧随单击 1 次生效」并给 expected/actual。唯 `inApp_longPressOnClearTypeSlot` 仍红(长按后单击未弹危险确认,前端-5 实测「not displayed」),待定向取证 | 前端-5 `-e class com.lioisme.modev.MonitorListScreensTest#inApp_longPressOnClearTypeSlot_doesNotExpandSearch` | QA-3 / AGENTS #24 | P2 | 🟡 4 条中 3 条关闭;1 条(清空确认对话框未弹)待定向取证 —— 下一步在长按后打印 `records-clear-confirm`/Dialog 节点存在性,判「点击被吞」还是「文案节点在 Dialog 双 root 下不可达」 |
| DEF-V45-006 | 前端-1 | `hook.DatabaseProbeTest#open_insert_query_iteration_transaction_full_chain` | 裸 `AssertionError`(注入链路无记录) | 五段 SQL 钩子全链产记录 | 覆盖安装后 lspd 不再向新进程注入(AGENTS 实锤坑),本轮未加 `-Reboot` | `-Reboot` 整机重启后 `-e class com.lioisme.modev.hook.DatabaseProbeTest` | T0.3 / BL-3 | P1 | 🟡 归环境排序:后续轮 `install` 阶段带 `-Reboot`(编排已实现等待 `boot_completed`) |
| DEF-V45-007 | 前端-1 | `RecordQueryPlanTest#neighborKeysetQueryAvoidsFullScanAndTempSort` | 6/7 条 EXPLAIN 计划断言绿,唯 keyset 邻接查询红 | 无裸 `SCAN` 且无 `USE TEMP B-TREE` | 本轮 XML 失败文本与相邻用例串位,未取得计划原文 | `-e class com.lioisme.modev.RecordQueryPlanTest#neighborKeysetQueryAvoidsFullScanAndTempSort`(日志 `PLAN:` 行) | ST-2 | P1 | ⬜ 单独取证;若确为 `(timestamp,id)` 双列比较退化,则按 ST-2 定向修生成器 |
| DEF-V45-008 | 前端-1 | 编排器自身(`run-acceptance.ps1` / `10-…` / `11-…` / `00-…`) | ①子步结束后编排器永久挂死;②所有矩阵步计数恒 0;③`-Args @("-SkipBuild")` 静默失效致 preflight 误跑全档 BLOCKED;④门禁步无逐断言台账 = 按不可判定判红;⑤手工 `pm clear` 探针把设备 PackageManager 打到假死(此后一切 `run-as`/`pm`/`dumpsys package` ≥25s 不返回) | 每步可判定、可计时、可归因 | ①`& powershell \| Tee` 下孙进程继承 stdout 句柄 ⇒ 管道无 EOF;②PS 5.1 `@(Get-Content -Raw \| ConvertFrom-Json)` 得到「1 元素 = 数组本身」;③参数名撞自动变量 `$args`;④10/11 无 DumpResults;⑤操作失误(已记教训:复位禁 `pm clear`,改删库文件) | 见各行内说明 | QA-1 / QA-9 / AGENTS 新增「验收编排器三坑」 | P0 | ✅ ①-④ 已修并实测(preflight PASS=7/exit=0;13 步 5 断言);⑤ 需整机重启恢复(`-Reboot`) |
| DEF-V45-009 | 前端-2 → 前端-4 归因 | `PureMarkdownIdentityTocTest`(2)/ `RecordListScrollPreservationTest`(1)/ `CryptoDetailZonesTest`(2)/ `MonitorEntryHeaderTest`(1) | 点监控页「算法记录」入口行后,列表页文案恒找不到;失败信息只给一份「6 children / `maxValue=2935` 的滚动容器」语义转储 | 点入口行必须进对应记录列表;入口行显示性断言必须成立 | **测试侧根因(非产品缺陷)**:监控页是整屏 `verticalScroll` Column,Hero/统计区之下的入口行落在视口外时 `performClick()` 的坐标在窗口外 ⇒ **触摸被静默丢弃(零异常零导航)**,失败只在下一屏断言上炸开;V40 T4 已在 `MonitorListScreensTest`/`FourScreensNavTest` 修过同一坑(`performScrollTo` 前置),其余用例从未同步。前端-2 的「停在应用列表」判读系几何猜错 | `-Stage install` 后 `--tests` 单类跑 `PureMarkdownIdentityTocTest`(前端-3 复现 → 前端-4 转绿) | QA-3 / AGENTS 新增「视口外 performClick 静默丢弃」 | P1 | ✅ 已修(前端-4/5 实测):①新增 `androidTest/AnchorProbe.kt` = 屏级锚点登记表 + `awaitAnchor(tag, note)`(超时把「当前停在哪一屏」写进失败信息)+ `clickScrolledIntoView(matcher)`(先滚进视口再点);②五个用例类的监控页入口点击全部改走该前置;③`MonitorEntryHeaderTest` 入口行显示性断言补 `performScrollTo()` |
| DEF-V45-010 | 前端-2 / MCP-2 / 前端-4 | 设备环境(框架守护进程) | `pidof lspd` 为空;本档实测 Magisk 侧同样缺席:`ps -A` 无 `magiskd`、`/data/adb/service.d` 与 `post-fs-data.d` 均空(模块 `service.sh` 从未被链接)、无 `/sbin`·`/debug_ramdisk`、`zygote64` maps 不含 vector/lspd 任何库 ⇒ **zygisk 链在本 boot 完全未加载**,不是「模块表未刷新」 | 注入矩阵可跑 = 需求 4 的硬前置 | 环境:维护者已冷启 VM(uptime 归零可证)但框架未恢复 ⇒ 根因在 VM 侧 Magisk 引导(boot 镜像/还原模式),而非 adb 层;`dnconsole quit` 需管理员权限(本机拒绝) | `adb -s emulator-5554 shell "su 0 ps -A \| grep -E 'magiskd\|lspd'"`(空即红) | ENV-03 / AGENTS「lspd 缓存模块表,不重启不生效」 | P0 | ⬜ 待维护者操作:需以管理员身份完全退出并启动雷电(或重打 Magisk 引导);恢复判据 = `pidof lspd` 非空 + `11-vector-scope.ps1` 注入断言绿,之后才能跑 `-Stage scope/inject` 与依赖注入的 MCP/插桩行(本轮 `WebViewBridgeTest` 1 例红同源) |
| DEF-V45-011 | 前端-2 / 前端-3 | 编排器 `run-acceptance.ps1`(`Set-SingleSequence` / `Run-Gradle`)+ `tt-env.ps1` | ①插桩轮 `[Z4] instrument 序列面` 打印 **两条**序列仍照常开跑;②前端轮 23 条失败里 7 条 `Failed to inject touch input`;③`-Stage ui` 在子构建结束后不再返回(挂死) | 插桩轮必须只见 1 条序列;步必须可计时可结束 | ①`tt-env.ps1` 在 dot-source 时设 `$env:ANDROID_ADB_SERVER_PORT=5038` 且**从不复原** ⇒ `disconnect`/`devices` 打在独立 server 上(看到双序列),更关键的是 **gradle/AGP 继承同一变量**,`connectedModernDebugAndroidTest` 对同一台 VM 的两条序列各跑一遍(抢前台/焦点/触摸注入)= 大面积假失败;②`Run-Gradle` 仍是 `& gradlew \| Tee-Object` 形态 = AGENTS「编排器三坑」①的同一挂死机制(构建/Kotlin 守护进程继承 stdout 句柄) | `powershell -File scripts/test-integration/run-acceptance.ps1 -Stage ui` 后看 `[Z4] instrument 序列面` 行数 | QA-1 / QA-9 / AGENTS「雷电同一设备双序列」「验收编排器三坑」 | P0 | ✅ 已修并实测:①插桩档先 `Remove-Item Env:ANDROID_ADB_SERVER_PORT` 再断连 TCP 序列,并新增 `Z4_VIOLATION` 硬门禁(序列数 ≠ 1 直接 BLOCKED,不再产出踩踏数据);②`Run-Gradle` 改 `Start-Process + 文件重定向 + WaitForExit(ms)` 同构形态(带 `-TimeoutMin 60`);③实测前端-3 = **118 例单序列**(vs 前端-2 的 145 例双跑),失败 23→19 且全部可归因 |
| DEF-V45-012 | 前端-3 / 前端-4 / 前端-5 | `DataSettingsScreenTest`(2 例)+ 构建链 | `NoSuchMethodError: <init>(CryptoRecordDao;ConfigServer;RecordCodecRegistry;IILFunction0;ILDefaultConstructorMarker)` in `LogRepository`,而 install 步同时打印 `[PASS] install … Success` | 装机步必须证明「设备上的产物 = 本次构建产物」 | **已归因 = Gradle 构建缓存交出陈旧 androidTest 产物**:`dexdump` 逐产物对账实锤 —— 宿主 APK 的 `LogRepository` 恒为 8 参(T1.6 载荷预算 + T1.7 治理出口),而 androidTest APK 内 `DataSettingsScreenTest` 的调用点是 **6 参 synthetic**(T1.7 之前的编译产物);`:app:clean` 后仍是 6 参(`--rerun-tasks --no-build-cache` 重编后变 8 参)⇒ 缓存按旧 key 命中并把旧 dex 当 up-to-date 交回。JVM 单测面同批编译源码故恒绿 = 只有插桩运行期才炸 | `./gradlew :app:packageModernDebugAndroidTest --rerun-tasks --no-build-cache` 后 `dexdump -d classes7.dex \| grep "LogRepository;.<init>"` | QA-1 / Z1(产物必须可归因) | P1 | ✅ 已修(加固双保险):①`run-acceptance.ps1` 的 `Run-Gradle` 与 `10-build-install.ps1` 的 assemble 一律加 **`--no-build-cache`**(验收轮从源码可复现优先于构建提速);②装机步新增**产物摘要对账断言**(本地 APK MD5 == 设备 `base.apk` MD5,不再信 `install` 文本与 versionName 回读) |
| DEF-V45-013 | 前端-4 | `GlobalTypeRecordsTest`(2)/ `MonitorListScreensTest`(5)`@Before seed` | 「种子写入须在读回中可见」expected:<2> was:<1>、expected:<4> was:<3> —— 整类用例在 @Before 即红 | 种子断言必须校验**本用例自己保证的**不变量 | **本轮自引入的回归**:为修 DEF-V45-001 残留(全局计数把别的包也算进来)把六处种子断言一律改成按包 scoped,但其中两类是 `clearAll()` + **跨包种子**(全局列表聚合语境),scoped 计数天然少算他包行。正确口径 = 与清除范围同构:`clearAll` 者按全局、`clearPackage` 者按该包 | `-e class com.lioisme.modev.MonitorListScreensTest`(前端-4 XML) | QA-3 / §3.3 数据准备 | P1 | ✅ 已修(前端-5 起):两类 `clearAll` 用例回退全局口径并在行内写明「不变量与清除范围必须同构」;`clearPackage` 的四类保持 scoped(前端-4 的 121≠120 残留即由此消解) |

登记规则：①一个现象一条，禁合并；②必须给出可机械复现的命令行；③归因列不得为空（新根因需在 AUDIT 文档新增条目并编号）；④关闭必须附「二次测试」整轮记录链接。

---

## 10. 执行台账

| 轮次 | 日期/时间 | 构建号 | 设备指纹 | G1 单测 | G2 lint | G7 前端 | G8 MCP | G9-G10 注入 | 结论 |
|---|---|---|---|---|---|---|---|---|---|
| 预检-0 | 2026-09-19 | 工作树（未构建） | emulator-5554 / A14 / x86_64 / magisk / Vector | **BUILD SUCCESSFUL 2m52s（112 XML / 726 @Test 全绿）** | 未跑 | 未跑（APK 为 2026-09-08 旧版 = BL-3） | 未跑 | 未跑 | 建档 + 审计完成；验收起跑线未就绪（Phase 0 待做） |
| 预检-1 | 2026-09-19 | 工作树 + Phase 0 修复（未安装到设备） | 指纹自动采集实测：A14 / sdk34 / x86_64 / model 25102RKBEC / uid=0 / framework=lspd / boot=1 | **733 例 / 114 suites / 0 fail / 0 skip**（+7 新例：`ClassValueCacheTest` 5、`MinSdkApiConventionTest` 1、registry 三态 1） | **0 error / 41 warning / 1 hint**（首轮为 9 error → INJ-12 修复） | 未跑（编排 `ui` 阶段待设备窗口） | 未跑（`run-acceptance.ps1` 已可执行，`-Stage report` 冒烟通过） | 未跑 | **Phase 0 出口达成**（BL-1/2/4/5/6 清零；BL-3 = T0.3 延后至设备窗口）；`assembleModern{Debug,Release}` + `assembleLegacyDebug` 全绿 |
| 预检-2 | 2026-09-20 | 工作树 + Phase 2 全量落地(未新增安装) | 同预检-1 指纹(A14 / sdk34 / x86_64 / 25102RKBEC / uid=0 / lspd / boot=1) | **781 例 / 124 suites / 0 fail / 0 error**(新增:`LayerDependencyGateTest` 3、`SingleWriteEntryPointTest` 3、`RecordWriteModelParityTest` 4、`PortWriteGuardTest` 5、`RecordTypeCountParityTest` 2、`LogRepositoryIngestBudgetTest` 2、`RecordFilterConvergenceTest` 7、双域闸 1) | **0 error / 41 warning / 1 hint**(基线零新增)+ `assembleModern{Debug,Release}` + `compileLegacyDebugKotlin` 全绿 | 未跑(转入前端-1) | 未跑 | 未跑 | **Phase 2 出口(除前端插桩)达成**;编排器 `-Stage preflight` 实测 PASS=7/exit=0 |
| 前端-1 | 2026-09-20 | `assembleModernDebug` + `testtarget-debug` 装机(10-build-install 绿) | emulator-5554 / 127.0.0.1:5555 @5038,A14 x86_64 root lspd boot=1 | **781 例全绿**(整轮内 `unit` 步) | **0 error**(整轮内 `lint` 步) | ❌ **未一次性通过:130 PASS / 22 FAIL / 1 SKIP**(androidTest XML 权威计数;失败已逐组登记 DEF-V45-001…007) | 未跑(本轮 `-SkipMcp`;且手工 `pm clear` 探针致设备 PMS 假死,见 DEF-V45-008⑤) | 未跑(`-SkipInject`) | **一次性全量未达成 ⇒ 按需求 4 继续「定向审计 → 修复 → 二次整轮」循环**。本轮净产出:①编排器 4 处假绿/挂死缺陷修复(DEF-V45-008①–④)+ 阶段序改「注入矩阵在前、插桩在后」;②新增 `13-reset-device-state.ps1` 落实 §2.4 复位;③`RecordQueryPlanTest` 种子泄漏修复;④绑定失败观测面用例改由 seam 注入(与 T2.4 硬闸契约对齐)。下一轮:`-Stage all -Reboot`(整机重启恢复 PMS 与注入),MCP 轮随后单独跑 |
| 预检-3 | 2026-09-20 | 工作树 + Phase 3(T3.1–T3.5/T3.7)与 T1.7 全量落地(未新增安装) | **无可用设备**:LDPlayer VM 未运行 ⇒ 指纹 `reachable:false`,`adb devices`(默认 server 与 5038 独立 server)两世界皆空 | **815 例 / 127 suites / 0 fail / 0 error / 0 skipped**(新增 34 例:`McpToolsTest` T3.2 参数矩阵 9、`UiMcpCapabilityParityTest` 6、`DetailAnchorContractTest` 5、`AppListenOverrideDomainTest` 5、`DatabaseGovernanceTest` 4、`McpToolParityTest` 装配/排除 2) | **0 error / 41 warning / 1 hint**(基线零新增)+ `assembleModernRelease` + `compileLegacyDebugKotlin` + `assembleModernDebugAndroidTest` 全绿 | ❌ 未跑(VM 不在) | ❌ 未跑 | ❌ 未跑 | **门禁级进展但验收轮仍未达成**:①`-Stage preflight` 实测 FAIL=7/BLOCKED —— 编排器在无设备时**如实报阻塞而非假绿**(Z1 纪律生效),台账 `v45-20260920-020216.json`;②本轮全部改动只经 JVM/编译/lint 证明,MCP 协议面新行为(`-32602` 三口径、派生载荷字段名)与 DUAL-12 分域恢复、T1.7 自愈/收缩**都还缺设备端真机证据**;③下一轮须由维护者启动 VM 后执行:`powershell -File scripts/test-integration/run-acceptance.ps1 -Stage all -Reboot`(阶段序:preflight→build/install→scope→inject→ui→mcp,插桩在注入之后),再按 §8 循环到一次性全量通过 |
| 前端-2 | 2026-09-20 | `-Stage install`(10-build-install PASS=5)+ 工作树含 Phase 3 与 T1.7 | 127.0.0.1:5555 @5038 / emulator-5554,A14 sdk34 x86_64 root uid=0 lspd boot=1(**但 `pidof lspd` 为空 = 框架守护进程未运行**) | 815 例全绿(本轮前已验) | 0 error / 41 warning / 1 hint(本轮前已验) | ❌ **未一次性通过:121 PASS / 23 FAIL / 1 SKIP** | 未跑(阶段序:插桩在注入之后,本轮注入矩阵被框架未运行阻塞) | ❌ 未跑(scope FAIL=1「Vector injection into testtarget」) | **归因清楚的三点**:①**DEF-V45-001 残留种子类已修**(上一轮「种子写入须在读回中可见 expected:1 was:0」全部消失;`13-reset-device-state.ps1` + T1.7 fail-open 修正共同作用,见下 ③);②新暴露(被上一轮更早失败掩盖)的失败类 = `PureMarkdownIdentityTocTest`/`RecordListScrollPreservationTest` 停在**应用列表**(失败节点 6 children = 列表未被离开),即 111 行 `onNodeWithText(ownPackage).performClick()` 未导航 → 与 AGENTS #24「行点击语义」相关,已登 DEF-V45-009;另有 7 条 `Failed to inject touch input`(DEF-V45-005 族);③**T1.7 首开自愈在设备上的真实风险已修**:`quick_check` 由布尔改三态(`Healthy/Suspect/Unknown`),探针跑不通不再触发删库重建(`verdictOf` 判定表纯函数 4 臂锁死),避免「某 ROM query 通道不收 PRAGMA ⇒ 每次冷启清空全部记录」;**④整轮被一个环境问题整体卡住:VM 未被真正冷重启**(`dnconsole quit` 需管理员权限,本机被拒;`adb reboot` 不足以让 lspd 重新挂载 zygote)⇒ 注入矩阵 + 依赖注入的 MCP/插桩行全部无法取得真机证据 |
| MCP-2 | 2026-09-20 | 同前端-2(工作树含 Phase 3;`-SkipBuild`) | 同上(框架未运行) | — | — | — | **36 PASS / 10 FAIL / 0 SKIP**(427s) | ❌ 未跑 | **10 条失败全部同一根因 = 靶 App 未被注入(记录恒 0)**:M-REC-01/02/03/05/06/07a(需 testtarget 捕获记录)、M-CFG-01b(需 FILE_READ 推送生效)、M-CFG-02b/02c(需 EXIT_INTERCEPT 真实拦截)、M-APP-01a(需框架作用域 Ready)。**本轮净新增通过的协议行**:M-PROTO-03a(未声明参数 -32602)/03b(非法枚举 -32602)/03c(required 传 null = 同缺失)/M-APP-01c(`showModules` 新键名往返)/tools-list 计数派生对账 + 45 工具面全广告一致 + 记录写入-读回闭环(`record_create`/`record_update` 用派生字段名成功)⇒ **T3.2/T3.3/T3.4 的对外契约在真机协议面成立**;其余 9 条需在框架恢复后重跑 |
| 前端-3 | 2026-09-20 | `-Stage ui`(单序列修复后首轮;工作树 = Phase 3 + T1.7) | emulator-5554 单序列(A14 x86_64 root),框架守护进程仍缺(`pidof lspd` 空) | 815 例(前端-2 已验,本轮无产品改动) | 0 error / 41 warning / 1 hint | ❌ **未一次性通过:118 例中 19 FAIL / 1 SKIP**(`Finished 118 tests`,vs 前端-2 的 145 = 双序列虚增) | 未跑(`-Stage ui` 不含注入) | ❌ 未跑 | **本轮价值 = 把「不可归因」变成「可归因」**:①Z4 环境变量泄漏修复后用例数从 145 回落到真实的 118,7 条 `Failed to inject touch input` 全部消失(DEF-V45-011);②新增 `AnchorProbe`(屏级锚点登记表 + `awaitAnchor` 超时报告「停在哪一屏」+ `clickScrolledIntoView`),首轮即用其判定推翻「停在应用列表」的几何猜,真根因 = 监控页入口行在视口外 ⇒ `performClick` 坐标落窗外被静默丢弃(DEF-V45-009);③新暴露构建链缺陷:同一轮宿主 APK 与 androidTest APK 的 `LogRepository` 构造器 ABI 不一致 ⇒ 只在设备运行期炸 `NoSuchMethodError`(DEF-V45-012);④`-Stage ui` 挂死根因 = `Run-Gradle` 仍是 `& \| Tee` 形态,改 `Start-Process` + 文件重定向 + `WaitForExit(ms)` |
| 前端-4 | 2026-09-20 | `-Stage install`(PASS=5)+ `-Stage ui`(测试侧修复后) | 同前端-3(emulator-5554 单序列) | 815 例(无产品改动) | 0 error / 41 warning / 1 hint | ❌ **未一次性通过:121 PASS / 13 FAIL / 1 SKIP** | 未跑 | ❌ 未跑 | **净关 6 类**:ColorSheet 4 例 + V42 色板/端口 + `jaKo_hotSwitch` = 补根页→设置页→服务页前置后全绿(DEF-V45-002);PureMarkdown 2 例 + RecordListScroll 1 例 + CryptoDetailZones 2 例 = `clickScrolledIntoView` + 详情页「双处」口径按 v29 静态顶栏改写后绿(DEF-V45-004/009);MonitorList 类别筛选 = 改走 T3.7 `kind-filter-CIPHER`/`kind-filter-all` 锚点(V43 T4 记录行自身承载类别词元 ⇒ 「文本 + 可点」恒双命中)。**自引入回归 7 例已当日回退**:把六处种子断言一律按包 scoped,但两类是 `clearAll` + 跨包种子 ⇒ 口径必须与清除范围同构(DEF-V45-013)。剩余 13 例逐条:DataSettings 2(DEF-V45-012 构建缓存)/MonitorList 5(DEF-V45-013)/`MonitorEntryHeaderTest` 1(入口行显示性断言缺 `performScrollTo`)/`jaKo_hotSwitch` 1(语言行副标与选项同名的双命中,改 `onFirst()`)/V42 语言 1(期望文案在循环外算一次 = 拿单语去比双语界面)/WebViewBridge 1(DEF-V45-010 注入缺失) |
| 前端-5 | 2026-09-20 | `-Stage install`(**新增产物 MD5 对账断言**,PASS=6)+ `-Stage ui` | 同前端-4;**本轮跑到 71/117 时 VM 的 adbd 消失**(`127.0.0.1:5555` 连接被拒 + 三 server 设备表全空,`dnplayer` 进程在但无 VM 后端) | 815 例(无产品改动) | 0 error / 41 warning / 1 hint | ⛔ **整轮作废(不可判定,非「未通过」)**:71 例完成 / 5 FAIL,设备中途掉线使剩余 46 例从未执行 ⇒ 按 §4.1「环境事件不得计入通过」;5 条失败中 2 条 = DEF-V45-012(构建缓存,已归因并加固)、2 条 = MonitorList(类别筛选转绿后**前进到下一步**:展开态 Dialog 输入域未出现 / 长按后单击未弹危险确认)、1 条 = V42 端口 sheet(掉线瞬间) | 未跑 | ❌ 未跑 | **本轮净产出 = 把 DEF-V45-012 从「玄学」钉成「事实」**:逐产物 `dexdump` 对账证明宿主 APK 的 `LogRepository` 恒 8 参、androidTest 调用点为 T1.7 之前的 6 参 synthetic,`clean` 后仍旧、`--rerun-tasks --no-build-cache` 后变 8 参 ⇒ 验收轮 gradle 全面禁缓存(编排 `Run-Gradle` + `10-build-install` assemble 均已加 `--no-build-cache`)。**阻塞 = 设备**:需维护者重启雷电 VM(且本 boot 的 Magisk/zygisk 链未加载,见 DEF-V45-010),恢复后按「install → ui 整轮 → scope → inject → mcp」序重跑;MonitorList 两条需在重跑前继续定向取证(下一步打印 `records-clear-confirm`/Dialog 输入域节点存在性) |

---

## 11. 附录：命令速查

```bash
# 单序列切换（Z4）
adb disconnect 127.0.0.1:5555; adb devices -l                 # 插桩轮：只留 emulator-5554
ANDROID_ADB_SERVER_PORT=5038 "D:/leidian/LDPlayer14/adb.exe" connect 127.0.0.1:5555   # 脚本轮：TCP 单序列

# 设备健康与环境指纹
adb -s emulator-5554 shell "getprop ro.build.version.sdk; getprop ro.product.cpu.abi; uptime; su -c id"
adb -s emulator-5554 shell "settings put global cached_apps_freezer disabled"

# MCP over forward（initialize → 取会话头 → 调用）
adb -s emulator-5554 forward tcp:1510 tcp:1510
curl -si -X POST http://127.0.0.1:1510/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","clientInfo":{"name":"modev-at","version":"1"}}}'
curl -s -X POST http://127.0.0.1:1510/mcp -H 'Mcp-Session-Id: <sid>' -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"records_type_counts","arguments":{}}}'

# 靶用例驱动与结果读取
adb -s emulator-5554 shell "am start -n com.lioisme.modev.testtarget/.TriggerActivity --es domain crypto --es case L-CRY-02"
adb -s emulator-5554 logcat -d | grep TT_RESULT | tail -20

# 定向插桩复跑（单用例）
adb -s emulator-5554 shell am instrument -w -e class com.lioisme.modev.services.ServicesScreenTest#x \
  com.lioisme.modev.test/androidx.test.runner.AndroidJUnitRunner

# 宿主库直查（只读）
adb -s emulator-5554 shell "run-as com.lioisme.modev cat databases/modev.db" > /tmp/m.db
```

> 插桩注意：`connectedAndroidTest` 后 AGP 会卸载宿主 APK 并复位 Vector 模块表 → 设备注入矩阵必须排在插桩之前，或测后立即执行 ENV-03/04 恢复序列（AGENTS 实锤坑）。
