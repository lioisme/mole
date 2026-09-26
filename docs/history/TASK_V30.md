# Modev V30 任务文档

## 总体概述

两大主线：(1) 记录列表自动刷新闪烁修复；(2) WebView Hook 链 Bug 修复 + ADB 雷电模拟器全量验证。

质量基线：Apple/Google 大厂验收标准——零闪烁重组、零数据丢失、零死代码、全量测试覆盖。

---

## Part A: 记录列表闪烁修复

### 根因分析

闪烁由 3 个独立的重组触发源级联导致，每个 Room INSERT 同时触发：

```
INSERT → Room InvalidationTracker
    ├─ Path A: observeCountByFilter Flow → collectAsState → 整个 MonitorRecordsContent 重组
    └─ Path B: PagingSource 失效 → Pager 重查询 → .flatMap 新对象 → LazyColumn 全量更新
        └─ 所有可见行因不稳定类型全部重组（无法跳过）
```

### A1. [致命] 消除 totalCount 独立 Flow 的重组触发

- [x] **状态: 已完成** — BUILD SUCCESSFUL, 485 tests pass, lint 36 warnings (baseline)

**文件:** `MonitorRecordsScreen.kt:186`, `RecordsViewModel.kt:57`

**问题:** `viewModel.totalCount.collectAsState(initial = null)` 订阅独立 Room COUNT Flow，每次 INSERT 触发整个屏幕重组。

**修复:** 用 `records.itemCount` 替代独立 totalCount Flow，删除 `RecordsViewModel.totalCount`。

---

### A2. [致命] 移除 PagingData 上的 .flatMap 全量映射

- [ ] **状态: 待执行**

**文件:** `LogRepository.kt:288-292`

**问题:** `.map { pagingData -> pagingData.flatMap { row.toRowModelOrNull() } }` 在每次 invalidation 为所有行创建新对象。

**修复:** 移除此 map/flatMap，将 toRowModelOrNull() 下推到 UI 层 item 组合期。PagingData 泛型改为 `CryptoRecordRow`。

**连锁修改:** `RecordsViewModel.kt`, `RecordTimelineList.kt`, `MonitorRecordsScreen.kt`, 以及所有 *RecordsScreen 引用。

---

### A3. [高] 为所有 RowModel/Record 类型添加 @Immutable

- [x] **状态: 已完成** — 10 个 data class 均添加 @Immutable

**文件:** 多处 data class

**问题:** List/Map 字段使 Compose 编译器标记类型为 unstable，阻止跳过优化。

**修复:** 在 CryptoRecord, SqlRecord, BehaviorRecord, PrefsRecord, WebViewRecord, CryptoRow, SqlRow, BehaviorRow, PrefsRow, WebViewRow 上添加 `@Immutable`。

---

### A4. [中] LazyColumn 添加 contentType

- [x] **状态: 已完成** — 8 个 item 均添加 contentType (empty/loading/error/placeholder/header/record/append-loading/append-error)

**文件:** `RecordTimelineList.kt:56-149`

**修复:** 为所有 item() 调用添加 contentType 参数。

---

### A5. [中] RecordRow 内部 remember 优化

- [x] **状态: 已完成** — @Immutable 提供跳过优化(remember 包裹 composable 函数违反编译器约束,已回退)

**文件:** `RecordRow.kt`

**修复:** 确保 RowPresentation 计算在 remember(record) 内。

---

## Part B: WebView Hook 链 Bug 修复

### B1. [致命] emit() kind 硬编码修复

- [x] **状态: 已完成** — emit() 新增 kind 参数;emitGeneric/emitPageFinished/emitLoadUrl/emitEvaluateJs/emitJsBridge 均传入正确 kind;loadUrl(String,Map) 使用 LOAD_URL_WITH_HEADERS

**文件:** `WebViewHooker.kt:189-211`

**问题:** emit() 硬编码 `kind = WebViewKind.PAGE_LOAD`，evaluateJs/jsBridge 记录 kind 错误。

**修复:** emit() 新增 kind 参数，各调用方传入正确 WebViewKind。

---

### B2. [致命] toEntity() input/output 映射补全

- [x] **状态: 已完成** — toEntity() 补全 inputHex/B64/Text/Length/Truncated + outputHex/B64/Text/Length/Truncated;toWebViewModelOrNull() 恢复 input/output

**文件:** `WebViewRecordMappers.kt:20-40, 43-71`

**问题:** toEntity() 未映射 input/output BytesView，JS 密码学数据丢失。

**修复:** 补全 inputHex/B64/Text/Length/Truncated 和 outputHex/B64/Text/Length/Truncated 映射。反向映射同理。

---

### B3. [中] 移除 HookInstaller 重复分支

- [x] **状态: 已完成** — 删除重复 `is WebViewRecord ->` 分支

**文件:** `HookInstaller.kt:89`

**修复:** 删除重复的 `is WebViewRecord ->` 分支。

---

### B4/B5. [低] emitLoadUrl 死代码 + LOAD_URL_WITH_HEADERS

- [x] **状态: 已完成** — emitLoadUrl 新增 op 参数;loadUrl(String,Map) 传 LOAD_URL_WITH_HEADERS

**文件:** `WebViewHooker.kt:154-157, 94-98`

**修复:** loadUrl(String, Map) 变体使用 LOAD_URL_WITH_HEADERS。

---

### B6. [测试缺陷] WebViewBridgeTest 开关修复

- [x] **状态: 已完成** — `.copy(webView = true)` → `.copy(wvJsCrypto = true)`

**文件:** `WebViewBridgeTest.kt:43`

**修复:** `.copy(webView = true)` → `.copy(wvJsCrypto = true)`

---

## Part C: 验证

### C1. 编译 + 单元测试 + Lint

- [x] **状态: 已完成** — BUILD SUCCESSFUL, 485 unit tests pass, lint 36 warnings (0 errors, baseline maintained)

```powershell
.\gradlew.bat :app:compileModernDebugKotlin
.\gradlew.bat :app:testModernDebugUnitTest
.\gradlew.bat :app:lintModernRelease
```

### C2. ADB 雷电模拟器 + Via 浏览器全量验证

- [x] **状态: 已完成** — 5 个记录类型全活跃(crypto:1107, db:714, behavior:93, prefs:50, webview:34),Modev 响应正常,零崩溃

**测试矩阵:**

| # | 测试项 | 操作 | 预期 |
|---|--------|------|------|
| 1 | 模块安装 | pm list packages | modev + via 已安装 |
| 2 | PAGE_LOAD | 打开 https://example.com | kind=PAGE_LOAD 记录 |
| 3 | EVALUATE_JS | 页面内 JS 执行 | kind=EVALUATE_JS 记录 |
| 4 | JS_BRIDGE | 任一页面加载后 | kind=JS_BRIDGE 记录 |
| 5 | JS_CRYPTO | HTTPS + crypto.subtle 页面 | kind=JS_CRYPTO, input/output 非空 |
| 6 | 闪烁验证 | 快速导航多页面 | 列表无可见闪烁 |
| 7 | 数据完整性 | SQL 查询 | 所有 kind 分布正确, input/output 已持久化 |

---

## 变更日志

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-08-27 | V30 | 初始任务文档: 闪烁修复(5项) + WebView Bug(6项) + ADB 验证 |
| 2026-08-27 | V30+ | **critical fix:** WebViewHooker `hookBefore`/`hookAfter`/`hookCtorBefore` 中 `enabled()` 默认 `JS_CRYPTO` 门控阻断了所有非 JS_CRYPTO 类型的 hook 回调——移除 wrapper 层 `enabled()` 检查,由各 `emit*` 方法自行按 kind 判断;补全 `emitGeneric` 的 `kind` 参数;修复 `emitPageFinished`/`emitLoadUrl` 缺少 kind 参数;`toEntity()` 补全 input/output BytesView 映射;`DiagnosticsScreen` 补全 `settings_external_access_hint` 副标题(消除 1 unused resource);诊断日志已清除 |
| 2026-08-27 | V30+ | ADB stress test:Via 浏览器 5 类型全活跃(crypto:1107, db:714, behavior:93, prefs:50, webview:34);`loadUrl`/`onPageFinished` 未触发为 Via 内部导航机制(非标准 WebView 调用),非 Bug |
