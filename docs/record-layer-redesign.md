# Modev 记录数据层一体化重构 — 任务文档（V22）

> 目标：单表精准查询/筛选（应用·类型·类别）、噪点类别默认停用、统一专业 CRUD 服务（前端 + 未来 MCP 共用）。
> 验收标准：代码/架构达大厂级；`gradlew :app:lintModernRelease` 零新增 warning；`:app:testModernDebugUnitTest` 全绿；新增/改写均有测试。

## 设计总则
- **单一事实源**：`crypto_records` 单表 + `recordType` 维度（crypto/db）不变；新增 `CategoryTaxonomy` 作为「类型↔类别↔默认态↔噪点级↔顺序」唯一来源，取代 UI 硬编码枚举遍历。
- **单一门面**：所有查询/增删改查经 `RecordService` 接口（由 `LogRepository` 实现），DAO 不对外暴露；MCP 未来仅依赖 `RecordService` + `RecordFilter` + `CategoryTaxonomy`。
- **精准筛选**：`RecordFilter` 新增 `operations`/`timeRange`/`sort`；`kinds` 经 `CategoryTaxonomy.validateKinds(recordType, kinds)` 作用域收敛，空交集在仓库层短路返回零结果（避免跨类型 kind 名碰撞）。
- **高效 & DRY**：count / delete / slice 三查询共享同一 `const val` WHERE 片段（`CryptoRecordDao.Companion.WHERE_FILTER`），杜绝筛选项漂移；列表投影 + `PAGING_WINDOW_CAP` 保留。
- **向后兼容**：`RecordFilter` 新增字段全部可空/有默认；`HookSwitch` 默认变更仅影响新安装（`fromJson` 保留旧 JSON）。

## 任务清单

| 任务 | 状态 | 备注 |
|------|------|------|
| T1 CategoryTaxonomy 单一事实源 | 待办 | |
| T2 HookSwitch 噪点类别默认停用 | 待办 | |
| T3 RecordFilter 增强 + 作用域校验 | 待办 | |
| T4 RecordQueryBuilder + DAO 扩展 + 迁移 v7→v8 | 待办 | |
| T5 RecordService 专业门面 | 待办 | |
| T6 UI 重构（设置 + 筛选）驱动分类法 | 待办 | |
| T7 MCP-ready 契约文档 / KDoc | 待办 | |
| 收尾 运行 lint + 单测 + 修复回归 | 待办 | |

## T1 — CategoryTaxonomy 单一事实源
- 新增 `core/model/CategoryTaxonomy.kt`：`enum class NoiseTier { SIGNAL, NOISE, NOISE_HIGH }`；`data class RecordCategory(val id, val type, val defaultEnabled, val noiseTier, val order)`；`object CategoryTaxonomy { all() / forType(type) / byId(type,id) / validateKinds(recordType, kinds) / defaultEnabledIds(type) }`。
- `id` 与 `CryptoKind.name`/`SqlKind.name` 对齐；标签解析仍走 `kindLabelRes`/`sqlKindLabelRes`（本类保持 Android 资源无关）。
- 类别顺序与默认态（推荐集）：
  - 算法：CIPHER(开,SIGNAL,0) MESSAGE_DIGEST(开,1) MAC(开,2) SIGNATURE(开,3) KEY_PARAM(关,NOISE_HIGH,4) KEY_GENERATION(关,5) KEY_FACTORY(关,6) KEY_STORE(关,7) KEY_AGREEMENT(关,8) RANDOM(关,9) BASE64(关,10) WEB_CRYPTO(关,11)。
  - 数据库：OPEN(关,NOISE_HIGH,0) QUERY(开,SIGNAL,1) DML(开,2) EXEC(开,3) TRANSACTION(关,NOISE_HIGH,4)。
- 测试：`CategoryTaxonomyTest`（默认集、forType 顺序、validateKinds 收敛/空交集）。

## T2 — HookSwitch 噪点类别默认停用
- 改 `core/model/HookSwitch.kt` 字段默认值：`base64=false`、`webView=false`、`keyParam/keyGeneration/keyFactory/keyStore/keyAgreement/random=false`；`dbOpen=false`、`dbTransaction=false`；其余算法/数据库类别保持 `true`。
- 不动 `fromJson`（旧 JSON 仍保留用户配置，仅新安装/清数据生效）。
- 测试：`HookSwitchTest` 断言新默认；`SettingsRepositoryTest` 回归旧 JSON。

## T3 — RecordFilter 增强 + 作用域校验
- `data/repo/RecordFilter.kt` 新增 `operations: Set<String>? = null`、`timeRange: LongRange? = null`、`sort: SortOrder = SortOrder.DESC`（`enum class SortOrder { DESC, ASC }`）。
- 新增 `normalize()`：单类型时收敛 `kinds`/`operations` 到该类型合法集合；返回收敛后副本（空交集仍为非空 `Set` 表示显式无匹配）。
- `validateKinds` 语义（在 `CategoryTaxonomy`）：`kinds==null/空` → `null`（不过滤）；`recordType==null` → 原样返回；否则返回与类型相交集合（可能为空 `Set`）。
- 测试：`LogRepositoryFilterTest` 扩用例（时间范围、operation、单类型 kind 收敛短路）。

## T4 — RecordQueryBuilder + DAO 扩展 + 迁移 v7→v8
- `CryptoRecordDao.Companion.WHERE_FILTER`（`const val`，WHERE 片段）：含 `recordTypes/packages/kinds/operations IN`（哨兵 `:xCount=0` 短路）、`timeStart/timeEnd`、`query LIKE`（同现有列集）。
- DAO 新增/扩展：
  - `observePagedRowsByFilter` / `observeCountByFilter`：扩展参数 `operations/list + opCount`、`timeStart/timeEnd`、`sortAsc`（追加默认值，兼容既有调用）。
  - `fun deleteByFilter(...): Int`（用 `WHERE_FILTER`，返回删除数）。
  - `fun sliceByFilter(...): List<CryptoRecordRow>`（同步切片，供 MCP `queryPage`，带 `limit/offset/sortAsc`）。
  - `fun observeCategoryCounts(type: String): List<KindCount>` 等效（`GROUP BY kind WHERE recordType=:type`）。
  - `@Upsert fun upsert(entity)` / `@Query("DELETE ... WHERE id=:id") fun deleteById(id): Int`。
- 索引 + 迁移：`CryptoRecordEntity` 增加 `@Index(["recordType","kind"])`、`@Index(["recordType","packageName","timestamp"])`；`ModevDatabase` 版本 7→8，新增 `MIGRATION_7_8`（按 Room 生成命名 `index_crypto_records_recordType_kind` / `index_crypto_records_recordType_packageName_timestamp` 建索引）。同步更新 `CryptoRecordDaoTest` 全部 4 处 `.addMigrations(...)` 列表 + `composite indexes exist` 断言 + 新增 `v7→v8` 迁移测试。
- 测试：`RecordQueryBuilderTest`（WHERE 等价性）、`CryptoRecordDaoTest` 扩（deleteByFilter/slice/categoryCounts/upsert/deleteById/v7→v8）。

## T5 — RecordService 专业门面
- `data/repo/RecordService.kt`（`interface`）：`observe(filter): Flow<PagingData<RowModel>>`、`suspend queryPage(filter, page, pageSize): RecordPage`、`suspend count(filter): Long`、`suspend get(id): RecordDetailModel?`、`suspend neighbor(id, newer): Long?`、`suspend create(entity): Long`、`suspend update(entity): Boolean`、`suspend delete(id): Boolean`、`suspend deleteByFilter(filter): Long`、`suspend clearPackage/clearByType/clearAll`、`observePackages()`、`observeTypeCounts()`、`suspend categoryCounts(type): Map<String,Long>`。
- `LogRepository` 实现 `RecordService`：`pagedRecords` 改为构建 `RecordFilter` 委托 `observe`；`paged/counts/observeById/clear*` 提升为接口实现；新增方法复用 DAO；空 kind 交集在 `observe`/`count` 短路。
- `RecordPage` 数据类（items + total + page + pageSize）。
- 测试：`RecordServiceCrudTest`（增→查→改→删闭环；`deleteByFilter` 删除数 == 前置 `count`；`queryPage` 与 `observe` 计数一致；`categoryCounts` 与 `count` 一致）。

## T6 — UI 重构（设置 + 筛选）驱动分类法
- `AlgorithmSettingsScreen`/`DatabaseSettingsScreen`：类别开关列表由 `CategoryTaxonomy.forType(type)` 驱动（顺序/默认态单一来源）；图标经 UI 侧 `Map<kindName, ImageVector>` 解析；`enabled = master && switch.enabled/dbEnabled(kind)` 保留；toggle 走 `viewModel.setSwitch { copy(...) }`。
- `MonitorRecordsScreen` 筛选菜单：类别选项由 `CategoryTaxonomy.forType(recordType)` 提供；选中 kind 经 `filter.normalize()` 作用域收敛后下发。
- 保留 `HardcodedStringConventionTest`/`StringResourceParityTest` 绿（标签复用既有 `kind_*` 资源，无需新增）。
- 测试：现有 `AlgorithmSettingsScreenTest`/`DatabaseSettingsScreenTest` 保持绿；新增「新安装默认关类别初始 off」断言。

## T7 — MCP-ready 契约文档 / KDoc
- `RecordService` KDoc 标注「MCP 唯一入口，禁止直触 DAO」；`RecordFilter` 标注 JSON 可序列化契约（字段→wire 映射）；`CategoryTaxonomy` 标注「MCP 元信息来源」。
- 不实现 MCP server（本期仅契约）。更新 AGENTS.md 相关契约条目（索引迁移、RecordService 门面、类别默认集、RecordFilter 作用域）。

## 风险与缓解
- **迁移**：`exportSchema=true` 无 schema 目录时不阻断编译；v7→v8 必须建索引且命名与 Room 生成一致，否则运行时 schema 校验抛异常 → 新增迁移测试覆盖。
- **默认变更回溯**：仅新安装生效（已确认），不引入 config schemaVersion。
- **空 kind 交集**：仓库层短路返回零结果，避免 `kindCount=0` 误判为「不过滤」。
- **共享 WHERE**：count/delete/slice 必须用同一 `WHERE_FILTER` 片段，防「删≠筛」。
