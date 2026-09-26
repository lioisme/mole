# Modev V26 任务文档:详情页 Markdown 化渲染 · 外部监听开关 · 诊断信息底部弹层

| 项 | 值 |
|---|---|
| 版本基线 | V25 → **V26**(本文档实施后沉淀) |
| 变体 | modern / legacy 双变体均需编译通过;wire 协议 VERSION 恒 4,注入侧零感知 |
| 需求来源 | 用户需求 1–4(2026-08-26)+ 决策确认:CodeGroup 替代三态分段;代码块限高+全屏查看;外部访问开关默认停用 |

## 进度跟踪

| 任务 | 状态 | 备注 |
|---|---|---|
| T1 依赖接入 markdown-renderer-m3 + 冒烟 | ✅ 已完成 | 锁定 0.38.1(stdlib 2.2.21 与 Kotlin 2.2.20 同 minor);新增 testImplementation compose-ui-test;冒烟发现两个关键坑:①库默认 Dispatchers.Default 异步解析,Robolectric 下与 waitForIdle 竞态(围栏用例侥幸通过)②m3 门面未透传 immediate 参数——解法:核心 `rememberMarkdownState(immediate=true)` + m3 state 重载,同步渲染有界且测试确定。MarkdownSmokeTest 2 用例绿 |
| T2 RecordMarkdownFactory 纯函数层 + JVM 测试 | ✅ 已完成 | 新增 markdown/RecordMarkdownFactory.kt(围栏反引号逃逸/GFM 表/128Ki 显示上限/截断标记注入)与 CodeGroupExtractor.kt(AST 围栏索引+连续运行聚合,EOL 不中断链)。JVM 用例见 T12 补充(工厂用例随 MarkdownFactoryTest 落地) |
| T3 CodeBlockCard 组件(限高/复制/全屏入口) | ✅ 已完成 | CodeBlockCard:surfaceContainerLowest 卡 + header(lang 标签/复制/全屏 TipIconButton)+ heightIn(max=320dp) 双向滚动 SelectionContainer 正文;复制经 DetailFenceHandle 全量句柄走屏幕层 copyFull(4M 护栏),顺手修复 record_copy_too_large 占位符缺参缺陷 |
| T4 ModevCodeGroup 组件(TabRow 三态承接) | ✅ 已完成 | 经库扩展点 markdownComponents(codeFence=) 注入 ModevMarkdownCodeFence:组内首围栏渲染 TabRow+活动卡(section_repr_* 文案,rememberSaveable 按 runFirstOrdinal 记忆选中),后续成员消费不重复发射;孤立围栏退化单卡;RecordMarkdownContent 以 rememberMarkdownState(immediate=true)+m3 state 重载同步渲染(m3 门面未透传 immediate 的绕行方案) |
| T5 全屏查看器 FullscreenTextDialog | ✅ 已完成 | Dialog(usePlatformDefaultWidth=false) 全屏 Surface + TopAppBar(关闭/复制)+ ChunkedText O(1) 分块虚拟化 LazyColumn(fs-N 键),navigationBars padding 兜底;复制复用统一出口 |
| T6 详情页装配改造(退役 SegmentedButton/TierB/ReadMoreText) | ✅ 已完成 | RecordDetailSections.kt 重写为 markdown 引擎(byteSectionDoc/textSectionDoc/metaTableDoc/markdownSectionItems,区块恒 1 item,锚点簿记契约保持);Screen 重写:文档组合期预计算(@Composable 构建器禁入 DSL 作用域)、MetaGrid 退役并入 GFM 元数据表、StackFrame/展开态/表示态映射删除、Hero+锚点保留(D1);删 MetaGrid.kt/SectionRenderModeTest.kt/ByteSectionTest.kt/DebugByteSectionTest.kt;readmore-material3 依赖已移除(全仓零残留);主源集编译通过 + 单测全绿 + androidTest 源集编译通过 |
| T7 ConfigServer 可配置绑定 + restart + 测试 | ✅ 已完成 | externalAccess volatile + epoch 代元守卫(修复重启竞态:旧 accept 线程覆盖 running/争抢绑定)+ restart()/boundAddress 观测点 + close 复位;ConfigServerLifecycleTest 3 用例(Robolectric 真实 TCP,ping/pong 往返回归) |
| T8 SettingsRepository 外部访问键 + VM 接线 | ✅ 已完成 | 新键 server_external_access(ConfigKeys,宿主本地 prefs 不入远程镜像);setServerExternalAccess = 持久化+volatile 同步置位+异步 restart;init 偏好重放;DiagnosticsViewModel 改依赖 domain ConnectionMonitor(boundAddress 上移接口)+ listeningAddress 组合回退,P5-1 包边界门禁复绿;既有两测试构造同步补参 |
| T9 诊断页开关行 + 端口行参数化 | ✅ 已完成 | 通信服务区新增 SettingsToggleItem 外部访问行(testTag diagnostics-bind-all,默认停用,副标题风险提示带端口参数);端口行值 = settings_port_value(address,port) + scope 后缀资源,复制快照同源参数化;SettingsToggleItem 加可选 modifier 参数 |
| T10 SettingsInfoItem 扩展 + 底部弹层详情 | ✅ 已完成 | 可选参 valueMaxLines/onClick(null 零波及,onClick 追加 chevron);诊断页框架行单行省略+弹层全文、错误独立 Text 块收敛为 Warning 单行省略行;DiagnosticsDetailSheet(ModalBottomSheet surfaceContainerHigh + imePadding + SelectionContainer 全文 + 复制,testTag diagnostics-info-sheet) |
| T11 i18n 双语资源落地 | ✅ 已完成 | 新增 detail_fullscreen/detail_exit_fullscreen/detail_truncated_marker/meta_table_field/meta_table_value/settings_port_scope_local/settings_port_scope_all/settings_external_access(+hint)/diagnostics_sheet_close/diagnostics_detail_copy 双语齐备;settings_port_value 改写为纯占位符格式串(入 Parity 许可清单);record_copy_too_large 占位缺参调用修复 |
| T12 androidTest 重写与新增 | ✅ 已完成 | MarkdownByteSectionTest 5 场景(单卡/三标签切换/旧记录单表示/超限截断+全量复制/全屏尾块);DiagnosticsScreenTest 参数化重写 + 开关用例(默认停用→开启改绑→@After 复位,瞬态组合窗口重试兜底);DiagnosticsInfoSheetTest 2 用例(行长值点击弹层/复制+Snackbar+BACK 关闭);DetailHeroAnchorsTest/CryptoDetailZonesTest/TimelineListTest 断言对齐 markdown 形态;新增常驻 Robolectric 回归三件套 AnchorScrollRoboTest/MetaTableRoboTest/MarkdownCardRoboTest;设备套件 **70/70 全绿** |
| T13 AGENTS.md V26 沉淀 + lint 门禁 + 全量验证 | ✅ 已完成 | 强约束3 改写为 markdown 渲染契约(版本锁/D1 边界/标记后置/LazyColumn state 红线);新增强约束19 服务绑定契约(epoch 代元/P5-1 boundAddress 上移/写路径唯一);词汇表增补代码组与外部访问;工具链坑增补 mikepenz 异步解析与 m3 门面、表格紧凑管道、锚点滚动静默失效形态;lint **37 warnings / 0 errors** 达基线(V26 净零新增:清孤儿键×5+readmore 残留,抵消新库版本提示);assembleModernRelease/Debug 双变体通过;单测 443 全绿(仅存历史抖动用例,单跑均绿) |

---

## 1. 需求映射与目标

| # | 需求 | 目标形态 |
|---|------|----------|
| R1 | 记录详情仅用 markdown 渲染,不同数据适配合适 md 语法 | 所有**内容区块**(元数据/字节/参数/堆栈/SQL)由 markdown 描述并经专业库渲染;多表示字节 = 代码组(标签页),单表示 = 单代码块 |
| R2 | 专业 markdown 组件库,禁止手搓自研 | 引入 `com.mikepenz:multiplatform-markdown-renderer-m3`(选型见 §3),自定义仅限库公开扩展点(`markdownComponents` DSL) |
| R3 | 监听 IP 开关:127.0.0.1 ↔ 0.0.0.0 | 诊断页「通信服务」分区新增 M3 Switch 行,默认停用(回环);切换即重启 TCP 服务 |
| R4 | 框架与作用域等内容过长单行省略,单击底部弹层显示完整信息 | `SettingsInfoItem` 支持 `maxLines=1 + Ellipsis + onClick`;详情走 `ModalBottomSheet`(V21 弹窗规范) |

**设计决策 D1(边界划定)**:「仅 markdown」作用于**数据内容区**;Hero 徽章卡(`detail/HeroHeader.kt`)与锚点芯片(`detail/AnchorChips.kt`)为导航/标识交互组件,保留原生 Compose 实现。此边界写入 AGENTS.md V26 沉淀。

---

## 2. 现状审计结论(实施依据)

### 2.1 详情页渲染栈(将被替换的面)

| 现状构件 | 位置 | 处置 |
|---|---|---|
| 三态枚举 `ByteRepresentation` | `ui/logs/ByteRepresentation.kt:10-42` | **保留**(驱动 CodeGroup 标签可用性/默认态) |
| `SingleChoiceSegmentedButtonRow` 三态分段 | `ui/logs/RecordDetailSections.kt:86-169` | **删除** |
| Tier 分档 `sectionRenderMode`(≤128Ki 判定) | `RecordDetailSections.kt:57-68` | **删除**(阈值语义迁移为代码块显示上限常量) |
| Tier B `ChunkedText` O(1) 分块 | `core/util/ChunkedText.kt:12-34` | **保留但改职责**:仅供全屏查看器虚拟化使用,详情页不再直接上屏 |
| `ReadMoreText` 收起预览 | `RecordDetailSections.kt:40/314-338/442-456` | **删除**;依赖 `readmore-material3` 一并从 toml/build 移除 |
| 字节区块引擎 `byteSectionItems` / 文本 `textSectionItems` | `RecordDetailSections.kt:186-243 / 351-401` | **重写**为 markdown 区块装配器,item 计数契约同步维护 |
| 表示态记忆 `rememberSaveable` map | `ui/logs/RecordDetailScreen.kt:342-360` | **保留**(键迁移为 CodeGroup 选中标签) |
| 复制/导出护栏 `COPY_MAX_CHARS=4M` / `EXPORT_MAX_CHARS=32M` | `RecordDetailSections.kt:71-74`,`RecordDetailScreen.kt:120-176` | **原样复用**,数值与文案键禁止变更 |
| `BytesView` 硬保险丝 16M hex / 8M text | `core/util/BytesView.kt:86-90` | **不动**(有 `BytesViewTest` 锁死) |
| 锚点簿记 `AnchorBookkeeping` | `RecordDetailScreen.kt:310-327` | 随区块 item 数变化**同步重排**,`DetailHeroAnchorsTest` 回归兜底 |

### 2.2 诊断页与服务端

| 现状构件 | 位置 | 关键事实 |
|---|---|---|
| 框架与作用域三分区 | `ui/settings/DiagnosticsScreen.kt:147-187` | 框架名/版本字符串拼接(:160),错误信息为独立多行 Text 块(:174-184) |
| 信息行组件 `SettingsInfoItem` | `ui/components/SettingsComponents.kt:195-225` | 值文本 maxLines=2 + 160dp 限宽,无点击展开;被多设置页共用(**只能加可选参数**) |
| 端口行地址固化在资源 | `DiagnosticsScreen.kt:194`,`strings.xml:298`/`values-zh:292` | `"127.0.0.1:%1$d (local only)"` 硬编码于文案 |
| 复制快照同源耦合 | `DiagnosticsScreen.kt:94-127` | 地址行取同一资源;测试断言字面子串 `127.0.0.1:${vm.port}`(`DiagnosticsScreenTest.kt:111`) |
| 服务绑定硬编码回环 | `data/server/ConfigServer.kt:96` | TCP JSON 行协议(非 HTTP),PORT=46299(`core/codec/WireProtocol.kt:25`) |
| 生命周期 | `ModevApp.kt:76-78`,`ConfigServer.kt:87-121(close:281-290)` | 进程级单例 lazy start;close() 生产零调用;**无重启机制** |
| 鉴权现状 | `ConfigServer.kt:84-85` | `requireToken=null` 默认关闭(Vector legacy 无法携带令牌) |
| 设置存储范式 | `data/repo/SettingsRepository.kt:63-126,175-198` | 本地 prefs + 即写即推范式;服务自身配置不需要远程镜像 |
| Manifest | `AndroidManifest.xml:5` | 已有 INTERNET;入站 ServerSocket 无需 cleartext 配置,零改动 |

### 2.3 构建环境事实

Kotlin **2.2.20**、Compose BOM **2025.06.01**、material3 **1.4.0** pin、JSON 权威出口 Gson 单例(`core/codec/Json.kt:12`)、minSdk 26 / compileSdk 36、lint 人工约定基线 **37 warnings / 0 errors**。

---

## 3. 技术选型:markdown 组件库

### 3.1 候选对比

| 候选 | 形态 | 结论 |
|---|---|---|
| **mikepenz multiplatform-markdown-renderer(-m3)** | 纯 Compose KMP,M3 专属构件,`markdownComponents(codeFence=/codeBlock=)` 公开 DSL | **选定** |
| halilibo compose-richtext(commonmark) | 组件级定制弱,停滞 alpha | 否 |
| Markwon 4.6.2 | Android View 世界混入 Compose 页,主题双轨成本高 | 否 |

### 3.2 版本锁定

| 版本 | kotlin-stdlib 依赖 | 判定 |
|---|---|---|
| 0.44.0(latest) | 2.4.0 | ❌ metadata 超出 Kotlin 2.2 编译器可读上限(N+1) |
| **0.38.1**(2025-11-01) | **2.2.21** | ✅ **锁定**(与项目 Kotlin 2.2.20 同 minor) |
| 0.35.0 | 2.1.21 | 备选回退 |

传递依赖 `org.jetbrains:markdown:0.7.3`(JetBrains 解析器)允许直接 import AST 类型用于代码组提取(纯解析非渲染,不违反「禁止手搓渲染」)。

---

## 4. 目标架构

```
CryptoRecord / SqlRecord (BytesView 三态字符串,上游保险丝不动)
        │
        ▼
RecordMarkdownFactory(纯 Kotlin,JVM 可测)
    · 区块 → md 文本:标题(###)+ GFM 表(元数据/参数/bindArgs)+ 围栏代码块
    · 多表示字节 → 连续三围栏,info string ∈ {text, base64, hex}
    · 围栏安全:内容含 N 个连续反引号 → 围栏长度 = max(N)+1(≥3)
    · 显示上限:MAX_DISPLAY_CHARS = 128*1024,超限截前缀 + 截断标记行
        │
        ▼
RecordMarkdownContent(Compose,库渲染层)
    Markdown(md, components = markdownComponents(codeFence = ::ModevCodeFence))
        ├── ModevCodeGroup:连续 text/base64/hex 围栏运行检测(CodeGroupExtractor 纯函数)
        │      → PrimaryTabRow(reuse section_repr_* 文案)+ 选中的 CodeBlockCard
        │      选中态 = 既有 rememberSaveable map<sectionKey, repr>
        └── ModevCodeBlockCard(单围栏复用)
               header:语言标签 + [复制][全屏] IconButton
               body:heightIn(max=320dp)+ verticalScroll(+horizontalScroll)
                    + SelectionContainer { Text(mono) }
               复制走统一出口 copyFull(4M 护栏/snackbar 复用)

FullscreenTextDialog(Dialog usePlatformDefaultWidth=false)
    topBar(标题/关闭/复制)+ LazyColumn { ChunkedText O(1) 分块逐项 }
```

服务端链路:

```
DiagnosticsScreen(Switch 行,testTag diagnostics-bind-all)
  → DiagnosticsViewModel.setExternalAccessEnabled(Boolean)
      → SettingsRepository.setServerExternalAccess(key "server_external_access",
           默认 false,仅本地 prefs,不走 MirroredConfigWriter 远程镜像)
      → appScope.launch(IO) { ConfigServer.restart() }
ConfigServer:
  @Volatile externalAccess: Boolean   // false → bind "127.0.0.1", true → "0.0.0.0"
  restart(): synchronized 生命周期闸(running=false → close serverSocket → join
             acceptThread(超时兜底) → start()),复用既有指数退避重绑循环
  boundAddress: String?               // 诊断展示与测试观测点
```

注入侧不受影响:hook 进程恒连本机回环(`hook/ConfigChannel.kt:136`);已连接会话在 restart 时断开,由既有客户端重连循环恢复。

---

## 5. 任务分解与验收标准

### T1 依赖接入与冒烟
- 改动:`gradle/libs.versions.toml`、`app/build.gradle.kts`;锁定 0.38.1。
- 出口判据:`assembleModernDebug` 通过;Robolectric 冒烟 `MarkdownSmokeTest`(渲染 `"# hi"` 不抛 CompositionLocal 异常)通过。

### T2 RecordMarkdownFactory(纯函数层,先测后码)
- 新建 `ui/logs/markdown/RecordMarkdownFactory.kt`(纯 Kotlin,不 import compose/ui 包;用户可见文本经调用方传参资源串)。
- 生成规范(每规则 ≥1 测试):多表示 → 连续 text/base64/hex 围栏(空行分隔);单表示 → 单围栏(hex 恒在);元数据/params/bindArgs → GFM 表(`\|`→`\|` 转义、换行折叠);SQL/resultText/dbPath → 围栏;堆栈 → text 围栏(STACK_MAX_FRAMES=300 + plurals 标记行);截断态(truncated 或超 MAX_DISPLAY_CHARS=128*1024)→ 围栏后引用行标记;null/空字段省略;内容含连续反引号 → 围栏长度 = 最长 run+1(≥3)。
- 配套 `CodeGroupExtractorTest`:连续三围栏识别、普通段落中断分组、非法 info string 不入组。

### T3 ModevCodeBlockCard 组件
- `ui/logs/markdown/CodeBlockCard.kt`:header(lang 标签 + 复制 + 全屏)/body(heightIn(max=320dp) 双向滚动 + SelectionContainer + mono)。
- 复制接 `copyFull` 同一出口(4M 护栏);顺手修复 `RecordDetailScreen.kt:122` 的 `record_copy_too_large` 占位符未传参缺陷。

### T4 ModevCodeGroup 组件
- `PrimaryTabRow` + 选中表示的 CodeBlockCard;标签文案复用 `section_repr_*`;不可用表示不生成标签;选中态由调用方 rememberSaveable map 传入。
- 库接线:`markdownComponents(codeFence = ...)`,组内首围栏渲染整组,后续兄弟消费不重复渲染。

### T5 全屏查看器 FullscreenTextDialog
- Dialog 全屏(usePlatformDefaultWidth=false)+ ChunkedText 虚拟化 LazyColumn(item key `fs-{i}`)+ 顶栏关闭/复制;断言遵循短前缀 substring 约定。

### T6 详情页装配改造(原子提交)
- byteSectionItems/textSectionItems 改产出「md 片段 + item 计数」,每区块一个 item(key=`section-$key`,md 经 remember 缓存);
- 删除 SegmentedButton/Tier A/B/ReadMoreText/sectionRenderMode 及 SectionRenderModeTest;
- AnchorBookkeeping 计数公式同步(DetailHeroAnchorsTest 为门禁);
- MetaGrid 退役并入 md 元数据表;Hero 卡与锚点行保留;
- 移除 readmore-material3 依赖(先 grep 零残留);DebugByteSectionTest 更新或删除。

### T7 ConfigServer 可配置绑定 + 受管重启
- `@Volatile externalAccess`(false→"127.0.0.1",true→"0.0.0.0");`restart()` synchronized;`boundAddress` 观测点。
- Robolectric `ConfigServerRestartTest`:默认回环 → 开启+restart → 0.0.0.0 且本地握手成功 → 回切。

### T8 设置仓库 + ViewModel 接线
- `SettingsRepository.serverExternalAccessFlow` + `setServerExternalAccess`(键 `server_external_access`,仅本地 prefs);
- `DiagnosticsViewModel.setExternalAccess` = 写偏好 → IO 协程 restart。

### T9 诊断页开关行 + 端口行参数化
- Switch 行(ListItem 显式 Low 容器色 + 整行 toggleable + 内层 clearAndSetSemantics,testTag `diagnostics-bind-all`);
- 端口行 `%1$s:%2$d` 参数化 + 作用域后缀资源;copyDiagnostics 与 DiagnosticsScreenTest 同步参数驱动。

### T10 SettingsInfoItem 扩展 + 底部弹层
- 加可选参 `valueMaxLines: Int = 2`、`onClick: (() -> Unit)? = null`(零波及既有调用);
- Ready 态框架行 maxLines=1 + 弹层;Unavailable 错误 Text 块改行 + 弹层;
- `DiagnosticsDetailSheet`:ModalBottomSheet(containerColor=surfaceContainerHigh、imePadding、禁 navigationBarsPadding、hide 后置空),SelectionContainer 全文 + 复制,testTag `diagnostics-info-sheet`。

### T11 i18n 双语资源落地

| 键 | en | zh | 备注 |
|---|---|---|---|
| `settings_port_value`(改写) | `%1$s:%2$d` | `%1$s:%2$d` | 替换固化文案 |
| `settings_port_scope_local` | `(local only)` | (仅本机) | 后缀 |
| `settings_port_scope_all` | `(all interfaces)` | (所有接口) | 后缀 |
| `settings_external_access` | External access | 外部访问 | 开关行标题 |
| `settings_external_access_hint` | Listen on all interfaces (0.0.0.0). Devices on the same network can connect to port %1$d. | 监听所有网络接口(0.0.0.0),同一网络的设备可连接端口 %1$d。 | 副标题风险提示 |
| `diagnostics_detail_copy` | Copy | 复制 | 弹层动作 |
| `diagnostics_info_sheet_close` | Close | 关闭 | 弹层关闭 CD |
| `detail_truncated_marker`(新增) | Showing first %1$,d of %2$,d characters | 仅显示前 %1$,d 字符(共 %2$,d 字符) | 显示上限标记 |
| `detail_fullscreen` / `detail_exit_fullscreen` | View full text / Exit full screen | 查看全文 / 退出全屏 | 图标 CD |

退役:`record_see_more`(ReadMoreText 移除后若零引用则删,Parity 强制双语同删)。

### T12 androidTest 重写与新增
| 文件 | 动作 | 关键场景 |
|---|---|---|
| ByteSectionTest → MarkdownByteSectionTest | 整体重写 | 单围栏;三标签切换;旧记录隐藏 base64 标签;超限截断标记+复制仍全量;全屏尾块可达 |
| CryptoDetailZonesTest:196-198 | 修订 | section_repr_hex 断言 → 标签页文案 |
| DetailHeroAnchorsTest:199 | 修订 | 同上 + 锚点滚动全量回归 |
| DiagnosticsScreenTest | 修订+扩充 | 端口值参数化;开关默认停用;切换后缀变化;复制快照同源 |
| DiagnosticsInfoSheetTest | 新增 | 行点击弹层全文;错误态弹层;关闭 |
| MarkdownSmokeTest(Robolectric) | 新增 | T1 冒烟固化 |

### T13 AGENTS.md V26 沉淀 + 门禁
- 强约束 3 改写为「详情页 markdown 渲染契约」;新增服务绑定契约;词汇表增补;lint ≤37 warnings / 0 errors。

---

## 6. 风险登记

| # | 风险 | 缓解 |
|---|---|---|
| K1 | mikepenz m3 构件(material3 1.3.x 编译)与 pin 的 1.4.0 二进制摩擦 | T1 出口判据先行;降级 0.35.0 再验 |
| K2 | 锚点簿记 item 计数与新装配不一致 → 错滚 | T6 原子提交 + DetailHeroAnchorsTest 门禁 |
| K3 | 大文档主线程解析卡顿 | 显示上限前置工厂;remember 缓存;全屏 ChunkedText 虚拟化 |
| K4 | 0.0.0.0 暴露面(requireToken 默认关) | 副标题风险文案 + 默认停用;不改令牌默认(legacy 兼容) |
| K5 | restart 竞态(旧 socket 未释放) | 指数退避重绑兜底 + Robolectric 往返用例 |
| K6 | gradle 守护进程 OOM | 重任务串行,assemble 前 --stop 清理 |
| K7 | PowerShell 管道毁中文源码 | 一律 edit 工具/.NET 字节级读写(实施中二次实锤,已即时重写修复) |

### 6.1 实施期新增沉淀(已并入 AGENTS.md V26,摘要)

- **LazyColumn 漏传 `state` = 锚点滚动静默失效**:V6 装配重写曾遗漏
  `state = listState`,animateScrollToItem 打在孤立状态上零异常零移动;
  回归测试 `AnchorScrollRoboTest`(Robolectric 全装配,秒级)。
- **截断标记打断代码组聚合**:引用块插在连续围栏之间 → 组退化多卡;
  工厂恒将标记后置于全部围栏之后。
- **mikepenz 异步解析竞态 + m3 门面未透传 immediate**:一律走核心
  `rememberMarkdownState(immediate=true)` + m3 state 重载。
- **GFM 单元格尾随空格**:'| Cell |' 解析保留管道前空格 → 紧凑 '|Cell|'。

## 7. 总体验收标准(DoD)与验收结果

### 7.1 验收结果(2026-08-26)

| 门禁 | 结果 |
|---|---|
| `:app:connectedModernDebugAndroidTest`(雷电 emulator-5554) | ✅ **70/70 全绿** |
| `:app:lintModernRelease` | ✅ **37 warnings / 0 errors**(= 基线,V26 净零新增) |
| `assembleModernRelease` / `assembleModernDebug` | ✅ 双变体通过 |
| AGENTS.md V26 沉淀 | ✅ 已落盘 |

### 7.2 DoD 清单

1. `.\gradlew.bat :app:testModernDebugUnitTest` 全绿;
2. 雷电模拟器 `:app:connectedModernDebugAndroidTest` 全绿(XML 无 failure);
3. `.\gradlew.bat :app:lintModernRelease` ≤37 warnings / 0 errors;
4. `assembleModernRelease + assembleModernDebug` 双变体通过;
5. 手动走查:①算法详情 md 形态(表格+代码组三标签、限高滚动、全屏尾部、4M 护栏)②SQL 详情(sql 围栏+bindArgs 表)③外部访问开关生效(telnet 验证)、关闭回切且 hook 正常上报④框架行单行省略+弹层全文可复制⑤深色模式容器色正确;
6. AGENTS.md V26 条目落盘。

**实施顺序**:T1 → T2 → T3 → T4 → T5 → T6 → (T7 → T8 → T9) ∥ T10 → T11/T12 随步落地 → T13 收口。

**非目标**:REST/MCP HTTP 服务、端口可配置化、令牌默认策略变更、wire 协议任何改动、回收站类功能。
