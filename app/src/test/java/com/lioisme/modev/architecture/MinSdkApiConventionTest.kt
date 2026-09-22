package com.lioisme.modev.architecture

import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定向测试(V45 P0 / PLAN T0.7):本模块 `minSdk = 26`,源码禁止引用**高于 26 才存在**的
 * 平台类型/静态方法。
 *
 * 引爆实录:V44 T2.5 为消除热路径重复反射,在 `AppInfoHooker` / `RootPackageManagerHooker`
 * / `SqlDatabaseHooker` 用了 `java.lang.ClassValue` —— 该类 **API 34** 才有,
 * Android 8–13 宿主上 Hooker 构造期即 `NoClassDefFoundError`(注入即崩,与功能开关无关),
 * 而 `lintModernRelease` 当时未跑,9 条 `NewApi` error 静默存在到本轮才暴露。
 *
 * 本门禁把该教训固化成构建期红线:lint 仍是终裁(它按 manifest 精确判级),
 * 本测试提供秒级前置拦截 —— 名单为「Android 上高 API 才存在、且 Kotlin 侧易误用」的形态。
 */
class MinSdkApiConventionTest {

    /** 禁用形态 → 首次可用 API 级别(注释即理由)。 */
    private val denied: List<Pair<Pattern, String>> = listOf(
        Pattern.compile("""\bClassValue\b""") to "java.lang.ClassValue = API 34",
        Pattern.compile("""\breachabilityFence\s*\(""") to "Reference.reachabilityFence = API 34",
        Pattern.compile("""\bProcessHandle\b""") to "java.lang.ProcessHandle = API 34",
        Pattern.compile("""\b(?:Map|List|Set)\.of\s*\(""") to "Java 9 静态工厂 = API 30(用 Kotlin mapOf/listOf/setOf)",
        Pattern.compile("""\bOptional\.isEmpty\s*\(""") to "Optional.isEmpty = API 35",
        Pattern.compile("""\bCollectors\.toUnmodifiable""") to "Java 10 collectors = API 30",
    )

    @Test
    fun noPlatformApisAboveMinSdkInMainSources() {
        val root = locateProjectRoot()
        val sources = listOf(
            File(root, "app/src/main/java"),
            File(root, "app/src/modern"),
            File(root, "app/src/legacy"),
            File(root, "testtarget/src/main/java"),
        ).filter { it.exists() }
        assertTrue("no source roots found under $root", sources.isNotEmpty())

        val blockComment = Pattern.compile("/\\*[\\s\\S]*?\\*/")
        val lineComment = Pattern.compile("//.*$")
        val violations = sources.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> offendingLines(file, blockComment, lineComment).map { "${file.relativeTo(root)}:$it" } }
        }.toList()

        assertTrue(
            "platform APIs above minSdk 26 in main sources (crash on older hosts; lint NewApi is the " +
                "final arbiter, this gate is the fast pre-check):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun offendingLines(file: File, blockComment: Pattern, lineComment: Pattern): List<String> {
        val source = file.readText()
        val stripped = lineComment.matcher(blockComment.matcher(source).replaceAll(" ")).replaceAll("")
        val out = mutableListOf<String>()
        stripped.lineSequence().forEachIndexed { index, line ->
            // KDoc 内的反引号形态同样拦截:门禁价值在于「别写进代码」,不在注释位置
            for ((pattern, why) in denied) {
                if (pattern.matcher(line).find()) out.add("L${index + 1} [$why] ${line.trim()}")
            }
        }
        return out
    }

    private fun locateProjectRoot(): File {
        val candidates = listOf(
            File(System.getProperty("user.dir")),
            File(System.getProperty("user.dir"), ".."),
        )
        return candidates.firstOrNull { File(it, "app/src/main/java/com/lioisme/modev").isDirectory }
            ?: error("project root not located from ${System.getProperty("user.dir")}")
    }
}
