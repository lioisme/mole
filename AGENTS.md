# AGENTS.md

Modev — Kotlin/Compose (Material3) Xposed 模块,算法监听(加解密/签名/密钥/BASE64/WebView)+ 宿主双进程日志。
构建与质量契约(2026-08-19 审计沉淀,V17)。

## 构建 / 测试(Windows,gradlew.bat)

```powershell
# 全量单测(含 Robolectric:Room / LocaleManager / 资源一致性 / 数值格式等)
.\gradlew.bat :app:testModernDebugUnitTest

# 指定测试
.\gradlew.bat :app:testModernDebugUnitTest --tests "com.lioisme.modev.i18n.*"

# 打包(modern 双框架变体;legacy 用于旧 LSPosed;build-all.ps1 打 4 个 APK)
.\gradlew.bat :app:assembleModernRelease :app:assembleModernDebug

# 设备 UI 测试(编译 + 安装运行;需已连接设备;指定类可传
# -Pandroid.testInstrumentationRunnerArguments.class=类A,类B,单方法用 类#方法)
.\gradlew.bat :app:assembleModernDebugAndroidTest
.\gradlew.bat :app:connectedModernDebugAndroidTest

# lint 门禁(基线:38 warning / 0 error;提交前必须跑,禁止新增 warning)
.\gradlew.bat :app:lintModernRelease
```

- 前置:JDK 17+(本机 21)、SDK android-36、build-tools ≥34(见 build-all.ps1)。
- 变体名:`modern`(默认,Vector 现代加载)/ `legacy`(Xposed API 82)。
- 设备 UI 测试后 AGP 会卸载被测 APK;失败详情在
  `app/build/outputs/androidTest-results/connected/debug/flavors/modern/TEST-*.xml`
  (PowerShell `[xml]` 解析,`failure` 是字符串属性)。

## 词汇表(全 App 统一,禁止旧词残留)

| 词 | 含义 | 禁用旧词 |
|----|------|----------|
| 记录 record(s) | 算法监听捕获条目 | 日志 log(s) |
| 运行中 Running | 进程存活态 | 在线 Online |
| 已作用域 / 未作用域 | Hook 作用域态 | 在作用域 / 未在作用域 |
| 已接收 / 丢弃 / 被拒 / 写入失败 / 超容量裁剪 | 统计区文案(原因括号后缀) | 队列溢出丢弃等 |

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
   **ReadMoreText(com.webtoonscorp)预览的语义文本仅含可见片段且「… 展开更多」
   并入同一节点**——插桩断言用短前缀 substring 匹配,勿对全量内容做精确匹配;
   长区块「收起」按钮在内容末尾,断言/点击前须 `performScrollToNode`。
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
7. **Xposed 模块识别双通道**:`isXposedModule` 判定 = 通道 A(`installedApplications`
   一次 `GET_META_DATA` 取 meta-data 含 `xposedmodule` 或 `xposedminversion`,
   零额外 binder)→ 未命中包走通道 B(惰性 zip 只读中央目录查
   `assets/xposed_init` / `META-INF/xposed/java_init.list`,异常降级非模块,
   按包缓存,随 `refresh()` 失效)。`AppListSettings.showXposedModules` 默认
   **false**(模块行默认隐藏,Modev 自身亦隐藏);测试需先 `setShowModules(true)`
   并复位 `setOnlyScoped(false)`(仅作用域按进程过滤会藏行,勾选后持久化,
   跨用例污染)。
8. **筛选菜单语义**:M3 `DropdownMenuItem` 整项 `MergeDescendants` 合并文本与
   OnClick;只读 `Checkbox(onCheckedChange=null)` 不暴露 `ToggleableState`
   语义——勾选态断言不可行,改行为级断言(点击项 → 行现/行隐)。
9. **RecordFilter 查询契约**:`LogRepository.paged(filter)` / `counts(filter)`
   唯一入口(REST/MCP 未来仅依赖 `LogRepository` + `RecordType` + `RecordFilter`);
   `recordType` 可空 = 全部类型。DAO `observeCountByFilter`/`observePagedRowsByFilter`
   用哨兵计数语义(0=过滤关闭,1=启用),空集合须传哨兵值 + `toBindList()`;
   `row.toRowModelOrNull()` 对非法 kind/operation 枚举名返回 null(行被丢弃,
   种子数据必须用合法枚举,如 `MESSAGE_DIGEST`/`CIPHER`/`MAC` +
   `DIGEST`/`ENCRYPT`/`SIGN`)。
10. **时间线分组契约**:`TimelineGroup.secondKey`/`dayKey`/`formatSecond`/
    `formatDay`(纯函数,跨日组头带日期前缀);组合侧邻行比较定组起始
    (`i==0 || prev==null || secondKey 不同`),列表手动索引循环
    (`record==null` 占位,`item(key="h-"+secondKey)` + `item(key=record.id)`);
    分页 prepend 边界 `prev==null` 不渲染头。插桩断言组头须
    `hasTestTag("timeline-header") and hasAnyDescendant(hasText(...))` 限定
    (同秒组头与行尾时间同串,裸 `hasText` 会多命中)。

## 已知工具链坑

- **Kotlin `-no-jdk` 下 `java.text.NumberFormat.getCompactNumberInstance` 编译期
  Unresolved**(内嵌 JDK 11 类元数据;AGP `--system jdkImage` 同坑)。
  方案:紧凑数字用 `android.icu.text.CompactDecimalFormat`(API 24+);
  相关单测须 Robolectric(纯 JVM 下 android.icu 是桩)。
- 新增 Java 源:AGP javac 同样受 `--system jdkImage` 限制,勿依赖 JDK 12+ 平台 API。
- 本 Compose 测试库无 `hasToggleableState`/`waitUntil`/`hasText(Regex)` 与
  `SemanticsProperties.OnClick`(用 `SemanticsActions.OnClick`);
  `onRoot`/`printToLog`/`onFirst` 是扩展需显式 import;`onRoot()` 在弹窗打开时
  有 2 个 root 会报错,须 `onAllNodes(isPopup())`。
- 并行跑两个 gradle 任务可能触发守护进程 OOM(「daemon disappeared」),lint 等
  重任务串行执行。

## 测试金字塔(新增行为必须有测试)

- 纯函数 → `app/src/test` 普通 JUnit(ChunkedText / Hex / SectionRenderMode /
  TimelineGroup / RecordType…)。
- Android 框架依赖 → Robolectric(`@Config(sdk=[34])`,resources + android.icu 可用)。
- 组合/导航/手势 → `app/src/androidTest` Compose UI 测试(实机或模拟器)。
