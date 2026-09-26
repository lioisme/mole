# V50 交付物 1 / 能力清单(全量分类 · 整理 · 命名)

> 生成日期:2026-09-24。口径 = **代码实测**，非文档转述。
> 每条计数均由本次审计执行 `./gradlew.bat :app:testModernDebugUnitTest --rerun --no-build-cache`
> (1174 用例 0 失败)后从源码逐条复核；与 AGENTS.md 冲突处，本文件为真，AGENTS.md 待更正(见 §6)。
>
> 事实源链(唯一，不可旁路):
> `core/model/CategoryTaxonomy.kt`(类别真身)→ `core/model/CapabilityMap.kt`(前后端投影)
> → `ui/settings/*` + `ui/apps/PerApp*SettingsScreen`(UI 分区)→ `mcp/tools/*`(Agent 面)。
> 门禁:`CapabilityMapTest` / `McpToolParityTest` / `UiMcpCapabilityParityTest` / `DeviceEnvKindTest`。

---

## 0. 三族定位(回答需求 1 的分类问题)

使用者表述的「信道监听 / 功能干预 / 画像伪装」三族，与代码的**双域闸**(`CapabilityDomain{LISTEN, FEATURE}`)
不是并列关系。定档结论:

| 族 | 载体 | 是否新增闸层 | 说明 |
|----|------|------------|------|
| **信道监听**(LISTEN) | `domain = LISTEN`，44 类 | 否 | 被动采集，产出记录，不改宿主行为 |
| **功能干预**(FEATURE) | `domain = FEATURE`，45 类 | 否 | 主动改结果/拦截，经 `featureOn(kind)` 双闸 |
| **画像伪装** | **FEATURE 域内的一个能力族**(DEVICE_SPOOF 组 + 画像库资产) | **否(禁止新增)** | 它是 FEATURE 的「值源」，不是第三个域 |

**为什么不立第三域**:域的机器含义 = 两页 UI 归属 + `settings_set` 双域闸 + `clearDomain` 按域清除
(AGENTS #23 / #30)。画像伪装没有独立于 `deviceEnvListen` 的闸，立第三域要新增一整套闸与清除语义,
等于把已经对齐的 89 行重新拆开——收益是呈现层的一个词，代价是契约层的双写。**呈现层单独立卡即可**
(现状已如此:全局资产页「画像库」+ 每应用功能页「设备伪装」)。

---

## 1. 实测基线数字(唯一口径)

> **V50 孤儿整改后现值 = 88 行(监听 43 / 功能 45)。**
> 保留"整改前实测"与"现值"两列:数字会变,证据链不能断。

| 对象 | 整改前实测 | **现值** | 证据 | 旧文档口径 |
|------|-----------|---------|------|-----------|
| 能力类别总数 | 89 | **88** | `CapabilityMapTest`(本次随整改更新) | AGENTS「83 行」 |
| 监听域 LISTEN | 44 | **43** | `CategoryTaxonomy.forDomain(LISTEN)` | AGENTS「44」 |
| 功能域 FEATURE | 45 | **45** | 同上 | AGENTS「39 / 37」两种旧值 |
| **孤儿类别** | 1 | **0** | 新门禁 `FeatureOrphanGateTest` O1 | 从未度量 |
| **孤儿操作枚举值** | 27 | **0** | 同上 O2 | 从未度量 |
| 记录类型 RecordType | 10 | **10** | `core/model/RecordType.kt:9-39` | 10 ✓ |
| `DeviceEnvKind` | 29 | **29** | `DeviceEnvKindTest` | 29 ✓ |
| 画像族 `PROFILE_*`(已接管) | 8 | **8** | `DeviceEnvKind.kt:96-117` | 8 ✓ |
| `ProfileKind`(画像层) | 20 | **20** | `ProfileFieldCatalogTest` | 20 ✓ |
| `ProfileField` | 41 | **41** | 同上 | 41 ✓ |
| 内置画像档 | 9 | **9** | `BuiltinDeviceProfiles.BUILTINS` | 9 ✓ |
| MCP 工具 | 50 | **50** | `McpTools.kt:53-60` + `McpToolParityTest:61` | 50 ✓ |
| Room 版本 | 15(14 条迁移) | **15** | `ModevDatabase.kt:57,433-439` | AGENTS #18「version=14」 |
| i18n 键 | 860 string + 13 plurals | **860 + 13**(四语等集) | `values*/strings.xml` 解析 | AGENTS「en 695 键、plurals 8」 |
| 机型库可达集 | 25 品牌 / 8,075 机型(apple 已剔) | 同 | `AndroidOnlyDataTest` | ✓ |
| 运营商库 | 1,597 条 | 同 | `assets/carriers.json` | ✓ |
| lint 基线 | 43 warning / 1 hint / 0 error | 见 §7 执行记录 | 本次实测 | ✓ |

---

## 2. 信道监听族(现值 43 类 · 10 记录类型)

呈现顺序 = 记录类型 → master → kind。`默认` 列 = `HookSwitch.DEFAULT`(内置推荐值，无全局 UI)。

### 2.1 算法监听 CRYPTO(master `cryptoListen`，探针 `AlgorithmProbe`，12 类)

| kind id | 现标签键 | 默认 | 钩子实现 | 抓取内容 |
|---|---|---|---|---|
| CIPHER | `kind_cipher` | on | CipherHooker | `Cipher.getInstance/doFinal/update` |
| MESSAGE_DIGEST | `kind_message_digest` | on | MessageDigestHooker | `MessageDigest.digest/update` |
| MAC | `kind_mac` | on | MacHooker | `Mac.doFinal` |
| SIGNATURE | `kind_signature` | on | SignatureHooker | `Signature.update/verify/sign` |
| KEY_PARAM | `kind_key_param` | off | KeyParamHooker | `SecretKeySpec/IvParameterSpec/PBE*` 构造 |
| KEY_GENERATION | `kind_key_generation` | off | KeyGenerationHooker | `KeyGenerator.generateKey` |
| KEY_FACTORY | `kind_key_factory` | off | KeyFactoryHooker | `*KeySpec ↔ Key` 转换 |
| KEY_STORE | `kind_key_store` | off | KeyStoreHooker | `load/getKey/setKeyEntry` |
| KEY_AGREEMENT | `kind_key_agreement` | off | KeyAgreementHooker | ECDH `doPhase` |
| RANDOM | `kind_random` | off | SecureRandomHooker | `nextBytes` |
| BASE64 | `kind_base64` | off | Base64Hooker | `android.util.Base64` 编解码 |
| ~~WEB_CRYPTO~~ | ~~`kind_webview`~~ | — | **已退役(V50 N1)** | 注入侧零发射的死行;JS 密码学由 `WebViewKind.JS_CRYPTO` 真实承担 |

### 2.2 数据库监听 DATABASE(master `dbListen`，`DatabaseProbe`，5 类)

| kind | 标签键 | 默认 | 钩子 | 备注 |
|---|---|---|---|---|
| OPEN | `kind_db_open`(「打开数据库」) | off | SqlOpenHelperHooker / SqlDatabaseHooker | **含 close 两操作**，命名不副实 → §5-N3 |
| QUERY | `kind_db_query` | on | SqlDatabaseHooker / SqlStatementHooker | 游标被动逐行(禁探测 API，#11) |
| DML | `kind_db_dml` | on | SqlDatabaseHooker | insert/update/delete |
| EXEC | `kind_db_exec` | on | SqlDatabaseHooker | execSQL |
| TRANSACTION | `kind_db_transaction` | off | SqlDatabaseHooker | begin/setTransactionSuccessful/end |

### 2.3 行为监听 BEHAVIOR(master `behaviorListen`，`BehaviorProbe` 五 Hooker，7 类)

| kind | 标签键 | 默认 | 钩点 | 覆盖洞 |
|---|---|---|---|---|
| FILE_READ | `kind_behavior_file_read` | off | `FileInputStream`、`Files.newInputStream/readAllBytes/readAllLines` | — |
| FILE_WRITE | `kind_behavior_file_write` | on | `FileOutputStream`、`Files.newOutputStream/write/copy`、`mkdir/mkdirs` | — |
| FILE_DELETE | `kind_behavior_delete` | on | `File.delete`、`Files.delete*` | — |
| FILE_RENAME | `kind_behavior_rename` | on | `File.renameTo`、`Files.move` | — |
| FILE_LIST | `kind_behavior_file_list` | off | 仅 `File.list/listFiles` | **NIO `Files.list/newDirectoryStream` 未覆盖** |
| ASSET_READ | `kind_behavior_asset_read` | off | `AssetManager.open/openNonAsset/openFd/list` | — |
| SHELL_EXEC | `kind_behavior_shell_exec` | on | `Runtime.exec`、`ProcessBuilder.start` | — |

微调项(非 kind):`switch_file_content_capture`、`capture_ignore_path`。
**被动红线**:内容预览仅限「随调用即可得」，流式句柄绝不触碰(#20)。

### 2.4 偏好监听 PREFS(master `prefsListen`，`PrefsProbe`，5 类)

OPEN(「读入配置文件」)/ GET / PUT / REMOVE / CLEAR。commit/apply 不挂钩(D5)。
`PrefsGetHooker` 默认关(NOISE_HIGH)。**实例索引双 LRU，禁反射读内部字段**。

### 2.5 WebView 监听 WEBVIEW(master `webviewListen`，`WebViewProbe` → 单 WebViewHooker，5 类)

PAGE_LOAD / EVALUATE_JS / JS_BRIDGE / DEBUG / JS_CRYPTO。
JS 侧通道:`hook/webview/`(探针 JS 常量 + JsBridge + Event + Mapper)。
注意:真正抓 WebCrypto 的是 `WebViewKind.JS_CRYPTO` —— 与 §2.1 死行 `CryptoKind.WEB_CRYPTO` 是两回事。

### 2.6 环境观测(跨域闸，UI 上在「监听页 · 环境监听」分区，7 类)

这 7 类的 **kind 归监听页，但其 master 归功能页** —— 属契约 #31④「跨域闸必须可见」的兑现点
(行内 `listen_kind_master_off_hint` 点名外来 master)。

| kind | 类型 | 默认 | master(在功能页) | 钩子 |
|---|---|---|---|---|
| REQUEST | NETWORK | on | `networkListen` | NetworkRequestHooker(okhttp `newCall` + `HttpURLConnection` Impl)+ DnsHideHooker(实为 DNS 观测) |
| DETECT_ROOT | DEVICE_ENV | off | `deviceEnvListen` | DetectionMonitorHooker(仅 `File.exists`) |
| DETECT_EMULATOR | DEVICE_ENV | off | `deviceEnvListen` | DetectionMonitorHooker(maps/stack/sensor/mac/Build)——**名实不符** §5-N2 |
| DIALOG_SHOW | UI_INTERACTION | on | `uiListen` | DialogLocationHooker(`Dialog.show`) |
| WIDGET_TEXT | UI_INTERACTION | off | `uiListen` | WidgetTextHooker(`TextView.setText`) |
| ACTIVITY_START | UI_INTERACTION | on | `uiListen` | ActivityTransitionHooker(`startActivity` 全重载) |
| ACTIVITY_RESUME | UI_INTERACTION | off | `uiListen` | ActivityTransitionHooker —— **实际挂在 `Activity.onCreate`** §5-N4 |

### 2.7 系统信息监听 SYSTEM(master `systemListen`，`SystemProbe`，3 类)

LOGCAT(`Log.v/d/i/w/e`，不含 `wtf`)/ APP_INFO(`getPackageInfo` → versionName/Code/minSdk/targetSdk)/
SIGNATURE(`getPackageInfo` → 证书 SHA-256)。

> 44 = 12 + 5 + 7 + 5 + 5 + 7 + 3。UI 入口计数已对账:监听页 6 master 行 + 37 类型 kind 行 + 7 环境行 = 44;
> 记录侧 10 条路由 + 10 个 `monitor-entry-*` 行。

---

## 3. 功能干预族(45 类 · 6 组，驱动源 `FeatureGroups`)

统一闸形态:`featureOn(kind)` = master ∧ kind(`InterventionGateConventionTest` + `FeatureGateBypassTest`)。
开关全关 = 零副作用(含安装期 Build 静态字段改写)。

| 组 | master | 类数 | kind → 钩子 → 动作 |
|---|---|---|---|
| **进程守护** PROCESS_GUARD | `processControlListen` | 3 | EXIT_INTERCEPT→ExitInterceptHooker(`System/Runtime.exit`、`killProcess` 前 `setResult(null)` 阻止退出);CRASH_PREVENT→CrashPreventHooker(包装 `setDefaultUncaughtExceptionHandler` + 吞 `dispatchUncaughtException`);SCREEN_RECORD→ScreenRecordHooker(`Window.setFlags/addFlags` **入参改写**剔 `FLAG_SECURE`)+ MediaProjectionHooker(自动放行授权 Intent)+ SurfaceBypassHooker(`lockCanvas`/`screenshot` 放行) |
| **弹窗关键词拦截** DIALOG_INTERCEPT | `uiListen` | 1 | DIALOG_KEYWORD→DialogKeywordInterceptHooker(`AlertDialog.show` 前命中 `activeKeywordRules` 才阻断;词表 = 全局库 ∧ 每应用子集)。**FEATURE 域唯一默认开的一类** |
| **SSL 绕过** SSL_BYPASS | `networkListen` | 6 | SSL_CONTEXT / SSL_TRUST_MANAGER / SSL_OKHTTP_PINNER / SSL_OKHTTP_VERIFIER / SSL_WEBVIEW / SSL_NETWORK_CONFIG → SSLBypassHooker(替换 TrustManager、`checkServerTrusted` 直通、pin/hostname 短路、WebView `proceed()`、NSC pin 绕过、**Android 14 `Platform.checkServerTrusted` 包私有桥**) |
| **代理/VPN 隐藏** PROXY_VPN_HIDE | `networkListen` | 8 | PROXY_SYSTEM_PROP / CONNECTIVITY / SETTINGS / OKHTTP → ProxyHideHooker;VPN_CAPABILITIES / INTERFACE / NETWORK_INFO / LINK_PROPERTIES → VpnHideHooker。**ProxyHideHooker 同时伪造 SSID/BSSID**(与画像 WIFI 族读口重叠 → §5-X3) |
| **Root/Xposed 隐藏** ROOT_XPOSED_HIDE | `deviceEnvListen` | 12 | ROOT_FILE(su/magisk 路径 `exists=false`)/ ROOT_EXEC(exec 抛 IOException)/ ROOT_PACKAGE_MANAGER(过滤 root 管理包)/ ROOT_PROPERTY(+ SettingsHooker)/ XPOSED_CLASS(`loadClass`/`findClass` 抛 CNFE，**禁钩 `Class.forName`** — V36 D-17)/ XPOSED_STACK(栈帧过滤)/ XPOSED_MAPS / SYSTEM_PROPERTIES / PROC_STATUS(TracerPid=0)/ PROC_MOUNT_INFO / PROC_CGROUP / PROC_NET(Frida 端口) → `ProcScanBroadcaster` 单一注册 + 行过滤 |
| **设备伪装** DEVICE_SPOOF | `deviceEnvListen` | 15 | **条件档 7**:EMULATOR_BUILD(惰性幂等补写)/ EMULATOR_FILE / EMULATOR_SENSOR / EMULATOR_NETWORK(android_id·MAC·deviceId)/ EMULATOR_TELEPHONY(哈希确定性假 IMEI·IMSI·ICCID·手机号)/ EMULATOR_CPU / EMULATOR_BATTERY。**画像强制档 8**(`PROFILE_*` 前缀):→ `Profile*Hooker`，值源恒 `feature.deviceProfile`(不再经 `SpoofProfiles`) |

---

## 4. 画像伪装族(41 字段 × 20 类 · 两套并存)

### 4.1 目标态:画像库 `spoof_profile`(Room v15)

取值 → 校验 → 编辑 → **选型**(V48)→ **逐项覆写**(V49)→ 传输 → 绑定 → 下发。
合并/反推唯一实现点 = `core/model/ProfileFieldDeviation`(`apply` 下发 / `between` 写盘，互逆由测试钉)。

### 4.2 接管边界(实至名归的分界，不是待办)

| 状态 | 类数 | 内容 |
|---|---|---|
| **已接管**(有钩子，值源=画像) | 8 族 / 覆盖 20 字段 | Build 身份、Build 版本、密度、核数、AndroidID、Telephony 标识、SIM、电池 |
| **未接管**(画像可编辑可下发但**零钩子**) | 12 族 / 21 字段 | NETWORK、WIFI、CELL、LOCATION、WIFI/CELL 定位隐藏、LOCALE、TIME_ZONE、APP_VERSION、SCREENSHOT、BLANK_CONTENT、APP_LIST |

UI 兑现 = `SpoofProfileLibraryScreen` 顶部 `profile_takeover_status_hint`，名单从 `DeviceEnvKind.PROFILE_*` **派生**(禁写死)。
执行体分裂点 = `ProfileHookPlan` + 各 `Profile*Hooker`;条件档让位三形状:
**整族**(核数)/ **按键**(电池只占 `BATTERY_PROPERTY_CAPACITY`)/ **按方法**(IMEI 不牵连手机号)。

### 4.3 逐字段选型现状(需求 4 的审计基线)

行级弹层现状(`ProfileFieldControls.kt:238-341`):枚举/槽位 → 可搜列表(恒留「改为手动填写」);
BOOL → 行内 Switch;其余 → 输入 sheet + 实时校验 + 就近随机。**成对捷径
(`pickDevice`/`pickCarrier`/`pickAndroidRelease`)只存在于画像编辑器，微调页明文排除。**

| 档位 | 字段 | 结论 |
|---|---|---|
| 行级已可选(15) | densityDpi、cpuCores、networkType、simCountry、language(半残)、timeZone、screenshotsFlag + 8 个 BOOL | 闭环(language 只给裸二字码 → N3) |
| **资产已有、缺行级入口(8)** | BRAND / MANUFACTURER / MODEL / DEVICE(→ `devices.json`)、SIM_OPERATOR / SIM_OPERATOR_NAME(→ `carriers.json`)、ANDROID_VERSION / SDK_INT(→ `ProfileValueSets` 37 对) | **需求 4 的主体工作量**，见交付物 2 WP-D |
| **开放域，手打正确(≈13)** | product / board / hardware(**恒不猜，上游不提供**)、fingerPrint、buildId、androidId、imei、phoneNum、wifiSSID、lac、cid、经纬度、versionCode/Name | 保持手打 + 校验 + 随机 |
| 遗留回落(**缺陷**) | IMSI / ICCID / 手机号前缀 / 假 SSID / MAC 前缀仍走 `SpoofProfiles`，对画像库主键**永远 miss** | §5-X2 |

---

## 5. 命名整改清单(需求 1 的「严谨命名」)

### 5.1 死行 / 死钩 / 名实相反(必须改代码，不是改文案)

| id | 对象 | 症状 | 建议定名 |
|---|---|---|---|
| **N1** | `CryptoKind.WEB_CRYPTO` | taxonomy 已登记、UI 有开关行、wire 有位，但 `hook/` **零发射**(已 grep 全仓确认)→ 勾选后「开关亮、永不出记录」 | **整行退役**(`CryptoKind` 条目 + `HookSwitch.webView` 位 + `kind_webview` 键)。JS 密码学由 `WebViewKind.JS_CRYPTO` 承担，不需两位 |
| **N2** | `DeviceEnvKind.DETECT_EMULATOR` | 关键词是 xposed/lspd/lsplant/frida/magisk/zygisk —— 抓的是**框架/hook 检测**，却申报「模拟器检测」 | 拆两类:`DETECT_HOOK_FRAME`(注入框架检测)+ `DETECT_EMULATOR`(模拟器检测);或改名「注入框架与模拟器检测」 |
| **N3** | `DetectionMonitorHooker.monitorBuildFields` | `BUILD_FIELDS = ["getFINGERPRINT","getMODEL",...]`，而 `android.os.Build` 这些是 **public static final 字段，无同名 getter** ⇒ `getMethod` 恒抛 ⇒ `?: continue` 咽掉 ⇒ **Build 字段监控自始未装钩**，且不走 `unresolvedHookPoints` 故门禁看不见 | 改读字段(`getStaticField`)或删除;并纳入 `unresolvedHookPoints` 观测面(交付物 2 WP-A3) |
| **N4** | `UiInteractionKind.ACTIVITY_RESUME` | 唯一发射点是 `Activity.onCreate`，操作枚举还叫 `ACTIVITY_ON_CREATE`(「创建」)⇒ 行说恢复、记录说创建 | 二选一:补装 `onResume`(枚举 `ACTIVITY_ON_RESUME` 已存在但永不发射)，或更名 `ACTIVITY_CREATE` |
| **N5** | `SqlKind.OPEN` 标签「打开数据库」 | 实际含 `CLOSE_DATABASE`/`CLOSE_HELPER` | 标签改「打开/关闭数据库」(四语) |
| **N6** | `DnsHideHooker` | 名为 Hide，doc 自陈「仅记录不干预」，挂 LISTEN `REQUEST` | 更名 `DnsObserveHooker` |
| **N7** | `XPOSED_MAPS` 等 `XPOSED_*` 前缀 | 掩蔽内容含 frida/magisk/zygisk，不只是 Xposed | 前缀统一 `FRAMEWORK_*`(注入框架)，与 N2 同轴 |

### 5.2 一层一名词(AGENTS #31① 的未尽事项)

| id | 症状 | 证据 | 建议 |
|---|---|---|---|
| **N8** | master 键名三种形态 | `{algorithm,database,behavior,prefs,webview,network}_monitor_master` / `listen_row_master_{system,ui,process}` / `device_env_master` | 统一 `master_{type.wire}`;键名改动 grep 面 = 键名(#31⑥)，须连 androidTest 一起改 |
| **N9** | FEATURE 域总开关字段名仍叫「监听」 | `uiListen`/`processControlListen` → `listen_row_master_ui`/`listen_row_master_process` | 语义上它们管弹窗拦截与进程守护，非监听;建议字段与键同步迁 `uiControl`/`processGuard`(带迁移，不破 JSON 兼容) |
| **N10** | 同一类型四层异名 | `RecordType.CRYPTO` / wire `crypto` / 路由 `records/algorithm/{pkg}` / 筛选键 `filter_type_crypto` / master 键 `algorithm_monitor_master`;DATABASE 更分裂(路径 `database` vs wire `db`) | **一个类型一个词**:向 `RecordType.wire` 收口(路由与资源键跟 wire)，禁再引第五种拼法 |
| **N11** | kind→标签键前缀五套形态 | `kind_*` / `kind_db_*` / `kind_behavior_*` / `kind_prefs_*` / `kind_wv_*` / `kind_net_*` / `kind_device_env_<ID>` / `kind_ui_<ID>` / `kind_system_<ID>` | 统一 `kind_{type.wire}_{id.lower()}`;新生成键即用新形，旧键一次性 `git mv` 式迁移 |
| **N12** | 组名不覆盖成员 | 「进程守护」含「解除录屏」(窗口策略，非进程)，且同受 `processControlListen` 闸 | 拆组或改组名「进程与窗口干预」 |
| **N13** | 类名碰撞 | `hook/probes/WebViewProbe.kt`(真探针) vs `hook/webview/WebviewProbe.kt`(JS 字符串常量库) | 后者更名 `WebViewProbeScript` |
| **N14** | 画像同义词五层 | `spoof_profile` 表 / `DeviceProfile` 正文 / `DeviceProfileEntry` 库行 / `SpoofProfiles` 遗留 / `BuiltinDeviceProfiles` 种子 | 术语表固化(本文件 §4 即为口径);MCP `profileFieldEntries` 与存储键 `profileOverrides` 在描述里互指 |
| **N15** | `filter_type_prefs` zh 值 = 「SharedPreferences」 | 与同行「算法/数据库/行为」不同阶(技术词混入产品词) | 改「偏好设置」 |
| **N16** | en `listen_kind_master_off_hint` 仍写 "master switch" | #31① 已退役「Masters」措辞(zh/ja/ko 已 0 命中，en 残留) | en 文案改为点名那个闸的实际名称 |
| **N17** | `ProfileField.kt:22-23` 注释与真值**相反** | 注释「零=强制允许，一=强制禁止」;`DeviceProfile.kt:35-39` SCREENSHOT_DENY=0/ALLOW=1 | 修注释(照注释理解 wire 配置会语义倒置) |
| **N18** | 标签键复用 | `ProfileKind.PROFILE_APP_LIST` → 键 `profile_kind_blank_app_list`(唯带 blank 前缀) | 统一 `profile_kind_*` |

> 说明:`op_*` 操作标签存在一对多(如 `op_ui_back_dismiss` 覆盖 dismiss/cancel/finish/onPause/onStop/onDestroy)——
> 这是**可接受**的呈现层聚合(操作身份由枚举唯一确定)，不列入整改，但需在词汇表登记，避免后续误判为缺陷。

---

## 6. 文档漂移更正(同轮必改，AGENTS.md / README.md)

| 位置 | 原述 | 实测 | 处置 |
|---|---|---|---|
| AGENTS 词汇表「能力表」 | 83 行 kind、冻结 83/44/39 | **89/44/45** | 更正(AGENTS #30 自己已写 89 ⇒ 文档内部自相矛盾) |
| AGENTS #23 | FEATURE 37 类 | 45 | 更正 |
| AGENTS #18 | `DbMigrationParityTest`(version=14…) | version = **15** | 更正 |
| AGENTS 词汇表「语言」 | en 695 键、plurals 8 | **860 + 13** | 更正(`scripts/v42_generate.mjs:107` 注释同样过期) |
| AGENTS #31① | 「12 个层级的上级闸:1 作用域 + 10 类型 master + 3 服务开关」 | 1+10+3 = **14** | 更正算式或计数 |
| `CapabilityMap.kt:4` | 「81 个类别」 | 89 | 更正 |
| `FeatureGroups.kt:4`、`FeatureConfigTools.kt:15` | 「37 个干预类别 / 6 组 37 类」 | 45 | 更正 |
| `DeviceEnvKind.kt:4` | 「扩展至 21 项」 | 29 | 更正 |
| `README.md:11` | 「81 类(44/37)」 | 89(44/45) | 更正 |
| `README.md:52` | 「注入侧逐字段仍全走 SpoofProfiles」 | WP-D1..D3 已接管 8 族 | 更正为 §4.2 边界 |
| AGENTS #26 | 危险工具句点名 `app_clear_records` | V45 T3.5 已删 | 更正 |
| AGENTS #26 | 「ModevMcp 仅落 method+tool+耗时」 | mcp 子树实际只有 start/bind/stop 三行日志，**无逐请求日志**(方向上更安全，但描述失实) | 更正 |

**防再犯**:这三处数字应各配一个「文档快照测试」——把 AGENTS/README 里的计数读出来与 `CapabilityMap`
派生值对账(镜像 `BaselineDataParityTest` 的做法)，否则数字永远靠口口相传过期。

---

## 7. 变更登记(每项能力动作落这里打卡 · 需求 2 的登记面)

> 规则:**做完一条就登记一条**,不攒到最后一次写。状态记号
> ✅ 已落码并通过门禁 / 🟡 部分完成 / ⬜ 未开始。
> 「反证」列 = 该条**改前为红**的测试(无红过的测试不算验收证据)。

| 编号 | 能力动作 | 状态 | 门禁/测试 | 反证(改前红) | 落码位置 |
|---|---|---|---|---|---|
| N1 | `CryptoKind.WEB_CRYPTO` 死行退役(监听 44→43) | ✅ | `FeatureOrphanGateTest.O1`、`CapabilityMapTest`、`CategoryTaxonomyTest` | O1 首跑点名该行 | `CryptoKind`/`HookSwitch`/`EffectiveSwitch`/`CategoryTaxonomy`/`RecordRow`/`KindIcons`/`KindColorTokens`/`CryptoFlowRoles` |
| N2 | `DETECT_EMULATOR` 名实不符(混含框架检测) | ⬜ | 需 kind 十站原子链 | — | 待排期(见交付物 2 WP-F 命名轮) |
| N3 | Build 字段监控死钩(`getMethod("getFINGERPRINT")`) | ✅ | 改钩 `Field.get` 反射读 + `unresolvedHookPoints` 点名 | 死钩由 O1/O2 扫描面暴露 | `DetectionMonitorHooker.monitorBuildFieldReflection` |
| N4 | `ACTIVITY_RESUME` 名实相反 | ✅ | `FeatureOrphanGateTest.O2` | O2 列出 5 个生命周期恒空操作 | `ActivityTransitionHooker.hookLifecycle`(onCreate/onResume/onPause/onStop/onDestroy/finish) |
| N5 | `SqlKind.OPEN` 标签不含 close | ⬜ | 四语键 | — | 待排期 |
| N6 | `DnsHideHooker` 更名为观测 | ⬜ | — | — | 待排期 |
| N7 | `XPOSED_*` 前缀改 `FRAMEWORK_*` | ⬜ | 需十站链 | — | 待排期 |
| N8–N18 | 一层一名词类(master 键形、类型四层异名、kind 键前缀等) | ⬜ | `DomainVocabularyTest` 扩面 | — | 待排期(跨四语 + 路由 + androidTest,单列一轮) |
| S1 | **每应用画像微调落盘不回读** | ✅ | 新门禁 `AppListenOverridePersistenceTest` A1/A2/A3 | **A1+A3 在去掉解析臂后实测双红**(已验证),A3 为真 JSON 往返 | `AppListenOverride.fromJson` + `FIELD_PROFILE_OVERRIDES` |
| S2 | Agent 可单方把绑定翻到 0.0.0.0 | ✅ | `McpToolsTest` confirm 缺值分型 | 加 confirm 前后向用例 | `ServerTools.serverSetExternalAccess` |
| S4 | `resolvedProfile` 全无写入方 | ✅ | `SettingsRepositoryProfileExpansionTest`(新增 precedence 用例) | 原「carried 优先」用例改前红 | `SettingsRepository.snapshotFor` + 画像库变更刷新 |
| OPS-1 | 7 个恒空操作退役(GETPROP / CLASS_FOR_NAME / OPEN_MAPS / GET_DEVICE_ID / GET_IMEI / ROOT_CHECK_API / CONTENT_QUERY_SETTINGS) | ✅ | `FeatureOrphanGateTest.O2` | O2 逐条点名 | `DeviceEnvOperation`(逐条附替代读口) |
| OPS-2 | CPU_PROC_INFO / CPU_SYSCONF 退役 | ✅ | 同上 | 同上 | `DeviceEnvOperation` |
| OPS-3 | 11 个恒空操作补发射(EVALUATE_JS_LEGACY / DIALOG_PASSED / DIALOG_CANCEL / WIDGET_SET_TEXT_RES / ACTIVITY_START_OPTIONS / ENCRYPT / DECRYPT / APP_PERMISSIONS / SIGNATURE_READ_V2 / LOG_FATAL / ROOT_CHECK_EXEC) | ✅ | 同上 | 同上 | `WebViewHooker`/`DialogKeywordInterceptHooker`/`DialogLocationHooker`/`WidgetTextHooker`/`CipherHooker`/`AppInfoHooker`/`SignatureMonitorHooker`/`LogcatHooker`/`RootExecHooker` |
| — | **孤儿门禁本体**(类别级 + 操作级 + 反向未登记 + 双域完备) | ✅ | `FeatureOrphanGateTest` O1–O4 全绿 | 首跑 1+27 红 | `app/src/test/.../architecture/FeatureOrphanGateTest.kt` |
| — | **框架入口/元数据契约门禁** | ✅ | `FrameworkEntrypointContractTest` 6 条 | 改前 modern `module.prop` 缺 `id`(实测) | `app/src/test/.../framework/` |
| HOOK-1 | db 五类恒 `count=0` —— **结论:不是产品缺陷**(R6 普查铁证) | ✅ 归因闭环(缺陷改判到量具侧,见 H6/H7) | 设备此刻已不可达时 `Q` 把 `adb.exe: device … not found` **当数据行**返回,`KindCountsAll` 因计数列非数字而静默跳行 ⇒ 整表读成零记录 ⇒ 五类恒 0。R3 红 / R10 绿 / R6 又红全部由掉线时点解释 | 普查块直接把那一行原文打出来(`!! L/db 整域零产出 ⇒ 打证据` 之下不是数据库行) | 修在 `tt-lib.ps1 KindCountsAll`(报错行不再被吞,记 `$script:LAST_QUERY_ERROR`)+ `20-run-listen.ps1`(不可达时详情打「计数不可信:<原始报错>」)。`SqlDatabaseHooker.install` 拆区域 + 惰性游标补装**保留**:它消除的是「游标解析失败连坐整个 SQLiteDatabase 表面」这一**确定存在**的静默形态,只是它并非本轮红字的成因 |
| HOOK-2 | `ProfileBatteryHooker` 两条读路互相压制(`audited` 记 `Int?` 只留最后一个操作) | ✅ | `ProfileDeviceValueHookerTest.battery_touchesOnlyCapacityAndTheTwoBatteryExtras` | 改前第二条审计恒丢 | `ProfileBatteryHooker.audited`(改 `Set<String>` 键 `"$operation:$level"`) |

### 7.1 仍登记的未决项(不许被"门禁绿"掩盖)

| 项 | 状态 | 为何仍未闭环 |
|---|---|---|
| 画像未接管的 12 族 / 21 字段 | ⬜ 逐批接管中(V49 WP-D4..D7) | 交付物 2 WP-D 排期 |
| 组件工厂化(需求 2 的 UI 侧) | ⬜ | 交付物 2 WP-C,12 项 |
| 性能度量门禁(需求 3 的 Jank 判据) | ⬜ | 交付物 2 WP-B B0,需新建 macrobenchmark 模块 |
| legacy 变体设备验收 | ⬜ | 交付物 3 E2,需 LSPosed/EdXposed 实机档 |
| 四语命名收敛(N8–N18) | ⬜ | 跨 `values*/strings.xml` + 路由 + androidTest 键名(AGENTS #31⑥ 的 grep 面) |
