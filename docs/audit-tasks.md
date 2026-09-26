# Modev 质量审计与改造任务文档

> 基线日期:2026-08-18 | 范围:全量审计(ui / i18n / core.util / data / hook / 测试)
> 验收标准:代码质量与架构设计达到 Apple / Google 发布级 —— 功能契约可测试、无已知崩溃路径、
> 性能有预算、i18n 全链路即时生效、命名与数值符合行业规范。

## 任务状态总表

| 编号 | 任务 | 优先级 | 状态 | 备注 |
|------|------|--------|------|------|
| T1 | 算法详情页整段选择(Tier A 128KiB + Tier B 回退) | 高 | ✅ 完成 | `sectionRenderMode` + 单测通过;ByteSectionTest 按档位重写;三源码集编译通过 |
| T2 | i18n:优先级反转 / 重建重解析 / locale-key / 截断后缀外置 + 资源一致性测试 | 高 | ✅ 完成 | `resolveTag` 显式选择权威;`refreshForRecreation` 双点刷新;timeFmt 改 remember(context);`BytesView.of` 去硬编码后缀;LocaleManagerTest 重写(乱码注释清除);新增 StringResourceParityTest(双语键互备+占位符一致)与 HardcodedStringConventionTest(ui 包字面量禁中文);单测全绿 |
| T4 | 详情页性能:硬保险丝 / params remember / 复制导出护栏 / 边界测试 | 高 | ✅ 完成 | `BytesView` 硬顶 16M hex/8M text(+单测);params 拼接 remember(record);copyFull >4M 字符、导出 >32M 字符护栏 + 双语提示;全量单测通过 |
| T3 | 带输入对话框改造为底部 sheet | 中 | ✅ 完成 | MaxBytesDialog / IgnoreStackDialog → ModalBottomSheet(imePadding + navigationBarsPadding,hide 动画后回调);危险确认/关于框保持居中 AlertDialog;编译通过 |
| T5 | 数值格式规范化(NumberFormat compact + 千分位 + 命名) | 中 | ✅ 完成 | `AppCountFormat.compact(count, locale)` locale 驱动:zh ≥1万 / en ≥1K 平台紧凑(CompactDecimalFormat,≤1 位小数),以下千分位;删 apps_count_wan/qian;4 键 %1$,d 千分位;DiagnosticsScreen 统计 grouped();switch_mac → MAC / MAC (HMAC);测试重写(注:工具链 -no-jdk/jdkImage 裁剪致 java.text compact API 编译期不可解析,换 android.icu,单测转 Robolectric) |
| T6 | 工程卫生(编码修复 / 崩溃日志清理 / org 垃圾清理) | 低 | ✅ 完成 | ModevApp.kt:52 乱码注释修复(双编码转 UTF-8);hs_err/replay 崩溃日志删除(gitignore 已有规则);`org/` 下 Robolectric 影子类误提交已 git rm --cached + 删除 + gitignore 增 org/ |
| T7 | 规范沉淀(AGENTS.md / 构建验证) | 低 | 进行中 | 见 §T7 |

## 一、审计结论(2026-08-18)

### 1.1 算法详情页整段选择(需求 1)
- 现状:字节区块按 4096 字符分块(`ChunkedText.DEFAULT_CHUNK_CHARS`),每块独立包
  `SelectionContainer`(`RecordDetailScreen.kt` `byteSectionChunks`),一次手势最多选中单块;
  收起态预览无选择能力。
- 根因:性能与可选择性的人为取舍 —— 单一巨型 `Text` 在多 MB 数据下会 OOM/ANR;
  Compose 1.8 `SelectionContainer` 不支持跨 LazyColumn item 连续选择(平台限制)。
- 方案:双层渲染 —— Tier A(≤128 KiB 字符)整段单 `SelectionContainer` 原生选择;
  Tier B(>128 KiB)保留分块虚拟化 + 逐块选择 + 复制按钮承载全选语义。

### 1.2 i18n(需求 2)
| # | 缺陷 | 证据 | 后果 |
|---|------|------|------|
| A | 系统 per-app locale 恒优先于 App 内选择,重启回退 | `LocaleManager.resolveTag` | 重启后语言“变回去” |
| B | 系统 per-app locale 中途变更不生效(仅进程启动读取) | `LocaleManager.init` + `MainActivity.attachBaseContext` 不重解析 | 必须杀进程重启 |
| C | locale 敏感值被 `remember` 缓存(`detailTimeFmt`) | `RecordDetailScreen.MetadataBlock` | 切换后时间文本不变 |
| D | `BytesView.of` 把 `"…(+N bytes)"` 硬编码烧进数据 | `BytesView.kt` | 中文界面出现英文 |

- 合规已有项:localeConfig 声明、supportsRtl、双语资源齐全、技术词保留拉丁、零重建热切换 + 测试。

### 1.3 带输入对话框(需求 3)
- 现状:`MaxBytesDialog` / `IgnoreStackDialog` 为居中 `AlertDialog`(`AlgorithmMonitorScreen.kt`);
  设置页选择类已用 `ModalBottomSheet`。危险确认/关于框无输入,居中属 M3 规范,保留。
- 方案:两个带输入对话框改造为底部 sheet(键盘贴底、拇指可达),与现有 sheet 体系统一。

### 1.4 详情页性能(需求 4)
- 现状(良好):LazyColumn 逐区块虚拟化、`ChunkedText` O(1) 惰性取块、收起态有界预览、
  导出后台构建 + SAF 流式写、Room 流式订阅 + 邻接投影;协议侧 `WIRE_MAX_LINE_CHARS=4MB` 天然限幅。
- 剩余风险:P1 `BytesView.of` 无硬顶;P2 params 拼接在主线程;P3 locale 缓存(见 1.2-C);
  P4 复制/导出无体积护栏;P5 空数据/极端数据缺专项测试。

### 1.5 命名 / 数值 / 单位(需求 5)
- `AppCountFormat` 万/千/“+”体系不规范(`%1$d万+` / `%1$d千+`,整数相除无小数失真,如 12,345 → “1万+”);
- 数值无千分位分组(`bytes_count`、`settings_*_value`、stats);
- `switch_mac` 英文 “HMAC Mac” 冗余不规范;
- 技术词不强行翻译(key/IV/Salt/AAD/Base64)等已有约定合规,予以保留。

## 二、任务明细

### T1 算法详情页整段选择 — 完成

**方案**
- 新增纯函数 `sectionRenderMode(hexChars)` + 常量 `SELECTABLE_CAP_CHARS = 128 * 1024`:
  - ≤ 128 KiB 字符(Tier A):展开态整段合并为**单个 LazyColumn item** =
    单个 `SelectionContainer { Column { text 单 Text; hex 单 Text } }`,
    系统长按手柄 / 「全选」一次覆盖整段;短内容(≤4096)同样走单 item 全量展示。
  - > 128 KiB 字符(Tier B):保留分块虚拟化 + 逐块 `SelectionContainer` + 收起按钮
    (跨 item 连续选择为 Compose 平台限制,复制按钮承载全选语义)。
- 收起态预览(ReadMoreText)与折叠语义不变。

**验收**
- ≤128K 段:展开后整段为单一文本节点,长按出现系统选择手柄,「全选」命中整段;
- >128K 段:分块惰性组合,滚动不卡顿,复制按钮复制完整 hex;
- `sectionRenderMode` 边界单测;`ByteSectionTest` 按 Tier 档位重写。

**验证记录**
- `SectionRenderModeTest`(单测)覆盖 0 / 上限 / 上限+1 / Int.MAX;
- `ByteSectionTest`(androidTest 编译验证)重写 Tier A 用例,保留 300K 惰性滚动回归;
- `:app:testModernDebugUnitTest` 通过;`:app:assembleModernDebugAndroidTest` 编译通过。

### T2 i18n 热切换与跟随系统修复 — 完成

**方案**
- R1 优先级反转:`resolveTag` = 显式 App 内选择(pref ≠ system)权威;
  仅当 pref = system 时查系统 per-app locale;否则跟随系统。
- R2 重建重解析:`LocaleManager.refreshForRecreation(base)`:
  以 `Resources.getSystem()` 刷新 `systemDefaultLocale`(真系统语言,不受 App 覆盖污染);
  pref = system 时按系统 per-app locale 重设 `localeFlow`。
  `MainActivity.attachBaseContext` 先 refresh 再 wrap —— 系统面板改语言 / per-app locale
  后 Activity 重建即立即跟随,无需重启。
- R3 locale-key:`detailTimeFmt` 改为 `remember(context)`(包装 Context 随语言切换换实例);
  全 ui 包扫查无其他 locale 敏感 remember 残留。
- R4 截断后缀外置:`BytesView.of` 不再烧 `"…(+N bytes)"`(纯 hex + truncated 标志,
  详情页头部已用资源渲染截断标记);删除死代码 `BytesView.preview` 及其测试。
- 新增自动化防护:`StringResourceParityTest`(双语 key 全覆盖 + 占位符一致);
  `HardcodedStringConventionTest`(ui 包源码禁止中文字面量)。
- 同步修正 `LocaleManagerTest` 乱码注释与断言(删除固化错误优先级的行为)。

**验收**
- App 内选择重启不回退;系统 per-app locale / 系统语言变更后立即跟随(重建路径);
- 中英切换全站文本(含时间)同帧变更;详情页/列表页/设置页分别验证;
- 双语资源 key 与占位符一致;ui 包无硬编码中文。

**验证记录**
- `LocaleManagerTest` 重写 resolveTag 语义 + 新增 refreshForRecreation 用例,全部通过;
- `StringResourceParityTest` / `HardcodedStringConventionTest` 新增并通过;
- `BytesViewTest` 按无后缀语义重写并通过;
- `LanguageHotSwitchTest`(androidTest)保持零重建契约,编译通过。

### T4 详情页性能加固 — 完成

**方案**
- P1 硬保险丝:`BytesView.of` 增加不可配置 `HARD_MAX_HEX_CHARS = 16M` /
  `HARD_MAX_TEXT_CHARS = 8M`,无论调用方配置(含 0 = 无限)UI 层永不物化超限字符串;
- P2 params 拼接移出组合热路径:`remember(record)` 缓存(记录实例不变不重算);
- P3 `detailTimeFmt` locale-key(随 T2-R3);
- P4 护栏:复制载荷 > 4M 字符 → snackbar 提示改用导出;导出 JSON > 32M 字符 → 拒绝并提示;
- P5 边界测试:ChunkedText 空串/越界(已有)、BytesView 硬顶、空记录三态(已有 ViewModel 测试)。

**验收**
- 最大规模记录(7×2M 字符)打开无 OOM;展开态滚动不掉帧;空记录/全空字段即时渲染;
- 复制/导出超限被拒绝且有文案提示;无新增主线程大分配路径。

**验证记录**
- `BytesViewTest` 新增硬顶用例(9MB 输入 → hex 有界截断),通过;
- 复制/导出护栏走查编译通过;详情页空态/三态契约由既有 `RecordDetailViewModelTest` 覆盖。

### T3 带输入对话框底部化 — 完成

**方案**
- `MaxBytesDialog` / `IgnoreStackDialog` → `ModalBottomSheet`(标题 + 输入 + 按钮行,
  `imePadding` + `navigationBarsPadding`,退场动画 hide 后回调,沿用 SettingsScreen sheet 范式);
- 输入过滤语义不变(纯数字 ≤9 位 / 每行一个前缀去空去重);
- 危险确认 / 关于框保持居中 AlertDialog(M3 规范,无输入场景)。

**验收**
- 键盘弹出输入框贴底不被遮挡;确认/取消经 hide 动画后执行;语义与资源不变。

**验证记录**
- `:app:assembleModernDebug` / AndroidTest 编译通过;行为与资源由既有测试覆盖。

### T5 数值 / 单位 / 命名规范化 — 完成

**方案**
- `AppCountFormat.compact(count, locale)` 改为 locale 驱动的平台紧凑格式:
  - zh:≥ 10,000 → compact SHORT(1 位小数,输出 `1.2万` / `10万`);以下千分位原样;
  - en:≥ 1,000 → compact(`1.2K` / `1.5M`);以下千分位原样;
  - 删除 “3千+” / “1万+” 式失真缩写与 `apps_count_wan/qian` 资源;
  - 注:`java.text.NumberFormat.getCompactNumberInstance` 在 Kotlin `-no-jdk` 与
    AGP javac `--system jdkImage` 编译链下均编译期 Unresolved(工具链类元数据
    裁剪;实测),改用 `android.icu.text.CompactDecimalFormat`(API 24+,
    minSdk 26 恒可用;纯 JVM 单测为桩 → AppCountFormatTest 转 Robolectric);
- 全站千分位:`bytes_count` / `settings_*_value` / 诊断统计 → `%1$,d`
  (统计值经 `AppCountFormat.grouped`,与字符串资源 %1$,d 同源语义);
- `switch_mac`:en `HMAC Mac` → `MAC`;zh → `MAC (HMAC)`;
- 技术词不强行翻译约定保持不变。

**验收**
- zh 3,000 → “3,000”、12,345 → “1.2万”、100,000 → “10万”;
- en 999 → “999”、1,200 → “1.2K”、1,500,000 → “1.5M”;
- 全屏截图人工走查(列表徽标 / 详情页字节数 / 设置统计)。

**验证记录**
- `AppCountFormatTest` 重写(Robolectric,zh/en 阈值、小数、分组),全部通过;
- 中英双语 `%1$,d` 资源生效,`StringResourceParityTest` 同步通过;
- 全量单测 / `assembleModernDebug` / AndroidTest / legacy+release 编译全绿。

### T6 工程卫生 — 完成

**方案**
- 修复 `ModevApp.kt` 与 `LocaleManagerTest.kt` 乱码注释(UTF-8 重写);
- 删除已入库的 `hs_err_pid*.log` / `replay_pid*.log`(gitignore 已含规则);
- 删除根目录误提交的 `org/robolectric/.../ShadowPackageManager.class`,`org/` 加入 gitignore。

**验收**
- 全仓无乱码注释、无崩溃日志、无 .class 垃圾文件;git status 干净(仅预期变更)。

### T7 规范沉淀 — 完成

**方案**
- 新增 `AGENTS.md`:构建/测试命令、i18n 契约(零重建热切换、优先级、locale-key 规则)、
  数值格式规范、详情页双层渲染决策记录、工具链坑(-no-jdk / jdkImage 裁剪);
- 本文档即任务台账,逐项标记完成状态与验证记录。

**验证记录**
- `:app:testModernDebugUnitTest` 全量通过;`:app:assembleModernDebug` +
  `:app:assembleModernDebugAndroidTest` 通过;`compileLegacyDebugKotlin` +
  `compileModernReleaseKotlin` 通过(4 变体源码同源验证);字符串资源无 BOM,
  仓库无崩溃日志 / org 垃圾。

## 三、验证矩阵

| 层 | 用例 | 位置 |
|----|------|------|
| 单测 | Tier 判定边界、ChunkedText 边界、BytesView 硬顶、AppCountFormat 各 locale、resolveTag 优先级、refreshForRecreation、双语资源 key/占位符一致、ui 包无硬编码中文 | `app/src/test` |
| AndroidTest | 详情页整段选择/复制、sheet 输入框、语言热切换(零重建 + 即时全量切换) | `app/src/androidTest` |
| 手工 | 4 种语言场景 × 3 页切换、系统面板改 per-app locale 立即生效、最大记录/空记录、键盘遮挡 | 真机走查 |

## 四、非目标(明确不做)

- 不引入新语言(维持 en/zh);不引入 AppCompatDelegate 重建式语言切换(零重建架构更优且测试锁定);
- 不做跨 LazyColumn item 连续选择实验(平台限制,以 Tier 方案规避);
- 不改注入侧协议;不迁移存量数据(旧记录截断后缀按原样展示);
- 详情页不做按字段懒加载 DB 投影(最坏 ~28MB 在预算内,列为未来优化)。
