# Modev

**English (short)** — Modev is an Xposed module and its viewer app in a single APK,
for a rooted Android device you own. It *listens* to what another app does
(crypto calls, SQL, file/pref/WebView activity) and can *change* what that app
sees (dialog blocking, screen-record release, SSL trust bypass, proxy/VPN hiding,
device-environment spoofing). Everything stays on the device: no account, no
telemetry, no server. Records leave only when you export them.

---

一句话:在 Xposed 作用域内**监听**目标应用(算法 / 数据库 / 行为 / 偏好 / WebView / 系统信息),
并可选地**干预**其行为(弹窗拦截 / SSL 绕过 / 代理与 VPN 隐藏 / Root·Xposed·模拟器伪装 / 进程守护),
配套一个用于浏览记录与逐项配置的宿主 App。**同一 APK 双框架兼容**:现代 Vector(libxposed)与旧版 LSPosed / EdXposed(legacy API 82)。

- 模块包名 `com.lioisme.modev`;宿主 App 与模块同一个 APK(自身运行即为宿主)
- 两个默认端口都只绑回环:wire 配置通道 **1511**、MCP 服务 **1510**;
  是否改绑 `0.0.0.0` 由设置里唯一的「外部访问」开关决定

## 它是什么 / 不是什么

| 是 | 不是 |
|---|---|
| 逆向与兼容性调试用的观察镜:目标 App 调了什么算法、执行了哪条 SQL、读了哪个文件,逐条落库可查 | 不是抓包工具,也不是 Charles/mitmproxy 的前端 |
| 作用域内可按类别逐项开关的行为改写器(关掉即零副作用) | 不是「过检测」方案:Play Integrity / 硬件证明 / 银行级风控的判定大量发生在被钩进程之外,本模块**做不到**,也不以此为目标 |
| 一台你自己 root 设备上的本地工具 | 不是隐身工具,也不是反取证工具;root 设备上不存在针对设备所有者的机密 |

用之前请读 [SECURITY.md](SECURITY.md):它列出了每个对外面的默认档位,以及三条**已知物理上限**
(直读 `static final` 字段不可钩、钩点解析失败即静默空转、同方法被两个模块争抢注册)。

## 前置要求

- 已 root 的 Android 设备(API 26+)
- 以下任一 Xposed 框架:Vector 2.x(libxposed)、LSPosed 1.8.x、EdXposed、或 LSPosed 1.9/1.10
- 需要「干预域」真正生效的验证环境(自带测试靶 App):见 [scripts/test-integration](scripts/test-integration)

## 安装

1. 下载 Release APK 并安装。
2. 在框架管理器里启用 Modev 模块。
3. 打开 Modev → 应用列表 → 目标应用行尾的**「作用域此应用」**勾选框(或直接由框架管理器勾作用域)。
4. 重启目标应用(或整机重启)。

选哪个变体:

| 你的框架 | 装这个 | 原因 |
|---|---|---|
| Vector 2.x | `modern`(默认) | 走 `META-INF/xposed/java_init.list` + libxposed 入口 |
| LSPosed 1.8.x / EdXposed 等旧框架 | `legacy` | 走 `assets/xposed_init` + Xposed API 82 |
| LSPosed 1.9 / 1.10 | `legacy` | 这些版本检测到 `java_init.list` 时**不回落** `xposed_init`,装 modern 变体会不加载 |

> 覆盖安装会把模块启用状态重置一次:升级后需重新启用并重启。

## 头十分钟

1. 应用列表搜索目标 App → 点进去(这一页是监控枢纽)。
2. 「监听设置」里打开想看的类别(例如「数据库」)。
3. 回到目标 App 做点事,触发一次查询。
4. 回监控枢纽 → 对应入口行进入记录列表;点一条记录看详情(整页 markdown,带目录、可导出 JSON)。
5. 想改行为而不是只看:同一应用页的「功能设置」按功能卡开关;「设置 · 数据管理」看总量、按类清理。

「监听」是被动采集,不改目标行为;「功能」会改。两者是两页、两套开关、互不越界。

## 能力清单(与 App 内两页逐类对齐)

类别总数 88:监听 43 / 功能 45。单一登记处是 `CategoryTaxonomy`,
`CapabilityMapTest` 冻结这个口径,`FeatureOrphanGateTest` 从发射面反向钉它
——登记了、能勾选、但注入侧永不兑现的条目一律判红。逐类明细见 [docs/capability-inventory.md](docs/capability-inventory.md)。

### 监听域(记录 = 目标应用做过什么)

| 记录类型 | 类别数 | 捕获内容 |
|---|---|---|
| 算法 | 11 | `MessageDigest` / `Cipher` / `Mac` / `Signature` / 密钥参数·生成·工厂·库·协商 / `Random` / Base64;JS 侧 WebCrypto 归 WebView |
| 数据库 | 5 | 库打开 / 查询(bindArgs + 结果集)/ DML / exec / 事务 —— 只在目标 App 自己迭代游标之后逐行采集,绝不替它移动游标 |
| 行为 | 7 | 文件读·写·删·重命名·列目录 + Assets 资源 + Shell 命令;正文预览仅限「随调用即可得」的整读 API,流式路径不碰句柄 |
| 偏好 | 5 | SharedPreferences 打开 / 读 / 写 / 删 / 清空(含文件名与 key) |
| WebView | 5 | 页面加载 / JS 执行 / JS 桥注册 / 调试开关 / JS 密码学 |
| 系统信息 | 3 | Logcat / 应用元数据 / 包签名 |
| 网络请求 | 1 | `HttpURLConnection` / OkHttp 的 URL、方法、头、状态 |
| 环境检测观测 | 2 | Root 检测监控 / 模拟器检测监控(只记录它在查什么,不改结果) |
| UI 观测 | 4 | 弹窗展示 / 控件文本 / Activity 起止 |

每条记录含:类别与操作、密钥·IV·输入·输出(hex + 可打印文本双态,超限截断)、线程、
过滤过模块与框架帧的调用堆栈、AndroidKeyStore 密钥别名(硬件密钥不可导出时只有别名)。

### 功能域(干预 = 改变目标应用看到的世界)

| 功能卡 | 类别数 | 做什么 |
|---|---|---|
| 弹窗关键词拦截 | 1 | 命中关键词库的对话框直接拦截(全局库 + 每应用子集) |
| 进程守护 | 3 | 退出拦截 / 崩溃预防 / 解除录屏限制 |
| SSL 校验绕过 | 6 | `SSLContext` / `X509TrustManager` / OkHttp 证书固定与校验 / WebView / 网络安全配置 |
| 代理与 VPN 隐藏 | 8 | 系统属性 / ConnectivityManager / Settings / OkHttp 代理 + VPN 四路 |
| Root 与 Xposed 隐藏 | 12 | Root 文件·命令·包查询·属性;Xposed 类·堆栈·maps;`/proc` 状态·挂载·cgroup·网络 |
| 设备伪装 | 15 | 模拟器特征(Build 伪造 / 设备文件 / 传感器 / 网络与 MAC / 电话 / CPU / 电池)+ 按画像驱动的 8 个改写族 |

一切干预动作统一过「总闸 ∧ 类别闸」双门控:开关全关 = 零副作用,连安装期的静态字段改写都不发生。

### 伪装画像:能选就不让你手打

41 项 `DeviceProfile` 字段构成一份画像,存在本机的画像库里,可编辑、校验、导入导出、按应用绑定,
也可以只覆写其中某几项。取值域有限的项一律走选择器:

- **机型库**(26 品牌 / 8533 机型)一次写全 BRAND / MANUFACTURER / MODEL / DEVICE。
  `PRODUCT` / `BOARD` / `HARDWARE` **恒不猜** —— 上游数据不提供这三个值,凑一个就是造一台不存在的设备。
  非 Android 平台的记录(iPhone / iPad 等)在选择器可达集里直接剔除:写进 `Build.BRAND` 就是伪造。
- **运营商库**(1597 条)一次写全 PLMN / 运营商名 / 国家码;国家码不是恰好两个字母就不写。
- **Android 版本**选择器一次写全 release 与 API level(二者不得分开可写);
  密度档 / 核数档 / 语言 / SIM 国家 / 时区走平台表,并且恒留「改为手动填写」这一项。
- 内置九档机型画像只填机型身份,不连带改运营商与坐标;整组随机按「同机型 + 同世代 + 同运营商」派生。

> **一条不粉饰的边界**:画像库负责取值 / 校验 / 编辑 / 选型 / 覆写 / 传输 / 绑定 / 下发;
> 注入侧则**按族逐个接管**。已接管的族,值源就是画像正文;**尚未接管的族仍走接管前的旧通路**——
> 那些字段你能编辑、能下发,但当前没有钩子消费它们。
> 所以「画像已生效」这句话只对已接管的族为真。
> 这条边界在 App 里画像库页顶部**明写在屏上**,名单从代码里的族枚举派生,不是写死的文案,
> 因此每接管一批它就自己更新一次。

### 服务与自动化

- **MCP 服务**:进程内常驻 Streamable HTTP(默认 1510,单端点 `POST /mcp`),对 Agent 暴露 50 个工具,
  与 UI 能力逐行对照(`McpToolParityTest` 冻结工具面)。多令牌认证是独立开关,默认停用;
  一旦启用即**全接口**强制 Bearer(含回环)。令牌的创建与查看只在 UI 里 —— 让 Agent 管理访问自己的令牌是引导悖论。
- **wire 配置通道**:宿主与注入进程之间逐会话解析有效配置(内置推荐默认 ⊕ 每应用稀疏二态偏差),定向推送。
- **诊断页**:框架与作用域 / 运行中的目标进程 / 管道统计 / 一键复制快照(令牌值恒不进快照)。

## 从源码构建

前置:JDK 17+、Android SDK platform 36、build-tools ≥ 34。

```bash
./gradlew :app:assembleModernDebug     # 默认变体(Vector / libxposed)
./gradlew :app:assembleLegacyDebug     # legacy 变体(旧框架)
./gradlew :app:testModernDebugUnitTest # 单元 + Robolectric(含全部架构门禁)
./gradlew :app:lintModernRelease       # lint(基线 43 warning / 1 hint / 0 error,不许新增)
```

Windows 上用 `.\gradlew.bat ...`。签名不需要配置:缺 `dev-local/secrets/keystore/keystore.properties`
时产出未安装包,构建不会红。跑一次 `scripts/build-all.ps1` 会自动生成本地签名密钥。

本地素材(密钥、构建产物、日志、验收台账、开发草稿)统一放在 `dev-local/`,该目录不入库。

## 项目结构

```
app/src/main        宿主 UI(Compose)+ 数据层(Room)+ MCP 服务 + 注入侧钩子
app/src/modern      现代框架入口元数据(java_init.list)
app/src/legacy      legacy 框架入口元数据(module.prop / xposed_init)
app/src/test        JVM + Robolectric:单元测试,以及 architecture/ 下的架构门禁
app/src/androidTest 设备插桩用例(需要 root + 框架 + 测试靶 App)
testtarget/         独立的测试靶 App(设备矩阵的触发表层)
docs/               人读文档;一次性任务书的历史在 docs/history/
scripts/            构建、i18n 生成器、设备验收矩阵
```

架构与不变量的权威说明:[AGENTS.md](AGENTS.md)(面向维护者与编码 agent)、
[docs/DUAL_FRAMEWORK_SUPPORT.md](docs/DUAL_FRAMEWORK_SUPPORT.md)(双框架入口)、
[docs/xposed-api-compat.md](docs/xposed-api-compat.md)(双 API 骨架契约)、
[docs/record-layer-redesign.md](docs/record-layer-redesign.md)(记录层)。
贡献规则与踩坑清单在 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 限制与隐私

- 目标应用未被注入、或宿主未运行时不产生记录;注入侧的自身日志在 Xposed log。
- AndroidKeyStore 硬件密钥材料不可导出,只记录别名。
- native 层算法调用(例如 libcrypto 的 EVP inline hook)捕获不到。
- 宿主与注入进程之间只走回环,不采集其他进程数据。所有记录只写本机 Room 数据库。
- 真 legacy 框架(LSPosed 1.8.6 / EdXposed)只支持到 Android 13。

## 贡献

先看 [CONTRIBUTING.md](CONTRIBUTING.md)。一句话摘要:这个仓库把架构约束写成可执行的测试
(`app/src/test/.../architecture/`),改之前先读它们要什么,改之后跑它们。

## 许可

- **代码**:Apache License 2.0(见 [LICENSE](LICENSE))。
- **随包数据集**:另受各自许可约束,见 [NOTICE](NOTICE)。其中机型库
  `app/src/main/assets/devices.json` 来自 KHwang9883/MobileModels-csv,是
  **CC BY-NC-SA 4.0(非商业 + 相同方式共享)**。只要它随包分发,整份发布物就**不得商用**,
  这一条优先于代码的 Apache 授权。需要商用请先移除或替换该资产。
- 运营商库 `carriers.json` 为 MIT(pbakondy/mcc-mnc-list)。

## 致谢

KHwang9883/MobileModels-csv(机型)、pbakondy/mcc-mnc-list(运营商)、
Guise Reborn(内置数据的对账基线)、mikepenz 的 markdown 渲染器与 aboutlibraries、
libxposed 与 Xposed API、nanohttpd、Highlights。

## 免责声明

本软件用于在你自己拥有的、已 root 的设备上观察与调试应用行为。把它用于绕过他人的安全控制、
规避风控或任何未经授权的环境,不在作者的支持范围内,后果由使用者承担。
