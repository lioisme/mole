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
**备注**:最终模型:`CryptoKind` 8 类(去掉了 KEY_AGREEMENT,新增 KEY_PARAM/KEY_GENERATION/KEY_FACTORY/BASE64);`WireProtocol`(PORT=46299、MAGIC=MDEV、VERSION=1、类型 hello/config/record/ping/pong、顶层字段 t/p)+ `WireCodec`(Gson JSON 行);`BytesView` 双态视图含截断与可打印判定(TextUtil 自实现 UTF-8 校验);`StackTraceFilter` 前缀剔除(模块/框架/反射/被 Hook 门面类)。单测留待 T9 补齐。

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

---

## 2. 目录结构(目标态)

```
modev/
├─ settings.gradle.kts / build.gradle.kts / gradle.properties / local.properties
├─ gradle/libs.versions.toml / wrapper/
├─ docs/DEVELOPMENT.md            ← 本文件
├─ README.md
└─ app/src/main/
   ├─ AndroidManifest.xml
   ├─ resources/META-INF/xposed/{java_init.list, module.prop}
   ├─ java/com/lioisme/modev/
   │  ├─ ModevApp.kt               # Application + 手动 DI 容器
   │  ├─ ModevModule.kt            # XposedModule 入口(注入进程)
   │  ├─ core/                     # 纯 Kotlin:model/util/codec(可单测)
   │  ├─ hook/                     # 注入进程:hook 引擎 + 八类 Hooker + 跟踪器 + 通道
   │  ├─ data/                     # 宿主进程:Room + Repository
   │  └─ ui/                       # 宿主进程:theme/nav/screens/viewmodels
   └─ test/java/com/lioisme/modev/core/   # 单元测试
```