# Modev 任务书 V12:展开/收起动效 + 长按自由复制 + WebView 加解密监听 + 列表行秒级时间与参数独立成行

**文档版本**: v1.0 | **创建日期**: 2026-08-17 | **状态**: 执行中
**适用范围**: `ui/logs/RecordDetailScreen.kt` + `ui/components/RecordRow.kt` + `hook/webview/`(新增)+ `hook/hooks/WebViewHooker.kt`(新增)+ `core/model/` + `ui/settings/CaptureSettingsScreen.kt` + `ui/theme/KindColorTokens.kt` + i18n(en/zh)
**质量目标**: 代码质量、架构设计达到苹果/谷歌大厂验收标准(动画与状态单一事实源、主线程零大分配、探针幂等零宿主侵入、门禁全绿)

---

## 0. 需求条款(用户口径)

| # | 需求 |
|---|------|
| R1 | 详情页字节区块「展开」「收起」按钮使用时**不得引起整体界面错位/移位,必须有动画**;内容文本支持**长按自由复制** |
| R2 | 新增能力:监听 WebView 内 JS 加解密库(WebCrypto / CryptoJS / JSEncrypt 等),经探针注入捕获完整链路 |
| R3 | 算法列表时间显示**精确到秒**;**除徽标外所有参数各自独立成行**(不共用一行) |

## 1. 全量审计(现状 + 根因)

### 1.1 R1 展开/收起 — 现状与缺口

**已具备(维持不动)**:折叠语义状态机 `userToggledFold`(XOR + `rememberSaveable(record.id)`,`RecordDetailScreen.kt:263-266`)、行数阈值估算(`LineEstimate` + `rememberFoldMetrics` `:588-601`)、`ChunkedText` 惰性分块零主线程物化(`:533-572`)、按钮文案「展开/收起」(`:519`)、预览与短全量已包 `SelectionContainer`(`:479-508`)。

**缺口 1(核心·动画)**:`toggleFold`(`:264-266`)瞬时翻转状态 → 展开态 chunk items 直接插入 LazyColumn、收起态直接移除——**内容瞬时出现/消失、下方区块瞬时跳位,零过渡**;预览↔分块之间也无连续动画。

**缺口 2(复制)**:展开态分块 Text(`:555-569`)与堆栈帧(`:375-382`)、元数据行(`:390-422`)均**无 `SelectionContainer`**,不可长按选择复制;V7 曾将复制收敛到导出,现按用户要求恢复"全文本可选"。

**缺口 3(锚点)**:LazyColumn item key 已稳定(`section-$key-header` / `-text-chunk-$i` / `-hex-chunk-$i`),具备做平滑位移动画的基础,但未启用 `animateItem()` 位移/显隐动画。

### 1.2 R2 WebView 监听 — 现状与缺口

**现状:零支持。** 全量 grep 确认代码库无 `WebView/evaluateJavascript/addJavascriptInterface/crypto.subtle/CryptoJS` 任何引用:

| # | 环节 | 现状 | 缺口 |
|---|------|------|------|
| A | Hook 装配 | `HookManager.kt:100-115` 注册 11 类 JCA Hooker | 无 WebView Hooker;`HookSwitch` 无 webview 开关;`enabled(kind)`/`anyEnabled` 无对应分支 |
| B | 类别模型 | `CryptoKind` 11 枚举(`CryptoKind.kt`);kind/operation 以**名称字符串**存库(`CryptoRecordEntity.kt:28-30`) | 新增枚举值零 DB 迁移、旧数据兼容;筛选菜单 `KindFilterMenu` 遍历 `entries` 自动纳入,`kindLabelRes`/`kindSymbol`/`kindColor` 由编译期强制补全 |
| C | 记录类型 | `RecordTypes.CRYPTO`(`RecordTypes.kt:8`) | 复用 CRYPTO 类型即可(仍是算法记录),无需新 recordType |
| D | 配置通道 | `HookSwitch` Gson 序列化(`WireCodec.kt:57-59`);宿主侧始终写全量对象 | 新增字段:新宿主→旧进程忽略未知字段(Gson 默认);旧宿主→新进程缺字段取 JVM 默认 false——新开关默认关,语义安全 |
| E | 注入点 | `MethodFinder` 反射缓存(空安全) | `android.webkit.*` 由 app classloader 加载,可正常 find;探针 JS 不能走 asset(注入进程无模块资源上下文),须内嵌字符串常量 |

### 1.3 R3 列表行 — 现状与缺口

`RecordRow.kt`:
- **时间无秒**:`:67` `DateFormat.getTimeFormat(context)` → 仅 HH:mm 粒度(12/24h 时制随系统,但无秒)。
- **参数同行**:`:94-111` 行1 = 算法 + 操作同行;`:112-133` 行2 = 方向胶囊 + 数据段同行;`:135-140` 时间右对齐第三列——违反"除徽标外每参数独立成行"。
- 徽章 28dp 左列锚点恒定(`:90-91`),为唯一允许共用一行的元素(与算法同行)。

## 2. 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 动画方案 | **头部双态互斥 `AnimatedVisibility` + 分块 `Modifier.animateItem()` 双轨**:①头部 item 内,折叠预览与内联全量(未超行数)分别包 `AnimatedVisibility(enter = expandVertically + fadeIn, exit = shrinkVertically + fadeOut)`——小数据展开/收起为**高度连续动画**,footer 与下方区块平滑随动;②大数据展开态分块 items 加 `Modifier.animateItem()`(出现 fadeIn + 位移 spring)——**插入/移除平滑位移,滚动锚点稳定**,无瞬时跳位;③按钮文案为状态即时翻转(语义即时),内容为动画过渡(视觉连续)——状态与动画职责分离 |
| D2 | 动画规格 | 时长/缓动单一事实源:头部 `EXPAND_ANIM_MS = 220` + `FastOutSlowInEasing`;分块位移/出现用 `spring(dampingRatio = Spring.DampingRatioNoBouncy)`;常量内联于 `RecordDetailScreen.kt`,插桩测试引用 |
| D3 | 复制范围 | 详情页**全部文本**可长按选择:元数据块整块、堆栈逐帧、参数/别名(已有 `SectionText`)、字节区块预览/内联全量(已有)、**展开态分块逐块**分别包 `SelectionContainer`;分块 ≤4K 字符/块,单块选择布局安全;**跨块选区不支持**(与 V11 hex 分块既有限制一致,完整数据出口=导出 JSON 维持) |
| D4 | WebView 记录形态 | 新增 `CryptoKind.WEB_CRYPTO`(字母徽章 `W`、类别色、`kindLabelRes` 新键)+ `HookSwitch.webView: Boolean = false`(默认关,同 Base64 先例)+ 新增 `CryptoOperation.ENCRYPT / DECRYPT`(WebCrypto encrypt/decrypt 语义,现有 `INIT/DO_FINAL` 不贴合);`enabled(kind)`/`anyEnabled` 同步扩展;kind/operation 字符串存储 → **零 DB 迁移** |
| D5 | 探针注入机制 | ①Hook 基类 `WebViewClient.onPageFinished(WebView, String)`(after):delegate 原实现后注入探针——覆盖绝大多数"复写并调 super"的客户端;②Hook `WebView.<init>` / `loadUrl` / `loadData` / `loadDataWithBaseURL` / `evaluateJavascript`(before):`ensureBridge`——`addJavascriptInterface(bridge, "modevJsBridge")` 每 WebView 经 WeakSet 幂等注册,覆盖不复写 onPageFinished 的客户端与 SPA 动态脚本;探针幂等守卫 `window.__modevProbe`,重复注入零副作用;③`@JavascriptInterface report(String)` 运行于 WebView 私有线程,`RecordSink.emit` 线程安全,直接复用 |
| D6 | 探针实现 | JS 探针内嵌 **Kotlin 字符串常量**(`hook/webview/WebviewProbe.kt`,非 asset,注入进程无资源上下文,零 IO 零失败面):包装 `crypto.subtle`(encrypt/decrypt/digest/sign/verify/importKey/generateKey)+ CryptoJS(AES/DES/TripleDES/Rabbit/RC4/MD5/SHA*/Hmac*/RSA)+ JSEncrypt(encrypt/decrypt);ArrayBuffer/WordArray → hex 转换;数据量经桥接接口 `maxHexChars()` 预截断(读 `HookSwitch.maxBytesPerField`),Java 侧 `BytesView.of` 保险丝兜底;包装失败/页面无 JS 环境仅记录,绝不影响宿主 |
| D7 | 时间格式 | `SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm:ss" else "h:mm:ss a", Locale.getDefault())` ——秒级精度且尊重系统时制 |
| D8 | 行版式 | 徽章独占左列(28dp 锚点不变);主体 Column 内**五段各占一行**:算法 / 操作 / 方向(有则显)/ 关键数据(有则显)/ 时间(移至行末);移除右对齐时间;`RECORD_ROW_MIN_HEIGHT=68dp` 保留为下限(内容自适应加高,空段行高一致) |
| D9 | 配置兼容 | `HookSwitch.webView` 附加于末尾;不引入迁移/重放逻辑(见 1.2-D 分析) |
| D10 | 宿主/存储 | 记录仍为 `recordType=CRYPTO`;详情页渲染零改动(WEB_CRYPTO 记录经既有字节区块渲染,`algorithm` 如 `WebCrypto/AES-GCM`、`CryptoJS/AES`,provider=`WebView`,params 承载 tagLength/mode 等) |

## 3. 改动清单(执行顺序)

### T1 `core/model/CryptoKind.kt` + `CryptoOperation.kt`
- 追加 `WEB_CRYPTO`(注释:WebView JS 加解密,WebCrypto/CryptoJS/JSEncrypt);
- 追加 `ENCRYPT / DECRYPT`(注释:JS 侧 encrypt/decrypt 语义)。

### T2 `core/model/HookSwitch.kt`
- 追加字段 `val webView: Boolean = false`;`enabled(kind)` 加 `WEB_CRYPTO -> webView` 分支;`anyEnabled` 纳入。

### T3 `hook/webview/` + `hook/hooks/WebViewHooker.kt`(核心)
- `WebviewProbe.kt`:探针 JS 字符串常量(PROBE_JS,幂等守卫 `__modevProbe`、crypto.subtle/CryptoJS/JSEncrypt 包装、`maxHexChars()` 预截断)+ `WebViewEventParser`(Gson 解析 `JsEvent`,纯 Kotlin 可 JVM 单测)+ `WebViewEventMapper`(JsEvent → CryptoRecord 映射,纯函数可 JVM 单测);
- `WebViewJsBridge.kt`:`@JavascriptInterface report(json)` → parser + mapper → `sink.emit`;`@JavascriptInterface maxHexChars(): Int` 读 config;畸形载荷静默丢弃(与 `WireCodec.recordOf` 同规);
- `WebViewHooker.kt : BaseHooker`(kind=WEB_CRYPTO):按 D5 注册 6 个 hook(全部经 `MethodFinder` 空安全 + `runCatching` 包裹,零异常外泄);`ensureBridge` WeakSet 幂等;provider=`WebView`;
- `HookManager.kt:100-115`:registrars 追加 `WebViewHooker(…)`。

### T4 `ui/logs/RecordDetailScreen.kt` — 动画 + 复制(核心)
- 头部 `ByteSectionHeader`:折叠预览与内联全量按 D1 包 `AnimatedVisibility`(互斥可见性,规格 D2);
- `byteSectionItems` 展开态分块 items:`SelectionContainer { Text(…) }.animateItem()`(text 与 hex 双路);
- 复制全覆盖(D3):`MetadataBlock` 整块、堆栈每帧、分块逐块补 `SelectionContainer`;
- 保持:折叠状态机、`ChunkedText` 惰性组合、`needsFold` 判定、key 结构——零回归面。

### T5 `ui/components/RecordRow.kt` — 版式 + 秒级时间
- 时间格式改 D7(seconds,尊重时制),`remember(context)` 缓存;
- 版式改 D8:右列时间移除,主体 Column 五段各占一行;徽章左列锚点、`kindColor`/`directionLabel` 复用。

### T6 `ui/settings/CaptureSettingsScreen.kt`
- 「算法捕获」区新增 `SwitchRowItem(switch_webview)`,`on = switch.webView`,默认关;图标 `Icons.Filled.Public`。

### T7 i18n `values/strings.xml` + `values-zh/strings.xml`
- 新增键:`switch_webview`、`kind_webview`、`op_encrypt`、`op_decrypt`;en/zh 同步,键对 diff=0。

### T8 `ui/theme/KindColorTokens.kt` + `RecordRow.kindSymbol`/`kindLabelRes`
- `WEB_CRYPTO` 补浅/深类别色(编译强制,对比度 ≥ AA)+ 徽章字母 `W` + 显示名键(T7)。

## 4. 测试计划

| 层 | 用例 | 断言要点 |
|----|------|----------|
| 单测(新) | `WebviewProbeTest`(JVM) | PROBE_JS 含 `__modevProbe` 幂等守卫、crypto.subtle 方法、CryptoJS AES/MD5/Hmac、JSEncrypt、`maxHexChars` 截断调用;不含 `</script>` 注入逃逸 |
| 单测(新) | `WebViewEventMapperTest`(JVM) | mapper 合法/畸形/超限/开关关四态映射:合法→记录(kind/方向/算法/IV/输入输出);畸形→null 丢弃;超限→BytesView 保险丝截断 |
| 单测 | `HookSwitchTest` 扩展 | webView 默认 false;enabled(WEB_CRYPTO) 与 anyEnabled 联动(仅 webView 开→anyEnabled=true) |
| 插桩 | `ByteSectionTest` 扩展 | ①`expand_animates_anchorsStable`:点击「展开」,mainClock 半程(动画进行中)chunk 节点在树、header 顶部 y 坐标不变(`getUnclippedBoundsInRoot`),动画完成后末块可 scrollToNode 达;②`collapse_animates_exit`:点击「收起」,waitForIdle(含动画终态)后分块移除、预览出现、按钮文案翻转;③`expandedChunks_selectable`:`hasSetSelectionAction()` 选择语义存在;④既有惰性/阈值用例零改动回归(首帧组合无出现动画,契约不变) |
| 插桩(新) | `RecordRowLayoutTest` | ①时间文本匹配秒级正则(`\d{1,2}:\d{2}:\d{2}` 或带 am/pm);②算法/操作/方向/数据/时间五段 `getUnclippedBoundsInRoot` 垂直互不重叠(独立成行);③徽章锚点恒定 |
| 插桩(新) | `WebViewBridgeTest`(宿主进程真实 WebView,不依赖 Xposed) | WebView + 探针 + bridge(复用 T3 装配路径)→ file:// base 页面执行 WebCrypto digest/encrypt → 测试 sink 断言方向/算法/输入输出记录;二次注入幂等(零重复记录) |
| 回归 | `SettingsComponentsTest`/`FourScreensNavTest`/`RecordDetailViewModelTest`/`AppSortTest`/`KindColorTokensTest` | 零改动全绿;筛选菜单遍历 `CryptoKind.entries` 自动纳入 WEB_CRYPTO |

## 5. 验收标准(Definition of Done)

- [ ] R1:小数据区块展开/收起为 220ms 高度连续动画,大数据区块分块淡入淡出+平滑位移;切换全程 header 顶部锚点不动、无瞬时跳位、无错位;详情页全部文本长按可选择复制(含展开态分块/堆栈/元数据);
- [ ] R2:WebView 探针捕获 WebCrypto/CryptoJS/JSEncrypt 记录(kind=WEB_CRYPTO,方向/IV/输入输出完整);开关默认关,开启后生效;捕获设置页可见开关;零 DB 迁移、旧记录兼容;
- [ ] R3:记录行时间含秒(尊重系统时制);除徽标外参数各自独立成行,徽章锚点恒定;
- [ ] 门禁:`testModernDebugUnitTest`/`testLegacyDebugUnitTest` 全绿、`connectedModernDebugAndroidTest` 全绿、`lintModernDebug` 零新增、`assembleModernRelease` 通过;
- [ ] 本任务书落盘 `docs/v12-webview-capture-ui-polish-task.md`,执行记录逐项勾选。

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| 动画测试受时钟/帧率影响 | 断言用语义节点 + `mainClock` 显式推进,锚点用边界不重叠而非像素宽度 |
| `animateItem()` 与既有惰性契约冲突 | 首帧组合不触发出现动画;既有「末块不在树」用例零改动验证 |
| 页面无 JS 环境 / 非安全上下文 / CSP | 探针幂等 + `runCatching` 静默降级;`crypto.subtle` 仅 https/localhost/file 存在,探针守卫判断 |
| 子类不复写 `onPageFinished` | 基类 onPageFinished 为主注入面;loadUrl/evaluateJavascript 钩子兜底(SPA/动态注入);自定义客户端不调 super 的极端场景仅日志提示 |
| 大字符串跨 JNI 开销 | JS 侧 `maxHexChars()` 预截断 + Java 侧 BytesView.of 保险丝,双闸防 1MB+ 字符串 |
| 旧版本组件配置缺字段 | 新开关默认 false(同 base64 先例);Gson 未知字段忽略,双版本互通 |
| 探针逃逸/宿主崩溃 | 探针为纯只读包装、无外联、异常全捕获;`HookEngine` PROTECTIVE 模式兜底 |

## 7. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | 枚举扩展(WEB_CRYPTO / ENCRYPT / DECRYPT) | [ ] | |
| T2 | HookSwitch.webView 开关 | [ ] | |
| T3 | WebViewHooker + JsBridge + 探针 + HookManager 注册 | [ ] | |
| T4 | 详情页动画 + 复制全覆盖 | [ ] | |
| T5 | RecordRow 版式 + 秒级时间 | [ ] | |
| T6 | 捕获设置开关行 | [ ] | |
| T7 | i18n 4 键(en/zh) | [ ] | |
| T8 | 类别色/徽章/标签 | [ ] | |
| T9 | 测试(§4) | [ ] | |
| T10 | 质量门禁 | [ ] | |
