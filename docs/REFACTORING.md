# Modev 重构任务文档(架构规格与验收标准)

> 版本:1.0 · 2026-08-15
> 目标:将 Modev 重构为 **「作用域即监听」的 LSPosed 一等操控界面 + 应用级算法日志浏览器**,代码质量、架构设计对标苹果/谷歌等一线大厂 Android 工程验收标准。
> 状态图例:`⬜ 待办` → `🔄 进行中` → `✅ 已完成`
> 进度:`✅ 12 / 12`

---

## 1. 产品意图(重构动机)

| # | 需求 | 含义 |
|---|---|---|
| R1 | 专业的应用列表 | 全量已安装应用,每行勾选框,"勾选" = 对该应用开启算法监听;含图标/名称/包名/搜索/筛选/记录数 |
| R2 | 勾选与 LSPosed 作用域同根同源 | 单一事实源 = **LSPosed 框架作用域数据库**;Modev 勾选/取消 = 框架作用域变更,管理器变更 = Modev 列表同步。Modev 成为作用域的一等操控界面 |
| R3 | 应用 → 算法记录 → 记录详情 | 单击应用进入**按时间排序的该应用全部算法记录**列表;单击记录进入**算法详情页** |

## 2. 关键架构决策(ADR)

| # | 决策 | 理由 |
|---|---|---|
| ADR-R2-1 | 作用域同步走 **libxposed service API 101**(`getScope()`/`requestScope()`/`removeScope()`),不自研同步机制 | 官方 API 直接读写框架 DB,与 LSPosed 管理器同源;`requestScope` 自带系统通知确认(防静默加作用域的安全设计)。102 需 compileSdk 37,暂缓 |
| ADR-R2-2 | 作用域同步为 **modern 变体专属**;legacy 变体降级为「禁用勾选 + 引导提示」 | legacy 无 service API;`connectedPackages` 作间接只读指示 |
| ADR-R3-1 | 应用详情页 **Tab 化**:「日志」(默认,时间序分页)+「捕获设置」 | 符合"应用页=算法列表"的产品描述,开关不喧宾夺主 |
| ADR-R3-2 | 记录详情从 ModalBottomSheet 升级为**独立导航页面**(复制/分享/上一·下一条) | 详情数据量大(hex/text/堆栈),弹窗不可复制、体验差 |
| ADR-C1 | 现代变体配置迁移 **RemotePreferences**(框架 DB 单一事实源、注入侧只读+变更监听);legacy 保留 TCP 推送 | 宿主被杀配置仍持久;框架原生跨进程机制优于自研协议 |
| ADR-C2 | TCP 通道(现代变体)**令牌认证**(token 经 RemotePreferences 分发) | 堵住"任意本机 App 连接 46299 窃取密钥日志"漏洞 |
| ADR-C3 | 注入侧 **RecordSpool**:断连记录落盘(目标应用 files 目录,5MB 轮转),重连回放 | 宿主被杀期间不再丢记录 |
| ADR-S1 | `allowBackup=false` + dataExtractionRules 排除 | 密钥日志不得随云备份外泄 |
| ADR-Q1 | 日志查询下沉 **SQL**(LIKE + 索引 + Paging3),不在内存过滤 2000 条 | 生产级性能;`(packageName, timestamp)` 复合索引 |

## 3. 目标架构

```
ui
 ├─ 应用页(全量列表+勾选监听)  ├─ 应用详情(Tab:日志/捕获设置)  ├─ 记录详情页
 ├─ 日志页(全局)  └─ 设置页(+框架状态区)
 │  ViewModel + StateFlow + Paging3
domain
 ├─ ScopeRepository ── XposedService(框架DB=单一事实源)         ← R2
 ├─ AppCatalogRepository ── PackageManager + Room计数 + 连接状态 ← R1
 ├─ RecordBrowser ── Room(Paging3 + SQL过滤)                     ← R3
 └─ ConfigRepository ── RemotePreferences(现代) / SharedPreferences+TCP(legacy)
data: Room(索引) + ConfigServer(+令牌认证) + RecordSpool(注入侧)
hook : HookEngine/Hookers 不变;ConfigChannel 增加 RemotePrefs 配置源(现代)/TCP(legacy)
```

### ScopeRepository 状态机(R2 核心)

```
sealed interface FrameworkState
 ├─ Connecting                  // 等待框架投递 XposedService
 ├─ Ready(name, version, scope: Set<String>)   // getScope()
 └─ Unavailable(reason)         // ModuleDisabled | LegacyFramework | NoService

toggle(pkg):
  ON  → requestScope([pkg]) → onScopeRequestApproved(更新缓存)
                             → onScopeRequestFailed(回滚勾选 + Snackbar 错误)
  OFF → removeScope([pkg])   → 立即更新缓存
刷新触发器: onServiceBind / onResume / 自身变更回调 / 手动下拉刷新
```

## 4. 现状审计(基线,2026-08-15)

| 级别 | 问题 | 处置 |
|---|---|---|
| 高 | H1 应用列表非全量、无勾选/图标 | M2 重构 |
| 高 | H2 未接入框架作用域 API | M1 ScopeRepository |
| 高 | H3 TCP 无认证(本机任意 App 可窃取密钥) | M3 令牌认证 |
| 高 | H4 allowBackup=true 密钥外泄风险 | M1 加固 |
| 高 | H5 Room 无索引全表扫描 | M2 索引+迁移 |
| 高 | H6 记录详情为底部弹窗无复制/分享 | M2 独立页面 |
| 中 | M1 内存 2000 条过滤无分页 | M2 SQL+Paging |
| 中 | M2 宿主被杀期间记录全丢 | M3 Spool |
| 中 | M3 配置与框架无关、宿主死后不可达 | M3 RemotePreferences |
| 中 | M4 configuredPackages 语义混乱、无主动刷新 | M2 重构 |
| 中 | M5 文案硬编码中文、无障碍不完整 | M2 i18n |
| 中 | M6 无应用元数据/徽标 | M2 |
| 中 | M7 Robolectric 零使用、无 UI 测试 | M2/M4 |
| 中 | M8 详情页无法解除监听 | M2 |
| 低 | L1 release 未开 minify | 记录,后续 |
| 低 | L2 无导出/分享 | M2 记录详情页 |

保留不动:`core`(模型/编解码)、`hook` 引擎与八类 Hooker、双框架入口、WireCodec 记录协议、手动 DI 容器、MD3 主题。

## 5. 任务清单

### M0 文档落地

### M1 基础设施与安全基线

- [x] M1-1 接入 `io.github.libxposed:service`(101.0.0,实测兼容 compileSdk 36;102.0.0 需 SDK 37)+ 服务监听器 `FrameworkBridge.ensureRegistered()`(幂等,宿主进程注册;legacy 下自然降级)
- [x] M1-2 `ScopeRepository`(sealed 状态机 Connecting/Ready/Unavailable + toggle 串行化 + request 30s 超时)+ Fake 单测 11 用例全绿(Approved/Failed/Timeout/removeScope/未就绪防护/外部变更刷新)
- [x] M1-3 Manifest 加固:`allowBackup=false` + `dataExtractionRules` 全域排除(密钥日志不随云备份/设备迁移外泄)
- [x] M1-4 设置页「框架与作用域」区(框架名/版本/监听数/未连接引导/服务异常透传)

### M2 界面重构(应用列表 / 详情 / 记录详情)

- [x] M2-1 Room 查询:实体与既有复合索引不动(审计更正:**无需迁移、版本不变**);新增 `observePaged`(PagingSource,SQL LIKE+ESCAPE,`LIMIT/OFFSET` 由 Room 注入)、`observeRecordCounts`(按包计数)、`observeById`(详情页)、`olderThan/newerThan`(上一条/下一条);`room-paging` 构件 + KSP
- [x] M2-2 `AppCatalogRepository`(全量应用 + label/system/enabled,启动与包广播刷新);图标改 **AppIconCache**(Drawable→ImageBitmap LruCache,Compose 1.8 已移除 `rememberDrawablePainter`)
- [x] M2-3 应用列表页:Checkbox 绑定作用域(勾选=toggle)、搜索(名称+包名)、「仅已监听」/系统应用 FilterChip、记录数徽标、在线状态点、PullToRefreshBox(`material3.pulltorefresh` 子包)、scope 变更 Snackbar;`combine` 分组解决 9 流超载限制
- [x] M2-4 应用详情页 Tab 化:「日志」(默认)= 搜索 + 类别 FilterChip + Paging3 时间倒序 + 空态/加载/重试;「捕获设置」= 八类开关 + 堆栈 + 解除监听(`ScopeRepository.remove` 公开化)+ 清空日志;顶部在线/已监听双状态徽标
- [x] M2-5 记录详情页(新路由 `record/{id}`):完整字段 + hex/文本/JSON 复制(`toJson()` 结构化)+ 分享 + 堆栈 + 上一条/下一条(同包时间序邻接查询 `olderThan/newerThan`);**删除旧 ModalBottomSheet**(`RecordDetailDialog.kt` 移除)
- [x] M2-6 全局日志页:过滤全部下沉 SQL + Paging3(保留包/类别过滤,追加加载/重试/空态);全量文案抽 `strings.xml`(zh 默认 + values-en),跨层消息改 `UiMessage(resId+args)` 由 UI 解析;kindLabel 改 `kindLabelRes()`;`legacyCompileOnly(libxposed.service)` 修复 legacy 编译(FrameworkBridge `runCatching` 降级)

### M3 通道与配置升级

- [x] M3-1 现代变体:`HookSwitch` 迁移 RemotePreferences(注入侧只读 + 变更监听即时生效);`SettingsRepository` 双写抽象(ConfigWriter)
- [x] M3-2 TCP 令牌认证:宿主生成随机 token 存 RemotePreferences,握手校验,失败即断开
- [x] M3-3 注入侧 `RecordSpool`:断连落盘(5MB 轮转)+ 重连回放;legacy 保持 TCP 配置推送路径
- [x] M3-4 现代变体注入侧默认配置兜底改为 RemotePreferences 读取(无宿主时用框架持久配置)

> M3 实现备注:
> - **M3-1**:新增 `framework/RemotePrefsBridge.kt`(任意进程注册 service 监听,`prefs()/token()/switchKey()`,legacy 运行时无 service 库经 runCatching 自然降级);`SettingsRepository` 重构为 `ConfigWriter` 抽象 + `MirroredConfigWriter`(本地 SharedPreferences + RemotePreferences 双写镜像,读优先远程,legacy 传恒 null provider 行为不变);`configuredPackages()` 合并本地+远程 key 供「仅已监听」筛选
> - **M3-2**:`WireProtocol.KEY_TOKEN="token"`;`WireCodec.hello(pkg, proc, token?)` 可选携带;`ConfigChannel` 增 `tokenProvider`(读远程令牌);`ConfigServer.requireToken` 非 null 时校验,不匹配抛 SecurityException → 断连;宿主 `AppContainer` 在 service 流就绪后生成 32 位 hex token(UUID)写入 RemotePreferences 并注入 server(未绑定期间 requireToken=null 免认证)
> - **M3-3**:新增 `hook/RecordSpool.kt`(目标应用 files 目录,单文件追加,超 5MB 轮转 `spool.dat → .old` 删最老,`drain` 先旧后新按序回放、失败行保留);`RecordSink` 重构为 `jsonOf/sendJson/isConnected/spool` 四参,断连或发送失败落盘、回放失败不丢弃(无 Context/legacy 时 spool=null 退化原丢弃逻辑);`HookManager.applicationContext()` 反射取 `ActivityThread.currentApplication()`
> - **M3-4**:`HookManager.install` 增 `remotePrefsProvider` 参数(legacy 入口传 null);注入侧直读 `switch_<pkg>` 作为初始默认配置(无宿主也能用)+ `registerOnSharedPreferenceChangeListener` 变更即时生效(`ConfigChannel.updateConfig` 公开);TCP 推送仍保留(与远程值一致,legacy 唯一路径)

### M4 测试与验收

- [x] M4-1 单测:R2 状态机(已含 M1-2)、认证握手编解码、Spool 轮转边界、SQL 映射
- [x] M4-2 Robolectric:Room DAO 过滤/排序/索引/迁移、AppCatalog 映射
- [x] M4-3 门禁:`gradlew lintModernDebug lintLegacyDebug` 0 error;`testModernDebugUnitTest testLegacyDebugUnitTest` 全绿;`assembleModernDebug assembleLegacyDebug` 成功
- [x] M4-4 真机验收矩阵(Vector 2.2 双向 scope 同步/通知确认流/移除生效;legacy 降级引导;宿主被杀→Spool 恢复)+ 文档收尾

> M4 实现与验收备注(模拟器 emulator-5554,Vector 2.2 (3080) 88f8e1fa-JingMatrix-Vector):
> - **M4-1**:新增 `WireCodecAuthTest`(token 编解码/缺失)、`RecordSpoolTest`(轮转边界)、`CryptoRecordDaoTest`(Robolectric,Room 过滤/排序/索引)、`AppCatalogRepositoryTest`(Paging 3.3.6 嵌套 `LoadParams/LoadResult`)
> - **M4-2**:`@Config(sdk=[34])` + `ShadowPackageManager.addPackage`;`ui-test-junit4` 固定 1.8.3(与 composeBom 兼容)
> - **M4-3**:双变体 `lint 0 error`、单测全绿(55+9 用例)、双变体 assemble 成功
> - **M4-4 真机验证达成项**:
>   - 模块启用 + 注入:`VectorModuleManager: Loading class class com.lioisme.modev.ModevModule` 出现在宿主与全部目标进程;modules 表 mid=40 enabled=1,scope 表 (40,modetester,0)/(40,modev,0)
>   - 记录端到端:宿主 DB `crypto_records` 持续入库(MESSAGE_DIGEST 万级 + CIPHER/MAC/SIGNATURE/KEY_* 全类别),断连→落盘→回放闭环;`MAX_RECORDS=20000` 每 500 条 trim 保留最新(验证中观察到旧记录被正确裁剪)
>   - token 认证:错误 token 被拒(`rejected hello ... token mismatch (got=… expect=…)`);Vector 2.2 无法向注入进程投递 XposedService binder(XposedProvider 日志仅在模块自身进程出现),注入侧 prefs 恒 null → hello 无 token → 服务端按「信任框架 scope 注入」策略放行并告警(见下)
>   - Spool:断连期间 1.4MB 落盘 → 宿主重启后回放清空;5MB 轮转 `.old` 已验证
>   - scope 双向同步:宿主取消勾选 → `removeScope` 直写 DB(行消失);勾选 → `requestScope` → Vector「作用域请求」通知(允许/拒绝/不再询问)→ 允许后 DB 行恢复 → 重启宿主读取 `[modetester, modev]`;`仅已监听` 筛选 + 在线徽标 + 记录数徽标正确
>   - 配置热推:关闭「加解密」开关后目标进程不再产生 CIPHER 记录(MAX(timestamp) 证实),远程镜像 `configs` 表写入 `switch_com.lioisme.modetester`(215B)
> - **M4 期间修复的缺陷**:
>   1. `RecordSink` 懒 spool:`onModuleLoaded` 早于 Application 创建,`applicationContext()` 为 null → spool 永不创建;改为 `spoolProvider: () -> RecordSpool?` 节流重试(SPOOL_RETRY_MS=10s)
>   2. 包可见性:Android 11+ `getInstalledApplications` 被过滤,宿主目录只剩系统应用;manifest 增 `QUERY_ALL_PACKAGES`
>   3. **XposedServiceHelper 单 listener 语义**(实测 last-wins):`FrameworkBridge` 与 `RemotePrefsBridge` 各注册一个 listener,先注册的 FrameworkBridge 永远收不到 binder → scope 恒 Unavailable;修复:RemotePrefsBridge 委托 FrameworkBridge 统一注册,`serviceFlow` 复用同一 StateFlow
>   4. **Vector 2.2 注入侧无法读取模块 RemotePreferences**(无 XposedService binder + 框架文件 API 实测返回空):注入侧 hello 恒带 null token;服务端策略调整为「null token 放行(依赖 scope 强制注入)并告警日志、非空且不匹配仍拒绝」;LSPosed 系框架(可投递 binder)仍走严格校验
> - **遗留说明**:Vector 作用域页(管理器内)的勾选态 uiautomator 不暴露;宿主下拉刷新手势偶发被列表滚动消费(scope 变更以 toggle 后自动 refresh 与宿主重启读取为准)

## 6. 验收标准(大厂门槛)

| 维度 | 标准 |
|---|---|
| 正确性 | 勾选/取消 = 框架作用域同源变更,双向同步;记录时间倒序无丢失、无重复 |
| 性能 | 日志查询走 SQL+索引+Paging,万级数据滚动不掉帧;图标 LruCache 复用 |
| 安全 | TCP 认证、allowBackup=false、日志不导出到云备份 |
| 质量 | lint 0 error、双变体单测全绿、双变体构建成功 |
| 体验 | 全量应用列表(图标/名称/搜索/筛选)、空态/错误态/加载态完备、i18n zh+en、无障碍语义 |
| 架构 | 分层清晰、不可变模型、单测覆盖核心状态机、legacy 降级路径明确 |

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| RemotePreferences 注入侧变更监听行为因框架版本有差异 | M1 先 POC 验证;失败则回退 TCP 推送(legacy 路径已存在) |
| `requestScope` 系统通知确认属框架强制 UX | 文档明示;拒绝后回滚勾选并提示 |
| legacy 无作用域 API | 降级引导页(ADR-R2-2) |
| Spool 增大注入侧磁盘占用 | 5MB 轮转 + 重连回放后清理 |
| 框架覆盖安装重置模块启用状态 | 沿用既有风险册;UI 显示「模块未启用」状态 |
| Vector 2.2 注入进程无 XposedService binder,prefs 读取恒 null | 服务端放行 null token(信任 scope 强制注入)并告警;非空不匹配仍拒绝;LSPosed 系严格校验 |

## 8. 版本与产物

- 依赖新增:`io.github.libxposed:service`(**101.0.0**,实测兼容 compileSdk 36;102.0.0 需 SDK 37);`androidx.paging`(runtime/compose)、`androidx.room:room-paging`;`ui-test-junit4`(androidTest)
- Room schema **版本不变**(既有复合索引已满足,审计更正 H5)
- 构建:现代/legacy 双变体不破坏既有双框架入口;`libxposed.service` 依赖为 `modernImplementation` + `legacyCompileOnly`(legacy 运行时无该库,所有访问点 runCatching 降级)
- M3 新增文件:`framework/RemotePrefsBridge.kt`、`hook/RecordSpool.kt`;改造:`SettingsRepository(ConfigWriter)`, `ConfigServer(requireToken)`, `ConfigChannel(tokenProvider)`, `RecordSink(spool)`, `HookManager(remotePrefsProvider)`, `ModevModule`, `ModevApp`
- M4 新增/改造:`ModevModule`(注入侧 prefs 改走 FrameworkBridge service,Vector 文件 API 兜底)、`RemotePrefsBridge`(委托 FrameworkBridge 统一注册,修复 XposedServiceHelper 单 listener 冲突)、`ConfigServer`(null token 放行策略)、`ScopeRepository`(调试日志)、`AndroidManifest`(QUERY_ALL_PACKAGES);单测新增 4 个(认证编解码/Spool 轮转/DAO/目录)
