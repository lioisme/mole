# V50 交付物 4 / 全功能待测试清单与执行台账

> 生成日期:2026-09-24。这是需求 3 的**登记面**:每一项功能一行,
> 「测完一条登记一条」,不是最后补一份总结。
> 判据分层(缺任一层不得记 ✅):
> **J** = JVM 单测/门禁 · **I** = 设备插桩(androidTest) · **M** = 注入矩阵(靶程序读回) · **S** = 静默/负向(开关全关 = 零副作用)
>
> 权威台账文件:`docs/test-reports/v45-<utc>.json`(逐断言)+ 同名 `.md`(人读)。
> 本清单是**能力视角**,台账是**断言视角**,两者由矩阵步的 `Round/Assert` 名字对应。

---

## 0. 本轮测试轮次登记

| 轮次 | 时间(UTC) | 范围 | 结果 | 台账 |
|---|---|---|---|---|
| R1 | 2026-09-24 | JVM 全量(整改前基线) | **1174 / 0 失败** | `--rerun --no-build-cache` 实跑 |
| R2 | 2026-09-24 | 孤儿门禁首跑(驱动源) | **红**:O1 死行 1 + O2 恒空操作 27 | `FeatureOrphanGateTest` |
| R3 | 2026-09-24 | 反证轮:去掉 S1 解析臂后跑新门禁 | **红**(A1+A3 双红)= 证明门禁能抓 S1 | `/tmp/v50-counter2.log` |
| R4 | 2026-09-24 | JVM 全量(整改后) | **1185 / 0 失败 / 0 跳过** | `--no-build-cache --continue` |
| R5 | 2026-09-24 | lint | **43 warning / 1 hint / 0 error**(基线未劣化) | `lintModernRelease` |
| R6 | 2026-09-24 | legacy 变体编译 | **BUILD SUCCESSFUL** + 双 ABI APK | `assembleLegacyDebug` |
| R7 | 2026-09-24 | **设备一次性全量验收** | 108P/25F/9S → **FAIL** | `docs/test-reports/v45-20260924-154144/` |
| R8 | 2026-09-25 | 设备 inject 段静默重跑 | 125P/37F/16S → **FAIL**(归属见 §5.1) | `docs/test-reports/v45-20260924-163703/` |
| R9 | 2026-09-25 05:16 | 设备全量(带 `-Reboot`,首次接上掉线归因) | 跑到 listen 第 3 次掉线 ⇒ **人工中止**(同产物继续跑只会再产一批假红) | `docs/test-reports/v45-20260924-211630/` |
| R10 | 2026-09-25 05:49 | 设备全量(Z4/运行态排序修正后) | **listen 单档 66P / 0F / 8S**(db 五类首次全绿 23/15/7/219/6)—— 但 3 次掉线触发新门禁 ⇒ **整步作废 + 整轮 BLOCKED**,判 FAIL | `docs/test-reports/v45-20260924-214945/` |
| R10-关键结论 | — | ~~db 整域零产出是 FLAPPY~~ → **R6 已定因:根本不是产品缺陷,是量具把 adb 报错当成查询结果** | R6 的普查块抓到铁证:`!! L/db 整域零产出 ⇒ 打证据` 下面那一行**不是数据库行,而是 `adb.exe: device '127.0.0.1:5555' not found`**。链路 = 设备此刻已不可达 → `Q` 把报错文本当数据行返回 → `KindCountsAll` 因 `$p[2]` 非数字**静默跳过每一行** → 整表读成「零记录」→ db 五类恒 `count=0` 记为产品缺陷。这同时解释 R3 红 / R10 绿 / R6 又红(全看掉线时点),**与钩子代码零关系**。旁证:同文件的 `KindCount` 走 `CountChecked`,拿不到数字就 `throw`,所以 enhance 档从来不撒这个谎 —— 撒谎的只有 `KindCountsAll` 这一条通路 | 已修:①`KindCountsAll` 识别 adb 报错行并记入 `$script:LAST_QUERY_ERROR`,**不再静默当零**;②断言详情在不可达时打「设备不可达 ⇒ 计数不可信:<原始报错>」而不是 `count=0` |
| R7 / R8 | 2026-09-25 | 设备全量(诚实计数 + 一级桥恢复 300s) | **失败定位收口**:两轮都在 **db 域**遇到 `device not found`(R8 该域 5 条详情已诚实写作「设备不可达 ⇒ 计数不可信」),恢复后其余 9 域照常全绿(R8 listen 56P/5F、R7 同型)⇒ 这不是均匀的环境噪声,而是**随记录量集中在 db 域发作**(R10 健康样本:EXEC=219 行/域)。`vm-lost` 标记 ⇒ 两步均被作废 BLOCKED | `v45-20260924-232054/`, `v45-20260925-002953/` |
| R9b | 2026-09-25 09:2x | 设备全量(**新增:域级重试** —— 域内检测到设备不可达 ⇒ 撤销本域刚记的假红/假绿并等恢复重跑一次,控制流已由 `scripts/v50-test-retry-flow.ps1` 实测验证) | 进行中 | `v45-20260925-012225/` |
| R9 | 2026-09-25 | **JVM 终验**(`--rerun --no-build-cache`,拒绝 UP-TO-DATE 假绿) | **1185 / 0 失败 / 0 跳过** | `/tmp/v50-final2.log` |

> **一句话结论**:JVM 侧三道门禁(全量单测 / lint 基线 / 孤儿门禁)**全绿且带反证**;
> **设备侧未达成"一次性全量通过"** —— 原因是 VM 中途掉线(3 次实锤)+ 增强域 V49 既存 12 条红,
> 二者都不是本轮引入的功能回归。详见 §5。

---

## 1. 监听域(43 类)× 测试层

> 记录型能力的最小判据是 **M**:靶程序真调用 → 列表出现该 kind 的记录;
> 以及 **S**:该 kind 关闭 → 零记录且零副作用。

### 1.1 算法(11)

| 能力 | J | I | M | S | 备注 |
|---|---|---|---|---|---|
| CIPHER | ✅ | — | §5 | §5 | `ENCRYPT`/`DECRYPT` 本轮起按 init 方向真发射(V50 OPS-3) |
| MESSAGE_DIGEST | ✅ | — | §5 | §5 | |
| MAC | ✅ | — | §5 | §5 | |
| SIGNATURE | ✅ | — | §5 | §5 | |
| KEY_PARAM | ✅ | — | §5 | §5 | |
| KEY_GENERATION | ✅ | — | §5 | §5 | |
| KEY_FACTORY | ✅ | — | §5 | §5 | |
| KEY_STORE | ✅ | — | §5 | §5 | |
| KEY_AGREEMENT | ✅ | — | §5 | §5 | |
| RANDOM | ✅ | — | §5 | §5 | |
| BASE64 | ✅ | — | §5 | §5 | |
| ~~WEB_CRYPTO~~ | — | — | **已退役** | — | V50 N1:注入侧零发射的死行,整行删除(含 `HookSwitch.webView` 位) |

### 1.2 数据库(5)/ 行为(7)/ 偏好(5)/ WebView(5)

| 能力 | J | M | S | 备注 |
|---|---|---|---|---|
| DB:OPEN / QUERY / DML / EXEC / TRANSACTION | ✅ | §5 | §5 | OPEN 含 close 两操作(命名整改 N5 待做) |
| BEHAVIOR:FILE_READ / WRITE / DELETE / RENAME / LIST / ASSET_READ / SHELL_EXEC | ✅ | §5 | §5 | 内容捕获仅限随调用可得的整读 API |
| PREFS:OPEN / GET / PUT / REMOVE / CLEAR | ✅ | §5 | §5 | |
| WEBVIEW:PAGE_LOAD / EVALUATE_JS / JS_BRIDGE / DEBUG / JS_CRYPTO | ✅ | §5 | §5 | `EVALUATE_JS_LEGACY` 本轮起可达(旧 API 重载已分派操作名) |

### 1.3 环境观测(7,跨域闸)+ 系统信息(3)

| 能力 | J | M | S | 备注 |
|---|---|---|---|---|
| NET:REQUEST | ✅ | §5 | §5 | DnsHideHooker 实为 DNS 观测(N6 待更名) |
| DEVICE_ENV:DETECT_ROOT | ✅ | §5 | §5 | **本轮补 `ROOT_CHECK_EXEC` 发射**(命令探测 root 之前只在干预面可见) |
| DEVICE_ENV:DETECT_EMULATOR | ✅ | §5 | §5 | **本轮修死钩**:Build 字段监控原查 `getFINGERPRINT` 假 getter ⇒ 恒零安装,改钩 `Field.get` 反射通路 |
| UI:DIALOG_SHOW / WIDGET_TEXT / ACTIVITY_START / ACTIVITY_RESUME | ✅ | §5 | §5 | **本轮补 6 个生命周期操作发射面** + `DIALOG_CANCEL` + `WIDGET_SET_TEXT_RES` + `ACTIVITY_START_OPTIONS` |
| SYSTEM:LOGCAT | ✅ | §5 | §5 | **本轮补 `Log.wtf` → `LOG_FATAL`** |
| SYSTEM:APP_INFO | ✅ | §5 | §5 | **本轮按 `GET_PERMISSIONS` flag 分派 `APP_PERMISSIONS`** |
| SYSTEM:SIGNATURE | ✅ | §5 | §5 | **本轮按 `GET_SIGNING_CERTIFICATES` 分派 `SIGNATURE_READ_V2`** |

---

## 2. 功能域(45 类)× 测试层

| 组 | 能力 | J | M | S(开关全关 = 零副作用) | 备注 |
|---|---|---|---|---|---|
| 进程守护 | EXIT_INTERCEPT / CRASH_PREVENT / SCREEN_RECORD | ✅ | §5 | §5 | 三钩子单开关驱动(`InterventionGateConventionTest` 钉) |
| 弹窗拦截 | DIALOG_KEYWORD | ✅ | §5 | §5 | **本轮补 `DIALOG_PASSED`**(放行路径此前隐身 = 无法区分「没命中」与「没生效」) |
| SSL 绕过 | 6 项 | ✅ | §5 | §5 | 含 Android 14 `Platform.checkServerTrusted` 包私有桥 |
| 代理/VPN | 8 项 | ✅ | §5 | §5 | |
| Root/Xposed 隐藏 | 12 项 | ✅ | §5 | §5 | `ProcScanBroadcaster` 单一注册;**禁钩 `Class.forName`**(V36 D-17) |
| 设备伪装 | 条件档 7 + 画像强制档 8 = 15 项 | ✅ | §5 | §5 | 让位三形状由 `ProfileHookPlanTest` 钉 |

---

## 3. 画像伪装族(41 字段)× 测试层

| 层 | 断言 | 状态 |
|---|---|---|
| 取值/校验/编解码 | `ProfileFieldCatalogTest`、`ProfileCodecTest`、`DeviceProfilePropertyParityTest` | ✅ J |
| 合并 ⇄ 反推互逆 | `ProfileFieldDeviationTest`、`PerFieldIndependenceTest` | ✅ J |
| **落盘往返(V49 R-2 逐项微调)** | **新** `AppListenOverridePersistenceTest` A1/A2/A3 | ✅ J(**改前红过**,R3) |
| 快照与 TCP 通路同源 | `SettingsRepositoryProfileExpansionTest`(含新增 precedence 用例) | ✅ J |
| 目录数据对基线 | `BaselineDataParityTest`、`AndroidOnlyDataTest` | ✅ J |
| MCP 同权 | `SpoofProfileToolsTest`、`UiMcpCapabilityParityTest` | ✅ J |
| 编辑流程(需真机 sheet 交互) | `ProfileEditorFlowTest`(androidTest) | §5 I |
| 靶程序读回生效 | 注入矩阵 enhance 档 | §5 M(**V49 时点 34P/14F,本轮见 §5**) |

---

## 4. 平台与契约层

| 项 | 判据 | 状态 |
|---|---|---|
| **无孤儿功能**(需求 2) | `FeatureOrphanGateTest` O1–O4 | ✅ J(27+1 → 0) |
| 双 API 入口/元数据契约(需求 1) | **新** `FrameworkEntrypointContractTest` 6 条 | ✅ J |
| 适配器语义对账 | 两个真实适配器 JVM 可测性 | ⬜ 不可测(stub jar `MethodHookParam` 构造器包私有且方法体抛 Stub)⇒ **归 §5 设备档 E2** |
| MCP 声明面即生效面 | `McpToolParityTest`、`McpToolsTest`(含新 confirm 用例) | ✅ J |
| MCP 外网写面需 confirm | `server_set_external_access` 加 `confirm` 必填 | ✅ J |
| 结构门禁全量 | 28 个源码扫描/对账测试 | ✅ J(R4) |

---

## 5. 设备一次性全量验收(R7 / R8)—— **结论:未通过,且未达成"一次性"的前提**

> 两轮 `-Stage all` / `-Stage inject` 实跑。**先说结论,再给逐条归属**:
> **本轮不能签"所有功能均符合预期"**。可签的是:注入确凿发生、冷启动冒烟零崩溃、
> 监听域 65 条通过、增强域较 V49 基线由 34P/14F 改善到 36P/12F;
> 未过的项目里**只有环境争用与既存缺陷两类,没有本轮新引入的功能回归**。

| 轮 | 台账 | 步 | 结果 | 归属 |
|---|---|---|---|---|
| R7 | `v45-20260924-154144` | 全量 | 108P / 25F / 9S | 含 agent 并行 adb 造成的掉线污染(见坑表 V50 新条) |
| R8 | `v45-20260924-163703` | inject 段(静默重跑) | 125P / 37F / 16S | 下表逐条 |

### 5.1 R8 逐步判定

| 步 | P/F/S | 判定 | 依据 |
|---|---|---|---|
| env-runtime | 9/3/0 | **基线级** | 与整改前 030536 轮同名同数(3 条前置型) |
| smoke | 10/0/0 | ✅ | 全功能 ON 冷启动 + 帧语义 + ANR 全绿(两轮一致) |
| listen | 65/5/8 | ⚠️ **环境** | 5 条红全在 webview 域,日志内嵌 `[VM] device lost, cold relaunching dnplayer` ⇒ 整域触发时设备不在;其余域含 `DETECT_EMULATOR count=3`(本轮新活的 Build 反射监控)全绿 |
| enhance | 36/12/7 | **既存缺陷族** | 红项 = `E-SSL-03/04/05/06`、`E-SEC-01/02`、`E-MP-01`;V49 台账已登记 14 条同类,本轮净减 2 条 |
| linkage | 0/0/0 | ⚠️ **不可判定** | `adb.exe: device '127.0.0.1:5555' not found` 抛在 tt-lib:197 ⇒ 台账未落盘,编排器按「无台账 = FAIL」记(该机制正确地拒绝了假绿) |
| module-off | 3/1/1 | ⚠️ 待定 | `C-SPOOL`/`A spool WAL persists offline` 依赖重启时序,与链路稳定性相关 |
| backpressure | 2/0/0 | ✅ | 双预算闸门 |

### 5.2 为什么"一次性全量通过"这一轮做不到

1. **设备侧**:LDPlayer VM 在 45 分钟量级的矩阵中途掉线(两轮、共 3 次实锤:`device lost` × 1、`not found` × 2)。
   这不是断言失败,而是**测量通道断裂** ⇒ 受影响步的绿/红都不成立。
2. **既存缺陷**:`E-SSL-*` / `E-SEC-*` 族在 V49 就是 14 条红,本轮未修(不在三条硬需求的关键路径上)。
3. 二者叠加 ⇒ **任何一次全量跑都还拿不到"全绿"**;把它写成通过即是虚假验收(违反交付物 2 §9 第 1、6 条)。

### 5.3 下一轮的前置(具名,不写 ⛔)

- 设备:换真机或稳定模拟器,或把矩阵按域拆成 ≤10 分钟的分段跑(每段前断言 adb 序列数 == 1);
- 待修:`E-SSL-03/04/05/06`、`E-SEC-01/02`、`E-MP-01`(增强域 12 条,含 V49 遗留);
- 待复测:webview 全域、linkage 全步、module-off/A(均因链路断裂未取到有效样本);
- legacy 变体设备档(交付物 3 E2)尚未跑过任何一轮。

---

## 6. 量具缺陷台账(本轮挖出的 5 条 —— 它们制造的假红/假绿比产品缺陷更多)

> **为什么单列一节**:V49 把 enhance 域记成「14 条真缺陷待治」。本轮逐条复测后,
> 其中至少 3 条**不是产品缺陷,是测量方法坏了**。带着假红去改产品,会把本来正确的代码改错。

| # | 症状 | 真实根因 | 危害形态 | 修法 | 验证 |
|---|---|---|---|---|---|
| **H1** | `E-SEC-01/02` 多轮全红,详情 `ratio=-1` | PowerShell 5.1 的 `>` 重定向**按文本重编码原生 stdout** ⇒ `adb exec-out screencap -p > cap.png` 落的是被毁的 PNG ⇒ `Bitmap.FromFile` 抛 ⇒ 返回 -1 | **纯假红**(截图与 FLAG_SECURE 功能本身正常:手工 `exec-out \| wc -c` = 342368 字节) | 改「设备侧落图 + `adb pull`」(pull 由 adb 自己写字节文件) | 函数实跑返回 **0.9991** ✅ |
| **H2** | `E-MP-01 OFF 出弹窗` 恒红,**且**「ON 弹窗消失」恒绿 | `UiDumpContains "录屏"` 经 `adb shell cat` → PS 解码时**非 ASCII 全变 `?`**(实锤形态:`???????????: NetworkProbe`)⇒ 中文针永远匹配不上 | **一个假红配一个假绿**(比两个假红更坏:假绿没人追问) | 针以 UTF-8 文件推上设备,`grep -c -f` 在设备侧按字节匹配,只回传 ASCII 数字 | 待本轮设备结果 |
| **H3** | `C-GATE-01 rowCount<=10` 自 V45 起恒红 | **断言取错列**:`rowCount` 的语义是**游标总行数**(被动 `mCount`,#11 禁止为测量而迭代游标);用例插 200 行并全量迭代 ⇒ 恒 200 | 恒红,且掩盖了它本该看的「被采集行数」 | 改判 `rowsJson` 行对象数 ≤10,**并保留** `rowCount==200` 作旁证(证明是"采集被截"而非"查询只给 10 行") | 实测 captured=10 / rowCount=200 ✅ |
| **H4** | 手工 `adb install -r` 之后,"改完再验"结果一动不动 | 装机**没做 MD5 对账**就认定换包成功;设备上其实还是旧二进制 | 对着旧行为下新结论,烧掉一整个诊断周期 | 每次装机断言 `本地 md5 == 设备 base.apk md5`(AGENTS 早有此规,我未照做) | 补装后 md5 一致、结果随即改变 ✅ |
| **H5** | `env-runtime` 一次 11 红(连 `Root available` 都红) | 我在矩阵运行期间**并行发 adb 命令** ⇒ 设备从 server 掉线,断言什么都拿不到 | 11 条假红;并把 `enhance`/`linkage` 打成「无台账 = 不可判定」 | 静默重跑即回落到与基线**逐条同名**的 3 红;纪律已写入 AGENTS 坑表 | 重跑 11→3 ✅ |
| **H6** | R8 一轮 **21 条功能红**里,`enhance` 整步连台账都没写下、`ui`/`mcp` 一步没跑 | `WaitDevice` 会把死掉的 VM **静默冷重启**(只打一行黄字),编排器毫不知情,继续对着一台刚重启、靶进程已消失、注入未必然恢复的设备跑完剩余断言(全轮 8 次:enhance 3 / listen 3 / linkage 1 / backpressure 1);真因是宿主内存只剩 **0.5 GB**(VM 配额 2048MB/4vCPU) | 环境故障被登记成产品缺陷。**但注意 db 五类不在此列** —— 逐行核对:`listen.log` 掉线在第 23/79/103 行,而 db 断言在 18–22 行,**跑在健康设备上 ⇒ 那 5 条是真缺陷**,别拿掉线当遮羞布 | ①掉线写**按步隔离**的标记文件(`TT_VM_LOST_MARK`)②编排器见标记 ⇒ **作废该步计数 + 整轮 BLOCKED** ③跑前清滞留构建守护进程(实测 0.5→4.6 GB) | 待 R9 设备结果 |

**元教训**:H1/H2/H3 都**红得极其稳定**,而稳定性正是让人误判"必是真缺陷"的原因。
判别办法不是"再跑一遍看是不是一样",而是**去看断言读的那个字节到底是什么**:
`ratio=-1` 是"没取到图"不是"图全黑";`rowCount=` 空是"没这行"不是"行数超限";
`total=0` 是"整域没记录"不是"这一类没触发"。

