# AGENTS.md

Modev — Kotlin/Compose (Material3) Xposed 模块,算法监听(加解密/签名/密钥/BASE64/WebView)+ 宿主双进程日志。
构建与质量契约(2026-08-18 审计沉淀,V16)。

## 构建 / 测试(Windows,gradlew.bat)

```powershell
# 全量单测(含 Robolectric:Room / LocaleManager / 资源一致性 / 数值格式等)
.\gradlew.bat :app:testModernDebugUnitTest

# 指定测试
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.i18n.*"

# 打包(modern 双框架变体;legacy 用于旧 LSPosed;build-all.ps1 打 4 个 APK)
.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug

# 设备 UI 测试(编译 + 安装运行;需已连接设备)
.\gradlew.bat :app:assembleModernDebugAndroidTest
.\gradlew.bat :app:connectedModernDebugAndroidTest

# lint 门禁(基线:38 warning / 0 error;提交前必须跑,禁止新增 warning)
.\gradlew.bat :app:lintModernRelease
```

- 前置:JDK 17+(本机 21)、SDK android-36、build-tools ≥34(见 build-all.ps1)。
- 变体名:`modern`(默认,Vector 现代加载)/ `legacy`(Xposed API 82)。

## 强约束(改了会挂测试/降级质量)

1. **i18n**:用户可见文本一律走 `values/strings.xml`(en)+ `values-zh/strings.xml`;
   新增键必须双语齐备、占位符形态一致(`StringResourceParityTest` 强制);
   `ui` 包源码字符串字面量禁止中文(`HardcodedStringConventionTest` 强制)。
   语言解析:`LocaleManager.resolveTag` = App 内显式选择优先,仅「跟随系统」
   时读系统 per-app locale;重建路径须经 `MainActivity.attachBaseContext` 调
   `LocaleManager.refreshForRecreation`;locale 敏感值用 `remember(context)`
   (包 Context 换实例,不残留旧 locale)。
2. **数值格式**:记录数/统计用 `AppCountFormat`(locale 驱动;zh ≥1万、其他 ≥1K
   平台紧凑格式 ≤1 位小数,以下千分位)。禁止自造单位(万+/千+/K+ 后缀)。
   资源格式串大数用 `%1$,d`。
3. **详情页双层渲染**:字节区块按 `sectionRenderMode(hexChars)` 分档——
   ≤128 KiB 字符 = 整段单 `SelectionContainer`(Tier A);超过 = 分块虚拟化
   (Tier B,`ChunkedText` O(1) 取块)。`BytesView.of` 有硬保险丝
   (16M hex / 8M text 字符),截断 hex 恒为纯前缀(截断标记由 UI 资源渲染);
   复制 >4M 字符、导出 >32M 字符拒绝并提示。
4. **输入即底部 sheet**:带键盘输入的弹层一律 `ModalBottomSheet`
   (`imePadding` + `navigationBarsPadding`,hide 动画后回调);
   仅危险确认/关于类保留居中 `AlertDialog`。
5. **弃用 API 零容忍**:新增源码一律不得引入已弃用 API;`getInstalledApplications`
   走 `core/util/PackageManagerCompat.kt` 统一入口(API 33+ `ApplicationInfoFlags`,
   旧系统弃用分支);`getLaunchIntentForPackage(String)` 全 API 无 flags 重载且从未
   弃用,直接使用即可。`lintModernRelease` 零新增(基线 38 warning/0 error,
   见上文构建命令)。
6. **计数文本复数**:en 带 `%d` 的计数文案用 `<plurals>`(one/other),zh 仅
   `other`;禁止 `<string>` 直接内嵌 `%d + 英文单词`(PluralsCandidate lint)。

## 已知工具链坑

- **Kotlin `-no-jdk` 下 `java.text.NumberFormat.getCompactNumberInstance` 编译期
  Unresolved**(内嵌 JDK 11 类元数据;AGP `--system jdkImage` 同坑)。
  方案:紧凑数字用 `android.icu.text.CompactDecimalFormat`(API 24+);
  相关单测须 Robolectric(纯 JVM 下 android.icu 是桩)。
- 新增 Java 源:AGP javac 同样受 `--system jdkImage` 限制,勿依赖 JDK 12+ 平台 API。

## 测试金字塔(新增行为必须有测试)

- 纯函数 → `app/src/test` 普通 JUnit(ChunkedText / Hex / SectionRenderMode…)。
- Android 框架依赖 → Robolectric(`@Config(sdk=[34])`,resources + android.icu 可用)。
- 组合/导航/手势 → `app/src/androidTest` Compose UI 测试(实机或模拟器)。
