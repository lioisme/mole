# Modev

抓取目标 App 全部算法调用(摘要 / 加解密 / 签名 / MAC / 密钥参数 / 密钥生成 / Base64)的 **LSPosed(libxposed API 102)Xposed 模块**,配套宿主 App 实时查看与搜索算法日志。

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

1. 设备已 root,并安装 **LSPosed v2.1.0+**(libxposed API 102,支持 Android 8.1+)
2. 构建或下载 `app-debug.apk`(模块与宿主一体)
3. 在 LSPosed 管理器中启用模块,勾选作用域:**自身**(作为宿主查看日志)+ 目标 App
4. 重启后生效(或使用作用域内"重新启动")

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

## 开发

- 技术栈:AGP 8.13 / Gradle 8.13 / Kotlin 2.2 / Jetpack Compose(Material3)/ Room / Navigation-Compose / Gson
- 架构:注入侧(core + hook)→ TCP 通道 → 宿主侧(data + ui)
- 任务清单与设计文档:`docs/DEVELOPMENT.md`

```bash
./gradlew assembleDebug        # 构建 APK
./gradlew testDebugUnitTest    # 单元测试
./gradlew lintDebug            # lint 检查
```