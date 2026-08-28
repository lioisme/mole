package com.lioisme.modev.hook.hooks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootPropertyHookerTest {

    @Test
    fun debuggableEquals1IsRootIndicative() {
        assertTrue(RootPropertyHooker.isRootIndicativeValue("ro.debuggable", "1"))
    }

    @Test
    fun debuggableEquals0IsNotRootIndicative() {
        assertFalse(RootPropertyHooker.isRootIndicativeValue("ro.debuggable", "0"))
    }

    @Test
    fun secureEquals0IsRootIndicative() {
        assertTrue(RootPropertyHooker.isRootIndicativeValue("ro.secure", "0"))
    }

    @Test
    fun secureEquals1IsNotRootIndicative() {
        assertFalse(RootPropertyHooker.isRootIndicativeValue("ro.secure", "1"))
    }

    @Test
    fun selinuxEquals0IsRootIndicative() {
        assertTrue(RootPropertyHooker.isRootIndicativeValue("ro.build.selinux", "0"))
    }

    @Test
    fun selinuxEquals1IsNotRootIndicative() {
        assertFalse(RootPropertyHooker.isRootIndicativeValue("ro.build.selinux", "1"))
    }

    @Test
    fun buildTypeEngIsRootIndicative() {
        assertTrue(RootPropertyHooker.isRootIndicativeValue("ro.build.type", "eng"))
    }

    @Test
    fun buildTypeUserdebugIsRootIndicative() {
        assertTrue(RootPropertyHooker.isRootIndicativeValue("ro.build.type", "userdebug"))
    }

    @Test
    fun buildTypeUserIsNotRootIndicative() {
        assertFalse(RootPropertyHooker.isRootIndicativeValue("ro.build.type", "user"))
    }

    @Test
    fun buildTagsWithoutReleaseKeysIsRootIndicative() {
        assertTrue(RootPropertyHooker.isRootIndicativeValue("ro.build.tags", "test-keys"))
        assertTrue(RootPropertyHooker.isRootIndicativeValue("ro.build.tags", "dev-keys"))
    }

    @Test
    fun buildTagsWithReleaseKeysIsNotRootIndicative() {
        assertFalse(RootPropertyHooker.isRootIndicativeValue("ro.build.tags", "release-keys"))
    }

    @Test
    fun unknownKeyIsNotRootIndicative() {
        assertFalse(RootPropertyHooker.isRootIndicativeValue("ro.build.display.id", "1"))
    }
}
