package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.server.ConfigServer
import java.io.File
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 存储占用出口契约(V23 T1.5):主库 + `-wal` + `-shm` 三文件字节和;
 * 未装配 databaseFile 提供者时返回 0(UI 禁止绕过仓库直触 DB 文件)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageUsageTest {

    private lateinit var db: ModevDatabase
    private lateinit var server: ConfigServer

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        server = ConfigServer(ServerSocket(0).use { it.localPort })
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun `returns zero when database file provider is missing`() = runBlocking {
        val repo = LogRepository(db.cryptoRecordDao(), server)
        assertEquals(0L, repo.storageUsage())
    }

    @Test
    fun `sums main wal and shm files`() = runBlocking {
        val dir = RuntimeEnvironment.getApplication().filesDir
        val main = File(dir, "storage-usage-test.db").apply { writeBytes(ByteArray(100)) }
        File(dir, "storage-usage-test.db-wal").apply { writeBytes(ByteArray(50)) }
        File(dir, "storage-usage-test.db-shm").apply { writeBytes(ByteArray(10)) }
        try {
            val repo = LogRepository(
                db.cryptoRecordDao(),
                server,
                databaseFile = { main },
            )
            assertEquals(160L, repo.storageUsage())
        } finally {
            listOf("", "-wal", "-shm").forEach { suffix -> File(dir, "storage-usage-test.db$suffix").delete() }
        }
    }

    @Test
    fun `missing sidecar files are tolerated`() = runBlocking {
        val dir = RuntimeEnvironment.getApplication().filesDir
        val main = File(dir, "storage-usage-solo.db").apply { writeBytes(ByteArray(7)) }
        try {
            val repo = LogRepository(
                db.cryptoRecordDao(),
                server,
                databaseFile = { main },
            )
            assertEquals(7L, repo.storageUsage())
        } finally {
            main.delete()
        }
    }
}
