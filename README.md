# Modev

在 Xposed 作用域内**监听**目标应用(算法 / 数据库 / 行为 / 偏好 / WebView / 系统信息)并**干预**其行为(弹窗拦截 / SSL 绕过 / 代理与 VPN 隐藏 / Root·Xposed·模拟器伪装 / 进程守护)的模块,配套宿主 App 浏览记录、逐项配置与画像伪装。**同一 APK 双框架兼容**:现代 Vector(libxposed API 102)与旧版 LSPosed(legacy API 82+)。

- 模块包名:`com.lioisme.modev`(注入目标进程,捕获与干预)
- 宿主 App 与模块同 APK(自身运行即为宿主,查看记录与配置)
- 通信:回环 TCP,wire 配置通道默认端口 **1511**、MCP 服务默认端口 **1510**(外部访问开关决定绑定 `127.0.0.1` 还是 `0.0.0.0`)

## 功能分类(与 App 内两页一一对应)

产品只有两个能力域:**监听**(被动采集,只读,不改目标应用行为)与**功能**(主动干预,会改变目标应用行为)。App 内每应用两页 ——「监听设置」/「功能设置」—— 与下表逐类对齐,类别总数由 `CategoryTaxonomy` 单点登记(当前 81 类:监听 44 / 功能 37)。

### 监听域(记录 = 目标应用做过什么)

| 记录类型 | 类别数 | 捕获内容 | 入口 |
|---|---|---|---|
| 算法 | 12 | `MessageDigest` / `Cipher` / `Mac` / `Signature` / 密钥参数·生成·工厂·库·协商 / `Random` / Base64 / WebView JS 密码学(算法名、密钥、IV、输入输出 hex+文本双态) | 每应用「算法记录」 |
| 数据库 | 5 | 库打开 / 查询(bindArgs + 结果集)/ DML / exec / 事务 —— 仅在目标 App 自身迭代游标之后逐行采集 | 「数据库记录」 |
| 行为 | 7 | 文件读·写·删·重命名·列目录 + Assets 资源 + Shell 命令;内容预览仅限「随调用即可得」的整读 API | 「行为记录」 |
| 偏好 | 5 | SharedPreferences 打开 / 读 / 写 / 删 / 清空(含文件名与 key) | 「偏好记录」 |
| WebView | 5 | 页面加载 / JS 执行 / JS 桥注册 / 调试 / JS 密码学 | 「WebView 记录」 |
| 系统信息 | 3 | Logcat / 应用元数据 / 包签名 | 「系统信息记录」 |
| 网络请求 | 1 | `HttpURLConnection` / OkHttp 请求观测(URL、方法、头、状态) | 「网络环境记录」 |
| 环境检测观测 | 2 | Root 检测监控 / 模拟器检测监控(记录目标在查什么,不改结果) | 「设备环境记录」 |
| UI 观测 | 4 | 弹窗展示 / 控件文本 / Activity 起止 | 「UI交互记录」 |

每条记录含:算法/操作阶段、密钥·IV·输入·输出(hex + 可打印文本双态,超限截断)、线程、调用堆栈(已过滤模块与框架帧)、AndroidKeyStore 密钥别名(硬件密钥不可导出时)。详情页以单一 markdown 文档渲染,多表示字节聚合成代码组标签页。

### 功能域(干预 = 改变目标应用看到的世界)

| 功能卡 | 类别数 | 做什么 |
|---|---|---|
| 弹窗关键词拦截 | 1 | 命中关键词库的对话框直接拦截(全局库 + 每应用子集) |
| 进程守护 | 3 | 退出拦截 / 崩溃预防 / 解除录屏限制 |
| SSL 校验绕过 | 6 | `SSLContext` / `X509TrustManager` / OkHttp 证书固定与校验 / WebView / 网络安全配置 |
| 代理与 VPN 隐藏 | 8 | 系统属性 / ConnectivityManager / Settings / OkHttp 代理 + VPN 四路 |
| Root 与 Xposed 隐藏 | 12 | Root 文件·命令·包查询·属性,Xposed 类·堆栈·maps,系统属性与 `/proc` 状态·挂载·cgroup·网络 |
| 模拟器与设备伪装 | 7 | Build 伪造 / 设备文件 / 传感器 / 网络与 MAC / 电话身份 / CPU / 电池 |

所有干预动作统一过 `featureOn(kind)` 双闸(master ∧ kind):开关全关 = 零副作用(连安装期静态字段改写都不发生)。

### 画像与设备选型

41 项 `DeviceProfile` 字段构成伪装画像,存于画像库(Room `spoof_profile`,v15),可编辑、校验、导入导出、按应用绑定。**取值域有限的项一律选而非填**:

- **机型库** `assets/devices.json`(26 品牌 / 8533 机型)一次写全 BRAND / MANUFACTURER / MODEL / DEVICE;`PRODUCT` / `BOARD` / `HARDWARE` 恒不猜
- **运营商库** `assets/carriers.json`(1597 条)一次写全 PLMN / 运营商名 / 国家码
- **Android 版本**选择器一次写全 release 与 API level;**密度档 / 核数档 / 语言 / SIM 国家 / 时区**走平台或事实表选择器,并恒留「改为手动填写」
- 内置九档机型画像(Pixel 6 / 6a / 7 Pro / 8、Xiaomi 13 / 14 Ultra、Galaxy S23 / S24 Ultra、OnePlus 11),只填机型身份、不连带改运营商与坐标
- 整组随机按「同一机型 + 同一世代 + 同一运营商」派生,fingerprint 与三元组互推一致

> **当前边界(不粉饰)**:画像库负责取值 / 校验 / 编辑 / 选型 / 传输 / 绑定 / 下发,但注入侧的逐字段改写仍全部走接管前的 `SpoofProfiles` 通路 —— 即画像库的正文尚未成为靶程序实际读到的值。画像库页顶部会把这条边界明写在屏上。逐类接管须与 kind 登记、操作枚举、钩子、冻结计数、MCP enum 同批原子落地(见 `AGENTS.md` #30、`TASK-V47-SPOOF-PLAN.md` WP-3c/3d)。

### 服务与自动化

- **MCP 服务**:进程内常驻 Streamable HTTP(默认 1510,单端点 `POST /mcp`),对 Agent 暴露 50 个工具,与 UI 能力逐行对照(`McpToolParityTest` 冻结);多令牌认证独立开关,默认停用,启用后全接口强制 Bearer;令牌管理只留在 UI(引导悖论 + PII)
- **wire 配置通道**:宿主 ↔ 注入进程逐会话解析有效配置(内置默认 ⊕ 每应用稀疏二态偏差),定向推送,协议 VERSION 恒 4
- **诊断页**:框架与作用域 / 运行中目标 / 8 项管道统计 / 一键复制快照(令牌值恒不进快照)

## 安装

1. 设备已 root,安装以下任一框架:
   - **Vector 2.x**(libxposed API 102,现代入口 `ModevModule`),或
   - **旧版 LSPosed 1.8.x / EdXposed / LSPosed 1.10.x**(legacy 入口 `LegacyModuleEntry`,`xposedminversion=82`)
2. 按框架选择构建变体:
   - Vector 现代加载:`assembleModernDebug`(默认,`java_init.list` + `minApiVersion=102`)
   - legacy 框架:构建 **legacy 变体** `assembleLegacyDebug`(无 `java_init.list`,`minApiVersion=82`;该变体在 Vector 的 legacy 路径同样可用)
3. 安装 APK,在框架管理器中启用模块,并在 Modev 内对目标应用打开**「作用域此应用」**(或直接勾框架作用域)
4. 重启后生效(或使用作用域内"重新启动")

> **覆盖安装提示**:框架会在同包名覆盖安装时重置模块启用状态,升级后需重新启用作用域并重启。
> **双入口互斥**:框架存在 `java_init.list` 时按现代模块加载并忽略 `xposed_init`,否则走 legacy 入口;两者共用同一套 Hook 引擎与记录链路,不会重复注入。注意 LSPosed 1.9/1.10 检测到 `java_init.list` 时**不回落** `xposed_init`,故 legacy 框架请使用 legacy 变体。

## 使用

1. 打开 Modev App → 根「应用」页(搜索 + 筛选,行尾勾选框 = 作用域此应用)→ 点进目标应用
2. 应用页(监控枢纽):顶部状态胶囊与作用域开关 → 「监听设置」按类别开关 → 「功能设置」按功能卡开关干预
3. 使用目标 App 触发行为;从应用页的对应入口行进入记录列表(按应用×类型),或从「设置 · 数据管理」进入按类型×全部应用的全局列表
4. 记录详情:markdown 全文渲染 + 目录导航 + 导出 JSON;搜索条内可「清空本类」
5. 「设置」下分:外观与语言 / 数据管理 / 服务 / 诊断 / 功能域(关键词库 · 伪装画像 · 检测黑名单)/ 关于

## 限制

- 模块未注入(未勾选作用域)或宿主未运行时,不产生日志;注入侧记录在 Xposed log
- AndroidKeyStore 硬件密钥材料不可导出,仅记录密钥别名
- native 层算法调用(如 libcrypto EVP inline hook)无法捕获
- 宿主与注入进程间仅 loopback 通信,不采集其他进程数据
- legacy 入口(LSPosed ≤93)仅在旧框架下生效;现代 LSPosed 1.9/1.10(API 100/101)检测到 `java_init.list` 时不回落 `xposed_init`,需使用 legacy 变体;真 legacy 框架(LSPosed 1.8.6 / EdXposed)仅支持 Android ≤13

## 开发

- 技术栈:AGP 8.13 / Gradle 8.13 / Kotlin 2.2 / Jetpack Compose(Material3)/ Room / Navigation-Compose / Gson
- 架构:注入侧(core + hook)→ TCP 通道 → 宿主侧(data + ui)
- 任务清单与设计文档:`docs/DEVELOPMENT.md`、`docs/DUAL_FRAMEWORK_SUPPORT.md`(双框架兼容设计)

```bash
./gradlew assembleModernDebug     # 默认 APK(Vector 现代,libxposed 102)
./gradlew assembleLegacyDebug     # legacy 变体(旧框架/legacy 路径)
./gradlew testModernDebugUnitTest testLegacyDebugUnitTest   # 单元测试
./gradlew lintModernDebug lintLegacyDebug                   # lint 检查
```