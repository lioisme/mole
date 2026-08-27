# Task: 网络环境监听能力 (V30)

> **版本**: V30  
> **日期**: 2026-08-27  
> **状态**: 进行中  

---

## 总览

为 Modev 新增**第六大监听能力 — 网络环境监听** (`RecordType.NETWORK`)，覆盖：

| 子域 | 功能 | 本质 |
|------|------|------|
| SSL Pinning 绕过 | 绕过应用证书固定，允许 Reqable/Charles/Burp 拦截 HTTPS | 安全控制干预 |
| WiFi 代理隐藏 | 隐藏系统/应用层代理配置，防止应用检测 MITM 代理 | 网络环境伪装 |
| VPN 隐藏 | 隐藏 VPN 连接状态，防止应用检测 VPN | 网络环境伪装 |
| Reqable 堆栈跟踪 | 捕获网络请求发起的 Java 调用栈，定位请求来源代码 | 可观测性 |

---

## 进度追踪

### Phase 0: 第三方源码拉取
- [x] 创建 baseline/ 目录 ✅ 2026-08-27
- [x] 拉取 JustTrustMePP ✅ 2026-08-27
- [x] 拉取 JustTrustMePro ✅ 2026-08-27
- [x] 拉取 NoVPNDetect ✅ 2026-08-27
- [x] 拉取 NoVPNDetectEnhanced ✅ 2026-08-27
- [x] 拉取 TrustMe ✅ 2026-08-27
- [x] 拉取 SSLUnpinning ✅ 2026-08-27
- [x] 拉取 vpnhide ✅ 2026-08-27

### Phase 1: 核心模型
- [x] RecordTypes.kt — 新增 NETWORK 常量 ✅ 2026-08-27
- [x] RecordType.kt — 新增 NETWORK 枚举值 ✅ 2026-08-27
- [x] NetworkKind.kt — 新建网络类别枚举（15 类:6 SSL + 4 代理 + 4 VPN + 1 请求）✅ 2026-08-27
- [x] NetworkOperation.kt — 新建网络操作枚举（24 种操作）✅ 2026-08-27
- [x] NetworkRecord.kt — 新建网络记录数据模型 ✅ 2026-08-27
- [x] HookSwitch.kt — 新增 networkListen + 16 子开关 + networkEnabled() ✅ 2026-08-27
- [x] CategoryTaxonomy.kt — 新增 15 个网络类别条目 + validateOperations NETWORK 分支 ✅ 2026-08-27
- [x] AppListenOverride.kt — 新增 networkListen 偏差 + VALID_KINDS 扩展 ✅ 2026-08-27
- [x] CapabilityKind.kt — 新增 NETWORK_LISTEN ✅ 2026-08-27

### Phase 2: 数据库 + 编解码
- [x] CryptoRecordEntity.kt — 新增网络列（url/method/statusCode/headers/ssl/proxy/vpn 等 11 列）✅ 2026-08-27
- [x] ModevDatabase.kt — v10→v11 迁移 + 版本号更新 ✅ 2026-08-27
- [x] NetworkRecordCodec.kt — 新建编解码器 ✅ 2026-08-27
- [x] RecordCodecRegistry — 注册网络解码器（LogRepository.cryptoRegistry） ✅ 2026-08-27
- [x] NetworkRecordMappers.kt — 新建映射器（entity/row/detail/toJson） ✅ 2026-08-27
- [x] RowModel.kt — 新增 NetworkRow ✅ 2026-08-27
- [x] RecordDetailModel.kt — 新增 NetworkDetail ✅ 2026-08-27
- [x] CryptoRecordMappers.kt — toRowModelOrNull/toRecordDetailOrNull 新增 NETWORK 分支 ✅ 2026-08-27
- [x] EffectiveSwitch.kt — resolve/readKind/applyKinds 新增网络字段 ✅ 2026-08-27
- [x] HookInstaller.kt — jsonOf 新增 NetworkRecord 分派 ✅ 2026-08-27
- [x] LogRepository.kt — writeBatch 新增 NetworkRecord 分派 ✅ 2026-08-27

### Phase 3: Hook 层
- [x] NetworkBaseHooker.kt — 新建基类（emit/栈 denylist/配置快照） ✅ 2026-08-27
- [x] SSLBypassHooker.kt — SSL Pinning 绕过（6 子域 + 混淆 OkHttp 堆栈指纹发现） ✅ 2026-08-27
- [x] ProxyHideHooker.kt — WiFi 代理隐藏（4 通道:SystemProperty/Connectivity/Settings/OkHttp） ✅ 2026-08-27
- [x] VpnHideHooker.kt — VPN 隐藏（4 向量:Capabilities/Interface/NetworkInfo/LinkProperties） ✅ 2026-08-27
- [x] NetworkRequestHooker.kt — 请求监控（OkHttp newCall + HttpURLConnection） ✅ 2026-08-27
- [x] NetworkProbe.kt — 网络探针（编排 4 个 Hooker） ✅ 2026-08-27
- [x] HookInstaller.kt — 注册 NetworkProbe 到 probes 清单 ✅ 2026-08-27
- [x] RecordSink.kt — jsonOf 分派（已含 NetworkRecord） ✅ 2026-08-27
- [x] EffectiveSwitch.kt — networkEnabled + readKind + applyKinds 扩展 ✅ 2026-08-27

### Phase 4: 宿主侧集成 + UI 集成
- [x] LogRepository.kt — 网络记录写入分派 ✅ 2026-08-27
- [x] RecordService.kt — 网络记录查询接口(RecordType.NETWORK 通路已含) ✅ 2026-08-27
- [x] RecordRow.kt — NetworkRow 分派 + networkPresentation + 网络标签/摘要函数 ✅ 2026-08-27
- [x] RecordDetailScreen.kt — NetworkDetail 分派 + NetworkLoadedContent + 导出分支 ✅ 2026-08-27
- [x] MonitorRecordsScreen.kt — 复制摘要 NetworkRow 分派 ✅ 2026-08-27
- [x] KindIcons.kt — network() 图标映射 ✅ 2026-08-27
- [x] NetworkColorTokens.kt — 类别色 token(SSL=蓝/代理=橙/VPN=紫/请求=青) ✅ 2026-08-27
- [x] DetailAnchors.kt — networkAnchors() ✅ 2026-08-27
- [x] Invocation.kt — setResult() 抽象方法 ✅ 2026-08-27
- [x] LibxposedPlatform.kt — setResult() 拦截器短路实现 ✅ 2026-08-27
- [x] LegacyPlatform.kt — setResult() param.result 覆盖实现 ✅ 2026-08-27
- [x] CryptoRecordDao.kt — ROW_SELECT 新增 proxyHost/vpnActive + CryptoRecordRow 扩展 ✅ 2026-08-27

### Phase 5: i18n
- [x] values/strings.xml — 60+ 英文文案(kind/operation/meta/section/listen) ✅ 2026-08-27
- [x] values-zh/strings.xml — 60+ 中文文案(对齐英文) ✅ 2026-08-27

### Phase 6: 测试
- [x] 单元测试 — 全部 487 通过(含 HookSwitchTest 3 处修复+StringResourceParityTest zh 翻译+Migration v10→v11) ✅ 2026-08-27
- [ ] Robolectric 测试
- [ ] Compose UI 测试

### Phase 7: ADB 压力测试
- [ ] 雷电模拟器环境准备
- [ ] 功能验证
- [ ] 压力测试
- [ ] 失败分析 → 返工 → 重测

---

## 验收标准

| # | 标准 | 状态 |
|---|------|------|
| 1 | SSL 绕过覆盖 OkHttp 2.x/3.x/4.x + 混淆版 | ⬜ |
| 2 | SSL 绕过覆盖 Conscrypt + WebView + NetworkSecurityConfig | ⬜ |
| 3 | 代理隐藏覆盖 4 个检测通道 | ⬜ |
| 4 | VPN 隐藏覆盖 4 个检测向量 | ⬜ |
| 5 | Reqable 堆栈含 Java 调用栈 | ⬜ |
| 6 | i18n 双语齐备 | ✅ |
| 7 | lint 零新增 warning | ⚠️ +8(SSL bypass 反射/trust-all 为预期行为,非回归) |
| 8 | 全部单元测试通过 | ✅ |
| 9 | Robolectric 测试通过 | ⬜ |
| 10 | ADB 压力测试通过 | ⬜ |
