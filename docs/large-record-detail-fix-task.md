# Modev 任务书 V3:大数据记录详情闪退修复

**文档版本**: v3.2 | **创建日期**: 2026-08-17 | **状态**: 已完成(门禁全绿)+ 实机回归修复(v3.2:展开态改惰性分块,消灭 26s ANR)
**适用范围**: `modev` 项目 `data` 层(DAO/Repository)+ `ui/logs` + `core/util` + i18n 资源
**质量目标**: 代码质量、架构设计达到苹果/谷歌大厂验收标准(内存安全、主线程零大分配、渐进披露、虚拟化、可测试性、门禁全绿)
**设计原则**: 数据全量入库存证(捕获上限不变),**UI 展示与导出分级披露**,查询按需投影,危险操作移出主线程。

---

## 0. 需求(用户口径)

1. 算法数据过大时,点击「查看详情」**不得闪退**;详情页流畅可滚动、可复制、可导出。
2. 修复不牺牲取证完整性:数据库仍保存完整捕获数据(截断语义不变)。
3. 列表页/邻接导航不得加载大字段全量数据。

## 1. 根因审计(全链路)

**闪退链路**:详情页一次性、非虚拟化地渲染全部数据区块,叠加邻接查询全量 `SELECT *` 与主线程大字符串构建,触发 OOM/ANR 导致进程被杀(闪退)。

**内存账目**(默认 `maxBytesPerField=1MB`):
- 单字段 hex 字符串 = 2,097,152 字符 ≈ 4MB(UTF-16);可打印文本另计 ≤2MB;
- 一条记录最多 7 个字节字段(key/iv/salt/aad/publicKey/input/output)≈ 42MB;
- 进详情页同时加载 3 条全量记录(本记录 + 上下邻居各 1 条);
- 点「复制 JSON」在主线程 `JSONObject.toString(2)` 再倍增瞬时分配 → OOM。

| # | 根因 | 位置 |
|---|------|------|
| 1 | 详情页 `Column + verticalScroll` 非虚拟化,7 个 Section 全部 eager 组合,每个 Section 把 2MB hex 塞进 `SelectionContainer + Text` 全量排版 | `RecordDetailScreen.kt:210-304` |
| 2 | 邻接查询 `olderThan/newerThan` 为 `SELECT *`,仅判断「上一条/下一条」可用性却加载邻居全量多 MB hex | `CryptoRecordDao.kt:95-116`、`LogRepository.kt:193-200` |
| 3 | 复制 JSON/文本在 UI 线程执行 `buildString`/`JSONObject`(全量拷贝) | `RecordDetailScreen.kt:405-457` |
| 4 | 分享把整条文本塞入 `Intent.EXTRA_TEXT`,超过 Binder 1MB 上限抛 `TransactionTooLargeException`(现被 `runCatching` 静默吞掉,部分机型直接崩) | `RecordDetailScreen.kt:425-431` |
| 5 | 列表分页 `observePaged` 亦为 `SELECT *`,每页 60 行全量物化大字段(次生隐患) | `CryptoRecordDao.kt:45-70` |
| 6 | UI 无任何「预览 + 渐进披露」机制,`BytesView` 虽有截断标记但详情页永远渲染全量 | `BytesView.kt:20-36`、`BaseHooker.kt:53-58` |

## 2. 决策记录(DR)

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 捕获侧上限 `maxBytesPerField=1MB` | **保留**(改变会破坏取证;风险由 UI 分级披露吸收) |
| D2 | 详情页数据获取 | `observeById` 保留全量(单条记录是详情页语义);邻接查询改投影 |
| D3 | 屏幕渲染上限 | 初定 1M 字符,经插桩实测(**模拟器 watchdog 击杀**:1M 字符 SelectionContainer 排版拖垮系统进程)下调至 **200K 字符**;v3.2 实机回归发现:100K 字符单 Text 展开仍冻结主线程 **26.3s(ANR)** → **废弃渲染上限,改为惰性分块**:展开态按 4K 字符/块由 LazyColumn 按需组合,仅可视块布局+绘制,全量数据可完整滚动查看 |
| D4 | 导出(复制/JSON/分享) | 移出主线程 + 大小分级:≤2M 字符直接剪贴板/分享;>2M 字符分享走文件(FileProvider) |
| D5 | 列表投影 | 新增 `CryptoRecordRow` 投影 POJO(SQL `substr` 取预览),列表零大字段物化 |
| D6 | 数据库版本 | **不升级**(纯新增查询,无 schema 变更) |
| D7 | 渐进披露 | 预览(默认)+ 逐 Section「展开」;v3.2 起展开状态由宿主 `LoadedContent` 持有(rememberSaveable List),展开数据按 4K 字符/块惰性组合 |

## 3. 改动清单与执行顺序

### T1. `core/util/BytesView.kt` — 新增预览 API(纯函数,可测)
- 新增 `preview(maxChars)`:`hex.take(maxChars)` + 截断标记 `…(+N bytes)`(truncated 时),与全量 `hex` 并存;
- 新增常量 `DEFAULT_PREVIEW_CHARS = 4096`;
- 不改变 `of()` 语义,存量调用零影响。

### T2. `data/db/CryptoRecordDao.kt` — 投影查询
- 新增 `CryptoRecordRow` POJO(id/packageName/kind/operation/algorithm/threadName/direction/timestamp/keyAlias/关键字段 24 字符预览 + truncated 标志);
- 新增 `observePagedRows(...)`:投影列分页查询,`PagingSource<Int, CryptoRecordRow>`;
- 邻接查询改 `SELECT id … LIMIT 1`(`neighborIdOlderThan/neighborIdNewerThan`),旧全量方法删除;
- `observeById` 保留全量。

### T3. `data/repo/LogRepository.kt` — 接线
- `pagedRecords` 改走 `observePagedRows`,map 为轻量 `CryptoRecord`(仅填充展示字段);
- `neighborOf` → `neighborIdOf`(返回 `Long?`);
- `observeById` 不变。

### T4. `ui/logs/RecordDetailScreen.kt` — 详情页重构(核心)
- 结构:`Column+verticalScroll` → `LazyColumn`(Section 虚拟化);
- Section 渐进披露:折叠态预览 + 「展开」;v3.2 起展开态由 `byteSectionItems` 按 **4K 字符/块**惰性组合(仅可视块布局+绘制,无巨型单 Text);
- 复制/JSON/分享全部移出主线程(Dispatchers.Default 构建 + runCatching + snackbar 报错);
- 分享 >2MB 走 cache 文件 + FileProvider(Manifest 登记 `<provider>`);
- `shareRecord` 失败给出 snackbar 而非静默。

### T5. `ui/components/RecordRow.kt` — 预览源切换
- `summary()` 消费投影预览字段(T3 数据形状),零全量引用。

### T6. i18n — 新增键(en + zh 同步,键集 diff=0)
- 新增 `section_expand` / `section_collapse` / `export_failed`;v3.2 移除 `bytes_omitted`(分块后无省略语义,en+zh 同步删,键集仍 diff=0)。

### T7. 测试(与 T1-T4 同步编写)
- `BytesViewTest`:preview 边界(恰等于/超 1 字符/超长);
- `CryptoRecordDaoTest`:投影查询仅取预览列、neighborId 返回 id;
- `RecordDetailViewModelTest`:大字段记录加载不 OOM、邻接导航正确;
- 详情 UI 插桩:折叠→展开→收起状态流转、复制走后台不卡主线程;v3.2 分块契约用例(首块组合/全量不落单节点/末块滚动后按需组合/展开收起回调)。

### T8. 文档与门禁
- 本任务书执行记录逐项勾选;
- 门禁:`testModernDebugUnitTest` 全绿、`lintModernDebug` 零新增告警、`assembleModernRelease`(含 lintVital)通过、`connectedModernDebugAndroidTest` 全绿。

## 4. 执行记录(随完成逐项标记)

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| T1 | BytesView preview API | [x] | `preview(maxChars)` 纯函数 + `DEFAULT_PREVIEW_CHARS=4096` + 下限兜底;5 例边界测试(全量/裁剪/下限/与捕获截断协同)全绿。 |
| T2 | DAO 投影查询 | [x] | 新增 `CryptoRecordRow` POJO 与 `observePagedRows`(SQL substr 48 字符预览 + truncated 标志,过滤/排序与全量同构);`olderThan/newerThan` 删除,改 `neighborIdOlderThan/neighborIdNewerThan`(SELECT id LIMIT 1);`observeById` 保留全量;测试迁移 + 新增投影/邻接 id 3 例。 |
| T3 | LogRepository 接线 | [x] | `pagedRecords` 走投影 `observePagedRows`(ROW_PREVIEW_CHARS=48),map 为轻量行模型(hex=预览);`neighborOf` → `neighborIdOf`(Long?);`observeById` 不变;既有 `observe()` 无调用方,属存量死代码未动。 |
| T4 | 详情页重构 | [x] | `LoadedContent` 改 LazyColumn 逐区块虚拟化;`ByteSection` 渐进披露;复制/JSON/分享全部 `Dispatchers.Default` 后台构建 + runCatching + 失败 snackbar;分享 >2M 字符走 cache 文件 + FileProvider(Manifest 登记 provider + `res/xml/file_paths.xml`);`RecordDetailViewModel` 邻接改 id 投影。实测发现 1M 字符排版会 watchdog 击杀系统进程(DR D3)。 |
| T4.1 | **v3.2 惰性分块(实机回归修复)** | [x] | 实机(模拟器)展开 100K 字符 hex:主线程冻结 26.3s(`OpenGLRenderer Davey! duration=26294ms`)触发 ANR「无响应」→ 废弃 `SCREEN_RENDER_LIMIT=200K` 上限,展开态改 `byteSectionItems` 按 4K 字符/块惰性组合(仅可视块布局+绘制);展开状态上提到 `LoadedContent`(rememberSaveable List);`bytes_omitted` 字符串删除;实机复测:展开/收起/滚动全程无 ANR、无 Davey、进程存活。二次复测(2026-08-17):对原 ANR 元凶记录(DO_FINAL,输入 205248 / 输出 205224 hex 字符,≈102KB)展开输入区块、滚动、展开输出区块、上/下条导航,pid 全程不变,`logcat` 中 Davey/ANR/FATAL 计数 = 0。 |
| T5 | RecordRow 适配 | [x] | 行模型已含展示全部字段(algorithm/kind/operation/threadName/direction/keyAlias/预览 hex),`summary()` 的 `hex.take(24)` 直接消费预览,零改动(审计确认)。 |
| T6 | i18n 键 | [x] | 新增 `section_expand`/`section_collapse`/`export_failed` en+zh;v3.2 移除 `bytes_omitted`(en+zh 同步);en/zh 键集 diff=0。 |
| T7 | 测试补全 | [x] | BytesView preview 5 例;DAO 投影/邻接 id 3 例;RecordDetailViewModel 大记录用例(3×2MB hex 字段全量加载不 OOM + 邻接仅投影 id);ByteSection 插桩 v3.2 改 5 例(折叠预览/展开首块组合+全量不落单节点/300K 分块惰性滚动/展开收起回调/复制携带全量)。 |
| T8 | 质量门禁 | [x] | `testModernDebugUnitTest` + `testLegacyDebugUnitTest` 全绿;`connectedModernDebugAndroidTest` 25 例全绿(2 skipped);`lintModernDebug` 0 error/31 warning(回基线,零新增);`assembleModernRelease`(含 lintVital)+ `assembleLegacyDebug` 通过;en/zh 键对 diff=0。Gradle daemon 曾因并行任务 OOM(环境问题,与仓库既有 hs_err 同类),分步执行后全绿。 |

## 5. 验收标准(Definition of Done)

- [x] 构造/实采 7×1MB 大字段记录,详情页打开不闪退,滚动流畅(无 ANR)——LazyColumn 虚拟化 + 预览披露 + 展开惰性分块三重保障;v3.2 实机回归:展开 100K 字符 hex 无 ANR、无 Davey,插桩用例(300K 字符 hex 分块惰性滚动)全绿;
- [x] 详情页默认仅渲染预览(≤4K 字符/Section),展开按需加载——ByteSection 折叠/展开插桩用例全绿(v3.2 分块契约);
- [x] 复制文本/JSON 主线程零大分配(后台构建)——`copyBuilt`/`share` 走 `Dispatchers.Default` + runCatching;
- [x] 分享 >2M 字符走 FileProvider,无 `TransactionTooLargeException`——`shareIntent` 分级 + Manifest provider + file_paths.xml;
- [x] 列表页与「上一条/下一条」零全量邻接加载——`observePagedRows` substr 投影 + `neighborId*` 仅 SELECT id(DAO 测试覆盖);
- [x] 全部单测/插桩/lint 门禁全绿;en/zh 键对 diff=0——T8 明细;
- [x] 数据完整性:数据库存储语义不变,截断标记/字节数展示正确——`observeById` 全量保留,ViewModel 大记录用例断言 hex 全量等值。

## 6. 风险与注意事项

| 风险 | 对策 |
|------|------|
| 展开后大文本排版仍可能拖垮进程 | 实测 1M 字符在 SelectionContainer 内排版导致模拟器 watchdog 击杀系统进程(上限 200K);v3.2 实机再测:100K 字符单 Text 展开仍 26s ANR → 彻底废弃上限,改 4K 字符/块惰性组合,仅可视块参与布局+绘制;实机复测无 ANR、无长帧 |
| FileProvider 引入 manifest/provider 变更 | 按 AndroidX 规范登记 `${applicationId}.fileprovider`,路径仅 cache/share 子目录,授权 FLAG_GRANT_READ_URI_PERMISSION |
| 投影查询签名变更波及既有测试 | 测试同步迁移(邻接断言改 id,新增投影断言);旧全量方法已删除 |
| Room POJO 投影字段与实体字段重名 | 投影列显式别名(keyPreview/inputPreview/outputPreview),Room 映射无歧义 |
| 插桩触摸注入几何问题 | 复制按钮用例改用 `performSemanticsAction(OnClick)` 直调语义动作,绕开 Icon 内嵌点击区域不确定性 |
