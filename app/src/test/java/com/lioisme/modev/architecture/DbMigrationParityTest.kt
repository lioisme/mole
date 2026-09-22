package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V43 T7(V5.13)·V47 T2 扩展:Room 迁移清单对账门禁。
 *
 * 背景:`@Database(version)`、`MIGRATION_x_y` 定义、迁移清单三处手工同步,
 * 漏登记任一迁移 = 老用户升级直接崩(Cannot run migration);无门禁前只能靠人眼。
 *
 * V47 T2 追加第四断言:迁移清单已收成 [ModevDatabase.ALL_MIGRATIONS] 单一事实源,
 * 而**测试侧曾有七处手写清单在旧门禁视野之外**(每次升版本都要挨个补,漏一处即假失败),
 * 故新增「任何 `addMigrations(` 调用点不得就地枚举迁移」的源码围栏。
 *
 * 纯源码解析(JVM):
 *  1. version 与「1→N 连续迁移链」一致(缺一环即红);
 *  2. 每条已定义迁移都在 ALL_MIGRATIONS 清单内(反向亦然,无孤儿);
 *  3. builder 用展开形式引用该清单;
 *  4. 全源集内无第二处就地枚举。
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

    private fun locateProjectRoot(): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        return listOf(File(userDir), File(userDir, "..")).firstOrNull {
            File(it, "app/src/main/java").isDirectory || File(it, "src/main/java").isDirectory
        } ?: error("project root not located from $userDir")
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
        val registered = Regex("""ALL_MIGRATIONS\s*:\s*Array<Migration>\s*=\s*arrayOf\(([^)]*)\)""")
            .find(src)
            ?.groupValues?.get(1)
            ?.split(',')
            ?.mapNotNull { Regex("""MIGRATION_\d+_(\d+)""").find(it)?.value }
            ?.toSet()
            ?: error("ALL_MIGRATIONS arrayOf(...) list not found")
        val definedNames = defined.map { (f, t) -> "MIGRATION_${f}_$t" }.toSet()
        assertEquals("已定义迁移必须全部登记 ALL_MIGRATIONS(反向不得有孤儿)", definedNames, registered)
    }

    @Test
    fun builder_consumes_the_single_migration_list() {
        val src = dbFile.readText()
        assertTrue(
            "builder() 必须展开引用 ALL_MIGRATIONS,不得另列清单",
            Regex("""addMigrations\(\s*\*\s*ALL_MIGRATIONS\s*\)""").containsMatchIn(src),
        )
    }

    @Test
    fun migrationListHasSingleSource() {
        val root = locateProjectRoot()
        val offenders = mutableListOf<String>()
        for (sourceRoot in listOf("app/src/main/java", "src/main/java", "app/src/test/java", "src/test/java")) {
            val dir = File(root, sourceRoot)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    Regex("""addMigrations\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { match ->
                        if (match.value.contains("MIGRATION_")) {
                            offenders += "${file.path} -> ${match.value.replace("\n", " ").take(80)}"
                        }
                    }
                }
        }
        assertTrue(
            "迁移清单唯一事实源 = ModevDatabase.ALL_MIGRATIONS;以下调用点就地枚举了迁移" +
                "(升版本时必然漏改并产生假失败):\n$offenders",
            offenders.isEmpty(),
        )
    }
}
