# Modev 开发任务文档

> 名称:**Modev** · 包名:`com.lioisme.modev`
> 类型:基于 **LSPosed(libxposed API 102)** 的 Xposed 模块,捕获目标 App 的全部算法调用(摘要/加解密/签名/MAC/密钥/编码),配套 MD3 宿主应用。
> 质量标准:架构分层、空安全、不可变模型、单元测试、lint 清零、文档齐全 —— 对标一线大厂 Android 工程验收标准。
> 状态图例:`⬜ 待办` → `🔄 进行中` → `✅ 已完成`

---

## 0. 技术基线(调研结论,2026-08)

| 项 | 结论 |
|---|---|
| 运行框架 | **LSPosed v2.1.0+**(libxposed API 102,要求 root + Magisk/Zygisk;支持 Android 8.1 ~ 17 Beta)。免 root 场景用 LSPatch/NPatch(重打包,签名校验受限),作为分发备选 |
| Hook API | `io.github.libxposed:api:102.0.0`(compileOnly),`XposedModule` 入口 + `XposedInterface.hook(Executable)` 拦截器链;API 102 禁止混用 legacy `de.robv.android.xposed` API |
| 捕获原理 | Hook JCA/JCE 门面类(`javax.crypto.Cipher`、`java.security.MessageDigest`、`javax.crypto.Mac`、`java.security.Signature`、KeySpec 构造器、KeyGenerator/KeyFactory、`android.util.Base64`),以对象实例为上下文把 `getInstance→init→update→doFinal` 关联成完整记录,附调用堆栈 |
| 配置/日志链路 | 宿主 App ↔ 注入进程通过 **loopback TCP(双工)** 传输 JSON 行协议;App 侧 Room 持久化;注入侧兜底写 Xposed log |
| 构建 | AGP 8.13 + Gradle 8.13(wrapper)+ Kotlin 2.2 + Compose(BOM)+ material3 + Room + Navigation-Compose;compileSdk 36 / minSdk 26 / targetSdk 36 |
| 已知边界 | ① native 层自研算法不可捕获(二期做 libcrypto EVP inline hook);② AndroidKeyStore 硬件密钥材料不可导出,只记录 alias+明密文;③ 反 Xposed 检测 App 需 Shamiko/排除列表;④ 宿主 App 被杀期间注入侧使用内存中最后配置 |

---

## 1. 任务清单

### T1 环境与 API 调研 ✅ 已完成
**备注**:确认本机 JDK 21 + Gradle 9.5.1 + Android SDK(platforms 34/35/36, build-tools 36.0.0);下载 `io.github.libxposed:api:102.0.0-sources.jar` 并逐文件核验 API 签名:
- 入口类 `XposedModule` 无参构造,继承 `XposedInterfaceWrapper implements XposedModuleInterface`;
- 生命周期:`onModuleLoaded(ModuleLoadedParam)` / `onPackageLoaded` / `onPackageReady` / `onHotReloading/ed`(102 热重载);
- Hook 链:`xposedInterface.hook(Executable) → HookBuilder(setPriority/setExceptionMode/setId) → intercept(Hooker)`;`Hooker.intercept(Chain)` 中 `chain.getThisObject()/getArgs()/proceed()/proceedWith()`;`ExceptionMode.PROTECTIVE` 兜底;
- 配置:`getRemotePreferences(group)` 在注入进程只读 → 决定自研双工 TCP 配置同步;
- `module.prop` 必填 `minApiVersion/targetApiVersion`;入口注册 `META-INF/xposed/java_init.list`。

### T2 开发文档落地 ✅ 已完成
**备注**:本文件(任务清单/验收标准/状态跟踪)+ 调研结论固化;后续每完成一项任务即更新状态与本备注栏。

### T3 项目骨架与构建脚本 ✅ 已完成
**验收**:`gradle wrapper --gradle-version 8.13` 成功;`settings.gradle.kts`(仓库/插件管理/`libs.versions.toml`);`gradle.properties`(AndroidX/JVM 参数);根 `build.gradle.kts`;`app/build.gradle.kts`(AGP 8.13 + Kotlin 2.2 + Compose + Room + compileOnly libxposed);`local.properties`(SDK 路径);资源目录 `src/main/resources/META-INF/xposed/{java_init.list,module.prop}`;AndroidManifest(应用名/图标/INTERNET 权限)。
**备注**:wrapper 8.13 生成成功;依赖选型 AGP 8.13.2 / Kotlin 2.2.20 / KSP 2.2.20-2.0.2 / Compose BOM 2025.06.01 / Room 2.7.2 / Gson 2.13.2 / libxposed-api 102.0.0(compileOnly);`module.prop` 已按官方示例置 `minApiVersion=102 targetApiVersion=102 exceptionMode=protective autoHotReload=false`;`proguard-rules.pro` 已按 libxposed README 加入 `-keep extends XposedModule` 等三条规则。

### T4 core 数据模型与工具(纯 Kotlin,可单测)✅ 已完成
**验收**:
- `CryptoKind`(MESSAGE_DIGEST/CIPHER/MAC/SIGNATURE/KEY/KEY_PARAM/KEY_AGREEMENT/BASE64)、`CryptoOperation`、不可变 `CryptoRecord`(含 key/iv/input/output 的 hex+text、方向、上下文 id、线程名、堆栈、时间戳);
- `Hex`(字节↔hex)、`BytesView`(hex+text 双态、超限截断、可打印判定)、`StackTraceFilter`(剔除模块自身/框架帧、限深)、`HookSwitch` 配置模型 + JSON 编解码(协议 v1)、`RecordCodec`(JSON 行协议)。
- 覆盖单测:hex 往返、截断边界、可打印判定、堆栈过滤、配置编解码、协议码编解码。
**备注**:最终模型:`CryptoKind` 8 类(去掉了 KEY_AGREEMENT,新增 KEY_PARAM/KEY_GENERATION/KEY_FACTORY/BASE64);`WireProtocol`(PORT=46299、MAGIC=MDEV、VERSION=1、类型 hello/config/record/ping/pong、顶层字段 t/p)+ `WireCodec`(Gson JSON 行);`BytesView` 双态视图含截断与可打印判定(TextUtil 自实现 UTF-8 校验);`StackTraceFilter` 前缀剔除(模块/框架/反射/被 Hook 门面类)。单测已由 T9 补齐并通过(`HexTest/BytesViewTest/StackTraceFilterTest/WireCodecTest/WireCodecAuthTest/HookSwitchTest`,后续扩展至全量 69 例单元 + 15 例插桩全绿)。

### T5 hook 引擎 ✅ 已完成
**验收**:
- `ModevModule`(XposedModule 入口):`onModuleLoaded` 内初始化引擎,按目标进程包名过滤(`param.getProcessName()`),注册/反注册;
- `MethodFinder`:按类名+方法名+参数类型反射定位并缓存,找不到时静默降级(PROTECTIVE);
- `HookEngine`:`hook(method) → HookBuilder.intercept(Hooker)` 统一封装,`ExceptionMode.PROTECTIVE` + 全部业务逻辑 try/catch;
- `CryptoContextTracker`:以对象实例 identityHashCode 为键关联上下文(算法/方向/key/IV/已输入字节),弱引用防泄漏;
- `ConfigChannel`(注入侧):TCP 客户端,连接宿主 App 固定端口,握手携带包名,接收配置推送;未连接时用默认配置;断线重连;
- `RecordSink`:内存有界队列(丢弃策略+计数)+ 异步写出(JSON 行)+ 兜底 `xposedInterface.log`。
**备注**:`ModevModule` 采用官方示例写法(无 `@AfterInvocation`,该注解不在 API 102 的 aar/annotation 1.0.0 中);`HookManager` 单例组装器(每进程一次);`MethodFinder` 增补 `findCtor`(构造器 hook 用);`ConfigChannel` 断线指数退避 2s→30s,握手带 magic/version/包名/进程名;`RecordSink` 用 `LinkedBlockingQueue(2048)` 满则丢最旧。**关键修正**:核验源码发现 `Chain` 接口**无 `getResult()`**(那是 legacy API 的概念),结果一律从 `chain.proceed()` 返回值取得,全部 Hooker 已按此重构。

### T6 八类算法 Hooker ✅ 已完成
**验收**:覆盖——① `Cipher`(getInstance×3/init×5/update×3/doFinal×3/getIV);② `MessageDigest`(getInstance/update/digest);③ `Mac`(getInstance/init/update/doFinal);④ `Signature`(getInstance×3/initSign/initVerify/update/sign/verify);⑤ `SecretKeySpec`/`IvParameterSpec`/`GCMParameterSpec`/`PBEKeySpec` 构造器;⑥ `KeyGenerator`/`KeyPairGenerator`;⑦ `KeyFactory`/`SecretKeyFactory`;⑧ `android.util.Base64`。每条记录关联上下文并附堆栈;key 获取失败(KeyStore)时标记不可导出;每类 Hooker 独立开关。
**备注**:8 个 Hooker + `BaseHooker`(统一 `enabled()`/`emit()`/堆栈采集)+ `ChainExt.kt`(越界安全 `getArgOrNull`);`CipherHooker.extractKey/extractIv` 供 Mac/Signature 复用;`init` 在 proceed **前**采集(参数形态安全),`update/doFinal/sign/verify` 在 proceed **后**采集;`Base64Hooker` 开关默认关;全部经 `HookEngine` PROTECTIVE 注册,单个失败不影响其余。

### T7 UI 侧数据层 ✅ 已完成
**验收**:Room(`CryptoRecordEntity`/Dao/DB,按包名索引、异步 IO);`ConfigServer`(ServerSocket 固定端口,JSON 行协议,客户端表+配置广播,断连清理);`SettingsRepository`(按包名的开关集,StateFlow);`LogRepository`(接收→入库→StateFlow 刷新,上限清理策略);`ModevApp`(Application,手动 DI 容器,单例,后台线程生命周期管理)。
**备注**:`ConfigServer` 仅绑定 127.0.0.1:46299,握手登记包名,`configProvider` 在握手后回放持久化配置;`onRecord` 可变字段供 LogRepository 接线;`SettingsRepository` 双写(SharedPreferences+StateFlow)并即时广播;`LogRepository` 经 Mutex 串行写库、每 500 条 trim 至 2 万条上限、实体↔模型双向映射(枚举名/堆栈 JSON/视图列展开);`ModevApp`+`AppContainer` 手动 DI。

### T8 MD3 UI ✅ 已完成
**验收**:Material3 主题(dynamic color+深浅色);底部导航三页——应用列表(搜索/系统应用开关/状态角标)→ 目标详情(八类功能开关/连接状态/清空日志)→ 日志页(按包过滤/分类 Chip/关键字搜索/记录详情含堆栈);设置页(截断阈值/连接端口/导出日志);Navigation-Compose 路由;ViewModel+StateFlow 状态管理;edge-to-edge。
**备注**:`ModevTheme`(dynamic color + MD3 明暗色);`ModevNav` 底部导航(应用/日志/设置)+ `apps/{packageName}` 详情路由;`AppsScreen` 搜索+应用卡片(已连接/已配置状态点);`AppDetailScreen` 八类算法开关+堆栈开关+连接状态+清空;`LogsScreen` 包过滤下拉(ExposedDropdownMenuBox)+分类 FilterChip+搜索+`RecordDetailDialog`(ModalBottomSheet 元数据/数据段/堆栈);`SettingsViewModel.kt` 初版误留 `ui.logs` 包名,已在编译调试中修正。

### T9 单元测试 ✅ 已完成
**验收**:core 全部纯逻辑单测(JUnit4,无设备依赖)在 `gradlew testDebugUnitTest` 通过。
**备注**:29 个用例全绿:Hex(4,含范围校验)、BytesView/TextUtil(9,双态视图/截断/UTF-8 可打印性)、StackTraceFilter(4,前缀过滤/深度)、WireCodec(7,hello/config/record 往返+非法输入)、HookSwitch(5,默认值/映射)。**发现并修复真实缺陷**:`WireCodec.recordOf` 对结构不符的 payload 会反序列化出全 null 空记录 → 已加 packageName/kind/operation 非空校验。

### T10 构建验证与收尾 ✅ 已完成
**验收**:`gradlew assembleDebug` 成功;`gradlew lintDebug` 无 error;`gradlew testDebugUnitTest` 全绿;README(使用/安装/作用域/限制);文档任务状态收尾。
**备注**:`compileDebugKotlin` 曾 55 个错误,已全部修复。踩坑清单:① `Chain` 无 `getResult()`,全部 Hooker 的 after 回调统一为 `(Chain, Any?)`,getInstance 等单参场景补单参重载;② `ModuleLoadedParam` 是 `XposedModuleInterface` 嵌套接口,Kotlin 需显式 import;③ `HookEngine.hook` 参数 `Method`→`Executable`,否则 `KeyParamHooker` 传构造器类型不匹配;④ `SettingsViewModel.kt` 初版误留 `ui.logs` 包名;⑤ 编辑 `ConfigServer` 时误删广播函数体、重复 `lastConfig` 声明,均已修复;⑥ Gradle 错误行默认截断至 ~119 字符,`--console=plain -q` 输出完整错误。`assembleDebug` 产出 `app-debug.apk`(17.8MB);`lintDebug` 无 error;`testDebugUnitTest` 29/29。根目录临时日志文件已清理。

### T11 真机实测(LSPosed 1.10.2 → Vector 2.2 升级 + 全功能验证)✅ 已完成
**验收**:在本机 x64 安卓虚拟机(Magisk v27.2-kitsune-4 + root 可用)上,以 `com.lioisme.modetester` 测试 APK(独立工程,`C:\Users\liois\AppData\Local\Temp\opencode\modev-tester\`)自动触发八类算法,配合宿主 UI 逐项验证捕获与配置链路。
**备注**:
- **框架升级**:原 LSPosed 1.10.2(7182)为 **API 93 加载器**(`lspd.dex` 无 `attachFramework`),提示模块版本过低。按用户要求升级至 **Vector v2.2 (3080)**(JingMatrix/Vector,libxposed API 102):下载 release zip → `magisk --install-module` 升级(自动禁用旧 zygisk_lsposed)→ 安装新 manager `org.matrix.vector.manager`、卸载旧 `org.lsposed.manager` → 重启。`/data/adb/lspd/config/modules_config.db` 配置兼容保留(modev enabled=1,scope=tester+modev)。曾把模块降级为 93 双协议(构造器 `(XposedInterface, ModuleLoadedParam)` + legacy 字段)以兼容,框架升级后**恢复纯 API 102 写法**(`minApiVersion=102`)。
- **修复 hook 崩溃(3 个真实缺陷)**:
  ① **静态 getInstance 用 `chain.getThisObject()` 抛异常** → Vector 日志 `Hooker crashed after proceed`,拦截器被吞。MessageDigest/Mac/Signature/KeyFactory/KeyGeneration 的 `afterGetInstance` 全部改为**用返回值 `result` 作 context 键**;Cipher 的 getInstance 由 `hookAfter` 改为专用 `hookInstanceOf(chain, result)`。
  ② **Base64(静态方法)同病**:`contextId` 改用固定 `STATIC_CTX` 占位对象;KeyParam(构造器)改用 `chain.proceed()` 返回值作 contextId。
  ③ **tester 无 INTERNET 权限 → `socket failed: EPERM`**:`ConfigChannel` 连不上宿主(之前 93 轮的 ESTABLISHED 实为宿主自连)。补 `android.permission.INTERNET`;tester 自身 `TextView` 跨线程更新导致中途 FAILED,补 `runOnUiThread`。
- **修复记录丢失**:宿主 `AppContainer.logs` 为 lazy,默认页(应用列表)不访问 → `onRecord` 未接线,服务器把记录丢给空回调。改为 `AppContainer.init { logs }`,**进程启动即接线并启动服务器**(不再依赖 UI 访问)。
- **实测结果**:八类全部捕获入库(逐字段核对明文/密文/key/IV/hex/堆栈);开关切换即时热推送到注入进程(关摘要后 MESSAGE_DIGEST 计数不再增长,其余类别继续);配置持久化(重启后开关状态保持);UI 三页+详情弹窗+调用堆栈(含源码行 `MainActivity.kt:156`)全部正常;无 `hook FAILED`/`crashed after proceed`/FATAL。5k+ 条 SHA-256 源自 PBKDF2WithHmacSHA256 1000 次迭代与签名内部摘要(真实行为,非缺陷)。
- **遗留调试日志**:`HookEngine.hook` 失败、`ConfigChannel` 连接失败、`ConfigServer` 丢弃记录三条 W 级日志保留,便于排障;`emit` V 级与 `hook registered` I 级日志已移除。

### T12 双框架兼容(现代 Vector + legacy LSPosed)✅ 已完成
**验收**:同一 APK 同时支持 Vector(libxposed API 102,`java_init.list` 现代入口)与旧版 LSPosed/EdXposed(legacy API 82+,`assets/xposed_init` legacy 入口);两入口共用中性 Hook 引擎,行为零漂移。设计文档:`docs/DUAL_FRAMEWORK_SUPPORT.md`。
**备注**:
- **加载语义**:LSPosed 加载器 `PreLoadedApk.loadModule` 对 `META-INF/xposed/java_init.list` 存在与否互斥分流(有→现代模块,忽略 xposed_init;无→legacy 模块);Vector 双支持。故双入口天然互斥,不会重复注入。
- **中性抽象**:`hook/Invocation.kt`(越界安全 getArg)、`hook/Platform.kt`(register/log)、`adapters/LibxposedPlatform.kt`(PROTECTIVE + intercept 内 before→proceed→after)、`adapters/LegacyPlatform.kt`(XC_MethodHook 映射,hasThrowable 跳过 after,按签名精确注册);HookEngine/HookManager/八类 Hooker/ConfigChannel 零框架 import。
- **依赖**:`compileOnly de.robv.android.xposed:api:82`(仓库 `api.xposed.info`,白名单 group),与 libxposed 102 并存无冲突;APK 不含任何框架类。
- **清单**:Manifest `xposedminversion=82` + `xposeddescription`(旧管理器识别必需);`module.prop` 保持 `minApiVersion=102` 不变;proguard 双入口 keep + 双框架 `-dontwarn`。
- **对齐决策**:ADR-1(module.prop 102 不变)、ADR-2(xposedminversion=82)、ADR-3(两入口均以 processName 作包标识)、ADR-4(after 在 throwable 时跳过)、ADR-5(日志统一走 Platform.log);`StackTraceFilter` 增补 `de.robv.android.xposed` 前缀,双框架堆栈过滤一致。
- **验证**:`testDebugUnitTest` 32 用例全绿(新增 InvocationTest 3 用例)、`lintDebug` 0 error、`assembleDebug` 成功;x86_64 APK 展开核验 `java_init.list`/`module.prop`/`assets/xposed_init` 三入口文件在位且无框架类打包。

### T13 双框架实机验证(2026-08-15,x64 安卓 VM,Android 14)✅ 已完成
**验收**:`docs/DUAL_FRAMEWORK_SUPPORT.md` §8 矩阵 T-01~T-07 双场景实测通过;实测发现回填 ADR-6 与风险册。
**备注**:
- **场景 A(Vector 2.2 现代,modern 变体)**:`VectorModuleManager: Loading class ModevModule`;八类捕获入库(摘要 5008 条/轮,七类分布 10/3/1/13/3/5008/12,BASE64 默认关);热推送(关摘要后计数停 5008、其余翻倍);重启持久化;无 crashed after proceed / hook FAILED / FATAL。
- **场景 B 适配**:真 legacy(1.8.6/EdXposed)不支持 Android 14;LSPosed 1.10.2 实测为 API 100 现代加载器,存在 `java_init.list` 时不回落 `xposed_init`(API 102 模块 `NoSuchMethodException` 后整体不注入)→ 按用户决策新增 **legacy 构建变体**(flavor 去掉 java_init.list、`minApiVersion=82`),在 LSPosed 1.10.2 legacy 路径实测全通(`Loading legacy module … LegacyModuleEntry`,八类捕获/热推送/持久化均与 A 一致);**交叉验证**:legacy 变体在 Vector 2.2 legacy 路径亦加载成功并完整捕获。
- **其他实测发现**:① 框架覆盖安装会重置模块 `enabled=0` 并级联删 scope(用 sqlite3 修复复验,已写入风险册与发布检查单);② Android 14 冻结后台宿主导致 46299 读超时丢记录,测试需保持宿主前台;③ 注入侧启动期 connect 重试为已知良性行为。
- **产物**:双变体构建命令与测试命令已更新 README;`testModern/LegacyDebugUnitTest` 32 用例全绿、`lintModern/LegacyDebug` 0 error。

### T14 V7 五需求重构(2026-08-17)✅ 已完成
**任务书**:`docs/v7-five-req-refactor-task.md`(需求条款/决策/违规清单/变更清单全量勾选)。
**备注**:
- **R1 详情页 AppBar 规范化**:顶栏收敛为返回/标题/上一条/下一条/更多(MoreVert);**更多菜单唯一功能 = 导出 JSON**(SAF `ACTION_CREATE_DOCUMENT`、文件名 `modev-record-{id}.json`、Default 后台构建 + IO 写流、成功/失败 snackbar);复制/分享全链删除(copyQuick/copyBuilt/share/toShareText/shareIntent/SHARE_DIRECT_MAX_CHARS/SHARE_DIR/copyToClipboard)+ FileProvider 清理;`toJson/toJsonObject` 提级 internal 供导出复用。
- **R2 应用行重构**:Checkbox 移行尾;删除 PlayArrow 运行按钮与 onLaunch 参数链(启动入口仅保留算法列表页头部,`AppInfo.launchable` 保留供其使用)。
- **R3 排序与置顶**:新增 `AppSort`(Collator PRIMARY 强度,大小写/声调不敏感,相等回退包名,全序稳定);终态排序唯一在 AppsViewModel;新增 `AppListSettings`(置顶开关持久化 + StateFlow,设置页直连、未动 SettingsViewModel);repository 内排序删除。
- **R5 捕获设置入口**:移入算法列表页 KindFilterMenu 底部(分隔线 + Settings 项,与主界面「设置」项同范式),内容区入口行删除。
- **测试**:`AppSortTest` 5 例(JVM);`FourScreensNavTest`/`ModevTopBarTest`/`ByteSectionTest` 同步新契约;质量门禁 G1-G5 全绿(compile ×3 变体/单测全绿/lint 0 error/en-zh 对等/死资源 grep 零残留)。

---

## 2. 目录结构(目标态)

```
modev/
├─ settings.gradle.kts / build.gradle.kts / gradle.properties / local.properties
├─ gradle/libs.versions.toml / wrapper/
├─ docs/DEVELOPMENT.md            ← 本文件
├─ README.md
└─ app/src/
   ├─ main/AndroidManifest.xml       # xposedminversion=82 + xposeddescription 元数据
   ├─ main/assets/xposed_init        # legacy 入口注册(API 82+,legacy 变体)
   ├─ modern/resources/META-INF/xposed/   # modern 变体:java_init.list + module.prop(minApiVersion=102)
   ├─ legacy/resources/META-INF/xposed/   # legacy 变体:module.prop(minApiVersion=82),无 java_init.list
   └─ main/java/com/lioisme/modev/
      ├─ ModevApp.kt               # Application + 手动 DI 容器
      ├─ ModevModule.kt            # XposedModule 现代入口(注入进程)
      ├─ LegacyModuleEntry.kt      # IXposedHookLoadPackage legacy 入口(注入进程)
      ├─ core/                     # 纯 Kotlin:model/util/codec(可单测)
      ├─ hook/                     # 注入进程:hook 引擎 + 八类 Hooker + 跟踪器 + 通道
      ├─ data/                     # 宿主进程:Room + Repository
      └─ ui/                       # 宿主进程:theme/nav/screens/viewmodels
   └─ test/java/com/lioisme/modev/core/   # 单元测试
```