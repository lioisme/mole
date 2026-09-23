# AGENTS.md

Modev — Kotlin/Compose (Material3) Xposed 模块,双域架构:监听域(算法/数据库/行为/偏好/WebView/系统信息 + 环境观测)+ 功能域(退出拦截/崩溃预防/解除录屏/弹窗关键词拦截/SSL 绕过/代理 VPN 隐藏/设备环境伪装)+ 宿主双进程日志。
构建与质量契约(2026-08-20 审计沉淀 V21;2026-08-25 V24 二态监听/记录收敛/详情重设计增量沉淀;2026-08-26 V26 详情页 markdown 化/外部监听开关/诊断详情弹层增量沉淀;2026-08-26 V27 记录列表重构/行为监听一等公民增量沉淀;2026-08-26 V28 详情页纯 markdown 化/偏好监听一等公民增量沉淀;2026-09-04 V37 双域拆分/FeatureGate 红线/关键词库与画像契约增量沉淀;2026-09-05 V38 MCP 服务/工具面 parity/注入侧 seq 续号修复增量沉淀;2026-09-06 V39 MCP 端口 1510/多令牌/服务诊断拆分/对话框统一/Appbar 排他/双击退出增量沉淀;2026-09-07 V41 令牌页崩溃修复/统一外部访问双服务消费/端口行纯端口化/副标题动态有效值/按应用浏览退役/根页 IA 五分区/DAO 直调门禁/全局记录路由 e2e 增量沉淀;2026-09-08 V42 搜索槽长按门禁/AppIcon 底图状态驱动/端口可用性校验链/主题色板回退 v8/四语 i18n 增量沉淀;2026-09-13 V43 宿主零阻塞锁链修复/热路径门控先行红线/单一类加载监视器/干预 setThrowable 契约忠实化/列表一行一参数重构增量沉淀;2026-09-22 V46 记录行圆角容器化(尾栏槽退役 ⇒ 正文通栏、波纹可见)/色板网格步长单点/令牌动作菜单化(查看·重命名·删除 + 契约层改名)增量沉淀;2026-09-23 V48 作用域更名(「作用域此应用」)/能力表前后端逐类对齐(强约束 #31)/画像选型库(carriers + 成对选择器 + 九内置档)+ 设备档插桩全量 136 用例 0 失败(六种静默红点归因入「已知工具链坑」;注入矩阵因靶机 Magisk/Zygisk 整场未运行而阻塞,见 V48-ENV-01)增量沉淀)。

## 构建 / 测试(Windows,gradlew.bat)

```powershell
# 全量单测(含 Robolectric:Room / LocaleManager / 资源一致性 / 数值格式等)
.\gradlew.bat :app:testModernDebugUnitTest

# 指定测试
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.i18n.*"

# 打包(modern 双框架变体;legacy 用于旧 LSPosed;build-all.ps1 打 4 个 APK)
.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug

# 设备 UI 测试(编译 + 安装运行;需已连接设备;指定类可传
# -Pandroid.testInstrumentationRunnerArguments.class=类A,类B,单方法用 类#方法)
.\gradlew.bat :app:assembleModernDebugAndroidTest
.\gradlew.bat :app:connectedModernDebugAndroidTest

# lint 门禁(基线:**43 warning / 1 hint / 0 error**,V46 实测复核;此前 40/41/43 三种
# 口径并存,一律以本次实测为准 —— V46 三项 UI 改动净新增 = 0,无一条落在 V46 触碰文件;
# 提交前必须跑,禁止新增 warning)
.\gradlew.bat :app:lintModernRelease
```

- 前置:JDK 17+(本机 21)、SDK android-36、build-tools ≥34(见 build-all.ps1)。
- 变体名:`modern`(默认,Vector 现代加载)/ `legacy`(Xposed API 82)。
- 设备 UI 测试后 AGP 会卸载被测 APK;失败详情在
  `app/build/outputs/androidTest-results/connected/debug/flavors/modern/TEST-*.xml`
  (PowerShell `[xml]` 解析,`failure` 是字符串属性)。

## 词汇表(全 App 统一,禁止旧词残留)

| 词 | 含义 | 禁用旧词 |
|----|------|----------|
| 记录 record(s) | 算法监听捕获条目 | 日志 log(s) |
| 数据库监听 | SQL 门面/语句/游标结果采集能力(5 类:open/query/DML/exec/transaction) | SQL 注入日志、db hook 日志 |
| 运行中 Running | 进程存活态 | 在线 Online |
| 已作用域 / 未作用域 | Hook 作用域态 | 在作用域 / 未在作用域 |
| 已接收 / 丢弃 / 被拒 / 写入失败 / 超容量裁剪 | 统计区文案(原因括号后缀) | 队列溢出丢弃等 |
| 默认配置 | 内置推荐值 `HookSwitch.DEFAULT`(无 UI;`switch_global` 仅为注入侧兜底种子,init 强制重置) | 全局设置(联动语义)、跟随全局 |
| 自定义偏差 / 恢复默认 | 每应用二态覆盖(V24)与清除动作 | 覆盖=三态、强制开/关、重置为全局 |
| 代码组 CodeGroup | 多表示字节区块的标签页代码块(mikepenz codeFence 扩展点聚合连续 text/base64/hex 围栏) | 三态分段、SegmentedButton(V26 退役) |
| 外部访问 External Access | **统一开关**(V41 D-1):wire+MCP 双服务同绑,服务页首「通用」分区;启用副标题 = 0.0.0.0 / 停用 = 127.0.0.1(动态);端口行仅显示端口,`IP:端口` 完整形态仅诊断复制快照保留 | 监听 IP、开放端口、MCP/wire 独立外部访问开关、双键分裂 |
| 行为监听 Behavior Listen | 文件读/写/删/重命名/列目录 + Assets 资源 + Shell 命令三类域(V27 一等公民,与算法/数据库并列) | 文件日志、hook 日志 |
| 内容捕获 fileContentCapture | 仅「随调用即可得」的整读 API 正文预览(Files.readAllBytes/readAllLines/write/copy 与 AssetManager.open 元数据;流式路径绝不触碰句柄) | 文件内容抓取、全量读取 |
| 监听域 Listen Domain | 被动采集能力域(算法/数据库/行为/偏好/WebView/系统信息 + 请求监控/检测监控/弹窗·控件·Activity 观测);`CategoryTaxonomy.domain = LISTEN` | 把干预功能称「××监听」 |
| 功能域 Feature Domain | 主动干预能力域(退出拦截/崩溃预防/解除录屏/弹窗关键词拦截/SSL 绕过/代理 VPN 隐藏/Root·Xposed·模拟器伪装);`domain = FEATURE` | 伪装成监听类型 |
| 关键词库 Keyword Library | 全局弹窗拦截关键词资产(Room `keyword_library`,text 唯一 + matchMode + 全局启停);每应用默认跟随全局,可显式子集 | 仅 override 扁平词表、无库 |
| 伪装画像 Spoof Profile | **两套并存,以 V47 画像库为准**:①画像库 `spoof_profile` 表(Room v15)= 41 项 `DeviceProfile` 正文 + **九内置档账本(V48 D1:3→9 档,机型身份 13 项,D-7 只填身份不填现场)** + DIY + 草稿新建 + 绑定唯一入口 `spoofProfileId`(= 库主键或 `""`);②接管前遗留通路 `SpoofProfiles`(auto/pixel7pro/xiaomi13 常量档)+ `spoofOverrides` 字段覆写,仍是**当前实际生效**的取值源。机型字面量唯一事实源仍是 `SpoofProfiles` 与 `BuiltinDeviceProfiles` 各自承担的两层(钩子禁内置)。**当前边界(实至名归的分界,不是待办)**:画像库已完成「取值 / 校验 / 编辑 / **选型(选而非填,V48 R-3)** / 传输 / 绑定 / 下发」——即 `FeatureConfig.deviceProfile` 快照经 `SettingsRepository.expandProfile` 随有效配置推到注入进程;但 **`hook` 子树零消费者**,注入侧改写仍全部走 `SpoofProfiles.resolve(spoofProfileId, spoofOverrides)`。说「画像已生效于靶程序」即假;`SpoofProfileLibraryScreen` 顶部 `profile-takeover-status`(键 `profile_takeover_status_hint`)那一句**就是这条边界的 UI 兑现,撤掉它 = 撒谎** | 把画像库称已完成、把遗留档称退役(两者都还没)、`spoofOverrides` 称已合并(未合并,写面已冻结而已) |
| 能力表 Capability Map | 前后端**逐类对照的唯一投影**(V48 R-2,`core/model/CapabilityMap.kt`):81 行 kind →(记录类型, 域, **分区资源键**, master 位名, 记录路由常量, 探针类名)。行集恒由 `CategoryTaxonomy.all()` 派生、功能域分区恒由 `FeatureGroups.kindRefs()` 派生 ⇒ 本文件**零 kind 字面量、不是第二份清单**;`CapabilityMapTest` 双向对账(表↔分类法/`R.string`/`ModevRoutes`/`hook/probes` 源文件)并冻结 81/44/37 与**三张跨域类型**(NETWORK / DEVICE_ENV / UI_INTERACTION 同类型内两域混居)。`sectionKey`/`routeKey` 存**名字**不存 `R.string` Int(core 子树零 android) | 在页面里再拼一份"哪些类归本页"(旧监听页自拼跨域清单即 V48-DFC-03 根因)、把表当文档不做门禁 |
| 画像选型库 Profile Picker 库 | 41 字段中**取值域有限的每一项都不许让用户凭记忆打字**,四本目录各有唯一通路:①**机型库**`devices.json`(见下行)写身份四项;②**运营商库** `assets/carriers.json`(1597 条,MIT,`carriers.NOTICE.txt` 记上游 revision,禁改文件):`core/model/CarrierCatalog.kt` 纯数据 + `parseCarrierCatalog` 唯一解码口 + `applyCarrierPreset` 一次写全 SIM_OPERATOR/SIM_OPERATOR_NAME/SIM_COUNTRY,**国家码恰两字母才写**(数据集实有 77 条 `"AU/CC/CX"` 多地区串,拆/猜即自造非法值 ⇒ 恒留空);③**平台表** `ProfileOptionSource.optionsFor`(语言/SIM 国家/时区 + **V48 新增密度档与核数档**);④**事实表** `core/model/ProfileValueSets`(Android release↔SDK **37 对**,表尾恒与基线同尾 = Android 17 / API 37,V49 D-6 以「内置数据照搬基线」反转了 V48「止于已知最高 API 36」定档,逐行同源由 `BaselineDataParityTest` 直读 `third_party/.../res/raw/presets.json` 钉;**唯一不照搬的是基线 release 字面量**(`6` 之类),因真机 API 23 上报 `6.0`,照搬即自造检测点,差异逐条登记在 `releaseLiteralDeltas`;13 密度档与基线零差异 / 6 核数档为 Modev 超基线项)。**成对字段走复合动作、不走 `optionsFor`**:`pickAndroidRelease`(release+SDK 同写)、`pickCarrier`、`pickDevice`。**`CarrierCatalogRepository` 与 `DeviceCatalogRepository` 共用 `data/repo/CachedAssetReader<T>`**(一次读盘 / null 不缓存 / 冷却窗 / `attempts` 计数)——加第三本随包目录时必须复用它,禁再抄一份 reader | 让任一有限域字段只可手填、把 countryCode 直写进画像、为运营商/密度另建第二张清单、把 SDK_INT 单独可写而与 release 脱钩 |
| 作用域 Scope（V48 R-1） | 本产品**唯一的顶层闸**:框架级 Xposed 作用域成员资格。动作行文案恒为「作用域此应用」(`app_monitor_scope_row`,四语同轴),它管辖两域共 81 类开关 + 全部干预;状态呈现词 = 已作用域 / 未作用域(`detail_listening`/`detail_not_listening`,键名保留历史形态、值已迁)。禁用旧词:**监听此应用**(把管辖干预的动作称作监听,`DomainVocabularyTest` 四语常驻拦截);第二控件(应用列表行尾 Checkbox)与监控页开关共享同一状态、同一名词、同一写路径(`app-row-scope` testTag + contentDescription)。移出作用域无独立确认入口(恒由开关的 off 分支承担),`removeScope()` 与 `danger_remove_scope_msg` 已随死路删除,**禁止复活** | 「监听此应用」再版、给行尾 Checkbox 换个名词、恢复移出作用域二次确认 |
| 机型库 Device Catalog | 随包 `assets/devices.json`(源自 KHwang9883/MobileModels-csv,**CC BY-NC-SA 4.0**,修订号与出处逐字记在 `devices.NOTICE.txt`,禁改文件 / 禁minify;**且与基线 `third_party/Guise_Reborn-main` 同目录文件逐字节相同,MD5 由 `BaselineDataParityTest` 钉** —— 「照搬基线内置数据」是可被静默违反的事,只能靠对账测试维持):品牌 → 型号的**身份选择器**唯一数据源(`core/model/DeviceCatalog.kt` 纯数据 + `domain/config/DeviceCatalogService` 契约 + `data/repo/DeviceCatalogRepository` 唯一读者与进程内缓存)。**非 Android 平台在 `parseDeviceCatalog` 一处剔除**(V49 R-3:上游收全平台机型,`apple` 品牌 458 条含 iPhone/iPad/Apple Watch/Apple TV,写进 `Build.BRAND` 就是造一台从不存在的设备;资产不动、可达集剔,`DeviceBrandEntry.isNonAndroidPlatform()` 是唯一谓词,`AndroidOnlyDataTest` 五面扫「解码集 / 逐条机型记录 / 9 内置档 41 字段 / 随机源 2000 种子 / 遗留 `SpoofProfiles`」——**判据是可达即违规,不是「文件里没有」**)。可达集实测 **25 品牌 / 8,075 机型**(= 基线 26/8,533 减 apple)。扫描面两条显式豁免:`pineapple` = 骁龙 8 Gen 3 板级代号、`darwin` = 锤子 `hardware` 与荣耀机型代号(V49 WP-B 首跑被这两条真 Android 数据判成假阳性,别再往 token 表里加常见词)。编辑器「选择真实机型」行一次写全 BRAND/MANUFACTURER/MODEL/DEVICE;**PRODUCT / BOARD / HARDWARE 恒不猜**(上游数据不提供这三个值,凑一个就是自造真实机型从不存在的字段组合);读不到资产 = `Unavailable` 上屏点名,禁退化成「库里没机型」。**另三项枚举字段(语言 / SIM 国家 / 时区)候选表唯一来源 = `core/model/ProfileOptionSource`**(直接取 `Locale.getISOLanguages` / `Locale.getISOCountries` / `TimeZone.getAvailableIDs` 平台表,**禁再随包或再手抄第三份清单**;`ProfileOptionSourceTest` 逐值断言平台表恒过 catalog 校验,选择器 sheet 首项恒留「改为手动填写」—— 做列表不得顺手夺走手填)| 拿 MODEL 或 DEVICE 猜 PRODUCT、把 devices.json 当画像正文来源、UI 侧再拼一份 `model (name)` 呈现串、为语言/时区另建一张本地清单、**把 Apple 记录当可选机型(或在 UI 侧过滤而不收口在解码口)** |
| 检测黑名单追加项 extra* | Root 包名/Xposed 前缀/Frida 端口/掩码词的用户追加项(内置基线 ∪ 追加,只增不删) | 覆盖内置基线 |
| MCP 服务 MCP Server | 进程内常驻 Streamable HTTP(spec 2025-06-18,V39 起端口默认 **1510**(wire 迁 1511 让位),单端点 POST /mcp,偏好 `mcp_port` 可改);监听绑定随**统一外部访问开关**(V41 D-1,见 #19);令牌认证独立开关(V39,默认停用)启用后**全接口强制**(含回环),服务端是否鉴权仅由 `tokenAuthEnabled` 决定(`McpServer` 无 externalAccess 耦合);多令牌 `McpTokenEntry`(SecureRandom 32 hex,宿主本地 `mcp_tokens` 不镜像,V38 单键惰性迁移);与 wire ConfigServer 完全独立生命周期(独立端口/开关/restart,互不触碰,VERSION 恒 4 零感知) | 把 MCP 当 wire 通道扩展、恢复 resources/prompts 双通道、令牌暴露为 MCP 工具(引导悖论 + PII)、恢复外部访问↔认证级联、恢复 mcp_external_access 独立键 |
| 令牌管理 Token Management | MCP 访问令牌管理页(`settings/mcp-tokens`):认证开关 + 多令牌列表(**行内零动作按钮,动作唯一入口 = trailing「更多」菜单三项:查看 / 重命名 / 删除**,V46 R3;创建走 Appbar `tokens-add`);重命名走 `McpServiceConfig.setMcpTokenLabel` 方法层契约(仅动 label,值与顺序不变 ⇒ 校验集内容零变化,经 `mcpTokenSetUpdater` 原地生效,不重启不踢会话);**行内恒掩码,明文只在 `ModevValueDialog` 一处上屏**(复制后关窗);删除最后一枚不停用认证(空集 = 全拒);令牌值宿主本地秘密,恒不进 MCP 工具输出与诊断快照 | 显隐(行内 toggle)/行内复制按钮/「撤销」措辞/`token_show`·`token_hide` 键(V46 全部退役);UI 直写 prefs 改名 |
| 工具面 Tool Surface 50 | MCP 对 Agent 的能力清单(记录 14/应用 6/监听 4/功能 4/资产 10/画像库 5/服务 4/作用域 3),与 UI 能力逐行对照、同上游继承;`McpToolParityTest` 冻结快照 + B1 边界源码扫描 + `UiMcpCapabilityParityTest` 能力归属与动作单点(R1-R6)三门禁(V45 T3.5:`app_clear_records` 与 `records_clear_package` 同上游重复已整体删除,应用组 7→6;V47 WP-7:画像库五工具 = 存/删/绑/目录/导入,`spoof_profiles_list` 改读画像库真身;V48 E1:`spoof_profile_catalog` 由零参数升为 `kind=fields\|devices\|carriers` 三本目录分派(**名仍 50**,参数字段声明新增;`kind` 带 enum ⇒ 乱值 = -32602;每行回 `writable` 对象 = UI 选择器真正落的那几项,读不到资产回 `available:false` + reason,**禁**回空表) | 「MCP 子集」「UI 专属阉割」(§5.3 明示排除除外)、「同一动作两处广告」(R1 红) |
| 端口校验 Port Validation | 端口编辑可用性链(V42 T3,强约束 #29):`ModevPortSheet` 异步校验 → `PortValidator`(特权 <1024/双服务冲突/占用探测三闸 + 自身豁免),失败硬阻止保存;wire/MCP 双 `bindError` 观测面 + 服务页对称错误行 | 恢复静默保存(无校验)、特权端口可录入、双服务同端口、wire 绑定失败不可见 |
| 语言 Language | 四语 en/zh/ja/ko(V42 T5):枚举唯一事实源 `LocaleManager.SUPPORTED_TAGS`(`locales_config.xml` 同表);选择面板 5 项(system + 四语,自名键 `lang_*`);values-ja/values-ko 为**生成物**(`scripts/v42_table_*.mjs` + `node scripts/v42_generate.mjs`;V46 复核实测 en 695 键、plurals 8 —— 旧记 701 为 V42 时点口径,以生成器控制台输出为准;技术词回落 en);ja/ko plurals 仅 other;`AppCountFormat` zh/ja/ko 同 10^4 万进位档;`TimelineGroup.formatDay` ja/ko 母语惯形分支 | 三语硬编码枚举(TAG 单常量散落)、ja/ko 资源缺键静默回落 en、日韩日期走 MMM 截断形态、**直写 values-ja/ko 而不回写翻译表**(V46 实锤的静默失效根因) |

## 强约束(改了会挂测试/降级质量)

1. **i18n(V42 T5 四语升级)**:用户可见文本一律走 `values/strings.xml`(en)+
   `values-zh`/`values-ja`/`values-ko`(四语);新增键必须四语齐备、占位符形态
   一致(`StringResourceParityTest` 四语循环 + plurals 逐 locale 标识符断言强制);
   ja/ko plurals 按 CLDR 仅 `other` 量项;ja/ko 与 en 同文仅限
   `IDENTICAL_ALLOWED`(+ ja/ko 例外 `confirm`="OK" —— 生成器
   `scripts/v42_generate.mjs` 同源);**`values-ja`/`values-ko` 是生成物,新键必须回写
   `scripts/v42_table_*.mjs` 再跑 `node scripts/v42_generate.mjs`;禁直写生成物**
   (V46 实锤:V43 T4/V45 期间 7 键 `record_field_app`/`meta_exit_code`/`meta_pid`/
   `record_action_blocked`/`record_action_detected`/`record_value_no`/`stats_bytes_dropped`
   被手写进 ja/ko 而未回表 ⇒ 生成器抛 `missing from translation table` 静默失效数周,
   已按现值回填恢复;新增键后必须实跑生成器看到 `DONE` 才算四语齐备);
   `ui` 包源码字符串字面量禁止中文
   (`HardcodedStringConventionTest` 强制)。
   语言解析:`LocaleManager.resolveTag` = App 内显式选择优先,仅「跟随系统」
   时读系统 per-app locale;支持语言枚举唯一事实源 = `LocaleManager.SUPPORTED_TAGS`
   (en/zh/ja/ko,`locales_config.xml` 同表双处同步);重建路径须经
   `MainActivity.attachBaseContext` 调 `LocaleManager.refreshForRecreation`;
   locale 敏感值用 `remember(context)`(包 Context 换实例,不残留旧 locale)。
2. **数值格式**:记录数/统计用 `AppCountFormat`(locale 驱动;zh ≥1万、其他 ≥1K
   平台紧凑格式 ≤1 位小数,以下千分位)。禁止自造单位(万+/千+/K+ 后缀)。
   资源格式串大数用 `%1$,d`。
3. **详情页 markdown 渲染(V26,取代 V21 三态双层契约)**:全部内容区块
   (元数据表/字节/参数/堆栈/SQL)以单一 markdown 文档经 mikepenz
   `multiplatform-markdown-renderer-m3` 渲染(`RecordMarkdownContent` 单管线);
   多表示字节 = 连续 text/base64/hex 围栏聚合为**代码组标签页**(文案复用
   `section_repr_*`,选中态 `rememberSaveable` 按 runFirstOrdinal 记忆),
   单表示退化为单代码卡;Hero 徽章卡与锚点芯片保留原生(D1 边界)。
   版本锁定 **0.38.1**(stdlib 2.2.21 与 Kotlin 2.2.20 同 minor;0.39+/0.44 需
   stdlib 2.3/2.4 超编译器 metadata 上限,**禁升**)。显示上限
    `MARKDOWN_DISPLAY_MAX_CHARS=32Ki`(工厂钳制「所见」,标记行 plurals
   `detail_truncated_marker` 恒置于全部围栏之后——夹在围栏间会打断代码组聚合);
   完整内容经 `DetailFenceHandle` 全量句柄走复制(4M 护栏)/全屏查看器
   (`FullscreenTextDialog`,`ChunkedText` O(1) 分块虚拟化);导出 32M 护栏不变。
   工厂(`RecordMarkdownFactory`)纯 Kotlin 零 UI import,本地化文案参数注入;
   围栏安全 = 内容反引号最长 run+1;GFM 表格用紧凑管道格式 `|Cell|`(解析器会
   把 '| Cell |' 的管道前空格留成单元格尾随字符)。区块恒 1 个 LazyColumn item,
   锚点簿记契约(mark/advance 与 DSL 发射严格同序)不变;
   **LazyColumn 必须显式 `state = listState`**(V26 曾遗漏致锚点滚动静默失效,
   回归测试 `AnchorScrollRoboTest`)。
4. **输入即底部 sheet**:带键盘输入的弹层一律 `ModalBottomSheet`
   (`imePadding` 即可——M3 已内部消费导航栏 inset,**禁止再叠
   `navigationBarsPadding`**,双内边距叠加 = 弹窗底部黑带根因 V21 T2;
   弹窗显式 `containerColor = surfaceContainerHigh`、宿主页 Scaffold 显式
   `containerColor = surface`,hide 动画后回调);
   仅危险确认/关于类保留居中 `AlertDialog`。诊断信息完整值弹层
   (`DiagnosticsDetailSheet`)同规;`SettingsInfoItem` 可选参
   `valueMaxLines`/`onClick`(onClick 追加 chevron,null 零波及既有调用)。
5. **弃用 API 零容忍**:新增源码一律不得引入已弃用 API;`getInstalledApplications`
   走 `core/util/PackageManagerCompat.kt` 统一入口(API 33+ `ApplicationInfoFlags`,
   旧系统弃用分支);`getLaunchIntentForPackage(String)` 全 API 无 flags 重载且从未
   弃用,直接使用即可。`lintModernRelease` 零新增(基线 43 warning/0 error,V34 清理 39 死资源,
   见上文构建命令)。
6. **计数文本复数**:en 带 `%d` 的计数文案用 `<plurals>`(one/other),zh 仅
   `other`;禁止 `<string>` 直接内嵌 `%d + 英文单词`(PluralsCandidate lint)。
7. **Xposed 模块识别双通道**:`isXposedModule` 判定 = 通道 A(`installedApplications`
   一次 `GET_META_DATA` 取 meta-data 含 `xposedmodule` 或 `xposedminversion`,
   零额外 binder)→ 未命中包走通道 B(惰性 zip 只读中央目录查
   `assets/xposed_init` / `META-INF/xposed/java_init.list`,异常降级非模块,
   按包缓存,随 `refresh()` 失效)。`AppListSettings.showModules`(偏好键 `show_xposed_modules`)
   默认
   **false**(模块行默认隐藏,Modev 自身亦隐藏);测试需先 `setShowModules(true)`
   并复位 `setOnlyScoped(false)`(仅作用域按进程过滤会藏行,勾选后持久化,
   跨用例污染)。
8. **筛选菜单语义**:M3 `DropdownMenuItem` 整项 `MergeDescendants` 合并文本与
   OnClick;只读 `Checkbox(onCheckedChange=null)` 不暴露 `ToggleableState`
   语义——勾选态断言不可行,改行为级断言(点击项 → 行现/行隐)。
9. **RecordFilter 查询契约(V45 T1.1 重写)**:记录读写唯一门面 = `RecordService`
   (`LogRepository` 实现),UI 与 MCP 同经此;`recordType`/`recordTypes` 可空 = 全部类型。
   **谓词不再写在 SQL 里**:`data/db/RecordQueryFactory` 是列表 / 计数 / 删除候选 id /
   详情邻接四路的**唯一谓词来源**(`predicate()` 私有,四路共用 → 「删 = 筛 = 计数」口径
   单一),缺省维度**不出列**(旧「哨兵计数 0/1 + 24 列 `%LIKE%`」形态在 110 列表上恒
   全表扫描,已整体退役);`Built : SupportSQLiteQuery` 暴露 sql + 位置绑定实参,
   DAO 只剩 5 个 `@RawQuery(observedEntities = [CryptoRecordEntity::class])` 执行位。
   Room `@RawQuery` **不支持 DML** ⇒ 批量删除 = 同谓词 `idsByQuery` 取 id +
   `id IN (:ids)` 分片(`DELETE_ID_CHUNK = 500`)。表名唯一事实源 =
   `CryptoRecordEntity.TABLE_NAME`(禁再写 `crypto_records` 字面量)。
   **DAO 直调门禁(V41)**:`cryptoRecordDao()/keywordLibraryDao()` 调用白名单 =
   ModevApp(装配)/LogRepository/KeywordLibraryRepository/ModevDatabase(声明点),
   越界即红(`RecordDaoAccessGateTest` 源码扫描)。
   `row.toRowModelOrNull()` 对非法 kind/operation 枚举名返回 null(行被丢弃,
   种子数据必须用合法枚举,如 `MESSAGE_DIGEST`/`CIPHER`/`MAC` +
   `DIGEST`/`ENCRYPT`/`SIGN`);列执行计划级验证 = 设备插桩
   `RecordQueryPlanTest`(4000 行种子断言零 `SCAN`、零 `USE TEMP B-TREE`,
   Robolectric 不支持 EXPLAIN,故不入 JVM 面)。
10. **时间线分组契约**:`TimelineGroup.secondKey`/`dayKey`/`formatSecond`/
    `formatDay`(纯函数,跨日组头带日期前缀);组合侧邻行比较定组起始
    (`i==0 || prev==null || secondKey 不同`),列表手动索引循环
    (`record==null` 占位,`item(key="h-"+secondKey)` + `item(key=record.id)`);
    分页 prepend 边界 `prev==null` 不渲染头。插桩断言组头须
    `hasTestTag("timeline-header") and hasAnyDescendant(hasText(...))` 限定
    (同秒组头与行尾时间同串,裸 `hasText` 会多命中)。
11. **数据库监听被动采集**:结果集逐行采集仅发生于 App 自身迭代之后;
    hook 内**禁止**移动游标/调用 `getCount` 等探测 API(改变 App 行为);
    行/字节闸门触发即截断补发 `QUERY_RESULT`;未迭代的游标 rowCount=null
    且无结果记录。记录行模型 sealed 分派(`SqlRow`/`CryptoRow`),经
    `toRowModelOrNull` 对非法 kind/operation 枚举名返回 null。
12. **数据库监听开关契约**:5 类开关(open/query/DML/exec/transaction)默认开
    (内置推荐值);每应用类别开关单击即写即推(`pushToPackage` 定向);
    结果上限为「行数 + 字节」双闸门(`IntLimitSheet` 底部输入,禁居中 Dialog,
    V25 起编辑入口 = 每应用「结果闸门」分区);`kind` 列跨枚举共用
    (CryptoKind.name / SqlKind.name),筛选菜单记录类型 + 类别双组单选。
13. **MD3 组件契约 + 设置页功能区**:设置类控件一律 M3 组件
    (`ListItem`/`AssistChip`/`TextButton`/`DropdownMenuItem`…);
     **行容器契约(V46 R1,`RecordRowContainerContractTest` 源码门禁)**:需要圆角/自绘背景的
     可点击行 = `Box(clip(MaterialTheme.shapes.X) + background(容器色) + combinedClickable)`,
     内部 `ListItem` 一律 `ListItemDefaults.colors(containerColor = Color.Transparent)` 只做内容
     布局(MD3 内边距/字阶/槽契约不破)。**圆角与可见波纹只能这样落在容器层**:
     Material3 1.4.0 `ListItem` 无 `shape` 形参(javap 实锤),而旧形态(clickable 在
     `ListItem` 外层 + 容器不透明)会把按压波纹吞在容器之下 = 零反馈;
    搜索输入统一 = 标准 `SearchBarDefaults.InputField`(`SearchInputField`
    共享封装,V40 T5.1 起 CollapsedSearchInput/D4 例外退役——折叠/展开单一路径,
    焦点驱动展开为 MD3 标准行为,`AppbarSearchExclusivityTest` 扫描标记同步);
    M3 1.4.0 `ListItem` 内容槽**合并为单一语义节点**(文本节点 = 整行高、
    节点 Text 列表含全部槽文本)——插桩勿对行内文本独立测距,锚定断言用
    `onNodeWithText` 精确匹配合并节点即可。
    **锚点归属铁律(V46 实锤)**:`record-row` 这一 testTag **必须由 `RecordRow` 自持并挂在
    `ListItem` 节点上**(该节点才是收文本的 mergeDescendants 节点);宿主 `RecordTimelineList`
    只传间距 modifier,**禁再自带 `testTag("record-row")`**。把锚点挪到外层容器 Box 会让
    `hasTestTag("record-row") and hasText(...)`(三个记录列表测试类的 `rowCount` 标准形态)
    **整体归零**(实测 merged 树 tag 节点 texts 为空);探针量测 = tag=1/text=1/both=0。
    同理:挂在 ListItem 槽位内的自定义 testTag 只在 `useUnmergedTree = true` 下可见。
    设置页功能区三入口(V39 拆分,
    算法/数据库两全局监听页已整体退役):数据管理
    (`settings/data`)/ 服务(`settings/services`,wire/MCP 配置面 +
    令牌入口 `settings/mcp-tokens`)/ 诊断(`settings/diagnostics`,
    运行时观测 + 复制诊断信息);监听配置唯一入口 = 每应用「监听设置」;全局列表页
    `records/algorithm`/`records/database`;
    危险清理(按类型/全部)一律居中 `AlertDialog` 确认,无回收站;
    计数/存储值断言必须资源驱动(`getString` 带格式参,zh 渲染 "2 条"
    非 "2")。设置页卡片容器色 = `surfaceContainerLow`(V21 T1 决策经
    E1/R1 修订最终定档,与 LIDE SettingsSection 逐字一致;T1 曾升
    `surfaceContainerHighest` 应对雷电 ListItem 渲染缺陷——根因 = M3 1.4.0
    ListItem 内部 Surface 默认容器色 Unspecified,嵌套卡内触发雷电转译 bug
    吞掉卡片色;**所有 ListItem 必须显式
    `colors = ListItemDefaults.colors(containerColor = <所在容器色>)`**
    (卡内 = Low / sheet 行 = High / 无容器列表 = surface),显式色后雷电
    渲染修复,卡片回退 Low;`SettingsThemeContrastTest` 纯 JVM 全 20 seed ×
    亮/暗强制:卡片/背景 ≥1.046(亮)/1.078(暗)、onSurface-on-卡片 ≥4.5、
    primary-on-surface ≥4.5,禁止改回 surfaceContainerHighest)。
14. **分页流契约(V20 R3.1 设备测试实锤,两条)**:Room 2.7.2 生成式
    `LimitOffsetPagingSource` 的计数查询 = `COUNT(*) FROM (<原 SQL>)`,
    原 SQL 自带固定 `LIMIT :limit OFFSET :offset` 会钳制 itemCount →
    固定小 limit 时 Refresh 后 nextKey=null、分页死锁于第一页(任何 >60 条
    列表只显示 60 条)。**DAO 分页查询的 `limit` 参数必须传大窗口上限**
    (仓库 `PAGING_WINDOW_CAP = MAX_RECORDS`,外层 loadSize 由 Room 动态
    注入),禁止传 pageSize。列表页 `records` 流一律 `cachedIn(viewModelScope)`
    多播缓存(`PagingData` 每实例单收集:同一实例被第二个 LazyPagingItems
    收集即抛 `Attempt to collect twice from pageEventFlow`——冷流重收集/
    StateFlow WhileSubscribed 热流在详情往返重组合时都会触发;cachedIn 包装
    MulticastedPagingData 为官方解法)。回归测试:`RecordPagingProbeTest`
    (直驱 DAO PagingSource Refresh/Append + 固定小 limit 警戒断言)。
15. **Android 组件契约(Compose 列表)**:`LazyPagingItems` 手动索引循环
    (`for (index in 0 until items.itemCount)`)+ VM 级 `LazyListState`
    承载滚动保持;`LazyPagingItems.get(index)` 内建 accessHint 触发分页
    (无需额外 onLoadAround 接线);`enablePlaceholders=false` 时
    `itemCount` = 已加载数。分页翻页验证须种子数据 > 2 页(如 120 条),
    否则分页路径永不触达(旧测试盲区)。
16. **二态偏差每应用监听(V24;V25 无全局配置收敛)**:有效配置 =
    内置推荐默认值(`HookSwitch.DEFAULT`,测试强制与 CategoryTaxonomy 对齐;
    **无全局设置 UI**——`switch_global` 键仅作注入侧兜底种子,
    `SettingsRepository.init` 无条件重置,历史用户自定义一次清除)⊕ 该应用
    稀疏二态偏差;模型 = `AppListenOverride`(masters×2 + kinds 17 项 +
    V25 微调项 `logStack/maxBytesPerField/ignoreStackPrefixes/maxResultRows/
    maxResultChars`,全部可空,null = 取默认);`EffectiveSwitch.resolve`
    产出普通 `HookSwitch` —— wire 协议/注入侧零感知(VERSION 恒 4);
    `readKind`/applyKinds 为读写互逆映射,新增类别必须同步双端;
    存储 = 每包一键 `override_{pkg}`,惰性创建、零偏差即删键;
    JSON 兼容 V23 三态遗留(`"ON"/"OFF"` + `kindOverrides` 键名容忍解析,
    写入恒为新格式);覆盖键读本地权威(`getLocal`),默认键读优先远程;
    下发 = ConfigServer per-session resolve(`effectiveResolver` +
    `pushToPackage` 定向推送;V25 起 `broadcastEffectiveConfig` 已随全局页
    退役,**禁止恢复「同一份广播全量」**);注入侧兜底(HookInstaller)
    直读两键后 resolve。设置页 = 行内 M3 Switch(`listen-row-*` testTag,
    整行 toggleable + 内层 Switch clearAndSetSemantics),单击即写即推;
    记录选项/结果闸门数值行经底部 sheet 编辑(MaxBytesSheet/IgnoreStackSheet/
    IntLimitSheet,≤0 显 ∞);「恢复默认配置」行仅在有偏差时渲染(testTag
    `listen-reset`,副标题 plurals `listen_settings_custom_n`);
    测试种子 hook 开关默认值见 V18(dbOpen 等噪点类别默认关),集成测试
    setUp 须显式开。
17. **记录浏览双维度(V24;V40 T4 修订;V41 T5 收敛)**:`records/all`(GlobalRecordsScreen)
    退役不恢复;记录浏览 = 两维度——**按应用×类型**(`records/{type}/{packageName}`
    10 目的地,自每应用监控页 EntryRow 进入)+ **按类型×全部应用**
    (`records/type/{recordType}` 全局列表,自数据管理页类型计数行进入);
    **数据管理页「按应用浏览」区已退役(V41)——应用维度浏览唯一入口 =
    主界面应用列表(目标路由原本相同),`data-app-*`/`onOpenApp`/`AppBrowseRow`
    禁止复活**;
    `RecordsViewModelFactory(recordType, packageName: String?)`——packageName
    非 null = 单应用语境,null = 全局列表(presetPackages=null 哨兵直透
    RecordFilter,数据层零新查询);VM 可变面仅 query/kindName,全局形态
    `packageLabels` 提供应用语境行(V43 T4 起独立参数行,`record_field_app` 标签;
    目录内存映射,禁逐行 binder;
    未安装包不入目录 → contextLabel=null,无包名回落属现网正确行为);
    「清空本类」在两形态分别 = 本应用该类型 / 该类型全部应用(deleteByFilter
    hasConstraint 对 type-only 过滤放行);数据管理页保留「清空全部」唯一危险行,
    按类型清理行退役(清理并入列表);危险清理行文案断言一律
    `onAllNodesWithText(...).onFirst()` 多节点安全形态,禁裸
    `onNodeWithText(<区块标题>)`。
18. **解耦契约(V23 R4;V43 T7 升级;V45 T2 收口)**:`PackageBoundaryTest` 规则 = **文本级全限定引用扫描**(去注释后匹配 `com.lioisme.modev.<子树>`,堵 `^import` 行扫描被 FQN 内联绕过的洞——V5.2 实证 SettingsRepository→mcp 依赖环):**ui 子树禁 hook 与 data 全子树**(V45 T2.2 收紧,原仅禁 data.server/data.db;V23 存储细节由仓库出口承担,如存储占用 =
    `LogRepository.storageUsage()`,DB+wal+shm 三文件和);**V43 新增**——domain 子树禁 android(白名单 `androidx.compose.runtime.Immutable` 纯注解 + `androidx.paging.*` 分页契约)/ui/data 逆向(模型族 `RowModel/RecordDetailModel/RecordTypeCount/PipelineStats/ChannelStats` 已上移 domain;**V45 T2.3:typealias 兼容层已整体删除** —— RowModelCompat / RecordDetailModelCompat / PipelineStatsCompat / data.repo.RecordFilter / data.repo.RecordService 五件 28 别名清零,消费方一律 import `domain.*`,`PackageBoundaryTest` 豁免集合恒空,`LayerDependencyGateTest` 禁复活);hook 子树禁 ui/compose(注入侧与宿主 UI 完全隔离);data.repo 禁 mcp(服务生命周期装配集中 ModevApp,仓库不得反控服务层);**mcp 子树禁 data 全子树**(V45 R6,与 UI 同一契约面 = 需求 3「双一等公民」的类型级保证)。
    **V45 分层三门禁(`LayerDependencyGateTest`)**:G1 兼容 typealias 禁复活;G2 `AppContainer` 的 public 属性类型必须来自 `domain.*`(实现类型只允许 `internal`/`private`,即仅装配可见 —— `settingsRepo/logsRepo/wireServer/mcpServer`),消费方持有的静态类型即接口,「这一屏只用什么」由类型系统表达而非命名纪律;G3 ui/mcp 零 `import ...data.`。**方法层契约族** = `ListenConfigService`/`FeatureService`(domain.config)、`KeywordLibraryService`(domain.asset)、`ServerConfigService`/`McpServiceConfig`/`ConnectionMonitor`/`PortWriteGuard`(domain.server)、`AppCatalogService`/`AppListPreferences`/`AppSort`(domain.app)、`RecordService`/`RecordFilter`/`RecordDraft`→已删/`MonitoredRecord`(core.model 类型化族,写入面唯一模型)、`RecordService.stats`(原 `LogRepository.statsFlow` 契约外成员)、`RecordService.exportJson`(原 UI import `data.mapping.toJson`);`McpServer` 与 `ConfigServer` 双双实现 `ConnectionMonitor`(含 `bindError`)。**写侧单点(`SingleWriteEntryPointTest` W1/W2/W3)** = prefs 文件仅设计所有者可开(`SettingsRepository`+`MirroredConfigWriter` / `AppListSettings` / `LocaleManager` / `ThemeSettingsManager`)、服务端可变面仅装配层与配置仓库可写、端口写仅经 `PortWriteGuard` 收口的契约方法。**写入模型对账(`RecordWriteModelParityTest` P1-P4)** = 实体每列必有映射器写入者(`id`/`seq` 登记为存储自管)、类型化模型必带 packageName/kind/operation/timestamp/threadName、`RecordDraft` 平铺镜像禁复活、domain 零 `androidx.room`。**MCP 双域闸** = `listen_settings_set` 拒 FEATURE kind、`feature_settings_set` 拒 LISTEN kind(与 UI 两页按 `CategoryTaxonomy.domain` 分列同口径)。**core 子树零 `android.*` import**(V5.1:日志经 `EffectiveSwitch.interventionWarn` 注入、`ScopeRepository.logger` 由 ModevApp 装配;V43 T7 已落地,禁止回归);**MCP 工具模型族 import 唯一事实源 = domain.record**(V5.4,McpDtos import data.mapping 即红,`is com.lioisme.modev.data.mapping.*` FQN 形态已被 B1 文本级扫描捕获);**端口默认值单一事实源 = `ConfigKeys.WIRE_DEFAULT_PORT(1511)/MCP_DEFAULT_PORT(1510)`**(V5.13,WireProtocol/McpProtocol/SettingsRepository 一律引用,禁再写字面量);**Room 迁移对账 = `DbMigrationParityTest`**(version=14 与 MIGRATION_x_y 链 + addMigrations 清单三处一致性测试锁定,升级新迁移必须同步三处否则红);data/repo 与 domain 的 RecordFilter 扩展双定义死重复已清除(V5.7;V45 T2.3 起兼容层本体已灭)。UI 变更
    应用列表偏好一律经 `AppsViewModel.setPinScoped/setShowSystem/
    setShowModules`(UI 零 import `AppListSettings`,VM 持注入的 `AppListPreferences`;其 init 缓存
    applicationContext,setX 无 Context 参数);作用域变更提示文案
    统一 `ui/common/scopeChangeMessage`,状态语义分派统一
    `ui/common/connectionStatus/scopeStatus`;Room 显式 WAL
    (`ModevDatabase.get`)、批量插入显式 `@Transaction`;
    lint 基线更新:**实测 43 warnings / 1 hint / 0 errors**(V46 复核修正,V46 三项 UI
    改动净新增 0;V45 曾记 41、构建段曾记 40 —— 三种口径并存已统一以本次实测为准,
    权威口径见「构建 / 测试」段 lint 门禁行;V34 清理 39 死资源键
    detail_remove_scope/listen_settings_title/record_copied/
    settings_pin_scoped_hint/settings_apps_section/detail_framework_error,
    新增计数文案必须 plurals 如 listen_settings_custom_n)。
19. **统一外部访问契约(V41,取代 V26 双键形态)**:单一开关 **wire + MCP 双服务消费**
    ——偏好键 `server_external_access`(`SettingsRepository`,宿主本地 prefs,
    **不入远程镜像**,注入侧零感知)为唯一事实源;`mcp_external_access` 已退役为
    迁移输入(init OR 合并后删除,幂等,**禁止复活双键分裂**)。写路径唯一 =
    `setExternalAccess`(持久化 → StateFlow → wire `server.externalAccess`
    volatile 置位 + 异步 `server.restart()` → `restartMcpAsync()` 异步
    `mcpRestarter` 回调回填 MCP 绑定态后重启;**MCP 重启恒异步**——`mcpRestarter`
    消费方多为 Main 调度,主线程 NanoHTTPD start 会致 jank/静默 bind 失败);
    双服务 restart 均带 epoch 代元守卫,已连接会话由注入侧重连循环恢复;
    服务端是否鉴权仅由 `tokenAuthEnabled` 决定(与外部访问无级联,V40 D-4 不变);
    注入侧恒连回环,wire 协议 VERSION 恒 4 零感知。UI 观测经 domain
    `ConnectionMonitor.boundAddress`(P5-1:ui 禁 import data.server,VM 组合
    `listeningAddress` 回退口径)。UI 唯一入口 = 服务页首「通用」分区单开关
    testTag `services-external-access`,副标题 = 动态绑定地址(0.0.0.0/127.0.0.1,
    VM `externalAccessAddress`),默认停用;wire/MCP 各自端口行仅显示端口(IP 由
    统一开关唯一承载,`IP:端口` 完整形态仅存于诊断复制快照);端口编辑经
    **PortValidator 可用性校验**(见 #29,探测地址消费本开关)。
29. **端口校验链契约(V42 T3)**:端口编辑唯一链 = `ModevPortSheet`(异步校验,
    busy 态 + maxLength 5)→ `ServicesViewModel.validateWirePort/validateMcpPort`
    → domain `PortValidator.check`——校验序 = 范围 → **特权 <1024 硬拒**
    (Android 应用进程绑定必 EACCES)→ **双服务冲突硬拒**(wire ≠ MCP)→
    **自身当前端口豁免**(重存原值 no-op,不探测)→ **占用探测硬拒**
    (ServerSocket 试绑 `reuseAddress=true`,与 ConfigServer 自绑语义一致,
    TIME_WAIT 不假阳性;地址随统一外部访问开关 0.0.0.0/127.0.0.1);
    校验失败 = 硬阻止保存(行内分型错误,偏好零写入);探测为即时快照非终审,
    最终裁决 = 真实绑定——wire `ConfigServer.bindError`/MCP `McpServer.bindError`
    双观测面(失败置位/成功清空),服务页各自错误行对称渲染
    (`wire_bind_error_*`/`mcp_bind_error_*`);退避重试机制不变;`ModevInputSheet`
    零改动(其他消费方零波及)。
20. **行为监听一等公民契约(V27)**:第三记录类型 `RecordType.BEHAVIOR`
    (wire=`behavior`),单表扩展(ADR-1,迁移 v8→v9 新增 10 可空列:
    path/destPath/command/fileSize + content 五元组);wire VERSION 恒 4
    零感知(`recordType` 泛型信封即扩展点)。注入侧 = `BehaviorProbe`
    装配五 Hooker(FileMeta/FileStream/NioFiles/Asset/Shell),emit 闸门序:
    config 快照 → `behaviorEnabled(kind)`(volatile 前置)→ `PathGate.isIgnored`
    (内置 9 系统前缀 + modev_spool/modev_seq_ 自写消除 + 用户
    ignorePathPrefixes 叠加)→ 栈 denylist → `SessionThrottle`(LRU cap256,
    同键 1s 窗口去重)→ 组装;**被动红线**:内容预览仅限「随调用即可得」
    (readAllBytes/readAllLines 返回值、write 实参、AssetFileDescriptor.length),
    流式 API 与 open() 返回的流绝不触碰。开关模型:HookSwitch +master
    behaviorListen + kinds 7 项(FILE_WRITE/DELETE/RENAME/SHELL_EXEC 默认开;
    FILE_READ/LIST/ASSET_READ 默认关)+ 微调 fileContentCapture/
    ignorePathPrefixes(AppListenOverride 偏差项同步扩展);新增行为类别必须
    同步五处:BehaviorKind/BehaviorOperation.kind 归属/CategoryTaxonomy 条目/
    HookSwitch.behaviorEnabled/EffectiveSwitch.readKind+applyKinds(互逆测试
    强制)。UI 三入口对齐:`records/behavior/{pkg}` 路由、AppMonitor 第三
    EntryRow(testTag `monitor-entry-behavior`)、StatsCard 三段比例条;
    监听设置页行为分区 testTag:`listen-row-master-behavior` /
    `listen-row-file-content` / `listen-row-ignore-path`;数据管理 ClearTarget
    三类型。列表行统一脚手架(V27;V40 T5.3 ListItem 化;**V43 T4 一行一参数重构**):
    `RecordRow` = 标准 M3 ListItem(RowPresentation 单一数据出口,supporting =
    **`RowField` 字段行列表**——每行恒承载一个逻辑参数,「类别 · 操作」拼行形态
    退役:类别词元独立 accent 着色行、操作/库名/行数/影响行/退出码(meta_exit_code)/
    pid(meta_pid)/应用(record_field_app)/状态(record_action_blocked/detected,
    record_value_no)各成行,标签恒走资源四语 D-3,行内禁止英文硬编码 + leading 36dp
    类别色@13% tonal 图标 KindIcons 映射 + headline mono Medium(身份非参数,
    进程行 headline=操作名资源本地化,RowModel.ProcessRow.headlineText 英文形态退役)
    + **V46 R1/D1 起时间与 MicroBadge 移入身份行右栏**(headline = `IdentityLine`,
    testTag `record-row-identity`;语义标记非参数,仍在右侧但只占首行);
    **`ListItem` 的 `trailingContent` 槽恒不传** —— 该槽存在即永久从文本列预留宽度
    (M3 1.4.0 `ListItem` 无 `shape` 形参,圆角只能在容器层做,详见 #13 行容器契约),
    这就是「底部参数行右侧明明还有位置却被提前截断」的根因;
    `record-row` testTag 与
    单击/长按/时间契约不变;插桩禁断言含「 · 」的行内拼串
    (RecordRowLayoutTest.noMultiParameterLines 结构哨兵;宽度与槽位回归由
    `textFieldColumn_reachesRowRightEdge...` 几何锁 + `RecordRowContainerContractTest` 源码门禁双向盯);
    路径/命令/URL/SQL 预览长值行真实启用 `MiddleEllipsisText` 中缀省略
    (纯函数 MiddleEllipsis.ellipsize JVM 直测,D-2——旧「文档声称走中缀、实装尾省略」
    漂移修复,勿再退役组件);计数副标消费 `records.itemCount`(V30 起消除独立
    COUNT Flow;V43 T5 读取收敛独立最小子组合器 RecordsCountSubtitle,
    页增长不得重进页面主体);
    长按复制摘要全族走资源(BLOCKED/DETECTED/exit/pid/bridge/HTTP/proxy/VPN
    英文形态退役,interventionAction 经 `interventionActionLabel` 映射)。
21. **详情页纯 markdown 化(V28)**:Appbar 以下信息内容 100% markdown 承载
    (identity `## 标题+粗体上下文行` / TOC 导航行 / H2 节根+H3 子表示),
    原生 Hero 卡、FilterChip 锚点行、ZoneHeader 色带、流向图标行全部退役;
    **D2.1:目录条目是导航控件非信息区块**——`RecordTocContent` 原生可点击行
    (testTag `toc-{key}`,标签与节标题同源),渲染库 markdown 链接的语义节点
    为 0×0 bounds LinkTestMarker(坐标点击不可达,勿走该链路);
    渲染层:`markdownTypography()` 显式字阶(H2=titleMedium SemiBold 等 16 槽,
    取代库默认 display* 巨型标题)+ 扁平代码块(`md-code-block`,
    surfaceContainerLowest+8dp 圆角+outlineVariant@40% 描边,零 elevation;
    SecondaryTabRow;rememberSaveable 无显式 key);高亮 = Highlights 引擎直连
    (`dev.snipme:highlights`,**禁手搓分词器**;json→JAVASCRIPT、bash/shell→SHELL,
    M3 同源 SyntaxTheme 亮暗自适应,`HIGHLIGHT_MAX_CHARS=32Ki` 降级纯 mono);
    params/bindArgs/rows 以真实 JSON 承载(D3:`formatParamsJson/
    formatBindArgsJson/formatRowsNdjson`,Gson 必须 serializeNulls 否则 SQL NULL
    丢失;json 围栏不参与代码组聚合);`export_too_large` 两处拒绝均带格式参。
22. **偏好监听一等公民契约(V28)**:第四记录类型 `RecordType.PREFS`
    (wire=`prefs`),迁移 v9→v10 仅新增 `prefKey TEXT`(值域复用 content 五元组、
    路径复用 path 列);注入侧 = PrefsProbe 三 Hooker(Open 构造器+edit 登记 /
    Get 全家桶+contains+getAll / Editor put*+remove+clear),emit 闸门序与行为
    逐位一致,**实例索引 PrefsInstanceIndex 双 LRU(cap256)杜绝反射读内部字段**
    (构造器 after 的 thisObject=新实例,登记依赖它——插桩驱动测试须传实例);
    开关模型:prefsListen master + kinds 5 项(OPEN/PUT/REMOVE/CLEAR=SIGNAL 默认开,
    GET=NOISE_HIGH 默认关)+ 零新增微调项(值捕获复用 fileContentCapture、路径降噪
    复用 ignorePathPrefixes);commit/apply 不挂钩(D5)。UI 四入口对齐:
    `records/prefs/{pkg}`、AppMonitor 第4 EntryRow(`monitor-entry-prefs`)、
    StatsCard 四段比例条(min weight 0.02f)、监听设置 prefs 分区
    (`listen-row-master-prefs`;**switchLabelRes 中 PrefsKind.OPEN/CLEAR 与 SQL
    kind 名碰撞,分支必须前置**)。新增类别五处同步清单同 #20
     (+PrefsRecordMappers dispatch 双臂 + LogRepository writeBatch/registry +
     HookInstaller probes/jsonOf + CapabilityKind.PREFS_LISTEN)。
23. **双域拆分契约(V37,最高架构优先级)**:`CapabilityDomain { LISTEN, FEATURE }`
     为类别归属唯一事实源,`CategoryTaxonomy.RecordCategory.domain` **必填无默认**
     (新增类别未标域编译即红);FEATURE 37 类 / LISTEN 44 类,快照测试锁定
     (kind id 跨类型碰撞 OPEN/SIGNATURE,域完备断言必须用 (type,id) 对)。
     **FeatureGate 红线**:一切干预动作(setResult/字段改写/弹窗拦截/进程拦截/
     掩码)必须经基类 `featureOn(kind)` = master∧kind 双门控,**禁止裸布尔位直读**
     (`InterventionGateConventionTest` 源码扫描门禁 + `FeatureGateBypassTest`
     行为门禁;开关全关 = 零副作用,含安装期 Build 静态字段改写——
     EmulatorBuildHooker 经 `ConfigWatcher` 惰性幂等补写,永不无条件篡改)。
     功能域数据一律走 `HookSwitch.feature: FeatureConfig`(词表规则/画像/黑名单
     追加,wire 信封 VERSION 恒 4、configVersion=2 读旧写新迁移);干预功能 UI
     唯一入口 = 每应用「功能设置」(`apps/{pkg}/feature-settings`,六功能卡由
     `FeatureGroups` 驱动,完备性测试锁定 FEATURE 域↔组并集),监听页只留
     LISTEN 域(环境观测分区由 taxonomy 驱动);
     **双域清侧同闸(V45 T3.5/DUAL-12)**:两页各一行「恢复默认配置」与 MCP 两工具
     `listen_settings_reset`/`feature_settings_reset` 一律经
     `ListenConfigService.resetOverride(pkg, domain)` **只清本域**(唯一分区事实源 =
     `AppListenOverride.clearDomain`/`masterDomainOf`/`CategoryTaxonomy.isFeatureKindKey`:
     FEATURE master = ui/network/device_env/process 四类型(wire 名),其余 6 类型归监听;
     微调项 logStack/maxBytes/ignoreStack/result*/fileContentCapture/ignorePathPrefixes 归监听,
     dialogKeywords/spoof*/extra*/keywordIds/keywordFollowGlobal 归功能);
     **禁止**再引入全量删键的消费方入口(实现方 `removeOverride` 仅服务零偏差归一与测试夹具);
     行可见性与副标计数一律 `deviationCountFor(domain)`(跨域 `deviationCount` 只用于监控页汇总);
     全局功能资产 = 设置页
     「拦截与伪装」区(关键词库 `settings/keywords` / 画像 `settings/profiles`
     / 黑名单 `settings/blacklist`,写 `feature_global` 本地键并入 switch_global
     种子);机型字面量唯一事实源 `SpoofProfiles`(钩子禁内置);
     关键词库 Room `keyword_library`(v14)+ 每应用 keywordIds/keywordFollowGlobal
     宿主端解析(`SettingsRepository.withKeywordRules`),库变更
     `pushToAllConnected` 重推。Capability 层(domain/capability)已整体退役,
     **禁止恢复**(双域 UI 唯一驱动源 = CategoryTaxonomy)。
24. **Appbar 动作契约(V37;V39 R5 排他修订;V40 T3 运行按钮迁移)**:每应用监控页
     「清空记录/启动」= TopBar actions 且**启动恒最右**(动作序 = `[app-clear-records]`
     [`app-launch`],`app-launch` 仅存在启动入口时渲染防闪烁,正文操作卡退役,
     无搜索页不受排他约束);**应用列表行 trailing = [作用域 Checkbox],行内运行按钮
     退役**(`app-row-run` 恒不存在,`FourScreensNavTest.appRow_hasNoRunButton` 锁定;
     启动唯一入口 = 监控页 Appbar,`AppsViewModel.resolveLaunchable`/launchable 已随按钮
     退役,`AppCatalogRepository.resolveLaunchable` 仓储能力保留供监控页消费);
     **凡 Appbar 含 SearchBar 的页面 actions 恒空**
     (`AppbarSearchExclusivityTest` 源码扫描门禁)——10 记录列表页「清空本类」
     收进搜索条动作槽(`CollapsedSearchInput`/`SearchInputField` `actionSlot`,
     折叠/展开同 testTag `records-clear-type` → 居中确认 `records-clear-confirm`,
     deleteByFilter 按预设语境);**搜索条槽按钮长按不展开全屏 SearchView**
     (V42 T1:SearchInputField 槽按压窗口 + 400ms 宽限门禁抑制展开意图,
     文本区长按选词聚焦展开保持 MD3 标准;探针 SearchInputComponentsTest
     longPress_* 三槽锁定);危险确认恒居中无图标 AlertDialog
     + surfaceContainerHigh(见 #27)。
25. **视觉 token 契约(V37 T4)**:alpha 魔法数一律 `ui/theme/Tokens`
     (pillContainerAlpha/badgeContainerAlpha/chevronAlpha/disabledContentAlpha/
     emptyIcon*/codeBorderAlpha/swatchBorderAlpha);记录行内边距 16dp 统一(旧 14dp 已归一);
     **行容器/卡片半径一律取 `MaterialTheme.shapes.*`**(记录行 = `shapes.medium`,V46 R1;
     禁在行容器上写 `RoundedCornerShape(<魔法数>.dp)` —— `RecordRowContainerContractTest` C2/C3 源码门禁);
     **色板网格步长单点 `ColorSwatchGap`**(V46 R2/D7:`SettingsScreen` 内横竖两向 `spacedBy`
     共用同一常量,几何门禁 `colorSheet_swatchGridHasEqualGaps` 断言「纵间距 > 0 且横纵等值」;
     旧实装只有横向 ⇒ 四行色点上下相切);
     相邻圆角行间隙须 ≥ 半径(记录行垂直留白 6dp ⇒ 间隙 12dp = `shapes.medium`,V46 D8);
     设置类行开关统一 `ToggleListItem`(PerAppSettingRows 共享);
      kind→标签映射唯一入口 `KindLabelRes.resolve`(监听/功能两页共用,
      Prefs OPEN/CLEAR 碰撞前置规则不变);
      AppIcon 占位底图 = **加载中/失败态限定**(V42 T2,`onState` 驱动 Success 即移除,
      testTag `app-icon-placeholder`;图标本体恒原样绘制禁裁切——不规则/透明图标
      禁再透出底层色块;主题色板 = v8 圆形色点网格形态唯一,FilterChip+hex 色板禁复活)。
26. **MCP 服务契约(V38,Agent 一等公民;V43 T7 边界收紧)**:`mcp.*` 包边界 = B1(禁 import
      `ui.*`/`androidx.compose.*`/`data.db.*`/`hook.*`/`data.server.*`/`framework.*`/**`data.mapping.*`**
      ——V5.4 起行/详情模型唯一事实源为 `domain.record`,McpDtos 直用域类型;
      **扫描为文本级全限定匹配,V43 T7,`is com.lioisme.modev.data.mapping.X` 内联 FQN 亦红**,
      `McpToolParityTest.mcp_layer_boundary_allowlist` 源码扫描门禁)+ B2(允许
      `domain.*`/`data.repo.*`/`core.*`,与 UI 同一上游 allowlist——「同一起游继承」
      由测试锁定);**工具面 parity**:`McpToolParityTest.frozen_50_tool_names` 冻结
      50 工具名快照,新增 UI 能力必须同步 MCP 工具(否则红),防「旁路上游」与
      「功能阉割」双向漂移;**声明面即生效面**(V45 T3.2,`McpToolRegistry.validate` 四条):
      `additionalProperties=false` ⇒ 未声明参数 = `-32602`(禁「广告了但不生效」/「生效了但没广告」)、
      schema `enum` 由注册表强制(非法枚举 = 协议错误,不再穿透成业务错误)、
      `required` 缺失与显式 `null` **同一分型** `-32602`、类型可为数组(跨类型同名不同型的并集);
      **记录载荷 schema 由模型反射派生**(`RecordPayloadSchema` 是 `MonitoredRecord` 族 →
      JSON Schema 的唯一通道,`FIELD_NOTES` 只写说明不写键集;旧 60 行手抄表把存储列名
      `contentHex`/`prefKey`/`httpMethod` 当参数广告、Agent 照抄必被拒,已整体退役;
      `payload_schema_and_handler_agree_per_record_type` 双推导对账);
      **写入实例化走 Kotlin 默认值构造器**(`McpJson` 内 `InstanceCreator` 以全 1 mask 调合成构造器;
      Gson 默认 Unsafe 会跳过默认值表达式 ⇒ 可选字段缺省等价 null:`stack` 缺席 NPE、
      `timestamp` 缺席落 1970;可空性不可反射(@NotNull 为 BINARY 保留),故无默认值的非空字段
      显式登记在 `WRITE_REQUIRED_COMMON`/`WRITE_REQUIRED_BY_TYPE`,由缺值检查点名);
      **协议结构恒定**(V45 T3.3/DUAL-7·8):`McpJson.obj/arr` 值型 `Any?`(null 恒落 JsonNull,
      禁 `JsonPrimitive(nullable)`),列表行信封 `{id,recordType,packageName,kind,operation,threadName,
      timestamp,fields}` 且 `fields` 内**不得**再现身份键,详情 `truncated` 键**恒在**(false 也写),
      作用域状态 JSON 单源 `scopeStateJson`(ServerTools 与 ScopeTools 共用,键集不随状态增减),
      截断上限唯一事实源 = `McpDtos.MAX_FIELD_CHARS`(工具描述文案派生引用,禁再写 "1MiB");
      **时间契约**(V45 T3.4):全部时间戳 = epoch millis(UTC 墙钟,`timestamp`/`timeStart`/`timeEnd`
      与 `records_*` 输出口径一致,`McpTools.SERVER_INSTRUCTIONS` 已对 Agent 声明);
      **能力对称可审计**(V45 T3.5):`UiMcpCapabilityParityTest` R1 禁两个工具广告同一可变上游
      (例外须登记理由)、R2/R4/R5 工具与 ViewModel 动作双向必须归属能力行、单侧行必须给理由;
      协议 = spec 2025-06-18 Streamable HTTP 单端点 POST /mcp,
      单请求单响应(batch→-32600),错误码分型(协议错误 -327xx/-326xx vs 业务错误
      `result.isError`),会话头 `Mcp-Session-Id`(restart 整体失效);认证(V39 令牌化;
      V40 D-4 解耦)= 令牌认证独立开关默认停用、启用后**全接口强制 Bearer(含回环)**;
      服务端是否鉴权**仅**由 `tokenAuthEnabled` 决定,外部访问与认证互相独立
      (0.0.0.0 无认证监听 = 用户显式选择;V41 起 MCP 绑定随统一外部访问开关,
      风险语义由令牌页状态行与确认弹窗承载,开关副标题仅动态绑定地址);校验集 =
      多令牌集合,变更经 `mcpTokenSetUpdater` 原地生效(不重启不踢会话);
      令牌管理仅 UI(`settings/mcp-tokens`,四动作 = 创建/重命名/查看/删除;重命名走
      方法层契约 `McpServiceConfig.setMcpTokenLabel`,UI 零直写 prefs,V46 R3),
      不暴露为 MCP 工具
      (`token_management_is_documented_exclusion` 登记引导悖论 + PII);`McpDtos` 必须用专用
      `GsonBuilder().serializeNulls()`(SQL NULL 键否则静默丢失);记录大字段
      1MiB 截断守卫(响应内 `truncated:true`);`records_clear_all`/`app_clear_records`
      等危险工具 `confirm:true` 必填(缺失→-32602 协议错误);日志 `ModevMcp` 仅落
      method+tool+耗时不落记录内容(PII 纪律);MCP 与 wire ConfigServer 完全独立
      生命周期(独立端口 V39 起 1510 /开关/restart,互不触碰,VERSION 恒 4 零感知);
      交付门禁 = `24-run-mcp.ps1`(42 子断言,§9.2 矩阵 22 项)。
27. **对话框/Sheet 统一契约(V39 R7)**:ui 内 `AlertDialog(` 仅允许
      `ui/components/ModevDialogs.kt`、`ModalBottomSheet(` 仅允许
      `ui/components/ModevSheetScaffold.kt`(`DialogConventionTest` 双源码扫描门禁;
      `FullscreenTextDialog` 的 `Dialog(` 不在扫描面)。确认一律
      `ModevConfirmDialog`(**无 icon 参数**,`destructive` 染 error 色,
      `confirmTestTag` 锚点);只读值展示一律 `ModevValueDialog`(V46 R3:同处
      `ModevDialogs.kt` ⇒ R1 出口唯一不破、同样禁 `icon =`;值等宽全量呈现 +
      复制按钮,组件面永不含输入控件 —— `ModevValueDialogTest` 源码门禁盯这点,
      带键盘的弹层仍归 #4 的底部 sheet);弹层一律 `ModevSheet`(标题 titleLarge@24dp +
      `ModevSheetScope.dismissWith` **hide 动画后回调收口** + imePadding 单内边距 +
      surfaceContainerHigh)系:`ModevInputSheet`(label 可空/error/maxLength/
      inputTestTag/confirmTestTag)、`ModevPortSheet`(wire/MCP 端口唯一实现,
      V42 起自带异步可用性校验见 #29,testTag `port-input`/`port-confirm`)、
      `ModevChoiceSheet<T>`、`ModevSheetButtonRow`(自定义内容复用);诊断/服务页
      两端口 sheet 已合并,`SettingsSheets` 三件为薄封装(签名不变零波及)。
28. **服务/诊断拆分 + 双击退出契约(V39)**:服务页(`settings/services`,
      `ServicesViewModel`)= wire/MCP **配置面**(开关/端口/令牌入口,`ModevPortSheet`);
      诊断页(`settings/diagnostics`)= **运行时观测**(框架作用域/在线目标/管道
      统计/复制快照,`DiagnosticsReport.Snapshot` 含 MCP 两行、令牌值恒不进);
      `McpServer.boundAddress`/`bindError` StateFlow 对齐 wire 可观测性;根页返回
      = `DoubleBackExit` 双击退出(2s 窗口,可注入时钟 JVM 直测;首次 Snackbar
      `exit_press_back_again`,窗口内再按 finish;根页 SnackbarHost 上提至
      `ModevNav` 与消息流共用宿主)。

30. **画像库契约(V47,画像即值源)**:`spoof_profile` 表(Room v15,`ALL_MIGRATIONS` 链内)
      是伪装取值的**目标事实源**,四视图(UI 表单行序 / MCP 参数域 / 门控表 / 靶程序期望表)
      一律向 `ProfileFieldCatalog` + `DeviceProfile` 穷尽 `when` 桥接派生,**禁反射、禁第二份字段清单**
      (`ProfileFieldCatalogTest` / `DeviceProfilePropertyParityTest` / `ProfileLabelsTest` 穷尽性锁 +
      `SpoofProfileToolsTest` 反扫取值域;**第四视图即 `v47-spoof-expectations.json` 尚未落地,属 WP-8 出口**)。
      新增一个 `ProfileField` 的**手工**站点只有:
      `ProfileField` 条目(kind/type/baselineKey —— **V49 R-1 起没有 section 轴**,分区就是 kind)、
      `DeviceProfile` 字段 + 三条穷尽桥接臂
      (`stringOf`/`isSetIn`/`withText`)、catalog `validate`(有格式约束时)、`RANDOMIZABLE` 与对应生成器、
      `profileFieldLabelRes` + 四语键 —— 其余(`byKind`/`populatedKinds`/`jsonKeyOf`/codec 字段序/编辑器行序)
      **全部由 `ProfileField.entries` 派生,再抄一份就是第二事实源**(漏抄由穷尽 `when` 与 parity 测试当场拦)。三条不可退让的语义:
      ① **零静默**:`ProfileResolution` 三态(`Unbound`/`Bound`/`Missing`)必须可区分并在屏上点名
      (GAP-02 旧形态:任何非内置 id 被吞成 auto = 用户选了而伪装不发生且零提示);
      ② **不钳制**:`ProfileCodec.isValid` / catalog 校验失败 = **拒绝写入**,`ProfileEditorForm.snapshot`
      忠实反映输入,可保存性由 `canSave` + 仓库原子拒绝两道把关(把非法值悄悄丢掉会让用户存进
      一份与自己输入不同的画像 —— 契约已因此改过一次,勿再改回);
      ③ **库里永不留空行**:新建走**草稿路由** `settings/profiles/draft:<name>`(`ModevRoutes.profileDraft`),
      首次保存 `create` 成功后编辑器就地升格为主键;`create` 对零字段正文恒返回 null 是**设计**
      (一行至少要装一个钩子,否则绑定它等于绑空气),不得为此开「允许空档」的洞。
      内置档账本:`BuiltinDeviceProfiles.BUILTINS` 为首装/升级补档源,`builtinSeedId` +
      `builtinFingerprint`(catalog 序派生,**不含 name/summary** —— 基线把呈现算进指纹导致切语言即
      「用户改过」误判,是反面教材)+ `managed`/`deleted` 墓碑列;用户改过即脱离管辖,删过即不复活。
      **当前接管边界(改任何一侧都要回访这条,别按想象写)**:画像库已覆盖取值/校验/编辑/传输/
      绑定/下发(`FeatureConfig.deviceProfile` 随 `expandProfile` 推到注入进程),但 `hook` 子树
      **零消费者** ⇒ 靶程序实际值仍出自 `SpoofProfiles.resolve(spoofProfileId, spoofOverrides)`;
      逐组接管须按「kind 登记 + 操作枚举 + 发射该操作的钩子 + 冻结计数快照 + MCP enum」**同一提交原子落**
      (`DeviceEnvKindTest` 要求每 kind 有 operation、声明面即生效面要求 enum 与钩子一致),
      这也是 `ProfileKind`(V49 R-1 后 **20**,画像层)与 `DeviceEnvKind`(21,taxonomy 层)目前**故意不合并**的原因 ——
      合并只能在批量登记那一轮做,半合并即把仓库留在红色(实测链见 `TASK-V47-SPOOF-PLAN.md` §WP-3c)。
      **V49 R-1 的分类单轴(改画像分组前必读)**:`底层来源 API 族 ≡ ProfileKind ≡ UI 分区 ≡ 钩子族 ≡
      MCP enum 值`,五者 1:1,由 `ProfileKind.sourceApi`(纯 ASCII 代码标识符,不进四语表)在屏上点名,
      分区标题 = `profile_section_title(本地化名, sourceApi)` 组装。V48 的 `ProfileSection`(10 值)与
      `profile_section_*`(10 键)已整体退役 —— 与类别 1:1 的第二条轴就是第二事实源,而它的 `OTHER`
      (电池 + 截屏)与 `LOCALE`(有钩子 + 零钩子)把两种不同机制混在一段,正是 R-1 要消灭的形态。
      拆分的两组:`PROFILE_BUILD`→`_IDENTITY`/`_VERSION`、`PROFILE_UNIQUE_ID`→`PROFILE_ANDROID_ID`
      (Settings 读口)/`PROFILE_TELEPHONY_ID`(Telephony 读口)。新增类别的连带站点:
      `ProfileKind.sourceApi` + `profileKindLabelRes` 臂 + 四语一键 + `ProfileFieldCatalogTest`
      的 20 计数与「sourceApi 互异」断言。**禁止**为 UI 方便再引入分组枚举。

31. **分类与词汇契约(V48,前后端逐类对齐)**:
    **① 一层一名词。** 域名词恒为 **监听(LISTEN,被动采集)/ 功能(FEATURE,主动干预)**;「总开关/Masters/マスター/마스터」在四语的**分区标题与行标签**里全部退役(本产品有 12 个层级的上级闸:1 作用域 + 10 类型 master + 3 服务开关,该词不携带信息);「作用域此应用」是唯一的顶层闸文案。以上由 `DomainVocabularyTest` 常驻拦截(四语禁把作用域称作监听/监视、两页标题在四语必须互异、两页重置确认同动词同句式、退役词不得复现、跨域提示必须点名那个闸)。
    **② 一个概念一个键。** 一个记录类型**只有一个**标题键(`××_records_title`;`settings_records_*` 十键已删,数据管理页与监控页入口共用);一条遗留通路只有一个名字(画像库三段 = 画像库(编辑·校验·绑定) / 旧内置档(仍生效中) / 旧字段覆写(仍生效中))。
    **③ 页面不许自拼分类清单。** 跨域行集合、分区归属、master 归属一律读 `CapabilityMap`(`crossDomainListen` / `of(type)`);kind→master 标签走 `KindLabelRes.masterLabelRes(type)`、master 现值走 `HookSwitch.masterFor(type)`,两处都是穷尽 `when`(新增类型编译期点名)。`CapabilityMapTest` 冻结 81/44/37 与三张跨域类型,并把表钉到 `R.string` / `ModevRoutes` / `hook/probes` 真实存在物上。
    **④ 跨域闸必须可见。** 监听页「环境监听」里 kind 已开而外来 master 关 ⇒ 行 `supporting` 点名该 master 的名字与所在页(`listen_kind_master_off_hint`);**禁**回到"勾了什么都不发生"的静默形态,也**禁**为此把 FEATURE kind 移到监听页(#23 双域闸与 DUAL-12 按域清除不破)。
    **⑤ 配对字段不得分开可写。** Android release↔SDK 由 `pickAndroidRelease` 一次写全,`ProfileValueSets` 是唯一配对表(`ProfileEditorForm` 原私有 `ANDROID_GENERATIONS` 与"认不出就硬写 34"已删);内置画像恒只填机型身份 13 项(D-7),新档的 MODEL/代号取自 `devices.json` 实测条目而非记忆书写。
    **⑥ 删键的 grep 面 = 键名。** V48 实锤:改文案时只搜了中文值、漏了 `DataSettingsScreenTest` 里的 `R.string.settings_records_algorithm` 引用 ⇒ androidTest 编译才炸。删/改任何资源键前先 `grep -rn "<key>" app/src scripts`。

## 已知工具链坑

- **Kotlin `-no-jdk` 下 `java.text.NumberFormat.getCompactNumberInstance` 编译期
  Unresolved**(内嵌 JDK 11 类元数据;AGP `--system jdkImage` 同坑)。
  方案:紧凑数字用 `android.icu.text.CompactDecimalFormat`(API 24+);
  相关单测须 Robolectric(纯 JVM 下 android.icu 是桩)。
- 新增 Java 源:AGP javac 同样受 `--system jdkImage` 限制,勿依赖 JDK 12+ 平台 API。
- **`ListItem` 内部槽位的自定义 testTag 在合并语义树里查不到(V46 实测量)**:Robolectric 下
  实测 `merged(identity)=0 / unmerged=1` —— 挂在 `headlineContent`/`supportingContent` 内
  任意节点的 `Modifier.testTag` 会被并给父合并节点,`onNode(hasTestTag(...))` 直接零命中
  (`fetchSemanticsNodes().first()` 抛 `NoSuchElementException`)。凡对 ListItem 槽位内节点做
  锚定/几何断言,一律 `useUnmergedTree = true`;行容器自身的 testTag(挂在 ListItem 外的 Box 上)
  在两棵树都可见,不必切树。
- **`assertAny` 只有集合接收者、`SemanticsNode` 无 `density`(V46 编译实锤)**:
  `SemanticsNodeInteractionCollection.assertAny(matcher)` 是唯一形态,单节点交互器上
  不存在该扩展 → 「某锚点内含有文本 X」一律写
  `onNode(hasTestTag(t)).assert(hasAnyDescendant(hasText(x)))`;几何换算的密度取
  `ComposeTestRule.density`(节点对象上没有 `density` 属性)。缺件清单再加
  `performTextClearance`(本仓无先例,预填输入框的用例改为**种子空值**规避清文本)。
- **Robolectric 打不动 `ModalBottomSheet` 里的按钮(V47 WP-5 控制实验实锤)**:弹层内容
  在语义树里可见(锚点存在性、行内文案都能断言),但对其按钮 `performClick` 后
  `onSubmit`/`onDismiss` **恒零触发**(推时钟 + `shadowOf(mainLooper).idle()` 均无效)。
  判据不是猜的:对 V39 起全 App 共用、真机正常的 `ModevInputSheet` 本体做同一操作,
  结果 `submitted=[] dismissed=0` ⇒ **harness 限制**,非产品缺陷。故弹层交互一律归
  `app/src/androidTest`(如 `ProfileEditorFlowTest`),Robolectric 档只测「一屏画什么」
  (整屏结构 + 派生态渲染,经 VM 写入面驱动)——两侧合起来才是完整证据,单侧绿不算。
  另注意:同一用例**只能一次 `setContent`**(换参数须拆用例);测「编辑既有条目」的
  夹具必须在库里种下那一行,否则跑到的是「条目缺失 ⇒ 弹栈」分支,屏上零节点。
- **插桩档四种「静默不生效」形态(V48 设备档实锤,四种都红过一整轮才被抓出)**:
  ① **懒列表点不到** —— `ModevSearchableChoiceSheet` = LazyColumn + `heightIn(max=420.dp)`
  + 120 行截断 ⇒ 第 8 行之后的行**压根没组合**,`performScrollTo` 对未组合节点无能为力
  (报「could not find any node」而非「off screen」)。写选择器端到端用例一律先用 sheet
  自带搜索框(`sheet-search-input`)过滤再点 —— 这也是产品给长名单设计的通路,测试绕开它
  等于替产品掩盖问题。
  ② **遮罩吞点击** —— DropdownMenu/Dialog 退场动画期间的 `performClick` 打在 scrim 上,
  零异常、零效果,失败炸在下一句。跨弹层动作前先 `awaitUntilGone(<弹层内锚点>)`
  (V48 实锤形态:筛完类别 → 立刻点折叠搜索条 → 展开「不生效」,真因是菜单还没收干净)。
  ③ **文案断言必须与被测形态同源** —— 「每应用 × 类型」列表的清空确认走
  `danger_clear_page_records_msg`(默认参),「类型 × 全部应用」全局列表才走
  `danger_clear_<type>_msg`;在单应用屏断全局键 = 恒红,而消息只说「not displayed」。
  ④ **枚举字面量不得抄死在断言里** —— 探针/模型改枚举名(V12 `WEB_CRYPTO`/`ENCRYPT`
  → V29 `JS_CRYPTO`/`JS_ENCRYPT`)后,抄字面量的设备用例当场变假红且**消息为空**;
  断言一律 `"kind=${WebViewKind.JS_CRYPTO}"` 这样由枚举派生 + 必带说明文案。
  另:挂在 `ListItem` 槽位(`trailingContent`/`supportingContent`)里的 testTag 只在
  `useUnmergedTree = true` 可见(同坑见上一条 Robolectric 条目),设备档一样成立。
- **Material3 1.4.0 `ListItem` 无 `shape` 形参(javap 实锤)**:圆角/自绘背景/可见波纹
  只能在容器层做(见 #13 行容器契约);另注意 `ListItem` 布局会按 `trailingContent`
  宽度**永久削减文本列宽度**(`TrailingContentStartPadding` 常量),尾栏放短内容 =
  其下方死空间 + 正文提前截断。
- 本 Compose 测试库无 `hasToggleableState`/`waitUntil`/`hasText(Regex)`/
  `assertExists`/`performScrollBy` 与 `SemanticsProperties.OnClick`
  (用 `SemanticsActions.OnClick`);`onRoot`/`printToLog`/`onFirst` 是扩展需
  显式 import;`onRoot()` 在弹窗打开时有 2 个 root 会报错,须
  `onAllNodes(isPopup())`;节点存在断言用
  `onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty()`。
- **单一 LazyColumn item(整段 `SettingsSection` 为一个 item)内,段内行
  `performScrollToNode` 不可靠**(实测滚动停在 161px、目标行仍在组合窗口外,
  仅跨 item 滚动稳定);整段 item 会整段组合,段内行断言存在即可;段尾行先
  滚动到相邻 item 再断言存在/可见。
- 断言计数/数值文案须资源驱动:zh 设备渲染 "2 条"(`settings_total_records_value`
  zh 带后缀),裸数字精确匹配失败;存储值断言防「Room 首查才建库文件」竞态
  (生产 `remember` 快照取 0 B)——测试先 `openOrCreateDatabase` 预建文件。
- 模拟器偶发 ADB `Connection reset`/`Failed to start Emulator console`(测试
  本身 XML 全绿但 Gradle 报驱动失败):重跑即可;跑前 `adb devices` 确认设备在线。
- 并行跑两个 gradle 任务可能触发守护进程 OOM(「daemon disappeared」),lint 等
  重任务串行执行。多项目/IDE 守护进程滞留(Xmx8G 等)会压垮页面文件
  (OS errno 1455/daemon 崩溃):assemble 前清理闲置守护进程,
  重任务用 `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2048m` 逐变体
  串行执行。**Kotlin 编译守护进程同样会被系统杀掉**(V45 实锤:
  `Connection to the Kotlin daemon has been unexpectedly lost`,特征 = 报
  `compile…Kotlin FAILED` 而**无任何 `e:` 诊断**)——此时先
  `-Dkotlin.compiler.execution.strategy=in-process` 重跑再归因源码,
  否则会把环境故障误读成编译错误。
  **OOM 崩溃还会撕裂 KSP 增量缓存**(V47 实锤:commit charge 38.6/40.6 GB 下测试执行器
  `Chunk::new` malloc 失败被杀,之后 `ksp…AndroidTestKotlin` 恒报
  `StreamCorruptedException: invalid stream header: 00000000`,`--rerun-tasks --no-build-cache`
  **也修不好**;缓存目录被 `--stop` 前的 daemon 句柄持住 ⇒ `Device or resource busy`)。
  处置 = `gradlew --stop`(只停闲置)→ 删 `app/build/kspCaches/<variant>`(纯生成物)→ 重跑。
  另:测试执行器被杀时 gradle 可能仍报 `BUILD FAILED` 而**无任何失败用例**,后台通知的
  「exit code 0」在这一族故障里同样不可信,判据是日志里 `BUILD` 行与结果 XML 的 mtime。
- **Robolectric 双模式均不支持 `EXPLAIN QUERY PLAN`**(legacy/native shadow
  都剥离 EXPLAIN 前缀直接执行原查询,返回普通结果集)——查询计划级验证留待
  真机插桩;JVM 门禁用「sqlite_master 索引存在性 + 行为对账」组合
  (`ExplainQueryPlanProbeTest`)。
- **验收编排器四坑(V45 首轮实锤,写/改 `run-acceptance.ps1` 一类编排脚本必读)**:
  ① 子进程**禁止** `& powershell -File … | Tee-Object`:子脚本会拉起 gradle/adb 守护进程,
  它们继承 stdout 句柄 ⇒ 管道永不 EOF ⇒ 编排器在子进程已退出后**永久挂死**
  (特征 = 子脚本日志已到 `install done` 而编排器不打印步结论);一律改
  `Start-Process -RedirectStandardOutput/-RedirectStandardError -PassThru` +
  `WaitForExit(ms)` 轮询(顺带才真正拥有 `-TimeoutMin`)。
  ② PS 5.1 `@(Get-Content -Raw | ConvertFrom-Json)` 对**JSON 数组**得到的是
  「1 个元素 = 数组本身」,`.Status` 变成 `"PASS PASS PASS…"` 全不匹配 ⇒
  逐断言计数**恒为 0 的假绿**;必须先 `| ForEach-Object { $_ }` 解除嵌套。
  ③ `Run-Child` 的透传参数曾名 `$Args`(= 自动 `$args`,大小写不敏感)⇒
  `-Args @("-SkipBuild")` 一类实参**不绑定**,子脚本按默认档静默跑
  (preflight 误跑全档 → 整轮 BLOCKED;MCP 步丢掉 -SkipBuild → 重复构建)。
  自动变量禁用清单见下文「PowerShell 参数名/脚本变量」条目。
  ④ `-Reboot` 步的**假就绪**(V48 实锤):`dnconsole.exe` 在本机**无 stdout、退出码恒 0**
  (直调还会抛 `ApplicationFailedException`),旧实装 `quit` 后仅 `Start-Sleep 8` 就
  `WaitDevice` —— VM 根本没关过,`boot_completed` 立刻为 1 ⇒ 打印「设备已就绪」,
  而后续注入矩阵对着 lspd 的陈旧模块表全红。修法 = **判据只能是失联**:`cmd.exe /c` 包装
  `quit` 后轮询 `sys.boot_completed` 读到非 `1`(90s 窗)才认定关机,否则整轮 BLOCKED。
- **框架守护进程整场缺席(V48 实锤,注入类断言全红的根因,改代码前先看这条)**:
  LSPosed 与 Vector 都是 **Zygisk 模块** —— Magisk 没跑,两者一起不加载,与本项目代码零关系。
  判据(全在设备侧,无需重跑矩阵):`/sbin` 与 `/dev/.magisk` 不存在 +
  `grep -c zygisk /proc/$(pidof zygote64)/maps` == 0 + `ps -A` 无 `magisk` +
  `/data/adb/lspd/log/*` 的 mtime 停在若干天前;`su` **仍然可用**(雷电自带 `ldinit`),
  所以「adb 能 root」不等于「框架在跑」。此时 `modules_config.db` 的
  `apk_path`/`enabled`/`scope` 三项即使全对,靶进程 `/proc/<pid>/maps` 里也只有自身 APK
  ⇒ 注入矩阵与 MCP 记录组必然红。**冷重启不恢复**(V48 实测一次无效),
  恢复属宿主 VM 的 Magisk/root 档位改动 = 共享设备侧操作,须先与使用者确认。
- **PowerShell 管道处理含中文源码会毁文件**:`Get-Content | -replace |
  Set-Content` 以系统 ANSI 解码 UTF-8,中文注释乱码且尾字节可吞换行把下一行
  代码并入注释(V23 实锤)。改文件一律用 edit 工具或 .NET API 字节级读写
  (`[IO.File]::ReadAllBytes/WriteAllBytes + Encoding.UTF8`)。
- androidx.sqlite `SimpleSQLiteQuery` 不解析 `:name` 命名参数(手写 SQL 直绑
  需用 `?` 占位);Robolectric 下 `?N` 编号占位触发 SQLITE_MISMATCH,用纯 `?`
  按序绑定。
- **mikepenz markdown-renderer(V26)**:①默认在 Dispatchers.Default 异步解析
  (Loading 态渲染空 Box),Robolectric 下与 waitForIdle 竞态——一律走核心
  `rememberMarkdownState(content=…, immediate=true)` + m3 的 state 重载
  (**m3 门面未透传 immediate**,直接 content 重载传不进去);②GFM 表格单元格
  解析器保留管道前空格为尾随字符 → 序列化用紧凑 `|Cell|`;③文档级围栏索引按
  content 字符串 remember 缓存,节点定位用 start/endOffset(跨解析实例稳定)。
- 详情页锚点滚动静默失效的形态:**LazyColumn 漏传 `state = listState`** 时
  animateScrollToItem 打在孤立状态上零异常零移动(firstVisible 恒 0);
  回归测试 `AnchorScrollRoboTest`(Robolectric 全装配驱动,秒级)。
- 截断标记/任何引用块插在连续围栏之间会打断代码组聚合(CodeGroupExtractor
  以块级相邻为准)→ 工厂恒将标记后置于全部围栏之后。
- **KDoc 注释内禁止书写 `get*/put*` 形态**:`*/` 会提前终结块注释,余文被当
  顶层声明解析出成串 Syntax error;且 KSP2 只报 `[MissingType] Element
  'CryptoRecordEntity' references a type that is not present`(掩盖真实行号)。
  定位法:临时摘除 `ksp(libs.androidx.room.compiler)` 跑 compile 看真实诊断,
  修毕恢复。写法用「get/put/remove 系列」(V43 T2 再实证:`emit*/ensure*` 同坑)。
- **KDoc/块注释内禁止出现 `/*` 序列**(V45 T3.5 实锤):Kotlin 块注释**可嵌套**,注释里写
  `ui/**`、`/**` 之类会开一层内注释,外层 `*/` 只关掉内层 → 其后代码整体被吞进注释,
  诊断表现为几百行外「Expecting member declaration / Unresolved reference」连锁。
  定位法:`awk` 逐行统计 `{}` 深度到 0 的行,或 grep `^\s*\*.*[^\\]/\*`;写法用「ui 子树」。
- **raw string 结尾不能紧跟引号**:`Regex("""name\s*=\s*"([a-z_]+)"""")` 里 `""""` 会被
  词法器当「三个引号收尾 + 游离引号」(Kotlin 原始串以首个 `"""` 结束,无贪心),
  报 `Syntax error: Expecting member declaration`(位置在下一行,掩盖真因)。
  需要末尾引号时用普通串拼接:`Regex("name\\s*=\\s*\"([a-z_]+)\"")`。
- **Gson 反序列化 Kotlin 数据类会跳过全部默认值**(V45 T3.2 实锤):Gson 无 no-arg 构造器时
  走 `Unsafe.allocateInstance`,于是「可选字段缺省」等价 null——非空集合字段(`stack`/`kinds`)
  直接 NPE、非空 Long(`timestamp`)落 0(记录停在 1970)。解法 = `registerTypeAdapter`
  一个 `InstanceCreator`,以全 1 mask 调 Kotlin 合成的默认值构造器
  (`(..., int $mask, DefaultConstructorMarker)`),非默认参数须给**占位值**
  (真实构造器对非空参数有 `checkNotNullParameter`,传 null 直接抛异常)。
  可空性不可反射(`@NotNull` 是 BINARY 保留,`RuntimeInvisibleParameterAnnotations` 反射读不到),
  故「无默认值的非空字段」必须显式登记清单并由缺值检查点名。
- **legacy Xposed API 82 的 MethodHookParam 无 `hasResult()`**(V43 T3 实锤):
  判「before 已 setResult」只能用 `param.result != null`(before 阶段 result
  恒 null,after 阶段 = 现读)。跨适配器 `Invocation.consumed` 语义须双实现
  同测(libxposed 用 shortCircuited||pendingThrowable,legacy 用
  result!=null||hasThrowable)。
- **管道命令吞 gradle 退出码**(V43 实施纪律):`gradlew.bat ... | grep/tail`
  后 `echo $?` 取到的是管道尾进程状态,构建失败被伪装成 exit 0——批处理
  脚本判 PASS 必须 `set -o pipefail` 或取 `${PIPESTATUS[0]}`,或直接看 BUILD
  SUCCESSFUL 行;后台任务通知的「exit code 0」同理不可信。
- **Gson 树序列化默认省略 JsonNull 成员**(gson 2.13 实测):SQL NULL 必须
  `GsonBuilder().serializeNulls()` 才落 `"value": null`,否则键静默丢失。
- mikepenz 0.38.1 的 MarkdownComponents 无 textLink 钩子;markdown 链接语义
  节点为 0×0 bounds(测试坐标点击不可达)→ 页内导航一律走原生可点击行
  (见强约束 #21 D2.1),勿依赖链接点击链路。
- lint NewerVersionAvailable 按声明依赖逐条计数:直依赖锁定旧版必 +1;
  仅传递使用的库不要显式声明(高亮引擎经 `dev.snipme:highlights` 直依赖并锁
  1.1.0,Kotlin 2.2.0 构建 metadata 安全;禁随上游盲升)。
- **PowerShell 参数名/脚本变量禁用自动变量名 `$args`/`$pid`(V38 实锤,大小写不敏感
  即冲突)**:`function F([object]$args)` 实参不绑定(自动变量遮蔽),`$pid = ...`
  静默失败(只读)——集成脚本中表现为「arguments 恒空」「存活判定拿 shell 自身 PID
  恒真 = 假 PASS」。一律改 `$toolArgs`/`$pidOut`;新写脚本函数先 `Get-Help` 心查
  自动变量表(完整列表见 about_Automatic_Variables)。
- **PS 5.1 `Invoke-WebRequest` 无 `-SkipHttpErrorCheck`**(PS 6+ 参数):参数绑定
  失败 = 语句终止错误,响应变量为 null。非 2xx 断言一律 try/catch +
  `$_.Exception.Response.GetResponseStream()` 读体。
- **雷电 x64 镜像剪贴板静默丢弃写入**(V38 实锤):`ClipboardManager.setPrimaryClip`
  前台执行后 `hasPrimaryClip` 仍 false(直写亦然)。剪贴板内容断言必须经
  `clipboardFunctional()` 哨兵探测降级(Snackbar 证处理器执行),内容同源由
  DiagnosticsReport JVM 单测 + MCP diagnostics 工具双端兜底;真机自动恢复全断言。
- **connectedAndroidTest 测后 AGP 卸载宿主 APK → Vector modules 行删除 + enabled
  复位**(V38 实锤):后续注入矩阵全 FAIL(记录零捕获)且无崩溃日志,极难归因。
  恢复序列:重装 APK → 等 Vector 重注册 → `UPDATE modules SET enabled=1` +
  scope 行(`11-vector-scope.ps1`)→ **重启 VM**(lspd 缓存模块表,不重启不生效)→
  HookInstaller 日志验证。androidTest 抽样必须排在设备集成矩阵之后,或测后即恢复。
- **注入早于 Application 时,任何构造期一次性求值的 Context 依赖初始化都会静默退化**
  (V38 seq 续号 bug 根因):`RecordSink.seqCounter` 构造期 `restoreSeq()` 时
  `ActivityThread.currentApplication()` 为 null → 每次重启 seq 从 1 重发 → 宿主
  `(packageName,processName,seq)` 唯一索引 REPLACE 静默覆盖上一代记录(跨重启数据
  丢失,矩阵表现为「记录凭空消失」)。规约:Context 依赖初始化一律惰性到首次使用
  且路径可见才定档;spoolProvider 同款懒重试。回归 `RecordSinkTest`
  「file becomes visible after construction」。
- **现代路径 after-setResult 曾被拦截器丢弃(V38 修复,功能域全局影响)**:
  `LibxposedPlatform.intercept` 恒 `return result` → after 钩子里的 setResult
  (maps 掩蔽/栈过滤/传感器伪装/VPN·DNS 隐藏/SSL 结果替换)全部空操作。
  after 短路值必须优先返回。新增结果改写型 Hooker 后必须以对应二态断言验证
  (裸挂钩不触发 = 假修复)。
- **Android 14 握手验证只经 Platform 包私有桥(V38 dexdump 实锤)**:
  `Platform.checkServerTrusted(X509TrustManager,X509Certificate[],String,
  AbstractConscryptSocket|ConscryptEngine)` 是 static 包私有(access 0x0008 无
  PUBLIC 位),`getMethod` 不可见必须 `getDeclaredMethod+setAccessible`;参数类型
  为 hide 类需运行时反射解析。TrustManagerImpl 公开重载不在 native 握手路径上。
- **app dex 类必须经 app classloader 显式装载(V38 三重实锤)**:①ART 同 loader
  内类引用解析走 native fast path,`ClassLoader.loadClass` 监视器看不到(okhttp
  全系类);②模块注入类的 forName 委派链不含 app dex;③模块与 app 各带一份
  kotlin-stdlib,`Function0` 等双份 Class 按 `==` 比较 → getDeclaredMethod 必败。
  统一解法:`ProbeContext.appClassLoader` + `Class.forName(name,false,appLoader)`
  (initialize=false 零副作用),以 SSLContext.init/握手回调为补装触发点。
- **Vector 2.2 同一方法的第二个 hook 不执行**(V38 观察):两个 hooker 各自对
  `ClassLoader.loadClass` 注册钩子时,仅先注册者生效——需要类加载观察的 hooker
  共享单一监视器或改用显式预载,禁止各自重复注册同一方法。
- **`install -r` 覆盖安装宿主 APK 后 lspd 不再向新进程注入**(V39 设备实测,与
  connectedAndroidTest 卸载同源):模块表按 APK inode/路径缓存,覆盖安装后冷启动进程
  无 `Loading module` 日志、记录零捕获。唯一恢复 = `dnconsole quit --index 0` +
  `dnplayer -index 0` **整机重启**(受限环境下 `Stop-Process` 拒绝访问,tt-lib
  WaitDevice 自愈同样失效);重启后 `uptime` 归零可证。
- **设备侧改宿主 SharedPreferences 三定律**(V39 令牌矩阵实锤):①必须
  `am force-stop` 后再写,运行中直写会被内存态在下次 persist 时刷回;②XML 内 JSON
  值是 `&quot;` 转义,sed 匹配须按转义形态;③on-device `sed` 替换文本中的 `&` 会
  展开为整个匹配 → 一律「`run-as cat` 拉取 → 本地改 → `run-as` 回写」,禁 on-device
  复杂 sed。
- **`am instrument` 前必须 force-stop 宿主**:残留宿主进程导致测试进程
  `failed to attach` / 原生 segfault(INSTRUMENTATION_CODE 0)。
- **雷电同一设备双序列**(`127.0.0.1:5555` + `emulator-5554`):Gradle
  connectedAndroidTest 会对两序列各跑一遍、互相踩踏(共享 prefs/前台/记录),表现为
  大面积假失败(实锤形态:`Failed to inject touch input` 成片、用例数虚增到 2×);
  单设备回归改用 `adb -s <单序列> shell am instrument -w
  <testPkg>/androidx.test.runner.AndroidJUnitRunner`。
  **V45 再实锤机制**:独立 server 端口是靠 `$env:ANDROID_ADB_SERVER_PORT` 设的
  (`tt-env.ps1` dot-source 时一次设定、**永不清理**),该变量会被同进程的
  `gradlew` 继承 ⇒ AGP 也走 5038 = 天然双序列,且编排器的 `disconnect`/`devices`
  检查打在同一个 server 上(看见双序列仍照常开跑 = 假门禁)。插桩档必须先
  `Remove-Item Env:ANDROID_ADB_SERVER_PORT` 回到默认 server,再断言序列数 == 1,
  不等即 BLOCKED(不许「带着踩踏跑完整轮」)。
- **视口外节点 `performClick()` 静默丢弃**(V45 前端轮 8 例红的唯一根因):Compose 测试的
  `performClick` 是**按节点中心坐标注入真实触摸**,节点若在当前滚动容器视口之外,坐标落在
  窗口外 ⇒ 手势被系统丢弃,**零异常、零导航**,失败几秒后在下一屏的断言上炸开,失败信息只给
  一份与根因无关的容器语义转储(几何猜由此诞生)。整屏 `Modifier.verticalScroll` 页
  (监控页 Hero/统计区之下的入口行、设置页分区尾行)与视口高/文案长度相关,呈「有的类绿、
  有的类红」的刀口形态。写法一律:先 `performScrollTo()`(视口内节点上部分版本会抛断言错,
  需吞掉)或容器 `performScrollToNode(matcher)`,再点;跨屏导航后必须**有界等待目标屏的
  testTag 锚点**(`awaitAnchor`),失败信息必须自带「当前停在哪一屏」。
- **Gradle 构建缓存可交出跨产物 ABI 不一致的陈旧 dex**(V45 实锤,难归因):宿主 APK 是
  新产物,而 `androidTest` APK 内调用点仍是**旧构造器 synthetic** ⇒ 只在设备运行期炸
  `NoSuchMethodError`(JVM 单测同批编译源码故恒绿);`:app:clean` 也不足以复位——命中旧缓存
  key 的 task 会把陈旧产物原样交回。判据用 `dexdump -d classesN.dex | grep "<init>:"` 逐
  产物对签名,处置是 `--rerun-tasks --no-build-cache`;**验收轮的 gradle 调用必须带
  `--no-build-cache`**(从源码可复现优先于构建提速),装机步另加**产物 MD5 == 设备
  `base.apk` MD5** 对账断言(`install` 文本 Success 与 `dumpsys versionName` 都不校验字节)。
- **靶程序 launcher 入口是 `.TriggerActivity`**(非 MainActivity);
  uiautomator dump 在高负载偶发挂起/截断,解析用 perl(宿主 grep 是 ugrep 别名,
  `-o` 行为不同)。

## 测试金字塔(新增行为必须有测试)

- 纯函数 → `app/src/test` 普通 JUnit(ChunkedText / Hex / RecordMarkdownFactory /
  CodeGroupExtractor / TimelineGroup / RecordType…)。
- Android 框架依赖 → Robolectric(`@Config(sdk=[34])`,resources + android.icu 可用)。
- 组合/导航/手势 → `app/src/androidTest` Compose UI 测试(实机或模拟器)。
- 插桩数据前置(`@Before seed`)两条硬口径(V45 实锤):**①种子读回断言的过滤范围必须与
  本用例的清除范围同构** —— `clearAll()` 者按全局计数(证「全表恰为我种的这些行」)、
  `clearPackage(pkg)` 者必须 `RecordFilter(packages = setOf(pkg))`(否则别的包的任何残留都
  炸整类,形态 = `expected:<120> was:<121>`);**②跨屏导航必须有前置** —— V41 根页 IA 五分区
  后主题色板/语言在设置页、端口/令牌在服务页,`createAndroidComposeRule<MainActivity>` 落地
  应用列表,缺前置的用例恒「找不到节点」;统一走 `AnchorProbe`
  (`openSettings`/`openServices`/`awaitAnchor`/`clickScrolledIntoView`)。

## V36 注入侧硬约束(2026-09-04 全功能验收沉淀,改前必读)

- **配置偏差层(V36 D-1)**:注入侧 `ConfigChannel` 持双态——`globalConfig`(宿主 TCP
  推送/远程偏好写)+ `localDeviation`(V35 磁盘兜底 `/data/local/tmp/modev_config_{pkg}.json`
  的 `AppListenOverride`)。有效配置恒为 `global ⊕ deviation`;**禁止**让推送路径直接覆盖
  currentConfig 或让磁盘兜底一次性 resolve 后即失效(Vector 2.2 不投递 XposedService,per-app
  偏差唯一可靠入口是磁盘兜底 + 偏差层)。回归测试 `ConfigChannelDeviationTest`。
- **主动心跳(V36 D-3)**:`ConfigChannel` 连接后每 20s 发 `ping`(远小于宿主 120s/注入侧
  60s 读超时)。协议定义 ping/pong 但**只收不发**会造成空闲半开连接 → 记录滞留 spool。
- **构造器/字段反射钩子(V36 D-2/D-11)**:libxposed 构造器 `chain.proceed()` 返回 void,
  新实例取 `invocation.thisObject`(非 result);声明于父类的字段(如 `Window.mFlags`)
  反射必须**沿 superclass 链**查找。入参改写型干预用 `Invocation.setArg`(libxposed 经
  `proceed(Array)`,legacy 经 args 写回),**禁止** before 清字段后放行原方法(会被设回)。
- **方法注册表含 protected(V36 D-13)**:`BehaviorMethodRegistry` 枚举 `methods ∪ 父类链
  declaredMethods`;`Activity.onCreate/onResume` 等 protected 生命周期此前漏装。
- **WebView 桥注册时机(V36 D-16)**:`addJavascriptInterface` 必须在 WebView 构造器
  **after**(before 阶段 provider 未就绪静默失败,JS 侧永远看不到 `modevJsBridge`);
  探针注入在 `WebViewClient.onPageFinished`(靶 App 须调 super)。
- **类探测钩面(V36 D-12b;V44 T2.8 修订)**:ART `Class.forName` 走原生解析绕过 Java
  `loadClass`,故类隐藏必须覆盖 Java 侧 `ClassLoader.loadClass` 与
  `BaseDexClassLoader.findClass`;**`java.lang.Class.forName` 系不得钩取**——该决策由
  D-17(下方硬约束)取代 D-12b 原文的「同时钩 forName」,代码以 D-17 为准,此处曾与之
  矛盾(V44 审计 P14 登记项已消除)。emit 须在 before **抛 CNFE 之前**(after 在异常路径不执行)。
- **HttpURLConnection/OkHttp 钩面(V36 D-8/D-9)**:抽象类 `getInputStream/getOutputStream`
  无方法体钩不触发,挂 `com.android.okhttp.internal.huc.*Impl`;应用侧 okhttp 类安装期未
  加载,经 loadClass 监视器**惰性安装**(幂等)。
- **集成测试环境(LDPlayer x64)**:靶程序 `:testtarget`(debuggable,run-as 零 su 查询);
  adb 独立端口 5038;`cached_apps_freezer` 禁用;VM 死亡 WaitDevice 冷重启自愈。掩蔽型
  device_env kind 在预干净模拟器「无脏可掩即零记录」属正确行为,矩阵标 SKIP。
- **禁止钩取依赖调用方栈帧语义的方法(V36 D-17 崩溃教训,硬约束)**:`Class.forName(String)`/
  `Class.forName(String,boolean,Class)` 等以 `Reflection.getCallerClass()` 取调用方 classloader
  的方法**一律不得钩取**——LSPlant 桥接帧(`HookBridge.invokeOriginalMethod`)替换真实调用方后,
  应用侧单参 `forName` 加载自身类全部以错误 loader 解析 → CNFE → 启动即崩(androidx.startup/okhttp
  首当其冲),且钩子无条件安装、**开关关闭也崩**。Xposed 类探测记录面仅靠 `ClassLoader.loadClass`/
  `BaseDexClassLoader.findClass`(实例方法,无 caller-frame 依赖,安全)。新增任何钩子前必须评估:
  ①是否依赖调用方帧;②是否在热路径无条件安装;③功能开关关闭时是否仍产生拦截开销。
- **冷启动冒烟门禁(V36)**:注入侧任何钩子变更后,必须跑 `scripts/test-integration/23-run-smoke.ps1`
  (全功能 ON 下逐个作用域 App 冷启动 + L-SMOKE-01 帧语义断言 + 驻留 ANR 检查)全绿方可交付;
  真机/稳定模拟器为最终聚合验收环境。