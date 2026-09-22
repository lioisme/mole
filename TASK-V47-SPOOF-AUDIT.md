# V47 · 设备画像伪装重建 —— 基线全量审计与差距登记册

> 配套执行文档:`TASK-V47-SPOOF-PLAN.md`(设计规格 + 工作包 + 压测矩阵 + 执行台账)
> 审计日期:2026-09-22 · 基线:`third_party/Guise_Reborn-main`(Guise Reborn,GPL-3,libxposed API 102,compileSdk 37)
> 被审对象:`com.lioisme.modev` master 工作树(V46 沉淀后,含并发改动)

---

## 0. 证据约定与阅读方式

| 标记 | 含义 |
|---|---|
| ✅ | 本文档作者**亲自逐行读源复核**(本轮 12 处关键断言已实测:Guise `ModuleConfig` 40 字段全清单、`HookFeature` 13 组枚举、内置模板 `seedId` 仅 2 项、Modev `DeviceEnvKind` 恰 21 枚举、`ModevDatabase` v14 + 13 条迁移、`DeviceCpuHooker#readText` 死钩、`EmulatorNetworkHooker` `02:42:ac:11:` 前缀、`DeviceEnvRecordMappers` `maskedValue = null`、靶程序 `TestCases` 128 用例 ID / `L-DEV-01..21`、`devices.json` 许可证声明、`guise-test` 探针存在性、`settings/profiles` 路由落点) |
| ◐ | 子代理审计给出的 `file:line`,**结论方向可信、行号须实施时回访**(见 §9 复核清单) |
| G-xx | 基线能力条目(Gap 需求源) |
| M-xx | Modev 现状条目 |
| GAP-xx | 差距条目(基线有、Modev 无/半) |
| DFC-xx | Modev 既有缺陷条目(与基线无关,但重建必须一并修) |
| DEBT-xx | 验证债条目(无测试/矩阵 SKIP/期望表缺失) |
| R-xx | 需求本体条目(用户 2026-09-22 定档) |

**判定口径纪律**:本登记册每条差距的判定依据 = **目标 App 实际可见的值**(行为本体),不以「有没有某个类/某个组件名」的代理指标宣布达标。凡「代码里存在但从不生效」者一律按**不存在**计(本案三处实锤:DFC-01 `/proc/cpuinfo`、DFC-02 `Build.getMODEL()` 系、DFC-03 `maskedValue` 不落库)。

---

## 1. 需求本体与定档

| ID | 需求 | 定档(用户 2026-09-22 确认) |
|---|---|---|
| **R-1** | 功能范围 = 基线 Guise 设备画像伪装能力 **100% 复刻** | **全 40 项逐字段复刻**,含空白通行证(通讯录/图/视/音/应用列表)、定位伪装与两类定位来源隐藏、截图双向、语言/时区/DPI、目标应用版本号伪装 |
| **R-2** | 内置 **三套经典设备画像模板** | **Pixel 7 Pro + Xiaomi 13 + Galaxy S23 Ultra**(三大 OEM 血统:AOSP 系 / HyperOS 系 / OneUI 系);基线**无对应物**(见 G-26),属 Modev 净新增设计 |
| **R-3** | 支持用户 **DIY 画像** | 画像库为一等公民实体(可命名/可编辑/可复制/可绑多应用/可导入导出);**不做**「绑定后再做每应用字段覆写」的三态叠加(决策 D-3) |
| **R-4** | 全量压力测试 = 常见设备信息检测软件 **+** 自建靶程序 | **靶程序逐字段逐 API 路径期望表 = 硬门禁(0 FAIL / 0 SKIP)**;三方检测 App = **观测档**(截图 + uiautomator 判定归档,非门禁) |
| **R-5** | 测试不达 100% 则归因修复后重跑,**直到 100% 通过** | 循环协议见 PLAN §5.6;期望表由 `ProfileFieldCatalog` **派生**,禁手抄,改期望须留裁决记录 |
| **R-6** | 基线用法 | **先整体复制跑通验证**(代码与数据资产均按基线实现落地),许可证合规列为**显式未决项 D-9**,不静默丢弃 |
| **R-7** | 文档质量 | 达苹果/谷歌验收标准:可追溯、可执行、每条有出口准则与证据约定 |
| **R-8** | 不降级既有能力 | Modev 独有超基线能力(Root/Xposed/Frida/proc 掩蔽、传感器、cpuinfo、热路径门控、记录侧观测、MCP 面)**全部保留**,基线缺口不构成删除理由 |

**硬事实前置**(影响 R-4 可达性,不粉饰):

1. momo(`io.github.vvb2060.mahoshojo`)在检测到 **Zygisk pre-specialize** 时**中止全部后续检查**(`TASK-MOMO-ADBTESTING.md:99-112` 已文档化)。该信号来自原生层,任何 Xposed 模块级掩蔽都无法消除;要全绿需 KernelSU/APatch + Shamiko 或更早注入点 —— 属刷机方案,不属本模块能力边界。**故「三方检测 App 100% 全绿」不可承诺**,R-4 的定档已与用户确认对齐。
2. 本轮审计时 `adb devices` **零设备在线**;最近一轮验收 `docs/test-reports/v45-20260920-085251.md` = **FAIL,卡 preflight,`reachable:false`**;上一轮 `stage=all` = **FAIL 130/22 且 inject/mcp 档 SKIPPED**。→ PLAN WP-0 必须先把设备环境拉回可验收态。
3. 靶环境 = LDPlayer14 **x86_64 / Android 14 / API 34 / 机型 `25102RKBEC`**,且**已被预伪装**;而基线是 **arm64-only / minSdk 29 / targetSdk 37**。画像伪装值与宿主 SDK 34 的一致性校验必须进期望表(见 WP-8 `CONSISTENCY-*`)。

---

## 2. 基线 Guise 能力全量清单

### 2.1 配置模式:恰好 **40 个可伪装参数** ✅

事实源 `app/src/main/java/com/houvven/guise/xposed/config/ModuleConfig.kt:10-51`(`@Serializable`,kotlinx JSON,`ignoreUnknownKeys = true`)。语义核心:**「字段缺省」即「该项不钩」,模型内无 per-field 布尔开关。**

| # | 字段 | 类型 | 缺省(=不钩) | 生效消费者 ◐ |
|---|---|---|---|---|
| 1 | `brand` | String | `""` | `Build.BRAND` 静态字段 |
| 2 | `manufacturer` | String | `""` | `Build.MANUFACTURER`(**独立于 brand**,留空不改) |
| 3 | `model` | String | `""` | `Build.MODEL` |
| 4 | `product` | String | `""` | `Build.PRODUCT` |
| 5 | `device` | String | `""` | `Build.DEVICE` |
| 6 | `board` | String | `""` | `Build.BOARD` |
| 7 | `hardware` | String | `""` | `Build.HARDWARE` |
| 8 | `buildId` | String | `""` | `Build.ID` |
| 9 | `androidVersion` | String | `""` | `Build.VERSION.RELEASE` |
| 10 | `sdkInt` | Int | `-1` | `Build.VERSION.SDK_INT` |
| 11 | `densityDpi` | Int | `-1`(生效域 **72..1000**) | `DisplayMetrics` 三值 + `Configuration` 三 dp 值 |
| 12 | `networkType` | Int | `NET_UNHOOK(0)`;`-1`=隐藏活动网络 | `NetworkInfo`/`NetworkCapabilities`/`ConnectivityManager` |
| 13 | `fingerPrint` | String | `""` | `Build.FINGERPRINT` |
| 14 | `wifiSSID` | String | `""` | `WifiInfo.getSSID`(**自动补首尾双引号**)+ 命中的 `ScanResult.SSID` |
| 15 | `wifiBSSID` | String | `""` | `WifiInfo.getBSSID` + 命中 `ScanResult.BSSID` |
| 16 | `wifiMacAddress` | String | `""` | `WifiInfo.getMacAddress` |
| 17 | `simOperator` | String | `""`(硬校验 **5..6 位纯数字**,否则整段放弃) | PLMN → 拆 mcc/mnc 写 TelephonyManager×4 + CellIdentity/SubscriptionInfo 四族 |
| 18 | `simOperatorName` | String | `""` | 运营商名 6 方法 |
| 19 | `simCountry` | String | `""` | 国家 ISO 5 方法 |
| 20 | `imei` | String | `""` | `getImei` 全重载 + `getPrimaryImei` + `getDeviceId` 全重载 + **TAC = `imei.take(8)`** |
| 21 | `phoneNum` | String | `""` | `getLine1Number` + `SubscriptionManager.getPhoneNumber*` + `SubscriptionInfo.getNumber` |
| 22 | `androidId` | String | `""` | `Settings.Secure/System#getStringForUser` 中 name==`ANDROID_ID` |
| 23 | `lac` | Int | `-1` | LAC/TAC/NetworkId → 7 个基站类 |
| 24 | `cid` | Int | `-1` | CID/CI/NCI/BaseStationId → 7 个基站类 |
| 25 | `language` | String | `""`(`zh_CN`/`zh-Hans`) | 进程默认 `Locale`/`LocaleList`/`Configuration`/per-app locales |
| 26 | `timeZone` | String | `""`(IANA,`ZoneId.of` 失败即放弃) | 进程默认 `java.util.TimeZone` |
| 27 | `longitude` | Double | `-1.0` | `Location.getLongitude` |
| 28 | `latitude` | Double | `-1.0` | `Location.getLatitude` |
| 29 | `randomOffset` | Boolean | `false` | 每进程启动叠 `(rand-0.5)*0.0001` ≈ ±5.5m |
| 30 | `makeWifiLocationFail` | Boolean | `false` | 隐藏 Wi-Fi 定位来源,**优先于 14/15/16** |
| 31 | `makeCellLocationFail` | Boolean | `false` | 隐藏基站定位,**优先于 23/24** |
| 32 | `versionCode` | Int | `-1` | 目标应用自身 `PackageInfo.longVersionCode` |
| 33 | `versionName` | String | `""` | 同上 `versionName` |
| 34 | `batteryLevel` | Int | `-1`(UI 校验 0..100) | `BatteryManager.getIntProperty(CAPACITY)` + `Intent.getIntExtra("level"/"scale")` |
| 35 | `screenshotsFlag` | Int | `UNHOOK(-1)`;`0`=强制禁止,`1`=强制允许 | `Window`/`WindowManagerImpl`/`Activity` FLAG_SECURE 双向 |
| 36 | `passContacts` | Boolean | `false` | 通讯录查询返回**保留列结构的空 `MatrixCursor`** |
| 37 | `passPhoto` | Boolean | `false` | `media/external/images` 空 Cursor |
| 38 | `passVideo` | Boolean | `false` | `…/video` 空 Cursor |
| 39 | `passAudio` | Boolean | `false` | `…/audio` 空 Cursor |
| 40 | `passApplications` | Boolean | `false` | 包列表只见自身 + `android` + 系统/更新系统应用 + APEX |

非参数位:`packageName`(`@Transient`,运行时注入)、`enabled`(注入进程唯一总闸)。
归一化两式:`hasSameParameters()` / `parameterSignature()`(`ModuleConfig.kt:58-62`,忽略包名与启停)——**这是「一份画像被哪些应用使用」的唯一反查键**,基线用它替代外键。

### 2.2 Hook 面:`HookFeature` **13 组** ✅ → 11 个 Hooker 文件 → 被钩约 **30 个类**

`xposed/HookPlan.kt:12-26`(枚举本体 ✅):`BATTERY, LOCALE, TIME_ZONE, LOCATION, CELL_LOCATION, NETWORK, OS_BUILD, DISPLAY_DENSITY, SCREENSHOTS, UNIQUE_ID, BLANK_PASS, APPLICATION_LIST, APP_VERSION`。

组 → 类映射要点(◐ 细节):

| 组 | 承载类 | 关键面 |
|---|---|---|
| OS_BUILD | `OsBuildHook` | `Build` 9 字段 + `Build.VERSION` 2 字段,**反射写静态字段**(一次性、进程级、非门控基类) |
| UNIQUE_ID | `UniquelyIdHook` | `Settings.Secure/System#getStringForUser` 漏斗(含 WebView 独立进程)+ `TelephonyManager` IMEI/TAC/deviceId 全重载 + 号码三族 |
| NETWORK | `NetworkHook`(内联驱动 `WifiHook`+`SimHook`) | `NetworkInfo` 4 方法 + `NetworkCapabilities.hasTransport` + `ConnectivityManager` 3 方法置空 |
| CELL_LOCATION | `CellLocationHook` | 7 基站类的 LAC/CID,含 `CellIdentityNr#getNci → cid.toLong()` |
| LOCATION | `LocationHook : LocationHookBase` | `Location` 经纬度、两个 `ListenerTransport#onLocationChanged` 实参递归改写、**高德 `AMapLocation` 专案**、`getLastLocation` 合成(provider=GPS/accuracy=10) |
| — | `LocationHookBase` | 基站定位隐藏面:`getCellLocation`/`getAllCellInfo`/`requestCellInfoUpdate`(**回调照发空集,不改调用方语义**)/`listen` 清位/`registerTelephonyCallback` + 6 类 `CellIdentity` 逐方法存在性探测 |
| BLANK_PASS | `BlankPass` | `ContentResolver.query` 与 `ContentProviderClient.query` **全重载**,按 authority+path 判定,返回保留请求列的空 Cursor |
| APPLICATION_LIST | `ApplicationListPass` | `ApplicationPackageManager` + **`IPackageManager$Stub$Proxy`**(Binder 代理层)+ `LauncherApps`;**LIST 16 方法过滤 + INFO 17 方法抛 `NameNotFoundException`**;`ParceledListSlice` 三态原地降级;`visiblePackages` 惰性记忆集保证「列不到 ↔ UID 反查」不自相矛盾 |
| DISPLAY_DENSITY | `DisplayDensityHook` | `Resources.getDisplayMetrics/getConfiguration` + `Display.getMetrics/getRealMetrics`,**保留原 fontScale**,按 dpi 比反算三组 dp 值 |
| LOCALE | `LocalHook` | 设置进程默认 Locale + `Resources.getConfiguration` 原地改 + `LocaleManager`(API≥33 硬闸) |
| TIME_ZONE | `TimeZoneHook` | **零 Hook**:仅 `TimeZone.setDefault` + **主 Looper post 后再设一次**(对抗 ActivityThread 覆写时序) |
| SCREENSHOTS | `ScreenshotsHook` | 双向。**按位判定**清 FLAG_SECURE(修原版整值比较漏过) |
| APP_VERSION | `BuildConfigHook` | `getPackageInfo` 四形态;**仅当查询包名==自身**;API≥33 分支;取代旧 `BuildConfig.VERSION_*` static-final 篡改(API 37 收紧) |

**before/after 拦截器语义**(`ktx-xposed/.../MethodHook.kt:30-47` ◐):before 若已 setResult → **返回该值短路原方法**;否则 `chain.proceed(param.args)`(实参改写靠带参 proceed);after 恒 proceed 后**优先返回 `param.result`**。→ 与 Modev V38「after-setResult 曾被拦截器吞」是同一坑,基线从设计上避开。

### 2.3 启停与失败隔离(四层闸 + 编译期裁剪)✅ 结构 / ◐ 行号

- **L0 框架作用域**:应用列表勾选是唯一启用节点,同时双向校准 LSPosed scope(`reconcileScope`);**空配置自动取消勾选**。
- **L1 进程/包过滤**:`!isFirstPackage` 或包名==自身 ⇒ 直接 return(**基线自身永不钩**);目标包**所有子进程**共享同一份配置(键只有包名)。
- **L2 总开关**:`enabled=false` ⇒ 零 Hook。
- **L3 `activeHookFeatures()` 编译期裁剪**:**未配置的组连对象都不构造**;互斥裁决在此(隐藏优先于伪装)。`HookPlanTest` 冻结「默认配置 = 零 Hook」。
- **L4 钩子内部二次门控**(纵深)。
- **失败隔离三层** `runXposedCatching`(组级 / 单方法级 / 字段级),类与方法存在性一律 `findClassIfExists`/`findMethodExactIfExists` 显式探测 ⇒ **无「装不上就拖崩目标进程」路径**。
- **生效需冷启动**:注入进程只在 `PackageReady` 读一次配置,**无偏好变更监听、无热更新**。停应用三级回退:ROOT `am force-stop` → Xposed `hotReload` 请求自杀 → 跳系统应用详情页。

### 2.4 值生成器的「真实形态」纪律(值得全盘继承)✅/◐

| 生成物 | 形态约束 |
|---|---|
| MAC | 6 字节随机 + `octet[0] = (b or 0x02) and 0xFE` ⇒ **合法本地管理单播**,不冒充厂商 OUI |
| IMEI | 14 位数字 + **Luhn 校验位** = 15 位合法 |
| Android ID | 16 位小写 hex |
| 手机号 | `"1" + 10 位数字` |
| Build ID | 前缀按大版本映射(10→QP1A、14→UP1A、15→AP3A、16→BP2A…),格式 `PREFIX.yyMMdd###` |
| Fingerprint | `brand/product/device:release/buildId/serial:user/release-keys`,各段空白与 `/`/`:` 换成 `_` |
| 坐标 | `lat∈[-90,90]`、`lon∈[-180,180]`,6 位小数 |
| **一键随机** | 从**同一台设备 + 同一 Android 世代 + 同一运营商**派生整组值(品牌→制造商、机型→device、版本→sdkInt、BuildID→Fingerprint 同源于同一段随机),**不各字段独立抽样** |

一键随机**不生成**:`board`/`hardware`/`product`/`densityDpi`/`language`/`lac`/`cid`/`versionName`/`versionCode`/`screenshotsFlag`/四个 `pass*`/两个 `make*Fail`/`randomOffset`。

### 2.5 画像/模板/目录数据层

| 条目 | 事实 |
|---|---|
| G-25 模板存储 ✅ | Room(`TemplateDBHelper.kt:10-11`,`version = 4`,文件 `template.db`),实体 `Template(id,name,description,type,configuration,createTime,updateTime,packageName)`;`configuration` = **整份 ModuleConfig JSON blob**(非一列一字段);`fallbackToDestructiveMigration(dropAllTables=true)`(仅 `BundledTemplateState` 那条是手写无损迁移) |
| G-26 **内置模板只有 2 个,且零设备画像** ✅ | `BundledTemplateCatalog.kt`:`seedId = "blank-passport"`、`"force-screenshots"` —— 无 pixel/xiaomi/samsung 任何档位。**结论:R-2 的「三套经典画像」在基线中不存在,是净新增设计;基线能提供的只是字段模式 + 机型目录 + 生成器** |
| G-27 内置模板账本 | 稳定 seedId + 独立内容 version + **SHA-256 内容指纹**(零化 id/时间戳后计算)+ 删除墓碑 + `managed` 位;同参数用户模板被**收养**而非重复创建;用户改过即不再覆盖 |
| G-28 机型目录 ✅ | `assets/devices.json` **1,938,874 B / 26 品牌 / 8,533 型号**;`{schemaVersion, sourceRevision, brands[{key,displayName,buildBrand,manufacturer,models[{model,name,device,code?,alias?,type,versionName?}]}]}`;消费时 `resolvedDevice = alias ?: device ?: code`,**绝不猜 `PRODUCT`**;来源 **KHwang9883/MobileModels,CC BY-NC-SA 4.0(禁商用)** |
| G-29 运营商目录 ✅ | `assets/carriers.json` **1,597 条 / 226 国家 / 233 MCC**;选一条同时回填 `simOperator+simOperatorName+simCountry`;来源 pbakondy/mcc-mnc-list,**MIT** |
| G-30 预设 ✅ | `res/raw/presets.json`:androidVersions 到 API 37、sdkLevels、densityDpi 13 档(120..640)、networks 6 项、languages 13 项;时区走运行时 `ZoneId.getAvailableZoneIds()` |
| G-31 编辑器 | **单一 `ConfigEditorView` 三宿主**(新建模板 / 编辑模板 / 每应用实时配置);四类输入原语:`InputBox`(清除)、`OperateInputBox`(预设列表按钮)、`RandomInputBox`(就近随机)、`ContainerSwitch`;选择器 = `ModalBottomSheet` + 搜索框 + 双列错落栅格 AssistChip;**无「读本机当前值」入口**(只有随机/预设/清空) |
| G-32 部署语义 | **快照部署**:应用模板 = 把画像 JSON 复制进该包 prefs 键,不存模板外键;「本模板被哪些应用使用」= `parameterSignature` 反查(派生值,非引用) |
| G-33 模板两型 | `COMMON`(多选应用装配页)/ `EXCLUSIVE`(绑定单包,一键确认启用) |
| G-34 导入导出 | 信封 `TemplateBundle{schemaVersion=1,exportedAt,templates[]}`;通道 = JSON 文件(SAF/MediaStore)/ **仅 HTTPS** URL(2,000,000 字符+字节双上限)/ 相机扫码 / 相册图扫码;QR = `guise1:` + base64url(deflate BEST_SPEED),解压 ≤2MiB、压缩 ≤512KiB,**单码无分片**;导入 `OnConflictStrategy.IGNORE` + `distinctBy id` |
| G-35 配置下发 | libxposed **Remote Preferences 只读镜像**,先写本地 prefs 再 `replacePreferences` 全量覆写;首次升级双向迁移且「远程有内容则远程优先」 |
| G-36 R8 反检测契约 | Release 校验 `java_init.list` 入口未改名 + 4 个私有方法名**必须**被混淆 + 4 个 config 类名**必须**被混淆 ⇒ 目标进程内配置类二进制层不可命名匹配 |

### 2.6 基线自认边界(**不构成 Modev 新增需求,但构成 R-8 保留清单**)✅ grep 复核为零覆盖

`SystemProperties`/原生属性、Root/Magisk/su、Xposed 框架检测规避(类探测/栈帧/`/proc/maps`)、传感器、蓝牙、`NetworkInterface`/本机 IP/DNS、`Build.getSerial`/`ro.serialno`、MEID/IMSI/ICCID/GAID/MediaDrm、`Build.TAGS/USER/TYPE/SUPPORTED_ABIS` 与 `VERSION.INCREMENTAL/SECURITY_PATCH/CODENAME/BASE_OS`、GNSS 卫星态与 NMEA、系统时钟、分辨率/刷新率/刘海、HealthStore、签名/`sourceDir`/安装来源、每进程差异化配置、配置热更新。

> 基线另有**两处能力残缺**:①`SCREENSHOTS_DISABLE(0)` 在 UI 上不可达(编辑器单布尔折叠为 ENABLE/UNHOOK);②IMEI 单值(双卡同值,UI 已声明)。→ Modev 重建须各自补齐(见 GAP-13、GAP-14),这是「100% 复刻」中基线自身欠的部分,已在 PLAN 决策 D-8 定档。

---

## 3. Modev 现状清单(重建的落点)

### 3.1 「画像」今天不是画像 ✅

`core/model/SpoofProfiles.kt`:`BUILTINS` 三个**编译期常量**(auto / pixel7pro / xiaomi13),`SpoofProfile` 仅 9 个字段段(`buildFields` 13 键、`roProperties` 14 键、4 个 telephony 前缀、`fakeSsid`/`fakeBssid`、`cpuCores`)。覆写语法是扁平字符串表(`build.<FIELD>` / `ro.<prop>` / `telephony.*` / `wifi.*` / `cpu.cores`)。

**真正消费画像的钩子点只有 4 个**:`EmulatorBuildHooker`、`DeviceSysPropHooker`、`SystemPropertiesHooker`(ro 组)、`DeviceTelephonyHooker`(前缀)+ `DeviceCpuHooker`(核数)+ `ProxyHideHooker`(wifi,但门控挂在**网络域** kind 上)。传感器名/电池三元组/MAC 前缀/cpuinfo 模板/Settings 值表**全是各 hooker 内的硬编码常量** —— AGENTS #23「机型字面量唯一事实源 = `SpoofProfiles`」**只兑现 4 处**。

### 3.2 钩子面现状 ✅ 规模 / ◐ 细节

`DeviceEnvKind` **21 项**(2 FEATURE master 之外的 kind 全表,已实测枚举计数):

- **DEVICE_SPOOF 7**:`EMULATOR_BUILD / EMULATOR_FILE / EMULATOR_SENSOR / EMULATOR_NETWORK / EMULATOR_TELEPHONY / EMULATOR_CPU / EMULATOR_BATTERY`
- **ROOT_XPOSED_HIDE 12**:`ROOT_FILE / ROOT_EXEC / ROOT_PACKAGE_MANAGER / ROOT_PROPERTY / XPOSED_CLASS / XPOSED_STACK / XPOSED_MAPS / SYSTEM_PROPERTIES / PROC_STATUS / PROC_MOUNT_INFO / PROC_CGROUP / PROC_NET`
- **LISTEN 观测 2**:`DETECT_ROOT / DETECT_EMULATOR`

`engine.hook/hookThrowing` 字面调用点约 40、`broadcast.subscribe` 约 12、proc 族三方法单点注册。`DeviceEnvOperation` 64 项,其中 **10 项从未被发射**(含 `CPU_SYSCONF`、`BATTERY_CHANGED`)。

### 3.3 基线的 40 项在 Modev 的对应物统计

| 基线家族 | Modev 现状 |
|---|---|
| Build 9+2 字段 | ◐ 有(13 键 map,但**条件掩蔽 + 一次性不可逆**) |
| 唯一标识 IMEI/号码/AndroidID | ◐ 有(包名哈希生成,**非画像值**) |
| SIM 运营商三项 / LAC-CID / 网络类型 / Wi-Fi SSID·BSSID / DPI / 语言 / 时区 / 电池档位 / 定位伪装 / 定位来源隐藏 / 应用版本伪装 / 空白通行证 4 项 | ❌ **全无**(部分有相邻能力:`RootPackageManagerHooker` 的包名黑名单 ≠ 空白通行证) |
| 截图双向 | ✅ 有(「解除录屏」独立 FEATURE kind,**画像不得出第二实装**) |
| 应用列表可见性 | ◐ 有(根包名黑名单掩蔽,语义不同:黑名单减法 vs 白名单「只留自身+系统」) |
| Modev 独有(Root 文件/exec/属性/栈/maps/proc 四族/Frida/传感器/cpuinfo/类加载监视器) | ✅ 有,基线零覆盖 → **R-8 全保留** |

**一句话**:基线 40 项中 Modev 具备**约 11 项且多为条件档/硬编码形态**,其余 **29 项完全缺失**。

---

## 4. 差距登记(GAP)

严重度:P0 = 不满足即 R-1/R-3 不成立;P1 = 满足 R-1 必需但可分批;P2 = 完整性/可维护性。变更半径:S(<3 文件)/ M(3–8)/ L(8–20)/ XL(>20 或跨全部契约层)。

| ID | 差距 | 基线证据 | Modev 证据 | 严重度 | 半径 | 处置(→ PLAN 工作包) |
|---|---|---|---|---|---|---|
| GAP-01 | **无画像实体**:内置三档是编译期常量,不可命名/不可 CRUD/不可绑多应用 | `Template` + `BundledTemplateManager` | `SpoofProfiles.kt:163-165` ✅ | P0 | XL | WP-2 Room v15 `spoof_profile` + `SpoofProfileService` |
| GAP-02 | **DIY 写入即静默失效**:未知 id 一律回落 `auto` | —— | `SpoofProfiles.kt:163-165` ✅ | P0 | M | WP-2(画像库解析 + 未知 id **硬失败**,禁回落) |
| GAP-03 | **无字段目录事实源**:40 字段的标签/校验/随机/i18n/MCP/探针用例无单点声明 | 分散在 `ConfigEditor.kt` | 分散在 5 个 hooker 常量 ✅ | P0 | L | WP-1 `ProfileFieldCatalog`(四处派生) |
| GAP-04 | **29 项基线能力完全缺失**(DPI/语言/时区/网络类型/SIM/LAC-CID/定位/两类定位隐藏/电池档位/版本号/空白通行证 4 项/Wi-Fi 三项…) | §2.1 | §3.3 | P0 | XL | WP-3/4/5 分三批落 18 kind |
| GAP-05 | **条件掩蔽使「100%」不可达**:只在脏值上改,显式画像不生效 | 基线按值无条件写 | `isEmulatorIndicator` / `isIndicativeValue` ✅(E-EMU-01 被 SKIP 的根因) | P0 | M | WP-3 **强制档/条件档双语义分家**(决策 D-5) |
| GAP-06 | **无最小安装面编译**:开关关闭仍有安装期开销/后开不补装 | `HookPlan.activeHookFeatures()` | 现有靠 `ConfigWatcher` 惰性补写 + `enabledIn` 装配期裁剪 | P1 | M | WP-3 `ProfileHookPlan.compile` 纯函数 + 冻结「空画像=零组」 |
| GAP-07 | **值生成器无真实形态纪律**:MAC 前缀 `02:42:ac:11:` **自认命中等**于自身判据 | `Randoms.kt` Luhn/本地管理 MAC | `EmulatorNetworkHooker.kt:104` ✅ | P1 | S | WP-3 生成器移植 + WP-8 断言形态合法 |
| GAP-08 | **随机不整体一致**:无「一台设备一组世代一运营商」派生,各字段独立 | `Utils.kt:14-78` | 无 | P1 | M | WP-4 `ProfileRandomizer` |
| GAP-09 | **Build 无读取期路径且改写不可逆**:关开关不复原、生效时机晚于首次读取 | FIELD 改写同法(基线亦如此) | `EmulatorBuildHooker.kt:27,52-57` | P1 | M | WP-3 + D-6 显式声明语义 |
| GAP-10 | **无机型/运营商目录资产**:画像选型无数据来源 | 8,533 + 1,597 ✅ | 无 | P1 | M | WP-4 引入 devices/carriers(R-6) |
| GAP-11 | **无导入导出**:画像不可迁移 | `TemplateTransfer.kt` + QR + URL | 无 | P1 | L | WP-6(zxing + CameraX,**lint 基线必漂**) |
| GAP-12 | **记录侧无「原值→伪装值」双侧 oracle**:`maskedValue` 读回恒 null | 基线无记录面(不适用) | `DeviceEnvRecordMappers.kt:64` ✅ | P1 | M | WP-2 v15 新列 `maskedValue` |
| GAP-13 | **截图双向不可表达「强制禁止」**(基线自身残缺,UI 单布尔折叠) | `ModuleConfigManager.kt:246-250` | Modev 只有「解除录屏」单向 | P2 | S | WP-4(三态钩面)+ WP-5(三态选择 sheet);决策 D-8 |
| GAP-14 | **IMEI 双卡同值**(基线自身残缺并已在 UI 声明) | `UniquelyIdHook.kt:48-50` | 同 | P2 | S | WP-3 单值 + UI 说明文案照基线诚实标注 |
| GAP-15 | **无每应用「本画像摘要可见性」**:绑了哪档、生效哪些字段不可读 | 基线 `parameterSignature` 反查 | 仅 `spoofProfileId` 一行 | P2 | S | WP-5 行副标题动态有效字段数 |
| GAP-16 | **空白通行证能力完全缺失**(通讯录/媒体空 Cursor) | `BlankPass.kt` | 无 | P0(属 R-1) | M | WP-4 |
| GAP-17 | **MCP 无画像工具面**:Agent 不可 CRUD | 基线无 MCP(不适用) | 45 工具中仅 `spoof_profiles_list`/`spoof_global_set` | P0(属 R-3 + 门禁 #18/#26) | L | WP-7 工具面 45→54 |
| GAP-18 | **UI 无编辑器**:功能设置页只能选档 | `ConfigEditor.kt` 40 字段 | `SpoofProfileScreen.kt` 3 行 | P0(属 R-3) | L | WP-5 |

## 5. 缺陷登记(DFC,与基线无关但重建必修)

| ID | 缺陷 | 证据 | 处置 |
|---|---|---|---|
| DFC-01 | `/proc/cpuinfo` 伪造**从未生效**:钩 `java.io.File#readText`,而 `readText` 是 Kotlin 扩展静态方法,`getMethod` 必抛 → `getOrNull` → 钩子永不安装 | ✅ `DeviceCpuHooker.kt:58-62` | WP-3 改挂 `ProcScanBroadcaster`(新增 `ProcPath.CPUINFO`);死路径整体删除 |
| DFC-02 | `Build.getMODEL()` 等 **13 次 getter 尝试中 12 次不存在**(AOSP 仅 `getSerial`),`DetectionMonitorHooker.BUILD_FIELDS` 四条同形 → 观测与改写双死路径 | ◐ `EmulatorBuildHooker.kt:100-104`、`DetectionMonitorHooker.kt:203-226` | WP-3 删除无效 getter 面,改纯 FIELD 写 + 文档声明边界 |
| DFC-03 | `maskedValue` 不落库 ⇒ 详情页「已隐藏」行**永不渲染**;文件头注释与实际列映射不符 | ✅ `DeviceEnvRecordMappers.kt:19` vs `:64` | WP-2 v15 新列 + 头注释同次修正 |
| DFC-04 | 假 MAC 前缀 `02:42:ac:11:` 命中自身模拟器判据(Docker/桥接 OUI) | ✅ `EmulatorNetworkHooker.kt:104,106-108` | WP-3 生成器换本地管理单播形态(GAP-07) |
| DFC-05 | `EmulatorFileHooker` 是**空壳**,实际能力寄生在 `RootFileHooker`;`WindowHook.kt`(基线)同形残留 | ◐ `EmulatorFileHooker.kt:9-16` | WP-3 归位或删除,禁留空壳 |
| DFC-06 | `SettingsHooker` / `FridaThreadHooker` **无自有 kind**,分别挂在 `ROOT_PROPERTY` / `PROC_STATUS` 下 ⇒ 关一个丢一族 | ◐ `SettingsHooker.kt:47,78,111,148` | WP-3 决策 D-7(独立 kind 或不拆,写进契约) |
| DFC-07 | `XPOSED_STACK` 词表**不吃 `extraMaskKeywords`**,同族 maps/mountinfo/cgroup 吃 ⇒ 黑名单语义不一致 | ◐ `XposedStackHooker.kt:81-91` | WP-3 同次归一 |
| DFC-08 | `EmulatorBuildHookerTest` **在测试体内重抄私有实现**(副本已漂移)⇒ 该 kind 门禁强度为 0 | ◐ `test/…/EmulatorBuildHookerTest.kt:76-110` | WP-3 改直测 `internal` 函数本体(两函数本就是 internal) |
| DFC-09 | `DeviceEnvOperation` 10 个枚举值从未发射;`HookSwitchTest` 的「默认值 ⊥ taxonomy 对齐」门禁**只覆盖 4 类**,device_env 不在内 | ◐ `HookSwitchTest.kt:118-152` | WP-3/WP-7 补 device_env 对齐闸 |
| DFC-10 | 文档/代码漂移 4 处:AGENTS #23 分区名「拦截与伪装」vs 实装「功能域」;#16 偏差位清单 vs 27 构造参数;`FeatureConfig` KDoc「28 项」实为 30;「机型唯一事实源」只兑现 4 处 | ✅ 多处 | WP-9 契约同次回写 |

## 6. 验证债登记(DEBT)

| ID | 债 | 现状证据 | 清偿 |
|---|---|---|---|
| DEBT-01 | **device_env 零行为测试**:SENSOR/NETWORK/TELEPHONY/CPU/BATTERY/SYSTEM_PROPERTIES/PROC_*/XPOSED_STACK/MAPS/SettingsHooker 全无 | ◐ 测试真值表(PLAN §6 复核清单) | WP-8 |
| DEBT-02 | **after-setResult 是否真生效从未验证**(V38 修复过的功能域全局风险面) | AGENTS 实锤段 + 无二态断言 | WP-8 逐字段二态断言 |
| DEBT-03 | **设备矩阵把关键伪装档标 SKIP**:`E-EMU-01`(`21-run-enhance.ps1:223-224`)、`E-ROOT` delta(`:201`)、`PROC_STATUS/CGROUP`(`:239-241`)、`E-VPN` delta(`:183`) | ✅ 脚本存在性已核,行号 ◐ | WP-8 SKIP 清单**恒空** |
| DEBT-04 | **无逐字段期望表**,画像值与实测值无对照物 | ✅ 全仓无 expectation 资产 | WP-8 `ProfileFieldCatalog` 派生 |
| DEBT-05 | **探针面覆盖不足**:`ANDROID_ID` 无探针、电池探针指向错误 API(广播 extras 而非 `BatteryManager.getIntProperty`)、`Settings.*` 5 键无探针、`getImei/getSubscriberId/getSimSerialNumber/getLine1Number` 无探针 | ✅ `TestCases.kt` L-DEV 仅 21 条;◐ 覆盖矩阵 | WP-8 |
| DEBT-06 | **无画像切换自动化**:无任何脚本写过 `spoofProfileId`/`spoofOverrides` | ◐ 脚本清单 | WP-8 `28-run-spoof.ps1` |
| DEBT-07 | **三方检测 App 只冷启动不断言**(`23-run-smoke.ps1` 仅 pidof/ANR/FATAL) | ✅ 脚本存在性;◐ 断言范围 | WP-8 观测档 |
| DEBT-08 | **无跨字段/跨进程/跨应用一致性校验** | ✅ | WP-8 `CONSISTENCY-*` |
| DEBT-09 | 设备环境不可验收:零序列在线,最近两轮 FAIL/BLOCKED | ✅ `adb devices` + `docs/test-reports/` | **WP-0 前置** |

---

## 7. 契约与门禁影响面(返工半径)

| 面 | 现状 → 目标 | 必须同步的闸 |
|---|---|---|
| Room | v14 → **v15**:新表 `spoof_profile` + `crypto_records` 新列 `maskedValue` | `DbMigrationParityTest`(三处同步)、`RecordWriteModelParityTest` P1 |
| 分类法 | FEATURE **37 → 50**、LISTEN 44 不变;`DeviceEnvKind` 21 → 34(净 +18/−5) | `CategoryTaxonomyTest` 快照、`FeatureGroupsTest` 组并集、`DeviceEnvKindTest` |
| 组归属收敛 | `DEVICE_SPOOF` 退役 5 kind(EMULATOR_BUILD/NETWORK/TELEPHONY/BATTERY/CPU),保留 FILE/SENSOR(→ ROOT_XPOSED_HIDE 族),新 `DEVICE_PROFILE` 组 18 kind | `InterventionGateConventionTest.gatedFields`(+18)、`HotPathGateConventionTest` 白名单 26 → +18 手工登记 |
| wire | `VERSION` 恒 **4** 不动;`FeatureConfig` 加已展开 `deviceProfile`,`configVersion 2 → 3` | `FeatureConfigMigrationTest` 读旧写新、`EffectiveSwitch` readKind/applyKinds 穷尽双表 |
| MCP | 工具面 **45 → 54**;`spoof_profiles_list` 语义重定义为画像库;`feature_settings_set.spoofOverrides` 参数删除 | `McpToolParityTest` 冻结快照、`UiMcpCapabilityParityTest` R1–R6、`McpToolsTest` 子集 32 |
| 写侧单点 | 画像库唯一写入口 = `SpoofProfileService`(domain.config) | `SingleWriteEntryPointTest` W1/W2/W3(新表 + 新可变面)、`LayerDependencyGateTest` G2 |
| i18n | ≈95 新键 × 四语(en/zh/ja/ko) | `StringResourceParityTest`;**必须回写 `scripts/v42_table_*.mjs` 并实跑生成器看到 `DONE`**(V46 静默失效根因) |
| lint | 43 warning / 1 hint / 0 error → **实测重定基线**(新增 zxing+CameraX 直依赖各必 +1;反射 `@SuppressLint` 若干) | 提交前 `lintModernRelease` 必跑并登记新数 |
| 词汇表/AGENTS | 新增词条:设备画像 Device Profile / 画像库 / 强制档 · 条件档 / 空白通行证 / 画像账本;新增强约束条目 | WP-9 与代码同一次提交回写 |

**依赖与许可证事实**(R-6 定档「先复制跑通」,但必须登记在案):基线代码 **GPL-3**,`devices.json` **CC BY-NC-SA 4.0(禁商用 + 相同方式共享 + 署名)**,`carriers.json` **MIT**;Modev 仓库**当前无 LICENSE 文件**。→ 交付前需 D-9 显式裁决(见 PLAN §7)。

---

## 8. 需求追溯矩阵(摘要;全表见 PLAN §8)

| 需求 | 基线条目 | Modev 落点 | 工作包 | 硬门禁证据 |
|---|---|---|---|---|
| R-1 全 40 项 | §2.1 G-01..G-40 | `DeviceProfile` 40(+1 cpuCores 超基线) | WP-1..5 | 期望表逐字段 100% |
| R-2 三内置档 | 无对应物(基线仅 2 非设备模板)✅ | 画像库内置档 + 账本 | WP-2 | `BuiltinProfileCatalogTest` + 压测逐档 |
| R-3 DIY | G-25..G-34 | Room 画像库 + 编辑器 + 导入导出 + MCP | WP-2/5/6/7 | CRUD e2e + R1–R6 parity 闸 |
| R-4 压测 | guise-test 靶应用(基线自带探针思想) | `L-ENV-DUMP-*` 全量快照 + 三方观测档 | WP-8 | 0 FAIL / 0 SKIP |
| R-5 循环 | —— | 期望表派生自 catalog,禁手抄 | WP-8 | 台账逐轮轨迹 |
| R-8 不降级 | §2.6 基线缺口清单 | 12 项 ROOT_XPOSED_HIDE 全保留 | WP-3 | 既有矩阵不回退 |

---

## 9. 实施时须回访的 `file:line`(子代理报告项)

以下断言方向可信、行号未经我逐条复核,实施第一步须回访确认(不得据其直接改码):OsBuildHook 11 字段行号、HookPlan 各组激活谓词行号、`ApplicationListPass` LIST 16 / INFO 17 计数、`ModuleConfigManager.updateConfigFromState` 行号、`Randoms.kt` 各生成器行号、`DeviceEnvCases.kt` 21 个用例行号、`21-run-enhance.ps1` 各 SKIP 行号、Modev 各 hooker 的钩子方法行号、`HotPathGateConventionTest` 白名单条目数。
