# V37 双域拆分重构任务书(监听域 / 功能域)

> 基线审计:2026-09-04 全量源码审计(290 源文件 / ~32.3k 行;10 RecordType / 78 类别 / 60+ 钩子)。
> 决策(用户确认):①按**类别粒度**拆分域归属(CategoryTaxonomy 单一事实源);②**允许必要迁移**
> (Room / wire VERSION 4→5);③弹窗关键词库 = **全局库 + 每应用启用**;④伪装值 = **内置档位 + 可编辑画像**。
> 验收红线:开关全关时干预零副作用;功能必须 UI 可达;双语键集 100% 对齐;lint 基线零新增;
> 冒烟全绿;新增行为 100% 有测试。

## 域划分基线(D-0)

- **监听域(LISTEN)**:CRYPTO 12、DATABASE 5、BEHAVIOR 7、PREFS 5、WEBVIEW 5、SYSTEM 3、
  NETWORK.REQUEST、UI.{DIALOG_SHOW, WIDGET_TEXT, ACTIVITY_START, ACTIVITY_RESUME}、
  DEVICE_ENV.{DETECT_ROOT, DETECT_EMULATOR}(共 44 类)
- **功能域(FEATURE)**:PROCESS_CONTROL 3(退出拦截/崩溃预防/解除录屏)、UI.DIALOG_KEYWORD(弹窗拦截)、
  NETWORK.{SSL×6, PROXY×4, VPN×4}(抓包环境)、DEVICE_ENV 19 干预项(Root/Xposed/模拟器/proc 伪装)
  (共 37 类)

## 违规清单(审计结论,修复映射见任务 ID)

### A. 功能性缺陷(Critical)
- A1 EmulatorBuildHooker 安装期无条件改写 Build 静态字段(EmulatorBuildHooker.kt:26,47-81)→ T0.2
- A2 弹窗关键词拦截不看 dialogKeyword/uiListen 开关(DialogKeywordInterceptHooker.kt:35-36)→ T0.1
- A3 干预钩子直读裸布尔位绕过 master(ExitInterceptHooker.kt:37,56 等全系)→ T0.1
- A4 dialogKeywords 有管线无 UI 编辑入口(功能不可达)→ T2.1/T2.2/T3.5
- A5 INTERVENTION_KINDS 漏 EMULATOR_SENSOR/DIALOG_KEYWORD(EffectiveSwitch.kt:19-55)→ T0.4

### B. 架构 / 领域模型
- B1 CategoryTaxonomy 无域字段,干预知识散落三处 → T1.1
- B2 HookSwitch ~90 字段上帝对象 → T1.2
- B3 Capability 层死代码(10 probe 仅 4 注册;缺 WEBVIEW_LISTEN)→ T1.4
- B4 伪装值全部硬编码注入侧 → T2.3/T2.4
- B5 NETWORK/DEVICE_ENV/UI 三类型观测干预混装 → T1.1(D-0 基线)+ T3.x(UI 分域)
- B6 wire 协议无功能数据通道 → T1.3
- B7 死枚举操作 RUNTIME_EXIT/RUNTIME_EXCEPTION/SCREEN_CAPTURE_INTENT → T0.5

### C. 信息架构 / 界面
- C1 AppMonitorScreen 10 入口一字平铺,监听/干预混排,图标复用冲突 → T3.1
- C2 启动/清空按钮在正文不在 Appbar → T3.2
- C3 每应用设置页 = 10 master + 78 类别开关墙 → T3.4
- C4 StatsCard 十段比例条不可读 → T3.3
- C5 数据管理仅覆盖 5/10 类型 → T3.6
- C6 ui/logs 包名违反词汇表 → T4.4(随标签重构一并改包)
- C7 RecordRow.kt 1111 行上帝文件 + 标签函数双轨 → T4.4
- C8 偏差计数逻辑双实现 → T4.4
- C9 重复 import(AppMonitorScreen Tune/FolderOpen)→ T4.1

### D. 视觉质感
- D1 Scaffold containerColor 缺 3 处(AppsScreen:136、MonitorRecordsScreen:317、RecordDetailScreen:242)→ T4.3
- D2 间距双标 16/24dp、页尾 24/8dp、Spacer(padding) 反 idiom → T4.1
- D3 alpha 魔法数散落、行内边距 14/16dp 双标 → T4.2
- D4 Appbar 范式三种并存、Icons.Default/Filled 混用、stringResource/res.getString 双轨 → T4.3
- D5 筛选激活指示漏 pinScoped → T4.5
- D6 sheet 实现双轨(Diagnostics 端口 sheet)→ T4.5
- D7 过时 KDoc 多处 → T4.6

### E. i18n / 文案
- E1 干预功能文案一律冠「监听」名义;strings 混排 → T5.1
- E2 master 键名四套构词法 → T5.2
- E3 错位键 / 命名不齐 → T5.2

### F. 测试 / 流程
- F1 开关旁路无回归测试 → T0.x/T6.1
- F2 AGENTS.md 契约滞后 → T6.4
- F3 双域门禁无覆盖 → T6.1/T6.2

---

## 任务清单(按开发顺序;完成即勾选并备注)

### Phase 0 · 功能缺陷修复(阻断级)

- [x] **T0.1 统一干预闸门**:全部干预动作点改走 master∧kind 门控
  (`processControlEnabled`/`deviceEnvEnabled`/`networkEnabled`/`uiInteractionEnabled`),
  消灭裸布尔位直读;DialogKeywordInterceptHooker 补开关前置检查。
  验收:JVM 门禁测试——master=false / kind=false 时 setResult/field.set 零调用。
- [x] **T0.2 EmulatorBuildHooker 门控**:静态字段改写受
  `deviceEnvEnabled(EMULATOR_BUILD)` 约束;新增 ConfigWatcher 配置变更订阅,
  开关后开时一次性惰性补写(AtomicBoolean 幂等)。
  验收:开关全关 Build 字段零修改;开启后补写生效。
- [x] **T0.3 录屏解除一位三钩子语义锁定**:文档化 + 契约测试(位变化三钩子一致响应)。
- [x] **T0.4 INTERVENTION_KINDS 派生化**:删除手工集合,改由
  `CategoryTaxonomy.domain == FEATURE` 派生;补 EMULATOR_SENSOR/DIALOG_KEYWORD 缺口(随 T1.1)。
- [x] **T0.5 死枚举操作清理**:RUNTIME_EXIT/RUNTIME_EXCEPTION/SCREEN_CAPTURE_INTENT
  删除或补 emit 点;枚举↔emit 一致性测试。

### Phase 1 · 领域模型重构

- [x] **T1.1 CategoryTaxonomy 域字段**:`CapabilityDomain { LISTEN, FEATURE }`;
  78 类别逐一标注(D-0 基线);域归属快照测试锁定;新增类别未标域即红。
- [x] **T1.2 配置模型拆分(实施口径修订)**:布尔开关保持 HookSwitch 平铺
  (UI/注入/协议零漂移),新增**嵌套 `FeatureConfig` 数据段**承载功能域数据
  (dialogKeywords 迁移 + 画像引用 + 黑名单追加项);Gson 单 JSON 兼容
  (读旧写新,`configVersion` 1→2 迁移);读写互逆测试扩展。
- [x] **T1.3 wire 协议演进(实施口径修订)**:信封 **VERSION 恒 4 不变**
  (feature 段随 `switch` JSON 字段透传,协议形状零变化——升 VERSION 只破坏
  旧模块兼容性而无任何收益,对齐项目「wire 零感知」哲学);配置 JSON 经
  `configVersion` 演进。兼容矩阵测试:旧扁平 JSON / 新 feature 段 JSON / 磁盘兜底文件。
- [x] **T1.4 Capability 层退役(实施口径修订)**:审计确认 `domain/capability` 全包
  **零消费者**(registry 仅 ModevApp 注册、UI/测试均不读取)——按「重建或退役」决策
  选**退役**:删除 Capability/CapabilityKind/CapabilityRegistry + 5 实现类,
  双域 UI 唯一驱动源 = `CategoryTaxonomy.forDomain`(消除平行死抽象,B3 根因修复)。

### Phase 2 · 功能域数据基础设施

- [x] **T2.1 关键词库存储**:Room v13→v14 表 `keyword_library`
  (text 唯一/matchMode CONTAINS|REGEX|EQUALS/enabled/createdAt)+ 每应用启用集
  (AppListenOverride.keywordIds);KeywordLibraryRepository CRUD + 内存快照;
  Robolectric 仓库测试 5 例。
- [x] **T2.2 关键词下发与消费**:有效规则表(全局启用 ∧ 应用启用,null=跟随全局)
  经 SettingsRepository.withKeywordRules 注入 FeatureConfig.dialogKeywordRules,
  随 per-session resolve 下发;库变更 → ConfigServer.pushToAllConnected 重推;
  DialogKeywordInterceptHooker 规则化消费(matchMode);KeywordRule 纯函数测试 5 例。
- [x] **T2.3 伪装画像模型**:SpoofProfile + SpoofProfiles(Pixel 7 Pro / 小米 13 / auto);
  EmulatorBuild/DeviceSysProp/SystemProperties/DeviceTelephony/DeviceCpu/ProxyHide
  六钩子改读 `config().activeSpoofProfile()`;机型字面量收敛至 SpoofProfiles 单一事实源;
  SpoofProfilesTest 6 例(覆写分派/漂移回落/扩展)。
- [x] **T2.4 黑名单可增删**:内置基线 ∪ 用户追加项(Root 包名/Xposed 前缀/Frida 端口/
  maps·mountinfo·cgroup 掩码词),经 FeatureConfig.extra* 下发(只增不删语义)。

### Phase 3 · UI 信息架构重构(双域界面)

- [x] **T3.1 AppMonitorScreen 双区重排**:「监听」区(监听设置 + 6 记录入口)+「功能」区
  (功能设置 + 4 功能记录入口);图标唯一化(Tune/Sensors 复用冲突消除);重复 import 清零。
- [x] **T3.2 Appbar 动作上移**:启动/清空进 TopBar actions(`app-launch`/`app-clear-records`);
  正文操作卡删除;10 列表页 appbar 统一「清空本类」(`records-clear-type` →
  `records-clear-confirm` 居中确认,RecordsViewModel.clearCurrentScope → deleteByFilter);
  设备测试断言同步迁移。
- [x] **T3.3 StatsCard 重设计**:监听/功能双段聚合(DomainBlock:域计数 + 比例条 + FlowRow 图例),
  零记录域隐藏比例条;十段单条退役。
- [x] **T3.4 每应用设置页拆分**:「监听设置」(LISTEN 域:6 master + 6 类类别 + 环境观测分区 + 微调)
  与「功能设置」(FEATURE 域:4 master + 六功能卡 + 关键词 chips + 画像 sheet);
  ToggleListItem/KindLabelRes/deviationCount 三处共享收敛(C8 消除)。
- [x] **T3.5 全局功能页**:关键词库管理 / 画像管理 / 黑名单追加(设置域新分区「拦截与伪装」;
  写路径 setGlobalFeature → feature_global 键 + switch_global 种子重播 + 全会话重推)。
- [x] **T3.6 数据管理补齐 10 类型**:ClearTarget sealed ByType/All,RecordType.entries 枚举驱动。
- [x] **T3.7 路由与导航更新**:`apps/{pkg}/feature-settings`、`settings/keywords`、
  `settings/profiles`、`settings/blacklist` 四路由 + ModevNav 注册。

### Phase 4 · 视觉质感打磨

- [x] **T4.1 间距 token 化**:`Dimens` 对象(sectionGap=24/sectionGapDense=16/pagePadding=16/
  pageBottom=24/rowPadding=16,双轨制文档化);Spacer(padding) 反 idiom 3 处归零;
  重写页(AppMonitor/Feature/DataSettings)全面接入。
- [x] **T4.2 alpha 语义 token**:`Tokens`(pill/badge/chevron/disabled/emptyIcon×2/codeBorder);
  EmptyState/RecordRow/AppMonitor/DataSettings 魔法数收口;记录行内边距 14dp→16dp。
- [x] **T4.3 顶栏/Scaffold 统一**:containerColor 3 处补齐(AppsScreen/MonitorRecords/RecordDetail);
  FullscreenTextDialog 保留 Close 图标(全屏 Dialog 非次级页,语义差异,经评审豁免);
  新页 Icons.Filled 单一风格。
- [x] **T4.4 标签函数归一 + 包更名**:`ui/logs` → `ui/detail`(23 文件,词汇表契约修复);
  `KindLabelRes` 提取(监听/功能两页共用);listenSummary 偏差计数收敛
  `AppListenOverride.deviationCount`。RecordRow.kt 拆分留待后续(见进度日志备注)。
- [x] **T4.5 sheet 双轨合一 + 筛选指示**:Diagnostics 端口 sheet 补 sheetState+hide 退场
  + titleLarge 对齐;ScopeFilterMenu 激活指示补 pinScoped(并修复 showModules 默认关误判恒亮 bug)。
- [x] **T4.6 过时 KDoc 刷新**:AppMonitorScreen/DataSettingsScreen/ModevRoutes/监听页
  头注释重写为 V37 双域描述。

### Phase 5 · i18n / 文案重构

- [x] **T5.1 干预文案去「监听」冠名词**:设备环境监听→设备环境伪装、进程控制监听→进程守护、
  网络环境监听→网络干预、UI交互监听→UI 交互;AGENTS.md 词汇表新增双域/词库/画像/黑名单四词条。
- [x] **T5.2 命名对齐**:network_records_title "Network"→"Network Records/网络环境记录";
  新页 master 统一走 feature_masters_section 分区(旧四套构词法键保留为行标签,
  新增键一律 `feature_*`/`settings_*` 前缀,禁止再扩构词法分支)。

### Phase 6 · 门禁与回归(横切)

- [x] **T6.1 测试金字塔补齐**:域快照(CategoryTaxonomyTest 4 例)、开关旁路回归
  (FeatureGateBypassTest 14 例)、词库 CRUD(KeywordLibraryRepositoryTest 5 例)、
  画像 resolve(SpoofProfilesTest 6 例)、配置 JSON 兼容矩阵(FeatureConfigMigrationTest 5 例)、
  功能组完备性(FeatureGroupsTest 3 例)、偏差计数(deviationCount 用例);
  双域 UI 插桩迁移(MonitorEntryHeader/AppMonitorRedesign/FourScreensNav)。
- [x] **T6.2 lint 零新增**:实测 49 warning / 0 error(V35/V36 漂移 +6 已记录基线修订;
  本次引入的唯一新警告 listen_settings_network 死键已清除);
  InterventionGateConventionTest 承担「ui/hook 禁直读干预裸字段」源码门禁
  (PackageBoundaryTest 保持存储边界规则)。
- [~] **T6.3 集成验收(部分)**:宿主 UI 全链路已在 LDPlayer 实机验收通过(见进度日志);
  注入侧冒烟 L-SMOKE-02 与干预矩阵需 LSPosed/Vector 作用域环境执行(命令已列)。
- [x] **T6.4 AGENTS.md 沉淀 V37 契约**:词汇表 +4 词条;强约束 +#23 双域拆分/FeatureGate 红线、
  +#24 Appbar 动作、+#25 视觉 token;lint 基线 43→49;Capability 层禁恢复声明。

## 里程碑

- M1(阻断修复):T0.* + T1.1 → ✅ 完成(全量单测绿)
- M2(模型+数据):T1.2-T1.4 + T2.* → ✅ 完成(Room v14;wire 信封恒 v4 口径修订)
- M3(界面):T3.* + T4.* + T5.* → ✅ 完成(LDPlayer 实机 UI 验收通过)
- M4(收口):T6.1/T6.2/T6.4 ✅;T6.3 注入侧冒烟待框架环境 → Release 候选

## 进度日志

(每完成一项任务,在此追加:`日期 T<id> 完成 —— 备注`并勾选上方复选框)

- 2026-09-04 **T1.1 完成** —— 新增 `core/model/CapabilityDomain.kt`(LISTEN/FEATURE);
  `RecordCategory` 增必填 `domain` 字段(新增类别未标域编译即红);81 类别全量标注
  (FEATURE 37 / LISTEN 44;kind id 跨类型碰撞 OPEN/SIGNATURE 由 (type,id) 对断言处理);
  `CategoryTaxonomy` 新增 `forDomain`/`isFeature` 出口;快照测试 4 例入 `CategoryTaxonomyTest`。
- 2026-09-04 **T0.4 完成** —— `EffectiveSwitch.INTERVENTION_KINDS` 手工清单删除,
  改由 `CategoryTaxonomy.forDomain(FEATURE)` 派生;EMULATOR_SENSOR/DIALOG_KEYWORD 缺口纳入
  (回归测试 `FeatureGateBypassTest.intervention gate covers previously missing kinds`)。
- 2026-09-04 **T0.1 完成** —— 四基类(ProcessControl/DeviceEnv/Network/UiInteraction
  BaseHooker)新增 `featureOn(kind)`(master∧kind);24 个钩子文件 ~80 处裸布尔位直读
  全部收口(ExitIntercept/CrashPrevent/ScreenRecord×3/DialogKeyword/SSL/Proxy/VPN/
  Root×4/Xposed×3/Emulator×2/SystemProperties/Proc×4/Settings/Frida/DetectionMonitor/
  DnsHide/NetworkRequest);`DialogKeywordInterceptHooker` 拦截判定提取纯函数
  `matchedKeywords`(闸门+词表+大小写不敏感);新增架构门禁
  `InterventionGateConventionTest`(hook/hooks 源码禁读干预裸位)+
  `FeatureGateBypassTest`(master 关/kind 关 → setResult 零调用)。
- 2026-09-04 **T0.2 完成** —— 新增 `hook/ConfigWatcher.kt`(配置变更订阅,监听器异常隔离);
  `ProbeContext` 增 `configWatcher`(默认空订阅,测试零改动);`HookInstaller` 接线
  ConfigChannel.onConfig → watcher.notify;`EmulatorBuildHooker` Build 静态字段改写
  改经 `applyBuildFieldMasking()`(featureOn 门控 + AtomicBoolean 幂等 + 订阅惰性补写),
  消灭「注入即篡改」旁路;门控/惰性补写/幂等 4 例回归测试。
- 2026-09-04 **T0.3 完成** —— 录屏解除三钩子统一 `featureOn(ProcessKind.SCREEN_RECORD)`;
  契约测试锁定一位三钩子(源码门禁)。
- 2026-09-04 **T0.5 完成** —— `RUNTIME_EXCEPTION`/`SCREEN_CAPTURE_INTENT` 死枚举删除
  (从未 emit,无存量数据);`RUNTIME_EXIT` 补 `Runtime.exit(int)` 钩子(直调路径此前漏拦);
  UI 标签映射同步收敛。
- 2026-09-04 **M1 验证** —— `:app:testModernDebugUnitTest` 全量 570+ 用例绿;
  `:app:compileModernDebugKotlin` 零告警新增。
- 2026-09-04 **T1.2 完成** —— 新增 `core/model/FeatureConfig.kt`(词表/规则/画像/黑名单追加);
  HookSwitch 增 `feature` 段 + `activeDialogKeywords`/`activeKeywordRules` 出口;
  `configVersion` 1→2(fromJson 双版本迁移:v<1 干预位重置、v<2 扁平词表→feature;
  干预重置与画像迁移解耦为独立版本闸);AppListenOverride 增 8 个功能域偏差字段
  (spoofProfileId/spoofOverrides/extra×4/keywordIds/dialogKeywords)含容错解析;
  EffectiveSwitch.resolve 叠加 feature 偏差。
- 2026-09-04 **T1.3 完成(口径修订)** —— wire 信封 VERSION 恒 4(feature 随 switch JSON
  透传,协议形状零变化);兼容矩阵测试 `FeatureConfigMigrationTest` 5 例
  (v1 扁平迁移 / v2 直解 / v0 干预重置 / 往返保真 / VERSION=4 锁定)。
- 2026-09-04 **T1.4 完成(退役)** —— 删除 `domain/capability` 全包 8 文件 + ModevApp 注册块
  (零消费者死抽象;双域 UI 改由 CategoryTaxonomy.forDomain 单一驱动)。
- 2026-09-04 **T2.1 完成** —— Room v13→v14:`keyword_library` 表(text 唯一索引/
  matchMode/enabled/createdAt)+ `KeywordEntity`/`KeywordLibraryDao`/`KeywordEntry`(领域视图);
  `KeywordLibraryRepository`(CRUD + Unconfined 快照 + resolveRules 交集语义);
  Robolectric 测试 5 例(去重/trim 去重/启停/子集/删除失效)。
- 2026-09-04 **T2.2 完成** —— `KeywordRule`(CONTAINS/REGEX/EQUALS 纯函数匹配,非法正则降级);
  SettingsRepository.withKeywordRules 注入规则 + effectiveSwitchFlow 三源 combine +
  attachKeywordLibrary;ConfigServer.pushToAllConnected(库变更全局重推);
  ModevApp 接线(entries.drop(1) → repush);DialogKeywordInterceptHooker 规则化消费;
  测试:KeywordRuleTest 5 例 + FeatureGateBypassTest 正则规则例。
- 2026-09-04 **T2.3 完成** —— `SpoofProfiles`(auto/pixel7pro/xiaomi13 内置档位 +
  build./ro./telephony./wifi./cpu. 前缀覆写分派 + 漂移回落)+ `HookSwitch.activeSpoofProfile()`;
  EmulatorBuild/DeviceSysProp/SystemProperties/DeviceTelephony/DeviceCpu/ProxyHide 六钩子
  机型字面量全部收敛至画像(SpoofProfilesTest 6 例)。
- 2026-09-04 **T2.4 完成** —— RootPackageManager/XposedClass/ProcNet/XposedMaps/
  ProcMountInfo/ProcCgroup 六钩子黑名单改「内置基线 ∪ feature.extra* 追加」(只增不删)。
- 2026-09-04 **M2 验证** —— 全量单测绿(585+ 用例;含迁移链 v1→v14 回归)。
- 2026-09-04 **T3.1-T3.7 完成** —— AppMonitorScreen 重写(双区 + Appbar 动作 + 双段统计卡,
  重复 import 清零);`PerAppFeatureSettingsScreen`(4 master + 六功能卡 + 关键词 chips +
  画像 sheet)+ `KeywordLibraryScreen` / `SpoofProfileScreen` / `FeatureBlacklistScreen`
  三全局页 + 四路由接线;监听页移除功能域行、新增环境观测分区(taxonomy 驱动);
  DataSettingsScreen 枚举驱动 10 类型;`SettingsRepository.setGlobalFeature`
  (feature_global 键 + switch_global 种子重播 + 全会话重推);
  新增双语键 ~90 条(含 parity 白名单豁免品牌名)。
- 2026-09-04 **T4.1-T4.6 完成** —— `Dimens`/`Tokens` 双 token 对象;Scaffold containerColor
  3 处补齐;Diagnostics 端口 sheet 退场动画 + titleLarge;ScopeFilterMenu 激活指示修复
  (pinScoped 漏计 + showModules 默认值误判恒亮 bug);`ui/logs`→`ui/detail` 包更名(23 文件);
  `KindLabelRes`/`ToggleListItem`/`deviationCount` 三处单一事实源收敛;RecordRow 14dp→16dp。
- 2026-09-04 **T5.1-T5.2 完成** —— zh 干预 master 文案重命名(UI 交互/网络干预/设备环境伪装/
  进程守护)+ network_records_title 对齐(注:首轮 ps1 无 BOM 致 zh 替换静默失败,
  设备复验发现后以 edit 工具修复——工具链坑沉淀:含中文的 .ps1 必须带 BOM 或改用 edit)。
- 2026-09-04 **T6.1/T6.2/T6.4 完成** —— 新增 JVM/Robolectric 测试 39 例
  (FeatureGateBypass/InterventionGateConvention/CategoryTaxonomy 域快照/KeywordRule/
  FeatureConfigMigration/FeatureDeviationRoundTrip/SpoofProfiles/KeywordLibraryRepository/
  FeatureGroups 完备性);lint 实测 49/0(本次引入的 1 条新警告已清,基线修订记录于 AGENTS);
  AGENTS.md 词汇表 +4 词条、强约束 +#23/#24/#25、lint 基线 43→49、V37 头注释更新。
- 2026-09-04 **T6.3 部分完成(设备端到端 UI 验收)** —— LDPlayer x86_64 实机验证:
  根页/设置「拦截与伪装」分区/关键词库空态→添加 sheet→RootDetect 入库并跨重装持久/
  功能设置页六组全渲染 + master 新文案 + 关键词 chip 跟随全局 + 画像行「跟随全局」/
  监控页双段统计卡(监听记录/功能干预)——全部通过。
  **待办(需框架环境)**:注入侧冒烟 `scripts/test-integration/23-run-smoke.ps1`
  与干预矩阵(开→干预发生、关→零副作用)须在 LSPosed/Vector 作用域环境执行;
  命令:`.\gradlew.bat :app:connectedModernDebugAndroidTest`
  (PerAppFeatureSettingsTest 5 例已就位)。
