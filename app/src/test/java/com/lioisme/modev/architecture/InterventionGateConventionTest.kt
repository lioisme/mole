package com.lioisme.modev.architecture

import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V37 T0.1 FeatureGate 红线门禁(源码扫描,镜像 [HardcodedStringConventionTest]):
 * hook/hooks 包源码禁止直读干预型布尔位(`config().<field>` / `switch.<field>`),
 * 干预与观测动作一律经基类 `featureOn(kind)`(master ∧ kind 双门控)。
 * 新增干预钩子若绕过闸门,本测试即失败——杜绝「只压记录不压干预」旁路回归。
 */
class InterventionGateConventionTest {

    /** 干预型/门控型布尔位清单(HookSwitch 字段名);直读即违规,须经 *Enabled(kind) 谓词。 */
    private val gatedFields = listOf(
        "exitIntercept", "crashPrevent", "screenRecordBypass",
        "sslContext", "sslTrustManager", "sslOkhttpPinner", "sslOkhttpVerifier",
        "sslWebview", "sslNetworkConfig",
        "proxySystemProp", "proxyConnectivity", "proxySettings", "proxyOkhttp",
        "vpnCapabilities", "vpnInterface", "vpnNetworkInfo", "vpnLinkProperties",
        "rootFile", "rootExec", "rootPackageManager", "rootProperty",
        "xposedClass", "xposedStack", "xposedMaps",
        "emulatorBuild", "emulatorFile", "emulatorSensor", "emulatorNetwork",
        "emulatorTelephony", "emulatorCpu", "emulatorBattery",
        "systemProperties", "procStatus", "procMountInfo", "procCgroup", "procNet",
        "detectRoot", "detectEmulator", "networkRequest",
        // V49 WP-D 画像强制档布尔位(D1 漏登记这两项,D2 起补齐 —— 直读即须过 featureOn)
        "profileBuildIdentity", "profileBuildVersion", "profileDensity", "profileCpu", "profileBattery",
        "dialogKeyword", "dialogShow", "widgetText", "activityStart", "activityResume",
    )

    @Test
    fun hookSourcesNeverReadGatedFieldsRaw() {
        val root = locateProjectRoot()
        val hooksDir = File(root, "app/src/main/java/com/lioisme/modev/hook/hooks")
        assertTrue("hooks source dir not found: $hooksDir", hooksDir.isDirectory)

        val rawRead = Pattern.compile(
            "\\b(?:config\\(\\)|switch)\\.(" + gatedFields.joinToString("|") + ")\\b",
        )
        val blockComment = Pattern.compile("/\\*[\\s\\S]*?\\*/")
        val lineComment = Pattern.compile("//.*$")

        val violations = hooksDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val stripped = blockComment.matcher(file.readText()).replaceAll(" ")
                stripped.lines().mapIndexedNotNull { index, rawLine ->
                    val line = lineComment.matcher(rawLine).replaceAll("")
                    val m = rawRead.matcher(line)
                    if (m.find()) "${file.name}:L${index + 1} ${m.group()}" else null
                }
            }
            .toList()

        assertTrue(
            "raw reads of gated switch fields in hook/hooks (must use featureOn(kind)):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /**
     * V37 T0.3 契约:录屏解除 = 单开关(screenRecordBypass/ProcessKind.SCREEN_RECORD)
     * 驱动三钩子(ScreenRecord/SurfaceBypass/MediaProjection),三位一体一致响应;
     * 任何一钩改挂其它 kind 即红。
     */
    @Test
    fun screenRecordSingleSwitchDrivesAllThreeHookers() {
        val root = locateProjectRoot()
        val expected = listOf(
            "ScreenRecordHooker.kt",
            "SurfaceBypassHooker.kt",
            "MediaProjectionHooker.kt",
        )
        val hooksDir = File(root, "app/src/main/java/com/lioisme/modev/hook/hooks")
        for (name in expected) {
            val source = File(hooksDir, name).readText()
            assertTrue(
                "$name must gate intervention via featureOn(ProcessKind.SCREEN_RECORD)",
                source.contains("featureOn(ProcessKind.SCREEN_RECORD)"),
            )
            assertTrue(
                "$name must not read screenRecordBypass raw",
                !Pattern.compile("\\b(?:config\\(\\)|switch)\\.screenRecordBypass\\b").matcher(source).find(),
            )
        }
    }

    private fun locateProjectRoot(): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        val candidates = listOf(
            File(userDir),
            File(userDir, ".."),
        )
        return candidates.firstOrNull {
            File(it, "app/src/main/java/com/lioisme/modev/hook/hooks").isDirectory
        } ?: error("project root not located from $userDir")
    }
}
