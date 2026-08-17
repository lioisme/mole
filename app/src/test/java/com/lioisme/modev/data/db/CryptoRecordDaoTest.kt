package com.lioisme.modev.data.db

import androidx.paging.PagingSource
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

    private suspend fun page(
        packageName: String? = null,
        kind: String? = null,
        query: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): List<CryptoRecordEntity> {
        val source: PagingSource<Int, CryptoRecordEntity> =
            dao.observePaged(packageName, kind, query, limit, offset)
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = limit, placeholdersEnabled = false),
        )
        return (result as PagingSource.LoadResult.Page).data
    }

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

        assertEquals(2, page(packageName = "pkg.a").size)
        assertEquals(1, page(packageName = "pkg.b").size)
        assertEquals(1, page(packageName = "pkg.a", kind = "digest").size)
        assertEquals(0, page(packageName = "pkg.a", kind = "mac").size)
    }

    @Test
    fun `paged query keyword search covers algorithm operation and key text`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "encrypt", "AES/CBC/PKCS5Padding", 100))
        dao.insert(entity("pkg.a", "cipher", "decrypt", "AES/ECB", 200))
        dao.insert(entity("pkg.a", "digest", "digest", "SHA-256", 300))

        assertEquals(1, page(query = "%CBC%").size)
        assertEquals(1, page(query = "%decrypt%").size)
        assertEquals(0, page(query = "%HmacSHA1%").size)
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

        val counts = dao.observeRecordCounts().first()
        assertEquals(mapOf("pkg.a" to 2L, "pkg.b" to 1L), counts.associate { it.packageName to it.count })
    }

    @Test
    fun `neighbor id queries respect same-package and tie-break by id`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 100))
        val mid = dao.insertReturningId(entity("pkg.a", "cipher", "op2", "AES", 100))
        val newerId = dao.insertReturningId(entity("pkg.a", "cipher", "op3", "AES", 300))
        dao.insert(entity("pkg.b", "cipher", "opx", "AES", 100))

        val older = dao.neighborIdOlderThan("pkg.a", 300, mid)
        val newer = dao.neighborIdNewerThan("pkg.a", 100, mid)
        // 仅投影 id:op3 的相邻上一条 = 同包最新更旧记录 op2
        assertEquals(mid, older)
        assertEquals(newerId, newer)
        // 与 op2 同时间戳时按 id 决胜:仅 op1 严格更旧
        assertEquals(1L, dao.neighborIdOlderThan("pkg.a", 100, mid))
        // 邻居查询不跨包
        assertNull(dao.neighborIdOlderThan("pkg.b", 100, mid))
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
        val source: PagingSource<Int, CryptoRecordRow> =
            dao.observePagedRows(null, null, null, previewChars = 48, limit = 20, offset = 0)
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )
        val row = (result as PagingSource.LoadResult.Page).data.single()
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

        suspend fun pageRows(pkg: String?, kind: String?, query: String?): List<CryptoRecordRow> {
            val source: PagingSource<Int, CryptoRecordRow> =
                dao.observePagedRows(pkg, kind, query, previewChars = 48, limit = 20, offset = 0)
            val result = source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
            )
            return (result as PagingSource.LoadResult.Page).data
        }
        assertEquals(2, pageRows("pkg.a", null, null).size)
        assertEquals(1, pageRows("pkg.a", "digest", null).size)
        assertEquals(1, pageRows(null, null, "%CBC%").size)
    }

    @Test
    fun `observeById returns single record`() = runBlocking {
        val id = dao.insertReturningId(entity("pkg.a", "cipher", "op1", "AES", 100))
        val found = dao.observeById(id).first()
        assertNotNull(found)
        assertEquals("pkg.a", found?.packageName)
        assertNull(dao.observeById(id + 999).first())
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
        // v3:重放幂等唯一索引
        assertTrue(names.contains("index_crypto_records_packageName_processName_seq"))
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
        assertEquals(999L, page(packageName = "pkg.a").single().timestamp)
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
        assertEquals(10, dao.counts().single().count)
    }

    @Test
    fun `counts snapshot groups by package`() = runBlocking {
        dao.insert(entity("pkg.a", "cipher", "op1", "AES", 1))
        dao.insert(entity("pkg.a", "digest", "op2", "SHA-256", 2))
        dao.insert(entity("pkg.b", "cipher", "op3", "AES", 3))
        val counts = dao.counts().associate { it.packageName to it.count }
        assertEquals(mapOf("pkg.a" to 2L, "pkg.b" to 1L), counts)
    }

    @Test
    fun `global trim keeps newest and returns deleted count`() = runBlocking {
        for (i in 1..6) dao.insert(entity("pkg.a", "cipher", "op$i", "AES", i.toLong()))
        val deleted = dao.trim(keep = 2)
        assertEquals(4, deleted)
        assertEquals(2, dao.count("pkg.a"))
        assertEquals(listOf(6L, 5L), page(packageName = "pkg.a").map { it.timestamp })
    }

    @Test
    fun `per-package trim keeps newest of that package only`() = runBlocking {
        for (i in 1..4) dao.insert(entity("pkg.a", "cipher", "op$i", "AES", i.toLong()))
        dao.insert(entity("pkg.b", "cipher", "op1", "AES", 1L))
        val deleted = dao.trimPackage("pkg.a", keep = 2)
        assertEquals(2, deleted)
        assertEquals(2, dao.count("pkg.a"))
        assertEquals(1, dao.count("pkg.b"))
        assertEquals(listOf(4L, 3L), page(packageName = "pkg.a").map { it.timestamp })
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
        assertEquals(2L, migratedDao.counts().sumOf { it.count })
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
            assertEquals(2L, migratedDao.counts().sumOf { it.count })

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