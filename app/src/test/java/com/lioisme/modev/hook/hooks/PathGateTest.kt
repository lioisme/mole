package com.lioisme.modev.hook.hooks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行为路径降噪闸契约(V27):内置系统前缀 / 模块自写子串 / 用户前缀叠加生效;
 * 空路径直通;用户清单整条前缀匹配。
 */
class PathGateTest {

    @Test
    fun `builtin system prefixes ignored`() {
        for (path in listOf(
            "/proc/self/cmdline",
            "/sys/devices/system",
            "/dev/null",
            "/system/lib/libc.so",
            "/apex/com.android.runtime/lib",
            "/vendor/etc/vintf.xml",
            "/product/app/Some.apk",
            "/system_ext/etc/config.xml",
            "/odm/lib/vendor.so",
        )) {
            assertTrue("$path should be ignored", PathGate.isIgnored(path, emptyList()))
        }
    }

    @Test
    fun `module spool paths ignored by builtin substring`() {
        assertTrue(PathGate.isIgnored("/data/data/pkg/files/modev_spool/main/spool.log", emptyList()))
        assertTrue(PathGate.isIgnored("/data/user/0/pkg/files/modev_seq_main", emptyList()))
    }

    @Test
    fun `app data paths are not ignored by default`() {
        assertFalse(PathGate.isIgnored("/data/data/pkg/files/config.json", emptyList()))
        assertFalse(PathGate.isIgnored("/storage/emulated/0/Download/file.bin", emptyList()))
        assertFalse(PathGate.isIgnored(null, listOf("/x")))
        assertFalse(PathGate.isIgnored("", emptyList()))
    }

    @Test
    fun `user prefixes overlay on builtin list`() {
        assertTrue(PathGate.isIgnored("/data/local/tmp/payload.bin", listOf("/data/local/tmp/")))
        assertFalse(PathGate.isIgnored("/data/data/pkg/cache/x", listOf("/data/local/tmp/")))
        // 空用户前缀不误伤
        assertFalse(PathGate.isIgnored("/data/data/pkg/c", listOf("")))
        // 内置清单在用户清单为空时仍生效
        assertTrue(PathGate.isIgnored("/proc/stat", listOf()))
    }
}
