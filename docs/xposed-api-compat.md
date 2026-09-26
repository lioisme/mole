# V50 交付物 3 / Xposed API 版本兼容性分析与方案

> 生成日期:2026-09-24。需求原文:「本项目使用了较新的 xposed api，以至于只能在 vector 框架中可用，
> 但主流依旧是 lsposed。要求本模块也支持旧 API，你需要审计分析是 vector 可以向下兼容，
> 还是要集成新旧两种 API，还是其它。」
>
> **先给结论(三句)**
> 1. **代码层已经「集成新旧两种 API」了** —— 双入口 + 双适配器 + 单一中性引擎 + 两个 product flavor，
>    本次实跑 `:app:assembleLegacyDebug` **编译通过并产出 APK**(2026-09-24 07:30)。
> 2. **真正的缺口不在 API 适配层，而在验证层与元数据层**:legacy 变体**从未在任何设备上跑过**
>    (整套验收矩阵硬编码 modern)，两个真实适配器**没有任何单测引用**(1174 个单测的注入侧用例
>    全部跑在 `FakeHookPlatform` 上)。
> 3. **「vector 向下兼容」这个提法本身要纠正**:兼容性是**框架侧**属性(Vector/LSPosed 是否读 `xposed_init`)，
>    不是模块侧能单方面获得的能力 —— 正确的问题是「**一个 APK 能否同时被两种加载器接受**」，
>    答案是**能，但在旧框架设备上会崩**(§4 方案 B)。因此推荐**继续双 APK + 补验收矩阵**(§4 方案 A)。

---

## 1. 现状事实表(全部本次实测，非转述)

| 维度 | modern 变体 | legacy 变体 | 证据 |
|---|---|---|---|
| 入口声明文件 | `META-INF/xposed/java_init.list`(内容 = `com.lioisme.modev.ModevModule`) | 仅 `assets/xposed_init`(内容 = `com.lioisme.modev.LegacyModuleEntry`) | `app/src/{modern,legacy}/resources/`、`app/src/main/assets/xposed_init` |
| 入口类 | `ModevModule : io.github.libxposed.api.XposedModule`(`onModuleLoaded`) | `LegacyModuleEntry : IXposedHookLoadPackage`(`handleLoadPackage`) | `ModevModule.kt:17`、`LegacyModuleEntry.kt:16` |
| API 版本元数据 | `module.prop`: `minApiVersion=102`、`targetApiVersion=102`、`exceptionMode=protective`、`autoHotReload=false`(**缺 `id=`/`name=`**) | `module.prop`: `id/name/version/versionCode/author/description` + `minApiVersion=82`(**缺 `targetApiVersion`**，缺 `exceptionMode`) | `cat` 两文件实测 |
| 适配器 | `LibxposedPlatform` | `LegacyPlatform`(object) | `hook/adapters/` |
| 业务引擎 | `HookInstaller` + `HookEngine` + 88 个 Hooker(**完全共用，零变体源码集**) | 同 | `find app/src/{modern,legacy} -name "*.kt"` = **0 个**(两变体只有 resources) |
| 依赖 | `compileOnly(libxposed-api)` + `implementation(libxposed-service)` | `compileOnly` 两者(运行时不打包) | `app/build.gradle.kts:173-178` |
| 编译 | ✓(既有产物) | ✓ **本次实测 BUILD SUCCESSFUL**，产出 arm64-v8a/x86_64 debug APK | `/tmp/audit-legacy-build.log:51` |
| 宿主进程框架服务桥 | `FrameworkBridge.ensureRegistered()`(ModevApp + ModevModule 双处调用) | `LegacyModuleEntry` **不调用** | `ModevApp.kt:68`、`ModevModule.kt:34`、`LegacyModuleEntry.kt`(无) |
| 设备验收 | 整套矩阵(10-build-install / 23-run-smoke / 24-run-mcp / 11-vector-scope / run-acceptance) | **零覆盖，全部脚本硬编码 `assembleModernDebug` 与 `apk/modern/…`** | `scripts/test-integration/*.ps1` 全量 grep |
| 适配器单测 | `LegacyPlatform` **0** 个 / `LibxposedPlatform` **0** 个引用 | 同 | `grep -rn "LegacyPlatform\|LibxposedPlatform" app/src/test` = 空;1174 用例走 `FakeHookPlatform`(`DeviceHookerTestSupport.kt:25`) |

### 1.1 抽象层设计是正确的(不要重做)

`hook/Platform.kt` 只有三个成员:`register` / `registerInterventional`(默认转 register) / `log`。
业务侧只依赖它，框架差异被收口在两个适配器里 —— 这是需求 5 的**已达成部分**，符合「协议适配层薄、
业务层中性」的标准做法。已识别并处理过的两个真陷阱也都在代码里:

- **legacy 无 `hasResult()`**(V43 T3 实锤):`MethodHookParam` 判「before 已 setResult」只能用
  `param.result != null`，对 `setResult(null)`(吞结果类干预)失明。V44 T2.6 已改为反射读
  `returnEarly` 字段 + 退回近似语义(`LegacyPlatform.kt:84-124`)。**方向正确，但零测试钉住**。
- **`checkcast` 污染 legacy 空值路径**(已处理):`FrameworkBridge.serviceFlow` 故意暴露 `StateFlow<Any?>`，
  并在注释点名「若消费方签名带 `XposedService?` 则生成的 checkcast 会在 legacy 首发射即抛
  NoClassDefFoundError —— 即使值为 null」(`FrameworkBridge.kt:20-27`)，且 `ensureRegistered` 用
  `runCatching` 兜 service 库缺失。**这是全仓最懂这个坑的地方，但同一纪律没有写成门禁**，
  新增消费方可以轻易破它。

---

## 2. 兼容性判定:是「Vector 向下兼容」还是「模块要双 API」？

**两者都不是当前问题的正确表述。** 拆开看:

| 问题 | 事实 | 判据来源 |
|---|---|---|
| Vector 是否执行旧 API 82 入口(`assets/xposed_init`)？ | **待实测**(V48/V49 只在 Vector 上验过 modern 路径，从未测过 legacy APK) | 本仓设备档无此证据 |
| LSPosed 是否执行现代入口？ | **是，自 1.9.0 起支持 libxposed API**(Remote Preferences 同批引入);1.8.x 仅旧 API | LSPosed 官方 wiki「Develop Xposed Modules Using Modern Xposed API」 |
| 模块能否单方面「向下兼容」？ | 不能。加载哪个入口由**框架加载器**决定:现代加载器优先 `java_init.list`，旧加载器只读 `xposed_init` | 本仓 `LegacyModuleEntry.kt` 注释 + 上条 wiki |
| 结论 | 兼容性问题 = **元数据 + 产物分发 + 设备验证** 三件事，不是 API 编码问题 | — |

因此:模块**必须**同时具备两套入口代码(已有)，但**不应**把两套入口塞进同一个 APK(见 §4 方案 B 的崩塌路径)。

---

## 3. 风险清单(按严重级)

| id | 风险 | 证据 | 级别 |
|---|---|---|---|
| **R1** | **legacy 变体从未在设备/模拟器上运行过**。所有设备验收脚本硬编码 modern;V48/V49 整轮设备档都是 Vector+modern。所谓「支持旧 API」目前是**编译期主张，非运行期事实** | `10-build-install.ps1:8,19`;`run-acceptance.ps1:259`;`25-run-v42-stress.ps1:61-68` | **P0** |
| **R2** | **两个真实适配器零单测引用**。1174 单测的注入侧用例跑在 `FakeHookPlatform` 上 ⇒ `Platform` 契约的双实现语义(短路判定、异常隔离、广播链可见性)没有任何对账;这正是本仓反复实锤的「绿在假夹具上」病族(`RecordSink` 未 `start()` 那条的同一形态) | grep 结果 + `DeviceHookerTestSupport.kt:25` | **P0** |
| **R3** | **modern `module.prop` 缺 `id=` 与 `name=`**。legacy 版有 `id=com.lioisme.modev`。若 libxposed 规范以 `id` 作模块身份校验，LSPosed 现代路径可能拒载 modern APK ⇒ 目标问题(「主流是 LSPosed」)未解。**假设项，须用 1.10.x/LSPosed 2.x 实测判定** | 两文件 `cat` 对照 | **P1** |
| **R4** | **`registerInterventional` 广播链语义双适配器不等价**。libxposed 走 PASSTHROUGH + 回调体内异常隔离;legacy 靠 `returnEarly` 反射 + `XposedBridge` 自身分发顺序。多 Hooker 共享同一方法(`SharedMethodBroadcast`)时，「后序订阅者能否看见前序 `setResult`」在两框架下**未对账**。影响面 = 所有干预型钩子(maps 掩蔽/栈过滤/传感器/SSL/VPN…) | `Platform.kt:22-30`;`AGENTS` V38「现代路径 after-setResult 曾被拦截器丢弃」同族历史 | **P1** |
| **R5** | **legacy 无远程偏好能力**。`LegacyModuleEntry` 不注册 `FrameworkBridge`(旧 API 也无此概念)，注入侧配置只能靠 TCP 推送 + 磁盘兜底。V36 已注记「Vector 2.2 不投递 XposedService」⇒ 叠加交付物 2 A2(`resolvedProfile` 无写入方)后，**画像在 legacy 与 Vector 上都可能静默不发生** | `LegacyModuleEntry.kt`;`ModevModule.kt:31-46` | **P1** |
| **R6** | **「含 legacy 路径的 1.10.x」是未验证主张**，写在 legacy `module.prop` 的 description 里对外广告。若现代加载器实际不回落到 `xposed_init`，这句对外承诺为假 | `app/src/legacy/resources/META-INF/xposed/module.prop` | P2 |
| **R7** | **单 APK 双入口的诱惑**。若后续为省事合并产物，旧框架设备上会因 `ModevModule` 的超类 `io.github.libxposed.api.XposedModule` 运行时缺失而 `NoClassDefFoundError`(取决于框架是否在 `xposed_init` 路径上校验/实例化全部候选类)⇒ **必须用依赖注入把两个入口编进同一产物时才成立**，代价是旧框架不可用。见 §4 | 分析 | P2(预防) |
| **R8** | **legacy 缺 `targetApiVersion` / `exceptionMode`**。libxposed 的 PROTECTIVE 模式在 legacy 无对等物(已用 `runSafely` try/catch 兜)，但 `targetApiVersion` 缺失会影响框架侧的行为档位判定 | 两 `module.prop` 对照 | P3 |
| **R9** | **legacy API 82 桩 jar 与真框架差异**:反射读 `returnEarly` 在单测桩里拿不到字段 → 退回近似语义，**测试永远走不到真分支** | `LegacyPlatform.kt:117-124` 注释自陈 | P2(与 R2 同修) |

---

## 4. 三个方案的比较与推荐

| 方案 | 做法 | 收益 | 代价 | 判定 |
|---|---|---|---|---|
| **A. 双 APK + 补验收矩阵**(现架构的完成态) | 保留两 flavor 与两入口;补 legacy 设备矩阵、双适配器契约测试、`module.prop` 元数据对齐、框架能力检测提示 | 每种框架跑各自的**已验证**产物;旧框架 APK 运行时零 libxposed 类 | 用户须选对 APK(需 UI/打包指引);验收脚本 +1 套 | ✅ **推荐** |
| **B. 单 APK 双入口** | 一个产物同时带 `java_init.list` + `xposed_init`，两入口类都编进去 | 用户零选择负担 | 旧框架设备上 libxposed-api 运行时缺失 ⇒ R7 崩风险;libxposed-service 被迫 `implementation` ⇒ 体积与 legacy 空值路径 checkcast 风险(`FrameworkBridge` 注释点名的正是这个) | ❌ 不推荐(除非引入框架侧类加载探测，收益不抵风险) |
| **C. 放弃 legacy，只发 modern** | 退役 legacy flavor | 最省 | 直接违背需求 5;把 LSPosed 1.9.x/1.8.x 与 EdXposed 用户整体排除 | ❌ 否决(用户显式要求) |

**推荐 A**，理由:代码架构已经付了「双 API」的成本且付对了(中性引擎 + 薄适配器)，
剩下的钱全在**验证**与**元数据**上 —— 这是本仓一贯的「声明面即生效面」纪律缺口的又一实例，
而不是设计缺陷。

### 4.1a 执行登记(2026-09-25 本轮实况 —— 未做的不许读成做过的)

| # | 工作 | 状态 | 证据 / 未做理由 |
|---|---|---|---|
| A-3 | `module.prop` 对齐 + JVM 门禁 | ✅ **完成** | modern 补 `id/name/version/versionCode/author/description`(原先只有 4 行 API 参数),legacy 补 `targetApiVersion=82` 并把 description 改成**已验证**口径;门禁 = `FrameworkEntrypointContractTest` 6 条 |
| A-5 | `checkcast` 纪律落门禁 | ✅ **完成** | 同一测试第 5 条(消费方形参禁现 `XposedService`,且 `serviceFlow` 必须仍是 `StateFlow<Any?>`) |
| A-1 | 双适配器契约测试 | ⚠️ **改判,不按原方案做** | JVM 路实测走不通:API 82 桩 jar 的 `XC_MethodHook$MethodHookParam` 构造器**包私有**、`setResult/getResult` 桩方法体抛 `Stub!`、类是 final ⇒ 不能构造也不能子类化;libxposed 侧需 mock `Chain`。**适配器语义的唯一真判据是设备档** |
| A-2 | legacy 设备验收矩阵 | ⬜ **未做** | 需先把三处硬编码参数化(`10-build-install.ps1:8,19`、`run-acceptance.ps1:259`、`25-run-v42-stress.ps1:61-68`);且本轮 VM 在 **modern** 档已掉线 3 次 —— 设备不稳时跑 legacy 只产出不可判定的红,**不做比做了更好** |
| A-4 | 框架能力探测上屏 | ⬜ 未做 | 无探测数据之前先做 UI = 造一个空壳 |
| A-6 | legacy 配置通路定性 | ⬜ 未做 | 依赖 A-2 |
| §5 | 四项开放问题实测 | ⬜ **全部未测** | 本文对它们的状态是「**未知**」,不是「已解答」 |

> **本轮新增的唯一确定事实**(实测,非推断):`unzip -l` 对照两个 APK ——
> **modern 同时携带 `assets/xposed_init` 与 `META-INF/xposed/java_init.list`(双声明)**,
> legacy 只携带 `xposed_init`。这条**修正了 §4 方案 B 的前提**:
> 「单 APK 双入口」在 modern 变体里**事实上已经存在**,所以方案 B 的风险应重述为
> 「旧框架加载 modern APK 时 `ModevModule` 通常不被装载(不在 `xposed_init` 上),
> 真实风险点是 `libxposed-service` 被 `implementation` 打进包体 + 框架是否校验候选类」——
> **仍需设备实测定论**;在拿到该证据前,「modern 也支持旧框架」这句**不许写成结论**。

### 4.1 方案 A 的工作包(逐条可验收,进交付物 2 WP-E)

| # | 工作 | 完成判据 |
|---|---|---|
| A-1 | **适配器契约测试族** `PlatformContractParityTest`:同一套 `MethodHook` 语义断言，分别跑在 `LegacyPlatform`(用 API 82 桩 + 一个反射提供 `returnEarly` 的仿真 param)与 `LibxposedPlatform`(mock `Chain`)上 | 用例集含:①before `setResult(null)` 后 `consumed == true`;②before `setThrowable` 跳过原方法;③after `setResult` 不被吞(V38 回归);④同方法双订阅者看见彼此结果;⑤原方法抛异常时 after 不执行 |
| A-2 | **legacy 设备验收矩阵**:`10-build-install` 增 `-Variant legacy`，新增 `3x-run-legacy-matrix`(LSPosed 1.8.x 或 EdXposed 档；若设备不可得则显式 BLOCKED 并写明前置) | 注入矩阵 + 23-run-smoke + 抽样 androidTest 在 legacy APK 上跑通，或产出「已定性阻塞 + 前置条件」报告(**禁**写 ⛔ 而无 scope 步证据，AGENTS 实锤坑) |
| A-3 | **`module.prop` 对齐**:modern 补 `id=`/`name=`/`version=`;legacy 补 `targetApiVersion=`;两变体 description 与实际支持矩阵一致 | 一个 JVM 门禁:两变体 `module.prop` 解析后 `id == applicationId`，且 `minApiVersion` 与 flavor 表一致 |
| A-4 | **框架能力探测上屏**(诊断页一行):检测「本模块 APK 变体 / 当前框架是否投递 XposedService / 是否走现代入口」 | 只读快照，无新数据源(用 `FrameworkBridge.serviceFlow` 是否非空 + `BuildConfig` 变体位);legacy 分支零类解析 |
| A-5 | **`checkcast` 纪律落门禁**:新增源码扫描 —— `FrameworkBridge.serviceFlow` 的消费方签名不得出现 `XposedService`(转型只允许在 `XposedScopeService` 包装点) | `PackageBoundaryTest` 加 R7 或独立 `LegacySafeCastTest` |
| A-6 | **legacy 配置通路定性**:明确「legacy = TCP-only + 磁盘兜底」，并把 V36 D-1 偏差层契约在 legacy 下重跑一遍 | `ConfigChannelDeviationTest` 双适配器参数化 |

---

## 5. 交付前必答的开放问题(需设备侧配合，勿猜)

| 问题 | 为什么必须实测 | 最小实验 |
|---|---|---|
| Vector 2.2 是否回落执行 `xposed_init`？ | 决定「装了 Vector 但拿错 APK」时的用户可见行为 | 同机装 legacy APK，看 logcat 是否出现 `Modev: module init crashed` 或任何 `Loading module` 痕迹 |
| LSPosed 1.10.x / 2.x 是否因 modern `module.prop` 缺 `id` 而拒载？ | R3 的唯一裁判 | 装 modern APK 到 LSPosed 2.x，看模块列表是否出现、是否可启用 |
| LSPosed 现代路径是否投递 `XposedService` 到**注入进程**？ | 决定画像/配置在 LSPosed 上是否绕过 Vector 的「不投递」缺陷(V36 注记) | legacy/modern 各一次，读注入侧 `getRemotePreferences` 是否非空 |
| `returnEarly` 字段在 LSPosed 与 EdXposed 的实现里是否都存在？ | 决定 `consumed` 判定退化为 `result != null` 的实际面 | 一次性反射探测 + 落到日志，不靠读源码 |

以上四项构成 A-2 的前置检查表;任何一项「测不了」都必须在验收报告里写成**具名前置**，
不允许以「门禁绿」代替(AGENTS「验收需可复现」纪律)。

---

## 6. 一句话给决策者

现有代码在 API 兼容上**没有走错路**，只是**没走完**:双入口/双适配器/单引擎的骨架是对的，
`returnEarly` 与 `checkcast` 两处硬骨头已经啃过。剩下的是验收事实(legacy 从没跑过、适配器零测试)
和元数据卫生(`module.prop` 不齐)——**这两类恰好都是本仓最擅长用门禁钉死的形态**，
所以本项工作的正确规模是「补测试与脚本」，不是「重做兼容层」。
