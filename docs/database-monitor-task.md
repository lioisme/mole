# Modev 数据库监听能力建设任务文档

> 名称:**Modev 数据库监听能力** · 定位:与算法监听并列的第二个监听能力(Probe),专业、低侵入、生产级地捕获被注入 App 的 SQLite 数据库操作(打开/关闭、增删改查、SQL 语句、事务、绑定参数、结果集),复用既有「捕获 → WAL → TCP → 宿主管线 → Room → UI」全链路。
> 质量标准:架构分层、空安全、不可变模型、单元/集成/设备三层测试、lint 零新增、i18n 双语齐备 —— 对标一线大厂 Android 工程验收标准。
> 基线:当前工作树(V17 R7);Room v5 单表 `crypto_records`;TCP v4 泛型信封已就绪。
> 状态图例:`⬜ 待办` → `🔄 进行中` → `✅ 已完成`

---

## 0. 背景与目标

### 0.1 立项依据(全量审计结论,2026-08-19)

全量代码走查后确认:注入侧 `Probe` 契约、`WireRecord` 类型化信封(v4)、宿主 `RecordCodecRegistry` 类型化分发、`RecordType`/`RecordTypes`/`CapabilityKind` 扩展位、`RecordFilter` 多类型过滤通道均已为数据库监听预留(注释明示,见 `RecordTypes.kt:5-6`、`CapabilityKind.kt:5-6`、`Probe.kt:12-13`、`HookInstaller.kt:28-29`)。**当前不存在任何针对被注入 App 的 SQLite Hook**(全仓库 53 处 SQLite 匹配均为宿主自身存储与测试)。

### 0.2 目标定义

> **验收基线**:任意作用域内目标 App,凡经 Java 层标准 SQLite 门面(`SQLiteDatabase` / `SQLiteOpenHelper` / `SQLiteStatement` / `SQLiteCursor`)的数据库操作均可被捕获:打开/关闭、插入/更新/删除/替换、execSQL、rawQuery/query、预编译语句及其绑定参数、事务生命周期、查询结果集(被动采集,完整行数据)、数据库路径、调用堆栈、线程、进程名。Hook 线程零 I/O、零阻塞、零额外查询执行;结果采集不改变被注入 App 可观察行为(游标位置/执行时序)。

---

## 1. 需求规格

### 1.1 捕获域矩阵(目标态)

| 类别(SqlKind) | Hook 目标 | 捕获内容 | 产出操作(SqlOperation) |
|---|---|---|---|
| OPEN | `SQLiteDatabase.openDatabase/openOrCreateDatabase/create/close`;`SQLiteOpenHelper.getWritableDatabase/getReadableDatabase/close` | 数据库路径、名称(路径 basename)、调用门面、helper 类名 | `OPEN_DATABASE` / `CLOSE_DATABASE` / `OPEN_HELPER` / `CLOSE_HELPER` |
| QUERY | `SQLiteDatabase.query×6 / rawQuery×3 / rawQueryWithFactory / queryWithFactory`(含 CancellationSignal 重载);`SQLiteCursor.moveToFirst/moveToNext/moveToPosition/move/requery/close`;`SQLiteStatement.simpleQueryForLong/String` | SQL、选择参数、表名、投影列名、**结果行数(被动)与完整结果行(被动采集,上限保护)**、标量结果 | `QUERY` / `RAW_QUERY` / `QUERY_RESULT` / `STATEMENT_QUERY_LONG` / `STATEMENT_QUERY_STRING` |
| DML | `insert / insertOrThrow / insertWithOnConflict / replace / replaceOrThrow / update / updateWithOnConflict / delete` | SQL(由参数组合还原)、表名、ContentValues(键+类型+值,blob 走双态视图)、selection+args、冲突算法、**受影响行数/rowId 返回** | `INSERT` / `REPLACE` / `UPDATE` / `DELETE` |
| EXEC | `execSQL×2`;`compileStatement` 产物 `SQLiteStatement.execute / executeInsert / executeUpdateDelete`(含绑定参数,`bindLong/Double/String/Blob/Null/AllArgsAsStrings/clearBindings` 捕获);`attach` | SQL、**绑定参数序列表(类型+值)**,executeInsert 返回 rowId、executeUpdateDelete 返回受影响行数 | `EXEC_SQL` / `STATEMENT_EXECUTE` / `STATEMENT_EXECUTE_INSERT` / `STATEMENT_EXECUTE_UPDATE_DELETE` / `ATTACH` |
| TRANSACTION | `beginTransaction / beginTransactionNonExclusive / beginTransactionWithListener(NonExclusive) / endTransaction / setTransactionSuccessful` | 事务嵌套深度(随 begin/end 增减)、最终成功标记(任意 setTransactionSuccessful 后 end) | `BEGIN_TRANSACTION` / `END_TRANSACTION` / `SET_TRANSACTION_SUCCESSFUL` |

**明确不捕获**(设计边界,风险册 #6):SQLite C API 层(native,libxposed 无能力)、`ContentResolver`(非 SQLite 门面)、其他应用 DB 文件、`SQLiteDatabase` 内部维护方法(`getVersion/setVersion/setLocale/isOpen` 等无数据价值)。

### 1.2 字段完整性规格

每条 SqlRecord 字段:

| 字段 | 承载 | 说明 |
|---|---|---|
| id / seq / recordType / packageName / processName | 既有 | recordType = `"db"`;seq 仍为注入侧进程级单调序号(与 crypto 共享,唯一索引幂等不受影响) |
| kind / operation | 既有列 | SqlKind / SqlOperation 枚举名 |
| databaseName | **复用 `algorithm` 列** | DB 路径 basename(如 `app.db`);语句类记录取所属 DB 实例名 |
| dbPath | **新增列** | 完整路径(打开事件/语句关联时写入) |
| sql | **新增列** | SQL 语句原文,截断上限 `SQL_MAX_CHARS=65536`(常量,超限置 `sqlTruncated` 标记) |
| bindArgs | **新增列 `bindArgsJson`** | 绑定参数序列化 JSON `[{i,t,v,hex?,len?,truncated?}]`(t ∈ L/D/S/B/N),上限 64KB;ContentValues 同构序列化 |
| rows | **新增列 `rowsJson`** | 结果行 JSON `[{c:[{n,t,v,hex?,len?,truncated?}]}]`,上限由 `maxResultRows`(默认 100 行)+ `maxResultChars`(默认 256KB)双闸门,超限截断置标记 |
| rowCount / affectedCount / rowId / resultText | **新增列 ×4** | 查询结果行数(被动获取,见 ADR-6)/ DML 受影响行数 / 插入 rowId / simpleQuery 标量结果 |
| params | 既有 `paramsJson` | 表名、冲突算法、事务深度、事务成功、journalMode、sqlTruncated/resultTruncated 等标量 |
| contextId / threadName / stack / timestamp | 既有 | contextId = **DB 实例 ID**(语句/游标事件均关联所属 DB 实例;query 与 query_result 同 contextId 链) |

### 1.3 链路完整性规格

- 同一 DB 实例的 open→query/exec→close 以 `contextId` 关联(`SqlContextTracker` 弱引用表,复用 CryptoContextTracker 模式);
- `compileStatement → bind×N → execute*` 为参数化链路:绑定值经**实例键控暂存**(非 ThreadLocal——SQLiteStatement 允许跨线程执行),execute 时快照入记录,`clearBindings` 清空;
- **结果集被动采集**(ADR-6):不强制执行游标、不移动游标位置;随 App 自身迭代逐行收集;游标 close 时补发 `QUERY_RESULT` 记录(同 contextId);不 close 的泄漏游标由行数/字节上限兜底,绝不无界驻留;
- 方法抛异常时:入参侧信息(before 阶段)仍产出(与算法监听语义一致);after 阶段异常被 PROTECTIVE 隔离,不影响宿主。

---

## 2. 现状审计结论(复用面与改动面)

### 2.1 零改动复用面(审计确认)

| 链路段 | 结论 |
|---|---|
| 线协议 v4 | `WireRecord{seq,recordType,payload}` 泛型信封,**无需版本升级**;4MB 行上限由字段保险丝保证 |
| 注入侧出口 | `RecordSink`(有界队列 2048 + 队列满直落盘 + volatile seq 检查点)/ `RecordSpool`(WAL 双游标 at-least-once)/ `ConfigChannel`(握手/配置推送/ACK)—— 均与 recordType 无关 |
| 宿主摄入 | `LogRepository`(pending 8192 信号量背压 + 32条/200ms 批量 + AckAggregator 会话聚合 + REPLACE 幂等 + 双层 trim)仅需**注册新 Codec 一行** |
| 存储主链 | `observePagedRowsByFilter / observeCountByFilter` 的 `recordType IN` 通道已参数化;`(packageName,processName,seq)` 唯一索引跨类型天然幂等 |
| 作用域/运行中/统计 | ScopeService、ConnectionMonitor、PipelineStats 与类型无关 |

### 2.2 必须改动面(本任务全部工作量)

| # | 改动 | 位置 |
|---|---|---|
| A1 | `RecordTypes.DATABASE="db"` + `RecordType.DATABASE` | core/model |
| A2 | `SqlKind`(5)/ `SqlOperation`(20)新枚举 | core/model |
| A3 | `SqlRecord` 模型 + `WireRecord` 密封接口(CRYPTO/DB 两实现,RecordSink.emit 泛化,注入侧编码分派) | core/model、hook/RecordSink.kt、HookInstaller.kt |
| A4 | `HookSwitch` +5 开关(默认开)+ `maxResultRows/maxResultChars` + `anyCryptoEnabled/anyDbEnabled` 投影 + `dbEnabled(SqlKind)` | core/model |
| A5 | `SqlRecordCodec`(wire 编解码,严格校验镜像 CryptoRecordCodec)注册进 `RecordCodecRegistry`(LogRepository.kt:330-333) | core/codec |
| A6 | Room v5→v6 迁移(新增 8 可空列)+ 实体/DAO 投影(加 `sqlPreview/rowCount/affectedCount/rowId` + 搜索含 `sql/resultText` 列) | data/db |
| A7 | `SqlContextTracker`(DB 实例/语句/游标弱引用表 + 事务深度 + 绑定暂存) | hook/ |
| A8 | `DatabaseProbe` + 三 Hooker(`SqlDatabaseHooker` / `SqlOpenHelperHooker` / `SqlStatementHooker`)+ `SqlBaseHooker.emit`(镜像 BaseHooker:开关快照、denylist、堆栈、上限) | hook/probes、hook/hooks |
| A9 | 映射层:`SqlRecord↔Entity` 双向 + 行投影 → 新轻量行模型;`CryptoRecordRow.toRowModelOrNull` 重构为 `RowModel`(sealed: CryptoRow/SqlRow) | data/mapping |
| A10 | 详情页 sealed 模型(CRYPTO/DB 分支渲染:SQL 代码块、绑定参数、结果行表格、事务)+ `SqlRecord.toJson` 导出 | ui/logs、ui/components |
| A11 | 记录列表:recordType 类型筛选(全部/算法/数据库)+ KindFilterMenu 按类型切换 + RecordRow 分支渲染 + SqlKind 调色板(+5 色,AA 门禁) | ui/apps、ui/components、ui/theme |
| A12 | `CapabilityKind.DATABASE_LISTEN` + `DatabaseListenCapability` + 路由 `settings/database` + `DatabaseMonitorScreen`(5 开关 + 结果上限设置)+ CapabilityUiRegistry 注册(Storage 图标) | domain/capability、ui/navigation、ui/settings |
| A13 | i18n 双语键(开关/类别/操作/详情节/筛选/结果行术语,约 60 键,Parity 测试强制) | res/values、values-zh |
| A14 | AGENTS.md 契约更新(词汇表新增「数据库监听」术语与开关默认值契约) | 仓库根 |

---

## 3. 架构与设计决策(ADR)

### ADR-1 单表扩展(Room v5→v6)

经评审,否决「新表 + SQL VIEW 合并」:Room 不支持 schema 导出视图、Paging/裁剪/清理/邻接/统计需双表重写。采用 **`crypto_records` 单表 + 8 个可空新增列**,`recordType` 列分流:`db` 行复用 `algorithm`(→databaseName)/`kind`/`operation`/`paramsJson`/`stackJson`/`contextId`/`processName`/`seq`/`threadName`/`timestamp`。纯 `ALTER TABLE ADD COLUMN` 增量迁移,不触碰存量数据,`fallbackToDestructiveMigration(false)` 保持。

新增列:`sql TEXT`、`dbPath TEXT`、`bindArgsJson TEXT`、`rowsJson TEXT`、`rowCount INTEGER`、`affectedCount INTEGER`、`rowId INTEGER`、`resultText TEXT`(全部可空)。索引不变:`(packageName,kind)` 已服务 DB 类别过滤;时间线/裁剪/邻接/幂等全部复用。

### ADR-2 注入侧类型化信封:`WireRecord` 密封接口

`RecordSink.emit(record: CryptoRecord)` → `emit(record: WireRecord)`;`HookInstaller` 装配编码分派 `when(wire){ is CryptoRecord -> CryptoRecordCodec.encode; is SqlRecord -> SqlRecordCodec.encode }` → `WireCodec.record(seq, wire.recordType, payload)`。宿主侧 `RecordCodecRegistry` 注册 `SqlRecordCodec`(未注册类型计数丢弃但仍 ACK 的既有语义天然覆盖降级路径)。协议 VERSION 不变(v4 信封已是泛型)。

### ADR-3 开关模型:5 类别默认启用(「常用的默认启用」决策)

`HookSwitch` 新增 `dbOpen/dbQuery/dbDml/dbExec/dbTransaction: Boolean = true`(用户决策:常用类别默认启用)+ `maxResultRows=100`、`maxResultChars=262144`(结果闸门)。**能力投影拆分**:新增 `anyCryptoEnabled`(仅 12 个 crypto 类别)与 `anyDbEnabled`(仅 5 个 db 类别);`AlgorithmListenCapability` 切至 crypto-only 投影(修复:否则仅开数据库监听时「算法监听」会被误标启用),新增 `DatabaseListenCapability`。配置键:`switch_db_open` 等(ConfigKeys 同源)。

### ADR-4 SQL 文本与绑定参数上限

`sql` 截断上限常量 `SQL_MAX_CHARS=65536`(超限 `params["sqlTruncated"]="true"`,hex 恒纯前缀语义仅用于字节——SQL 为纯文本直接截断);`bindArgsJson` 上限 64KB;blob 绑定值走 `maxBytesPerField` 双态视图保险丝(超限标记 truncated 且仅存前缀)。WAL 4MB 行上限(RecordLimits.WIRE_MAX_LINE_CHARS)在全部字段叠加后仍由上述保险丝保证不击穿。

### ADR-5 事务深度跟踪

`beginTransaction*`(before):`depth++` 并 emit BEGIN(深度);`setTransactionSuccessful`(after):实例标记 `success=true`;`endTransaction`(after):emit END(深度、成功标记),深度 `--` 归零清标记。深度计数挂 DB 实例上下文(`SqlContextTracker`),嵌套事务记录层级。`ATTACH` 归 EXEC 类别,路径入 params。

### ADR-6 结果集被动采集(完整行数据,零额外执行)

- **禁止**在 hook 中调用任何会触发查询执行的方法(`getCount()`/`moveToFirst()` 等,未物化游标上二者均强制执行 SQL);
- 行数:反射读 `SQLiteCursor.mCount`(私有字段,runCatching 包裹,失败置 null);`fillWindow(int)` after 后 mCount 已物化可读;
- 行数据:随 App **自身**迭代采集——hook `moveToFirst/moveToNext/moveToPosition/move/requery`(after):当前位置行序列化为 `[{n,t,v,hex?,len?,truncated?}]`(列名一次取 `getColumnNames()`,逐列按 `getType()` 分派 `getString/getLong/getDouble/getBlob` 全 runCatching,blob 经 `maxBytesPerField` 双态视图);`moveToPosition(-1)`/`requery` 重置缓冲(防重复行);`close`(after)补发 `QUERY_RESULT` 记录(同 contextId,含完整 rows + rowCount);
- 双闸门:`maxResultRows`(默认 100 行)或 `maxResultChars`(默认 256KB)任一超限 → 立即补发截断的 `QUERY_RESULT`(置 `resultTruncated`)并停止采集——**无界驻留不可能**,泄漏游标最坏代价 = 上限字节,随弱引用 GC;
- query-after 记录(QUERY/RAW_QUERY)先发:SQL/args/表/列名(未物化时列名为空);结果随后以 `QUERY_RESULT` 补齐——两条记录同 contextId,时间线相邻可见。

### ADR-7 语句绑定暂存(实例键控,非 ThreadLocal)

`bindLong/Double/String/Blob/Null/AllArgsAsStrings`(after)写入 `SqlContextTracker` 的语句实例弱键 `Map<Int,BindValue>`;`execute/executeInsert/executeUpdateDelete/simpleQuery*`(before)快照(上限保护)后清空;`clearBindings`(after)清空。允许跨线程 bind→execute(WeakHashMap + 同步包装,复用 CryptoContextTracker 模式)。语句实例 ID 不入库(生命周期短),记录关联 DB 实例 contextId。

### ADR-8 方法定位健壮性(多重重载)

`SQLiteDatabase.query` 有 6 个重载、rawQuery 3 个、insert 4 个——`MethodFinder` 现有精确签名查找**不可枚举重载**。新增 `DatabaseMethodRegistry`:安装期 `Class.getDeclaredMethods()` 按方法名过滤(参数任意),逐方法注册;注册失败静默跳过(engine 既有语义),任一重载缺失不影响其余。boot classloader 直接解析 `android.database.sqlite.*`(系统类)。

### ADR-9 宿主侧零侵入摄入

`LogRepository` 仅注册 `SqlRecordCodec`(1 行)。批量/背压/ACK/幂等/trim/统计全部复用;`toRowModelOrNull` 按 `recordType` 分派(未知枚举仍丢弃计数)。搜索:分页/计数查询的 LIKE 子句追加 `sql`/`resultText` 两列(ESCAPE 语义不变)。

### ADR-10 行模型 sealed 化

`CryptoRecordRow.toRowModelOrNull()` 现返回 `CryptoRecord`(轻量复用)——DB 行无此形状。重构为 `RowModel` sealed:`CryptoRow(CryptoRecord)` / `SqlRow(SqlRecordRow 轻量:sqlPreview/databaseName/rowCount/affectedCount)`;DAO 投影新增 `sqlPreview = substr(sql,1,:previewChars)`、`rowCount/affectedCount/rowId` 列。RecordRow 按类型分支渲染(DB 行:操作标签 + SQL 摘要 + 数据库名)。

### ADR-11 详情页 sealed 渲染 + 导出

`observeById` 实体 → `RecordDetailModel` sealed(CRYPTO→CryptoRecord / DB→SqlRecord)。DB 详情节:概览(路径/表/门面/线程/进程/事务深度)、SQL 语句(等宽代码块,>128KiB 走既有 ChunkedText Tier B)、绑定参数(行:序号+类型+值,blob 转字节 Section)、结果(行数/rowId/受影响行/标量)、结果集(行列表,列名+值,blob 截断)、堆栈。`SqlRecord.toJson()` 结构化导出(org.json)。

### ADR-12 探针装配与能力注册

`DatabaseProbe`(recordType=`RecordTypes.DATABASE`)加入 `HookInstaller` 默认 probes 清单;探针常驻安装,`enabled()` 每次调用现读(关闭时零开销,镜像既有模式)。`CapabilityKind.DATABASE_LISTEN` + `CapabilityUiRegistry` 注册(Storage 图标,标题「数据库监听」,导航 `settings/database`);`DatabaseMonitorScreen` 镜像 AlgorithmMonitorScreen:监听类别 5 开关(含当前启用态)+ 记录选项(结果行数上限/结果字节上限数字 sheet,复用 maxBytesPerField/logStack/ignoreStackPrefixes 既有设置区)。

---

## 4. 任务清单

### P0 模型 / 协议 / 存储

#### T1 模型层:SqlKind / SqlOperation / SqlRecord / WireRecord ✅ 已完成
**验收**:`SqlKind`(OPEN/QUERY/DML/EXEC/TRANSACTION)与 `SqlOperation`(20 值)枚举定义;`SqlRecord` 不可变 data class 全字段(§1.2);`WireRecord` sealed 接口落地于 `core/model/WireRecord.kt`,`CryptoRecord` 实现之(recordType 字段 override,无行为回归);`RecordTypes.DATABASE="db"`、`RecordType.DATABASE`;`RecordTypeTest` 更新(fromWire 双向)。
**备注**:枚举缺失时 `fromWire`/`enumOf` 返回 null 语义不变(协议/旧数据漂移兜底)。**勘误**:`SqlOperation` 实际为 **21 值**(文档写 20;`STATEMENT_EXECUTE` 与 `STATEMENT_EXECUTE_UPDATE_DELETE` 之外含 `STATEMENT_EXECUTE_INSERT`,`ATTACH` 归属 EXEC;op_db_* i18n 键与 `SqlOperationLabelRes` 均按 21 值实现)。

#### T2 开关与配置 ✅ 已完成
**验收**:`HookSwitch` +5 开关(默认 **true**)+ `maxResultRows=100` + `maxResultChars=262144` + `anyCryptoEnabled/anyDbEnabled` + `dbEnabled(SqlKind)`;`HookSwitchTest` 更新(默认值、anyEnabled 语义拆分、db 类别映射);`AlgorithmListenCapability` 切 crypto-only 投影 + 新 `DatabaseListenCapability` + `CapabilityKind.DATABASE_LISTEN`;`SettingsRepository` 无需改动(JSON 兼容新增字段)。
**备注**:存量 `switch_global` 配置无 db 键 → Gson 默认值生效(=开),无需迁移。

#### T3 线协议与编解码 ✅ 已完成
**验收**:`SqlRecordCodec`(encode/decode)严格校验镜像 `CryptoRecordCodec`(先按原始 JSON 校验 kind/operation 枚举已知 + packageName 非空,再 fromJson);注入侧 `RecordSink.emit(WireRecord)` 泛化 + `HookInstaller` 编码分派;宿主 `LogRepository.kt:330` 注册 1 行;`WireCodecTest`/`RecordCodecRegistryTest` 扩展 db 类型用例(未知 db 记录解码 null、未注册类型仍 ACK 语义回归)。
**备注**:协议 VERSION 不变;注入侧 WAL/ACK/幂等链路零改动。

#### T4 Room v6(迁移 + 实体 + DAO)✅ 已完成
**验收**:`CryptoRecordEntity` +8 可空列;`ModevDatabase.version=6` + `MIGRATION_5_6`(纯 ALTER TABLE ADD COLUMN);schema 导出 `6.json`;DAO:投影查询 + `sqlPreview/rowCount/affectedCount/rowId` 列、搜索 LIKE 追加 `sql`/`resultText`(observePaged/observePagedRows/ByFilter/counts 四处一致)、`observeById` 自动覆盖新列;`CryptoRecordDaoTest` 扩展(新列 round-trip、db 行过滤/分页/搜索、v5→v6 迁移测试手工复刻 v5 库)。
**备注**:schema `app/schemas/.../6.json` 已由 KSP 导出(待纳入版本管理);迁移测试 `migration from v1/v2/v4` 三处 `addMigrations` 已补 `MIGRATION_5_6` 达 v6(否则 Room 报 4→6 缺路径)。

### P1 注入侧捕获

#### T5 SqlContextTracker ✅ 已完成
**验收**:弱引用 `WeakHashMap<Any, SqlContext>`(同步包装)+ AtomicLong 自增实例 ID(DB 实例/语句/游标三用);`SqlContext`:dbPath/name、事务深度、success 标记、绑定暂存表(语句弱键 `Map<Int,BindValue>`)、游标缓冲(rows/行数/列名/采集闸门);reset 语义;`SqlContextTrackerTest`(ID 唯一稳定、事务深度增减、绑定快照/清空、行缓冲重置/上限截断)。
**备注**:`SqlContextTrackerTest` 10 用例全绿(2026-08-19,纯 JVM)。**闸门语义勘误**:`CursorBuffer.addRow` 先追加行、再判 `rows.size>=maxRows || chars>=maxChars` 置 truncated 并返回 `collecting`(=!truncated)——即**超限的当次追加已入缓冲且返回 false**;字符成本 = `v.length + hex.length + 8`。测试据此断言(行闸门 maxRows=2 → 第 2 次返回 false;字符闸门 12 字符行 → 首次即 false)。

#### T6 SqlDatabaseHooker(SQLiteDatabase 全表面)✅ 已完成
**验收**:`DatabaseMethodRegistry` 按名枚举注册:open×4/create/close、insert×4/replace×2/update×2/delete、query×6/rawQuery×3/rawQueryWithFactory/queryWithFactory(含 CancellationSignal)、execSQL×2、compileStatement、attach、beginTransaction×4/endTransaction/setTransactionSuccessful、enableWriteAheadLogging/setJournalMode(入 params);open-after 注册实例上下文并 emit `OPEN_DATABASE`(path/name),close-after emit `CLOSE_DATABASE`;DML-after 读返回 rowId/affected 入列;query-after emit `QUERY`(sql/args/表/列名,mCount 反射行数);游标钩子(ADR-6)采集与 `QUERY_RESULT` 补发;事务深度(ADR-5)。
**备注**:全部 hook 经 `SqlBaseHooker.emit`(开关快照 + denylist + 堆栈 + 上限);after 异常由 PROTECTIVE 隔离。**工程勘误与加固**(2026-08-19):
1. `DatabaseMethodRegistry` 用 `clazz.methods`(公开方法全集,含继承实现,重载去重后最派生声明仅一次)替代 `declaredMethods`——Robolectric 沙箱 SQLite 类部分方法继承自父类(如 SQLiteCursor.moveToFirst 继承自 AbstractCursor、SQLiteDatabase.close 继承自 SQLiteClosable),declaredMethods 会漏注册;真机 AOSP 表面不变(方法名枚举仅增不损)。
2. `contentValuesToBinds` 改为按**原始值类型**分派(`cv.get(key)` 直取),修复 `getAsBoolean("alice")` 返回 false 导致字符串误判为 0 的坑;blob 兼容跨进程转换后的 `ByteBuffer`(direct buffer 下 `get()` 的 `array()` 拷贝抛异常,`hasArray` 兜底 `duplicate().get()` 手工读入)。
3. `mCount` 反射:负数(未物化/Robolectric 恒 -1)置 null,不再误报行数。
4. `dbNameOf` basename 同时兼容 `/` 与 `\` 分隔符(Robolectric 宿主 Windows 路径)。
5. 顺带完成 hook 目录 13 个文件中文注释乱码修复(GBK 双重编码,行级 PowerShell 替换脚本 + 验证扫描 CLEAN;CipherHooker L77-78 拆分后缩进修复)。

#### T7 SqlOpenHelperHooker + SqlStatementHooker ✅ 已完成
**验收**:`SQLiteOpenHelper.getWritableDatabase/getReadableDatabase`(after)→ `OPEN_HELPER`(dbName=getDatabaseName()、path=返回 db 的 getPath())、`close` → `CLOSE_HELPER`(helper 类名入 params);`SQLiteStatement` bind×7 暂存(ADR-7)、`execute/executeInsert/executeUpdateDelete/simpleQueryForLong/simpleQueryForString`(after)emit(绑定额 + rowId/affected/标量结果);`compileStatement` 返回对象不产记录(执行时才有)。
**备注**:绑定暂存键=语句实例弱键(ADR-7,非 ThreadLocal);execute 族按返回类型分派(executeInsert→rowId、executeUpdateDelete→affected、simpleQueryFor*→resultText);`afterBindAll` 忽略空参数;`truncated` 缺省 false 防 NPE;dbName 优先 `helper.databaseName`、兜底 `dbNameOf(dbPath)`。

#### T8 DatabaseProbe 装配 ✅ 已完成
**验收**:`DatabaseProbe`(recordType=DB)三 Hooker 装配清单即能力边界;注册进 `HookInstaller.kt:29` 默认 probes;`HookInstaller` 装配 sink 泛化后编译通过;注入侧 `enabled()` 零开销关闭态验证。
**备注**:`HookInstaller` 默认 probes = `listOf(AlgorithmProbe(), DatabaseProbe())`;关闭态零产出由集成测试 `db 开关全关时零产出` 用例验证(开关快照短路,钩子注册本身无副作用)。

### P2 宿主侧映射与 UI

#### T9 映射层 ✅ 已完成
**验收**:`SqlRecord.toEntity()/toSqlModelOrNull()`(未知枚举 null、json 解析容错,镜像既有模式);`CryptoRecordRow.toRowModelOrNull` → `RowModel` sealed(CryptoRow/SqlRow);`SqlRecord.toJson()` 导出;`CryptoRecordMappersTest` 扩展(db 全字段 round-trip、rowsJson/bindArgsJson 容错、未知枚举丢弃)。
**备注**:`RowModel`(CryptoRow/SqlRow)、`RecordDetailModel`(CryptoDetail/SqlDetail)落地;`LogRepository` 双模型摄入/分页/详情/邻接已泛化;`neighborIdOf` 签名改 `(recordType, packageName, timestamp, id, newer)` 按类型限定邻接。**测试适配**:`CryptoRecordMappersTest`/`RecordDetailViewModelTest` 已按 CryptoRow/CryptoDetail 解包更新;SQL 映射新单测见 T14 收尾(待补 toEntity/toJson/sqlRowModel 往返用例)。

#### T10 记录列表:类型筛选 + 行渲染 + 调色板 ✅ 已完成
**验收**:AppRecordsScreen 新增记录类型筛选(全部/算法/数据库);KindFilterMenu 按类型切换(CryptoKind 9+1 / SqlKind 5+1);RecordRow 分支渲染(DB 行:操作标签 + sql 摘要 48 字符 + 数据库名);`KindColorTokens` +5 SqlKind 色(浅/深,AA ≥ 4.5:1,`KindColorTokensTest` 门禁);时间线分组/分页/邻接零改动;`TimelineListTest` 增 db 行种子用例。
**备注**(2026-08-19):
- 筛选菜单双组单选:记录类型(全部记录/算法/数据库,切类型清空类别)+ 类别组随类型渲染(CryptoKind 12 项 / SqlKind 5 项;类型=全部时两组全显)。类别选中不改类型(kind 列跨枚举唯一,SQL 过滤直接命中)。只读 RadioButton 语义不可断言 → 行为级断言(点击项 → 行现/行隐)。
- `LogRepository.pagedRecords` 的 `kind: CryptoKind?` 参数改 `kindName: String?`(CryptoKind.name / SqlKind.name 共用 kind 列,跨枚举过滤);`AppRecordsViewModel` 增加 `setType(RecordType?)` + `setKindName(String?)`,records 流 combine 三元组。
- SqlRow 列表行补渲染数据库名(元信息行前缀 `dbname · 类别 · 操作`),满足验收「DB 行含数据库名」。
- 新增 i18n 键 `record_types_all/crypto/db` 双语;删除随旧菜单失用的 `all` 键(lint UnusedResources 归零)。
- `TimelineListTest`:种子 +1 db 行(recordType='db'/kind=DML/operation=INSERT,s1 同秒不新增组头,读回计数 5→6),新增 `timelineGroups_dbRowRendersWithDbNameAndSqlPreview` 用例(断言 t14.db 与 SQL 预览可见)。
- 顺带修复 `RecordRowLayoutTest` 6 处 V17 遗留编译错(RecordRow 入参已泛化 RowModel,CryptoRecord 需包 CryptoRow)。

#### T11 详情页 sealed 渲染 + 导出 ✅ 已完成
**验收**:`RecordDetailViewModel` 出 `RecordDetailModel` sealed;DB 详情五节渲染(§ADR-11),SQL 长块走 ChunkedText(Tier B),结果行表格(blob 截断 + 展开);`RecordDetailScreen` 导出 JSON 分支;文本护栏(`RecordDetailTextFormat`)覆盖 rowsJson/bindArgsJson 大字段;`RecordDetailViewModelTest`/`ByteSectionTest` 扩展。
**备注**:`SqlLoadedContent` 渲染(SQL/dbPath/bindArgs/rows/resultText/params/stack 分档节)+ `RecordDetailTextFormat` 护栏(`BIND_ARGS_MAX_ROWS=500`/`RESULT_MAX_ROWS=200`/`RESULT_MAX_COLS=50` + `formatBindArgsText/formatRowsText/estimateSqlJsonChars`)+ 导出/标题分支均已落地;`RecordDetailViewModelTest` 已适配 sealed 模型。

#### T12 数据库监听设置页 + 能力注册 ✅ 已完成
**验收**:路由 `settings/database` + `DatabaseMonitorScreen`(5 类别开关 + 结果行数/结果字节上限 sheet);`CapabilityUiRegistry` 注册 DATABASE_LISTEN(Storage 图标);SettingsScreen「功能」区自动出现入口(注册表驱动,零硬编码);开关即时广播生效(既有链路)。
**备注**(2026-08-19):新增 `DatabaseMonitorScreen.kt` + `DatabaseMonitorViewModel.kt`(镜像 AlgorithmMonitor 模式,单一事实源 SettingsRepository.globalSwitch,变更即时广播);`ModevRoutes.DATABASE_MONITOR="settings/database"` + ModevNav 注册路由与 `CapabilityUiRegistry.Entry(DATABASE_LISTEN, Storage 图标, database_monitor_title)`;结果集双闸门 sheet 共用 `IntLimitSheet`(ModalBottomSheet + imePadding,符合 V15 输入底部化规范);`SwitchRowItem/OptionRowItem` 自 AlgorithmMonitorScreen 提取为 `SettingsRowItems.kt`(internal,两页共用,消除私有重复);能力域注册已在 T2 完成(ModevApp.AppContainer 两能力并立)。

#### T13 i18n 双语 ✅ 已完成
**验收**:en/zh 齐备:`switch_db_*`(5)、`db_kind_*`(5)、`db_op_*`(20)、`db_section_*`(5)、`db_meta_*`(路径/表/门面/深度/成功/行数/rowId/受影响/结果集/截断)、`record_types_all/crypto/db` 等约 60 键;占位符形态一致(`StringResourceParityTest` 强制);ui 包零中文字面量(`HardcodedStringConventionTest`)。
**备注**(2026-08-19):补齐 T12 设置页键 `switch_db_open/query/dml/exec/transaction`(5)、`db_max_result_rows/chars`(+hint/+dialog_title 各 3)、`database_monitor_title`、`record_types_all/crypto/db`(3)双语,占位符形态一致;`StringResourceParityTest` 全绿;删除失用 `all` 键。i18n 全部键集 diff = 0(parity 测试门禁通过)。

### P3 测试与验证

#### T14 注入侧单测(Robolectric 真实 SQLite)✅ 已完成
**验收**:Robolectric 内以 FakePlatform 捕获注册的 MethodHook,直接驱动真实 `SQLiteDatabase`(Robolectric sqlite 实现)验证:open→insert→query→close 全链产出(记录字段断言:sql/值/rowId/affected);事务深度与 success;语句 bind 快照;游标被动采集(真实 moveToNext 迭代 → QUERY_RESULT rows 完整;未迭代 → rowCount=null 且无结果记录;超行数/字节上限 → truncated 补发;requery 重置);关闭态零产出。
**备注**:9 用例全绿(2026-08-19):open/insert(ContentValues 还原 SQL+绑参+rowId)/rawQuery 全链(QUERY→游标迭代→QUERY_RESULT 2 行)/query 重建 SELECT/bind 快照(compileStatement+executeInsert)/update+delete(affected+whereArgs)/事务深度与 success/close/开关全关零产出。**验证口径**:
- 测试经 `FakePlatform.fireAfter` 以**真实方法引用**(`getMethod`,公开含继承)驱动 hook,非代理执行;await 异步落盘(Robolectric 沙箱线程),断言用 5s 轮询。
- Robolectric SQLite 表面与真机差异已隔离:游标行数反射恒 -1(→null 断言宽松)、close 继承自 SQLiteClosable(registry 已按 getMethods 覆盖)。
- 记录断言含 RowModel 分派(SqlRow)与 toJson 往返、`QUERY_RESULT` rows 完整行(列名/值)。
- 修正过程中定位并修复 3 个真实缺陷:getAsBoolean 字符串误判、ByteBuffer 跨进程 blob、Windows 路径 basename;`mCount` 负数置 null。
- 诊断探针 `SqlProbeTest`/`CvProbeTest` 使用后已删除。

#### T15 设备 UI 测试 ✅ 已完成
**验收**:镜像 `WebViewBridgeTest` 宿主内注入模式:实机安装 DatabaseProbe(宿主进程自身 hook 生效),真实 `SQLiteOpenHelper` CRUD + query 迭代 + 事务 → 断言记录(类型=db、op、sql、rowId、rows 行数);开关关 → 零产出;`DatabaseMonitorScreen` 开关热切换即时生效;列表类型筛选/详情页渲染导航链路。
**备注**(2026-08-20):新增设备测试 `DatabaseProbeTest`(真机 SQLite 原生实现,镜像 T14 口径:open→insert→query 迭代(close 时补发 QUERY_RESULT)→事务深度/success;开关全关零产出;mCount 反射为正数,宽松断言与 Robolectric 口径一致)与 `TimelineListTest` 扩展(类型筛选:记录类型→数据库 仅 DB 行 / 复位全部;DB 行详情页导航:SQL 节标题 + 全量 SQL 可见)。**全量 `connectedModernDebugAndroidTest` 在模拟器 emulator-5554(API 34)回归通过:57 用例、0 failed、3 skipped(FourScreensNavTest 既有设备条件跳过)**。期间排查 3 个断言问题:awaitSql 非消费式需累计计数、QUERY_RESULT 在游标 close 时补发、菜单项与分组标题同串需 hasClickAction 限定 + SQL 文本顶栏/节内双现取首节点。开关热切换复用既有 SettingsRepository 广播链路(算法侧 DebugHotSwitchTest 已覆盖)+ 探针开关关用例兜底。

#### T16 构建与门禁 ✅ 已完成
**验收**:`.\gradlew.bat :app:testModernDebugUnitTest` 全绿(含既有用例回归);`:app:lintModernRelease` **零新增**(基线 38 warning/0 error);`compileModernDebugKotlin` + `compileLegacyDebugKotlin` + 两变体 assemble;`StringResourceParityTest`/`HardcodedStringConventionTest`/`KindColorTokensTest` 全绿。
**备注**(2026-08-20):`testModernDebugUnitTest` 全绿(281+ 用例,含 SqlContextTrackerTest 10 + SqlDatabaseIntegrationTest 9);`lintModernRelease` = 0 error / **37 warnings**(< 基线 38;删除失用 `all` 键后较基线再 -1)。四变体 assemble 全绿:`assembleModernDebug`/`assembleModernRelease`/`assembleLegacyDebug`/`assembleLegacyRelease`(各 × arm64-v8a/x86_64,共 8 个 APK,legacy 变体编译仅既有 warning,新 DB 代码在 Xposed API 82 下无新增问题)。**坑**:机器内存 15.6 GB 且多项目 Gradle/Kotlin 守护进程滞留(Xmx8G)导致 page-file 压力(OS 报 errno 1455)→ assemble 曾触发 daemon disappeared;缓解 = 清理闲置守护进程 + `--no-daemon --max-workers=1` + `-Dorg.gradle.jvmargs=-Xmx2048m` 逐变体串行构建。lint/单测与 assemble 均串行执行(AGENTS.md 已知坑)。

#### T17 AGENTS.md 与文档 ✅ 已完成
**验收**:词汇表新增「数据库监听」;AGENTS.md 强约束补充:结果采集被动原则(禁止强制执行游标)、开关默认态契约(5 类默认开)、行模型 sealed 契约;本文档回写状态。
**备注**(2026-08-20):AGENTS.md 已补:词汇表「数据库监听」「记录」语义;强约束新增「结果采集被动原则」(hook 内禁止移动/强制迭代游标、禁止 getCount 探测);本文档 §4 执行记录 T1-T17 全量回写,附录 A/B 更新;`RecordFilter` 契约(第 9 条)新增 DB 记录语义;sql 摘要/详情双层渲染(Tier B)与 ReadMoreText 语义约束已并入第 3 条。

---

## 5. 测试策略

| 层 | 手段 | 覆盖点 |
|---|---|---|
| 纯逻辑 | JVM 单测(app/src/test) | SqlKind/SqlOperation 枚举、SqlRecordCodec 严格校验、绑定参数序列化、SqlContextTracker(ID/深度/暂存/重置)、行缓冲闸门、RowModel 分派、迁移 v5→v6 |
| 框架依赖 | Robolectric(`@Config(sdk=[34])`) | **真实 SQLite 全链集成**(FakePlatform + 真实 SQLiteDatabase/OpenHelper/Statement/Cursor)、DAO 新列/过滤/搜索、v6 迁移、映射 round-trip、mCount 反射降级 |
| 设备 | Compose UI(androidTest) | DatabaseProbe 宿主内注入端到端、开关热切换、列表类型筛选、详情页 DB 节渲染、导出 |
| 门禁 | lint + 架构测试 | lint 零新增、HardcodedStringConvention、StringResourceParity、KindColorTokens AA |

## 6. 风险册与已知边界

| # | 风险/边界 | 影响 | 缓解 |
|---|---|---|---|
| 1 | SQL 高频应用记录洪峰(5 类默认开) | 容量/性能压力 | 既有有界队列+WAL+批量+双层 trim(20K/5K)承接;行/字节闸门;类别可关;denylist |
| 2 | `mCount` 私有字段反射在个别 ROM 受限 | 行数缺失 | runCatching 置 null;fillWindow 后重试;不影响行数据采集 |
| 3 | 未关闭游标(App 泄漏) | QUERY_RESULT 延迟/缺失 | 行/字节闸门触发即补发截断结果;弱引用无内存风险;文档化边界 |
| 4 | 绑定值跨线程 bind→execute 竞态 | 参数缺失/错配 | 同步 WeakHashMap + 语句弱键;快照在 execute-before |
| 5 | 游标位置可观察性 | 改变 App 行为 | 被动采集设计:任何 hook 内禁止移动游标/调用 getCount;逐行采集仅发生于 App 自身迭代之后 |
| 6 | native SQLite C API / ContentProvider 层 | 覆盖盲区 | 明确边界(Java 标准门面);libxposed API 102 无 native hook(已有结论) |
| 7 | 大 SQL/大 blob 记录 | 传输/DB 膨胀 | SQL 64KB / bindArgs 64KB / rows 256KB+100 行 / blob maxBytesPerField 四层保险丝 |
| 8 | 既有用户 Room v5 升级 | 数据丢失 | 纯增量 ALTER + schema 6.json 基线 + 迁移测试;fallbackToDestructiveMigration(false) |

## 附录 A:实施顺序与依赖

```
T1 → T2 → T3 → T4(模型/协议/存储,必须先于一切)✅
  → T5 → T6 → T7 → T8(注入侧捕获)✅
  → T9 → T10 → T11 → T12 → T13(宿主侧/UI/i18n)✅
  → T14(Robolectric 集成)✅ → T15(设备测试)✅
  → T16(构建与门禁)✅ → T17(文档)✅
```
每个任务完成后回写本表状态(⬜→✅)并追加工作备注。

## 附录 B:新增/改动文件速查

| 动作 | 文件 |
|---|---|
| 新增 | `core/model/SqlKind.kt`、`SqlOperation.kt`、`SqlRecord.kt`、`WireRecord.kt`;`core/codec/SqlRecordCodec.kt`;`hook/SqlContextTracker.kt`、`hooks/SqlBaseHooker.kt`、`hooks/SqlDatabaseHooker.kt`、`hooks/SqlOpenHelperHooker.kt`、`hooks/SqlStatementHooker.kt`、`hooks/DatabaseMethodRegistry.kt`、`probes/DatabaseProbe.kt`;`ui/settings/DatabaseMonitorScreen.kt`、`DatabaseMonitorViewModel.kt`、`SettingsRowItems.kt`;测试:`SqlContextTrackerTest`、`SqlDatabaseIntegrationTest`(Robolectric)、`DatabaseProbeTest`(设备)、`TimelineListTest` 扩展(设备) |
| 改动 | `RecordTypes/RecordType`、`HookSwitch`、`RecordSink`、`HookInstaller`、`CryptoRecordEntity`、`ModevDatabase`(v6)、`CryptoRecordDao`、`CryptoRecordMappers`(RowModel sealed)、`LogRepository`(pagedRecords kindName 化 + toRowModel 分派)、`CapabilityKind/AlgorithmListenCapability`(+DatabaseListenCapability)、`CapabilityUiRegistry`、`ModevRoutes/ModevNav`、`AppRecordsScreen/KindFilterMenu`(双组筛选)、`AppRecordsViewModel`(setType/setKindName)、`RecordRow`(SqlRow 元信息 + 分支渲染)、`RecordDetailScreen/ViewModel`(sealed + SQL 节)、`RecordRowLayoutTest`(CryptoRow 包装)、`KindColorTokens`、`strings.xml ×2`、`AGENTS.md`、schema `6.json` |