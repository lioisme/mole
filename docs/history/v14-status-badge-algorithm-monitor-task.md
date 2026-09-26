# Modev 任务书 V14:状态角标化 · 捕获设置→算法监听 · 详情页原生选择+专业折叠 · 应用列表设置收敛

**文档版本**: v1.0 | **创建日期**: 2026-08-18 | **状态**: 执行中
**适用范围**: `ui/components/RecordRow.kt`、`ui/logs/RecordDetailScreen.kt`、`ui/apps/{AppsScreen,AppRecordsScreen,AppsViewModel,AppSort}.kt`、`ui/settings/{SettingsScreen,CaptureSettingsScreen→AlgorithmMonitorScreen,CaptureSettingsViewModel→AlgorithmMonitorViewModel,CapabilityUiRegistry}.kt`、`ui/theme/StatusColorTokens.kt`(新增)、`ui/components/StatusDot.kt`(新增)、`data/repo/AppListSettings.kt`、`domain/capability/{CapabilityKind,CryptoCaptureCapability→AlgorithmListenCapability}.kt`、`ui/navigation/ModevRoutes.kt`、`ui/ModevNav.kt`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、i18n 资源(en/zh)、测试三件套
**质量目标**: 代码质量、架构设计达到苹果/谷歌大厂验收标准(单一事实源、虚拟化与性能底线、无障碍语义、i18n 零硬编码、门禁全绿)

---

## 0. 需求条款(用户口径)

| # | 需求 |
|---|------|
| R1 | 算法列表 item 界面具备专业、标准的风格 |
| R2 | 算法详情文本支持**长按自由复制**(原生选择手柄框选),而非「长按=整段复制」 |
| R3 | 算法详情文本过长时**自动收起**,使用**专业组件库**实现 |
| R4 | 「离线/在线/已在作用域/未在作用域」改用**角标+色彩**传递,不用高占用屏幕的文本 |
| R5 | 「捕获设置」正式更名「**算法监听**」,为后续「行为监听/数据库监听」打基础 |
| R6 | 应用列表按名称顺序排序;已作用域置顶时组内**仍按名称顺序** |
| R7 | 「已作用域应用置顶」移入**通用设置**,默认启用 |
| R8 | 应用列表「已作用域/系统应用」勾选状态**持久化** |
| R9 | 质量与架构达到苹果/谷歌大厂验收标准 |

## 1. 全量审计(现状 + 根因)

### 1.1 R1 算法列表行(RecordRow.kt) — 缺口

**现状**:V13 已卡片化(`RecordRow.kt:67-129`,shapes.large 16dp + surfaceContainerLow),5 行全左对齐:算法名(等宽 bodyLarge)→ 操作(labelMedium 类别色)→ 方向胶囊 → 数据摘要 → 时间。已合规:卡片层次、类别色承载、空白治理(无徽章/无 minHeight)、长按复制摘要。

**缺口**(距专业标准):
| # | 位置 | 违规内容 |
|---|------|----------|
| R1-1 | `RecordRow.kt:87-125` | 信息层级扁平:5 行左对齐,主从关系弱(算法名与操作同为常规字重) |
| R1-2 | `RecordRow.kt:120-125` | 时间独占一行居主体底部——标准列表行(Apple/Google)时间应尾随右侧,顶部对齐 |
| R1-3 | `RecordRow.kt:96-101` | 类别信息仅以颜色隐现于操作行,无「类别·操作」显式组合语义 |

**方案**(精修卡片式,已确认):按 M3 ListItem 信息架构重排——行1 headline(等宽 bodyLarge **SemiBold** 算法名,单行省略)+ 尾随时间(labelSmall onSurfaceVariant,顶部对齐);行2 supporting(类别·操作 = `kindLabel · operation`,labelMedium 类别色 + 方向胶囊内联);行3 数据摘要(等宽 labelMedium 单行省略)。卡片几何/间距/长按摘要回调不动。

### 1.2 R2 详情页复制语义 — 违规清单

| # | 位置 | 违规内容 |
|---|------|----------|
| R2-1 | `RecordDetailScreen.kt:502-523` | `byteSectionItems` 逐块 `CopyableChunk`(pointerInput `onLongPress` → `copyFull` 自动整段复制)——与「长按自由框选」冲突 |
| R2-2 | `RecordDetailScreen.kt:532-554` | `CopyableChunk` 本体(自定义长按消费原生手势) |

**已合规(保留不动)**:`MetadataBlock`(`:385`)/`SectionText`(`:566`)/堆栈逐帧(`:366`)原生 `SelectionContainer`;footer 复制按钮 `ByteSectionHeader`(`:473-478`,整段 hex 出口 + TalkBack 兜底)。

**改动**:每块 Text 包 `SelectionContainer`(原生选择手柄,长按自由框选任意范围);删除 `CopyableChunk` 与长按整段复制路径;`copyFull` 仅保留服务 footer 按钮。约束(源自 V13 DR):SelectionContainer 消费子树长按 = 本次目标行为,互斥问题不存在。

### 1.3 R3 详情页折叠 — 缺口与方案

**现状**:V13 恒展开(`RecordDetailScreen.kt` 全文件无折叠组件);V11 自研 `LineEstimate` 已删除且不可回退(用户要求组件库)。

**方案**(库选型已确认 = Webtoon ReadMoreTextView):
- 依赖:`com.webtoon.readmore:readmore-material3`(WEBTOON 生产同源,持续维护;构建期核验与项目 M3 1.4.0 / Compose 1.8 的版本矩阵,冲突时退 `readmore-material`);
- **自动收起阈值**:区块内容字符数 > 4096(`ChunkedText.DEFAULT_CHUNK_CHARS`)→ 默认收起;
- **收起态**:有界预览串(前 4096 字符,text 优先、无 text 用 hex)+ `ReadMoreText(collapsedMaxLines=4, seeMoreLabel=「… 展开」)`——预览有界,测量成本有界(2MB 全量排版 = V11 ANR 根因,绝不回退);
- **展开态**:既有惰性分块 items 全量虚拟化不变;`ByteSectionHeader` 增「收起」TextButton(复用 `section_collapse` 键),点击回收起态;
- **状态**:`rememberSaveable` 按区块 key 独立(V11 已修复跨记录串键,沿用);
- **短内容**(≤ 阈值):零折叠组件,与 V13 行为完全一致;
- **范围**:字节区块(Key/IV/Salt/AAD/PublicKey/Input/Output)+ Params 区;堆栈保持逐帧虚拟化(结构化行列表,非长文本,DR);
- 收起态预览不包 SelectionContainer(LinkAnnotation 点击与选择手势共存风险,DR);
- i18n 新增 `record_see_more`(en/zh),收起按钮复用现有键。

### 1.4 R4 状态文本占屏 — 违规清单

| # | 位置 | 违规内容 |
|---|------|----------|
| R4-1 | `AppsScreen.kt:447-462` | 行内「● Online / ○ Not running」labelSmall 着色文本占行宽 |
| R4-2 | `AppRecordsScreen.kt:433-450` | 头部两个 `StatusChip` 文本胶囊(各 ≤110dp,「框架服务未连接」截断不可读) |
| R4-3 | `AppRecordsScreen.kt:557-586` | `StatusChip` 组件本体 + widthIn 限制 |

**方案**:
- 新增 `ui/theme/StatusColorTokens.kt`(仿 `KindColorTokens` 范式):浅/深语义色对,对比度 ≥ AA——online 绿 `#34A853/#81C995`、offline 灰(outline 系)、scoped=primary、未作用域=outline、connecting 琥珀 `#F9A825/#FFD666`、框架错误=error token;
- 新增 `ui/components/StatusDot.kt`:8dp 圆点(M3 Badge 形态)+ `semantics { contentDescription = 本地化状态文案 }` + `TooltipBox` 长按提示(复用 `TipIconButton:42-51` 的 M3 1.4 锚点 API 范式);
- `AppsScreen` 行:包名行尾随状态点(仅 scoped 行显示,online=绿/offline=灰;未 scoped 不占位,V13 空白治理);
- `AppRecordsScreen` 头部:在线点 + 作用域点(Ready→primary/outline;Connecting→琥珀;NO_SERVICE→error),完整语义文案进 tooltip/contentDescription;删除 `StatusChip`;
- 字符串保留(转为 a11y/tooltip 语义文本,不渲染):`online/offline/apps_not_running/detail_listening/detail_not_listening/detail_reading_scope/detail_framework_no_service`——同时移除值内 `●/○` 字形(en:11-12,23 / zh 同),en-zh 键对 diff 仍为 0。

### 1.5 R5 更名「捕获设置」→「算法监听」 — 影响面

| # | 位置 | 现值 → 目标值 |
|---|------|----------------|
| R5-1 | `strings.xml:169` / `values-zh:170` | `capture_settings_title` → `algorithm_monitor_title`(en "Algorithm Monitoring" / zh「算法监听」) |
| R5-2 | `strings.xml:44` / `values-zh:45` | `section_algorithms`(算法捕获/Algorithm Capture)→ `section_monitor_kinds`(监听类别/Monitor Categories) |
| R5-3 | `strings.xml:4` / `values-zh:3` | `app_description`:"algorithm capture/算法捕获" → "algorithm monitoring/算法监听" |
| R5-4 | `ui/settings/CaptureSettingsScreen.kt` | 文件+类 → `AlgorithmMonitorScreen.kt` |
| R5-5 | `ui/settings/CaptureSettingsViewModel.kt` | 类+Factory → `AlgorithmMonitorViewModel(+Factory)` |
| R5-6 | `ui/navigation/ModevRoutes.kt:18` | `CAPTURE_SETTINGS="settings/capture"` → `ALGORITHM_MONITOR="settings/algorithm"`(路由不持久化,安全) |
| R5-7 | `domain/capability/CapabilityKind.kt:10` | `CRYPTO_CAPTURE` → `ALGORITHM_LISTEN`(KDoc 注明:算法监听为首个实现,行为/数据库/交互监听按枚举扩展——R5 架构打底) |
| R5-8 | `domain/capability/CryptoCaptureCapability.kt` | → `AlgorithmListenCapability` |
| R5-9 | `ui/ModevNav.kt:64-70` | 注册表 titleRes 随 R5-1;icon `Tune` → `Sensors`(监听语义) |
| R5-10 | `androidTest/FourScreensNavTest.kt:139,148,153` | 三处 `capture_settings_title` 引用更新 |

**保留不动**:`capture_max_bytes`/`capture_ignore_stack` 等记录选项键(词汇属记录,非能力名)。

### 1.6 R6 排序 — 合规确认 + 契约锁定

**审计结论:已合规**。`AppsViewModel.kt:111-142`:先 `sortedWith(nameComparator)`(Collator PRIMARY,名称同 key 回退包名,全序稳定)再 `AppSort.pinScoped` 稳定分区(O(n),组内保持名称序)——置顶组与非置顶组均按名称升序。`AppSortTest` 已锁稳定分区契约。

**缺口**:无「预排序输入下置顶组内仍名称升序」的显式契约用例。**补测**:`AppSortTest` 增 2 例(置顶组内名称升序断言;全流程模拟输入→排序→置顶的顺序不变性)。R7 默认开启置顶后此契约直接上线,必须有测试锚点。

### 1.7 R7 置顶进通用设置 — 现状与改动

| # | 位置 | 现状 → 目标 |
|---|------|--------------|
| R7-1 | `AppListSettings.kt:26` | `KEY_PIN_SCOPED` 默认 `false` → `true`(首启行为变化,文档化) |
| R7-2 | `AppsScreen.kt:350-364` | 筛选菜单置顶项**移除**(已确认单入口);菜单收敛为「仅已作用域/系统应用/设置」 |
| R7-3 | `SettingsScreen.kt` | 新增「应用列表」分区(`settings_apps_section`)+ `SettingsToggleItem(PushPin, settings_pin_scoped, 副题 settings_pin_scoped_hint)`;状态收集 `AppListSettings.pinScoped`,写入 `setPinScoped(it, context)`(与 `ThemeSettingsManager` 同范式,直连对象) |
| R7-4 | i18n | 新增 `settings_apps_section`(en "App List"/zh「应用列表」)、`settings_pin_scoped_hint`(en "Scoped apps stay name-sorted on top"/zh「已作用域应用按名称顺序置顶」) |

### 1.8 R8 筛选勾选持久化 — 缺口

| # | 位置 | 现状 → 目标 |
|---|------|--------------|
| R8-1 | `AppsViewModel.kt:98-99` | `onlyScoped/showSystem` 私有内存 `MutableStateFlow` → 迁入 `AppListSettings`(StateFlow + SharedPreferences 双写,同 `pinScoped` 范式) |
| R8-2 | `AppsViewModel.kt:176-182` | `setOnlyScoped/setShowSystem` 改为 UI 层携 context 调 `AppListSettings.setX(it, context)`(ViewModel 不持 Context,沿用 `AppsScreen.kt:165` 既有模式) |
| R8-3 | `AppsViewModel.kt:111-142` 流水线 | `SortPrefs/UiPrefs` combine 源替换为 `AppListSettings` 流(与 `pinScoped` :117 并列),行为零漂移 |
| R8-4 | `AppListSettings.kt` | 新增 `onlyScoped`(默认 false)/`showSystem`(默认 true)键 + `init()` 装载 |

### 1.9 R9 质量基线确认

已具备:双变体单测(`testModern/LegacyDebugUnitTest`)、lint 双变体 0 error、androidTest 编译门禁、en-zh 键对 diff=0、对比度门禁测试(`KindColorTokensTest` 范式)、性能插桩契约(`ByteSectionTest`)、实机截图验证(V13 起)。本次按 §5 全量复跑。

### 1.10 同族顺带修复(P0)

| # | 位置 | 违规 | 处置 |
|---|------|------|------|
| P0-1 | `strings.xml:11-12,23` | `●/○` 字形残留(角标化后无承载方) | 随 R4 清除,值仅保留语义文本 |
| P0-2 | zh 字符串文件 | 审计读取出现乱码(疑控制台编码,需核验) | 执行期以 UTF-8 字节级核验文件,若真损坏即修复 |
| P0-3 | `RecordDetailScreen.kt` | `record_copy_done` 仅剩 footer 按钮引用 | 保留,勿删 |

## 2. 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | R2 复制语义 | 分块 Text 包 `SelectionContainer`(原生手柄自由框选);删除长按整段复制;footer「复制完整 hex」按钮保留为整段 + 无障碍出口 |
| D2 | R3 组件库 | `com.webtoon.readmore:readmore-material3`;构建期核验版本矩阵(项目 M3 1.4.0 stable 高于其 1.4.0-alpha15 依赖,应正常解析),冲突退 `readmore-material` |
| D3 | R3 折叠架构 | 阈值=4096 字符自动收起;收起态 = 有界预览 + `ReadMoreText`(专业「… 展开」内联控件);展开态 = 既有惰性分块虚拟化 + 「收起」按钮;**测量成本恒有界**,V11/V13 性能契约不回退 |
| D4 | R3 折叠范围 | 字节区块 + Params;堆栈不折叠(结构化虚拟化行列表) |
| D5 | R4 角标 | `StatusDot`(8dp 圆点)+ 语义色 token + contentDescription + 长按 tooltip;不渲染文本,完整语义进 a11y |
| D6 | R5 更名链 | 全链重命名(领域/UI/路由/i18n/测试),`algorithm_monitor_title` 命名模式为「行为/数据库监听」复用(`{kind}_monitor_title`) |
| D7 | R7 入口 | 置顶开关单入口收敛通用设置;筛选菜单移除置顶项;默认 `true` |
| D8 | R8 持久化 | 复用 `AppListSettings` 单一事实源(StateFlow + SharedPreferences),ViewModel 零 Context 依赖 |
| D9 | R1 行风格 | 精修卡片式:M3 ListItem 三级信息架构(headline+supporting+数据段)+ 尾部时间;不动卡片几何/长按摘要 |

## 3. 改动清单(执行顺序)

- [x] T1 本任务书落盘(`docs/v14-status-badge-algorithm-monitor-task.md`)
- [x] T2 依赖:`libs.versions.toml` + `app/build.gradle.kts` 引入 readmore-material3,构建验证
- [x] T3 i18n(en/zh 同步,键对 diff=0):新增 `algorithm_monitor_title`/`section_monitor_kinds`/`record_see_more`/`settings_apps_section`/`settings_pin_scoped_hint`;更新 `app_description`;`online/offline/apps_not_running` 去字形
- [x] T4 `ui/theme/StatusColorTokens.kt` 新增(浅/深 6 语义色,含对比度自校验常量)
- [x] T5 `ui/components/StatusDot.kt` 新增(semantics + TooltipBox)
- [x] T6 `AppsScreen.kt`:状态点替换行内文本;筛选菜单去置顶项;`setOnlyScoped/setShowSystem` 改走 `AppListSettings`
- [x] T7 `AppRecordsScreen.kt`:头部双状态点替换 `StatusChip`;删除 `StatusChip`/widthIn
- [x] T8 `RecordDetailScreen.kt`(核心):R2 原生选择(去 CopyableChunk)+ R3 自动折叠(ReadMoreText 收起态 + 展开态「收起」按钮 + rememberSaveable 分区块状态)
- [x] T9 `RecordRow.kt`:R1 精修(层级/尾部时间/类别·操作组合)
- [x] T10 `AppListSettings.kt`:pinScoped 默认 true + `onlyScoped/showSystem` 新增
- [x] T11 `SettingsScreen.kt`:应用列表分区 + 置顶开关
- [x] T12 更名链:领域(`CapabilityKind.ALGORITHM_LISTEN` + `AlgorithmListenCapability`)/UI(`AlgorithmMonitorScreen/ViewModel`)/路由/`ModevNav`(Sensors 图标)
- [x] T13 测试:重写 `ByteSectionTest`;更新 `RecordRowLayoutTest`/`FourScreensNavTest`;新增 `AppSortTest` 2 例、`AppListSettingsTest`、`StatusColorTokensTest`
- [x] T14 质量门禁:双变体单测 + lint + androidTest 编译 + en-zh 键对 diff=0 + 死字符串 grep
- [x] T15 实机验证(模拟器,双变体 APK):R2 长按出选择手柄;R3 大记录自动收起/展开流畅;R4 角标色彩;R5 新名称;R7/R8 持久化(杀进程重启断言);截图入 `docs/screenshots_manual/`
- [x] T16 执行记录勾选 + 验收收尾

## 4. 测试计划

| 层 | 用例 | 断言要点 |
|----|------|----------|
| 插桩(重写) | `ByteSectionTest` | ①短内容无折叠控件全量可见;②>4096 字符默认收起(展开链接在树、分块不在);③点展开链接 → 分块虚拟化组合;④「收起」回折叠态;⑤长按分块 → **不触发整段复制**(回调零变化),SelectionContainer 存在(原生选择语义);⑥footer 复制按钮仍整段 hex;⑦300K hex/text 惰性滚动回归(V11 性能防线) |
| 插桩(更新) | `RecordRowLayoutTest` | 新布局:算法名 SemiBold + 尾部时间 + 类别·操作组合行 + 数据段单行省略;长按复制摘要回调保持 |
| 插桩(更新) | `FourScreensNavTest` | 筛选菜单 = 2 过滤项 + 设置(无置顶项);设置页应用列表分区含置顶开关且可切换;`algorithm_monitor_title` 可达(替换三处 capture 引用);全程无「捕获设置」文案节点 |
| 单测(新增) | `AppListSettingsTest`(Robolectric) | 默认值:pinScoped=true/onlyScoped=false/showSystem=true;set→持久化→init 重载回读一致 |
| 单测(新增) | `StatusColorTokensTest` | 6 语义色浅/深前景对比度 ≥ AA(仿 `KindColorTokensTest`) |
| 单测(新增) | `AppSortTest` +2 | 置顶组内名称升序;排序→置顶全流程顺序不变性 |
| 回归 | `SettingsComponentsTest`/`RecordDetailViewModelTest` 等 | 零改动跑绿 |

## 5. 验收标准(Definition of Done)

- [x] R1:列表行为 M3 ListItem 三级层级,时间尾随;R9 视觉走查通过(实机组合行「摘要 · 摘要/摘要 · 更新」实测)
- [x] R2:详情页任意分块长按弹出系统选择手柄可自由框选;长按不再整段自动复制;footer 整段复制可用(实机手柄窗口 A/B 复验 + ByteSectionTest ⑤⑥)
- [x] R3:>4096 字符区块自动收起(「… 展开」内联,组件库提供);展开流畅、收起可回;短内容无折叠控件;1MB×3 字段 + 1MB 文本滚动不卡顿(插桩契约全绿 + 实机 3000/4500 字节区块往返)
- [x] R4:全界面无「在线/离线/作用域」状态文本渲染,一律角标+色彩;a11y contentDescription/tooltip 承载完整语义(实机 content-desc「未运行」+ 像素采样 #9AA0A6)
- [x] R5:全链更名完成,grep 无 `CaptureSettings/CRYPTO_CAPTURE/capture_settings_title` 残留(记录选项键除外);`CapabilityKind.ALGORITHM_LISTEN` 就位,行为/数据库监听按枚举扩展零主链路改动(实机「算法监听」入口)
- [x] R6:`AppSortTest` 置顶组内名称序契约全绿(7/7)
- [x] R7:通用设置「应用列表」区置顶开关默认开;筛选菜单无置顶项;开关即时重排(实机默认 checked=true + 筛选菜单三断言)
- [x] R8:仅已作用域/系统应用勾选杀进程重启后保持(实机 force-stop 重启断言 + pref 落盘)
- [x] R9 门禁:`testModernDebugUnitTest`/`testLegacyDebugUnitTest` 全绿;`lintModernDebug`/`lintLegacyDebug` 0 error;androidTest 编译通过;en-zh 键对 diff=0;死字符串/死代码 grep 零残留
- [x] 实机验证 + 截图(V14 系列入 `docs/screenshots_manual/`);执行记录逐项勾选

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| readmore-material3 与 M3 1.4.0 版本矩阵冲突 | 构建期验证;依赖解析应为 1.4.0 stable 胜出;失败退 `readmore-material`(无 M3 依赖) |
| 折叠测量成本回退(V11 ANR 根因) | 收起态仅喂 4096 字符有界预览;展开态恒走惰性分块;性能插桩契约 T13-⑦ 回归防线 |
| SelectionContainer 与 LinkAnnotation/滚动手势 | 收起态预览不包 SelectionContainer(展开后才有选择);detectTapGestures 已移除,无手势冲突 |
| 更名链波及测试/文档 | grep 全量收口(R5 验收清单);`capture_*` 记录选项键明确保留范围 |
| zh 字符串乱码疑点 | 执行期 UTF-8 字节核验;真损坏则修复并纳入门禁 |
| 置顶默认开改变首启体验 | 任务书文档化;设置页开关可见可逆 |
| 状态点色彩对比度不足 | StatusColorTokens 门禁测试 + 实机深浅色双走查 |

## 7. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 任务书落盘 | ✅ | |
| T2 | 依赖引入 | ✅ | `com.webtoonscorp.android:readmore-material3:1.7.5`(核验 Central 元数据:1.9.0 需 Compose 1.9/M3 1.5,故取 1.7.5=Compose 1.8.2+M3 1.4.0-alpha15);`modernDebugCompileClasspath` 解析:material3 恒 1.4.0 stable,readmore-foundation 1.7.5 无冲突 |
| T3 | i18n 键 | ✅ | 新增 `algorithm_monitor_title`/`section_monitor_kinds`/`record_see_more`/`settings_apps_section`/`settings_pin_scoped_hint`;更新 `app_description`(算法捕获→算法监听);`online/offline/apps_not_running` 去字形;T12 时删旧键 `capture_settings_title`/`section_algorithms`;en/zh 键对恒等 |
| T4 | StatusColorTokens | ✅ | 新建 `ui/theme/StatusColorTokens.kt`:`StatusKind` 6 态;ONLINE/OFFLINE/CONNECTING/UNSCOPED 自定义 token(浅:137333/5F6368/B45309/6E6E6E,深:81C995/9AA0A6/FFD666/C4C7C5);SCOPED/ERROR 派生 M3 primary/error(scheme outline 仅 4.27:1 不达严格档,故 UNSCOPED 改自定义灰);`statusColorForMode` internal + `statusColor` @Composable |
| T5 | StatusDot | ✅ | 新建 `ui/components/StatusDot.kt`:8dp 圆点 + semantics contentDescription + TooltipBox(M3 1.4 `TooltipAnchorPosition.Above`) |
| T6 | AppsScreen | ✅ | AppRowItem 状态文本→StatusDot(ONLINE/OFFLINE,`online`/`apps_not_running` 标签);ScopeFilterMenu 去 pinScoped 参数与置顶项;`setOnlyScoped/setShowSystem` 携 context 直调 AppListSettings;去 PushPin 导入 |
| T7 | AppRecordsScreen | ✅ | 头部双 StatusDot 替换两枚 StatusChip(连接 ONLINE/OFFLINE;作用域 SCOPED/UNSCOPED/CONNECTING/ERROR 按 scopeState 分支);StatusChip 组件删除;去 widthIn/Surface 导入 |
| T8 | RecordDetailScreen | ✅ | R2:删除 CopyableChunk,展开态分块全包 `SelectionContainer`(原生选择),footer 复制按钮保留;R3:byteSectionItems 新签名(expanded/onExpandedChange),>4096 字符默认收起=ReadMoreText 预览(前 4096 字符 text 优先,`record_see_more`,4 行上限),展开=text/hex 惰性分块 + 「收起」TextButton;`rememberSaveable` 分区块 map 状态;短内容零折叠控件;300K 惰性滚动插桩回归 |
| T9 | RecordRow | ✅ | M3 ListItem 三级层级:headline 算法 bodyLarge+SemiBold 单行省略;supporting「类别 · 操作」组合行(kindLabelRes + " · " + operationLabel,类别色);尾部时间 labelSmall 右对齐(Row Top 对齐) |
| T10 | AppListSettings | ✅ | pinScoped 默认 `true`(原 false);新增 `onlyScoped`(默认 false)/`showSystem`(默认 true)+ setOnlyScoped/setShowSystem + persist 助手;AppsViewModel 改源 AppListSettings(删私有 flow 与 setX) |
| T11 | SettingsScreen | ✅ | 语言与能力区之间插入「应用列表」分区 SettingsToggleItem(PushPin、`settings_pin_scoped`/`settings_pin_scoped_hint`),设置页唯一置顶入口 |
| T12 | 更名链 | ✅ | `CapabilityKind.ALGORITHM_LISTEN`;`AlgorithmListenCapability.kt` 新建(旧文件删除);ModevApp 注册;`ModevRoutes.ALGORITHM_MONITOR="settings/algorithm"`;ModevNav Tune→Sensors 图标 + 路由/工厂/屏幕引用;CaptureSettingsScreen/ViewModel.kt 重命名 AlgorithmMonitorScreen/ViewModel.kt 并重写(标题 `algorithm_monitor_title`、分区 `section_monitor_kinds`);strings 删旧键;`capture_*` 记录选项键保留;两轮编译 BUILD SUCCESSFUL |
| T13 | 测试 | ✅ | 重写 `ByteSectionTest`(8 例覆盖契约①-⑦:短内容零控件/默认收起预览在树/展开惰性分块/收起回退/长按分块不触发整段复制/复制按钮全量/300K hex+text 惰性滚动);`RecordRowLayoutTest` 组合行断言 + 去徽章字母断言;`FourScreensNavTest` 测试2更名 + 筛选菜单三断言(置顶项不存在)+ `algorithm_monitor_title` + 设置页分区/开关切换还原;新增 `AppSortTest` 2 例(置顶稳定分区/全流程不变式)、`AppListSettingsTest`(默认值/持久化回读)、`StatusColorTokensTest`(4 自定义 token 双模式 ≥4.5:1 + SCOPED/ERROR 方案色同源校验;首轮 ONLINE 浅 2.91:1 未过,改 #137333 后全绿) |
| T14 | 质量门禁 | ✅ | `testModernDebugUnitTest`+`testLegacyDebugUnitTest` 全绿(227 例;含新增 AppSortTest 7/7、AppListSettingsTest 3/3、StatusColorTokensTest 2/2,首轮 ONLINE 浅色 2.91:1 修正后全绿);`compileModernDebugAndroidTestKotlin`+`compileLegacyDebugAndroidTestKotlin` 通过;`lintModernDebug`+`lintLegacyDebug` 0 error/34 warning(全部为既有基线项:ModifierParameter=RecordRow 参数序、UnusedResources=detail_framework_error 孤儿键,均非本次引入);en/zh 键对 diff=0(198 键 PARITY_OK);死字符串 grep:CaptureSettings/CRYPTO_CAPTURE/capture_settings_title/section_algorithms/CopyableChunk/StatusChip 主源码零残留 |
| T15 | 实机验证 | ✅ | 模拟器 emulator-5554(x86_64,API 34,中文环境),双变体 APK 均安装冒烟通过。R3:ModevTester AES/CBC 记录(输入 3000 字节/输出 4500 字节,均 >4096 字符阈值)默认收起显示「… 展开更多」;点按展开=惰性分块替换预览,分块间滚动流畅;分块尾部「收起」点击还原预览;输入/输出两区块独立状态(输入展开时输出仍收起);短内容区块(SHA1 摘要)无折叠控件。R2:展开态分块长按 → 2 枚 100×100 APPLICATION_SUB_PANEL 选择手柄窗口出现,点击空白清除(A/B 复验);无整段自动复制。R4:应用行状态点仅 content-desc「未运行」,无行内文本;截图像素采样 = #9AA0A6 = OFFLINE 深色 token 精确命中。R5:设置页「功能」区「算法监听」+「已启用」。R7:设置页「应用列表」分区「已作用域应用置顶」默认开(checked=true),开关落盘 `pin_scoped_on_top`,筛选菜单仅「仅已作用域/系统应用/设置」三项(无置顶项)。R8:`only_scoped=true` 杀进程重启后保持,列表仅剩作用域应用;开关还原默认。截图 3 张入 `docs/screenshots_manual/`(v14_1_apps/v14_2_settings/v14_3_record_detail)。 |
| T16 | 收尾 | ✅ | §5 验收标准 R1-R9 + 实机验证 10 项全勾;§3 计划 T1-T16 全勾;执行记录 T1-T16 逐项落账;legacy 变体冒烟已随 T15 完成(安装/启动/渲染通过),设备已还原 modern 变体;任务书全量收口 |
