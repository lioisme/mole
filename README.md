# Modev

抓取目标 App 全部算法调用(摘要 / 加解密 / 签名 / MAC / 密钥参数 / 密钥生成 / Base64)的 **Xposed 模块**,配套宿主 App 实时查看与搜索算法日志。**同一 APK 双框架兼容**:现代 Vector(libxposed API 102)与旧版 LSPosed(legacy API 82+)。

- 模块包名:`com.lioisme.modev`(注入目标进程,捕获调用)
- 宿主 App 与模块同 APK(自身运行即为宿主,查看日志)
- 通信:loopback TCP `127.0.0.1:46299`,JSON 行协议

## 功能

| 类别 | 捕获内容 |
|---|---|
| 消息摘要 | `MessageDigest` getInstance / update / digest(算法、输入、输出) |
| 加解密 | `Cipher` getInstance / init / update / doFinal / getIV(密钥、IV、方向、明/密文) |
| MAC | `Mac` getInstance / init / update / doFinal(密钥、算法) |
| 签名 | `Signature` getInstance / initSign / initVerify / update / sign / verify(密钥、签名值、验签结果) |
| 密钥参数 | `SecretKeySpec` / `IvParameterSpec` / `GCMParameterSpec` / `PBEKeySpec` 构造 |
| 密钥生成 | `KeyGenerator` / `KeyPairGenerator` generateKey(KeyPair) |
| 密钥工厂 | `KeyFactory` / `SecretKeyFactory` generatePrivate / generatePublic / generateSecret |
| Base64 | `android.util.Base64` encode / decode(默认关闭) |

每条记录包含:算法名、操作阶段、密钥 / IV / 输入 / 输出的 **hex + 可打印文本双态视图**(超限截断)、线程、调用堆栈(已过滤模块与框架帧)、AndroidKeyStore 密钥别名(硬件密钥不可导出时)。

## 安装

1. 设备已 root,安装以下任一框架:
   - **Vector 2.x**(libxposed API 102,现代入口 `ModevModule`),或
   - **旧版 LSPosed 1.8.x / EdXposed / LSPosed 1.10.x**(legacy 入口 `LegacyModuleEntry`,`xposedminversion=82`)
2. 按框架选择构建变体:
   - Vector 现代加载:`assembleModernDebug`(默认,`java_init.list` + `minApiVersion=102`)
   - legacy 框架:构建 **legacy 变体** `assembleLegacyDebug`(无 `java_init.list`,`minApiVersion=82`;该变体在 Vector 的 legacy 路径同样可用)
3. 安装 APK,在框架管理器中启用模块,勾选作用域:**自身**(作为宿主查看日志)+ 目标 App
4. 重启后生效(或使用作用域内"重新启动")

> **覆盖安装提示**:框架会在同包名覆盖安装时重置模块启用状态,升级后需重新启用作用域并重启。
> **双入口互斥**:框架存在 `java_init.list` 时按现代模块加载并忽略 `xposed_init`,否则走 legacy 入口;两者共用同一套 Hook 引擎与日志链路,不会重复注入。注意 LSPosed 1.9/1.10 检测到 `java_init.list` 时**不回落** `xposed_init`,故 legacy 框架请使用 legacy 变体。

## 使用

1. 打开 Modev App → 底部「应用」页:选择已勾选作用域的目标 App
2. 在详情页按需开启八类算法开关(改动即时推送,无需重启)
3. 使用目标 App 触发算法调用
4. 切到「日志」页:按包过滤、按分类筛选、关键字搜索、点击记录查看完整数据与调用堆栈
5. 「设置」页可清空全部日志;「应用」页详情内可清空指定 App 日志

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