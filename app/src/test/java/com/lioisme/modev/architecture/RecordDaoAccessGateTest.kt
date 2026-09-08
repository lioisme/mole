package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一上层筛选器门禁(V41 T7,R7 契约强制化,源码扫描形态镜像 [DialogConventionTest]):
 *
 * 记录数据 = 单库单表大库(`crypto_records` 承载全部 RecordType),查询/删除唯一
 * 上游 = `RecordFilter` → `LogRepository`(RecordService 门面,WHERE 口径单点收敛)。
 * 本门禁把「UI/VM/MCP/REST 零 DAO 直调」从口头契约升级为强制——白名单外任何
 * `cryptoRecordDao()` / `keywordLibraryDao()` 调用点即红(防旁路 RecordFilter)。
 *
 * 白名单(装配层与仓库自身):
 *  - `ModevApp.kt`(AppContainer 装配:DAO 注入 LogRepository / KeywordLibraryRepository);
 *  - `LogRepository.kt`(记录仓库唯一持有者);
 *  - `KeywordLibraryRepository.kt`(功能域关键词库自有仓库);
 *  - `ModevDatabase.kt`(Room DAO 抽象声明点——数据层本体,非消费旁路)。
 * tests/androidTest 源集不在扫描面(测试基建允许直用内存库种子)。
 */
class RecordDaoAccessGateTest {

    private val mainRoot = "app/src/main/java"

    private fun locateProjectRoot(): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        val candidates = listOf(
            File(userDir),
            File(userDir, ".."),
        )
        return candidates.firstOrNull {
            File(it, mainRoot).isDirectory
        } ?: error("project root not located from $userDir")
    }

    private fun sources(): List<File> =
        File(locateProjectRoot(), mainRoot).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    @Test
    fun daoAccessOnlyViaRepositoryLayer() {
        val allowed = setOf("ModevApp.kt", "LogRepository.kt", "KeywordLibraryRepository.kt", "ModevDatabase.kt")
        val violations = sources()
            .filter { it.name !in allowed }
            .flatMap { file ->
                Regex("""cryptoRecordDao\s*\(|keywordLibraryDao\s*\(""")
                    .findAll(file.readText())
                    .map { "${file.path}:${it.value}" }
            }
            .toList()
        assertTrue(
            "DAO access outside repository layer is forbidden (route via LogRepository + " +
                "RecordFilter, AGENTS #9/#14; allowed only in $allowed):\n$violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun whitelistStillOwnsDaoWiring() {
        // 门禁自验:白名单链路必须仍然成立,防止装配被搬走/门禁空转后假绿。
        // 消费形态:ModevApp 直调 accessor 装配;两仓库经构造器注入消费 DAO 类型。
        val root = locateProjectRoot()
        val modevApp = File(root, "$mainRoot/com/lioisme/modev/ModevApp.kt").readText()
        val logRepo = File(root, "$mainRoot/com/lioisme/modev/data/repo/LogRepository.kt").readText()
        val keywordRepo = File(root, "$mainRoot/com/lioisme/modev/data/repo/KeywordLibraryRepository.kt").readText()
        assertTrue("ModevApp 装配层必须持有 cryptoRecordDao() 接线", modevApp.contains("cryptoRecordDao("))
        assertTrue("LogRepository 必须经构造器消费 CryptoRecordDao", logRepo.contains("CryptoRecordDao"))
        assertTrue("KeywordLibraryRepository 必须经构造器消费 KeywordLibraryDao", keywordRepo.contains("KeywordLibraryDao"))
    }
}
