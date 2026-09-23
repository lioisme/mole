package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeviceEnvKindTest {

    @Test
    fun `all DeviceEnvKind entries have corresponding DeviceEnvOperation`() {
        for (kind in DeviceEnvKind.entries) {
            val operations = DeviceEnvOperation.entries.filter { it.kind == kind }
            assert(operations.isNotEmpty()) { "DeviceEnvKind.$kind has no operations" }
        }
    }

    @Test
    fun `all DeviceEnvKind entries are in CategoryTaxonomy`() {
        for (kind in DeviceEnvKind.entries) {
            val cat = CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name)
            assertNotNull("CategoryTaxonomy missing ${kind.name}", cat)
        }
    }

    @Test
    fun `DeviceEnvKind count is 29`() {
        assertEquals(29, DeviceEnvKind.entries.size)
    }

    @Test
    fun `DeviceEnvOperation count is at least 30`() {
        assert(DeviceEnvOperation.entries.size >= 30) {
            "Expected at least 30 DeviceEnvOperations, got ${DeviceEnvOperation.entries.size}"
        }
    }

    @Test
    fun `root kinds default to disabled in CategoryTaxonomy`() {
        val rootKinds = listOf(
            DeviceEnvKind.ROOT_FILE, DeviceEnvKind.ROOT_EXEC,
            DeviceEnvKind.ROOT_PACKAGE_MANAGER, DeviceEnvKind.ROOT_PROPERTY,
        )
        for (kind in rootKinds) {
            val cat = CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name)!!
            assertEquals("$kind should default disabled (safe-first)", false, cat.defaultEnabled)
        }
    }

    @Test
    fun `xposed kinds default to disabled in CategoryTaxonomy`() {
        val xposedKinds = listOf(
            DeviceEnvKind.XPOSED_CLASS, DeviceEnvKind.XPOSED_STACK, DeviceEnvKind.XPOSED_MAPS,
        )
        for (kind in xposedKinds) {
            val cat = CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name)!!
            assertEquals("$kind should default disabled (safe-first)", false, cat.defaultEnabled)
        }
    }

    @Test
    fun `emulator kinds default to disabled in CategoryTaxonomy`() {
        val emuKinds = listOf(
            DeviceEnvKind.EMULATOR_BUILD, DeviceEnvKind.EMULATOR_FILE,
            DeviceEnvKind.EMULATOR_SENSOR, DeviceEnvKind.EMULATOR_NETWORK,
            DeviceEnvKind.EMULATOR_TELEPHONY, DeviceEnvKind.EMULATOR_CPU,
            DeviceEnvKind.EMULATOR_BATTERY,
        )
        for (kind in emuKinds) {
            val cat = CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name)!!
            assertEquals("$kind should default disabled (safe-first)", false, cat.defaultEnabled)
        }
    }

    @Test
    fun `sysprop kinds default to disabled in CategoryTaxonomy`() {
        val syspropKinds = listOf(
            DeviceEnvKind.SYSTEM_PROPERTIES,
        )
        for (kind in syspropKinds) {
            val cat = CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name)!!
            assertEquals("$kind should default disabled (safe-first)", false, cat.defaultEnabled)
        }
    }

    @Test
    fun `proc kinds default to disabled in CategoryTaxonomy`() {
        val procKinds = listOf(
            DeviceEnvKind.PROC_STATUS, DeviceEnvKind.PROC_MOUNT_INFO,
            DeviceEnvKind.PROC_CGROUP, DeviceEnvKind.PROC_NET,
        )
        for (kind in procKinds) {
            val cat = CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name)!!
            assertEquals("$kind should default disabled (safe-first)", false, cat.defaultEnabled)
        }
    }

    @Test
    fun `monitor kinds default to disabled in CategoryTaxonomy`() {
        val monKinds = listOf(DeviceEnvKind.DETECT_ROOT, DeviceEnvKind.DETECT_EMULATOR)
        for (kind in monKinds) {
            val cat = CategoryTaxonomy.byId(RecordType.DEVICE_ENV, kind.name)!!
            assertEquals("$kind should default disabled", false, cat.defaultEnabled)
        }
    }
}
