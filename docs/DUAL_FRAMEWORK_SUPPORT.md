# Modev 双框架兼容工程文档(DUAL_FRAMEWORK_SUPPORT)

> 名称:**Modev 双框架兼容** · 模块包名:`com.lioisme.modev`
> 目标:同一 APK 同时支持 **Vector(现代 libxposed API 102)** 与 **旧版 LSPosed(legacy Xposed API ≤93)** 两种注入框架。
> 质量基线:架构分层、空安全、不可变模型、单元测试、lint 清零、文档齐全 —— 对标一线大厂 Android 工程验收标准。
> 状态图例:`⬜ 待办` → `🔄 进行中` → `✅ 已完成`
> 本文档为本次工程的主任务载体:每完成一项任务即在本文件第 7 节标记状态并补充备注。

---

## 1. 概述

### 1.1 背景

Modev 是抓取目标 App 全部算法调用(JCA/JCE 门面类)的 Xposed 模块,当前仅支持 **libxposed API 102**(Vector 2.x / LSPosed 1.9+ 现代加载器)。用户同时保有使用 **旧版 LSPosed(API 93 及以下,如 LSPosed 1.8.6 / 1.10.2-7182 等无现代加载器的构建)** 的设备,要求同一模块 APK 在两代框架上均可识别、启用与注入。

### 1.2 目标(IN SCOPE)

- 同一 APK 内含**双入口**:现代入口(`META-INF/xposed/java_init.list`)+ 旧版入口(`assets/xposed_init`)
- 两入口共享同一套业务引擎(八类 Hooker / 上下文跟踪 / 记录出口 / 配置通道),**零业务逻辑复制**
- 现代侧保持 API 102 全特性(EXCEPTION_MODE.PROTECTIVE / 优先级 / 框架日志)
- 旧版侧严格使用 legacy API 82+ 稳定接口(IXposedHookLoadPackage / XposedHelpers / XC_MethodHook)

### 1.3 非目标(OUT OF SCOPE,明确排除)

- 现代侧支持 **LSPosed 1.9.x/1.10.x(API 100/101)**:其现代加载器会读取 `java_init.list` 并按 `module.prop` 的 `minApiVersion` 拒绝 API 102 模块,且**不会回退**到 `assets/xposed_init`(见 3.2 加载器优先级)。如未来需要,只需将 `minApiVersion` 降至 100 并对 API 100/101 差异(如 `XposedModule` 构造风格)做兼容审计,本文档 ADR-1 已记录该路径。
- native 层算法捕获、AndroidKeyStore 密钥材料导出、反 Xposed 检测对抗(沿用既有边界)。
- 热重载:仅现代框架支持;旧版框架无此能力(维持 `autoHotReload=false`)。

---

## 2. 需求规格与验收标准

每条需求均编号,验收标准可执行、可测量。

### 2.1 功能需求(FR)

| 编号 | 需求 | 验收标准(可通过自动化或真机脚本验证) |
|---|---|---|
| FR-01 | Vector(现代)识别与注入 | 在 Vector 2.2(API 102)上,管理器将模块识别为现代模块(读 `java_init.list`);勾选作用域后重启生效,目标进程产生捕获日志 |
| FR-02 | 旧版 LSPosed(legacy)识别与注入 | 在 LSPosed 1.8.6(API 93)上,管理器通过 `xposedminversion` 元数据识别模块;勾选作用域后重启生效,目标进程产生捕获日志 |
| FR-03 | 双框架捕获一致性 | 同一触发脚本(modev-tester 八类算法)在两种框架下的记录:包名/算法/操作/输入输出 hex/密钥/IV/堆栈深度语义一致;无重复记录(单入口生效) |
| FR-04 | 配置链路 | 宿主 App 开关热推送、配置持久化、日志入库在两种框架下行为一致 |
| FR-05 | 入口互斥 | 任一框架下仅加载一个入口:Vector 加载现代入口(不加载 legacy),旧版 LSPosed 加载 legacy 入口(不加载现代);`HookManager.install` 进程内幂等 |
| FR-06 | 健壮性 | 任一入口初始化失败(类缺失/框架不兼容)时静默降级,不影响被注入进程启动 |
| FR-07 | 记录出口 | 宿主 App 未运行时注入侧不崩溃、有界队列丢弃计数正确(沿用既有 RecordSink 语义) |

### 2.2 非功能需求(NFR)

| 编号 | 需求 | 验收标准 |
|---|---|---|
| NFR-01 | 不打包框架 API 类 | APK 内不包含 `io.github.libxposed.*` 与 `de.robv.android.xposed.*` 类(compileOnly 校验;APK 内容核对) |
| NFR-02 | 构建链路 | `assembleDebug` 成功、`lintDebug` 零 error、`testDebugUnitTest` 全绿(29 既有 + 新增) |
| NFR-03 | 架构纯度 | `hook/` 业务层零框架 import:libxposed 与 legacy 类仅允许出现在 `hook/adapters/` 与入口文件 |
| NFR-04 | 线程/性能 | 记录组装不阻塞被 Hook 调用线程;`Invocation.getArg` 无数组拷贝;双框架下均无新增锁 |
| NFR-05 | 可维护性 | 新增一类 Hooker 不需要了解任何框架 API(只依赖 `HookEngine` + `MethodHook`) |

---

## 3. 可行性论证

### 3.1 关键事实

1. **模块识别与加载是文件驱动且互斥**。LSPosed/Vector 加载器(`XposedInit.java` / `PreLoadedApk.loadModule`)扫描 APK:
   ```
   readName(apkFile, "META-INF/xposed/java_init.list", moduleClassNames);
   if (moduleClassNames.isEmpty()) {
       file.legacy = true;
       readName(apkFile, "assets/xposed_init", moduleClassNames);
   } else {
       file.legacy = false;
       ...
   }
   ```
   结论:**`java_init.list` 存在 → 按现代模块加载,`assets/xposed_init` 被忽略;不存在 → 按 legacy 模块加载**。双入口并存于一个 APK 在任一框架下都不会重复加载(FR-05 的理论依据)。
2. **旧框架识别模块的必要条件**:legacy 管理器(ModuleUtil)通过 `AndroidManifest.xml` 的 `<meta-data android:name="xposedminversion">` 判定模块身份;`assets/xposed_init` 只负责列出入口类。当前 Manifest 无该元数据 → 旧框架无法识别,必须新增。
3. **Vector 同时支持两代 API**:Vector 官方 README 明确 "supports both legacy and modern hooking standards" —— legacy 模块(仅 `xposed_init`)在现代框架上仍经兼容层加载。
4. **API 包级隔离**:`io.github.libxposed.*`(现代,compileOnly)与 `de.robv.android.xposed.*`(legacy,compileOnly)包名不冲突;运行时由各框架提供对应实现。两个入口的类加载路径互相不可达(见 5.4),不存在类污染。
5. **legacy API 82 稳定面(经 jar 核验)**:
   - `IXposedHookLoadPackage.handleLoadPackage(XC_LoadPackage.LoadPackageParam)`
   - `XposedHelpers.findAndHookMethod(Class, String, Object...)` / `findAndHookConstructor(Class, Object...)`
   - `XC_MethodHook.beforeHookedMethod/afterHookedMethod(MethodHookParam)`;`param.args / thisObject / method(Member) / getResult() / hasThrowable()`
   - `XposedBridge.log(String)` / `log(Throwable)`
   - 仓库:`https://api.xposed.info/`(`de.robv.android.xposed:api:82`,已核验 pom+jar 可达)
6. **现代 API 102 稳定面(经 aar 核验)**:
   - `XposedModule()` 无参构造 + `onModuleLoaded(ModuleLoadedParam)`
   - `XposedInterface.hook(Executable) → HookBuilder.setPriority/setExceptionMode(PROTECTIVE).intercept(Hooker)`
   - `Chain.getExecutable()/getThisObject()/getArgs()/getArg(int)/proceed()`
   - `XposedInterface.log(int, String, String[, Throwable])`

### 3.2 兼容性边界(灰区,已记录)

| 框架 | 加载器 | 本方案行为 |
|---|---|---|
| Vector 2.x(API 102) | 现代 | ✅ 现代入口(ADR-1) |
| LSPosed 1.9.x/1.10.x(API 100/101,含现代加载器) | 现代 | ⚠️ 拒绝(minApiVersion=102 > 框架 API,无 legacy 回退)——见 1.3 与 ADR-1 |
| LSPosed ≤ 1.8.6 及无现代加载器的 1.10.x 构建(API 93) | legacy | ✅ legacy 入口 |
| EdXposed / 其他 legacy 框架(API 82~90) | legacy | ✅ legacy 入口(xposedminversion=82) |

---

## 4. 现状审计(改造前依赖清单)

仅注入侧 4 个文件强依赖 libxposed,宿主侧(UI/data/core)全部框架无关:

| 文件 | libxposed 依赖 | 处置 |
|---|---|---|
| `ModevModule.kt` | `XposedModule` | 保留为现代入口,改为经 `LibxposedPlatform` 装配 |
| `HookEngine.kt` | `XposedInterface.hook` | 改为委托 `Platform.register` |
| `HookManager.kt` | `XposedInterface` 入参 | 改为 `Platform` 入参 |
| `ChainExt.kt` | `Chain` 扩展 | 删除(并入 `Invocation.getArg` 越界安全语义) |
| `ConfigChannel.kt` | 构造参数 `xposed`(**实际未使用**) | 移除冗余参数 |
| 8 个 Hooker | `XposedInterface.Chain` 类型 | 迁移到 `Invocation` |
| `RecordSink` / `CryptoContextTracker` / `MethodFinder` / `core/**` / 宿主 UI+data | 无 | 零改动 |

---

## 5. 架构设计

### 5.1 总体分层

```
┌─ 注入进程 ──────────────────────────────────────────────┐
│ 入口层        ModevModule (API 102)    LegacyModuleEntry │
│               │                          (legacy API 82) │
│ 适配层        LibxposedPlatform      LegacyPlatform      │
│               (io.github.libxposed.*) (de.robv.android.*)│
│ 业务层        HookEngine ── MethodHook/Invocation        │
│               8× Hooker ── CryptoContextTracker          │
│               ConfigChannel / RecordSink / WireCodec     │
│ 纯逻辑层      core/** (零框架依赖,可单测)                 │
└──────────────────────────────────────────────────────────┘
```

### 5.2 核心抽象(新增 3 个文件)

- `hook/Invocation.kt`:`abstract class Invocation`(executable / thisObject / result / argCount / **越界安全** getArg)+ `abstract class MethodHook`(before / after 空实现)。生命周期语义与 libxposed 对齐:**before = proceed 前,after = proceed 后且原方法无异常**。
- `hook/Platform.kt`:`interface Platform { register(Executable?, MethodHook): Boolean; log(msg, throwable?) }`。业务层唯一框架接触点。
- `hook/adapters/LibxposedPlatform.kt`:`intercept { chain -> before; proceed(); after }`,`ExceptionMode.PROTECTIVE`,静态方法 `getThisObject()` 异常转 null,日志走 `xposed.log`。
- `hook/adapters/LegacyPlatform.kt`:`XC_MethodHook` 直接映射 before/after;`param.hasThrowable()` 时跳过 after(与现代侧 proceed 抛出即跳过语义一致);`findAndHookMethod/findAndHookConstructor` 精确按签名注册;日志走 `XposedBridge.log`。

### 5.3 时序(以 Cipher.init 为例)

```
调用线程                        Platform(适配器)            MethodHook
   │  Cipher.init(key, spec)          │                         │
   │ ───────────────────────────────► │ before(Invocation) ────►│ 采集 direction/key/IV
   │                                  │  proceed()              │
   │ ◄─────────────── 原方法执行 ────  │                         │
   │                                  │ after(Invocation) ─────►│ 组装记录 → RecordSink
```

### 5.4 类加载安全(无类污染)

- **旧框架**:只读 `assets/xposed_init` → 仅加载 `LegacyModuleEntry` → 仅触碰 `de.robv.android.xposed.*`(框架提供)。`ModevModule`/`LibxposedPlatform` 的 `io.github.libxposed.*` 引用即使缺失,也只是未触发的符号引用,DEX 校验器不报错。
- **现代框架**:`java_init.list` 优先 → 仅加载 `ModevModule`;`assets/xposed_init` 被忽略,legacy 适配器类永不实例化。
- **业务层不 import 任何框架类**(NFR-03 由 lint/grep 检查兜底)。

### 5.5 入口互斥与幂等

`HookManager.install(platform, packageName, processName)` 保留 `@Synchronized + installed` 幂等保护;两入口均以 `processName` 作为包标识(延续现有 `:sub` 分离进程区分语义)。

### 5.6 构建变体(ADR-6)

`app/build.gradle.kts` 定义 `framework` 维度双 flavor,仅影响 Xposed 入口资源,代码与测试完全共享:

| 变体 | 入口资源 | 适用框架 | 产物 |
|---|---|---|---|
| `modern`(默认) | `META-INF/xposed/java_init.list` + `module.prop(minApiVersion=102)` | Vector 2.x(现代路径) | `app-modern-*-debug.apk` |
| `legacy` | `assets/xposed_init` + `module.prop(minApiVersion=82)`,无 `java_init.list` | LSPosed ≤93 / EdXposed / LSPosed 1.10.x legacy 路径 / Vector legacy 路径 | `app-legacy-*-debug.apk` |

---

## 6. 决策记录(ADR)

| 编号 | 决策 | 理由 | 备选与代价 |
|---|---|---|---|
| ADR-1 | 现代侧 `module.prop` 保持 `minApiVersion=102`,不降级 | 与代码实际使用的 API 面一致,零兼容风险;用户明确仅覆盖 Vector | 降至 100 可覆盖 LSPosed 1.9/1.10 现代,但需 API 100/101 兼容审计,超出本次范围 |
| ADR-2 | Manifest `xposedminversion=82` | 覆盖全部 legacy 框架(82~93);模块只用 82 即有的稳定接口 | 若设 93,EdXposed(API 90 及以下)被拒 |
| ADR-3 | legacy 入口以 `processName` 作包标识(与现代入口一致) | 保持分离进程日志可区分,行为零漂移 | — |
| ADR-4 | after 阶段在 `throwable` 时跳过(两适配器一致) | 与现代侧 `proceed()` 抛出即跳过的既有行为对齐,避免异常场景多产生空记录 | legacy 框架默认会回调 after;显式跳过消除双框架行为差异 |
| ADR-5 | 日志统一走 `Platform.log`(现代→`xposed.log`,legacy→`XposedBridge.log`) | 兑现 README "注入侧记录在 Xposed log" 承诺,排障可见 | 同时保留 android.util.Log 会双写;不采用 |
| ADR-6 | 引入 `modern`/`legacy` 双构建变体(`productFlavors`) | 实机发现:LSPosed 1.9/1.10 加载器在存在 `java_init.list` 时不回落 `xposed_init`(仅 API ≤100 构造器),单 APK 无法在 Android 14 上触达 legacy 入口;legacy 变体(无 java_init.list + `minApiVersion=82`)让任意框架走 legacy 路径 | 默认 APK 保持纯 102;legacy 变体仅在需要旧框架时构建 |

---

## 7. 任务清单与状态跟踪

### T0 工程文档落地
**验收**:本文件创建完成,含需求/论证/审计/设计/决策/任务/风险/发布清单。
**状态**:✅ → 备注:本文件完成,含 FR-01~07/NFR-01~05、可行性论证(含加载器源码语义)、现状审计、架构、ADR-1~5、任务 T0~T9、真机矩阵、风险与发布清单。

### T1 构建基线:legacy API 依赖
**验收**:`settings.gradle.kts` 增加 `api.xposed.info` 仓库(含 group 约束);`libs.versions.toml` 增加 `xposed-api:82`;`app/build.gradle.kts` 增加 `compileOnly(libs.xposed.api)`;与 libxposed 102 compileOnly 并存无冲突。
**状态**:✅ → 备注:已加仓库(含 `includeGroup("de.robv.android.xposed")` 白名单)、`xposedApi=82`、`compileOnly`;`gradlew :app:dependencies --configuration debugCompileClasspath` 验证 102.0.0 与 api:82 并存解析无冲突。

### T2 中性 Hook 抽象层
**验收**:`hook/Invocation.kt`、`hook/Platform.kt`、`hook/adapters/LibxposedPlatform.kt`、`hook/adapters/LegacyPlatform.kt` 四文件落地;语义与 5.2/5.3 一致。
**状态**:✅ → 备注:`Invocation.getArg` 越界安全返回 null;`MethodHook` 抽象 before/after;`LibxposedPlatform`(PROTECTIVE 异常模式,intercept 内 before→proceed→after);`LegacyPlatform`(object,XC_MethodHook 映射,hasThrowable 跳过 after,try/catch 兜底,按签名精确注册 findAndHookMethod/findAndHookConstructor)。

### T3 业务层去框架化
**验收**:`HookEngine` 委托 `Platform`;`HookManager.install(Platform,…)`;`ConfigChannel` 删除未使用 `xposed` 参数;删除 `ChainExt.kt`;`hook/` 业务层零 libxposed import(grep 校验)。
**状态**:✅ → 备注:重写 HookEngine/HookManager,ConfigChannel 去参,删除 ChainExt;grep 复核 libxposed/legacy import 仅存在于两入口与两适配器(StackTraceFilter 仅含字符串前缀,用于双框架堆栈过滤)。

### T4 八类 Hooker 迁移
**验收**:8 个 Hooker 全部改用 `MethodHook/Invocation`;`hookBefore/hookAfter/hookInstanceOf` 语义与重构前逐一对齐(开关判断、上下文关联、thisObject 空安全)。
**状态**:✅ → 备注:MessageDigest/Cipher/Signature/Mac/KeyParam/KeyGeneration/KeyFactory/Base64 八个 Hooker 全部迁移;统一 `enabled()` 守卫 + `thisObject` 局部 val 空安全;`CipherHooker.extractKey` internal 复用保持;方法/构造器经 `MethodFinder` 兜底注册。

### T5 双入口
**验收**:`ModevModule` 经 `LibxposedPlatform` 装配;新增 `LegacyModuleEntry : IXposedHookLoadPackage`(try/catch 兜底 + `XposedBridge.log`);两入口共用 `HookManager`。
**状态**:✅ → 备注:ModevModule 改为 `HookManager.install(LibxposedPlatform(this), …)`;新增 LegacyModuleEntry,初始化崩溃不传染目标进程(legacy 无 PROTECTIVE);两入口均以 processName 作包标识。

### T6 资源与清单
**验收**:新增 `assets/xposed_init` 指向 `LegacyModuleEntry`;Manifest 增加 `xposedminversion=82` 与 `xposeddescription`;`module.prop` 不变(102);`proguard-rules.pro` 增加 legacy 入口 keep 与 `-dontwarn de.robv.android.xposed.**`。
**状态**:✅ → 备注:assets/xposed_init、Manifest 双 meta-data(description 引用 strings.xml)、proguard 双入口 keep + 双框架 dontwarn;module.prop/java_init.list 未动;合并后 Manifest 已核验含两元数据。

### T7 单元测试
**验收**:既有 29 用例保持全绿;新增 `InvocationTest`(越界安全/空实现契约)。
**状态**:✅ → 备注:新增 InvocationTest 3 用例(越界取值/空参/空实现契约),`testDebugUnitTest` 共 32 用例全绿、0 失败。

### T8 构建验证
**验收**:`gradlew assembleDebug`、`gradlew lintDebug`(零 error)、`gradlew testDebugUnitTest`(全绿);APK 内容核对不含 `*.xposed.*` 类(如开 R8 则核对 keep)。
**状态**:✅ → 备注:assembleDebug/lintDebug/testDebugUnitTest 全绿;lint 0 error(17 warning 均为既有文件,无新增);x86_64 debug APK 展开核验:含 META-INF/xposed/java_init.list+module.prop 与 assets/xposed_init,无任何框架类打包;Manifest 元数据在位。

### T9 文档收尾
**验收**:README 安装/限制章节补充双框架说明;`docs/DEVELOPMENT.md` 增加 T12 双框架兼容条目;本文件状态全部收敛。
**状态**:✅ → 备注:README 功能导语/安装(双框架二选一 + 双入口互斥说明)/限制(新增 legacy 范围与 LSPosed 1.9/1.10 灰区)/开发链接全部更新;DEVELOPMENT.md 增加 T12 条目与目录结构(双入口 + assets/xposed_init);本文件 T0~T9 全部标记完成并附工作备注。

---

## 8. 测试计划与矩阵(真机)

复用现有 x64 安卓虚拟机(Magisk + root,Android 14 / API 34)与 `com.lioisme.modetester` 测试 APK。

> **环境说明**:真 legacy 框架(LSPosed 1.8.6 / EdXposed)仅支持 Android ≤13,本 VM(API 34)无法安装。场景 B 以 **LSPosed 1.10.2(API 100)的 legacy 加载路径** 实测(与 1.8.6 同为 legacy API 82+ 接口面);受测 APK 为 **legacy 构建变体**(无 `java_init.list`,`minApiVersion=82`,ADR-6)。另补测 legacy 变体在 Vector 2.2 的 legacy 路径。

| 用例 | 场景 A:Vector 2.2(现代,modern 变体) | 场景 B:LSPosed 1.10.2(legacy 路径,legacy 变体) |
|---|---|---|
| T-01 模块识别与启用 | ✅ `VectorModuleManager: Loading class com.lioisme.modev.ModevModule`;作用域生效 | ✅ `LSPosed-Bridge: Loading legacy module … Loading class LegacyModuleEntry` |
| T-02 八类算法捕获 | ✅ 全部捕获入库;摘要 5008 条/轮(1000 次 PBKDF2 迭代属真实行为),七类分布:10/3/1/13/3/5008/12(BASE64 默认关) | ✅ 与 A 逐类别一致(同一轮次分布完全相同) |
| T-03 开关热推送 | ✅ 关摘要后计数停于 5008,其余类别翻倍 | ✅ 同 A(停于 5008,其余翻倍) |
| T-04 配置持久化 | ✅ 重启后开关保持 ON,计数继续增长 | ✅ 同 A(重启后 5008→10016) |
| T-05 重复记录检测 | ✅ 无(计数严格翻倍,单入口) | ✅ 无 |
| T-06 崩溃/降级 | ✅ 无 `crashed after proceed` / hook FAILED / FATAL | ✅ 无 hook 异常;仅注入侧启动期 `config channel connect failed` 重试日志(宿主未就绪,属已知行为) |
| T-07 分离进程 | N/A(本模块单进程,processName==packageName;设计上保留 `:sub` 后缀透传) | N/A(同左) |

**交叉验证(补充)**:legacy 变体在 **Vector 2.2 legacy 路径** 同样加载成功(`VectorLegacyBridge: Loading legacy module … Loading class LegacyModuleEntry`)并完整捕获,证明 legacy 变体双框架通用。

**实测发现(重要)**:
1. LSPosed 1.9/1.10 加载器 **不回落 `xposed_init`**:存在 `java_init.list` 时按现代加载(期待 API ≤100 双参构造器),API 102 模块报 `NoSuchMethodException` 后模块整体不注入 → 单 APK 在此类框架上 legacy 入口不可达(已由 ADR-6 变体方案解决,并列入风险册)。
2. 框架对模块重装(同包名覆盖安装)会重置 `enabled=0` 并级联删除 scope 行,需在管理器 UI 或 `modules_config.db` 重新启用(本次测试已用 sqlite3 修复并复验)。
3. Android 14 缓存进程冻结:宿主被冻结时 46299 监听不服务,注入侧读超时丢记录 → 测试需保持宿主在前台(产品行为不变:README 已注明宿主未运行时无日志)。

## 9. 风险登记册

| 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|
| 灰区框架(LSPosed 1.9/1.10 现代)拒载 | 该区间用户无法使用 | 中 | 实测证实:存在 `java_init.list` 时不回落 `xposed_init` 且仅支持 API ≤100 构造器 → ADR-6 legacy 变体覆盖(该变体在 1.10.2 legacy 路径实测通过) |
| legacy 适配器误引用 libxposed 类 | 旧框架 NoClassDefFoundError | 低 | 包结构隔离 + NFR-03 grep 检查 + T-06 实测通过(legacy 路径无类加载错误) |
| `findAndHookMethod` 重载匹配偏差 | 漏捕获/重复捕获 | 低 | 按既有 MethodFinder 精确签名注册,与现代侧同源;T-02 计数与 A 场景逐类一致证实无漏/重 |
| `api.xposed.info` 仓库不可达 | 构建失败 | 低 | 首次构建验证 + 必要时 vendored jar 备选 |
| 双入口被某框架同时加载 | 重复记录 | 低 | 实测 LSPosed 1.10.2/Vector 均按入口文件单路径加载;T-05 计数严格翻倍证实无重复 |
| 框架对覆盖安装重置模块启用状态 | 升级后模块失效(静默) | 中(已实测) | 文档与发布检查单注明:覆盖安装后需在管理器重新启用作用域;README 安装步骤补充提示 |
| 真 legacy 框架(LSPosed 1.8.6/EdXposed)需 Android ≤13 | Android 14+ 无法覆盖该区间 | 高 | ADR-6 legacy 变体在 1.10.2 legacy 路径实测通过(接口面同为 API 82+);Android ≤13 设备仍建议抽测 1.8.6 |

## 10. 发布检查单

- [x] `gradlew testModernDebugUnitTest` / `testLegacyDebugUnitTest` 全绿(32 用例)
- [x] `gradlew lintModernDebug` / `lintLegacyDebug` 零 error
- [x] `gradlew assembleModernDebug`(默认)与 `assembleLegacyDebug` 产出 APK
- [x] APK 内无 `io.github.libxposed.*` / `de.robv.android.xposed.*` 类
- [x] 真机矩阵 T-01~T-07 双场景通过(Vector 2.2 现代 + LSPosed 1.10.2 legacy 路径;真 legacy 1.8.6 待 Android ≤13 设备抽测)
- [x] README 与本文档状态一致
- [ ] 覆盖安装后在框架管理器重新启用模块作用域(框架会重置 enabled)并重启验证

---

## 11. 参考资料

- LSPosed 加载器源码:`core/src/main/java/de/robv/android/xposed/XposedInit.java`(PreLoadedApk.loadModule 优先级判定)
- Vector README:"supports both legacy and modern hooking standards"
- legacy API javadoc:`https://api.xposed.info/reference/`
- libxposed API javadoc:`https://libxposed.github.io/api/`
- 仓库:`https://api.xposed.info/`(de.robv.android.xposed:api:82)、Maven Central(io.github.libxposed:api:102.0.0)