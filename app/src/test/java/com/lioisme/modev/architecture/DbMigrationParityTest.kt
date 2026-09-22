package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V43 T7(V5.13):Room 迁移清单对账门禁。
 *
 * 背景:`@Database(version)`、`MIGRATION_x_y` 定义、`addMigrations(...)` 清单三处
 * 手工同步,漏登记任一迁移 = 老用户升级直接崩(Cannot run migration);无门禁前
 * 只能靠人眼。本测试纯源码解析(JVM):
 *  1. version 与「1→N 连续迁移链」一致(缺一环即红);
 *  2. 每条已定义迁移都出现在 addMigrations 清单(反向亦然,无孤儿)。
 */
class DbMigrationParityTest {

    private val dbFile: File
        get() {
            // JVM 单测 user.dir = 模块根(与 [PackageBoundaryTest] 同源定位法)
            val root = File(System.getProperty("user.dir"))
            val candidates = listOf(
                File(root, "src/main/java/com/lioisme/modev/data/db/ModevDatabase.kt"),
                File(root.parentFile ?: root, "app/src/main/java/com/lioisme/modev/data/db/ModevDatabase.kt"),
            )
            return candidates.firstOrNull { it.isFile }
                ?: error("ModevDatabase.kt not found from ${root.absolutePath}")
        }

    @Test
    fun version_migrations_and_registered_list_are_consistent() {
        val src = dbFile.readText()
        val version = Regex("""version\s*=\s*(\d+)""").find(src)?.groupValues?.get(1)?.toInt()
            ?: error("no @Database version")
        val defined = Regex("""MIGRATION_(\d+)_(\d+)\s*=\s*object\s*:\s*Migration\((\d+),\s*(\d+)\)""")
            .findAll(src)
            .map { m ->
                val (fromName, toName, fromCtor, toCtor) = m.destructured
                assertEquals("迁移名与构造参数错位: $fromName-$toName", fromName, fromCtor)
                assertEquals("迁移名与构造参数错位(目标)", toName, toCtor)
                fromName.toInt() to toName.toInt()
            }
            .toSet()
        val expectedChain = (1 until version).map { it to it + 1 }.toSet()
        assertEquals(
            "迁移链必须覆盖 1→$version 每一级(缺一环升级即崩;多环须同步 version)",
            expectedChain,
            defined,
        )
        val registered = Regex("""addMigrations\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(src)
            ?.groupValues?.get(1)
            ?.split(',')
            ?.mapNotNull { Regex("""MIGRATION_(\d+)_(\d+)""").find(it)?.value }
            ?.toSet()
            ?: error("addMigrations(...) list not found")
        val definedNames = defined.map { (f, t) -> "MIGRATION_${f}_$t" }.toSet()
        assertEquals("已定义迁移必须全部登记 addMigrations(反向不得有孤儿)", definedNames, registered)
    }
}
