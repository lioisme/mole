# V49 · 设备画像伪装：按底层类型分类 / 逐项自定义 / 内置数据照搬基线 / 全量端到端生效验证 —— 审计与执行任务书

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐工作包执行。本文所有步骤用 `- [ ]` 复选框跟踪。
> **依赖文档:** `AGENTS.md`（强约束 4/13/16/18/23/26/27/30/31、词汇表「伪装画像/画像选型库/机型库/能力表」四行、已知工具链坑「框架守护进程整场缺席」「设备侧改宿主 SharedPreferences 三定律」「connectedAndroidTest 测后 AGP 卸载宿主 APK」三条）、`TASK-V47-SPOOF-PLAN.md`（WP-3c/3d/4/8/9 = 本文 WP-D 的前置与失败教训）、`TASK-V47-SPOOF-AUDIT.md`（GAP/DFC 登记册）、`TASK-V48-SCOPE-CLASSIFY-PICKERS.md`（`CapabilityMap` 契约与 V48-ENV-01）、`TEST-V48-ACCEPTANCE.md`（64 项口径与 ⛔ 记号纪律）。
> **本文位置说明:** 按仓库既有惯例落在根目录（`TASK-V<NN>-*.md`），不落 `docs/superpowers/plans/` —— 本仓 12 份任务书与 `AGENTS.md` 的交叉引用都以根目录为准。

**Goal:** 让设备画像伪装的 **41 个字段每一个都在靶程序里真实生效、可被独立自定义、按它真正钩的 Android API 族分类、内置数据与基线 Guise 逐字节同源且零 Apple 记录**，并以一次性全量通过的设备档为收口证据。

**Architecture:** 一条通路，不是两条。今日实况是**画像库（宿主侧完整）+ 遗留档 `SpoofProfiles`（注入侧生效）两套并存且互不相干** —— 41 字段对靶程序的影响为零。V49 的核心动作是**把注入侧接管过来，然后删掉遗留通路**，而不是给两套栈各修一半。分类只有一条轴：**底层来源族 ≡ 干预 kind ≡ UI 分区 ≡ 钩子安装点 ≡ MCP enum 值**，由既有的 `CapabilityMap` 承载（V48 已建成的机制，V49 只把画像行集接进去，禁再抄第三份清单）。验证靠**三重 oracle 对账**：靶程序自己读回的值（唯一能证明「App 看到了什么」的通道）== 期望值（由画像夹具派生）== 宿主库里的 `maskedValue`（钩子自称写了什么）。

**Tech Stack:** Kotlin 2.2.20 / Compose Material3 1.4.0 / Room 2.7.2（v15）/ libxposed API 102 + legacy Xposed API 82 双适配器 / Gson（`serializeNulls`）/ Robolectric + Compose 插桩档 / PowerShell 设备档编排器 + `TT_RESULT` logcat 断言通道 / Node（四语翻译表生成器）。

**Spec:** 用户 2026-09-23 四条需求（R-1…R-4，原文见 §1）。

---

## 0. 阅读约定与实测基线

| 记号 | 含义 |
|---|---|
| `file:line` | 本文每条断言的落点均为 2026-09-24 实测（Read/grep/md5/node 解析）；执行时若行号漂移，以**符号名**为准 |
| V49-DFC-xx | V49 审计新登记的缺陷（与 V47 GAP/DFC、V48 DFC 编号不重叠） |
| V49-ENV-01 | 阻塞因在靶机/宿主而非代码的环境前置（见 §2.8）；既不记 ✅ 也不记 ❌，恢复后必须补跑 |
| ✅ / ❌ / ⬜ / 🔵 / ⛔ | 符合 / 不符合（须归因）/ 未跑 / 进行中 / 环境阻塞（承接 V48 记号） |
| **判定口径纪律** | 「已实现」= **靶程序实际读到的值 + 用户实际看到的文字 + 注册表内容**。「某个类存在」「某个键被写入偏好」「某个 test 绿」一律不是代理指标（承接 V47 §0 与 V48 §0） |

**实测规模基线（2026-09-24，`git HEAD = db0ba7a`）：**

| 层 | 事实 | 实测方式 |
|---|---|---|
| 画像字段 | 41（`ProfileField`）/ 干预组 18（`ProfileKind`）/ 分区 10（`ProfileSection`） | `core/model/ProfileField.kt:8-38,73-240` 逐条计数 |
| 分类法 | 81 kind（LISTEN 44 / FEATURE 37）；`DEVICE_ENV` 21 kind | `core/model/CategoryTaxonomy.kt:115-136`、`DeviceEnvKindTest` |
| 能力表 | `CapabilityMap` 81 行，零 kind 字面量，双向对账 | `core/model/CapabilityMap.kt`、`CapabilityMapTest` |
| 注入侧画像消费者 | **0** | `grep -rn "deviceProfile\|ProfileField\|ProfileHookPlan\|activeGroups" app/src/main/java/com/lioisme/modev/hook/` → **空输出**（本轮实测复核） |
| 设备环境探针 | 23 个 hooker（全部为**条件掩蔽档**） | `hook/probes/DeviceEnvProbe.kt:52-76` |
| 机型资产 | `app/src/main/assets/devices.json` 1,938,874 B / **26 品牌 / 8,533 机型**，其中 **apple 品牌 458 条**（mob 204 / pad 144 / watch 99 / tv 11，`buildBrand=Apple`，`manufacturer=Apple`） | node 解析（§2.5 附命令） |
| 与基线的关系 | `devices.json` 与 `carriers.json` **md5 与 `third_party/Guise_Reborn-main` 逐字节相同**（`43ce139f…` / `efde9112…`） | `md5sum` 四文件对比 |
| 靶程序 | `testtarget` = 无界面的 logcat 触发器；**零 ANDROID_ID / 零 locale / 零 density / 零 location / 零 SIM 探针** | `testtarget/src/main/AndroidManifest.xml:3-7`、`cases/DeviceEnvCases.kt:38-195`、`Tt.kt:9-18` |
| JVM 单测 | 157 类 / 1,114 用例 / 0 失败 / 0 跳过 | `TEST-V48-ACCEPTANCE.md` 执行记录 09-23 19:1x 轮 |
| 插桩档 | 136 用例 / 0 失败 / 1 表内豁免 SKIP | 同上 G7 |
| lint | 0 error / 43 warning / 1 hint | 同上 G1-2 |
| Room / MCP | version 15 / 50 工具（冻结快照） | `data/db/ModevDatabase.kt:55-59`、`McpToolParityTest` |
| 设备档注入证据 | 本轮**未取到**（V48-ENV-01） | `TEST-V48-ACCEPTANCE.md:132` |

---

## 1. 需求本体（逐条可验收）

### R-1 「设备画像伪装功能要求按底层类型进行分类。比如设备信息类、系统类、权限类等。」

**解读（取最严格且可判定的那种）：** 分类轴必须是**这个值从哪个 Android 子系统来、要改它必须钩哪个 API**，而不是「看起来像什么」。同一分区的字段必须能用**同一个钩子族**实现 —— 分区因此不是文案装饰，而是实现单元。

**验收：**
1. 画像的每个分区标题都点名它的来源 API 族（四语），且分区集 = 干预 kind 集 = 钩子安装点集 = MCP `kind` enum 值集，**四者严格 1:1，由测试对账**（不是文档对照）。
2. 全 41 个字段都能从「底层类型」视角被追溯到一个来源族，零「OTHER 杂物抽屉」——现行 `ProfileSection.OTHER`（电池 + 截屏）与 `LOCALE`（语言 + 时区，两种完全不同的生效机制）必须拆开。
3. 用户可见：画像编辑器、每应用「画像微调」区、MCP `taxonomy_feature` 三处的分区归属逐字相同（同一张表派生）。

**禁过关形态：** 只改分区标题文案、只加一份「分类说明文档」、把 FEATURE kind 与 LISTEN kind 混进同一分区（V48 #23 双域闸不破）。

### R-2 「前端界面每一个配置都可以自定义，而不是只能在一个入口依次设置参数，要求使用这种最简单的逻辑实现。」

**解读（两层都要成立，取最严格）：**
- **画像内**：任一字段可**单独**读 / 改 / 清空 / 随机，与其他 40 项零顺序依赖；复合选择器（机型库 / 运营商库 / Android release 成对档）只能是**捷径**，绝不得是任何字段的**唯一**通路。
- **每应用**：只给「绑一整张画像」这一个入口 = 「只能在一个入口依次设置参数」。故每应用页必须能把 41 项中的**任意一项**单独定制作答，不必为此新建或改动整张画像。

**验收：**
1. 任一单项从空画像出发可保存并生效（注入侧对该项写入、其余 40 项零副作用）—— 由设备档 `SP-FIELD-<FIELD>` 41 条逐字段用例证明，不是由 UI 用例证明。
2. 每应用「画像微调」区：41 项逐项可编辑、逐项可单独还原；行组件与画像编辑器**同一个** `ProfileFieldRow`（禁再拼一份清单，#31③）。
3. 复合选择器落的那几项，编辑后行内值同步（已是现状，加回归门禁防退化）。

**禁过关形态：** 用「画像编辑器已有 41 行」糊弄第二层；为每应用另建一套表单组件。

### R-3 「安卓系统，不应该有苹果、Apple 的内置库。另外，内置数据要求完全照搬基线文件夹下的 Guise 项目。」

**验收：**
1. **可达即违规**：机型库解码后的品牌集与型号集中，`apple` / `iPhone` / `iPad` / `iPod` / `Apple Watch` / `Apple TV` / `Mac-` 平台标识符**零命中**；选择器与随机源都取不到它们（`BuiltinDeviceProfiles` 与 `ProfileGenerators` 的取值域同样在扫描面内）。
2. **照搬**：随包的每一本基线数据与其 `NOTICE` 与 `third_party/Guise_Reborn-main` 对应文件**逐字节相同**（md5 门禁），且基线的**每一本**内置数据都在包内 —— 缺口必须补齐或显式登记为「有意不取」（本文 D-6 登记两条，附理由）。
3. 基线 `presets.json` 的五张表（androidVersions 37 / sdkLevels 37 / densityDpi 13 / networks 6 / languages 13）作为**资产**进包并成为取值源（不再由 Kotlin 手抄一份，见 D-6）。

**禁过关形态：** 改 `devices.json` 内容来「去 Apple」（那就不是照搬，且违反上游 ShareAlike 完整性与「禁改文件」条）；在 UI 侧过滤而 `ProfileGenerators`/`SpoofProfiles` 随机源仍能吐 Apple 值；把基线的 13 条语言 label 变成第二份语言事实源。

### R-4 「对设备画像伪装的所有功能进行全量测试验证，确保确实生效，确实可以伪装。自己做一个被作用域的程序靶机并使用主流设备信息工具进行测试。所有功能都正常生效，不符合预期则分析原因并修复测试，直到所有测试结果一次性全量符合预期才能结束任务。」

**验收（= 本文 §6 的收口判据，逐条硬）：**
1. 靶机自建：`testtarget` 增加**读回档**（41 字段 × 多通路，含 UI 屏与 `TT_RESULT` 两种输出），并进入作用域。
2. 主流设备信息工具作为**独立第三方观测档**（真实屏上文本 + 截图归档），检测类工具（momo / NativeTest）同档；该档**不作门禁但必须留证**，且**预先登记预期红项**（否则它会被用来假装全绿）。
3. 门禁 = `28-run-spoof.ps1` **单轮一次性** 41/41 全绿（含 :sub 双进程、冷启动持久、开关全关 = 零副作用三个附加面），**SKIP 集恒空**。
4. 「一次性全量符合预期」的定义见 §6；未达成不得结束任务。

**禁过关形态：** 用 `crypto_records.maskedValue` 单证当生效证明（那是钩子自称，非 App 所见 —— §2.1 论证）；用 JVM 单测绿当设备生效；用「靶机没连上/框架没跑」把 41 条记 SKIP。

---

## 2. 审计结论

### 2.1 主判决：两条互不相干的伪装栈，画像对靶程序的实际影响 = 0

```
宿主侧（完整、可编、可存、可推）              注入侧（真实生效，但读的是另一套值）
ProfileField(41) ─┬─ ProfileFieldCatalog       HookInstaller
                  ├─ ProfileEditorForm/Screen    └─ HookSwitch.activeSpoofProfile()
                  ├─ ProfileCodec / Room v15         └─ SpoofProfiles.resolve(spoofProfileId, spoofOverrides)
                  ├─ ProfileHookPlan ← 只被 UI 预览消费      ├─ buildFields(13) ──► EmulatorBuildHooker
                  └─ BuiltinDeviceProfiles(9 档)             ├─ roProperties(14) ─► DeviceSysPropHooker / SystemPropertiesHooker
SettingsRepository.expandProfile()                          ├─ imei/imsi/iccid/phone 前缀 ─► DeviceTelephonyHooker
   └─► FeatureConfig.deviceProfile 快照（随有效配置下发）      ├─ fakeSsid/fakeBssid ─► ProxyHideHooker
         ▲                                                  └─ cpuCores ─► DeviceCpuHooker
         └── hook/ 子树对它 **零读者**（本轮 grep 实测：空输出）
```

三条后果，句句可复核：

1. **41 个字段对靶程序一个都不生效。** `hook/` 里没有任何 `deviceProfile` / `ProfileField` / `ProfileHookPlan` 读者；下发链终点就是它自己（`core/model/FeatureConfig.kt:33-45` 的注释与 `data/repo/SettingsRepository.kt:279-280` 的「直至 WP-3/WP-4 逐组接管」都承认这点）。`AGENTS.md` #30 那句「说『画像已生效于靶程序』即假」今天**仍然为真**，`SpoofProfileLibraryScreen.kt:336` 的 `profile_takeover_status_hint` 就是这条边界的 UI 兑现。
2. **遗留通路自己也在漂移。** V47 迁移后 `spoofProfileId` 存的是库主键（`builtin:pixel-7-pro` / UUID），而 `SpoofProfiles.resolve` 只认 `auto/pixel7pro/xiaomi13`，未知 id **静默回落 AUTO**（`core/model/SpoofProfiles.kt:164`）；`EmulatorBuildHooker.kt:138-151` 更因 `id != "auto"` 走「显式档」分支，把预干净模拟器的 vbox/nox → 小米组路由变成死路。⇒ 今天任何库绑定都只产出「Pixel 组、且仅在脏值上」。
3. **生效的那一批全是「条件掩蔽档」，不是伪装。** `EmulatorBuildHooker.kt:84` 只在 `isEmulatorIndicator(current)` 为真时改写；`DeviceCpuHooker.kt:40-42` 只在真实核数更多时改写；`DeviceBatteryHooker.kt:87-99` 吐的是硬编码 67/-5432100。这正是 `ProfileKind.kt:11-13` 自己划的界：本枚举 = 画像驱动的**强制档**，`DeviceEnvKind` = **条件档**。用户要的「按画像伪装」= 强制档，**执行体一个都没有**（`ProfileHookPlan.activeGroups()` 的调用点只有 `ProfileEditorForm.kt:104-107` 与 `ProfileEditorState.kt:56` = 预览卡）。

### 2.2 41 字段全量对照表（字段 → 底层来源 → 今日生效状况 → V49 归属）

状态记号：**∅** = 零钩子；**◐** = 有钩子但只在检出脏值时掩蔽、且值源为遗留档/硬编码；**◎** = V49 目标（画像驱动的无条件写入）。

| # | 底层来源族（= 新 kind / 新分区） | 字段（`ProfileField`） | 今日 | V49 批次 / 钩子落点 | 靶机读回观测（`TT_RESULT` key） |
|---|---|---|---|---|---|

> 第一列 = **来源族名**（本文用它讨论「要钩哪个 API」）；与之 1:1 的 kind 名 = WP-D 各批标题里的 `PROFILE_*`；分区标题键 = D-2 的 `profile_kind_<NAME>`。三名一物，不许各说各话。
| 1 | `android.os.Build` 静态字段 + OEM getter | BRAND, MANUFACTURER, MODEL, PRODUCT, DEVICE, BOARD, HARDWARE, FINGERPRINT | ◐ `EmulatorBuildHooker` 值源=遗留 | **D1** `ProfileBuildHooker` | `brand/manufacturer/model/product/device/board/hardware/fingerprint`（反射 `getField` + 直读 `Build.X` 双通路） |
| 2 | `android.os.Build.VERSION` + `Build.ID` | BUILD_ID, ANDROID_VERSION, SDK_INT | ∅ | **D1** 同上 | `buildId/release/sdkInt` |
| 3 | `Resources`/`DisplayMetrics`/`Configuration` | DENSITY_DPI | ∅ | **D2** `ProfileDensityHooker` | `dpi_sys/dpi_app/density/scaled/widthDp/realDpi`（6 通路，基线 `DisplayDensityHook` 同集） |
| 4 | `Runtime` + `/proc/cpuinfo` | CPU_CORES | ◐ `DeviceCpuHooker` | **D2** `ProfileCpuHooker` | `availProcessors/cpuinfoProcessors` |
| 5 | `Settings.Secure`/`Settings.System.getString` | ANDROID_ID | ◐ `EmulatorNetworkHooker` **自生成值**，且缺 System 分支 | **D3** `ProfileAndroidIdHooker` | `androidIdSecure/androidIdSystem` |
| 6 | `TelephonyManager` ID 族 + `SubscriptionManager` | IMEI, PHONE_NUM | ◐ `DeviceTelephonyHooker`（前缀派生、仅模拟器形态） | **D3** `ProfileUniqueIdHooker` | `imei0/imei1/deviceId/tac/line1/subPhone` |
| 7 | `TelephonyManager` SIM 族 + `SubscriptionInfo` + `CellIdentity` MCC/MNC | SIM_OPERATOR, SIM_OPERATOR_NAME, SIM_COUNTRY | ∅ | **D3** `ProfileSimHooker` | `simOperator/networkOperator/simOpName/netOpName/simCountry/netCountry/subMccMnc` |
| 8 | `ConnectivityManager`/`NetworkInfo`/`NetworkCapabilities` | NETWORK_TYPE | ∅（`VpnHideHooker` 只在 VPN 语境 7→1） | **D4** `ProfileNetworkHooker` | `activeNetInfo/typeName/capHasWifi/capHasCell` |
| 9 | `WifiManager`/`WifiInfo`/`ScanResult` | WIFI_SSID, WIFI_BSSID, WIFI_MAC_ADDRESS | ◐ SSID/BSSID 值源=遗留；MAC **自生成且吐 `02:42:ac` 虚拟化 OUI**（GAP-07 未清） | **D4** `ProfileWifiHooker` | `ssid/bssid/mac/scanBssid` |
| 10 | `GsmCellLocation` + 六类 `CellIdentity` | LAC, CID | ∅ | **D5** `ProfileCellHooker` | `gsmLac/gsmCid/lteTac/lteCi/nrTac/nrNci` |
| 11 | `LocationManager`/`Location` | LONGITUDE, LATITUDE, RANDOM_OFFSET | ∅ | **D5** `ProfileLocationHooker` | `lastKnownLat/lastKnownLng/offsetTwice` |
| 12 | Wi-Fi 定位来源压制 | WIFI_LOCATION_HIDE | ∅ | **D5** 同上（抑制分支，R-B 裁决） | `wifiScanCount/wifiState` |
| 13 | 基站定位信息压制 | CELL_LOCATION_HIDE | ∅ | **D5** 同上（R-C 裁决） | `cellInfoCount/requestUpdates` |
| 14 | `Locale`/`LocaleList`/`Configuration`/`LocaleManager` | LANGUAGE | ∅ | **D6** `ProfileLocaleHooker` | `localeDef/localeTag/configLocale/perAppLocales` |
| 15 | `TimeZone` 进程默认值（零钩子） | TIME_ZONE | ∅ | **D6** `TimeZone.setDefault` | `tzJava/tzZoneId/tzIcu` |
| 16 | `ApplicationPackageManager.getPackageInfo`（自身包，四签名） | VERSION_CODE, VERSION_NAME | ∅（`AppInfoHooker` 观测不写） | **D6** `ProfileAppVersionHooker` | `manifestVersion/pmVersionName/pmVersionCode` |
| 17 | `BatteryManager` + `ACTION_BATTERY_CHANGED` extras | BATTERY_LEVEL | ◐ 硬编码常量，非画像 | **D2** `ProfileBatteryHooker` | `batteryCapacity/intentLevel/intentScale` |
| 18 | `Window` flags / `WindowManager` params + Activity 生命周期 | SCREENSHOTS_FLAG | ◐ 只有「解除」方向（`ScreenRecordHooker`），无「强制禁止」 | **D6** **复用同引擎**（V47 D-14：与解除录屏共用，禁第二份实现） | `windowFlags/secureActivityScreenshot` |
| 19 | `ContentResolver.query` / `ContentProviderClient.query` 空 `MatrixCursor` | PASS_CONTACTS, PASS_PHOTO, PASS_VIDEO, PASS_AUDIO | ∅ | **D7** `ProfileBlankPassHooker` | `contactsRows/imagesRows/videosRows/audioRows` + 列名保留数 |
| 20 | `PackageManager` 枚举族（含 `queryIntentActivities`/`getPackagesHoldingPermissions`） | PASS_APPLICATIONS | ∅（现有过滤只针对 Root 名单） | **D7** `ProfileAppListHooker` | `pkgTotal/pkgSystem/pkgSelf/pkgOther/xposedVisible` |

**计数对账：** 20 个来源族 / 41 字段。**今日画像驱动生效字段 = 0 / 41**；有任意钩子机制的字段 = 14 / 41，全部为条件档且值源非画像。
**分类变更：** 现有 18 组拆两处即得 20（`PROFILE_BUILD` → #1+#2；`PROFILE_UNIQUE_ID` → #5+#6），`OTHER`（电池+截屏）与 `LOCALE`（语言+时区）拆干净 —— 分区轴与 kind 轴合二为一，`ProfileSection` 枚举随之删除（分区标题由 kind 的能力表行 `sectionKey` 承载，见 D-2）。

### 2.3 缺陷登记（按影响排序）

| ID | 缺陷 | 证据 | 处置 |
|---|---|---|---|
| **V49-DFC-01** | 画像 41 字段对靶程序零生效；「画像库已完成」的表述与在屏 banner 之间的边界靠文案维持，不靠测试维持 | §2.1 grep 空输出 + `FeatureConfig.kt:33-45` | WP-D（接管）+ WP-F（设备档证明），收口后删遗留通路 |
| **V49-DFC-02** | 遗留通路**静默回落**：库主键进 `SpoofProfiles.resolve` → AUTO；`EmulatorBuildHooker` 的模拟器族路由对迁移后用户成死路 | `SpoofProfiles.kt:164`、`EmulatorBuildHooker.kt:138-151` | WP-D6 随遗留通路一并删除（不是修补） |
| **V49-DFC-03** | 分类轴不是「底层类型」：`OTHER` 混装电池+截屏（两种机制）、`LOCALE` 混装语言+时区（有钩子 vs 零钩子）、`IDENTITY` 混装 `Settings` 与 `TelephonyManager`、`DEVICE` 混装 `Build` 与 `DisplayMetrics` 与 `Runtime` | `ProfileField.kt:8-38,123-127,200-222` | WP-A（D-2 单轴化） |
| **V49-DFC-04** | 每应用只有一个入口 = 绑整张画像；`spoofOverrides`（稀疏字段覆写）**写面已冻结、无 UI**，故「每一项都能单独定制」在产品上只有一层成立 | `core/model/FeatureConfig.kt:27-32`（冻结注释）、`ui/apps/PerAppFeatureSettingsScreen.kt`（只有 `spoofProfileId` 绑定行） | WP-C（D-4：每应用逐项微调） |
| **V49-DFC-05** | **Android 产品随包 458 条 Apple 记录**，且在选择器里可达可写：品牌行 `apple`（`buildBrand=Apple`/`manufacturer=Apple`）→ 选 iPhone 16 Pro 会把 `Build.BRAND` 写成 `Apple` | `assets/devices.json`（node 解析：26 品牌 / 8,533 机型 / apple 458）；`ProfileEditorScreen.kt:181-206` 未过滤 + `applyDevicePreset`/`applyBrandIdentity`（`DeviceCatalog.kt:162-184`） | WP-B（D-5 解码边界过滤 + 全仓扫描门禁） |
| **V49-DFC-06** | 「内置数据照搬基线」有 3 处未照搬：`res/raw/presets.json` 五张表未进包（改用 Kotlin 手抄，且**止于 API 36**，比基线少 Android 17/37）；基线 2 张隐私模板档（blank-passport / force-screenshots）无对应物 | 基线 `app/src/main/res/raw/presets.json`（37/37/13/6/13 条）vs `ProfileValueSets.kt:17,22-32` | WP-B（D-6；含两条「有意不取」登记） |
| **V49-DFC-07** | 靶机不能证明任何事：`testtarget` 只读 8 个 Build 字段 + MAC + deviceId + 电池 + 2 个 ro 属性；**无 ANDROID_ID / locale / timezone / density / location / SIM / SSID / 包版本** 探针；缺 6 项权限使 location/contacts/media/包枚举探针物理不可测 | `cases/DeviceEnvCases.kt:38-195`、`AndroidManifest.xml:3-7`、`10-build-install.ps1:28` | WP-E（读回档 41 字段 × 多通路，基线 `guise-test` 为设计参照） |
| **V49-DFC-08** | 设备档从不验画像值：唯一「值变了」的 `E-EMU-01` 断言**被 SKIP 掉了**（拿 LDPlayer 预伪装当真值），`run-acceptance.ps1` 无 spoof 阶段，`28-run-spoof.ps1` 不存在 | `21-run-enhance.ps1:223-224`（两条 Skip 原文）、`run-acceptance.ps1:20,255-349` 阶段集 | WP-F（D-8：SKIP 集对画像恒空 + `-Stage spoof`） |
| **V49-DFC-09** | 环境门禁是代理指标：`00-env-verify.ps1:67-68` 用 `pm list packages \| grep magisk` 判「Magisk installed」，V47 基线轮报 7 PASS / 0 FAIL，次日框架被证明**整场未运行** | `docs/test-reports/v47-baseline-20260922.md:35` vs `TEST-V48-ACCEPTANCE.md:132` | WP-0（换成运行期四判据硬闸） |
| **V49-DFC-10** | Wi-Fi MAC 生成器与钩子双侧都在吐 `02:42:ac:11:*`（Docker 前缀）= 自造检测点，V47 登记 GAP-07/DFC-04 未清偿 | `EmulatorNetworkHooker.kt:90-93`（`02:42:ac:11:` 硬拼）、`ProfileGenerators.kt:13-14`（注释已点名） | WP-D3（发射器与生成器同处收口，禁两处各修一半） |
| **V49-DFC-11** | 许可与出处债务：`devices.json` 上游 **CC BY-NC-SA 4.0**（非商业 + ShareAlike）、基线代码 **GPLv3**，而 Modev 根目录**零 LICENSE**、`README.md` 零数据出处鸣谢（`LicensesScreen` 只列 Gradle 依赖，不含数据资产） | `assets/devices.NOTICE.txt:1-14`、基线 `LICENSE`/`README.md:354-356`、`ui/settings/LicensesScreen.kt:39-41` | **登记为 RISK-1，待使用者定夺**（非本文自修范围，见 §7） |
| **V49-DFC-12** | 画像相关 kind 未进分类法 ⇒ `ProfileKind` 是第四份「清单」，与 #31③「页面不许自拼分类清单」的精神冲突（它没有 `CapabilityMap` 行、没有 master 位、没有记录路由） | `CategoryTaxonomy.kt` 内 `ProfileKind` 零命中 | WP-A + WP-D（逐批登记 kind 时并入，见 D-3） |

### 2.4 R-2 侧现状（实测，决定工作量）

画像编辑器已经做的比需求要求的一半还好：41 行逐字段渲染（`ProfileEditorScreen.kt:491-527`）、逐字段弹层（枚举走 `ModevSearchableChoiceSheet`、槽走 `ModevChoiceSheet`、文本走 `ModevInputSheet`、布尔行内 Switch）、逐字段清空 / 逐字段随机（`onClear`/`onRandomize`）、无顺序要求（`canSave` 只看校验不看填写次序）。**R-2 第一层只差「不许复合选择器成为唯一通路」的门禁**（今日 MODEL/DEVICE/BRAND/MANUFACTURER 四个字段既是复合行的产物，也各有自己的行与弹层 —— 这点必须钉死，否则下一次「顺手优化」就会把 4 个行收掉）。

真正缺的是**第二层**：每应用页只有一个画像入口（V49-DFC-04），以及 `spoofOverrides` 写面冻结。

### 2.5 R-3 侧数据事实（本文命令可直接复跑）

```bash
node -e "const d=require('./app/src/main/assets/devices.json');
  let t=0;for(const b of d.brands)t+=b.models.length;
  console.log(d.brands.length,t);                    // 26 8533
  const a=d.brands.find(b=>b.key==='apple');
  console.log(a.models.length,a.buildBrand,a.manufacturer);"  // 458 Apple Apple
md5sum app/src/main/assets/{devices,carriers}.json \
        third_party/Guise_Reborn-main/app/src/main/assets/{devices,carriers}.json   # 两两相同
node -e "const p=require('./third_party/Guise_Reborn-main/app/src/main/res/raw/presets.json');
  for(const k in p)console.log(k,p[k].length)"        # androidVersions 37 / sdkLevels 37 / densityDpi 13 / networks 6 / languages 13
```

基线数据资产全集就这些：**2 本 JSON + 1 本 presets**。基线**没有**传感器表、GPU/SoC 表、baseband 表、OUI 前缀表、版本权重表（grep 零命中）—— 所以「照搬」的可扩展范围是有穷的，本文不假装存在第三批基线数据。

### 2.6 R-4 侧验证缺口（六道，缺一即无证据）

1. **执行体**：强制档钩子一族都不存在（§2.1）⇒ 41 条设备档用例的**主语是空集**。
2. **靶机读回**：41 字段 × 多通路的 `L-ENV-DUMP-01` 不存在；权限不足。
3. **期望表 + 夹具**：`scripts/test-integration/v49-spoof-fixture.json`（一份填满 41 项的画像）与它的 JVM 对账测试均不存在（V47 计划的 `v47-spoof-expectations.json` 从未落地）。
4. **阶段与编排**：`28-run-spoof.ps1` 与 `run-acceptance.ps1 -Stage spoof` 均不存在。
5. **第三方观测档**：momo / NativeTest 只在 `23-run-smoke.ps1:12-15` 当冷启动存活对象用；`TASK-MOMO-ADB-TESTING.md:53-70` 已实锤它在无 Zygisk 机器上连检测阶段都进不去 ⇒ 只能作观测档，不能作门禁。
6. **运行中的框架**：V48-ENV-01，见 §2.8。

### 2.7 一条必须写下来的口径纠正

`crypto_records` 的 `maskedValue` **不是**生效证据。它是钩子在自己调用点交给 `emit()` 的字符串（`DeviceEnvBaseHooker.kt:51-89`），与「App 实际读到了什么」之间隔着四类已知漂移：ART 直读 `GETSTATIC` 绕过静态字段改写（`DeviceEnvCases.kt:101-102` 已记录）、门控未开导致钩子根本没装、第二个 hooker 后写覆盖、App 在掩蔽生效前已缓存值。V38 的「after-setResult 被拦截器丢弃」事故就是同一族问题的历史实锤。**任何以 `maskedValue` 单证冒充「已生效」的验收设计一律拒绝**（`AGENTS.md` 已把它写进 `maskedValue` 列的由来）。

### 2.8 环境前置 V49-ENV-01（R-4 的硬阻塞，需使用者处置）

V48 实测并三次登记：靶机 LDPlayer 的 Magisk/Zygisk 整场未运行 ⇒ LSPosed 与 Vector 两个 zygisk 模块同时不加载。判据四条全在设备侧（`/sbin`+`/dev/.magisk` 不存在、`grep -c zygisk /proc/$(pidof zygote64)/maps`==0、`ps -A` 无 magisk、`/data/adb/lspd/log/*` mtime 陈旧）；`su` 仍可用（雷电自带 `ldinit`）⇒ 「能 root」零指示力；**整机冷重启一次无效**。恢复 = 宿主 VM 的 Magisk/root 档位改动 = **共享设备侧动作，须先与使用者确认**（WP-0）。**在它恢复之前 R-4 不可能「一次性全量通过」，本文不许任何人在此条件下把画像设备档记 ✅。**

---

## 3. 设计决定

| ID | 决定 | 理由 / 被否决的替代方案 |
|---|---|---|
| **D-1** | **单轴分类**：底层来源族 = kind = 分区 = 钩子安装点 = MCP enum 值 = UI 开关行，四者 1:1；`ProfileSection` 枚举**删除**，`ProfileKind` 18 → 20（拆 `PROFILE_BUILD`、拆 `PROFILE_UNIQUE_ID`），分区标题资源键挂到 `CapabilityMap` 行上（`sectionKey`） | 需求 R-1 要的是「按底层类型」；一个 kind 一个闸一个钩子本就是 #23 FeatureGate 红线的形状。替代方案「再加一层大类」被否：那会造出第四份清单（违反 #31③），且解决不了「OTHER 混装两种机制」的实际困惑 |
| **D-2** | 20 组的分区标题在四语里**自带来源说明**（如 `Build 字段（android.os.Build）`）；标题集由 `ProfileField.entries` 派生，`CapabilityMapTest` 双向对账 | 「分类」若不能在屏上读出来，用户仍需凭记忆；派生而非新表是 ladder 第二级 |
| **D-3** | 画像 kind 在 WP-D **与钩子同一提交**登记进 `CategoryTaxonomy`/`DeviceEnvKind`（V47 3c 定档的原子链：kind 登记 + 操作枚举 + 发射该操作的钩子 + 冻结计数快照 + MCP enum + 四语） | V47 实测过：半批推进 = 仓库红色，主动退回是对的。V49 把它变成**批次纪律**而不是退回理由 |
| **D-4** | 每应用逐项微调 = **复用既有稀疏偏差机制**：`AppListenOverride` 加 `profileOverrides: Map<String,String>`（键 = `ProfileField` 的 `jsonKeyOf`），宿主侧 `expandProfile` 做 `画像 ⊕ 每应用偏差 → 普通 DeviceProfile 快照`；**`spoofOverrides` 旧语法整体迁移并删除**，注入侧协议 VERSION 恒 4 | #16 已把「每应用稀疏二态偏差 + resolve + 零协议改动」这条路走通并测透，另建一层是重复。**否**：给每应用做独立表单组件（第二事实源）、给 wire 加画像段（协议风险） |
| **D-5** | 资产文件**逐字节保持与基线相同**（md5 门禁），非 Android 平台在**唯一解码口** `parseDeviceCatalog` 处过滤；扫描门禁覆盖「解码结果 + 品牌集 + 随机源 + 内置档」四处可达面 | 既要「照搬」又要「无 Apple」，只有「文件不动、解码过滤」同时满足；改文件即破照搬、破 NOTICE 的 revision 对账、且 UI 侧过滤漏掉 `ProfileGenerators` |
| **D-6** | 基线 `presets.json` **作为资产进包并成为取值源**：`ProfileValueSets` 改为读它（37 对 release↔SDK 含 Android 17/37、13 档 dpi、6 槽 network）；**`languages` 的 13 条只取 label 作显示名，值域仍由平台表判定**；基线 2 张模板档**不取**（Modev 已有 9 内置档 + 草稿新建，模板机制与之重复） | R-3 明示「内置数据要求完全照搬基线」。这**推翻** V48 D-3「表尾恒停 API 36、禁预告不存在的版本」—— 重述的需求对冻结契约有否决权（既有记忆口径），但须同步改 `ProfileValueSetsTest` 与 `AGENTS.md`。语言不取第二份值域是 #机型库「禁再手抄第三份清单」不破 |
| **D-7** | 靶机 = **扩 `testtarget`**（同 `:testtarget` 模块，加 `EnvDumpActivity` + `L-ENV-DUMP-01` 用例 + 6 权限），不新建模块、不直接跑基线 `guise-test` | 基线 harness 要 compileSdk 37 + build-tools 37（本机只到 36）、`isDebuggable=false` 且 minify ⇒ `run-as` 通路全断；`testtarget` 已有 `TT_RESULT` 与全套脚本原语。基线 `guise-test/MainActivity.kt:96-167,362-472` 作为**读回点清单**逐条参照（它就是 R-4 要的「自建被作用域靶机」的成熟设计） |
| **D-8** | 画像设备档 **SKIP 集恒空**、`skip-allowlist.txt` 的画像条目整体删除；缺前置（框架、权限、作用域、夹具）= 用例自己造前置或整轮 BLOCKED，不得逐条 SKIP | 对伪装来说 SKIP 就是覆盖空洞本身。`21-run-enhance.ps1:223-224` 那两条「拿预伪装当真值」的 Skip 是本次要消灭的对象 |
| **D-9** | 三重 oracle 对账：`TT_RESULT`（靶程序所见）== 夹具期望（派生规则唯一落 `ProfileGenerators`/`ProfileCodec`）== `maskedValue`（钩子自称）。三者任一不一致即 ❌ 并归因 | §2.7；同时给「钩子装了但 App 没读到」和「App 读到了但钩子没记录」两种病留出各自的定位方向 |
| **D-10** | 遗留通路在 WP-D6 **删除**：`SpoofProfiles`（含 `BUILTINS`/`resolve`/`activeSpoofProfile`）、`spoofOverrides` 字段与其四语行、`profile_takeover_status_hint` banner 与键、`ProfileHookPlan`（其裁决逻辑迁入钩子基类的单一判据）、`profile-takeover-status` testTag | 接管完成后 banner 从「诚实」变「谎报未做」；两套值源并存就是 V49-DFC-02 的成因。**否**：保留旧通路「向后兼容」—— 本产品未发布过含画像库的正式版，兼容负担为零（`FeatureConfig` 迁移链已处理旧键） |
| **D-11** | 主流设备信息工具与检测工具 = **预登记观测档**（截图 + `uiautomator` 文本入 `docs/test-reports/v49-detectors/`），不作门禁；预期红项在跑之前写死在文档里 | 它们读 `/proc`、`sysfs`、`SystemProperties` 等我们本就不钩的路径；作门禁会把「不匹配」误读成「伪装失败」，而作零门禁的绿又会被误读成「全生效」—— 只有预登记 + 不判决是诚实的 |

---

## 4. 工作包

> 每包出口 = 出口准则全绿 **且** §8 台账有对应证据行（命令 + 时间 + 输出要点）。包内步骤按 TDD 序：先写会红的测试 → 跑它确认它按预期红 → 最小实现 → 跑它确认绿 → 提交。

### WP-0 · 环境前置恢复（R-4 的入口，需使用者确认）

**Files:** Modify `scripts/test-integration/00-env-verify.ps1:60-75`、`scripts/test-integration/11-vector-scope.ps1:43-58`

- [ ] **Step 1** 把运行期四判据提为**共库函数** `Test-FrameworkLive`（读 `/dev/.magisk` 存在、`grep -c zygisk /proc/$(pidof zygote64)/maps` > 0、`ps -A` 有 `magisk`、`/data/adb/lspd/log/*` mtime 在 30 分钟内），任一不满足 = 该步 `BLOCKED` 并点名缺哪一条。
- [ ] **Step 2** 删除 `00-env-verify.ps1:67-68` 的 `pm list packages | grep magisk` 判定（V49-DFC-09 的代理指标），改调 `Test-FrameworkLive`。**Step 3** `11-vector-scope.ps1` 的内联探针换用同一函数（禁两份判据）。
- [ ] **Step 4** 向使用者报告四判据实况并**请求授权**处置宿主 VM 的 Magisk/root 档位（共享设备动作）。未获授权 ⇒ 本文其余设备档全部 ⛔，任务不得声称完成（WP-A/B/C 不受影响，可先行）。

**出口：** 四判据全真 + `logcat -s HookInstaller` 出现「探针已安装」= 框架活。

### WP-A · 单轴分类（R-1）

**Files:** Modify `core/model/ProfileField.kt:8-38`（删 `ProfileSection`）、`:73-240`（41 条目去 `section` 参）、`core/model/ProfileKind.kt`（18→20）、`core/model/ProfileFieldCatalog.kt:57-67`（`bySection`→`byKind` 唯一分组）、`core/model/CapabilityMap.kt`、`ui/settings/profiles/ProfileEditorScreen.kt:491-527`、`ProfileEditorState.kt:41,201`、`ProfileEditorForm.kt`（`rowsBySection`→`rowsByKind`）、`ProfileLabels.kt`、`values*/strings.xml`（10 键退役、20 键新增）、`scripts/v42_table_*.mjs` + `node scripts/v42_generate.mjs`、`app/src/test/.../core/model/{ProfileFieldCatalogTest,DeviceProfilePropertyParityTest,ProfileLabelsTest,CapabilityMapTest,ProfileEditorFormTest,ProfileEditorStateTest}.kt`

- [ ] **Step 1（红）** 在 `ProfileFieldCatalogTest` 加三条穷尽断言：每个 `ProfileField` 的 `kind` 恰属于一个来源族；**同一 `kind` 内所有字段的来源族相同**（按新增的 `ProfileKind.sourceFamily` 声明）；分区标题资源键存在且四语齐。跑 `:app:testModernDebugUnitTest --tests "*ProfileFieldCatalog*"` 确认因缺 `sourceFamily` / 缺新 kind 而**编译红**。
- [ ] **Step 2** `ProfileKind` 拆两组：新增 `PROFILE_BUILD_IDENTITY`（8 字段）与 `PROFILE_BUILD_VERSION`（`BUILD_ID`/`ANDROID_VERSION`/`SDK_INT`），删 `PROFILE_BUILD`；`PROFILE_UNIQUE_ID` 拆为 `PROFILE_ANDROID_ID`（1）与 `PROFILE_TELEPHONY_ID`（`IMEI`/`PHONE_NUM`），删旧。每条目挂 `sourceFamily: ProfileSourceFamily`（新枚举，20 值，值即「要钩的 API 族」的字符串事实，如 `"android.os.Build"`、`"Settings.Secure/System.getString"`）。
- [ ] **Step 3** 删 `ProfileSection`：`ProfileField` 构造器去 `section` 参；`ProfileFieldCatalog.byKind` 成为唯一分组出口；`ProfileEditorForm.rowsByKind()` 直接委派 catalog（**禁**在 UI 侧再排一次顺序，#31③）。
- [ ] **Step 4** 四语：20 个 `profile_kind_<NAME>` 分区键（标题含来源括号），10 个旧 `profile_section_*` 键退役；先回写 `scripts/v42_table_*.mjs`，跑生成器直到控制台 **DONE**（#1；直写 `values-ja/ko` = V46 静默失效根因）。
- [ ] **Step 5（绿）** 全量单测 + `CapabilityMapTest`（此时画像仍不在 81 行内，D-3 决定它在 WP-D 进；本步只保证既有 81 行零漂移）。
- [ ] **Step 6** `grep -rn "ProfileSection\|profile_section_" app/src scripts` → 零命中（V48 强约束 #31⑥：删键的 grep 面 = 键名，含 androidTest）。
- [ ] **Step 7** 提交 `v49: single classification axis = source family = kind = section (R-1)`。

**出口：** 41 字段全部可经 `ProfileField.kind.sourceFamily` 追溯；屏上 20 个分区标题四语点名来源 API；`DeviceProfilePropertyParityTest` 四视图对账绿（第 5 视图 = 分区标题，新增断言）。

### WP-B · 内置数据：去 Apple + 照搬基线（R-3）

**Files:** Modify `core/model/DeviceCatalog.kt:144-152`（解码口过滤）、`core/model/ProfileValueSets.kt`（改读资产）、新增 `app/src/main/assets/presets.json` + `presets.NOTICE.txt`、`ProfileFieldCatalog.kt:234-243`（语言值域仍走平台表）、`app/src/test/.../core/model/{DeviceCatalogTest,ProfileValueSetsTest,AndroidOnlyDataTest(新)}.kt`、`app/src/androidTest/.../ProfileEditorFlowTest.kt`

- [ ] **Step 1（红）** 新 `AndroidOnlyDataTest`（纯 JVM，读真资产）：
```kotlin
@Test fun decodedCatalogCarriesNoApplePlatform() {
    val catalog = parseDeviceCatalog(readAsset("devices.json"))
    val brands = catalog.brands.map { it.key.lowercase() }
    assertTrue(brands.none { it == "apple" || it == "ipad" || it == "iphone" })
    val hit = catalog.brands.flatMap { b -> b.models.map { b to it } }
        .filter { (b, m) -> listOf(b.key, b.buildBrand, b.manufacturer, m.model, m.name, m.device, m.code, m.alias.orEmpty())
            .any { s -> APPLE_TOKENS.any { t -> s.contains(t, true) } } }
    assertEquals("Apple 记录必须不可达: ${hit.take(3)}", 0, hit.size)
    assertEquals(25, catalog.brands.size); assertEquals(8075, catalog.brands.sumOf { it.models.size })
}
```
（`APPLE_TOKENS = ["iphone","ipad","ipod","apple","watchos","mac-","airpods","appletv"]` 放测试内，产品码里只有一条品牌谓词。）跑它，确认因未过滤而红。
- [ ] **Step 2** `parseDeviceCatalog` 里一行收口（**唯一**过滤点，随机源/选择器/MCP catalog 工具都经它）：`brands.filterNot { it.key.equals("apple", true) }`，并在 `DeviceBrandEntry` 解码后加同谓词的注释指向本决定。禁在 `ProfileEditorScreen` 或 `SpoofProfileTools` 里过滤（那才是「可达即违规」的漏点）。
- [ ] **Step 3** 同测试类再加两条可达面扫描：`BuiltinDeviceProfiles.BUILTINS` 41 字段全值、`ProfileGenerators` 1000 种子的 device/model/brand 输出，均零 `APPLE_TOKENS` 命中（`board="pineapple"` 是骁龙 8 Gen 3 代号，**测试内显式豁免并注释理由**，禁把它算进违规）。
- [ ] **Step 4** 资产字节对账（新增 `AssetByteParityWithBaselineTest`，JVM）：`assets/devices.json`、`assets/carriers.json`、`assets/presets.json` 与 `third_party/Guise_Reborn-main` 对应文件 **MD5 相同**，且三本 `*.NOTICE.txt` 逐字节相同。`cp` 基线 presets 进包（禁改内容）。跑它确认 Step 4 之前因 presets 缺失而红。
- [ ] **Step 5** `ProfileValueSets` 改为**读 `presets.json`**（唯一读者，走 `CachedAssetReader<T>` —— 与 `DeviceCatalogRepository`/`CarrierCatalogRepository` 同一复用点，禁再抄 reader）：`androidReleases()`/`sdkLevels()`/`densities()`/`networkSlots()` 全部派生自它；语言 label 走 `presets.json` 的 13 条**仅作显示名**，`ProfileOptionSource.optionsFor(LANGUAGE)` 值集仍为 `Locale.getISOLanguages`（D-6）。
- [ ] **Step 6** `ProfileValueSetsTest` 改口径：表尾恒到 **API 37 / Android 17**（基线值，逐条 `ProfileFieldCatalog.validate` 通过）；`pickAndroidRelease` 成对写全仍成立。跑它，看到 36→37 的期望变更点逐个红→改→绿。
- [ ] **Step 7** 全量单测 + lint；`ProfileEditorFlowTest`（插桩）机型选择器用例补断言：品牌列表首项非 Apple、搜索 `iphone` 零结果（禁在设备上验 Step 1 的解码全量 —— 那是 JVM 档）。
- [ ] **Step 8** `AGENTS.md` 词汇表两行同步（机型库行加「非 Android 平台在解码口过滤，资产与基线逐字节同源」；画像选型库行的 presets 来源），提交 `v49: android-only reachable data, baseline datasets shipped verbatim (R-3)`。

**出口：** Step 1-6 测试全绿；APK 内 `unzip -l` 见三本数据 + 三本 NOTICE；`md5sum` 对账行贴进 §8 台账。

### WP-C · 每一处都能自定义（R-2）

**Files:** Modify `core/model/AppListenOverride.kt`（+`profileOverrides`、`clearDomain`/`deviationCountFor` 同步）、`core/model/EffectiveSwitch.kt`（resolve 双臂）、`core/model/FeatureConfig.kt`（删 `spoofOverrides`）、`data/repo/SettingsRepository.kt:260-291`（`expandProfile` = 画像 ⊕ 每应用偏差）、`domain/config/SpoofProfileService.kt`（`previewEffective(pkg)` 只读投影）、新增 `ui/apps/PerAppProfileTuneScreen.kt` + 路由 `apps/{pkg}/profile-tune`、复用 `ui/settings/profiles/ProfileFieldRow`（提到 `ui/common/`）、`mcp/tools/SpoofProfileTools.kt`（+`spoof_app_field_set`/`spoof_app_field_clear`/`spoof_app_fields_list`，工具面 50→52 或登记替换）、`ModevRoutes.kt`、四语表

- [ ] **Step 1（红）** `AppListenOverrideProfileFieldsTest`：41 项中**任取一项**写入即只脏那一项；`resolve` 产出的快照仅该字段异于所绑画像；`clearDomain(FEATURE)` 只清画像偏差、零跨域；零偏差 = 键被删（#16 的「零偏差即删键」同规）。
- [ ] **Step 2** 迁移旧 `spoofOverrides` 键：`build.MODEL`→`model`、`ro.*`→**丢弃并登记**（`DeviceProfile` 无 ro 族，V47 O-7；丢弃动作须一次性、有日志、幂等），写入恒为新格式。
- [ ] **Step 3** 每应用「画像微调」页 = `ProfileFieldRow` 同一组件、同一 catalog 派生顺序；未设置项显示为「取所绑画像：<值> / 未设置」，单击即开该字段的弹层（枚举 → 可搜表 + 「改为手动填写」，槽 → `ModevChoiceSheet`，文本 → `ModevInputSheet`，布尔 → 行内 Switch），底部「还原为画像值」。分区与 WP-A 的 20 组逐字相同（**同一张表**）。
- [ ] **Step 4** 门禁（源码扫描 + 行为）：`ProfileEditorRenderRoboTest` 补「渲染行数 == catalog 行数」在**两屏**（编辑器 + 每应用微调）都成立；新 `PerFieldIndependenceTest`：从空画像出发，逐个字段 `update(field, x)` 后 `canSave == true` 且 `snapshot` 仅含该项（41 条参数化用例，一次红一次绿都要看）。
- [ ] **Step 5** 复合选择器仍只是捷径：`ProfileEditorFlowTest` 三条既有对账用例（机型 / release+SDK / 运营商）保留，并新增断言 = 那四个字段的行内弹层各**仍可单独打开并保存**。
- [ ] **Step 6** 提交 `v49: every profile field customizable per-app, one row one field one action (R-2)`。

**出口：** Step 1/4/5 全绿；MCP 三个新工具过 `McpToolParityTest`（声明面即生效面 + 双域闸拒 LISTEN kind）；`UiMcpCapabilityParityTest` R2/R4/R5 双向归属对账绿。

### WP-D · 注入侧接管（R-4 的**前置**，按批次原子提交）

每批的原子清单（D-3，缺一即不提交）：`DeviceEnvKind` +N、`CategoryTaxonomy` +N（FEATURE 域、默认 false、SIGNAL）、`DeviceEnvOperation` +N + 四语操作标签、`HookSwitch` 布尔位 + `deviceEnvEnabled`/`anyDeviceEnvEnabled`、`EffectiveSwitch.readKind`/`applyKinds` 双臂、`FeatureGroups` 组、`KindLabelRes`、`KindIcons`、`RecordRow.deviceEnvKindLabelRes`、`InterventionGateConventionTest.gatedFields`、`HotPathGateConventionTest` 名单、冻结计数（`DeviceEnvKindTest` 21→、`CategoryTaxonomyTest` 三条、`McpToolsTest`）、MCP `feature_settings_set.kindEntries` enum、二态行为测试、**靶机读回用例**。

**每个新 hooker 的共同契约**（写进 `ProfileBaseHooker`，一次写好、七个文件复用）：
- 值源**只有** `config().feature.deviceProfile`（`ProfileField` 的 `stringOf/isSetIn/withText` 三臂桥接，禁反射、禁第二份键表）。
- 门控先行：`featureOn(kind)` = master ∧ kind；关 = **零副作用**（不写静态字段、不改 `Locale` 默认值、不注册类监视器）—— #23 FeatureGate 红线 + `InterventionGateConventionTest` 源码扫描。
- 发射即对账：`emit(originalValue = 真读到的值, maskedValue = 画像值, interventionAction = BLOCKED)`；`DETECTED` 恒不用于强制档（§2.7 的可追溯性要求）。
- 安装幂等 + 进程内一次（`EmulatorBuildHooker.kt:52-57` 的 idempotent 形态照搬）；冷启动早于 `Application` 时**不得**在构造期求 Context（V38 seq 续号事故的规约）。

- [ ] **D1 · `PROFILE_BUILD_IDENTITY`(8) + `PROFILE_BUILD_VERSION`(3)**：新 `ProfileBuildHooker` —— `Build` 静态字段写 + `Build.getSerial()/getRadioVersion()` 系 OEM getter + `Class.getField/getDeclaredField` 观测，值源恒为画像快照、**无条件**。**`EmulatorBuildHooker` 同批降级**：删其 `getRealisticValue`/`SpoofProfiles` 取值分支（`:136-155`），只保留「画像未填 且 `isEmulatorIndicator(current)` 才掩蔽」的纯条件档职责（与 `ProfileKind.kt:11-13` 自己划的界一致），两族不共享任一字段写入路径 —— 禁止在两个类里各写一份 `Build.MODEL`。红→绿判据：Robolectric 二态 + 设备档 `SP-#1..#2`。
- [ ] **D2 · `PROFILE_DENSITY`(1) + `PROFILE_CPU`(1) + `PROFILE_BATTERY`(1)**：`ProfileDensityHooker`（`Resources.getSystem().displayMetrics/configuration` 原地改 + `getDisplayMetrics`/`getConfiguration`/`Display.getMetrics`/`getRealMetrics` 四通路，重算 `density/scaledDensity/三 *WidthDp`，72–1000 钳制；基线 `DisplayDensityHook.kt:16-70` 为清单）、`ProfileCpuHooker`（`Runtime.availableProcessors` + `/proc/cpuinfo` 正文，**无条件**）、`ProfileBatteryHooker`（`BatteryManager.getIntProperty` + sticky Intent `EXTRA_LEVEL/EXTRA_SCALE→100`，**值来自画像**，删硬编码 67）。
- [ ] **D3 · `PROFILE_ANDROID_ID`(1) + `PROFILE_TELEPHONY_ID`(2) + `PROFILE_SIM`(3)**：`Settings.Secure`**与**`Settings.System` 双分支 `getStringForUser`（基线 `UniquelyIdHook.kt:37-44` 有 System 分支，Modev 缺）、`getImei/getImei(int)/getDeviceId/getAll/getTypeAllocationCode(=imei.take(8))/getLine1Number/SubscriptionManager`、SIM 三族 + `SubscriptionInfo` + 六类 `CellIdentity` 的 MCC/MNC 派生（基线 `SimHook.kt:22-107` 为清单）。**同批清偿 V49-DFC-10**：MAC/SSAID 发射器与 `ProfileGenerators` 同一处收口（GAP-07）。
- [ ] **D4 · `PROFILE_NETWORK_TYPE`(1) + `PROFILE_WIFI`(3)**：`NetworkInfo.getType/getTypeName/getSubtype/getSubtypeName` + `NetworkCapabilities.hasTransport` + `getActiveNetworkInfo/getAllNetworks`（「无网络」档语义）、`WifiInfo.getSSID/BSSID/MacAddress` + `getScanResults`（只改命中当前 BSSID 的那条，基线 `WifiHook.kt:17-59` 同裁决）。
- [ ] **D5 · `PROFILE_CELL`(2) + `PROFILE_LOCATION`(3) + `PROFILE_WIFI_LOC_HIDE`(1) + `PROFILE_CELL_LOC_HIDE`(1)**：`GsmCellLocation` + 六类 identity（基线 `CellLocationHook.kt:17-33`）、`Location.getLongitude/Latitude`(+`set*` 忠实回写)、`getLastKnownLocation/getLastLocation` + 每进程 `RANDOM_OFFSET`（基线 `LocationHook.kt:39-95`）、两个隐藏档按 `ProfileHookPlan` 的 R-B/R-C 优先序（裁决唯一落基类，基线把同一裁决写两处的反面教材不得重演）。
- [ ] **D6 · `PROFILE_LOCALE`(1) + `PROFILE_TIME_ZONE`(1) + `PROFILE_APP_VERSION`(2) + `PROFILE_SCREENSHOT`(1)**：`Locale.setDefault/LocaleList/Configuration.getLocales/Resources.getConfiguration/LocaleManager.getApplicationLocales`（基线 `LocalHook.kt:15-81` 清单，含 per-app 分支）；`TimeZone.setDefault` 双次（即时 + 一次 main-looper post，基线 `TimeZoneHook.kt:12-27` 的既有形态照搬，**零钩子**要在屏上说清）；`getPackageInfo` 四签名（仅目标自身包）；截屏三态**复用** `ScreenRecordHooker` 引擎补「强制禁止」方向（V47 D-14 同引擎，源码扫描锁「禁第二份实现」）。
- [ ] **D7 · `PROFILE_BLANK_CONTENT`(4) + `PROFILE_APP_LIST`(1)**：`ContentResolver.query`/`ContentProviderClient.query` 全重载 → 保留列名的空 `MatrixCursor`（基线 `BlankPass.kt:14-48`）；`PackageManager` 枚举族含 `getPackagesHoldingPermissions` 与 `ParceledListSlice` 就地过滤（基线 `ApplicationListPass.kt:28-203`），白名单记忆集行为测试。
- [ ] **D6x · 遗留通路删除（随 D1..D7 最后一批同提交）**：删 `SpoofProfiles`（含 `BUILTINS`/`resolve`/`activeSpoofProfile`/`SpoofProfile` 类）、`spoofOverrides` 全链、`ProfileHookPlan`（裁决迁基类）、`profile_takeover_status_hint` 键与 `BuiltinEditingHint` 旁的未接管 banner、`SpoofProfilesTest`；`EmulatorBuildHooker`/`DeviceSysPropHooker`/`SystemPropertiesHooker`/`DeviceCpuHooker`/`DeviceBatteryHooker`/`ProxyHideHooker`/`EmulatorNetworkHooker` 的取值改读画像快照或退为纯掩蔽。
- [ ] **每批收尾**：`:app:testModernDebugUnitTest --no-build-cache` 全量 + `:app:lintModernRelease`（基线 0e/43w/1h 零新增）+ 提交 `v49 D<n>: <kinds> profile-driven hookers + kind registration (atomic)`。

**出口（R-4 的前置）：** 20 组 / 41 字段各有「画像驱动无条件写入」的执行体；`InterventionGateConventionTest`/`HotPathGateConventionTest` 名单同步；**开关全关 = 零副作用**（含安装期 Build 静态字段）行为门禁绿；分类法 FEATURE 37→57、`DeviceEnvKind` 21→41（20 画像 kind + `ProfileKind` 并入，数字以实际批次为准并同步冻结快照的 5 处）。

### WP-E · 靶机读回（R-4）

**Files:** Modify `testtarget/src/main/AndroidManifest.xml`（+ `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`READ_CONTACTS`、`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_AUDIO`、`QUERY_ALL_PACKAGES`）、`cases/EnvCases.kt`（新）、`EnvDumpActivity`（新，参照基线 `guise-test` 的 50 key × 10 段）、`cases/TestCases.kt`（注册 `env` 域）、`scripts/test-integration/10-build-install.ps1:28`（授权清单）、`tt-lib.ps1`（+ `Get-EnvDump` 解析）

- [ ] **Step 1** 定清单：§2.2 最后一列全部 key（41 字段 × 多通路 = **80 个**）逐个落成 `Tt.result("L-ENV-DUMP-01", *pairs)`；**每字段至少两通路**（如 Build 走反射 `getField` + 直读 `Build.X`；ANDROID_ID 走 Secure + System；density 走 System/App/`Display.getRealMetrics`；IMEI 走 `getImei(0)/getDeviceId`）。
- [ ] **Step 2** 每个 collector `runCatching` 包裹并在失败时把异常类名+message 当值打出（基线 `MainActivity.kt:363-366,582` 形态）—— 这样「权限没给」与「没伪装」在证据上可区分，是本包最重要的防假绿设计。
- [ ] **Step 3** 同屏上渲染（`EnvDumpActivity` 一份 `LazyColumn` 文本表）：给第三方工具档与人工截图用，也让 `uiautomator dump` 能取到同一批值 —— 屏上文本与 logcat 值必须同源同函数（禁两份读数）。
- [ ] **Step 4** 冒烟：`adb shell am start -n com.lioisme.modev.testtarget/.EnvDumpActivity` + `logcat -d -s TT_RESULT` 在**未作用域**状态下取一次基线值，存为 `docs/test-reports/v49-baseline-dump.txt`（后续每条断言的「变了没有」参照物）。
- [ ] **Step 5** 注册进 `TestCases.kt` 的 `env` 域并跑 `23-run-smoke.ps1`（冷启动存活 + 无 FATAL/ANR，41 字段读回不得把靶机搞崩）。

**出口：** 80 个 key 一次打印齐、无 crash；基线值与夹具值集合两两不同（同值 = 探针假，立刻归因，见 §2.6 坑②）。

### WP-F · 设备档矩阵与一次性全绿（R-4）

**Files:** 新增 `scripts/test-integration/v49-spoof-fixture.json`、`28-run-spoof.ps1`；Modify `run-acceptance.ps1:20,255-349`（+ `spoof` 阶段并进 `all` 序）、`skip-allowlist.txt`（删画像条目）、`app/src/test/.../SpoofFixtureParityTest.kt`（新）、`24-run-mcp.ps1`（`M-ASSET-02a` 补「值真的到了靶程序」）

- [ ] **Step 1** 夹具 = 一份填满 41 项、**自洽**的画像 JSON（同机型世代 + 同运营商，走 `ProfileCodec` 与 `ProfileFieldCatalog.validate` 全通过；含 `fingerprint` 三元组自洽、`imei` Luhn 合法、MAC 非虚拟化 OUI）。
- [ ] **Step 2** `SpoofFixtureParityTest`（JVM）：夹具 key 集 **≡** `ProfileField.entries` 41（缺一即红，防「测了 39 个宣称 41」）；期望值派生规则**只**调产品码里的 `ProfileGenerators`/`ProfileCodec`，测试不写第二套派生逻辑。
- [ ] **Step 3** `28-run-spoof.ps1` 序列：装夹具（`spoof_profile_import`）→ `spoof_profile_apply` 绑 `testtarget` → `am force-stop` → 冷启动 → `L-ENV-DUMP-01` → 逐 key 与期望对账 → 同批对账 `records_query`（kind 集与 `maskedValue`，D-9 第三 oracle）→ 每应用单字段微调一条（R-2 第二层的设备证明）→ `:sub` 双进程一致性 → 重启后持久性 → 20 个 kind 逐个 off 的「零副作用」证据（含 `Build` 静态字段未被改写）。
- [ ] **Step 4** 编排接入：`-Stage spoof` 落在 `scope` 之后、`mcp` 之前；假绿防线照 V45 四坑（`Start-Process` + `WaitForExit`、JSON 数组解嵌套、禁用自动变量名、`BUILD SUCCESSFUL` 判绿）。
- [ ] **Step 5** **一次性全量**跑（§6 判据），任一不符 → `superpowers:systematic-debugging` 归因 → 修（修产品优先于修测试，除非测试本身是四种「静默不生效」形态之一）→ 复跑，直到单轮 41/41 + 三附加面全绿。
- [ ] **Step 6** 第三方观测档（D-11）：跑前把「预期红项清单」写进 `TEST-V49-SPOOF-ACCEPTANCE.md`，跑后截图与 `uiautomator` 文本入 `docs/test-reports/v49-detectors/`，只登记不判决。
- [ ] **Step 7** 契约同步同轮提交：`AGENTS.md` #30 改写（接管边界**取消**、遗留通路退役、新单轴分类条）、#31 加「底层类型 = kind = 分区」一条、词汇表「伪装画像 / 画像选型库 / 机型库」三行更新、`README.md` 功能域段。

**出口：** §6 判据全绿 + 台账证据行齐 + 文档同步（记忆口径：文档同步属同一轮，不留到以后）。

---

## 5. 验收矩阵（`TEST-V49-SPOOF-ACCEPTANCE.md` 的骨架）

| 档 | 组 | 项数 | 判定方式 |
|---|---|---|---|
| **编译/静态** | G1 全量单测 / lint / 三 APK / Room 链 / 四语齐 | 7 | `BUILD SUCCESSFUL` + 结果 XML；lint 基线零新增 |
| | G2 R-1 单轴分类 | 6 | catalog↔kind↔分区↔钩子↔MCP enum 五方对账测试；退役枚举零引用 grep；插桩屏上 20 分区 |
| | G3 R-3 数据 | 8 | Apple 零命中（解码集 + 品牌集 + 随机源 1000 种子 + 9 内置档）；三本资产 md5 == 基线；presets 五表逐条过 `validate`；进包 `unzip -l` |
| | G4 R-2 逐项自定义 | 9 | 41 条参数化「单字段可保存」；两屏「渲染行数 == catalog 行数」；复合选择器非唯一通路；每应用单字段设备生效 1 条 |
| **设备档（一次性全量）** | G5 20 组 / 41 字段读回 | **41** | `TT_RESULT` == 夹具期望 == `maskedValue`（D-9） |
| | G6 附加面 | 4 | 双进程一致 / 重启持久 / 全关零副作用 / 冷启动存活 |
| | G7 第三方观测 | 观测档 | 截图 + dump 文本 + 预登记红项（不判决） |
| **合计** | | **75 门禁项 + 观测档** | 41/41 与 4/4 必须同轮，见 §6 |

---

## 6. 「任务结束」的唯一定义（R-4 最后半句）

1. **单轮、一次性**：`run-acceptance.ps1 -Stage spoof`（或整轮 `all`）在**同一次连续执行**内 G5 = 41/41 且 G6 = 4/4；不允许「三轮各绿一部分再拼」。
2. **SKIP 集恒空**（D-8）：`28-run-spoof.ps1` 若有 Skip 被消费即 FAIL。
3. **⛔ 不算过**：框架不活 ⇒ 整轮 `BLOCKED`，此时本文任何设备档**不得记 ✅**（V48-ENV-01 教训）。
4. **双 oracle 一致**：靶程序读回值与宿主库 `maskedValue` 同值；只有 `maskedValue` 的用例即无效用例。
5. **归档**：结果 XML/JSON + 基线 dump + 检测工具截图入 `docs/test-reports/`，台账 §8 每行带时间戳与命令。
6. **文档同轮**：`AGENTS.md` 与 `README.md` 的接管边界条已改写为「已生效」，否则代码绿也不算交付。

---

## 7. 风险登记

| ID | 风险 | 影响 | 处置（不得自行吸收） |
|---|---|---|---|
| **RISK-1** | 内置数据来源：`devices.json` 上游 **CC BY-NC-SA 4.0（非商业 + ShareAlike）**，基线代码 **GPLv3**，Modev 根目录零 LICENSE、README 零数据出处 | 若 Modev 有任何商业/收费形态，「完全照搬基线数据」这一条本身就与上游许可证冲突；ShareAlike 还会向派生数据集传染 | **需使用者定夺**（三选一：确认非商业并保留 NOTICE；换可商用数据源并破 R-3 照搬条；取得上游授权）。本文按现状继续（保留 NOTICE + 加 README 鸣谢），不改产品定性 |
| **RISK-2** | V49-ENV-01：靶机框架不活 | R-4 整档不可证；WP-E/F 白跑 | WP-0 硬闸 + 使用前确认；恢复是共享设备动作 |
| **RISK-3** | 强制档无条件改写 `Build`/`Locale`/`Location` 等，比现有条件档危险一个量级 | 靶 App 崩溃/ANR/静默行为漂移（V36 D-17 类事故：钩了依赖调用方栈帧的方法会启动即崩） | 每批必过 `23-run-smoke.ps1`（5 App × 全开关 ON）；新钩子逐条评估「是否依赖调用方帧 / 是否热路径无条件 / 关闸后是否仍有开销」；`Class.forName` 系恒不钩 |
| **RISK-4** | 20 个 kind 登记 = 冻结快照 5 处 + 四语 ≈60 键 + MCP enum，中途红窗口长 | 仓库长时间不可提交 | D-3 批次原子性；每批 ≤4 kind；`--no-daemon --max-workers=1`（本机 commit-charge OOM 史） |
| **RISK-5** | `pickAndroidRelease` 到 API 37 与「本机 SDK 36」并存 | 选到 37 = 靶机上报一个本机从未有过的版本（自洽性问题，非缺陷） | 在屏上说清「这是要伪装成的版本」；`ProfileValueSetsTest` 记录 D-6 的口径反转 |
| **RISK-6** | `PASS_APPLICATIONS` 的白名单记忆集会误伤（系统应用判定） | 靶 App 看不到自己的组件 ⇒ 行为变了但没崩，难归因 | 白名单集行为测试 + 观测档单独一条；不给它「默认开」 |

---

## 8. 执行台账（回填用，跑一条记一条）

| 时间(CST) | WP / G# | 命令 | 输出要点 | 结果 |
|---|---|---|---|---|
| | WP-0 | `00-env-verify.ps1 -Mode runtime` | 四判据逐条 | ⬜ |
| | WP-A | `:app:testModernDebugUnitTest --tests "*ProfileFieldCatalog*"` | | ⬜ |
| | WP-B | `md5sum` 三本资产对账 | | ⬜ |
| | WP-C | `PerFieldIndependenceTest` | 41/41 | ⬜ |
| | WP-D1..D7 | 逐批：全量单测 / lint / smoke | | ⬜ |
| | WP-E | `L-ENV-DUMP-01` 基线 dump | key 数 = | ⬜ |
| | G5 41 条 | `28-run-spoof.ps1` | 41/41 单轮 | ⬜ |
| | G6 4 条 | 同上 | | ⬜ |
| | G7 观测 | 检测/信息工具 | 截图目录 | ⬜ |

---

## 9. 契约同步清单（改代码同轮做，#31 与记忆口径）

- [ ] `AGENTS.md` #30：删「`hook` 子树零消费者」段，改写为「画像快照是注入侧唯一值源；`SpoofProfiles`/`spoofOverrides`/`ProfileHookPlan` 已退役、禁复活」；`profile_takeover_status_hint` 条目整段删除。
- [ ] `AGENTS.md` #31：加第 ⑦ 条「画像分区 = 底层来源族 = kind = 钩子族 = MCP enum，五者 1:1，由 `CapabilityMap` 承载」；② 补「一项配置一行一动作，复合选择器禁成为唯一通路」。
- [ ] `AGENTS.md` 词汇表：「伪装画像」行重写（两套并存 → 一套）、「画像选型库」行补 presets 与 Apple 过滤、「机型库」行补「非 Android 平台在解码口过滤，资产与基线逐字节同源」。
- [ ] `AGENTS.md` 已知工具链坑：新增「`maskedValue` 不是生效证据」与「无框架时画像设备档恒 ⛔」两条（若本轮真踩到新的静默形态，按 V48 那四条的写法登记）。
- [ ] `README.md` 功能域段与能力计数。
- [ ] `TASK-V47-SPOOF-PLAN.md` 台账：WP-3c/3d/4/8/9 指向本文（避免两份任务书各说一半）。
