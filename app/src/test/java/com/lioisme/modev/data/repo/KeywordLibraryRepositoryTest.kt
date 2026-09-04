package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.data.db.KeywordLibraryDao
import com.lioisme.modev.data.db.ModevDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** V37 T2.1 关键词库仓库:CRUD、去重、启停与每应用规则解析(Robolectric 内存库)。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeywordLibraryRepositoryTest {

    private lateinit var db: ModevDatabase
    private lateinit var dao: KeywordLibraryDao
    private lateinit var repository: KeywordLibraryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        dao = db.keywordLibraryDao()
        repository = KeywordLibraryRepository(dao, CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun awaitUntil(desc: String, predicate: (List<com.lioisme.modev.core.model.KeywordEntry>) -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate(repository.entries.value)) return
            Thread.sleep(20)
        }
        org.junit.Assert.fail("snapshot not settled: $desc, actual=${repository.entries.value}")
    }

    @Test
    fun `add dedupes by text and rejects blank`() = runBlocking {
        assertTrue(repository.add("root detect"))
        assertFalse("重复文本必须拒绝", repository.add("root detect"))
        assertFalse("trim 后同文本视为重复", repository.add("  root detect  "))
        assertFalse(repository.add("   "))
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `resolve rules follows global enablement when keywordIds null`() = runBlocking {
        repository.add("a")
        repository.add("b")
        awaitUntil("two entries visible") { it.size == 2 }
        val idB = repository.entries.value.first { it.text == "b" }.id
        repository.setEnabled(idB, false)
        awaitUntil("b disabled") { list -> list.firstOrNull { it.id == idB }?.enabled == false }
        val rules = repository.resolveRules(null)
        assertEquals(listOf("a"), rules.map { it.text })
    }

    @Test
    fun `resolve rules intersects global enabled with per-app subset`() = runBlocking {
        repository.add("a")
        repository.add("b")
        repository.add("c")
        awaitUntil("three entries visible") { it.size == 3 }
        val ids = repository.entries.value.associate { it.text to it.id }
        repository.setEnabled(ids.getValue("c"), false)
        awaitUntil("c disabled") { list -> list.firstOrNull { it.id == ids.getValue("c") }?.enabled == false }
        val rules = repository.resolveRules(listOf(ids.getValue("a"), ids.getValue("b"), ids.getValue("c")))
        assertEquals(setOf("a", "b"), rules.map { it.text }.toSet())
    }

    @Test
    fun `resolve rules drops deleted ids and empty selection yields empty`() = runBlocking {
        repository.add("a")
        awaitUntil("one entry visible") { it.size == 1 }
        val idA = repository.entries.value.first().id
        assertEquals(1, repository.resolveRules(listOf(idA)).size)
        repository.remove(idA)
        awaitUntil("entry deleted") { it.isEmpty() }
        assertTrue(repository.resolveRules(listOf(idA)).isEmpty())
        assertTrue(repository.resolveRules(emptyList()).isEmpty())
    }

    @Test
    fun `update changes text and mode`() = runBlocking {
        repository.add("old")
        awaitUntil("one entry visible") { it.size == 1 }
        val id = repository.entries.value.first().id
        assertTrue(repository.update(id, "new", KeywordMatchMode.REGEX))
        awaitUntil("text updated") { list -> list.firstOrNull { it.id == id }?.text == "new" }
        val entry = repository.entries.value.first()
        assertEquals("new", entry.text)
        assertEquals(KeywordMatchMode.REGEX, entry.matchMode)
    }
}
