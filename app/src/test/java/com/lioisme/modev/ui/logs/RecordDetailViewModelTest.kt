package com.lioisme.modev.ui.logs

import androidx.room.Room
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 定向测试(RecordDetailViewModel,2026-08-15 UI 重构 Phase 5 三态契约):
 * 加载→有记录(Loaded)/记录消失(记录被清理→Failed 数据条件)/retry 恢复订阅/
 * 同包上下条移动。内存 Room + 真实 LogRepository,数据流与生产一致;
 * viewModelScope 跑在 Robolectric 主 Looper,用 idleMainLooper 泵协程 + 真实轮询等待。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordDetailViewModelTest {

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

    private suspend fun insert(
        packageName: String,
        kind: CryptoKind,
        timestamp: Long,
    ): Long = db.cryptoRecordDao().insertReturningId(
        CryptoRecordEntity(
            packageName = packageName,
            kind = kind.name,
            operation = CryptoOperation.INIT.name,
            algorithm = "AES",
            threadName = "main",
            timestamp = timestamp,
        ),
    )

    /** 泵主 Looper 让 viewModelScope 协程推进,Room 在真实线程,以真实超时轮询状态。 */
    private fun awaitRecord(
        vm: RecordDetailViewModel,
        predicate: (CryptoRecord?) -> Boolean,
    ): CryptoRecord? {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (predicate(vm.record.value)) return vm.record.value
            Thread.sleep(20)
        }
        return null
    }

    @Test
    fun `initial state is loading until first real query result`() {
        val vm = RecordDetailViewModel(recordId = 42, logs = repo)
        // 首个真实查询在后台线程,此刻必然仍是加载中(回归 stateIn 初始值假阳性的修复)
        assertTrue(!vm.loaded.value)
        assertEquals(null, vm.record.value)
    }

    @Test
    fun `record emits loaded after insert`() = runBlocking {
        val id = insert("pkg.a", CryptoKind.CIPHER, 100)
        val vm = RecordDetailViewModel(recordId = id, logs = repo)
        val record = awaitRecord(vm) { it != null }
        assertNotNull(record)
        assertTrue(vm.loaded.value)
        assertEquals("pkg.a", record?.packageName)
        assertEquals(CryptoKind.CIPHER, record?.kind)
    }

    @Test
    fun `record becomes null after deletion (failed data condition)`() = runBlocking {
        val id = insert("pkg.a", CryptoKind.CIPHER, 100)
        val vm = RecordDetailViewModel(recordId = id, logs = repo)
        awaitRecord(vm) { it != null }

        db.cryptoRecordDao().clearAll()
        // 记录被清理后重发 null → 详情页进入失败态
        awaitRecord(vm) { it == null }
        assertNull(vm.record.value)
        assertTrue(vm.loaded.value)
    }

    @Test
    fun `retry re-subscribes and stays failed while record stays gone`() = runBlocking {
        val id = insert("pkg.a", CryptoKind.CIPHER, 100)
        val vm = RecordDetailViewModel(recordId = id, logs = repo)
        awaitRecord(vm) { it != null }

        db.cryptoRecordDao().clearAll()
        awaitRecord(vm) { it == null }

        // retry 重新订阅同一 id(自增 id 不可复用,生产语义:记录已删则保持失败态,不回到加载中)
        vm.retry()
        val after = awaitRecord(vm) { it == null && vm.loaded.value }
        assertNull(after)
        assertTrue(vm.loaded.value)
    }

    @Test
    fun `prev next move within same package`() = runBlocking {
        val olderId = insert("pkg.a", CryptoKind.CIPHER, 100)
        val newerId = insert("pkg.a", CryptoKind.CIPHER, 200)
        // 无关包记录不影响邻居
        insert("pkg.b", CryptoKind.MESSAGE_DIGEST, 300)

        val vm = RecordDetailViewModel(recordId = olderId, logs = repo)
        // 邻居标志在 collect 内依次查询,连同标志一起等待避免竞态
        val first = awaitRecord(vm) { it != null && !vm.canOlder.value && vm.canNewer.value }
        assertEquals(olderId, first?.id)

        vm.goNewer()
        val second = awaitRecord(vm) { it != null && vm.canOlder.value && !vm.canNewer.value }
        assertEquals(newerId, second?.id)

        vm.goOlder()
        val third = awaitRecord(vm) { it != null && !vm.canOlder.value && vm.canNewer.value }
        assertEquals(olderId, third?.id)
    }

    @Test
    fun `large hex record loads in full and neighbor projection stays light`() = runBlocking {
        // 捕获上限字段:2MB hex 字符(1MB 字节)× 3 字段——详情页全量加载不 OOM
        val bigHex = "ab".repeat(1_048_576)
        val id = db.cryptoRecordDao().insertReturningId(
            CryptoRecordEntity(
                packageName = "pkg.big",
                kind = CryptoKind.CIPHER.name,
                operation = CryptoOperation.INIT.name,
                algorithm = "AES/CBC/PKCS5Padding",
                keyHex = bigHex,
                keyLength = bigHex.length / 2,
                inputHex = bigHex,
                inputLength = bigHex.length / 2,
                outputHex = bigHex,
                outputLength = bigHex.length / 2,
                threadName = "main",
            ),
        )

        val vm = RecordDetailViewModel(recordId = id, logs = repo)
        val record = awaitRecord(vm) { it != null }
        assertNotNull(record)
        // 全量数据保留(取证语义不变),截断标记正确
        assertEquals(bigHex, record?.key?.hex)
        assertFalse(record!!.key!!.truncated)
        assertEquals(bigHex.length / 2, record.key!!.totalLength)
        // 邻接查询仅投影 id:无邻居 → 标志为 false,不加载任何大字段
        assertTrue(!vm.canOlder.value)
        assertTrue(!vm.canNewer.value)
    }
}
