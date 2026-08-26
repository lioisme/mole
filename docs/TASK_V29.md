# Modev V29 任务文档

## 总体概述

四大任务：(1) WebView 监控升级为第五一等公民；(2) 记录列表重构（信息密度最大化 + 性能强化 + 视觉升级）；(3) 全局列表性能优化；(4) 雷电模拟器全量压力测试。

---

## 任务一：WebView 监控 → 第五一等公民

### 1.1 现状

WebView 监控仅作为 `RecordType.CRYPTO` 下的子类别 `WEB_CRYPTO`，覆盖 JS 密码学 API（WebCrypto/CryptoJS/JSEncrypt）。

### 1.2 目标

独立 RecordType `WEBVIEW`，与 Algorithm/Database/Behavior/Prefs 并列为第五一等公民。

### 1.3 WebViewKind 类别

| WebViewKind | 语义 | 默认 | 噪点 |
|-------------|------|------|------|
| PAGE_LOAD | loadUrl/loadData/loadDataWithBaseURL | 开 | SIGNAL |
| EVALUATE_JS | evaluateJavascript | 开 | SIGNAL |
| JS_BRIDGE | addJavascriptInterface | 开 | SIGNAL |
| DEBUG | setWebContentsDebuggingEnabled | 开 | SIGNAL |
| JS_CRYPTO | WebCrypto/CryptoJS/JSEncrypt | 关 | NOISE_HIGH |

### 1.4 实施进度

- [x] Phase 1: Core 模型层
  - [x] RecordTypes.WEBVIEW + RecordType.WEBVIEW
  - [x] WebViewKind 枚举
  - [x] WebViewOperation 枚举
  - [x] WebViewRecord 数据类
  - [x] HookSwitch 扩展 (webviewListen + 5 类别)
  - [x] AppListenOverride 扩展 (webviewListen 字段)
  - [x] EffectiveSwitch WebView 分支
  - [x] CategoryTaxonomy WebView 5 类别
- [x] Phase 2: Codec 层
  - [x] WebViewRecordCodec
  - [x] RecordCodecRegistry 注册
- [x] Phase 3: Data 层
  - [x] RowModel.WebViewRow
  - [x] 映射器 WebViewRecord → WebViewRow
  - [x] LogRepository writeBatch WebView 分支
- [x] Phase 4: Hook 层
  - [x] 独立 WebViewProbe (Probe 接口)
  - [x] WebViewHooker 重构 (5 个子 Hooker)
  - [x] HookInstaller probes 清单
- [x] Phase 5: UI 层
  - [x] WebViewColorTokens
  - [x] KindIcons.webview
  - [x] RecordRow.webviewPresentation
  - [x] WebViewRecordsScreen
  - [x] AppMonitorScreen 第五行入口 + 五段统计
  - [x] PerAppListenSettingsScreen WebView 分区
  - [x] ModevRoutes + ModevNav 路由
- [x] Phase 6: i18n 双语字符串

---

## 任务二：记录列表重构

### 2.1 目标

信息密度最大化 + 视觉层次 + 性能。

### 2.2 实施进度

- [ ] RowPresentation remember 化
- [ ] RecordRow 布局优化 (密度 + 尾栏)
- [x] WebViewRow 行形态

---

## 任务三：全局列表性能优化

### 3.1 目标

大量 item 不卡顿、不掉帧、不崩溃。

### 3.2 实施进度

- [ ] LazyColumn contentType 声明
- [ ] DAO 索引确认
- [ ] PagingSource 窗口验证

---

## 任务四：全量测试

### 4.1 实施进度

- [x] 单元测试 (`testModernDebugUnitTest`) — 485 tests pass
- [x] Lint 门禁 (`lintModernRelease`) — 36 warnings baseline, 0 errors
- [ ] 设备 UI 测试 (`connectedModernDebugAndroidTest`)
- [ ] ADB 压力测试 (雷电模拟器)
