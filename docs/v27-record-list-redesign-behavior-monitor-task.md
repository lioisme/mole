# Modev V27 任务文档:记录列表视觉重构 · 行为监听一等公民

| 项 | 值 |
|---|---|
| 版本基线 | V26 → **V27**(本文档实施后沉淀) |
| 变体 | modern / legacy 双变体均需编译通过;wire 协议 VERSION 恒 4(`recordType` 泛型信封即第三类型扩展点,注入侧零握手变更) |
| 需求来源 | 用户需求(2026-08-26)+ 决策确认:①一期覆盖文件读/写/删/重命名/列目录 + Assets + Shell;②文件内容捕获支持、默认关、受 `maxBytesPerField` 限制;③写/删/Shell 默认开,读/列目录/Assets 默认关 |

## 进度跟踪

| 任务 | 状态 | 备注 |
|---|---|---|
| T0 任务文档落盘 | ✅ 已完成 | 本文档建立,M1-M6 里程碑与 T 任务分解;审计结论来自 2026-08-26 三路并行全量代码审计 |
| M1-T1 MiddleEllipsisText 中缀省略工具 + JVM 测试 | ✅ 已完成 | 新增 MiddleEllipsisText.kt(纯函数 ellipsize 字符预算 + 组合层 TextUtils.ellipsize MIDDLE 像素收敛,两帧自适应、防回摆);MiddleEllipsisTest 6 用例(修复预算=2 超限边界:keep<2 时仅省略号) |
| M1-T2 KindIcons 类别图标映射表 | ✅ 已完成 | KindIcons.crypto(CIPHER 按方向 LockOpen/EnhancedEncryption)+ KindIcons.sql 双族映射,material-icons-extended Outlined 全 Outline 风格统一 |
| M1-T3 RecordListItem 统一行脚手架(Crypto/Sql 双族先行) | ✅ 已完成 | RecordRow.kt 重写:RowPresentation 数据出口 + RowScope.RecordListCard 骨架(leading 36dp 类别色@13% 圆角徽章 + headline mono Medium + 元行 AnnotatedString 类别词元着色 + 预览行 + 方向 MicroBadge 取代 AssistChip,minHeight 68dp/间距 12dp);导出函数(kindLabelRes 等 9 个)原样保留零波及 |
| M1-T4 列表页装配(计数副标/长按复制接线/空态升级) | ✅ 已完成 | MonitorRecordsScreen:totalCount 流首次消费(折叠态 caption「N 条记录」AppCountFormat.grouped + locale 驱动)+ SnackbarHost + 长按复制摘要(CryptoRow.copySummary/SqlRow.sqlRowSummary → 剪贴板,双列表实例均接线);RecordTimelineList 加 onRecordLongPress 可选参;EmptyState 升级 56dp 圆形容器 + hint 副文(可选参零波及);新增 i18n 键 ×3 双语(detail_empty_hint/records_count_subtitle/record_copied_toast) |
| M1-T5 测试对齐 + 单测验证 | ✅ 已完成 | 全量 :app:testModernDebugUnitTest 绿(450 用例,含新增 6);RecordRowLayoutTest 断言文本契约不变自然兼容;TimelineHeader surfaceContainer/10dp;设备插桩验证归入 M6 | |
| M2-T1 BehaviorKind/BehaviorOperation 枚举 + RecordType.BEHAVIOR | ✅ 已完成 | BehaviorKind 7 值(默认态对齐决策)+ BehaviorOperation 14 值(kind 归属矩阵);RecordType.BEHAVIOR(wire=behavior)/RecordTypes.BEHAVIOR;RecordTypeTest 兼容 | |
| M2-T2 CategoryTaxonomy +7 条目 + validateOperations 分支 | ✅ 已完成 | SIGNAL={write,delete,rename,shell}/NOISE_HIGH={read,list,asset};validateOperations BEHAVIOR 分支;defaults_align 测试扩展三类型对齐 | |
| M2-T3 HookSwitch(master+7 kinds+fileContentCapture+ignorePathPrefixes) | ✅ 已完成 | behaviorListen 缺键补 true 同 masters 惯例;behaviorEnabled(kind)/anyBehaviorEnabled;HookSwitchTest 更新 all_off/master_gate 并新增 behavior_enabled_maps/noise_defaults 用例 | |
| M2-T4 AppListenOverride 白名单/偏差项扩展 + EffectiveSwitch 互逆 | ✅ 已完成 | VALID_KINDS ∪ BehaviorKind;偏差项 fileContentCapture/ignorePathPrefixes;isDefault 归零;readKind/applyKinds 三族对称;JsonTest/EffectiveSwitchTest 各 +2 行为用例(含全类别互逆不变量自动覆盖行为族)|
| M2-T5 CapabilityKind.BEHAVIOR_LISTEN + 注册 | ✅ 已完成 | BehaviorListenCapability(anyBehaviorEnabled 投影);ModevApp capabilityRegistry 第三注册,主链路零改动 | |
| M2-T6 存储 v8→v9 迁移(10 列)+ DAO 投影扩展 | ✅ 已完成 | path/destPath/command/fileSize + content 五元组;ROW_SELECT 扩展;WHERE_FILTER 关键字 LIKE 增 path/destPath/command;CryptoRecordDaoTest 全部迁移装置补 8_9 注册 + 新增 v8→v9 专项测试(数据保留+新列可写可读)| |
| M2-T7 BehaviorRecord wire 模型 + codec + registry 注册 | ✅ 已完成 | BehaviorRecord(MonitoredRecord 第三实现,commandDisplay 展示助手);BehaviorRecordCodec(decode 校验同规);LogRepository writeBatch 第三分派 + cryptoRegistry 注册;HookInstaller jsonOf 第三编码 | |
| M2-T8 mappers 四向映射 + RowModel.BehaviorRow + 验证测试 | ✅ 已完成 | BehaviorRecordMappers(toEntity/toBehaviorModelOrNull/behaviorRowModelOrNull/toJson 导出);view/enumOf 提 internal 共享;RowModel.BehaviorRow(headlineText basename 单一事实源)+ RecordDetailModel.BehaviorDetail;toRowModelOrNull/toRecordDetailOrNull 分派;BehaviorRecordCodecTest 4 用例;全量单测绿(453+)| |
| M3-T1 BehaviorProbe + MethodRegistry + BaseHooker emit 管线 | ✅ 已完成 | BehaviorBaseHooker 闸门序(kind 前置→PathGate→栈 denylist→SessionThrottle→组装);BehaviorMethodRegistry(boot loader 优先,类缺失零安装);BehaviorProbe 装配五 Hooker;HookInstaller 默认清单 +BehaviorProbe | |
| M3-T2 FileMeta/FileStream/NioFiles/Asset/Shell 五 Hooker | ✅ 已完成 | FileMeta(delete/renameTo 以 true 为准/list 条目数);FileStream(双流构造器全重载,fd 变体记 source;append 标记);NioFiles(API26+ 缺类静默,内容预览仅整读 API=readAllBytes/readAllLines 返回值与 write 实参,流式 API 零触碰);Asset(open/openFd 元数据+length,list 计数,流绝不读取);Shell(Runtime.exec 形态分派 argv+workdir/env/redirects + ProcessBuilder.command/directory)| |
| M3-T3 SessionThrottle 节流 + 路径降噪 denylist + 测试 | ✅ 已完成 | SessionThrottle(access-order LRU cap256 + 1s 窗口,nanoTime 注入纯 JVM 可测);PathGate(内置 9 系统前缀 + modev_spool 子串 + modev_seq_ 基名 + 用户 ignorePathPrefixes 叠加);SessionThrottleTest 5 用例 + PathGateTest 4 用例绿 | |
| M4-T1 BehaviorColorTokens 主题色板 | ✅ 已完成 | 7 类别浅/暗双板 + behaviorKindColor/behaviorKindColorForMode(与既有两族访问器同规) | |
| M4-T2 导航路由 records/behavior + AppMonitor 第三入口/StatsCard 三段化 | ✅ 已完成 | BEHAVIOR_RECORDS 路由+构造函数+slideComposable 注册;AppMonitorScreen +onOpenBehavior 参数与第三 EntryRow(testTag monitor-entry-behavior);StatsCard 三段比例条+三行图例;VM +behaviorCount;ModevNav 接线 | |
| M4-T3 行为列表页 + 详情页(Hero/锚点/markdown 分区) | ✅ 已完成(详情部分于 M2/M1 提前落地) | BehaviorRecordsScreen 薄壳(kinds=7 behaviorKindLabelRes);RecordRow 第三形态;长按复制 behaviorSummary;BehaviorHero(titleMedium mono 路径 headline)+behaviorAnchors(command/content/params/stack)+BehaviorLoadedContent(meta 表→bash 围栏命令→内容字节代码组→参数→栈);导出估算 estimateBehaviorJsonChars + toJson 分派;顶栏标题 behaviorHeadline;修复 MarkdownByteSectionTest 历史 R.string→plurals 引用编译债 | |
| M4-T4 监听设置页行为分区(IgnorePathSheet/fileContentCapture 行) | ✅ 已完成 | 行为分区(master 行 listen-row-master-behavior + 7 类别行复用 kind 标签 + 捕获文件内容开关 listen-row-file-content + 忽略路径前缀 sheet 行 listen-row-ignore-path);IgnoreStackSheet 重构为共用 PrefixListSheet(IgnorePathSheet 同构零重复);VM +setBehaviorMaster/setFileContentCapture/setIgnorePathPrefixes;定制计数含新偏差项 | |
| M4-T5 数据管理/危险清理三类型 + i18n 双语落地 | ✅ 已完成 | ClearTarget +BEHAVIOR(计数行/清理行/确认消息);新增键 ×20 全部双语齐备(behavior_records_*/filter_type_behavior/kind_behavior_*×7/op_behavior_*×13/meta_path 等/section_command/section_content/behavior_monitor_master/switch_file_content_capture/capture_ignore_path 组/settings_records_behavior 组)| |
| M4-T6 设备插桩测试(雷电)行为列表/详情/设置 | ✅ 已完成(并入 M6) | 全套 69 用例于雷电 127.0.0.1:5555 通过;adb -s 手动驱动(gradle 多设备 ANDROID_SERIAL 不生效,物理机安装失败 INSTALL_FAILED_MEDIA_UNAVAILABLE 的确定性绕行) | |
| M5-T1 observePackages 去硬编码 + 注释乱码块重写 | ✅ 已完成 | observePackages(null) 跨类型统计(应用列表徽标修复);LogRepository.kt 全文 U+FFFD 乱码清零(类头 KDoc/管道注释/companion 常量块/PipelineStats 共 ~30 处重写,顺带消除两处历史重复声明)| |
| M5-T2 AGENTS.md V27 沉淀(词汇表+行为监听契约) | ✅ 已完成 | 头部定位句/版本链 +V27;词汇表 +行为监听/内容捕获两词条;新增强约束20 行为监听一等公民契约(wire/闸门序/被动红线/五处同步/三入口/testTag 清单/行脚手架);lint 门禁基线更新为 36(V27 由 37 净降 1)| |
| M6 全量回归(testModernDebugUnitTest + assembleModern 双变体 + lint 基线 37/0) | ✅ 已完成 | 单测全绿(456 用例);assembleModernDebug/Release 双变体通过;lint **0 errors / 36 warnings**(优于基线,V27 零新增);设备插桩 **69/69 全绿**(雷电 127.0.0.1:5555;期间修复 RecordRow 重构误删尾栏时间的真回归 + 两处因新增行导致的滚动断言更新:FourScreensNavTest 入口行与清空全部行)| |

---

## 1. Part A — 记录列表 UI 重构(M1)

### A1. 设计原则

1. **一屏三问**:每行 0.5 秒回答「什么操作 / 关键参数 / 何时发生」——图标定类、标题定主体、元行定语境、尾栏定时序。
2. **色不过三**:每行至多一种语义色(类别色),仅用于 leading 图标容器 + 元行类别词元。
3. **等宽字体只给机器数据**(算法名/SQL/路径/命令);自然语言标签用常规字阶。
4. **路径中缀省略**:`/data/data/pkg/files/config.json` 保头掐腰。
5. **密度即高级**:行高 ≥68dp,水平 14dp/垂直 12dp/元素间距 12dp,圆角沿用 `AppShapes.large(16dp)`。

### A2. RecordListItem 结构(新 `ui/components/RecordListItem.kt`)

```
Surface(shape=large(16dp), color=surfaceContainerLow, testTag="record-row")
└─ Row(minHeight 68dp, padding(h14,v12), spacedBy 12dp)
   ├─ LeadingBadge(36dp, RoundedCornerShape(10dp)):kindColor@13% 容器 + kindColor 图标 20dp
   ├─ Column(weight1f, spacedBy 2dp)
   │    ├─ Headline bodyLarge Medium onSurface maxLines1(Crypto=算法名 mono / Sql=SQL 首行 mono)
   │    ├─ MetaLine labelMedium:<类别词元·kindColor> · 操作 · 附加(dbName/行数 plurals)
   │    └─ PreviewLine bodySmall mono onSurfaceVariant maxLines1(valuableDataPair / SQL 预览)
   └─ TrailingColumn(End, spacedBy 4dp)
        ├─ 时间 labelSmall(formatSecond 口径)
        └─ MicroBadgeRow(方向箭头/验签 ✓✗/行数,紧凑胶囊 labelSmall,替代 AssistChip DirectionChip)
```

硬性要求:`record-row`/`timeline-header` testTag 不变;行内派生值 remember 化;leading 图标装饰化语义;整行可点。

### A3-A5. 配套

- `KindIcons.kt`:kind→ImageVector 纯函数映射(Crypto 12/Sql 5);
- `MiddleEllipsisText.kt`:纯函数 `middleEllipsize` + Composable(TextUtils.ellipsize MIDDLE 兜底);
- 计数副标:topBar 内 CollapsedSearchInput 下方 caption,`AppCountFormat.grouped(totalCount)` 资源格式串;
- 长按复制:Crypto/Sql copySummary 接线 → 剪贴板 + Snackbar;
- 空态:EmptyState 扩展 tinted 圆形图标 + 副文(null 安全可选参);
- 组头微调:surfaceVariant→surfaceContainer、24dp 高。

---

## 2. Part B — 行为监听(M2-M4)

### B1. 枚举与开关(core/model)

```kotlin
enum class BehaviorKind { FILE_READ(false), FILE_WRITE(true), FILE_DELETE(true),
    FILE_RENAME(true), FILE_LIST(false), ASSET_READ(false), SHELL_EXEC(true) }

enum class BehaviorOperation { OPEN_READ→READ, READ_ALL, OPEN_WRITE, WRITE_ALL,
    MKDIR, MKDIRS→FILE_WRITE; DELETE; RENAME; LIST; ASSET_OPEN, ASSET_OPEN_FD, ASSET_LIST; EXEC }
```

同步:`CategoryTaxonomy` +7(SIGNAL={write,delete,rename,shell}/NOISE_HIGH={read,list,asset});`HookSwitch` +master behaviorListen=true + 7 kind 字段 + fileContentCapture=false + ignorePathPrefixes=[];`AppListenOverride` VALID_KINDS +7、偏差项 +fileContentCapture/ignorePathPrefixes;`EffectiveSwitch.readKind`/`applyKinds` +BEHAVIOR 分支(互逆测试强制);`RecordType.BEHAVIOR(wire="behavior")`;`CapabilityKind.BEHAVIOR_LISTEN`。

### B2. 存储 v8→v9

新增 10 可空列:path/destPath/command/fileSize + contentHex/contentB64/contentText/contentLength/contentTruncated(五元组复制既有字节列约定)。纯 ALTER TABLE,无新索引(v8 `(recordType,packageName,timestamp)`、`(recordType,kind)` 已覆盖)。

### B3. wire 与仓库

`BehaviorRecord`(path/destPath/command/fileSize/content BytesView/params{append,api,dirCount…});`BehaviorRecordCodec`(decode 校验枚举+包名,非法返 null 走「丢弃但仍 ACK」);registry 注册第三 codec;mappers 四向(wire↔entity↔BehaviorDetail↔BehaviorRow);`toRowModelOrNull` "behavior" 分派;修复 observePackages() CRYPTO 硬编码。

### B4. 注入侧(hook/,克隆 DatabaseProbe 模板)

- `probes/BehaviorProbe.kt`(recordType="behavior")+ `hooks/BehaviorMethodRegistry.kt`;
- Hooker 五件:FileMeta(delete/renameTo/mkdir(s)/list×4)/FileStream(FIS/FOS 构造器×3,NioFiles API26+ 缺类静默零安装)/Asset(open×4/openFd/list)/Shell(Runtime.exec×6 + ProcessBuilder.start);
- emit 闸门序:config 快照 → kind 门控(volatile 前置)→ **路径降噪**(DEFAULT_IGNORED_PATHS:/proc,/sys,/dev,/system,/apex,/vendor,/product + 模块 spool + 用户 ignorePathPrefixes)→ 栈 denylist(既有)→ **SessionThrottle**(LRU cap256,key=kind|op|path,TTL 1000ms)→ 内容捕获(fileContentCapture 开启才采,仅整读 API:Files.readAllBytes/readAllLines/write/copy 与 AssetManager.open;流构造器路径绝不碰句柄——被动红线 #11)→ sink.emit(seq/WAL/ACK 全链复用)。

### B5-B8. UI 全链路

- 监听设置页:「行为监听」分区 = master 行 + 7 类别行 + 捕获文件内容开关行(listen-row-file-content)+ 忽略路径前缀行(IgnorePathSheet,克隆 IgnoreStackSheet);
- 路由 `records/behavior/{packageName}`;AppMonitor 第三 EntryRow(FolderOpen)+ StatsCard 三段化;危险清理/数据管理三类型;
- 列表页 `BehaviorRecordsScreen`;详情页 Hero/锚点(content/command/params/stack)/markdown 分区(meta 表→command bash 围栏→content 字节组→params→stack);
- `ui/theme/BehaviorColorTokens.kt` 7 类别浅/暗双板。

## 3. 里程碑依赖与验收

M1 ∥ M2 → M3(依赖 M2)→ M4(依赖 M1+M2+M3)→ M5 → M6。每里程碑出口:新增单测绿 + lint 零新增 + 双变体编译通过。

## 4. 全局约束(实施全程)

i18n 双语齐备/AppCountFormat/plurals;底部 sheet 规范(imePadding 禁叠 navigationBarsPadding);弃用 API 零容忍;PackageBoundaryTest(ui 禁 import data.db/server);LazyColumn 显式 state=listState;分页大窗口+cachedIn;markdown 版本锁 0.38.1 禁升;PowerShell 禁管道改中文文件;lint 基线 37 warnings/0 errors 不新增;模拟器只用雷电(D:\leidian\LDPlayer14)。
