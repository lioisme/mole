package com.lioisme.modev.hook.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnvPathMatcherTest {

    // ── Root path matching ──────────────────────────────────

    @Test
    fun `exact root path matches`() {
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/system/bin/su"))
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/sbin/su"))
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/data/adb/magisk"))
    }

    @Test
    fun `root path with subdirectory matches`() {
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/system/bin/su/something"))
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/data/adb/magisk/modules"))
    }

    @Test
    fun `root path with extension matches`() {
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/data/adb/magisk.db"))
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/system/app/Superuser.apk"))
    }

    @Test
    fun `legitimate paths with su substring do NOT match`() {
        assertFalse(DeviceEnvPathMatcher.matchesRootPath("/data/user/0/com.example"))
        assertFalse(DeviceEnvPathMatcher.matchesRootPath("/data/user_de/0/com.example"))
        assertFalse(DeviceEnvPathMatcher.matchesRootPath("/data/success/data"))
        assertFalse(DeviceEnvPathMatcher.matchesRootPath("/system/app/supported"))
        assertFalse(DeviceEnvPathMatcher.matchesRootPath("/data/super/secret"))
    }

    @Test
    fun `empty path does NOT match`() {
        assertFalse(DeviceEnvPathMatcher.matchesRootPath(""))
    }

    @Test
    fun `case insensitive matching works via caller lowering`() {
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/system/bin/su".lowercase()))
        assertTrue(DeviceEnvPathMatcher.matchesRootPath("/SYSTEM/BIN/SU".lowercase()))
    }

    // ── Emulator path matching ──────────────────────────────

    @Test
    fun `exact emulator path matches`() {
        assertEquals("/dev/qemu_pipe", DeviceEnvPathMatcher.matchesEmulatorPath("/dev/qemu_pipe"))
        assertEquals("/dev/goldfish_pipe", DeviceEnvPathMatcher.matchesEmulatorPath("/dev/goldfish_pipe"))
    }

    @Test
    fun `emulator path with subdirectory matches`() {
        assertEquals("/dev/qemu_pipe", DeviceEnvPathMatcher.matchesEmulatorPath("/dev/qemu_pipe/sub"))
    }

    @Test
    fun `legitimate paths do NOT match emulator`() {
        assertNull(DeviceEnvPathMatcher.matchesEmulatorPath("/dev/graphics/fb0"))
        assertNull(DeviceEnvPathMatcher.matchesEmulatorPath("/system/lib64/libc.so"))
        assertNull(DeviceEnvPathMatcher.matchesEmulatorPath("/data/local/tmp"))
    }

    @Test
    fun `empty path returns null for emulator`() {
        assertNull(DeviceEnvPathMatcher.matchesEmulatorPath(""))
    }
}
