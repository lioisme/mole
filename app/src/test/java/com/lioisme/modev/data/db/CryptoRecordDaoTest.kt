package com.lioisme.modev.data.db

import androidx.paging.PagingSource
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.SortOrder
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Room DAO 过滤/排序/分页/计数/邻接查询 + 复合索引(Robolectric 内存库)。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoRecordDaoTest {

    private lateinit var db: ModevDatabase
    private lateinit var dao: CryptoRecordDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        dao = db.cryptoRecordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        packageName: String,
        kind: String,
        operation: String,
        algorithm: String,
        timestamp: Long,
        id: Long = 0L,
    ) = CryptoRecordEntity(
        id = id,
        packageName = packageName,
        kind = kind,
        operation = operation,
        algorithm = algorithm,
        threadName = "main",
        timestamp = timestamp,
    )

    @Test
    fun `paged query filters by record type`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES", 100))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 200).copy(recordType = "database"))

        // recordType = null 不过滤(跨类型合计)
        assertEquals(2, page().size)
        // 按类型过滤:仅 crypto
        assertEquals(1, page(recordTypes = setOf("crypto")).size)
        assertEquals("cipher", page(recordTypes = setOf("crypto")).single().kind)
    }

    @Test
    fun `counts and packages respect record type filter`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 1))
        dao.insert(entity("pkg.a", "digest", "op2", "SHA-256", 2).copy(recordType = "database"))

        assertEquals(1L, dao.counts("crypto").single().count)
        assertEquals(listOf("pkg.a"), dao.packagesOver(0).map { it.packageName }.sorted())
        // null = 不过滤
        assertEquals(2L, dao.counts(null).single().count)
        assertEquals(1L, dao.observeRecordCounts("crypto").first().single().count)
    }

    @Test
    fun `neighbor queries respect record type filter`() = runBlocking {
        val op1Id = dao.insertReturningId(entity("pkg.a", "cipher", "op1", "AES", 100))
        val cryptoId = dao.insertReturningId(entity("pkg.a", "cipher", "op2", "AES", 200))
        val dbId = dao.insertReturningId(entity("pkg.a", "cipher", "op3", "AES", 300).copy(recordType = "database"))

        // crypto 视角:op2 之后无更近的 crypto 记录(database 记录不可见)
        assertNull(neighbor("crypto", "pkg.a", 200, cryptoId, newer = true))
        // 不过滤视角:database 记录就是更新的邻居
        assertEquals(dbId, neighbor(null, "pkg.a", 200, cryptoId, newer = true))
        // 更旧方向:两种视角都回到 op1
        assertEquals(op1Id, neighbor("crypto", "pkg.a", 200, cryptoId, newer = false))
    }

    /**
     * PagingSource 执行位的窗口上限(= 生产 `LogRepository.PAGING_WINDOW_CAP` 同义:
     * Room 生成式计数会按 SQL 自带 LIMIT 钳制 itemCount,传小 limit 会死锁于第一页,
     * AGENTS #14 / V20 R3.1 实锤)。
     */
    private val PAGING_TEST_WINDOW = 20_000

    /**
     * 对账(UI 路径 vs MCP 路径,V45 T1.1):同一 filter 经 `pagedRows`(PagingSource)
     * 与 `sliceRows`(切片)必须返回同一批行与同一顺序——「双端同一查询核心」的执行位证据。
     */
    @Test
    fun pagingSourcePathAgreesWithSlice() = runBlocking {
        for (i in 1..5) dao.insert(entity("pkg.a", "cipher", "op$i", "AES", i.toLong()))
        dao.insert(entity("pkg.b", "mac", "other", "HmacSHA256", 6))

        val filter = RecordFilter(packages = setOf("pkg.a"))
        val viaPaging = paged(filter, loadSize = 5).map { it.id }
        val viaSlice = rows(packages = setOf("pkg.a")).map { it.id }
        assertEquals(viaSlice, viaPaging)
        assertEquals(5, viaPaging.size)
    }

    /**
     * 行加载助手(V45 T1.1:谓词唯一来源 = [RecordQueryFactory],执行位 = `@RawQuery`)。
     *
     * 旧助手承载「哨兵计数」契约(`typeCount = 0` 跳过该维),该契约已随哨兵 WHERE 片段
     * 一并退役——现在**集合缺省(null)= 不过滤**,非空集合 = IN 多值,与
     * [com.lioisme.modev.domain.record.RecordFilter] 声明语义逐字一致;
     * `query` 传**裸关键字**(LIKE 模式与转义由生成器负责,调用方不再拼 `%`)。
     * 默认走切片执行位(LIMIT/OFFSET 直白);PagingSource 执行位由
     * [pagingSourcePathAgreesWithSlice] 专门对账(UI 路径)。
     */
    private suspend fun page(
        recordTypes: Set<String>? = null,
        packages: Set<String>? = null,
        kinds: Set<String>? = null,
        operations: Set<String>? = null,
        query: String? = null,
        limit: Int = 20,
        offset: Int = 0,
        sort: SortOrder = SortOrder.DESC,
    ): List<CryptoRecordRow> = rows(
        recordTypes = recordTypes,
        packages = packages,
        kinds = kinds,
        operations = operations,
        query = query,
        limit = limit,
        offset = offset,
        sort = sort,
    )

    /** 切片执行位助手(MCP / 导出路径)。 */
    private suspend fun rows(
        recordTypes: Set<String>? = null,
        packages: Set<String>? = null,
        kinds: Set<String>? = null,
        operations: Set<String>? = null,
        query: String? = null,
        limit: Int = 20,
        offset: Int = 0,
        sort: SortOrder = SortOrder.DESC,
    ): List<CryptoRecordRow> = dao.sliceRows(
        RecordQueryFactory.rows(
            filterOf(recordTypes, packages, kinds, operations, query, sort),
            limit = limit,
            offset = offset,
            previewChars = 48,
        ),
    )

    /** PagingSource 执行位助手(UI 路径;窗口上限同生产 `PAGING_WINDOW_CAP` 语义)。 */
    private suspend fun paged(
        filter: RecordFilter,
        loadSize: Int = 20,
    ): List<CryptoRecordRow> {
        val source = dao.pagedRows(
            RecordQueryFactory.rows(filter, limit = PAGING_TEST_WINDOW, offset = 0, previewChars = 48),
        )
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = loadSize, placeholdersEnabled = false),
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    /** 计数执行位助手。 */
    private suspend fun countOf(
        recordTypes: Set<String>? = null,
        packages: Set<String>? = null,
        kinds: Set<String>? = null,
        query: String? = null,
    ): Long = dao.countByQuery(
        RecordQueryFactory.count(filterOf(recordTypes, packages, kinds, null, query, SortOrder.DESC)),
    ).first()

    /** 邻接 id 执行位助手([newer] = true 取更新、false 取更旧)。 */
    private suspend fun neighbor(
        recordType: String?,
        packageName: String,
        ts: Long,
        id: Long,
        newer: Boolean,
    ): Long? = dao.neighborIdByQuery(
        RecordQueryFactory.neighborId(
            filter = RecordFilter(
                recordTypes = recordType?.let { setOf(it) },
                packages = setOf(packageName),
            ),
            ts = ts,
            id = id,
            newer = newer,
        ),
    )

    private fun filterOf(
        recordTypes: Set<String>?,
        packages: Set<String>?,
        kinds: Set<String>?,
        operations: Set<String>?,
        query: String?,
        sort: SortOrder,
    ) = RecordFilter(
        recordTypes = recordTypes,
        packages = packages,
        kinds = kinds,
        operations = operations,
        query = query,
        sort = sort,
    )

    @Test
    fun `paged query orders by timestamp desc then id desc`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES", 100))
        dao.insert(entity("pkg.a", "cipher", "decrypt", "AES", 200))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 100))

        val rows = page()
        assertEquals(3, rows.size)
        assertEquals(listOf(200L, 100L, 100L), rows.map { it.timestamp })
        // 同时间戳按 id 倒序:先插的 cipher(id 小)排在 digest 之后
        assertTrue(rows[1].id > rows[2].id)
        assertEquals("digest", rows[1].kind)
        assertEquals("cipher", rows[2].kind)
    }

    @Test
    fun `paged query filters by package and kind`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES", 100))
        dao.insert(entity("pkg.b", "cipher", "encrypt", "AES", 200))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 300))

        assertEquals(2, page(packages = setOf("pkg.a")).size)
        assertEquals(1, page(packages = setOf("pkg.b")).size)
        assertEquals(1, page(packages = setOf("pkg.a"), kinds = setOf("digest")).size)
        assertEquals(0, page(packages = setOf("pkg.a"), kinds = setOf("mac")).size)
    }

    @Test
    fun `paged query keyword search covers algorithm operation and key text`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES/CBC/PKCS5Padding", 100))
        dao.insert(entity("pkg.a", "cipher", "decrypt", "AES/ECB", 200))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 300))

        assertEquals(1, page(query = "CBC").size)
        assertEquals(1, page(query = "decrypt").size)
        assertEquals(0, page(query = "HmacSHA1").size)
        // 生成器负责转义:字面 % 不得被当作通配符
        assertEquals(0, page(query = "CBC%25").size)
    }

    @Test
    fun `paged query applies limit and offset`() = runBlocking {
        for (i in 1..5) dao.insert(entity("pkg.a", "cipher", "op$i", "AES", i.toLong()))

        val first = page(limit = 2)
        val second = page(limit = 2, offset = 2)
        assertEquals(2, first.size)
        assertEquals(2, second.size)
        // 时间倒序分页不重叠且衔接
        assertEquals(listOf(5L, 4L), first.map { it.timestamp })
        assertEquals(listOf(3L, 2L), second.map { it.timestamp })
    }

    @Test
    fun `record counts group by package`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 1))
        dao.insert(entity("pkg.a", "digest", "op2", "SHA-256", 2))
        dao.insert(entity("pkg.b", "cipher", "op3", "AES", 3))

        val counts = dao.observeRecordCounts(null).first()
        assertEquals(mapOf("pkg.a" to 2L, "pkg.b" to 1L), counts.associate { it.packageName to it.count })
    }

    @Test
    fun `paged rows by filter supports IN collections and dimension skip`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES", 100))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 200).copy(recordType = "database"))
        dao.insert(entity("pkg.b", "mac", "mac", "HmacSHA256", 300))

        // 维度全缺省 = 不过滤(全部 3 条;谓词里根本不出这些列)
        assertEquals(3, page().size)
        // 类型 IN 多值
        assertEquals(2, page(recordTypes = setOf("crypto")).size)
        assertEquals(
            3,
            page(recordTypes = setOf("crypto", "database")).size,
        )
        // 包 IN 多值(pkg.a 两行 + pkg.b 一行 = 3)
        assertEquals(
            3,
            page(packages = setOf("pkg.a", "pkg.b")).size,
        )
        assertEquals(2, page(packages = setOf("pkg.a")).size)
        // 类别 IN
        assertEquals(1, page(kinds = setOf("mac")).size)
        // 组合过滤
        assertEquals(
            1,
            page(
                recordTypes = setOf("database"),
                packages = setOf("pkg.a"),
            ).size,
        )
        // 关键字与已启用维度共存(关键字走生成器转义的 LIKE)
        assertEquals(
            1,
            page(
                packages = setOf("pkg.a"),
                query = "SHA",
            ).size,
        )
    }

    @Test
    fun `count by filter totals with IN collections and query`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES", 100))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 200).copy(recordType = "database"))
        dao.insert(entity("pkg.b", "mac", "mac", "HmacSHA256", 300))

        assertEquals(3L, countOf())
        assertEquals(2L, countOf(recordTypes = setOf("crypto")))
        assertEquals(3L, countOf(packages = setOf("pkg.a", "pkg.b")))
        assertEquals(1L, countOf(kinds = setOf("mac"), query = "Hmac"))
    }

    @Test
    fun `record type counts group by type`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 1))
        dao.insert(entity("pkg.a", "digest", "op2", "SHA-256", 2))
        dao.insert(entity("pkg.b", "mac", "op3", "HmacSHA256", 3).copy(recordType = "database"))

        val counts = dao.observeRecordTypeCounts().first()
        assertEquals(
            mapOf("crypto" to 2L, "database" to 1L),
            counts.associate { it.recordType to it.count },
        )
    }

    @Test
    fun `neighbor id queries respect same-package and tie-break by id`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 100))
        val mid = dao.insertReturningId(entity("pkg.a", "cipher", "op2", "AES", 100))
        val newerId = dao.insertReturningId(entity("pkg.a", "cipher", "op3", "AES", 300))
        dao.insert(entity("pkg.b", "cipher", "opx", "AES", 100))

        val older = neighbor(null, "pkg.a", 300, mid, newer = false)
        val newer = neighbor(null, "pkg.a", 100, mid, newer = true)
        // 仅投影 id:op3 的相邻上一条 = 同包最新更旧记录 op2
        assertEquals(mid, older)
        assertEquals(newerId, newer)
        // 与 op2 同时间戳时按 id 决胜:仅 op1 严格更旧
        assertEquals(1L, neighbor(null, "pkg.a", 100, mid, newer = false))
        // 邻居查询不跨包
        assertNull(neighbor(null, "pkg.b", 100, mid, newer = false))
    }

    @Test
    fun `paged rows projection carries previews without full hex`() = runBlocking {
        val bigHex = "ab".repeat(4096) // 8KB 字符:远超列表预览窗口
        val id = dao.insertReturningId(
            entity("pkg.a", "cipher", "encrypt", "AES", 100).copy(
                keyHex = bigHex,
                keyTruncated = true,
                inputHex = "0102",
                outputHex = bigHex,
            ),
        )
        val row = page().single()
        assertEquals(id, row.id)
        assertEquals("pkg.a", row.packageName)
        assertEquals("encrypt", row.operation)
        // SQL 侧预览裁剪:仅 48 字符,不携带全量大字段
        assertEquals(48, row.keyPreview!!.length)
        assertEquals("0102", row.inputPreview)
        assertTrue(row.keyTruncated)
        assertEquals(48, row.outputPreview!!.length)
    }

    @Test
    fun `paged rows applies same filters as full query`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES/CBC", 100))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 200))
        dao.insert(entity("pkg.b", "cipher", "encrypt", "AES", 300))

        // 唯一投影语义面(observePagedRowsByFilter):过滤与排序同全查询一致。
        assertEquals(2, page(packages = setOf("pkg.a")).size)
        assertEquals(1, page(packages = setOf("pkg.a"), kinds = setOf("digest")).size)
        assertEquals(1, page(query = "CBC").size)
    }

    @Test
    fun `observeById returns single record`() = runBlocking {
        val id = dao.insertReturningId(entity("pkg.a", "cipher", "op1", "AES", 100))
        val found = dao.observeById(id).first()
        assertNotNull(found)
        assertEquals("pkg.a", found?.packageName)
        assertNull(dao.observeById(id + 999).first())
    }

    /**
     * V43 T7(V5.13):类型计数投影映射钉死——domain RecordTypeCount 去
     * androidx.room.ColumnInfo 后,SQL 别名必须与属性名 `count` 对齐
     * (Room 默认按属性名取列);本测试即别名漂移的运行时门禁。
     */
    @Test
    fun `observe record type counts maps count alias`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES", 100))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 200))
        dao.insert(entity("pkg.b", "cipher", "encrypt", "AES", 300).copy(recordType = "database"))

        val counts = dao.observeRecordTypeCounts().first().associate { it.recordType to it.count }
        assertEquals(mapOf("crypto" to 2L, "database" to 1L), counts)
    }

    @Test
    fun `clearPackage removes only that package`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 1))
        dao.insert(entity("pkg.b", "cipher", "op2", "AES", 2))

        dao.clearPackage("pkg.a")

        assertEquals(0, dao.count("pkg.a"))
        assertEquals(1, dao.count("pkg.b"))
    }

    @Test
    fun `composite indexes exist on package plus timestamp and kind`() {
        val sqlite: SupportSQLiteDatabase = db.openHelper.writableDatabase
        val names = mutableListOf<String>()
        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'crypto_records'")
            .use { c -> while (c.moveToNext()) names.add(c.getString(0)) }
        assertTrue(names.contains("index_crypto_records_packageName_timestamp"))
        assertTrue(names.contains("index_crypto_records_packageName_kind"))
        // v8:多维筛选复合索引
        assertTrue(names.contains("index_crypto_records_recordType_kind"))
        assertTrue(names.contains("index_crypto_records_recordType_packageName_timestamp"))
        // v3:重放幂等唯一索引
        assertTrue(names.contains("index_crypto_records_packageName_processName_seq"))
        // v5:timestamp 单列索引(时间倒序热路径)
        assertTrue(names.contains("index_crypto_records_timestamp"))
    }

    @Test
    fun `insertAll replace dedups replay by package process seq`() = runBlocking {
        val original = entity("pkg.a", "cipher", "encrypt", "AES", 1).copy(
            processName = "pkg.a",
            seq = 5L,
        )
        dao.insertAll(listOf(original))
        // 断线重放副本:(packageName, processName, seq) 冲突 → REPLACE 幂等吸收
        dao.insertAll(listOf(original.copy(timestamp = 999L)))

        assertEquals(1, dao.count("pkg.a"))
        assertEquals(999L, page(packages = setOf("pkg.a")).single().timestamp)
    }

    @Test
    fun `null seq rows do not collide on unique index`() = runBlocking {
        val base = entity("pkg.a", "cipher", "encrypt", "AES", 1).copy(processName = "pkg.a", seq = null)
        // v2 存量行 seq 均为 NULL:SQLite 唯一索引视 NULL 互不相等,允许共存
        dao.insertAll(listOf(base, base.copy(timestamp = 2L)))
        assertEquals(2, dao.count("pkg.a"))
    }

    @Test
    fun `insertAll inserts batch in one call`() = runBlocking {
        val batch = (1..10).map { entity("pkg.a", "cipher", "op$it", "AES", it.toLong()) }
        dao.insertAll(batch)
        assertEquals(10, dao.count("pkg.a"))
        assertEquals(10, dao.counts(null).single().count)
    }

    @Test
    fun `counts snapshot groups by package`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 1))
        dao.insert(entity("pkg.a", "digest", "op2", "SHA-256", 2))
        dao.insert(entity("pkg.b", "cipher", "op3", "AES", 3))
        val counts = dao.counts(null).associate { it.packageName to it.count }
        assertEquals(mapOf("pkg.a" to 2L, "pkg.b" to 1L), counts)
    }

    /**
     * V45 T1.6 截断点键与列表排序键同构:并列同戳必须按 id 定序精确保留 keep 条
     * (旧 `id NOT IN (…LIMIT)` 天然精确,新形态若漏掉 `id <=` 即静默多留)。
     */
    @Test
    fun `trim keeps exactly keep rows when timestamps tie`() = runBlocking {
        (1..5).forEach { dao.insert(entity("pkg.a", "cipher", "op$it", "AES", 7L)) }
        val before = page(packages = setOf("pkg.a")).map { it.id }
        val deleted = dao.trim(keep = 2)
        assertEquals(3, deleted)
        assertEquals(2, dao.count("pkg.a"))
        // 保留集 = 现排序键下的前 keep 行(与列表首屏同构)
        assertEquals(before.take(2), page(packages = setOf("pkg.a")).map { it.id })
    }

    @Test
    fun `global trim keeps newest and returns deleted count`() = runBlocking {
        for (i in 1..6) dao.insert(entity("pkg.a", "cipher", "op$i", "AES", i.toLong()))
        val deleted = dao.trim(keep = 2)
        assertEquals(4, deleted)
        assertEquals(2, dao.count("pkg.a"))
        assertEquals(listOf(6L, 5L), page(packages = setOf("pkg.a")).map { it.timestamp })
    }

    @Test
    fun `per-package trim keeps newest of that package only`() = runBlocking {
        for (i in 1..4) dao.insert(entity("pkg.a", "cipher", "op$i", "AES", i.toLong()))
        dao.insert(entity("pkg.b", "cipher", "op1", "AES", 1L))
        val deleted = dao.trimPackage("pkg.a", keep = 2)
        assertEquals(2, deleted)
        assertEquals(2, dao.count("pkg.a"))
        assertEquals(1, dao.count("pkg.b"))
        assertEquals(listOf(4L, 3L), page(packages = setOf("pkg.a")).map { it.timestamp })
    }

    @Test
    fun `migration from v1 keeps v1 data and adds the six capture fields`() {
        runBlocking {
        val dbFile = RuntimeEnvironment.getApplication().getDatabasePath("mig-v1.db")
        dbFile.delete()
        val oldHelper = createV1Helper(dbFile)
        oldHelper.writableDatabase.execSQL(
            """
            INSERT INTO crypto_records
                (packageName, kind, operation, algorithm, threadName, timestamp,
                 keyTruncated, ivTruncated, keyExportable, inputTruncated, outputTruncated, contextId)
            VALUES ('pkg.v1', 'cipher', 'encrypt', 'AES', 'main', 1, 0, 0, 1, 0, 0, 0)
            """.trimIndent()
        )
        oldHelper.close()

        val migrated = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
            "mig-v1.db",
        ).addMigrations(
            ModevDatabase.MIGRATION_1_2,
            ModevDatabase.MIGRATION_2_3,
            ModevDatabase.MIGRATION_3_4,
            ModevDatabase.MIGRATION_4_5,
            ModevDatabase.MIGRATION_5_6,
            ModevDatabase.MIGRATION_6_7,
            ModevDatabase.MIGRATION_7_8,
            ModevDatabase.MIGRATION_8_9,
            ModevDatabase.MIGRATION_9_10,
            ModevDatabase.MIGRATION_10_11,
            ModevDatabase.MIGRATION_11_12,
            ModevDatabase.MIGRATION_12_13,
            ModevDatabase.MIGRATION_13_14,
        ).build()
        val migratedDao = migrated.cryptoRecordDao()

        // v1 数据完整保留
        val row = migratedDao.observeById(1).first()
        assertNotNull(row)
        assertEquals("pkg.v1", row!!.packageName)
        assertEquals("AES", row.algorithm)
        assertEquals("encrypt", row.operation)

        // 新列已就位且可写可读
        val inserted = CryptoRecordEntity(
            packageName = "pkg.v2",
            kind = "key_store",
            operation = "get_key",
            algorithm = "KeyStore",
            keyExportable = true,
            keyTruncated = false,
            ivTruncated = false,
            inputTruncated = false,
            outputTruncated = false,
            saltTruncated = false,
            aadTruncated = false,
            publicKeyTruncated = false,
            saltHex = "0102",
            verifyResult = true,
            processName = "pkg.v2:push",
            contextId = 7L,
            threadName = "worker",
            timestamp = 2L,
        )
        val insertedId = migratedDao.insertReturningId(inserted)
        val back = migratedDao.observeById(insertedId).first()
        assertEquals("0102", back!!.saltHex)
        assertEquals(true, back.verifyResult)
        assertEquals("pkg.v2:push", back.processName)
        assertEquals(7L, back.contextId)

        // 新列含入索引查询(每包治理)
        assertEquals(2L, migratedDao.counts(null).sumOf { it.count })
        migrated.close()
        dbFile.delete()
        }
    }

    @Test
    fun `migration from v2 to v3 keeps data adds seq column and unique index`() {
        runBlocking {
            val dbFile = RuntimeEnvironment.getApplication().getDatabasePath("mig-v2.db")
            dbFile.delete()
            val oldHelper = createV2Helper(dbFile)
            oldHelper.writableDatabase.execSQL(
                """
                INSERT INTO crypto_records
                    (packageName, kind, operation, algorithm, threadName, timestamp,
                     keyTruncated, ivTruncated, keyExportable, inputTruncated, outputTruncated,
                     saltTruncated, aadTruncated, publicKeyTruncated, contextId, processName)
                VALUES ('pkg.v2', 'cipher', 'encrypt', 'AES', 'main', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 'pkg.v2')
                """.trimIndent()
            )
            oldHelper.close()

            val migrated = Room.databaseBuilder(
                RuntimeEnvironment.getApplication(),
                ModevDatabase::class.java,
                "mig-v2.db",
).addMigrations(
            ModevDatabase.MIGRATION_1_2,
            ModevDatabase.MIGRATION_2_3,
            ModevDatabase.MIGRATION_3_4,
            ModevDatabase.MIGRATION_4_5,
            ModevDatabase.MIGRATION_5_6,
            ModevDatabase.MIGRATION_6_7,
            ModevDatabase.MIGRATION_7_8,
            ModevDatabase.MIGRATION_8_9,
            ModevDatabase.MIGRATION_9_10,
            ModevDatabase.MIGRATION_10_11,
            ModevDatabase.MIGRATION_11_12,
            ModevDatabase.MIGRATION_12_13,
            ModevDatabase.MIGRATION_13_14,
        ).build()
        val migratedDao = migrated.cryptoRecordDao()

        // v2 存量数据保留,seq 列新增且存量行为 NULL
        val row = migratedDao.observeById(1).first()
            assertNotNull(row)
            assertEquals("pkg.v2", row!!.packageName)
            assertEquals(null, row.seq)

            // 新行带 seq 可写可读;唯一索引已就位
            val withSeq = entity("pkg.v3", "cipher", "encrypt", "AES", 2).copy(
                processName = "pkg.v3",
                seq = 9L,
            )
            migratedDao.insertAll(listOf(withSeq))
            migratedDao.insertAll(listOf(withSeq.copy(timestamp = 3L))) // 重放 → REPLACE
            assertEquals(2L, migratedDao.counts(null).sumOf { it.count })

            val sqlite: SupportSQLiteDatabase = migrated.openHelper.writableDatabase
            val names = mutableListOf<String>()
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'crypto_records'")
                .use { c -> while (c.moveToNext()) names.add(c.getString(0)) }
            assertTrue(names.contains("index_crypto_records_packageName_processName_seq"))

            migrated.close()
            dbFile.delete()
        }
    }

    private fun createV1Helper(dbFile: java.io.File): androidx.sqlite.db.SupportSQLiteOpenHelper {
        // 严格复刻 v1 表结构(无 DEFAULT、无 v2 新列)+ 实体声明的两个复合索引
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        return factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                RuntimeEnvironment.getApplication()
            ).name(dbFile.path)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(V1_TABLE_DDL)
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_timestamp " +
                                    "ON crypto_records (packageName, timestamp)"
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_kind " +
                                    "ON crypto_records (packageName, kind)"
                            )
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            // v1 已是最高版本,无升级路径
                        }
                    }
                ).build()
        )
    }

    private fun createV2Helper(dbFile: java.io.File): androidx.sqlite.db.SupportSQLiteOpenHelper {
        // 严格复刻 v2 表结构(v1 + MIGRATION_1_2 的 13 个新列)
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        return factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                RuntimeEnvironment.getApplication()
            ).name(dbFile.path)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(V1_TABLE_DDL)
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_timestamp " +
                                    "ON crypto_records (packageName, timestamp)"
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_kind " +
                                    "ON crypto_records (packageName, kind)"
                            )
                            val v2Columns = listOf(
                                "saltHex TEXT", "saltText TEXT", "saltLength INTEGER",
                                "saltTruncated INTEGER NOT NULL DEFAULT 0",
                                "aadHex TEXT", "aadText TEXT", "aadLength INTEGER",
                                "aadTruncated INTEGER NOT NULL DEFAULT 0",
                                "publicKeyHex TEXT", "publicKeyText TEXT", "publicKeyLength INTEGER",
                                "publicKeyTruncated INTEGER NOT NULL DEFAULT 0",
                                "paramsJson TEXT", "verifyResult INTEGER", "processName TEXT",
                            )
                            for (column in v2Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                        }
                    }
                ).build()
        )
    }

    @Test
    fun `migration from v4 to v5 keeps data and adds timestamp index`() {
        runBlocking {
            val dbFile = RuntimeEnvironment.getApplication().getDatabasePath("mig-v4.db")
            dbFile.delete()
            val oldHelper = createV4Helper(dbFile)
            oldHelper.writableDatabase.execSQL(
                """
                INSERT INTO crypto_records
                    (recordType, packageName, kind, operation, algorithm, threadName, timestamp,
                     keyTruncated, ivTruncated, keyExportable, inputTruncated, outputTruncated,
                     saltTruncated, aadTruncated, publicKeyTruncated, contextId, processName)
                VALUES ('crypto', 'pkg.v4', 'cipher', 'encrypt', 'AES', 'main', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 'pkg.v4')
                """.trimIndent()
            )
            oldHelper.close()

            val migrated = Room.databaseBuilder(
                RuntimeEnvironment.getApplication(),
                ModevDatabase::class.java,
                "mig-v4.db",
            ).addMigrations(
                ModevDatabase.MIGRATION_4_5,
                ModevDatabase.MIGRATION_5_6,
                ModevDatabase.MIGRATION_6_7,
            ModevDatabase.MIGRATION_7_8,
            ModevDatabase.MIGRATION_8_9,
            ModevDatabase.MIGRATION_9_10,
            ModevDatabase.MIGRATION_10_11,
            ModevDatabase.MIGRATION_11_12,
            ModevDatabase.MIGRATION_12_13,
            ModevDatabase.MIGRATION_13_14,
            ).build()
            val migratedDao = migrated.cryptoRecordDao()

            // v4 存量数据完整保留
            val row = migratedDao.observeById(1).first()
            assertNotNull(row)
            assertEquals("pkg.v4", row!!.packageName)
            assertEquals("crypto", row.recordType)

            // timestamp 单列索引已就位
            val sqlite: SupportSQLiteDatabase = migrated.openHelper.writableDatabase
            val names = mutableListOf<String>()
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'crypto_records'")
                .use { c -> while (c.moveToNext()) names.add(c.getString(0)) }
            assertTrue(names.contains("index_crypto_records_timestamp"))

            migrated.close()
            dbFile.delete()
        }
    }

    @Test
    fun `migration from v6 to v7 keeps data and adds base64 columns`() {
        runBlocking {
            val dbFile = RuntimeEnvironment.getApplication().getDatabasePath("mig-v6.db")
            dbFile.delete()
            val oldHelper = createV6Helper(dbFile)
            oldHelper.writableDatabase.execSQL(
                """
                INSERT INTO crypto_records
                    (recordType, packageName, kind, operation, algorithm, threadName, timestamp,
                     keyTruncated, ivTruncated, keyExportable, inputTruncated, outputTruncated,
                     saltTruncated, aadTruncated, publicKeyTruncated, contextId, processName,
                     keyHex, inputHex, outputHex)
                VALUES ('crypto', 'pkg.v6', 'cipher', 'encrypt', 'AES', 'main', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 'pkg.v6',
                        'aabb', 'deadbeef', '00ff')
                """.trimIndent()
            )
            oldHelper.close()

            val migrated = Room.databaseBuilder(
                RuntimeEnvironment.getApplication(),
                ModevDatabase::class.java,
                "mig-v6.db",
            ).addMigrations(
                ModevDatabase.MIGRATION_6_7,
            ModevDatabase.MIGRATION_7_8,
            ModevDatabase.MIGRATION_8_9,
            ModevDatabase.MIGRATION_9_10,
            ModevDatabase.MIGRATION_10_11,
            ModevDatabase.MIGRATION_11_12,
            ModevDatabase.MIGRATION_12_13,
            ModevDatabase.MIGRATION_13_14,
            ).build()
            val migratedDao = migrated.cryptoRecordDao()

            // v6 存量数据完整保留,hex 不变,新 b64 列为 NULL(旧记录降级双态)
            val row = migratedDao.observeById(1).first()
            assertNotNull(row)
            assertEquals("pkg.v6", row!!.packageName)
            assertEquals("aabb", row.keyHex)
            assertNull(row.keyB64)
            assertNull(row.inputB64)
            assertNull(row.outputB64)
            assertNull(row.ivB64)
            assertNull(row.saltB64)
            assertNull(row.aadB64)
            assertNull(row.publicKeyB64)

            // 新列可写可读(三态落库)
            val withB64 = entity("pkg.v7", "cipher", "encrypt", "AES", 2).copy(
                keyHex = "aabb",
                keyB64 = "qrs=",
                inputHex = "deadbeef",
                inputB64 = "3q2+7w==",
            )
            val newId = migratedDao.insertReturningId(withB64)
            val back = migratedDao.observeById(newId).first()
            assertEquals("qrs=", back!!.keyB64)
            assertEquals("3q2+7w==", back.inputB64)

            migrated.close()
            dbFile.delete()
        }
    }

    @Test
    fun `migration from v7 to v8 adds composite indexes`() = runBlocking {
        val dbFile = RuntimeEnvironment.getApplication().getDatabasePath("mig-v7.db")
        dbFile.delete()
        val oldHelper = createV7Helper(dbFile)
        oldHelper.writableDatabase.execSQL(
            """
            INSERT INTO crypto_records
                (recordType, packageName, kind, operation, algorithm, threadName, timestamp,
                 keyTruncated, ivTruncated, keyExportable, inputTruncated, outputTruncated,
                 saltTruncated, aadTruncated, publicKeyTruncated, contextId, processName,
                 keyHex, inputHex, outputHex)
            VALUES ('crypto', 'pkg.v7', 'cipher', 'encrypt', 'AES', 'main', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 'pkg.v7',
                    'aabb', 'deadbeef', '00ff')
            """.trimIndent(),
        )
        oldHelper.close()

        val migrated = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
            "mig-v7.db",
        ).addMigrations(ModevDatabase.MIGRATION_7_8, ModevDatabase.MIGRATION_8_9, ModevDatabase.MIGRATION_9_10, ModevDatabase.MIGRATION_10_11, ModevDatabase.MIGRATION_11_12, ModevDatabase.MIGRATION_12_13, ModevDatabase.MIGRATION_13_14).build()
        val migratedDao = migrated.cryptoRecordDao()

        // v7 存量数据完整保留
        val row = migratedDao.observeById(1).first()
        assertNotNull(row)
        assertEquals("pkg.v7", row!!.packageName)
        assertEquals("aabb", row.keyHex)

        // v8 复合索引已就位且命名与实体生成一致
        val sqlite: SupportSQLiteDatabase = migrated.openHelper.writableDatabase
        val names = mutableListOf<String>()
        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'crypto_records'")
            .use { c -> while (c.moveToNext()) names.add(c.getString(0)) }
        assertTrue(names.contains("index_crypto_records_recordType_kind"))
        assertTrue(names.contains("index_crypto_records_recordType_packageName_timestamp"))

        migrated.close()
        dbFile.delete()
        Unit
    }

    @Test
    fun `migration from v8 to v9 adds behavior columns and keeps data`() = runBlocking {
        val dbFile = RuntimeEnvironment.getApplication().getDatabasePath("mig-v8.db")
        dbFile.delete()
        val oldHelper = createV8Helper(dbFile)
        oldHelper.writableDatabase.execSQL(
            """
            INSERT INTO crypto_records
                (recordType, packageName, kind, operation, algorithm, threadName, timestamp,
                 keyTruncated, ivTruncated, keyExportable, inputTruncated, outputTruncated,
                 saltTruncated, aadTruncated, publicKeyTruncated, contextId, processName, seq)
            VALUES ('crypto', 'pkg.v8', 'cipher', 'encrypt', 'AES', 'main', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 'pkg.v8', 1)
            """.trimIndent(),
        )
        oldHelper.close()

        val migrated = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
            "mig-v8.db",
        ).addMigrations(ModevDatabase.MIGRATION_8_9, ModevDatabase.MIGRATION_9_10, ModevDatabase.MIGRATION_10_11, ModevDatabase.MIGRATION_11_12, ModevDatabase.MIGRATION_12_13, ModevDatabase.MIGRATION_13_14).build()
        val migratedDao = migrated.cryptoRecordDao()

        // v8 存量数据完整保留
        val row = migratedDao.observeById(1).first()
        assertNotNull(row)
        assertEquals("pkg.v8", row!!.packageName)

        // v9 行为列已就位且可写可读(behavior 行承载,path/command/content 往返)
        val inserted = CryptoRecordEntity(
            packageName = "pkg.behavior",
            kind = "FILE_WRITE",
            operation = "OPEN_WRITE",
            algorithm = "",
            recordType = "behavior",
            path = "/data/data/pkg/files/config.json",
            destPath = null,
            command = null,
            fileSize = 42L,
            contentHex = "deadbeef",
            contextId = 3L,
            threadName = "main",
            timestamp = 5L,
            seq = 2L,
        )
        migratedDao.insert(inserted)
        val behaviorRow = migratedDao.observeById(2).first()
        assertNotNull(behaviorRow)
        assertEquals("behavior", behaviorRow!!.recordType)
        assertEquals("/data/data/pkg/files/config.json", behaviorRow.path)
        assertEquals(42L, behaviorRow.fileSize!!)
        assertEquals("deadbeef", behaviorRow.contentHex)

        migrated.close()
        dbFile.delete()
        Unit
    }

    @Test
    fun `migration from v9 to v10 adds prefKey column and keeps data`() = runBlocking {
        val dbFile = RuntimeEnvironment.getApplication().getDatabasePath("mig-v9.db")
        dbFile.delete()
        val oldHelper = createV9Helper(dbFile)
        oldHelper.writableDatabase.execSQL(
            """
            INSERT INTO crypto_records
                (recordType, packageName, kind, operation, algorithm, threadName, timestamp,
                 keyTruncated, ivTruncated, keyExportable, inputTruncated, outputTruncated,
                 saltTruncated, aadTruncated, publicKeyTruncated, contextId, processName, seq)
            VALUES ('crypto', 'pkg.v9', 'cipher', 'encrypt', 'AES', 'main', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 'pkg.v9', 1)
            """.trimIndent(),
        )
        oldHelper.close()

        val migrated = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
            "mig-v9.db",
        ).addMigrations(ModevDatabase.MIGRATION_9_10, ModevDatabase.MIGRATION_10_11, ModevDatabase.MIGRATION_11_12, ModevDatabase.MIGRATION_12_13, ModevDatabase.MIGRATION_13_14).build()
        val migratedDao = migrated.cryptoRecordDao()

        // v9 存量数据完整保留
        val row = migratedDao.observeById(1).first()
        assertNotNull(row)
        assertEquals("pkg.v9", row!!.packageName)

        // v10 偏好列已就位且可写可读(prefs 行承载,prefKey/content 往返;path 列复用)
        val inserted = CryptoRecordEntity(
            packageName = "pkg.prefs",
            kind = "PUT",
            operation = "PUT_STRING",
            algorithm = "",
            recordType = "prefs",
            path = "/data/data/pkg.prefs/shared_prefs/config.xml",
            prefKey = "server_url",
            contentHex = "68656c6c6f",
            contentText = "hello",
            contentLength = 5,
            threadName = "main",
            timestamp = 7L,
            seq = 3L,
        )
        migratedDao.insert(inserted)
        val prefsRow = migratedDao.observeById(2).first()
        assertNotNull(prefsRow)
        assertEquals("prefs", prefsRow!!.recordType)
        assertEquals("server_url", prefsRow.prefKey)
        assertEquals("/data/data/pkg.prefs/shared_prefs/config.xml", prefsRow.path)
        assertEquals("68656c6c6f", prefsRow.contentHex)
        assertEquals("hello", prefsRow.contentText)

        migrated.close()
        dbFile.delete()
        Unit
    }

    /** 复刻 v9 表结构(v8 全列 + 行为监听列),供 v9→v10 迁移测试。 */
    private fun createV9Helper(dbFile: java.io.File): androidx.sqlite.db.SupportSQLiteOpenHelper {
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        return factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                RuntimeEnvironment.getApplication(),
            ).name(dbFile.path)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            createV8Schema(db)
                            val v9Columns = listOf(
                                "path TEXT", "destPath TEXT", "command TEXT", "fileSize INTEGER",
                                "contentHex TEXT", "contentB64 TEXT", "contentText TEXT",
                                "contentLength INTEGER", "contentTruncated INTEGER NOT NULL DEFAULT 0",
                            )
                            for (column in v9Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                        }
                    }
                ).build()
        )
    }

    /** v8 完整 schema 复刻(v1 DDL + 索引 + 各代列),供 v8/v9 helper 复用。 */
    private fun createV8Schema(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(V1_TABLE_DDL)
        db.execSQL(
            "CREATE INDEX index_crypto_records_packageName_timestamp " +
                "ON crypto_records (packageName, timestamp)",
        )
        db.execSQL(
            "CREATE INDEX index_crypto_records_packageName_kind " +
                "ON crypto_records (packageName, kind)",
        )
        val v2Columns = listOf(
            "saltHex TEXT", "saltText TEXT", "saltLength INTEGER",
            "saltTruncated INTEGER NOT NULL DEFAULT 0",
            "aadHex TEXT", "aadText TEXT", "aadLength INTEGER",
            "aadTruncated INTEGER NOT NULL DEFAULT 0",
            "publicKeyHex TEXT", "publicKeyText TEXT", "publicKeyLength INTEGER",
            "publicKeyTruncated INTEGER NOT NULL DEFAULT 0",
            "paramsJson TEXT", "verifyResult INTEGER", "processName TEXT",
        )
        for (column in v2Columns) {
            db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
        }
        db.execSQL("ALTER TABLE crypto_records ADD COLUMN seq INTEGER")
        db.execSQL(
            "CREATE UNIQUE INDEX index_crypto_records_packageName_processName_seq " +
                "ON crypto_records (packageName, processName, seq)",
        )
        db.execSQL(
            "ALTER TABLE crypto_records ADD COLUMN recordType TEXT NOT NULL DEFAULT 'crypto'",
        )
        db.execSQL(
            "CREATE INDEX index_crypto_records_timestamp " +
                "ON crypto_records (timestamp)",
        )
        val v6Columns = listOf(
            "sql TEXT", "dbPath TEXT", "bindArgsJson TEXT", "rowsJson TEXT",
            "rowCount INTEGER", "affectedCount INTEGER", "rowId INTEGER",
            "resultText TEXT",
        )
        for (column in v6Columns) {
            db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
        }
        val v7Columns = listOf(
            "keyB64 TEXT", "ivB64 TEXT", "inputB64 TEXT", "outputB64 TEXT",
            "saltB64 TEXT", "aadB64 TEXT", "publicKeyB64 TEXT",
        )
        for (column in v7Columns) {
            db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
        }
        db.execSQL(
            "CREATE INDEX index_crypto_records_recordType_kind " +
                "ON crypto_records (recordType, kind)",
        )
        db.execSQL(
            "CREATE INDEX index_crypto_records_recordType_packageName_timestamp " +
                "ON crypto_records (recordType, packageName, timestamp)",
        )
    }

    private fun createV8Helper(dbFile: java.io.File): androidx.sqlite.db.SupportSQLiteOpenHelper {
        // 复刻 v8 表结构(v7 全列 + 两复合索引;v9 行为列由迁移补齐)
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        return factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                RuntimeEnvironment.getApplication(),
            ).name(dbFile.path)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(V1_TABLE_DDL)
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_timestamp " +
                                    "ON crypto_records (packageName, timestamp)",
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_kind " +
                                    "ON crypto_records (packageName, kind)",
                            )
                            val v2Columns = listOf(
                                "saltHex TEXT", "saltText TEXT", "saltLength INTEGER",
                                "saltTruncated INTEGER NOT NULL DEFAULT 0",
                                "aadHex TEXT", "aadText TEXT", "aadLength INTEGER",
                                "aadTruncated INTEGER NOT NULL DEFAULT 0",
                                "publicKeyHex TEXT", "publicKeyText TEXT", "publicKeyLength INTEGER",
                                "publicKeyTruncated INTEGER NOT NULL DEFAULT 0",
                                "paramsJson TEXT", "verifyResult INTEGER", "processName TEXT",
                            )
                            for (column in v2Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                            db.execSQL("ALTER TABLE crypto_records ADD COLUMN seq INTEGER")
                            db.execSQL(
                                "CREATE UNIQUE INDEX index_crypto_records_packageName_processName_seq " +
                                    "ON crypto_records (packageName, processName, seq)",
                            )
                            db.execSQL(
                                "ALTER TABLE crypto_records ADD COLUMN recordType TEXT NOT NULL DEFAULT 'crypto'",
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_timestamp " +
                                    "ON crypto_records (timestamp)",
                            )
                            val v6Columns = listOf(
                                "sql TEXT", "dbPath TEXT", "bindArgsJson TEXT", "rowsJson TEXT",
                                "rowCount INTEGER", "affectedCount INTEGER", "rowId INTEGER",
                                "resultText TEXT",
                            )
                            for (column in v6Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                            val v7Columns = listOf(
                                "keyB64 TEXT", "ivB64 TEXT", "inputB64 TEXT", "outputB64 TEXT",
                                "saltB64 TEXT", "aadB64 TEXT", "publicKeyB64 TEXT",
                            )
                            for (column in v7Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_recordType_kind " +
                                    "ON crypto_records (recordType, kind)",
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_recordType_packageName_timestamp " +
                                    "ON crypto_records (recordType, packageName, timestamp)",
                            )
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                        }
                    }
                ).build()
        )
    }

    private fun createV7Helper(dbFile: java.io.File): androidx.sqlite.db.SupportSQLiteOpenHelper {
        // 严格复刻 v7 表结构(v6 全列 + 7 个三态 Base64 列),供 v7→v8 迁移测试
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        return factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                RuntimeEnvironment.getApplication(),
            ).name(dbFile.path)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(V1_TABLE_DDL)
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_timestamp " +
                                    "ON crypto_records (packageName, timestamp)",
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_kind " +
                                    "ON crypto_records (packageName, kind)",
                            )
                            val v2Columns = listOf(
                                "saltHex TEXT", "saltText TEXT", "saltLength INTEGER",
                                "saltTruncated INTEGER NOT NULL DEFAULT 0",
                                "aadHex TEXT", "aadText TEXT", "aadLength INTEGER",
                                "aadTruncated INTEGER NOT NULL DEFAULT 0",
                                "publicKeyHex TEXT", "publicKeyText TEXT", "publicKeyLength INTEGER",
                                "publicKeyTruncated INTEGER NOT NULL DEFAULT 0",
                                "paramsJson TEXT", "verifyResult INTEGER", "processName TEXT",
                            )
                            for (column in v2Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                            db.execSQL("ALTER TABLE crypto_records ADD COLUMN seq INTEGER")
                            db.execSQL(
                                "CREATE UNIQUE INDEX index_crypto_records_packageName_processName_seq " +
                                    "ON crypto_records (packageName, processName, seq)",
                            )
                            db.execSQL(
                                "ALTER TABLE crypto_records ADD COLUMN recordType TEXT NOT NULL DEFAULT 'crypto'",
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_timestamp " +
                                    "ON crypto_records (timestamp)",
                            )
                            val v6Columns = listOf(
                                "sql TEXT", "dbPath TEXT", "bindArgsJson TEXT", "rowsJson TEXT",
                                "rowCount INTEGER", "affectedCount INTEGER", "rowId INTEGER",
                                "resultText TEXT",
                            )
                            for (column in v6Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                            val v7Columns = listOf(
                                "keyB64 TEXT", "ivB64 TEXT", "inputB64 TEXT", "outputB64 TEXT",
                                "saltB64 TEXT", "aadB64 TEXT", "publicKeyB64 TEXT",
                            )
                            for (column in v7Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                        }
                    }
                ).build()
        )
    }

    private fun createV4Helper(dbFile: java.io.File): androidx.sqlite.db.SupportSQLiteOpenHelper {
        // 严格复刻 v4 表结构(v3 + recordType 列 + 全部实体索引,不含 timestamp 单列索引)
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        return factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                RuntimeEnvironment.getApplication()
            ).name(dbFile.path)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(V1_TABLE_DDL)
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_timestamp " +
                                    "ON crypto_records (packageName, timestamp)"
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_kind " +
                                    "ON crypto_records (packageName, kind)"
                            )
                            val v2Columns = listOf(
                                "saltHex TEXT", "saltText TEXT", "saltLength INTEGER",
                                "saltTruncated INTEGER NOT NULL DEFAULT 0",
                                "aadHex TEXT", "aadText TEXT", "aadLength INTEGER",
                                "aadTruncated INTEGER NOT NULL DEFAULT 0",
                                "publicKeyHex TEXT", "publicKeyText TEXT", "publicKeyLength INTEGER",
                                "publicKeyTruncated INTEGER NOT NULL DEFAULT 0",
                                "paramsJson TEXT", "verifyResult INTEGER", "processName TEXT",
                            )
                            for (column in v2Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                            db.execSQL(
                                "ALTER TABLE crypto_records ADD COLUMN seq INTEGER"
                            )
                            db.execSQL(
                                "CREATE UNIQUE INDEX index_crypto_records_packageName_processName_seq " +
                                    "ON crypto_records (packageName, processName, seq)"
                            )
                            db.execSQL(
                                "ALTER TABLE crypto_records ADD COLUMN recordType TEXT NOT NULL DEFAULT 'crypto'"
                            )
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                        }
                    }
                ).build()
        )
    }

    private fun createV6Helper(dbFile: java.io.File): androidx.sqlite.db.SupportSQLiteOpenHelper {
        // 严格复刻 v6 表结构(v5 全列 + 8 个数据库监听列),供 v6→v7 迁移测试
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        return factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                RuntimeEnvironment.getApplication()
            ).name(dbFile.path)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(V1_TABLE_DDL)
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_timestamp " +
                                    "ON crypto_records (packageName, timestamp)"
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_packageName_kind " +
                                    "ON crypto_records (packageName, kind)"
                            )
                            val v2Columns = listOf(
                                "saltHex TEXT", "saltText TEXT", "saltLength INTEGER",
                                "saltTruncated INTEGER NOT NULL DEFAULT 0",
                                "aadHex TEXT", "aadText TEXT", "aadLength INTEGER",
                                "aadTruncated INTEGER NOT NULL DEFAULT 0",
                                "publicKeyHex TEXT", "publicKeyText TEXT", "publicKeyLength INTEGER",
                                "publicKeyTruncated INTEGER NOT NULL DEFAULT 0",
                                "paramsJson TEXT", "verifyResult INTEGER", "processName TEXT",
                            )
                            for (column in v2Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                            db.execSQL(
                                "ALTER TABLE crypto_records ADD COLUMN seq INTEGER"
                            )
                            db.execSQL(
                                "CREATE UNIQUE INDEX index_crypto_records_packageName_processName_seq " +
                                    "ON crypto_records (packageName, processName, seq)"
                            )
                            db.execSQL(
                                "ALTER TABLE crypto_records ADD COLUMN recordType TEXT NOT NULL DEFAULT 'crypto'"
                            )
                            db.execSQL(
                                "CREATE INDEX index_crypto_records_timestamp " +
                                    "ON crypto_records (timestamp)"
                            )
                            val v6Columns = listOf(
                                "sql TEXT", "dbPath TEXT", "bindArgsJson TEXT", "rowsJson TEXT",
                                "rowCount INTEGER", "affectedCount INTEGER", "rowId INTEGER",
                                "resultText TEXT",
                            )
                            for (column in v6Columns) {
                                db.execSQL("ALTER TABLE crypto_records ADD COLUMN $column")
                            }
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                        }
                    }
                ).build()
        )
    }

    private companion object {
        /** v1 表结构(严格复刻,供迁移测试构造旧版本库)。 */
        const val V1_TABLE_DDL = """
            CREATE TABLE crypto_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                packageName TEXT NOT NULL,
                kind TEXT NOT NULL,
                operation TEXT NOT NULL,
                algorithm TEXT NOT NULL,
                provider TEXT,
                direction INTEGER,
                keyHex TEXT, keyText TEXT, keyLength INTEGER, keyTruncated INTEGER NOT NULL,
                ivHex TEXT, ivText TEXT, ivLength INTEGER, ivTruncated INTEGER NOT NULL,
                keyAlias TEXT, keyExportable INTEGER NOT NULL,
                inputHex TEXT, inputText TEXT, inputLength INTEGER, inputTruncated INTEGER NOT NULL,
                outputHex TEXT, outputText TEXT, outputLength INTEGER, outputTruncated INTEGER NOT NULL,
                contextId INTEGER NOT NULL,
                threadName TEXT NOT NULL,
                stack TEXT,
                timestamp INTEGER NOT NULL
            )
        """
    }
}
