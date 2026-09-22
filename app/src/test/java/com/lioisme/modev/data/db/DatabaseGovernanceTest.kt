package com.lioisme.modev.data.db

import androidx.room.Room
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.record.RecordFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 库文件治理门禁(V45 T1.7 / ST-8·ST-9,Robolectric 真文件库):
 * PRAGMA 定档、大批量删除后的真实收缩、损坏库首开自愈。
 *
 * 为什么必须真文件:内存库永远测不到「文件只增不减」与「文件头损坏」这两件事 ——
 * 而它们正是本项要治的(旧形态无任何治理语句:clearAll 后 `storage_usage` 恒高、
 * 损坏只进 writeFailures 计数无人知)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseGovernanceTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun fileDb(name: String): Pair<ModevDatabase, File> {
        val database = Room.databaseBuilder(ctx, ModevDatabase::class.java, name)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(ModevDatabase.GOVERNANCE)
            .fallbackToDestructiveMigration(false)
            .build()
        return database to ctx.getDatabasePath(name)
    }

    private fun pragma(db: ModevDatabase, sql: String): String? =
        db.openHelper.readableDatabase.query(sql).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun seed(db: ModevDatabase, rows: Int) = runBlocking {
        db.cryptoRecordDao().insertAll(
            (1..rows).map { i ->
                CryptoRecordEntity(
                    recordType = "crypto",
                    kind = CryptoKind.CIPHER.name,
                    operation = CryptoOperation.ENCRYPT.name,
                    algorithm = "AES",
                    // 撑大页用量(收缩前后的差值必须显著,否则断言会被页对齐噪声吞掉)
                    keyHex = "ab".repeat(512),
                    inputHex = "cd".repeat(512),
                    threadName = "main",
                    packageName = "pkg.gov",
                    timestamp = 1_000L + i,
                    seq = i.toLong(),
                    processName = "p1",
                )
            },
        )
    }

    @Test
    fun governance_pragmas_are_applied_on_open() {
        val (db, _) = fileDb("gov_pragma.db")
        try {
            assertEquals("busy_timeout 必须定档(并发写不再裸抛 SQLITE_BUSY)", "5000", pragma(db, "PRAGMA busy_timeout"))
            assertEquals("wal_autocheckpoint 收敛到 200 页", "200", pragma(db, "PRAGMA wal_autocheckpoint"))
            assertTrue("新库体检必须过", db.quickCheckOk())
            assertTrue("空库必须在可回收判据内", db.reclaimIfCheap())
        } finally {
            db.close()
        }
    }

    /**
     * 大批量清理后**文件页数**真实回落(T1.7 / ST-8 机制级证明)。
     *
     * 断言打在 `page_count` 而不是字节数:字节数里 wal 是大头,单靠 WAL 检查点就能过,
     * 那样测不到「存活页回收」这件真正要治的事。
     */
    @Test
    fun clear_all_reclaims_file_pages() = runBlocking {
        val (db, file) = fileDb("gov_shrink.db")
        val repo = LogRepository(
            dao = db.cryptoRecordDao(),
            server = ConfigServer(),
            databaseFile = { file },
            maintenance = { db.shrinkStorage() },
        )
        try {
            seed(db, 1_500)
            val before = repo.storageUsage()
            val pagesBefore = db.pageCount()
            assertTrue("种子库必须已有可观占用: $before / $pagesBefore 页", before > 200_000 && pagesBefore > 400)
            repo.clearAll()
            val after = repo.storageUsage()
            val pagesAfter = db.pageCount()
            assertEquals("清理后记录数必须为 0", 0L, repo.count(RecordFilter()).first())
            assertTrue(
                "clearAll 后必须回收存活页(未回收 = 存储占用恒高):pages $pagesBefore -> $pagesAfter, " +
                    "bytes $before -> $after",
                pagesAfter < pagesBefore / 4,
            )
            assertTrue("字节占用同样必须显著回落: before=$before after=$after", after < before / 4)
        } finally {
            db.close()
        }
    }

    /**
     * 删库这一不可逆动作的唯一裁决点(T1.7):判定表逐臂钉死。
     * 「探针跑不通」必须是 Unknown —— 设备端 ROM 的 query 通道若不接受 PRAGMA,
     * 把它当损坏就等于每次冷启清空用户记录(V45 设备轮实测的假过风险)。
     */
    @Test
    fun integrity_verdict_table_never_suspects_on_probe_failure() {
        assertEquals(ModevDatabase.IntegrityVerdict.Healthy, ModevDatabase.verdictOf(Result.success("ok")))
        assertEquals(
            ModevDatabase.IntegrityVerdict.Suspect,
            ModevDatabase.verdictOf(Result.success("*** in database main *** btree page 2 has invalid cell")),
        )
        assertEquals(ModevDatabase.IntegrityVerdict.Unknown, ModevDatabase.verdictOf(Result.success(null)))
        assertEquals(
            ModevDatabase.IntegrityVerdict.Unknown,
            ModevDatabase.verdictOf(Result.failure(IllegalStateException("pragma rejected"))),
        )
    }

    /** 正常库必须判 Healthy(既不删数据也不谎报损坏)。 */
    @Test
    fun integrity_probe_reports_healthy_for_good_database() {
        val db = Room.inMemoryDatabaseBuilder(ctx, ModevDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            assertEquals(ModevDatabase.IntegrityVerdict.Healthy, db.quickCheck())
        } finally {
            db.close()
        }
    }

    /**
     * 排期自愈的真实路径(T1.7):写路径报损坏 → 置排期 → 下次打开**删文件重建** →
     * 空库可用且标记复位。为什么不用「手工毁字节」:Robolectric 的 SQLite 对
     * 「合法头 + 毁数据页」仍报 ok(实测),伪造不出 Suspect 输入 ⇒ 判定表已由上一例
     * 逐臂锁住,这里只验排期之后的**动作**是否真的发生。
     */
    @Test
    fun scheduled_recovery_rebuilds_on_next_open() {
        ModevDatabase.resetInstanceForTest()
        val seeded = ModevDatabase.get(ctx)
        runBlocking {
            seed(seeded, 30)
            assertEquals("前置:种子必须已入库", 30L, seeded.cryptoRecordDao().count("pkg.gov"))
        }
        seeded.close()
        ModevDatabase.resetInstanceForTest()

        // 模拟运行中检出损坏(与 noteSqliteError 的线上路径同一条)
        assertTrue(
            ModevDatabase.noteSqliteError(
                RuntimeException("android.database.sqlite.SQLiteException: database disk image is malformed"),
            ),
        )
        assertTrue(ModevDatabase.isRecoveryPending())

        val recovered = ModevDatabase.get(ctx)
        try {
            assertEquals(ModevDatabase.IntegrityVerdict.Healthy, recovered.quickCheck())
            assertEquals(
                "自愈语义 = 重建空库(旧数据不残存)",
                0L,
                runBlocking { recovered.cryptoRecordDao().count("pkg.gov") },
            )
            assertTrue("排期标记必须在重建后复位", !ModevDatabase.isRecoveryPending())
        } finally {
            recovered.close()
            ModevDatabase.resetInstanceForTest()
        }
    }

    /** 损坏特征分类(观测面判定唯一入口;Room 2.7.2 无 DatabaseErrorListener,只能按异常文本判)。 */
    @Test
    fun corruption_classifier_recognises_sqlite_corruption_wordings() {
        ModevDatabase.clearRecoveryPendingForTest()
        listOf(
            "android.database.sqlite.SQLiteException: database disk image is malformed (code 11)",
            "SQLiteCompletionException: file is not a database",
            "SQLITE_CORRUPT: something",
        ).forEach { message ->
            assertTrue("必须判为损坏: $message", ModevDatabase.noteSqliteError(RuntimeException(message)))
        }
        assertTrue("命中损坏后必须排期重建", ModevDatabase.isRecoveryPending())
        // 非损坏类错误不得误判(约束冲突/繁忙重试路径必须保持原语义)
        listOf(
            "android.database.sqlite.SQLiteConstraintException: UNIQUE constraint failed",
            "SQLITE_BUSY database is locked",
            null,
        ).forEach { message ->
            assertTrue(
                "不得判为损坏: $message",
                !ModevDatabase.isCorruptionMessage(message),
            )
        }
        ModevDatabase.clearRecoveryPendingForTest()
    }
}
