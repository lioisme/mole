package com.lioisme.modev.testtarget.cases

import android.content.Context
import com.lioisme.modev.testtarget.Tt
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** L-BHV-*:行为监听触发器(文件元操作/流/NIO/Assets/Shell)。 */
object BehaviorCases {

    private fun ttFile(ctx: Context, name: String): File =
        File(ctx.filesDir, "tt/$name").also { it.parentFile?.mkdirs() }

    fun run(ctx: Context, case: String) {
        when (case) {
            "L-BHV-01" -> {
                val f = ttFile(ctx, "fos.txt")
                FileOutputStream(f).use { it.write("fos-write".toByteArray()) }
                Tt.result(case, "written" to "true")
            }
            "L-BHV-02" -> {
                val p = ttFile(ctx, "nio-write.txt").toPath()
                Files.write(p, "nio-write".toByteArray())
                Tt.result(case, "written" to "true")
            }
            "L-BHV-03" -> {
                val f = ttFile(ctx, "created.txt")
                f.delete(); f.createNewFile()
                val d = File(ctx.filesDir, "tt/dir-${System.nanoTime()}")
                d.mkdir(); d.parentFile?.mkdirs()
                Tt.result(case, "created" to f.exists().toString())
            }
            "L-BHV-04" -> {
                val f = ttFile(ctx, "del.txt")
                FileOutputStream(f).use { it.write("x".toByteArray()) }
                Tt.result(case, "deleted" to f.delete().toString())
            }
            "L-BHV-05" -> {
                val a = ttFile(ctx, "ren-a.txt"); val b = ttFile(ctx, "ren-b.txt")
                b.delete()
                FileOutputStream(a).use { it.write("r".toByteArray()) }
                val rn = a.renameTo(b)
                val c = ttFile(ctx, "mv-c.txt"); val d = ttFile(ctx, "mv-d.txt")
                d.delete()
                Files.write(c.toPath(), "m".toByteArray())
                Files.move(c.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Tt.result(case, "rename" to rn.toString(), "move" to d.exists().toString())
            }
            "L-BHV-06" -> {
                val f = ttFile(ctx, "read.txt")
                FileOutputStream(f).use { it.write("read-me".toByteArray()) }
                val n = FileInputStream(f).use { it.read() }
                Tt.result(case, "firstByte" to n.toString())
            }
            "L-BHV-07" -> {
                val f = ttFile(ctx, "readall.txt")
                Files.write(f.toPath(), "read-all-bytes".toByteArray())
                val b = Files.readAllBytes(f.toPath())
                Files.readAllLines(f.toPath())
                Tt.result(case, "len" to b.size.toString())
            }
            "L-BHV-08" -> {
                val names = ctx.filesDir.list()
                ctx.filesDir.listFiles()
                Tt.result(case, "entries" to (names?.size ?: 0).toString())
            }
            "L-BHV-09" -> {
                ctx.assets.open("wv_basic.html").use { it.read() }
                ctx.assets.list("")
                Tt.result(case, "asset" to "ok")
            }
            "L-BHV-10" -> {
                val p = Runtime.getRuntime().exec("id")
                val out = BufferedReader(InputStreamReader(p.inputStream)).readLine()
                p.waitFor()
                Tt.result(case, "exec" to (out ?: "null"))
            }
            "L-BHV-11" -> {
                val p = ProcessBuilder("echo", "pb").start()
                val out = BufferedReader(InputStreamReader(p.inputStream)).readLine()
                p.waitFor()
                Tt.result(case, "pb" to (out ?: "null"))
            }
            "L-BHV-12" -> {
                // C-THR-01:同路径 1s 内两次写 → SessionThrottle 去重(预期 1 条)
                val f = ttFile(ctx, "throttle.txt")
                FileOutputStream(f).use { it.write("t1".toByteArray()) }
                FileOutputStream(f).use { it.write("t2".toByteArray()) }
                Tt.result(case, "writes" to "2")
            }
            "L-BHV-13" -> {
                // C-GATE-03:PathGate 内置前缀命中(/data/data 之外系统路径)+ 用户 ignorePathPrefixes
                File("/data/local/tmp/tt-ignore-probe").exists()
                File("/proc/self/cmdline").exists()
                val f = ttFile(ctx, "gate-normal.txt")
                FileOutputStream(f).use { it.write("g".toByteArray()) }
                Tt.result(case, "normalWritten" to "true")
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
