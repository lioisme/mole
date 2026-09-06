package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.record.RecordDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScratchInsertTest {
    private lateinit var db: ModevDatabase
    private lateinit var repo: LogRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        repo = LogRepository(db.cryptoRecordDao(), ConfigServer())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun scratch() = runBlocking {
        val hc = RecordFilter().hasConstraint
        if (hc) throw RuntimeException("HAS_CONSTRAINT_TRUE for empty RecordFilter")
        val id = repo.create(
            RecordDraft(
                packageName = "pkg.a",
                kind = "CIPHER",
                operation = "ENCRYPT",
                algorithm = "AES",
                threadName = "main",
                processName = "proc-x",
                timestamp = 100,
                recordType = RecordTypes.CRYPTO,
            ),
        )
        val deleted = repo.deleteByFilter(RecordFilter())
        if (deleted != 0L) throw RuntimeException("DELETE_BY_EMPTY_RETURNED=$deleted")
        val c = repo.count(RecordFilter()).first()
        if (c != 1L) throw RuntimeException("COUNT_AFTER=$c")
        assertEquals(0L, deleted)
    }
}
